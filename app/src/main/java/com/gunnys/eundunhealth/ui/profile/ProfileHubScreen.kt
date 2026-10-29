package com.gunnys.eundunhealth.ui.profile

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.domain.model.FitnessLevel
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.TOTAL_DAYS_IN_WEEK
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.normalizeRestDays
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.components.DAY_SHORT_LABELS
import com.gunnys.eundunhealth.ui.theme.EundunTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHubScreen(
    state: ProfileHubUiState,
    onEditProfile: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectManagement: () -> Unit,
    onRefreshHealthConnectStatus: () -> Unit,
    onCycleTheme: () -> Unit,
    onSelectThemeMode: ((ThemeMode) -> Unit)? = null,
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onLogout: () -> Unit,
    onUpdateDefaultWorkoutPlanMode: (WorkoutPlanMode?) -> Unit = {},
) {
    var showModeDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    RefreshOnResume(onRefreshHealthConnectStatus)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("프로필") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.defaultPlanModeError?.let { err ->
                AuthErrorBanner(error = err, screen = "profile_default_plan_mode")
            }

            // 1. 내 신체 & 루틴 요약 대시보드 히어로 (기존 '신체 정보 및 휴식 요일' 정적 행 대체)
            ProfileBodyHeroCard(
                profile = state.profile,
                onEditProfile = onEditProfile,
            )

            // 2. 운동 환경 & Health Connect 연동 (단일 통합 그룹 카드)
            ProfileSectionHeader(
                title = "운동 환경 및 기기 연동",
                subtitle = "새 주간 루틴 기본 모드와 Health Connect 운동 세션 동기화",
            )
            WorkoutAndHealthIntegrationCard(
                state = state,
                onOpenModeDialog = { showModeDialog = true },
                onRequestHealthPermissions = onRequestHealthPermissions,
                onOpenHealthConnectManagement = onOpenHealthConnectManagement,
            )

            // 3. 화면 스타일 (테마 3버튼 직접 선택 + Dynamic Color 통합 카드)
            ProfileSectionHeader(
                title = "화면 스타일",
                subtitle = "앱 테마 모드 및 팔레트 설정",
            )
            AppearanceSettingsCard(
                themeMode = state.themeMode,
                dynamicColorEnabled = state.dynamicColorEnabled,
                onCycleTheme = onCycleTheme,
                onSelectThemeMode = onSelectThemeMode,
                onToggleDynamicColor = onToggleDynamicColor,
            )

            // 4. 간결한 계정 로그아웃 & 하단 버전 캡션 (불필요한 '정보 / 계정' 2개 섹션 과감히 정리)
            AccountAndVersionFooter(
                onLogoutClick = { showLogoutDialog = true },
            )
            Spacer(modifier = Modifier.height(8.dp))
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
private fun ProfileSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 상단 '신체 & 루틴 대시보드 히어로 카드':
 * 기존에 아무 수치도 보여주지 않던 정적 텍스트 행을 대체하여,
 * 키·몸무게·BMI·운동 수준 배지·골격근량/체지방률·주간 운동/휴식 요일 스탬프를 한눈에 시각화.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileBodyHeroCard(
    profile: UserProfile?,
    onEditProfile: () -> Unit,
) {
    Card(
        onClick = onEditProfile,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "신체 정보 및 휴식 요일",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        profile?.let { p ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            ) {
                                Text(
                                    text = "${p.gender.toGenderLabel()} · ${p.fitnessLevel.toFitnessLevelLabel()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "운동 계획에 반영되는 신체 정보와 휴식일을 수정합니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "신체 정보 수정",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                    )
                }
            }

            if (profile != null) {
                BodyMetricMiniGrid(profile = profile)
                RestDayVisualStrip(restDays = profile.restDays)
            }
        }
    }
}

