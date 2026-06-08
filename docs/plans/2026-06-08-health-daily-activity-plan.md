---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0
ledger_topic: android
tags: [health-connect, daily-activity, steps, calories, heart-rate, home]
---

# #2 홈 "오늘의 활동" 요약 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (권장) 또는 `superpowers:executing-plans`. Step 은 checkbox(`- [ ]`).

**Goal:** 홈 `WeeklyProgressCard` 아래에 오늘의 걸음수·소모 칼로리·평균 심박을 보여주는 `TodayActivityCard` 추가. HC aggregate 1회 IPC, 표시 전용(백엔드 무변경).

**Architecture (요약):** #1 계층 패턴 그대로. `HealthConnectDataSource.readTodayActivity()`(aggregate) → `HealthRepository.getTodayActivity()` → `GetTodayActivityUseCase`(권한/가용 gating + `TodayActivityResult` 반환) → `HomeViewModel.loadTodayActivity()`(plan 렌더 후 백그라운드 → Success.copy) → `HomeScreen.TodayActivityCard`(in-Composable 권한 launcher).

**Tech Stack:** Kotlin 2.2.10 / Compose / Hilt / Health Connect 1.1.0 (aggregate API) / JUnit4 + coroutines-test.

**참고:**
- Design: `docs/plans/2026-06-08-health-daily-activity-design.md`
- Branch: `feat/health-daily-activity` (이미 생성, design+plan 포함)
- 선행: #1(PR #84) 머지됨. HealthRepository = isAvailable/hasPermissions/getExerciseDatesThisWeek/hasBodyCompositionPermissions/getLatestBodyComposition 보유. **HealthRepository 구현 fake 2곳**: `SyncHealthDataUseCaseTest.FakeHealthRepo`, `ImportBodyCompositionUseCaseTest.FakeHealthRepo`.

**중요 원칙:**
- TDD: 동작 변경 task red→green→commit. (DataSource/Compose 는 Android 의존 → Task 7 수동/게이트 검증.)
- 모든 commit 은 `feat/health-daily-activity`, 최종 PR 1개. 각 실행 Step 첫 줄 `bash`/`pwsh` 명시.
- 룰 11(UDF) 준수: 일회성 없음(활동은 Success 상태에 반영), `@Immutable`, `collectAsStateWithLifecycle`.
- detekt baseline drift 시 시그니처 갱신(억제 아닌 dead code 제거). MaxLineLength(140) 위반은 reformat(block body), baseline 박제 금지.

**Task 순서:**
```
Task 0  환경 확인
Task 1  매니페스트 권한 + DailyActivity 모델
Task 2  HealthConnectDataSource.readTodayActivity (aggregate)
Task 3  HealthRepository 인터페이스/impl + 기존 Fake 2곳 갱신
Task 4  GetTodayActivityUseCase (TDD)
Task 5  HomeViewModel 활동 로드
Task 6  HomeScreen TodayActivityCard + 권한 launcher
Task 7  전체 검증(게이트)
Task 8  push + PR
```

---

## Phase 1: 데이터 계층

### Task 0: 환경 확인
- [ ] **Step 1 (bash):** `git branch --show-current` → `feat/health-daily-activity`. `git log --oneline -1 origin/main` 에 `(#84)` 포함 확인.

---

### Task 1: 매니페스트 권한 + DailyActivity 모델

**Files:** Modify `app/src/main/AndroidManifest.xml`; Create `app/src/main/java/com/gunnys/eundunhealth/domain/model/DailyActivity.kt`

- [ ] **Step 1:** 매니페스트 기존 health 권한 아래에 추가:
```xml
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
```

- [ ] **Step 2:** 모델 생성:
```kotlin
package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

/** 오늘의 활동 요약. HC 에 없으면 각 필드 null. */
@Immutable
data class DailyActivity(
    val steps: Long?,
    val totalCaloriesKcal: Int?,
    val avgHeartRateBpm: Long?,
) {
    val hasAny: Boolean get() = steps != null || totalCaloriesKcal != null || avgHeartRateBpm != null
}
```

