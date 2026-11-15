package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.Account
import com.example.core.ClxyUiState
import com.example.core.ConnectionModeUi
import com.example.core.CountryNames
import com.example.core.EnginePhase
import com.example.core.PassInfo
import com.example.core.Usage
import com.example.ui.theme.ClxyColors

@Composable
fun HomeScreen(
    uiState: ClxyUiState,
    onToggleConnection: () -> Unit,
    onSelectMode: (ConnectionModeUi) -> Unit,
    onOpenServers: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = uiState.status
    val proxyOn = status?.proxyOn == true
    val phase = status?.state?.phase ?: EnginePhase.OFF
    val isConnecting = uiState.switchingProxy ||
        (proxyOn && phase == EnginePhase.STARTING) ||
        status?.connectPending == true ||
        (proxyOn && phase == EnginePhase.RUNNING && status?.confirmedBy(uiState.lastTest) != true && !uiState.coreStarting)
    val isConnected = proxyOn && (phase == EnginePhase.RUNNING) && !isConnecting
    val hasFailure = !proxyOn && (status?.connectFailure?.isNotEmpty() == true || phase == EnginePhase.ERROR)

    val statusColor by animateColorAsState(
        targetValue = when {
            isConnected -> ClxyColors.CyanText
            isConnecting -> ClxyColors.AmberWarning
            hasFailure -> ClxyColors.RedDisconnectStart
            else -> ClxyColors.TextDim
        },
        animationSpec = tween(350),
        label = "homeStatusColor",
    )
    val shieldColor by animateColorAsState(
        targetValue = when {
            isConnected -> ClxyColors.CyanGlow
            isConnecting -> ClxyColors.AmberWarning
            hasFailure -> ClxyColors.RedDisconnectStart
            else -> ClxyColors.TimerDim
        },
        animationSpec = tween(350),
        label = "homeShieldColor",
    )

    val statusLabel = when {
        uiState.coreStarting -> "Starting core (${uiState.connectionMode.label})"
        isConnected -> "Connection Secure • ${uiState.connectionMode.label}"
        status?.noNetwork == true && proxyOn -> "No Network (${uiState.connectionMode.label})"
        isConnecting -> "Connecting… • ${uiState.connectionMode.label}"
        status?.connectFailure?.isNotEmpty() == true -> "Connection Failed (${uiState.connectionMode.label})"
        else -> "Disconnected (${uiState.connectionMode.label})"
    }

    val (hrsStr, minSecStr) = CountryNames.formatTimerParts(
        if (proxyOn) uiState.elapsedSeconds else 0L,
    )
    val downMbps = if (proxyOn) CountryNames.formatMbpsComma(status?.traffic?.downRate ?: 0L) else "0,0"
    val upMbps = if (proxyOn) CountryNames.formatMbpsComma(status?.traffic?.upRate ?: 0L) else "0,0"

    val selectedCode = status?.selection?.takeIf { it.isNotEmpty() } ?: "REC"
    val activeExitCode = status?.exit?.countryCode?.takeIf { it.isNotEmpty() }
        ?: status?.proxy?.countryCode?.takeIf { it.isNotEmpty() }
        ?: selectedCode
    val serverDisplayName = when {
        isConnected || isConnecting -> {
            val city = status?.exit?.city?.takeIf { it.isNotEmpty() } ?: status?.proxy?.cityName.orEmpty()
            val cName = CountryNames.locationName(activeExitCode, status?.selectionName.orEmpty())
            if (city.isNotEmpty() && !cName.equals(city, ignoreCase = true)) "$cName · $city" else cName
        }
        else -> status?.selectionName?.takeIf { it.isNotEmpty() }
            ?: CountryNames.locationName(selectedCode)
    }

    val exitIp = when {
        isConnected && uiState.appSettings.showPublicIp && status?.exit?.ipv4?.isNotEmpty() == true -> status.exit.ipv4
        status?.proxy?.listen?.isNotEmpty() == true && uiState.connectionMode != ConnectionModeUi.VPN -> status.proxy.listen
        status?.proxy?.upstream?.isNotEmpty() == true -> status.proxy.upstream
        else -> if (selectedCode == "REC") "Auto Route" else selectedCode
    }
    val pingMs = uiState.lastTest?.responseMs?.takeIf { it > 0 && status?.isCurrentTest(uiState.lastTest) == true } ?: 0
    val pingLabel = if (pingMs > 0) "${pingMs}ms" else if (isConnected) "Online" else "Ready"

    val alertMessage: Pair<String, Boolean>? = when {
        uiState.unavailable != null ->
            "Core offline: ${CountryNames.brandAsClxy(uiState.unavailable.detail).ifEmpty { "restarting…" }}" to false
        status == null -> null
        status.connectFailure.isNotEmpty() -> CountryNames.describeFailure(status.connectFailure, "") to true
        status.state.phase == EnginePhase.WAITING && status.totalAccounts == 0 ->
            "Add a CLXY Account to connect" to false
        status.state.phase == EnginePhase.WAITING ->
            CountryNames.describeState(status.state, status.totalAccounts) to true
        status.state.phase == EnginePhase.ERROR ->
            CountryNames.describeState(status.state, status.totalAccounts) to true
        status.exitMismatch != null ->
            "Public IP is in ${CountryNames.locationName(status.exitMismatch.exitCountryCode)} instead of ${CountryNames.locationName(status.exitMismatch.chosenCountryCode)}" to true
        uiState.connectionMode != ConnectionModeUi.VPN && status.proxy?.listen?.isNotEmpty() == true ->
            "SOCKS5 proxy listening on ${status.proxy.listen}" to false
        else -> null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 680.dp
        val topPadding = if (maxHeight > 680.dp) {
            ((maxHeight - 620.dp) * 0.14f).coerceIn(12.dp, 28.dp)
        } else {
            8.dp
        }
        val sectionGap = if (compact) 6.dp else 10.dp
        val cardGap = if (compact) 6.dp else 10.dp
        val timerSizeSp = if (compact) 36.sp else 42.sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(topPadding))
            ClxyBrandHeader(
                logoSize = if (compact) 26.dp else 28.dp,
                labelSizeSp = if (compact) 14f else 15f,
            )
            Spacer(modifier = Modifier.height(sectionGap))

            // Shield + Status Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.testTag("home_status_row"),
            ) {
                ShieldIcon(color = shieldColor, sizeDp = 14f)
                AnimatedContent(
                    targetState = statusLabel,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                    label = "statusLabelAnim",
                ) { label ->
                    Text(
                        text = label,
                        color = statusColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Large Tabular Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("home_timer"),
            ) {
                Text(
                    text = hrsStr,
                    color = ClxyColors.TimerDim,
                    fontSize = timerSizeSp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = minSecStr,
                    color = ClxyColors.TextPrimary,
                    fontSize = timerSizeSp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Speed stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = downMbps,
                            color = ClxyColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " Mbp/s",
                            color = ClxyColors.TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Text("Download", color = ClxyColors.TextDim, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = upMbps,
                            color = ClxyColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " Mbp/s",
                            color = ClxyColors.TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Text("Upload", color = ClxyColors.TextDim, fontSize = 11.sp)
                }
            }

            // Flexible Dotted World Map + Main Power Button (adapts to viewport without scrolling)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val mapHeight = maxHeight.coerceAtMost(220.dp)
                val buttonDiameter = (maxHeight / 1.5f).coerceIn(72.dp, 132.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    DottedWorldMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_world_map"),
                        dotAlpha = if (isConnected || isConnecting) 0.54f else 0.44f,
                        animated = true,
                    )

                    CosmicPowerButton(
                        connected = isConnected,
                        connecting = isConnecting,
                        buttonSize = buttonDiameter,
                        onClick = onToggleConnection,
                    )
                }
            }

            RouteTunnelBox(
                connected = isConnected || isConnecting,
                originCode = uiState.deviceOriginCountryCode,
                originName = uiState.deviceOriginLabel,
                originIp = status?.exit?.ipv4?.takeIf { !isConnected } ?: uiState.deviceLocalIp,
                destCode = activeExitCode,
                destName = CountryNames.locationName(activeExitCode, serverDisplayName),
                destIp = exitIp,
            )

            Spacer(modifier = Modifier.height(cardGap))

            HomeDataUsageCard(
                accounts = uiState.accounts?.accounts.orEmpty(),
                activeAccount = status?.activeAccount,
                pass = status?.pass,
                trackedBytes = uiState.activeAccountTrackedBytes,
                modifier = Modifier.widthIn(max = 460.dp),
            )

            // Optional compact alert banner when account/diagnostic needs attention
            AnimatedVisibility(
                visible = alertMessage != null,
                enter = fadeIn(tween(240)) + expandVertically(),
                exit = fadeOut(tween(180)) + shrinkVertically(),
            ) {
                if (alertMessage != null) {
                    Column {
                        Spacer(modifier = Modifier.height(cardGap))
                        val (msg, isDiag) = alertMessage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 460.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xD90F172E))
                                .border(
                                    1.dp,
                                    if (hasFailure) ClxyColors.RedDisconnectStart.copy(alpha = 0.45f)
                                    else ClxyColors.AmberWarning.copy(alpha = 0.40f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { if (status?.totalAccounts == 0 && !isDiag) onOpenSignIn() else onOpenDiagnostics() }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = msg,
                                color = ClxyColors.TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (status?.totalAccounts == 0 && !isDiag) "Sign in" else "Details",
                                color = ClxyColors.CyanText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(cardGap))

            // 2-Pill Android Mode Selector (Proxy SOCKS5 | VPN All Device)
            ModePillGroup(
                selected = uiState.connectionMode,
                onSelect = onSelectMode,
                modifier = Modifier.widthIn(max = 460.dp),
            )

            Spacer(modifier = Modifier.height(cardGap))

            // Selected Server Card -> navigates to All Server
            Row(
                modifier = Modifier
                    .testTag("home_server_card")
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .defaultMinSize(minHeight = 60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClxyColors.CardSurface)
                    .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenServers)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    CircularCountryFlag(countryCode = activeExitCode, size = 34.dp)
                    Column {
                        Text(
                            text = serverDisplayName,
                            color = ClxyColors.TextPrimary,
                            fontSize = 14.sp,
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
                                text = if (exitIp.contains('.')) "IP $exitIp • $pingLabel" else "$exitIp • $pingLabel",
                                color = ClxyColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            SignalStrengthBars(pingMs = pingMs)
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Choose Server",
                    tint = ClxyColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
        }
    }
}

@Composable
private fun CosmicPowerButton(
    connected: Boolean,
    connecting: Boolean,
    buttonSize: Dp = 132.dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "powerPressScale",
    )

    val infinite = rememberInfiniteTransition(label = "powerPulse")
    val phase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase1",
    )
    val idleBreath by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idleBreath",
    )
    val phase2 = (phase1 + 0.5f) % 1f

    val glowPrimary by animateColorAsState(
        targetValue = when {
            connected -> ClxyColors.CyanGlow
            connecting -> ClxyColors.AmberWarning
            else -> ClxyColors.BluePrimary.copy(alpha = 0.5f)
        },
        animationSpec = tween(420),
        label = "glowPrimary",
    )
    val glowSecondary by animateColorAsState(
        targetValue = when {
            connected -> ClxyColors.PurplePrimary
            connecting -> ClxyColors.BluePrimary
            else -> ClxyColors.PurplePrimary.copy(alpha = 0.4f)
        },
        animationSpec = tween(420),
        label = "glowSecondary",
    )

    Box(
        modifier = Modifier
            .size(buttonSize * 1.46f)
            .scale(pressScale),
        contentAlignment = Alignment.Center,
    ) {
        if (connected || connecting) {
            listOf(phase1, phase2).forEach { p ->
                val ringScale = 0.96f + p * 0.42f
                val ringAlpha = (1f - p) * 0.52f
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    glowPrimary.copy(alpha = ringAlpha),
                                    glowSecondary.copy(alpha = ringAlpha),
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    glowPrimary.copy(alpha = ringAlpha * 0.25f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(buttonSize * 1.08f)
                    .scale(idleBreath)
                    .clip(CircleShape)
                    .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.16f), CircleShape),
            )
        }

        val coreStart by animateColorAsState(
            targetValue = when {
                connected -> ClxyColors.CyanBright
                connecting -> ClxyColors.AmberWarning
                else -> Color(0xFF1E293B)
            },
            animationSpec = tween(380),
            label = "coreStart",
        )
        val coreMid by animateColorAsState(
            targetValue = when {
                connected -> ClxyColors.BlueDeep
                connecting -> ClxyColors.BluePrimary
                else -> Color(0xFF0F172A)
            },
            animationSpec = tween(380),
            label = "coreMid",
        )
        val coreEnd by animateColorAsState(
            targetValue = when {
                connected -> ClxyColors.PurplePrimary
                connecting -> ClxyColors.PurplePrimary
                else -> Color(0xFF1E1B4B)
            },
            animationSpec = tween(380),
            label = "coreEnd",
        )
        val borderColor by animateColorAsState(
            targetValue = if (connected || connecting) Color(0x99BAE6FD) else Color(0x4794A3B8),
            animationSpec = tween(320),
            label = "powerBorder",
        )

        Box(
            modifier = Modifier
                .testTag("home_power_button")
                .size(buttonSize)
                .shadow(
                    elevation = if (connected) 28.dp else 14.dp,
                    shape = CircleShape,
                    ambientColor = if (connected) ClxyColors.CyanGlow else Color.Black,
                    spotColor = if (connected) ClxyColors.PurplePrimary else Color.Black,
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(coreStart, coreMid, coreEnd)))
                .border(1.5.dp, borderColor, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(buttonSize * 0.33f),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (connected) "Disconnect VPN" else "Connect VPN",
                    tint = Color.White,
                    modifier = Modifier.size(buttonSize * 0.35f),
                )
            }
        }
    }
}

