package dev.monitorprobe.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.monitorprobe.client.data.Node
import dev.monitorprobe.client.format.billingCycleName
import dev.monitorprobe.client.format.countryFlag
import dev.monitorprobe.client.format.formatBytes
import dev.monitorprobe.client.format.formatPercent
import dev.monitorprobe.client.format.formatRate
import dev.monitorprobe.client.format.formatUptime
import dev.monitorprobe.client.format.relativeSeconds
import dev.monitorprobe.client.format.virtName

/**
 * One node. The card leads with what a glance needs — is it up, how loaded is it —
 * and keeps the plan details underneath, because a phone screen shows three of
 * these at a time and the figures that change are the ones worth the space.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NodeCard(node: Node, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = node.metrics
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (node.online) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(node.online)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.name.ifBlank { "未命名节点" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val flag = countryFlag(node.country)
                if (flag.isNotEmpty()) {
                    Text(text = flag, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (node.online) "在线" else relativeSeconds(node.lastSeenAgo),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (node.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            val subtitle = listOfNotNull(
                node.os.takeIf { it.isNotBlank() },
                virtName(node.virt).takeIf { it.isNotBlank() },
                node.arch.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            val note = node.publicRemark?.takeIf { it.isNotBlank() } ?: node.remark?.takeIf { it.isNotBlank() }
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (metrics != null) {
                Spacer(Modifier.height(12.dp))
                MeterRow(
                    label = "CPU",
                    fraction = (metrics.cpu / 100.0).toFloat(),
                    valueText = String.format(java.util.Locale.US, "%.1f%%", metrics.cpu),
                )
                Spacer(Modifier.height(8.dp))
                MeterRow(
                    label = "内存",
                    fraction = metrics.memFraction,
                    valueText = formatBytes(metrics.memUsed) + " / " + formatBytes(metrics.memTotal),
                )
                Spacer(Modifier.height(8.dp))
                MeterRow(
                    label = "硬盘",
                    fraction = metrics.diskFraction,
                    valueText = formatBytes(metrics.diskUsed) + " / " + formatBytes(metrics.diskTotal),
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    KeyValue("上行", formatRate(metrics.netTx))
                    KeyValue("下行", formatRate(metrics.netRx))
                    KeyValue("连接", "${metrics.tcp} TCP")
                    KeyValue("进程", metrics.procs.toString())
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    KeyValue("运行时长", formatUptime(metrics.uptime))
                    KeyValue("负载", metrics.load.take(3).joinToString(" ") { String.format(java.util.Locale.US, "%.2f", it) })
                    KeyValue("累计", formatBytes(metrics.totalRx + metrics.totalTx))
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "没有实时数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (node.trafficLimit > 0) {
                Spacer(Modifier.height(12.dp))
                MeterRow(
                    label = "本月流量",
                    fraction = node.trafficFraction,
                    valueText = formatBytes(node.monthUsed) + " / " + formatBytes(node.trafficLimit) +
                        "  (" + node.trafficPercent + "%)",
                    warnAt = 0.8f,
                )
            }

            val planBits = buildList {
                if (node.price > 0) add("${node.currency} ${node.price} / ${billingCycleName(node.billingCycle)}")
                node.expiresAt?.takeIf { it.isNotBlank() }?.let { add("到期 $it") }
                if (node.trafficMode.isNotBlank() && node.trafficLimit > 0) {
                    add(if (node.trafficMode == "sum") "双向计费" else "单向计费")
                }
            }
            if (planBits.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = planBits.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (metrics?.let { it.memFraction } != null && metrics.swapTotal > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Swap " + formatBytes(metrics.swapUsed) + " / " + formatBytes(metrics.swapTotal) +
                        "  (" + formatPercent(metrics.swapFraction) + ")",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
