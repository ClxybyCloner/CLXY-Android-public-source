package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ClxyViewModel
import com.example.core.CountryNames
import com.example.core.DestinationTest
import com.example.core.JsonHelpers
import com.example.core.RoutingInfo
import com.example.ui.theme.ClxyColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ADS_RULE = "geosite:category-ads-all"

@Composable
fun clxySwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = ClxyColors.BgTop,
    checkedTrackColor = ClxyColors.CyanGlow,
    uncheckedThumbColor = ClxyColors.TextMuted,
    uncheckedTrackColor = ClxyColors.BarInactive,
)

@Composable
fun RoutingSettingsDialog(
    viewModel: ClxyViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var routingInfo by remember { mutableStateOf<RoutingInfo?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf("") }
    var directText by remember { mutableStateOf("") }
    var blockText by remember { mutableStateOf("") }
    var resolveDomains by remember { mutableStateOf(false) }
    var geositeUrl by remember { mutableStateOf("") }
    var geoipUrl by remember { mutableStateOf("") }
    var updateInterval by remember { mutableStateOf("240h") }
    var showSyntaxHelp by remember { mutableStateOf(false) }
    var testTarget by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<DestinationTest?>(null) }
    var testingDest by remember { mutableStateOf(false) }
    var updatingLists by remember { mutableStateOf(false) }

    suspend fun reload() {
        val cfg = viewModel.loadSettings()
        val r = cfg.optJSONObject("routing") ?: JSONObject()
        enabled = r.optBoolean("enabled", false)
        region = r.optString("region", "")
        directText = JsonHelpers.strList(r.optJSONArray("direct")).joinToString("\n")
        blockText = JsonHelpers.strList(r.optJSONArray("block")).joinToString("\n")
        resolveDomains = r.optBoolean("resolve_domains", false)
        geositeUrl = r.optString("geosite_url", "")
        geoipUrl = r.optString("geoip_url", "")
        updateInterval = r.optString("update_interval", "240h").ifEmpty { "240h" }
        routingInfo = runCatching { viewModel.loadRouting() }.getOrNull()
        loading = false
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure { loading = false }
    }

    fun parseLines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun saveAll() {
        scope.launch {
            saving = true
            try {
                viewModel.updateSettings { cfg ->
                    val r = cfg.optJSONObject("routing") ?: JSONObject().also { cfg.put("routing", it) }
                    r.put("enabled", enabled)
                    r.put("region", region)
                    r.put("direct", JSONArray(parseLines(directText)))
                    r.put("block", JSONArray(parseLines(blockText)))
                    r.put("resolve_domains", resolveDomains)
                    r.put("geosite_url", geositeUrl.trim())
                    r.put("geoip_url", geoipUrl.trim())
                    r.put("update_interval", updateInterval)
                }
                routingInfo = runCatching { viewModel.loadRouting() }.getOrNull()
                viewModel.showToast("Routing settings saved")
            } catch (e: Exception) {
                viewModel.showToast(e.message ?: "Failed to save routing settings")
            } finally {
                saving = false
            }
        }
    }

    val blockAdsEnabled = remember(blockText) {
        parseLines(blockText).any { it.equals(ADS_RULE, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Routing & Split Rules", onDismiss)
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
                            title = "Enable Routing Engine",
                            subtitle = routingInfo?.summary?.takeIf { it.isNotEmpty() }?.let { CountryNames.brandAsClxy(it) }
                                ?: "Split direct / block / VPN traffic",
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                        )

                        SettingSwitchRow(
                            title = "Block Ads ($ADS_RULE)",
                            subtitle = "Adds or removes geosite ad blocking rule",
                            checked = blockAdsEnabled,
                            onCheckedChange = { wantAds ->
                                val lines = parseLines(blockText).filterNot { it.equals(ADS_RULE, ignoreCase = true) }
                                blockText = (if (wantAds) lines + ADS_RULE else lines).joinToString("\n")
                            },
                        )

                        Text("Regional Direct Preset", color = ClxyColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CountryNames.routingRegions.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { (code, label) ->
                                        val selected = region == code
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) ClxyColors.BlueDeep.copy(alpha = 0.45f) else ClxyColors.CardSurface)
                                                .border(
                                                    1.dp,
                                                    if (selected) ClxyColors.CyanGlow else ClxyColors.GlassBorder,
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .clickable {
                                                    region = code
                                                    if (code.isNotEmpty() && !enabled) enabled = true
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (selected) Color.White else ClxyColors.TextSecondary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        if (!routingInfo?.regionRules.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val presets = routingInfo?.regionRules.orEmpty()
                                    val merged = (presets + parseLines(directText)).distinct()
                                    directText = merged.joinToString("\n")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Restore region's preset rules", color = ClxyColors.CyanText, fontSize = 12.sp)
                            }
                        }

                        OutlinedTextField(
                            value = directText,
                            onValueChange = { directText = it },
                            label = { Text("Direct rules (one per line)") },
                            minLines = 3,
                            maxLines = 6,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = blockText,
                            onValueChange = { blockText = it },
                            label = { Text("Block rules (one per line)") },
                            minLines = 2,
                            maxLines = 5,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        SettingSwitchRow(
                            title = "Resolve domain names",
                            subtitle = "Resolves unmatched domains locally to apply GeoIP/CIDR rules",
                            checked = resolveDomains,
                            onCheckedChange = { resolveDomains = it },
                        )

                        OutlinedButton(
                            onClick = { showSyntaxHelp = !showSyntaxHelp },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("routing_syntax_toggle"),
                        ) {
                            Text(
                                text = if (showSyntaxHelp) "Hide Rule Syntax Guide" else "Show Rule Syntax Guide",
                                color = ClxyColors.CyanText,
                                fontSize = 12.sp,
                            )
                        }

                        if (showSyntaxHelp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ClxyColors.CardSurface)
                                    .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text("Supported Rule Syntax", color = ClxyColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                listOf(
                                    "example.com or domain:example.com — domain + subdomains",
                                    "full:exact.example.com — exact hostname match only",
                                    "keyword:tracker — substring match in hostname",
                                    "regexp:^.*\\.local$ — regular expression match",
                                    "192.168.0.0/16 or 1.1.1.1 — IP or CIDR range",
                                    "geosite:cn / geoip:ir — GeoSite category or GeoIP country",
                                ).forEach { line ->
                                    Text(line, color = ClxyColors.TextMuted, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        if (!routingInfo?.sources.isNullOrEmpty()) {
                            Text("Rule List Source Mirror", color = ClxyColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                routingInfo!!.sources.forEach { src ->
                                    val selected = geositeUrl.trim() == src.geositeUrl && geoipUrl.trim() == src.geoipUrl
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) ClxyColors.BlueDeep.copy(alpha = 0.50f) else ClxyColors.CardSurface)
                                            .border(
                                                1.dp,
                                                if (selected) ClxyColors.CyanGlow else ClxyColors.GlassBorder,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .clickable {
                                                geositeUrl = src.geositeUrl
                                                geoipUrl = src.geoipUrl
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = src.name,
                                            color = if (selected) Color.White else ClxyColors.TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }

                        ChoiceRow(
                            title = "Auto-Update Rule Lists",
                            options = listOf(
                                "0s" to "Manual",
                                "24h" to "Daily",
                                "72h" to "3 Days",
                                "240h" to "10 Days",
                                "720h" to "30 Days",
                            ),
                            selected = updateInterval,
                            onSelect = { updateInterval = it },
                        )

                        OutlinedTextField(
                            value = geositeUrl,
                            onValueChange = { geositeUrl = it },
                            label = { Text("GeoSite list URL") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = geoipUrl,
                            onValueChange = { geoipUrl = it },
                            label = { Text("GeoIP list URL") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (!routingInfo?.lists.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ClxyColors.CardSurface)
                                    .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Installed Rule Files", color = ClxyColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
                                routingInfo!!.lists.forEach { file ->
                                    val dateStr = file.updatedAtMs?.let { fmt.format(Date(it)) } ?: "bundled/pending"
                                    Text(
                                        text = "${file.name} • ${CountryNames.formatBytes(file.size)} • $dateStr",
                                        color = ClxyColors.TextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        updatingLists = true
                                        try {
                                            routingInfo = viewModel.updateRoutingLists()
                                            viewModel.showToast("Routing lists updated")
                                        } catch (e: Exception) {
                                            viewModel.showToast(e.message ?: "Update failed")
                                        } finally {
                                            updatingLists = false
                                        }
                                    }
                                },
                                enabled = !updatingLists,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(if (updatingLists) "Updating…" else "Update lists now", color = ClxyColors.CyanText, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { saveAll() },
                                enabled = !saving,
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(if (saving) "Saving…" else "Save routing", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        if (!routingInfo?.error.isNullOrEmpty()) {
                            ErrorBanner(CountryNames.brandAsClxy(routingInfo!!.error))
                        }

                        // Test Destination Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Test Destination Rule", color = ClxyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = testTarget,
                                    onValueChange = { testTarget = it },
                                    label = { Text("Domain, IP, or URL") },
                                    singleLine = true,
                                    colors = clxyTextFieldColors(),
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = {
                                        if (testTarget.isBlank()) return@Button
                                        scope.launch {
                                            testingDest = true
                                            testResult = runCatching { viewModel.testDestination(testTarget.trim()) }.getOrNull()
                                            testingDest = false
                                        }
                                    },
                                    enabled = !testingDest,
                                    colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                ) {
                                    Text(if (testingDest) "…" else "Test", color = Color.White)
                                }
                            }
                            testResult?.let { res ->
                                val safeHost = CountryNames.brandAsClxy(res.host)
                                val safeRule = CountryNames.brandAsClxy(res.rule).ifEmpty { "default" }
                                val safeNote = CountryNames.brandAsClxy(res.note)
                                Text(
                                    text = "$safeHost → ${res.route.uppercase()} ($safeRule)",
                                    color = ClxyColors.CyanText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (res.resolved.isNotEmpty() || safeNote.isNotEmpty()) {
                                    Text(
                                        text = listOf(res.resolved, safeNote).filter { it.isNotEmpty() }.joinToString(" • "),
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
}


@Composable
fun SheetHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClxyColors.ModalSurface)
            .border(0.5.dp, ClxyColors.GlassBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = ClxyColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ClxyColors.TextPrimary)
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.CardSurface)
            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = ClxyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = ClxyColors.TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = clxySwitchColors(),
        )
    }
}

@Composable
fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = ClxyColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (key, label) ->
                val isSelected = selected == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ClxyColors.BlueDeep.copy(alpha = 0.50f) else ClxyColors.CardSurface)
                        .border(1.dp, if (isSelected) ClxyColors.CyanGlow else ClxyColors.GlassBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelect(key) }
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else ClxyColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
