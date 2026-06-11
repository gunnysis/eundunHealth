package com.gunnys.eundunhealth.ui.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.ui.components.BodyMetricsSliders
import com.gunnys.eundunhealth.ui.components.ProfileSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is ProfileSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ProfileSideEffect.SavedAndNavigateBack -> onBack()
                is ProfileSideEffect.NavigateToLogin -> onAccountDeleted()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("신체 정보 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState) {
            is ProfileUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProfileUiState.Empty -> {
                com.gunnys.eundunhealth.ui.components.EmptyContent(
                    message = "프로필 정보를 찾을 수 없습니다",
                    modifier = Modifier.padding(padding),
                    actionLabel = "다시 시도",
                    onAction = { viewModel.loadProfile() },
                )
            }
            is ProfileUiState.Error -> {
                com.gunnys.eundunhealth.ui.components.ErrorContent(
                    error = state.error,
                    modifier = Modifier.padding(padding),
                    onRetry = { viewModel.loadProfile() },
                )
            }
            is ProfileUiState.Loaded -> {
                ProfileEditContent(
                    initialHeight = state.profile.heightCm,
                    initialWeight = state.profile.weightKg,
                    initialBodyFat = state.profile.bodyFatPercent ?: 20f,
                    initialMuscleMass = state.profile.muscleMassKg ?: 30f,
                    initialRestDay = state.profile.restDay,
                    isSaving = state.isSaving,
                    isDeleting = state.isDeleting,
                    onSave = viewModel::saveProfile,
                    onDeleteClick = { showDeleteDialog = true },
                    modifier = Modifier.padding(padding),
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("계정 삭제") },
                text = {
                    Text(
                        "계정을 삭제하면 모든 운동 기록·배지·프로필이 영구적으로 사라지며, " +
                            "이 작업은 되돌릴 수 없습니다. 그래도 삭제하시겠습니까?",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteAccount()
                        },
                    ) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("취소")
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileEditContent(
    initialHeight: Float,
    initialWeight: Float,
    initialBodyFat: Float,
    initialMuscleMass: Float,
    initialRestDay: Int,
    isSaving: Boolean,
    isDeleting: Boolean,
    onSave: (Float, Float, Float, Float, Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var height by rememberSaveable { mutableFloatStateOf(initialHeight) }
    var weight by rememberSaveable { mutableFloatStateOf(initialWeight) }
    var bodyFat by rememberSaveable { mutableFloatStateOf(initialBodyFat) }
    var muscleMass by rememberSaveable { mutableFloatStateOf(initialMuscleMass) }
    var restDay by rememberSaveable { mutableIntStateOf(initialRestDay.coerceIn(1, 7)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .animateContentSize(),
    ) {
        Text(
            "운동 계획에 반영할 신체 정보를 수정해주세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

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

        RestDaySelector(restDay = restDay, onRestDayChange = { restDay = it })

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSummaryCard(
            height = height,
            weight = weight,
            bodyFat = bodyFat,
            muscleMass = muscleMass,
            title = "변경 요약",
        )
        Spacer(modifier = Modifier.height(12.dp))

        ProfileActionButtons(
            isSaving = isSaving,
            isDeleting = isDeleting,
            onSave = { onSave(height, weight, bodyFat, muscleMass, restDay) },
            onDeleteClick = onDeleteClick,
        )

        Spacer(modifier = Modifier.height(24.dp))
        AppVersionLabel(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun AppVersionLabel(modifier: Modifier = Modifier) {
    Text(
        text = "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun RestDaySelector(restDay: Int, onRestDayChange: (Int) -> Unit) {
    Text(
        "휴식일",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(modifier = Modifier.height(8.dp))
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        dayLabels.forEachIndexed { idx, label ->
            val dayNumber = idx + 1
            SegmentedButton(
                selected = restDay == dayNumber,
                onClick = { onRestDayChange(dayNumber) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = dayLabels.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun ProfileActionButtons(
    isSaving: Boolean,
    isDeleting: Boolean,
    onSave: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSaving && !isDeleting,
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text("저장하기")
    }

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedButton(
        onClick = onDeleteClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSaving && !isDeleting,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text("계정 삭제")
    }
}
