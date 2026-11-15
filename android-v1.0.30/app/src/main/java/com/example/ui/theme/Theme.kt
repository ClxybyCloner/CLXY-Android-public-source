package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ClxyDarkColorScheme = darkColorScheme(
    primary = ClxyColors.CyanGlow,
    onPrimary = ClxyColors.BgTop,
    primaryContainer = ClxyColors.BlueDeep,
    onPrimaryContainer = ClxyColors.TextPrimary,
    secondary = ClxyColors.BluePrimary,
    onSecondary = ClxyColors.TextPrimary,
    secondaryContainer = ClxyColors.CardSurfaceSolid,
    onSecondaryContainer = ClxyColors.TextPrimary,
    tertiary = ClxyColors.PurplePrimary,
    onTertiary = ClxyColors.TextPrimary,
    background = ClxyColors.BgTop,
    onBackground = ClxyColors.TextPrimary,
    surface = ClxyColors.CardSurfaceSolid,
    onSurface = ClxyColors.TextPrimary,
    surfaceVariant = ClxyColors.ModalSurface,
    onSurfaceVariant = ClxyColors.TextMuted,
    outline = ClxyColors.GlassBorder,
    error = ClxyColors.RedDisconnectStart,
    onError = ClxyColors.TextPrimary,
)

@Composable
fun ClxyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClxyDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
