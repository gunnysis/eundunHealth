package com.gunnys.eundunhealth.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object MotionTokens {
    const val DurationShort = 150
    const val DurationMedium = 250
    const val DurationLong = 400

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    fun <T> standardSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> checkBounceSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> standardFade() = tween<T>(
        durationMillis = DurationMedium,
        easing = StandardEasing,
    )
}
