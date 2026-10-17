package com.gunnys.eundunhealth.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHubScreen(
    state: ProfileHubUiState,
    onEditProfile: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectManagement: () -> Unit,
    onRefreshHealthConnectStatus: () -> Unit,
    onCycleTheme: () -> Unit,
    onLogout: () -> Unit,
) {
    RefreshOnResume(onRefreshHealthConnectStatus)
    Scaffold(topBar = { TopAppBar(title = { Text("프로필") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("계정 및 설정", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SettingsRow(Icons.Default.Person, "신체 정보", "운동 계획에 반영되는 정보를 수정합니다", onEditProfile)
            val healthSummary = when {
                state.isHealthConnectLoading -> "연결 상태를 확인하고 있습니다"
                !state.isHealthConnectAvailable -> "이 기기에서 사용할 수 없습니다"
                state.hasHealthPermission -> "운동 세션 읽기 권한 연결됨 · 관리 화면 열기"
                else -> "운동 세션 읽기 권한을 연결하세요"
            }
            val healthAction: () -> Unit = when {
                state.isHealthConnectLoading -> ({})
                !state.isHealthConnectAvailable -> ({})
                state.hasHealthPermission -> onOpenHealthConnectManagement
                else -> onRequestHealthPermissions
            }
            SettingsRow(
                Icons.Default.HealthAndSafety,
                "Health Connect",
                healthSummary,
                healthAction,
                enabled = state.isHealthConnectAvailable && !state.isHealthConnectLoading,
                showChevron = state.isHealthConnectAvailable && !state.isHealthConnectLoading,
            )
            SettingsRow(Icons.Default.DarkMode, "테마", state.themeMode.displayName(), onCycleTheme)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = onLogout) {
                ListItem(
                    headlineContent = { Text("로그아웃") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun RefreshOnResume(onRefresh: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, onRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick, enabled = enabled) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = if (showChevron) {
                { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            } else {
                null
            },
        )
    }
}

private fun com.gunnys.eundunhealth.data.preferences.ThemeMode.displayName(): String = when (this) {
    com.gunnys.eundunhealth.data.preferences.ThemeMode.SYSTEM -> "시스템 설정 따름"
    com.gunnys.eundunhealth.data.preferences.ThemeMode.LIGHT -> "라이트"
    com.gunnys.eundunhealth.data.preferences.ThemeMode.DARK -> "다크"
}
