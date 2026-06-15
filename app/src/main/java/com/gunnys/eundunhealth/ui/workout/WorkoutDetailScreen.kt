package com.gunnys.eundunhealth.ui.workout

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.ui.components.ErrorContent

/** 복사용 텍스트: 운동 이름 + 번호 매긴 방법. 영어 콘텐츠를 번역기에 붙여넣는 용도로 한 번에 복사. */
private fun buildCopyText(ex: Exercise): String = buildString {
    append(ex.name)
    append("\n\n")
    ex.instructions.forEachIndexed { i, step -> append("${i + 1}. $step\n") }
}.trimEnd()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    exerciseId: String,
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
                val clipboard = LocalClipboardManager.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
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
                    // 운동 이름·방법을 길게 눌러 선택·복사할 수 있게 SelectionContainer 로 감싼다.
                    // 콘텐츠가 영어라 사용자가 방법을 복사해 번역기에 붙여넣는 동선을 지원한다.
                    SelectionContainer {
                        Column {
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                "${ex.bodyPart} | ${ex.equipment}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics {
                                    contentDescription = "부위 ${ex.bodyPart}, 장비 ${ex.equipment}"
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${ex.sets}세트 x ${ex.reps}회",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "운동 방법",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f).semantics { heading() },
                                )
                                // 버튼은 선택 제스처 대상에서 제외 (DisableSelection).
                                DisableSelection {
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(buildCopyText(ex)))
                                            Toast.makeText(context, "운동 방법을 복사했어요", Toast.LENGTH_SHORT).show()
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
