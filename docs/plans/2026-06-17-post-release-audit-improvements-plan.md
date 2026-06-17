---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: v0.1.16
ledger_topic: process-infra
tags: [audit, performance, accessibility, testing, backend, refactoring]
---

# 출시 후 심층 감사 — 개선 Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` (또는 `subagent-driven-development`) 로 task-by-task 구현.

**Goal:** v0.1.15 출시 후 심층 감사에서 확정된 Tier 1 개선 5건(A~E)을 TDD 로 구현하고, Tier 2/3 는 후속 actionable task 로 남긴다.

**Architecture (요약):** A=백엔드 JWKS 동기호출을 `asyncio.to_thread` 오프로드 + timeout 5s. B=`RetryInterceptor` mockk 단위테스트. C=`GoalUiState.error` 추가 + 기존 `ErrorContent` 재사용. D=`DayPlanCard` 포맷팅 `remember` hoist. E=활동 지표 `mergeDescendants`/`clearAndSetSemantics` a11y.

**Tech Stack:** Python 3.12 / FastAPI / PyJWT · Kotlin 2.2.10 / Compose BOM 2026.05.01 / mockk 1.14.11 / JUnit4 / OkHttp 5.3.2

**참고:**
- Design: `docs/plans/2026-06-17-post-release-audit-improvements-design.md`
- Branch: `feature/deep-audit-improvements` (Task 0 에서 생성)

**중요 원칙:**
- TDD: 동작 변경 task 는 red → green → commit. D/E 는 Compose UI(계측 부재) → 컴파일+detekt+실기기.
- 모든 commit 은 `feature/deep-audit-improvements`, 최종 PR 1개.
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시.
- 백엔드 게이트 mypy 는 `python -m mypy`(래퍼 깨짐 — 메모리 [[mypy-exe-wrapper-broken]]).

**Task 순서:**
```
Task 0   branch + 환경 확인 + baseline 측정(룰 9)
Task A   백엔드 JWKS 오프로드 + timeout (TDD)
Task B   RetryInterceptorTest 신설 (test-only)
Task C   GoalScreen 에러 상태 (TDD: GoalViewModelTest → VM → Screen)
Task D   DayPlanCard 포맷팅 remember hoist
Task E   오늘의 활동 a11y
Task F   전체 회귀 게이트 + 버전 bump
Task G   push + PR
[optional] Tier 2/3 후속 task (T2a~T2e, T3a~T3c)
```

---

## Phase 0: 준비

### Task 0: branch + baseline

- [ ] **Step 1 (bash): 브랜치 생성**
```bash
git checkout -b feature/deep-audit-improvements
git branch --show-current   # 기대: feature/deep-audit-improvements
```

- [ ] **Step 2 (bash): baseline 측정 (룰 9 — 측정 후 진행)**
```bash
ls app/src/test/java/com/gunnys/eundunhealth/**/*.kt | wc -l   # 기대: 23
grep -rc "@Test" app/src/test/java | awk -F: '{s+=$2} END{print s}'  # 기대: 118
grep -ci mockwebserver gradle/libs.versions.toml              # 기대: 0
```
결과를 PR 본문에 기록. 불일치 시 design §6.2 측정값 재검토.

---

## Phase 1: A — 백엔드 JWKS 블로킹 제거 (TDD)

### Task A: `get_signing_key_from_jwt` 오프로드 + timeout 5s

**Files:**
- Modify: `backend/app/dependencies.py`
- Test: `backend/tests/test_dependencies.py` (기존 4건 + 신규 2건)

- [ ] **Step 1: 실패 테스트 2건 추가** (`backend/tests/test_dependencies.py` 하단)
```python
import threading


@pytest.mark.asyncio
async def test_signing_key_lookup_runs_off_event_loop(monkeypatch):
    """JWKS 동기 조회가 워커 스레드로 오프로드되어 이벤트 루프를 막지 않는다."""
    main_thread = threading.get_ident()
    captured: dict[str, int] = {}

    class _ThreadCapturingJwk:
        def get_signing_key_from_jwt(self, token):
            captured["thread"] = threading.get_ident()
            return type("K", (), {"key": "fake-key"})()

    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _ThreadCapturingJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: {"sub": "user-1"})

    result = await dependencies.get_current_user_id(_creds(), _settings())

    assert result == "user-1"
    assert captured["thread"] != main_thread  # 워커 스레드 = 루프 블로킹 안 함


def test_jwk_client_uses_short_timeout(monkeypatch):
    """느린 JWKS 가 워커를 30초간 점유하지 못하게 기본 30s → 5s."""
    dependencies._jwk_client = None  # 모듈 전역 캐시 리셋
    client = dependencies._get_jwk_client("https://test.supabase.co")
    assert client.timeout == 5
    dependencies._jwk_client = None  # 다른 테스트 격리
```

