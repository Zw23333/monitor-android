package dev.monitorprobe.client.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One node's live metrics, exactly as the hub's `Metrics` in `web-admin/src/lib/api.ts`
 * describes them. Every field carries a default: a hub older than the field simply
 * omits it, and `ignoreUnknownKeys` covers the reverse.
 */
@Serializable
data class Metrics(
    /** The agent's `--iface`; empty means the default rules. */
    val iface: String? = null,
    val uptime: Long = 0,
    val cpu: Double = 0.0,
    val load: List<Double> = emptyList(),
    @SerialName("mem_total") val memTotal: Long = 0,
    @SerialName("mem_used") val memUsed: Long = 0,
    @SerialName("swap_total") val swapTotal: Long = 0,
    @SerialName("swap_used") val swapUsed: Long = 0,
    @SerialName("disk_total") val diskTotal: Long = 0,
    @SerialName("disk_used") val diskUsed: Long = 0,
    @SerialName("net_rx") val netRx: Long = 0,
    @SerialName("net_tx") val netTx: Long = 0,
    @SerialName("total_rx") val totalRx: Long = 0,
    @SerialName("total_tx") val totalTx: Long = 0,
    @SerialName("month_rx") val monthRx: Long = 0,
    @SerialName("month_tx") val monthTx: Long = 0,
    val tcp: Long = 0,
    val udp: Long = 0,
    val procs: Long = 0,
) {
    val memFraction: Float get() = fraction(memUsed, memTotal)
    val diskFraction: Float get() = fraction(diskUsed, diskTotal)
    val swapFraction: Float get() = fraction(swapUsed, swapTotal)

    private fun fraction(used: Long, total: Long): Float =
        if (total <= 0) 0f else (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

@Serializable
data class Node(
    val id: Long,
    val name: String = "",
    val public: Boolean = true,
    val online: Boolean = false,
    /** Seconds since the last report on the hub's clock; null for a node never seen. */
    @SerialName("last_seen_ago") val lastSeenAgo: Long? = null,
    val metrics: Metrics? = null,
    val os: String = "",
    val kernel: String = "",
    val arch: String = "",
    val virt: String = "",
    @SerialName("cpu_name") val cpuName: String = "",
    @SerialName("cpu_cores") val cpuCores: Int = 0,
    @SerialName("mem_total") val memTotal: Long = 0,
    @SerialName("swap_total") val swapTotal: Long = 0,
    @SerialName("disk_total") val diskTotal: Long = 0,
    @SerialName("agent_version") val agentVersion: String = "",
    val price: Double = 0.0,
    val currency: String = "USD",
    @SerialName("billing_cycle") val billingCycle: String = "monthly",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("traffic_limit") val trafficLimit: Long = 0,
    @SerialName("traffic_mode") val trafficMode: String = "sum",
    @SerialName("traffic_reset_day") val trafficResetDay: Int = 1,
    @SerialName("total_rx") val totalRx: Long = 0,
    @SerialName("total_tx") val totalTx: Long = 0,
    @SerialName("month_rx") val monthRx: Long = 0,
    @SerialName("month_tx") val monthTx: Long = 0,
    /** This period's usage as the plan meters it, computed by the hub. */
    @SerialName("month_used") val monthUsed: Long = 0,
    @SerialName("month_start") val monthStart: String = "",
    /** Panel only. */
    val hostname: String? = null,
    /** ISO 3166-1 alpha-2 as shown. */
    val country: String = "",
    /** Set by hand and public; empty is ungrouped. */
    val group: String? = null,
    val ip: String? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val remark: String? = null,
    @SerialName("public_remark") val publicRemark: String? = null,
) {
    /** The plan's usage fraction for this period; 0 when the plan is unmetered. */
    val trafficFraction: Float
        get() = if (trafficLimit <= 0) 0f
        else (monthUsed.toDouble() / trafficLimit.toDouble()).coerceIn(0.0, 1.0).toFloat()

    val trafficPercent: Int get() = (trafficFraction * 100).toInt()
}

/** The frame `/api/nodes` answers and `/api/ws` pushes every two seconds. */
@Serializable
data class LiveFrame(
    val nodes: List<Node> = emptyList(),
    val admin: Boolean = false,
)

/** `/api/version` — hub's own build plus the latest published tags. */
@Serializable
data class Versions(
    val hub: String = "",
    @SerialName("hub_latest") val hubLatest: String = "",
    @SerialName("agent_latest") val agentLatest: String = "",
    val notice: Boolean = true,
)

/**
 * `/api/db` — `{path, size, wal, free, oldest, oldest_ago, retention, rows}`.
 * `rows` is a table-name → row-count map; the rest are scalars.
 */
@Serializable
data class DbStats(
    val path: String = "",
    val size: Long = 0,
    val wal: Long = 0,
    val free: Long = 0,
    val oldest: Long? = null,
    @SerialName("oldest_ago") val oldestAgo: Long? = null,
    val retention: Long = 0,
    val rows: Map<String, Long> = emptyMap(),
)

/** `/api/settings` — the readable keys plus a few computed ones. Parsed loosely on purpose. */
@Serializable
data class Settings(
    @SerialName("site_name") val siteName: String = "",
    @SerialName("public_page") val publicPage: String = "",
    @SerialName("retention_days") val retentionDays: String = "",
    val theme: String = "",
    @SerialName("your_address") val yourAddress: String = "",
    @SerialName("github_secret_set") val githubSecretSet: Boolean = false,
) {
    /** The hub stores booleans as text; anything but "off"/"false"/"" counts as on. */
    val publicPageOn: Boolean
        get() = publicPage.isNotBlank() && publicPage != "off" && publicPage != "false"
}

/**
 * One node's history window, as `/api/nodes/{id}/metrics` answers it. The request
 * omits `series`, which is what makes the hub send both halves at once — asking
 * for `metrics` and then `ping` would be two scans of the same window.
 */
@Serializable
data class History(
    val metrics: List<MetricPoint> = emptyList(),
    val ping: List<PingPoint> = emptyList(),
    /** Probe id (as text, which is how JSON object keys arrive) to its name. */
    val probes: Map<String, String> = emptyMap(),
    /** Probe id to its loss percentage across the whole window; absent when nothing was lost. */
    val loss: Map<String, Double> = emptyMap(),
    /** Seconds each point spans. The last bucket is the one still filling. */
    val step: Long = 0,
) {
    /** The probes this window has samples for, in the order the hub ranked them. */
    val probeIds: List<String>
        get() = ping.map { it.taskId.toString() }.distinct()

    fun probeName(id: String): String = probes[id]?.takeIf { it.isNotBlank() } ?: "探针 $id"

    fun probeLoss(id: String): Double = loss[id] ?: 0.0

    fun seriesFor(id: String): List<PingPoint> = ping.filter { it.taskId.toString() == id }

    /** The median latency across every probe's answered samples, or null for none. */
    val medianLatency: Long?
        get() {
            val answered = ping.mapNotNull { it.latency }.sorted()
            if (answered.isEmpty()) return null
            return answered[(answered.size - 1) / 2]
        }

    val hasAnyLoss: Boolean get() = loss.isNotEmpty()
}

/** One bucket of a node's resource history. */
@Serializable
data class MetricPoint(
    val ts: Long = 0,
    val cpu: Double = 0.0,
    @SerialName("mem_used") val memUsed: Long = 0,
    @SerialName("disk_used") val diskUsed: Long = 0,
    @SerialName("net_rx") val netRx: Long = 0,
    @SerialName("net_tx") val netTx: Long = 0,
    @SerialName("net_rx_max") val netRxMax: Long = 0,
    @SerialName("net_tx_max") val netTxMax: Long = 0,
    @SerialName("cpu_max") val cpuMax: Double = 0.0,
    val minutes: Long = 0,
    @SerialName("swap_used") val swapUsed: Long = 0,
    val tcp: Long = 0,
    val udp: Long = 0,
    val procs: Long = 0,
)

/**
 * One bucket of one probe's latency. `latency` is the median of the answers in the
 * bucket and is absent when nothing answered; `band` is `[min, max]` and is only
 * sent when the bucket actually varied; `loss` is an integer percentage and is
 * only sent when something was lost in that bucket.
 */
@Serializable
data class PingPoint(
    @SerialName("task_id") val taskId: Long = 0,
    val ts: Long = 0,
    val latency: Long? = null,
    val band: List<Long> = emptyList(),
    val loss: Int? = null,
) {
    val low: Long? get() = band.getOrNull(0)
    val high: Long? get() = band.getOrNull(1)
}

/** A node's identity as the app remembers it after a login. */
@Serializable
data class Me(val admin: Boolean = false, val raw: JsonObject = JsonObject(emptyMap()))

/**
 * The compact roll-up the widget draws and the notification would carry: the
 * hub-level numbers only, so a widget update never has to hold the node list.
 */
@Serializable
data class HubSummary(
    val siteName: String = "",
    val hubVersion: String = "",
    val total: Int = 0,
    val online: Int = 0,
    val offline: Int = 0,
    /** Sum of every online node's current throughput, in bytes per second. */
    val netRx: Long = 0,
    val netTx: Long = 0,
    /** Summed monthly usage against the summed plan limits, across metered nodes. */
    val monthUsed: Long = 0,
    val monthLimit: Long = 0,
    val updatedAt: Long = 0,
    val error: String? = null,
) {
    val allOnline: Boolean get() = total > 0 && offline == 0
}
