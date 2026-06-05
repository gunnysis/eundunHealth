---
type: plan
status: shipped
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0+
ledger_topic: android
tags: [architecture, udf, compose-performance, a11y, ux, dependency, build]
---

# 프론트엔드 대규모 개선 실행 계획 (Rev.2)

- **작성일**: 2026-06-05
- **상태**: shipped
- **설계 문서**: `docs/plans/2026-06-05-frontend-major-improvement-design.md` (Rev.2)
- **대상 버전**: v0.2.0 ~ v0.3.x

---

## 실행 원칙

1. **UDF-Enhanced 패턴** — 공식 Google 문서 준수. 직접 메서드 호출 유지 + 단일 UiState + SideEffect Channel.
2. **Phase별 독립 PR** — 각 Phase 1~3개 PR.
3. **팩트체크 + 공식 문서 보정값 사용** — FC-3: 31건, FC-4: ~26건, FC-5: 7종, M3 actions: 최대 2개, KSP: 2.3.9, OkHttp Java: 8+.

---

## Phase 1 — Compose 성능 기반 + 빌드 정리

### PR 1-A: Compose 성능 (collectAsStateWithLifecycle + @Immutable)

| Task | 작업 | 검증 |
|------|------|------|
| T1 | `lifecycle-runtime-compose` 의존성 확인 (**이미 존재** — libs.versions.toml + build.gradle.kts) | import 가능 확인 |
| T2 | `collectAsState()` → `collectAsStateWithLifecycle()` 31건 전환 | `grep -r "collectAsState" app/src/ --include="*.kt" \| grep -v import \| grep -v "WithLifecycle"` = 0건 |
| T3 | `@Immutable` 7종 추가 | 파일 대조 |
| T4 | 테스트 실행 | `./gradlew :app:testDebugUnitTest` 전 PASS |

### PR 1-B: TopAppBar M3 Expressive 준수

| Task | 작업 | 검증 |
|------|------|------|
| T1 | HomeTopBarActions 리팩터 — 새로고침 + MoreVert(DropdownMenu 7항목) | — |
| T2 | DropdownMenu expanded state 관리 | — |
| T3 | 실기기 검증 (360dp 폰, title + actions 정상) | adb install |

### PR 1-C: 빌드 환경 정리

| Task | 작업 | 검증 |
|------|------|------|
| T1 | `android.builtInKotlin=false` + `android.newDsl=false` 제거 | `./gradlew clean :app:kspDebugKotlin :app:assembleDebug` |
| T2 | (실패 시 revert) | — |
| T3 | `android.r8.strictFullModeForKeepRules=false` 제거 | 빌드 성공 |
| T4 | dependabot.yml Backend Ktor entry 삭제 | — |
| T5 | Detekt baseline 이원화 해소 | `./gradlew :app:detektDebug` 성공 |

### PR 1-D: HomeScreen 패딩 검증 (DEFERRED)

| Task | 작업 |
|------|------|
| T1 | 현재 코드 실기기 스크린샷 |
| T2 | LazyColumn contentPadding 제거 후 비교 |
| T3 | 차이 있으면 수정, 없으면 "정상" 확정 |

---

## Phase 2 — Simple ViewModel UiState 통합 + a11y

> **Phase 2 vs 3 분류 근거**: StateFlow 수가 아닌 **변환 복잡도** 기준. Phase 2 = load-once 패턴 + SideEffect 불필요. Phase 3 = 페이지네이션(History) / SideEffect 필요(Goal=Snackbar, Onboarding=Navigate) / 다중 상태 통합(Onboarding 3개).

### PR 2-A: Statistics + WorkoutDetail + Badge UiState 통합

**각 ViewModel 공통 전환 패턴**:
1. 별도 `_error: StateFlow<AppError?>` → UiState data class에 `error` 필드 통합
2. SideEffect sealed interface + Channel 추가 (향후 확장 대비)
3. **직접 메서드 호출 유지** (공식 패턴 — Intent/dispatch 미사용)
4. Screen에서 `collectAsStateWithLifecycle` 단일 collect

