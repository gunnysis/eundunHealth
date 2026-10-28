package com.gunnys.eundunhealth.ui.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.ProfileHistoryPoint
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.components.LineChart
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(
    onBack: () -> Unit,
    viewModel: GoalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.sideEffect) { effect ->
        when (effect) {
            is GoalSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("목표 & 진행") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val error = uiState.error
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            ErrorContent(
                error = error,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )
        } else {
            val latestPoint = uiState.history.lastOrNull()
            val firstPoint = uiState.history.firstOrNull()
            val weightGoal = uiState.goals.firstOrNull { it.type == GoalType.WEIGHT }
            val bodyFatGoal = uiState.goals.firstOrNull { it.type == GoalType.BODY_FAT }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GoalEditCard(
                    type = GoalType.WEIGHT,
                    current = weightGoal,
                    startValue = firstPoint?.weightKg,
                    latestValue = latestPoint?.weightKg,
                    isSaving = uiState.isSaving,
                    onSave = { v -> viewModel.saveGoal(GoalType.WEIGHT, v) },
                )
                GoalEditCard(
                    type = GoalType.BODY_FAT,
                    current = bodyFatGoal,
                    startValue = uiState.history.firstOrNull { it.bodyFatPct != null }?.bodyFatPct,
                    latestValue = uiState.history.lastOrNull { it.bodyFatPct != null }?.bodyFatPct,
                    isSaving = uiState.isSaving,
                    onSave = { v -> viewModel.saveGoal(GoalType.BODY_FAT, v) },
                )

                // 진행 차트
                if (uiState.history.isNotEmpty()) {
                    ProgressChartCard("체중 추이", uiState.history, "kg") { it.weightKg.toDouble() }
                    val bfHistory = uiState.history.filter { it.bodyFatPct != null }
                    if (bfHistory.isNotEmpty()) {
                        ProgressChartCard("체지방률 추이", bfHistory, "%") {
                            (it.bodyFatPct ?: 0f).toDouble()
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "체지방률 추이",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "체성분 기록이 아직 없습니다. 프로필 편집에서 선택 항목인 체지방률을 입력하면 추이 차트가 표시됩니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "프로필을 저장하면 진행 차트가 표시됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalEditCard(
    type: GoalType,
    current: Goal?,
    startValue: Float?,
    latestValue: Float?,
    isSaving: Boolean,
    onSave: (Float) -> Unit,
) {
    var text by rememberSaveable(current?.targetValue) {
        mutableStateOf(current?.targetValue?.toString().orEmpty())
    }
    val parsed = text.toFloatOrNull()
    val isValid = parsed != null && parsed > 0f && parsed <= 500f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("목표값 (${type.unit})") },
                    singleLine = true,
                    isError = text.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { parsed?.let(onSave) },
                    enabled = isValid && !isSaving,
                ) { Text("저장") }
            }
            if (current != null) {
                Spacer(Modifier.height(8.dp))
                if (latestValue != null) {
                    val diff = current.targetValue - latestValue
                    val diffLabel = if (abs(diff) < 0.05f) {
                        "목표 달성!"
                    } else if (diff > 0f) {
                        "목표까지 +${"%.1f".format(diff)}${type.unit}"
                    } else {
                        "목표까지 ${"%.1f".format(diff)}${type.unit}"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "현재 ${"%.1f".format(latestValue)}${type.unit} → 목표 ${"%.1f".format(current.targetValue)}${type.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = diffLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (startValue != null && abs(current.targetValue - startValue) > 0.1f) {
                        val totalSpan = abs(current.targetValue - startValue)
                        val remaining = abs(current.targetValue - latestValue)
                        val progress = ((totalSpan - remaining) / totalSpan).coerceIn(0f, 1f)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            strokeCap = StrokeCap.Round,
                        )
                    }
                } else {
                    Text(
                        "현재 목표: ${current.targetValue}${type.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressChartCard(
    title: String,
    points: List<ProfileHistoryPoint>,
    unit: String,
    selector: (ProfileHistoryPoint) -> Double,
) {
    val yValues = points.map(selector)
    val a11ySummary = remember(title, yValues, unit) {
        val first = yValues.firstOrNull()
        val last = yValues.lastOrNull()
        if (first != null && last != null) {
            "$title 차트: 시작 ${"%.1f".format(first)}$unit, 최근 ${"%.1f".format(last)}$unit (총 ${yValues.size}회 기록)"
        } else {
            "$title 차트"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            LineChart(
                yValues = yValues,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .semantics { contentDescription = a11ySummary },
            )
        }
    }
}
