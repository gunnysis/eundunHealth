---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: v0.1.17
ledger_topic: process-infra
tags: [audit, pre-release, security, a11y, rule-8, test]
---

# 공개 출시 전 전체 감사 구현 계획

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task.

**Goal:** v0.1.16/30 공개 출시 전 7개 도메인 전수 감사 결과(CRITICAL 2건·HIGH 7건·MED 4건) 수정 — Android 코드/테스트, Backend 테스트, 문서 동기화를 포괄한다.

**Architecture (요약):** Android는 UDF-Enhanced ViewModel 패턴(룰 11)·Rule 8 준수 inline 에러 배너·AuthErrorBanner 재사용. Backend는 pytest 경계값 보완. 문서는 SSoT(version.properties·requirements.txt)를 기준으로 CLAUDE.md·README·TRD 동기화. 브랜치: `fix/pre-release-audit`.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2026.05.01, Python 3.12, pytest, mockk

**참고:**
- Design: `docs/plans/2026-06-18-pre-release-full-audit-design.md`
- Branch: `fix/pre-release-audit`

**중요 원칙:**
- TDD: VM/Test 변경은 red → green → commit
- 모든 commit은 `fix/pre-release-audit` 브랜치, 최종 PR 1개
- Windows 호스트: PowerShell 7 primary, bash는 .sh 스크립트용
- Rule 8 준수 체크리스트 (모든 에러 UI): inline + persistent + liveRegion Polite + Sentry breadcrumb

**감사 트리아지 (MEASURED 2026-06-18):**

| 심각도 | # | 내용 |
|--------|---|------|
| CRITICAL | 1 | OnboardingScreen 에러 상태 없음 (Rule 8 위반) |
| CRITICAL | 2 | 문서 드리프트: FastAPI 0.136.1→실제 0.137.1 (supply-chain 보안 맥락) |
| HIGH | 3 | HistoryScreen a11y: 완료 아이콘 contentDescription null |
| HIGH | 4 | HomeViewModel toggle 실패 Snackbar 단독 (Rule 8 위반) |
| HIGH | 5 | ProfileViewModel save/delete 실패 Snackbar 단독 (Rule 8 위반) |
| HIGH | 6 | BadgeViewModelTest 없음 (12개 VM 중 유일 누락) |
| HIGH | 7-9 | 문서 드리프트: Sentry Android/Backend, App version |
| MED | 10 | Backend 프로필 극단값 경계 테스트 누락 |
| MED | 11 | account_service.py resp.text 전체 로깅 (defensive) |
| MED | 12-13 | TRD Alembic head/SQLAlchemy 버전 낙후 |

**Task 순서:**

```
Task 0  브랜치 생성
Task 1  OnboardingViewModel + Screen 에러 상태 (CRITICAL)
Task 2  HistoryScreen a11y 수정 (HIGH)
Task 3  HomeViewModel toggle 실패 Rule 8 (HIGH)
Task 4  ProfileViewModel save/delete 실패 Rule 8 (HIGH)
Task 5  BadgeViewModelTest 작성 (HIGH)
Task 6  Backend 프로필 극단값 테스트 (MED)
Task 7  account_service.py 로그 개선 (MED)
Task 8  문서 동기화: CLAUDE.md + README + TRD (CRITICAL/HIGH)
Task 9  Android 최종 게이트
Task 10 Backend 최종 게이트
Task 11 gen-plans-index + version bump + push + PR
```

---

## Phase 1: Android CRITICAL/HIGH 수정

### Task 0: 브랜치 생성

**Files:** (없음)

- [ ] **Step 1: 브랜치 생성 및 전환** (PowerShell)

```powershell
git checkout -b fix/pre-release-audit
git status
```

Expected: `On branch fix/pre-release-audit, nothing to commit`

---

### Task 1: OnboardingViewModel + Screen 에러 상태 (CRITICAL)

**근거:** 감사 결과 — OnboardingScreen에 서버 저장 실패 시 Snackbar 단독 표시, Rule 8 위반. 에러 상태 없음.  
**공식 참조:** CLAUDE.md 룰 8 체크리스트, `ui/components/AuthErrorBanner.kt` (재사용)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: OnboardingViewModel.kt 수정**

