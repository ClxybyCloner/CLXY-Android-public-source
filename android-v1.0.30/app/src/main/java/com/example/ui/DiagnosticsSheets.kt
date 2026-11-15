package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ClxyUiState
import com.example.core.ClxyViewModel
import com.example.core.CountryNames
import com.example.core.LogEntry
import com.example.core.ProxyConnection
import com.example.core.RelayTestEvent
import com.example.core.RelayTestResult
import com.example.core.RelayTestStep
import com.example.ui.theme.ClxyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsLogsDialog(
    viewModel: ClxyViewModel,
    uiState: ClxyUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tabIndex by remember { mutableIntStateOf(0) }
    val logEntries = remember { mutableStateListOf<LogEntry>() }
    var lastLogId by remember { mutableLongStateOf(0L) }
    var clearedAfterId by remember { mutableLongStateOf(0L) }
    var levelFilter by remember { mutableStateOf("ALL") }
    var searchFilter by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    var detailedLogs by remember { mutableStateOf(false) }
    var connections by remember { mutableStateOf<List<ProxyConnection>>(emptyList()) }
    val logListState = rememberLazyListState()
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val log = cfg.optJSONObject("logging") ?: JSONObject()
            detailedLogs = log.optBoolean("verbose", false)
        }
    }

    LaunchedEffect(tabIndex) {
        while (isActive) {
            if (tabIndex == 0) {
                val fresh = runCatching { viewModel.loadLogs(lastLogId) }.getOrDefault(emptyList())
                if (fresh.isNotEmpty()) {
                    logEntries.addAll(fresh)
                    if (logEntries.size > 600) {
                        val removeCount = logEntries.size - 600
                        repeat(removeCount) { logEntries.removeAt(0) }
                    }
                    lastLogId = fresh.last().id
                }
            } else {
                connections = runCatching { viewModel.loadConnections() }.getOrDefault(connections)
            }
            delay(1_500L)
        }
    }

    val filteredLogs = remember(logEntries.size, lastLogId, clearedAfterId, levelFilter, searchFilter) {
        val q = searchFilter.trim().lowercase()
        logEntries.filter { entry ->
            if (entry.id <= clearedAfterId) return@filter false
            val lvl = entry.level.uppercase()
            val levelOk = when (levelFilter) {
                "INFO" -> lvl == "INFO"
                "WARN" -> lvl == "WARN" || lvl == "WARNING"
                "ERROR" -> lvl == "ERROR" || lvl == "ERR"
                else -> true
            }
            if (!levelOk) return@filter false
            val safeMsg = CountryNames.censorLogText(entry.message)
            q.isEmpty() || safeMsg.lowercase().contains(q) || lvl.lowercase().contains(q)
        }
    }

    LaunchedEffect(filteredLogs.size, autoScroll, tabIndex) {
        if (tabIndex == 0 && autoScroll && filteredLogs.isNotEmpty()) {
            logListState.scrollToItem(filteredLogs.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Logs", onDismiss)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Live traffic & Total traffic strip (matching desktop sidebar)
                    val traffic = uiState.status?.traffic
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("logs_traffic_summary"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live traffic", color = ClxyColors.TextDim, fontSize = 10.sp)
                            Text(
                                text = "↑ ${CountryNames.formatBytes(traffic?.upRate ?: 0L)}/s  ↓ ${CountryNames.formatBytes(traffic?.downRate ?: 0L)}/s",
                                color = ClxyColors.CyanText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Total traffic", color = ClxyColors.TextDim, fontSize = 10.sp)
                            Text(
                                text = "↑ ${CountryNames.formatBytes(traffic?.up ?: 0L)}  ↓ ${CountryNames.formatBytes(traffic?.down ?: 0L)}",
                                color = ClxyColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // Live Connection Test summary bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val t = uiState.lastTest
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = when {
                                    t == null -> "No connection test yet"
                                    t.ok -> "Verified • Total ${t.totalMs}ms (TLS ${t.tlsMs}ms)"
                                    else -> "Failed: ${CountryNames.describeFailure(t.errorCode, CountryNames.brandAsClxy(t.error))}"
                                },
                                color = when {
                                    t == null -> ClxyColors.TextMuted
                                    t.ok -> ClxyColors.EmeraldGood
                                    else -> ClxyColors.RedDisconnectStart
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (t != null && t.exitIpv4.isNotEmpty()) {
                                Text(
                                    text = "Exit ${t.exitIpv4} • ${CountryNames.locationName(t.exitCountry)} ${t.exitCity}",
                                    color = ClxyColors.TextMuted,
                                    fontSize = 10.5.sp,
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.runConnectionTest() },
                            enabled = !uiState.testingConnection,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                        ) {
                            Text(if (uiState.testingConnection) "Testing…" else "Test", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // Segmented Tab bar: Log vs Connections
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0 to "Log (${filteredLogs.size})", 1 to "Connections (${connections.size})").forEach { (idx, title) ->
                            val selected = tabIndex == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) ClxyColors.BlueDeep.copy(alpha = 0.55f) else Color.Transparent)
                                    .clickable { tabIndex = idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = title,
                                    color = if (selected) Color.White else ClxyColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                if (tabIndex == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Severity level chips: All | INFO | WARN | ERROR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("ALL" to "All", "INFO" to "INFO", "WARN" to "WARN", "ERROR" to "ERROR").forEach { (key, label) ->
                                val selected = levelFilter == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) ClxyColors.BlueDeep.copy(alpha = 0.50f) else ClxyColors.CardSurface)
                                        .border(
                                            1.dp,
                                            if (selected) ClxyColors.CyanGlow else ClxyColors.GlassBorder,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable { levelFilter = key }
                                        .testTag("logs_filter_${key.lowercase()}")
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else ClxyColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        // Search Filter field
                        OutlinedTextField(
                            value = searchFilter,
                            onValueChange = { searchFilter = it },
                            placeholder = { Text("Filter logs…") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("logs_search_input"),
                        )

                        // Auto-scroll, Detailed logs, Copy, Clear row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Auto-scroll", color = ClxyColors.TextSecondary, fontSize = 11.sp)
                                Switch(
                                    checked = autoScroll,
                                    onCheckedChange = { autoScroll = it },
                                    colors = clxySwitchColors(),
                                    modifier = Modifier.testTag("logs_autoscroll_switch"),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Detailed", color = ClxyColors.TextSecondary, fontSize = 11.sp)
                                Switch(
                                    checked = detailedLogs,
                                    onCheckedChange = { next ->
                                        detailedLogs = next
                                        scope.launch {
                                            runCatching {
                                                viewModel.updateSettings { cfg ->
                                                    val log = cfg.optJSONObject("logging") ?: JSONObject().also { cfg.put("logging", it) }
                                                    log.put("verbose", next)
                                                }
                                            }
                                        }
                                    },
                                    colors = clxySwitchColors(),
                                    modifier = Modifier.testTag("logs_verbose_switch"),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val text = filteredLogs.joinToString("\n") {
                                            val ts = it.timeMs?.let { t -> timeFmt.format(Date(t)) + " " } ?: ""
                                            "$ts[${it.level.uppercase()}] ${CountryNames.censorLogText(it.message)}"
                                        }
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("CLXY Logs", text))
                                        viewModel.showToast("Logs copied to clipboard")
                                    },
                                    modifier = Modifier.testTag("logs_copy_button"),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Logs", tint = ClxyColors.CyanText)
                                }
                                IconButton(
                                    onClick = { clearedAfterId = lastLogId },
                                    modifier = Modifier.testTag("logs_clear_button"),
                                ) {
                                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Logs", tint = ClxyColors.TextMuted)
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            val levelColor = when (entry.level.lowercase()) {
                                "error", "err" -> ClxyColors.RedDisconnectStart
                                "warn", "warning" -> ClxyColors.AmberWarning
                                else -> ClxyColors.CyanText
                            }
                            val ts = entry.timeMs?.let { timeFmt.format(Date(it)) } ?: ""
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClxyColors.CardSurface)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (ts.isNotEmpty()) {
                                        Text(
                                            text = ts,
                                            color = ClxyColors.TextDim,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    Text(
                                        text = entry.level.uppercase(),
                                        color = levelColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = CountryNames.censorLogText(entry.message),
                                    color = ClxyColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            scope.launch {
                                connections = runCatching { viewModel.clearConnections() }.getOrDefault(emptyList())
                            }
                        }) {
                            Text("Clear Closed", color = ClxyColors.CyanText, fontSize = 12.sp)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(connections.reversed(), key = { it.id }) { conn ->
                            val routeColor = when (conn.route.lowercase()) {
                                "vpn" -> ClxyColors.EmeraldGood
                                "direct" -> ClxyColors.CyanText
                                "blocked", "port", "failed" -> ClxyColors.RedDisconnectStart
                                else -> ClxyColors.TextMuted
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ClxyColors.CardSurface)
                                    .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = CountryNames.censorLogText(conn.target.ifEmpty { conn.client }),
                                        color = ClxyColors.TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${conn.route.uppercase()}${if (conn.open) " • OPEN" else ""}",
                                        color = routeColor,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${conn.inbound.uppercase()} ${conn.transport} • ↓ ${CountryNames.formatBytes(conn.down)}  ↑ ${CountryNames.formatBytes(conn.up)}",
                                    color = ClxyColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelayTestDialog(
    viewModel: ClxyViewModel,
    relayUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val steps = remember { mutableStateListOf<RelayTestStep>() }
    var result by remember { mutableStateOf<RelayTestResult?>(null) }
    var running by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(relayUrl) {
        steps.clear()
        result = null
        running = true
        streamError = null
        try {
            viewModel.testRelay(relayUrl).collect { event ->
                when (event) {
                    is RelayTestEvent.Step -> steps.add(event.step)
                    is RelayTestEvent.Result -> {
                        result = event.result
                        running = false
                    }
                }
            }
            running = false
        } catch (e: Exception) {
            streamError = e.message?.let { CountryNames.brandAsClxy(it) } ?: "Relay test failed"
            running = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ClxyColors.ModalSurface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Personal Relay Diagnostics",
                    color = ClxyColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        running -> "Streaming live DNS / TCP / ECH / TLS / Health probes…"
                        result?.ok == true -> "Relay ready (${result?.totalMs} ms)"
                        streamError != null -> CountryNames.brandAsClxy(streamError!!)
                        else -> "Relay probe failed (${result?.totalMs ?: 0} ms)"
                    },
                    color = when {
                        running -> ClxyColors.CyanText
                        result?.ok == true -> ClxyColors.EmeraldGood
                        else -> ClxyColors.RedDisconnectStart
                    },
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    steps.forEach { step ->
                        val color = when (step.status) {
                            "ok" -> ClxyColors.EmeraldGood
                            "warning" -> ClxyColors.AmberWarning
                            "error" -> ClxyColors.RedDisconnectStart
                            else -> ClxyColors.CyanText
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClxyColors.CardSurface)
                                .padding(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${CountryNames.brandAsClxy(step.stage).uppercase()}${if (step.code.isNotEmpty()) " (${CountryNames.brandAsClxy(step.code)})" else ""}",
                                    color = color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("${step.elapsedMs}ms", color = ClxyColors.TextDim, fontSize = 10.5.sp)
                            }
                            if (step.detail.isNotEmpty()) {
                                Text(CountryNames.brandAsClxy(step.detail), color = ClxyColors.TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }
                    if (running) {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ClxyColors.CyanGlow, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = {
                        val report = steps.joinToString("\n") {
                            "[${it.elapsedMs}ms] ${CountryNames.brandAsClxy(it.stage)} (${it.status}/${CountryNames.brandAsClxy(it.code)}): ${CountryNames.brandAsClxy(it.detail)}"
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("CLXY Relay Report", report))
                        viewModel.showToast("Relay report copied")
                    }) {
                        Text("Copy Report", color = ClxyColors.CyanText, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }
}
