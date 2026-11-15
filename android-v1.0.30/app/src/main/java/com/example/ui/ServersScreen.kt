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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ClxyUiState
import com.example.core.CountryNames
import com.example.core.VpnLocation
import com.example.ui.theme.ClxyColors
import com.example.ui.theme.InterFontFamily

@Composable
fun ServersScreen(
    uiState: ClxyUiState,
    onSelectLocation: (String) -> Unit,
    onDisconnect: () -> Unit,
    onPingAll: () -> Unit,
    onRefreshLocations: () -> Unit,
    onCloseToHome: () -> Unit,
    modifier: Modifier = Modifier,
    onEnableSharedRelay: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (uiState.locations?.locations.isNullOrEmpty() && !uiState.locationsLoading && uiState.locationsError == null) {
            onRefreshLocations()
        }
    }

    val status = uiState.status
    val proxyOn = status?.proxyOn == true
    val activeSelection = status?.selection.orEmpty()
    val allLocations = uiState.locations?.locations.orEmpty()

    val recommendedLoc = allLocations.firstOrNull { it.recommended || CountryNames.isRecommendedPlace(it.code) }
    val filteredAuto = buildList {
        val recName = recommendedLoc?.let {
            if (it.city.isNotEmpty()) "${CountryNames.locationName(it.code, it.name)} · ${it.city}" else "Recommended"
        } ?: "Recommended"
        if ("clxy-auto.m1.fastly recommended auto fastest clxy-fast".contains(query.trim(), ignoreCase = true) ||
            recName.contains(query.trim(), ignoreCase = true) ||
            query.isBlank()
        ) {
            add(
                Triple(
                    "",
                    "CLXY-AUTO.M1.FASTLY · Recommended",
                    recommendedLoc?.pingMs ?: 0,
                ),
            )
            add(
                Triple(
                    "fastest",
                    "CLXY-FAST.M1.FASTLY · Fastest Location",
                    allLocations.filter { it.pingMs > 0 }.minOfOrNull { it.pingMs } ?: 0,
                ),
            )
        }
    }

    val countryLocations = remember(allLocations, query) {
        val q = query.trim().lowercase()
        allLocations
            .filter { !CountryNames.isRecommendedPlace(it.code) }
            .withIndex()
            .filter { (idx, loc) ->
                if (q.isEmpty()) true else {
                    val display = formatClxyServerTitle(loc, idx).lowercase()
                    val name = CountryNames.locationName(loc.code, loc.name).lowercase()
                    display.contains(q) || name.contains(q) || loc.city.lowercase().contains(q) || loc.code.lowercase().contains(q) || loc.server.lowercase().contains(q)
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header matching clxy-ui Screen 3: ClXY logo + "All Server" + Ping + Close X
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
                    text = "All Server",
                    color = ClxyColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Ping all button
                Row(
                    modifier = Modifier
                        .testTag("servers_ping_all_button")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(8.dp))
                        .clickable(enabled = !uiState.locationsPinging) { onPingAll() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (uiState.locationsPinging) {
                        CircularProgressIndicator(
                            color = ClxyColors.CyanGlow,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "Ping all servers",
                            tint = ClxyColors.CyanText,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = if (uiState.locationsPinging) "Pinging" else "Ping",
                        color = ClxyColors.CyanText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Box(
                    modifier = Modifier
                        .testTag("servers_close_button")
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCloseToHome),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Server List",
                        tint = ClxyColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Box matching clxy-ui Screen 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE60F172E))
                .border(1.dp, Color(0x99334155), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = ClxyColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search server here...",
                        color = ClxyColors.TextDim,
                        fontSize = 12.5.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    cursorBrush = SolidColor(ClxyColors.CyanGlow),
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        color = ClxyColors.TextPrimary,
                        fontSize = 12.5.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("servers_search_input"),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (uiState.locationsLoading && allLocations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ClxyColors.CyanGlow)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (filteredAuto.isNotEmpty()) {
                    item(key = "header_auto") {
                        Text(
                            text = "Automatic server",
                            color = ClxyColors.TextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    items(filteredAuto, key = { "auto_${it.first}" }) { (code, title, ping) ->
                        val isActive = proxyOn && activeSelection.equals(code, ignoreCase = true)
                        ServerListCard(
                            flagCode = if (code.isEmpty() || code == "fastest") "REC" else code,
                            title = title,
                            subtitle = buildString {
                                if (code.isEmpty() && recommendedLoc != null && recommendedLoc.name.isNotEmpty()) {
                                    append(CountryNames.locationName(recommendedLoc.code, recommendedLoc.name))
                                } else if (code == "fastest") {
                                    append("Lowest measured ping")
                                } else {
                                    append("Auto Route")
                                }
                                if (ping > 0) append(" • ${ping}ms") else append(" • Auto")
                            },
                            pingMs = ping,
                            isConnectedHere = isActive,
                            onAction = {
                                if (isActive) onDisconnect() else onSelectLocation(code)
                            },
                        )
                    }
                }

                if (countryLocations.isNotEmpty()) {
                    item(key = "header_free") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Global servers (${countryLocations.size})",
                            color = ClxyColors.TextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    items(countryLocations, key = { (_, loc) -> "loc_${loc.code}_${loc.city}" }) { (idx, loc) ->
                        val isActive = proxyOn && activeSelection.equals(loc.code, ignoreCase = true)
                        val title = formatClxyServerTitle(loc, idx)
                        val subtitle = buildString {
                            if (loc.city.isNotEmpty()) {
                                append(loc.city)
                            } else {
                                append(loc.code.uppercase())
                            }
                            if (loc.pingMs > 0) {
                                append(" • ${loc.pingMs}ms")
                            } else if (loc.pingError.isNotEmpty()) {
                                append(" • Timeout")
                            }
                        }
                        ServerListCard(
                            flagCode = loc.code,
                            title = title,
                            subtitle = subtitle,
                            pingMs = loc.pingMs,
                            isConnectedHere = isActive,
                            onAction = {
                                if (isActive) onDisconnect() else onSelectLocation(loc.code)
                            },
                        )
                    }
                } else if (uiState.locationsError != null) {
                    item(key = "err") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = uiState.locationsError,
                                color = ClxyColors.TextMuted,
                                fontSize = 12.sp,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .testTag("servers_retry_button")
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = onRefreshLocations)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Tap to retry",
                                        color = ClxyColors.CyanText,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (!uiState.sharedRelayEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .testTag("servers_enable_relay_button")
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xBF0F172A))
                                            .border(1.dp, Color(0x4738BDF8), RoundedCornerShape(8.dp))
                                            .clickable(onClick = onEnableSharedRelay)
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Enable Shared Relay & Retry",
                                            color = ClxyColors.CyanGlow,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (query.isNotBlank()) {
                                    "No matching servers found"
                                } else {
                                    "Could not load global servers yet. Retry or enable Shared Relay Pool if direct access is blocked."
                                },
                                color = ClxyColors.TextMuted,
                                fontSize = 12.sp,
                            )
                            if (query.isBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .testTag("servers_empty_retry_button")
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = onRefreshLocations)
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Retry",
                                            color = ClxyColors.CyanText,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    if (!uiState.sharedRelayEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .testTag("servers_empty_enable_relay_button")
                                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xBF0F172A))
                                                .border(1.dp, Color(0x4738BDF8), RoundedCornerShape(8.dp))
                                                .clickable(onClick = onEnableSharedRelay)
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "Enable Shared Relay",
                                                color = ClxyColors.CyanGlow,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
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
}

@Composable
private fun ServerListCard(
    flagCode: String,
    title: String,
    subtitle: String,
    pingMs: Int,
    isConnectedHere: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .testTag("server_card_${flagCode.ifEmpty { "auto" }}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.CardSurface)
            .border(
                1.dp,
                if (isConnectedHere) ClxyColors.CyanBorder else ClxyColors.GlassBorder,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onAction)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            CircularCountryFlag(countryCode = flagCode, size = 34.dp)
            Column {
                Text(
                    text = title,
                    color = ClxyColors.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = subtitle,
                        color = ClxyColors.TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SignalStrengthBars(pingMs = pingMs)
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 78.dp, minHeight = 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (isConnectedHere) {
                        Modifier
                            .background(ClxyDisconnectBrush)
                            .border(1.dp, Color(0x66FB7185), RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                            .background(Color(0xBF0F172A))
                            .border(1.dp, Color(0x4738BDF8), RoundedCornerShape(6.dp))
                    },
                )
                .clickable(onClick = onAction)
                .padding(horizontal = 13.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isConnectedHere) "Disconnect" else "Connect",
                color = if (isConnectedHere) Color.White else Color(0xFF38BDF8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatClxyServerTitle(loc: VpnLocation, index: Int): String {
    val hostDigits = if (loc.server.isNotEmpty() && !loc.server.first().isDigit()) {
        Regex("""(\d{1,3})""").find(loc.server)?.groupValues?.getOrNull(1)
    } else {
        null
    }
    val nodeNum = hostDigits ?: "${101 + index}"
    val countryName = CountryNames.locationName(loc.code, loc.name)
    return "CLXY${nodeNum}.M1.FASTLY · $countryName"
}
