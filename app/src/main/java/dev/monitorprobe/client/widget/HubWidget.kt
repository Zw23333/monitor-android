package dev.monitorprobe.client.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.monitorprobe.client.MainActivity
import dev.monitorprobe.client.MonitorApp
import dev.monitorprobe.client.data.HubSummary
import dev.monitorprobe.client.format.formatBytes
import dev.monitorprobe.client.format.formatRate
import dev.monitorprobe.client.format.relativeTime
import kotlinx.serialization.json.Json

/**
 * The home-screen widget: the hub's own numbers, nothing else. Deliberately not
 * the node list — a widget is read at a glance, and "how many are up" is the one
 * question that answers itself in a glance. Tapping it opens the app on the full
 * list; tapping the refresh row asks the shared repository for a fresh frame.
 *
 * Colours come from `GlanceTheme`, which on Android 12 and later resolves to the
 * platform's wallpaper-derived palette — the same source the app's own theme uses,
 * so the widget and the app never disagree about what "primary" is.
 */
class HubWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = readSummary(context)
        provideContent { Content(summary) }
    }

    private fun readSummary(context: Context): HubSummary {
        val raw = MonitorApp.from(context).prefs.widgetCache
        if (raw.isBlank()) return HubSummary()
        return runCatching { widgetJson.decodeFromString(HubSummary.serializer(), raw) }
            .getOrElse { HubSummary() }
    }

    @Composable
    private fun Content(summary: HubSummary) {
        // Glance 1.1 has no reified `actionStartActivity<T>()`, so the Intent is
        // built here; `LocalContext` is the widget's own context, not an Activity's.
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.siteName.ifBlank { "monitor" },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    // `defaultWeight()` is a member extension of RowScope, so it is
                    // in scope inside the Row and needs no import.
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = if (summary.updatedAt == 0L) "—" else relativeTime(summary.updatedAt),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            if (summary.error != null) {
                Text(
                    text = summary.error,
                    style = TextStyle(color = GlanceTheme.colors.error, fontSize = 12.sp),
                    maxLines = 3,
                )
                return@Column
            }

            if (summary.total == 0) {
                Text(
                    text = "还没有数据，点开填写主控地址",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 2,
                )
                return@Column
            }

            // The headline: how many are up, and how many are not.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.online.toString(),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = " / ${summary.total} 在线",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
                if (summary.offline > 0) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "${summary.offline} 离线",
                        style = TextStyle(color = GlanceTheme.colors.error, fontSize = 13.sp),
                    )
                }
            }

            Spacer(GlanceModifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "↑ " + formatRate(summary.netTx),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    text = "↓ " + formatRate(summary.netRx),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }

            if (summary.monthLimit > 0) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = "本月 " + formatBytes(summary.monthUsed) + " / " + formatBytes(summary.monthLimit),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionRunCallback<RefreshAction>(actionParametersOf(RefreshAction.Force to true))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (summary.hubVersion.isBlank()) "点此刷新" else "v${summary.hubVersion} · 点此刷新",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Asks the shared repository for a fresh frame, then redraws every widget instance. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val app = MonitorApp.from(context)
        runCatching { app.repository.refresh() }
        runCatching { HubWidget().updateAll(context) }
    }

    companion object {
        val Force = ActionParameters.Key<Boolean>("force")
    }
}

class HubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HubWidget()
}

/**
 * One instance for the whole widget: the reader runs on every widget update, and a
 * fresh `Json` per call is pure overhead.
 */
private val widgetJson = Json { ignoreUnknownKeys = true }
