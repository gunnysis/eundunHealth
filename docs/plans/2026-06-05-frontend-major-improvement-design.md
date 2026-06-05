---
type: design
status: shipped
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0+
ledger_topic: android
tags: [architecture, udf, compose-performance, a11y, ux, dependency, build]
---

# 프론트엔드 대규모 개선 종합 설계 (Rev.2)

- **작성일**: 2026-06-05
- **상태**: shipped
- **연관 작업**: 2026-06-04 프론트엔드 7개 분석 세션 (logs/android.md) + 2026-06-05 팩트체크 + 공식 문서 검증 + 2026-06-05 감사 보정
- **대상 버전**: v0.2.0 ~ v0.3.x (Phase별 점진 적용)
- **선행 작업**: 없음 (본 문서 자체가 master plan)

---

## 1. 배경

2026-06-04에 7개 분석 세션, 2026-06-05에 2단계 검증 (코드 팩트체크 + 공식 문서 조회) 수행.

| # | 세션 | 핵심 발견 |
|---|------|----------|
| S1 | Clean Architecture + MVI + Multi-module 설계 검토 | MVI 전환 Gap 6건 (G1~G6) |
| S2 | Compose 퍼포먼스 공식 문서 기반 성능 점검 | @Immutable 7종 누락 (FC-5 보정), runBlocking 워크어라운드 |
| S3 | 빌드 환경 및 의존성 현대화 검토 | 레거시 플래그 5건 (B1~B5), 제거 대상 3건 (B1+B2+B4), B3 heap/B5 resource shrinking 유지 |
| S4 | 의존성 LTS/Stable 마이그레이션 검토 | Kotlin/Coil/OkHttp 3건 마이그레이션 대상 |
| S5 | 프론트엔드 전수 분석 (UI/디자인/a11y/내비게이션/성능) | a11y heading() 0%, @Preview 0%, TopAppBar 과밀 |
| S6 | UDF 디자인 패턴 설계 검토 | Critical 위반 0건, Minor 4건 |
| S7 | HomeScreen 레이아웃 UX/UI 디자인 점검 | 8건 개선 사항 |

---

## 2. 팩트체크 + 공식 문서 검증 결과

### 2.1 코드베이스 팩트체크 (11건 — 룰 9 + 룰 10)

| # | 주장 | 결과 | 라벨 |
|---|------|------|------|
| FC-1 | AuthViewModel StateFlow 8개 | **불일치 — 실측 7개** (sessionState/signupState/authOpState/pendingEmail/passwordResetSent/resendCooldownSec/resendError) | MEASURED |
| FC-2 | ProfileViewModel StateFlow 5개 | **확인** | MEASURED |
| FC-3 | collectAsState() 30건 | **불일치 — 실측 31건** (empty-parens 30 + with-arg 1) | MEASURED |
| FC-4 | ViewModel 직접 메서드 호출 40+건 | **불일치 — 실측 ~26건** | MEASURED |
| FC-5 | @Immutable 누락 6종 | **확인 + UserProfile 1종 추가 → 7종** | MEASURED |
| FC-6 | UseCase 3개 | **확인** | MEASURED |
| FC-7 | Repository interface 6개 | **확인** | MEASURED |
| FC-8 | LazyColumn 안정 key 전수 사용 | **확인** | MEASURED |
| FC-9 | @Preview 함수 0개 | **확인** | MEASURED |
| FC-10 | HomeScreen 이중 패딩 | **재검증 필요** — 실기기 시각 확인 후 판단 | DEFERRED |
| FC-11 | TopAppBar 아이콘 8개 | **확인** | MEASURED |

### 2.2 공식 문서 검증 (7건 조회) — **설계 방향 변경 사항 포함**

#### 2.2.1 아키텍처 패턴 — **CRITICAL: MVI Intent/dispatch 패턴 재검토**

