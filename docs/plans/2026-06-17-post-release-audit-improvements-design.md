---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: v0.1.16
ledger_topic: process-infra
tags: [audit, performance, accessibility, testing, backend, refactoring]
---

# 출시 후 심층 감사 — 개선 항목 기획·설계

- **작성일**: 2026-06-17
- **상태**: 작성 중 (검토 대기)
- **연관 작업**: PR #122/#123/#125 (출시 차단 버그 + 감사 LOW + 후속 백로그) 이후 심층 재감사
- **대상 버전**: v0.1.16 / versionCode 30 (Tier 1 의 Android 부분). 백엔드 항목은 API `1.0.0` 불변(계약 변경 없음).
- **선행 작업**: 없음 (현 `main` `4a5c050` 기준)

## 1. 배경

v0.1.15 출시 사이클(PR #122 빈 운동계획·토글 근본수정 / #123 감사 LOW / #125 후속 백로그 8항목)을 마친 뒤, 5-도메인 병렬 심층 감사(Android 리팩토링·Compose 성능 / Backend 리팩토링·성능·DB / 테스트 커버리지 / 의존성·deprecation 팩트체크 / UX·접근성)를 수행했다. controller(룰 10)가 핵심 측정값을 직접 재검증했고, 각 수정 접근법은 **공식 문서로 fact-check**했다(§9).

**총평: 코드베이스는 건강하다. 출시 차단(blocker) 0건.** 발견 항목은 전부 신뢰성·성능·접근성·테스트 폴리시 수준이다. 본 문서는 발견된 **모든 항목**을 검토·적용 가능하도록 tier 로 분류하고, 각 항목의 수정안을 공식 문서 근거와 함께 확정한다.

### 감사 중 공식 문서가 정정한 2건 (룰 9 — 추정 후 측정 금지)

1. **PyJWKClient timeout** — 감사 에이전트는 "timeout 미설정 → urllib 무한 대기"로 보고했으나, [PyJWT API](https://pyjwt.readthedocs.io/en/stable/api.html) 확인 결과 `PyJWKClient` 는 이미 `timeout: float = 30` **기본값**을 가진다([PR #875](https://github.com/jpadilla/pyjwt/pull/875)). 즉 현재는 무한 대기가 아니라 **30초**. 수정 방향이 "timeout 신설"이 아니라 "**블로킹 오프로드 + 30→5초 단축**"으로 정정됨.
2. **Compose stability config** — 감사 에이전트는 "stability config 부재로 `@Immutable` 이 skippability 를 못 준다"(MED)고 보고했으나, [Strong skipping mode 공식 문서](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) 확인 결과 strong skipping 은 **Kotlin 2.0.20+ 에서 기본 활성**(본 프로젝트 2.2.10). unstable `List`/`LocalDate` 파라미터도 referential equality(`===`)로 skip된다. 단일 `_uiState` StateFlow UDF 에서 list 인스턴스는 상태 변경 시에만 바뀌므로 실질 영향 없음 → **stability config 도입 불필요(Won't-do, §5.6)**.

## 2. Scope

### In-scope

**Tier 1 — 실행 권장 (이번 사이클 대상, 낮은 리스크 · 가치 분명)**
- **A** (Backend): JWKS 서명키 조회 이벤트 루프 블로킹 제거(`asyncio.to_thread` + timeout 5s)
- **B** (Android): `RetryInterceptor` 단위 테스트 신설 (모든 백엔드 호출 경로 가드)
- **C** (Android/UX): `GoalScreen` 에러 상태 추가 (네트워크 실패를 "데이터 없음"으로 오표시하는 silent failure 제거)
- **D** (Android/Perf): `DayPlanCard` 의 매-recomposition locale 포맷팅을 `remember` 로 hoist
- **E** (Android/A11y): "오늘의 활동" 이모지-only 지표를 TalkBack 가독 형태로 (`mergeDescendants` + `clearAndSetSemantics`)

**Tier 2 — defer 가능 (가치 있으나 분량/데이터 증가 의존, 후속 사이클)**
- **T2a**: 무테스트 ViewModel 4종 회귀 테스트 (Profile/History/Statistics/Goal/Onboarding)
- **T2b**: `user_profile_history` 복합 인덱스 `(user_id, recorded_at DESC)` (룰 7 적용)
- **T2c**: `/weekly-plan/history` 페이지마다 `COUNT(*)` 재실행 최적화
- **T2d**: DB 커넥션 풀 `pool_pre_ping` + `pool_size` 재검토
- **T2e**: 계정 삭제 orphan reaper (auth 삭제 후 DB purge 실패 정리 배치)

**Tier 3 — housekeeping (사소)**
- **T3a**: `requirements.txt` 상단 `0.136.x` MAL 주석 정리 (실제 핀 0.137.1 과 불일치)
- **T3b**: `sentry-sdk` 2.62.0 → 2.63.0 (패치 1)
- **T3c**: i18n 의도(한국어 전용 하드코딩) CLAUDE.md 명문화 — 향후 감사 재플래그 방지

### Out-of-scope

- **Compose stability config / kotlinx.collections.immutable 도입** (이유: strong skipping 기본 활성으로 불필요 — §5.6 Won't-do)
- **Kotlin 2.3/2.4 업그레이드** (이유: Hilt 2.59.2 가 Kotlin 2.3+ 미지원, `docs/ops/dependency-deferred.md` — 공식 [Dagger 릴리스](https://github.com/google/dagger/releases) + CI #117/#118 실패로 재확인. 올바르게 차단됨)
- **i18n 전면 리소스화** (이유: 한국어 전용 제품, T3c 로 결정만 명문화. 실 리소스화는 다국어 요구 발생 시)
- **0 androidTest → 계측 테스트 도입** (이유: 로직이 VM/repo 에 있어 JVM 단위로 충분. HC 경로만 계측 가치 있으나 큰 투자 — 별도 검토)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | A — 블로킹 오프로드 방식 | `asyncio.to_thread` (stdlib) | Python 3.12, contextvar 복사 자동, 표준 방식. `run_in_threadpool` 대비 의존성 없음 ([FastAPI async 가이드](https://github.com/zhanymkanov/fastapi-best-practices)) |
| D2 | A — timeout 값 | 30s → **5s** | 기본 30s 는 느린 JWKS 가 워커 1개를 30초 점유. 5s = Supabase JWKS 정상 응답(수십 ms) 대비 충분히 여유 + 장애 시 빠른 실패 |
| D3 | B — interceptor 테스트 방식 | mockk `Interceptor.Chain` + `initialDelayMs=0L` | MockWebServer 의존성 부재(`libs.versions.toml` 확인). mockk 는 이미 사용 중. `initialDelayMs=0` 으로 실제 `Thread.sleep` 회피(테스트 ms) |
| D4 | C — 에러 표시 컴포넌트 | 기존 `ui/components/ErrorContent` 재사용 | 이미 `onRetry` + a11y `liveRegion` 보유. Statistics/Badge 가 동일 패턴 사용 — 신규 컴포넌트 불필요(YAGNI) |
| D5 | C — 에러 분기 조건 | `error != null && goals.isEmpty()` 일 때 전체화면 ErrorContent | "데이터 있는데 부분 실패"는 기존 화면 유지가 나음. 빈 상태 + 실패만 전체 에러 전환 |
| D6 | D — 포맷팅 hoist 방식 | `remember(day.date)` 단일 블록 | strong skipping 무관(애니메이션으로 recompose 발생). key=`day.date` 면 날짜 동일 시 재계산 0. 형제 화면(History/Statistics)이 이미 formatter hoist |
| D7 | E — a11y 병합 방식 | metric `Column` 에 `semantics(mergeDescendants=true){contentDescription}` + 이모지 `clearAndSetSemantics{}` | [공식 merging-clearing 문서](https://developer.android.com/develop/ui/compose/accessibility/merging-clearing) idiom. 값+단위를 한 노드로, 이모지는 미announce |
| D8 | Finding 1(stability config) | **Won't-do** | strong skipping 기본 활성(§1.2) |
| D9 | 페어 ledger_topic | `process-infra` | 교차 도메인 감사 후속. PR #125(audit-followup) 선례와 일치 |
| D10 | Tier 2/3 | 본 plan 의 optional task / 후속 | Tier 1 만 mandatory. 데이터 증가·분량 의존 항목은 근거만 확정, 실행은 후속 |

## 4. 옵션 비교

### A — 블로킹 오프로드 (D1)

| 옵션 | A. `asyncio.to_thread` (채택) | B. `fastapi.concurrency.run_in_threadpool` | C. 그대로 두기 |
|---|---|---|---|
| 의존성 | stdlib only | FastAPI 모듈 import | - |
| contextvar | 자동 복사 | 자동 복사 | - |
| 이벤트 루프 보호 | ✅ | ✅ | ❌ 콜드스타트/키로테이션 시 최대 5s(수정후) 루프 정지 |
| 채택 이유 | 표준·최소 의존성 | 동등하나 import 추가 | 24h 캐시로 평소 무해하나 워스트케이스 루프 정지 잔존 |

### B — interceptor 테스트 (D3)

| 옵션 | A. mockk Chain (채택) | B. MockWebServer 추가 |
|---|---|---|
| 신규 의존성 | 없음 | `mockwebserver3` testImplementation 추가 |
| 실제 네트워크 | 불필요(순수 단위) | 로컬 소켓 |
| 속도 | `initialDelayMs=0` 으로 ms | 소켓 셋업 오버헤드 |
| 채택 이유 | RetryInterceptor 가 `chain.request()`/`chain.proceed()` 만 사용 → fake Chain 으로 충분 | 통합 성격, 본 단위 테스트엔 과함 |

## 5. 구성 요소별 변경

### 5.1 A — Backend JWKS 블로킹 제거 (`backend/app/dependencies.py`)

**현재** (`dependencies.py:14-22, 32`):
```python
def _get_jwk_client(supabase_url: str) -> PyJWKClient:
    global _jwk_client
    if _jwk_client is None:
        _jwk_client = PyJWKClient(
            f"{supabase_url}/auth/v1/.well-known/jwks.json",
            cache_keys=True,
            lifespan=86400,  # 24시간 TTL
        )
    return _jwk_client

async def get_current_user_id(...):
    ...
    jwk_client = _get_jwk_client(settings.supabase_url)
    signing_key = jwk_client.get_signing_key_from_jwt(credentials.credentials)  # ← 동기 urllib, 루프 블로킹
```

**변경**:
```python
import asyncio  # 파일 상단에 추가

def _get_jwk_client(supabase_url: str) -> PyJWKClient:
    global _jwk_client
    if _jwk_client is None:
        _jwk_client = PyJWKClient(
            f"{supabase_url}/auth/v1/.well-known/jwks.json",
            cache_keys=True,
            lifespan=86400,
            timeout=5,  # 기본 30s → 5s: 느린 JWKS 가 워커를 오래 점유하지 못하게(D2)
        )
    return _jwk_client

async def get_current_user_id(...):
    ...
    jwk_client = _get_jwk_client(settings.supabase_url)
    # 동기 urllib 호출을 워커 스레드로 오프로드해 이벤트 루프 블로킹 방지(D1).
    # 24h 캐시 적중 시엔 네트워크 없음 — 콜드스타트/키로테이션 시에만 실제 fetch.
    signing_key = await asyncio.to_thread(jwk_client.get_signing_key_from_jwt, credentials.credentials)
```

`to_thread` 는 워커 스레드의 예외를 그대로 전파하므로 기존 `except InvalidTokenError/PyJWKClientError/...` 분기는 불변. 기존 `tests/test_dependencies.py` 4건이 회귀 가드.

### 5.2 B — RetryInterceptor 단위 테스트 (신규 `RetryInterceptorTest.kt`)

대상: `data/remote/interceptor/RetryInterceptor.kt:12-33` (모든 백엔드 호출을 감싸는 미검증 컴포넌트). 테스트 메서드 6개:
1. 500 → 200: `proceed` 정확히 2회, 200 반환
2. 영속 500: `proceed` 정확히 `maxRetries`(3)회, **마지막 500 반환(throw 아님)**
3. 첫 200: `proceed` 1회, 무재시도
4. 4xx(404): 즉시 반환, 무재시도
5. IOException → 200: 복구, `proceed` 2회
6. 영속 IOException: `maxRetries` 후 마지막 예외 throw

`RetryInterceptor(maxRetries = 3, initialDelayMs = 0L)` 로 실제 sleep 회피. 상세 코드는 plan Task B.

### 5.3 C — GoalScreen 에러 상태 (`ui/goal/GoalViewModel.kt` + `GoalScreen.kt`)

**ViewModel** — `GoalUiState` 에 `error` 필드 추가 + `load()` 에서 실패 시 set:
```kotlin
@Immutable
data class GoalUiState(
    val goals: List<Goal> = emptyList(),
    val history: List<ProfileHistoryPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AppError? = null,  // ← 추가
)

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
(기존 `handleError` 는 `saveGoal` 의 snackbar 경로로 유지 — 저장 실패는 화면 전환 없이 snackbar 가 적절.)

**Screen** — `isLoading` 분기 뒤에 `error` 분기 추가 (`GoalScreen.kt:79`):
```kotlin
if (uiState.isLoading) {
    Box(...) { CircularProgressIndicator() }
} else if (uiState.error != null && uiState.goals.isEmpty()) {
    ErrorContent(error = uiState.error!!, modifier = Modifier.padding(padding), onRetry = viewModel::load)
} else {
    Column(...) { /* 기존 */ }
}
```
신규 import: `com.gunnys.eundunhealth.ui.components.ErrorContent`. 회귀 가드: 신규 `GoalViewModelTest`(load 실패→error set / retry→재호출 / 성공→error null).

### 5.4 D — DayPlanCard 포맷팅 hoist (`ui/home/HomeScreen.kt:179-180`)

**현재**: 매 recomposition(토글 시 `animateColorAsState` 로 빈번)마다 `getDisplayName`(ResourceBundle lookup) + 문자열 할당.
```kotlin
val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
val dateStr = "${day.date.monthValue}/${day.date.dayOfMonth}"
```
**변경**:
```kotlin
val dayLabel = remember(day.date) {
    val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    "$name (${day.date.monthValue}/${day.date.dayOfMonth})"
}
```
사용처 `Text("$dayName ($dateStr)", ...)` → `Text(dayLabel, ...)`. [공식 성능 가이드](https://developer.android.com/develop/ui/compose/performance/bestpractices): "composition 중 비싼 계산은 `remember` 로". 검증은 컴파일+detekt(계측 테스트 부재).

### 5.5 E — 오늘의 활동 a11y (`ui/home/components/TodayActivityCard.kt:78-99`)

**현재**: `ActivityMetric("👟", "$it", "걸음")` — TalkBack 이 이모지 + 분리된 값/단위를 읽음.
**변경** (`ActivityMetric` 시그니처에 a11y 라벨 추가):
```kotlin
// 호출부
activity.steps?.let { ActivityMetric("👟", "$it", "걸음", "걸음 $it 보") }
activity.totalCaloriesKcal?.let { ActivityMetric("🔥", "$it", "kcal", "소모 칼로리 $it kcal") }
activity.avgHeartRateBpm?.let { ActivityMetric("❤", "$it", "bpm", "평균 심박 $it bpm") }

@Composable
private fun ActivityMetric(icon: String, value: String, unit: String, contentDesc: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = contentDesc },
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics {})  // 이모지 미announce
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```
신규 import: `androidx.compose.ui.semantics.contentDescription`, `clearAndSetSemantics`, `semantics`. [공식 merging-clearing](https://developer.android.com/develop/ui/compose/accessibility/merging-clearing).

### 5.6 Won't-do — Compose stability config (Finding 1)

도입하지 않는다. strong skipping 이 Kotlin 2.0.20+(본 프로젝트 2.2.10)에서 기본 활성이라 모든 restartable composable 이 unstable 파라미터와 무관하게 skippable 하며 unstable 값은 referential equality 로 비교된다([공식](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)). 단일 `_uiState` StateFlow UDF 에서 list 인스턴스는 상태 변경 시에만 교체되므로 `@Immutable` 어노테이션은 이미 의도한 skippability 를 받는다. config 파일/immutable-collections 도입은 ROI 없음.

### 5.7 Tier 2 — 근거 확정 (실행은 후속)

- **T2b 인덱스** (`backend/app/models/user_profile_history.py:17,22`): 현재 `user_id` 단일 인덱스. 모든 read 가 `WHERE user_id ORDER BY recorded_at DESC LIMIT N`. 복합 인덱스 `Index("ix_history_user_recorded", "user_id", recorded_at.desc())` 추가 시 sort step 제거. **현재 user 당 row 수 적어 실측 영향 ≈0** → 데이터 증가 시 실행. 추가 시 **룰 7**(alembic + entrypoint 검증 + operations-snapshot head 갱신) 필수. [SQLAlchemy 인덱스 가이드](https://www.opcito.com/blogs/a-guide-to-postgresql-indexing-with-sqlalchemy): 복합 인덱스는 WHERE/ORDER BY 동반 컬럼에 유효.
  - 참고: `weekly_plans` 는 `UniqueConstraint(user_id, week_start)` 의 leftmost prefix 가 `week_start DESC` 정렬을 backward scan 으로 커버 → **이미 충분**(추가 불필요). `goals.user_id` 도 unique 제약 prefix 로 커버되나 타 테이블과 표기 비대칭(consistency nit, 선택).
- **T2c COUNT** (`weekly_plan_repo.py:69-74` + `weekly_plan_service.py:100-101`): 페이지마다 full `COUNT(*)`. user 당 plan 수 적어 저렴하나 2-round-trip. `page==0` 에서만 count 반환 또는 `count(*) over()` window 로 1쿼리화. **최적화이지 버그 아님** → 후속.
- **T2d 풀** (`main.py:51`): `pool_size=3, max_overflow=0`. probe 3종 + 동시 사용자 + readiness 가 자체 세션 사용 → 타이트. [SQLAlchemy asyncpg 풀 토론](https://github.com/sqlalchemy/sqlalchemy/discussions/11707) 및 best-practice: idle 후 stale 연결 방지 위해 `pool_pre_ping=True` 권장. `pool_size` 상향(예 5)은 PG B1ms 연결 한도 확인 후. **측정 후 결정**(DEFERRED).
- **T2e reaper** (`account_service.py:43-53`): auth 삭제 성공 후 DB purge 실패 시 orphan 을 로깅+재raise(UoW 롤백으로 부분삭제 없음). 정리 배치 없음 → Container Apps Jobs 패턴(룰 7 예외 항과 동일 결)으로 분리 검토. release 차단 아님(문서상 accepted risk).

### 5.8 Tier 3 — housekeeping

- **T3a** (`requirements.txt:1-5`): 상단 주석이 "0.136.1 로 다운그레이드"를 설명하나 실제 핀은 `0.137.1`(MAL-2026-4750 미영향, 0.136.3 이후). 주석을 현재 상태로 갱신.
- **T3b** (`requirements.txt:20`): `sentry-sdk[fastapi]==2.62.0` → `2.63.0`([PyPI](https://pypi.org/project/sentry-sdk/), 2026-06-16). 패치 1, 선택. CI `pip-audit`/테스트로 검증.
- **T3c**: CLAUDE.md 또는 `docs/conventions/` 에 "UI 문자열 한국어 하드코딩은 의도된 결정(한국어 전용 제품)" 1줄 명문화. 감사 에이전트가 i18n 부재를 매번 재발견하므로 결정 박제로 노이즈 차단.

## 6. 검증 계획

### 6.1 게이트 (Tier 1)

- **Backend (A)**: `pytest tests/ -v`(현 71 + A 신규 2 = 73 PASS 기대) · `ruff check app/ tests/` · `python -m mypy app/`(룰: mypy.exe 깨짐) · `bandit -r app -ll` · `bash scripts/sync-openapi.sh`(스펙 무변경 확인, A 는 계약 변경 없음).
- **Android (B~E)**: `./gradlew :app:testDebugUnitTest`(현 118 + B 6 + C(GoalVM) 3 = 127 @Test 기대) · `./gradlew :app:detektDebug` · `./gradlew :app:spotlessCheck`.

### 6.2 추정값 → 측정 검증 (룰 9)

| 항목 | 라벨 | 측정 명령 / 결과 |
|---|---|---|
| Android 테스트 파일 23개 | MEASURED | `Glob app/src/test/**/*.kt` = 23 |
| Android @Test 118개 | MEASURED (감사 에이전트) | `grep -rc "@Test" app/src/test` 합 = 118 (Task B 에서 controller 재확인) |
| Backend pytest 71 | MEASURED | CLAUDE.md + PR #125 = 71 |
| MockWebServer 의존성 부재 | MEASURED | `grep -i mockwebserver gradle/libs.versions.toml` = 0 |
| hiltViewModel 구위치 import | MEASURED | `grep -rc "androidx.hilt.navigation.compose.hiltViewModel" app/src/main` = 0 |
| PyJWKClient 기본 timeout 30s | MEASURED | [PyJWT API 문서](https://pyjwt.readthedocs.io/en/stable/api.html) `timeout: float = 30` |
| strong skipping 기본 활성 | MEASURED | [공식 문서](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) "enabled by default in Kotlin 2.0.20" |
| D — DayPlanCard 포맷팅 위치 | MEASURED | `HomeScreen.kt:179-180` (본 세션 Read) |
| C — GoalUiState error 필드 부재 | MEASURED | `GoalViewModel.kt:26-32` (본 세션 Read) |

### 6.3 실기기 검증 (계측 테스트 부재 항목)

- **D**(포맷팅), **E**(a11y): 단위 테스트 불가 → **Flip3(SM-F711N) 실기기**로 (룰: 테스트 기기 = Flip3 전용). E 는 TalkBack 켜고 "오늘의 활동" 카드에서 "걸음 N 보"/"소모 칼로리 N kcal"/"평균 심박 N bpm" 음성 확인. D 는 토글 애니메이션 중 버벅임 없음 육안.

## 7. 롤백 절차

- 코드 변경 전부 단일 feature 브랜치 `feature/deep-audit-improvements` + PR. 머지 전 롤백 = 브랜치 폐기.
- A(백엔드)는 머지 시 자동 배포(`backend.yml`). 회귀 시 직전 image 로 revert(`docs/ops/migration-runbook.md`). A 는 동작 보존(오프로드만) → 회귀 가능성 낮음.
- C/D/E(Android)는 버전 bump 후 Play 업로드 전까지 사용자 영향 0. 문제 시 다음 빌드에서 수정.

## 8. 잔여 리스크

- **A**: `to_thread` 워커 스레드 풀(기본 `min(32, cpu+4)`)이 동시 콜드스타트 폭주 시 포화 가능 — 단 JWKS 는 24h 캐시라 사실상 인스턴스당 1회. 리스크 낮음.
- **C**: `error != null && goals.isEmpty()` 조건이라, goals 캐시가 있고 history 만 실패하면 에러 화면 대신 부분 데이터 유지(의도된 D5). history 실패는 snackbar 도 안 뜸 → 후속에서 부분 실패 인디케이터 고려 가능(LOW).
- **E**: `ActivityMetric` 시그니처 변경(파라미터 추가)은 내부(`private`) 한정이라 외부 영향 없음.
- **Tier 2/3 미실행 시**: 운영상 무해(데이터 규모·문서 수준). T2d(풀)만 동시 사용자 급증 시 잠재 — 모니터링 권장.

## 9. 참고 자료 (공식·외부 — fact-check 출처)

- PyJWT — [API Reference (`PyJWKClient` timeout=30 기본)](https://pyjwt.readthedocs.io/en/stable/api.html), [timeout 도입 PR #875](https://github.com/jpadilla/pyjwt/pull/875), [jwks_client.py 소스](https://github.com/jpadilla/pyjwt/blob/master/jwt/jwks_client.py)
- FastAPI/asyncio — [FastAPI Best Practices (run_in_threadpool/to_thread)](https://github.com/zhanymkanov/fastapi-best-practices), [asyncio 블로킹 처리](https://github.com/fastapi/fastapi/discussions/8842)
- Jetpack Compose — [Strong skipping mode](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping), [성능 best practice (remember)](https://developer.android.com/develop/ui/compose/performance/bestpractices), [접근성 Merging & clearing](https://developer.android.com/develop/ui/compose/accessibility/merging-clearing)
- SQLAlchemy/PG — [PostgreSQL indexing with SQLAlchemy](https://www.opcito.com/blogs/a-guide-to-postgresql-indexing-with-sqlalchemy), [asyncpg 풀 max_overflow 토론](https://github.com/sqlalchemy/sqlalchemy/discussions/11707)
- 의존성 — [sentry-sdk PyPI 2.63.0](https://pypi.org/project/sentry-sdk/), [Dagger 릴리스(Hilt Kotlin 2.3 미지원)](https://github.com/google/dagger/releases), [starlette CVE 클러스터(1.3.1 천장)](https://osv.dev/vulnerability/PYSEC-2026-161)
