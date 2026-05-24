package com.gunnys.eundunhealth.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object WorkoutDetail : Screen("workout/{exerciseId}") {
        fun createRoute(exerciseId: String) = "workout/$exerciseId"
    }
    object Profile : Screen("profile")
    object Badges : Screen("badges")
    object History : Screen("history")
    object Statistics : Screen("statistics")
    object Goal : Screen("goal")
}
