package com.gunnys.eundunhealth.ui.mealplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.DailyMealPlan
import com.gunnys.eundunhealth.domain.model.MealInfo
import com.gunnys.eundunhealth.domain.model.WeeklyMealPlan
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.theme.EundunTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onBack: (() -> Unit)? = null,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAiDataConsent by rememberSaveable { mutableStateOf(false) }

    if (showAiDataConsent) {
        AlertDialog(
            onDismissRequest = { showAiDataConsent = false },
            title = { Text("AI 식단 데이터 처리 동의") },
            text = {
                Text(
                    text = "맞춤형 식단 생성을 위해 성별, 키, 몸무게, 체지방률, 골격근량과 이번 주 운동일을 " +
                        "Microsoft Azure AI Foundry(DeepSeek)에 암호화해 전송합니다. Global Standard " +
                        "서비스 특성상 한국 외 Azure 리전에서 처리될 수 있습니다. 이 정보는 식단 생성 " +
                        "목적으로만 사용되며 AI 모델 학습에는 사용되지 않습니다.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiDataConsent = false
                        viewModel.generatePlan()
                    },
                ) {
                    Text("동의하고 생성")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiDataConsent = false }) {
                    Text("취소")
                }
            },
        )
    }

    val canRegenerate = uiState is MealPlanUiState.Success

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("주간 식단") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                },
                actions = {
                    if (canRegenerate) {
                        TextButton(onClick = { showAiDataConsent = true }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("다시 만들기")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is MealPlanUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("식단을 불러오는 중입니다...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is MealPlanUiState.Generating -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "신체 정보와 운동 일정에 맞춰 AI 주간 식단을 생성하고 있습니다...",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                is MealPlanUiState.Empty -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "이번 주 식단이 아직 없습니다.",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showAiDataConsent = true }) {
                            Text("식단 자동 생성하기")
                        }
                    }
                }
                is MealPlanUiState.Error -> {
                    ErrorContent(
                        error = state.error,
                        onRetry = { viewModel.loadCurrentPlan() },
                    )
                }
                is MealPlanUiState.Success -> {
                    MealPlanLoadedContent(
                        plan = state.plan,
                        regenerateError = state.regenerateError,
                    )
                }
            }
        }
    }
}

@Composable
private fun MealPlanLoadedContent(
    plan: WeeklyMealPlan,
    regenerateError: AppError?,
) {
    val todayFullKo = remember {
        LocalDate.now(KST_ZONE).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    }
    val todayShortKo = remember {
        LocalDate.now(KST_ZONE).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    }

    val sortedPlans = remember(plan.weeklyPlan, todayFullKo, todayShortKo) {
        val (todayList, others) = plan.weeklyPlan.partition {
            it.day.contains(todayFullKo) || it.day.startsWith(todayShortKo)
        }
        todayList + others
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        regenerateError?.let { err ->
            item {
                AuthErrorBanner(
                    error = err,
                    screen = "meal_plan_regenerate",
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI 식단 조언",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = plan.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AI가 생성한 참고용 정보이며 의료 조언이 아닙니다. 알레르기나 질환이 있으면 전문가와 확인하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        items(sortedPlans, key = { it.day }) { dailyPlan ->
            val isToday = dailyPlan.day.contains(todayFullKo) || dailyPlan.day.startsWith(todayShortKo)
            DailyMealCard(
                dailyPlan = dailyPlan,
                isToday = isToday,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyMealCard(
    dailyPlan: DailyMealPlan,
    isToday: Boolean = false,
) {
    var expanded by rememberSaveable(dailyPlan.day, isToday) { mutableStateOf(isToday) }
    val totalCalories = remember(dailyPlan.meals) { dailyPlan.meals.sumOf { it.calories } }
    val totalProtein = remember(dailyPlan.meals) { dailyPlan.meals.sumOf { it.proteinG } }

    val border = if (isToday) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (isToday) "오늘 · ${dailyPlan.day}" else dailyPlan.day,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        MealDayTypeBadge(isRestDay = dailyPlan.isRestDay)
                    }
                    if (dailyPlan.meals.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "총 ${totalCalories}kcal · 단백질 ${totalProtein}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "접기" else "펼치기",
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (dailyPlan.meals.isEmpty()) {
                        Text(
                            text = if (dailyPlan.isRestDay) {
                                "이 날은 휴식일입니다. 적절한 영양 섭취와 휴식을 취해주세요."
                            } else {
                                "식단 정보가 없습니다."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        dailyPlan.meals.forEachIndexed { index, meal ->
                            MealInfoItem(meal = meal)
                            if (index < dailyPlan.meals.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealDayTypeBadge(isRestDay: Boolean) {
    val bgColor = if (isRestDay) {
        EundunTheme.extendedColors.restContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isRestDay) {
        EundunTheme.extendedColors.onRestContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        contentColor = contentColor,
    ) {
        Text(
            text = if (isRestDay) "휴식일" else "운동일",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealInfoItem(meal: MealInfo) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = meal.mealType,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${meal.calories}kcal",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = meal.menuName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MacroNutrientChip(
                label = "단백질 ${meal.proteinG}g",
                color = EundunTheme.extendedColors.macroProtein,
            )
            MacroNutrientChip(
                label = "탄수화물 ${meal.carbsG}g",
                color = EundunTheme.extendedColors.macroCarbs,
            )
            MacroNutrientChip(
                label = "지방 ${meal.fatG}g",
                color = EundunTheme.extendedColors.macroFat,
            )
        }
    }
}

@Composable
private fun MacroNutrientChip(
    label: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
