package com.gunnys.eundunhealth.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")

    // 브라우저 위임 전환으로 login/signup/forgot_password 3개 라우트가 하나로 합쳐졌다.
    // 로그인과 가입의 분기는 Entra 호스팅 페이지 안에서 일어난다.
    object AuthGate : Screen("auth_gate")
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