`OnboardingUiState`에 `error: AppError? = null` 추가.  
`saveProfile` 실패 경로를 Snackbar SideEffect → UiState error 변경.  
로그인 없음도 동일 처리.

```kotlin
// OnboardingUiState — error 필드 추가
@Immutable
data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

// OnboardingSideEffect — ShowSnackbar 제거 (NavigateToHome만 유지)
@Immutable
sealed class OnboardingSideEffect {
    data object NavigateToHome : OnboardingSideEffect()
}
```

`saveProfile` 함수 전체 교체:
```kotlin
fun saveProfile(heightCm: Float, weightKg: Float, bodyFatPct: Float, muscleMassKg: Float) = viewModelScope.launch {
    _uiState.value = OnboardingUiState(isLoading = true, error = null)
    val userId = authRepo.getCurrentUserId()
    if (userId == null) {
        _uiState.value = OnboardingUiState(isLoading = false, error = AppError.Auth("로그인이 필요합니다"))
        return@launch
    }
    runCatching {
        userRepo.saveProfile(
            UserProfile(userId, heightCm, weightKg, bodyFatPct, muscleMassKg),
        ).getOrThrow()
    }
        .onSuccess { _sideEffect.send(OnboardingSideEffect.NavigateToHome) }
        .onFailure {
            val appErr = it.toAppError()
            appErr.reportToSentry()
            _uiState.value = OnboardingUiState(isLoading = false, error = appErr)
        }
    if (_uiState.value.isLoading) {
        _uiState.value = _uiState.value.copy(isLoading = false)
    }
}
```

필요 import 추가:
```kotlin
import com.gunnys.eundunhealth.domain.model.AppError
```

- [ ] **Step 2: OnboardingScreen.kt 수정**

`ObserveAsEvents` 에서 `ShowSnackbar` 분기 제거 (NavigateToHome만 처리).  
`SnackbarHost` 제거 (더 이상 불필요).  
`uiState.error` 비null이면 `AuthErrorBanner` 표시 (헤드라인 아래, 첫 슬라이더 위).

상단 import 추가:
```kotlin
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
```

`remember { SnackbarHostState() }` 라인 및 `Scaffold(snackbarHost = ...)` 제거,
`SnackbarHost`, `SnackbarHostState` import 제거.

`ObserveAsEvents` 블록:
```kotlin
ObserveAsEvents(viewModel.sideEffect) { effect ->
    when (effect) {
        is OnboardingSideEffect.NavigateToHome -> onComplete()
    }
}
```

Column 내 헤드라인 Text 바로 아래에 에러 배너 삽입:
```kotlin
uiState.error?.let { err ->
    Spacer(modifier = Modifier.height(8.dp))
    AuthErrorBanner(error = err, screen = "onboarding")
    Spacer(modifier = Modifier.height(8.dp))
}
```

- [ ] **Step 3: 테스트 실행**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.onboarding.*" --info 2>&1 | Select-String -Pattern "PASSED|FAILED|tests"
```

기존 OnboardingViewModelTest (@Test 2개)가 여전히 PASS여야 함.  
`ShowSnackbar` SideEffect를 expect하는 테스트가 있다면 수정 필요.

- [ ] **Step 4: commit** (PowerShell)

```powershell
git add app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingViewModel.kt `
        app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt
git commit -m "fix(onboarding): Rule 8 — 저장 실패 시 inline AuthErrorBanner (Snackbar 단독 제거)"
```

---

### Task 2: HistoryScreen a11y 수정 (HIGH)

**근거:** 감사 결과 — HistoryScreen.kt 라인 151, 완료/미완료 아이콘 `contentDescription = null`.  
**공식 참조:** [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility) — informative icon must have description.

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/history/HistoryScreen.kt:151`

- [ ] **Step 1: contentDescription 수정**

라인 151의 `contentDescription = null`을 교체:
```kotlin
// Before
contentDescription = null,

