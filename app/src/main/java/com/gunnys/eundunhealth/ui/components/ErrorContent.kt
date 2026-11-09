package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.ui.navigation.openPlayStoreListing
import com.gunnys.eundunhealth.ui.theme.EundunHealthTheme

/**
 * 화면 전체 영역에 표시되는 에러 표시 컴포넌트.
 *
 * @param error 표시할 [AppError] — [AppError.userMessage]를 본문으로 사용
 * @param onRetry null이 아니면 "다시 시도" 버튼이 노출된다([AppError.ClientUpdateRequired] 인 경우에는
 *   재시도가 무의미하므로 Play 스토어 업데이트 버튼을 우선 노출한다)
 */
@Composable
fun ErrorContent(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isUpdateRequired = error is AppError.ClientUpdateRequired

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isUpdateRequired) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isUpdateRequired) Icons.Default.SystemUpdate else Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (isUpdateRequired) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = error.userMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (isUpdateRequired) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { openPlayStoreListing(context) },
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "Google Play에서 업데이트",
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "다시 시도",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    EundunHealthTheme {
        ErrorContent(
            error = AppError.Network("네트워크 연결을 확인해주세요"),
            onRetry = {},
        )
    }
}
