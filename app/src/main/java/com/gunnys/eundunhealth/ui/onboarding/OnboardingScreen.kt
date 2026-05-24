package com.gunnys.eundunhealth.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gunnys.eundunhealth.ui.components.ProfileSummaryCard

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var height by rememberSaveable { mutableFloatStateOf(170f) }
    var weight by rememberSaveable { mutableFloatStateOf(65f) }
    var bodyFat by rememberSaveable { mutableFloatStateOf(20f) }
    var muscleMass by rememberSaveable { mutableFloatStateOf(30f) }
    val saved by viewModel.saved.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saved) {
        if (saved) onComplete()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.userMessage)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .animateContentSize()
        ) {
            Text("신체 정보 입력", style = MaterialTheme.typography.headlineMedium)
            Text(
                "맞춤 운동 계획을 위해 기본 정보를 입력해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            ProfileSlider("키", height, 140f..210f, "cm", 0) { height = it }
            ProfileSlider("몸무게", weight, 40f..150f, "kg", 1) { weight = it }
            ProfileSlider("근육량", muscleMass, 10f..60f, "kg", 1) { muscleMass = it }
            ProfileSlider("체지방률", bodyFat, 5f..50f, "%", 1) { bodyFat = it }

            Spacer(modifier = Modifier.height(24.dp))

            ProfileSummaryCard(
                height = height,
                weight = weight,
                bodyFat = bodyFat,
                muscleMass = muscleMass
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "입력한 정보를 기반으로 맞춤 주간 운동 계획이 생성됩니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.saveProfile(height, weight, bodyFat, muscleMass) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text("운동 계획 받기")
            }
        }
    }
}

@Composable
fun ProfileSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    decimals: Int,
    onValueChange: (Float) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val formatPattern = if (decimals == 0) "%.0f" else "%.${decimals}f"
    var textValue by remember(value) { mutableStateOf(formatPattern.format(value)) }
    val isError = textValue.toFloatOrNull()?.let { it !in range } ?: textValue.isNotEmpty()

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input
                        input.toFloatOrNull()?.let { parsed ->
                            if (parsed in range) onValueChange(parsed)
                        }
                    },
                    modifier = Modifier.width(90.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                    ),
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (isError) {
            Text(
                "${range.start.toInt()}~${range.endInclusive.toInt()}$unit 범위로 입력해주세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
