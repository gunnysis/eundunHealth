# Android 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-11-06 — Wear OS 6 (API 36) 3모듈(`:app` + `:wear` + `:core:wear-contract`) Foundation · 원형 M3 진단 UI · 공급망/CI 가드 구축 및 환경설정 구현 설계(`PROP-06` / `PROP-06-A` 경계 통합) 완료 (commit `2a49cea`)

- **Commit**: `2a49cea`
- **Why**: `PROP-06`(워치 기반 마음 상태·스트레스·공황·증상 포착 및 생체 신호 수집 통합 아키텍처 제안) 및 `PROP-06-A`(Azure 백엔드 아키텍처 판단) 중 백엔드 API나 모바일 프로덕션 런타임 동작에 영향을 주지 않고 즉시 구축 가능한 **환경 설정·멀티모듈 스캐폴딩·테마 파운데이션·공급망 락·정적/동적 검증 인프라·Azure 환경 경계**를 선행 구축하여 후속 런타임 Phase 0~3의 기반을 마련해야 했다.
- **What**:
  - **3모듈 멀티모듈 스캐폴딩 (`settings.gradle.kts`, `:core:wear-contract`, `:wear`)**: 순수 JVM 17 공유 계약 모듈 `:core:wear-contract`(`WearTimeConstants.kt` `KST_ZONE = ZoneId.of("Asia/Seoul")` SSoT 통합)와 Wear OS 6 전용 모듈 `:wear`(`namespace = "com.gunnys.eundunhealth.wear"`, `applicationId = "com.gunnys.eundunhealth"`, `minSdk = 36`, `targetSdk = 37`, `wearVersionCode = 10001`)를 신설하고 `:app`의 `TimeConstants.kt`가 `:core:wear-contract`의 `KST_ZONE`을 위임 참조하도록 일원화.
  - **Wear Compose Material 3 (`1.7.1`) 원형 디자인 시스템 & 환경 진단 화면**: OLED 순수 블랙(`#000000`) 배경 및 브랜드 에메랄드 민트(`#7CDA9C`, `#60BE82`) 기반 29개 컬러 토큰(`WearTheme.kt` `EundunWearColorScheme`), `WearEnvironmentSpec.kt` 런타임/타깃 SDK 실측 전달 및 `isRuntimeApiSupported` 판정, `AppScaffold` + `ScreenScaffold`(`!LocalScrollCaptureInProgress.current` 스크롤 인디케이터 가드) + `TransformingLazyColumn` + `SurfaceTransformation` + TalkBack `stateDescription` 기반 `WearEnvironmentStatusScreen` 및 `@Preview(device = WearDevices.LARGE_ROUND)` / `SMALL_ROUND` / `LargeFont(1.24f)` 3종 프리뷰 구현.
  - **리소스 축소 방어 & Capability 양방향 선언**: `app/src/main/res/values/wear.xml`, `wear/src/main/res/values/wear.xml` 정적 Capability 배열 대칭 선언 및 R8 `isShrinkResources = true` 소거 방지용 `res/raw/keep_wear_capabilities.xml`(`tools:keep="@array/android_wear_capabilities"`) 적용.
  - **공급망 락파일 & CI/로컬 가드 확장**: `gradle.lockfile`, `wear/gradle.lockfile`, `core/wear-contract/gradle.lockfile` 및 `gradle/verification-metadata.xml`(`aapt2-9.5.1-16666295-linux.jar` 포함) 동기화, `check-agp10-compat.sh`·`check-version-monotonic.sh`·`preflight-release.sh`·`android-cli-check.sh`·`.githooks/pre-commit`·`.github/workflows/android.yml`·`doc_audit.py` 3모듈 확장.
  - **환경설정 구현 설계 고도화 (`PROP-06-A` Azure 환경 경계 통합)**: `docs/plans/2026-11-06-wear-os-foundation-env-setup-plan.md`에 기존 Azure Container Apps + PostgreSQL 18 재사용 환경 경계(`D9`, 신규 PaaS 0건)와 6대 근본 원인 트러블슈팅/재발 방지 가드(`T1~T6`), 사용자 게이트(`G0~G5`, `A0~A6`)를 통합.
