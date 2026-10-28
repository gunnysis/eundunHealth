package com.gunnys.eundunhealth.ui.theme

import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyCompletenessTest {

    @Test
    fun `15개 M3 Typography 슬롯이 모두 PretendardFontFamily 와 명시적 크기를 가진다`() {
        val styles = mapOf(
            "displayLarge" to Typography.displayLarge,
            "displayMedium" to Typography.displayMedium,
            "displaySmall" to Typography.displaySmall,
            "headlineLarge" to Typography.headlineLarge,
            "headlineMedium" to Typography.headlineMedium,
            "headlineSmall" to Typography.headlineSmall,
            "titleLarge" to Typography.titleLarge,
            "titleMedium" to Typography.titleMedium,
            "titleSmall" to Typography.titleSmall,
            "bodyLarge" to Typography.bodyLarge,
            "bodyMedium" to Typography.bodyMedium,
            "bodySmall" to Typography.bodySmall,
            "labelLarge" to Typography.labelLarge,
            "labelMedium" to Typography.labelMedium,
            "labelSmall" to Typography.labelSmall,
        )

        assertEquals(15, styles.size)
        styles.forEach { (name, style) ->
            assertEquals("$name fontFamily", PretendardFontFamily, style.fontFamily)
            assertNotEquals("$name fontSize", TextUnit.Unspecified, style.fontSize)
            assertNotEquals("$name lineHeight", TextUnit.Unspecified, style.lineHeight)
            assertTrue(
                "$name letterSpacing should be <= 0.sp for Korean readability",
                style.letterSpacing.value <= 0f,
            )
        }
    }
}
