package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.MainTab
import com.example.ui.theme.ClxyColors
import com.example.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val ClxyBrandGradient = Brush.linearGradient(
    colors = listOf(ClxyColors.CyanBright, Color(0xFF60A5FA), ClxyColors.PinkAccent),
)

val ClxyPrimaryButtonBrush = Brush.linearGradient(
    colors = listOf(ClxyColors.CyanGlow, ClxyColors.BluePrimary, ClxyColors.PurplePrimary),
)

val ClxyDisconnectBrush = Brush.linearGradient(
    colors = listOf(ClxyColors.RedDisconnectStart, ClxyColors.RedDisconnectEnd),
)

@Composable
fun ClxyCosmicBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ClxyColors.BgTop, ClxyColors.BgMid, ClxyColors.BgBottom),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClxyColors.CyanGlow.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.20f),
                    radius = size.width * 0.65f,
                ),
                center = Offset(size.width * 0.18f, size.height * 0.20f),
                radius = size.width * 0.65f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClxyColors.PurplePrimary.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.84f, size.height * 0.26f),
                    radius = size.width * 0.68f,
                ),
                center = Offset(size.width * 0.84f, size.height * 0.26f),
                radius = size.width * 0.68f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClxyColors.BluePrimary.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * 0.50f, size.height * 0.86f),
                    radius = size.width * 0.72f,
                ),
                center = Offset(size.width * 0.50f, size.height * 0.86f),
                radius = size.width * 0.72f,
            )
        }
        content()
    }
}

@Composable
fun ClxyBrandHeader(
    modifier: Modifier = Modifier,
    logoSize: Dp = 30.dp,
    showLabel: Boolean = true,
    labelSizeSp: Float = 15f,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.clxy_logo),
            contentDescription = "CLXY logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = logoSize, height = logoSize * 0.75f),
        )
        if (showLabel) {
            Text(
                text = "ClXY",
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSizeSp.sp,
                    letterSpacing = 0.8.sp,
                    brush = ClxyBrandGradient,
                ),
            )
        }
    }
}

