package dev.monitorprobe.client.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Bytes as the panel writes them: one decimal below ten, none above, and a
 * binary unit because that is what `free`, `df` and the agent all report in.
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes.toDouble() / 1024.0.pow(group.toDouble())
    return if (group == 0) "${bytes} B" else oneDecimal(value) + " " + units[group]
}

/** A throughput, as `net_rx`/`net_tx` are reported: bytes per second. */
fun formatRate(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"

/** Two significant-ish digits: 9.8, 42, 103. */
fun oneDecimal(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded < 10) String.format(Locale.US, "%.1f", rounded)
    else String.format(Locale.US, "%.0f", rounded)
}

/** A 0..1 fraction as a percentage, without a decimal when it is whole. */
fun formatPercent(fraction: Float): String {
    val percent = (fraction * 100).toDouble()
    return if (abs(percent - percent.toLong()) < 0.05) "${percent.toLong()}%"
    else String.format(Locale.US, "%.1f%%", percent)
}

/** Seconds as the panel writes an uptime: the two largest units that matter. */
fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}天${hours}小时"
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分"
        else -> "${seconds}秒"
    }
}

/** A duration in seconds as a short span, for "retention: 30 天" and the like. */
fun formatDays(days: Long): String = if (days <= 0) "—" else "${days} 天"

/** Milliseconds since the epoch as "刚刚 / 12 秒前 / 3 分钟前 / 2 小时前". */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0) return "—"
    val seconds = (now - epochMillis) / 1000
    return when {
        seconds < 0 -> "刚刚"
        seconds < 5 -> "刚刚"
        seconds < 60 -> "${seconds} 秒前"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3_600} 小时前"
        else -> "${seconds / 86_400} 天前"
    }
}

/** How long ago a node last reported, as the hub measured it (already in seconds). */
fun relativeSeconds(seconds: Long?): String {
    if (seconds == null) return "从未上报"
    return when {
        seconds < 60 -> "${seconds} 秒前"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3_600} 小时前"
        else -> "${seconds / 86_400} 天前"
    }
}

/** A billing cycle as its Chinese name, falling back to whatever the hub stored. */
fun billingCycleName(cycle: String): String = when (cycle.lowercase()) {
    "monthly" -> "月付"
    "quarterly" -> "季付"
    "semiannually", "halfyearly" -> "半年付"
    "annually", "yearly" -> "年付"
    "biennially" -> "两年付"
    "triennially" -> "三年付"
    "one-time", "onetime" -> "一次性"
    else -> cycle
}

/**
 * A two-letter country code as its flag, built from the regional indicator
 * symbols. Anything that is not two ASCII letters is returned unchanged, so a
 * hub that stores something else does not render as nonsense.
 */
fun countryFlag(code: String): String {
    val upper = code.trim().uppercase(Locale.US)
    if (upper.length != 2 || upper.any { it !in 'A'..'Z' }) return ""
    val base = 0x1F1E6 - 'A'.code
    return String(Character.toChars(base + upper[0].code)) + String(Character.toChars(base + upper[1].code))
}

/** A short tag for a virtualisation the agent detected. */
fun virtName(virt: String): String = when (virt.lowercase()) {
    "kvm" -> "KVM"
    "lxc" -> "LXC"
    "openvz" -> "OpenVZ"
    "docker" -> "Docker"
    "vmware" -> "VMware"
    "microsoft", "hyper-v", "hyperv" -> "Hyper-V"
    "xen" -> "Xen"
    "none", "" -> "独服"
    else -> virt
}
