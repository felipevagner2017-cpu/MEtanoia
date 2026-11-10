package br.com.metanoia.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import br.com.metanoia.data.AgendaItem
import br.com.metanoia.data.MetanoiaDatabase
import br.com.metanoia.wallpaper.LockScreenAgendaNotification
import br.com.metanoia.wallpaper.LockScreenAgendaWallpaper
import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderFlowInstrumentedTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val dao
        get() = MetanoiaDatabase.get(context).agendaDao()
    private val notifications
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scheduler
        get() = ReminderScheduler(context)

    private val insertedIds = mutableListOf<Long>()

    @Before
    fun clearNotifications() {
        notifications.cancelAll()
    }

    @After
    fun cleanUp() {
        insertedIds.forEach { id ->
            val item = kotlinx.coroutines.runBlocking { dao.findById(id) }
            if (item != null) {
                scheduler.cancel(item)
                kotlinx.coroutines.runBlocking { dao.delete(item) }
            }
        }
        notifications.cancelAll()
        listOf(7, 12, 18).forEach(scheduler::cancelDaily)
    }

    private fun insertFutureEvent(recurrence: String? = null, offsetHours: Long = 4): AgendaItem {
        val now = System.currentTimeMillis()
        val item = AgendaItem(
            title = "Compromisso de teste",
            scheduledAt = now + offsetHours * 60 * 60 * 1000L,
            reminderAt = now,
            recurrenceRule = recurrence
        )
        val id = kotlinx.coroutines.runBlocking { dao.insert(item) }
        insertedIds += id
        return item.copy(id = id)
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
    }

    private fun alarmDump(): String = shell("dumpsys alarm")

    private fun await(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    @Test
    fun schedulerRegistersCommitmentAlarmsAndBootReceiverRestoresRecurringOnes() {
        val occurrences = listOf(
            insertFutureEvent("DAILY", 4),
            insertFutureEvent("DAILY", 28),
            insertFutureEvent("DAILY", 52)
        )
        occurrences.forEach(scheduler::schedule)

        assertTrue(
            "O AlarmManager deve registrar os alarmes de antecedência dos compromissos.",
            await { Regex("ReminderReceiver").findAll(alarmDump()).count() >= 9 }
        )

        kotlinx.coroutines.runBlocking { BootReceiver().reschedulePending(context) }
        assertTrue(
            "A inicialização deve restaurar alarmes diários e todas as ocorrências pendentes.",
            await { Regex("ReminderReceiver").findAll(alarmDump()).count() >= 12 }
        )

        val stored = kotlinx.coroutines.runBlocking { dao.pendingReminders(System.currentTimeMillis()) }
            .filter { it.recurrenceRule == "DAILY" }
        assertEquals("As três ocorrências recorrentes devem continuar no Room.", 3, stored.size)
        assertEquals(3, stored.map { it.scheduledAt }.distinct().size)
        assertTrue(stored.all { it.reminderAt != null && !it.completed })
    }

    @Test
    fun receiverDeliversCommitmentNotificationWithoutWaitingForItsFutureTime() {
        val item = insertFutureEvent()
        listOf(120, 60, 30).forEachIndexed { index, lead ->
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_ID, item.id)
                .putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, lead)
            context.sendBroadcast(intent)
            val notificationId = (item.id * 10L + index).toInt()
            assertTrue(
                "O receiver deve entregar o lembrete de $lead minutos sem aguardar o horário real.",
                await { notifications.activeNotifications.any { it.id == notificationId } }
            )
            val posted = notifications.activeNotifications.first { it.id == notificationId }
            val channel = notifications.getNotificationChannel(posted.notification.channelId)
            assertEquals("O canal do compromisso deve permitir exibição pública na tela bloqueada.", android.app.Notification.VISIBILITY_PUBLIC, channel?.lockscreenVisibility)
        }
        scheduler.cancel(item)
        assertTrue(
            "Editar, concluir ou excluir o compromisso deve remover os avisos antigos já publicados.",
            await { notifications.activeNotifications.none { it.id in listOf((item.id * 10L).toInt(), (item.id * 10L + 1L).toInt(), (item.id * 10L + 2L).toInt()) } }
        )
    }

    @Test
    fun receiverHonorsOnlyTheReminderOffsetsSelectedForEachEvent() {
        val item = insertFutureEvent().copy(reminderOffsets = "60")
        kotlinx.coroutines.runBlocking { dao.update(item) }
        listOf(120, 60, 30).forEach { lead ->
            context.sendBroadcast(
                Intent(context, ReminderReceiver::class.java)
                    .putExtra(ReminderReceiver.EXTRA_ID, item.id)
                    .putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, lead)
            )
        }
        val disabledIds = listOf((item.id * 10L).toInt(), (item.id * 10L + 2L).toInt())
        assertTrue(
            "Somente o lembrete de 60 minutos foi selecionado; os demais não devem ser publicados.",
            await {
                notifications.activeNotifications.any { it.id == (item.id * 10L + 1L).toInt() } &&
                    notifications.activeNotifications.none { it.id in disabledIds }
            }
        )
    }

    @Test
    fun receiverDoesNotPublishWhenAndroidNotificationPermissionIsRevoked() {
        val item = insertFutureEvent()
        try {
            // POST_NOTIFICATION is UID-scoped. Verify the OS policy directly, then
            // assert that the receiver cannot deliver while that policy is denied.
            shell("appops set --uid ${context.packageName} POST_NOTIFICATION ignore")
            val appOpsState = shell("appops get --uid ${context.packageName} POST_NOTIFICATION")
            assertTrue(
                "O Android deve registrar POST_NOTIFICATION como ignore para o UID do app: $appOpsState",
                appOpsState.contains("ignore", ignoreCase = true)
            )

            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_ID, item.id)
                .putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, 60)
            context.sendBroadcast(intent)

            val notificationId = (item.id * 10L + 1L).toInt()
            assertTrue(
                "Com POST_NOTIFICATION bloqueada no UID, o receiver não pode publicar a notificação.",
                await { notifications.activeNotifications.none { it.id == notificationId } }
            )
        } finally {
            shell("appops set --uid ${context.packageName} POST_NOTIFICATION allow")
        }
    }

    @Test
    fun dailyReceiverPublishesAndSchedulesTheNextOccurrence() {
        listOf(7, 12, 18).forEach { hour ->
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_DAILY, true)
                .putExtra(ReminderReceiver.EXTRA_HOUR, hour)
            context.sendBroadcast(intent)
            assertTrue(
                "O lembrete diário das $hour horas deve ser entregue sem esperar o relógio.",
                await { notifications.activeNotifications.any { it.id == 70000 + hour } }
            )
        }
        assertTrue(
            "Após entregar, o receiver deve agendar as próximas ocorrências diárias.",
            await { Regex("ReminderReceiver").findAll(alarmDump()).count() >= 3 }
        )
    }
    @Test
    fun persistentAgendaNotificationShowsUpcomingCommitmentsOnLockScreenChannel() {
        val item = insertFutureEvent()
        kotlinx.coroutines.runBlocking {
            LockScreenAgendaNotification.refreshNow(context, MetanoiaDatabase.get(context))
        }
        val posted = notifications.activeNotifications.firstOrNull {
            it.id == LockScreenAgendaNotification.NOTIFICATION_ID
        }
        assertNotNull("O resumo persistente da agenda deve ser publicado.", posted)
        assertEquals(
            "O resumo precisa estar marcado como visível na tela bloqueada.",
            android.app.Notification.VISIBILITY_PUBLIC,
            posted!!.notification.visibility
        )
        assertTrue(
            "A notificação da agenda deve permanecer até ser atualizada ou desativada.",
            posted.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
        )
        val expanded = posted.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val lines = posted.notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        assertTrue(
            "O resumo precisa refletir o compromisso salvo no Room.",
            expanded.contains(item.title) || lines.contains(item.title)
        )
    }

    @Test
    fun testNotificationIsActuallyPostedToAndroidNotificationManager() {
        assertTrue("A permissão de notificação deve estar concedida neste teste.", LockScreenAgendaNotification.canPost(context))
        assertTrue("O método de teste deve publicar uma notificação real.", LockScreenAgendaNotification.showTestNotification(context))
        assertTrue(
            "A notificação de teste deve aparecer no NotificationManager do Android.",
            await { notifications.activeNotifications.any { it.id == 81002 } }
        )
    }

    @Test
    fun alarmManagerDeliversScheduledReminderAtItsTriggerTime() {
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val now = System.currentTimeMillis()
        val item = AgendaItem(
            title = "Disparo real do alarme MEtonoia",
            scheduledAt = now + 120L * 60_000L + 4_000L,
            reminderAt = now + 4_000L,
            reminderOffsets = "120"
        )
        val id = kotlinx.coroutines.runBlocking { dao.insert(item) }
        insertedIds += id
        scheduler.schedule(item.copy(id = id))
        assertTrue(
            "O AlarmManager deve disparar o lembrete agendado, sem envio manual de broadcast.",
            await(timeoutMs = 30_000) {
                notifications.activeNotifications.any { it.id == (id * 10L).toInt() }
            }
        )
    }

    @Test
    fun changingAndDeletingCommitmentCancelsAndReplacesItsScheduledAlarms() {
        val original = insertFutureEvent()
        val baseline = Regex("ReminderReceiver").findAll(alarmDump()).count()
        scheduler.schedule(original)
        val afterFirstSchedule = Regex("ReminderReceiver").findAll(alarmDump()).count()
        assertTrue("Os três alarmes escolhidos devem ser registrados.", afterFirstSchedule >= baseline + 3)
        val changed = original.copy(
            scheduledAt = original.scheduledAt + 60L * 60_000L,
            reminderAt = original.scheduledAt + 60L * 60_000L - 120L * 60_000L
        )
        scheduler.cancel(original)
        kotlinx.coroutines.runBlocking { dao.update(changed) }
        scheduler.schedule(changed)
        val afterUpdate = Regex("ReminderReceiver").findAll(alarmDump()).count()
        assertTrue("Editar deve substituir os alarmes antigos, sem duplicar os três novos.", afterUpdate >= baseline + 3 && afterUpdate <= afterFirstSchedule)
        scheduler.cancel(changed)
        val afterDelete = Regex("ReminderReceiver").findAll(alarmDump()).count()
        assertTrue("Após cancelar/excluir, os alarmes do compromisso devem ser removidos.", afterDelete <= baseline)
    }

    @Test
    fun agendaWallpaperIsAppliedToTheAndroidLockScreenSlot() {
        val manager = android.app.WallpaperManager.getInstance(context)
        kotlinx.coroutines.runBlocking {
            LockScreenAgendaWallpaper.refreshNow(context, MetanoiaDatabase.get(context))
        }
        val lockWallpaperId = manager.getWallpaperId(android.app.WallpaperManager.FLAG_LOCK)
        assertTrue(
            "A agenda deve ser aplicada ao slot de papel de parede da tela bloqueada no Android.",
            lockWallpaperId >= 0
        )
    }

}