- [ ] **Step 2: 실패 확인**
```bash
cd backend && .venv/Scripts/pytest tests/test_dependencies.py -v
```
기대: `test_signing_key_lookup_runs_off_event_loop` FAIL(같은 스레드에서 실행 → captured==main), `test_jwk_client_uses_short_timeout` FAIL(timeout==30).

- [ ] **Step 3: 구현** (`backend/app/dependencies.py`)
  - 파일 상단 `import jwt` 위에 `import asyncio` 추가.
  - `_get_jwk_client` 의 `PyJWKClient(...)` 에 `timeout=5,` 추가(`lifespan=86400,` 다음 줄).
  - `get_current_user_id` 안:
```python
        signing_key = await asyncio.to_thread(jwk_client.get_signing_key_from_jwt, credentials.credentials)
```
(기존 `signing_key = jwk_client.get_signing_key_from_jwt(credentials.credentials)` 교체)

- [ ] **Step 4: 통과 확인 (기존 4 + 신규 2 = 6)**
```bash
cd backend && .venv/Scripts/pytest tests/test_dependencies.py -v
```
기대: 6 PASS.

- [ ] **Step 5: 백엔드 게이트**
```bash
cd backend && .venv/Scripts/pytest tests/ -q && .venv/Scripts/ruff check app/ tests/ && .venv/Scripts/python -m mypy app/
```
기대: 73 PASS · ruff clean · mypy clean. (스펙 무변경이므로 `sync-openapi.sh` 불필요.)

- [ ] **Step 6: commit (bash)**
```bash
git add backend/app/dependencies.py backend/tests/test_dependencies.py
git commit -m "fix(backend): offload blocking JWKS lookup off event loop + 5s timeout"
```

---

## Phase 2: B — RetryInterceptor 단위 테스트 (test-only)

### Task B: `RetryInterceptorTest` 신설

**Files:**
- Create: `app/src/test/java/com/gunnys/eundunhealth/data/remote/interceptor/RetryInterceptorTest.kt`

- [ ] **Step 1: 테스트 작성** (전체 파일)
```kotlin
package com.gunnys.eundunhealth.data.remote.interceptor

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RetryInterceptorTest {

    private val request = Request.Builder().url("http://localhost/test").build()

    private fun response(code: Int): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 500..599) "Server Error" else "OK")
        .body("".toResponseBody(null))
        .build()

    private fun chainReturning(vararg responses: Response): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany responses.toList()
        return chain
    }

    @Test
    fun `500 다음 200 이면 재시도 후 200 을 반환한다`() {
        val chain = chainReturning(response(500), response(200))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(200, result.code)
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test
    fun `영속 500 이면 maxRetries 후 마지막 500 을 반환한다 (throw 아님)`() {
        val chain = chainReturning(response(500), response(500), response(500))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(500, result.code)
        verify(exactly = 3) { chain.proceed(request) }
    }

    @Test
    fun `첫 응답이 200 이면 재시도 없이 즉시 반환한다`() {
        val chain = chainReturning(response(200))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(200, result.code)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `4xx 는 재시도하지 않고 즉시 반환한다`() {
        val chain = chainReturning(response(404))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(404, result.code)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `IOException 다음 성공이면 복구한다`() {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        var calls = 0
        every { chain.proceed(request) } answers {
            calls++
            if (calls < 2) throw IOException("transient") else response(200)
        }
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(200, result.code)
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test(expected = IOException::class)
    fun `영속 IOException 이면 maxRetries 후 마지막 예외를 throw 한다`() {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws IOException("down")
        RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
    }
}
```

- [ ] **Step 2: 실행 (기존 코드 그대로 통과 — 가드 추가이므로 즉시 GREEN)**
```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.data.remote.interceptor.RetryInterceptorTest"
```
기대: 6 PASS. (이 task 는 기존 동작을 박제하는 가드라 RED 없이 GREEN — design D3.)
> 만약 FAIL 하면 RetryInterceptor 의 실제 동작이 예상과 다른 것 → 버그 발견. 그 경우 systematic-debugging 으로 근본원인 후 테스트/코드 정합.

- [ ] **Step 3: spotless**
```bash
./gradlew :app:spotlessApply
```

