package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 키·몸무게·체지방률 등 수치 입력용 슬라이더 + 텍스트 필드 페어.
 *
 * Onboarding과 Profile 양쪽에서 동일하게 사용 — 원래 onboarding 패키지에 있던 것을
 * 공유 컴포넌트로 이동(Phase 6A).
 */
@Composable
fun ProfileSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    decimals: Int,
    onValueChange: (Float) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // 패턴 문자열 재계산 + format 호출을 입력 의존성 변경 시에만 수행하도록 캐싱
    val formatPattern = remember(decimals) {
        if (decimals == 0) "%.0f" else "%.${decimals}f"
    }
    val initialText = remember(value, formatPattern) { formatPattern.format(value) }
    var textValue by remember(value) { mutableStateOf(initialText) }
    val isError = textValue.toFloatOrNull()?.let { it !in range } ?: textValue.isNotEmpty()

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
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
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
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
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
