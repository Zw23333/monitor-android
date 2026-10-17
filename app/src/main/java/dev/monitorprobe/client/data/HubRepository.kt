package dev.monitorprobe.client.data

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the UI draws, in one immutable snapshot. */
data class HubState(
    /** False until a hub address is on file. */
    val configured: Boolean = false,
    val loading: Boolean = false,
    /** False while a request is in flight, so the first paint is distinguishable. */
    val everLoaded: Boolean = false,
    val nodes: List<Node> = emptyList(),
    /** Whether the session on file is an admin one. */
    val admin: Boolean = false,
    val versions: Versions? = null,
    val db: DbStats? = null,
    val settings: Settings? = null,
    val error: String? = null,
    /** Milliseconds since the epoch, on this device's clock. */
    val lastUpdated: Long = 0,
) {
    /**
     * The aggregates the header shows. Computed once per state by [Rollup] rather
     * than as properties: a property would re-walk the node list on every
     * recomposition, and this screen recomposes on every poll.
     */
    val rollup: Rollup by lazy { Rollup.of(nodes) }

    val online: Int get() = rollup.online
    val offline: Int get() = rollup.offline
    val metered: List<Node> get() = nodes.filter { it.trafficLimit > 0 }

    fun summary(siteName: String): HubSummary = HubSummary(
        siteName = siteName.ifBlank { settings?.siteName.orEmpty() },
        hubVersion = versions?.hub.orEmpty(),
        total = nodes.size,
        online = rollup.online,
        offline = rollup.offline,
        netRx = rollup.netRx,
        netTx = rollup.netTx,
        monthUsed = rollup.monthUsed,
        monthLimit = rollup.monthLimit,
        updatedAt = lastUpdated,
        error = error,
    )

    /** What the header needs, folded in one pass. */
    data class Rollup(
        val online: Int,
        val offline: Int,
        val netRx: Long,
        val netTx: Long,
        val monthUsed: Long,
        val monthLimit: Long,
    ) {
        companion object {
            fun of(nodes: List<Node>): Rollup {
                var online = 0
                var netRx = 0L
                var netTx = 0L
                var monthUsed = 0L
                var monthLimit = 0L
                for (node in nodes) {
                    if (node.online) {
                        online++
                        netRx += node.metrics?.netRx ?: 0
                        netTx += node.metrics?.netTx ?: 0
                    }
                    monthUsed += node.monthUsed
                    monthLimit += node.trafficLimit
                }
                return Rollup(online, nodes.size - online, netRx, netTx, monthUsed, monthLimit)
            }
        }
    }
}

/**
 * Owns the cached admin payloads and the history cache. **It does not poll on its
 * own.** The live loop is driven by whoever can see the screen
 * (`MainActivity`, via `repeatOnLifecycle`), and the background refresh by a
 * `WorkManager` job; a repository that started its own loop from `Application`
 * would keep polling with the screen off and the device dozing, which is exactly
 * the battery the platform suspends the network to protect.
 */
class HubRepository(private val prefs: Prefs, private val context: Context) {

    private val api = HubApi(prefs)
    private val _state = MutableStateFlow(HubState(configured = prefs.baseUrl.isNotEmpty()))
    val state: StateFlow<HubState> = _state.asStateFlow()

    private var ticks = 0

    /** History is expensive on the hub and changes slowly, so it is kept briefly. */
    private val historyCache = LinkedHashMap<String, CacheEntry>()
    private val historyLock = Any()

    private class CacheEntry(val history: History, val at: Long)

    /**
     * How long to wait between polls. Widened while the system is saving power or
     * in Doze: the point of the platform suspending the network there is that a
     * monitor should not be the thing that keeps waking it.
     */
    fun pollIntervalMillis(): Long {
        val base = prefs.intervalSeconds.coerceIn(2, 300) * 1000L
        val power = context.getSystemService(PowerManager::class.java)
        val saving = power?.isPowerSaveMode == true || power?.isDeviceIdleMode == true
        return if (saving) maxOf(base, POWER_SAVE_FLOOR_MS) else base
    }