- [ ] **Step 4: commit (bash)**
```bash
git add app/src/test/java/com/gunnys/eundunhealth/data/remote/interceptor/RetryInterceptorTest.kt
git commit -m "test(android): add RetryInterceptor unit tests (retry/backoff/leak guard)"
```

---

## Phase 3: C — GoalScreen 에러 상태 (TDD)

### Task C1: GoalViewModel 에러 노출 (TDD)

**Files:**
- Create: `app/src/test/java/com/gunnys/eundunhealth/ui/goal/GoalViewModelTest.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalViewModel.kt`

- [ ] **Step 1: 실패 테스트 작성** (전체 파일)
```kotlin
package com.gunnys.eundunhealth.ui.goal

import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.repository.GoalRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var goalRepo: GoalRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        goalRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load 성공 시 goals 가 채워지고 error 는 null`() = runTest {
        coEvery { goalRepo.getGoals() } returns Result.success(
            listOf(Goal(type = GoalType.WEIGHT, targetValue = 70f, createdAt = null)),
        )
        coEvery { goalRepo.getProfileHistory() } returns Result.success(emptyList())

        val vm = GoalViewModel(goalRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.goals.size)
        assertNull(state.error)
    }

    @Test
    fun `load 실패 시 error 가 set 되고 isLoading 은 false (silent empty 금지)`() = runTest {
        coEvery { goalRepo.getGoals() } returns Result.failure(RuntimeException("network"))
        coEvery { goalRepo.getProfileHistory() } returns Result.success(emptyList())

        val vm = GoalViewModel(goalRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull("로드 실패는 error 로 노출되어야 한다", state.error)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `에러 후 재시도(load) 성공 시 error 가 사라진다`() = runTest {
        coEvery { goalRepo.getGoals() } returns Result.failure(RuntimeException("network"))
        coEvery { goalRepo.getProfileHistory() } returns Result.success(emptyList())
        val vm = GoalViewModel(goalRepo)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)

        coEvery { goalRepo.getGoals() } returns Result.success(emptyList())
        vm.load()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
    }
}
```

- [ ] **Step 2: 실패 확인**
```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.goal.GoalViewModelTest"
```
기대: 컴파일 에러(`GoalUiState` 에 `error` 없음) 또는 FAIL. `error` 필드 추가 전이므로 RED.

- [ ] **Step 3: 구현** (`GoalViewModel.kt`)
  - import 추가: `import com.gunnys.eundunhealth.domain.model.AppError`
  - `GoalUiState` 에 필드 추가:
```kotlin
@Immutable
data class GoalUiState(
    val goals: List<Goal> = emptyList(),
    val history: List<ProfileHistoryPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AppError? = null,
)
```
  - `load()` 교체:
```kotlin
    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val goalsResult = goalRepo.getGoals()
        val historyResult = goalRepo.getProfileHistory()

        val firstError = goalsResult.exceptionOrNull() ?: historyResult.exceptionOrNull()
        if (firstError != null) {
            val appErr = firstError.toAppError()
            appErr.reportToSentry()
            _uiState.value = _uiState.value.copy(isLoading = false, error = appErr)
            return@launch
        }
        _uiState.value = GoalUiState(
            goals = goalsResult.getOrDefault(emptyList()),
            history = historyResult.getOrDefault(emptyList()),
            isLoading = false,
        )
    }
```
(`saveGoal` / `handleError` 는 그대로 — 저장 실패는 snackbar 유지.)

- [ ] **Step 4: 통과 확인**
```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.goal.GoalViewModelTest"
```
기대: 3 PASS.

- [ ] **Step 5: commit (bash)**
```bash
./gradlew :app:spotlessApply
git add app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalViewModel.kt app/src/test/java/com/gunnys/eundunhealth/ui/goal/GoalViewModelTest.kt
git commit -m "feat(goal): surface load failures as error state (GoalViewModel)"
```

### Task C2: GoalScreen 에러 분기 (Compose — 컴파일 가드)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt`

- [ ] **Step 1: 구현**
  - import 추가: `import com.gunnys.eundunhealth.ui.components.ErrorContent`
  - `GoalScreen.kt:79-83` 의 `if (uiState.isLoading) {...} else {` 를 다음으로 교체:
```kotlin
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null && uiState.goals.isEmpty()) {
            ErrorContent(
                error = uiState.error!!,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )
        } else {
```
(나머지 `Column { ... }` 블록은 불변.)