- [ ] **Step 3 (bash):** `./gradlew :app:compileDebugKotlin 2>&1 | tail -5` → SUCCESSFUL. commit:
```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/gunnys/eundunhealth/domain/model/DailyActivity.kt
git commit -m "feat(health): 활동 읽기 권한 선언 + DailyActivity 모델"
```

---

### Task 2: HealthConnectDataSource.readTodayActivity (aggregate)

**Files:** Modify `app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectDataSource.kt`
> DataSource = Android 프레임워크 의존 → 단위테스트 없음(Task 7 수동/게이트).

- [ ] **Step 1:** import 추가:
```kotlin
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import com.gunnys.eundunhealth.domain.model.DailyActivity
```

- [ ] **Step 2:** companion 에 권한 set 추가:
```kotlin
val DAILY_ACTIVITY_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
)
```

- [ ] **Step 3:** 클래스 본문에 추가:
```kotlin
suspend fun hasDailyActivityPermissions(): Boolean {
    val granted = client.permissionController.getGrantedPermissions()
    return granted.containsAll(DAILY_ACTIVITY_PERMISSIONS)
}

/** 오늘 0시~현재 걸음·칼로리·평균심박 집계 (aggregate 1회). */
suspend fun readTodayActivity(): DailyActivity {
    val zone = ZoneId.systemDefault()
    val filter = TimeRangeFilter.between(
        LocalDate.now(zone).atStartOfDay(zone).toInstant(),
        Instant.now(),
    )
    val res = client.aggregate(
        AggregateRequest(
            metrics = setOf(
                StepsRecord.COUNT_TOTAL,
                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                HeartRateRecord.BPM_AVG,
            ),
            timeRangeFilter = filter,
        ),
    )
    return DailyActivity(
        steps = res[StepsRecord.COUNT_TOTAL],
        totalCaloriesKcal = res[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toInt(),
        avgHeartRateBpm = res[HeartRateRecord.BPM_AVG],
    )
}
```

- [ ] **Step 4 (bash):** compile + commit:
```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectDataSource.kt
git commit -m "feat(health): HealthConnectDataSource 오늘 활동 aggregate read"
```

---

### Task 3: HealthRepository 인터페이스/impl + 기존 Fake 2곳 갱신

**Files:** Modify `domain/repository/HealthRepository.kt`, `data/repository/HealthRepositoryImpl.kt`, `app/src/test/.../SyncHealthDataUseCaseTest.kt`, `app/src/test/.../ImportBodyCompositionUseCaseTest.kt`
> 인터페이스 메서드 추가 → impl + **2개 테스트 fake** 동시 갱신해야 컴파일.

- [ ] **Step 1:** `HealthRepository.kt` interface 에 추가 (+ import `DailyActivity`):
```kotlin
    suspend fun hasDailyActivityPermissions(): Boolean
    suspend fun getTodayActivity(): Result<DailyActivity>
```

- [ ] **Step 2:** `HealthRepositoryImpl.kt` 구현 (+ import `DailyActivity`):
```kotlin
    override suspend fun hasDailyActivityPermissions(): Boolean = try {
        healthConnect.hasDailyActivityPermissions()
    } catch (_: Exception) {
        false
    }

    override suspend fun getTodayActivity(): Result<DailyActivity> = runCatching {
        healthConnect.readTodayActivity()
    }
```

- [ ] **Step 3:** `SyncHealthDataUseCaseTest.kt` 의 `FakeHealthRepo` 에 추가 (+ import `DailyActivity`):
```kotlin
        override suspend fun hasDailyActivityPermissions(): Boolean = false
        override suspend fun getTodayActivity(): Result<DailyActivity> =
            Result.success(DailyActivity(null, null, null))
```

