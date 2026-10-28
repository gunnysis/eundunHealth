package com.gunnys.eundunhealth.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)),
    )
}

@Composable
fun SkeletonDayCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f), height = 20.dp)
            Spacer(modifier = Modifier.height(6.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.25f), height = 12.dp)
            Spacer(modifier = Modifier.height(14.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 44.dp)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
}

/**
 * 오늘 탭(`HomeScreen`)의 새로운 레이아웃 구조(TodayHeroCard + WeeklyProgressCard + 접이식 주간 헤더)와
 * 1:1로 대응하는 스켈레톤 UI. 로딩 완료 시 레이아웃 점프(CLS)를 방지한다.
 */
@Composable
fun SkeletonHomeContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 4.dp)) {
        // 1. TodayHeroCard 스켈레톤
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.42f), height = 18.dp)
                    ShimmerBox(modifier = Modifier.size(width = 56.dp, height = 22.dp), height = 22.dp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f), height = 26.dp)
                Spacer(modifier = Modifier.height(18.dp))
                repeat(3) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 52.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 52.dp)
            }
        }

        // 2. WeeklyProgressCard 스켈레톤
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 18.dp)
                    ShimmerBox(modifier = Modifier.size(width = 52.dp, height = 24.dp), height = 24.dp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 8.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(7) {
                        ShimmerBox(
                            modifier = Modifier.size(28.dp).clip(CircleShape),
                            height = 28.dp,
                        )
                    }
                }
            }
        }

        // 3. WeeklyPlanExpandHeader 스켈레톤
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f), height = 16.dp)
                ShimmerBox(modifier = Modifier.size(20.dp), height = 20.dp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SkeletonHomeContentPreview() {
    MaterialTheme {
        SkeletonHomeContent()
    }
}
