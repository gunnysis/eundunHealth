package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector

/** The four destinations that remain visible in the adaptive navigation suite. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TODAY(Screen.Home.route, "오늘", Icons.Default.Home),
    MEAL(Screen.MealPlan.route, "식단", Icons.Default.Restaurant),
    PROGRESS(Screen.Progress.route, "진행", Icons.Default.Insights),
    PROFILE(Screen.Profile.route, "프로필", Icons.Default.AccountCircle),
}

fun String.topLevelDestination(): TopLevelDestination = when (this) {
    Screen.MealPlan.route -> TopLevelDestination.MEAL
    Screen.Progress.route, Screen.Badges.route, Screen.History.route, Screen.Statistics.route, Screen.Goal.route ->
        TopLevelDestination.PROGRESS
    Screen.Profile.route, Screen.ProfileEdit.route -> TopLevelDestination.PROFILE
    else -> TopLevelDestination.TODAY
}
