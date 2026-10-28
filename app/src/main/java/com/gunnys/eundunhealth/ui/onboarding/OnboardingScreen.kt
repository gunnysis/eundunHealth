package com.gunnys.eundunhealth.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.DEFAULT_REST_DAY
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.components.BodyMetricDefaults
import com.gunnys.eundunhealth.ui.components.BodyMetricsSliders
import com.gunnys.eundunhealth.ui.components.GenderSelector
import com.gunnys.eundunhealth.ui.components.ProfileSummaryCard
import com.gunnys.eundunhealth.ui.components.RestDaysSelector
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var gender by rememberSaveable { mutableStateOf(Gender.MALE) }
    var hasUserTouchedMetrics by rememberSaveable { mutableStateOf(false) }
    var height by rememberSaveable { mutableFloatStateOf(BodyMetricDefaults.DEFAULT_MALE_HEIGHT_CM) }
    var weight by rememberSaveable { mutableFloatStateOf(BodyMetricDefaults.DEFAULT_MALE_WEIGHT_KG) }
    var bodyFat by rememberSaveable { mutableStateOf<Float?>(null) }
    var muscleMass by rememberSaveable { mutableStateOf<Float?>(null) }
    var restDays by rememberSaveable { mutableStateOf<List<Int>>(ArrayList(listOf(DEFAULT_REST_DAY))) }
    var selectedMode by rememberSaveable { mutableStateOf(WorkoutPlanMode.HOME) }
    var saveAsDefaultMode by rememberSaveable { mutableStateOf(true) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = step == 2 && !uiState.isLoading) {
        step = 1
    }

    ObserveAsEvents(viewModel.sideEffect) { effect ->
        when (effect) {
            is OnboardingSideEffect.NavigateToHome -> onComplete()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .animateContentSize(),
        ) {
            // 상단 2-Step 진행 표시줄
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (step == 1) "Step 1/2 · 기본 신체 정보" else "Step 2/2 · 운동 스타일 및 휴식일",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$step/2",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { step / 2f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                strokeCap = StrokeCap.Round,
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (step == 1) {
                Text(
                    text = "신체 정보 입력",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "맞춤 운동 루틴과 AI 식단 계산에 필요한 최소 정보만 입력해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(24.dp))

                GenderSelector(
                    gender = gender,
                    onGenderChange = { newGender ->
                        gender = newGender
                        if (!hasUserTouchedMetrics) {
                            height = BodyMetricDefaults.defaultHeightFor(newGender)
                            weight = BodyMetricDefaults.defaultWeightFor(newGender)
                        }
                    },
                )

                Spacer(modifier = Modifier.height(20.dp))

                BodyMetricsSliders(
                    height = height,
                    onHeightChange = {
                        hasUserTouchedMetrics = true
                        height = it
                    },
                    weight = weight,
                    onWeightChange = {
                        hasUserTouchedMetrics = true
                        weight = it
                    },
                    muscleMass = muscleMass,
                    onMuscleMassChange = { muscleMass = it },
                    bodyFat = bodyFat,
                    onBodyFatChange = { bodyFat = it },
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { step = 2 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text("다음 (운동 스타일 설정)", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "운동 스타일 및 휴식 요일",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "선호하는 운동 환경과 쉬고 싶은 요일을 선택하면 첫 주 루틴을 바로 만들어 드립니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                uiState.error?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    AuthErrorBanner(error = err, screen = "onboarding")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "선호하는 운동 환경",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OnboardingModeCard(
                        title = "홈트레이닝 (집에서 맨몸 위주)",
                        description = "별도 기구 없이 집에서 조용히 따라할 수 있는 7일 루틴",
                        selected = selectedMode == WorkoutPlanMode.HOME,
                        enabled = !uiState.isLoading,
                        onClick = { selectedMode = WorkoutPlanMode.HOME },
                    )
                    OnboardingModeCard(
                        title = "헬스장 (기구·덤벨·바벨 활용)",
                        description = "헬스장 머신과 프리웨이트를 활용하는 체계적인 분할 루틴",
                        selected = selectedMode == WorkoutPlanMode.GYM,
                        enabled = !uiState.isLoading,
                        onClick = { selectedMode = WorkoutPlanMode.GYM },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .toggleable(
                            value = saveAsDefaultMode,
                            enabled = !uiState.isLoading,
                            role = Role.Checkbox,
                            onValueChange = { saveAsDefaultMode = it },
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Checkbox(
                        checked = saveAsDefaultMode,
                        onCheckedChange = null,
                        enabled = !uiState.isLoading,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "매주 이 환경으로 새 주간 계획을 자동 생성하기",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                RestDaysSelector(
                    restDays = restDays,
                    onRestDaysChange = { restDays = ArrayList(it) },
                )

                Spacer(modifier = Modifier.height(20.dp))

                ProfileSummaryCard(
                    height = height,
                    weight = weight,
                    bodyFat = bodyFat,
                    muscleMass = muscleMass,
                    restDays = restDays,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { step = 1 },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                    ) {
                        Text("이전")
                    }
                    Button(
                        onClick = {
                            viewModel.saveProfile(
                                heightCm = height,
                                weightKg = weight,
                                gender = gender,
                                bodyFatPct = bodyFat,
                                muscleMassKg = muscleMass,
                                restDays = restDays,
                                selectedMode = selectedMode,
                                saveAsDefaultMode = saveAsDefaultMode,
                            )
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .weight(1.8f)
                            .heightIn(min = 52.dp),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("맞춤 루틴 생성 중...", textAlign = TextAlign.Center)
                        } else {
                            Text(
                                text = "첫 주 계획 시작하기",
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingModeCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