// After
contentDescription = if (day.isCompleted) "완료" else "미완료",
```

- [ ] **Step 2: 테스트 실행 (빌드 검증)**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.history.*" 2>&1 | Select-String "PASSED|FAILED|tests"
```

- [ ] **Step 3: commit**

```powershell
git add app/src/main/java/com/gunnys/eundunhealth/ui/history/HistoryScreen.kt
git commit -m "fix(history): a11y — 완료/미완료 아이콘 contentDescription 추가"
```

---

### Task 3: HomeViewModel toggle 실패 Rule 8 (HIGH)

**근거:** 감사 결과 — HomeViewModel.kt 라인 186, toggle 실패 시 `_sideEffect.trySend(HomeSideEffect.ShowSnackbar(...))` 단독. Rule 8 위반.  
**접근:** `HomeUiState.Success`에 `toggleError: AppError? = null` 추가 → HomeScreen에 persistent 배너.

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt`

- [ ] **Step 1: HomeUiState.Success에 toggleError 필드 추가**

`HomeViewModel.kt`의 `HomeUiState.Success` data class에 필드 추가:
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
    val toggleError: AppError? = null,   // ← 추가
) : HomeUiState() {
    val completionRate: Float get() = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f
}
```

- [ ] **Step 2: toggle 실패 경로 수정**

`toggleDayCompletion` 내 `.onFailure` 블록 (라인 173~187):
```kotlin
.onFailure {
    // revert (기존 로직 유지)
    val live = _uiState.value
    if (live is HomeUiState.Success) {
        _uiState.value = live.copy(
            plan = current.plan,
            completedCount = current.completedCount,
            totalWorkoutDays = current.totalWorkoutDays,
            toggleError = it.toAppError(),   // ← 추가
        )
    }
    val appErr = it.toAppError()
    appErr.reportToSentry()
    // ShowSnackbar trySend 제거
}
```

- [ ] **Step 3: 다음 toggle 시 toggleError 초기화**

`toggleDayCompletion` 함수 초입 optimistic update 블록(라인 ~163) 직후:
```kotlin
_uiState.value = current.copy(
    plan = updatedPlan,
    completedCount = updatedPlan.days.count { !it.isRestDay && it.isCompleted },
    totalWorkoutDays = updatedPlan.days.count { !it.isRestDay },
    toggleError = null,   // ← 다음 toggle 시 에러 초기화
)
```

- [ ] **Step 4: HomeScreen.kt — toggleError 배너 표시**

`HomeScreen.kt`를 읽고, `HomeUiState.Success` 처리 블록 내 LazyColumn 또는 주 콘텐츠 시작 직전에 삽입:

```kotlin
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
```

Success 상태 처리 내 (주간계획 카드 위, 상단 스크롤 영역 첫 번째 item으로):
```kotlin
state.toggleError?.let { err ->
    AuthErrorBanner(
        error = err,
        screen = "home_toggle",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
```

- [ ] **Step 5: HomeViewModelTest 회귀 확인**

기존 HomeViewModelTest에서 `HomeSideEffect.ShowSnackbar`를 toggle 실패 경로에서 expect하는 테스트가 있다면 `toggleError` 상태 확인으로 변경:
```kotlin
// 변경 전 (있는 경우)
// sideEffects.first() is HomeSideEffect.ShowSnackbar

// 변경 후
val state = vm.uiState.value as HomeUiState.Success
assertNotNull(state.toggleError)
```

```powershell
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.home.*" 2>&1 | Select-String "PASSED|FAILED|tests"
```

- [ ] **Step 6: commit**

```powershell
git add app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt `
        app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt
git commit -m "fix(home): Rule 8 — toggle 실패 시 toggleError 상태 + AuthErrorBanner (Snackbar 단독 제거)"
```

---

### Task 4: ProfileViewModel save/delete 실패 Rule 8 (HIGH)

**근거:** 감사 결과 — ProfileViewModel.kt 라인 107, 127, saveProfile/deleteAccount 실패 시 Snackbar 단독. Rule 8 위반.  
**접근:** `ProfileUiState.Loaded`에 `saveError`·`deleteError` 추가.

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt`