| Task | 대상 | 변경 |
|------|------|------|
| T1 | StatisticsVM | 2 StateFlow → 1 UiState (기존 sealed class에 `Error(AppError)` variant 추가, 별도 `_error` 제거). **Screen-side**: `when(uiState)` exhaustive 분기에 `Error` case 추가 필요 |
| T2 | WorkoutDetailVM | 2 StateFlow → 1 UiState (**신규 UiState data class 생성 필요** — 현재 `_exercise: Exercise?` + `_error`만 보유) |
| T3 | BadgeVM | 2 StateFlow → 1 UiState (**신규 BadgeUiState data class 생성 필요** — 현재 `_badges: List<BadgeDisplayItem>` + `_error`만 보유, BadgeUiState 없음) |
| T4 | 테스트 | `./gradlew :app:testDebugUnitTest` PASS (현재 Statistics/WorkoutDetail/Badge VM 테스트 없음 — Phase 2에서 신규 작성) |

### PR 2-B: a11y 강화

| Task | 작업 | 검증 |
|------|------|------|
| T1 | heading() 시맨틱 (content-rich 화면 섹션 제목 ~8개소) | TalkBack |
| T2 | liveRegion — **에러 메시지/알림만** (로딩 스피너 제외, 공식 가이드) | TalkBack |
| T3 | @Preview 함수 — shared 컴포넌트 6개 | IDE preview 확인 |

---

## Phase 3 — Medium ViewModel UiState 통합 + 의존성

### PR 3-A: History + Goal + Onboarding UiState 통합

| Task | 대상 | 현재 StateFlow | 목표 |
|------|------|---------------|------|
| T1 | HistoryVM | 2 | 단일 UiState |
| T2 | GoalVM | 2 | 단일 UiState (기존 `isSaving` 필드 보존) + SideEffect(Snackbar) |
| T3 | OnboardingVM | 3 | 단일 UiState + SideEffect(Navigate) |
| T4 | GoalScreen supportingText 추가 | — | — |

### PR 3-B: OkHttp 5 마이그레이션

| Task | 작업 | 검증 |
|------|------|------|
| T1 | `libs.versions.toml` okhttp 버전 변경 (4.12.0 → **5.3.2**) | 빌드 성공 |
| T2 | 실기기 API 호출 smoke test | /profile, /weekly-plan 정상 |

> Java 요구사항: **8+** (원래 주장 "11" 은 오류, OkHttp README 확인)

### PR 3-C: Coil 3 마이그레이션 (OkHttp와 독립)

| Task | 작업 | 검증 |
|------|------|------|
| T1 | Maven 좌표 `io.coil-kt` → `io.coil-kt.coil3` | 빌드 |
| T2 | `coil-network-okhttp` 의존성 추가 (필수 — 공식 가이드 확인) | 빌드 |
| T3 | CoilModule DI 업데이트 (ImageLoader 팩토리 API 변경 + GIF decoder 재등록) | 빌드 |
| T4 | WorkoutDetailScreen GIF 로딩 검증 | 실기기 |

> Coil 3은 OkHttp **4.12.0** 기반 (원래 주장 "5 기반"은 오류). OkHttp 5 선행 불필요.

---

## Phase 4 — Complex ViewModel UiState 통합

### PR 4-A: HomeViewModel UiState 통합

| Task | 작업 |
|------|------|
| T1 | 3 StateFlow → 단일 UiState. **error는 UiState에 통합, themeMode는 별도 StateFlow 유지 검토** — themeMode는 DataStore hot flow로 콘텐츠 상태(Loading/Success/Empty)와 독립적이므로 combine 불필요할 수 있음 (실 구현 시 결정) |
| T2 | `_error` + `HomeUiState.Empty` SSOT 위반 해소 |
| T3 | SideEffect Channel 추가 |
| T4 | DayPlanCard 중첩 클릭 분리 (체크 아이콘만 clickable) |
| T5 | 휴식일 카드 false affordance 제거 (`enabled = !day.isRestDay`) |

