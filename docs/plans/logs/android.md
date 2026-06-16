# Android 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-06-16 — 감사 LOW 후속: SideEffect 라이프사이클 헬퍼 (v0.1.15)

- **PR**: [#123](https://github.com/gunnysis/eundunHealth/pull/123) (merged, squash `078a24fb`) — 백엔드 ③④·starlette 는 backend/dependencies ledger
- **Why**: 직전 v0.1.14 전수감사에서 보류한 LOW 권장 항목 후속 — SideEffect 수집이 화면별 `LaunchedEffect`/수동 collect 라 라이프사이클-aware 하지 않음.
- **What**: `ui/util/ObserveAsEvents.kt` 신설 — `repeatOnLifecycle(STARTED)` 기반 일회성 이벤트 수집 헬퍼. SideEffect Channel 수집을 7 Screen(Login/Signup/ForgotPassword/Home/Profile/Goal/Onboarding)에서 통일.
- **Decisions**: ② 403 매핑은 스킵 — 앱의 403 은 전부 auth 성격이라 별도 매핑 불필요(점검 정정). ① 을 사용자에 반영하려 v0.1.15 bump + 재빌드.
- **Outcome**: 게이트 green + 회귀가드(`ObserveAsEvents`). preflight AAB 8.35 MB, Sentry 매핑 `1e11310d`. `release: v0.1.15` `f9e6790` + 태그 push. Play 업로드 대기(v0.1.14 핵심수정 + ① 포함).
- **Lessons**: `collectAsStateWithLifecycle`(상태) ↔ `ObserveAsEvents`(일회성 이벤트) 가 라이프사이클-aware 짝. (이 도입으로 `collectAsStateWithLifecycle` 측정치 33→20 으로 감소 — CLAUDE.md 룰 11 baseline 갱신.)
- **Files touched**: ui/util/ObserveAsEvents.kt(신규), ui/{auth/Login,auth/Signup,auth/ForgotPassword,home/Home,profile/Profile,goal/Goal,onboarding/Onboarding}Screen.kt

---

### 2026-06-15 — 출시 준비 종합: 빈 운동계획·토글 버그 근본수정 + 전수감사 (v0.1.14)

- **PR**: [#122](https://github.com/gunnysis/eundunHealth/pull/122) (merged, squash `e2d7460`) — design/plan 페어 없음(디버깅 발 + 감사). 인시던트: `incident-log.md` INC-25/26.
- **Why**: 실기기(Flip3) 제보 2버그 — ① 릴리스에서 운동계획이 통째로 빔 ② 완료 체크 해제가 새로고침 후 되돌아옴. + 출시 전 4-에이전트 전수감사로 출시차단 클러스터 식별.
- **What**: ① **R8 Gson keep 갭**(INC-25) — ExerciseDto 만 keep, 래퍼 ExerciseListResponse/PageMeta 누락 → R8 제거 → `emptyList` 폴백. 패키지 단위 keep + `GetOrCreateWeeklyPlanUseCase` 자가치유(`hasExercises`) + 빈 풀 저장 차단. ② **토글 해제 미보존**(INC-26) — `CompletionRequest.manual`/day `manuallySet` 수동 우선 + 토글 직렬화. 전수감사 해소: 완료 정합성(통계 isCompleted 통일·행잠금), 입력검증 500→400, 토큰갱신 동시성/일시실패 견고화(`SessionRefresher` 분리), 운동상세(GIF 싱글톤 복구·복사/선택·`getExerciseById` 데이터흐름 로컬화), 캐시 read 갱신·type 폴백·weekStart KST 고정, BadgeRepo @Singleton.
- **Outcome**: 게이트 green. 백엔드 자동배포(`manual` live) 후 Flip3 e2e 검증(해제→새로고침 유지). v0.1.14 (AAB 8.35 MB, Sentry 매핑 `1a8f12bb`).
- **Lessons**: (1) 릴리스-only silent empty data 는 디버그/단위테스트로 못 잡음 → 룰 12 + `ProguardKeepRulesTest` 박제 + 실기기 계측 필수. [[r8-gson-wrapper-keep-gap]] (2) 검증 실기기 = Flip3 only(사용자 환경 일치, S9+ 사이드이펙트). [[test-device-preference]] (3) **`bump-version.sh` blind-replace**(INC-27) — bump 후 `git diff` 로 과거 버전 오염·versionCode 잔재 수동 정정 필수.
- **재발 방지**: CLAUDE.md 룰 12, `ProguardKeepRulesTest`, TokenAuthenticator/Sync/HomeVM/PlanJsonModels 회귀 테스트.
- **Files touched**: proguard-rules.pro(+ProguardKeepRulesTest), domain/model/WeeklyPlan.kt, domain/usecase/{GetOrCreateWeeklyPlan,SyncHealthData}UseCase.kt, data/repository/WorkoutRepositoryImpl.kt, data/remote/api/dto/PlanJsonModels.kt, data/remote/interceptor/{TokenAuthenticator,SessionRefresher}.kt, ui/home/HomeViewModel.kt, ui/workout/WorkoutDetailScreen.kt(+VM), EundunHealthApplication.kt(GIF 싱글톤)

---

### 2026-06-11 — Health Connect 체성분 가져오기 제거 (수동 단일화, v0.1.12)

- **PR**: [#106](https://github.com/gunnysis/eundunHealth/pull/106) (merged, squash `bf260e9`) — 원래 #105였으나 #104 머지 시 base `--delete-branch` 삭제로 #105 자동 CLOSE → main rebase 후 #106 재생성
- **Why**: #84에서 도입한 HC 체성분 가져오기가 **구조적으로 무용** — HC에 골격근량 타입 부재(공식), 체지방 삼성헬스→HC 동기화 flaky, 스마트체중계 없는 대다수 무데이터 → 영구 "기록 없음" 혼란. 사용자 확인 후 제거(B안). design+plan: `docs/plans/2026-06-10-body-composition-data-{design,plan}.md`.
- **What**: 가져오기 버튼 + `ImportBodyCompositionUseCase` + `BodyComposition` + `readLatestBodyComposition`/`hasBodyCompositionPermissions`/`BODY_COMPOSITION_PERMISSIONS` + `reduceBodyComposition` + `PrefillBodyComposition` + `canImportBodyComposition` 제거. `READ_WEIGHT`/`READ_BODY_FAT` 권한 회수(**6→4**). rationale/privacy/Play권한선언서(`health-connect-permissions.md`) 정합 정정. 신체 4지표 수동 슬라이더 단일화. **활동 HC·목표·알고리즘·백엔드 불변.**
- **Outcome**: 4 게이트 green + grep 무참조 + 최종 코드리뷰(subagent) CLEAN. v0.1.12/26. subagent-driven 자율 실행(구현 subagent + controller fact-check + 리뷰 subagent). HC=활동 자동추적 전용, 신체수치=직접입력 제품 단순화.
- **Lessons**: (1) HC는 저장소/중개자(직접입력 불가)·골격근량 타입 없음·삼성헬스 체성분 동기화 불안정 → 공식·외부 문서로 확정. [[healthconnect-rationale-android14-bug]] (2) **stacked PR의 base를 `gh pr merge --delete-branch`로 머지하면 의존 PR이 retarget 아니라 자동 CLOSE + base 삭제로 reopen 불가 → 의존 PR 먼저 main retarget 후 머지하거나 `--delete-branch` 생략. 복구: `git rebase --onto main <old-base>` + 새 PR.** (3) `bump-version.sh`는 doc 설명문의 옛 버전번호도 치환(설명 오염) → 연속/stacked bump 시 수동 정정 필요.
- **Files touched**: ui/profile/ProfileViewModel.kt·ProfileScreen.kt, domain/repository/HealthRepository.kt+data/repository/HealthRepositoryImpl.kt, data/healthconnect/HealthConnectDataSource.kt·HealthConnectMappers.kt(+Test), AndroidManifest.xml, PermissionsRationaleActivity.kt, docs/store/privacy-policy.md·health-connect-permissions.md, version.properties, README/PRD/operations-snapshot/CLAUDE/CHANGELOG

---

### 2026-06-11 — 코드베이스 리팩토링 Bundle E·A·C (도메인 정합·알고리즘 분리·UI 중복)

- **PR**: E [#109](https://github.com/gunnysis/eundunHealth/pull/109)(`bb4a0ed`) · A [#110](https://github.com/gunnysis/eundunHealth/pull/110)(`efdaf56`) · C [#111](https://github.com/gunnysis/eundunHealth/pull/111)(`16909ad`) (모두 merged)
- **Design/Plan**: `docs/plans/2026-06-11-codebase-refactoring-{design,plan}.md` (5-번들 이니셔티브 공통 페어 — process-infra/backend ledger 와 함께 아카이브)
- **Why**: Android 감사 — (E) `UserProfile.bodyFat/muscleMass` 가 백엔드 nullable 계약·`Goal`/`ProfileHistoryPoint` 와 불일치(0f fabrication). (A) `WorkoutRepositoryImpl.createWeeklyPlan` 84줄에 핵심 제품 로직(seeded shuffle·슬롯·rest-day)이 I/O 와 묶여 무테스트. (C) Vico 차트 중복 + composition 스레드 `runBlocking`(ANR/jank) + Auth VM resend 로직 byte-identical 복붙 + 에러 idiom 5곳.
- **What**: (E) body metrics `Float?` + `fitnessLevel` null-safe `(?:0f)` + 0f 마스킹 제거 + 슬라이더 기본값 0%→20%. (A) 순수 `WeeklyPlanGenerator` 추출(line-for-line 동일·JVM 테스트) + 죽은코드 4건(savePlanToServer·deleteOldPlans·inert 404·미사용 default). (C) 공유 `LineChart`(runBlocking 제거·Vico 공식 LaunchedEffect 패턴) + `ResendConfirmationController`(합성) + `toAppErrorReporting()` 확장 + `BodyMetricsSliders` promote.
- **Decisions**: E 는 **정합·데이터충실성** 리팩토링 — `fitnessLevel` 이 `(bodyFat ?:0f)` coalesce 라 null/0f 동일 → "ADVANCED 오분류" 는 실버그 아님(점검 정정), `ProfileSummaryCard` "—" 은 YAGNI 로 제거(카드는 슬라이더 Float 만 수신). 온보딩 체지방 "선택 입력화" 는 **거부**(#106 수동 단일화 유지). resend 는 합성>상속. A generator 는 도메인 순수(`@Immutable` 만)라 JVM 테스트.
- **Outcome**: 통합 main 게이트 green(BUILD SUCCESSFUL) + backend 54 passed. 신규 테스트 10: WeeklyPlanGenerator 5·UserProfile 3·ResendController 2. A 알고리즘 회귀 0·C ViewModel 공개 API 보존(Screen 무변경) 검증.
- **Lessons**: (1) **stacked PR + squash 머지**: Screen 파일이 겹쳐 E(base D)←C(base E) stack. squash 는 자식이 부모 커밋을 잃으므로 **부모 머지 후 자식을 `git rebase --onto origin/main <old-base>` 로 재배치·force-push** 해야 clean(안 하면 자식 diff 가 B/A revert 로 오염). A 는 무겹침이라 독립(main). [[git-workflow]] (2) controller 가 매 번들 diff 직접 검수 + 게이트 독립 재실행(룰 10)으로 subagent 보고 fact-check. (3) `runBlocking` 잔여 grep 시 KDoc 주석("runBlocking 없음")이 false positive — 호출/주석 구분 필요.
- **Files touched**: (E) domain/model/UserProfile.kt(+Test), data/repository/UserRepositoryImpl.kt, ui/profile/ProfileScreen.kt / (A) domain/usecase/WeeklyPlanGenerator.kt(+Test 신규), domain/repository/WorkoutRepository.kt, data/repository/WorkoutRepositoryImpl.kt, data/local/dao/WeeklyPlanDao.kt / (C) ui/components/{LineChart,BodyMetricsSliders}.kt(신규), ui/auth/{AuthErrorReporting,ResendConfirmationController}.kt(+Test 신규), ui/auth/{Login,Signup,Auth}ViewModel.kt, ui/goal/GoalScreen.kt, ui/statistics/StatisticsScreen.kt, ui/onboarding/OnboardingScreen.kt
- **Postmortem**: (머지 + 7일 후.)

### 2026-06-10 — Health Connect Android 14+ 수정: 연동 버튼 무반응 + 읽기 실패 (v0.1.11)

- **PR**: [#104](https://github.com/gunnysis/eundunHealth/pull/104) (merged, squash `7381007`) — design/plan 페어 없음(디버깅 발 기능 수정)
- **Why**: 내부테스트 실기기(Galaxy Flip3/Android 15)에서 "연동" 버튼 무반응 + 체성분/오늘의활동 읽기 실패. 개발기기(S9+/Android 10)에선 정상 → **Android 버전 게이트 버그**(사용자 확인 단계에서 첫 발현).
- **What**: ① **rationale intent** — 매니페스트 `ViewPermissionUsageActivity` 가 14+ 액션(`VIEW_PERMISSION_USAGE`+`HEALTH_PERMISSIONS`) 대신 레거시만 선언 → controller 가 grant 화면 거부(`App should support rationale intent, finishing!`). 14+/≤13 둘 다 선언 + `PermissionsRationaleActivity` 신설 + `ManifestHealthConnectRationaleTest`. ② **런처 아이콘** 색상-only 어댑티브(intrinsic -1) → HC access-log 비트맵화 실패(`width and height must be > 0`) → 모든 HC 읽기 throw. `app-icon.svg`→어댑티브 벡터 foreground+monochrome(테마)+density PNG 로 intrinsic 확보. ③ **account-deletion 완전성**(`goals`·`user_profile_history` purge, backend) + Play store 자산.
- **Outcome**: 실기기(Android 15) 실측 검증(grant 화면 렌더링 + bitmap 예외 0건 + 읽기 성공) + backend.yml 배포 success + 4 게이트 green. v0.1.11. systematic-debugging(logcat ground truth)으로 진단.
- **Lessons**: 색상-only/placeholder 어댑티브 아이콘은 Android 14+ HC 읽기를 깬다. HC rationale 은 14+/≤13 별개 선언 필요. 버전 게이트 버그는 개발기기(구버전)에서 안 잡힘 → 타깃 버전 실기기 검증 필수. [[healthconnect-rationale-android14-bug]]
- **Files touched**: AndroidManifest.xml, PermissionsRationaleActivity.kt(+Test), res/mipmap-*·drawable 아이콘, backend/app/services/account_service.py·repositories(+test), docs/store/*, version.properties, README/PRD/operations-snapshot/CLAUDE/CHANGELOG

---

### 2026-06-08 — 홈 "오늘의 활동" 요약 (#2, 걸음·칼로리·심박)

- **PR**: [#85](https://github.com/gunnysis/eundunHealth/pull/85) (merged, squash `1ff03fb`)
- **Why**: 갤럭시워치/폰이 측정한 오늘 활동량(HC 동기화됨 — 로드맵 R2 해소)을 홈에 glanceable 표시 → engagement. 로드맵 #2.
- **What**: `DailyActivity` 모델 + `HealthConnectDataSource.readTodayActivity()`(**aggregate 1 IPC**: Steps.COUNT_TOTAL / TotalCalories.ENERGY_TOTAL / HeartRate.BPM_AVG) + `DAILY_ACTIVITY_PERMISSIONS` + `HealthRepository.getTodayActivity()`/`hasDailyActivityPermissions()` + `GetTodayActivityUseCase`(TDD 5, `TodayActivityResult(activity, hasPermission)`) + HomeViewModel render-first 백그라운드 로드 + HomeScreen `TodayActivityCard`(무권한→연동 / 빈→안내 / 데이터→👟🔥❤) + in-Composable 권한 launcher. **표시 전용(백엔드 무변경)**.
- **Outcome**: 4 게이트 green(use-case 테스트 16: GetTodayActivity 5 + Import 5 + Sync 6). 최종 독립 리뷰 머지 가능(Critical/Important 0). v0.2.0. Subagent-Driven(데이터→UI 2그룹).
- **Lessons**: (1) `Success` data class 에 새 필드 추가 시 **재생성 헬퍼(`successWithStats`)를 쓰는 모든 경로**가 새 필드를 기본값으로 리셋함 → `toggleDayCompletion` 에서 활동 카드 사라지는 회귀를 controller 리뷰로 발견·수정(`current.copy`). 새 상태 필드 추가 시 모든 Success 생성부 점검 필수. (2) 그 줄 변경이 `toggleDayCompletion` 의 detekt UnreachableCode false-positive baseline 시그니처를 깨 #83 drift 재발 → 재생성 diff 로 새 위반 0 확인 후 시그니처 정정(억제 아님). [[detekt-baseline-drift]]
- **Files touched**: AndroidManifest.xml, domain/model/DailyActivity.kt, data/healthconnect/HealthConnectDataSource.kt, domain/repository/HealthRepository.kt, data/repository/HealthRepositoryImpl.kt, domain/usecase/GetTodayActivityUseCase.kt(+Test), ui/home/HomeViewModel.kt, ui/home/HomeScreen.kt, SyncHealthDataUseCaseTest.kt, ImportBodyCompositionUseCaseTest.kt, config/detekt/baseline.xml

---

### 2026-06-08 — 체성분(체중·체지방) Health Connect 가져오기 (#1) + 골격근량 표기

- **PR**: [#84](https://github.com/gunnysis/eundunHealth/pull/84) (merged, squash `0315a84`)
- **Why**: 체중·체지방을 수기 입력하던 것을, HC 최신 측정값(워치/체중계→삼성헬스→HC)을 **사용자 확인 가져오기**로 줄임. health data 로드맵 #1 (`docs/plans/2026-06-08-health-data-roadmap-design.md`).
- **What**: `BodyComposition` 모델 + `HealthConnectDataSource.readLatestBodyComposition()`(Weight/BodyFat 최신) + `BODY_COMPOSITION_PERMISSIONS` + `HealthRepository.getLatestBodyComposition()`/`hasBodyCompositionPermissions()` + `ImportBodyCompositionUseCase`(TDD 5) + `ProfileViewModel.importBodyComposition()` → `PrefillBodyComposition` SideEffect(룰 11) + ProfileScreen 가져오기 버튼/권한 launcher/슬라이더 prefill. "근육량"→"골격근량" 표기(내부 필드 `muscleMassKg` 유지). **백엔드 무변경**(PUT /profile → history append 재사용).
- **Outcome**: spotlessCheck+detektDebug+testDebugUnitTest(Import 5 + Sync 6)+assembleDebug green. 최종 독립 코드리뷰 "머지 가능"(Critical/Important 0). v0.2.0 대상. Subagent-Driven 실행(데이터→UI→라벨 3그룹). 수동 실기기 검증은 미완(HC 실기기 필요).
- **Lessons**: check-index 의 *shipped-페어-잔존 가드* 가 기존 frontend shipped 문서 3건(ledger entry 는 있었으나 `git rm` 누락)을 본 PR 의 docs/plans 변경으로 탐지 → 누락 `git rm` 완료로 해소. shipped 처리는 ledger entry + `git rm` 둘 다 같은 변경에 포함해야 함.
- **Follow-ups**: Play Console Health 권한 선언(READ_WEIGHT/READ_BODY_FAT, #4) · 권한 거부 UX·한쪽 권한(WEIGHT only) import(#4/후속) · 골격근량 자동 가져오기는 **미채택(reject, 2026-06-09)** — 수동 입력 영구 유지(Samsung SDK 파트너 승인·벤더 종속 회피. LeanBodyMass=제지방량은 골격근량과 다른 지표라 대용 불가).
- **Files touched**: AndroidManifest.xml, domain/model/BodyComposition.kt, data/healthconnect/HealthConnectDataSource.kt, domain/repository/HealthRepository.kt, data/repository/HealthRepositoryImpl.kt, domain/usecase/ImportBodyCompositionUseCase.kt(+Test), ui/profile/ProfileViewModel.kt, ui/profile/ProfileScreen.kt, ui/onboarding/OnboardingScreen.kt, ui/components/ProfileSummaryCard.kt, SyncHealthDataUseCaseTest.kt, README/PRD/SPEC/privacy-policy

---

### 2026-06-06 — 프론트엔드 회귀 방지 3계층 가드

- **Why**: Phase 1-5 UDF-Enhanced 마이그레이션 후 옛 패턴 (분산 StateFlow, `collectAsState()`, `@Immutable` 누락) 재도입 방지.
- **설계**: `docs/plans/2026-06-06-frontend-regression-prevention-design.md`
- **산출물**: CLAUDE.md 룰 11 (ViewModel UDF-Enhanced 5개 체크리스트) + `.github/workflows/android.yml` collectAsState CI step + `.githooks/pre-commit` collectAsState check
- **커밋**: `614545d`
- **결과**: CI + pre-commit + CLAUDE.md 3계층 가드 운영 중. baseline: `collectAsState()` 0건, `collectAsStateWithLifecycle` 33건.

---

### 2026-06-05/06 — 프론트엔드 대규모 개선 Phase 1-5 (UDF-Enhanced 마이그레이션)

- **Why**: 12 ViewModel의 분산 StateFlow / `collectAsState()` / `@Immutable` 누락 등 6개 Gap을 일괄 해소. Compose lifecycle 리소스 낭비 + recomposition 회귀 + SSOT 위반 제거.
- **설계**: `docs/plans/2026-06-05-frontend-major-improvement-design.md` (Rev.2)
- **실행 계획**: `docs/plans/2026-06-05-frontend-major-improvement-plan.md` (Rev.2)
- **산출물**:
  - 9 ViewModel → 단일 `_uiState: MutableStateFlow<XxxUiState>` 전환
  - 3 ViewModel 신규 (AuthVM 분리 → LoginVM, SignupVM, ForgotPasswordVM)
  - 11 Screen → `collectAsStateWithLifecycle` 교체
  - `@Immutable` 45건 across 17 files
  - SideEffect Channel 7 VMs
  - OkHttp 4→5, Coil 2→3 메이저 업그레이드
  - 3 test files 신규 (LoginVMTest 202L, SignupVMTest 192L, ForgotPasswordVMTest 110L)
- **커밋**: `614545d`
- **결과**: 46 files, +752 / -869 lines (net -117). CI 전체 통과.

---

### 2026-06-04 — Claude Code Plugin Errors 진단 및 해결 방안

- **Why**: Claude Code 시작 시 10개 플러그인 에러 발생 — 9개 "not found in marketplace" + 1개 `spawn vtsls ENOENT`. 근본 원인 분석 및 해결 방안 설계.
- **대상**: `~/.claude/plugins/`, `~/.claude/settings.json`, `.claude/settings.json` (프로젝트)
- **결과**: ✅ **해결 완료 (2026-06-04)** — 4개 조치 후 재시작 에러 0건 확인. ① 마켓플레이스 re-clone (`git clone anthropics/claude-plugins-official`) ② blocklist에서 code-review 제거 ③ vtsls 프로젝트 설정 제거 ④ vtsls 글로벌 `false`

---

#### A. 에러 분류 및 근본 원인

| # | 에러 메시지 | 마켓플레이스 디렉토리 존재 | 캐시 존재 | 근본 원인 |
|---|-----------|------------------------|---------|----------|
| 1 | `superpowers@claude-plugins-official: not found` | ❌ (`plugins/`·`external_plugins/` 모두 없음) | ✅ `cache/claude-plugins-official/superpowers/5.1.0/` | **외부 소스 플러그인** — catalog에서 `https://github.com/obra/superpowers.git` URL로 참조되나 marketplace 디렉토리에는 미포함 |
| 2 | `feature-dev@claude-plugins-official: not found` | ✅ `plugins/feature-dev/` | ✅ `cache/.../feature-dev/` | **마켓플레이스 해석 실패** |
| 3 | `frontend-design@claude-plugins-official: not found` | ✅ `plugins/frontend-design/` | ✅ `cache/.../frontend-design/` | 동일 |
| 4 | `github@claude-plugins-official: not found` | ✅ `external_plugins/github/` | ✅ `cache/.../github/` | 동일 |
| 5 | `code-review@claude-plugins-official: not found` | ✅ `plugins/code-review/` | ✅ `cache/.../code-review/` | 동일 + **blocklist.json에 등록됨** (테스트 항목) |
| 6 | `context7@claude-plugins-official: not found` | ✅ `external_plugins/context7/` | ✅ `cache/.../context7/` | 마켓플레이스 해석 실패 |
| 7 | `commit-commands@claude-plugins-official: not found` | ✅ `plugins/commit-commands/` | ✅ `cache/.../commit-commands/` | 동일 |
| 8 | `pr-review-toolkit@claude-plugins-official: not found` | ✅ `plugins/pr-review-toolkit/` | ✅ `cache/.../pr-review-toolkit/` | 동일 |
| 9 | `kotlin-lsp@claude-plugins-official: not found` | ✅ `plugins/kotlin-lsp/` | ✅ `cache/.../kotlin-lsp/1.0.0/` | 동일 |
| 10 | `plugin:vtsls:typescript: spawn vtsls ENOENT` | — (`claude-code-lsps` 마켓) | ✅ `cache/claude-code-lsps/vtsls/0.1.0/` | **vtsls 바이너리 미설치** — npm 패키지 `@vtsls/language-server` 미설치 |

#### B. 근본 원인 분석

##### B.1 마켓플레이스 디렉토리 손상 (에러 1~9)

**발견**: `~/.claude/plugins/marketplaces/claude-plugins-official/`에 `.git` 디렉토리 없음.

```
marketplaces/claude-plugins-official/
├── README.md
├── plugins/          ← 36개 내부 플러그인
└── external_plugins/ ← 15개 외부 플러그인
```

`known_marketplaces.json`은 소스를 `github:anthropics/claude-plugins-official`로 기록하고 `lastUpdated: 2026-05-27`을 표시하지만, 실제 디렉토리에 git metadata가 없어 **Claude Code의 플러그인 해석기가 마켓플레이스를 유효한 git 클론으로 인식하지 못함**.

**가설**: Claude Code 업데이트 또는 마켓플레이스 동기화 과정에서 shallow clone이 실패하거나 `.git/` 이 제거됨. 파일은 남아있으나 git 기반 검증이 실패하여 모든 플러그인을 "not found"로 보고.

##### B.2 superpowers 특수 케이스 (에러 1)

`superpowers`는 catalog에서 외부 URL(`https://github.com/obra/superpowers.git`)로 소스되는 플러그인. 마켓플레이스 디렉토리에 물리적으로 존재하지 않으며, 별도 git clone으로 `cache/`에만 존재. 캐시가 유효하면 동작해야 하나, 마켓플레이스 해석 실패와 연쇄적으로 에러 발생.

##### B.3 vtsls ENOENT (에러 10)

`vtsls`는 `claude-code-lsps` 마켓플레이스(GitHub: `Piebald-AI/claude-code-lsps`)의 LSP 플러그인. 플러그인 설정은 정상이나, 실제 Language Server 바이너리(`vtsls` 또는 `@vtsls/language-server` npm 패키지)가 시스템 PATH에 없음.

```bash
$ which vtsls      → NOT FOUND
$ npm list -g @vtsls/language-server → (empty)
```

##### B.4 code-review blocklist (에러 5 보조 원인)

`blocklist.json`에 `code-review@claude-plugins-official`이 등록됨 (사유: `"just-a-test"`). blocklist 항목은 플러그인 로드를 차단하므로, 마켓플레이스 해석 성공해도 별도로 차단됨.

---

#### C. 설정 파일 현황

**Global** (`~/.claude/settings.json`):
```json
"enabledPlugins": {
    "github@claude-plugins-official": false,        // 비활성
    "frontend-design@claude-plugins-official": true,
    "superpowers@claude-plugins-official": true,
    "feature-dev@claude-plugins-official": true,
    "context7@claude-plugins-official": true,
    "code-review@claude-plugins-official": true,     // blocklist 충돌
    "vtsls@claude-code-lsps": true,
    "document-skills@anthropic-agent-skills": true,  // 정상 동작 중
    "commit-commands@claude-plugins-official": true,
    "pr-review-toolkit@claude-plugins-official": true,
    "powershell-editor-services@claude-code-lsps": true
}
```

**Project** (`.claude/settings.json`):
```json
"enabledPlugins": {
    "kotlin-lsp@claude-plugins-official": true,
    "vtsls@claude-code-lsps": true
}
```

**마켓플레이스 등록** (`extraKnownMarketplaces`):
- `claude-code-lsps` → `Piebald-AI/claude-code-lsps` ✅
- `anthropic-agent-skills` → `anthropics/skills` ✅ (에러 없음)

---

#### D. 해결 방안

##### D.1 마켓플레이스 재동기화 (에러 1~9 일괄 해결) — 권장

```bash
# 방법 1: Claude Code CLI로 마켓플레이스 업데이트
claude plugin update

# 방법 2: 마켓플레이스 디렉토리 삭제 후 재설치
rm -rf ~/.claude/plugins/marketplaces/claude-plugins-official
# → 다음 Claude Code 시작 시 자동 re-clone

# 방법 3: 전체 플러그인 캐시 초기화 (최후 수단)
rm -rf ~/.claude/plugins/marketplaces/
rm -rf ~/.claude/plugins/cache/claude-plugins-official/
# → 다음 Claude Code 시작 시 전체 재구축
```

**검증**:
```bash
# 마켓플레이스 디렉토리에 .git 존재 확인
ls -la ~/.claude/plugins/marketplaces/claude-plugins-official/.git
# 플러그인 목록 확인
claude plugin list
```

##### D.2 vtsls 바이너리 설치 (에러 10) — 선택

**옵션 A: npm 글로벌 설치** (TypeScript/JavaScript 프로젝트 사용 시)
```bash
npm install -g @vtsls/language-server
# 설치 확인
which vtsls
```

**옵션 B: 플러그인 비활성화** (본 프로젝트는 Kotlin/Android — TS 불필요)
```bash
# Global에서 비활성화
# ~/.claude/settings.json의 enabledPlugins에서 제거:
#   "vtsls@claude-code-lsps": true  → 삭제 또는 false

# Project에서 비활성화
# .claude/settings.json의 enabledPlugins에서 제거:
#   "vtsls@claude-code-lsps": true  → 삭제 또는 false
```

**권장**: **옵션 B** — 본 프로젝트는 Android/Kotlin 전용이므로 TypeScript LSP 불필요. 프로젝트 설정에서 제거.

##### D.3 code-review blocklist 해제 (에러 5 보조)

```bash
# blocklist.json에서 code-review 항목 제거
# ~/.claude/plugins/blocklist.json:
# "plugins" 배열에서 code-review 항목 삭제
```

현재 blocklist 내용:
```json
{
  "plugins": [
    {
      "plugin": "code-review@claude-plugins-official",
      "reason": "just-a-test"     // ← 테스트 목적 등록, 제거 가능
    },
    ...
  ]
}
```

##### D.4 불필요 플러그인 정리 (선택)

본 프로젝트(Android/Kotlin)에 불필요한 플러그인:

| 플러그인 | 용도 | 본 프로젝트 필요성 | 조치 |
|---------|------|-----------------|------|
| `vtsls@claude-code-lsps` | TypeScript LSP | ❌ 불필요 | 비활성화 |
| `github@claude-plugins-official` | GitHub MCP | ⚠️ 이미 `false` | 유지 (비활성) |
| `frontend-design@claude-plugins-official` | 웹 프론트엔드 디자인 | ⚠️ 웹 아님 | 유지 가능 (document-skills 포함) |
| `powershell-editor-services@claude-code-lsps` | PowerShell LSP | ⚠️ 스크립트 보조 | 유지 권장 |

필수 유지:
- `superpowers` — SDD 워크플로우 (CLAUDE.md 참조)
- `feature-dev` — 기능 개발 워크플로우
- `context7` — 컨텍스트 관리
- `code-review` — 코드 리뷰 (blocklist 해제 필요)
- `commit-commands` — Git 커밋 워크플로우
- `pr-review-toolkit` — PR 리뷰
- `kotlin-lsp` — Kotlin 코드 인텔리전스 (핵심)
- `document-skills@anthropic-agent-skills` — 문서 생성 (현재 정상 동작)

---

#### E. 실행 순서 (우선순위)

| 순서 | 작업 | 명령 | 영향 |
|------|------|------|------|
| **1** | 마켓플레이스 디렉토리 삭제 | `rm -rf ~/.claude/plugins/marketplaces/claude-plugins-official` | 에러 1~9 해결 |
| **2** | Claude Code 재시작 | `claude` (새 세션) | 마켓플레이스 자동 re-clone |
| **3** | 플러그인 로드 확인 | 시작 시 Plugin Errors 메시지 확인 | 검증 |
| **4** | blocklist에서 code-review 제거 | `blocklist.json` 편집 | 에러 5 보조 해결 |
| **5** | vtsls 비활성화 (프로젝트) | `.claude/settings.json` 편집 | 에러 10 해결 |
| **6** | vtsls 비활성화 (글로벌) | `~/.claude/settings.json` 편집 | 다른 프로젝트에서도 에러 방지 |

---

#### F. 3개 마켓플레이스 상태 요약

| 마켓플레이스 | 소스 | 플러그인 수 | 최종 업데이트 | 상태 |
|------------|------|-----------|-------------|------|
| `claude-plugins-official` | `anthropics/claude-plugins-official` | 203 (catalog) / 51 (local) | 2026-05-27 | ⚠️ .git 없음 → 해석 실패 |
| `claude-code-lsps` | `Piebald-AI/claude-code-lsps` | 2 (vtsls, powershell-editor-services) | 2026-05-23 | ⚠️ vtsls 바이너리 미설치 |
| `anthropic-agent-skills` | `anthropics/skills` | 1 (document-skills) | 2026-05-24 | ✅ 정상 |

### 2026-06-04 — Clean Architecture + MVI + Multi-module 아키텍처 설계 검토

- **Why**: 시니어 안드로이드 아키텍트 관점에서 현재 프로젝트를 [Clean Architecture + MVI + Multi-module] 최신 권장 표준 대비 전수 감사. Presentation / Domain / Data / DI / Module 5개 계층별 적합도 분석 + MVI 패턴 Gap 식별. 코드 변경 없는 분석 세션.
- **대상**: `ui/` 전체 (9 ViewModel · 11 Screen) + `domain/` (3 UseCase · 6 Repository interface · 11 Model) + `data/` (6 Repository impl · DTO · Mapper · Room · DataSource) + `di/` (5 Hilt Module) + Gradle module 구조

---

#### A. 현재 아키텍처 종합 진단

| 계층 | 현재 패턴 | MVI 표준 대비 | 적합도 |
|------|----------|-------------|--------|
| **Presentation** | MVVM hybrid (direct method calls + 다중 StateFlow) | Intent dispatch + 단일 UiState + SideEffect Channel | ⚠️ 40% |
| **Domain** | UseCase `operator invoke()` + Repository interface | 표준 일치 | ✅ 90% |
| **Data** | DTO/Entity 분리 + Mapper + Repository impl | 표준 일치 | ✅ 95% |
| **DI** | Hilt `@Binds`/`@Provides` + SingletonComponent | 표준 일치 | ✅ 95% |
| **Module** | 단일 모듈 (`:app` only) | Multi-module (core/feature/data) | ❌ 0% |

---

#### B. Presentation Layer 상세 분석

##### B.1 ViewModel 상태 관리 현황 (9개 ViewModel)

| ViewModel | 노출 StateFlow 수 | UiState 패턴 | MVI 적합 |
|-----------|-------------------|-------------|---------|
| `AuthViewModel` | **8개** | 3 sealed class 분산 (`SessionState` + `AuthOpState` + `SignupState` + `pendingEmail` + `passwordResetSent` + `resendCooldownSec` + `resendError`) | ❌ 심각 |
| `ProfileViewModel` | **5개** | 3 sealed class 분산 (`ProfileUiState` + `SaveState` + `DeleteState` + `isSaving` + `error`) | ❌ |
| `OnboardingViewModel` | **3개** | 개별 flag (`saved` + `isLoading` + `error`) | ❌ |
| `BadgeViewModel` | **2개** | 개별 (`badges` + `error`) | ⚠️ |
| `WorkoutDetailViewModel` | **2개** | 개별 (`exercise` + `error`) | ⚠️ |
| `HomeViewModel` | **3개** | sealed `HomeUiState` + `error` + `themeMode` | ⚠️ 부분 |
| `HistoryViewModel` | **2개** | 단일 data class `HistoryUiState` + `error` | ✅ 양호 |
| `GoalViewModel` | **2개** | 단일 data class `GoalUiState` + `error` | ✅ 양호 |
| `StatisticsViewModel` | **2개** | sealed `StatisticsUiState` + `error` | ✅ 양호 |

**핵심 문제**: MVI에서는 화면당 **단일 `UiState`** + **단일 `SideEffect`** 2개 Flow만 노출해야 함. 현재 AuthViewModel이 8개, ProfileViewModel이 5개 StateFlow를 노출 — UI가 여러 Flow를 개별 collect하며 상태 동기화 위험.

##### B.2 Intent/Action 패턴 — ❌ 부재

**현재**: 모든 Screen에서 ViewModel 메서드 **직접 호출** (총 40+ 개소)

```kotlin
// 현재 (HomeScreen.kt:82,95,110,131)
viewModel.loadPlan()
viewModel.toggleDayCompletion(day.date)
viewModel.cycleTheme()
viewModel.clearError()

// MVI 권장
viewModel.dispatch(HomeIntent.LoadPlan)
viewModel.dispatch(HomeIntent.ToggleDayCompletion(day.date))
viewModel.dispatch(HomeIntent.CycleTheme)
// clearError는 UiState 내부 error 필드를 Intent로 소비
```

**검출 위치별 직접 호출 수**:

| Screen | 직접 호출 수 | 주요 호출 |
|--------|------------|----------|
| `LoginScreen.kt` | 5 | `login()`, `resendConfirmation()`, `consumeAuthOpError()`, `clearResendError()`, `clearPendingEmail()` |
| `SignupScreen.kt` | 6 | `signup()`, `resendConfirmation()`, `clearSignupError()`, `setPendingEmail()`, `resetSignupState()`, `clearResendError()` |
| `ForgotPasswordScreen.kt` | 3 | `resetPassword()`, `consumePasswordResetSent()`, `consumeAuthOpError()` |
| `HomeScreen.kt` | 4 | `loadPlan()`, `toggleDayCompletion()`, `cycleTheme()`, `clearError()` |
| `ProfileScreen.kt` | 6 | `loadProfile()`, `saveProfile()`, `deleteAccount()`, `clearError()`, `clearSaveState()` |
| `OnboardingScreen.kt` | 2 | `saveProfile()`, `clearError()` |
| `HistoryScreen.kt` | 2 | `loadNextPage()`, `clearError()` |
| `BadgeScreen.kt` | 2 | `loadBadges()`, `clearError()` |
| `GoalScreen.kt` | 2 | `saveGoal()`, `clearError()` |
| `StatisticsScreen.kt` | 2 | `load()`, `clearError()` |
| `WorkoutDetailScreen.kt` | 1 | `clearError()` |

##### B.3 Side Effect 처리 — ❌ SharedFlow/Channel 미사용

**현재**: `LaunchedEffect`로 StateFlow 변화를 감시하여 1회성 이벤트(Navigation, Snackbar) 처리.

```kotlin
// 현재 패턴 (ProfileScreen.kt:72-81)
LaunchedEffect(saveState) {
    when (saveState) {
        is SaveState.Success -> {
            snackbarHostState.showSnackbar("신체 정보가 저장되었습니다")
            viewModel.clearSaveState()   // 수동 소비 필요
            onBack()                      // Navigation side effect
        }
        is SaveState.Idle -> {}
    }
}
```

**MVI 권장**: `Channel` 또는 `SharedFlow`로 1회성 이벤트를 별도 스트림에서 처리.

```kotlin
// MVI 권장 패턴
sealed class ProfileSideEffect {
    data class ShowSnackbar(val message: String) : ProfileSideEffect()
    data object NavigateBack : ProfileSideEffect()
}

// ViewModel
private val _sideEffect = Channel<ProfileSideEffect>(Channel.BUFFERED)
val sideEffect = _sideEffect.receiveAsFlow()

// Screen
LaunchedEffect(Unit) {
    viewModel.sideEffect.collect { effect ->
        when (effect) {
            is ProfileSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            ProfileSideEffect.NavigateBack -> onBack()
        }
    }
}
```

**현재 Side Effect 발생 지점** (LaunchedEffect 기반, 수동 소비):

| Screen | LaunchedEffect key | Side Effect 종류 | 수동 소비 메서드 |
|--------|--------------------|-----------------|-----------------|
| `ProfileScreen.kt:66` | `deleteState` | Navigation (account deleted) | — |
| `ProfileScreen.kt:72` | `saveState` | Snackbar + Navigation (back) | `clearSaveState()` |
| `ProfileScreen.kt:83` | `error` | Snackbar | `clearError()` |
| `GoalScreen.kt:69` | `error` | Snackbar | `clearError()` |
| `ForgotPasswordScreen.kt:57` | `passwordResetSent` | Snackbar + Navigation (back) | `consumePasswordResetSent()` |
| `LoginScreen.kt:57` | `Unit` | 초기화 (pending email) | `clearPendingEmail()` |
| `AppNavigation.kt:36` | `sessionState` | Navigation (auth → home) | — |

**위험**: StateFlow 기반 side effect는 **재소비(replay)** 위험 — configuration change 시 `LaunchedEffect`가 재실행되면 이미 처리된 이벤트를 다시 처리할 수 있음. `Channel`은 소비 즉시 삭제되어 이 문제 없음.

##### B.4 State Collection — ❌ 100% `collectAsState()` 사용

**전수 검사 결과**: 30개 collection 지점 전부 `collectAsState()`.

| 구분 | 횟수 | 비율 |
|------|------|------|
| `collectAsState()` | **30** | 100% |
| `collectAsStateWithLifecycle()` | **0** | 0% |

**차이점**:
- `collectAsState()`: Composition scope에서 collect — 화면이 백그라운드에 있어도 수집 계속 → 불필요한 리소스 소비
- `collectAsStateWithLifecycle()`: Lifecycle.State.STARTED 이상에서만 collect — 백그라운드 시 자동 중단

**영향**: 현재 앱은 단일 Activity + Compose Navigation이므로 실질적 영향은 제한적이나, MVI 표준 준수 및 멀티 Activity / 멀티 윈도우 환경에서 필수.

---

#### C. Domain Layer 상세 분석 — ✅ 90% 적합

##### C.1 UseCase (3개)

| UseCase | 파일 | `operator invoke()` | Repository interface 의존 | 상태 |
|---------|------|---------------------|--------------------------|------|
| `GetOrCreateWeeklyPlanUseCase` | `domain/usecase/GetOrCreateWeeklyPlanUseCase.kt` | ✅ | `WorkoutRepository`, `UserRepository` | ✅ |
| `SyncHealthDataUseCase` | `domain/usecase/SyncHealthDataUseCase.kt` | ✅ | `HealthRepository`, `WorkoutRepository` | ✅ |
| `CheckAndAwardBadgesUseCase` | `domain/usecase/CheckAndAwardBadgesUseCase.kt` | ✅ | `BadgeRepository` | ✅ |

**Gap**: UseCase가 3개뿐 — HomeViewModel에만 집중. 다른 ViewModel(Profile, Auth, Goal 등)은 Repository를 직접 호출하여 UseCase 캡슐화 누락.

##### C.2 Repository Interface (6개)

| Interface | 메서드 수 | 구현체 | 상태 |
|-----------|----------|--------|------|
| `AuthRepository` | 9 | `AuthRepositoryImpl` | ✅ |
| `UserRepository` | 2 | `UserRepositoryImpl` | ✅ |
| `WorkoutRepository` | 6 | `WorkoutRepositoryImpl` | ✅ |
| `BadgeRepository` | 3 | `BadgeRepositoryImpl` | ✅ |
| `GoalRepository` | 3 | `GoalRepositoryImpl` | ✅ |
| `HealthRepository` | 2 | `HealthRepositoryImpl` | ✅ |

모든 Repository interface가 `domain/repository/`에 정의, 구현체가 `data/`에 위치 — **의존성 역전 원칙** 준수.

##### C.3 Domain Model (11개)

`domain/model/` 하위에 11개 모델 정의. API import 없음 — 순수 도메인 모델. `AppError` sealed class + `toAppError()` 확장 함수로 통일 에러 모델 제공.

---

#### D. Data Layer 상세 분석 — ✅ 95% 적합

##### D.1 DTO/Entity 분리 — ✅ 완전 분리

| 구분 | 위치 | 종류 | Domain Model과 분리 |
|------|------|------|-------------------|
| **Generated DTO** (OpenAPI) | `api/generated/model/` | `UserProfileResponse`, `WeeklyPlanResponse`, `BadgeResponse`, `GoalResponse`, `ProfileHistoryEntry`, `StatisticsResponse` 등 15개 | ✅ |
| **Manual DTO** | `data/remote/api/dto/` | `DayPlanJson`, `ExerciseJson` | ✅ |
| **ExerciseDB DTO** | `data/remote/exercisedb/` | `ExerciseDto` | ✅ |
| **Room Entity** | `data/local/entity/` | `WeeklyPlanEntity` | ✅ |
| **Domain Model** | `domain/model/` | `WeeklyPlan`, `DayPlan`, `Exercise`, `UserProfile` 등 11개 | — (기준) |

##### D.2 Mapper 함수 — ✅ 일관된 `toDomain()` 패턴

| Mapper | 위치 | 변환 | 비고 |
|--------|------|------|------|
| `WeeklyPlanResponse.toDomain()` | `WorkoutRepositoryImpl.kt:185` | Generated DTO → Domain | private extension |
| `BadgeResponse.toDomain()` | `BadgeRepositoryImpl.kt:46` | Generated DTO → Domain | `BadgeCatalog.getInfo()` 조회 포함 |
| `GoalResponse.toDomain()` | `GoalRepositoryImpl.kt:41` | Generated DTO → Domain | `Instant` parse + null safety |
| `ProfileHistoryEntry.toDomain()` | `GoalRepositoryImpl.kt:50` | Generated DTO → Domain | `BigDecimal→Float` 변환 |
| `ExerciseDto.toDomain()` | `ExerciseDto.kt:47` | OSS DTO → Domain | `ExerciseType.valueOf()` 변환 |
| `DayPlanJson.toDayPlan()` | `PlanJsonModels.kt:21` | JSON DTO → Domain | Gson 역직렬화 후 |
| `ExerciseJson.toExercise()` | `PlanJsonModels.kt:47` | JSON DTO → Domain | — |

**Gap (Minor)**: Mapper가 Repository impl 내부 private 함수 — 재사용성 낮으나 현재 단일 사용처이므로 적절. Multi-module 전환 시 별도 `mapper/` 패키지 추출 필요.

##### D.3 Repository Implementation 패턴

6개 Repository impl 모두:
- ✅ Domain Repository interface 구현
- ✅ DTO → Domain 매핑 수행
- ✅ 에러 매핑 (`HttpException` → `AppError`)
- ✅ `Result<T>` 래핑 반환

**특수 패턴**:
- `WorkoutRepositoryImpl`: Remote → Local 캐시 fallback (`WeeklyPlanDao` Room)
- `BadgeRepositoryImpl`: 인메모리 TTL 캐시 (60초)
- `AuthRepositoryImpl`: Supabase SDK 에러 → `AppError` 매핑 (`mapAuthError()`, 8개 패턴 매치)

---

#### E. DI Layer 분석 — ✅ 95% 적합

| Module | Scope | 바인딩 패턴 | 제공 | 상태 |
|--------|-------|-----------|------|------|
| `NetworkModule` | `SingletonComponent` | `@Provides` | OkHttpClient, Retrofit, 5 Generated API | ✅ |
| `SupabaseModule` | `SingletonComponent` | `@Provides` | SupabaseClient | ✅ |
| `DatabaseModule` | `SingletonComponent` | `@Provides` | Room DB, WeeklyPlanDao | ✅ |
| `RepositoryModule` | `SingletonComponent` | `@Binds` | 6 Repository interface↔impl | ✅ |
| `CoilModule` | `SingletonComponent` | `@Provides` | ImageLoader (GIF decoder) | ✅ |

**정합성**: ViewModel은 `@Inject constructor`로 interface 타입만 주입받음 (impl 직접 참조 없음). `@Binds` 패턴으로 인터페이스 바인딩.

---

#### F. Module 구조 분석 — ❌ 단일 모듈

**현재**: `:app` 모듈 1개 (`settings.gradle.kts` — `include(":app")`)

**Multi-module 권장 구조** (참고용):

```
:core:model          — Domain 모델
:core:data           — Repository impl + DTO + Mapper
:core:domain         — UseCase + Repository interface
:core:network        — Retrofit + OkHttp + Interceptors
:core:database       — Room
:core:ui             — 공통 Composable (ErrorContent, SkeletonUi 등)
:feature:home        — HomeScreen + HomeViewModel
:feature:auth        — Login/Signup/ForgotPassword + AuthViewModel
:feature:profile     — ProfileScreen + ProfileViewModel
:feature:workout     — WorkoutDetailScreen + WorkoutDetailViewModel
:feature:history     — HistoryScreen + HistoryViewModel
:feature:statistics  — StatisticsScreen + StatisticsViewModel
:feature:goal        — GoalScreen + GoalViewModel
:feature:badge       — BadgeScreen + BadgeViewModel
:feature:onboarding  — OnboardingScreen + OnboardingViewModel
:app                 — Application class + Navigation + DI wiring
```

**현 시점 판단**: v0.1.x 단계에서 단일 모듈은 합리적. 패키지 구조가 이미 계층별 분리되어 있어 Multi-module 전환 시 패키지 → 모듈 승격이 비교적 용이. **빌드 시간이 병목이 되거나 feature 팀 분리가 필요한 시점**에 전환 권장.

---

#### G. MVI 전환 Gap 요약

| # | Gap | 현재 | MVI 표준 | 심각도 | 영향 범위 |
|---|-----|------|---------|--------|----------|
| **G1** | Intent/Action sealed class 부재 | 직접 메서드 호출 (40+개소) | `sealed interface ScreenIntent` → `dispatch()` | **HIGH** | 전 Screen (11개) |
| **G2** | 다중 StateFlow 노출 | AuthVM 8개, ProfileVM 5개 | 화면당 단일 `UiState` data class | **HIGH** | AuthVM, ProfileVM, OnboardingVM, BadgeVM, WorkoutDetailVM |
| **G3** | SideEffect Channel/SharedFlow 부재 | LaunchedEffect + StateFlow 감시 | `Channel<SideEffect>` | **HIGH** | Navigation·Snackbar 처리 7개소 |
| **G4** | `collectAsState()` 전면 사용 | 30개 지점 | `collectAsStateWithLifecycle()` | **MEDIUM** | 전 Screen |
| **G5** | UseCase 커버리지 부족 | 3개 (Home only) | ViewModel↔Repository 사이 UseCase 캡슐화 | **LOW** | Profile, Auth, Goal, Badge, Onboarding |
| **G6** | Multi-module 미적용 | 단일 `:app` | Feature 모듈 분리 | **LOW** | 전체 (빌드 시간 병목 시) |

---

#### H. 바텀업 마이그레이션 로드맵

##### Phase 1 — Foundation (공통 인프라) `P0`
1. **MVI Base 클래스 도입**: `BaseViewModel<UiState, Intent, SideEffect>` 추상 클래스 작성
   - `_uiState: MutableStateFlow<S>`, `val uiState: StateFlow<S>`
   - `_sideEffect: Channel<E>`, `val sideEffect: Flow<E>`
   - `abstract fun handleIntent(intent: I)`
   - `fun dispatch(intent: I)` public entry point
2. **`collectAsState()` → `collectAsStateWithLifecycle()` 일괄 전환** (기계적 치환, 의존성 추가: `lifecycle-runtime-compose`)
3. **UiState 통합 템플릿**: 화면별 단일 `data class ScreenUiState(...)` + `sealed interface ScreenSideEffect` + `sealed interface ScreenIntent`

##### Phase 2 — Simple Screen 마이그레이션 (검증) `P1`
**대상**: 가장 단순한 Screen부터 (상태 2개 ViewModel)
1. `StatisticsViewModel` → MVI (sealed UiState 이미 존재, error 통합 + Intent 추가)
2. `WorkoutDetailViewModel` → MVI (exercise + error → 단일 UiState)
3. `BadgeViewModel` → MVI (badges + error → 단일 UiState)

##### Phase 3 — Medium Screen 마이그레이션 `P1`
1. `HistoryViewModel` → MVI (이미 단일 UiState, error 통합 + Intent + SideEffect)
2. `GoalViewModel` → MVI (이미 단일 UiState, 동일)
3. `OnboardingViewModel` → MVI (saved/isLoading/error → 단일 UiState)

##### Phase 4 — Complex Screen 마이그레이션 `P2`
1. `HomeViewModel` → MVI (3 StateFlow → 단일 UiState + theme를 UiState 필드로)
2. `ProfileViewModel` → MVI (5 StateFlow → 단일 UiState + delete/save side effect 분리)

##### Phase 5 — Auth 대규모 리팩터 `P2`
1. `AuthViewModel` → MVI (8 StateFlow → 화면별 UiState 분리 or 단일 AuthUiState)
   - 옵션 A: 3 Auth Screen이 공유하는 단일 AuthViewModel 유지 + 단일 통합 UiState
   - 옵션 B: LoginViewModel / SignupViewModel / ForgotPasswordViewModel 분리 (각각 단일 UiState)
   - **권장**: 옵션 B — 관심사 분리 + UiState 단순화

##### Phase 6 — UseCase 확장 (선택) `P3`
- Profile, Goal, Badge 등 Repository 직접 호출 → UseCase 캡슐화
- 비즈니스 로직이 단순한 CRUD는 UseCase 생략 가능 (over-engineering 방지)

##### Phase 7 — Multi-module (조건부) `P3`
- 빌드 시간 > 60초 또는 팀 규모 확대 시 착수
- 패키지 → 모듈 승격 (기존 패키지 구조가 이미 계층별 분리)

---

#### I. MVI 전환 예시 — HomeViewModel (Before/After)

**Before** (현재):
```kotlin
// HomeViewModel.kt — 3 StateFlow + 직접 메서드
sealed class HomeUiState { ... }
class HomeViewModel {
    val uiState: StateFlow<HomeUiState>   // 1
    val error: StateFlow<AppError?>       // 2
    val themeMode: StateFlow<ThemeMode>   // 3
    fun loadPlan() { ... }
    fun toggleDayCompletion(date: LocalDate) { ... }
    fun cycleTheme() { ... }
    fun clearError() { ... }
}
// HomeScreen.kt
val uiState by viewModel.uiState.collectAsState()
val error by viewModel.error.collectAsState()
val themeMode by viewModel.themeMode.collectAsState()
viewModel.loadPlan()
viewModel.toggleDayCompletion(day.date)
```

**After** (MVI):
```kotlin
// HomeContract.kt
@Immutable
data class HomeUiState(
    val plan: WeeklyPlan? = null,
    val isLoading: Boolean = true,
    val hasHealthPermission: Boolean = false,
    val completedCount: Int = 0,
    val totalWorkoutDays: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val error: AppError? = null,
) {
    val completionRate: Float get() = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f
    val isEmpty: Boolean get() = plan == null && !isLoading
}

sealed interface HomeIntent {
    data object LoadPlan : HomeIntent
    data class ToggleDayCompletion(val date: LocalDate) : HomeIntent
    data object CycleTheme : HomeIntent
    data object DismissError : HomeIntent
    data object RequestHealthPermissions : HomeIntent
}

sealed interface HomeSideEffect {
    data class ShowError(val message: String) : HomeSideEffect
}

// HomeViewModel.kt — 단일 UiState + Intent dispatch
class HomeViewModel : BaseViewModel<HomeUiState, HomeIntent, HomeSideEffect>(HomeUiState()) {
    override fun handleIntent(intent: HomeIntent) = when (intent) {
        HomeIntent.LoadPlan -> loadPlan()
        is HomeIntent.ToggleDayCompletion -> toggleDay(intent.date)
        HomeIntent.CycleTheme -> cycleTheme()
        HomeIntent.DismissError -> updateState { copy(error = null) }
        HomeIntent.RequestHealthPermissions -> { /* delegate */ }
    }
}

// HomeScreen.kt — 단일 collect + dispatch
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
LaunchedEffect(Unit) {
    viewModel.sideEffect.collect { effect -> ... }
}
viewModel.dispatch(HomeIntent.LoadPlan)
viewModel.dispatch(HomeIntent.ToggleDayCompletion(day.date))
```

---

#### J. 현재 잘 되어 있는 부분 (유지 사항)

1. **Domain 계층 독립성**: domain/ 패키지에 API/Framework import 없음 — 순수 Kotlin
2. **Repository interface/impl 분리**: 의존성 역전 원칙 100% 준수
3. **DTO → Domain Mapper 일관성**: `toDomain()` 확장 함수 패턴 통일
4. **Hilt @Binds 바인딩**: Repository 바인딩에 `@Binds` 사용 (best practice)
5. **UseCase operator invoke()**: 3개 UseCase 모두 `suspend operator fun invoke()` 패턴
6. **통일 에러 모델**: `AppError` sealed class + `toAppError()` + `reportToSentry()`
7. **Room Entity 분리**: `WeeklyPlanEntity` ≠ `WeeklyPlan` — 독립적 캐시 레이어
8. **OpenAPI Generated API 활용**: Backend 계약 자동 동기화 (`sync-openapi.sh`)

### 2026-06-04 — Compose 퍼포먼스 공식 문서 기반 성능 점검

- **Why**: [Jetpack Compose Performance](https://developer.android.com/develop/ui/compose/performance) 공식 문서 (Best Practices + Stability 하위 문서 포함) 의 체크리스트를 기준으로 프로젝트 전체 Compose 코드의 성능 패턴 적합성 감사. 코드 변경 없는 분석 세션.
- **참조 문서**: `developer.android.com/develop/ui/compose/performance/bestpractices` (remember · LazyLayout keys · derivedStateOf · state deferral · backwards writes), `developer.android.com/develop/ui/compose/performance/stability` (@Immutable/@Stable · strong skipping · List 불안정성 · 진단)
- **대상**: `ui/` 전체 (9 Screen + 6 Component + 9 ViewModel) + `domain/model/` + build config (R8, baseline profile)

---

#### 1. remember로 비용 높은 계산 캐싱 — ✅ GOOD

| 위치 | 패턴 | 상태 |
|------|------|------|
| `HistoryScreen.kt:47` | `DateTimeFormatter` top-level 싱글톤 | ✅ 매 recomposition마다 ofPattern 호출 방지 |
| `StatisticsScreen.kt:50` | `WEEK_FORMATTER` 동일 패턴 | ✅ |
| `ProfileSlider.kt:45-48` | `remember(decimals)`, `remember(value, formatPattern)` | ✅ 포맷 패턴·결과 캐싱 |
| `StatisticsScreen.kt:165` | `remember { CartesianChartModelProducer() }` | ✅ Vico producer 인스턴스 캐싱 |
| `GoalScreen.kt:194` | 동일 | ✅ |
| `HistoryScreen.kt:60` | `remember { derivedStateOf { ... } }` | ✅ 무한 스크롤 조건 캐싱 |

**Gap (Low)**: `StatisticsScreen.kt:166-167`과 `GoalScreen.kt:195`에서 `stats.weeklyRates.map { ... }` / `points.map(selector)` 가 `remember` 없이 composition scope에서 실행. 데이터 크기가 작아 (최대 12~50개) 실질 영향 미미하나, 공식 가이드의 "sort/filter를 remember로 감싸라" 원칙과 불일치.

```kotlin
// 현재 (StatisticsScreen.kt:166-167)
val yValues = stats.weeklyRates.map { (it.completionRate * 100).toDouble() }
val labels = stats.weeklyRates.map { it.weekStart.format(WEEK_FORMATTER) }

// 권장
val yValues = remember(stats) { stats.weeklyRates.map { (it.completionRate * 100).toDouble() } }
val labels = remember(stats) { stats.weeklyRates.map { it.weekStart.format(WEEK_FORMATTER) } }
```

---

#### 2. LazyLayout 안정 키 제공 — ✅ EXCELLENT

| LazyColumn | key 파라미터 | 안정성 |
|------------|-------------|--------|
| `HomeScreen.kt:127` | `key = { it.date.toString() }` | ✅ LocalDate 기반 고유 |
| `HistoryScreen.kt:110` | `key = { it.id }` | ✅ UUID 기반 고유 |
| `BadgeScreen.kt:76` | `key = { it.key }` | ✅ enum key 기반 고유 |

인덱스 기반 key 없음. 리스트 재정렬 시 불필요한 recomposition 없이 아이템 이동 인식 가능.

---

#### 3. derivedStateOf로 recomposition 제한 — ✅ BEST PRACTICE

`HistoryScreen.kt:60-65`:
```kotlin
val shouldLoadMore by remember {
    derivedStateOf {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisible >= listState.layoutInfo.totalItemsCount - 3
    }
}
```
공식 문서의 "scroll position → boolean" 변환 패턴과 정확히 일치. `LazyListState`가 매 프레임 변경되더라도 boolean이 바뀔 때만 `LaunchedEffect(shouldLoadMore)` 트리거.

---

#### 4. State 읽기 지연 (Deferral) — ✅ GOOD

**collectAsState() 패턴**: 모든 Screen에서 composition 최상단에서 `val uiState by viewModel.uiState.collectAsState()` 호출. ViewModel → StateFlow → collectAsState 단방향 흐름 일관.

**Lambda progress 패턴**:
- `HomeScreen.kt:203-204`: `LinearProgressIndicator(progress = { completionRate }, ...)` — progress를 lambda로 전달하여 composition phase skip 가능. ✅
- `HistoryScreen.kt:143-144`: `LinearProgressIndicator(progress = { rate }, ...)` — 동일. ✅

**Lambda modifier 패턴**: `Modifier.offset { }`, `Modifier.drawBehind { }` 등 자주 변경되는 값의 phase deferral 대상 없음 — 현재 애니메이션이 `animateColorAsState`/`animateContentSize` 등 Compose 내장 API로 처리되어 별도 lambda modifier 불필요.

---

#### 5. Backwards Write (역방향 쓰기) — ✅ 위반 없음

Composition body에서 state 쓰기 0건. 모든 state 변경은:
- 이벤트 핸들러 (`onClick`, `onValueChange`)
- `LaunchedEffect` 내부
- ViewModel coroutine (`viewModelScope.launch`)

에서만 발생. 공식 가이드의 "Never write to state in the composition body" 원칙 준수.

---

#### 6. 타입 안정성 (Stability) — ⚠️ MIXED

##### 6.1 @Immutable 적용 현황

| 타입 | 위치 | @Immutable | List<T> 포함 | 영향 |
|------|------|-----------|-------------|------|
| `HomeUiState` (3 branch) | `HomeViewModel.kt:28-42` | ✅ | Success.plan: `WeeklyPlan` | WeeklyPlan 자체가 unstable |
| `GoalUiState` | `GoalViewModel.kt:20-26` | ✅ | goals, history | @Immutable이 override |
| `StatisticsUiState` (3 branch) | `StatisticsViewModel.kt:18-24` | ✅ | Loaded.data: `Statistics` | Statistics에 @Immutable ✅ |
| `HistoryUiState` | `HistoryViewModel.kt:18` | ✅ | plans: `List<WeeklyPlan>` | @Immutable이 override |
| `BadgeDisplayItem` | `BadgeViewModel.kt:18` | ✅ | — | — |
| Auth states (6종) | `AuthViewModel.kt:21-44` | ✅ | — | — |
| `Goal` | `Goal.kt:16` | ✅ | — | — |
| `ProfileHistoryPoint` | `Goal.kt:23` | ✅ | — | — |
| `Statistics` | `Statistics.kt:12` | ✅ | weeklyRates: `List<WeeklyRate>` | @Immutable이 override |
| `WeeklyRate` | `Statistics.kt:6` | ✅ | — | — |
| **`ProfileUiState`** | `ProfileViewModel.kt:18-22` | ❌ | — | **Loaded(UserProfile) skipping 불가** |
| **`SaveState`** | `ProfileViewModel.kt:24-27` | ❌ | — | **skip 불가 (sealed class)** |
| **`DeleteState`** | `ProfileViewModel.kt:29-33` | ❌ | — | **skip 불가** |
| **`WeeklyPlan`** | `WeeklyPlan.kt:5-10` | ❌ | `days: List<DayPlan>` | **HomeScreen LazyColumn 영향** |
| **`DayPlan`** | `WeeklyPlan.kt:12-17` | ❌ | `exercises: List<Exercise>` | **DayPlanCard 영향** |
| **`Exercise`** | `Exercise.kt:3-13` | ❌ | `instructions: List<String>` | **WorkoutDetailScreen 영향** |

##### 6.2 핵심 영향 분석

공식 문서: "Compose always considers collection classes (List, Set, Map) unstable." → `List<T>` 프로퍼티를 가진 data class는 @Immutable 없이는 unstable.

**Strong Skipping Mode** (Kotlin 2.0+ / Compose Compiler 2.0+ 기본 활성):
- Unstable 파라미터도 `===` (참조 동일성) 비교로 skip 가능
- 하지만 data class `copy()`로 새 인스턴스 생성 시 `===` 실패 → recompose
- @Immutable 적용 시 `equals()` 비교 → 같은 데이터면 skip

**실질 영향**:
- `WeeklyPlan` / `DayPlan`: `HomeViewModel.toggleDayCompletion()`의 optimistic update에서 `plan.copy(days = updatedDays)` 실행 → 모든 DayPlanCard recompose. LazyColumn key가 완화하지만, @Immutable이면 변경 안 된 DayPlan은 `equals()` true → skip 가능.
- `ProfileUiState` / `SaveState` / `DeleteState`: ProfileScreen에서 5개 StateFlow를 collect. @Immutable 없으면 sealed branch 전환 시 모든 하위 composable recompose 가능.

##### 6.3 권장 조치

```kotlin
// WeeklyPlan.kt — @Immutable 추가
@Immutable
data class WeeklyPlan(...)

@Immutable
data class DayPlan(...)

// Exercise.kt
@Immutable
data class Exercise(...)

// ProfileViewModel.kt — sealed class branch에 @Immutable
@Immutable sealed class ProfileUiState { ... }
@Immutable sealed class SaveState { ... }
@Immutable sealed class DeleteState { ... }
```

---

#### 7. runBlocking in Composition — ⚠️ MEDIUM RISK

**발견 위치**:
- `StatisticsScreen.kt:177-182`:
  ```kotlin
  remember(stats) {
      if (yValues.isNotEmpty()) {
          runBlocking { producer.runTransaction { lineSeries { series(yValues) } } }
      }
      Unit
  }
  ```
- `GoalScreen.kt:202-207`: 동일 패턴

**문제**: `runBlocking`은 calling thread (Main) 를 차단. Vico `CartesianChartModelProducer.runTransaction`이 suspend 함수이므로 동기 호출 시 main thread blocking.

**맥락**: 코드 주석 "초기 1프레임도 데이터를 채우기 위해 동기 1회" — Vico는 빈 모델에 차트를 그리지 않아서, `LaunchedEffect`만으로는 첫 프레임에 빈 차트 flash 발생. `remember` + `runBlocking`으로 첫 composition에서 데이터를 synchronous populate.

**실질 영향**: `runTransaction`은 내부적으로 `Mutex.withLock` + model update로, 데이터 12~50건 기준 ~1-3ms. 체감 jank 없으나 공식 가이드의 "expensive calculation in composition" 안티패턴에 해당.

**개선 방향** (P3):
1. Vico 3.x API에 synchronous initialization 지원 여부 확인
2. 대안: `remember` 블록에서 `CartesianChartModelProducer.build { }` (Vico 3.x) 사용 가능 여부 조사
3. 현 시점에서는 데이터 양이 적어 실질 영향 미미 → Vico 메이저 업그레이드 시 자연 해소 가능

---

#### 8. Side Effect 정합성 — ✅ WELL STRUCTURED

| Screen | LaunchedEffect key | 목적 | 적합성 |
|--------|-------------------|------|--------|
| `ProfileScreen.kt:66` | `deleteState` | 삭제 성공 → navigate | ✅ |
| `ProfileScreen.kt:72` | `saveState` | 저장 성공 → snackbar + back | ✅ |
| `ProfileScreen.kt:83` | `error` | 에러 → snackbar | ✅ |
| `LoginScreen.kt:57` | `Unit` | pending email 초기화 | ✅ (1회성) |
| `LoginScreen.kt:66` | `formValid, lastError` | 폼 유효 → 배너 dismiss | ✅ |
| `HistoryScreen.kt:66` | `shouldLoadMore` | 무한 스크롤 트리거 | ✅ |
| `StatisticsScreen.kt:169` | `stats` | 차트 데이터 비동기 업데이트 | ✅ |
| `GoalScreen.kt:69` | `error` | 에러 → snackbar | ✅ |
| `AuthErrorBanner.kt:49` | `error, screen` | Sentry breadcrumb | ✅ (룰 8) |

SideEffect / DisposableEffect 사용 없음 — 현 아키텍처에서 cleanup이나 non-Compose side effect 불필요.

---

#### 9. Image Loading 성능 — ✅ OPTIMIZED

`WorkoutDetailScreen.kt:66-85` (유일한 이미지 로딩 지점):
- `SubcomposeAsyncImage` 사용 — loading/error slot 별도 composable 지원
- `.size(512)` — 원본 GIF 전체 다운로드 방지, 512px으로 downscale
- `.crossfade(true)` — 부드러운 전환
- Coil 기본 디스크/메모리 캐시 활성 (CoilModule.kt DI)

앱 전체에서 이미지 로딩이 WorkoutDetailScreen 1곳뿐이므로 캐시 튜닝 불필요.

---

#### 10. Build Configuration (R8 + Baseline Profile) — ✅ CORRECT

| 항목 | 설정 | 상태 |
|------|------|------|
| R8 (isMinifyEnabled) | `true` (release) | ✅ 공식 권장 |
| 리소스 축소 (isShrinkResources) | `true` (release) | ✅ |
| ProGuard rules | `proguard-rules.pro` | ✅ Gson DTO keep |
| Baseline Profile | AGP 9.2.1 auto-generated | ✅ 충분 |
| Custom BaselineProfileGenerator | 없음 | — v0.1.x에서 불필요 |

공식 문서: "Compose includes a default profile" + "Create app-specific profiles for critical journeys." 현 단계에서 auto-generated profile로 충분. 사용자 수 증가 후 cold start / 스크롤 jank 측정 필요 시 custom profile 검토.

---

#### 11. HomeScreen 이중 패딩 (Layout Phase) — 🐛 BUG (기존 발견 재확인)

```kotlin
// HomeScreen.kt:92-97 — Scaffold padding을 PullToRefreshBox에 적용
Scaffold(...) { padding ->
    PullToRefreshBox(
        modifier = Modifier.padding(padding),  // 1차 적용
    ) {
        ...
        LazyColumn(contentPadding = padding) {  // 2차 적용 → 이중 inset
```

Layout phase에서 동일 padding이 두 번 계산 → 콘텐츠가 의도한 위치보다 아래로 밀림. 성능 자체보다 layout 정확성 문제이나, 불필요한 layout 계산이 매 프레임 발생.

---

#### 12. 종합 점검표

| # | 공식 가이드 항목 | 프로젝트 상태 | 우선순위 |
|---|----------------|-------------|---------|
| 1 | remember for expensive calculations | ✅ (Gap: chart map 미감싸) | P3 |
| 2 | Lazy layout stable keys | ✅ 완벽 | — |
| 3 | derivedStateOf | ✅ 모범 사례 | — |
| 4 | State read deferral | ✅ lambda progress 포함 | — |
| 5 | No backwards writes | ✅ 위반 없음 | — |
| 6 | Type stability (@Immutable) | ⚠️ 6종 누락 | **P2** |
| 7 | runBlocking in composition | ⚠️ Vico 워크어라운드 | P3 |
| 8 | Side effects | ✅ 정합 | — |
| 9 | Image loading | ✅ 최적화 | — |
| 10 | R8 + Baseline Profile | ✅ 정상 | — |
| 11 | Layout correctness | 🐛 이중 패딩 | **P1** |
| 12 | Strong Skipping | ✅ 기본 활성 (Kotlin 2.2.10) | — |

---

#### 13. 개선 로드맵

**P1 — 즉시 (다음 PR)**:
1. HomeScreen 이중 패딩 수정: `LazyColumn(contentPadding = padding)` 제거 또는 `PullToRefreshBox`의 padding 제거

**P2 — 단기 (v0.2.x)**:
2. `WeeklyPlan`, `DayPlan`, `Exercise`에 `@Immutable` 추가 — HomeScreen LazyColumn skip 최적화
3. `ProfileUiState`, `SaveState`, `DeleteState`에 `@Immutable` 추가

**P3 — 관찰 (Vico 업그레이드 시)**:
4. chart composable의 `map` 연산을 `remember(stats)` 로 감싸기
5. `runBlocking` → Vico 3.x synchronous init API 전환 검토

**불필요 (현 시점)**:
- Custom BaselineProfileGenerator (사용자 수 < 100)
- kotlinx-collections-immutable 도입 (@Immutable annotation으로 충분)
- Modifier.drawBehind / graphicsLayer lambda (해당 패턴 미사용)

### 2026-06-04 — 프론트엔드 빌드 환경 및 의존성 현대화 검토

- **Why**: v0.1.7 시점 Android 빌드 툴체인 · Gradle 설정 · CI/CD · 코드 품질 도구 · 의존성 관리 패턴 전반의 현대화 수준 점검. 코드 변경 없는 분석 세션.
- **대상 파일**: `build.gradle.kts` (root + app), `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle-wrapper.properties`, `proguard-rules.pro`, `config/detekt/detekt.yml`, `.githooks/pre-commit`, `.github/workflows/android.yml`, `.github/dependabot.yml`

---

#### A. 빌드 툴체인 현황

| 항목 | 현재 | 최신 Stable | 상태 |
|------|------|------------|------|
| Gradle | 9.5.1 | 9.5.1 | ✅ 최신 |
| AGP | 9.2.1 | 9.2.1 | ✅ 최신 |
| Kotlin | 2.2.10 | 2.4.0 | ⚠️ 보류 (Hilt 블로커) |
| KSP | 2.3.2 | 2.3.7 | ⚠️ Kotlin 연동 |
| JDK | 17 (Temurin) | 17 LTS | ✅ AGP 9.x 최소 요구 충족 |
| compileSdk | 37 | 37 | ✅ 최신 |
| targetSdk | 37 | 37 | ✅ 최신 |
| minSdk | 26 | 26 | ✅ (Android 8.0, java.time 네이티브) |

---

#### B. gradle.properties 정밀 점검 — 레거시 플래그 잔존

```properties
# gradle.properties 현재 내용
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.builtInKotlin=false          # ← 점검 대상
android.newDsl=false                 # ← 점검 대상
android.defaults.buildfeatures.resvalues=true
android.enableAppCompileTimeRClass=false
android.usesSdkInManifest.disallowed=false
android.uniquePackageNames=false
android.dependency.useConstraints=true
android.r8.strictFullModeForKeepRules=false
android.r8.optimizedResourceShrinking=false
```

**검출 사항 5건:**

| # | 플래그 | 문제 | 권장 |
|---|--------|------|------|
| B1 | `android.builtInKotlin=false` | 주석에 "Hilt 2.56.2가 새 DSL 미지원"이라 적혀있으나 현재 Hilt **2.59.2** (AGP 9 공식 지원). 플래그가 stale할 가능성 | Hilt 2.59+ AGP 9 호환 확인 후 제거 검토. 제거 시 `./gradlew clean :app:kspDebugKotlin` 로 검증 |
| B2 | `android.newDsl=false` | 동일 맥락의 AGP 9 호환성 플래그. Hilt 2.59+에서 불필요할 수 있음 | B1과 함께 검증 |
| B3 | `org.gradle.jvmargs=-Xmx2048m` | 프로젝트 규모(단일 :app 모듈) 대비 충분하나, Kotlin 2.2 + KSP + OpenAPI generator 동시 실행 시 여유 부족 가능 | 빌드 시간 이슈 발생 시 `-Xmx4096m` 상향 고려 |
| B4 | `android.r8.strictFullModeForKeepRules=false` | R8 strict mode 비활성. AGP 9.x 기본값이 false이므로 명시 불필요 | 제거 가능 (기본값 중복) |
| B5 | `android.r8.optimizedResourceShrinking=false` | 최적화 리소스 축소 비활성. AGP 9.2에서 개선된 기능이나 안정성 우선 선택으로 보임 | 현재 유지 OK. 향후 테스트 후 true 전환하면 APK 크기 추가 감소 가능 |

---

#### C. CI/CD (android.yml) 점검

**현재 파이프라인:**
```
checkout → JDK 17 → Gradle setup → local.properties stub
→ spotlessCheck → detektDebug → testDebugUnitTest → assembleDebug
→ (PR시) upload-artifact
```

**Actions 버전:**

| Action | 현재 | 최신 | 상태 |
|--------|------|------|------|
| actions/checkout | v4 | v4 | ✅ |
| actions/setup-java | v5 | v5 | ✅ |
| gradle/actions/setup-gradle | v6 | v6 | ✅ |
| actions/upload-artifact | v7 | v7 | ✅ |

**검출 사항 3건:**

| # | 항목 | 설명 | 영향 |
|---|------|------|------|
| C1 | Release 빌드 검증 미포함 | CI가 `assembleDebug`만 수행. R8/ProGuard 문제는 release 빌드에서만 발견됨 (`isMinifyEnabled = true`). `assembleRelease`는 서명 키 필요 → CI에서 별도 처리 필요 | 중간 — R8 회귀 미감지 |
| C2 | OpenAPI generator drift 미검증 | Backend CI(`backend.yml`)에 drift detection이 있으나, Android CI에서 `openApiGenerate` 결과의 컴파일 통과 여부를 명시적으로 확인하지 않음 (assembleDebug가 간접 커버하지만 명시성 부족) | 낮음 |
| C3 | Gradle 캐시 최적화 | `setup-gradle@v6`이 자동 캐싱하지만, `.gradle/caches/` 크기를 모니터링하는 step 없음. 캐시 비대화 시 CI 속도 저하 | 낮음 |

---

#### D. Dependabot 설정 점검

**검출 사항 2건:**

| # | 항목 | 설명 | 권장 |
|---|------|------|------|
| D1 | **Backend Ktor entry 잔존** | `directory: /backend`, `package-ecosystem: gradle`, `labels: [backend-ktor]`. 주석에 "마이그레이션 완료 시점에 삭제 권장"이라 적혀있고, FastAPI 전환은 이미 완료됨 (CLAUDE.md 확인). backend/ 에 Gradle 프로젝트 없으므로 dependabot이 매주 빈 스캔 수행 중 | 해당 entry 삭제 |
| D2 | **Kotlin 2.3 보류에 대한 ignore 미설정** | Kotlin 2.3+ PR이 dependabot에서 자동 생성되면 close → 재생성 반복 가능. `dependency-deferred.md`에 "같은 major 버전에 대해 dependabot이 다시 PR을 만들지 않는다"고 적혀있으나, 2.4.0 등 새 minor가 나오면 새 PR 생성됨 | 의도적 대기라면 현행 유지 OK. noise 줄이려면 `ignore` 규칙 추가 |

---

#### E. R8 / ProGuard 점검

```pro
# proguard-rules.pro (app/)
-keepattributes Signature, *Annotation*
-keep class com.gunnys.eundunhealth.data.remote.api.dto.** { *; }
-keep class com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDto { *; }
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
```

**평가**: 최소한의 규칙만 선언, 라이브러리 자체 consumer-rules에 위임. 건강한 패턴 ✅.

**검출 사항 1건:**

| # | 항목 | 설명 |
|---|------|------|
| E1 | OpenAPI generated DTO keep 규칙 미포함 | `app/build/generated/openapi/` 의 generated model class들이 Gson deserialization 대상이 될 경우 별도 `-keep` 필요. 현재는 side-by-side 단계(generated 미사용)라 무해하나, Phase 5 전환 시 추가 필요 |

---

#### F. 코드 품질 도구 점검

| 도구 | 버전 | 설정 | 평가 |
|------|------|------|------|
| **Detekt** 1.23.7 | `config/detekt/detekt.yml` | Compose-aware (LongMethod/LongParameterList/FunctionNaming ignoreAnnotated). baseline 접근법으로 generated code 처리 | ✅ 우수 |
| **Spotless** 8.5.1 + **ktlint** 1.5.0 | `app/build.gradle.kts` | wildcard-imports/function-naming/property-naming disable (Compose 호환). `src/**/*.kt` + `*.kts` 대상 | ✅ 우수 |
| **pre-commit hook** | `.githooks/pre-commit` | Kotlin → spotlessApply + detektDebug, docs/plans → gen-plans-index, backend → ruff check. 분기별 독립 동작 | ✅ 우수 |

**검출 사항 1건:**

| # | 항목 | 설명 |
|---|------|------|
| F1 | Detekt baseline 이원화 | `baseline.xml` (git tracked) + `baseline-debug.xml` (별도). `baseline.xml`은 `detekt` 명령용, `baseline-debug.xml`은 `detektDebug` (AGP variant task)용. 이 이원화가 PR #52 vico 마이그레이션 시 CI 연속 실패의 원인이었음 (android.md §2026-05-29 entry 참조). 근본 fix 미완 |

---

#### G. 의존성 관리 패턴 점검

**Version Catalog (`libs.versions.toml`):**
- 27개 의존성 모두 version catalog로 중앙 관리 ✅
- BOM 사용: Compose BOM으로 UI 라이브러리 버전 통합 ✅
- 테스트 의존성 3개도 catalog에 포함 ✅

**검출 사항 2건:**

| # | 항목 | 설명 |
|---|------|------|
| G1 | 테스트 의존성 일부 inline 버전 | `junit 4.13.2`, `mockk 1.14.9`, `kotlinx-coroutines-test 1.10.2`가 `[libraries]` 섹션에서 `version = "..."` 인라인. 다른 의존성은 모두 `[versions]` 섹션 참조(`version.ref`). 일관성을 위해 `[versions]`로 추출 가능 |
| G2 | Compose BOM 외 개별 버전 pin 없음 | BOM이 관리하는 UI 라이브러리(material3, ui, ui-graphics 등)에 개별 버전 오버라이드 없음 ✅ 건강한 패턴 |

---

#### H. settings.gradle.kts 점검

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}
```

- **foojay-resolver** 0.10.0: JDK toolchain 자동 다운로드 지원 ✅
- **FAIL_ON_PROJECT_REPOS**: 서브프로젝트의 독자 repository 선언 차단 ✅ (의존성 일관성)
- 단일 모듈(`include(":app")`) 구조 ✅

---

#### I. 종합 개선 로드맵

| 우선순위 | ID | 항목 | 위험도 | 작업량 |
|---------|-----|------|--------|--------|
| **P0** | D1 | Dependabot Backend Ktor entry 삭제 | 없음 | 1줄 삭제 |
| **P1** | B1+B2 | `builtInKotlin=false` + `newDsl=false` 플래그 stale 여부 검증 + 제거 | 낮음 | 빌드 테스트 1회 |
| **P1** | C1 | CI에 release 빌드 검증 추가 (서명 없이 `./gradlew :app:assembleRelease` 또는 lint 수준) | 중간 | workflow 수정 |
| **P2** | B4 | `r8.strictFullModeForKeepRules=false` 기본값 중복 제거 | 없음 | 1줄 삭제 |
| **P2** | F1 | Detekt baseline 이원화 근본 fix (단일 source 또는 auto-sync) | 낮음 | 조사 필요 |
| **P2** | G1 | 테스트 의존성 버전을 `[versions]` 섹션으로 추출 | 없음 | 정리 작업 |
| **P3** | E1 | OpenAPI generated DTO ProGuard keep 규칙 (Phase 5 전환 시) | — | 전환 시점 |
| **P3** | B5 | `r8.optimizedResourceShrinking=true` 테스트 후 활성화 | 낮음 | APK 크기 측정 |
| **보류** | — | Kotlin 2.2→2.3/2.4 + KSP 업그레이드 | 높음 | Hilt 블로커 해소 후 |
| **선택** | — | OkHttp 4→5 + Coil 2→3 | 중간 | 별도 마이그레이션 세션 |

**빌드 환경 성숙도: 높음.** AGP 9.2 + Gradle 9.5 + compileSdk 37 최신 조합. 주요 개선점은 레거시 호환 플래그 정리(B1/B2)와 CI release 빌드 검증(C1). 의존성은 27개 중 24개 최신이며 블로커는 Kotlin/Hilt 호환성 1건.

### 2026-06-04 — 프론트엔드 의존성 LTS/Stable 마이그레이션 검토

- **Why**: v0.1.7 시점 전체 Android 프론트엔드 의존성의 최신 LTS/Stable 버전 대비 현황 파악 + 마이그레이션 위험도 분석. 코드 변경 없는 조사 세션.
- **참고**: `docs/ops/dependency-deferred.md` (Kotlin 2.3 보류 항목)

#### 현재 vs 최신 버전 비교표 (2026-06-04 기준)

| 카테고리 | 의존성 | 현재 | 최신 Stable | 차이 | 상태 |
|---------|--------|------|------------|------|------|
| **Build** | Gradle | 9.5.1 | **9.5.1** | — | ✅ 최신 |
| | AGP | 9.2.1 | **9.2.1** | — | ✅ 최신 |
| **Language** | Kotlin | 2.2.10 | **2.4.0** (2.3.20 중간) | **2 minor** | ⚠️ 블로커 |
| | KSP | 2.3.2 | **2.3.7** | patch | ⚠️ Kotlin 연동 |
| **Compose** | BOM | 2026.05.01 | **2026.05.01** | — | ✅ 최신 |
| | Navigation | 2.9.8 | 2.9.8 | — | ✅ 최신 |
| **DI** | Hilt | 2.59.2 | **2.59.2** | — | ✅ 최신 (⚠️ Kotlin 2.3 미지원) |
| | Hilt Nav Compose | 1.3.0 | 1.3.0 | — | ✅ 최신 |
| **DB** | Room | 2.8.4 | **2.8.4** (3.0 alpha) | — | ✅ 최신 (2.x) |
| | DataStore | 1.2.1 | 1.2.1 | — | ✅ 최신 |
| **Network** | Retrofit | 3.0.0 | **3.0.0** | — | ✅ 최신 |
| | OkHttp | 4.12.0 | **5.3.0** | **1 major** | 🔄 선택적 |
| **Auth** | Supabase-kt | 3.6.0 | 3.6.x | — | ✅ 최신 |
| | Ktor (client) | 3.5.0 | 3.5.0 | — | ✅ 최신 |
| **Image** | Coil | 2.7.0 | **3.4.0** | **1 major** | 🔄 선택적 |
| **Chart** | Vico | 3.1.0 | 3.1.x (KMP 계열 별도) | — | ✅ 최신 |
| **Monitoring** | Sentry | 8.42.0 | **8.42.0** | — | ✅ 최신 |
| | Sentry Gradle | 5.8.0 | 5.8.0 | — | ✅ 최신 |
| **Health** | Health Connect | 1.1.0 | 1.1.0 | — | ✅ 최신 |
| **QA** | Detekt | 1.23.7 | 1.23.7 | — | ✅ 최신 |
| | Spotless | 8.5.1 | 8.5.1 | — | ✅ 최신 |
| | ktlint | 1.5.0 | 1.5.0 | — | ✅ 최신 |
| **Test** | JUnit | 4.13.2 | 4.13.2 | — | ✅ 최신 |
| | MockK | 1.14.9 | 1.14.9 | — | ✅ 최신 |
| | Coroutines Test | 1.10.2 | 1.10.2 | — | ✅ 최신 |

**요약: 27개 의존성 중 24개 최신. 마이그레이션 대상 3개 (Kotlin, Coil, OkHttp).**

---

#### 마이그레이션 대상 상세 분석

##### 1. Kotlin 2.2.10 → 2.3.20 (또는 2.4.0) — 블로커 있음

**현황**: `dependency-deferred.md` §1에 기록된 보류 항목. Kotlin 2.3은 language release, 2.3.20은 tooling release, 2.4.0은 최신 language release(2026-06).

**Hilt 호환성 블로커**:
- Hilt 2.59.2(최신)가 사용하는 kotlin-metadata-jvm이 Kotlin 2.2.x metadata까지만 지원.
- Kotlin 2.3+ 사용 시 `Provided Metadata instance has version 2.3.0, maximum supported is 2.2.0` 빌드 에러 발생 ([google/dagger#5001](https://github.com/google/dagger/issues/5001), [#5059](https://github.com/google/dagger/issues/5059)).
- **Dagger 2.57+에서 kotlin-metadata-jvm을 unshade** 처리했으므로, 명시적으로 최신 `kotlinx-metadata-jvm`을 KSP 의존성에 추가하면 우회 가능할 수 있음. 단, 공식 검증 필요.

**수반 변경**:
- KSP 2.3.2 → 2.3.7 (Kotlin 2.3+ 호환 필수, KSP1은 Kotlin 2.3부터 미지원)
- Compose Compiler: Kotlin 2.0+ lockstep이므로 Kotlin 버전 = Compose Compiler 버전. 별도 검증 불필요.

**Kotlin 2.4.0 주요 신기능**: stable context parameters, explicit backing fields, UUID API, Java 26 지원, Gradle 9.5 호환.

**위험도**: 높음 (Hilt 호환성 공식 미확인, DI 전체 빌드 깨질 가능성)

**권장 경로**:
1. Hilt 2.60+ 출시 대기 (Kotlin 2.3 metadata 공식 지원) — 가장 안전
2. 또는 explicit kotlin-metadata-jvm override 실험 → worktree에서 검증 → 성공 시 PR

---

##### 2. Coil 2.7.0 → 3.4.0 — 선택적, 중간 위험

**주요 변경점**:
- Maven 좌표 변경: `io.coil-kt` → `io.coil-kt.coil3` (모든 import 및 dependency 수정)
- `coil-base` → `coil-core`, `coil-compose-base` → `coil-compose-core` 리네이밍
- **네트워크 로딩 기본 미포함**: `coil-network-okhttp` 또는 `coil-network-ktor` 명시 추가 필요
- Compose API (`AsyncImage`, `SubcomposeAsyncImage`, `rememberAsyncImagePainter`)는 대부분 동일
- Compose Multiplatform 지원 + restartable/skippable 최적화

**영향 범위**: `WorkoutDetailScreen.kt` (SubcomposeAsyncImage 1곳) + `build.gradle.kts` 의존성 2줄 + DI `CoilModule`

**위험도**: 중간 (좌표 변경은 기계적이나 네트워크 artifact 추가 누락 시 런타임 크래시)

**실익**: Compose 성능 최적화 (restartable/skippable), KMP 준비, 활발한 유지보수 (2.x는 maintenance mode 진입 예상)

---

##### 3. OkHttp 4.12.0 → 5.3.0 — 선택적, 낮은 위험

**주요 변경점**:
- OkHttp 4 → 5는 **바이너리 호환** (동일 `okhttp3` 패키지, drop-in replacement)
- 최소 Java 11 필요 (프로젝트 Java 17 ✅)
- Retrofit 3.0.0이 내부적으로 OkHttp 4.12.0을 사용하지만, OkHttp 5로 독립 업그레이드 가능 (바이너리 호환)

**영향 범위**: `libs.versions.toml` 버전만 변경, 코드 수정 없음 예상

**위험도**: 낮음 (바이너리 호환 명시)

**실익**: 성능 개선, 최신 TLS/HTTP 지원

---

#### 마이그레이션 불필요 항목 (참고)

| 의존성 | 사유 |
|--------|------|
| Room 3.0 | alpha 단계, KMP 초점. 현재 2.8.4가 Android 전용 최신 stable |
| Kotlin 2.4.0 직행 | 2.3 건너뛰기는 가능하나 Hilt 블로커 동일. 단계적 2.3 → 2.4 권장 |
| Vico KMP | 현재 `compose-m3` 3.1.0이 안정. KMP 전환은 프로젝트가 KMP 채택 시 |

---

#### 권장 마이그레이션 로드맵 (우선순위)

| 순서 | 작업 | 위험도 | 선행 조건 |
|------|------|--------|----------|
| 1 | OkHttp 4.12.0 → 5.3.0 | 낮음 | 없음 |
| 2 | Coil 2.7.0 → 3.4.0 | 중간 | OkHttp 5 선행 권장 (Coil 3의 network-okhttp가 OkHttp 5 기반) |
| 3 | Kotlin 2.2.10 → 2.3.20 + KSP 2.3.7 | 높음 | Hilt 호환 확인 (2.60+ 출시 대기 또는 metadata-jvm 우회 검증) |
| 4 | Kotlin 2.3.20 → 2.4.0 (후속) | 중간 | Step 3 안정화 후 |

**Step 1+2는 즉시 착수 가능. Step 3은 Hilt 블로커 해소 후.**

---

#### Sources
- [Kotlin 2.4.0 Released](https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/)
- [Kotlin 2.3.20 Released](https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/)
- [Dagger Releases](https://github.com/google/dagger/releases) — 2.59.2 최신 (2026-02-20)
- [Dagger #5001 — Kotlin 2.3 metadata support](https://github.com/google/dagger/issues/5001)
- [Dagger #5059 — kotlin-metadata-jvm 2.3.0 support](https://github.com/google/dagger/issues/5059)
- [KSP Releases](https://github.com/google/ksp/releases) — 2.3.7 최신
- [Coil 3 Upgrade Guide](https://coil-kt.github.io/coil/upgrading_to_coil3/)
- [OkHttp Changelog](https://square.github.io/okhttp/changelogs/changelog/) — 5.3.0 최신
- [AGP 9.2.0 Release Notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom) — 2026.05.01
- [Room 3.0 Alpha](https://developer.android.com/jetpack/androidx/releases/room3)
- [Gradle Releases](https://gradle.org/releases/) — 9.5.1 최신

### 2026-06-04 — 프론트엔드 전수 분석 (UI 구조 · 디자인 시스템 · a11y · 내비게이션 · 성능)

- **Why**: v0.1.7 시점 프론트엔드 전체 현황 파악 및 개선점 도출. 코드 변경 없는 분석 세션.
- **범위**: ui/ 디렉토리 전체 32개 파일 (10 Screen + 6 shared component + 15+ private composable + theme 3 + navigation 2)
- **참고 문서**: [Compose Layouts](https://developer.android.com/develop/ui/compose/layouts), [Compose Architecture](https://developer.android.com/develop/ui/compose/architecture)

---

#### A. UI 컴포넌트 구조 분석

**Screen 목록 (10개):**

| Screen | 파일 | ViewModel | shared 컴포넌트 사용 |
|--------|------|-----------|---------------------|
| SplashScreen | ui/splash/ | 없음 | 없음 |
| LoginScreen | ui/auth/ | AuthViewModel | AuthErrorBanner ×2 |
| SignupScreen | ui/auth/ | AuthViewModel | AuthErrorBanner ×2 |
| ForgotPasswordScreen | ui/auth/ | AuthViewModel | AuthErrorBanner ×1 |
| OnboardingScreen | ui/onboarding/ | OnboardingViewModel | ProfileSlider ×4, ProfileSummaryCard |
| HomeScreen | ui/home/ | HomeViewModel | SkeletonHomeContent, ErrorContent |
| WorkoutDetailScreen | ui/workout/ | WorkoutDetailViewModel | 없음 (Coil SubcomposeAsyncImage) |
| ProfileScreen | ui/profile/ | ProfileViewModel | ProfileSlider ×4, ProfileSummaryCard, ErrorContent, EmptyContent |
| BadgeScreen | ui/badge/ | BadgeViewModel | ErrorContent, EmptyContent |
| HistoryScreen | ui/history/ | HistoryViewModel | ErrorContent, EmptyContent |
| StatisticsScreen | ui/statistics/ | StatisticsViewModel | ErrorContent, EmptyContent |
| GoalScreen | ui/goal/ | GoalViewModel | 없음 (Vico chart) |

**Shared 컴포넌트 (6개, ui/components/):**

| 컴포넌트 | 재사용 횟수 | 주요 기능 |
|----------|-----------|----------|
| AuthErrorBanner | 5회 (Auth 3화면) | inline + persistent + a11y liveRegion + Sentry breadcrumb (룰 8) |
| ErrorContent | 5회 | full-screen 에러 + 선택적 retry 버튼 |
| EmptyContent | 4회 | full-screen 빈 상태 + 선택적 action 버튼 |
| ProfileSlider | 8회 (Onboarding + Profile) | label + 양방향 TextField↔Slider + 범위 검증 |
| ProfileSummaryCard | 2회 | 신체 정보 요약 카드, title 커스터마이즈 가능 |
| SkeletonUi (3 export) | 1회 (HomeScreen) | ShimmerBox + SkeletonDayCard + SkeletonHomeContent |

**일관성 감사 결과:**

| 패턴 | 일관성 | 비고 |
|------|--------|------|
| Error 처리 | 92% | Auth = AuthErrorBanner, 비Auth = ErrorContent + Snackbar. GoalScreen만 Snackbar 단독 |
| Loading 상태 | 100% | Skeleton(복잡 카드) → 중앙 CircularProgress(전체화면) → 인라인(버튼) |
| Empty 상태 | 95% | EmptyContent 일관 사용. GoalScreen만 인라인 텍스트 |
| Card 색상 | 100% | primaryContainer=강조, surfaceVariant=보조, surface=목록 |
| Form 검증 | 98% | OutlinedTextField isError + supportingText. GoalScreen만 supportingText 누락 |
| 네비게이션 콜백 | 100% | lambda 기반, Screen 객체 직접 전달 없음 |

**@Preview 함수: 0개** — IDE 프리뷰 불가, 실기기/에뮬레이터 필수.

---

#### B. 디자인 시스템 분석

**테마 구조 (ui/theme/):**
- `Color.kt` — Light 12토큰 + Dark 12토큰 (M3 green health 테마, primary=#006D3C)
- `Type.kt` — 7개 M3 typography 스타일 (headlineLarge 32sp ~ labelLarge 14sp)
- `Theme.kt` — `EundunHealthTheme` composable, Dynamic Color(Android 12+) + Light/Dark fallback

**위반 검사 결과:**
- 하드코딩 Color(0x...) — **0건** (Color.kt 외 발견 없음)
- 하드코딩 fontSize — **0건** (모든 텍스트 MaterialTheme.typography 사용)
- Shape 시스템 — M3 기본값 사용, 커스텀 Shape.kt 없음 (v0.1 MVP 적합)
- 아이콘 — Material Icons 단독 사용 (Default Filled + Outlined + AutoMirrored), 외부 아이콘 없음

**Spacing 체계:**
- 4-8dp 증분 스케일 일관 사용 (4/6/8/12/16/24/32/48dp)
- 비표준 값(7dp, 13dp 등) 없음
- 별도 Dimen.kt 없이 인라인 dp — Compose 관용적 패턴

**Dynamic Color + Dark Mode:**
- Android 12+ Material You 지원 ✅
- 모든 화면 dark mode 호환 ✅ (theme color 전용 사용)
- 테마 전환: SYSTEM → DARK → LIGHT 순환 (HomeScreen 아이콘)

---

#### C. 접근성(a11y) 분석

| 카테고리 | 준수도 | 세부 |
|----------|--------|------|
| contentDescription | 80% | 기능 아이콘 = 한국어 설명 ✅, 장식 아이콘 = null ✅ |
| semantics / liveRegion | **5%** | AuthErrorBanner만 liveRegion 구현. 나머지 동적 UI 미적용 |
| heading() 시맨틱 | **0%** | 모든 화면 제목/섹션 헤딩에 heading() 마크업 없음 |
| Touch target | 85% | IconButton 48dp ✅. 일부 TextButton 44dp 미달 가능 |
| 색상 대비 | 100% | 모든 텍스트 on* 컬러 롤 사용 |
| 텍스트 스케일링 | 90% | M3 typography 시스템 + 디바이스 설정 반영. 일부 동적 텍스트 overflow 리스크 |
| Focus 관리 | 10% | ProfileSlider IME Done만 구현. form 필드 간 focus chain 없음 |
| testTag | **0%** | 전체 코드베이스에 testTag 없음 |

**Critical 개선 사항:**
1. **heading() 시맨틱 추가 (0% → 목표 80%)**: 스크린 리더가 제목/본문 구분 불가. LoginScreen "은둔헬스", OnboardingScreen "신체 정보 입력", StatisticsScreen 섹션 제목 등 8+ 곳.
2. **liveRegion 확대**: 로딩 상태 변경, form 검증 에러, 목록 갱신 시 TalkBack 알림 필요.
3. **testTag 도입**: UI 테스트(Espresso/Compose Testing) 기반 마련.

---

#### D. 내비게이션 아키텍처 분석

**Graph 구조:**
- 11개 composable destination, 1개 동적 인자 (WorkoutDetail.exerciseId)
- 진입점: Splash → (SessionState 분기) → Login or Home
- Auth 상태 변경 시 `popUpTo(0) { inclusive = true }` 로 전체 back stack 정리 ✅

**Deep Link 안전장치 (MainActivity.kt):**
1. `consumedDeepLinkUri` — `onSaveInstanceState` 에 저장, process death 후 PKCE 코드 이중 교환 방지 ✅
2. `beginDeepLinkProcessing()` — SessionState.Unknown 유지로 Login 화면 깜빡임 방지 ✅
3. Supabase-kt 3.6.0 버그 우회 — `error_code` query param 수동 파싱 ✅

**Back Stack 안전성:** 모든 하위 화면에 `popBackStack()` 콜백. 사용자가 갇힐 경로 없음 ✅.

**ViewModel 스코핑:** `hiltViewModel()` 기본 파라미터 → NavBackStackEntry 생명주기에 자동 스코핑. 메모리 누수 없음 ✅.

---

#### E. Compose 성능 분석

**우수 패턴:**

| 패턴 | 위치 | 설명 |
|------|------|------|
| `derivedStateOf` | HistoryScreen:60-65 | 무한스크롤 감지, 불필요 recomposition 방지 |
| LazyColumn `key` | HomeScreen:127, BadgeScreen:76, HistoryScreen:110 | 모든 lazy list에 안정 고유 키 |
| `remember(deps)` | ProfileSlider:44-49 | 포맷 패턴 캐싱, 의존성 정확 |
| `animateColorAsState` label | HomeScreen:223, BadgeScreen:87 | 디버깅용 label 명시 |
| Coil size 최적화 | WorkoutDetailScreen:66-85 | GIF 512px 제한으로 OOM 방지 |
| Modifier 체인 순서 | OnboardingScreen:62-70, ProfileScreen:195-200 | scroll→padding→animateContentSize 올바른 순서 |

**검출된 성능 개선 사항:**

| 우선순위 | 항목 | 위치 | 설명 |
|---------|------|------|------|
| P2 | `@Immutable` 누락 — domain 모델 | domain/model/ | `Exercise`, `DayPlan`, `WeeklyPlan`, `UserProfile` 4개 data class에 `@Immutable` 없음. Compose 컴파일러 skip 최적화 불가 |
| P2 | forEach 내 lambda 재생성 | HomeScreen:256-268 | `onClick = { onExerciseClick(exercise.id) }` 매 recomposition마다 새 lambda 생성 |
| P3 | forEach로 O(n) composable 생성 | HistoryScreen:149-162, WorkoutDetailScreen:102-108 | day.exercises/instructions forEach. 현재 규모(7일×5개)에서는 무해하나 확장 시 lazy 전환 고려 |
| P3 | Chart `runBlocking` in `remember` | StatisticsScreen:177, GoalScreen:207 | Vico 1-frame 워크어라운드. LaunchedEffect와 중복이나 라이브러리 제약상 수용 |

---

#### F. 종합 개선 로드맵 (미확정 — 구현 시 별도 design+plan 필요)

**P0 — 이전 세션에서 식별:**
1. TopAppBar 아이콘 8개 과밀 → OverflowMenu 또는 BottomNav
2. Scaffold padding 이중 적용 (HomeScreen:97 + 116)

**P1 — 본 세션 신규:**
3. heading() 시맨틱 전체 도입 (a11y critical, 0% → 80%)
4. DayPlanCard 중첩 클릭 영역 분리
5. 휴식일 카드 false affordance 제거

**P2 — 본 세션 신규:**
6. `@Immutable` domain 모델 4개 추가 (Exercise, DayPlan, WeeklyPlan, UserProfile)
7. `@Immutable` ProfileViewModel 상태 3개 sealed class 추가
8. HomeUiState.Empty + _error SSOT 통합
9. liveRegion 확대 (동적 콘텐츠 변경 알림)
10. @Preview 함수 도입 (주요 화면 + shared 컴포넌트)

**P3 — 낮은 우선순위:**
11. testTag 전체 도입
12. form focus chain 구현
13. vertical padding 통일 (8dp/6dp)
14. GoalScreen supportingText 추가
15. Error 소비 패턴 통일

### 2026-06-04 — UDF 디자인 패턴 설계 검토

- **Why**: Jetpack Compose Architecture 문서 ([developer.android.com/develop/ui/compose/architecture](https://developer.android.com/develop/ui/compose/architecture)) 기준으로 전체 9개 ViewModel-Screen 쌍의 UDF(Unidirectional Data Flow) 패턴 준수도 전수 점검. 코드 변경 없는 분석 세션.
- **대상**: AuthViewModel(+Login/Signup/ForgotPassword), HomeViewModel(+HomeScreen), ProfileViewModel(+ProfileScreen), GoalViewModel(+GoalScreen), StatisticsViewModel(+StatisticsScreen), HistoryViewModel(+HistoryScreen), BadgeViewModel(+BadgeScreen), WorkoutDetailViewModel(+WorkoutDetailScreen), OnboardingViewModel(+OnboardingScreen)

#### 전체 평가

| 영역 | 평가 |
|------|------|
| State flows down (ViewModel → UI) | ✅ 전 화면 준수 |
| Events flow up (UI → ViewModel) | ✅ 전 화면 준수 |
| Immutability (`@Immutable` + data class) | ✅ (Profile 3개 sealed class `@Immutable` 누락 제외) |
| Single Source of Truth | ⚠️ HomeViewModel `_error` + `HomeUiState.Empty` 이원 관리 |
| Side Effects (`LaunchedEffect` 등) | ✅ (Chart `runBlocking` 워크어라운드 제외) |
| Error 패턴 일관성 | ⚠️ 화면별 소비 방식 상이 (기능 문제 아님) |

**Critical 위반: 0건 / Minor 개선: 4건**

#### 검출 사항 4건

**1. `@Immutable` 누락 — ProfileViewModel 상태 클래스 (P3)**
- `ProfileUiState`, `SaveState`, `DeleteState` 3개 sealed class에 `@Immutable` 어노테이션 없음.
- 다른 모든 화면(Home, Auth, Goal, Statistics, History, Badge)은 명시. 구조적으로 이미 불변이라 기능 문제 없으나 Compose 컴파일러 stability 힌트 + 프로젝트 일관성 위해 추가 권장.

**2. HomeViewModel `_error` + `HomeUiState.Empty` SSOT 위반 (P2)**
- `_error: MutableStateFlow<AppError?>` + `_uiState(Empty)` 두 개 source로 에러 상태 표현.
- `Empty`인데 `_error`가 `null`인 틈 가능 → `ErrorContent`에서 fallback `AppError.Unknown` 필요해짐 (HomeScreen.kt:105-108).
- **개선 방향**: `HomeUiState.Error(val error: AppError)` variant 추가하여 에러 정보를 상태 자체에 포함, 또는 `Empty`에 `error` 필드 추가.

**3. Error 소비 패턴 불일치 (P3)**
- Auth 3화면: `LaunchedEffect(formValid, error)` → form 유효 시 자동 dismiss (룰 8)
- Profile/Goal/Statistics: `LaunchedEffect(error)` → Snackbar → 즉시 `clearError()`
- Home: 별도 소비 없음 (ErrorContent 표시만, retry 시점에만 `clearError()`)
- Auth는 룰 8 대상이라 다른 것이 정당. Home의 stale error는 `Empty` branch에서만 읽으므로 기능 무해하지만 메모리에 stale 에러 잔존.

**4. Chart 초기화 `runBlocking` in `remember` (P3, 수용 가능)**
- GoalScreen.kt, StatisticsScreen.kt: `remember(stats) { runBlocking { producer.runTransaction { ... } } }`.
- Vico 라이브러리 1-frame 초기화 워크어라운드. 데이터 12주 이하로 실질 영향 없음. `LaunchedEffect(stats)`도 병존하여 중복이기도 함.

#### UDF 준수 우수 패턴 (긍정 사례 5건)

1. **Optimistic Update + Rollback** (HomeViewModel): 즉시 UI 반영 → 서버 실패 시 원래 상태 복원.
2. **Pagination Guard** (HistoryViewModel): `isLoading || !hasMore` 체크로 중복 요청 방지.
3. **`derivedStateOf`** (HistoryScreen): 무한 스크롤 감지 시 불필요한 recomposition 방지.
4. **Local form state ↔ Remote state 분리**: 모든 form 화면에서 `rememberSaveable`(input) + `StateFlow`(domain) 적절 분리.
5. **통일된 Error 인프라**: 9개 ViewModel 모두 `_error: MutableStateFlow<AppError?>` + `clearError()` 패턴.

### 2026-06-04 — HomeScreen 레이아웃 UX/UI 디자인 점검

- **Why**: 로그인 후 메인 화면(HomeScreen)의 레이아웃이 Jetpack Compose 공식 문서 및 Material Design 3 가이드라인 대비 어떤 개선점이 있는지 전수 점검. 코드 변경 없는 분석 세션.
- **참고 문서**: [Jetpack Compose Layouts](https://developer.android.com/develop/ui/compose/layouts)
- **대상 파일**: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/components/SkeletonUi.kt`, `ui/components/ErrorContent.kt`, `ui/theme/{Theme,Type,Color}.kt`, `ui/navigation/AppNavigation.kt`

#### 전체 레이아웃 점검 결과 (8건)

| 우선순위 | 항목 | 영향 |
|---------|------|------|
| **P0** | TopAppBar 아이콘 8개 과밀 | 터치 미스 + title 잘림 + a11y |
| **P0** | Scaffold padding 이중 적용 | 콘텐츠 상단에 불필요한 빈 공간 |
| **P1** | DayPlanCard 중첩 클릭 (Card onClick + 내부 TextButton) | 의도치 않은 동작 |
| **P1** | 휴식일 카드 false affordance (onClick no-op + ripple 동작) | UX 혼란 |
| **P2** | WeeklyProgressCard 시각적 강조 부족 (8dp LinearProgressIndicator) | 핵심 정보 가시성 |
| **P2** | Empty vs Error 상태 미분리 (둘 다 ErrorContent 사용) | semantic 정확성 |
| **P3** | vertical padding 불일치 (WeeklyProgressCard 8dp vs DayPlanCard 6dp) | 시각적 리듬 |
| **P3** | Exercise forEach → lazy item 전환 (현재 영향 없음) | 미래 확장성 |

#### P0-1: TopAppBar 아이콘 8개 과밀 — 상세

`HomeTopBarActions` (HomeScreen.kt:153-169) 에 IconButton 8개 일렬 배치:
```
프로필 | 테마 | 새로고침 | 기록 | 통계 | 목표 | 배지 | 로그아웃
```
- 실측: IconButton 48dp × 8 = 384dp + title ~150dp + 패딩 8dp = **~542dp** 필요. 일반 폰 360~412dp 에서 **title 잘림 + actions 화면 밖 이탈**.
- M3 TopAppBar actions 권장 상한: 2~3개. 다른 화면(Goal/Badge/Profile/Statistics/History)은 모두 actions 0개 + navigationIcon(ArrowBack) 패턴 — HomeScreen 만 유일한 예외.
- 아이콘 간 시각 유사성 문제: History ↔ Refresh (원형 화살표 계열), QueryStats ↔ Flag (기능 연관성 높은데 분리).

#### P0-2: Scaffold padding 이중 적용 — 상세

```kotlin
// HomeScreen.kt:97 — PullToRefreshBox 에 Scaffold padding 적용
Modifier.padding(padding)
// HomeScreen.kt:116 — 내부 LazyColumn 에도 같은 padding 재적용
LazyColumn(contentPadding = padding)
```
TopAppBar 높이만큼 빈 공간이 2배로 걸림. 수정: `LazyColumn(contentPadding = padding)` → `LazyColumn()`.

#### P1-1: DayPlanCard 중첩 클릭 — 상세

- Card 전체 `onClick` = 완료 토글 (HomeScreen.kt:233)
- 내부 exercise 별 `TextButton onClick` = 운동 상세 이동 (HomeScreen.kt:258)
- 사용자가 exercise 텍스트 탭 시 어느 handler 가 먹을지 예측 불가.
- **개선 방향**: 완료 토글을 Card 전체가 아닌 체크 아이콘 영역만 clickable 로 분리, 또는 Card onClick 제거 + 별도 Checkbox/IconButton.

#### P1-2: 휴식일 카드 false affordance — 상세

```kotlin
Card(onClick = { if (!day.isRestDay) onToggleComplete() }, ...)
```
onClick 람다 존재 → ripple + 클릭 피드백 활성화. 휴식일에도 ripple 반응하지만 no-op → broken 느낌.
**수정**: 휴식일이면 일반 `Card()` 사용 또는 `enabled = !day.isRestDay`.

#### Header(TopAppBar) 전용 점검 결과 (5건)

1. **scrollBehavior 미적용**: 7일치 DayPlanCard 스크롤 콘텐츠인데 TopAppBar 가 항상 64dp 고정. `enterAlwaysScrollBehavior()` 또는 `exitUntilCollapsedScrollBehavior()` + `nestedScroll` 연결 없음. 세로 공간 낭비.

2. **TopAppBar variant 부적합**: small `TopAppBar` 에 8개 action = variant 설계 의도 벗어남. M3 4종:
   - `TopAppBar` (small, 64dp) — 적은 action 용
   - `CenterAlignedTopAppBar` (64dp) — action 1~2개
   - `MediumTopAppBar` (112→64dp) — 제목 강조 + 스크롤 축소
   - `LargeTopAppBar` (152→64dp) — 큰 제목 + 스크롤 축소

3. **Title slot 활용 미흡**: `Text("이번 주 운동 계획")` 단순 텍스트만. 주(week) 날짜 범위, 진행률 요약 등을 slot 에 함께 넣어 정보 밀도를 높이는 것이 가능.

4. **navigationIcon 부재**: Home 이 root destination 이라 ArrowBack 불필요 (정상). 단 BottomNav 도입 시 logo/앱명 영역으로 활용 가능.

5. **아이콘 의미 구별 어려움**: 동일 크기/색상의 아이콘 8개 나열 → 인지 부하 높음.

#### 개선 방향 3가지 옵션 (미확정 — 사용자 결정 대기)

- **Option A (최소 변경)**: OverflowMenu — TopAppBar actions = Refresh + MoreVert(DropdownMenu 7항목). 현재 Navigation 구조 유지.
- **Option B (M3 권장)**: BottomNavigationBar(홈/통계/목표/프로필) + TopAppBar actions 축소(Refresh + 테마). Navigation 구조 변경 필요.
- **Option C (A + 스크롤)**: LargeTopAppBar + scrollBehavior + OverflowMenu. 주 범위 부제 추가. 공간 효율 + 정보 밀도 향상.

### 2026-05-30 — LoginScreen + ForgotPasswordScreen 룰 8 적용 (v0.1.7)

- **PR**: [#62](https://github.com/gunnysis/eundunHealth/pull/62) (shipped, v0.1.7, **supersedes** RFC `2026-05-30-login-error-banner-rfc` + design+plan 페어 `2026-05-30-login-error-banner-{design,plan}` — git history)
- **Why**: INC-2026-05-26-01 의 가시성 결함을 SignupScreen (v0.1.6) 외 Login + ForgotPassword 에도 일관 적용. CLAUDE.md 룰 8 (PR #60, 2026-05-30) 등재 후 첫 다중 화면 마이그레이션 사례. 룰 8 의 4 요소 (inline + persistent + a11y + Sentry) 모두 만족.
- **What**: `AuthErrorBanner` promote (SignupScreen 내 private → `ui/components/AuthErrorBanner.kt` public, 3 Auth 화면 공유). LoginScreen 의 `SnackbarHost` + `LaunchedEffect(lastError)`/`LaunchedEffect(resendError)` snackbar 인프라 제거 + Banner 통합 + `LaunchedEffect(formValid, lastError)` dismiss (D4). EmailNotConfirmed 만 기존 inline 재전송 UI (재전송 버튼 + cooldown) sticky 유지 (Option A, D2) — Banner 분기 제외. resendError 도 EmailNotConfirmed 영역 아래 같은 Banner 재사용 (D10). ForgotPasswordScreen 의 `opError` Banner 통합 (D5) + `passwordResetSent` 성공 snackbar 보존 (룰 8 예외 — 비-critical 성공). Sentry breadcrumb screen 값: `login` / `login_resend` / `forgot_password` 추가.
- **Outcome**: v0.1.7 (versionCode 21) release. 5 commit 분리 보존 (`--merge`): A docs (design+plan+RFC frontmatter) / B Banner promote / C Login 통합 / D Forgot 통합 / E version+docs. + fixup F (`1e5f2a6`) plans/README.md gen-plans-index.sh 누락 보완. AuthViewModelTest 19 PASS 유지 (회귀 없음). preflight-release.sh green (AAB 7.96 MB / APK 5.76 MB — v0.1.6 와 동일 size, 변경량 작음).
- **Lessons**:
  - **plan miss — gen-plans-index.sh Task 1 누락**: Task 1 (docs commit) 시점에 design+plan 페어 + RFC superseded 가 추가됐는데 README.md 재생성을 plan 에 포함 안 함 → CI `check-index` job fail → fixup commit 으로 보완 (이로 인해 5 → 6 commit). **개선**: docs/plans/_templates/plan.md 의 "PR 머지 후" 섹션 뿐 아니라 **page-level commit 시점에도 `bash scripts/gen-plans-index.sh` 명시**. design+plan 페어 추가 시 INDEX drift 가 즉시 발생함 (CI guard).
  - **v0.1.6 의 Lessons 3건 회피 결과**: (1) detekt UnusedPrivateMember — public composable promote 라 rule 비대상, **회피 성공**. (2) Spotless preflight 발견 — 각 commit 전 `spotlessApply` 명시 실행, **회피 성공** (모든 commit 이 한 번에 BUILD SUCCESSFUL). (3) design+plan 페어 staged — Task 0 Step 4 에서 명시 `git add`, **회피 성공** (self-apply 시 git rm 사용 가능).
  - **검증**: production / 실기기 시나리오 검증 + Sentry `auth.error_banner_shown` breadcrumb 의 `screen=login`/`forgot_password`/`login_resend` 확인은 머지 후 24h+ 별도 작업.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt` (NEW), `app/src/main/java/com/gunnys/eundunhealth/ui/auth/{LoginScreen,SignupScreen,ForgotPasswordScreen}.kt`, `app/build.gradle.kts`, `CLAUDE.md`, `docs/{PRD,SPEC,TRD,CHANGELOG,ops/operations-snapshot}.md`

### 2026-05-29 — Signup Failed UX inline error banner (v0.1.6)

- **PR**: [#58](https://github.com/gunnysis/eundunHealth/pull/58) (shipped, v0.1.6, **supersedes** RFC `2026-05-27-signup-failed-ux-visibility` — git history)
- **Why**: INC-2026-05-26-01 의 가시성 결함 — Signup 화면의 Failed 상태가 하단 snackbar 2초 자동 dismiss 로 사용자 인지 부족 (v0.1.4 실기기 검증 중 발견). RFC 작성 후 review 12 개선 사항 통합 (D1~D12). 단순 duration 상향 (Option A) 보다 form 내 inline banner (Option B) 가 본질적 해결.
- **What**: `AuthErrorBanner` (SignupScreen.kt 안 private, D5 YAGNI) — Material 3 `Surface(errorContainer)` + Icon + a11y liveRegion Polite + Sentry breadcrumb `auth.error_banner_shown` (D10). `AuthViewModel.clearSignupError()` Failed 시만 Form 전환 (D6 race 회피). dismiss = `LaunchedEffect(formValid, error)` button enabled 시점 (D1, input 변경 시 보존). resendError 도 같은 Banner 재사용 (AwaitingConfirmationCard, D7). `BuildConfig.MOCK_AUTH_ERROR` debug-only flag — release 빈 string + DEBUG short-circuit double-guard (D11). Snackbar 인프라 제거 (D12 dead code).
- **Outcome**: v0.1.6 (versionCode 20) release. 6 commit 분리 보존 (--merge): A docs / BC AuthErrorBanner+ViewModel+test+UI 통합 / D BuildConfig+mock / E version+docs / spotless fixup / F PR# fix. AuthViewModelTest +2 PASS. preflight-release green (AAB 7.96 MB / APK 5.76 MB). RFC + design + plan 3 페어 git rm + 본 entry 로 흡수 — **plans hybrid 컨벤션 (#57 plans-ledger-restructure) 의 첫 검증 사례**.
- **Lessons** (2026-05-30 본 세션 fresh 기록 — production 검증 결과 + Sentry breadcrumb 활용은 24h+ 후 별도):
  - **Plans hybrid 컨벤션 첫 사용 마찰점 3건**: (1) `pre-commit hook` 의 detekt `UnusedPrivateMember` 가 commit 분리 (D3) 와 충돌 — Banner private composable 을 add 한 commit 직후 detekt 가 unused 로 fail 가능. Task 2 + 3 를 통합 commit (Commit BC) 으로 우회. 향후 비슷한 패턴 = "type/function add + usage add" 가 같은 commit 권장. (2) `Spotless` 가 preflight-release 단계에서 unused import 발견 → fix-up commit 추가 → 4 → 5 commit. **개선**: Commit 직전 `./gradlew :app:spotlessApply` 습관화 (CLAUDE.md 룰 추가는 noise, pre-commit hook 의 spotless 가 이미 catch — 본 케이스는 SignupScreen Snackbar 인프라 제거 후 `remember` import 가 unused 됐는데 본인 commit 시 detect 안 된 이유는 별도 조사 필요). (3) design+plan 페어가 `git tracked` 안 된 상태로 작업 진행 → self-apply 시 `git rm` 불가 → OS rm. **개선**: Task 0 단계에서 design+plan 페어 staged 명시 (plan 의 Step 0.1 에 "git add design plan" 추가).
  - **D11 BuildConfig wording fix**: plan 작성 중 design 의 "compile error 로 leak 차단" 표현이 실제 release build 의 reference 필요 (`AuthRepositoryImpl` 의 `BuildConfig.MOCK_AUTH_ERROR` 비교) 와 align 안 됨 발견 → design D11 inline fix (release 빈 string 명시 + DEBUG short-circuit double-guard). 패턴은 memory `build-config-debug-only-pattern.md` 에 영구 등록.
  - **Expression body return 함정**: `override suspend fun signUp(...) = try { ... }` 에서 mock 분기에 `return Result.failure(...)` 추가 시 "Returns are prohibited" compile error. if-then-else expression 으로 변경. 같은 memory 에 등록.
  - **CLAUDE.md 룰 8 등재**: 본 작업 결과로 "Auth/UI 사용자 액션 실패 = inline + persistent + a11y + Sentry breadcrumb 4 요소" 룰 등재. INC-2026-05-26-01 의 구조적 재발방지.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`, `.../AuthViewModel.kt`, `app/src/test/.../AuthViewModelTest.kt`, `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt`, `app/build.gradle.kts`, `CLAUDE.md`, `docs/{PRD,SPEC,ops/operations-snapshot,CHANGELOG}.md`

### 2026-05-29 — Vico 2.1 → 3.1 chart migration

- **PR**: [#52](https://github.com/gunnysis/eundunHealth/pull/52) (shipped, v0.1.5)
- **Why**: dependabot #39 (vico 2 → 3 자동 PR) 가 close 된 후속 정식 마이그레이션. v3 의 변경은 import 경로 (`com.patrykandpatrick.vico.core.cartesian.*` → `compose.cartesian.*`) 만이고 builder DSL 은 v2.1 과 동일. dependabot verify 가 builder 깨진 것처럼 보였던 건 receiver (`CartesianChartModelProducer`) 의 import 가 깨져서 그 위 extension 도 resolve 안 됐던 것.
- **What**: 2 chart 파일 (`StatisticsScreen.kt`, `GoalScreen.kt`) 의 vico import 8 라인 교체. Opportunistic 후보 2개 적용 — `LineCartesianLayer.Interpolator.catmullRom` (line 부드러움) + `VerticalAxis.rememberStart(tickPosition = BaseAxis.TickPosition.Inside)` (chart 영역 활용성 ↑).
- **Outcome**: AAB 7.96 MB green, 실기기 시각 검증 OK (사용자 확인 2026-05-29). 3 commit 분리 보존 (minimal `fc00a41` + opportunistic `c2de8bf` + detekt baseline refresh `70c15e1`) — `--merge` 머지로 squash 금지. v0.1.5 release (#56) 에 포함.
- **Lessons**: detekt baseline drift (chronic CI failure, PR #42~#52 사이 5건 연속 fail) 가 본 vico PR 머지에 막혀서 발견 → 같은 PR 의 3번째 commit 으로 동반 해소. baseline.xml (tracked) vs baseline-debug.xml (.gitignore) 의 task 갱신 비대칭이 원인. 근본 fix (auto-sync 또는 단일 source) 는 별도 작업.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt`, `.../ui/goal/GoalScreen.kt`, `gradle/libs.versions.toml`, `config/detekt/baseline.xml`

### 2026-05-26 — Android App Links 자동 로그인

- **PR**: [#42](https://github.com/gunnysis/eundunHealth/pull/42) (shipped, v0.1.3)
- **Why**: v0.1.2 까지의 가입 흐름은 사용자가 메일 인증 후 앱으로 수동 복귀 → 비번 재입력 → "로그인" 클릭의 두 단계. App Links 도입으로 메일 클릭 한 번만으로 자동 로그인 → Onboarding/Home 도달이 목표.
- **What**: FastAPI 에 정적 라우트 2개 (`/.well-known/assetlinks.json` + `/auth/confirm` fallback HTML). Android AndroidManifest 의 intent-filter (autoVerify=true) + MainActivity 에서 supabase-kt 빌트인 `SupabaseClient.handleDeeplinks` extension. `SupabaseModule` 에 `flowType = FlowType.PKCE`, `host = BuildConfig.APP_LINKS_HOST`. `mapAuthError` 에 `otp_expired` / `bad_code_verifier` 한국어 매핑 추가.
- **Outcome**: Container App 기본 subdomain 활용 (custom domain 없음). v0.1.3 (versionCode 17) 출시.
- **Lessons**: 실기기 검증 결과 Supabase Console 의 Site URL 에 `/auth/confirm` path 를 포함시켜도 redirect_to 파라미터에는 origin(host) 만 사용됨 → App Links pathPrefix 매칭 실패. v0.1.4 (#44) 에서 `AuthRepositoryImpl.signUp`/`resendConfirmation` 의 `redirectUrl` 명시 전달로 hotfix. Supabase Site URL 의 path 처리 동작은 docs 와 실제가 다름.
- **Files touched**: `AuthRepositoryImpl.kt`, `SupabaseModule.kt`, `MainActivity.kt`, `AuthViewModel.kt`, `AndroidManifest.xml`, `mapAuthError` (auth error mapping), backend `auth_routes.py`, `assetlinks.json` 정적 응답

### 2026-05-26 — Supabase 가입 이메일 확인 흐름 + 인증 상태 모델 리팩터

- **PR**: [#40](https://github.com/gunnysis/eundunHealth/pull/40) (shipped, v0.1.1)
- **Why**: versionCode 14 (v0.1.0) 에서 가입 버튼 "무반응" 증상. 원인 두 가지 겹침 — (1) Supabase Confirm Email ON 일 때 `AuthRepositoryImpl.signUp` 의 `currentUserOrNull()` non-null 가정 위배 (PKCE flow 가입 직후 세션 미완성) → IllegalStateException → 일반 "회원가입에 실패했습니다" 에러. (2) `AppNavigation.LaunchedEffect(authState)` 가 `Unauthenticated` 진입 시 `popUpTo(0) inclusive=true` 로 Login 강제 이동 → SignupScreen dispose → 스낵바 표시 기회 손실.
- **What**: `AuthViewModel` 상태 모델 분리 (`SessionState` / `AuthOpState` / `SignupState`). `AwaitingEmailConfirmation` 안내 카드 UI + 60초 재전송 cooldown + 이메일 자동 채움. `LoginScreen` 의 `EmailNotConfirmed` 시 inline 재전송 버튼. `AppNavigation.LaunchedEffect` 단순화 (강제 popUpTo 제거).
- **Outcome**: v0.1.1 (versionCode 15) 출시. 인증 상태가 sealed class 로 명확히 분리되어 후속 작업 (App Links #42 등) 의 기반.
- **Lessons**: v0.1.2 (#41) 에서 supabase-kt 3.6.0 `Email.decodeResult` 가 GoTrue 응답 JSON 의 `aud`/`id` 누락으로 `SupabaseEncodingException` 던지는 케이스 hotfix 필요 (Confirm Email ON 에서 서버 성공 + 클라이언트 실패). `mapSignUpException` 헬퍼에서 `AwaitingConfirmation` 으로 분류. Supabase SDK 의 decode 오류를 "정상 가입의 일부" 로 분류한 사례.
- **Files touched**: `AuthRepository.kt`, `AuthRepositoryImpl.kt`, `AuthViewModel.kt`, `AppNavigation.kt`, `SignupScreen.kt`, `LoginScreen.kt`, `ForgotPasswordScreen.kt`, `mapAuthError` / `mapSignUpException`

## Older

(없음 — 모든 entry 가 last 90 days 이내)