@Composable
private fun BodyMetricMiniGrid(profile: UserProfile) {
    val bodyCompValue = when {
        profile.muscleMassKg != null && profile.bodyFatPercent != null ->
            "${"%.1f".format(profile.muscleMassKg)}kg · ${"%.1f".format(profile.bodyFatPercent)}%"
        profile.muscleMassKg != null ->
            "근육 ${"%.1f".format(profile.muscleMassKg)}kg"
        profile.bodyFatPercent != null ->
            "체지방 ${"%.1f".format(profile.bodyFatPercent)}%"
        else -> "미입력"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BodyStatPill(
            label = "신장 · 체중",
            value = "${"%.0f".format(profile.heightCm)}cm · ${"%.1f".format(profile.weightKg)}kg",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        BodyStatPill(
            label = "BMI 지수",
            value = "${"%.1f".format(profile.bmi)} (${profile.bmi.bmiCategoryLabel()})",
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight(),
        )
        BodyStatPill(
            label = "골격근 · 체지방",
            value = bodyCompValue,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun BodyStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestDayVisualStrip(restDays: List<Int>) {
    val normalized = normalizeRestDays(restDays).toSet()
    val workoutDays = TOTAL_DAYS_IN_WEEK - normalized.size
    val restDayNames = normalized.joinToString("·") { DAY_SHORT_LABELS[it - 1] }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "주 ${workoutDays}일 운동 · 휴식 ($restDayNames)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_SHORT_LABELS.forEachIndexed { index, dayLabel ->
                    val isRest = (index + 1) in normalized
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRest) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRest) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutAndHealthIntegrationCard(
    state: ProfileHubUiState,
    onOpenModeDialog: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectManagement: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DefaultWorkoutEnvironmentRow(
                defaultMode = state.defaultWorkoutPlanMode,
                isUpdating = state.isUpdatingDefaultPlanMode,
                onClick = onOpenModeDialog,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            HealthConnectIntegrationRow(
                state = state,
                onRequestHealthPermissions = onRequestHealthPermissions,
                onOpenHealthConnectManagement = onOpenHealthConnectManagement,
            )
        }
    }
}

@Composable
private fun DefaultWorkoutEnvironmentRow(
    defaultMode: WorkoutPlanMode?,
    isUpdating: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !isUpdating,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                TintedIconBadge(
                    icon = Icons.Default.FitnessCenter,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
            headlineContent = {
                Text(
                    text = "기본 운동 환경",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(defaultMode.toPreferenceSummary())
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun HealthConnectIntegrationRow(
    state: ProfileHubUiState,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectManagement: () -> Unit,
) {
    val (healthSummary, healthBadgeText) = resolveHealthConnectLabels(state)
    val isHealthClickable = state.isHealthConnectAvailable && !state.isHealthConnectLoading
    val healthAction: () -> Unit = if (state.hasHealthPermission) {
        onOpenHealthConnectManagement
    } else {
        onRequestHealthPermissions
    }

    if (isHealthClickable) {
        Surface(
            onClick = healthAction,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HealthConnectListItemContent(
                hasPermission = state.hasHealthPermission,
                badgeText = healthBadgeText,
                summary = healthSummary,
                showChevron = true,
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            HealthConnectListItemContent(
                hasPermission = state.hasHealthPermission,
                badgeText = healthBadgeText,
                summary = healthSummary,
                showChevron = false,
            )
        }
    }
}

private fun resolveHealthConnectLabels(state: ProfileHubUiState): Pair<String, String> = when {
    state.isHealthConnectLoading -> "연결 상태를 확인하고 있습니다" to "확인 중"
    !state.isHealthConnectAvailable -> "이 기기에서 사용할 수 없습니다" to "미지원"
    state.hasHealthPermission -> "운동 세션 읽기 권한 연결됨 · 관리 화면 열기" to "연결됨"
    else -> "운동 세션 읽기 권한을 연결하세요" to "권한 필요"
}

@Composable
private fun HealthConnectListItemContent(
    hasPermission: Boolean,
    badgeText: String,
    summary: String,
    showChevron: Boolean,
) {
    val extendedColors = EundunTheme.extendedColors
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            TintedIconBadge(
                icon = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.HealthAndSafety,
                containerColor = if (hasPermission) {
                    extendedColors.successContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                contentColor = if (hasPermission) {
                    extendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
            )
        },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Health Connect",
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (hasPermission) {
                        extendedColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasPermission) {
                            extendedColors.onSuccessContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        },
        supportingContent = { Text(summary) },
        trailingContent = if (showChevron) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun AppearanceSettingsCard(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    onCycleTheme: () -> Unit,
    onSelectThemeMode: ((ThemeMode) -> Unit)?,
    onToggleDynamicColor: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 테마 설정: 행 탭 시 순환 + 하단 3버튼 세그먼트 칩으로 원탭 직접 선택 지원
            Surface(
                onClick = onCycleTheme,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TintedIconBadge(
                            icon = Icons.Default.DarkMode,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "테마",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = themeMode.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (onSelectThemeMode != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ThemeModeSegmentedRow(
                            selectedMode = themeMode,
                            onSelectMode = onSelectThemeMode,
                        )
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                Surface(
                    onClick = { onToggleDynamicColor(!dynamicColorEnabled) },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            TintedIconBadge(
                                icon = Icons.Default.Palette,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = "배경화면 색상 사용 (Dynamic Color)",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        supportingContent = {
                            Text(
                                if (dynamicColorEnabled) {
                                    "기기 배경화면 팔레트 사용 중"
                                } else {
                                    "은둔헬스 브랜드 그린 팔레트 사용 중"
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = dynamicColorEnabled,
                                onCheckedChange = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeSegmentedRow(
    selectedMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit,
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "자동", Icons.Default.SettingsBrightness),
        Triple(ThemeMode.LIGHT, "라이트", Icons.Default.LightMode),
        Triple(ThemeMode.DARK, "다크", Icons.Default.DarkMode),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (mode, label, icon) ->
            val selected = selectedMode == mode
            Surface(
                onClick = { onSelectMode(mode) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                },
                border = if (selected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountAndVersionFooter(
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "로그아웃",
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = "은둔헬스 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TintedIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
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

private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "시스템 설정 따름"
    ThemeMode.LIGHT -> "라이트"
    ThemeMode.DARK -> "다크"
}

private fun WorkoutPlanMode?.toPreferenceSummary(): String = when (this) {
    WorkoutPlanMode.GYM -> "헬스장 · 새 주 시작 시 자동 생성"
    WorkoutPlanMode.HOME -> "홈트레이닝 · 새 주 시작 시 자동 생성"
    null -> "매주 직접 선택 (미설정)"
}

private fun Gender.toGenderLabel(): String = when (this) {
    Gender.MALE -> "남성"
    Gender.FEMALE -> "여성"
    Gender.UNSPECIFIED -> "기본"
}

private fun FitnessLevel.toFitnessLevelLabel(): String = when (this) {
    FitnessLevel.BEGINNER -> "입문 코칭"
    FitnessLevel.INTERMEDIATE -> "중급 코칭"
    FitnessLevel.ADVANCED -> "숙련 코칭"
}

private fun Float.bmiCategoryLabel(): String = when {
    this < 18.5f -> "저체중"
    this < 23.0f -> "정상"
    this < 25.0f -> "과체중"
    else -> "비만"
}