- [ ] **Step 1: ProfileUiState.Loaded에 에러 필드 추가**

```kotlin
@Immutable data class Loaded(
    val profile: UserProfile,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveError: AppError? = null,    // ← 추가
    val deleteError: AppError? = null,  // ← 추가
) : ProfileUiState()
```

- [ ] **Step 2: saveProfile 실패 경로 수정**

라인 104~111 교체:
```kotlin
.onFailure {
    val appErr = it.toAppError()
    appErr.reportToSentry()
    if (current is ProfileUiState.Loaded) {
        _uiState.value = current.copy(isSaving = false, saveError = appErr)
    }
}
// 기존 finally-like 블록에서 isSaving 초기화가 Failure 경로에도 적용되어 있으면 제거
// (위에서 이미 copy로 isSaving=false 설정)
```

saveProfile 함수 초입(저장 시도 시 saveError 초기화):
```kotlin
if (current is ProfileUiState.Loaded) {
    _uiState.value = current.copy(isSaving = true, saveError = null)
}
```

`ProfileSideEffect.ShowSnackbar` — saveProfile 실패 경로에서 send 제거. 성공 케이스 Snackbar는 유지 (룰 8 예외: 비critical 성공 알림 OK).

- [ ] **Step 3: deleteAccount 실패 경로 수정**

라인 121~128 교체:
```kotlin
.onFailure {
    val appErr = it.toAppError()
    appErr.reportToSentry()
    if (current is ProfileUiState.Loaded) {
        _uiState.value = current.copy(isDeleting = false, deleteError = appErr)
    }
}
```

deleteAccount 함수 초입(deleteError 초기화):
```kotlin
if (current is ProfileUiState.Loaded) {
    _uiState.value = current.copy(isDeleting = true, deleteError = null)
}
```

- [ ] **Step 4: ProfileScreen.kt — 에러 배너 표시**

ProfileScreen.kt를 읽고, Loaded 상태 처리 내 저장 버튼 위에 `saveError` 배너, 계정 삭제 섹션 근처에 `deleteError` 배너 추가:

```kotlin
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
```

저장 버튼 위:
```kotlin
state.saveError?.let { err ->
    Spacer(modifier = Modifier.height(8.dp))
    AuthErrorBanner(error = err, screen = "profile_save")
    Spacer(modifier = Modifier.height(4.dp))
}
```

계정 삭제 버튼 위:
```kotlin
state.deleteError?.let { err ->
    Spacer(modifier = Modifier.height(8.dp))
    AuthErrorBanner(error = err, screen = "profile_delete")
    Spacer(modifier = Modifier.height(4.dp))
}
```

- [ ] **Step 5: 테스트 회귀 확인**

ProfileViewModelTest의 기존 ShowSnackbar expect가 save/delete 실패 경로에 있다면 `saveError`/`deleteError` 상태 확인으로 변경.

```powershell
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.profile.*" 2>&1 | Select-String "PASSED|FAILED|tests"
```

- [ ] **Step 6: commit**

```powershell
git add app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt `
        app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt
git commit -m "fix(profile): Rule 8 — save/delete 실패 시 inline 에러 배너 (Snackbar 단독 제거)"
```

---

### Task 5: BadgeViewModelTest 작성 (HIGH)

**근거:** 감사 결과 — 12개 ViewModel 중 BadgeViewModel만 단위 테스트 없음. BadgeCatalog.all이 9개 항목이므로 earned=emptyList()여도 Loaded(9 items) 상태.

**Files:**
- Create: `app/src/test/java/com/gunnys/eundunhealth/ui/badge/BadgeViewModelTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package com.gunnys.eundunhealth.ui.badge