@Composable
fun DottedWorldMap(
    modifier: Modifier = Modifier,
    dotAlpha: Float = 0.42f,
    animated: Boolean = true,
) {
    val wavePhase = if (animated) {
        val infinite = rememberInfiniteTransition(label = "worldMapWave")
        val phase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wavePhase",
        )
        phase
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val step = (w / 58f).coerceIn(4.5.dp.toPx(), 7.dp.toPx())
        val radius = step * 0.24f

        fun ellipse(nx: Float, ny: Float, cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
            val dx = (nx - cx) / rx
            val dy = (ny - cy) / ry
            return dx * dx + dy * dy <= 1f
        }

        fun insideContinent(nx: Float, ny: Float): Boolean {
            // Alaska & Canada / North America
            val alaska = ellipse(nx, ny, 0.09f, 0.21f, 0.055f, 0.055f)
            val canadaUs = ellipse(nx, ny, 0.20f, 0.28f, 0.115f, 0.125f)
            val mexico = ellipse(nx, ny, 0.21f, 0.43f, 0.045f, 0.065f)
            val greenland = ellipse(nx, ny, 0.34f, 0.13f, 0.048f, 0.055f)
            // South America
            val southAmNorth = ellipse(nx, ny, 0.275f, 0.59f, 0.065f, 0.095f)
            val southAmSouth = ellipse(nx, ny, 0.265f, 0.74f, 0.036f, 0.120f)
            // Europe & UK & Scandinavia
            val uk = ellipse(nx, ny, 0.435f, 0.25f, 0.018f, 0.030f)
            val europe = ellipse(nx, ny, 0.50f, 0.27f, 0.065f, 0.075f)
            val scandi = ellipse(nx, ny, 0.515f, 0.175f, 0.040f, 0.048f)
            // Africa & Madagascar
            val africaNorth = ellipse(nx, ny, 0.505f, 0.465f, 0.078f, 0.095f)
            val africaSouth = ellipse(nx, ny, 0.530f, 0.625f, 0.048f, 0.125f)
            val madagascar = ellipse(nx, ny, 0.598f, 0.665f, 0.014f, 0.045f)
            // Middle East & Eurasia & India
            val middleEast = ellipse(nx, ny, 0.595f, 0.395f, 0.045f, 0.058f)
            val siberiaWest = ellipse(nx, ny, 0.66f, 0.215f, 0.115f, 0.095f)
            val eastAsia = ellipse(nx, ny, 0.76f, 0.32f, 0.115f, 0.120f)
            val india = ellipse(nx, ny, 0.695f, 0.455f, 0.040f, 0.075f)
            val indochina = ellipse(nx, ny, 0.775f, 0.475f, 0.035f, 0.065f)
            // Japan, Indonesia, Australia, New Zealand
            val japan = ellipse(nx, ny, 0.895f, 0.305f, 0.018f, 0.055f)
            val indonesia = ellipse(nx, ny, 0.815f, 0.575f, 0.065f, 0.025f)
            val australia = ellipse(nx, ny, 0.845f, 0.725f, 0.065f, 0.088f)
            val newZealand = ellipse(nx, ny, 0.942f, 0.810f, 0.015f, 0.042f)

            return alaska || canadaUs || mexico || greenland ||
                southAmNorth || southAmSouth ||
                uk || europe || scandi ||
                africaNorth || africaSouth || madagascar ||
                middleEast || siberiaWest || eastAsia || india || indochina ||
                japan || indonesia || australia || newZealand
        }

        // Soft cosmic halo behind globe silhouette
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ClxyColors.CyanGlow.copy(alpha = dotAlpha * 0.24f),
                    ClxyColors.BluePrimary.copy(alpha = dotAlpha * 0.12f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.48f),
                radius = w * 0.46f,
            ),
            center = Offset(w * 0.5f, h * 0.48f),
            radius = w * 0.46f,
        )

        var y = step * 0.5f
        while (y < h) {
            var x = step * 0.5f
            while (x < w) {
                val nx = x / w
                val ny = y / h
                if (insideContinent(nx, ny)) {
                    val shimmer = if (animated) {
                        0.82f + 0.18f * sin(wavePhase + nx * 7.5f + ny * 4.5f)
                    } else {
                        1f
                    }
                    val a = (dotAlpha * shimmer).coerceIn(0.12f, 0.95f)
                    val color = when {
                        nx < 0.36f -> ClxyColors.CyanGlow.copy(alpha = a)
                        nx < 0.68f -> Color(0xFF60A5FA).copy(alpha = a)
                        else -> Color(0xFFC084FC).copy(alpha = (a + 0.06f).coerceAtMost(1f))
                    }
                    drawCircle(color = color, radius = radius, center = Offset(x, y))
                }
                x += step
            }
            y += step
        }
    }
}

