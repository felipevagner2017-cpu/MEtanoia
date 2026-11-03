package br.com.metanoia.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isDaily = intent.getBooleanExtra(EXTRA_DAILY, false)
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val id = if (isDaily) DAILY_NOTIFICATION_BASE + hour else {
            val eventId = intent.getLongExtra(EXTRA_ID, 0L).toInt()
            val lead = intent.getIntExtra(EXTRA_LEAD_MINUTES, 0)
            eventId * 10 + when (lead) { 120 -> 0; 60 -> 1; else -> 2 }
        }
        val title = if (isDaily && hour == 7) "MEtanoia · Desafio com Ingrid"
            else if (isDaily) "MEtanoia · Cotidiano" else "MEtanoia · Compromisso próximo"
        val message = if (isDaily) dailyMessage(hour) else {
            val lead = intent.getIntExtra(EXTRA_LEAD_MINUTES, 0)
            val whenText = when (lead) { 120 -> "em 2 horas"; 60 -> "em 1 hora"; else -> "em 30 minutos" }
            "${intent.getStringExtra(EXTRA_TITLE) ?: "Compromisso agendado"} — começa $whenText."
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Lembretes MEtanoia", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes diários e compromissos da sua agenda"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
            }
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(longArrayOf(0, 250, 120, 250))
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(id, notification) }
        catch (_: SecurityException) { }
        if (isDaily && hour in listOf(7, 12, 18)) ReminderScheduler(context).scheduleNextDailyReminder(hour)
    }

    private fun dailyMessage(hour: Int): String = when (hour) {
        7 -> "Pergunte à Ingrid como ela está e o que seria cuidado para ela hoje. Escute sem presumir e transforme a resposta em uma atitude concreta."
        12 -> "Faça uma pausa: revise o que está sob sua responsabilidade e reorganize o restante com sabedoria."
        18 -> "Encerre o dia: reconheça o que avançou, registre o que ficou e desacelere."
        else -> "Pare um momento e revise suas prioridades com sabedoria."
    }

    companion object {
        const val CHANNEL_ID = "metanoia_reminders_v2"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_DAILY = "extra_daily"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
        private const val DAILY_NOTIFICATION_BASE = 70000
    }
}
