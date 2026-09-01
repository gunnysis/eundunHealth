package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gunnys.eundunhealth.ui.auth.AuthGateScreen
import com.gunnys.eundunhealth.ui.auth.AuthViewModel
import com.gunnys.eundunhealth.ui.auth.SessionState
import com.gunnys.eundunhealth.ui.badge.BadgeScreen
import com.gunnys.eundunhealth.ui.goal.GoalScreen
import com.gunnys.eundunhealth.ui.history.HistoryScreen
import com.gunnys.eundunhealth.ui.home.HomeScreen
import com.gunnys.eundunhealth.ui.onboarding.OnboardingScreen
import com.gunnys.eundunhealth.ui.profile.ProfileScreen
import com.gunnys.eundunhealth.ui.splash.SplashScreen
import com.gunnys.eundunhealth.ui.statistics.StatisticsScreen
import com.gunnys.eundunhealth.ui.workout.WorkoutDetailScreen

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = hiltViewModel(),
    onRequestHealthPermissions: () -> Unit = {},
) {
    val navController = rememberNavController()
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionState) {
        when (val s = sessionState) {
            is SessionState.Authenticated -> {
                val dest = if (s.needsOnboarding) Screen.Onboarding.route else Screen.Home.route
                navController.navigate(dest) {
                    popUpTo(0) { inclusive = true }
                }
            }
            SessionState.Unauthenticated -> {
                navController.navigate(Screen.AuthGate.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            SessionState.Unknown -> { /* Splash 유지 */ }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen()
        }
        composable(Screen.AuthGate.route) {
            AuthGateScreen(authViewModel = authViewModel)
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onExerciseClick = { exerciseId ->
                    navController.navigate(Screen.WorkoutDetail.createRoute(exerciseId))
                },
                onBadgesClick = { navController.navigate(Screen.Badges.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onStatisticsClick = { navController.navigate(Screen.Statistics.route) },
                onGoalClick = { navController.navigate(Screen.Goal.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onLogout = { authViewModel.logout() },
                onRequestHealthPermissions = onRequestHealthPermissions,
            )
        }
        composable(
            Screen.WorkoutDetail.route,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { backStackEntry ->
            WorkoutDetailScreen(
                exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onAccountDeleted = {
                    // 계정 삭제 경로가 이미 로그아웃까지 마쳤다 — 세션 상태만 되돌리면
                    // 상단 LaunchedEffect 가 인증 게이트로 이동시킨다.
                    authViewModel.onSessionEnded()
                },
            )
        }
        composable(Screen.Badges.route) {
            BadgeScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Statistics.route) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Goal.route) {
            GoalScreen(onBack = { navController.popBackStack() })
        }
    }
}
