package com.gunnys.eundunhealth.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object WorkoutDetail : Screen("workout/{exerciseId}") {
        fun createRoute(exerciseId: String) = "workout/$exerciseId"
    }
    object Badges : Screen("badges")
    object History : Screen("history")
}
