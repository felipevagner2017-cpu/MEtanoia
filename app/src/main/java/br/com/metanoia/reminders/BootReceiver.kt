package br.com.metanoia.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.metanoia.data.MetanoiaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        if (intent.action !in supported) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reschedulePending(context)
            } finally { pendingResult.finish() }
        }
    }

    internal suspend fun reschedulePending(context: Context) {
        val dao = MetanoiaDatabase.get(context).agendaDao()
        val scheduler = ReminderScheduler(context)
        scheduler.scheduleDailyReminders()
        br.com.metanoia.wallpaper.LockScreenAgendaNotification.refresh(context, MetanoiaDatabase.get(context))
        dao.pendingReminders(System.currentTimeMillis()).forEach(scheduler::schedule)
    }
}