- **Outcome**: Spotless / 3모듈 Detekt(0건) / 3모듈 단위 테스트 266/266 PASS(`WearTimeConstantsTest`, `WearEnvironmentSpecTest`, `AgpBuildContractTest`, `ProguardKeepRulesTest` 포함) / `:app`·`:wear` R8 `assembleRelease` PASS / WSL2 Ubuntu Linux CI 패리티 검증 PASS / `doc_audit.py --strict` 드리프트 0건 통과.
- **Files touched**: `settings.gradle.kts`, `version.properties`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, `gradle.lockfile`, `app/{build.gradle.kts,gradle.lockfile,src/main/res/values/wear.xml,src/main/res/raw/keep_wear_capabilities.xml}`, `core/wear-contract/**`, `wear/**`, `scripts/{check-agp10-compat,check-version-monotonic,preflight-release,android-cli-check}.sh`, `.githooks/pre-commit`, `.github/workflows/android.yml`, `scripts/agents/{doc_audit,test_doc_audit}.py`, `docs/plans/2026-11-06-wear-os-foundation-env-setup-plan.md`, `docs/proposals/2026-11-06-wear-os-integration-architecture.md`, `docs/proposals/2026-11-06-wear-os-azure-backend-architecture.md`
- **설계 원문**: `docs/plans/2026-11-06-wear-os-foundation-env-setup-plan.md`

### 2026-11-06 — AGP(Android Gradle Plugin) 최신 정식 안정판 `9.5.1` 업그레이드 (룰 15 6단계 전수 검증 · 듀얼 플랫폼 `aapt2` 해시 동기화 · SSoT 문서 정합화)

- **Why**: Google Maven 및 Android Studio 인덱스 기준 최신 정식 안정판 AGP `9.5.1`(`com.android.tools.build:gradle:9.5.1`, `aapt2:9.5.1-16666295`)이 릴리스됨에 따라 룰 15(AGP 아키텍처·호환성 매트릭스 및 업그레이드 6단계 거버넌스)에 의거해 빌드 툴체인과 공급망 해시 락을 최신 안정판으로 동기화해야 했다.
- **What**:
  - **AGP `9.5.0` → `9.5.1` 업그레이드 (`gradle/libs.versions.toml`)**: `android studio version-lookup agp`(`Stable: 9.5.1`) 및 Google Maven 공식 메타데이터 실측 후 `agp = "9.5.1"` 적용 (`Gradle 9.8.2` / `Kotlin 2.4.21` / `KSP 2.3.12` / `Hilt 2.60.1` / `JDK 17` 호환).
  - **공급망 락파일 & 멀티 플랫폼(`Windows` + `Linux` + `macOS`) SHA-256 검증 메타데이터 동기화**: `app/gradle.lockfile` 및 `gradle/verification-metadata.xml`을 `--write-locks --write-verification-metadata sha256`으로 갱신하고, Google Maven 공식 `aapt2-9.5.1-16666295-linux.jar`(`fa1014cb...`), `aapt2-9.5.1-16666295-osx.jar`(`a43df4c4...`), `aapt2-9.5.1-16666295-windows.jar`(`0cca2866...`), `.pom`(`fe9320e0...`) 해시를 모두 등재하여 `AgpBuildContractTest` 및 Linux CI(`ubuntu-latest`) 패리티 보장.
  - **SSoT 문서 일괄 동기화**: `CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/TRD.md`, `docs/SPEC.md`의 AGP 버전 표기를 `9.5.1`로 정합화.
- **Outcome**: `check-agp10-compat.sh` OK, `-Pandroid.debug.obsoleteApi=true` 신규 경고 0건, Spotless/Detekt(0건)/Lint/단위 테스트 259/259 PASS(`AgpBuildContractTest` 포함)/`assembleDebug`/`assembleRelease`(R8 Full Mode) 통과, `doc_audit.py --strict` 드리프트 0건 통과.
- **Files touched**: `gradle/libs.versions.toml`, `app/gradle.lockfile`, `gradle/verification-metadata.xml`, `CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/TRD.md`, `docs/SPEC.md`

### 2026-11-06 — 스마일 트레이닝 무중단 직행(`INTRO`·`COMPLETED` 완전 제거) & 프로젝트 전역 Compose(`BOM 2026.09.00`) 신기술 적용·M3 디자인 통일·레거시 청산

