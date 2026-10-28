package com.gunnys.eundunhealth.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gunnys.eundunhealth.ui.auth.AuthGateScreen
import com.gunnys.eundunhealth.ui.auth.AuthViewModel
import com.gunnys.eundunhealth.ui.auth.SessionState
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.onboarding.OnboardingScreen
import com.gunnys.eundunhealth.ui.smile.SmileTrainingScreen
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

    when (val s = sessionState) {
        is SessionState.UpdateRequired -> {
            UpdateRequiredScreen(
                minimumVersionCode = s.minimumSupportedVersionCode,
                playPackageUri = s.playPackageUri,
            )
            return
        }
        is SessionState.ResetFailed -> {
            Scaffold { padding ->
                ErrorContent(
                    error = s.error,
                    modifier = Modifier.padding(padding),
                    onRetry = authViewModel::retryStartup,
                )
            }
            return
        }
        else -> Unit
    }

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
            SessionState.Unknown,
            is SessionState.UpdateRequired,
            is SessionState.ResetFailed,
            -> { /* Splash 또는 상위 분기 유지 */ }
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
                onNavigateToSmileTraining = {
                    navController.navigate(Screen.SmileTraining.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Screen.SmileTraining.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(durationMillis = 300),
                ) + fadeIn(animationSpec = tween(durationMillis = 250))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(durationMillis = 250),
                ) + fadeOut(animationSpec = tween(durationMillis = 200))
            },
        ) {
            SmileTrainingScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
fun UpdateRequiredScreen(
    @Suppress("UnusedParameter") minimumVersionCode: Int,
    playPackageUri: String,
) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "앱 업데이트가 필요합니다",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "안전한 서비스 이용과 최신 운동 계획 기능을 위해 Play 스토어에서 최신 버전으로 업데이트해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(playPackageUri)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }.onFailure {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.gunnys.eundunhealth"),
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Google Play에서 업데이트")
            }
        }
    }
}