@Composable
private fun RouteTunnelBox(
    connected: Boolean,
    originCode: String,
    originName: String,
    originIp: String,
    destCode: String,
    destName: String,
    destIp: String,
) {
    val infinite = rememberInfiniteTransition(label = "routeFlow")
    val dashOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -16f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "dashOffset",
    )
    val packetProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "packetProgress",
    )
    val boxBorder by animateColorAsState(
        targetValue = if (connected) Color(0x4D38BDF8) else Color(0x2294A3B8),
        animationSpec = tween(320),
        label = "routeBorder",
    )

    Row(
        modifier = Modifier
            .testTag("home_route_tunnel_box")
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.RouteBoxSurface)
            .border(1.dp, boxBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Origin node (flag placed adjacent to the arrow, symmetrical with destination)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
            modifier = Modifier
                .weight(1f)
                .testTag("home_route_origin"),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    text = originName,
                    color = ClxyColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = originIp,
                    color = ClxyColors.TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CircularCountryFlag(
                countryCode = originCode,
                size = 24.dp,
                modifier = Modifier.testTag("home_origin_flag"),
            )
        }

        // Animated dashed arrow + glowing packet bead when connected
        Canvas(
            modifier = Modifier
                .testTag("home_route_arrow")
                .padding(horizontal = 8.dp)
                .width(54.dp)
                .height(14.dp),
        ) {
            val cy = size.height / 2f
            val startX = 2.dp.toPx()
            val endX = size.width - 4.dp.toPx()
            val brush = if (connected) {
                Brush.horizontalGradient(listOf(ClxyColors.CyanGlow, ClxyColors.BluePrimary, Color(0xFFA855F7)))
            } else {
                Brush.horizontalGradient(listOf(ClxyColors.TimerDim, ClxyColors.TimerDim))
            }
            drawLine(
                brush = brush,
                start = Offset(startX, cy),
                end = Offset(endX, cy),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (connected) {
                    PathEffect.dashPathEffect(floatArrayOf(8f, 8f), dashOffset)
                } else {
                    PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                },
            )
            if (connected) {
                val beadX = startX + (endX - startX) * packetProgress
                drawCircle(
                    color = ClxyColors.CyanGlow.copy(alpha = 0.35f),
                    radius = 5.dp.toPx(),
                    center = Offset(beadX, cy),
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.2.dp.toPx(),
                    center = Offset(beadX, cy),
                )
            }
            val head = Path().apply {
                moveTo(endX - 5.dp.toPx(), cy - 4.dp.toPx())
                lineTo(endX, cy)
                lineTo(endX - 5.dp.toPx(), cy + 4.dp.toPx())
            }
            drawPath(
                head,
                color = if (connected) Color(0xFFA855F7) else ClxyColors.TimerDim,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Destination node
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f),
        ) {
            CircularCountryFlag(
                countryCode = destCode,
                size = 24.dp,
                modifier = Modifier.testTag("home_dest_flag"),
            )
            Column {
                Text(
                    text = destName,
                    color = ClxyColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = destIp,
                    color = if (connected) ClxyColors.CyanText else ClxyColors.TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ModePillGroup(
    selected: ConnectionModeUi,
    onSelect: (ConnectionModeUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xD10C1328))
            .border(1.dp, Color(0x2994A3B8), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionModeUi.entries.forEach { mode ->
            val active = mode == selected
            val pillScale by animateFloatAsState(
                targetValue = if (active) 1.0f else 0.98f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "modeScale_${mode.wire}",
            )
            val borderCol by animateColorAsState(
                targetValue = if (active) ClxyColors.CyanGlow.copy(alpha = 0.62f) else Color.Transparent,
                animationSpec = tween(240),
                label = "modeBorder_${mode.wire}",
            )
            val titleCol by animateColorAsState(
                targetValue = if (active) Color.White else ClxyColors.TextMuted,
                animationSpec = tween(220),
                label = "modeText_${mode.wire}",
            )
            Box(
                modifier = Modifier
                    .testTag("mode_pill_${mode.wire}")
                    .weight(1f)
                    .scale(pillScale)
                    .defaultMinSize(minHeight = 46.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (active) {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x3D00E5FF),
                                        Color(0x4D3B82F6),
                                        Color(0x4D9333EA),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .border(1.dp, borderCol, RoundedCornerShape(999.dp))
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = mode.label,
                        color = titleCol,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = mode.subtitle,
                        color = if (active) ClxyColors.CyanText else ClxyColors.TextDim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDataUsageCard(
    accounts: List<Account>,
    activeAccount: Account?,
    pass: PassInfo? = null,
    trackedBytes: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val listedPrimary = accounts.firstOrNull { it.active }
        ?: accounts.firstOrNull { it.enabled && it.usage != null }
        ?: accounts.firstOrNull { it.enabled }
        ?: accounts.firstOrNull { it.usage != null }
        ?: accounts.firstOrNull()

    val resolvedAccount = when {
        activeAccount == null -> listedPrimary
        listedPrimary == null -> activeAccount
        activeAccount.id.isNotEmpty() && listedPrimary.id.isNotEmpty() && activeAccount.id != listedPrimary.id ->
            activeAccount
        else -> {
            val sChecked = activeAccount.usage?.checkedAtMs ?: 0L
            val lChecked = listedPrimary.usage?.checkedAtMs ?: 0L
            when {
                activeAccount.usage == null && listedPrimary.usage != null -> listedPrimary
                listedPrimary.usage == null -> activeAccount
                lChecked > sChecked -> listedPrimary
                else -> activeAccount
            }
        }
    }

    val primaryUsage: Usage? = resolvedAccount?.usage ?: pass?.toFallbackUsage()
    val isUnlimited = primaryUsage?.unlimited == true
    val hasFinitePlan = !isUnlimited && primaryUsage != null && primaryUsage.limitBytes > 0L

    val limitBytes = if (hasFinitePlan) primaryUsage!!.limitBytes else 0L
    val serverRemainingBytes = if (hasFinitePlan) primaryUsage!!.remainingBytes.coerceIn(0L, limitBytes) else 0L
    val serverUsedBytes = if (hasFinitePlan) (limitBytes - serverRemainingBytes).coerceIn(0L, limitBytes) else 0L
    val safeTracked = trackedBytes.coerceAtLeast(0L)
    val usedBytes = when {
        hasFinitePlan -> maxOf(serverUsedBytes, safeTracked.coerceAtMost(limitBytes))
        isUnlimited -> safeTracked
        else -> safeTracked
    }
    val remainingBytes = if (hasFinitePlan) {
        (limitBytes - usedBytes).coerceIn(0L, limitBytes)
    } else {
        0L
    }
    val usedPct = if (hasFinitePlan && limitBytes > 0L) {
        ((usedBytes * 100.0) / limitBytes).toInt().coerceIn(0, 100)
    } else {
        0
    }

    val hasAnyAccount = resolvedAccount != null || accounts.isNotEmpty()
    val usedText = when {
        hasFinitePlan || isUnlimited -> CountryNames.formatBytes(usedBytes)
        usedBytes > 0L -> CountryNames.formatBytes(usedBytes)
        else -> "—"
    }
    val usedSubtitle = when {
        !hasAnyAccount -> "No active account"
        isUnlimited -> resolvedAccount?.email?.ifEmpty { "Unlimited account" } ?: "Unlimited account"
        hasFinitePlan -> {
            val label = resolvedAccount?.email?.ifEmpty { null } ?: "Active account"
            "$label • $usedPct% used"
        }
        else -> "Quota syncing…"
    }
    val remainingText = when {
        isUnlimited -> "∞ Unlimited"
        hasFinitePlan -> CountryNames.formatBytes(remainingBytes)
        hasAnyAccount -> "Pending"
        else -> "—"
    }
    val remainingSubtitle = when {
        isUnlimited -> "Unlimited plan"
        hasFinitePlan -> "of ${CountryNames.formatBytes(limitBytes)}"
        hasAnyAccount -> "Tap Refresh in Profile"
        else -> "Add account to track"
    }

    Row(
        modifier = modifier
            .testTag("home_data_usage_card")
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.RouteBoxSurface)
            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Account Data Used",
                color = ClxyColors.TextDim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = usedText,
                color = ClxyColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("home_data_used_value"),
            )
            Text(
                text = usedSubtitle,
                color = ClxyColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(Color(0x2E94A3B8)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "Remaining",
                color = ClxyColors.TextDim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = remainingText,
                color = ClxyColors.CyanText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("home_data_remaining_value"),
            )
            Text(
                text = remainingSubtitle,
                color = ClxyColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

