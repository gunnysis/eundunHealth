package com.gunnys.eundunhealth.ui.home.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.gunnys.eundunhealth.data.healthconnect.HealthConnectDataSource
import com.gunnys.eundunhealth.domain.model.DailyActivity

/** "오늘의 활동" 카드 — 권한 없으면 연동 버튼, 있으면 걸음·칼로리·심박을 표시. */
@Composable
internal fun TodayActivityCard(
    activity: DailyActivity?,
    hasPermission: Boolean,
    isAvailable: Boolean,
    onRefresh: () -> Unit,
) {
    if (!isAvailable) return // HC 미설치는 HealthConnectUnavailableCard 가 커버

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.containsAll(HealthConnectDataSource.DAILY_ACTIVITY_PERMISSIONS)) onRefresh()
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "오늘의 활동",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                !hasPermission -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "걸음수·소모 칼로리·심박을 자동으로 표시",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = { permissionLauncher.launch(HealthConnectDataSource.DAILY_ACTIVITY_PERMISSIONS) },
                        ) { Text("연동") }
                    }
                }
                activity?.hasAny != true -> {
                    Text(
                        "오늘 활동 기록이 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        activity.steps?.let { ActivityMetric("👟", "$it", "걸음") }
                        activity.totalCaloriesKcal?.let { ActivityMetric("🔥", "$it", "kcal") }
                        activity.avgHeartRateBpm?.let { ActivityMetric("❤", "$it", "bpm") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMetric(icon: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