import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BadgeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var badgeRepo: BadgeRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        badgeRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `뱃지 로드 성공 — 미획득 목록도 카탈로그 전체 표시 Loaded`() = runTest {
        coEvery { badgeRepo.getEarnedBadges() } returns Result.success(emptyList())
        val vm = BadgeViewModel(badgeRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Loaded but was $state", state is BadgeUiState.Loaded)
        val items = (state as BadgeUiState.Loaded).badges
        assertTrue("카탈로그 항목 있어야 함", items.isNotEmpty())
        assertTrue("미획득 상태", items.none { it.earned })
    }

    @Test
    fun `획득 뱃지 있으면 해당 항목 earned=true`() = runTest {
        val earned = listOf(
            Badge(key = "first_workout", earnedAt = java.time.Instant.now()),
        )
        coEvery { badgeRepo.getEarnedBadges() } returns Result.success(earned)
        val vm = BadgeViewModel(badgeRepo)
        advanceUntilIdle()

        val state = vm.uiState.value as BadgeUiState.Loaded
        val firstWorkout = state.badges.find { it.key == "first_workout" }
        assertNotNull("first_workout 항목 존재해야 함", firstWorkout)
        assertTrue("first_workout 획득 표시", firstWorkout!!.earned)
    }

    @Test
    fun `뱃지 로드 실패 시 Error 상태`() = runTest {
        coEvery { badgeRepo.getEarnedBadges() } returns Result.failure(RuntimeException("네트워크 오류"))
        val vm = BadgeViewModel(badgeRepo)
        advanceUntilIdle()

        assertTrue("Expected Error", vm.uiState.value is BadgeUiState.Error)
    }
}
```

- [ ] **Step 2: 테스트 실행 — RED 확인**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.badge.BadgeViewModelTest" 2>&1 | Select-String "PASSED|FAILED|ERROR|tests"
```

Expected: 테스트 파일이 없었으므로 이제 처음 실행. `Badge` data class import 오류가 있을 경우 올바른 경로로 수정 (`domain.model.Badge`).

- [ ] **Step 3: 테스트 실행 — GREEN 확인**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.badge.BadgeViewModelTest" 2>&1 | Select-String "PASSED|FAILED|tests"
```

Expected: `3 tests completed, 0 failed`

- [ ] **Step 4: commit**

```powershell
git add app/src/test/java/com/gunnys/eundunhealth/ui/badge/BadgeViewModelTest.kt
git commit -m "test(badge): BadgeViewModelTest 추가 — Loaded·earned·Error 3케이스"
```

---

## Phase 2: Backend MED 수정

### Task 6: 프로필 극단값 경계 테스트 (MED)

**근거:** 감사 결과 — backend test_edge_cases.py에 height/weight 극단값 입력 검증 테스트 없음.  
**공식 참조:** [Pydantic Field constraints](https://docs.pydantic.dev/latest/concepts/fields/) — `ge`, `le` 범위 검증이 코드에 있으므로 400 응답이 보장되어야 함.

**Files:**
- Modify: `backend/tests/test_edge_cases.py`

- [ ] **Step 1: 극단값 테스트 추가**

`backend/tests/test_edge_cases.py` 파일을 읽고 기존 테스트 패턴 파악 후 다음 테스트 추가:

```python
async def test_profile_height_below_minimum_returns_422(client: AsyncClient, auth_headers: dict):
    """height_cm < 50 → 422 Pydantic 검증 오류."""
    resp = await client.put(
        "/profile",
        json={"heightCm": 10.0, "weightKg": 65.0, "bodyFatPct": 20.0, "muscleMassKg": 30.0},
        headers=auth_headers,
    )
    assert resp.status_code == 422


async def test_profile_weight_above_maximum_returns_422(client: AsyncClient, auth_headers: dict):
    """weight_kg > 500 → 422 Pydantic 검증 오류."""
    resp = await client.put(
        "/profile",
        json={"heightCm": 170.0, "weightKg": 999.0, "bodyFatPct": 20.0, "muscleMassKg": 30.0},
        headers=auth_headers,
    )
    assert resp.status_code == 422


async def test_profile_height_above_maximum_returns_422(client: AsyncClient, auth_headers: dict):
    """height_cm > 300 → 422 Pydantic 검증 오류."""
    resp = await client.put(
        "/profile",
        json={"heightCm": 400.0, "weightKg": 65.0, "bodyFatPct": 20.0, "muscleMassKg": 30.0},
        headers=auth_headers,
    )
    assert resp.status_code == 422
