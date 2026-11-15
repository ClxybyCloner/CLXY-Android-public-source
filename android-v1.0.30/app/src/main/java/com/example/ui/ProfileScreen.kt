package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.core.Account
import com.example.core.AccountStatus
import com.example.core.ClxyUiState
import com.example.core.ClxyViewModel
import com.example.core.CountryNames
import com.example.ui.theme.ClxyColors

@Composable
fun ProfileScreen(
    viewModel: ClxyViewModel,
    uiState: ClxyUiState,
    modifier: Modifier = Modifier,
) {
    var showGeneral by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    var showRouting by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var showTun by remember { mutableStateOf(false) }
    var showBypassApps by remember { mutableStateOf(false) }
    var showRelay by remember { mutableStateOf(false) }
    var showTlsFragment by remember { mutableStateOf(false) }
    var showUpstreamProxy by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var relayTestUrl by remember { mutableStateOf<String?>(null) }

    val accounts = uiState.accounts?.accounts.orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ClxyBrandHeader(logoSize = 28.dp, showLabel = false)
                    Text(
                        text = "Profile",
                        color = ClxyColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "v${BuildConfig.VERSION_NAME} • Core v${uiState.status?.version?.ifEmpty { "1.0.18" } ?: "1.0.18"}",
                    color = ClxyColors.TextDim,
                    fontSize = 11.sp,
                )
            }
        }

        // CLXY Accounts Header & Quick Add Actions
        item(key = "accounts_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CLXY Accounts (${accounts.size})",
                    color = ClxyColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .testTag("profile_add_account")
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.openSignIn(forceForm = true) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add account", tint = ClxyColors.CyanGlow, modifier = Modifier.size(15.dp))
                    Text("Add Account", color = ClxyColors.CyanText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (accounts.isEmpty()) {
            item(key = "no_accounts") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No accounts configured", color = ClxyColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add one or more CLXY Accounts for automatic failover and encrypted browsing.",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.5.sp,
                    )
                }
            }
        } else {
            itemsIndexed(accounts, key = { _, acc -> acc.id }) { idx, acc ->
                AccountCard(
                    account = acc,
                    isBusy = uiState.busyAccountIds.contains(acc.id),
                    canMoveUp = idx > 0,
                    canMoveDown = idx < accounts.lastIndex,
                    onActivate = { viewModel.activateAccount(acc) },
                    onToggleEnabled = { viewModel.setAccountEnabled(acc, it) },
                    onRefreshQuota = { viewModel.refreshAccountQuota(acc) },
                    onReauth = { viewModel.openSignIn(email = acc.email, forceForm = true) },
                    onMoveUp = { viewModel.moveAccount(acc, -1) },
                    onMoveDown = { viewModel.moveAccount(acc, 1) },
                    onRemove = { viewModel.removeAccount(acc) },
                )
            }
        }

        item(key = "section_general") {
            Text(
                text = "General",
                color = ClxyColors.CyanText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item(key = "menu_general") {
            ProfileMenuCard(
                icon = Icons.Rounded.Tune,
                title = "General",
                subtitle = "Home IP badge, live notification speed, sign-in method & account switch %",
                tag = "profile_general_settings",
                onClick = { showGeneral = true },
            )
        }

        item(key = "menu_connection") {
            ProfileMenuCard(
                icon = Icons.Rounded.Computer,
                title = "Connection",
                subtitle = "H2/H3 transport, IPv4/IPv6, pool size, max connections, pinned server & LAN listen",
                tag = "profile_connection_settings",
                onClick = { showConnection = true },
            )
        }

        item(key = "menu_routing") {
            ProfileMenuCard(
                icon = Icons.AutoMirrored.Rounded.AltRoute,
                title = "Routing",
                subtitle = "Regional direct presets, AdBlock, GeoSite/GeoIP updates & route tester",
                tag = "profile_routing",
                onClick = { showRouting = true },
            )
        }

        item(key = "menu_dns") {
            ProfileMenuCard(
                icon = Icons.Rounded.Dns,
                title = "DNS",
                subtitle = "System, Cloudflare, Google, Quad9, custom DoH/DoT & live DNS check",
                tag = "profile_dns_settings",
                onClick = { showDns = true },
            )
        }

        item(key = "menu_tun") {
            ProfileMenuCard(
                icon = Icons.Rounded.Shield,
                title = "VPN settings",
                subtitle = "Per-app split tunneling, interface MTU, QUIC forwarding & UDP notices",
                tag = "profile_tun_settings",
                onClick = { showTun = true },
            )
        }

        item(key = "section_blocked") {
            Text(
                text = "If the service is blocked",
                color = ClxyColors.CyanText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        item(key = "menu_relay") {
            ProfileMenuCard(
                icon = Icons.Rounded.CloudQueue,
                title = "Relay server",
                subtitle = "Shared relay fallback, Personal Worker/PHP relay, ECH & control endpoint",
                tag = "profile_relay_settings",
                onClick = { showRelay = true },
            )
        }

        item(key = "menu_tls_fragment") {
            ProfileMenuCard(
                icon = Icons.Rounded.ContentCut,
                title = "TLS fragmentation",
                subtitle = "ClientHello split modes: Off, 1st Byte, Split SNI, or 2 TLS Records",
                tag = "profile_tls_fragment",
                onClick = { showTlsFragment = true },
            )
        }

        item(key = "menu_upstream_proxy") {
            ProfileMenuCard(
                icon = Icons.Rounded.Hub,
                title = "Upstream proxy",
                subtitle = "Chain connections through a local or remote SOCKS5 proxy",
                tag = "profile_upstream_proxy",
                onClick = { showUpstreamProxy = true },
            )
        }

        item(key = "section_app") {
            Text(
                text = "App",
                color = ClxyColors.CyanText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        item(key = "menu_advanced") {
            ProfileMenuCard(
                icon = Icons.Rounded.Build,
                title = "Advanced",
                subtitle = "Dashboard API bind, detailed logging, retry/restart core",
                tag = "profile_advanced_settings",
                onClick = { showAdvanced = true },
            )
        }

        item(key = "menu_logs") {
            ProfileMenuCard(
                icon = Icons.Rounded.ReceiptLong,
                title = "Logs",
                subtitle = "Live & total traffic, connection test, filtered core logs & socket table",
                tag = "profile_diagnostics",
                onClick = { showDiagnostics = true },
            )
        }

        item(key = "menu_check_update") {
            val update = uiState.availableUpdate
            ProfileMenuCard(
                icon = Icons.Rounded.SystemUpdate,
                title = "Check for Update",
                subtitle = when {
                    uiState.checkingForUpdate -> "Checking GitHub (ClxybyCloner/CLXY-Android)…"
                    update != null -> "Update available: ${update.latestTag} (Current v${BuildConfig.VERSION_NAME})"
                    else -> "Current v${BuildConfig.VERSION_NAME} • Check GitHub releases"
                },
                badgeText = if (update != null) "NEW" else null,
                tag = "profile_check_update",
                onClick = { viewModel.openUpdateDialog() },
            )
        }
    }

    if (showGeneral) GeneralSettingsDialog(viewModel = viewModel, uiState = uiState, onDismiss = { showGeneral = false })
    if (showConnection) ConnectionSettingsDialog(viewModel = viewModel, onDismiss = { showConnection = false })
    if (showRouting) RoutingSettingsDialog(viewModel = viewModel, onDismiss = { showRouting = false })
    if (showDns) DnsSettingsDialog(viewModel = viewModel, onDismiss = { showDns = false })
    if (showTun) {
        TunSettingsDialog(
            viewModel = viewModel,
            uiState = uiState,
            onDismiss = { showTun = false },
            onOpenBypassApps = { showBypassApps = true },
        )
    }
    if (showBypassApps) BypassAppsDialog(viewModel = viewModel, onDismiss = { showBypassApps = false })
    if (showRelay) {
        RelaySettingsDialog(
            viewModel = viewModel,
            onDismiss = { showRelay = false },
            onOpenRelayTest = { relayTestUrl = it },
        )
    }
    if (relayTestUrl != null) {
        RelayTestDialog(viewModel = viewModel, relayUrl = relayTestUrl!!, onDismiss = { relayTestUrl = null })
    }
    if (showTlsFragment) TlsFragmentDialog(viewModel = viewModel, onDismiss = { showTlsFragment = false })
    if (showUpstreamProxy) UpstreamProxyDialog(viewModel = viewModel, onDismiss = { showUpstreamProxy = false })
    if (showAdvanced) {
        AdvancedSettingsDialog(
            viewModel = viewModel,
            uiState = uiState,
            onDismiss = { showAdvanced = false },
        )
    }
    if (showDiagnostics) DiagnosticsLogsDialog(viewModel = viewModel, uiState = uiState, onDismiss = { showDiagnostics = false })
}

@Composable
private fun AccountCard(
    account: Account,
    isBusy: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onActivate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRefreshQuota: () -> Unit,
    onReauth: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val statusColor = when (account.status) {
        AccountStatus.READY -> ClxyColors.EmeraldGood
        AccountStatus.REAUTH_REQUIRED -> ClxyColors.RedDisconnectStart
        AccountStatus.RESERVED -> ClxyColors.AmberWarning
        else -> ClxyColors.TextMuted
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.CardSurface)
            .border(
                1.dp,
                if (account.active) ClxyColors.CyanBorder else ClxyColors.GlassBorder,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (account.active) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(ClxyColors.CyanGlow.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text("ACTIVE", color = ClxyColors.CyanText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = account.status.uppercase(),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = account.email.ifEmpty { account.name },
                    color = ClxyColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isBusy) {
                CircularProgressIndicator(color = ClxyColors.CyanGlow, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Switch(
                    checked = account.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = clxySwitchColors(),
                )
            }
        }

        val usage = account.usage
        if (usage != null) {
            val finiteQuota = !usage.unlimited && usage.limitBytes > 0L
            val pct = if (finiteQuota) {
                val computedPct = ((usage.usedBytes * 100.0) / usage.limitBytes).toFloat()
                val rawPct = if (account.usedPercent > 0.0) account.usedPercent.toFloat() else computedPct
                (rawPct / 100f).coerceIn(0f, 1f)
            } else {
                0f
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { if (usage.unlimited) 0.15f else pct },
                    color = if (pct > 0.9f) ClxyColors.RedDisconnectStart else ClxyColors.CyanGlow,
                    trackColor = ClxyColors.BarInactive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
                Text(
                    text = when {
                        usage.unlimited -> "Unlimited quota"
                        finiteQuota -> "${CountryNames.formatBytes(usage.usedBytes)} / ${CountryNames.formatBytes(usage.limitBytes)} (${(pct * 100).toInt()}%)"
                        else -> "Quota unknown until first use"
                    },
                    color = ClxyColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }

        if (account.reason.isNotEmpty()) {
            Text(CountryNames.brandAsClxy(account.reason), color = ClxyColors.AmberWarning, fontSize = 11.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!account.active && account.enabled) {
                    Text(
                        text = "Use Now",
                        color = ClxyColors.CyanText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onActivate)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
                if (account.needsSignIn) {
                    Text(
                        text = "Sign In",
                        color = ClxyColors.AmberWarning,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onReauth)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canMoveUp) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", tint = ClxyColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", tint = ClxyColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onRefreshQuota, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Quota", tint = ClxyColors.CyanText, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove Account", tint = ClxyColors.RedDisconnectStart, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.CardSurface)
            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.BlueDeep.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ClxyColors.CyanGlow, modifier = Modifier.size(19.dp))
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, color = ClxyColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    if (badgeText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(ClxyColors.CyanGlow.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(badgeText, color = ClxyColors.CyanText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = ClxyColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ClxyColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
