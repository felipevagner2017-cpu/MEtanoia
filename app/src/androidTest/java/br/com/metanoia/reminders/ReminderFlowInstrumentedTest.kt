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
        }
    }

    @Test
    fun receiverDoesNotPublishWhenAndroidNotificationPermissionIsRevoked() {
        val item = insertFutureEvent()
        shell("appops set ${context.packageName} POST_NOTIFICATION ignore")
        val appOpsState = shell("appops get ${context.packageName} POST_NOTIFICATION")
        assertTrue(
            "O AppOps do Android deve registrar o bloqueio de notificações.",
            appOpsState.contains("ignore", ignoreCase = true)
        )

        try {
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_ID, item.id)
                .putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, 60)
            context.sendBroadcast(intent)

            Thread.sleep(1_000)
            val notificationId = (item.id * 10L + 1L).toInt()
            assertFalse(
                "Sem POST_NOTIFICATIONS, o receiver não pode deixar uma notificação ativa.",
                notifications.activeNotifications.any { it.id == notificationId }
            )
        } finally {
            shell("appops set ${context.packageName} POST_NOTIFICATION allow")
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
}
