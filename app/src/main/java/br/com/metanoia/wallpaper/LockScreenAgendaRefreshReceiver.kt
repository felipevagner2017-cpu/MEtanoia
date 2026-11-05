package br.com.metanoia.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.metanoia.data.MetanoiaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Refreshes the lock-screen agenda after day, clock, time-zone, boot or app updates. */
class LockScreenAgendaRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in supported) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                LockScreenWallpaperWorker.schedule(app)
                LockScreenAgendaWallpaper.refreshNow(app, MetanoiaDatabase.get(app))
            } catch (_: Exception) {
                // WorkManager remains the periodic fallback if this immediate refresh is interrupted.
            } finally {
                pending.finish()
            }
        }
    }
}