@Composable
fun CircularCountryFlag(
    countryCode: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val code = countryCode.trim().uppercase(Locale.US)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }
            clipPath(circle) {
                when (code) {
                    "", "REC", "AUTO" -> {
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF3B82F6), Color(0xFF9333EA)),
                            ),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = w * 0.32f,
                            style = Stroke(width = w * 0.06f),
                        )
                        drawCircle(color = Color.White, radius = w * 0.12f)
                    }
                    "US" -> {
                        val stripeH = h / 13f
                        for (i in 0 until 13) {
                            drawRect(
                                color = if (i % 2 == 0) Color(0xFFB22234) else Color.White,
                                topLeft = Offset(0f, i * stripeH),
                                size = Size(w, stripeH + 0.5f),
                            )
                        }
                        drawRect(color = Color(0xFF3C3B6E), size = Size(w * 0.46f, h * 0.54f))
                        for (row in 0..2) {
                            for (col in 0..2) {
                                drawCircle(
                                    color = Color.White,
                                    radius = w * 0.028f,
                                    center = Offset(w * (0.10f + col * 0.13f), h * (0.12f + row * 0.14f)),
                                )
                            }
                        }
                    }
                    "IR" -> {
                        drawRect(color = Color(0xFF239F40), topLeft = Offset.Zero, size = Size(w, h / 3f))
                        drawRect(color = Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                        drawRect(color = Color(0xFFDA0000), topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f))
                        drawCircle(color = Color(0xFFDA0000), radius = w * 0.08f, center = Offset(w / 2f, h / 2f))
                    }
                    "FR" -> triVertical(Color(0xFF002654), Color.White, Color(0xFFED2939))
                    "IT" -> triVertical(Color(0xFF009246), Color.White, Color(0xFFCE2B37))
                    "BE" -> triVertical(Color(0xFF111827), Color(0xFFFDDA24), Color(0xFFEF3340))
                    "IE" -> triVertical(Color(0xFF169B62), Color.White, Color(0xFFFF883E))
                    "RO" -> triVertical(Color(0xFF002B7F), Color(0xFFFCD116), Color(0xFFCE1126))
                    "DE" -> triHorizontal(Color(0xFF111111), Color(0xFFDD0000), Color(0xFFFFCE00))
                    "NL" -> triHorizontal(Color(0xFFAE1C28), Color.White, Color(0xFF21468B))
                    "HU" -> triHorizontal(Color(0xFFCD2A3E), Color.White, Color(0xFF436F4D))
                    "AT" -> triHorizontal(Color(0xFFED2939), Color.White, Color(0xFFED2939))
                    "RU" -> triHorizontal(Color.White, Color(0xFF0039A6), Color(0xFFD52B1E))
                    "BG" -> triHorizontal(Color.White, Color(0xFF00966E), Color(0xFFD62612))
                    "EE" -> triHorizontal(Color(0xFF0072CE), Color(0xFF111111), Color.White)
                    "LT" -> triHorizontal(Color(0xFFFDB913), Color(0xFF006A44), Color(0xFFC1272D))
                    "LU" -> triHorizontal(Color(0xFFED2939), Color.White, Color(0xFF00A1DE))
                    "ES" -> {
                        drawRect(color = Color(0xFFAA151B), topLeft = Offset.Zero, size = Size(w, h * 0.25f))
                        drawRect(color = Color(0xFFF1BF00), topLeft = Offset(0f, h * 0.25f), size = Size(w, h * 0.50f))
                        drawRect(color = Color(0xFFAA151B), topLeft = Offset(0f, h * 0.75f), size = Size(w, h * 0.25f))
                    }
                    "UA" -> {
                        drawRect(color = Color(0xFF0057B7), topLeft = Offset.Zero, size = Size(w, h * 0.5f))
                        drawRect(color = Color(0xFFFFD700), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                    }
                    "PL" -> {
                        drawRect(color = Color.White, topLeft = Offset.Zero, size = Size(w, h * 0.5f))
                        drawRect(color = Color(0xFFDC143C), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                    }
                    "ID" -> {
                        drawRect(color = Color(0xFFFF0000), topLeft = Offset.Zero, size = Size(w, h * 0.5f))
                        drawRect(color = Color.White, topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                    }
                    "SG" -> {
                        drawRect(color = Color(0xFFEF3340), topLeft = Offset.Zero, size = Size(w, h * 0.5f))
                        drawRect(color = Color.White, topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                        drawCircle(color = Color.White, radius = w * 0.14f, center = Offset(w * 0.30f, h * 0.26f))
                        drawCircle(color = Color(0xFFEF3340), radius = w * 0.12f, center = Offset(w * 0.35f, h * 0.26f))
                    }
                    "JP" -> {
                        drawRect(color = Color.White)
                        drawCircle(color = Color(0xFFBC002D), radius = w * 0.28f)
                    }
                    "CH" -> {
                        drawRect(color = Color(0xFFD52B1E))
                        drawRect(color = Color.White, topLeft = Offset(w * 0.40f, h * 0.20f), size = Size(w * 0.20f, h * 0.60f))
                        drawRect(color = Color.White, topLeft = Offset(w * 0.20f, h * 0.40f), size = Size(w * 0.60f, h * 0.20f))
                    }
                    "SE" -> nordicCross(Color(0xFF006AA7), Color(0xFFFECC00))
                    "FI" -> nordicCross(Color.White, Color(0xFF003580))
                    "DK" -> nordicCross(Color(0xFFC8102E), Color.White)
                    "NO" -> {
                        nordicCross(Color(0xFFBA0C2F), Color.White)
                        drawRect(color = Color(0xFF00205B), topLeft = Offset(w * 0.31f, 0f), size = Size(w * 0.10f, h))
                        drawRect(color = Color(0xFF00205B), topLeft = Offset(0f, h * 0.45f), size = Size(w, h * 0.10f))
                    }
                    "GB" -> {
                        drawRect(color = Color(0xFF012169))
                        drawLine(Color.White, Offset.Zero, Offset(w, h), strokeWidth = w * 0.18f)
                        drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.18f)
                        drawLine(Color(0xFFC8102E), Offset.Zero, Offset(w, h), strokeWidth = w * 0.08f)
                        drawLine(Color(0xFFC8102E), Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.08f)
                        drawRect(color = Color.White, topLeft = Offset(w * 0.39f, 0f), size = Size(w * 0.22f, h))
                        drawRect(color = Color.White, topLeft = Offset(0f, h * 0.39f), size = Size(w, h * 0.22f))
                        drawRect(color = Color(0xFFC8102E), topLeft = Offset(w * 0.43f, 0f), size = Size(w * 0.14f, h))
                        drawRect(color = Color(0xFFC8102E), topLeft = Offset(0f, h * 0.43f), size = Size(w, h * 0.14f))
                    }
                    "CA" -> {
                        drawRect(color = Color(0xFFFF0000), topLeft = Offset.Zero, size = Size(w * 0.25f, h))
                        drawRect(color = Color.White, topLeft = Offset(w * 0.25f, 0f), size = Size(w * 0.50f, h))
                        drawRect(color = Color(0xFFFF0000), topLeft = Offset(w * 0.75f, 0f), size = Size(w * 0.25f, h))
                        drawCircle(color = Color(0xFFFF0000), radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
                    }
                    "BR" -> {
                        drawRect(color = Color(0xFF009C3B))
                        val diamond = Path().apply {
                            moveTo(w * 0.5f, h * 0.16f)
                            lineTo(w * 0.86f, h * 0.5f)
                            lineTo(w * 0.5f, h * 0.84f)
                            lineTo(w * 0.14f, h * 0.5f)
                            close()
                        }
                        drawPath(diamond, Color(0xFFFFDF00))
                        drawCircle(color = Color(0xFF002776), radius = w * 0.18f)
                    }
                    "TR" -> {
                        drawRect(color = Color(0xFFE30A17))
                        drawCircle(color = Color.White, radius = w * 0.22f, center = Offset(w * 0.42f, h * 0.5f))
                        drawCircle(color = Color(0xFFE30A17), radius = w * 0.175f, center = Offset(w * 0.48f, h * 0.5f))
                        drawCircle(color = Color.White, radius = w * 0.06f, center = Offset(w * 0.67f, h * 0.5f))
                    }
                    "AU", "NZ" -> {
                        drawRect(color = Color(0xFF012169))
                        drawCircle(color = Color.White, radius = w * 0.06f, center = Offset(w * 0.72f, h * 0.34f))
                        drawCircle(color = Color.White, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.62f))
                        drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.78f, h * 0.68f))
                        drawRect(color = Color.White, topLeft = Offset(w * 0.18f, h * 0.16f), size = Size(w * 0.24f, h * 0.07f))
                        drawRect(color = Color.White, topLeft = Offset(w * 0.27f, h * 0.07f), size = Size(w * 0.07f, h * 0.24f))
                    }
                    "KR" -> {
                        drawRect(color = Color.White)
                        drawCircle(color = Color(0xFFCD2E3A), radius = w * 0.22f)
                        drawArc(
                            color = Color(0xFF0047A0),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(w * 0.28f, h * 0.28f),
                            size = Size(w * 0.44f, h * 0.44f),
                        )
                    }
                    "CZ" -> {
                        drawRect(color = Color.White, topLeft = Offset.Zero, size = Size(w, h * 0.5f))
                        drawRect(color = Color(0xFFD7141A), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                        val tri = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(w * 0.54f, h * 0.5f)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(tri, Color(0xFF11457E))
                    }
                    else -> {
                        val h1 = (code.hashCode() and 0x7FFFFFFF) % 360
                        val c1 = Color.hsl(h1.toFloat(), 0.65f, 0.42f)
                        val c2 = Color.hsl(((h1 + 48) % 360).toFloat(), 0.72f, 0.30f)
                        drawRect(brush = Brush.linearGradient(listOf(c1, c2)))
                        val cx = w / 2f
                        val cy = h / 2f
                        for (i in 0 until 6) {
                            val angle = i * (PI / 3.0)
                            drawCircle(
                                color = Color.White.copy(alpha = 0.22f),
                                radius = w * 0.05f,
                                center = Offset(
                                    (cx + cos(angle) * w * 0.24f).toFloat(),
                                    (cy + sin(angle) * h * 0.24f).toFloat(),
                                ),
                            )
                        }
                    }
                }
            }
        }
        val needsCodeOverlay = code !in setOf(
            "", "REC", "AUTO", "US", "IR", "FR", "IT", "BE", "IE", "RO",
            "DE", "NL", "HU", "AT", "RU", "BG", "EE", "LT", "LU", "ES",
            "UA", "PL", "ID", "SG", "JP", "CH", "SE", "FI", "DK", "NO",
            "GB", "CA", "BR", "TR", "AU", "NZ", "KR", "CZ",
        )
        if (needsCodeOverlay) {
            Text(
                text = code.take(2),
                color = Color.White,
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.triVertical(
    c1: Color,
    c2: Color,
    c3: Color,
) {
    val w = size.width
    val h = size.height
    drawRect(color = c1, topLeft = Offset.Zero, size = Size(w / 3f + 0.5f, h))
    drawRect(color = c2, topLeft = Offset(w / 3f, 0f), size = Size(w / 3f + 0.5f, h))
    drawRect(color = c3, topLeft = Offset(2f * w / 3f, 0f), size = Size(w / 3f + 0.5f, h))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.triHorizontal(
    c1: Color,
    c2: Color,
    c3: Color,
) {
    val w = size.width
    val h = size.height
    drawRect(color = c1, topLeft = Offset.Zero, size = Size(w, h / 3f + 0.5f))
    drawRect(color = c2, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f + 0.5f))
    drawRect(color = c3, topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f + 0.5f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.nordicCross(bg: Color, cross: Color) {
    val w = size.width
    val h = size.height
    drawRect(color = bg)
    drawRect(color = cross, topLeft = Offset(w * 0.28f, 0f), size = Size(w * 0.16f, h))
    drawRect(color = cross, topLeft = Offset(0f, h * 0.42f), size = Size(w, h * 0.16f))
}

@Composable
fun SignalStrengthBars(
    pingMs: Int,
    modifier: Modifier = Modifier,
) {
    val activeBars = when {
        pingMs <= 0 -> 3
        pingMs < 120 -> 4
        pingMs < 240 -> 3
        pingMs < 450 -> 2
        else -> 1
    }
    val activeColor = when {
        pingMs <= 0 || pingMs < 400 -> ClxyColors.CyanGlow
        pingMs < 1000 -> ClxyColors.AmberWarning
        else -> ClxyColors.RedDisconnectStart
    }
    Canvas(modifier = modifier.size(width = 14.dp, height = 10.dp)) {
        val barW = size.width / 5.5f
        val gap = (size.width - barW * 4) / 3f
        val heights = listOf(0.30f, 0.50f, 0.75f, 1.0f)
        heights.forEachIndexed { index, frac ->
            val h = size.height * frac
            val x = index * (barW + gap)
            val y = size.height - h
            drawRoundRect(
                color = if (index < activeBars) activeColor else ClxyColors.BarInactive,
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
}

@Composable
fun ClxyBottomNavBar(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ClxyColors.NavBarSurface)
            .border(width = 0.5.dp, color = Color(0xFF1E293B))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(
            selected = activeTab == MainTab.HOME,
            label = "Home",
            tag = "nav_home_button",
            icon = { selected ->
                Icon(
                    imageVector = if (selected) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(22.dp),
                )
            },
            onClick = { onSelectTab(MainTab.HOME) },
        )
        BottomNavItem(
            selected = activeTab == MainTab.SERVERS,
            label = "All Server",
            tag = "nav_servers_button",
            icon = { selected ->
                Icon(
                    imageVector = if (selected) Icons.Filled.Public else Icons.Outlined.Public,
                    contentDescription = "All Server",
                    modifier = Modifier.size(22.dp),
                )
            },
            onClick = { onSelectTab(MainTab.SERVERS) },
        )
        BottomNavItem(
            selected = activeTab == MainTab.PROFILE,
            label = "Profile",
            tag = "nav_profile_button",
            icon = { selected ->
                Icon(
                    imageVector = if (selected) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.size(22.dp),
                )
            },
            onClick = { onSelectTab(MainTab.PROFILE) },
        )
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    label: String,
    tag: String,
    icon: @Composable (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) ClxyColors.CyanGlow else ClxyColors.TextDim,
        animationSpec = tween(260),
        label = "navTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navScale",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 26.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navPillWidth",
    )

    Column(
        modifier = Modifier
            .testTag(tag)
            .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (pillWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ClxyColors.CyanGlow),
            )
            Spacer(modifier = Modifier.height(3.dp))
        }
        Box(modifier = Modifier.scale(iconScale)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides tint,
            ) {
                icon(selected)
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun ClxyToastBanner(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(999.dp))
                .background(Color(0xF20F172E), RoundedCornerShape(999.dp))
                .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(ClxyColors.CyanGlow, CircleShape),
            )
            Text(
                text = message.orEmpty(),
                color = ClxyColors.TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
