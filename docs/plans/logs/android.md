# Android 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-10-29 — 진행(`ProgressHubScreen`) & 프로필(`ProfileHubScreen`) 비주얼 대시보드·간소화 개편 · 체성분 직접 입력 0.1 정밀도 버그 수정 및 운영 출시 (v0.7.2/43, commit `bfb9ee8`)

- **Commit**: `bfb9ee8` (PR #221 squash merge; Play 프로덕션 업로드 run `40847386065` / 원장 PR #223 `a89447a` `LAST_UPLOADED_VERSION_CODE=43`, 백엔드 활성 리비전 `eundunhealth-api--0000068`)
- **Why**: ① 진행 탭(`ProgressHubScreen`)의 '한눈에 보는 요약' 3열 텍스트 타일과 하단 '운동 기록과 목표'의 `통계·기록·목표` 3행 메뉴가 중복되어 정보 밀도와 시각적 가독성이 떨어졌고, ② 프로필 탭(`ProfileHubScreen`)의 '신체 정보 및 휴식 요일' 행이 실제 신체 수치를 전혀 보여주지 않으며 5개 섹션(`내 정보·연동·화면·정보·계정`)으로 파편화되어 있었으며, ③ 골격근량·체지방률 직접 입력 다이얼로그에서 `0.1` 단위 소수점을 입력해도 눈금자 휠 `step = 0.5f`로 강제 스냅되던 정밀도 손실 버그가 존재했다.
- **What**:
  - **진행 허브(`ProgressHubScreen` / `ProgressHubViewModel`) 개편**: `VisualProgressSummaryDashboard`(`ThisWeekVisualHeroCard` 원형 프로그레스 링·플랜 모드 배지·7일 요일 미니 스탬프 바·운동 기록 열기 칩 + `FourWeekTrendVisualTile` 4주 미니 스파크바·연속 스트릭 배지 + `GoalProgressVisualTile` 체중·체지방 목표 진행 타일) 도입, 중복 3행 메뉴(`통계·기록·목표`) 과감히 제거 및 단일 `BadgeCollectionShowcaseCard`(진행률 바 + 대표 배지 5종 `BadgeMiniChip` 프리뷰)로 통합, `RefreshProgressOnResume` 자동 동기화 추가.
  - **프로필 허브(`ProfileHubScreen` / `ProfileHubViewModel`) 개편**: `ProfileBodyHeroCard`(`BodyMetricMiniGrid` 키·체중·BMI 및 판정·골격근량·체지방률 + `RestDayVisualStrip` 7일 원형 요일 스탬프), `WorkoutAndHealthIntegrationCard` 통합, `AppearanceSettingsCard` 인라인 1-Tap 3단 테마 세그먼트(`ThemeModeSegmentedRow`, `setThemeMode`), `AccountAndVersionFooter` 경량 푸터로 통합.
  - **체성분 직접 입력 `0.1` 정밀도 수정 & 공급망 업데이트**: `HybridBodyMetricPicker`의 골격근량·체지방률 `step = 0.1f` 정렬 및 `DirectMetricInputDialog` `roundAndClampMetricValue` 직접 적용(`BodyMetricAnchorTest`), `okhttp 5.5.1`·`coil 3.6.3` 업그레이드 및 `.github/dependabot.yml` AGP 자동 번프 제외 가드 추가.
- **Outcome**: Spotless/Detekt(0건)/AGP 10 가드/단위 테스트 242/242 PASS, R8 서명 릴리스 빌드 및 Google Play 프로덕션(`completed` 100%, run `40847386065`, 원장 PR #223) 출시 완료.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/{ProgressHubScreen,ProgressHubViewModel,MainAppShell}.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/profile/{ProfileHubScreen,ProfileHubViewModel}.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/components/HybridBodyMetricPicker.kt`, `app/src/test/java/com/gunnys/eundunhealth/ui/**`, `gradle/libs.versions.toml`, `.github/dependabot.yml`

### 2026-10-29 — 콜드 스타트 기동 크래시(`EUNDUNHEALTH-H`) 핫픽스 · Room 지연 초기화 · 5계층 심층 방어(룰 14) & AGP 거버넌스(룰 15) (v0.7.1/42)

- **Why**: `v0.7.0/41` 배포 후 기기 업데이트 시 콜드 스타트 과정에서 `AuthViewModel -> DefaultLocalDataWiper -> DatabaseModule.provideSmileTrainingDatabase`가 호출될 때, `Room.databaseBuilder`에 `MIGRATION_1_2`(`startVersion = 1`)와 `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`이 동시에 등록되어 `RoomDatabase.Builder.validateMigrationsNotRequired()`가 `IllegalArgumentException`을 던지며 앱이 100% 자동 종료됐다(`INC-2026-10-29-34`, Sentry `EUNDUNHEALTH-H`). 또한 단위 테스트가 DAO를 Mock으로 대체하여 실제 Hilt `@Provides` 빌더 조합을 호출하지 않던 테스트 사각지대를 근본 해소해야 했다.
- **What**:
  - **콜드 스타트 크래시 근본 해결 & 초기화 리팩토링**: `DatabaseModule.provideSmileTrainingDatabase`에서 충돌하던 `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)` 제거, `DefaultLocalDataWiper`에 `dagger.Lazy<WeeklyPlanDao>` / `dagger.Lazy<SmileSessionDao>` 지연 주입 적용(콜드 스타트 메인 스레드 Room DB 동기 초기화 차단), `runSuspendCatching` 공통화 및 `AppError.reportToSentry()`의 `CancellationException` 재던지기 가드 전수 적용(12개 클래스).
  - **5계층 심층 방어(룰 14) & AGP 거버넌스(룰 15) 테스트·스크립트 구축**: `DatabaseModuleTest`, `NetworkModuleTest`, `DefaultLocalDataWiperTest`, `AgpBuildContractTest` 단위 테스트(238건), `DatabaseMigrationAndInitTest` 계측 테스트, `scripts/smoke-cold-start.sh` 에뮬레이터/실기기 콜드 스타트 런타임 스모크 게이트 및 `scripts/check-agp10-compat.sh` 고도화.
- **Outcome**: Spotless/Detekt(0건)/AGP 호환성 가드/단위 테스트 238/238 PASS, 에뮬레이터(`Galaxy_S25_Ultra`, Android 16) 계측 테스트 2/2 PASS 및 Debug/Release APK 콜드 스타트 스모크 게이트(`FATAL EXCEPTION` 0건) 통과.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/di/DatabaseModule.kt`, `app/src/main/java/com/gunnys/eundunhealth/data/local/LocalDataWiper.kt`, `app/src/main/java/com/gunnys/eundunhealth/domain/model/AppError.kt`, `app/src/test/java/com/gunnys/eundunhealth/di/*`, `app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt`, `app/src/androidTest/java/com/gunnys/eundunhealth/data/local/DatabaseMigrationAndInitTest.kt`, `scripts/smoke-cold-start.sh`, `scripts/check-agp10-compat.sh`, `scripts/preflight-release.sh`, `CLAUDE.md`

### 2026-10-29 — 스마일 트레이닝 v2 리팩토링 · Zero-Legacy Cutover · 4대 접속 경로(IA) 구현 및 운영 출시 (v0.7.0/41, commit 40595a1)

- **Commit**: `40595a1` (PR #213 release/v0.7.0 머지; Play 프로덕션 업로드 run `40816257901` / 원장 PR #215 `LAST_UPLOADED_VERSION_CODE=41`, 백엔드 배포 run `40816129146` / 리비전 `0000067`)
- **Why**: `v0.5.0`에 도입된 스마일 트레이닝 v1의 복잡한 3중 설정(루틴 3 × 난이도 3 × 횟수 5 = 45개 조합)과 5점 감정 회고 설문이 높은 이탈률을 유발하고, `READY` 단계 프레임 무시 결함(수동 2차 버튼 터치 강제) 및 홈 화면 접근성 단절이 존재하여, 이를 단일 표준 규격(`2초 유지 × 3회 반복`)과 1.0초 얼굴 정렬 자동 시작, 4대 접속 경로(IA)로 개편하고 전 계층 v1 레거시를 전면 소거해야 했다.
- **What**:
  - **도메인 & 비전 단일 표준화**: `SmileRoutineType`, `SmileDifficultyLevel`, `reflectionEmoji`, `REFLECTION` 상태를 전량 제거하고 단일 표준 스펙 상수(`SmileTrainingSpec`)로 통합. `SmileSignalEvaluator`에 지수 이동 평균(EMA alpha 0.6f) 스무딩과 `READY` 단계 1.0초 연속 얼굴 정렬 시 자동 시작 트리거(`allowAutoStart = !isPaused`) 구현.
  - **Room DB v2 Cutover Reset & 오프라인 워커**: `SmileSessionEntity`에서 루틴·난이도·이모지 컬럼 제거, `SmileTrainingDatabase` v2(`MIGRATION_1_2` + `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`), `SmileSyncWorker`에 `ExistingWorkPolicy.APPEND_OR_REPLACE` 및 10배치 반복 드레인 적용, `deleteAllSessions()` 시 WorkManager 예약 취소 연동.
  - **UI/UX 리팩토링 & 경량화**: `SmileTrainingScreen.kt` 1,114줄 → 606줄 대폭 경량화, `Modern Forest & Mint` 브랜드 토큰 적용, 풀블리드 카메라 HUD + 플로팅 글래스 시트, `ACTIVE -> COMPLETED` 직행 자동 저장, TalkBack `liveRegion` 깜빡임 방지 및 200% 대형 글꼴 스크롤 가드, 어두운 HUD 진입 시 상태바 명암 자동 전환.
  - **4대 접속 경로(IA) 확립**: 홈 상단바 우측 상시 `SmileQuickActionChip`("스마일" / "✓ 스마일 완료"), `TodayHeroCard` 하단 스마트 `SmileTrainingContextualCard`(미완료 시 노출/완료 시 자동 숨김), `WorkoutGuideScreen` 완료 화면 쿨다운 브릿지 버튼, `ProgressHubScreen` 최상단 마음 연습 카드 승격.
  - **백엔드 v2 API 승격**: `/v2/smile-training/sessions` (POST/GET/DELETE) 및 `/v2/smile-training/statistics` (GET) 4개 라우트 승격(`CamelSchema` 및 `operation_id` 보존). 구형 `/smile-training/*` 및 v1 세션 데이터 완전 정리.
- **Outcome**: Android Spotless/AGP 10 가드/Detekt(0건)/단위 테스트 212/212 PASS, Zero-Legacy Grep Gate(잔존 0건) 통과, R8 릴리스 빌드 완료 및 Google Play 프로덕션(`completed` 100%, run `40816257901`)·백엔드(`0000067`, run `40816129146`) 배포 완료.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/domain/smile/*`, `app/src/main/java/com/gunnys/eundunhealth/data/local/*`, `app/src/main/java/com/gunnys/eundunhealth/data/sync/*`, `app/src/main/java/com/gunnys/eundunhealth/ui/smile/*`, `app/src/main/java/com/gunnys/eundunhealth/ui/home/*`, `app/src/main/java/com/gunnys/eundunhealth/ui/workout/*`, `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/*`, `backend/app/routers/smile_training.py`
- **설계 원문**: `2026-10-29-smile-training-v2-plan.md` (본 entry 로 흡수, git rm)

### 2026-10-28 — UI/UX 전면 개선 (`PROP-05` Phase 1~4: 브랜드·M3·Pretendard·Today Hero·운동 가이드 모드·허브·3중 하이브리드 바디 피커·2단계 온보딩) 구현·심층 리팩토링 및 운영 출시 (v0.6.1/40, commit `c12f969`)

- **Commit**: `c12f969` (PR #201 squash merge; Play 프로덕션 업로드 run `40647597577` / 원장 PR #211 `LAST_UPLOADED_VERSION_CODE=40`, 백엔드 배포 run `40647560099` / 리비전 `0000066`)
- **Why**: `v0.4.0~v0.6.0` 기능 확장 이후에도 시각 기반과 핵심 루프("앱 진입 → 오늘 운동 파악 → 자세 확인 및 완료")가 초기 `v0.1` 구조에 머물러 있던 8대 구조적 결함(`F-1` M3 색 역할 누락/보라 톤 유입, `F-2` 다크모드 콜드 스타트 백색 점멸, `F-3` 체성분 미입력자 `20%`/`30kg` 가짜 기본값 강제 저장, `F-4` 홈 새로고침 시 전체 화면 깜빡임, `F-5` 식단 재생성 부재 및 상태 패턴 미준수, `F-6` 5단계 `surfaceContainer*` 부재, `F-7` 타이포그래피 8종 결손·한글 자간 벌어짐, `F-8` Dynamic Color 기본 강제로 브랜드 그린 소실)을 근본 해결하고 사용자 경험을 전면 고도화해야 했다.
- **What**:
  - **영역 A (브랜드 & 디자인 파운데이션 + Quick Wins 4종)**: `#006D3C` Modern Forest & Mint 기반 M3 36슬롯 + 시맨틱 13토큰(`EundunExtendedColors`, 5단계 `surfaceContainerLowest..Highest`), `Pretendard Variable` 한글 타이포그래피 15슬롯(`includeFontPadding = false`, 음수 자간), DayNight 스플래시 테마(`values-night`, `values-v31`, `values-night-v31`), `MotionTokens` 및 `HapticFeedbackHelper`, `dynamicColor = false` 기본값 + Android 12+ opt-in, 체성분 미입력 `null` 전송(`F-3`), 홈 인플레이스 갱신(`isRefreshing = true`, `F-4`), 로그아웃 확인 다이얼로그, `UpdateRequiredScreen` 문구 개선.
  - **영역 B & C ('오늘' 탭 Today Hero & 단계별 운동 가이드 모드)**: `WorkoutDurationEstimator` 및 `TodayHeroCard`(예상 소요 시간, 종목 칩, 가이드 시작 주 CTA + 바로 완료 보조 액션), 완료 해제 시 5초 Undo 스낵바(`HomeSideEffect.ShowUndoUncompleteSnackbar`), 휴식일/운동 완료 후 스마일 트레이닝 바로가기 카드, 기본 접힘 7일 계획, `WorkoutGuideScreen` & `WorkoutGuideViewModel`(`HorizontalPager`, `FLAG_KEEP_SCREEN_ON`, 3지 선다 이탈 확인 다이얼로그, 마지막 종목 완료 시 당일 운동 자동 완료 및 `savedStateHandle` 홈 즉시 갱신).
  - **영역 D & E (허브 고도화 & 3중 하이브리드 바디 피커 + 2단계 온보딩)**: `ProgressHubScreen` 3열 요약 타일(`IntrinsicSize.Max` 균일 높이, 병렬 로드), `StatisticsScreen` 차트 TalkBack 요약 및 헬스장/홈트 비율 카드, `GoalScreen` 달성률 바 및 체지방 미입력 안내, `HistoryScreen` 한국어 요일 및 아코디언 상세, `MealPlanScreen` `@Immutable` 상태 + `regenerateError` 인라인 배너(룰 8/11) + 오늘 식단 최상단 자동 펼침, `ProfileHubScreen` 5대 섹션 그룹화, 레거시 `ProfileSlider` 삭제 및 `HybridBodyMetricPicker`(`rememberUpdatedState` + `BoxWithConstraints` 동적 중앙 패딩 눈금자 휠 + 숫자패드 + 스테퍼 + 성별 스마트 앵커링) + `OptionalBodyCompositionCard` + 2지 선다 `GenderSelector`, `AuthGateScreen` 3슬라이드 소개 페이저, 2단계 `OnboardingScreen` 위저드(완료 즉시 첫 주간 계획 자동 생성).
- **Outcome**: Android Spotless/AGP 10 가드/Detekt(0건)/단위 테스트 **209/209 PASS**(43 파일)/`releaseArtifacts`(R8 full mode + Sentry mapping) 통과 및 Play 프로덕션(`completed` 100%, run `40647597577`)·백엔드(`0000066`, run `40647560099`) 배포 완료.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/**`, `app/src/main/java/com/gunnys/eundunhealth/domain/util/WorkoutDurationEstimator.kt`, `app/src/main/res/font/pretendard_variable.ttf`, `app/src/main/res/values*/themes.xml`, `app/src/test/java/com/gunnys/eundunhealth/**`
- **설계 원문**: `2026-10-28-ux-improvement-design.md` (`PROP-05`, 본 entry 로 흡수, git rm)

### 2026-10-27 — v0.6.1 휴식일·프로필 UI 접근성 및 반응형 레이아웃

- **Commit**: `c12f969` (통합 배포)
- **Why**: 7요일 단일 행은 320dp 폭과 큰 글꼴에서 겹칠 수 있고 선택 상태가 색상에 과도하게 의존했다. 프로필 label/value `SpaceBetween` 행도 긴 텍스트의 충돌 위험이 있었다.
- **What**:
  - **`RestDaysSelector.kt`**: `FlowRow` 기반 48dp 이상 `FilterChip`, 기본 체크 표시, selected semantics, 오류 `LiveRegionMode.Polite`를 적용했다. 휴식일 1~6일 API/DB 계약은 유지했다.
  - **`ProfileSummaryCard.kt`**: label/value가 독립적으로 줄바꿈되는 적층 셀과 불투명 의미 색상 토큰으로 변경했다.
- **Outcome**: Android Spotless/Detekt/unit/lint/debug/androidTest compile/R8 release 통과 및 `v0.6.1/40`(run `40647597577`)에 통합되어 Play 프로덕션 배포 완료.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/components/RestDaysSelector.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/components/ProfileSummaryCard.kt`

### 2026-10-27 — 적응형 주간 운동 계획 v2(`헬스장`/`홈트레이닝`) & 휴식 요일 자유 다중 지정(`restDays` 1~6일, Zero-Legacy) 풀스택 구현 및 운영 출시 (v0.6.0, commit `a0b6701`)

- **Commit**: `a0b6701` (`4562506` + `6ef6fb1` + `a0b6701`; Play 프로덕션 업로드 run `40469947190` / 원장 `f9eeefc`, 백엔드 배포 run `40469877206` / 리비전 `0000065`)
- **Why**: ① 홈 화면 진입 시 무조건 헬스장 계획을 자동 생성하고 동일 주 프로필 수정 시 계획을 덮어쓰던 v1 방식을 개선해, 사용자가 `헬스장`과 무장비 `홈트레이닝` 중 운동 환경을 선택하거나 기본 환경을 설정할 수 있어야 했다. ② 고정 단일 휴식일(`restDay: Int = 7`, 주 6일 강제 운동)이 초보·홈트 사용자에게 과도한 부담과 이탈을 유발하므로, 주 1~6일 범위에서 자유롭게 다중 휴식 요일(`restDays: List<Int>`)을 선택하고 실제 운동 일수($N$)를 기준으로 주간 완료율을 계산해야 했다.
- **What**:
  - **Zero-Legacy 다중 휴식 요일(`restDays`) & 대근육 우선순위 큐**: 레거시 단일 `rest_day`/`restDay`를 백엔드·OpenAPI·Android 전 계층에서 완전 제거하고, PostgreSQL `user_profiles.rest_days` `INTEGER[]`(`ck_user_profiles_rest_days_valid`: 1~6개, 1~7 범위, 중복 금지) 및 Room `EundunDatabase` v3(`IntListConverters`)를 도입. 비휴식일(1~6일)에는 `WeeklyPlanGenerator`(헬스장)와 `HomeWeeklyPlanGenerator`(내장 18종 무장비·저충격 카탈로그 `local:home:<slug>:v2.0`, `REPS`/`SECONDS` 단위)가 대근육 우선순위 큐(`[PUSH → LEGS → PULL → CARDIO → MIXED → CARDIO]`)를 순환 배치.
  - **명시적 주간 계획 v2 계약 & 클라이언트 버전 가드**: 백엔드 `/v2/weekly-plan` 7개 API(`planMode = gym | home` 필수, 동일 주 중복 생성 시 `409 weekly_plan_exists`, 손상 계획 복구용 `DELETE /v2/weekly-plan` 204), 신체 계측 이력을 오염시키지 않는 `PUT /v2/profile/default-workout-plan-mode`, `GET /app-compatibility` 및 `Eundun-Client-Version-Code` 헤더 가드(`MINIMUM_SUPPORTED_ANDROID_VERSION_CODE=39`, `PUT /profile` 포함)를 구현하고, KST 시간대 유틸(`backend/app/time_utils.py`) 및 식단/통계 서비스를 정합화.
  - **Material 3 UI/UX & 도메인 SSoT 일원화**: `RestDaysSelector`(`MultiChoiceSegmentedButtonRow` + 주 3일/주말/일요일 프리셋 칩), `ProfileSummaryCard` 다중 휴식 요일 요약, `NoWeeklyPlanCard` & `WorkoutPlanModeSelectionSheet`(기본 운동 환경 저장 체크박스 포함), `WorkoutDetailScreen` 초 단위(`초`) 표기 및 `HomeExerciseGuideCard` 배너, `MainAppShell` 구버전 강제 업데이트 다이얼로그(`ClientUpdateRequired`), `UserProfile.kt` 요일 상수/정규화 SSoT 및 `HistoryViewModel.refresh()` 당겨서 새로고침 버그 수정을 반영.
- **Outcome**: C1 운영 DB 마이그레이션(`d7f4a2c91b10`) 및 v2 사용자 데이터 초기화(`reset_v2_user_data.py` 감사 ID `v2-cutover-20261027`), C2 백엔드 ACA 배포(`eundunhealth-api--0000065` & `eundunhealth-reaper`), C3 Google Play 프로덕션 트랙 `v0.6.0/39` `completed`(100%) 출시 완료. Android 단위 테스트 186/186 PASS, Backend pytest 140/140 PASS.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/domain/model/*`, `app/src/main/java/com/gunnys/eundunhealth/domain/usecase/*`, `app/src/main/java/com/gunnys/eundunhealth/data/local/*`, `app/src/main/java/com/gunnys/eundunhealth/data/repository/*`, `app/src/main/java/com/gunnys/eundunhealth/ui/components/RestDaysSelector.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/home/*`, `app/src/main/java/com/gunnys/eundunhealth/ui/profile/*`, `app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/*`, `backend/app/routers/weekly_plan.py`, `backend/app/routers/profile.py`, `backend/app/routers/app_compatibility.py`, `backend/app/time_utils.py`, `backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`, `backend/scripts/reset_v2_user_data.py`
- **설계 원문**: `docs/reports/2026-10-26-adaptive-weekly-workout-proposal.md`, `2026-10-27-flexible-rest-days-design.md` (본 entry 로 흡수, git rm)

### 2026-10-26 — 온디바이스 스마일 트레이닝(Smile Training) 풀스택 구현 및 UI/UX 고도화 (v0.5.0, commit `869407a`)

- **Commit**: `869407a` (Play 프로덕션 업로드 run `40274395812` / 원장 `cbd155f`, 백엔드 배포 run `40274391632` / 리비전 `0000063`)
- **Why**: 은둔·고립 경향이 있거나 혼자 운동하는 사용자가 사진·영상 촬영이나 외부 서버 전송에 대한 심리적·개인정보 부담 없이, 기기 내부 메모리에서만 실시간으로 미소 근육 연습과 짧은 감정 회고를 수행하고 연속일(Streak) 동기를 유지할 수 있는 마음 연습 기능이 필요했다.
- **What**:
  - **온디바이스 비전 & 순수 도메인 평가기**: `CameraX` 1.6.2(`CameraXViewfinder` + `ResolutionSelector` 640×480 + `STRATEGY_KEEP_ONLY_LATEST`)와 Google Play 서비스 `ML Kit Face Detection` 17.1.0(`ModuleInstallClient` 사전 준비)을 연동하고, 모든 경로에서 `imageProxy.close()`를 보장해 원시 프레임 무저장·무전송을 구현. `SmileRoutineRule`(루틴 3종 × 난이도 3단계)과 `SmileSignalEvaluator`(단조 시간 `timestampElapsedMs` 누적, 히스테리시스, 1,000ms 슬라이딩 윈도우 >= 75% 충족률, `trackingId` 변경 시 홀드 초기화, 일시정지 시 완료 반복 보존 `clearActiveHold()`)를 순수 Kotlin으로 분리.
  - **루트 모달 Compose UI (`SmileTrainingScreen`, `SmileCameraStage`)**: `AppNavigation` 루트 모달(`Screen.SmileTraining`)로 하단 탐색 바 가림 원천 차단, `WindowInsets.safeDrawing` 안전 영역, `CompositingStrategy.Offscreen` + `BlendMode.Clear` 타원 비네트, 스프링 보간 `SweepGradient` 프로그레스 링, 반복 단계 도트 인디케이터, 실시간 정렬 상태 배지, `ON_STOP` 자동 일시정지 및 `ON_RESUME` 권한 재검증, `FLAG_KEEP_SCREEN_ON`, TalkBack `LiveRegionMode.Polite` 적용.
  - **오프라인 우선 Room SSOT + WorkManager 멱등 동기화 + 백엔드 API**: `SmileTrainingDatabase`(`smile_training.db` v1, KSP 스키마 export) + `SmileSyncWorker`(`@HiltWorker`, `ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`)로 백그라운드 업로드 및 400/409/422 `BLOCKED` 격리(무한 재시도 배터리 소모 차단), 기기 변경 시 빈 로컬 DB 자동 복원(`refreshRemoteHistoryIfEmpty`), 계정 삭제 시 로컬 와이퍼(`DefaultLocalDataWiper` + `AuthRepositoryImpl` 선캡처 `userId`) 연동. 백엔드 FastAPI `/smile-training/sessions`(`POST`/`GET`/`DELETE`), `/smile-training/statistics`(`GET`), Alembic `a3869f575d6a`(`smile_training_sessions` 테이블), `AccountService._purge_app_data` 및 고아 정리 `UNION` 확장.
- **Outcome**: Android 단위 테스트 165/165 PASS, Backend pytest 126/126 PASS, Spotless/Detekt/R8 릴리스 빌드 및 Azure Container Apps(`eundunhealth-api--0000063`) + Google Play 프로덕션(`v0.5.0/38`) 배포 완료.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/domain/smile/*`, `app/src/main/java/com/gunnys/eundunhealth/data/smile/*`, `app/src/main/java/com/gunnys/eundunhealth/data/local/Smile*`, `app/src/main/java/com/gunnys/eundunhealth/data/sync/SmileSyncWorker.kt`, `app/src/main/java/com/gunnys/eundunhealth/data/repository/SmileTrainingRepositoryImpl.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/smile/*`, `backend/app/models/smile_training.py`, `backend/app/routers/smile_training.py`, `backend/alembic/versions/a3869f575d6a_create_smile_training_sessions.py`
- **설계 원문**: `2026-10-26-smile-training-design.md`, `2026-10-26-smile-training-plan.md` (본 entry 로 흡수, git rm)

### 2026-10-16 — '오늘의 활동' 제거, Health Connect 최소 권한화(READ_EXERCISE 단독) 및 HomeScreen M3 리디자인 (commit `ddcddb5`)

- **Commit**: `ddcddb5`
- **Why**: Google Play Health Connect 최소 권한 정책 준수를 위해 실제 핵심 가치와 무관한 일일 활동(`READ_STEPS`, `READ_TOTAL_CALORIES_BURNED`, `READ_HEART_RATE`) 권한을 회수하고, 홈 화면의 주간 진행률 가시성 및 오늘 운동 하이라이트(`PRD PLAN-03`)를 개선해야 했다.
- **What**:
  - `TodayActivityCard.kt`, `DailyActivity.kt`, `GetTodayActivityUseCase.kt`, `HealthConnectMappers.kt` 및 관련 테스트 2종 삭제; `AndroidManifest.xml`을 `READ_EXERCISE` 1종 권한으로 축소하고 `ManifestHealthConnectRationaleTest` 회귀 가드 추가.
  - `HomeScreen.kt`: `WeeklyProgressCard`에 주차 날짜 범위·완료율 캡슐 배지·애니메이션 진행률 바·7일 미니 스탬프 바(`WeeklyDayStampRow`)를 도입하고, `DayPlanCard`에 KST 기준 오늘 날짜(`isToday: Boolean` 원시 파라미터로 Compose Strong Skipping 보장) 2.dp Primary 테두리·`"오늘"` 배지 및 구조화된 `ExerciseRow` 적용.
  - 법적 고지(`privacy-policy.md`, `account-deletion.md`, `health-connect-permissions.md`) 동기화(`scripts/sync-legal-docs.sh`).
- **Outcome**: Health Connect 요청 권한 4→1 축소, `PLAN-03` 구현 완료, Android 단위 테스트 및 정적 분석 통과.
- **설계 원문**: `docs/plans/implementation_plan.md` (본 entry 로 흡수, git rm)

### 2026-10-15 — ExerciseDB 운동 콘텐츠 한국어 로컬라이징 (v0.3.1, PR #178·#186)

- **PR**: [#178](https://github.com/gunnysis/eundunHealth/pull/178) · [#186](https://github.com/gunnysis/eundunHealth/pull/186)
- **Why**: ExerciseDB API가 영문 운동 이름과 지침만 제공하여 한국어 사용자 경험이 저하됐다. 런타임 번역 비용이나 자체 백엔드/DB 추가 없이 앱이 추천 풀로 사용하는 46개 운동을 안전하게 한국어화해야 했다.
- **What**:
  - `app/src/main/assets/exercises/korean_exercises.json` 및 `ExerciseLocalization.kt` 도입: 추천 풀 46개 운동의 한국어 이름·단계별 지침(`instructions`)·자세 팁(`tips`) 및 근육(15종)·신체 부위(10종)·장비(7종) 한국어 매핑 제공.
  - 원문 이름+지침 정규화 SHA-256 지문 대조를 통해 상류 원문이 변경되거나 미번역 운동이 유입되면 자동으로 영문 원문으로 안전 폴백.
  - 서버/Room plan JSON은 영문 canonical로 유지하고 `WorkoutRepositoryImpl` 반환 경계 및 `ExerciseDetailScreen`에서 한국어를 적용.
  - `scripts/verify-exercise-translations.ps1`로 릴리스 전 46개 운동 ID 및 원문 지문 드리프트를 검증.
- **Outcome**: 46/46 운동 지문 일치 및 단위 테스트 통과. 런타임 번역 API/DB 추가 비용 0원.
- **Files touched**: `app/src/main/assets/exercises/korean_exercises.json`, `app/src/main/java/com/gunnys/eundunhealth/data/local/ExerciseLocalization.kt`, `app/src/main/java/com/gunnys/eundunhealth/data/repository/WorkoutRepositoryImpl.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/detail/ExerciseDetailScreen.kt`, `scripts/verify-exercise-translations.ps1`
- **설계 원문**: `2026-10-15-exercise-korean-localization-design.md` (본 entry 로 흡수, git rm)

## Older

- 2026-06-16 감사 LOW 후속: SideEffect 라이프사이클 헬퍼 (v0.1.15) ([#123](https://github.com/gunnysis/eundunHealth/pull/123)) — 직전 v0.
- 2026-06-15 출시 준비 종합: 빈 운동계획·토글 버그 근본수정 + 전수감사 (v0.1.14) ([#122](https://github.com/gunnysis/eundunHealth/pull/122)) — 실기기(Flip3) 제보 2버그 — ① 릴리스에서 운동계획이 통째로 빔 ② 완료 체크 해제가 새로고침 후 되돌아옴.
- 2026-06-11 코드베이스 리팩토링 Bundle E·A·C (도메인 정합·알고리즘 분리·UI 중복) — Android 감사 — (E) `UserProfile.
- 2026-06-11 Health Connect 체성분 가져오기 제거 (수동 단일화, v0.1.12) ([#106](https://github.com/gunnysis/eundunHealth/pull/106)) — #84에서 도입한 HC 체성분 가져오기가 **구조적으로 무용** — HC에 골격근량 타입 부재(공식), 체지방 삼성헬스→HC 동기화 flaky, 스마트체중계 없는 대다수 무데이터 → 영구 "기록 없음" 혼란.
- 2026-06-10 Health Connect Android 14+ 수정: 연동 버튼 무반응 + 읽기 실패 (v0.1.11) ([#104](https://github.com/gunnysis/eundunHealth/pull/104)) — 내부테스트 실기기(Galaxy Flip3/Android 15)에서 "연동" 버튼 무반응 + 체성분/오늘의활동 읽기 실패.
- 2026-06-08 홈 "오늘의 활동" 요약 (#2, 걸음·칼로리·심박) ([#85](https://github.com/gunnysis/eundunHealth/pull/85)) — 갤럭시워치/폰이 측정한 오늘 활동량(HC 동기화됨 — 로드맵 R2 해소)을 홈에 glanceable 표시 → engagement.
- 2026-06-08 체성분(체중·체지방) Health Connect 가져오기 (#1) + 골격근량 표기 ([#84](https://github.com/gunnysis/eundunHealth/pull/84)) — 체중·체지방을 수기 입력하던 것을, HC 최신 측정값(워치/체중계→삼성헬스→HC)을 **사용자 확인 가져오기**로 줄임.
- 2026-06-06 프론트엔드 회귀 방지 3계층 가드 — Phase 1-5 UDF-Enhanced 마이그레이션 후 옛 패턴 (분산 StateFlow, `collectAsState()`, `@Immutable` 누락) 재도입 방지.
- 2026-06-04 프론트엔드 전수 분석 (UI 구조 · 디자인 시스템 · a11y · 내비게이션 · 성능) — v0.
- 2026-06-04 프론트엔드 의존성 LTS/Stable 마이그레이션 검토 — v0.
- 2026-06-04 프론트엔드 빌드 환경 및 의존성 현대화 검토 — v0.
- 2026-06-04 UDF 디자인 패턴 설계 검토 — Jetpack Compose Architecture 문서 ([developer.
- 2026-06-04 HomeScreen 레이아웃 UX/UI 디자인 점검 — 로그인 후 메인 화면(HomeScreen)의 레이아웃이 Jetpack Compose 공식 문서 및 Material Design 3 가이드라인 대비 어떤 개선점이 있는지 전수 점검.
- 2026-06-04 Compose 퍼포먼스 공식 문서 기반 성능 점검 — [Jetpack Compose Performance](https://developer.
- 2026-06-04 Clean Architecture + MVI + Multi-module 아키텍처 설계 검토 — 시니어 안드로이드 아키텍트 관점에서 현재 프로젝트를 [Clean Architecture + MVI + Multi-module] 최신 권장 표준 대비 전수 감사.
- 2026-06-04 Claude Code Plugin Errors 진단 및 해결 방안 — Claude Code 시작 시 10개 플러그인 에러 발생 — 9개 "not found in marketplace" + 1개 `spawn vtsls ENOENT`.
- 2026-05-30 LoginScreen + ForgotPasswordScreen 룰 8 적용 (v0.1.7) ([#62](https://github.com/gunnysis/eundunHealth/pull/62)) — INC-2026-05-26-01 의 가시성 결함을 SignupScreen (v0.
- 2026-05-29 Vico 2.1 → 3.1 chart migration ([#52](https://github.com/gunnysis/eundunHealth/pull/52)) — dependabot #39 (vico 2 → 3 자동 PR) 가 close 된 후속 정식 마이그레이션.
- 2026-05-29 Signup Failed UX inline error banner (v0.1.6) ([#58](https://github.com/gunnysis/eundunHealth/pull/58)) — INC-2026-05-26-01 의 가시성 결함 — Signup 화면의 Failed 상태가 하단 snackbar 2초 자동 dismiss 로 사용자 인지 부족 (v0.
- 2026-05-26 Supabase 가입 이메일 확인 흐름 + 인증 상태 모델 리팩터 ([#40](https://github.com/gunnysis/eundunHealth/pull/40)) — versionCode 14 (v0.
- 2026-05-26 Android App Links 자동 로그인 ([#42](https://github.com/gunnysis/eundunHealth/pull/42)) — v0.
