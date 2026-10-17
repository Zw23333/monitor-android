package dev.monitorprobe.client.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.monitorprobe.client.data.HubState
import dev.monitorprobe.client.data.Node
import dev.monitorprobe.client.format.formatBytes
import dev.monitorprobe.client.format.formatDays
import dev.monitorprobe.client.format.formatPercent
import dev.monitorprobe.client.format.formatRate
import dev.monitorprobe.client.format.relativeTime

/**
 * The whole app: the hub's own numbers first, then its nodes. That order is the
 * point — "主控信息" answers whether the thing the nodes report to is healthy
 * before any individual node's figures are worth reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    state: HubState,
    intervalSeconds: Int,
    powerSaving: Boolean,
    onRefresh: () -> Unit,
    onSetBaseUrl: (String) -> Unit,
    onLogin: (String) -> Unit,
    onLogout: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onOpenNode: (Node) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // The group selection, kept across rotation. The panel's encoding, so a group
    // literally named "all" or "none" cannot be confused with the two keywords.
    var groupFilter by rememberSaveable { mutableStateOf(GROUP_ALL) }

    // Computed out here rather than inside the LazyColumn: that lambda is a
    // LazyListScope, not a composable scope, so `remember` is not available in it.
    val groups = remember(state.nodes) { groupsOf(state.nodes) }
    val sections = remember(state.nodes, groupFilter) { sectionsOf(state.nodes, groupFilter) }

    // A hub with no address yet has nothing to show, so the dialog is the screen.
    if (!state.configured) {
        AddressDialog(
            initial = "",
            onDismiss = {},
            onConfirm = { onSetBaseUrl(it); showAddress = false },
            dismissible = false,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.settings?.siteName?.takeIf { it.isNotBlank() } ?: "monitor 主控",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.configured) {
                            Text(
                                text = when {
                                    state.error != null -> state.error
                                    powerSaving -> "省电模式：已放慢到 ${intervalSeconds.coerceAtLeast(60)} 秒"
                                    state.lastUpdated > 0 -> "更新于 " + relativeTime(state.lastUpdated)
                                    else -> "正在连接…"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.error != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).width(20.dp).height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            if (state.admin) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "菜单",
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("主控地址…") },
                            onClick = { menuOpen = false; showAddress = true },
                        )
                        if (state.admin) {
                            DropdownMenuItem(
                                text = { Text("退出登录") },
                                onClick = { menuOpen = false; onLogout() },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("管理员登录…") },
                                onClick = { menuOpen = false; showLogin = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("刷新间隔…") },
                            onClick = { menuOpen = false; showSettings = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        if (!state.configured) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HubInfoCard(state) }

            if (state.error != null) {
                item { ErrorCard(state.error) }
            }

            if (groups.isNotEmpty()) {
                item(key = "group-filter") {
                    GroupFilterRow(
                        groups = groups,
                        nodes = state.nodes,
                        selection = groupFilter,
                        onSelect = { groupFilter = it },
                    )
                }
            }

            if (state.nodes.isEmpty() && state.everLoaded) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(
                            text = "这个主控还没有节点。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (sections.isEmpty() && state.nodes.isNotEmpty()) {
                item(key = "empty-group") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(
                            text = "这个分组里没有节点。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            sections.forEach { section ->
                section.title?.let { title ->
                    item(key = "section:$title") { SectionHeader(title, section.nodes.size) }
                }
                // Keys are prefixed because they share one namespace across the whole
                // list, and a group named like a node id would otherwise collide.
                items(section.nodes, key = { "node:${it.id}" }) { node ->
                    NodeCard(node, onClick = { onOpenNode(node) })
                }
            }
        }
    }

    if (showAddress) {
        AddressDialog(
            initial = state.settings?.let { "" } ?: "",
            onDismiss = { showAddress = false },
            onConfirm = { onSetBaseUrl(it); showAddress = false },
            dismissible = true,
        )
    }
    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            onConfirm = { onLogin(it); showLogin = false },
        )
    }
    if (showSettings) {
        IntervalDialog(
            current = intervalSeconds,
            onDismiss = { showSettings = false },
            onConfirm = { onSetInterval(it); showSettings = false },
        )
    }
}

/** The 主控信息 card: the hub's own build, health and storage. */
@Composable
private fun HubInfoCard(state: HubState) {
    val versions = state.versions
    val db = state.db
    val metered = state.metered

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "主控",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (state.admin) "管理员会话" else "公开视图",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KeyValue("节点总数", state.nodes.size.toString(), Modifier.weight(1f))
                KeyValue(
                    "在线",
                    state.online.toString(),
                    Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                KeyValue(
                    "离线",
                    state.offline.toString(),
                    Modifier.weight(1f),
                    valueColor = if (state.offline > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KeyValue("总上行", formatRate(state.rollup.netTx), Modifier.weight(1f))
                KeyValue("总下行", formatRate(state.rollup.netRx), Modifier.weight(1f))
                KeyValue(
                    "本月已用",
                    if (state.rollup.monthLimit > 0) {
                        formatBytes(state.rollup.monthUsed) + " (" +
                            formatPercent((state.rollup.monthUsed.toDouble() / state.rollup.monthLimit).toFloat()) + ")"
                    } else formatBytes(state.rollup.monthUsed),
                    Modifier.weight(1f),
                )
            }

            if (metered.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                KeyValue(
                    "计费节点",
                    metered.joinToString("、", limit = 4, truncated = "…") { it.name } +
                        "（共 ${metered.size} 台）",
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("版本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KeyValue("主控", versions?.hub?.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "—", Modifier.weight(1f))
                KeyValue(
                    "主控最新",
                    versions?.hubLatest?.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "—",
                    Modifier.weight(1f),
                    valueColor = if (versions != null && versions.hubLatest.isNotBlank() && versions.hub != versions.hubLatest)
                        MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                KeyValue(
                    "Agent 最新",
                    versions?.agentLatest?.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "—",
                    Modifier.weight(1f),
                )
            }
            if (!state.admin) {
                Text(
                    text = "登录后可查看主控版本、数据库与设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (db != null) {
                Spacer(Modifier.height(14.dp))
                Text("数据库", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyValue("大小", formatBytes(db.size), Modifier.weight(1f))
                    KeyValue("WAL", formatBytes(db.wal), Modifier.weight(1f))
                    KeyValue("可回收", formatBytes(db.free), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyValue("保留", formatDays(db.retention), Modifier.weight(1f))
                    KeyValue(
                        "最早记录",
                        db.oldestAgo?.let { relativeTime(System.currentTimeMillis() - it * 1000) } ?: "—",
                        Modifier.weight(1f),
                    )
                    KeyValue("历史行数", (db.rows["metric"] ?: 0).toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyValue("节点行", (db.rows["node"] ?: 0).toString(), Modifier.weight(1f))
                    KeyValue("探针行", (db.rows["ping_record"] ?: 0).toString(), Modifier.weight(1f))
                    KeyValue("会话", (db.rows["session"] ?: 0).toString(), Modifier.weight(1f))
                }
            }

            state.settings?.let { settings ->
                Spacer(Modifier.height(14.dp))
                Text("设置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyValue("公开页", if (settings.publicPageOn) "开启" else "关闭", Modifier.weight(1f))
                    KeyValue("保留天数", settings.retentionDays.ifBlank { "—" }, Modifier.weight(1f))
                    KeyValue("主题", settings.theme.ifBlank { "—" }, Modifier.weight(1f))
                }
                if (settings.yourAddress.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    KeyValue("你的来源地址", settings.yourAddress)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * The group filter, as the panel's dropdown offers it: everything, each group in
 * use, and the ungrouped run — which only appears when there is one, since a chip
 * that can only ever show nothing is noise. Counts are on the chips because the
 * question a filter answers is "how many are in there".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFilterRow(
    groups: List<String>,
    nodes: List<Node>,
    selection: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selection == GROUP_ALL,
            onClick = { onSelect(GROUP_ALL) },
            label = { Text("全部 ${nodes.size}") },
        )
        groups.forEach { name ->
            val key = groupKey(name)
            FilterChip(
                selected = selection == key,
                onClick = { onSelect(key) },
                label = { Text("$name ${countFor(nodes, key)}") },
            )
        }
        val ungrouped = countFor(nodes, GROUP_NONE)
        if (ungrouped > 0) {
            FilterChip(
                selected = selection == GROUP_NONE,
                onClick = { onSelect(GROUP_NONE) },
                label = { Text("未分组 $ungrouped") },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddressDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    dismissible: Boolean,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        title = { Text("主控地址") },
        text = {
            Column {
                Text(
                    text = "填 hub 的地址，例如 https://monitor.example.com。局域网内用 http:// 也可以。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("地址") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = if (dismissible) {
            { TextButton(onClick = onDismiss) { Text("取消") } }
        } else null,
    )
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理员登录") },
        text = {
            Column {
                Text(
                    text = "用面板的应急密码登录，会话会保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) { Text("登录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun IntervalDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val choices = listOf(2, 5, 10, 30, 60, 120)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("刷新间隔") },
        text = {
            Column {
                Text(
                    text = "当前每 ${current} 秒拉取一次。间隔越短越费电。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                choices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { seconds ->
                            AssistChip(
                                onClick = { onConfirm(seconds) },
                                label = { Text(if (seconds == current) "✓ ${seconds}s" else "${seconds}s") },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
