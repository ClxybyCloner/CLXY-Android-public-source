package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ClxyUiState
import com.example.core.ClxyViewModel
import com.example.core.CountryNames
import com.example.core.DnsTestHost
import com.example.core.JsonHelpers
import com.example.core.SignInMethod
import com.example.ui.theme.ClxyColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun GeneralSettingsDialog(
    viewModel: ClxyViewModel,
    uiState: ClxyUiState,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var thresholdPercent by remember { mutableStateOf("95") }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val acc = cfg.optJSONObject("accounts") ?: JSONObject()
            val v = acc.optDouble("switch_threshold_percent", 95.0)
            thresholdPercent = if (v > 0.0) {
                if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
            } else {
                "95"
            }
        }
        loading = false
    }

    fun saveThreshold() {
        val parsed = thresholdPercent.trim().toDoubleOrNull()
        if (parsed == null || parsed <= 0.0 || parsed > 100.0) {
            viewModel.showToast("Switch threshold must be between 1 and 100")
            return
        }
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val acc = cfg.optJSONObject("accounts") ?: JSONObject().also { cfg.put("accounts", it) }
                    acc.put("switch_threshold_percent", parsed)
                }
                viewModel.showToast("General settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save general settings")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("General", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingSwitchRow(
                            title = "Show exit public IP on Home",
                            subtitle = "Display verified exit IPv4 in the tunnel route box",
                            checked = uiState.appSettings.showPublicIp,
                            onCheckedChange = { viewModel.setShowPublicIp(it) },
                        )

                        SettingSwitchRow(
                            title = "Show live speed in notification",
                            subtitle = "Updates ongoing Android VPN notification every 2 seconds",
                            checked = uiState.showSpeedInNotification,
                            onCheckedChange = { viewModel.setShowSpeedInNotification(it) },
                        )

                        SettingSwitchRow(
                            title = "UDP game & voice notices",
                            subtitle = "Show an alert when an app's non-DNS UDP flow is refused",
                            checked = uiState.appSettings.udpNotice,
                            onCheckedChange = { viewModel.setUdpNotice(it) },
                        )

                        ChoiceRow(
                            title = "Account Sign-In Method",
                            options = listOf("form" to "Username & Password", "browser" to "In-App Browser"),
                            selected = uiState.appSettings.signInMethod.raw,
                            onSelect = { viewModel.setSignInMethod(SignInMethod.parse(it)) },
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Auto-Switch Account Threshold (%)",
                                color = ClxyColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Automatically rotates to the next account once monthly quota reaches this percentage.",
                                color = ClxyColors.TextMuted,
                                fontSize = 11.sp,
                            )
                            ChoiceRow(
                                title = "Quick presets",
                                options = listOf("80" to "80%", "90" to "90%", "95" to "95%", "99" to "99%"),
                                selected = thresholdPercent.trim(),
                                onSelect = { thresholdPercent = it },
                            )
                            OutlinedTextField(
                                value = thresholdPercent,
                                onValueChange = { thresholdPercent = it },
                                label = { Text("Custom threshold % (1–100)") },
                                singleLine = true,
                                colors = clxyTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("general_switch_threshold_input"),
                            )
                            Button(
                                onClick = { saveThreshold() },
                                enabled = !saving,
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("general_save_button"),
                            ) {
                                Text(if (saving) "Saving…" else "Save General Settings", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionSettingsDialog(
    viewModel: ClxyViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    var transport by remember { mutableStateOf("h2") }
    var ipVersion by remember { mutableStateOf("auto") }
    var upstreamConnections by remember { mutableStateOf("1") }
    var maxConnections by remember { mutableStateOf("256") }
    var pinnedServer by remember { mutableStateOf("") }
    var idleSleep by remember { mutableStateOf("0s") }
    var proxyListen by remember { mutableStateOf("127.0.0.1:10810") }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPass by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val proxy = cfg.optJSONObject("proxy") ?: JSONObject()
            transport = proxy.optString("transport", "h2").ifEmpty { "h2" }
            ipVersion = proxy.optString("ip_version", "auto").ifEmpty { "auto" }
            upstreamConnections = proxy.optInt("upstream_connections", 1).coerceAtLeast(1).toString()
            maxConnections = proxy.optInt("max_connections", 256).coerceAtLeast(0).toString()
            pinnedServer = proxy.optString("server", "")
            idleSleep = proxy.optString("idle_sleep", "0s").ifEmpty { "0s" }
            proxyListen = proxy.optString("listen", "127.0.0.1:10810")
            proxyUser = proxy.optString("username", "")
            proxyPass = proxy.optString("password", "")
        }
        loading = false
    }

    fun saveConnection() {
        val poolCount = upstreamConnections.trim().toIntOrNull()
        if (poolCount == null || poolCount < 1) {
            viewModel.showToast("Upstream connection pool must be at least 1")
            return
        }
        val maxConn = maxConnections.trim().toIntOrNull()
        if (maxConn == null || maxConn < 0) {
            viewModel.showToast("Max connections must be 0 (unlimited) or positive")
            return
        }
        val cleanUser = proxyUser.trim()
        if ((cleanUser.isEmpty()) != proxyPass.isEmpty()) {
            viewModel.showToast("Provide both proxy username and password, or leave both blank")
            return
        }

        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val proxy = cfg.optJSONObject("proxy") ?: JSONObject().also { cfg.put("proxy", it) }
                    proxy.put("transport", transport)
                    proxy.put("ip_version", ipVersion)
                    proxy.put("upstream_connections", poolCount)
                    proxy.put("max_connections", maxConn)
                    val srv = pinnedServer.trim()
                    proxy.put("server", srv)
                    if (srv.isNotEmpty()) {
                        // Go config.Validate() rejects setting both proxy.country and proxy.server.
                        proxy.put("country", "")
                    }
                    proxy.put("idle_sleep", idleSleep.trim().ifEmpty { "0s" })
                    proxy.put("listen", proxyListen.trim().ifEmpty { "127.0.0.1:10810" })
                    proxy.put("username", cleanUser)
                    proxy.put("password", proxyPass)
                }
                viewModel.showToast("Connection settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save connection settings")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Connection", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ChoiceRow(
                            title = "Proxy Transport Protocol",
                            options = listOf("h2" to "HTTP/2", "h3" to "HTTP/3 (QUIC)", "h1" to "HTTP/1.1"),
                            selected = transport,
                            onSelect = { transport = it },
                        )

                        ChoiceRow(
                            title = "Server IP Version",
                            options = listOf(
                                "auto" to "Auto",
                                "dual" to "Dual",
                                "ipv6_first" to "IPv6 1st",
                                "ipv4" to "IPv4",
                                "ipv6" to "IPv6",
                            ),
                            selected = ipVersion,
                            onSelect = { ipVersion = it },
                        )

                        ChoiceRow(
                            title = "Upstream Connections (Pool Size)",
                            options = listOf("1" to "1", "2" to "2", "4" to "4", "8" to "8"),
                            selected = upstreamConnections.trim(),
                            onSelect = { upstreamConnections = it },
                        )

                        OutlinedTextField(
                            value = upstreamConnections,
                            onValueChange = { upstreamConnections = it },
                            label = { Text("Custom upstream connections (>= 1)") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        ChoiceRow(
                            title = "Max Concurrent Connections (0 = Unlimited)",
                            options = listOf("0" to "Unlimited", "128" to "128", "256" to "256", "512" to "512"),
                            selected = maxConnections.trim(),
                            onSelect = { maxConnections = it },
                        )

                        OutlinedTextField(
                            value = maxConnections,
                            onValueChange = { maxConnections = it },
                            label = { Text("Custom max concurrent connections") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = pinnedServer,
                            onValueChange = { pinnedServer = it },
                            label = { Text("Pinned server hostname (optional; clears country)") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connection_pinned_server_input"),
                        )

                        ChoiceRow(
                            title = "Idle Sleep (Desktop/Proxy mode)",
                            options = listOf("0s" to "Never", "2m" to "2 min", "5m" to "5 min", "15m" to "15 min"),
                            selected = if (idleSleep == "0" || idleSleep.isEmpty()) "0s" else idleSleep,
                            onSelect = { idleSleep = it },
                        )

                        OutlinedTextField(
                            value = proxyListen,
                            onValueChange = { proxyListen = it },
                            label = { Text("Local SOCKS5/HTTP listen address") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        SettingSwitchRow(
                            title = "Allow LAN devices to connect",
                            subtitle = "Listen on 0.0.0.0 instead of 127.0.0.1 in Proxy mode",
                            checked = proxyListen.startsWith("0.0.0.0:"),
                            onCheckedChange = { lan ->
                                val port = proxyListen.substringAfterLast(':', "10810")
                                proxyListen = if (lan) "0.0.0.0:$port" else "127.0.0.1:$port"
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = proxyUser,
                                onValueChange = { proxyUser = it },
                                label = { Text("Proxy username") },
                                singleLine = true,
                                colors = clxyTextFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = proxyPass,
                                onValueChange = { proxyPass = it },
                                label = { Text("Proxy password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = clxyTextFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.restartProxy() },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("connection_restart_proxy_button"),
                            ) {
                                Text("Restart Proxy", color = ClxyColors.CyanText)
                            }
                            Button(
                                onClick = { saveConnection() },
                                enabled = !saving,
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("connection_save_button"),
                            ) {
                                Text(if (saving) "Saving…" else "Save Connection", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DnsSettingsDialog(
    viewModel: ClxyViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var dnsServersText by remember { mutableStateOf("") }
    var dnsFallback by remember { mutableStateOf(true) }
    var dnsResults by remember { mutableStateOf<List<DnsTestHost>>(emptyList()) }
    var testingDns by remember { mutableStateOf(false) }
    val privateDnsOn = remember { viewModel.isPrivateDnsActive() }

    val presets = listOf(
        "System" to "",
        "Auto DoH" to "https://1.1.1.1/dns-query\nhttps://8.8.8.8/dns-query\nhttps://9.9.9.9/dns-query",
        "Cloudflare" to "https://cloudflare-dns.com/dns-query\nhttps://1.1.1.1/dns-query",
        "Google" to "8.8.8.8\n8.8.4.4",
        "Quad9" to "9.9.9.9\n149.112.112.112",
        "CF DoT" to "tls://1.1.1.1\ntls://1.0.0.1",
    )

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val dns = cfg.optJSONObject("dns") ?: JSONObject()
            dnsServersText = JsonHelpers.strList(dns.optJSONArray("servers")).joinToString("\n")
            dnsFallback = dns.optBoolean("fallback_to_system", true)
        }
        loading = false
    }

    fun parseDnsLines(): List<String> =
        dnsServersText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun saveDns() {
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val dns = cfg.optJSONObject("dns") ?: JSONObject().also { cfg.put("dns", it) }
                    dns.put("servers", JSONArray(parseDnsLines()))
                    dns.put("fallback_to_system", dnsFallback)
                }
                viewModel.showToast("DNS settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save DNS settings")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("DNS", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (privateDnsOn) {
                            ErrorBanner("Android Private DNS is active in system settings and may override TUN DNS queries.")
                        }

                        Text("DNS Resolvers", color = ClxyColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            presets.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { (label, value) ->
                                        val isSelected = dnsServersText.trim() == value.trim()
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) ClxyColors.BlueDeep.copy(alpha = 0.50f) else ClxyColors.CardSurface)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) ClxyColors.CyanGlow else ClxyColors.GlassBorder,
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .clickable { dnsServersText = value }
                                                .padding(horizontal = 6.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else ClxyColors.TextSecondary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = dnsServersText,
                            onValueChange = { dnsServersText = it },
                            label = { Text("DNS servers (DoH https://, DoT tls://, or IP)") },
                            minLines = 3,
                            maxLines = 6,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dns_servers_input"),
                        )

                        SettingSwitchRow(
                            title = "Fallback to system DNS",
                            subtitle = "Use device resolver if all configured DoH/DoT servers fail",
                            checked = dnsFallback,
                            onCheckedChange = { dnsFallback = it },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        testingDns = true
                                        dnsResults = runCatching { viewModel.testDns(parseDnsLines()) }.getOrDefault(emptyList())
                                        testingDns = false
                                    }
                                },
                                enabled = !testingDns,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("dns_test_button"),
                            ) {
                                Text(if (testingDns) "Testing…" else "Test DNS", color = ClxyColors.CyanText)
                            }
                            Button(
                                onClick = { saveDns() },
                                enabled = !saving,
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("dns_save_button"),
                            ) {
                                Text(if (saving) "Saving…" else "Save DNS", color = Color.White)
                            }
                        }

                        if (dnsResults.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ClxyColors.CardSurface)
                                    .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("DNS Resolution Comparison", color = ClxyColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                dnsResults.forEach { host ->
                                    val safeHost = CountryNames.brandAsClxy(host.host)
                                    val safeRole = CountryNames.brandAsClxy(host.role)
                                    val safeSource = CountryNames.brandAsClxy(host.source.ifEmpty { "auto" })
                                    val safeError = CountryNames.brandAsClxy(host.error)
                                    val safeSystemError = CountryNames.brandAsClxy(host.systemError)
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(
                                            text = if (safeRole.isNotEmpty()) "$safeHost ($safeRole)" else safeHost,
                                            color = if (host.filtered) ClxyColors.RedDisconnectStart else ClxyColors.TextPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Configured ($safeSource ${host.ms}ms): ${host.addrs.joinToString().ifEmpty { safeError }}",
                                            color = ClxyColors.CyanText,
                                            fontSize = 10.5.sp,
                                        )
                                        Text(
                                            text = "System (${host.systemMs}ms): ${host.systemAddrs.joinToString().ifEmpty { safeSystemError }}${if (host.filtered) " [BLOCKED/FILTERED]" else ""}",
                                            color = if (host.filtered) ClxyColors.AmberWarning else ClxyColors.TextMuted,
                                            fontSize = 10.5.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TunSettingsDialog(
    viewModel: ClxyViewModel,
    uiState: ClxyUiState,
    onDismiss: () -> Unit,
    onOpenBypassApps: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var tunMtu by remember { mutableStateOf("0") }
    var tunQuic by remember { mutableStateOf(false) }
    val appRouting = remember { viewModel.loadAppRouting() }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val tun = cfg.optJSONObject("tun") ?: JSONObject()
            tunMtu = tun.optInt("mtu", 0).toString()
            tunQuic = tun.optBoolean("quic", false)
        }
        loading = false
    }

    fun saveTun() {
        val mtuVal = tunMtu.trim().toIntOrNull()
        if (mtuVal == null || (mtuVal != 0 && mtuVal !in 1280..9000)) {
            viewModel.showToast("TUN MTU must be 0 (default) or between 1280 and 9000")
            return
        }
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val tun = cfg.optJSONObject("tun") ?: JSONObject().also { cfg.put("tun", it) }
                    tun.put("mtu", mtuVal)
                    tun.put("quic", tunQuic)
                }
                viewModel.showToast("TUN settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save TUN settings")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("VPN settings", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val routingSummary = when {
                            appRouting.onlyChosen -> "Only ${appRouting.chosen.size} selected app(s) use TUN"
                            appRouting.chosen.isNotEmpty() -> "${appRouting.chosen.size} app(s) bypassed from TUN"
                            else -> "All apps currently route through CLXY VPN"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(12.dp))
                                .clickable { onOpenBypassApps() }
                                .padding(14.dp)
                                .testTag("tun_open_bypass_apps"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Per-App Split Tunneling",
                                color = ClxyColors.TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = routingSummary,
                                color = ClxyColors.CyanText,
                                fontSize = 11.5.sp,
                            )
                            Text(
                                text = "Tap to bypass or isolate specific Android apps",
                                color = ClxyColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }

                        ChoiceRow(
                            title = "TUN Interface MTU (0 = Default 1500)",
                            options = listOf("0" to "Auto", "1280" to "1280", "1400" to "1400", "1500" to "1500"),
                            selected = tunMtu.trim(),
                            onSelect = { tunMtu = it },
                        )

                        OutlinedTextField(
                            value = tunMtu,
                            onValueChange = { tunMtu = it },
                            label = { Text("Custom MTU (0 or 1280–9000)") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tun_mtu_input"),
                        )

                        SettingSwitchRow(
                            title = "Carry QUIC (UDP 443) in TUN",
                            subtitle = "Off by default so apps fall back cleanly to TCP over TLS",
                            checked = tunQuic,
                            onCheckedChange = { tunQuic = it },
                        )

                        SettingSwitchRow(
                            title = "Warn on refused UDP traffic",
                            subtitle = "Displays notice when non-DNS UDP packets are dropped by proxy server",
                            checked = uiState.appSettings.udpNotice,
                            onCheckedChange = { viewModel.setUdpNotice(it) },
                        )

                        Button(
                            onClick = { saveTun() },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("tun_save_button"),
                        ) {
                            Text(if (saving) "Saving…" else "Save TUN Settings", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelaySettingsDialog(
    viewModel: ClxyViewModel,
    onDismiss: () -> Unit,
    onOpenRelayTest: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    var sharedRelay by remember { mutableStateOf(false) }
    var apiRelayEnabled by remember { mutableStateOf(false) }
    var apiRelayUrl by remember { mutableStateOf("") }
    var echMode by remember { mutableStateOf("off") }
    var echConfig by remember { mutableStateOf("") }
    var guardianEndpoint by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val net = cfg.optJSONObject("network") ?: JSONObject()
            sharedRelay = net.optBoolean("shared_relay_enabled", false)
            apiRelayEnabled = net.optBoolean("api_relay_enabled", false)
            apiRelayUrl = net.optString("api_relay", "")
            echMode = net.optString("api_relay_ech", "off").ifEmpty { "off" }
            echConfig = net.optString("api_relay_ech_config", "")
            guardianEndpoint = net.optString("guardian_endpoint", "")
        }
        loading = false
    }

    fun saveRelay() {
        val url = apiRelayUrl.trim()
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            viewModel.showToast("Relay URL must start with https://")
            return
        }
        val g = guardianEndpoint.trim()
        if (g.isNotEmpty() && !g.startsWith("https://")) {
            viewModel.showToast("Control endpoint must start with https://")
            return
        }
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val net = cfg.optJSONObject("network") ?: JSONObject().also { cfg.put("network", it) }
                    net.put("shared_relay_enabled", sharedRelay)
                    net.put("api_relay_enabled", apiRelayEnabled && url.isNotEmpty())
                    net.put("api_relay", url)
                    net.put("api_relay_ech", echMode)
                    net.put("api_relay_ech_config", echConfig.trim())
                    net.put("guardian_endpoint", g)
                }
                viewModel.markRelayUserConfigured()
                viewModel.showToast("Relay settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save relay settings")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Relay server", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingSwitchRow(
                            title = "Shared Relay Pool",
                            subtitle = "Automatically fall back to shared relays when direct CLXY control-plane requests fail or time out (HTTP 451 / blocked network)",
                            checked = sharedRelay,
                            onCheckedChange = { sharedRelay = it },
                        )

                        SettingSwitchRow(
                            title = "Use Personal API Relay",
                            subtitle = "Route CLXY authentication and server list API calls via your Cloudflare Worker or PHP relay",
                            checked = apiRelayEnabled,
                            onCheckedChange = { apiRelayEnabled = it },
                        )

                        OutlinedTextField(
                            value = apiRelayUrl,
                            onValueChange = { apiRelayUrl = it },
                            label = { Text("Personal relay URL (https://…?secret=…)") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("relay_url_input"),
                        )

                        ChoiceRow(
                            title = "Relay ECH Mode",
                            options = listOf("on" to "Auto ECH", "require" to "Require ECH", "off" to "Off"),
                            selected = echMode,
                            onSelect = { echMode = it },
                        )

                        OutlinedTextField(
                            value = echConfig,
                            onValueChange = { echConfig = it },
                            label = { Text("ECH host override or base64 config (optional)") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = guardianEndpoint,
                            onValueChange = { guardianEndpoint = it },
                            label = { Text("Custom control API endpoint (optional https://...)") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("relay_guardian_endpoint_input"),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenRelayTest(apiRelayUrl.trim()) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("relay_test_button"),
                            ) {
                                Text("Test Relay", color = ClxyColors.CyanText)
                            }
                            Button(
                                onClick = { saveRelay() },
                                enabled = !saving,
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("relay_save_button"),
                            ) {
                                Text(if (saving) "Saving…" else "Save Relay", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TlsFragmentDialog(
    viewModel: ClxyViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var helloSplit by remember { mutableStateOf("off") }

    val modes = listOf(
        Triple("off", "Off", "Standard single-packet TLS ClientHello handshake."),
        Triple("first-byte", "1st Byte", "Splits ClientHello right after the first byte to evade stateful SNI inspection."),
        Triple("sni", "Split SNI", "Cuts the TLS ClientHello across the Server Name Indication extension boundary."),
        Triple("record", "2 TLS Records", "Fragments ClientHello into two separate TLS plaintext records."),
    )

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val net = cfg.optJSONObject("network") ?: JSONObject()
            helloSplit = net.optString("hello_split", "off").ifEmpty { "off" }
        }
        loading = false
    }

    fun saveTlsFragment() {
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val net = cfg.optJSONObject("network") ?: JSONObject().also { cfg.put("network", it) }
                    net.put("hello_split", helloSplit)
                }
                viewModel.showToast("TLS fragmentation mode saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save TLS fragmentation")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("TLS fragmentation", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Select how TLS ClientHello packets are fragmented when connecting directly or without a relay.",
                            color = ClxyColors.TextMuted,
                            fontSize = 12.sp,
                        )
                        modes.forEach { (key, title, desc) ->
                            val selected = helloSplit == key
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) ClxyColors.BlueDeep.copy(alpha = 0.45f) else ClxyColors.CardSurface)
                                    .border(
                                        1.dp,
                                        if (selected) ClxyColors.CyanGlow else ClxyColors.GlassBorder,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { helloSplit = key }
                                    .padding(14.dp)
                                    .testTag("tls_fragment_$key"),
                            ) {
                                Text(
                                    text = title,
                                    color = if (selected) Color.White else ClxyColors.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = desc,
                                    color = ClxyColors.TextMuted,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { saveTlsFragment() },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("tls_fragment_save_button"),
                        ) {
                            Text(if (saving) "Saving…" else "Save TLS Fragmentation", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpstreamProxyDialog(
    viewModel: ClxyViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    var upstreamEnabled by remember { mutableStateOf(false) }
    var upstreamAddr by remember { mutableStateOf("") }
    var upstreamUser by remember { mutableStateOf("") }
    var upstreamPass by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val net = cfg.optJSONObject("network") ?: JSONObject()
            upstreamEnabled = net.optBoolean("upstream_proxy_enabled", false)
            upstreamAddr = net.optString("upstream_proxy", "")
            upstreamUser = net.optString("upstream_proxy_username", "")
            upstreamPass = net.optString("upstream_proxy_password", "")
        }
        loading = false
    }

    fun saveUpstream() {
        val addr = upstreamAddr.trim()
        if (upstreamEnabled && addr.isEmpty()) {
            viewModel.showToast("Enter SOCKS5 host:port to enable upstream proxy")
            return
        }
        val user = upstreamUser.trim()
        if ((user.isEmpty()) != upstreamPass.isEmpty()) {
            viewModel.showToast("Provide both upstream username and password, or leave both blank")
            return
        }
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val net = cfg.optJSONObject("network") ?: JSONObject().also { cfg.put("network", it) }
                    net.put("upstream_proxy_enabled", upstreamEnabled && addr.isNotEmpty())
                    net.put("upstream_proxy", addr)
                    net.put("upstream_proxy_username", user)
                    net.put("upstream_proxy_password", upstreamPass)
                }
                viewModel.showToast("Upstream proxy settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save upstream proxy")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Upstream proxy", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingSwitchRow(
                            title = "Enable Upstream SOCKS5 Proxy",
                            subtitle = "Chain all CLXY core traffic through another local/remote SOCKS5 proxy",
                            checked = upstreamEnabled,
                            onCheckedChange = { upstreamEnabled = it },
                        )

                        OutlinedTextField(
                            value = upstreamAddr,
                            onValueChange = { upstreamAddr = it },
                            label = { Text("SOCKS5 address (host:port)") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upstream_addr_input"),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = upstreamUser,
                                onValueChange = { upstreamUser = it },
                                label = { Text("Username (optional)") },
                                singleLine = true,
                                colors = clxyTextFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = upstreamPass,
                                onValueChange = { upstreamPass = it },
                                label = { Text("Password (optional)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = clxyTextFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Button(
                            onClick = { saveUpstream() },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("upstream_save_button"),
                        ) {
                            Text(if (saving) "Saving…" else "Save Upstream Proxy", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsDialog(
    viewModel: ClxyViewModel,
    uiState: ClxyUiState,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    var dashboardEnabled by remember { mutableStateOf(true) }
    var dashboardListen by remember { mutableStateOf("127.0.0.1:0") }
    var verboseLogs by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = viewModel.loadSettings()
            val dash = cfg.optJSONObject("dashboard") ?: JSONObject()
            val log = cfg.optJSONObject("logging") ?: JSONObject()
            dashboardEnabled = dash.optBoolean("enabled", true)
            dashboardListen = dash.optString("listen", "127.0.0.1:0").ifEmpty { "127.0.0.1:0" }
            verboseLogs = log.optBoolean("verbose", false)
        }
        loading = false
    }

    fun saveAdvanced() {
        val listen = dashboardListen.trim().ifEmpty { "127.0.0.1:0" }
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val dash = cfg.optJSONObject("dashboard") ?: JSONObject().also { cfg.put("dashboard", it) }
                    dash.put("enabled", dashboardEnabled)
                    dash.put("listen", listen)
                    val log = cfg.optJSONObject("logging") ?: JSONObject().also { cfg.put("logging", it) }
                    log.put("verbose", verboseLogs)
                }
                viewModel.showToast("Advanced settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save advanced settings")
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Advanced", onDismiss)
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingSwitchRow(
                            title = "Detailed Connection Logs",
                            subtitle = "Record destination hostnames and handshake timing in core logs",
                            checked = verboseLogs,
                            onCheckedChange = { verboseLogs = it },
                        )

                        SettingSwitchRow(
                            title = "Local Dashboard Control Plane",
                            subtitle = "Keeps local HTTP dashboard API bound for app-to-core IPC",
                            checked = dashboardEnabled,
                            onCheckedChange = { dashboardEnabled = it },
                        )

                        OutlinedTextField(
                            value = dashboardListen,
                            onValueChange = { dashboardListen = it },
                            label = { Text("Dashboard API listen address") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Core Runtime Info", color = ClxyColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "Version: ${uiState.status?.version?.ifEmpty { "1.0.18" } ?: "1.0.18"}",
                                color = ClxyColors.TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = "Data directory: ${uiState.status?.dataDir.orEmpty().ifEmpty { "app internal storage" }}",
                                color = ClxyColors.TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.retryConnection() },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("advanced_retry_button"),
                            ) {
                                Text("Retry Connect", color = ClxyColors.CyanText)
                            }
                            OutlinedButton(
                                onClick = { viewModel.restartProxy() },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("advanced_restart_proxy_button"),
                            ) {
                                Text("Restart Proxy", color = ClxyColors.CyanText)
                            }
                        }

                        Button(
                            onClick = { saveAdvanced() },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("advanced_save_button"),
                        ) {
                            Text(if (saving) "Saving…" else "Save Advanced Settings", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