```

- [ ] **Step 2: 테스트 실행** (PowerShell, backend 디렉토리에서)

```powershell
cd backend
.venv/Scripts/pytest tests/test_edge_cases.py -v 2>&1 | Select-String "PASSED|FAILED|ERROR|passed|failed"
cd ..
```

Expected: 새 테스트 3개 PASS (Pydantic `ge=50, le=300` 등 이미 구현됨)

- [ ] **Step 3: commit**

```powershell
git add backend/tests/test_edge_cases.py
git commit -m "test(backend): 프로필 극단값 경계 테스트 3건 추가 (height/weight 범위 검증)"
```

---

### Task 7: account_service.py 로그 개선 (MED)

**근거:** 감사 결과 — account_service.py 라인 120, `resp.text` 전체 로깅. Defensive: 구조화된 message만 추출.  
**원칙:** 외부 API 오류 응답에서 message 필드만 추출하여 로깅 (예측 불가 응답 대비).

**Files:**
- Modify: `backend/app/services/account_service.py:120`

- [ ] **Step 1: 로그 추출 개선**

라인 120 교체:
```python
# Before
logger.error(f"Supabase user deletion failed: {resp.status_code} {resp.text}")

# After
try:
    error_detail = resp.json().get("message", "unknown")
except Exception:
    error_detail = "non-JSON response"
logger.error("Supabase user deletion failed: %s %s", resp.status_code, error_detail)
```

- [ ] **Step 2: Backend 테스트 실행 (회귀 없음 확인)**

```powershell
cd backend
.venv/Scripts/pytest tests/test_account.py -v 2>&1 | Select-String "PASSED|FAILED|passed|failed"
cd ..
```

Expected: 기존 5 tests PASS.

- [ ] **Step 3: commit**

```powershell
git add backend/app/services/account_service.py
git commit -m "fix(backend): account_service Supabase 오류 로그 — resp.text 전체 → message 필드만 추출"
```

---

## Phase 3: 문서 동기화 (CRITICAL/HIGH)

### Task 8: CLAUDE.md + README.md + docs/TRD.md 버전 드리프트 수정

**근거:** 감사 결과 — MEASURED 기준 5건의 버전/내용 불일치.  
**SSoT:** `gradle/libs.versions.toml`, `backend/requirements.txt`, `version.properties`

**발견된 드리프트 (MEASURED 2026-06-18):**

| 파일 | 항목 | 현재 기재 | SSoT 실제값 |
|------|------|----------|-----------|
| CLAUDE.md | App version | 0.1.15 / versionCode 29 | 0.1.16 / 30 |
| CLAUDE.md | Sentry Android | 8.43.1 | 8.43.2 (libs.versions.toml) |
| CLAUDE.md | Sentry SDK (backend) | 2.61.1 | 2.63.0 (requirements.txt) |
| CLAUDE.md | FastAPI | 0.136.1 | 0.137.1 (requirements.txt) |
| CLAUDE.md | @Test 수 언급 | "118→138" | 현재 139 (@Test MEASURED) |
| README.md | Sentry Android | 8.43.1 | 8.43.2 |
| README.md | FastAPI | 0.136.1 | 0.137.1 |
| docs/TRD.md | FastAPI | 0.136.1 | 0.137.1 |
| docs/TRD.md | SQLAlchemy | 2.0.50 | 2.0.51 (requirements.txt) |
| docs/TRD.md | Alembic head | c849579de6c4 | b78b256c2b20 |

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`
- Modify: `docs/TRD.md`

- [ ] **Step 1: CLAUDE.md 수정**

다음 항목들을 정확히 수정 (파일 내 grep으로 위치 확인 후 Edit):

1. App version 라인: `versionName **\`0.1.15\`**, versionCode **\`29\`**` → `versionName **\`0.1.16\`**, versionCode **\`30\`**`
2. Sentry Android 라인: `Sentry Android 8.43.1` → `Sentry Android 8.43.2`
3. Sentry SDK backend 라인: `Sentry SDK 2.61.1` → `Sentry SDK 2.63.0`
4. FastAPI 라인: `FastAPI 0.136.1` → `FastAPI 0.137.1`
   - 이 변경에 supply-chain 맥락 주석 추가(인라인):
     `FastAPI 0.137.1 (0.136.x → MAL-2026-4750 supply-chain 침해 회피)`