| 주장 | 공식 문서 | 판정 | 출처 |
|------|----------|------|------|
| "MVI 단일 UiState 권장" | UDF(Unidirectional Data Flow)로 권장. **"MVI"라는 용어는 developer.android.com 어디에도 없음.** 무관한 데이터는 별도 stream 허용 | PARTIALLY CONFIRMED | [UI Layer](https://developer.android.com/topic/architecture/ui-layer) |
| `collectAsStateWithLifecycle` | **"Strongly Recommended"** (Google 최고 등급). 원문: "Use this API as the recommended way to collect flows on Android apps." | **CONFIRMED** | [State in Compose](https://developer.android.com/develop/ui/compose/state) |
| **sealed interface Intent + dispatch() 패턴** | **공식 문서에 없음.** 공식 패턴은 **ViewModel 직접 메서드 호출** (e.g., `viewModel.refreshNews()`). "MVI", "dispatch", "sealed interface Intent" 검색 결과 0건 | **NOT FOUND** — 커뮤니티 관행 | [UI Events](https://developer.android.com/topic/architecture/ui-layer/events) |

> **공식 가이드의 이벤트 처리 패턴**: Button(onClick = { viewModel.refreshNews() }) — 직접 메서드 호출.
> "Do not send events from the ViewModel to the UI. Process the event immediately in the ViewModel and cause a state update." — **Strongly Recommended**

**설계 영향**: S1 분석이 전제한 "MVI 표준" (sealed Intent + dispatch + BaseViewModel)은 커뮤니티 패턴이지 공식 권장이 아님. **D1(전 Screen MVI 전환), D2(BaseViewModel 도입) 근거 소멸.**

#### 2.2.2 Compose Performance / Stability

| 주장 | 공식 문서 | 판정 | 출처 |
|------|----------|------|------|
| @Immutable → equals() 비교 | @Immutable → stable 마킹 → **Strong Skipping Mode에서** stable 파라미터에 equals() 적용, unstable은 === | PARTIALLY CONFIRMED | [Strong Skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) |
| List<T> 항상 unstable | "Compose always considers collection classes unstable" | **CONFIRMED** | [Stability](https://developer.android.com/develop/ui/compose/performance/stability) |
| Strong Skipping 기본 활성 | **Kotlin 2.0.20**부터 (원래 주장 "2.0+" 는 부정확) | 버전 보정 | [Strong Skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) |
| remember로 sort/filter 감싸기 | 공식 anti-pattern 예시 + 권장 fix 명시 | **CONFIRMED** | [Best Practices](https://developer.android.com/develop/ui/compose/performance/bestpractices) |
| runBlocking in composition 안티패턴 | `runBlocking` 명시 언급 없으나 "composables must be fast" + "side-effect free" + "might run every frame" 원칙으로 강하게 암시 | NOT EXPLICIT | [Mental Model](https://developer.android.com/develop/ui/compose/mental-model) |

#### 2.2.3 Material Design 3 TopAppBar — **수치 보정**

| 주장 | M3 Expressive spec | 판정 | 출처 |
|------|-------------------|------|------|
| Actions 상한 2~3개 | **모바일: 최대 2개** icon button. 대형 화면: 최대 4개. filled/tonal 사용 시 1개만 | **원래 주장보다 더 엄격** | [M3 App Bars Guidelines](https://m3.material.io/components/app-bars/guidelines) |
| scrollBehavior 필수 | 선택사항. "can remain at all times, or can hide and reappear" | NOT MANDATORY | 동일 |

#### 2.2.4 OkHttp 5 + Coil 3 — **2건 오류 발견**

| 주장 | 실제 | 판정 | 출처 |
|------|------|------|------|
| OkHttp 5 바이너리 호환 | Jake Wharton: "binary and behavioral compatible with 4.x (and 3.x)" | **CONFIRMED** | [Retrofit Discussion #4684](https://github.com/square/retrofit/discussions/4684) |
| Retrofit 3.0.0 + OkHttp 5 | 작동 (drop-in override). Retrofit 3.0.0은 OkHttp 4.12 번들, 5.x로 override 가능 | **CONFIRMED** | 동일 |
| Coil 3 network-okhttp 필수 | "coil-core no longer supports loading images from the network by default" | **CONFIRMED** | [Coil 3 Upgrade Guide](https://coil-kt.github.io/coil/upgrading_to_coil3/) |
| **Coil 3 = OkHttp 5 기반** | **FALSE** — Coil 3의 `libs.versions.toml`: `okhttp = "4.12.0"` | **오류** | [Coil source](https://github.com/coil-kt/coil/blob/main/gradle/libs.versions.toml) |
| **OkHttp 5 최소 Java 11** | **FALSE** — OkHttp README: "Java 8+" | **오류** | [OkHttp README](https://github.com/square/okhttp) |

**설계 영향**: D8 "OkHttp 선행 → Coil 후행" 근거 소멸. Coil 3이 OkHttp 4.12 기반이므로 **순서 무관**, 독립 마이그레이션 가능.

#### 2.2.5 Hilt + Kotlin 호환

| 주장 | 실제 | 판정 | 출처 |
|------|------|------|------|
| Hilt 2.59.2 Kotlin 2.3 미지원 | 확인. PR #5062 fix 머지됐으나 **릴리스 미포함** | **CONFIRMED** | [Dagger #5001](https://github.com/google/dagger/issues/5001) |
| kotlin-metadata-jvm override 워크어라운드 | Dagger 메인테이너 + 다수 사용자 확인 | **CONFIRMED** | 동일 |
| Hilt 2.60 출시 여부 | **미출시** — 최신 2.59.2 (2026-02-20) | **CONFIRMED** | [Dagger Releases](https://github.com/google/dagger/releases) |
| KSP 최신 = 2.3.7 | **보정: 2.3.9** (2026-05-26) | 보정 | [KSP Releases](https://github.com/google/ksp/releases) |
| KSP1 Kotlin 2.3부터 미지원 | 확인. KSP2만 사용 가능 | **CONFIRMED** | [KSP2 Migration](https://github.com/google/ksp/blob/main/docs/ksp2.md) |

#### 2.2.6 접근성(a11y) — **testTag 재평가**

| 주장 | 공식 문서 | 판정 | 출처 |
|------|----------|------|------|
| heading() 섹션 제목에 적용 | 확인. content-rich 화면의 subsection용. 전 screen 제목 필수는 아님 | **CONFIRMED** | [Semantics](https://developer.android.com/develop/ui/compose/semantics) |
| liveRegion 동적 콘텐츠 | 에러 메시지 OK. **빈번 업데이트(로딩 스피너)는 부적절** — "shouldn't be used on content that updates frequently" | PARTIALLY CONFIRMED | 동일 |
| 터치 타겟 48dp | "set the minimum size to 48dp" 명시 | **CONFIRMED** | [Accessibility API Defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) |
| **testTag 필수** | **"last resort"로 명시.** 공식 우선순위: semantic matcher → node matcher → testTag | **오류** — testTag는 최후 수단 | [Compose Testing](https://developer.android.com/codelabs/jetpack-compose-testing) |

#### 2.2.7 Kotlin 2.4.0

| 주장 | 실제 | 판정 |
|------|------|------|
| context parameters stable | Stable (context arguments/callable references는 Experimental) | **CONFIRMED** |
| explicit backing fields | Stable | **CONFIRMED** |
| UUID API | Stable (V4/V7 생성은 Experimental) | **CONFIRMED** |
| Java 26 지원 | "compiler can generate classes containing Java 26 bytecode" | **CONFIRMED** |
| 최신 Kotlin = 2.4.0 | 2026-06-03 출시 | **CONFIRMED** |

---

## 3. 아키텍처 전략 재설계 — UDF-Enhanced vs Full MVI

공식 문서 검증 결과, S1 분석의 전제였던 "MVI가 표준"은 **커뮤니티 관행**이지 Google 공식 권장이 아닌 것으로 확인되었다. 두 접근법을 비교한다.

### Option A: UDF-Enhanced (공식 Google 패턴 준수)

**핵심**: 단일 UiState + SideEffect Channel + **ViewModel 직접 메서드 호출 유지**

```kotlin
// ViewModel — 공식 패턴 유지
class StatisticsViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<StatisticsSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    fun load() { ... }            // 직접 메서드 호출 (공식 패턴)
    fun dismissError() { ... }
}

// Screen
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
Button(onClick = { viewModel.load() })  // 공식 이벤트 패턴
```

| 장점 | 단점 |
|------|------|
| **공식 문서 100% 일치** | sealed Intent 없어 이벤트 추적이 분산적 |
| BaseViewModel 불필요 — boilerplate 적음 | 메서드 수가 많아지면 ViewModel interface 비대 |
| 기존 코드 변경 최소 (메서드 호출 그대로) | — |
| 신규 입사자 학습 비용 최소 | — |

**변경 범위**: 다중 StateFlow → 단일 UiState 통합 + SideEffect Channel 추가. 메서드 호출 방식 유지.

### Option B: Full MVI (커뮤니티 패턴)

**핵심**: 단일 UiState + SideEffect Channel + **sealed interface Intent + dispatch()**

```kotlin
// ViewModel — 커뮤니티 MVI 패턴
class StatisticsViewModel @Inject constructor(...) : BaseViewModel<...>(...) {
    override suspend fun handleIntent(intent: StatisticsIntent) = when (intent) {
        StatisticsIntent.Load -> load()
        StatisticsIntent.DismissError -> updateState { copy(error = null) }
    }
}

// Screen
viewModel.dispatch(StatisticsIntent.Load)
```

| 장점 | 단점 |
|------|------|
| 모든 이벤트가 sealed interface에 명시 — 추적 용이 | **공식 문서에 없는 패턴** |
| handleIntent() 단일 진입점 — 로깅/디버깅 편의 | BaseViewModel 추상 클래스 도입 필요 |
| Orbit MVI/MVIKotlin 등 라이브러리 생태계 | 기존 ~26개 메서드 호출 전면 교체 |
| — | 학습 비용 (Intent sealed class 작성) |

### 결론 및 권장

| 기준 | Option A (UDF-Enhanced) | Option B (Full MVI) |
|------|------------------------|---------------------|
| 공식 문서 일치도 | 100% | 0% (커뮤니티) |
| 기존 코드 변경량 | **적음** (StateFlow 통합 + Channel 추가) | **많음** (+ Intent 전환 26건) |
| 단일 UiState 달성 | ✅ | ✅ |
| SideEffect Channel | ✅ | ✅ |
| collectAsStateWithLifecycle | ✅ | ✅ |

**두 옵션 모두 핵심 개선 (단일 UiState + SideEffect Channel + lifecycle-aware collect)은 동일하게 달성.** 차이는 이벤트 전달 방식(직접 호출 vs dispatch)뿐.

본 설계는 **Option A (UDF-Enhanced)를 기본으로 채택하되, 사용자가 Option B를 선호할 경우 전환 가능**하도록 구성한다.

---

## 4. Scope

### In-scope

| WS | 작업 영역 | 핵심 변경 | Phase |
|----|----------|----------|-------|
| **WS-1** | UDF 상태 통합 | 다중 StateFlow → 단일 UiState + SideEffect Channel | 2~5 |
| **WS-2** | Compose 성능 최적화 | @Immutable 7종 + collectAsStateWithLifecycle 31건 | 1 |
| **WS-3** | UX/UI 개선 | TopAppBar 과밀 해소 (M3: 모바일 최대 2개) + HomeScreen 레이아웃 | 1~4 |
| **WS-4** | 접근성(a11y) 강화 | heading() + liveRegion(에러 메시지 한정, 로딩 스피너 제외) | 2 |
| **WS-5** | 빌드 환경 현대화 | 레거시 플래그 정리 + dependabot 정리 + Detekt baseline 통합 | 1 |
| **WS-6** | 의존성 마이그레이션 | OkHttp 5 + Coil 3 (순서 무관) + Kotlin 2.3/2.4 (조건부) | 3~4 |

### Out-of-scope

| 항목 | 이유 |
|------|------|
| Multi-module 전환 | 빌드 시간 병목 없음. 팀 규모 확대 시 재검토 |
| UseCase 전면 확장 | 단순 CRUD는 over-engineering |
| **BaseViewModel 추상 클래스** | **Option A 채택 시 불필요** — 각 ViewModel이 직접 StateFlow + Channel 관리 |
| **sealed interface Intent** | **Option A 채택 시 불필요** — 공식 패턴은 직접 메서드 호출 |
| **testTag 전면 도입** | **공식 문서 "last resort"** — semantic matcher 우선 사용. 필요 시 점진 도입 |
| kotlinx-collections-immutable | `@Immutable`이 Compose 컴파일러에게 stable 계약을 보장 → `List<T>` unstable 기본 동작을 override. ViewModel이 `copy()`로 functional update만 사용하므로 런타임 List 불변성 유지. List 원소 타입(Goal, WeeklyRate, ProfileHistoryPoint 등)도 모두 `@Immutable` 적용 → **stability chain 완전** |
| Custom Baseline Profile Generator | 사용자 수 < 100 |

---

## 5. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|------|--------|------|
| **D1** | 아키텍처 패턴 | **UDF-Enhanced** (Option A) — 직접 메서드 호출 유지 + 단일 UiState + SideEffect Channel | 공식 Google 문서 "Strongly Recommended" 패턴. MVI Intent/dispatch는 공식 문서에 없음 |
| ~~D2~~ | ~~BaseViewModel 도입~~ | **철회** | D1에서 Option A 채택으로 불필요 |
| **D3** | Auth ViewModel 분리 | 3개 분리 (LoginVM/SignupVM/ForgotPasswordVM) | AuthVM 7 StateFlow → 각 2~3으로 단순화. 이것은 MVI와 무관한 관심사 분리 |
| **D4** | TopAppBar 과밀 해소 | OverflowMenu — actions = **새로고침 1개** + MoreVert | M3 Expressive: 모바일 **최대 2개** icon button (원래 주장 2~3개보다 엄격) |
| **D5** | collectAsStateWithLifecycle 전환 | Phase 1에서 일괄 (31건 기계적 치환) | Google **"Strongly Recommended"** 최고 등급 |
| **D6** | @Immutable 적용 | 7종 추가 | Strong Skipping Mode(Kotlin 2.0.20+ 기본) 하에서 stable→equals() 비교 활성화 |
| **D7** | HomeScreen 이중 패딩 | 실기기 시각 검증 후 결정 (DEFERRED) | 팩트체크에서 M3 정상 패턴 가능성 |
| **D8** | OkHttp/Coil 마이그레이션 순서 | **순서 무관** (독립 마이그레이션) | ~~"Coil 3 = OkHttp 5 기반"~~ 오류. Coil 3은 OkHttp **4.12.0** 사용 |
| **D9** | @Preview 도입 범위 | shared 컴포넌트 6개 우선 | 전 Screen @Preview는 작업량 대비 실익 낮음 |
| **D10** | Detekt baseline 이원화 해소 | 단일 baseline 파일로 통합 | CI 연속 실패 원인 |
| **D11** | testTag 도입 | **축소** — semantic matcher 우선, testTag는 불가피한 곳만 | 공식 문서 "last resort" 명시 |
| **D12** | liveRegion 범위 | 에러 메시지/알림에만 적용. **로딩 스피너 제외** | 공식: "shouldn't be used on content that updates frequently" |
| **D13** | Kotlin 업그레이드 경로 | 2가지 경로 열어둠: (a) Hilt 2.60+ 대기 (b) kotlin-metadata-jvm override 워크어라운드 | Dagger 메인테이너 확인 워크어라운드 + PR #5062 머지(미릴리스) |
| **D14** | KSP 버전 | 최신 **2.3.9** (원래 주장 2.3.7 보정) | [KSP Releases](https://github.com/google/ksp/releases) |

---

## 6. 통합 개선 로드맵

### Phase 1 — Compose 성능 기반 + 빌드 정리 `v0.2.0-alpha`

| # | 작업 | WS | 위험도 |
|---|------|----|--------|
| 1.1 | `collectAsState()` → `collectAsStateWithLifecycle()` 31건 일괄 전환 (의존성 이미 존재) | WS-2 | 낮음 |
| 1.2 | `@Immutable` 7종 추가 (WeeklyPlan, DayPlan, Exercise, UserProfile, ProfileUiState, SaveState, DeleteState) | WS-2 | 낮음 |
| 1.3 | TopAppBar OverflowMenu 전환 (8→1+Menu, M3 모바일 최대 2개 준수) | WS-3 | 낮음 |
| 1.4 | gradle.properties 레거시 플래그 정리 (B1+B2 검증 후 제거, B4 제거) | WS-5 | 낮음 |
| 1.5 | dependabot.yml Backend Ktor entry 삭제 | WS-5 | 없음 |
| 1.6 | Detekt baseline 이원화 해소 | WS-5 | 낮음 |
| 1.7 | HomeScreen 패딩 실기기 검증 (DEFERRED) | WS-3 | — |

### Phase 분류 기준

> Phase 2~4 분류는 **StateFlow 수가 아닌 변환 복잡도** 기준:
> - **Phase 2 (Simple)**: load-once 패턴, SideEffect 불필요, error 통합만
> - **Phase 3 (Medium)**: 페이지네이션(History) / SideEffect 필요(Goal=Snackbar, Onboarding=Navigate) / 다중 상태 통합(Onboarding 3개)
> - **Phase 4 (Complex)**: StateFlow 3~5개 + SSOT 위반 해소(Home) / SideEffect 다중(Profile: Navigate+Snackbar)

### Phase 2 — Simple ViewModel UiState 통합 + a11y `v0.2.0-beta`

| # | 작업 | 대상 | 현재 StateFlow | 목표 |
|---|------|------|---------------|------|
| 2.1 | StatisticsVM — error 통합 | StatisticsScreen + VM | 2 | 기존 sealed class에 `Error(AppError)` variant 추가 |
| 2.2 | WorkoutDetailVM — error 통합 | WorkoutDetailScreen + VM | 2 | **신규 UiState data class 생성** (현재 `_exercise: Exercise?` 구조) |
| 2.3 | BadgeVM — error 통합 | BadgeScreen + VM | 2 | **신규 BadgeUiState data class 생성** (현재 `_badges: List<BadgeDisplayItem>` 구조) |
| 2.4 | heading() 시맨틱 도입 (content-rich 화면 위주) | ~8개소 | — | — |
| 2.5 | liveRegion 확대 (**에러 메시지/알림만**, 로딩 스피너 제외) | ErrorContent, EmptyContent | — | — |
| 2.6 | @Preview 함수 도입 (shared 컴포넌트 6개) | ui/components/ | — | — |

**UiState 통합 패턴 (Phase 2~5 공통)** — ViewModel 구조에 따라 2가지 패턴:

#### 패턴 A: sealed class ViewModel (Statistics, Home, Profile)

기존 sealed class에 `Error` variant 추가. Screen의 `when(uiState)` exhaustive 분기에 Error case 동시 추가 필요.

```kotlin
// Before — sealed class + 별도 _error
sealed class StatisticsUiState {
    data object Loading : StatisticsUiState()
    data class Loaded(val data: Statistics) : StatisticsUiState()
    data object Empty : StatisticsUiState()
}
val uiState: StateFlow<StatisticsUiState>  // 1
val error: StateFlow<AppError?>            // 2 (별도)

// After — Error variant 추가 + _error 제거
sealed class StatisticsUiState {
    data object Loading : StatisticsUiState()
    data class Loaded(val data: Statistics) : StatisticsUiState()
    data object Empty : StatisticsUiState()
    data class Error(val error: AppError) : StatisticsUiState()  // ← 추가
}
val uiState: StateFlow<StatisticsUiState>         // 단일
val sideEffect = channel.receiveAsFlow()          // 1회성 이벤트

// Screen — exhaustive check
when (val state = uiState) {
    is StatisticsUiState.Loading -> LoadingContent()
    is StatisticsUiState.Loaded -> StatisticsContent(state.data)
    is StatisticsUiState.Empty -> EmptyContent()
    is StatisticsUiState.Error -> ErrorContent(state.error)  // ← 추가
}
```

#### 패턴 B: flat data class ViewModel (Goal, History, Badge, WorkoutDetail)

기존 data class에 `error` 필드 추가 또는 신규 UiState data class 생성.

```kotlin
// Before — data class + 별도 _error
val uiState: StateFlow<GoalUiState>  // 1
val error: StateFlow<AppError?>      // 2 (별도)

// After — error 필드 통합
@Immutable
data class GoalUiState(
    val goals: List<Goal> = emptyList(),
    val history: List<ProfileHistoryPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,       // 기존 필드 보존
    val error: AppError? = null,         // ← 통합
)
val uiState: StateFlow<GoalUiState>               // 단일
val sideEffect = channel.receiveAsFlow()           // 1회성 이벤트
fun dismissError() { _uiState.update { it.copy(error = null) } }

// Screen — 공식 패턴
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
LaunchedEffect(Unit) { viewModel.sideEffect.collect { effect -> ... } }
Button(onClick = { viewModel.saveGoal(...) })   // 직접 호출 유지
```

### Phase 3 — Medium ViewModel UiState 통합 `v0.2.0`

| # | 작업 | 현재 StateFlow | 목표 |
|---|------|---------------|------|
| 3.1 | HistoryVM — error 통합 | 2 | 단일 UiState |
| 3.2 | GoalVM — error 통합 + SideEffect(Snackbar) | 2 | 단일 UiState + SideEffect |
| 3.3 | OnboardingVM — saved/isLoading/error 통합 | 3 | 단일 UiState + SideEffect |
| 3.4 | OkHttp 4.12.0 → **5.3.2** (최신 stable, 바이너리 호환, Java 8+) | — | libs.versions.toml 변경만 |
| 3.5 | Coil 2.7.0 → 3.4.0 (D8: 순서 무관, OkHttp와 독립) | — | 좌표 변경 + network-okhttp 추가 |

### Phase 4 — Complex ViewModel UiState 통합 `v0.2.x`

| # | 작업 | 현재 StateFlow | 특이사항 |
|---|------|---------------|---------|
| 4.1 | HomeVM — 3 StateFlow → 단일 + `_error`/`Empty` SSOT 해소 | 3 | error를 UiState에 통합. **themeMode는 별도 StateFlow 유지 검토** — DataStore hot flow로 콘텐츠 상태와 독립 (R6) |
| 4.2 | ProfileVM — 5 StateFlow → 단일 + SideEffect(Snackbar, Navigate) | 5 | delete/save를 SideEffect로 분리 |
| 4.3 | HomeScreen DayPlanCard 중첩 클릭 분리 | — | 체크 아이콘 영역만 clickable |
| 4.4 | 휴식일 카드 false affordance 제거 | — | `enabled = !day.isRestDay` |

### Phase 5 — Auth 대규모 리팩터 `v0.2.x`

| # | 작업 | 특이사항 |
|---|------|---------|
| 5.1 | AuthViewModel 3분할 — LoginVM / SignupVM / ForgotPasswordVM | 7 StateFlow → 각 2~3 |
| 5.2 | 각 VM 단일 UiState + SideEffect 패턴 적용 | 직접 메서드 호출 유지 |
| 5.3 | AuthErrorBanner 호환성 유지 | 3개 VM 공통 |
| 5.4 | AuthViewModelTest 21개 → 3개 VM 테스트로 재구성 | — |

### Phase 6 — 선택적 개선 (조건부) `v0.3.x`

| # | 작업 | 트리거 |
|---|------|--------|
| 6.1 | CI release 빌드 검증 | 서명 키 CI 등록 시 |
| 6.2 | ~~testTag 전면 도입~~ → semantic matcher 우선 + 점진 testTag | UI 테스트 도입 시 |
| 6.3 | form focus chain | 사용자 피드백 |
| 6.4 | Chart runBlocking 해소 | Vico 업그레이드 시 |
| 6.5 | remember로 chart map 감싸기 | 데이터 규모 증가 시 |
| 6.6 | Kotlin 2.3/2.4 + KSP **2.3.9** | Hilt 2.60+ 출시 또는 metadata-jvm 워크어라운드 검증 후 |
| 6.7 | BottomNavigationBar | 화면 수 증가 시 |

---

## 7. 구성 요소별 변경 상세

### 7.1 MODIFY: TopAppBar (Phase 1.3) — M3 Expressive 준수

**M3 Expressive 공식 spec**: "Up to **two** icon buttons can be placed after the headline"

**Before**: 8 IconButton (542dp 필요, 360dp 화면에서 이탈)
**After**: 1 IconButton(새로고침) + OverflowMenu = **M3 모바일 2개 상한 준수**

```
TopAppBar: [새로고침][⋮]
  DropdownMenu: 프로필 / 테마 / 기록 / 통계 / 목표 / 배지 / 로그아웃
```

### 7.2 MODIFY: @Immutable 7종 (Phase 1.2)

| 파일 | 대상 |
|------|------|
| `domain/model/WeeklyPlan.kt` | `WeeklyPlan`, `DayPlan` |
| `domain/model/Exercise.kt` | `Exercise` |
| `domain/model/UserProfile.kt` | `UserProfile` |
| `ui/profile/ProfileViewModel.kt` | `ProfileUiState`, `SaveState`, `DeleteState` |

Strong Skipping Mode (Kotlin 2.0.20+ 기본 활성, 현재 프로젝트 Kotlin 2.2.10 ✅): stable 파라미터 → `equals()` 비교 → 불필요 recomposition skip.

**Stability chain 검증**: `@Immutable` UiState → `List<T>` → T 타입도 `@Immutable` 필요. 현재 상태:
- `WeeklyPlan.days: List<DayPlan>` → DayPlan은 Phase 1에서 `@Immutable` 추가 예정 ✅
- `DayPlan.exercises: List<Exercise>` → Exercise도 Phase 1에서 추가 ✅
- `Exercise.instructions: List<String>` → String은 primitive stable ✅
- `GoalUiState.goals: List<Goal>` → Goal **이미 `@Immutable`** ✅
- `GoalUiState.history: List<ProfileHistoryPoint>` → ProfileHistoryPoint **이미 `@Immutable`** ✅
- `Statistics.weeklyRates: List<WeeklyRate>` → WeeklyRate **이미 `@Immutable`** ✅
- `HistoryUiState.plans: List<WeeklyPlan>` → WeeklyPlan Phase 1 추가 ✅

### 7.3 MODIFY: collectAsStateWithLifecycle 31건 (Phase 1.1)

의존성: `lifecycle-runtime-compose` **이미 존재** (libs.versions.toml + build.gradle.kts). 추가 작업 불필요 — 즉시 전환 가능.

```toml
# libs.versions.toml (기존 — 변경 없음)
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
```

### 7.4 EXAMPLE: UDF-Enhanced ViewModel 전환 (Phase 2~5 공통)

```kotlin
// === Before (현재) ===
class GoalViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()
    private val _error = MutableStateFlow<AppError?>(null)  // 별도 stream
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun saveGoal(...) { ... }
    fun clearError() { _error.value = null }
}
// Screen
val error by viewModel.error.collectAsState()   // 별도 collect
LaunchedEffect(error) { ... snackbar ... viewModel.clearError() }

// === After (UDF-Enhanced) ===
class GoalViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<GoalSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    fun saveGoal(...) { ... }
    fun dismissError() { _uiState.update { it.copy(error = null) } }
}

@Immutable
data class GoalUiState(
    val goals: List<Goal> = emptyList(),
    val history: List<ProfileHistoryPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,     // ← 기존 필드 보존
    val error: AppError? = null,       // ← 통합
)

sealed interface GoalSideEffect {
    data class ShowSnackbar(val message: String) : GoalSideEffect
}

// Screen — 직접 메서드 호출 (공식 패턴)
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
LaunchedEffect(Unit) { viewModel.sideEffect.collect { effect -> ... } }
Button(onClick = { viewModel.saveGoal(...) })   // 직접 호출 유지
```

---

## 8. 검증 계획

### 8.1 측정값 (룰 9)

| 항목 | 값 | 라벨 | 측정 명령/출처 |
|------|---|------|--------------|
| collectAsState 전환 대상 | 31건 (empty-parens 30 + with-arg 1) | MEASURED | `grep -r "collectAsState" app/src/ --include="*.kt" \| grep -v import` |
| ViewModel 직접 메서드 호출 | ~26건 | MEASURED | Screen .kt 수동 집계 |
| @Immutable 누락 | 7종 | MEASURED | domain/model/ + ProfileViewModel 대조 |
| M3 모바일 TopAppBar actions 상한 | **2개** | MEASURED | [M3 App Bars Guidelines](https://m3.material.io/components/app-bars/guidelines) |
| OkHttp 5 최소 Java | **8+** (원래 주장 11 오류) | MEASURED | [OkHttp README](https://github.com/square/okhttp) |
| Coil 3 OkHttp 버전 | **4.12.0** (원래 주장 5 오류) | MEASURED | [Coil libs.versions.toml](https://github.com/coil-kt/coil) |
| KSP 최신 | **2.3.9** (원래 주장 2.3.7 보정) | MEASURED | [KSP Releases](https://github.com/google/ksp/releases) |
| Kotlin 최신 | **2.4.0** (2026-06-03) | MEASURED | [kotlinlang.org](https://kotlinlang.org/docs/releases.html) |
| Hilt 최신 | **2.59.2** (2.60 미출시) | MEASURED | [Dagger Releases](https://github.com/google/dagger/releases) |

### 8.2 Phase별 검증

| Phase | 검증 항목 | 방법 |
|-------|----------|------|
| 1 | collectAsStateWithLifecycle 전환 완전성 | `grep -r "collectAsState" app/src/ --include="*.kt" \| grep -v import \| grep -v "WithLifecycle"` = 0건 |
| 1 | @Immutable 7종 적용 | 파일 대조 |
| 1 | TopAppBar M3 준수 | 실기기 360dp 화면 검증 |
| 2~5 | 단일 UiState 통합 | ViewModel당 public StateFlow 1개 + sideEffect 1개 |
| 2~5 | 테스트 전 PASS | `./gradlew :app:testDebugUnitTest` |
| 3 | OkHttp 5 호환 | API 호출 smoke test |
| 3 | Coil 3 호환 | GIF 로딩 검증 |

---

## 9. 롤백 절차

각 Phase 독립 PR. 회귀 시 `git revert --no-commit <merge-sha>` → Phase N-1 상태 확인.

---

## 10. 잔여 리스크

| # | 리스크 | 확률 | 영향 | 완화 |
|---|--------|------|------|------|
| R1 | SideEffect Channel.BUFFERED 용량 부족 | 극히 낮음 | 이벤트 누락 | UNLIMITED 또는 SharedFlow 대안 |
| R2 | Auth 3분할 시 공유 상태 동기화 | 중간 | 상태 불일치 | NavGraph scoped shared ViewModel |
| R3 | Kotlin 2.3 Hilt 블로커 장기화 | 중간 | 의존성 노후화 | metadata-jvm override 워크어라운드 (Dagger 메인테이너 확인) |
| R4 | Coil 3 Maven 좌표 변경 누락 | 낮음 | 빌드 실패 | 체크리스트 (upgrade guide 참조) |
| R5 | Coil 3 GIF decoder API breaking change | 중간 | GIF 로딩 실패 (WorkoutDetailScreen) | CoilModule DI에서 ImageLoader 팩토리 + decoder 재등록. 실기기 GIF smoke test 필수 |
| R6 | HomeVM themeMode combine 시 테마 flicker | 낮음 | 화면 전환 시 순간 깜빡임 | themeMode를 UiState에 통합하지 않고 별도 StateFlow 유지 검토 (DataStore hot flow 독립성) |
| R7 | StatisticsVM Error variant 추가 시 Screen exhaustive check 누락 | 낮음 | 컴파일 에러 (when 미처리) | sealed class variant 추가 시 Screen의 `when(uiState)` 분기에 Error case 동시 추가 |

---

## 11. 참고 자료

### 공식 문서 (2026-06-05 직접 조회 확인)
- [UI Layer](https://developer.android.com/topic/architecture/ui-layer) — 단일 UiState 권장 (UDF, "Recommended")
- [UI Events](https://developer.android.com/topic/architecture/ui-layer/events) — 직접 메서드 호출 ("Strongly Recommended")
- [State in Compose](https://developer.android.com/develop/ui/compose/state) — collectAsStateWithLifecycle ("Strongly Recommended")
- [Architecture Recommendations](https://developer.android.com/topic/architecture/recommendations) — 전체 등급표
- [Compose Stability](https://developer.android.com/develop/ui/compose/performance/stability) — @Immutable, List unstable
- [Strong Skipping Mode](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) — Kotlin 2.0.20+ 기본
- [Performance Best Practices](https://developer.android.com/develop/ui/compose/performance/bestpractices) — remember sort/filter
- [M3 App Bars Guidelines](https://m3.material.io/components/app-bars/guidelines) — 모바일 **최대 2** icon buttons
- [Compose Semantics](https://developer.android.com/develop/ui/compose/semantics) — heading(), liveRegion
- [Compose Accessibility](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) — 48dp touch target
- [Compose Testing](https://developer.android.com/codelabs/jetpack-compose-testing) — testTag = **last resort**
- [Coil 3 Upgrade Guide](https://coil-kt.github.io/coil/upgrading_to_coil3/) — network-okhttp 필수
- [OkHttp README](https://github.com/square/okhttp) — Java 8+, binary compat
- [Dagger #5001](https://github.com/google/dagger/issues/5001) — Kotlin 2.3 metadata 블로커
- [KSP2 Migration](https://github.com/google/ksp/blob/main/docs/ksp2.md) — KSP1 Kotlin 2.3 미지원
- [Kotlin 2.4.0](https://kotlinlang.org/docs/whatsnew24.html) — 2026-06-03 출시

### 분석 세션 원본
- `docs/plans/logs/android.md` §2026-06-04 — 7개 분석 세션 전체 기록

---

## 12. 부록: 세션별 발견 → 설계 항목 추적 + 공식 문서 검증 상태

| 세션 | 발견 | 설계 매핑 | 공식 문서 검증 | Phase |
|------|------|----------|--------------|-------|
| S1/G1 | ~~Intent sealed class 부재~~ | **철회** — 공식 패턴은 직접 메서드 호출 | NOT FOUND in official docs | — |
| S1/G2 | 다중 StateFlow | WS-1 Phase 2~5 (단일 UiState 통합) | "Recommended" (UDF) | 2~5 |
| S1/G3 | SideEffect Channel 부재 | WS-1 Phase 2~5 | "Strongly Recommended" (events→state) | 2~5 |
| S1/G4 | collectAsState 전면 사용 | WS-2 Phase 1.1 (31건 전환) | "Strongly Recommended" | 1 |
| S1/G5 | UseCase 커버리지 부족 | Out-of-scope | — | — |
| S1/G6 | Multi-module 미적용 | Out-of-scope | — | — |
| S2/항목6 | @Immutable 누락 | WS-2 Phase 1.2 (7종) | CONFIRMED (Strong Skipping) | 1 |
| S2/항목11 | 이중 패딩 | D7 DEFERRED | — | 1 |
| S3/B1+B2 | 레거시 플래그 | WS-5 Phase 1.4 | — | 1 |
| S3/D1 | dependabot Ktor | WS-5 Phase 1.5 | — | 1 |
| S3/F1 | Detekt baseline 이원화 | WS-5 Phase 1.6 | — | 1 |
| S4/OkHttp | 4.12→5.x | WS-6 Phase 3.4 | CONFIRMED (binary compat, **Java 8+**) | 3 |
| S4/Coil | 2.7→3.4 | WS-6 Phase 3.5 (**순서 무관**) | CONFIRMED (OkHttp **4.12** 기반) | 3 |
| S4/Kotlin | 2.2→2.3/2.4 | Phase 6.6 | CONFIRMED (Hilt 블로커, KSP **2.3.9**) | 6 |
| S5/a11y heading | 0%→확대 | WS-4 Phase 2.4 | CONFIRMED | 2 |
| S5/a11y liveRegion | 5%→확대 | WS-4 Phase 2.5 (**에러만, 로딩 제외**) | PARTIALLY CONFIRMED | 2 |
| S5/@Preview | 0개 | WS-4 Phase 2.6 | — | 2 |
| S5/testTag | 0개 | ~~Phase 6.2~~ → **축소** (semantic 우선) | **"last resort"** | 6 |
| S7/TopAppBar | 8 아이콘 | WS-3 Phase 1.3 (M3: **최대 2개**) | 원래 주장보다 더 엄격 | 1 |
| S7/DayPlanCard | 중첩 클릭 | WS-3 Phase 4.3 | — | 4 |
| S7/휴식일 | false affordance | WS-3 Phase 4.4 | — | 4 |

---

## 13. 감사 이력

| 일시 | 감사 항목 | 보정 |
|------|----------|------|
| 2026-06-05 | FC-3 collectAsState 수 | 42 → **31** (empty-parens 30 + with-arg 1). 기존 grep `"collectAsState()"` 가 `collectAsState(initial=...)` 누락 |
| 2026-06-05 | FC-1 AuthViewModel StateFlow 수 | 8 → **7** (7개 재확인: sessionState/signupState/authOpState/pendingEmail/passwordResetSent/resendCooldownSec/resendError) |
| 2026-06-05 | 성공지표 현재값 | "~3/9 부분" → **0/9** (전 VM 별도 `_error` StateFlow 보유) |
| 2026-06-05 | Plan 설계 문서 경로 | `docs/plans/` → `docs/plans/_staging/` (staging 상태 반영) |
| 2026-06-05 | 본문 상태 표기 | "작성 중" → "proposed (리뷰 대기)" (frontmatter 일치) |
| 2026-06-05 | Phase 1.1 검증 명령 | `grep "collectAsState()"` → `grep "collectAsState" \| grep -v import \| grep -v WithLifecycle` |
| 2026-06-05 | Phase 2 WorkoutDetailVM | UiState data class 신규 생성 필요 명시 (기존 `_exercise: Exercise?` 구조) |
| 2026-06-06 | AuthViewModelTest 테스트 수 | 19 → **21** (`@Test` 메서드 전수 집계) |
| 2026-06-06 | lifecycle-runtime-compose | "의존성 추가" → **이미 존재** (libs.versions.toml:31 + build.gradle.kts:148) |
| 2026-06-06 | BadgeVM UiState | "기존 BadgeUiState에 error 통합" → **BadgeUiState 없음** — `_badges: List<BadgeDisplayItem>` + `_error` 구조. 신규 생성 필요 |
| 2026-06-06 | StatisticsVM UiState 전환 전략 | sealed class (Loading/Loaded/Empty)에 `Error(AppError)` variant 추가 방식 명시 |
| 2026-06-06 | §1 S2 @Immutable 수 | "6종" → **7종** (FC-5 보정값 배경 테이블 반영) |
| 2026-06-06 | OkHttp 목표 버전 | "5.x" → **5.3.2** (최신 stable 확인) |
| 2026-06-06 | Phase 2 테스트 현황 | Statistics/WorkoutDetail/Badge VM 테스트 **없음** 명시 (AuthViewModelTest만 존재) |
| 2026-06-06 | S3 레거시 플래그 | "5건" 중 제거 대상 B1+B2+B4 (3건), B3 heap/B5 resource shrinking 유지 명시 |
| 2026-06-06 | §6 Phase 2 공통 코드 예제 | flat data class 단일 → **패턴 A (sealed class) + 패턴 B (flat data class) 2가지로 분리**. StatisticsVM은 sealed class 방식인데 예제가 flat으로 표현 — 모순 해소 |
| 2026-06-06 | §7.4 GoalVM After 예제 | `isSaving` 필드 **누락** → 보존 추가. 현재 GoalUiState에 isSaving 있음 |
| 2026-06-06 | Phase 분류 근거 | 미명시 → **변환 복잡도 기준** (load-once vs 페이지네이션/SideEffect) 명시 |
| 2026-06-06 | 리스크 R5~R7 | 누락 → **R5** Coil 3 GIF decoder API, **R6** HomeVM themeMode combine flicker, **R7** StatisticsVM Error variant Screen exhaustive check |
| 2026-06-06 | HomeVM themeMode | "error + themeMode 통합" → themeMode **별도 유지 검토** (DataStore hot flow 독립성) |
| 2026-06-06 | Out-of-scope kotlinx-collections-immutable | "@Immutable으로 충분" → **stability chain 완전성 근거** 추가 (List 원소 타입 전수 @Immutable 확인) |
| 2026-06-06 | §7.2 @Immutable | 목록만 → **stability chain 검증** 테이블 추가 (7개 List<T> 필드의 원소 타입 stable 확인) |
