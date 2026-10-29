package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.ui.util.HapticFeedbackHelper
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun roundAndClampMetricValue(
    raw: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
): Float {
    val factor = 10f.pow(decimals.coerceAtLeast(0))
    val rounded = if (decimals <= 0) {
        raw.roundToInt().toFloat()
    } else {
        (raw * factor).roundToInt() / factor
    }
    return rounded.coerceIn(range.start, range.endInclusive)
}

internal fun parseMetricDirectInput(rawInput: String): Float? = rawInput.trim().replace(',', '.').toFloatOrNull()

/**
 * 3-Way 하이브리드 신체 수치 피커:
 * 1) 상단 대형 숫자 탭 시 숫자 키패드 직접 입력 다이얼로그
 * 2) 양옆 48dp [-] / [+] 미세 증감 스테퍼 버튼
 * 3) 하단 수평 눈금 룰러 휠 (SnapFling + 틱 햅틱)
 */
@Composable
fun HybridBodyMetricPicker(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    decimals: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var showDirectInputDialog by rememberSaveable { mutableStateOf(false) }

    val formatPattern = remember(decimals) {
        if (decimals == 0) "%.0f" else "%.${decimals}f"
    }
    val formattedValue = remember(value, formatPattern) { formatPattern.format(value) }

    val totalSteps = remember(range, step) {
        ((range.endInclusive - range.start) / step).roundToInt().coerceAtLeast(1)
    }
    val currentStepIndex = remember(value, range, step, totalSteps) {
        ((value - range.start) / step).roundToInt().coerceIn(0, totalSteps)
    }

    fun updateByStepIndex(index: Int) {
        val clampedIndex = index.coerceIn(0, totalSteps)
        val raw = range.start + clampedIndex * step
        onValueChange(roundAndClampMetricValue(raw, range, decimals))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$label ${formattedValue}$unit"
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, totalSteps)
                setProgress { target ->
                    val targetIdx = ((target - range.start) / step).roundToInt()
                    updateByStepIndex(targetIdx)
                    true
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "숫자를 누르면 직접 입력",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // [-]  44sp 대형 숫자 표시  [+]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (currentStepIndex > 0) {
                            HapticFeedbackHelper.performStepClick(haptic)
                            updateByStepIndex(currentStepIndex - 1)
                        }
                    },
                    enabled = currentStepIndex > 0,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "$label 감소",
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDirectInputDialog = true }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = formattedValue,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "$label 직접 입력",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .size(18.dp),
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        if (currentStepIndex < totalSteps) {
                            HapticFeedbackHelper.performStepClick(haptic)
                            updateByStepIndex(currentStepIndex + 1)
                        }
                    },
                    enabled = currentStepIndex < totalSteps,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "$label 증가",
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 수평 눈금 룰러
            HorizontalRulerWheel(
                totalSteps = totalSteps,
                currentStepIndex = currentStepIndex,
                onStepIndexChanged = { newIdx ->
                    HapticFeedbackHelper.performTick(haptic)
                    updateByStepIndex(newIdx)
                },
            )
        }
    }

    if (showDirectInputDialog) {
        DirectMetricInputDialog(
            label = label,
            initialText = formattedValue,
            range = range,
            unit = unit,
            onDismiss = { showDirectInputDialog = false },
            onConfirm = { parsed ->
                showDirectInputDialog = false
                onValueChange(roundAndClampMetricValue(parsed, range, decimals))
            },
        )
    }
}

@Composable
private fun HorizontalRulerWheel(
    totalSteps: Int,
    currentStepIndex: Int,
    onStepIndexChanged: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentStepIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val tickStepWidth = 10.dp
    val density = LocalDensity.current
    val tickStepWidthPx = remember(density) { with(density) { tickStepWidth.toPx().coerceAtLeast(1f) } }

    val latestStepIndex by rememberUpdatedState(currentStepIndex)
    val latestOnStepIndexChanged by rememberUpdatedState(onStepIndexChanged)

    LaunchedEffect(currentStepIndex) {
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != currentStepIndex) {
            listState.scrollToItem(currentStepIndex)
        }
    }

    LaunchedEffect(listState, tickStepWidthPx, totalSteps) {
        snapshotFlow {
            val offsetSteps = (listState.firstVisibleItemScrollOffset / tickStepWidthPx).roundToInt()
            (listState.firstVisibleItemIndex + offsetSteps).coerceIn(0, totalSteps)
        }
            .distinctUntilChanged()
            .drop(1)
            .collect { idx ->
                if (listState.isScrollInProgress && idx != latestStepIndex) {
                    latestOnStepIndexChanged(idx)
                }
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val horizontalPadding = ((maxWidth - tickStepWidth) / 2).coerceAtLeast(0.dp)

        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(count = totalSteps + 1, key = { it }) { idx ->
                val isMajor = idx % 10 == 0
                val isMedium = idx % 5 == 0
                Canvas(
                    modifier = Modifier
                        .width(tickStepWidth)
                        .height(36.dp),
                ) {
                    val strokePx = with(density) { (if (isMajor) 2.dp else 1.dp).toPx() }
                    val lineHeight = when {
                        isMajor -> size.height * 0.85f
                        isMedium -> size.height * 0.6f
                        else -> size.height * 0.38f
                    }
                    val topY = (size.height - lineHeight) / 2f
                    drawLine(
                        color = if (isMajor) outlineColor else outlineVariantColor,
                        start = Offset(size.width / 2f, topY),
                        end = Offset(size.width / 2f, topY + lineHeight),
                        strokeWidth = strokePx,
                    )
                }
            }
        }

        // 중앙 고정 인디케이터 포인터
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor),
        )
    }
}

@Composable
private fun DirectMetricInputDialog(
    label: String,
    initialText: String,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var input by rememberSaveable(initialText) { mutableStateOf(initialText) }
    val parsed = parseMetricDirectInput(input)
    val isValid = parsed != null && parsed in range

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$label 직접 입력") },
        text = {
            Column {
                Text(
                    text = "${range.start.toInt()}~${range.endInclusive.toInt()}$unit 범위로 입력해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("$label ($unit)") },
                    singleLine = true,
                    isError = input.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (parsed != null && isValid) onConfirm(parsed)
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (parsed != null && isValid) onConfirm(parsed) },
                enabled = isValid,
            ) {
                Text("적용")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}
