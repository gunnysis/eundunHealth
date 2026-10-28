package com.gunnys.eundunhealth.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.util.WorkoutDurationEstimator
import com.gunnys.eundunhealth.ui.home.PlanModeBadge
import com.gunnys.eundunhealth.ui.theme.EundunTheme
import com.gunnys.eundunhealth.ui.util.HapticFeedbackHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HERO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

@Composable
fun TodayHeroCard(
    todayPlan: DayPlan?,
    todayDate: LocalDate,
    mode: WorkoutPlanMode,
    onStartWorkoutGuide: (LocalDate) -> Unit,
    onExerciseClick: (String) -> Unit,
    onToggleComplete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val dayLabel = remember(todayDate) {
        val dow = todayDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        "오늘 · ${todayDate.format(HERO_DATE_FORMATTER)} ($dow)"
    }
    val exercises = todayPlan?.exercises.orEmpty()
    val estimatedMinutes = remember(exercises) {
        WorkoutDurationEstimator.estimateMinutes(exercises)
    }
    val isRestDay = todayPlan?.isRestDay == true
    val isCompleted = todayPlan?.isCompleted == true

    val containerColor = when {
        isCompleted -> EundunTheme.extendedColors.successContainer.copy(alpha = 0.55f)
        isRestDay -> EundunTheme.extendedColors.restContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = when {
        isCompleted -> EundunTheme.extendedColors.success
        isRestDay -> EundunTheme.extendedColors.rest
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(2.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                PlanModeBadge(mode = mode)
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                todayPlan == null -> {
                    Text(
                        text = "오늘 배정된 운동 일정이 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                isRestDay -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = EundunTheme.extendedColors.rest,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "오늘은 회복과 충전의 휴식일입니다",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "가벼운 스트레칭과 충분한 수분 섭취로 근육 회복을 도와주세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    val statusHeadline = if (isCompleted) {
                        "오늘의 운동 완료! (${exercises.size}종목 · 약 ${estimatedMinutes}분)"
                    } else {
                        "오늘의 루틴: ${exercises.size}종목 · 예상 약 ${estimatedMinutes}분"
                    }
                    Text(
                        text = statusHeadline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) {
                            EundunTheme.extendedColors.success
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )

                    if (exercises.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            exercises.forEach { exercise ->
                                TodayHeroExerciseRow(
                                    exercise = exercise,
                                    onClick = { onExerciseClick(exercise.id) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isCompleted) {
                        Button(
                            onClick = { onStartWorkoutGuide(todayPlan.date) },
                            enabled = exercises.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "운동 시작 (가이드 모드)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                HapticFeedbackHelper.performConfirm(view)
                                onToggleComplete(todayPlan.date)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("가이드 없이 바로 오늘 운동 완료 체크")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onStartWorkoutGuide(todayPlan.date) },
                                enabled = exercises.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Text("동작 다시 보기")
                            }
                            TextButton(
                                onClick = { onToggleComplete(todayPlan.date) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("완료 해제")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayHeroExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
) {
    val typeIcon = if (exercise.type == ExerciseType.CARDIO) {
        Icons.AutoMirrored.Filled.DirectionsRun
    } else {
        Icons.Default.FitnessCenter
    }
    val prescriptionLabel = when (exercise.prescriptionUnit) {
        ExercisePrescriptionUnit.SECONDS -> "${exercise.sets}세트 · ${exercise.reps}초"
        ExercisePrescriptionUnit.REPS -> "${exercise.sets}세트 · ${exercise.reps}회"
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (exercise.bodyPart.isNotBlank()) {
                    Text(
                        text = exercise.bodyPart,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = prescriptionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "상세 보기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
