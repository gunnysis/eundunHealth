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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.ProfileHistoryPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(
    onBack: () -> Unit,
    viewModel: GoalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.userMessage)
            viewModel.clearError()
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
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 목표 입력 카드 — 두 가지 타입을 한 화면에서 관리
                GoalEditCard(
                    type = GoalType.WEIGHT,
                    current = uiState.goals.firstOrNull { it.type == GoalType.WEIGHT },
                    isSaving = uiState.isSaving,
                    onSave = { v -> viewModel.saveGoal(GoalType.WEIGHT, v) },
                )
                GoalEditCard(
                    type = GoalType.BODY_FAT,
                    current = uiState.goals.firstOrNull { it.type == GoalType.BODY_FAT },
                    isSaving = uiState.isSaving,
                    onSave = { v -> viewModel.saveGoal(GoalType.BODY_FAT, v) },
                )

                // 진행 차트
                if (uiState.history.isNotEmpty()) {
                    ProgressChartCard("체중 추이", uiState.history) { it.weightKg.toDouble() }
                    val bfHistory = uiState.history.filter { it.bodyFatPct != null }
                    if (bfHistory.isNotEmpty()) {
                        ProgressChartCard("체지방률 추이", bfHistory) {
                            (it.bodyFatPct ?: 0f).toDouble()
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(type.label, style = MaterialTheme.typography.titleMedium)
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
                Spacer(Modifier.height(4.dp))
                Text(
                    "현재 목표: ${current.targetValue}${type.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgressChartCard(
    title: String,
    points: List<ProfileHistoryPoint>,
    selector: (ProfileHistoryPoint) -> Double,
) {
    val producer = remember { CartesianChartModelProducer() }
    val yValues = points.map(selector)

    LaunchedEffect(points) {
        if (yValues.isNotEmpty()) {
            producer.runTransaction { lineSeries { series(yValues) } }
        }
    }
    remember(points) {
        if (yValues.isNotEmpty()) {
            runBlocking { producer.runTransaction { lineSeries { series(yValues) } } }
        }
        Unit
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                            ),
                        ),
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        tickPosition = BaseAxis.TickPosition.Inside,
                    ),
                ),
                modelProducer = producer,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                scrollState = rememberVicoScrollState(scrollEnabled = false),
            )
        }
    }
}
