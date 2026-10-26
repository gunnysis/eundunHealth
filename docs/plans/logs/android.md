# Android 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

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
