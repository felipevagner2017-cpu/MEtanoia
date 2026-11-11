package br.com.metanoia.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import br.com.metanoia.data.AgendaItem
import java.time.ZonedDateTime

class ReminderScheduler(private val context: Context) {
    private val preferences = NotificationPreferences(context)
    private val offsets = listOf(120, 60, 30)

    fun schedule(item: AgendaItem) {
        cancel(item)
        if (item.completed || item.reminderAt == null || item.scheduledAt <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val selectedOffsets = item.reminderOffsets.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()
        selectedOffsets.forEach { minutes ->
            val index = offsets.indexOf(minutes)
            if (index < 0 || !preferences.leadEnabled(minutes)) return@forEach
            val triggerAt = item.scheduledAt - minutes * 60_000L
            if (triggerAt <= System.currentTimeMillis()) return@forEach
            val pending = eventPendingIntent(item.id, index, minutes)
            setAlarm(alarmManager, triggerAt, pending)
        }
    }

    fun scheduleDailyReminders() {
        listOf(7, 12, 18).forEach { hour ->
            val pending = dailyPendingIntent(hour)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pending)
            if (preferences.dailyEnabled(hour)) scheduleNextDailyReminder(hour)
        }
    }

    fun scheduleNextDailyReminder(hour: Int) {
        if (!preferences.dailyEnabled(hour)) return
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setAlarm(alarmManager, next.toInstant().toEpochMilli(), dailyPendingIntent(hour))
    }

    fun cancel(item: AgendaItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        offsets.forEachIndexed { index, minutes ->
            alarmManager.cancel(eventPendingIntent(item.id, index, minutes))
        }
    }

    fun cancelDaily(hour: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(dailyPendingIntent(hour))
    }

    private fun setAlarm(manager: AlarmManager, triggerAt: Long, pending: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            // Inexact fallback when Android does not grant exact-alarm access.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun eventPendingIntent(id: Long, index: Int, minutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, minutes)
        return PendingIntent.getBroadcast(
            context, (id * 10L + index).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dailyPendingIntent(hour: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_DAILY, true)
            .putExtra(ReminderReceiver.EXTRA_HOUR, hour)
        return PendingIntent.getBroadcast(
            context, DAILY_REQUEST_BASE + hour, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object { private const val DAILY_REQUEST_BASE = 70000 }
}
