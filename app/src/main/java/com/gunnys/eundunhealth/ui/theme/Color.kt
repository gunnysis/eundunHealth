package com.gunnys.eundunhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light theme colors - Modern Forest & Mint (#006D3C seed)
val md_theme_light_primary = Color(0xFF006D3C)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF98F7B6)
val md_theme_light_onPrimaryContainer = Color(0xFF00210F)
val md_theme_light_inversePrimary = Color(0xFF7CDA9C)

val md_theme_light_secondary = Color(0xFF4F6354)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFD2E8D5)
val md_theme_light_onSecondaryContainer = Color(0xFF0D1F14)

val md_theme_light_tertiary = Color(0xFF3A6470)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFBDEAF7)
val md_theme_light_onTertiaryContainer = Color(0xFF001F27)

val md_theme_light_background = Color(0xFFFBFDF8)
val md_theme_light_onBackground = Color(0xFF191C19)
val md_theme_light_surface = Color(0xFFFBFDF8)
val md_theme_light_onSurface = Color(0xFF191C19)
val md_theme_light_surfaceVariant = Color(0xFFDDE5DB)
val md_theme_light_onSurfaceVariant = Color(0xFF414942)
val md_theme_light_surfaceTint = Color(0xFF006D3C)
val md_theme_light_inverseSurface = Color(0xFF2E312E)
val md_theme_light_inverseOnSurface = Color(0xFFF0F1EC)

val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)

val md_theme_light_outline = Color(0xFF727970)
val md_theme_light_outlineVariant = Color(0xFFC1C9BF)
val md_theme_light_scrim = Color(0xFF000000)

val md_theme_light_surfaceBright = Color(0xFFFBFDF8)
val md_theme_light_surfaceDim = Color(0xFFDBDDD8)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF5F7F2)
val md_theme_light_surfaceContainer = Color(0xFFEFEEEA)
val md_theme_light_surfaceContainerHigh = Color(0xFFE9E9E4)
val md_theme_light_surfaceContainerHighest = Color(0xFFE3E3DE)

// Dark theme colors - Modern Forest & Mint (#7CDA9C primary, #111412 deep charcoal surface)
val md_theme_dark_primary = Color(0xFF7CDA9C)
val md_theme_dark_onPrimary = Color(0xFF00391D)
val md_theme_dark_primaryContainer = Color(0xFF00522C)
val md_theme_dark_onPrimaryContainer = Color(0xFF98F7B6)
val md_theme_dark_inversePrimary = Color(0xFF006D3C)

val md_theme_dark_secondary = Color(0xFFB6CCB9)
val md_theme_dark_onSecondary = Color(0xFF223528)
val md_theme_dark_secondaryContainer = Color(0xFF384B3D)
val md_theme_dark_onSecondaryContainer = Color(0xFFD2E8D5)

val md_theme_dark_tertiary = Color(0xFFA2CEDB)
val md_theme_dark_onTertiary = Color(0xFF023640)
val md_theme_dark_tertiaryContainer = Color(0xFF214C57)
val md_theme_dark_onTertiaryContainer = Color(0xFFBDEAF7)

val md_theme_dark_background = Color(0xFF111412)
val md_theme_dark_onBackground = Color(0xFFE1E3DE)
val md_theme_dark_surface = Color(0xFF111412)
val md_theme_dark_onSurface = Color(0xFFE1E3DE)
val md_theme_dark_surfaceVariant = Color(0xFF414941)
val md_theme_dark_onSurfaceVariant = Color(0xFFC1C9BF)
val md_theme_dark_surfaceTint = Color(0xFF7CDA9C)
val md_theme_dark_inverseSurface = Color(0xFFE1E3DE)
val md_theme_dark_inverseOnSurface = Color(0xFF2E312E)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_outline = Color(0xFF8B938A)
val md_theme_dark_outlineVariant = Color(0xFF414942)
val md_theme_dark_scrim = Color(0xFF000000)

val md_theme_dark_surfaceBright = Color(0xFF373A37)
val md_theme_dark_surfaceDim = Color(0xFF111412)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0C0F0D)
val md_theme_dark_surfaceContainerLow = Color(0xFF191C19)
val md_theme_dark_surfaceContainer = Color(0xFF1D201D)
val md_theme_dark_surfaceContainerHigh = Color(0xFF282B27)
val md_theme_dark_surfaceContainerHighest = Color(0xFF323632)

/**
 * 도메인 전용 확장 시맨틱 컬러 토큰 (성공·휴식·경고·식단 매크로).
 */
@Immutable
data class EundunExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val rest: Color,
    val onRest: Color,
    val restContainer: Color,
    val onRestContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val macroCarbs: Color,
    val macroProtein: Color,
    val macroFat: Color,
)

val LightExtendedColors = EundunExtendedColors(
    success = Color(0xFF146C2E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA6F2B4),
    onSuccessContainer = Color(0xFF002108),
    rest = Color(0xFF3A6470),
    onRest = Color(0xFFFFFFFF),
    restContainer = Color(0xFFBDEAF7),
    onRestContainer = Color(0xFF001F27),
    warning = Color(0xFF8B5000),
    onWarning = Color(0xFFFFFFFF),
    macroCarbs = Color(0xFFD97706),
    macroProtein = Color(0xFF006D3C),
    macroFat = Color(0xFF4F46E5),
)

val DarkExtendedColors = EundunExtendedColors(
    success = Color(0xFF8BD59A),
    onSuccess = Color(0xFF003913),
    successContainer = Color(0xFF00531F),
    onSuccessContainer = Color(0xFFA6F2B4),
    rest = Color(0xFFA2CEDB),
    onRest = Color(0xFF023640),
    restContainer = Color(0xFF214C57),
    onRestContainer = Color(0xFFBDEAF7),
    warning = Color(0xFFFFB869),
    onWarning = Color(0xFF4A2800),
    macroCarbs = Color(0xFFFBBF24),
    macroProtein = Color(0xFF7CDA9C),
    macroFat = Color(0xFF818CF8),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
