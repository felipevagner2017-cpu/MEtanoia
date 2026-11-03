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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = MetanoiaDatabase.get(context).agendaDao()
                val scheduler = ReminderScheduler(context)
                scheduler.scheduleDailyReminders()
                dao.pendingReminders(System.currentTimeMillis()).forEach(scheduler::schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
