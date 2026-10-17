package dev.monitorprobe.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.monitorprobe.client.data.normalizeBaseUrl
import dev.monitorprobe.client.ui.HubScreen
import dev.monitorprobe.client.ui.NodeDetailScreen
import dev.monitorprobe.client.ui.theme.MonitorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app draws behind the system bars; Scaffold's insets keep the content
        // clear of them, and a dynamic-colour surface wants the full height.
        enableEdgeToEdge()

        val app = MonitorApp.from(this)

        setContent {
            // No colour arguments: on Android 12+ this resolves to the platform's
            // wallpaper-derived palette, and the whole app follows it.
            MonitorTheme {
                val state by app.repository.state.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                var selectedNodeId by rememberSaveable { mutableStateOf<Long?>(null) }

                // The live loop, scoped to the screen being started. On STOP - the
                // screen going off, the app being backgrounded - the coroutine is
                // cancelled and nothing polls; the periodic WorkManager job is what
                // covers the time in between, and it runs inside the system's own
                // maintenance windows rather than fighting Doze.
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner, state.configured) {
                    if (!state.configured) return@LaunchedEffect
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (isActive) {
                            app.repository.refresh()
                            delay(app.repository.pollIntervalMillis())
                        }
                    }
                }

                val selected = state.nodes.firstOrNull { it.id == selectedNodeId }
                if (selected != null) {
                    BackHandler { selectedNodeId = null }
                    NodeDetailScreen(
                        node = selected,
                        loadHistory = { hours, force -> app.repository.history(selected.id, hours, force) },
                        onBack = { selectedNodeId = null },
                    )
                } else {
                    HubScreen(
                        state = state,
                        intervalSeconds = app.repository.intervalSeconds(),
                        powerSaving = app.repository.isPowerSaving(),
                        onRefresh = { scope.launch { app.repository.refresh() } },
                        onSetBaseUrl = { typed ->
                            scope.launch { app.repository.setBaseUrl(normalizeBaseUrl(typed)) }
                        },
                        onLogin = { password -> scope.launch { runCatching { app.repository.login(password) } } },
                        onLogout = { scope.launch { app.repository.logout() } },
                        onSetInterval = { seconds -> app.repository.setIntervalSeconds(seconds) },
                        onOpenNode = { node -> selectedNodeId = node.id },
                    )
                }
            }
        }
    }
}
