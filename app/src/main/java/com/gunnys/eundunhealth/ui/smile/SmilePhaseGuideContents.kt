package com.gunnys.eundunhealth.ui.smile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun PermissionRationaleContent(
    onAllowClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    SmileGuideCardLayout(
        icon = Icons.Default.CameraAlt,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        title = "전면 카메라 권한 안내",
        description = "스마일 트레이닝은 전면 카메라로 미소 유지 여부를 기기 안에서만 측정합니다. 얼굴 사진이나 영상은 저장되거나 서버로 전송되지 않습니다.",
    ) {
        Button(
            onClick = onAllowClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("카메라 권한 허용하기", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCancelClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("돌아가기")
        }
    }
}

@Composable
internal fun PermissionBlockedContent(
    onOpenSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    SmileGuideCardLayout(
        icon = Icons.Default.ErrorOutline,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
        title = "카메라 권한이 꺼져 있어요",
        description = "앱 설정 > 권한에서 카메라 권한을 허용해주시면 스마일 트레이닝을 바로 시작할 수 있습니다.",
    ) {
        Button(
            onClick = onOpenSettingsClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("앱 설정 열기", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("돌아가기")
        }
    }
}

@Composable
internal fun ModelPreparingContent(
    progressPercent: Int?,
    onCancelClick: () -> Unit,
) {
    SmileGuideCardLayout(
        icon = Icons.Default.CloudDownload,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        title = "온디바이스 얼굴 인식 모듈 준비 중",
        description = "최초 실행 시 Google Play 서비스를 통해 온디바이스 모델을 준비합니다.",
    ) {
        if (progressPercent != null) {
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$progressPercent%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onCancelClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("취소")
        }
    }
}

@Composable
internal fun ModelUnavailableContent(
    reason: String,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    SmileGuideCardLayout(
        icon = Icons.Default.ErrorOutline,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
        title = "준비 상태 확인 필요",
        description = reason,
        announceDescriptionPolite = true,
    ) {
        Button(
            onClick = onRetryClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("다시 시도", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("돌아가기")
        }
    }
}

@Composable
private fun SmileGuideCardLayout(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    description: String,
    announceDescriptionPolite: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = iconTint,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = if (announceDescriptionPolite) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
                )
                Spacer(Modifier.height(28.dp))
                actions()
            }
        }
    }
}
