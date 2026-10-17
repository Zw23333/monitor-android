package dev.monitorprobe.client.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.monitorprobe.client.MonitorApp
import dev.monitorprobe.client.widget.HubWidget
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The only thing that touches the hub while the app is not on screen.
 *
 * It exists because the alternative — polling from a long-lived coroutine in
 * `Application` — is exactly what the platform's Doze and App Standby modes are
 * built to stop: the network is suspended while the device is idle, so a poll
 * there either fails or, worse, keeps a wakeup scheduled to fail. `WorkManager`
 * instead hands the schedule to the system, which batches and defers it inside
 * maintenance windows and simply does not run it when the device is dozing or the
 * battery is low. Fifteen minutes is its floor for periodic work, which is also
 * the right order for a widget nobody is staring at.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MonitorApp ?: return Result.success()
        return try {
            app.repository.refresh()
            // The repository writes the cached summary and the app's collector
            // pushes it; when the process was started only for this worker there is
            // no collector, so push here too. `updateAll` is cheap when the widget
            // is not on any home screen.
            runCatching { HubWidget().updateAll(applicationContext) }
            Result.success()
        } catch (e: Exception) {
            // A hub that is down is the normal case this app must survive; retrying
            // with the system's backoff is right for a network error, and the next
            // period comes round regardless.
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "hub-refresh"

        /**
         * Schedules the periodic refresh. `KEEP` so re-launching the app does not
         * reset the period and starve the widget of ever actually running.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