### PR 4-B: ProfileViewModel UiState 통합

| Task | 작업 |
|------|------|
| T1 | 5 StateFlow → 단일 UiState |
| T2 | delete/save → SideEffect(Navigate, Snackbar) |
| T3 | 직접 메서드 호출 유지 |

---

## Phase 5 — Auth 대규모 리팩터

### PR 5-A: AuthViewModel 3분할

| Task | 작업 |
|------|------|
| T1 | LoginViewModel 추출 (단일 UiState + SideEffect) |
| T2 | SignupViewModel 추출 |
| T3 | ForgotPasswordViewModel 추출 |
| T4 | 공유 상태 — NavGraph scoped ViewModel 또는 AuthSessionManager |
| T5 | AuthErrorBanner 3개 VM 호환 유지 |
| T6 | AuthViewModelTest 21개 → 3개 VM 테스트로 재구성 |

---

## Phase 6 — 조건부 개선

| # | 작업 | 트리거 |
|---|------|--------|
| 6.1 | CI release 빌드 검증 | 서명 키 CI 등록 시 |
| 6.2 | UI 테스트 — **semantic matcher 우선** + testTag는 불가피 시만 | 공식: "testTag = last resort" |
| 6.3 | form focus chain | 사용자 피드백 |
| 6.4 | Chart runBlocking 해소 | Vico 업그레이드 시 |
| 6.5 | Kotlin 2.4.0 + KSP **2.3.9** | Hilt 2.60+ 출시 또는 metadata-jvm 워크어라운드 |
| 6.6 | BottomNavigationBar | 화면 수 증가 시 |

---

## 성공 지표

| 지표 | 현재 | 목표 (Phase 5 완료) |
|------|------|-------------------|
| 단일 UiState ViewModel | 0/9 (전 VM 별도 `_error` StateFlow 보유) | **11/11** (HomeVM themeMode 별도 유지 시 10/11 + themeMode 1) |
| SideEffect Channel 사용 | 0/9 | **11/11** |
| collectAsStateWithLifecycle | 0% (0/31) | **100%** |
| @Immutable 누락 | 7종 | **0종** |
| a11y heading() | 0% | **80%+** |
| TopAppBar actions | 8개 (M3 위반) | **1~2개** (M3 Expressive 준수) |
| 레거시 빌드 플래그 | 3개 | **0개** |
| Detekt baseline 파일 | 2개 | **1개** |

---

## Rev.2 주요 변경 사항 (Rev.1 대비)

| 항목 | Rev.1 | Rev.2 | 사유 |
|------|-------|-------|------|
| 아키텍처 패턴 | Full MVI (sealed Intent + dispatch + BaseViewModel) | **UDF-Enhanced** (직접 메서드 호출 + 단일 UiState + SideEffect Channel) | **공식 문서에 MVI 패턴 없음.** Intent/dispatch는 커뮤니티 관행 |
| BaseViewModel | 도입 (D2) | **철회** | UDF-Enhanced에서 불필요 |
| M3 TopAppBar 상한 | 2~3개 | **2개** (M3 Expressive) | 공식 spec 원문 확인 |
| OkHttp/Coil 순서 | OkHttp 선행 필수 | **순서 무관** | Coil 3 = OkHttp 4.12 기반 (5 아님) |
| OkHttp Java 요구 | Java 11 | **Java 8+** | README 확인 |
| testTag | 전면 도입 | **축소** (semantic matcher 우선) | 공식: "last resort" |
| liveRegion 범위 | 로딩 + 에러 | **에러만** (로딩 제외) | 공식: 빈번 업데이트 부적절 |
| KSP 최신 | 2.3.7 | **2.3.9** | 릴리스 확인 |
| Kotlin 최신 | 2.4.0 확인 | 동일 + **Hilt 2.60 미출시** 확인 | Dagger releases 확인 |

