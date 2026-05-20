package com.gunnys.eundunhealth.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gunnys.eundunhealth.domain.model.DayPlan
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onExerciseClick: (String) -> Unit,
    onBadgesClick: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이번 주 운동 계획") },
                actions = {
                    IconButton(onClick = { viewModel.loadPlan() }) {
                        Icon(Icons.Default.Refresh, "새로고침")
                    }
                    IconButton(onClick = onBadgesClick) {
                        Icon(Icons.Default.EmojiEvents, "배지")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HomeUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.loadPlan() }) {
                            Text("다시 시도")
                        }
                    }
                }
            }
            is HomeUiState.Success -> {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    if (!state.hasHealthPermission) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FitnessCenter, "Health Connect")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Health Connect로 운동 달성을 자동 추적", modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = onRequestHealthPermissions) { Text("연동") }
                                }
                            }
                        }
                    }
                    items(state.plan.days, key = { it.date.toString() }) { day ->
                        DayPlanCard(day = day, onExerciseClick = onExerciseClick)
                    }
                }
            }
        }
    }
}

@Composable
fun DayPlanCard(day: DayPlan, onExerciseClick: (String) -> Unit) {
    val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    val dateStr = "${day.date.monthValue}/${day.date.dayOfMonth}"
    val containerColor by animateColorAsState(
        if (day.isCompleted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "cardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$dayName ($dateStr)", style = MaterialTheme.typography.titleMedium)
                }
                if (day.isCompleted) {
                    Icon(Icons.Default.CheckCircle, "완료", tint = MaterialTheme.colorScheme.primary)
                } else if (!day.isRestDay) {
                    Icon(Icons.Outlined.Circle, "미완료", tint = MaterialTheme.colorScheme.outline)
                }
            }
            if (day.isRestDay) {
                Text("휴식일", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                day.exercises.forEach { exercise ->
                    TextButton(
                        onClick = { onExerciseClick(exercise.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Text("${exercise.name}  ${exercise.sets}x${exercise.reps}",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
