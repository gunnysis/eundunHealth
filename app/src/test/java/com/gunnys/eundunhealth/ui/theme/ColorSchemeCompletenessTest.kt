package com.gunnys.eundunhealth.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ColorSchemeCompletenessTest {

    private fun ColorScheme.allRoleColors(): Map<String, Color> = mapOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )

    @Test
    fun `LightColorScheme 와 DarkColorScheme 의 36개 M3 역할이 모두 지정되고 Unspecified 가 아니다`() {
        val lightRoles = LightColorScheme.allRoleColors()
        val darkRoles = DarkColorScheme.allRoleColors()

        assertEquals(36, lightRoles.size)
        assertEquals(36, darkRoles.size)

        lightRoles.forEach { (name, color) ->
            assertNotEquals("Light $name should not be Unspecified", Color.Unspecified, color)
        }
        darkRoles.forEach { (name, color) ->
            assertNotEquals("Dark $name should not be Unspecified", Color.Unspecified, color)
        }
    }

    @Test
    fun `브랜드 시드 컬러 #006D3C 와 다크 배경 #111412 및 보라색 기본값 부재 검증`() {
        val defaultPurple40 = Color(0xFF6650a4)
        val defaultPurple80 = Color(0xFFD0BCFF)

        assertEquals(Color(0xFF006D3C), LightColorScheme.primary)
        assertEquals(Color(0xFFFBFDF8), LightColorScheme.background)
        assertEquals(Color(0xFF7CDA9C), DarkColorScheme.primary)
        assertEquals(Color(0xFF111412), DarkColorScheme.background)

        LightColorScheme.allRoleColors().values.forEach {
            assertNotEquals(defaultPurple40, it)
            assertNotEquals(defaultPurple80, it)
        }
        DarkColorScheme.allRoleColors().values.forEach {
            assertNotEquals(defaultPurple40, it)
            assertNotEquals(defaultPurple80, it)
        }
    }

    @Test
    fun `ExtendedColors 의 13개 확장 시맨틱 토큰이 모두 정의되어 있다`() {
        val lightExt = listOf(
            LightExtendedColors.success,
            LightExtendedColors.onSuccess,
            LightExtendedColors.successContainer,
            LightExtendedColors.onSuccessContainer,
            LightExtendedColors.rest,
            LightExtendedColors.onRest,
            LightExtendedColors.restContainer,
            LightExtendedColors.onRestContainer,
            LightExtendedColors.warning,
            LightExtendedColors.onWarning,
            LightExtendedColors.macroCarbs,
            LightExtendedColors.macroProtein,
            LightExtendedColors.macroFat,
        )
        val darkExt = listOf(
            DarkExtendedColors.success,
            DarkExtendedColors.onSuccess,
            DarkExtendedColors.successContainer,
            DarkExtendedColors.onSuccessContainer,
            DarkExtendedColors.rest,
            DarkExtendedColors.onRest,
            DarkExtendedColors.restContainer,
            DarkExtendedColors.onRestContainer,
            DarkExtendedColors.warning,
            DarkExtendedColors.onWarning,
            DarkExtendedColors.macroCarbs,
            DarkExtendedColors.macroProtein,
            DarkExtendedColors.macroFat,
        )

        assertEquals(13, lightExt.size)
        assertEquals(13, darkExt.size)
        lightExt.forEach { assertNotEquals(Color.Unspecified, it) }
        darkExt.forEach { assertNotEquals(Color.Unspecified, it) }
    }
}
