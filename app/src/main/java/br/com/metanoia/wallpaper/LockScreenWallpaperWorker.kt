package br.com.metanoia.wallpaper
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.metanoia.data.MetanoiaDatabase
import java.util.concurrent.TimeUnit

class LockScreenWallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        LockScreenAgendaWallpaper.refreshNow(applicationContext, MetanoiaDatabase.get(applicationContext))
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "metanoia-lockscreen-agenda-refresh"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LockScreenWallpaperWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