- **Why**: ① 스마일 트레이닝 진입 시 중간 대기소(`SmileSessionPhase.INTRO` / `SmileIntroContent.kt`)와 완료 후 정적 페이지(`COMPLETED` / `CompletedContent`)가 각각 2단계 터치를 강제하고 `오늘`·`진행` 탭의 대시보드와 중복되었으며, ② 카메라 HUD·폴더블(`480dp~600dp` 높이) 타원 오버플로우·가이드 화면·`EmptyContent`/`ErrorContent`·`BadgeScreen`·`StatisticsScreen`·`HistoryScreen`·`GoalScreen`·`MealPlanScreen`의 디자인 토큰 편차, ③ Compose `BOM 2026.09.00`(`UI/Foundation 1.12.1`)·`CameraX 1.6.2`·`Lifecycle 2.11.0` 환경에서 레거시 수동 `DisposableEffect`·`FLAG_KEEP_SCREEN_ON`·`ProcessCameraProvider.getInstance` 콜백 잔존, ④ `findActivity()` 4중 중복, `KST_ZONE` 5중 중복, `suspend` 내 일반 `runCatching` 잔존(룰 14 Layer 2), 죽은 `cycleTheme()` 코드 및 미사용 카탈로그 선언을 전사적으로 일괄 해소해야 했다.
- **What**:
  - **Part 1 (스마일 트레이닝 `INTRO`·`COMPLETED` 제거 및 카메라 HUD·가이드 업그레이드)**: `SmileSessionPhase.INTRO`와 `SmileIntroContent.kt`를 완전 삭제하고 4대 진입점 터치 즉시 `onInitialEntry(hasCameraHardware, hasCameraPermission)` → `READY`(1.0초 자동 시작) → `ACTIVE`(2초 × 3회) → `withContext(NonCancellable)` 저장 후 `NavigateBack`(에러 시 `SaveErrorOverlayDialog`) 무중단 루프를 완성. `SmilePhaseGuideContents.kt`(`SmileGuideCardLayout` 72dp 원형 배지 + `RoundedCornerShape(24.dp)` 카드), `SmileCameraStage.kt`(폴더블 안전 `ovalHeight <= size.height * 0.44f` 클램프, `FaceOvalVignetteOverlay` 색상 보간·외곽 글로우, `RepStepIndicatorRow` 폭·색상 보간, `BottomCoachingOverlay` `animateContentSize` + `AnimatedVisibility`), `ProgressHubScreen.kt`(누적 연습 시간 칩 통합) 고도화.
  - **Part 2 (Jetpack Compose `BOM 2026.09.00` 신기술 전수 적용)**: `Modifier.keepScreenOn()`(`SmileCameraStage`, `WorkoutGuideScreen`), `ProcessCameraProvider.awaitInstance(context)` + `awaitCancellation()`(`SmileCameraStage`), `LifecycleStartEffect`/`LifecycleResumeEffect`(`SmileCameraStage`, `SmileTrainingScreen`, `AppNavigation`, `HomeScreen`, `ProgressHubScreen`, `ProfileHubScreen`), `TextAutoSize.StepBased`(`ProfileHubScreen`, `ProgressHubScreen`, `StatisticsScreen`), `MotionTokens.quickSpring()`/`progressSpring()` 확장 및 전 화면 모션 토큰 통일.
  - **Part 3 (전역 화면 & 컴포넌트 M3 디자인 시스템 통일)**: `EmptyContent`/`ErrorContent`(72dp 원형 배지 컨테이너, 52dp `RoundedCornerShape(16.dp)` 버튼), `BadgeScreen`(`BadgeCollectionSummaryHeader` 수집 현황 요약 카드 + 48dp 원형 배지 아이콘 + `RoundedCornerShape(20.dp)`), `StatisticsScreen`·`HistoryScreen`·`GoalScreen`·`MealPlanScreen` 카드/버튼/모션 토큰 일원화.
  - **Part 4 (전역 레거시 및 기술 부채 청산)**: `ui/util/ContextExt.kt`(`findActivity()`), `domain/model/TimeConstants.kt`(`KST_ZONE`, `formatDurationKorean`) SSoT 통합, `HealthRepositoryImpl`·`ProfileHubViewModel` `runSuspendCatching`(룰 14 Layer 2) 교체, `MealPlanRepositoryImpl.toDomain()` 단일화, `ProfileHubViewModel.cycleTheme()` 데드코드 제거, `gradle/libs.versions.toml` 미사용 `material3Adaptive`·`hilt-navigation-compose` 정리, `@Preview` `EundunHealthTheme` 통일.
- **Outcome**: Spotless/Detekt(0건)/AGP 10 호환성 가드/단위 테스트 259/259 PASS/`compileDebugAndroidTestKotlin` PASS/`doc_audit.py --strict` 드리프트 0건 통과.
- **Files touched**: `gradle/libs.versions.toml`, `app/src/main/java/com/gunnys/eundunhealth/domain/{model/TimeConstants,smile/SmileSignalEvaluator}.kt`, `app/src/main/java/com/gunnys/eundunhealth/data/{healthconnect/HealthConnectDataSource,local/dao/SmileSessionDao,repository/{HealthRepositoryImpl,MealPlanRepositoryImpl,SmileTrainingRepositoryImpl,WorkoutRepositoryImpl},smile/MlKitSmileSignalAnalyzer}.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/{auth/AuthGateScreen,badge/BadgeScreen,components/*,goal/GoalScreen,history/HistoryScreen,home/*,mealplan/MealPlanScreen,navigation/*,profile/*,smile/*,statistics/StatisticsScreen,theme/MotionTokens,util/ContextExt,workout/*}.kt`, `app/src/test/java/com/gunnys/eundunhealth/**`, `app/src/androidTest/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreenTest.kt`