5. `@Test 118→138` → `@Test 118→139`

- [ ] **Step 2: README.md 수정**

1. `Sentry Android 8.43.1` → `Sentry Android 8.43.2`
2. `FastAPI 0.136.1` → `FastAPI 0.137.1`

- [ ] **Step 3: docs/TRD.md 수정**

1. `FastAPI 0.136.1` → `FastAPI 0.137.1`
2. `SQLAlchemy 2.0.50` → `SQLAlchemy 2.0.51`  
3. `c849579de6c4` → `b78b256c2b20` (Alembic head)
   - 설명도 업데이트: "user_profile_history (user_id, recorded_at) 복합 인덱스"

- [ ] **Step 4: 드리프트 해소 확인**

```powershell
# CLAUDE.md 수정 확인
Select-String -Path CLAUDE.md -Pattern "0.1.16|8.43.2|2.63.0|0.137.1|118→139"
# README 확인
Select-String -Path README.md -Pattern "8.43.2|0.137.1"
# TRD 확인
Select-String -Path docs/TRD.md -Pattern "0.137.1|2.0.51|b78b256c2b20"
```

Expected: 각 파일에 새 값들이 검색됨.

- [ ] **Step 5: commit**

```powershell
git add CLAUDE.md README.md docs/TRD.md
git commit -m "docs: 버전 드리프트 수정 — FastAPI 0.137.1(MAL-2026-4750), Sentry 8.43.2/2.63.0, @Test 139, Alembic b78b256c2b20"
```

---

## Phase 4: 최종 게이트

### Task 9: Android 최종 게이트

- [ ] **Step 1: spotless 포맷 검사**

```powershell
./gradlew :app:spotlessCheck 2>&1 | Select-String "BUILD|FAILED|spotless"
```

Expected: `BUILD SUCCESSFUL`  
실패 시: `./gradlew :app:spotlessApply` 실행 후 재시도.

- [ ] **Step 2: detekt 정적 분석**

```powershell
./gradlew :app:detektDebug 2>&1 | Select-String "BUILD|FAILED|violation"
```

Expected: `BUILD SUCCESSFUL` (baseline 내)

- [ ] **Step 3: 전체 단위 테스트**

```powershell
./gradlew :app:testDebugUnitTest 2>&1 | Select-String "tests|PASSED|FAILED|BUILD"
```

Expected: **@Test 142 이상** (기존 139 + BadgeViewModelTest 3 = 142).  
FAILED 0.

- [ ] **Step 4: collectAsState 안티패턴 검사 (룰 11)**

```powershell
Select-String -Path "app/src/main/java/com/gunnys/eundunhealth" -Pattern "\.collectAsState\(\)" -Recurse
```

Expected: **0건**

---

### Task 10: Backend 최종 게이트

- [ ] **Step 1: ruff**

```powershell
cd backend; .venv/Scripts/ruff check app/ tests/; cd ..
```

Expected: no issues.

- [ ] **Step 2: mypy**

```powershell
cd backend; .venv/Scripts/python -m mypy app/; cd ..
```

Expected: `Success: no issues found`

- [ ] **Step 3: bandit**

```powershell
cd backend; .venv/Scripts/bandit -r app -ll; cd ..
```

Expected: `No issues identified.`

- [ ] **Step 4: pip-audit**

```powershell
cd backend; .venv/Scripts/pip-audit -r requirements.txt --strict --ignore-vuln PYSEC-2026-161; cd ..
```

Expected: `No known vulnerabilities found`

- [ ] **Step 5: pytest**

```powershell
cd backend; .venv/Scripts/pytest tests/ -v 2>&1 | Select-String "passed|failed|error"; cd ..
```

Expected: 기존 70 + 신규 3 = **73 passed**, 0 failed

---

### Task 11: gen-plans-index + version bump + push + PR

