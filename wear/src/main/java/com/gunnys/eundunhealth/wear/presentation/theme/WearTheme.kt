package com.gunnys.eundunhealth.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Eundun Health Wear OS M3 Color Scheme.
 * Aligned with the mobile app's "Modern Forest & Mint" dark palette (`Color.kt`)
 * and tuned for OLED watch screens (pure black background + WCAG AAA >= 7:1 contrast).
 */
private val EundunWearColorScheme =
    ColorScheme(
        primary = Color(0xFF7CDA9C),
        primaryDim = Color(0xFF60BE82),
        primaryContainer = Color(0xFF00522C),
        onPrimary = Color(0xFF00391D),
        onPrimaryContainer = Color(0xFF98F7B6),
        secondary = Color(0xFFB6CCB9),
        secondaryDim = Color(0xFF9AB09E),
        secondaryContainer = Color(0xFF384B3D),
        onSecondary = Color(0xFF223528),
        onSecondaryContainer = Color(0xFFD2E8D5),
        tertiary = Color(0xFFA2CEDB),
        tertiaryDim = Color(0xFF87B2BF),
        tertiaryContainer = Color(0xFF214C57),
        onTertiary = Color(0xFF023640),
        onTertiaryContainer = Color(0xFFBDEAF7),
        surfaceContainerLow = Color(0xFF151A16),
        surfaceContainer = Color(0xFF1C221E),
        surfaceContainerHigh = Color(0xFF262D28),
        onSurface = Color(0xFFE1E3DE),
        onSurfaceVariant = Color(0xFFC1C9BF),
        outline = Color(0xFF8B938A),
        outlineVariant = Color(0xFF414942),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE1E3DE),
        error = Color(0xFFFFB4AB),
        errorDim = Color(0xFFFF897D),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
    )

@Composable
fun EundunWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EundunWearColorScheme,
        content = content,
    )
}
