package dev.monitorprobe.client

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.monitorprobe.client.data.HubRepository
import dev.monitorprobe.client.data.HubSummary
import dev.monitorprobe.client.data.Prefs
import dev.monitorprobe.client.widget.HubWidget
import dev.monitorprobe.client.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Owns the one repository the app and the widget share.
 *
 * It deliberately starts **no polling**. The live loop belongs to whatever can see
 * the screen, and the background refresh belongs to `WorkManager`; anything
 * started here would outlive the UI and keep the radio busy through Doze.
 */
class MonitorApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var repository: HubRepository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = HubRepository(prefs, this)

        RefreshWorker.schedule(this)

        scope.launch {
            repository.state
                // The widget draws a handful of figures; repainting it because
                // `lastUpdated` moved would wake the launcher for a number that did
                // not. Keyed on the summary's own fields instead, with the error
                // included so a broken hub still shows up without opening the app.
                .map { state ->
                    state.summary(prefs.baseUrl).copy(updatedAt = 0L)
                }
                .distinctUntilChanged()
                .collect { summary ->
                    val state = repository.state.value
                    if (!state.everLoaded && state.error == null) return@collect
                    prefs.widgetCache = json.encodeToString(
                        HubSummary.serializer(),
                        state.summary(prefs.baseUrl),
                    )
                    runCatching { HubWidget().updateAll(this@MonitorApp) }
                }
        }
    }

    companion object {
        fun from(context: Context): MonitorApp = context.applicationContext as MonitorApp
    }
}