### 2026-10-29 — 앱 업데이트 감지 및 강제 안내 시스템 고도화 (`Strict Mandatory Update`: Google Play In-App Updates `2.1.0` + 포그라운드 `ON_START` 재검증 + 버전 SSoT 자동화, v0.7.3/44)

- **Why**: 신규 프로덕션 버전(`v0.7.2/43`) 출시 후에도 기존 설치 앱(`v0.7.0/41`, `v0.7.1/42`)을 재실행했을 때 업데이트 안내가 표시되지 않던 5대 구조적 결함(① 백엔드 `MINIMUM_SUPPORTED_ANDROID_VERSION_CODE` 4중 드리프트 및 번프 스크립트 미연동, ② 런타임 `403 client_update_required` 수신 시 `ErrorContent` 스토어 이동 버튼 부재, ③ `AuthViewModel.init` 1회 한정 체크로 인한 웜 스타트 미감지, ④ 단말 Google Play In-App Updates 미연동, ⑤ `UpdateRequiredScreen` 미사용 파라미터 및 죽은 주석 잔존)을 근본 해결해야 했다.
- **What**:
  - **Android 이중 채널 강제 업데이트 감지 & `ON_START` 재검증**: `com.google.android.play:app-update:2.1.0` 및 `app-update-ktx:2.1.0`을 도입해 `PlayInAppUpdateChecker`(`UPDATE_AVAILABLE` 및 중단된 업데이트 `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` 감지, `AppUpdateType.IMMEDIATE` 전체화면 시트 실행, 룰 14 준수 `by lazy` 초기화)를 구현하고, `AuthViewModel.onAppForegrounded()`(30초 쿨다운 + 오프라인 실패 시 즉시 재시도) 및 `AppNavigation` `Lifecycle.Event.ON_START` 옵저버를 연결하여 콜드 스타트와 포그라운드 재진입 모두에서 즉시 `SessionState.UpdateRequired`로 전환.
  - **런타임 `403` 스토어 이동 CTA 및 레거시 제거**: `ErrorContent`에서 `AppError.ClientUpdateRequired`일 때 `"Google Play에서 업데이트"` 버튼을 노출하고, `UpdateRequiredScreen`의 미사용 `minimumVersionCode` 파라미터 및 `app/build.gradle.kts` 죽은 주석을 제거.
  - **버전 SSoT 자동화 & 테스트 하드코딩 제거**: `scripts/bump-version.sh`가 백엔드(`config.py`, `containerapp.yaml`, `docker-compose.yml`), 배포 검증기(`verify_containerapp_deployment.py`), 문서(`TRD.md`, `operations-snapshot.md`)의 최소 지원 버전을 원자적으로 함께 치환하도록 확장하고, `scripts/check-version-monotonic.sh`에 SSoT 불변식 검증 가드를 추가. `conftest.py`, `test_weekly_plan.py`, `test_profile.py`의 버전 숫자 하드코딩을 `get_settings().minimum_supported_android_version_code` 동적 참조로 리팩토링.
- **Outcome**: Backend pytest 147/147 PASS, Android Spotless/Detekt(0건)/AGP 10 가드/단위 테스트(`AuthViewModelTest`, `PlayInAppUpdateCheckerTest` 신규 9건 포함) 전수 PASS, 버전 SSoT(`44`) 및 단조성 가드 통과.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/update/PlayInAppUpdateChecker.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/components/ErrorContent.kt`, `app/src/main/java/com/gunnys/eundunhealth/di/RepositoryModule.kt`, `backend/app/config.py`, `backend/containerapp.yaml`, `backend/docker-compose.yml`, `backend/tests/{conftest,test_weekly_plan,test_profile}.py`, `scripts/{bump-version,check-version-monotonic}.sh`, `scripts/verify_containerapp_deployment.py`
- **설계 원문**: `2026-10-29-mandatory-app-update-design.md`, `2026-10-29-mandatory-app-update-plan.md` (본 entry 로 흡수, git rm)

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
