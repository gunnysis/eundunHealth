package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gunnys.eundunhealth.ui.badge.BadgeScreen
import com.gunnys.eundunhealth.ui.goal.GoalScreen
import com.gunnys.eundunhealth.ui.history.HistoryScreen
import com.gunnys.eundunhealth.ui.home.HomeScreen
import com.gunnys.eundunhealth.ui.home.HomeViewModel
import com.gunnys.eundunhealth.ui.mealplan.MealPlanScreen
import com.gunnys.eundunhealth.ui.profile.ProfileEditScreen
import com.gunnys.eundunhealth.ui.profile.ProfileHubScreen
import com.gunnys.eundunhealth.ui.profile.ProfileHubViewModel
import com.gunnys.eundunhealth.ui.statistics.StatisticsScreen
import com.gunnys.eundunhealth.ui.workout.WorkoutDetailScreen
import com.gunnys.eundunhealth.ui.workout.WorkoutGuideScreen

private const val KEY_REFRESH_HOME = "refresh_home_plan"
private const val KEY_REFRESH_PROGRESS = "refresh_progress_summary"

@Composable
fun MainAppShell(
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    healthPermissionsRevision: Int,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectManagement: () -> Unit,
    onNavigateToSmileTraining: () -> Unit = {},
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val selected = entry?.destination?.route?.topLevelDestination() ?: TopLevelDestination.TODAY

    TopLevelNavigationSuite(
        selected = selected,
        onDestinationSelected = { destination ->
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) {
        MainContent(
            navController = navController,
            onLogout = onLogout,
            onAccountDeleted = onAccountDeleted,
            healthPermissionsRevision = healthPermissionsRevision,
            onRequestHealthPermissions = onRequestHealthPermissions,
            onOpenHealthConnectManagement = onOpenHealthConnectManagement,
            onNavigateToSmileTraining = onNavigateToSmileTraining,
        )
    }
}

@Composable
internal fun TopLevelNavigationSuite(
    selected: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                    selected = selected == destination,
                    onClick = { onDestinationSelected(destination) },
                )
            }
        },
    ) {
        content()
    }
}

@Composable
private fun MainContent(
    navController: NavHostController,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    healthPermissionsRevision: Int,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectManagement: () -> Unit,
    onNavigateToSmileTraining: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(Screen.Home.route) { backStackEntry ->
            val homeViewModel: HomeViewModel = hiltViewModel()
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow(KEY_REFRESH_HOME, false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(shouldRefresh) {
                if (shouldRefresh) {
                    backStackEntry.savedStateHandle[KEY_REFRESH_HOME] = false
                    homeViewModel.loadPlan()
                }
            }
            HomeScreen(
                onExerciseClick = { exerciseId ->
                    navController.navigate(Screen.WorkoutDetail.createRoute(exerciseId))
                },
                onRequestHealthPermissions = onRequestHealthPermissions,
                onStartWorkoutGuide = { date ->
                    navController.navigate(Screen.WorkoutGuide.createRoute(date)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSmileTraining = onNavigateToSmileTraining,
                viewModel = homeViewModel,
            )
        }
        composable(Screen.MealPlan.route) { MealPlanScreen(onBack = null) }
        composable(Screen.Progress.route) { backStackEntry ->
            val progressViewModel: ProgressHubViewModel = hiltViewModel()
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow(KEY_REFRESH_PROGRESS, false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(shouldRefresh) {
                if (shouldRefresh) {
                    backStackEntry.savedStateHandle[KEY_REFRESH_PROGRESS] = false
                    progressViewModel.loadSummary()
                }
            }
            ProgressHubScreen(
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onStatisticsClick = { navController.navigate(Screen.Statistics.route) },
                onGoalClick = { navController.navigate(Screen.Goal.route) },
                onBadgesClick = { navController.navigate(Screen.Badges.route) },
                onSmileTrainingClick = onNavigateToSmileTraining,
                viewModel = progressViewModel,
            )
        }
        composable(Screen.Profile.route) {
            val viewModel: ProfileHubViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(healthPermissionsRevision) {
                viewModel.refreshHealthConnectStatus()
            }
            ProfileHubScreen(
                state = state,
                onEditProfile = { navController.navigate(Screen.ProfileEdit.route) },
                onRequestHealthPermissions = onRequestHealthPermissions,
                onOpenHealthConnectManagement = onOpenHealthConnectManagement,
                onRefreshHealthConnectStatus = {
                    viewModel.refreshHealthConnectStatus()
                    viewModel.refreshDefaultWorkoutPlanMode()
                },
                onCycleTheme = viewModel::cycleTheme,
                onToggleDynamicColor = viewModel::setDynamicColorEnabled,
                onLogout = onLogout,
                onUpdateDefaultWorkoutPlanMode = viewModel::updateDefaultWorkoutPlanMode,
            )
        }
        composable(Screen.ProfileEdit.route) {
            ProfileEditScreen(
                onBack = { navController.popBackStack() },
                onAccountDeleted = onAccountDeleted,
            )
        }
        composable(
            Screen.WorkoutDetail.route,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { WorkoutDetailScreen(onBack = { navController.popBackStack() }) }
        composable(
            Screen.WorkoutGuide.route,
            arguments = listOf(navArgument("dateIso") { type = NavType.StringType }),
        ) {
            WorkoutGuideScreen(
                onBack = { navController.popBackStack() },
                onWorkoutCompleted = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_REFRESH_HOME, true)
                },
            )
        }
        composable(Screen.Badges.route) { BadgeScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.History.route) { HistoryScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.Statistics.route) { StatisticsScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.Goal.route) {
            GoalScreen(
                onBack = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_REFRESH_PROGRESS, true)
                    navController.popBackStack()
                },
            )
        }
    }
}
