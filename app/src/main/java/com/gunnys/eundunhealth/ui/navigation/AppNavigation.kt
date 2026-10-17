package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gunnys.eundunhealth.ui.auth.AuthGateScreen
import com.gunnys.eundunhealth.ui.auth.AuthViewModel
import com.gunnys.eundunhealth.ui.auth.SessionState
import com.gunnys.eundunhealth.ui.onboarding.OnboardingScreen
import com.gunnys.eundunhealth.ui.splash.SplashScreen

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = hiltViewModel(),
    healthPermissionsRevision: Int = 0,
    onRequestHealthPermissions: () -> Unit = {},
    onOpenHealthConnectManagement: () -> Unit = {},
) {
    val navController = rememberNavController()
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionState) {
        when (val s = sessionState) {
            is SessionState.Authenticated -> {
                val dest = if (s.needsOnboarding) Screen.Onboarding.route else Screen.Main.route
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
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Main.route) {
            MainAppShell(
                onLogout = authViewModel::logout,
                onAccountDeleted = authViewModel::onSessionEnded,
                healthPermissionsRevision = healthPermissionsRevision,
                onRequestHealthPermissions = onRequestHealthPermissions,
                onOpenHealthConnectManagement = onOpenHealthConnectManagement,
            )
        }
    }
}
