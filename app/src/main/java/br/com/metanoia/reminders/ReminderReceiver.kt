package br.com.metanoia.reminders

import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.metanoia.MainActivity
import br.com.metanoia.data.MetanoiaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val daily = intent.getBooleanExtra(EXTRA_DAILY, false)
                val hour = intent.getIntExtra(EXTRA_HOUR, -1)
                val title: String
                val message: String
                val sound: String
                val category: String
                val notificationId: Int

                if (daily) {
                    if (hour !in listOf(7, 12, 18)) return@launch
                    val preferences = NotificationPreferences(context)
                    if (!preferences.dailyEnabled(hour)) return@launch
                    title = when (hour) { 7 -> "Planeje seu dia"; 12 -> "Confira sua agenda"; else -> "Organize o restante do dia" }
                    message = dailyMessage(hour)
                    sound = preferences.dailySound()
                    category = "daily"
                    notificationId = DAILY_NOTIFICATION_BASE + hour
                } else {
                    val eventId = intent.getLongExtra(EXTRA_ID, 0L)
                    val lead = intent.getIntExtra(EXTRA_LEAD_MINUTES, 0)
                    if (eventId <= 0 || lead !in listOf(120, 60, 30)) return@launch
                    val item = MetanoiaDatabase.get(context).agendaDao().findById(eventId) ?: return@launch
                    if (item.completed || item.scheduledAt <= System.currentTimeMillis()) return@launch
                    if (item.reminderAt == null || !NotificationPreferences(context).leadEnabled(lead)) return@launch
                    val whenText = when (lead) { 120 -> "Faltam 2 horas"; 60 -> "Falta 1 hora"; else -> "Faltam 30 minutos" }
                    title = item.title
                    val eventTime = java.time.Instant.ofEpochMilli(item.scheduledAt).atZone(java.time.ZoneId.systemDefault())
                    val dateTime = eventTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", java.util.Locale("pt", "BR")))
                    message = "$whenText · $dateTime"
                    sound = NotificationPreferences(context).eventSound()
                    category = "event"
                    notificationId = (eventId * 10L + when (lead) { 120 -> 0; 60 -> 1; else -> 2 }).toInt()
                }

                showNotification(context, category, notificationId, title, message, sound)
                if (daily) ReminderScheduler(context).scheduleNextDailyReminder(hour)
            } finally { pending.finish() }
        }
    }

    private fun showNotification(context: Context, category: String, id: Int, title: String, message: String, sound: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = channelId(category, sound)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                if (category == "daily") "MEtonoia · Lembretes diários" else "MEtonoia · Alarmes de compromissos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações da agenda MEtonoia"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
                setSound(soundUri(sound), if (sound == NotificationPreferences.SILENT_SOUND) null else
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                // User-requested agenda reminders must be eligible for lock-screen display.
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        val open = android.app.PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MEtonoia · $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (category == "event") NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri(sound)).setDefaults(if (sound == NotificationPreferences.SILENT_SOUND) 0 else NotificationCompat.DEFAULT_VIBRATE)
        }
        try { NotificationManagerCompat.from(context).notify(id, builder.build()) } catch (_: SecurityException) { }
    }

    private fun channelId(category: String, sound: String): String =
        "metanoia_${category}_${sound.hashCode().toUInt().toString(16)}"

    private fun soundUri(sound: String): Uri? = when (sound) {
        NotificationPreferences.SILENT_SOUND -> null
        NotificationPreferences.SYSTEM_SOUND -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> Uri.parse(sound)
    }

    private fun dailyMessage(hour: Int): String = when (hour) {
        7 -> "Bom dia! Seu dia começa com intenção. Confira seus compromissos no MEtonoia."
        12 -> "Uma pausa para se organizar. Confira o que vem pela frente."
        else -> "Organize suas próximas horas. Sua transformação é construída diariamente."
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_DAILY = "extra_daily"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
        private const val DAILY_NOTIFICATION_BASE = 70000
    }
}