- [ ] **Step 4:** `ImportBodyCompositionUseCaseTest.kt` 의 `FakeHealthRepo` 에 동일 2개 메서드 추가 (+ import `DailyActivity`):
```kotlin
        override suspend fun hasDailyActivityPermissions(): Boolean = false
        override suspend fun getTodayActivity(): Result<DailyActivity> =
            Result.success(DailyActivity(null, null, null))
```

- [ ] **Step 5 (bash):** 회귀 확인 + commit:
```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.*" 2>&1 | tail -6
git add app/src/main/java/com/gunnys/eundunhealth/domain/repository/HealthRepository.kt app/src/main/java/com/gunnys/eundunhealth/data/repository/HealthRepositoryImpl.kt app/src/test/java/com/gunnys/eundunhealth/domain/usecase/SyncHealthDataUseCaseTest.kt app/src/test/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCaseTest.kt
git commit -m "feat(health): HealthRepository 에 오늘 활동 조회 추가"
```
Expected: Sync 6 + Import 5 = 11 PASS.

---

### Task 4: GetTodayActivityUseCase (TDD)

**Files:** Create `domain/usecase/GetTodayActivityUseCase.kt`; Test `app/src/test/.../GetTodayActivityUseCaseTest.kt`

- [ ] **Step 1: 실패 테스트 (RED)**
```kotlin
package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.BodyComposition
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class GetTodayActivityUseCaseTest {

    class FakeHealthRepo(
        private val available: Boolean = true,
        private val hasActivityPerms: Boolean = true,
        private val activity: Result<DailyActivity> = Result.success(DailyActivity(8000L, 320, 72L)),
    ) : HealthRepository {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun hasPermissions(): Boolean = true
        override suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>> =
            Result.success(emptyList())
        override suspend fun hasBodyCompositionPermissions(): Boolean = false
        override suspend fun getLatestBodyComposition(): Result<BodyComposition> =
            Result.success(BodyComposition(null, null, null))
        override suspend fun hasDailyActivityPermissions(): Boolean = hasActivityPerms
        override suspend fun getTodayActivity(): Result<DailyActivity> = activity
    }

    @Test
    fun `returns activity with permission when available`() = runTest {
        val result = GetTodayActivityUseCase(FakeHealthRepo())().getOrThrow()
        assertTrue(result.hasPermission)
        assertEquals(8000L, result.activity?.steps)
        assertEquals(320, result.activity?.totalCaloriesKcal)
        assertEquals(72L, result.activity?.avgHeartRateBpm)
    }

    @Test
    fun `no permission returns null activity and hasPermission false`() = runTest {
        val result = GetTodayActivityUseCase(FakeHealthRepo(hasActivityPerms = false))().getOrThrow()
        assertFalse(result.hasPermission)
        assertNull(result.activity)
    }

    @Test
    fun `unavailable returns null activity`() = runTest {
        val result = GetTodayActivityUseCase(FakeHealthRepo(available = false))().getOrThrow()
        assertFalse(result.hasPermission)
        assertNull(result.activity)
    }

    @Test
    fun `read failure falls back to null activity but keeps permission true`() = runTest {
        val result = GetTodayActivityUseCase(
            FakeHealthRepo(activity = Result.failure(IOException("HC read failed"))),
        )().getOrThrow()
        assertTrue(result.hasPermission)
        assertNull(result.activity)
    }

    @Test
    fun `no data returns activity object with null fields`() = runTest {
        val result = GetTodayActivityUseCase(
            FakeHealthRepo(activity = Result.success(DailyActivity(null, null, null))),
        )().getOrThrow()
        assertTrue(result.hasPermission)
        assertFalse(result.activity?.hasAny ?: true)
    }
}
```

- [ ] **Step 2 (bash): RED 확인** — `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.GetTodayActivityUseCaseTest" 2>&1 | tail -8` → 컴파일 실패(`GetTodayActivityUseCase` 미존재).

