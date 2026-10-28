package com.gunnys.eundunhealth.ui.profile

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHubScreen(
    state: ProfileHubUiState,
    onEditProfile: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectManagement: () -> Unit,
    onRefreshHealthConnectStatus: () -> Unit,
    onCycleTheme: () -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onLogout: () -> Unit,
    onUpdateDefaultWorkoutPlanMode: (WorkoutPlanMode?) -> Unit = {},
) {
    var showModeDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    RefreshOnResume(onRefreshHealthConnectStatus)
    Scaffold(topBar = { TopAppBar(title = { Text("프로필") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 내 정보
            ProfileSectionHeader("내 정보")
            state.defaultPlanModeError?.let { err ->
                AuthErrorBanner(error = err, screen = "profile_default_plan_mode")
            }
            SettingsRow(
                icon = Icons.Default.Person,
                title = "신체 정보 및 휴식 요일",
                summary = "운동 계획에 반영되는 신체 정보와 휴식일을 수정합니다",
                onClick = onEditProfile,
            )
            SettingsRow(
                icon = Icons.Default.FitnessCenter,
                title = "기본 운동 환경",
                summary = state.defaultWorkoutPlanMode.toPreferenceSummary(),
                onClick = { showModeDialog = true },
                enabled = !state.isUpdatingDefaultPlanMode,
            )

            // 2. 연동
            Spacer(Modifier.height(4.dp))
            ProfileSectionHeader("연동")
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
                icon = Icons.Default.HealthAndSafety,
                title = "Health Connect",
                summary = healthSummary,
                onClick = healthAction,
                enabled = state.isHealthConnectAvailable && !state.isHealthConnectLoading,
                showChevron = state.isHealthConnectAvailable && !state.isHealthConnectLoading,
            )

            // 3. 화면
            Spacer(Modifier.height(4.dp))
            ProfileSectionHeader("화면")
            SettingsRow(
                icon = Icons.Default.DarkMode,
                title = "테마",
                summary = state.themeMode.displayName(),
                onClick = onCycleTheme,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onToggleDynamicColor(!state.dynamicColorEnabled) },
                ) {
                    ListItem(
                        headlineContent = { Text("배경화면 색상 사용 (Dynamic Color)") },
                        supportingContent = {
                            Text(
                                if (state.dynamicColorEnabled) {
                                    "기기 배경화면 팔레트 사용 중"
                                } else {
                                    "은둔헬스 브랜드 그린 팔레트 사용 중"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                        trailingContent = {
                            // Card 자체에 onClick이 있으므로 Switch는 onCheckedChange = null로 두어
                            // TalkBack 이중 포커스를 방지하고 행 전체를 단일 48dp+ 타겟으로 동작시킨다.
                            Switch(
                                checked = state.dynamicColorEnabled,
                                onCheckedChange = null,
                            )
                        },
                    )
                }
            }

            // 4. 정보
            Spacer(Modifier.height(4.dp))
            ProfileSectionHeader("정보")
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("앱 버전") },
                    supportingContent = { Text("v${BuildConfig.VERSION_NAME}") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                )
            }

            // 5. 계정
            Spacer(Modifier.height(4.dp))
            ProfileSectionHeader("계정")
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showLogoutDialog = true },
            ) {
                ListItem(
                    headlineContent = { Text("로그아웃") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }

        if (showModeDialog) {
            DefaultWorkoutPlanModeDialog(
                currentMode = state.defaultWorkoutPlanMode,
                onSelect = { selected ->
                    showModeDialog = false
                    onUpdateDefaultWorkoutPlanMode(selected)
                },
                onDismiss = { showModeDialog = false },
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("로그아웃 하시겠어요?") },
                text = { Text("다시 로그인하면 저장된 운동 계획과 기록을 이어서 볼 수 있습니다.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            onLogout()
                        },
                    ) {
                        Text("로그아웃")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("취소")
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun DefaultWorkoutPlanModeDialog(
    currentMode: WorkoutPlanMode?,
    onSelect: (WorkoutPlanMode?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("기본 운동 환경") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "새 주간 운동 계획이 없을 때 자동으로 생성할 기본 환경을 설정합니다. 이미 생성된 이번 주 계획은 바뀌지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                DefaultPlanModeOptionRow(
                    label = "매주 직접 선택 (미설정)",
                    selected = currentMode == null,
                    onClick = { onSelect(null) },
                )
                DefaultPlanModeOptionRow(
                    label = "헬스장 — 장비를 사용하는 운동 계획",
                    selected = currentMode == WorkoutPlanMode.GYM,
                    onClick = { onSelect(WorkoutPlanMode.GYM) },
                )
                DefaultPlanModeOptionRow(
                    label = "홈트레이닝 — 장비 없이 하는 기본 동작 계획",
                    selected = currentMode == WorkoutPlanMode.HOME,
                    onClick = { onSelect(WorkoutPlanMode.HOME) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun DefaultPlanModeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
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
                { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
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

private fun WorkoutPlanMode?.toPreferenceSummary(): String = when (this) {
    WorkoutPlanMode.GYM -> "헬스장 · 새 주 시작 시 자동 생성"
    WorkoutPlanMode.HOME -> "홈트레이닝 · 새 주 시작 시 자동 생성"
    null -> "매주 직접 선택 (미설정)"
}