## Rev.2a 감사 보정 (2026-06-05)

| 항목 | Rev.2 | Rev.2a | 사유 |
|------|-------|--------|------|
| collectAsState 전환 대상 | 42건 | **31건** | grep `"collectAsState()"` 가 with-arg 호출 누락. 재측정 = 30 empty-parens + 1 with-arg |
| AuthViewModel StateFlow | 8개 | **7개** | 재측정 확인 (7개 public StateFlow) |
| 단일 UiState 현재값 | ~3/9 | **0/9** | 전 VM 별도 `_error` 보유 — "부분 달성" 아님 |
| 설계 문서 경로 | `docs/plans/` | `docs/plans/_staging/` | staging 위치 반영 |
| 본문 상태 표기 | "작성 중" | "proposed (리뷰 대기)" | frontmatter 일치 |
| WorkoutDetailVM Phase 2 | UiState 통합 | + **신규 UiState class 생성 필요** 명시 | 기존 `_exercise: Exercise?` 구조에 UiState class 없음 |
| Phase 1 검증 명령 | `grep "collectAsState()"` | import/WithLifecycle 제외 필터 추가 | with-arg 호출 누락 방지 |

## Rev.2b 전수 검증 보정 (2026-06-06)

| 항목 | Rev.2a | Rev.2b | 사유 |
|------|--------|--------|------|
| AuthViewModelTest 수 | 19개 | **21개** | `@Test` 메서드 전수 집계 |
| lifecycle-runtime-compose | "의존성 추가" | **이미 존재** | libs.versions.toml:31 + build.gradle.kts:148 확인 |
| BadgeVM UiState | "기존 BadgeUiState에 error 통합" | **신규 생성 필요** | BadgeUiState 클래스 없음 (`_badges: List<BadgeDisplayItem>`) |
| StatisticsVM 전환 전략 | 미명시 | sealed class에 **Error variant 추가** 명시 | 현재 Loading/Loaded/Empty sealed class |
| Phase 2 테스트 현황 | "기존 PASS + 신규" | VM 테스트 **없음** 명시 | AuthViewModelTest만 존재 |
| S2 @Immutable | "6종 누락" | **7종** | FC-5 보정값 배경 테이블 미반영 |
| OkHttp 목표 버전 | "5.x" | **5.3.2** | 최신 stable 확인 |
| S3 레거시 플래그 | "5건" | 제거 대상 **3건** (B1+B2+B4) + 유지 2건 (B3/B5) | 의도적 유지 명시 |

## Rev.2c 테스트/디버깅 + 디자인 보정 (2026-06-06)

| 항목 | Rev.2b | Rev.2c | 사유 |
|------|--------|--------|------|
| Phase 2 T1 StatisticsVM | Screen 영향 미명시 | Screen-side `when` exhaustive **Error case 추가 필요** 명시 | sealed class variant 추가 시 Screen 변경 연동 |
| Phase 2 vs 3 분류 근거 | 미명시 | **변환 복잡도 기준** 명시 (load-once vs 페이지네이션/SideEffect) | HistoryVM(2 StateFlow)이 Phase 3인 이유 설명 |
| Phase 3 T2 GoalVM | `isSaving` 보존 미명시 | 기존 `isSaving` **필드 보존** 명시 | 실제 GoalUiState에 isSaving 있음 — 누락 시 저장 중 표시 회귀 |
| Phase 3-C T3 CoilModule | "DI 업데이트" | ImageLoader 팩토리 API + **GIF decoder 재등록** 명시 | Coil 3 API breaking change |
| Phase 4 T1 HomeVM themeMode | "error + themeMode 통합" | themeMode **별도 유지 검토** — DataStore hot flow 독립성 | combine 불필요 가능성 + 테마 flicker 리스크 |
| 성공 지표 단일 UiState | 11/11 고정 | HomeVM themeMode 별도 시 **10/11 + themeMode 1** 주석 | themeMode 설계 결정 반영 |
