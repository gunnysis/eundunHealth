package com.gunnys.eundunhealth.ui.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.components.BodyMetricsSliders
import com.gunnys.eundunhealth.ui.components.GenderSelector
import com.gunnys.eundunhealth.ui.components.ProfileSummaryCard
import com.gunnys.eundunhealth.ui.components.RestDaysSelector
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    ObserveAsEvents(viewModel.sideEffect) { effect ->
        when (effect) {
            is ProfileSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            is ProfileSideEffect.SavedAndNavigateBack -> onBack()
            is ProfileSideEffect.NavigateToLogin -> onAccountDeleted()
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
                val safeInitialGender = if (state.profile.gender == Gender.UNSPECIFIED) {
                    Gender.MALE
                } else {
                    state.profile.gender
                }
                ProfileEditContent(
                    initialGender = safeInitialGender,
                    initialHeight = state.profile.heightCm,
                    initialWeight = state.profile.weightKg,
                    initialBodyFat = state.profile.bodyFatPercent,
                    initialMuscleMass = state.profile.muscleMassKg,
                    initialRestDays = state.profile.restDays,
                    isSaving = state.isSaving,
                    isDeleting = state.isDeleting,
                    saveError = state.saveError,
                    deleteError = state.deleteError,
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

@Suppress("LongParameterList")
@Composable
private fun ProfileEditContent(
    initialGender: Gender,
    initialHeight: Float,
    initialWeight: Float,
    initialBodyFat: Float?,
    initialMuscleMass: Float?,
    initialRestDays: List<Int>,
    isSaving: Boolean,
    isDeleting: Boolean,
    saveError: AppError?,
    deleteError: AppError?,
    onSave: (Float, Float, Gender, Float?, Float?, List<Int>) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var gender by rememberSaveable(initialGender) { mutableStateOf(initialGender) }
    var height by rememberSaveable(initialHeight) { mutableFloatStateOf(initialHeight) }
    var weight by rememberSaveable(initialWeight) { mutableFloatStateOf(initialWeight) }
    var bodyFat by rememberSaveable(initialBodyFat) { mutableStateOf(initialBodyFat) }
    var muscleMass by rememberSaveable(initialMuscleMass) { mutableStateOf(initialMuscleMass) }
    var restDays by rememberSaveable(initialRestDays) { mutableStateOf<List<Int>>(ArrayList(initialRestDays)) }

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

        GenderSelector(gender = gender, onGenderChange = { gender = it })

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

        RestDaysSelector(restDays = restDays, onRestDaysChange = { restDays = ArrayList(it) })

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSummaryCard(
            height = height,
            weight = weight,
            bodyFat = bodyFat,
            muscleMass = muscleMass,
            title = "변경 요약",
            restDays = restDays,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "변경된 신체 정보와 휴식 요일은 다음 주간 운동 계획 생성 시부터 반영됩니다 (이미 생성된 이번 주 계획은 유지됩니다)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))

        saveError?.let { err ->
            Spacer(modifier = Modifier.height(4.dp))
            AuthErrorBanner(error = err, screen = "profile_save")
            Spacer(modifier = Modifier.height(8.dp))
        }

        ProfileActionButtons(
            isSaving = isSaving,
            isDeleting = isDeleting,
            deleteError = deleteError,
            onSave = { onSave(height, weight, gender, bodyFat, muscleMass, restDays) },
            onDeleteClick = onDeleteClick,
        )

        Spacer(modifier = Modifier.height(24.dp))
        ExerciseDataAttribution(modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(4.dp))
        AppVersionLabel(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ExerciseDataAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri("https://oss.exercisedb.dev/docs") },
        modifier = modifier,
    ) {
        Text("운동 데이터 제공: AscendAPI ExerciseDB", style = MaterialTheme.typography.bodySmall)
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
private fun ProfileActionButtons(
    isSaving: Boolean,
    isDeleting: Boolean,
    deleteError: AppError?,
    onSave: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Button(
        onClick = onSave,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        enabled = !isSaving && !isDeleting,
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text("저장하기")
    }

    Spacer(modifier = Modifier.height(32.dp))

    deleteError?.let { err ->
        AuthErrorBanner(error = err, screen = "profile_delete")
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedButton(
        onClick = onDeleteClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        enabled = !isSaving && !isDeleting,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text("계정 삭제")
    }
}