- [ ] **Step 1: gen-plans-index 실행 (plans README 갱신)**

```powershell
bash scripts/gen-plans-index.sh
```

- [ ] **Step 2: plans index 스테이지**

```powershell
git add docs/plans/README.md
```

- [ ] **Step 3: version bump v0.1.17**

```powershell
bash scripts/bump-version.sh 0.1.17
```

Expected: `version.properties` versionName=0.1.17, versionCode=31  
`git diff --stat` 로 의도치 않은 매칭 수동 확인 (CLAUDE.md 조항 — bump 후 꼭 확인).

- [ ] **Step 4: bump 결과 커밋**

```powershell
git add version.properties CLAUDE.md README.md docs/ops/operations-snapshot.md
git commit -m "chore: version bump 0.1.17 (versionCode 31) — pre-release audit 완료"
```

- [ ] **Step 5: push**

```powershell
git push -u origin fix/pre-release-audit
```

- [ ] **Step 6: PR 생성**

```powershell
gh pr create `
  --title "fix: 공개 출시 전 전체 감사 수정 (v0.1.17)" `
  --body "$(cat <<'EOF'
## Summary

출시 전 7개 도메인 전수 감사(2026-06-18) 결과 CRITICAL/HIGH/MED 이슈 수정.

### Android
- **CRITICAL**: OnboardingScreen 저장 실패 → inline `AuthErrorBanner` (Rule 8 준수, Snackbar 단독 제거)
- **HIGH**: HistoryScreen 완료 아이콘 `contentDescription` 추가 (a11y)
- **HIGH**: HomeScreen 토글 실패 → `toggleError` 상태 + inline 배너 (Rule 8)
- **HIGH**: ProfileScreen 저장/삭제 실패 → `saveError`/`deleteError` 상태 + inline 배너 (Rule 8)
- **HIGH**: `BadgeViewModelTest` 신규 3개 (@Test 139→142)

### Backend
- **MED**: 프로필 극단값 경계 테스트 3건 추가 (height < 50, > 300, weight > 500)
- **MED**: `account_service` Supabase 오류 로그 — `resp.text` 전체 → message 필드만 추출

### 문서
- **CRITICAL**: FastAPI 0.136.1 → 0.137.1 (MAL-2026-4750 supply-chain 회피 맥락 명시)
- **HIGH**: Sentry Android 8.43.1 → 8.43.2, Sentry SDK 2.61.1 → 2.63.0
- **HIGH**: App version CLAUDE.md 드리프트 수정 (0.1.15/29 → 0.1.16/30)
- **MED**: TRD Alembic head, SQLAlchemy 버전 동기화

## Test plan

- [ ] Android @Test 142 (기존 139 + BadgeViewModelTest 3), 0 failed
- [ ] `./gradlew :app:spotlessCheck :app:detektDebug` BUILD SUCCESSFUL
- [ ] `collectAsState()` 0건 (룰 11)
- [ ] Backend pytest 73 passed (기존 70 + 경계값 3), ruff/mypy/bandit/pip-audit clean
- [ ] 실기기(Flip3): OnboardingScreen 저장 오류 시 배너 표시, HistoryScreen 완료 아이콘 TalkBack 읽기

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 잔여 리스크 / 후속 작업

- **Kotlin 2.4 업그레이드**: Hilt 2.59.3+ 출시 대기 중 — 여전히 deferred (`docs/ops/dependency-deferred.md`)
- **weekly_plans 복합 인덱스**: MED 성능 개선 — 트래픽 증가 후 재검토 (DB EXPLAIN ANALYZE 필요)
- **Year boundary 테스트**: LOW — Dec 28 ~ Jan 3 주간 경계 테스트
- **실기기 검증**: Task 11 PR 후 Flip3에서 OnboardingScreen / HistoryScreen TalkBack 직접 검증 필요 (회원님 수행)

## Postmortem

> (PR 머지 + 7일 후 채움.)

---

## PR 머지 후

페어 파일 핵심 결정 + outcome → `docs/plans/logs/process-infra.md` Recent 섹션 추가 → `git rm` 페어.
