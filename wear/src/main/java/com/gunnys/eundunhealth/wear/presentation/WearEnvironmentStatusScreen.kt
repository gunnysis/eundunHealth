package com.gunnys.eundunhealth.wear.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.gunnys.eundunhealth.wear.R
import com.gunnys.eundunhealth.wear.presentation.theme.EundunWearTheme

@Composable
fun WearEnvironmentStatusScreen(
    spec: WearEnvironmentSpec,
    modifier: Modifier = Modifier,
    biometricState: WearBiometricUiState = WearBiometricUiState.previewSample(),
    onRequestPermissions: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    columnState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    initialEnvExpanded: Boolean = false,
    initialStatusExpanded: Boolean = false,
) {
    val transformationSpec = rememberTransformationSpec()
    var isEnvExpanded by rememberSaveable(initialEnvExpanded) { mutableStateOf(initialEnvExpanded) }
    var isStatusExpanded by rememberSaveable(initialStatusExpanded) { mutableStateOf(initialStatusExpanded) }

    val expandLabel = stringResource(R.string.wear_diag_action_expand)
    val collapseLabel = stringResource(R.string.wear_diag_action_collapse)
    val expandedStateDesc = stringResource(R.string.wear_diag_state_expanded)
    val collapsedStateDesc = stringResource(R.string.wear_diag_state_collapsed)
    val scrollCaptureInProgress = LocalScrollCaptureInProgress.current

    AppScaffold(modifier = modifier) {
        ScreenScaffold(
            scrollState = columnState,
            scrollIndicator = {
                if (!scrollCaptureInProgress) {
                    ScrollIndicator(state = columnState)
                }
            },
        ) { contentPadding ->
            TransformingLazyColumn(
                state = columnState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    ListHeader(
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ListHeaderDefaults.minimumTopListContentPadding,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.wear_diag_header),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item(key = "live_biometric_card") {
                    LiveBiometricCard(
                        biometricState = biometricState,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                CardDefaults.minimumVerticalListContentPadding,
                            ),
                        onRequestPermissions = onRequestPermissions,
                        onSyncNow = onSyncNow,
                    )
                }

                item(key = "zone_distribution_card") {
                    ZoneDistributionCard(
                        biometricState = biometricState,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                CardDefaults.minimumVerticalListContentPadding,
                            ),
                        onSyncNow = onSyncNow,
                    )
                }

                item(key = "sync_action_card") {
                    SyncActionCard(
                        biometricState = biometricState,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                CardDefaults.minimumVerticalListContentPadding,
                            ),
                        onRequestPermissions = onRequestPermissions,
                        onSyncNow = onSyncNow,
                    )
                }

                item(key = "env_card") {
                    val envActionLabel = if (isEnvExpanded) collapseLabel else expandLabel
                    val envStateDesc = if (isEnvExpanded) expandedStateDesc else collapsedStateDesc

                    TitleCard(
                        onClick = { isEnvExpanded = !isEnvExpanded },
                        title = {
                            Text(text = stringResource(R.string.wear_diag_env_title))
                        },
                        subtitle = {
                            Text(
                                text =
                                if (isEnvExpanded) {
                                    stringResource(
                                        R.string.wear_diag_env_subtitle_expanded,
                                        spec.minSdkInt,
                                        spec.targetSdkInt,
                                        spec.timeZoneId,
                                    )
                                } else {
                                    stringResource(
                                        R.string.wear_diag_env_subtitle_compact,
                                        spec.targetSdkInt,
                                        spec.timeZoneId,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                CardDefaults.minimumVerticalListContentPadding,
                            )
                            .semantics(mergeDescendants = true) {
                                stateDescription = envStateDesc
                                onClick(label = envActionLabel, action = null)
                            },
                    ) {
                        Text(
                            text =
                            stringResource(
                                R.string.wear_diag_env_version_api,
                                spec.versionName,
                                spec.wearVersionCode,
                                spec.runtimeSdkInt,
                            ),
                            style =
                            if (isEnvExpanded) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.bodyExtraSmall
                            },
                        )
                    }
                }

                item(key = "status_card") {
                    val statusActionLabel = if (isStatusExpanded) collapseLabel else expandLabel
                    val statusStateDesc = if (isStatusExpanded) expandedStateDesc else collapsedStateDesc

                    TitleCard(
                        onClick = { isStatusExpanded = !isStatusExpanded },
                        title = {
                            Text(text = stringResource(R.string.wear_diag_status_title))
                        },
                        subtitle = {
                            Text(
                                text = stringResource(R.string.wear_diag_status_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                CardDefaults.minimumVerticalListContentPadding,
                            )
                            .semantics(mergeDescendants = true) {
                                stateDescription = statusStateDesc
                                onClick(label = statusActionLabel, action = null)
                            },
                    ) {
                        Text(
                            text =
                            if (isStatusExpanded) {
                                stringResource(R.string.wear_diag_status_detail)
                            } else {
                                stringResource(R.string.wear_diag_status_summary)
                            },
                            style =
                            if (isStatusExpanded) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBiometricCard(
    biometricState: WearBiometricUiState,
    transformation: SurfaceTransformation,
    modifier: Modifier,
    onRequestPermissions: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val summary = biometricState.todaySummary
    val bpmText = biometricState.liveBpm?.let { "$it BPM" } ?: "-- BPM"
    val recoveryText = summary?.recoveryScore?.let { "회복 ${it}점" } ?: "회복 산출 중"
    val restingText = summary?.restingBpm?.let { "안정 $it" } ?: "안정 --"
    val steps = summary?.totalSteps ?: 0
    val cal = summary?.activeCaloriesKcal?.toInt() ?: 0

    TitleCard(
        onClick = {
            if (!biometricState.hasPermissions) {
                onRequestPermissions()
            } else {
                onSyncNow()
            }
        },
        title = {
            Text(text = "$bpmText · $recoveryText")
        },
        subtitle = {
            Text(
                text = "${biometricState.currentContextKoreanLabel} · $restingText",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        transformation = transformation,
        modifier = modifier,
    ) {
        Text(
            text = "${biometricState.sensorStatusText} · ${steps}보 · ${cal}kcal",
            style = MaterialTheme.typography.bodyExtraSmall,
        )
    }
}

@Composable
private fun ZoneDistributionCard(
    biometricState: WearBiometricUiState,
    transformation: SurfaceTransformation,
    modifier: Modifier,
    onSyncNow: () -> Unit,
) {
    val summary = biometricState.todaySummary
    val monitored = summary?.totalMonitoredMinutes ?: 0
    val relaxed = summary?.relaxedZoneMinutes ?: 0
    val normal = summary?.normalZoneMinutes ?: 0
    val elevated = summary?.elevatedTensionMinutes ?: 0
    val highLoad = summary?.highLoadMinutes ?: 0
    val rapidSpikes = summary?.rapidDeltaSpikeCount ?: 0
    val tensionCount = summary?.tensionEpisodeCount ?: 0
    val hrrLabel = summary?.avgRecoveryDropBpm?.let { "회복폭 -${it}BPM" } ?: "회복폭 대기"

    TitleCard(
        onClick = onSyncNow,
        title = {
            Text(text = "착용 ${monitored}분 전 구간 분석")
        },
        subtitle = {
            Text(
                text = "이완 ${relaxed}분 · 일상 ${normal}분 · 긴장 ${elevated}분 · 운동 ${highLoad}분",
                style = MaterialTheme.typography.bodyExtraSmall,
            )
        },
        transformation = transformation,
        modifier = modifier,
    ) {
        Text(
            text = "급상승 ${rapidSpikes}회 · 지속긴장 ${tensionCount}회 · $hrrLabel",
            style = MaterialTheme.typography.bodyExtraSmall,
        )
    }
}

@Composable
private fun SyncActionCard(
    biometricState: WearBiometricUiState,
    transformation: SurfaceTransformation,
    modifier: Modifier,
    onRequestPermissions: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val summary = biometricState.todaySummary
    val actionTitle =
        when {
            !biometricState.hasPermissions -> "센서 권한 허용하기"
            biometricState.isSyncing -> "동기화 진행 중..."
            else -> "스마트폰 즉시 동기화"
        }
    val sleepAwakenings = summary?.sleepAwakeningCount ?: 0

    TitleCard(
        onClick = {
            if (!biometricState.hasPermissions) {
                onRequestPermissions()
            } else {
                onSyncNow()
            }
        },
        title = {
            Text(text = actionTitle)
        },
        subtitle = {
            Text(
                text = biometricState.syncStatusMessage,
                style = MaterialTheme.typography.bodyExtraSmall,
            )
        },
        transformation = transformation,
        modifier = modifier,
    ) {
        Text(
            text = "야간 각성 ${sleepAwakenings}회 · 24시간 자동 감시",
            style = MaterialTheme.typography.bodyExtraSmall,
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
internal fun WearEnvironmentStatusScreenPreview() {
    EundunWearTheme {
        WearEnvironmentStatusScreen(
            spec =
            WearEnvironmentSpec(
                versionName = "0.7.3",
                wearVersionCode = 10001,
                minSdkInt = 36,
                targetSdkInt = 37,
                runtimeSdkInt = 36,
                timeZoneId = "Asia/Seoul",
            ),
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
internal fun WearEnvironmentStatusScreenSmallRoundPreview() {
    EundunWearTheme {
        WearEnvironmentStatusScreen(
            spec =
            WearEnvironmentSpec(
                versionName = "0.7.3",
                wearVersionCode = 10001,
                minSdkInt = 36,
                targetSdkInt = 37,
                runtimeSdkInt = 36,
                timeZoneId = "Asia/Seoul",
            ),
        )
    }
}

@Preview(
    device = WearDevices.SMALL_ROUND,
    showSystemUi = true,
    fontScale = 1.24f,
)
@Composable
internal fun WearEnvironmentStatusScreenLargeFontPreview() {
    EundunWearTheme {
        WearEnvironmentStatusScreen(
            spec =
            WearEnvironmentSpec(
                versionName = "0.7.3",
                wearVersionCode = 10001,
                minSdkInt = 36,
                targetSdkInt = 37,
                runtimeSdkInt = 36,
                timeZoneId = "Asia/Seoul",
            ),
        )
    }
}