- [ ] **Step 3: 구현 (GREEN)**
```kotlin
package com.gunnys.eundunhealth.domain.usecase

import androidx.compose.runtime.Immutable
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import javax.inject.Inject

@Immutable
data class TodayActivityResult(val activity: DailyActivity?, val hasPermission: Boolean)

/**
 * 오늘의 활동을 읽어온다. 비가용/무권한이면 hasPermission=false + activity=null.
 * read 실패는 Sentry 보고 후 activity=null 로 degrade (PR #83 패턴) — 호출은 성공.
 */
class GetTodayActivityUseCase @Inject constructor(
    private val healthRepo: HealthRepository,
) {
    suspend operator fun invoke(): Result<TodayActivityResult> = runCatching {
        if (!healthRepo.isAvailable() || !healthRepo.hasDailyActivityPermissions()) {
            return@runCatching TodayActivityResult(activity = null, hasPermission = false)
        }
        val activity = healthRepo.getTodayActivity().getOrElse {
            it.toAppError().reportToSentry()
            null
        }
        TodayActivityResult(activity = activity, hasPermission = true)
    }
}
```

- [ ] **Step 4 (bash): GREEN + commit**
```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.GetTodayActivityUseCaseTest" 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/domain/usecase/GetTodayActivityUseCase.kt app/src/test/java/com/gunnys/eundunhealth/domain/usecase/GetTodayActivityUseCaseTest.kt
git commit -m "feat(health): GetTodayActivityUseCase (TDD 5건)"
```

---

## Phase 2: UI 계층

### Task 5: HomeViewModel 활동 로드

**Files:** Modify `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt`
> 룰 11: 활동은 Success 상태에 반영(일회성 SideEffect 아님). VM 단위테스트 부재 → Task 7 게이트.

- [ ] **Step 1:** import 추가:
```kotlin
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.usecase.GetTodayActivityUseCase
```

- [ ] **Step 2:** `HomeUiState.Success` 에 필드 2개 추가:
```kotlin
    @Immutable
    data class Success(
        val plan: WeeklyPlan,
        val isHealthConnectAvailable: Boolean = true,
        val hasHealthPermission: Boolean = false,
        val completedCount: Int = 0,
        val totalWorkoutDays: Int = 0,
        val todayActivity: DailyActivity? = null,
        val hasActivityPermission: Boolean = false,
    ) : HomeUiState() {
        val completionRate: Float get() = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f
    }
```

- [ ] **Step 3:** 생성자에 use case 주입 (기존 파라미터 뒤):
```kotlin
    private val getTodayActivity: GetTodayActivityUseCase,
```

- [ ] **Step 4:** `loadPlan()` 의 `_uiState.value = successWithStats(...)` 줄 바로 다음에 활동 로드 호출 추가:
```kotlin
                _uiState.value = successWithStats(sync.plan, sync.isAvailable, sync.hasPermission)
                loadTodayActivity()
```

- [ ] **Step 5:** `loadPlan()` 아래에 메서드 2개 추가:
```kotlin
    private fun loadTodayActivity() = viewModelScope.launch {
        val result = getTodayActivity().getOrNull() ?: return@launch
        val current = _uiState.value
        if (current is HomeUiState.Success) {
            _uiState.value = current.copy(
                todayActivity = result.activity,
                hasActivityPermission = result.hasPermission,
            )
        }
    }

    fun refreshActivity() = loadTodayActivity()
```

- [ ] **Step 6 (bash):** compile + commit:
```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt
git commit -m "feat(home): ViewModel 오늘 활동 로드 (render-first 백그라운드)"
```

---

### Task 6: HomeScreen TodayActivityCard + 권한 launcher

**Files:** Modify `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt`
> Compose → Task 7 게이트/수동.

- [ ] **Step 1:** import 추가:
```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import com.gunnys.eundunhealth.data.healthconnect.HealthConnectDataSource
import com.gunnys.eundunhealth.domain.model.DailyActivity
```

