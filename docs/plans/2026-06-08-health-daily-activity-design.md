---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0
ledger_topic: android
tags: [health-connect, daily-activity, steps, calories, heart-rate, home]
---

# #2 홈 "오늘의 활동" 요약 (걸음·칼로리·심박) 설계

- **작성일**: 2026-06-08
- **상태**: 작성 중 (proposed)
- **연관 작업**: `docs/plans/2026-06-08-health-data-roadmap-design.md` (#2) · #1(PR #84, HealthRepository 기반) · Phase 0 공식 문서 연구
- **대상 버전**: v0.2.0
- **선행 작업**: 없음 (#1 머지 완료 — HealthRepository/HealthConnectDataSource 패턴 존재)

---

## 1. 배경

홈 화면은 "이번 주 운동 계획"만 보여준다. 갤럭시 워치/폰이 측정한 **오늘의 활동량**(걸음수·소모 칼로리·심박)은 Health Connect 에 이미 들어와 있으나(걸음수는 워치·폰 모두 삼성헬스→HC 동기화됨 — Phase 0 R2 해소) 앱이 표시하지 않는다. 매일 여는 메인 화면에 **glanceable 활동 요약**을 더해 동기와 engagement 를 높인다.

### Phase 0 확정 사실
- 누적형은 **aggregate API** 사용(수동 readRecords+합산 금지 — 중복 카운트): `StepsRecord.COUNT_TOTAL`, `TotalCaloriesBurnedRecord.ENERGY_TOTAL`, `HeartRateRecord.BPM_AVG`. 한 `AggregateRequest` 에 3 metric 동시 → IPC 1회.
- 권한: `READ_STEPS`, `READ_TOTAL_CALORIES_BURNED`, `READ_HEART_RATE`.
- **걸음수는 삼성헬스→HC 동기화됨**(폰 만보계 + 갤럭시 워치). 미동기화는 삼성의 *activity tracker*(별개 기능)뿐 — 우리 지표와 무관.
- 표시 전용 → **백엔드 변경 없음**.

## 2. Scope

### In-scope
- 홈 `WeeklyProgressCard` 아래 **`TodayActivityCard`**: 오늘(0시~현재) 걸음수·소모 칼로리·평균 심박. 값 없는 지표는 숨김.
- 활동 전용 권한 set + 카드 내 "연동" 버튼(in-Composable launcher) + 빈/무권한 상태.

### Out-of-scope
- 통계 화면 주간 추이 차트(B안) — 후속/별도 plan.
- 거리·수면·활동 칼로리(Active) — YAGNI.
- 백엔드/DB 변경(표시 전용).

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 위치/단위 | 홈 "오늘의 활동" 일일 요약 카드 | 매일 여는 메인 화면, glanceable. 최소 범위 |
| D2 | 지표 | 걸음수 + 소모 칼로리 + 평균 심박 | 로드맵 #2 핵심 3종 |
| D3 | 칼로리 타입 | `TotalCaloriesBurnedRecord` | **삼성헬스가 HC로 동기화하는 칼로리**(Active 아님) → 갤럭시워치 가용성 ↑ |
| D4 | 심박 | `BPM_AVG`(오늘 평균) | 단순·glanceable. 데이터 없으면 숨김 |
| D5 | 권한 | 활동 전용 `DAILY_ACTIVITY_PERMISSIONS` + 홈 in-Composable launcher | 화면별 필요 권한만 contextual(#1 패턴). exercise 권한과 분리 |
| D6 | 로드 | plan 렌더 후 백그라운드 활동 로드 | #83 render-first 계승(홈 지연 없음) |
| D7 | 백엔드 | 변경 없음 | 표시 전용 |

## 4. 옵션 비교

| 옵션 | A. 홈 일일 요약 (채택) | B. 통계 주간 추이 | C. 둘 다 |
|---|---|---|---|
| 가치 시점 | 즉시(메인 화면) | 추세 중심 | 최대 |
| 범위/권한/테스트 | 작음 | 중간(Vico) | 큼 |
| 결정 | ✅ | 후속 | 분리 |

권한: **활동 전용 set 분리** vs exercise 권한에 bundle → 분리 채택(화면 맥락·#1 일관성).

## 5. 구성 요소별 변경

### 5.1 MODIFY: `app/src/main/AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
```
> Play Console Health 권한 선언 갱신 필요(§8 R1).

### 5.2 NEW: `domain/model/DailyActivity.kt`
```kotlin
@Immutable
data class DailyActivity(
    val steps: Long?,
    val totalCaloriesKcal: Int?,
    val avgHeartRateBpm: Long?,
) {
    val hasAny: Boolean get() = steps != null || totalCaloriesKcal != null || avgHeartRateBpm != null
}
```

### 5.3 MODIFY: `data/healthconnect/HealthConnectDataSource.kt`
```kotlin
val DAILY_ACTIVITY_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
)
suspend fun hasDailyActivityPermissions(): Boolean { /* containsAll */ }

// 오늘 0시~현재 aggregate 3 metric (IPC 1회)
suspend fun readTodayActivity(): DailyActivity {
    val zone = ZoneId.systemDefault()
    val filter = TimeRangeFilter.between(LocalDate.now(zone).atStartOfDay(zone).toInstant(), Instant.now())
    val res = client.aggregate(AggregateRequest(
        metrics = setOf(StepsRecord.COUNT_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL, HeartRateRecord.BPM_AVG),
        timeRangeFilter = filter,
    ))
    return DailyActivity(
        steps = res[StepsRecord.COUNT_TOTAL],
        totalCaloriesKcal = res[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toInt(),
        avgHeartRateBpm = res[HeartRateRecord.BPM_AVG],
    )
}
```

### 5.4 MODIFY: `domain/repository/HealthRepository.kt` (+impl)
```kotlin
suspend fun hasDailyActivityPermissions(): Boolean
suspend fun getTodayActivity(): Result<DailyActivity?>
```

### 5.5 NEW: `domain/usecase/GetTodayActivityUseCase.kt`
```kotlin
suspend operator fun invoke(): Result<DailyActivity?> = runCatching {
    if (!healthRepo.isAvailable() || !healthRepo.hasDailyActivityPermissions()) return@runCatching null
    healthRepo.getTodayActivity().getOrElse { it.toAppError().reportToSentry(); null }
}
```

### 5.6 MODIFY: `ui/home/HomeViewModel.kt`
- `HomeUiState.Success` 에 `todayActivity: DailyActivity? = null` + `hasActivityPermission: Boolean = false` 추가.
- `loadPlan()` 의 plan 렌더 직후 `loadTodayActivity()` 호출(백그라운드).
- `loadTodayActivity()`: `getTodayActivity()` + `healthRepo.hasDailyActivityPermissions()` → 현재 Success 상태를 `copy(todayActivity=…, hasActivityPermission=…)`.
- `refreshActivity()`: 권한 허용 후 재로드(=loadTodayActivity).

### 5.7 MODIFY: `ui/home/HomeScreen.kt`
- `WeeklyProgressCard` 아래 item 으로 `TodayActivityCard(state.todayActivity, state.hasActivityPermission, onConnect, ...)`.
- 카드 분기: 무권한 → "활동 데이터 연동" 버튼(in-Composable `rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract())`, `DAILY_ACTIVITY_PERMISSIONS`) → granted 시 `viewModel.refreshActivity()`. 권한 있고 `todayActivity?.hasAny != true` → "오늘 활동 기록 없음". 데이터 있음 → 👟걸음/🔥kcal/❤bpm metric row(null 지표 숨김).
- HC 미설치(`!isHealthConnectAvailable`)면 활동 카드 비노출(기존 `HealthConnectUnavailableCard` 가 커버).

## 6. 검증 계획

### 6.1 테스트 (Phase 4 TDD)
- `GetTodayActivityUseCaseTest`: 정상(3값) / 무권한→null / 비가용→null / read실패→null(success) / 데이터없음→`DailyActivity(null,null,null)`. Fake HealthRepository 확장(getTodayActivity/hasDailyActivityPermissions).

### 6.2 게이트
- spotlessCheck + detektDebug + testDebugUnitTest + assembleDebug green. detekt baseline drift 시 시그니처 갱신(억제 아닌 dead code 제거).

### 6.X 추정값 → 측정 (룰 9)
- 변경 파일 수: **ESTIMATE-ONLY ~8개** (plan 작성 시 `MEASURED`).
- 권한 문자열 3종 + aggregate metric: **MEASURED** (Phase 0, context7 + developer.android.com).

## 7. 롤백 절차
- 단일 feature branch PR → revert. 권한 매니페스트 3줄 + 카드 revert. 백엔드/DB 무변경이라 데이터 리스크 없음.

## 8. 잔여 리스크
- **R1 (Play Console)**: READ_STEPS/READ_TOTAL_CALORIES_BURNED/READ_HEART_RATE 추가 → Health 권한 선언 양식 갱신(#4 연계, 출시 전).
- **R2 (HR 평균 의미)**: 하루 BPM_AVG 는 휴식+활동 혼합이라 거칠다. 데이터 없으면 숨김. 추후 RestingHeartRate 정교화 가능(후속).
- **R3 (TotalCalories=BMR 포함)**: 표기 "소모 칼로리" 로 일반화. Active 칼로리 분리는 후속.
- **R4 (detekt baseline)**: HomeViewModel/HomeScreen 라인 시프트 시 시그니처 갱신(#1·#83 lesson).

## 9. 참고 자료
- `docs/plans/2026-06-08-health-data-roadmap-design.md` (#2, R2 해소)
- Health Connect aggregate: developer.android.com/health-and-fitness/health-connect/aggregate-data
- Samsung 동기화(걸음수): developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect
- #1 PR #84 (체성분 import — 동일 계층 패턴) · #83 PR (render-first)
