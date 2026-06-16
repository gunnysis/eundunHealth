package com.gunnys.eundunhealth.ui.home.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Health Connect 권한 미허용 — 자동 추적 연동을 유도하는 프롬프트 카드. */
@Composable
internal fun HealthConnectPromptCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FitnessCenter, "Health Connect")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Health Connect로 운동 달성을 자동 추적",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRequest) { Text("연동") }
        }
    }
}

// Health Connect 미설치/비활성 — 갤럭시 워치·삼성 헬스 기록은 Health Connect 경유로만
// 들어오므로, 설치를 유도해 자동 추적 온보딩을 완성한다.
@Composable
internal fun HealthConnectUnavailableCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FitnessCenter, "Health Connect")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Health Connect를 설치하면 갤럭시 워치·삼성 헬스 운동 기록을 자동으로 가져옵니다",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { openHealthConnectListing(context) }) { Text("설치") }
        }
    }
}

// Health Connect Play Store 페이지로 이동 (Play 미존재 시 웹 폴백).
private fun openHealthConnectListing(context: Context) {
    val marketUri = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, marketUri))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
