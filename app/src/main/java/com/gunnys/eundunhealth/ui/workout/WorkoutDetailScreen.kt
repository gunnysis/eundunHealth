package com.gunnys.eundunhealth.ui.workout

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.ui.components.ErrorContent
import kotlinx.coroutines.launch

/** 복사용 텍스트: 운동 이름과 번호를 매긴 방법을 한 번에 복사한다. */
private fun buildCopyText(ex: Exercise): String = buildString {
    append(ex.name)
    append("\n\n")
    ex.instructions.forEachIndexed { i, step -> append("${i + 1}. $step\n") }
}.trimEnd()

/**
 * 운동 상세 화면.
 *
 * `exerciseId` 는 파라미터로 받지 않는다 — `WorkoutDetailViewModel` 이 `SavedStateHandle` 로
 * 네비 인자에서 직접 읽으므로, 여기서 또 받으면 미사용 인자가 된다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (uiState as? WorkoutDetailUiState.Loaded)?.exercise?.name ?: "운동 상세",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is WorkoutDetailUiState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is WorkoutDetailUiState.Error -> {
                ErrorContent(
                    error = state.error,
                    modifier = Modifier.padding(padding),
                    onRetry = { viewModel.load() },
                )
            }
            is WorkoutDetailUiState.Loaded -> {
                val ex = state.exercise
                val context = LocalContext.current
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()
                val prescriptionText = when (ex.prescriptionUnit) {
                    ExercisePrescriptionUnit.SECONDS -> "목표 처방: ${ex.sets}세트 · ${ex.reps}초 유지"
                    ExercisePrescriptionUnit.REPS -> "목표 처방: ${ex.sets}세트 · ${ex.reps}회"
                }
                val typeLabel = if (ex.type == ExerciseType.CARDIO) "유산소" else "근력"
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    if (ex.gifUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ex.gifUrl)
                                .size(512)
                                .crossfade(true)
                                .build(),
                            contentDescription = ex.name,
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            },
                            error = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("이미지를 불러올 수 없습니다", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        HomeExerciseGuideCard()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    // 운동 이름·방법을 길게 눌러 선택·복사할 수 있게 SelectionContainer 로 감싼다.
                    SelectionContainer {
                        Column {
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.semantics {
                                    contentDescription = "부위 ${ex.bodyPart}, 장비 ${ex.equipment}, 유형 $typeLabel"
                                },
                            ) {
                                if (ex.bodyPart.isNotBlank()) {
                                    DetailMetaChip(ex.bodyPart)
                                }
                                if (ex.equipment.isNotBlank()) {
                                    DetailMetaChip(ex.equipment)
                                }
                                DetailMetaChip(typeLabel)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Text(
                                    text = prescriptionText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "운동 방법",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f).semantics { heading() },
                                )
                                // 버튼은 선택 제스처 대상에서 제외 (DisableSelection).
                                DisableSelection {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(ClipData.newPlainText("운동 방법", buildCopyText(ex))),
                                                )
                                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                                    Toast.makeText(context, "운동 방법을 복사했어요", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Default.ContentCopy, "운동 방법 복사")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ex.instructions.forEachIndexed { i, step ->
                                Text(
                                    "${i + 1}. $step",
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetaChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HomeExerciseGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "무장비 홈트레이닝 가이드",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "장비 없이 실내에서 안전하게 수행하는 동작입니다. 아래 순서에 맞춰 호흡과 자세에 집중해보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