- [ ] **Step 2: 컴파일 + 정적분석**
```bash
./gradlew :app:compileDebugKotlin :app:detektDebug :app:spotlessCheck
```
기대: BUILD SUCCESSFUL.

- [ ] **Step 3: commit (bash)**
```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt
git commit -m "feat(goal): show ErrorContent with retry on load failure (GoalScreen)"
```

---

## Phase 4: D — DayPlanCard 포맷팅 hoist

### Task D: `remember(day.date)` 로 locale 포맷팅 캐시

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt:179-180,197`

- [ ] **Step 1: 구현**
  - import 확인: `androidx.compose.runtime.remember` (이미 있을 가능성 높음; 없으면 추가).
  - `DayPlanCard` 상단 `:179-180` 교체:
```kotlin
    val dayLabel = remember(day.date) {
        val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        "$name (${day.date.monthValue}/${day.date.dayOfMonth})"
    }
```
  - 사용처 `:197` `Text("$dayName ($dateStr)", ...)` → `Text(dayLabel, style = MaterialTheme.typography.titleMedium)`

- [ ] **Step 2: 컴파일 + 정적분석**
```bash
./gradlew :app:compileDebugKotlin :app:detektDebug :app:spotlessCheck
```
기대: BUILD SUCCESSFUL. (미사용 import `TextStyle`/`Locale` 잔존 여부 확인 — 여전히 `remember` 안에서 사용하므로 유지.)

- [ ] **Step 3: commit (bash)**
```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt
git commit -m "perf(home): hoist DayPlanCard locale formatting into remember(day.date)"
```

---

## Phase 5: E — 오늘의 활동 a11y

### Task E: 활동 지표 mergeDescendants + 이모지 clearAndSetSemantics

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/components/TodayActivityCard.kt:78-99`

- [ ] **Step 1: 구현**
  - import 추가:
```kotlin
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
```
(`semantics` 는 이미 import 됨.)
  - 호출부 `:78-80` 교체:
```kotlin
                        activity.steps?.let { ActivityMetric("👟", "$it", "걸음", "걸음 $it 보") }
                        activity.totalCaloriesKcal?.let { ActivityMetric("🔥", "$it", "kcal", "소모 칼로리 $it kcal") }
                        activity.avgHeartRateBpm?.let { ActivityMetric("❤", "$it", "bpm", "평균 심박 $it bpm") }
```
  - `ActivityMetric` 교체 `:88-99`:
```kotlin
@Composable
private fun ActivityMetric(icon: String, value: String, unit: String, contentDesc: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = contentDesc },
    ) {
        Text(
            icon,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 2: 컴파일 + 정적분석**
```bash
./gradlew :app:compileDebugKotlin :app:detektDebug :app:spotlessCheck
```
기대: BUILD SUCCESSFUL.

- [ ] **Step 3: commit (bash)**
```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/home/components/TodayActivityCard.kt
git commit -m "a11y(home): merge activity metric semantics + mute emoji for TalkBack"
```

---

## Phase 6: 최종 검증 + 버전 + PR

### Task F: 전체 회귀 + 버전 bump

- [ ] **Step 1 (bash): Android 전체 게이트**
```bash
./gradlew :app:testDebugUnitTest :app:detektDebug :app:spotlessCheck
```
기대: BUILD SUCCESSFUL, @Test 합 = 118 + 6(B) + 3(C) = 127. (룰 9 — 실제 출력으로 확인.)

- [ ] **Step 2 (bash): Backend 전체 게이트**
```bash
cd backend && .venv/Scripts/pytest tests/ -q && .venv/Scripts/ruff check app/ tests/ && .venv/Scripts/python -m mypy app/ && .venv/Scripts/bandit -r app -ll
```
기대: 73 PASS · clean.

- [ ] **Step 3 (bash): 버전 bump (Android 변경 포함 → versionCode +1)**
```bash
bash scripts/bump-version.sh 0.1.16
git --no-pager diff --stat   # bump-version blind-replace drift 수동 검토(메모리 INC-27 교훈)
```
의도치 않은 과거 버전 치환이 있으면 정정. CHANGELOG 항목 작성(`/changelog` 또는 수동).

- [ ] **Step 4 (bash): bump commit**
```bash
git add version.properties docs/CHANGELOG.md README.md docs/PRD.md docs/ops/operations-snapshot.md
git commit -m "release: v0.1.16 (versionCode 30) — 심층 감사 개선 A~E"
```

### Task G: push + PR

- [ ] **Step 1 (bash): push**
```bash
git push -u origin feature/deep-audit-improvements
```

- [ ] **Step 2 (bash): PR 생성**
```bash
gh pr create --title "심층 감사 개선 A~E: JWKS 오프로드 · RetryInterceptor 테스트 · Goal 에러상태 · DayPlanCard perf · 활동 a11y" \
  --body "design: docs/plans/2026-06-17-post-release-audit-improvements-design.md ..."
