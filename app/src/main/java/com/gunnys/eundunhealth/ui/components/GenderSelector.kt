package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.Gender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenderSelector(gender: Gender, onGenderChange: (Gender) -> Unit) {
    Text(
        "성별",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(modifier = Modifier.height(8.dp))

    val genderOptions = listOf(
        Gender.MALE to "남성",
        Gender.FEMALE to "여성",
        Gender.UNSPECIFIED to "선택 안 함",
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        genderOptions.forEachIndexed { idx, (genderValue, label) ->
            SegmentedButton(
                selected = gender == genderValue,
                onClick = { onGenderChange(genderValue) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = genderOptions.size),
            ) { Text(label) }
        }
    }
}
