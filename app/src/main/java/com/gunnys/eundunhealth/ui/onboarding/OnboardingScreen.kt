package com.gunnys.eundunhealth.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.ui.components.BodyMetricsSliders
import com.gunnys.eundunhealth.ui.components.ProfileSummaryCard

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var height by rememberSaveable { mutableFloatStateOf(170f) }
    var weight by rememberSaveable { mutableFloatStateOf(65f) }
    var bodyFat by rememberSaveable { mutableFloatStateOf(20f) }
    var muscleMass by rememberSaveable { mutableFloatStateOf(30f) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is OnboardingSideEffect.NavigateToHome -> onComplete()
                is OnboardingSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .animateContentSize(),
        ) {
            Text(
                "신체 정보 입력",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "맞춤 운동 계획을 위해 기본 정보를 입력해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))

            BodyMetricsSliders(
                height = height,
                onHeightChange = { height = it },
                weight = weight,
                onWeightChange = { weight = it },
                muscleMass = muscleMass,
                onMuscleMassChange = { muscleMass = it },
                bodyFat = bodyFat,
                onBodyFatChange = { bodyFat = it },
            )

            Spacer(modifier = Modifier.height(24.dp))

            ProfileSummaryCard(
                height = height,
                weight = weight,
                bodyFat = bodyFat,
                muscleMass = muscleMass,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "입력한 정보를 기반으로 맞춤 주간 운동 계획이 생성됩니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.saveProfile(height, weight, bodyFat, muscleMass) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text("운동 계획 받기")
            }
        }
    }
}