```
PR 본문에 Task 0 baseline 측정값 + 게이트 결과 + 실기기(E TalkBack / D 애니메이션) 검증 결과 기록.

- [ ] **Step 3: 머지 후** — design+plan 페어를 `docs/plans/logs/process-infra.md` Recent 에 entry 흡수 + 페어 `git rm` (plan.md 말미 컨벤션). `bash scripts/gen-plans-index.sh` 로 INDEX 갱신.

---

## Phase 7 (선택): Tier 2/3 후속

> Tier 1 머지 후 별도 사이클. design §5.7~§5.8 근거 확정 완료, 실행만 남음.

### Task T2a: 무테스트 ViewModel 회귀 테스트 (Android, test-only)
ProfileViewModel(계정삭제/저장 null-userId 가드 — HIGH), HistoryViewModel(페이지네이션 `hasMore` 경계), StatisticsViewModel(empty/loaded/error 분기), OnboardingViewModel(null-userId 가드). `HomeViewModelTest`/`GoalViewModelTest` 패턴 재사용(StandardTestDispatcher + mockk). 각 3~4 케이스.

### Task T2b: user_profile_history 복합 인덱스 (Backend, 룰 7)
`backend/app/models/user_profile_history.py` 에 `__table_args__ = (Index("ix_history_user_recorded", "user_id", recorded_at.desc()),)` + `recorded_at` 의 `index=` 조정. **룰 7 필수**: `bash scripts/alembic-autogen.sh "history user+recorded composite index"` → `docker compose up -d --build` entrypoint `alembic upgrade head` + `/health` 확인 → operations-snapshot Alembic head 갱신. **데이터 증가 시 실행**(현재 영향 ≈0).

### Task T2c: history COUNT 최적화 (Backend)
`weekly_plan_service.get_history` 가 `page==0` 일 때만 `count_by_user` 호출하거나 `count(*) over()` window 로 1쿼리화. 회귀: `test_weekly_plan.py` 멀티페이지 경계 테스트 추가(12건 생성, `?page=1&size=10` → 2건 + totalCount=12).

### Task T2d: 풀 pool_pre_ping + size 재검토 (Backend)
`main.py:51` `create_async_engine(..., pool_size=3, max_overflow=0)` → `pool_pre_ping=True` 추가 검토(idle stale 연결 방지). `pool_size` 상향은 PG B1ms 연결 한도 측정 후(DEFERRED — 동시 사용자 실측).

### Task T2e: 계정삭제 orphan reaper (Backend, 후속)
`account_service` auth 삭제 후 DB purge 실패 시 orphan 정리 배치 — Container Apps Jobs 패턴. release 차단 아님.

### Task T3a: requirements.txt 주석 정리 (Backend)
`backend/requirements.txt:1-5` 의 `0.136.x` MAL 주석을 실제 핀(0.137.1, 미영향) 기준으로 갱신.

### Task T3b: sentry-sdk 2.62.0 → 2.63.0 (Backend)
`requirements.txt:20`. `pip-audit` + 테스트 게이트.

### Task T3c: i18n 의도 명문화 (docs)
CLAUDE.md(또는 `docs/conventions/`)에 "UI 문자열 한국어 하드코딩 = 의도된 결정(한국어 전용)" 1줄 — 감사 재플래그 차단.

---

## 잔여 리스크 / 후속 작업

- design §8 참조. A 의 `to_thread` 워커풀 포화(콜드스타트 폭주)는 JWKS 24h 캐시로 사실상 인스턴스당 1회라 낮음.
- C 의 부분 실패(history만 실패) 인디케이터는 후속 LOW.
- D/E 는 계측 테스트 부재 → 실기기(Flip3) 검증 필수.

## Postmortem

> (PR 머지 + 7일 후 채움. 없으면 "특이사항 없음" 1줄.)

---

## PR 머지 후 (수동, 컨벤션)

본 페어(design + plan)의 핵심 결정 + outcome 을 15-30줄 entry 로 `docs/plans/logs/process-infra.md` 의 `## Recent (last 90 days)` 맨 위에 추가 → 페어 2파일 `git rm`. `bash scripts/gen-plans-index.sh` 로 INDEX 갱신.