    /** True when the poll interval is currently stretched, for the UI to say so. */
    fun isPowerSaving(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isPowerSaveMode || power.isDeviceIdleMode
    }

    /** One refresh of the live frame, plus the admin payloads when due. */
    suspend fun refresh() {
        if (prefs.baseUrl.isEmpty()) {
            _state.update { it.copy(configured = false, loading = false) }
            return
        }
        _state.update { it.copy(loading = true) }
        try {
            val frame = api.nodes()
            _state.update {
                it.copy(
                    loading = false,
                    configured = true,
                    everLoaded = true,
                    nodes = frame.nodes,
                    admin = frame.admin,
                    error = null,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
            // These three change rarely and each is a separate round trip, so they
            // ride every tenth poll - and when they do run they run together rather
            // than in series, which is three latencies instead of one.
            if (frame.admin && ticks++ % ADMIN_EVERY == 0) refreshAdmin()
        } catch (e: Exception) {
            _state.update {
                it.copy(loading = false, everLoaded = true, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun refreshAdmin() = coroutineScope {
        // Started together so the three round trips overlap; awaited separately
        // because `awaitAll` over three different payload types would collapse them
        // to their common supertype and lose the result each one carries.
        val versions = async { runCatching { api.versions() } }
        val db = async { runCatching { api.dbStats() } }
        val settings = async { runCatching { api.settings() } }
        versions.await().getOrNull()?.let { v -> _state.update { it.copy(versions = v) } }
        db.await().getOrNull()?.let { d -> _state.update { it.copy(db = d) } }
        settings.await().getOrNull()?.let { s -> _state.update { it.copy(settings = s) } }
    }

    /**
     * One node's history. Served from a short-lived cache when it can be: the hub
     * admits only a few history scans at once, and reopening the same node within
     * the TTL would otherwise spend one of them for data that has not moved.
     */
    suspend fun history(nodeId: Long, hours: Int, force: Boolean = false): Result<History> {
        val key = "$nodeId/$hours"
        val now = System.currentTimeMillis()
        if (!force) {
            synchronized(historyLock) {
                historyCache[key]?.let { entry ->
                    if (now - entry.at < HISTORY_TTL_MS) return Result.success(entry.history)
                }
            }
        }
        return runCatching { api.history(nodeId, hours) }
            .onSuccess { history ->
                synchronized(historyLock) {
                    historyCache[key] = CacheEntry(history, now)
                    // Bounded: a handful of nodes at one or two windows each.
                    while (historyCache.size > HISTORY_CACHE_MAX) {
                        val oldest = historyCache.keys.firstOrNull() ?: break
                        historyCache.remove(oldest)
                    }
                }
            }
    }

    suspend fun login(password: String) {
        api.login(password)
        ticks = 0
        clearHistory()
        refresh()
    }

    suspend fun logout() {
        api.logout()
        _state.update { it.copy(admin = false, versions = null, db = null, settings = null) }
        clearHistory()
        refresh()
    }

    /** Points the app at a new hub, dropping the session the old one issued. */
    suspend fun setBaseUrl(url: String) {
        api.reset()
        prefs.baseUrl = url
        _state.value = HubState(configured = url.isNotEmpty())
        ticks = 0
        clearHistory()
        refresh()
    }

    fun intervalSeconds(): Int = prefs.intervalSeconds

    fun setIntervalSeconds(seconds: Int) {
        prefs.intervalSeconds = seconds.coerceIn(2, 300)
    }

    private fun clearHistory() {
        synchronized(historyLock) { historyCache.clear() }
    }

    private companion object {
        const val ADMIN_EVERY = 10
        const val HISTORY_TTL_MS = 30_000L
        const val HISTORY_CACHE_MAX = 8
        const val POWER_SAVE_FLOOR_MS = 60_000L
    }
}
