package com.gunnys.eundunhealth.wear.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
    columnState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    val transformationSpec = rememberTransformationSpec()
    var isEnvExpanded by rememberSaveable { mutableStateOf(false) }
    var isStatusExpanded by rememberSaveable { mutableStateOf(false) }

    val expandLabel = stringResource(R.string.wear_diag_action_expand)
    val collapseLabel = stringResource(R.string.wear_diag_action_collapse)

    AppScaffold(modifier = modifier) {
        ScreenScaffold(scrollState = columnState) { contentPadding ->
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

                item(key = "env_card") {
                    val envActionLabel = if (isEnvExpanded) collapseLabel else expandLabel

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
                            .semantics {
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
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item(key = "status_card") {
                    val statusActionLabel = if (isStatusExpanded) collapseLabel else expandLabel

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
                            .semantics {
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
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
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
