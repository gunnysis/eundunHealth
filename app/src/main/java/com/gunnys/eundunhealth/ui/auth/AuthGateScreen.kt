package com.gunnys.eundunhealth.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.theme.EundunHealthTheme

private data class ValueIntroSlide(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val VALUE_INTRO_SLIDES = listOf(
    ValueIntroSlide(
        icon = Icons.Default.FitnessCenter,
        title = "집에서도 헬스장에서도 맞춤 7일 루틴",
        description = "장비 없는 홈트레이닝부터 헬스장 기구 루틴까지, 내 신체 정보와 휴식일에 맞춰 자동으로 설계합니다.",
    ),
    ValueIntroSlide(
        icon = Icons.Default.Restaurant,
        title = "운동 일정과 연동되는 AI 고단백 식단",
        description = "운동일과 휴식일의 에너지 소비량에 맞춘 7일 식단과 탄단지 영양 가이드를 한눈에 확인하세요.",
    ),
    ValueIntroSlide(
        icon = Icons.Default.SelfImprovement,
        title = "혼자서도 부담 없는 운동 가이드 & 마음 연습",
        description = "타인의 시선이나 복잡한 기록 강요 없이, 오늘 할 운동만 깔끔하게 따라하고 미소 훈련으로 마무리하세요.",
    ),
)

@Composable
fun AuthGateScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    AuthGateContent(
        uiState = uiState,
        onAuthenticate = { activity?.let(authViewModel::authenticate) },
    )
}

@Composable
private fun AuthGateContent(
    uiState: AuthGateUiState,
    onAuthenticate: () -> Unit,
) {
    val isBusy = uiState is AuthGateUiState.Authenticating
    val pagerState = rememberPagerState(pageCount = { VALUE_INTRO_SLIDES.size })

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "은둔헬스",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "집에서 시작하는 나만의 주간 운동 & 식단 코치",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val slide = VALUE_INTRO_SLIDES[page]
                ValueIntroCard(slide = slide)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VALUE_INTRO_SLIDES.indices.forEach { idx ->
                    val selected = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 룰 8 — inline + persistent. 사용자가 다시 시도할 때까지 유지된다.
            if (uiState is AuthGateUiState.Failed) {
                AuthErrorBanner(error = uiState.error, screen = "auth_gate")
                Spacer(modifier = Modifier.height(20.dp))
            }

            Button(
                onClick = onAuthenticate,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics {
                        contentDescription = "로그인 또는 회원가입. 누르면 브라우저에서 로그인 화면이 열립니다"
                    },
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("로그인 / 회원가입", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "계정이 없으시면 열린 보안 로그인 화면에서 바로 가입하실 수 있습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ValueIntroCard(slide: ValueIntroSlide) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = slide.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun AuthGateIdlePreview() {
    EundunHealthTheme {
        AuthGateContent(uiState = AuthGateUiState.Idle, onAuthenticate = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthGateFailedPreview() {
    EundunHealthTheme {
        AuthGateContent(
            uiState = AuthGateUiState.Failed(
                com.gunnys.eundunhealth.domain.model.AppError.Network(),
            ),
            onAuthenticate = {},
        )
    }
}
