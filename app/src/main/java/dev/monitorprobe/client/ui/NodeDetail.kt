package dev.monitorprobe.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.monitorprobe.client.data.History
import dev.monitorprobe.client.data.Node
import dev.monitorprobe.client.format.formatBytes
import dev.monitorprobe.client.format.formatRate
import dev.monitorprobe.client.format.formatUptime
import dev.monitorprobe.client.format.relativeSeconds

/**
 * One node in detail: its probe latencies, its packet loss, and its resource
 * history.
 *
 * The window is chosen here rather than inherited, because the two questions this
 * screen answers want different spans — "is the line bad right now" is a one-hour
 * question, "has it been flapping all week" is a seven-day one. The hub clamps the
 * request to its own retention, so asking for more than it keeps is safe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailScreen(
    node: Node,
    loadHistory: suspend (hours: Int, force: Boolean) -> Result<History>,
    onBack: () -> Unit,
) {
    var hours by rememberSaveable { mutableStateOf(6) }
    var reload by remember { mutableStateOf(0) }
    var force by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<History?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(node.id, hours, reload) {
        loading = true
        error = null
        loadHistory(hours, force)
            .onSuccess { history = it }
            .onFailure { error = it.message ?: "读取历史失败" }
        loading = false
        force = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(node.name.ifBlank { "未命名节点" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (node.online) "在线" else relativeSeconds(node.lastSeenAgo),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (node.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { force = true; reload++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1 小时", 6 to "6 小时", 24 to "24 小时", 168 to "7 天").forEach { (value, label) ->
                        AssistChip(
                            onClick = { hours = value },
                            label = { Text(if (value == hours) "✓ $label" else label) },
                        )
                    }
                }
            }

            if (error != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = error!!,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            val loaded = history
            if (loaded == null) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator() else Text(
                            text = "没有历史数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item { LatencySection(loaded) }
                item { ResourceSection(node, loaded) }
            }
        }
    }
}

@Composable
private fun LatencySection(history: History) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "延迟监控",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                if (history.step > 0) {
                    Text(
                        text = "每点 ${history.step} 秒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (history.ping.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "主控上没有给这个节点分配探针，所以没有延迟数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Spacer(Modifier.height(10.dp))
            ChartFooter(
                "整体中位" to (history.medianLatency?.let { "$it ms" } ?: "—"),
                "丢包探针" to "${history.loss.size} / ${history.probeIds.size}",
            )

            val palette = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.error,
            )

            history.probeIds.forEachIndexed { index, id ->
                val points = history.seriesFor(id)
                val color = palette[index % palette.size]
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(color)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = history.probeName(id),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val last = points.lastOrNull { it.latency != null }
                    Text(
                        text = last?.latency?.let { "$it ms" } ?: "无应答",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                    val loss = history.probeLoss(id)
                    if (loss > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "丢包 " + formatLoss(loss),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                LatencyChart(points = points, modifier = Modifier.fillMaxWidth(), lineColor = color)
                val answered = points.count { it.latency != null }
                val peak = points.mapNotNull { it.high ?: it.latency }.maxOrNull()
                ChartFooter(
                    "采样" to "$answered / ${points.size}",
                    "峰值" to (peak?.let { "$it ms" } ?: "—"),
                    "丢包" to formatLoss(history.probeLoss(id)),
                )
            }
        }
    }
}

@Composable
private fun ResourceSection(node: Node, history: History) {
    val points = history.metrics
    if (points.isEmpty()) return

    val cpu = points.map { it.cpu.toFloat() }
    val mem = points.map { point ->
        if (node.memTotal > 0) (point.memUsed.toDouble() / node.memTotal).toFloat() else 0f
    }
    val rx = points.map { it.netRx.toFloat() }
    val tx = points.map { it.netTx.toFloat() }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "资源历史",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            SeriesBlock("CPU", cpu, MaterialTheme.colorScheme.primary) { value ->
                String.format(java.util.Locale.US, "%.1f%%", value)
            }
            Spacer(Modifier.height(14.dp))
            SeriesBlock("内存", mem, MaterialTheme.colorScheme.tertiary) { value ->
                formatBytes((value * node.memTotal).toLong()) + " / " + formatBytes(node.memTotal)
            }
            Spacer(Modifier.height(14.dp))
            SeriesBlock("下行", rx, MaterialTheme.colorScheme.secondary) { formatRate(it.toLong()) }
            Spacer(Modifier.height(14.dp))
            SeriesBlock("上行", tx, MaterialTheme.colorScheme.secondary) { formatRate(it.toLong()) }
        }
    }
}

@Composable
private fun SeriesBlock(
    label: String,
    values: List<Float>,
    color: Color,
    format: (Float) -> String,
) {
    val latest = values.lastOrNull() ?: 0f
    val peak = values.maxOrNull() ?: 0f
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(text = format(latest), style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Spacer(Modifier.height(6.dp))
        Sparkline(values = values, modifier = Modifier.fillMaxWidth().height(48.dp), color = color)
        ChartFooter("峰值" to format(peak), "采样" to values.size.toString())
    }
}

/** The hub sends an unrounded percentage on purpose; keep a fractional one readable. */
private fun formatLoss(percent: Double): String =
    if (percent <= 0.0) "0%"
    else if (percent < 1.0) String.format(java.util.Locale.US, "%.2f%%", percent)
    else if (percent < 10.0) String.format(java.util.Locale.US, "%.1f%%", percent)
    else "${percent.toInt()}%"
