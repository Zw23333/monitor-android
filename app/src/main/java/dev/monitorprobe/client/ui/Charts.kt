package dev.monitorprobe.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import dev.monitorprobe.client.data.PingPoint

/**
 * One probe's latency over the window: the median as a line, the min/max of each
 * bucket as a band behind it, and a vertical tick wherever the bucket lost a
 * packet. All three come from the hub's own row shape, so nothing is recomputed
 * here — in particular the band is the hub's `band`, not a min/max of the medians,
 * which would be a different (and wrong) thing.
 *
 * Gaps are drawn as gaps: a bucket that answered nothing has no `latency` and the
 * line restarts after it, rather than being bridged across an outage.
 */
@Composable
fun LatencyChart(
    points: List<PingPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val lossColor = MaterialTheme.colorScheme.error
    val bandColor = lineColor.copy(alpha = 0.18f)

    Box(modifier) {
        if (points.isEmpty() || points.all { it.latency == null }) {
            Text(
                text = "这段时间没有延迟数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val values = points.flatMap { listOfNotNull(it.low, it.latency, it.high) }.map { it.toFloat() }
            val minValue = values.minOrNull() ?: 0f
            val maxValue = values.maxOrNull() ?: 1f
            // A flat series has no span; give it one so the line lands mid-height
            // instead of dividing by zero.
            val span = (maxValue - minValue).takeIf { it > 0.001f } ?: 1f
            val count = points.size
            val dx = if (count > 1) size.width / (count - 1) else 0f
            fun x(index: Int) = dx * index
            fun y(value: Float) = size.height - (value - minValue) / span * size.height

            // The band first, so the median line draws over it.
            val banded = points.withIndex().filter { it.value.low != null && it.value.high != null }
            if (banded.size > 1) {
                val band = Path()
                banded.forEachIndexed { k, (index, point) ->
                    val py = y(point.high!!.toFloat())
                    if (k == 0) band.moveTo(x(index), py) else band.lineTo(x(index), py)
                }
                for (k in banded.indices.reversed()) {
                    val (index, point) = banded[k]
                    band.lineTo(x(index), y(point.low!!.toFloat()))
                }
                band.close()
                drawPath(band, bandColor)
            }

            val line = Path()
            var drawing = false
            points.forEachIndexed { index, point ->
                val value = point.latency?.toFloat()
                if (value == null) {
                    drawing = false
                } else if (!drawing) {
                    line.moveTo(x(index), y(value))
                    drawing = true
                } else {
                    line.lineTo(x(index), y(value))
                }
            }
            drawPath(
                line,
                lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Where a bucket lost packets, mark the whole column: loss is the thing
            // a latency line hides.
            points.forEachIndexed { index, point ->
                if ((point.loss ?: 0) > 0) {
                    drawLine(lossColor, Offset(x(index), 0f), Offset(x(index), size.height), 1.5.dp.toPx())
                }
            }
        }
    }
}

/** A single filled series, for the resource tabs. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    val fill = color.copy(alpha = 0.16f)
    Canvas(modifier) {
        val minValue = minOf(0f, values.minOrNull() ?: 0f)
        val maxValue = values.maxOrNull() ?: 1f
        val span = (maxValue - minValue).takeIf { it > 0.001f } ?: 1f
        val dx = size.width / (values.size - 1)
        fun x(index: Int) = dx * index
        fun y(value: Float) = size.height - (value - minValue) / span * size.height

        val line = Path()
        values.forEachIndexed { index, value ->
            if (index == 0) line.moveTo(x(index), y(value)) else line.lineTo(x(index), y(value))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(values.size - 1), size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(
            line,
            color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** A probe's colour in the legend, matching the order the hub ranked them. */
@Composable
fun LegendDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/** A labelled row of figures under a chart. */
@Composable
fun ChartFooter(vararg cells: Pair<String, String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEach { (label, value) ->
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}
