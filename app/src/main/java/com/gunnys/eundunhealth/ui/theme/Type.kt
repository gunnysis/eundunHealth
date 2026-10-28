package com.gunnys.eundunhealth.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.gunnys.eundunhealth.R

val PretendardFontFamily = FontFamily(
    Font(resId = R.font.pretendard_variable, weight = FontWeight.Normal),
    Font(resId = R.font.pretendard_variable, weight = FontWeight.Medium),
    Font(resId = R.font.pretendard_variable, weight = FontWeight.SemiBold),
    Font(resId = R.font.pretendard_variable, weight = FontWeight.Bold),
)

private val KoreanPlatformTextStyle = PlatformTextStyle(
    includeFontPadding = false,
)

private val KoreanLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun eundunTextStyle(
    fontWeight: FontWeight,
    fontSizeSp: Int,
    lineHeightSp: Int,
    letterSpacingSp: Float,
) = TextStyle(
    fontFamily = PretendardFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingSp.sp,
    platformStyle = KoreanPlatformTextStyle,
    lineHeightStyle = KoreanLineHeightStyle,
)

val Typography = Typography(
    displayLarge = eundunTextStyle(
        fontWeight = FontWeight.Bold,
        fontSizeSp = 44,
        lineHeightSp = 52,
        letterSpacingSp = -0.25f,
    ),
    displayMedium = eundunTextStyle(
        fontWeight = FontWeight.Bold,
        fontSizeSp = 36,
        lineHeightSp = 44,
        letterSpacingSp = -0.2f,
    ),
    displaySmall = eundunTextStyle(
        fontWeight = FontWeight.Bold,
        fontSizeSp = 32,
        lineHeightSp = 40,
        letterSpacingSp = -0.2f,
    ),
    headlineLarge = eundunTextStyle(
        fontWeight = FontWeight.Bold,
        fontSizeSp = 28,
        lineHeightSp = 36,
        letterSpacingSp = -0.2f,
    ),
    headlineMedium = eundunTextStyle(
        fontWeight = FontWeight.Bold,
        fontSizeSp = 24,
        lineHeightSp = 32,
        letterSpacingSp = -0.15f,
    ),
    headlineSmall = eundunTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSizeSp = 22,
        lineHeightSp = 30,
        letterSpacingSp = -0.15f,
    ),
    titleLarge = eundunTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSizeSp = 20,
        lineHeightSp = 28,
        letterSpacingSp = -0.15f,
    ),
    titleMedium = eundunTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSizeSp = 16,
        lineHeightSp = 24,
        letterSpacingSp = -0.1f,
    ),
    titleSmall = eundunTextStyle(
        fontWeight = FontWeight.Medium,
        fontSizeSp = 14,
        lineHeightSp = 20,
        letterSpacingSp = -0.1f,
    ),
    bodyLarge = eundunTextStyle(
        fontWeight = FontWeight.Normal,
        fontSizeSp = 16,
        lineHeightSp = 24,
        letterSpacingSp = -0.1f,
    ),
    bodyMedium = eundunTextStyle(
        fontWeight = FontWeight.Normal,
        fontSizeSp = 14,
        lineHeightSp = 20,
        letterSpacingSp = -0.1f,
    ),
    bodySmall = eundunTextStyle(
        fontWeight = FontWeight.Normal,
        fontSizeSp = 12,
        lineHeightSp = 18,
        letterSpacingSp = 0f,
    ),
    labelLarge = eundunTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSizeSp = 14,
        lineHeightSp = 20,
        letterSpacingSp = 0f,
    ),
    labelMedium = eundunTextStyle(
        fontWeight = FontWeight.Medium,
        fontSizeSp = 12,
        lineHeightSp = 16,
        letterSpacingSp = 0f,
    ),
    labelSmall = eundunTextStyle(
        fontWeight = FontWeight.Medium,
        fontSizeSp = 11,
        lineHeightSp = 16,
        letterSpacingSp = 0f,
    ),
)