- [ ] **Step 2:** `HomeUiState.Success` 분기의 `LazyColumn` 에서, `WeeklyProgressCard` item 다음에 활동 카드 item 추가:
```kotlin
                        item {
                            TodayActivityCard(
                                activity = state.todayActivity,
                                hasPermission = state.hasActivityPermission,
                                isAvailable = state.isHealthConnectAvailable,
                                onRefresh = { viewModel.refreshActivity() },
                            )
                        }
```
(기존 `if (!state.isHealthConnectAvailable) { ... } else if (!state.hasHealthPermission) { ... }` 분기 위에 둔다.)

- [ ] **Step 3:** `HealthConnectUnavailableCard` 아래(파일 하단 private composable 영역)에 카드 추가:
```kotlin
@Composable
private fun TodayActivityCard(
    activity: DailyActivity?,
    hasPermission: Boolean,
    isAvailable: Boolean,
    onRefresh: () -> Unit,
) {
    if (!isAvailable) return  // HC 미설치는 HealthConnectUnavailableCard 가 커버

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
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 4 (bash):** assembleDebug + commit:
```bash
./gradlew :app:assembleDebug 2>&1 | tail -6
git add app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt
git commit -m "feat(home): 오늘의 활동 카드 (걸음·칼로리·심박) + 권한 launcher"
```

---

## Phase 3: 검증 + PR

### Task 7: 전체 검증 (게이트 + 수동)
- [ ] **Step 1 (bash):** `./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug 2>&1 | tail -15` → BUILD SUCCESSFUL. (detekt baseline drift 시: 시그니처 갱신 `config/detekt/baseline.xml` + `baseline-debug.xml` 동기. 새 코드 위반은 reformat, 박제 금지.)
- [ ] **Step 2 (수동, 기기):** HC 에 오늘 걸음/칼로리/심박 기록 있는 기기에서 → 홈 "오늘의 활동" 카드 → 무권한 시 "연동" → 허용 후 값 표시 / 기록 없으면 "오늘 활동 기록 없음" / HC 미설치 시 카드 비노출 확인.

---

### Task 8: push + PR
- [ ] **Step 1 (bash):**
```bash
git push -u origin feat/health-daily-activity
gh pr create --base main --title "feat(health): #2 홈 오늘의 활동 요약 (걸음·칼로리·심박)" --body "$(cat <<'BODY'
## Summary
- 홈 WeeklyProgressCard 아래 TodayActivityCard — 오늘 걸음수/소모칼로리/평균심박 (HC aggregate 1회 IPC)
- GetTodayActivityUseCase (TDD 5) + HealthRepository/DataSource 확장 + 활동 전용 권한 set
- HomeViewModel render-first 백그라운드 로드, 룰 11 준수. 표시 전용(백엔드 무변경)
- design+plan 문서 동반

## Test Plan
- [x] spotlessCheck / detektDebug / testDebugUnitTest(GetTodayActivity 5 + 기존) / assembleDebug
- [ ] 수동: HC 활동 기록 → 카드 표시 / 연동 / 빈상태 (Task 7)

## Follow-ups
- Play Console Health 권한 선언(READ_STEPS/READ_TOTAL_CALORIES_BURNED/READ_HEART_RATE) — #4
- 통계 주간 추이(B안), RestingHeartRate 정교화 — 후속

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

---

## 잔여 리스크 / 후속 작업
- R1 Play Console Health 권한 선언(#4). R2 BPM_AVG 거칢(RestingHeartRate 후속). R3 TotalCalories=BMR 포함("소모 칼로리" 일반화). R4 detekt baseline drift(시그니처 갱신).

## Postmortem
> (PR 머지 + 7일 후 채움. 없으면 "특이사항 없음".)

---

## PR 머지 후 (수동, 컨벤션)
design+plan 페어를 `logs/android.md` Recent 최상단 압축 entry 로 흡수 + 페어 `git rm` + `bash scripts/gen-plans-index.sh`. (#1 흡수 사례 참조 — entry + git rm 둘 다.)
