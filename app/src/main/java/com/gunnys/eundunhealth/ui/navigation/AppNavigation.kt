package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gunnys.eundunhealth.ui.auth.AuthState
import com.gunnys.eundunhealth.ui.auth.AuthViewModel
import com.gunnys.eundunhealth.ui.auth.ForgotPasswordScreen
import com.gunnys.eundunhealth.ui.auth.LoginScreen
import com.gunnys.eundunhealth.ui.auth.SignupScreen
import com.gunnys.eundunhealth.ui.badge.BadgeScreen
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
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                val needsOnboarding = (authState as AuthState.Authenticated).needsOnboarding
                val dest = if (needsOnboarding) Screen.Onboarding.route else Screen.Home.route
                navController.navigate(dest) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.Unauthenticated -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> {}
        }
    }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen()
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                authViewModel = authViewModel,
            )
        }
        composable(Screen.Signup.route) {
            SignupScreen(
                onNavigateToLogin = { navController.popBackStack() },
                authViewModel = authViewModel,
            )
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                authViewModel = authViewModel,
            )
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
                    // AuthViewModel을 Unauthenticated로 전환 → 상단 LaunchedEffect가 Login으로 이동
                    authViewModel.logout()
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
    }
}
