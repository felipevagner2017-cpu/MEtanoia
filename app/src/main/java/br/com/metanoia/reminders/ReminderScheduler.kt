package br.com.metanoia.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import br.com.metanoia.data.AgendaItem
import java.time.ZonedDateTime

class ReminderScheduler(private val context: Context) {
    private val offsets = listOf(120, 60, 30)

    fun schedule(item: AgendaItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        offsets.forEachIndexed { index, minutes ->
            val triggerAt = item.scheduledAt - minutes * 60_000L
            if (triggerAt <= System.currentTimeMillis()) return@forEachIndexed
            val pending = pendingIntent(item, index, minutes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun scheduleDailyReminders() {
        listOf(7, 12, 18).forEach(::scheduleNextDailyReminder)
    }

    fun scheduleNextDailyReminder(hour: Int) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_DAILY, true)
            .putExtra(ReminderReceiver.EXTRA_HOUR, hour)
        val pending = PendingIntent.getBroadcast(
            context, DAILY_REQUEST_BASE + hour, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = next.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(item: AgendaItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        offsets.forEachIndexed { index, minutes ->
            alarmManager.cancel(pendingIntent(item, index, minutes))
        }
    }

    private fun pendingIntent(item: AgendaItem, index: Int, minutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TITLE, item.title)
            .putExtra(ReminderReceiver.EXTRA_ID, item.id)
            .putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, minutes)
        return PendingIntent.getBroadcast(
            context, item.id.toInt() * 10 + index, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val DAILY_REQUEST_BASE = 70000
    }
}
