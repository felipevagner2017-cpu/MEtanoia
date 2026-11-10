package br.com.metanoia.wallpaper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.metanoia.MainActivity
import br.com.metanoia.data.MetanoiaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ongoing, lock-screen eligible agenda summary. Android and the device owner's
 * lock-screen privacy policy remain authoritative over whether details are shown.
 */
object LockScreenAgendaNotification {
    const val NOTIFICATION_ID = 81001
    private const val TEST_NOTIFICATION_ID = 81002
    private const val CHANNEL_ID = "metonoia_lock_screen_agenda_v1"
    private const val TEST_CHANNEL_ID = "metonoia_notification_test_v1"

    fun hasRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean {
        if (!hasRuntimePermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun refresh(context: Context, db: MetanoiaDatabase) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { refreshNow(app, db) }
                .onFailure { android.util.Log.e("MEtonoiaLockScreen", "Falha ao atualizar notificação da agenda", it) }
        }
    }

    suspend fun refreshNow(context: Context, db: MetanoiaDatabase) {
        if (!canPost(context)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(7).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val upcoming = db.agendaDao().observeRange(start, end).first()
            .filterNot { it.completed }
            .sortedBy { it.scheduledAt }
            .take(5)

        ensureAgendaChannel(context)
        val lines = upcoming.map { item ->
            val event = Instant.ofEpochMilli(item.scheduledAt).atZone(zone)
            val date = when (event.toLocalDate()) {
                today -> "Hoje"
                today.plusDays(1) -> "Amanhã"
                else -> event.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale("pt", "BR")))
            }
            "$date · ${event.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${item.title}"
        }
        val summary = lines.firstOrNull() ?: "Nenhum compromisso nos próximos 7 dias."
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle("Próximos compromissos")
        if (lines.isEmpty()) style.addLine("Nenhum compromisso nos próximos 7 dias.")
        else lines.forEach(style::addLine)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MEtonoia · Sua agenda")
            .setContentText(summary)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("MEtonoia · Agenda")
                    .setContentText("Seus próximos compromissos estão atualizados.")
                    .build()
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            android.util.Log.w("MEtonoiaLockScreen", "Permissão de notificação indisponível", error)
        }
    }

    fun showTestNotification(context: Context): Boolean {
        if (!canPost(context)) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    TEST_CHANNEL_ID,
                    "MEtonoia · Teste de notificação",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Confirmação solicitada pelo usuário nas configurações do MEtanoia"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        val open = PendingIntent.getActivity(
            context, TEST_NOTIFICATION_ID, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MEtonoia · Notificação de teste")
            .setContentText("As notificações do aplicativo estão funcionando neste aparelho.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureAgendaChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "MEtonoia · Agenda na tela de bloqueio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Resumo persistente dos próximos compromissos da agenda"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
    }
}
