# eundunHealth 작업 내역서

> 형식: 큰 변화 순서대로 위에서 아래로. 각 릴리스의 세부 커밋은 git log 참조.

---

## [v0.1.19] — 2026-07-03 — Android CD 첫 자동 출시(내부 트랙) + 의존성 배치

> P4 Android CD(`release.yml`, PR #143)의 첫 실 e2e 릴리스 — 태그 `v*` push → environment `play-release` 승인 → preflight 전체 게이트(룰 2·13·Sentry 매핑) → Play **내부 트랙** 자동 업로드 → 원장 자동 갱신 커밋. 사용자 가시 동작 변화 없음(의존성 업데이트 + 빌드/파이프라인). **같은 날(2026-07-03) Console 수동 승격으로 프로덕션 반영.**

### 🚀 릴리스 파이프라인
- 태그 push → Play 내부 트랙 자동 업로드 경로(`release.yml`) 첫 실전 사용. dry-run 3차 green(빌드 8m40s 실측) 후 본 릴리스로 실 업로드 검증.

### 📦 의존성 (v0.1.18 빌드 이후 누적 — #139·#132)
- sentry-gradle 6.11.0→6.12.0, Compose BOM →2026.06.00, lifecycle →2.11.0, Gradle →9.6.0, actions/checkout →v7.

### 🔧 빌드/스크립트
- release 서명 keystore **존재-조건부화**(INC-2026-07-02-29 — CodeQL autobuild 가 clean checkout 에서 release variant 빌드 실패하던 문제; keystore 없으면 unsigned 폴백 + preflight 서명 fail-fast 가드).
- `bump-version.sh` 잉여 인자 거부 — `--dry-run` 을 버전 뒤에 붙이면 조용히 무시되고 실제 적용되던 footgun 차단.
- preflight Sentry 토큰 폴백의 `set -e`/pipefail 무출력 즉사 수정(`ec7535c`) — 토큰 없는 로컬 사용자도 겪던 잠복 버그.

### 🔢 버전
- versionName 0.1.18 → 0.1.19, versionCode 32 → 33.

---

## [v0.1.18] — 2026-06-19 — 출시 재업로드 + versionCode 단조성 가드

> **🎉 2026-06-29 Google Play 프로덕션 정식 출시(LIVE)** — 본 버전(v0.1.18/32)으로 출시·승인 완료. 이후 2026-07-02 repo public 전환(보안감사·식별자 스크럽 PR #137 + secret scanning·push protection·CodeQL 활성) + CodeQL 대응 release 서명 존재-조건부화(INC-2026-07-02-29, 앱 산출물 변화 없음).
>
> v0.1.17 업로드가 Play "이미 사용된 버전 코드 31" 로 거부됨(INC-2026-06-19-28) → versionCode 32 로 재빌드 + 재발 방지 인프라. **앱 동작 변화 없음** — v0.1.17 빌드와 동일, versionCode/versionName 만 상이.

### 🚦 릴리스 파이프라인 — versionCode 단조성 가드
- `docs/ops/play-upload-ledger.md` 신설 — Play 에 이미 업로드된 최고 versionCode(`LAST_UPLOADED_VERSION_CODE=31`) SSoT. 업로드 성공마다 갱신.
- `scripts/check-version-monotonic.sh` 신설 — 후보 versionCode 가 원장 최고값보다 큰지 검증(≤ 면 fail-fast). `preflight-release.sh`(빌드 전) + `bump-version.sh`(번프 시) 배선.
- `CLAUDE.md` 룰 13 + `docs/conventions/versioning.md §3` 명문화.

### 🔢 버전
- versionName 0.1.17 → 0.1.18, versionCode 31 → 32 (Play 중복 거부 해소).

---

## [v0.1.17] — 2026-06-18 — 공개 출시 전 전체 감사

> 7-도메인 공개 출시 전 전체 점검(보안·성능·에러UX·테스트·의존성·Play 컴플라이언스·코드품질). 출시 차단 없음. 브랜치 `fix/pre-release-audit`. 설계: `docs/plans/2026-06-18-pre-release-full-audit-{design,plan}.md`.

### 🛠️ Android — Rule 8 inline 에러 배너 완전 적용
- **OnboardingScreen**: 프로필 저장 실패 시 `Snackbar` → `AuthErrorBanner`(inline persistent + `liveRegion=Polite` + Sentry breadcrumb). `OnboardingUiState.error: AppError?` 신설.
- **HomeScreen**: 완료 토글 실패 시 `Snackbar` → `AuthErrorBanner`(LazyColumn 내 item). `HomeUiState.Success.toggleError: AppError?` 신설. `HomeSideEffect`에서 `ShowSnackbar` 제거.
- **ProfileScreen**: 프로필 저장·계정 삭제 실패 시 `Snackbar` → `AuthErrorBanner`. `ProfileUiState.Loaded.saveError / deleteError: AppError?` 신설.

### 🔡 Android — a11y
- **HistoryScreen**: 완료/미완료 아이콘에 `contentDescription = if (day.isCompleted) "완료" else "미완료"` 추가(TalkBack 접근성).

### 🧪 테스트
- **BadgeViewModelTest** 신설(3건): 카탈로그 9개 Loaded·획득 표시·로드 실패 Error.
- **OnboardingViewModelTest** 업데이트: `ShowSnackbar` 검증 → `uiState.error != null`.
- **ProfileViewModelTest** 업데이트: `ShowSnackbar` 검증 → `saveError/deleteError != null`.
- **백엔드** `test_edge_cases.py` 2건 추가: `weightKg > 500` → 422, `heightCm > 300` → 422.

### 📝 문서 드리프트 정정
- CLAUDE.md / README.md / TRD.md: App 0.1.15/29→0.1.16/30, Sentry Android 8.43.1→**8.43.2**, FastAPI 0.136.1→**0.137.1**, SQLAlchemy 2.0.50→**2.0.51**, Sentry SDK 2.61.1→**2.63.0**, Alembic head c849579de6c4→**b78b256c2b20** (SSoT 실측 정정).

### 🔧 Backend 개선
- `account_service._delete_supabase_user`: Supabase 삭제 실패 로그를 구조화 (`%s` 포맷 + JSON 파싱 분기).

---

## [v0.1.16] — 2026-06-17 — 출시 후 심층 감사 개선

> v0.1.15 출시 사이클 후 5-도메인 심층 재감사(Android/Backend/테스트/의존성/UX). 공식 문서 fact-check 로 감사 발견 2건 정정. 코드 건강·출시 차단 0건 — 신뢰성·성능·접근성·테스트 폴리시. 브랜치 `feature/deep-audit-improvements`. 설계: `docs/plans/2026-06-17-post-release-audit-improvements-{design,plan}.md`.

### 🛠️ Tier 1 — 개선 (A~E)
- **A (Backend)**: JWKS 서명키 동기 조회를 `asyncio.to_thread` 로 이벤트 루프 밖으로 오프로드 + `PyJWKClient` timeout 30s→5s. 콜드스타트·키 로테이션 시 루프 정지 리스크 제거. (공식 PyJWT API 확인: 기본 timeout 은 무한대가 아니라 30s — 감사 보고 정정)
- **B (Test)**: `RetryInterceptor` 단위 테스트 6건 신설 — 모든 백엔드 호출 경로(재시도/백오프/누수) 가드.
- **C (UX)**: `GoalScreen` 이 네트워크 실패를 "데이터 없음"으로 오표시하던 silent failure 제거 → `ErrorContent`(재시도) + `GoalViewModelTest`.
- **D (Perf)**: `DayPlanCard` 의 매-recomposition locale 포맷팅을 `remember(day.date)` 로 hoist.
- **E (A11y)**: "오늘의 활동" 이모지-only 지표를 `mergeDescendants` + `clearAndSetSemantics` 로 TalkBack 가독화.

### 🧪 Tier 2 — 테스트·신뢰성·성능
- 무테스트 ViewModel 4종(Profile/History/Statistics/Onboarding) 특성화 테스트 추가 — 계정삭제·페이지네이션 경계 등 고위험 로직 가드 (@Test 118→138).
- 백엔드 DB 풀 `pool_pre_ping=True` — warm baseline 인스턴스 idle 후 끊긴 연결 first-request 500 방지.
- **history COUNT 1-쿼리화** — 페이지 SELECT + 별도 `COUNT(*)` 2-round-trip → `count(*) over()` window(빈 페이지만 count 폴백) + 멀티페이지 경계 테스트.
- **`user_profile_history (user_id, recorded_at)` 복합 인덱스** — 진행 차트 정렬 step 제거(alembic `b78b256c2b20`, 룰 7 PG 컨테이너 + entrypoint 실증). 단일방향 DESC 는 backward scan 으로 커버되어 DESC 수식어 불필요, 기존 단일 user_id 인덱스 제거(복합 prefix 가 커버).
- **계정삭제 orphan reaper** — Auth엔 없고 DB엔 남은 고아 데이터를 청소하는 안전망. `reap_orphaned_data()`(fail-safe: Auth 404 확정만 purge) + `_purge_app_data` DRY 추출 + `scripts/reap_orphaned_accounts.py`.

### 🧹 Tier 3 — housekeeping
- `sentry-sdk` 2.62.0→2.63.0 · `requirements.txt` MAL 주석 실제 핀(0.137.1) 정합 · i18n 한국어 하드코딩이 의도된 결정임을 CLAUDE.md 명문화.

### 🚫 Won't-do / 후속
- **Compose stability config**: strong skipping 기본 활성(Kotlin 2.0.20+)으로 불필요(공식 확인) — 감사의 MED 보고 정정.
- **후속**: orphan reaper 의 Container Apps Job(cron) wiring(스크립트는 포함, 잡 프로비저닝만 분리). 출시 전이라 스키마 변경 OK 판단으로 당초 defer 했던 T2b/T2c/T2e 는 본 PR 에 모두 포함.

### ✅ 검증
- Backend pytest **73 PASS** · ruff · mypy · bandit · pip-audit clean. Android **@Test 138** · detekt · spotless green. versionCode 29→30.

---

## [main] — 2026-06-16 — Dependabot PR 6개 triage

> open dependabot PR 6개 일괄 정리 — CI 상태 + 호환성 기준 머지 3 / 닫기 3.

### ✅ 머지 (3개)
- **Sentry Android 8.43.1 → 8.43.2** (#120, 패치)
- **MockK 1.14.9 → 1.14.11** (#121, 패치)
- **Backend minor-patch 6개** (#124): fastapi 0.136.1→0.137.1 · sqlalchemy 2.0.50→2.0.51 · sentry-sdk 2.61.1→2.62.0 · pytest 9.0.3→9.1.0 · ruff + pip-audit 패치

### 🚫 닫기 (3개, 사유 코멘트 후)
- **Kotlin 2.4.0 + KSP + coroutines** (#117) — Hilt 2.59.3+ 및 build.gradle.kts DSL 마이그레이션 선행 필요
- **Coil 3.5.0** (#118) — Kotlin 2.4.0 내부 사용으로 동일 차단
- **openapi-generator 7.23.0** (#119) — 13 minor 점프, Android 생성 클라이언트 코드 변동 별도 검토 필요

`docs/ops/dependency-deferred.md §1` 갱신(신규 close PR 추가) + §2 신설(openapi-generator 보류).

---

## [main] — 2026-06-16 — Sentry 알림 스크립트 점검·재발방지 개선

> `scripts/setup-sentry-alerts.ps1` 첫 실행 시 8개 룰 전부 404 실패 → 근본원인 5개 분석·수정·주석화 + 구조 개선. 이후 스크립트 정상 실행 완료, 8개 알림 룰 Sentry UI 활성 확인. 커밋 `d18e335`.

### 🐛 버그 수정 (5건)

- **B1 — PowerShell 대소문자 충돌**: `$org`(API 응답 수신 변수)와 `$ORG`(org 슬러그 상수)가 PowerShell 변수명 case-insensitive 특성으로 충돌 → URI에 PS 객체가 삽입되어 전체 404. 수정: API 응답은 `$orgInfo`로 분리, 모든 상수는 `$script:` 명시 접두어 사용.
- **B2 — environment="production" 404**: 첫 이벤트 수신 전 Sentry에 "production" 환경이 등록되지 않아 거부. 수정: `environment` 필드 생략(출시 후 UI Edit에서 추가하도록 안내 주석).
- **B3 — interval="30m" 무효 값**: Sentry API가 비표준 인터벌 거부. 유효값: `1m/5m/10m/1h/4h/24h/1w`. 수정: `"1h"` 사용 + 주석 명시.
- **B4 — dataset="transactions" deprecated**: Sentry가 spans(`events_analytics_platform`)로 마이그레이션 완료. 수정: `dataset="events_analytics_platform"`, `query="is_transaction:true"`, `p95(span.duration)`.
- **B5 — targetType="team" 솔로 프로젝트 부적합**: 팀 ID(`targetIdentifier`) 필수인데 팀 없음 → 404. 수정: `targetType="user"` + `targetIdentifier=<sentry-user-id>`(Sentry user.id, 멤버 .id 아님).

### ✅ 구조 개선

- `param([switch]$DryRun)` + `Set-StrictMode -Version Latest` + `$ErrorActionPreference = "Stop"` 적용.
- GET 기반 idempotency: 에러 응답 메시지 파싱 대신 기존 룰 목록 조회 후 동일 이름 skip — 신뢰성 ↑.
- `Get-SentryErrorDetail` 공통 헬퍼(null guard 포함) — 에러 메시지 추출 중복 제거.
- 설정 상수 블록 단일화(`$script:ORG_SLUG` 등), 파일 상단에서 5줄 수정으로 프로젝트 이관 가능.
- B1~B5 재발방지 주석 블록, 버그 이력 영구 기록.

### 🗑 정리

- Sentry가 B5 디버깅 중 자동 생성한 잘못된 Priority Notification 룰 2개(#3589906, #3589907) UI에서 삭제.

### ✅ 검증

- 8개 알림 룰 전부 Sentry UI 활성 확인: Issue Alert 6개(Android 신규·회귀·빈도급증 / Backend 신규·회귀·빈도급증) + Metric Alert 2개(Backend p95·에러율).
- `-DryRun` 플래그로 실행 계획 사전 검증 후 실 실행 성공.

---

## [v0.1.15] — 2026-06-16 — 감사 LOW 후속 (내부 품질, 사용자 가시 동작 변화 없음)

> 출시 준비 감사(PR #122)에서 보류한 LOW 항목 중 권장 3건 구현 + 차단된 의존성 CVE 동반. PR #123 (squash `078a24fb`). versionCode 29. 백엔드 자동배포·CORS live 검증 완료, Android Play 업로드 대기.

### ✅ 변경
- **① SideEffect 수집 라이프사이클-aware** — 7개 Screen 이 `LaunchedEffect(Unit){ sideEffect.collect{} }` 로 컴포지션 전 생애 수집(STOPPED 중 도착 이벤트 즉시 소비) → 공식 패턴 `repeatOnLifecycle(STARTED)` 재사용 헬퍼 `ObserveAsEvents` 추출. Channel(BUFFERED) 버퍼가 화면 보일 때 전달. 룰 11 정합·향후 화면 자동 안전.
- **③ alembic rest_day server_default 환경 일관화(B6)** — 초기 마이그레이션이 server_default 없이 생성 → fresh DB 분기. forward 마이그레이션 `c849579de6c4`(ALTER SET DEFAULT 7, idempotent) + 모델 server_default. **head = c849579de6c4**. runtime-smoke(PG)로 룰7 검증, live 적용.
- **④ CORS 와일드카드 차단** — `allow_origins=['*']` → `config.py` 기본 `[]` + `containerapp.yaml` `[]`(--yaml 배포 = env 단일출처). 웹 cross-origin 표면 없음(네이티브 앱+App Links). live 검증: 임의 origin 에 ACAO 헤더 없음. 회귀가드 `test_cors_does_not_allow_arbitrary_origin`.
- **starlette 1.2.1 → 1.3.1** — CI pip-audit 가 신규 CVE(GHSA-82w8-qh3p-5jfq, GHSA-jp82-jpqv-5vv3) 검출 → bump(PR 무관하나 차단 픽스). module-level CORS 라 룰4 무관.

### ⏭ 스킵
- **② AppError 403 매핑** — 앱의 403 은 전부 인증 성격(HTTPBearer 헤더 부재), authz-403 엔드포인트 없음, 401→refresh/403→no-refresh 동작 정확 → 현행 적정.

### ✅ 검증
- 백엔드 pytest 71 passed(+CORS 가드), ruff/mypy clean · Android 전체 단위테스트 GREEN, spotless/detekt clean · PR #123 CI 전 job pass(Security/runtime-smoke/Android/백엔드)
- preflight: AAB + APK + Sentry 매핑 `1e11310d`

---

## [v0.1.14] — 2026-06-15 — 출시 준비 종합 (제보 2버그 근본수정 + 전수감사 + 재발방지)

> 실기기(Galaxy Z Flip3) 제보 2건에서 시작 → 4-에이전트 병렬 전수감사로 출시 차단 요소 발굴 → 클러스터별 TDD 수정 + 실기기/단위 검증 + 재발방지 가드. PR #122 (squash `e2d7460`). versionCode 28. 백엔드 자동배포 완료(`manual`/`manuallySet` live), Android Play 업로드 대기.

### 🎯 제보 2버그 (근본 수정)
- **운동 계획에 운동이 안 보임** — 근본원인: proguard 가 `ExerciseDto` 만 keep 하고 Gson 응답 래퍼 `ExerciseListResponse`/`PageMeta` 누락 → 릴리스 R8 이 제거(mapping·usage.txt·타 모델 @SerializedName 삼중 확인) → `data` 가 기본 `emptyList()` 폴백 → 빈 계획이 결정론적으로 생성·저장·고착(릴리스 전용 silent). → 패키지 단위 keep + 빈계획 미저장·자가치유. Flip3 실기기 검증.
- **체크 해제 후 새로고침하면 다시 체크됨** — `SyncHealthDataUseCase` 가 HC 운동기록 있는 날을 매번 재완료. → 백엔드 `CompletionRequest.manual` + day `manuallySet` 박제 + Android 수동 우선 skip + 토글 전송 직렬화. live 백엔드로 Flip3 e2e 검증(해제→새로고침 유지).

### ✅ 전수감사 클러스터
- **완료 정합성(HIGH)** — 통계 `isCompleted` 통일(빈 운동일 이력왜곡 해소) + 완료 PATCH 행잠금(lost-update) + 클라 토글 직렬화
- **입력검증(HIGH)** — 잘못된 day_offset/날짜/dayPlans → 500 대신 400 + write 검증
- **인증 견고화(HIGH)** — 토큰 갱신 `synchronized` + 이미-갱신 재시도 + 일시실패 토큰보존(간헐 강제 로그아웃 방지). `SessionRefresher` 분리
- **운동 상세** — GIF 정지프레임 복구(Coil 싱글톤 연결) + 방법 복사/선택 + 데이터흐름 로컬화(`getExerciseById`)
- **캐시/파싱/KST(MED)** — read 캐시갱신 + 알 수 없는 type 폴백 + weekStart KST 고정
- **폴리시(LOW)** — BadgeRepo @Singleton / GoalRepo silent-drop 관측화 / 계정삭제 orphan 로깅

### 🛡 재발방지
`ProguardKeepRulesTest` + CLAUDE.md 룰 12(Gson keep 패키지 단위) · `TokenAuthenticatorTest`(동시401→refresh 1회 등) · `SyncHealthDataUseCaseTest`(수동우선 skip) · `HomeViewModelTest`(토글 직렬화) · `PlanJsonModelsTest`(type 폴백) · 백엔드 통계·검증·manual 테스트

### 검증
- 백엔드 pytest **61 passed**(ruff/mypy clean) · Android 전체 단위테스트 GREEN(spotless/detekt clean) · CI 전 job pass
- Flip3 실기기: 운동 표시 / GIF 애니메이션 / 방법 복사 / 토글 해제 보존
- 보류 LOW(SideEffect 라이프사이클·403 매핑·alembic server_default·CORS)는 PR #122 에 근거 명시

---

## [v0.1.13] — 2026-06-11 — 코드베이스 리팩토링 (내부 품질, 사용자 영향 없음)

> 진단(4-영역 병렬 감사) → 다관점 설계(공식문서 fact-check) → subagent-driven 자율 실행 → 5 번들 6 PR(#107~#112) 머지. 사용자 기능/동작 변화 없음. versionCode 27. 첫 프로덕션 트랙 출시 빌드.

### ✨ 변경 (번들별 PR)
- **A (#110)** 핵심 알고리즘 분리: `WorkoutRepositoryImpl.createWeeklyPlan` 의 주간계획 생성 로직을 순수 `WeeklyPlanGenerator` 로 추출(line-for-line 동일·JVM 단위테스트 5건) + 죽은코드 4건 제거(savePlanToServer·deleteOldPlans·inert 404·미사용 default).
- **B (#108)** 백엔드 실버그: JWT 검증 `except` 좁히기(JWKS/인프라 장애를 401로 은폐 방지 — `PyJWKClientError`→503) + goal `createdAt` flush/refresh(조작된 `datetime.utcnow()` 제거) + stale 라우터 docstring 정정 + openapi 재싱크. pytest 54.
- **D (#107)** 위생: detekt baseline 단일화(이중 baseline footgun 해소) + `bodyOrNull404` 헬퍼 + NetworkModule 상수화 + `hiltViewModel` deprecation(androidx hilt 1.3.0) 마이그레이션 12파일.
- **E (#109)** 도메인 정합: `UserProfile` body metrics `Float?`(백엔드 nullable 계약·`Goal`/`ProfileHistoryPoint` 일치) + 0f fabrication 제거.
- **C (#111)** UI 중복 제거: 공유 `LineChart`(composition `runBlocking` 제거 — Vico 공식 LaunchedEffect 패턴) + `ResendConfirmationController`(합성) + `toAppErrorReporting()` + `BodyMetricsSliders` promote.

### ✅ 검증
- 통합 main 게이트 green (Android `BUILD SUCCESSFUL` + Backend `54 passed`). 신규 테스트 15건(generator 5·dependencies 4·goal_repo 1·UserProfile 3·ResendController 2).
- 번들별 spec 검수 + 룰 10 게이트 독립 재확인. 상세 ledger: `docs/plans/logs/{android,backend,process-infra}.md`.
- 릴리즈 인프라: `preflight-release.sh` 버전 출처 정정(build.gradle.kts 리터럴 → `version.properties`, PR #102 이후 stale).

---

## [v0.1.12] — 2026-06-11 — Health Connect 체성분 가져오기 제거 (수동 단일화)

### 🎯 Prompts
1. "'HC 가져오기'는 무소용이니까 제거하는 게 좋을 것 같은데 너 생각은 어때?" → A안(보조 편의)에서 B안(제거)로 전환 승인
2. "권장 방식(subagent-driven) 연구해서 구현 + 리뷰/승인 없이 끝까지"

### ✅ Changes
- **HC 체성분 가져오기 완전 제거** — 프로필 "Health Connect에서 체중·체지방 가져오기" 버튼 + `ImportBodyCompositionUseCase` + `BodyComposition` 모델 + `readLatestBodyComposition`/`hasBodyCompositionPermissions`/`BODY_COMPOSITION_PERMISSIONS` + `reduceBodyComposition` 매퍼 + `PrefillBodyComposition` SideEffect + `canImportBodyComposition`.
- **권한 회수** — 매니페스트 `READ_WEIGHT`·`READ_BODY_FAT` 제거 → 건강권한 **6→4**(EXERCISE/STEPS/TOTAL_CALORIES_BURNED/HEART_RATE). 건강권한 표면·Play 심사·프라이버시 축소.
- **문서 정합성** — `PermissionsRationaleActivity`·`docs/store/privacy-policy.md`·`docs/store/health-connect-permissions.md`(Play Console 권한 선언서)에서 체성분 읽기 문구 제거 → 4권한.
- **신체 4지표(키·몸무게·골격근량·체지방률) 수동 슬라이더 단일화.**
- **불변** — 활동 HC(걸음·칼로리·심박·운동), 목표 "체중 추이"/체지방 차트(백엔드 이력), `bmi`/`fitnessLevel` 알고리즘, 백엔드.

### 근거 / 연구
HC 체성분 가져오기는 **구조적으로 무용**: HC에 골격근량 타입 부재(공식), 체지방 삼성헬스→HC 동기화 flaky, 스마트체중계 없는 대다수 무데이터 → 영구 "기록 없음". 공식·외부 문서 분석([HC 데이터타입](https://developer.android.com/health-and-fitness/health-connect/data-types) · [Samsung Developer HC 동기화](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect) · [Google Health Help](https://support.google.com/android/answer/13770320)) 후 제거 결론. 삼성헬스 Data SDK 직접 연동은 파트너 부재로 기각, HC-only 유지. design+plan: `docs/plans/2026-06-10-body-composition-data-{design,plan}.md` (B안).

### 📁 주요 Files
- 삭제: `domain/usecase/ImportBodyCompositionUseCase.kt`(+테스트), `domain/model/BodyComposition.kt`
- 수정: `ui/profile/ProfileViewModel.kt`·`ProfileScreen.kt`, `domain/repository/HealthRepository.kt`+`data/repository/HealthRepositoryImpl.kt`, `data/healthconnect/HealthConnectDataSource.kt`·`HealthConnectMappers.kt`(+테스트), `AndroidManifest.xml`, `PermissionsRationaleActivity.kt`, `docs/store/privacy-policy.md`·`health-connect-permissions.md`
- 릴리스: `version.properties`(0.1.11→**0.1.12**/26), `README.md`, `docs/PRD.md`, `docs/ops/operations-snapshot.md`, `CLAUDE.md`

### 검증 / 프로세스
spotlessCheck·detektDebug·testDebugUnitTest·assembleRelease green + grep 무참조 + 최종 코드리뷰(subagent) CLEAN. **subagent-driven 자율 실행**(구현 subagent + controller fact-check + 리뷰 subagent). PR #105 — 처음 #104 위 stacked 로 생성됐다가 #104 squash 머지 시 base 삭제로 CLOSE → main rebase(`--onto main`) 후 재개. **Lesson**: stacked PR base 를 `--delete-branch` 로 머지하면 의존 PR 이 자동 CLOSE 된다(retarget 아님) → 의존 PR 먼저 retarget 후 머지하거나 base 미삭제.

---

## [v0.1.11] — 2026-06-10 — Health Connect Android 14+ 수정(연동 버튼 무반응 + 읽기 실패) + Play Store 계정 삭제

### 🎯 Prompts
1. "내부 테스트로 최신 aab 파일로 설치된 실기기에서 앱의 연동 버튼 무반응 현상 발생 점검 작업. 현재 안드로이드 스튜디오에 해당 실기기 연결되어 있어."
2. "작업할 때 리팩토링/성능/디자인/테스트와 디버깅(근본 원인 해결)/팩트체크/연구/공식 문서 검색 등 다양한 관점에서 작업해줘."

### ✅ Changes

#### fix(android): Health Connect 권한 rationale intent — Android 14+ "연동 버튼 무반응" (실측 Android 15)
- **근본 원인**: Android 14+(API 34+) 통합 Health Connect 는 권한 grant 화면을 띄우기 전 요청 앱이 rationale(개인정보 처리방침) intent 를 resolve 할 수 있는지 검사한다. 매니페스트의 `ViewPermissionUsageActivity` alias 가 14+ 액션(`android.intent.action.VIEW_PERMISSION_USAGE` + category `android.intent.category.HEALTH_PERMISSIONS`) 대신 레거시 액션(`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`)만 선언 → controller 가 `E PermissionsActivity: App should support rationale intent, finishing!` 로 grant 화면을 그리기 직전 finish → 사용자에겐 깜빡임/무반응. (개발 테스트 기기 Galaxy S9+/Android 10 은 레거시 경로라 통과 → 14+ 실사용 기기에서만 발현.)
- **수정**: 14+ alias 를 올바른 액션+카테고리로 정정 + ≤13 레거시 경로(`PermissionsRationaleActivity` 의 `ACTION_SHOW_PERMISSIONS_RATIONALE` intent-filter) 별도 유지(minSdk=26). 전용 `PermissionsRationaleActivity`(앱 내 개인정보 안내 화면, `docs/store/privacy-policy.md` §1 동기화) 신설 — alias 의 targetActivity.
- **회귀 가드**: `ManifestHealthConnectRationaleTest` — 소스 매니페스트를 파싱해 14+/≤13 두 intent-filter 존재를 검증(Robolectric 불요). 수정 전 14+ 케이스 FAIL → 수정 후 PASS.
- **검증**: 실사용 타깃 Android 15(Galaxy Flip3)에서 fixed release APK 설치 후 권한 화면 정상 렌더링(`Displayed …PermissionsActivity`) + 이전 에러 로그 0건 실측 확인.
- 공식 문서 fact-check: [Health Connect get-started](https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started) 의 14+/≤13 manifest 선언과 일치.

#### fix(android): 런처 아이콘 정상화 — Android 14+ Health Connect "읽기 실패" 해소 (실측 Android 15)
- **근본 원인**: 런처 아이콘이 색상-only 어댑티브(`<foreground>`=`@color`, density PNG 0개) → `AdaptiveIconDrawable` intrinsic 크기 **-1**. Android 14+ HC 는 읽기마다 access-log 에 호출 앱 아이콘을 저장하려 `AppInfoHelper.getBitmapFromDrawable(getApplicationIcon())` → `createBitmap(-1,-1)` → `IllegalArgumentException: width and height must be > 0` → `readRecords`/`aggregate` throw. 결과: "오늘의 활동" 빈값 + 체성분 가져오기가 "체성분을 가져오지 못했습니다". (HC 가 별도 APK 인 ≤13 기기엔 이 access-log 경로가 없어 정상.)
- **수정**: `assets/app-icon.svg`(덤벨)를 실제 어댑티브 아이콘으로 적용 — `drawable/ic_launcher_foreground.xml`(VectorDrawable 108dp → intrinsic 크기 확보) + `ic_launcher_monochrome.xml`(테마 아이콘) + `@color` 녹색 배경 + density mipmap PNG(mdpi~xxxhdpi, `ic_launcher`/`ic_launcher_round`, master 512px 다운스케일). 단색 placeholder → 실제 브랜드 아이콘 + 버그 해소를 동시 달성.
- **검증**: fixed release APK 재설치(권한 보존) 후 HC 읽기 실행 시 `width and height must be > 0` / `AppInfoHelper.getBitmapFromDrawable` / HealthConnectService 예외 **모두 0건**, `HealthConnectRecordHelper` 읽기 정상 실행 실측. 잔여 `HCReadAccessLogsHelper: invalid package name` 로그는 감사 엔트리 skip 일 뿐 비치명적(읽기 성공).

#### feat: Play Store 계정 삭제 페이지 + 계정 삭제 완전성 수정 (`af6b99e`)
- `docs/store/account-deletion.md` 신규 — Google 요구 3요소(앱/개발자명·삭제 단계·삭제/보관 데이터 유형·보관기간) + 앱 미사용자용 이메일 요청 경로.
- **fix(backend)**: 계정 삭제가 `goals`·`user_profile_history` 를 삭제하지 않아 목표·신체 계측 이력(민감 건강데이터)이 영구 잔존하던 결함 수정. `account_service` 가 user_id 보유 전 테이블을 비우도록 `goal_repo`/`profile_history_repo` 에 삭제 메서드 추가.
- **test**: user_id 컬럼 보유 모델을 동적 수집해 삭제 후 0건 검증(`test_delete_account_purges_all_user_data`) — pytest 49 passed.
- privacy-policy 를 `docs/store/` 로 이동 + §4/§5 Sentry 보존 정정.

#### 릴리스 (버전 bump + 문서)
- versionName 0.1.9 → **0.1.11**, versionCode 23 → **25** (`version.properties` SSoT). 0.1.10/24 는 미출시 로컬 bump 으로 건너뜀.
- current-state 문서 동기화 — `README.md`, `docs/PRD.md`, `docs/ops/operations-snapshot.md`, `CLAUDE.md`.

### 📁 주요 Files
- `app/src/main/AndroidManifest.xml`, `PermissionsRationaleActivity.kt`(신규), `ManifestHealthConnectRationaleTest.kt`(신규, 테스트)
- 아이콘: `res/drawable/ic_launcher_foreground.xml`·`ic_launcher_monochrome.xml`(신규 벡터), `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml`(수정), `res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png`(신규 10개)
- `backend/app/services/account_service.py`, `backend/app/repositories/{goal_repo,profile_history_repo}.py`, `backend/tests/test_account.py`, `docs/store/account-deletion.md`(신규), `docs/store/privacy-policy.md`
- `version.properties`, `README.md`, `docs/PRD.md`, `docs/ops/operations-snapshot.md`, `CLAUDE.md`

### 근거 / 진단
연동 버튼 무반응을 systematic-debugging 으로 진단: 디바이스 logcat 캡처 → `E PermissionsActivity: App should support rationale intent, finishing!` 가 매 탭마다 발화 + `healthconnect.controller/PermissionsActivity` 가 `Displayed` 에 한 번도 도달 안 함(렌더 직전 finish) 을 ground truth 로 근본 원인 확정. 공식 문서로 fix 검증 후 동일 기기에서 무재현 확인.

### 후속 (해소)
- ~~Android 15 HC access-log `width and height must be > 0` 버그로 읽기 실패~~ → **해소**. 근본은 우리 placeholder 아이콘의 intrinsic -1 이었고, 위 "런처 아이콘 정상화" 로 수정. 체성분 가져오기 실패("체성분을 가져오지 못했습니다")가 이 경로였음이 logcat 으로 확인됨(readRecords access-log → 동일 bitmap 예외).

---

## [main] — 2026-06-10 — 앱 버전 명시 방식 종합 (PR #102)

### 🎯 Prompts
1. "외부 문서 및 공식 문서(kotlin, android) 참고하여 앱 버전 명시 방식 연구 및 설계"
2. (브레인스토밍) "종합 설계 + 앱 프론트엔드 버전 표시" → "펙트체크" → "설계 확정으로 진행"
3. "순차적으로 모두 진행해줘. 디버깅도 작업하면서."

### ✅ Changes
- **App 버전 SSoT** — 루트 `version.properties`(versionName/versionCode) 도입, `app/build.gradle.kts` 가 읽음. 이력 주석블록(11줄) 제거 → 이력 SSoT 는 `docs/CHANGELOG.md`. 값은 0.1.9/23 불변.
- **Backend 독립 API 버전** — `backend/app/__version__="1.0.0"` → `FastAPI(version=)` → OpenAPI `info.version`(기본 `0.1.0` 누수 해소). 앱과 독립 진화. openapi 재싱크.
- **Frontend** — `ProfileScreen` 하단 `AppVersionLabel`(BuildConfig) "버전 0.1.9 (23)".
- **Automation** — `scripts/bump-version.sh`(semver/단조 가드 + current-state 문서 동기화 + `--dry-run`).
- **Docs** — `docs/conventions/versioning.md` 정책 SSoT + CLAUDE.md 링크.
- **fix(deploy)** — 머지 후 배포가 base `python:3.12-slim` 의 openssl `CVE-2026-45447`(HIGH)로 Trivy 1차 차단(PR 과 무관한 base-image CVE) → `backend/Dockerfile` 에 `apt-get upgrade` 레이어 핫픽스(자가치유, `5a78c69`). 라이브 검증 `info.version=1.0.0` · `/health` 200.

### 📁 주요 Files
- `version.properties`(신규), `app/build.gradle.kts`, `ui/profile/ProfileScreen.kt`, `backend/app/__init__.py`, `backend/app/main.py`, `backend/openapi.json`, `backend/tests/test_app_version.py`(신규), `backend/Dockerfile`, `scripts/bump-version.sh`(신규), `docs/conventions/versioning.md`(신규), `CLAUDE.md`

### 근거
공식 문서 라이브 fact-check — [Android Versioning](https://developer.android.com/studio/publish/versioning) · [Semantic Versioning 2.0.0](https://semver.org/) · [FastAPI version](https://fastapi.tiangolo.com/reference/fastapi/). 상세: `docs/plans/logs/process-infra.md` 2026-06-10 entry.

---

## [v0.1.9] — 2026-06-10 — Health Connect 체성분/오늘의 활동 + 갤럭시 워치 온보딩 + 사전점검 수정

### 🎯 Prompts
1. "release 버전 빌드 작업."
2. "펙트 체크 및 점검과 개선 사항 검토하고 빌드 전에 개선 및 수정 작업하고나서 빌드 하기전에 물어봐줘"

### ✅ Changes

#### Health Connect 기능 (PR #83/#84/#85 — v0.1.8 태그 이후 누적, 본 릴리스로 정식 출시)
- **체중·체지방 가져오기** (#84): `ImportBodyCompositionUseCase` + `ProfileScreen` "Health Connect에서 체중·체지방 가져오기" 버튼 → 최근 30일 최신 기록을 슬라이더에 prefill. 골격근량은 Google HC 미제공으로 수동 입력 유지(#1c reject).
- **홈 "오늘의 활동" 요약** (#85): `GetTodayActivityUseCase` + `HomeScreen` 카드 — 오늘 0시~현재 걸음·소모 칼로리·평균 심박 aggregate 1회. 무권한 시 연동 CTA, 무데이터 시 안내 표시.
- **HC 동기화 경로 정리 + 갤럭시 워치 온보딩** (#83): `HealthConnectDataSource`/`HealthRepository` 권한 set 단일화(체성분·활동·운동 분리), `isAvailable()` 가드 + HC 미설치 시 설치 유도 카드(Play Store 딥링크 + web fallback). 신규 HC 권한 5종 manifest 선언.

#### 사전 출시 점검 수정 (다관점 리뷰 — 버그/규약/silent-failure)
- **fix(health)**: 체성분 import read 실패를 `Result.failure` 로 전파 — 기존 degrade-to-null 은 transient HC read 실패를 "가져올 기록이 없습니다" 로 **오표시**했음. 이제 실패 시 "체성분을 가져오지 못했습니다…" 별도 안내 + Sentry 보고 (`ImportBodyCompositionUseCase`/`ProfileViewModel` + 테스트 갱신).
- **fix(home)**: `toggleDayCompletion` 서버 실패 revert 시 토글 직전 스냅샷 전체로 덮어 그 사이 로드된 `todayActivity` 가 사라지던 문제 → plan/완료 카운트만 revert, 활동 필드는 live 보존.
- **fix(home)**: HC 신규 감지 완료의 백그라운드 서버 푸시 실패를 silent drop 하지 않고 Sentry 보고 추가.
- **chore(룰 11)**: `HomeUiState`/`HomeSideEffect.ShowSnackbar`/`ProfileSideEffect` 하위타입 `@Immutable` 누락 보완(v0.1.8 부터 잔존).

#### 릴리스 (버전 bump + 문서)
- **Modified** `app/build.gradle.kts` — versionCode 22→**23**, versionName 0.1.8→**0.1.9** + 버전 주석.
- **Modified** `operations-snapshot.md` / `PRD.md` / `README.md` — 버전 표기 0.1.8/22 → 0.1.9/23 + 로드맵 v0.1.9 라인.
- 백엔드 cold start 제거 + Key Vault full IaC 는 아래 `[main] — 2026-06-09` 섹션 참조(이미 prod 배포 완료, 본 앱 릴리스와 독립).

### 📁 주요 Files Modified
- `app/build.gradle.kts`, `domain/usecase/ImportBodyCompositionUseCase.kt`, `ui/profile/ProfileViewModel.kt`, `ui/home/HomeViewModel.kt`, `app/src/test/.../ImportBodyCompositionUseCaseTest.kt`
- `docs/ops/operations-snapshot.md`, `docs/PRD.md`, `README.md`, `docs/CHANGELOG.md`

---

## [main] — 2026-06-09 — Cold start 제거 + Key Vault full IaC

### 🎯 요약
"로그인 느림" 반복 신고 → 측정으로 근본원인 규명(백엔드 scale-to-zero **cold start 21.5s**, Supabase Auth 아님 warm 28ms) → Entra External ID 전환 평가(수백 MAU 절감 $0) 후 보류, **A안(현행 유지 + cold start 해결)** 채택.

### 변경 (PR #92~#99)
- **Phase 1** (#92): Container App `min/max 1/3` + http-concurrency scale rule → cold start 제거(warm baseline).
- **`/health/ready`** (#93): readiness probe (DB `SELECT 1` → 200/503) + overridable dependency 단위테스트 2건 (44→46 PASS).
- **Phase 2 full IaC** (#96, #97): secret → **Key Vault 참조**(`kv-eundunhealth` + system MI + RBAC) · registries **MI pull**(admin password 제거) · **HTTP probe 3종** · committed `backend/containerapp.yaml` `--yaml` 배포 + KeyVault precheck. throwaway staging dry-run 으로 clobber/resolve 실증 후 정리. prod 검증 통과.
- **Dependabot** 정리: 5건 머지(sentry 8.43.1 / spotless 8.6.0 / detekt 1.23.8 / androidx core-ktx 1.19.0 / codecov-action 7), 2건 close(kotlin 2.4 CI fail, fastapi 0.136.3 MAL).
- **보존 편집** (#99): HomeScreen 중복 `contentPadding` 제거 · CLAUDE.md Live Edit 섹션 · `.vscode/mcp.json` Context7 키 `${input:}`(VS Code secret storage) 외부화 — 평문 제거.

### Lessons (→ `docs/plans/logs/process-infra.md` 2026-06-09)
deploy path(CI `working-directory: backend` → `backend/` prefix 중복) · `az --yaml` cp949 인코딩(주석 ASCII 화) · RBAC vault control-plane Owner ≠ data-plane(self-grant Secrets Officer) · Git Bash MSYS resource-ID 손상 → PowerShell.

---

## [main] — 2026-06-08 — Sentry ProGuard 매핑 게이팅 (로컬 release 빌드 결정성 회복)

### 🎯 Prompts
1. "'부수 발견' 근본 원인 해결 작업해줘." (직전 세션의 Sentry 매핑 비결정성 부수발견)
2. (brainstorming) 해결 강도 선택 → "완전 해결 (권장)" → "구현 작업"

### ✅ Changes

#### 근본 원인
- Sentry Gradle 플러그인의 ProGuard 매핑 처리(UUID 생성 + `sentry-debug-meta.properties` asset 주입 + Sentry 업로드)가 `hasToken`만 게이트로 **모든 release 빌드에서 무조건 실행**. → 로컬 release 빌드마다 ① asset 비결정성(UUID 매번 변경) ② Sentry 업로드 churn(네트워크/프로젝트 오염) ③ release on-device 의 "Apply Code Changes" 깨짐.
- CI(android.yml)는 release 를 빌드하지 않음 → 출시는 항상 로컬 preflight 경로. 따라서 "실제 출시 빌드"와 "로컬 실험용 release 빌드"를 명시 신호로 구분하는 게 근본 해결.

#### Fix — 명시적 출시 신호 게이팅
- **Modified** `app/build.gradle.kts` — `sentry {}` 블록에 `isOfficialRelease` 도입 (`-PsentryRelease=true` 또는 `SENTRY_RELEASE=true`). `includeProguardMapping`/`autoUploadProguardMapping` 게이트를 `hasToken` → `hasToken && isOfficialRelease` 로 변경.
- **Modified** `scripts/preflight-release.sh` — releaseArtifacts 호출에 `-PsentryRelease=true` 추가 (출시 경로 자동 ON).
- **Modified** `CLAUDE.md` — 룰 2 에 Sentry 매핑 게이트 설명 + 출시는 preflight/플래그 경로 필수 명시.

#### 검증 (MEASURED)
- 플래그 없이 `assembleRelease` task graph: `generateSentryProguardUuidRelease`·`uploadSentryProguardMappingsRelease` **부재**. inject 실행돼도 `sentry-debug-meta.properties` **미생성** = 결정적.
- `-PsentryRelease=true`: UUID asset 생성 + upload task graph 포함.
- 트레이드오프: preflight 아닌 경로로 빌드한 release 는 crash deobfuscation 불가 (출시는 룰 2 = preflight 유일 경로라 안전).

### 📁 Files Modified
- `app/build.gradle.kts` (+8, -2)
- `scripts/preflight-release.sh` (+2)
- `CLAUDE.md` (+2, -1)
- `docs/CHANGELOG.md` (+이 엔트리)

---

## [v0.1.8] — 2026-06-08 — AndroidManifest "modified" 근본 원인 규명 + v0.1.8 릴리즈

### 🎯 Prompts
1. "'Modifications to AndroidManifest.xml require an app restart...' 에러 근본 원인 해결 작업하고 프로젝트의 release 버전 빌드 작업"
2. (작업 방식) "리팩토링/성능 개선/디자인/테스트와 디버깅/펙트 체크/연구/공식 문서 검색을 곁들여 작업"
3. "git tag v0.1.7 하고 푸시해줘" → 태그 기존재 + 미출시 코드 발견 → "새 버전으로 릴리즈"

### ✅ Changes

#### AndroidManifest "modified" 에러 근본 원인 조사 (코드 변경 없음 — 빌드 버그 아님)
- **Root cause**: 메시지는 Android Studio Apply Changes/Live Edit의 **설계상 동작**. manifest 변경은 hot-swap 불가 → full restart 요구. 빌드 측 비결정성 원인 아님.
- **증거 (MEASURED)**: `processDebugMainManifest` / `processReleaseMainManifest` `--rerun-tasks` 후 merged manifest diff = byte 동일(결정적). 최종 packaged manifest에 변동값 없음. 빌드 후 소스 manifest `git status` clean.
- **Fact-check (공식)**: Sentry Gradle plugin 6.10.0은 proguard UUID를 manifest가 아닌 `assets/sentry-debug-meta.properties`에 기록 (getsentry/sentry-android-gradle-plugin#313). 방금 빌드 UUID `35da139f...`도 assets에서 확인.
- 해결은 IDE/workflow: manifest 편집 후 ▶Run, Live Edit 활성, spurious 시 Invalidate Caches + clean reinstall.

#### v0.1.8 릴리즈 (버전 bump)
- **Modified** `app/build.gradle.kts` — versionCode 21→**22**, versionName 0.1.7→**0.1.8** + 버전 주석 추가. v0.1.7 태그(658d2de) 이후 미출시 누적분(12 ViewModel UDF 리팩토링 + Sentry Gradle 6.10.0 + 의존성 bump)을 정식 출시.
- **Modified** `docs/ops/operations-snapshot.md` — 버전 표기 0.1.7/21 → 0.1.8/22, 최근 갱신일 갱신.
- preflight-release.sh 일괄 green (Spotless·Detekt·Unit 테스트·releaseArtifacts). AAB 7.69 MB / APK 5.53 MB. ProGuard 매핑 Sentry 업로드 완료.

### 📁 Files Modified
- `app/build.gradle.kts` (+2, -1)
- `docs/ops/operations-snapshot.md` (+2, -2)
- `docs/CHANGELOG.md` (+이 엔트리)

---

## [main] — 2026-06-06 — _staging 문서 승격 + shipped pr:null 검증 버그 수정

### 🎯 Prompts
1. "docs\plans\_staging 경로의 문서 정리 role은 뭐야?"
2. "_staging 문서들 점검해서 shipped 되면 정리해줘"
3. "커밋 필요해."

### ✅ Changes

#### _staging → docs/plans/ 승격 (3건 shipped 문서)
- **Added** `docs/plans/2026-06-05-frontend-major-improvement-design.md` — 프론트엔드 대규모 개선 설계 (Rev.2, 613줄)
- **Added** `docs/plans/2026-06-05-frontend-major-improvement-plan.md` — 실행 계획 (Rev.2, 244줄)
- **Added** `docs/plans/2026-06-06-frontend-regression-prevention-design.md` — 회귀 방지 설계 (90줄)
- 본문 상태 텍스트 frontmatter `shipped`와 동기화, 내부 `_staging/` 경로 참조 수정

#### gen_plans_index.py shipped pr:null 검증 버그 수정
- **Root cause**: `validate()` line 108의 `shipped requires pr` 검증이 main 직접 커밋 워크플로우를 차단. v2 hybrid 구조에서 traceability는 topic ledger entry가 담당하므로 pr 필드 필수 근거 소멸
- **Modified** `scripts/gen_plans_index.py` — shipped status의 pr 필드를 선택으로 완화
- **Modified** `scripts/test_gen_plans_index.py` — `test_validate_shipped_without_pr_passes` + `test_main_shipped_without_pr_succeeds` 신규, `test_main_fails_on_invalid_frontmatter` invalid status 케이스로 변경 (32/32 PASS)

#### 경로 참조 수정
- **Modified** `docs/plans/logs/android.md` — `_staging/` → `docs/plans/` 경로 3건 수정

### 📁 Files Modified
- `docs/plans/2026-06-05-frontend-major-improvement-design.md` (+613, new)
- `docs/plans/2026-06-05-frontend-major-improvement-plan.md` (+244, new)
- `docs/plans/2026-06-06-frontend-regression-prevention-design.md` (+90, new)
- `docs/plans/logs/android.md` (+3, -3)
- `scripts/gen_plans_index.py` (+2, -2)
- `scripts/test_gen_plans_index.py` (+21, -5)

---

## [main] — 2026-06-06 — 프로젝트 문서 최신화 + _staging 점검

### 🎯 Prompts
1. "프로젝트의 문서 최신화 작업해줘"
2. "changelog 작업해줘. docs\plans\_staging 경로의 문서들 점검해줘."

### ✅ Changes

#### 문서 최신화 (dependabot 머지 반영)
- **Modified** `CLAUDE.md` — dependency-deferred 참조 "kotlin 2.3"→"kotlin 2.4" + DSL 마이그레이션 블로커
- **Modified** `docs/ops/operations-snapshot.md` — 헤더 갱신 (커밋 완료 상태), Vico 3.2.2, Sentry Gradle 6.10.0 추가, §13 dependabot 배치 이력
- **Modified** `docs/ops/dependency-deferred.md` — kotlin 타겟 2.3.21→2.4.0, build.gradle.kts DSL 마이그레이션 블로커 추가, 재개 조건 OR→AND 변경

#### _staging 점검 (3건 status 갱신 + ledger entry)
- **Modified** `docs/plans/_staging/` 3파일 — frontmatter `status: proposed` → `shipped` (gitignored, 로컬 참조용)
- **Modified** `docs/plans/logs/android.md` — ledger entry 2건 추가:
  - 2026-06-05/06 프론트엔드 대규모 개선 Phase 1-5
  - 2026-06-06 프론트엔드 회귀 방지 3계층 가드

### 📁 Files Modified
- `CLAUDE.md` (+1, -1)
- `docs/ops/operations-snapshot.md` (+5, -4)
- `docs/ops/dependency-deferred.md` (+19, -12)
- `docs/plans/logs/android.md` (+28, ledger entries)

---

## [main] — 2026-06-06 — Dependabot PR 일괄 정리 + 의존성 업그레이드

### 🎯 Prompts
1. "dependabot 나머지 PR들도 정리해줘"
2. "CI 결과 확인해줘"
3. "changelog 작업해줘"

### ✅ Changes

#### Merged (squash merge, 4건)
- **#78** `org.gradle.toolchains.foojay-resolver-convention` 0.10.0 → 1.0.0 (`settings.gradle.kts`)
- **#79** `io.sentry.android.gradle` 5.8.0 → 6.10.0 (`gradle/libs.versions.toml`)
- **#80** `com.patrykandpatrick.vico:compose-m3` 3.1.0 → 3.2.2 (`gradle/libs.versions.toml`)
- **#67** `mypy` 1.13.0 → 2.1.0 (`backend/requirements-dev.txt`)

#### 수동 적용 (1건 — merge conflict로 직접 main 커밋)
- **#81 partial** backend 5/6 패키지 (`backend/requirements.txt`, `backend/requirements-dev.txt`)
  - starlette 1.1.0 → 1.2.1, uvicorn 0.48.0 → 0.49.0, sentry-sdk 2.60.0 → 2.61.1
  - pytest-asyncio 1.3.0 → 1.4.0, ruff 0.15.14 → 0.15.16
  - **fastapi 0.136.3 제외** — MAL-2026-4750 compromised release, 0.136.1 유지

#### Closed (CI 실패 / 마이그레이션 필요, 2건)
- **#74** Kotlin 2.2.10 → 2.4.0 — `BaseAppModuleExtension` deprecated + `kotlinOptions` DSL 제거 + Hilt 호환성 미충족
- **#75** openapi-generator 7.10 → 7.22 — generated code detekt 실패

#### 문서 동기화
- **Modified** `CLAUDE.md` — Vico 3.1.0→3.2.2, starlette 1.1.0→1.2.1, Sentry SDK 2.60.0→2.61.1

### 📁 Files Modified
- `settings.gradle.kts` (+1, -1) — #78
- `gradle/libs.versions.toml` (+2, -2) — #79, #80
- `backend/requirements-dev.txt` (+3, -3) — #67, #81 partial
- `backend/requirements.txt` (+3, -3) — #81 partial
- `CLAUDE.md` (+3, -3) — version sync

---

## [main] — 2026-06-06 — GitHub Actions Node.js 20 → 24 런타임 업그레이드

### 🎯 Prompts
1. "dependabot Node.js 20 경고 해결해줘"
2. "CI 결과 확인해줘"
3. "changelog 작업해줘"

### ✅ Changes
- **Modified** `.github/workflows/android.yml` — `actions/checkout@v4` → `@v6`
- **Modified** `.github/workflows/backend.yml` — `actions/checkout@v4` → `@v6` (4곳), `gitleaks/gitleaks-action@v2` → `@v3`, `aquasecurity/trivy-action@master` → `@v0.36.0`
- **Modified** `.github/workflows/docs-plans-index.yml` — `actions/checkout@v5` → `@v6`
- **Closed** Dependabot PR #72 (`actions/checkout` v4→v6), PR #73 (`gitleaks-action` v2→v3)

### 📁 Files Modified
- `.github/workflows/android.yml` (+1, -1)
- `.github/workflows/backend.yml` (+6, -6)
- `.github/workflows/docs-plans-index.yml` (+1, -1)

### 🔍 Notes
- Node.js 20 deprecated June 2026, removed September 2026
- 9개 action 중 3개만 업그레이드 필요 — 나머지 6개 (`setup-java@v5`, `setup-gradle@v6`, `upload-artifact@v7`, `setup-python@v6`, `codecov-action@v6`, `azure/login@v3`) 는 이미 Node.js 24 런타임
- `trivy-action`은 Docker 기반이라 Node.js 런타임 경고 대상 아님, `@master` → `@v0.36.0` pin은 supply chain 보안 목적
- 첫 push에서 `@0.36.0` (`v` prefix 누락)으로 deploy 실패 → `@v0.36.0`으로 즉시 수정

---

## [main] — 2026-06-06 — 프론트엔드 UDF-Enhanced 마이그레이션 + 회귀 방지 가드

### 🎯 Prompts
1. "프론트엔드 대규모 개선 종합 설계 (Rev.2) 기반 Phase 1-5 구현"
2. "프론트엔드 회귀 방지 설계 — 실행 계획 구현"
3. "프로젝트의 문서 최신화 작업해줘"

### ✅ Changes

#### Phase 1-5: 12 ViewModel UDF-Enhanced 패턴 마이그레이션
- **Modified** 9 ViewModels → 단일 `_uiState: MutableStateFlow<XxxUiState>` 패턴으로 전환. 분산 StateFlow (`_error`/`_isLoading`) 제거.
  - AuthVM, BadgeVM, GoalVM, HistoryVM, HomeVM, OnboardingVM, ProfileVM, StatisticsVM, WorkoutDetailVM
- **Added** 3 ViewModels (AuthVM 분리): `LoginViewModel` (96L), `SignupViewModel` (102L), `ForgotPasswordViewModel` (61L). AuthVM 은 session lifecycle 전용으로 축소.
- **Modified** 11 Screens → 모든 `collectAsState()` 를 `collectAsStateWithLifecycle()` 로 교체 (lifecycle-aware collection).
- **Added** `@Immutable` annotation 45건 across 17 files — domain models 5개 (Exercise, Goal, Statistics, UserProfile, WeeklyPlan) + 12 ViewModel UiState/SideEffect sealed class.
- **Added** SideEffect Channel 7 VMs — 일회성 이벤트 (navigation, snackbar) 를 `Channel<SideEffect>(Channel.BUFFERED)` + `receiveAsFlow()` 로 전달.
- **Added** 3 test files: `LoginViewModelTest` (202L), `SignupViewModelTest` (192L), `ForgotPasswordViewModelTest` (110L)
- **Modified** `AuthViewModelTest` — AuthVM 축소에 따라 per-screen 테스트를 신규 테스트 파일로 분리. 대폭 감소.

#### 의존성 메이저 업그레이드
- **OkHttp 4.12.0 → 5.3.2**: Kotlin-first API, HTTP/3 지원, 개선된 connection pool.
- **Coil 2.7.0 → 3.4.0**: module group `io.coil-kt` → `io.coil-kt.coil3`. `coil-network-okhttp` 신규 의존성 추가. `CoilModule` Coil 3 API 마이그레이션 (`ImageLoader` → `SingletonImageLoader.Factory` 패턴).
- **gradle.properties**: `android.r8.strictFullModeForKeepRules=false` 제거 (OkHttp 5 / Coil 3 호환).

#### 회귀 방지 3계층 가드
- **Added** CLAUDE.md **룰 11** — ViewModel UDF-Enhanced 패턴 5개 체크리스트 + 허용 예외 + baseline.
- **Added** `.github/workflows/android.yml` "Check collectAsState anti-pattern" CI step — import `$` anchor + 호출부 `grep -v` 필터, false positive 0.
- **Modified** `.githooks/pre-commit` — collectAsState grep check 섹션 추가 (staged `.kt` 파일 한정, 룰 11).
- **Added** `docs/plans/_staging/2026-06-06-frontend-regression-prevention-design.md` — 설계 문서 (D1~D6 결정 테이블 + 잔여 리스크).

#### 문서 동기화
- **Modified** `CLAUDE.md` — Key patterns 섹션 UDF-Enhanced 반영, stale 버전 수정 (Sentry 8.16→8.42, Vico 2.1→3.1, Spotless 7.0→8.5, OkHttp/Coil 추가), pre-commit 설명 갱신.
- **Modified** `docs/ops/operations-snapshot.md` — §8 CI/자동화에 collectAsState 가드 추가, §13 변경 이력 추가.
- **Modified** `docs/CHANGELOG.md` (this entry)

### 📊 Metrics (MEASURED 2026-06-06)
- `@Immutable`: 45 annotations across 17 files
- `collectAsStateWithLifecycle`: 33 occurrences across 13 files
- `collectAsState()`: **0건** (anti-pattern 완전 제거)
- SideEffect Channel: 7 VMs
- Total: 46 files, +752 / -869 lines (net -117)

### 📁 Files Modified
- ViewModels: 9 modified + 3 new
- Screens: 11 modified
- Tests: 1 modified + 3 new
- Domain models: 3 modified (`@Immutable` 추가)
- UI components: 6 modified (`@Immutable` 추가)
- Infra: `CoilModule`, `MainActivity`, `AppNavigation`, `build.gradle.kts`, `libs.versions.toml`, `gradle.properties`
- Guards: `CLAUDE.md`, `android.yml`, `.githooks/pre-commit`
- Docs: `operations-snapshot.md`, `CHANGELOG.md`
- Design: `docs/plans/_staging/2026-06-06-frontend-regression-prevention-design.md` (new)

---

## [main] — 2026-06-05 — docs/plans/ lifecycle 관리 개선

### 🎯 Prompts
1. "Implement the following plan: docs/plans/ 문서 lifecycle 관리 개선 [holding/deferred status + root-only scan + grouped rendering + _staging gitignore]"
2. "CLAUDE.md 업데이트해줘"

### ✅ Changes
- **Modified**: `scripts/gen_plans_index.py` — `holding`/`deferred` status 추가, `rglob`→`glob` root-only 스캔, `render_readme_v2` status 그룹별 하위 섹션 렌더링 (`진행 중`/`대기`/`보류`)
- **Modified**: `scripts/test_gen_plans_index.py` — 4개 테스트 추가 (31 total, all pass)
- **Modified**: `.gitignore` — `docs/plans/_staging/` 추가 (scratch 작업 폴더)
- **Modified**: `docs/plans/_templates/{design,plan}.md` — status lifecycle 주석 추가
- **Moved**: `docs/plans/expected/` → `docs/plans/_staging/` (gitignored)
- **Modified**: `CLAUDE.md` — `gen-plans-index.sh` 항목에 root-only scan, 8개 status 값, 그룹 렌더링, `_staging/` 설명 반영

### 📊 Test Results
- Total: 31/31 passed (100%)
- `gen-plans-index.sh --check` drift 없음

### 📁 Files Modified
- `scripts/gen_plans_index.py` (+27, -10 lines)
- `scripts/test_gen_plans_index.py` (+71, -1 lines)
- `.gitignore` (+3 lines)
- `docs/plans/_templates/design.md` (+1, -1 lines)
- `docs/plans/_templates/plan.md` (+1, -1 lines)
- `CLAUDE.md` (+1, -1 lines)

---

## [main] — 2026-06-04 — Android 프론트엔드 분석 + Plugin 에러 해결

### 🎯 Prompts
1. "`https://developer.android.com/develop/ui/compose/performance` 문서 분석하여 프로젝트에 적용 검토 후 memory에 작업해줘."
2. "아래 내용으로 프로젝트 설계 검토 작업해줘 [Clean Architecture + MVI + Multi-module 아키텍처]"
3. "아래 claude code plugin error 해결 방안 연구 설계 작업해줘 [10개 에러]"
4. "plugin error 해결 작업 진행해줘"

### ✅ Changes
- **Added**: `docs/plans/logs/android.md` — 8개 분석 엔트리 추가 (+1469 lines)
  - Compose 퍼포먼스 공식 문서 기반 성능 점검 (13항목 체크리스트, P1 double padding 버그 발견)
  - Clean Architecture + MVI + Multi-module 아키텍처 설계 검토 (6개 gap 식별, 7-phase 마이그레이션 로드맵)
  - Claude Code Plugin Errors 진단 및 해결 방안 (근본 원인 분석 + 4개 조치 설계)
  - HomeScreen 레이아웃 UX/UI, UDF 디자인 패턴, 프론트엔드 전수 분석, 의존성 LTS, 빌드 환경 검토
- **Fixed**: Claude Code plugin 에러 10건 해결
  - 마켓플레이스 `claude-plugins-official` re-clone (`.git` 누락 → 9개 "not found" 해소)
  - `~/.claude/plugins/blocklist.json` — `code-review` 테스트 항목 제거
  - `.claude/settings.json` — `vtsls@claude-code-lsps` 제거 (Android 프로젝트에 TS LSP 불필요)
  - `~/.claude/settings.json` — `vtsls@claude-code-lsps: false` (글로벌 비활성화)

### 📊 Verification
- 재시작 후 plugin error 0건 확인

### 📁 Files Modified
- `docs/plans/logs/android.md` (+1469 lines)
- `.claude/settings.json` (+1, -2 lines)
- `~/.claude/settings.json` (global, vtsls false)
- `~/.claude/plugins/blocklist.json` (global, code-review 제거)
- `~/.claude/plugins/marketplaces/claude-plugins-official/` (global, re-clone)
- `docs/CHANGELOG.md` (this entry)

---

## [main] — 2026-06-03 — Azure Monitor Alerts 프로비저닝 (P1+P2)

### 🎯 Prompts
1. "Implement the following plan: Azure Monitor Alerts (P1+P2) 설계 및 적용"
2. "push it"
3. "firewall alert 이메일 왔는지 확인해봐"
4. "CLAUDE.md에 setup-azure-alerts.sh 추가해줘"

### ✅ Changes
- **Added**: `scripts/setup-azure-alerts.sh` — idempotent Azure CLI 스크립트 (`--dry-run`, `--delete`, `--help`). MSYS path conversion 방지 포함
  - Action Group `ag-eundunhealth-prod` (email → `qkr133456@gmail.com`)
  - P1 Activity Log alerts 3개: ServiceHealth, ResourceHealth, Deletion (무료)
  - P2 Metric alerts 4개: PG CPU/Storage/Connections, CA 5xx (~$0.40/월)
  - P2 Activity Log alert 1개: PG Firewall 변경 (무료)
- **Added**: `docs/plans/2026-06-03-azure-monitor-alerts-design.md` — 설계 문서 (D1~D8 의사결정, 옵션 비교, 검증 계획, 롤백 절차)
- **Modified**: `docs/ops/monitoring-and-cost.md` — §7 Alert 섹션 신설, §4 비용 갱신 (+~700원), §5 체크리스트에 alert 확인 항목 추가
- **Modified**: `docs/ops/operations-snapshot.md` — §12 Alert 인벤토리 신설 (8개 alert 테이블), §9 비용 갱신, §13 변경 이력 추가
- **Modified**: `CLAUDE.md` — 자동화 스크립트 섹션에 `setup-azure-alerts.sh` 항목 추가

### 📊 Verification
- 스크립트 실행 결과: metric alert 4개 + activity log alert 4개 = 총 8개 정상 생성
- PG Firewall alert 실측 테스트: temp rule 생성/삭제 → email 3통 수신 확인 (Gmail MCP 검증)
- Action Group 파이프라인 end-to-end 정상 동작

### 📁 Files Modified
- `scripts/setup-azure-alerts.sh` (+385 lines, new)
- `docs/plans/2026-06-03-azure-monitor-alerts-design.md` (+149 lines, new)
- `docs/ops/monitoring-and-cost.md` (+65, -2 lines)
- `docs/ops/operations-snapshot.md` (+33, -2 lines)
- `docs/plans/README.md` (+4, -2 lines, auto-generated)
- `CLAUDE.md` (+1 line)
- `docs/CHANGELOG.md` (this entry)

---

## [main] — 2026-06-03 — PowerShell 7.6 LTS 전환 + atomic commit 워크플로우

### 🎯 Prompts
1. "Implement the following plan: PowerShell 7 전환: 프로젝트 설정 적용 설계"
2. "지금까지 한 두 커밋 squash 해줘" → main force push 불가 확인 → 추천 방식 연구 요청
3. "atomic commit 워크플로우: changelog amend 패턴 설계" (plan → 구현)

### ✅ Changes
- **Modified**: CLAUDE.md PowerShell 섹션을 7.6 LTS 기준으로 리팩토링 (`CLAUDE.md:283-325`)
  - 버전 명시: "PowerShell 7(`pwsh`)" → "**PowerShell 7.6 LTS** (`pwsh.exe`, .NET 10)"
  - `pwsh.exe` vs `powershell.exe` 실행 파일 구분, UTF-8 인코딩, ConciseView, Profile 경로
  - 7.x 신규 연산자 표 (ternary, `??`, `??=`, null-conditional, `-Parallel`)
  - WMI cmdlet 제거 (`Get-WmiObject` → `Get-CimInstance`)
- **Modified**: 자동화 스크립트 섹션에 bash 유지 사유 추가 (`CLAUDE.md:345`)
- **Added**: Commit / Push 워크플로우 컨벤션 (`CLAUDE.md:276-281`)
  - main 직접 작업 시 changelog 포함 후 1회 push, amend 패턴, fallback squash
- **Added**: `/changelog` 스킬 amend 패턴 전환 (`.claude/skills/changelog/SKILL.md`)
  - push 상태 판별 (`@{push}..HEAD`) → 미push 시 amend 기본, push 후 별도 커밋
  - `docs/CHANGELOG.md` 경로 수정, 프로젝트 맞춤 템플릿, 불필요 Helper Script 섹션 제거

### 📁 Files Modified
- `CLAUDE.md` (+30, -3 lines)
- `.claude/skills/changelog/SKILL.md` (+280 lines, new)
- `docs/CHANGELOG.md` (this entry)

---

## v0.1.7 — 2026-05-30 (versionCode 21) — LoginScreen + ForgotPasswordScreen 룰 8 적용

### Changed
- **LoginScreen 룰 8 적용**: Snackbar 단독 → inline `AuthErrorBanner` (persistent + a11y liveRegion + Sentry breadcrumb). password input 아래 / "로그인" 버튼 위 (D3). `LaunchedEffect(formValid, lastError)` 가 input 보완 시점 자동 dismiss (D4). EmailNotConfirmed 만 기존 inline 재전송 UI 보존 (Option A, D2). resendError 도 EmailNotConfirmed 영역 아래 같은 Banner 재사용 (D10). Sentry breadcrumb `screen=login` / `login_resend`.
- **ForgotPasswordScreen opError Banner 통합**: `LaunchedEffect(opError)` snackbar 제거 → Banner (`screen=forgot_password`). `LaunchedEffect(formValid, opError)` dismiss (D5). `passwordResetSent` 성공 snackbar 는 그대로 유지 (룰 8 예외 — 비-critical 성공).

### Refactored
- **`AuthErrorBanner` promote**: `ui/auth/SignupScreen.kt` 내 `private @Composable` → `ui/components/AuthErrorBanner.kt` public composable. 3 Auth 화면 (Signup, Login, ForgotPassword) 공유. 룰 8 의 "두 번째 화면 마이그레이션 시점에 promote" 트리거 (CLAUDE.md). SignupScreen 호출부는 동일 시그니처 → 동작 변경 없음.

### Why
INC-2026-05-26-01 의 가시성 결함을 SignupScreen 외 Login + ForgotPassword 에도 일관 적용. CLAUDE.md 룰 8 (PR #60, 2026-05-30) 등재 후 **첫 다중 화면 마이그레이션 사례**. 룰 8 의 4 요소 (inline + persistent + a11y + Sentry) 모두 만족.

---

## v0.1.6 — 2026-05-29 (versionCode 20) — Signup Failed UX inline error banner

### Fixed
- **INC-2026-05-26-01 가시성 결함 해소**: Signup 화면의 Failed 상태가 하단 snackbar 2초 자동 dismiss 로 사용자 인지 부족하던 문제 (실기기 검증 중 발견). RFC 작성 후 review 12 개선 통합 (D1~D12). `AuthErrorBanner` 를 form headline 아래/email input 위에 표시. dismiss = button enabled (모든 validation pass) 시점 자동 (D1). resendError 도 같은 Banner 재사용 (D7). a11y liveRegion Polite + Sentry breadcrumb (`auth.error_banner_shown`).

### Added
- `AuthErrorBanner` private composable (SignupScreen.kt) — Material 3 `Surface(errorContainer)` + `Icon(ErrorOutline)` + `Text(userMessage)`. LoginScreen 등 다른 화면 마이그레이션 시점에 `ui/components/` 로 promote (D5 YAGNI).
- `AuthViewModel.clearSignupError()` — current state == `Failed` 시만 `Form` 전환, 그 외 silent no-op (D6 race 회피).
- `BuildConfig.MOCK_AUTH_ERROR` (debug-only) — 수동 검증 reproducibility. 사용: `./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit` → `AuthRepositoryImpl.signUp` 가 강제 `AppError.Auth("요청이 너무 많습니다...")` 반환. release 빈 string + DEBUG short-circuit 으로 double-guard (D11).

### Changed
- `SignupScreen.kt`: `LaunchedEffect(signupState)` snackbar + delay + `resetSignupState` 블록 제거. `LaunchedEffect(resendError)` 제거. `SnackbarHostState` + `Scaffold.snackbarHost` 인프라 제거 (D12 — dead code).
- `SignupForm` 시그니처: `error: AppError?` + `onClearError: () -> Unit` 추가. `LaunchedEffect(formValid, error)` 가 button enabled 시점에 `onClearError()` 호출.
- `AwaitingConfirmationCard` 시그니처: `resendError: AppError?` 추가. `onResend` 가 `clearResendError()` + `resendConfirmation()` 둘 다 호출.
- `AuthRepositoryImpl.signUp`: expression body 의 if-else 분기로 mock 통합 (return 사용 불가, if-then-else try-catch 구조).

### Test
- `AuthViewModelTest.kt` +2: `clearSignupError` Failed → Form 전환 + Form 상태 no-op (D6).
- Compose UI test (Banner / SignupScreen) 는 Out-of-scope (D2) — `androidTest/` 인프라 부재. 별도 RFC.

### Refs
- PR: #58
- Design + Plan: `docs/plans/2026-05-29-signup-error-banner-{design,plan}.md` (머지 후 `logs/android.md` entry 로 흡수 + git rm)
- Supersedes RFC: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md`
- INC: `docs/ops/incident-log.md` INC-2026-05-26-01

---

## v0.1.5 — 2026-05-29 (versionCode 19) — Vico 3.1 + healthConnect stable + starlette 1.1.0

### Changed
- **Vico 차트 라이브러리 2.1.0 → 3.1.0** (#52): v3 의 변경은 import 경로 (`com.patrykandpatrick.vico.core.cartesian.*` → `compose.cartesian.*`) 만, builder DSL 은 v2.1 과 동일. dependabot #39 (close) follow-up 정식 마이그레이션. 2 commit 분리 보존 — Commit 1 minimal import migration, Commit 2 opportunistic 개선 (`LineCartesianLayer.Interpolator.catmullRom` 으로 line 부드럽게 + `VerticalAxis.rememberStart(tickPosition = BaseAxis.TickPosition.Inside)` 로 chart 영역 활용성 ↑). 실기기 시각 검증 통과.
- **Health Connect 1.1.0-rc01 → 1.1.0 stable** (#53): 2025-10-08 stable 출시 (rc03 → stable 승격, API 변경 없음). `dependency-deferred.md §3` 보류 종료. dependabot #35 follow-up.
- **starlette 0.49.1 → 1.1.0** (#54): fastapi 0.136.1 의 starlette 의존성 범위가 `>=0.46.0` (상한 없음) 이라 1.x 허용 확인. starlette 1.1.0 이 `PYSEC-2026-161` fix 포함 → `backend.yml` 의 `--ignore-vuln PYSEC-2026-161` 옵션 제거 (직접 fix 됨). `app/main.py` 의 `add_middleware` 는 INC-2026-05-24-03 fix 후 모듈 레벨 등록이라 starlette 1.x lifespan 정책 무관. 로컬 pytest 44 PASS + docker compose runtime-smoke `/health` 200 + CI runtime-smoke 통과. `dependency-deferred.md §2` 보류 종료. dependabot #9 follow-up.

### Infrastructure
- **detekt baseline drift 1차 해소** (#52 동반): `config/detekt/baseline.xml` (git tracked) vs `config/detekt/baseline-debug.xml` (.gitignore) 의 task 갱신 비대칭으로 PR #42 부터 main 의 detekt 가 chronic failure 였던 것을 baseline 재생성으로 해소. drift 자체의 구조적 fix 는 TODO (기록: `~/.claude/.../memory/detekt-baseline-drift.md`).
- **dependency-deferred 정리** (#55): `docs/ops/dependency-deferred.md` 의 §2 (starlette) + §3 (healthConnect) 삭제, §1 (kotlin 2.3) 에 상태 점검 메모 추가. 남은 블로커: Hilt 2.59.3+ 출시 대기 (최신 2.59.2 from 2026-02-20).

### Refs
- PRs: #52 vico migration / #53 healthConnect 1.1.0 stable / #54 starlette 1.1.0 / #55 kotlin docs
- Design+plan: `docs/plans/2026-05-28-vico-3-migration-design.md` + `-plan.md`
- 트리거: 2026-05-28 dependabot 8 PR triage 세션 (PR #50 plan)
- 보류 정책: `docs/ops/dependency-deferred.md`

---

## v0.1.4 — 2026-05-26 (versionCode 18) — Hotfix: redirect URL path

### Fixed
- **App Links 자동 로그인 미작동 hotfix**: v0.1.3 실기기 검증 결과, Supabase Console 의 Site URL 에 `/auth/confirm` path 를 포함시켜도 redirect_to 파라미터에는 origin(host) 만 사용됨이 확인됨. 메일 링크가 `https://host/?code=...` (root path) 로 와서 Android intent-filter (`pathPrefix=/auth/confirm`) 가 매칭되지 않아 App Links autoVerify 가 작동 못 함.
- `AuthRepositoryImpl.signUp` 의 `signUpWith(Email)` 호출에 `redirectUrl = "https://${BuildConfig.APP_LINKS_HOST}/auth/confirm"` 명시 전달 — 클라이언트가 path 까지 포함된 redirect URL 을 지정.
- `AuthRepositoryImpl.resendConfirmation` 의 `resendEmail` 호출에도 동일한 `redirectUrl` 전달 (재전송 메일에도 같은 형식 보장).

### 운영 수동 절차 (v0.1.4 머지 후 1회)
- Supabase Dashboard → Authentication → URL Configuration → **Additional Redirect URLs** allowlist 에 다음 URL 추가:
  ```
  https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/auth/confirm
  ```
  명시 redirectUrl 이 honor 되려면 allowlist 에 등록 필수 (Supabase 보안 정책).

### Refs
- 진단: v0.1.3 Phase 5 Step 4 디바이스 검증 중 발견
- 원본 design: `docs/plans/2026-05-26-applinks-deep-link-design.md`

---

## v0.1.3 — 2026-05-26 (versionCode 17) — Android App Links 자동 로그인

### Added
- **App Links 자동 로그인**: Supabase Confirm Email 메일 링크를 같은 디바이스에서 클릭하면 앱이 자동으로 열려 PKCE code 교환을 거쳐 자동 로그인까지 완료 (Onboarding/Home 직진). 사용자 액션은 메일 링크 클릭 1회만.
- 백엔드: FastAPI 에 정적 라우트 2개 추가 — `/.well-known/assetlinks.json` (App Links 검증) + `/auth/confirm` (앱 미설치 사용자용 fallback HTML + Google Play 링크).
- Android: AndroidManifest intent-filter (autoVerify=true) + MainActivity 가 supabase-kt 빌트인 `SupabaseClient.handleDeeplinks` extension 으로 deep link 처리.
- `AuthViewModel.onDeepLinkSuccess` / `onDeepLinkError` — 라이브러리 콜백 받아 sessionState / authOpState 갱신.
- `mapAuthError` +3 매핑: `otp_expired` / `flow_state_expired` → 만료 메시지, `bad_code_verifier` / `flow_state_not_found` → 인증 정보 불일치 메시지.

### Changed
- `SupabaseModule` 에 `flowType = FlowType.PKCE`, `scheme = "https"`, `host = BuildConfig.APP_LINKS_HOST` 명시.
- AndroidManifest MainActivity 에 `launchMode="singleTop"` 추가 — deep link 재진입 시 Compose state 보존.

### Infrastructure
- Container App 기본 subdomain (`eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io`) 활용. Custom domain 미보유 — v1.0 출시 전 재검토.
- 단위 테스트: AuthErrorMappingTest +3 (11), AuthViewModelTest +4 (19), backend test_auth_routes +3 (44 total).

### 운영 수동 절차 (이번 릴리스 머지 후 1회)
- Supabase Dashboard → Authentication → URL Configuration → Site URL = `https://<APP_LINKS_HOST>/auth/confirm`, Additional Redirect URLs 에도 추가.
- (선택) `local.properties` 에 `APP_LINKS_HOST=<container-app-fqdn>` 등록 — build.gradle.kts 기본값으로도 동작하지만 override 가능.
- assetlinks.json 의 SHA256 fingerprint 가 release keystore 와 일치하는지 디바이스에서 `adb shell pm get-app-links --user 0 com.gunnys.eundunhealth` 로 확인 (`verified` 표시).

### Refs
- Design: `docs/plans/2026-05-26-applinks-deep-link-design.md`
- Plan: `docs/plans/2026-05-26-applinks-deep-link-plan.md`

---

## v0.1.2 — 2026-05-26 (versionCode 16) — Hotfix

### Fixed
- **가입 무반응 hotfix**: Supabase project Confirm Email 토글 ON 상태에서 사용자는 정상 생성되고 확인 메일도 발송되지만, supabase-kt 3.6.0 의 `Email.decodeResult` 가 GoTrue 응답 JSON 의 일부 필드(특히 `aud`/`id`) 누락으로 `MissingFieldException` → `SupabaseEncodingException` 을 throw 하여 클라이언트가 "회원가입에 실패하였습니다"로 표시하던 문제. `mapSignUpException` 헬퍼에서 `SupabaseEncodingException` 을 `AwaitingConfirmation` 으로 분류하도록 별도 처리.
- 단위 테스트 +2 (`AuthErrorMappingTest`): SupabaseEncodingException → AwaitingConfirmation, 일반 예외 → AppErrorException

### 운영 메모 (이번 릴리스 외 후속 액션)
- Supabase Project → Authentication → URL Configuration → **Site URL을 localhost 외 값으로 변경** 필요. 확인 메일 링크가 localhost로 리다이렉트되어 외부 디바이스에서 "연결 거부" 발생. 임시로 `https://github.com/gunnysis/eundunHealth` 같은 정적 페이지로 지정하거나, 향후 deep link RFC 도입 시 `eundunhealth://confirm` 같은 custom scheme 사용.

---

## v0.1.1 — 2026-05-26 (versionCode 15)

### Added
- 회원가입 이메일 확인 흐름: 안내 카드 (AwaitingEmailConfirmation) + 60초 쿨다운 재전송 버튼
- Login 미인증 사용자(EmailNotConfirmed)에게 inline "인증 메일 다시 보내기" 액션 노출
- Login 이메일 자동 채움 (가입 후 "로그인하러 가기" 흐름에서 pendingEmail 전달)
- SignupScreen 비밀번호 6자 미만 inline 검증

### Changed
- 인증 상태 모델을 `SessionState` (글로벌) / `AuthOpState` (인증 화면 작업) / `SignupState` (가입 흐름) 세 sealed로 분리
- `ResetState`/`AuthState` 레거시 제거. `AppNavigation`은 `sessionState`만 구독
- `AuthRepository.signUp` 반환 타입을 `Result<SignupResult>` (`AutoSignedIn`/`AwaitingConfirmation`)로 변경
- `mapAuthError`를 top-level internal 함수로 분리, `email_not_confirmed` → `AppError.EmailNotConfirmed`

### Fixed
- 가입/로그인 실패 시 화면이 Login으로 튕기며 에러 스낵바가 잘리던 구조적 버그 — `AuthState.Unauthenticated` 전환을 명시적 logout/세션 만료에만 한정

### Infrastructure
- Supabase 사용 범위를 Authentication 한정으로 SPEC.md에 명시 (Database/Storage/Realtime/Edge Functions = out-of-scope)
- 단위 테스트 케이스 추가: AppErrorTest +1, AuthErrorMappingTest +6 (신규), AuthViewModelTest +8

### Refs
- Design: `docs/plans/2026-05-26-signup-confirmation-flow-design.md`
- Plan: `docs/plans/2026-05-26-signup-confirmation-flow-plan.md`

---

## v0.1.0 (2026-05-25) — 첫 의미있는 milestone

versionCode `14`, versionName `0.1.0`. v0.1·v0.2·v0.3 spec 전체 + 인프라 마이그레이션 + 출시 직전 안정화(Phase 1~6A + Dependabot 정리)를 한 릴리스로 묶음. Internal Testing 트랙 배포. 13은 안정화 전 첫 시도(업로드 완료, 14로 대체).

### Backend 전환 (Ktor → FastAPI)

- **Ktor(Kotlin) → FastAPI(Python 3.12)** 백엔드 전면 재작성. 동일 API 계약 유지 (camelCase JSON, 9개 → 12개 엔드포인트로 확장).
- 아키텍처: Router → Service → Repository, SQLAlchemy 2.0 async + asyncpg, Alembic async, JWKS(ES256) JWT 인증.
- 로컬 개발: docker-compose (Postgres + uvicorn hot reload).
- 운영 인프라:
  - **Container App** `eundunhealth-api` (RG `apps`, Korea Central) — 이미지 교체 + 환경변수 일괄 swap으로 무중단 cutover.
  - **Azure PostgreSQL** `healthapp` 동일 인스턴스 재활용. 스키마 무변경 (`alembic stamp head`로 기준점 설정 후 v0.3 마이그레이션 적용 → head `24d0fe2eb397`).
  - 옛 Ktor 이미지와 디렉토리(`backend/`)는 정리. Ktor 코드는 `D:\backup\dev\project\eundunHealth\`로 이관.

### Android 클라이언트 (v0.1)

- **에러 핸들링 통일** — `AppError` sealed class + `Throwable.toAppError()` + `AppError.reportToSentry()`. ViewModel 7개에 `_error: MutableStateFlow<AppError?>` + `clearError()` 패턴 통일. Screen 측은 `error.userMessage`를 SnackBar로 표시.
- **비밀번호 재설정** — `ForgotPasswordScreen` + `AuthViewModel.resetPassword` + Supabase `resetPasswordForEmail`.
- **UX 컴포넌트** — `ErrorContent`/`EmptyContent` 신규 + Home/History `PullToRefreshBox`.
- **회원 탈퇴** — `DELETE /account` + AlertDialog 확인 + Supabase Admin API로 Auth + 앱 DB 일괄 삭제.
- **OSS ExerciseDB 전환** — RapidAPI(`exercisedb.p.rapidapi.com`) → **OSS** `oss.exercisedb.dev`. 인증 불필요. DTO/API/DataSource 재작성 + Gson 회귀 테스트 5건.

### v0.2 — 운동 추천 + 통계

- **PUSH/PULL/LEGS 알고리즘** — 부위 균형(`chest+shoulders` / `back+upper arms` / `upper legs+lower legs`) + 화·목·토 cardio 분배 + 이전 주 운동 후순위(`excludeIds`). `GET /weekly-plan/previous` 신규.
- **통계 대시보드** — Vico 2.1.0 차트로 12주 완료율 + 현재/최장 스트릭. `GET /weekly-plan/statistics` 연동.
- **Detekt + Spotless + pre-commit + Android CI workflow** — `baseline-debug.xml`로 점진적 정리.

### v0.3 — 휴식일 + 목표 + 배지 확장

- **휴식일 커스터마이징** — `UserProfile.restDay` (ISO 1=월~7=일) + `SegmentedButton` + `WorkoutRepositoryImpl` 동적 슬롯 배치. 6요일에 push/cardio_a/pull/cardio_b/legs/mixed 순서.
- **목표 + 진행 차트** — `GoalScreen` 체중·체지방률 입력 + Vico 차트(`/profile/history`). `GET/PUT /goals` 연동. `user_profile_history`·`goals` 테이블 + Alembic 마이그레이션 (`24d0fe2eb397`).
- **배지 9종 확장** — 마일스톤 4 (`first_workout`/`workouts_10/50`/`streak_8weeks`) + 목표 달성 2 (`goal_weight/body_fat_achieved`). `CheckAndAwardBadgesUseCase.FIRST_WORKOUT` 로직.

### Supabase 한국 리전 전환

- 옛 프로젝트(`hcowzkqapzlvrvmawfcd`, US) → **한국 리전** `ttzzbfoksncqazvcsfiu`. Container App env/secret + Android BuildConfig 일괄 갱신. 옛 Auth 사용자 데이터는 출시 전 단계라 모두 무효화. Azure PG의 5개 사용자 테이블 `TRUNCATE`로 정리(`alembic_version`은 보존).

### 인프라 / CI/CD

- **Backend GitHub Actions** — ruff + mypy(strict) + pytest + Codecov → Docker compose runtime smoke (INC-03 차단) → pip-audit + bandit + gitleaks → main push 시 Trivy + ACR push + **secret precheck (INC-18 차단)** → Container App 배포 → /health.
  - `workflow_dispatch` trigger 지원 — paths 필터 우회로 secret rotation/긴급 재배포 가능.
  - 자동 배포 end-to-end 검증: revision `eundunhealth-api--0000007` 활성, /health 200 OK.
- **Android GitHub Actions** — spotlessCheck + detektDebug + testDebugUnitTest + assembleDebug. (`gradlew` + `scripts/*.sh` git exec bit 부여로 Linux runner에서도 실행 가능)
- **Dependabot** — pip + github-actions + gradle 주간 PR.
- **운영 시크릿** (총 5) — `database-url`(asyncpg URL), `supabase-url`, `supabase-service-role-key`, `sentry-dsn-backend`, ACR pull credential. 옛 `db-password`, `jwt-secret`은 미사용으로 제거.
- **GitHub Actions secret** — `AZURE_CREDENTIALS` (service principal + AcrPush role). 만료 갱신 절차: `scripts/register-azure-credentials.ps1 -Verify`.
- **Sentry 분리** — Android `eundunhealth` + Backend `eundunhealth-backend` 두 프로젝트. tracesSampleRate DEBUG=1.0 / PROD=0.2.
- **ACR 정리 후크** — Basic SKU retention 미지원 → `redeploy.sh`가 timestamp 태그 최근 5개만 보존하도록 자동 untag.
- **release 빌드** — `assembleRelease`(R8+서명) + `bundleRelease`(AAB 7.7MB) + ProGuard mapping Sentry 자동 업로드. keystore alias 수정(`eundunhealth_sign_key`). `scripts/preflight-release.sh`로 일괄 검증.

### 인시던트 + 재발 방지 (INC-01~18)

- 운영 사고·회귀 18건을 `docs/ops/incident-log.md`에 정리. 각 인시던트마다 **증상 → 근본 원인 → 복구 → 재발 방지** 4단으로 기록.
- 자동화 정착: `scripts/preflight-release.sh` (INC-04), `scripts/alembic-autogen.sh` (INC-07), `scripts/register-azure-credentials.ps1` (INC-17), `backend.yml` "Verify required Container App secrets exist" step (INC-18), `runtime-smoke` job (INC-03).
- 강제 룰: `CLAUDE.md`에 룰 1~6 영구 명시 (ACR untag-only, releaseArtifacts, alembic-autogen, lifespan middleware 금지, Supabase 교체 금지, secretref 동기화).
- 공통 안전망: `.github/PULL_REQUEST_TEMPLATE.md` destructive-ops 5문항 + AAB/APK 동기 + 마이그레이션 PG 검증 + secret 동기화 체크리스트.

### 버그 수정 (CRITICAL/HIGH/MEDIUM)

- **[CRITICAL]** `WeeklyPlanDao`에 `userId` 필터 누락 → 단말에서 사용자 전환 시 옛 사용자 캐시 노출 가능. EundunDatabase v1→v2.
- **[HIGH]** `TokenAuthenticator` 무한 대기 → 5초 timeout + 실패 시 token 무효화.
- **[HIGH]** release `signingConfig` 미연결.
- **[MEDIUM]** `BadgeRepository.hasBadge`를 `Result<Boolean>`로 통일 + 1분 TTL 캐시 + Repository에서 직접 `Sentry.captureException` 제거 (ViewModel `reportToSentry`로 일원화).
- **[Backend]** starlette 0.49+ `add_middleware`가 lifespan 내부 호출 금지 → 모듈 레벨로 이동. Docker 스모크 검증에서 발견.

### 성능 개선

- `DateTimeFormatter` 싱글톤 (HistoryScreen 카드별 재생성 제거).
- `ProfileSlider`의 `format` 결과를 `remember`로 캐싱.
- Coil `ImageRequest.size(512)`로 GIF 다운스케일.

### 운영 문서

- `docs/ops/migration-runbook.md` — Ktor→FastAPI 절차 + 사후 정리 결과.
- `docs/ops/monitoring-and-cost.md` — Sentry 활성화 가이드 + ACR Basic 한계 + Azure 비용 알림.
- `docs/ops/play-store-release.md` — 첫 출시 8단계 + 데이터 안전 답변표.
- `docs/ops/operations-snapshot.md` — 현재 운영 상태 단일 출처.
- `docs/privacy-policy.md` — 한국 리전 반영 개인정보 처리방침.
- `docs/ops/containerapp-env-ktor-backup.json` — cutover 직전 env 스냅샷(보존).

### 검증 / 품질

- Backend: pytest **41/41 PASS** (12 v0.1 + 8 edge case + 8 v0.2 + 13 v0.3), mypy strict clean, ruff/bandit clean, pip-audit (1 ignored: `PYSEC-2026-161` starlette 1.0 미지원).
- Android: spotlessCheck clean, detektDebug clean, unit test 일체 PASS, assembleRelease + bundleRelease BUILD SUCCESSFUL.

### 출시 직전 안정화 (PR #19–#26, Dependabot 정리)

5월 25일 v0.1.0 첫 milestone 이후 출시 직전까지 진행된 안정화. 운영 silent 버그 6건 정리 + 의존성 정비 + Compose 가독성 개선.

#### Phase 1–4: OpenAPI 자동 생성 + drift detection 인프라 (#19)
- backend FastAPI 스펙(`backend/openapi.json`)을 Android Retrofit 클라이언트의 단일 출처로 만들기 위한 side-by-side 인프라.
- `openapi-generator 7.10.0` Gradle 플러그인 도입 — `:app:openApiGenerate`가 `preBuild`에서 자동 실행, 출력은 `app/build/generated/openapi/com/gunnys/eundunhealth/api/generated/`.
- `scripts/sync-openapi.sh` 신규 — 라우터/스키마 변경 시 spec 재생성.
- `backend.yml`의 "Verify OpenAPI spec is in sync" CI step — spec drift PR 단계에서 fast-fail.
- backend 라우터 15개 endpoint에 `operation_id` 명시 + `weekStart` Query alias로 통일. 숨은 drift 동시 수정: `getWeeklyPlan`이 보낸 `weekStart` 쿼리를 backend가 무시하고 default(today)로 fallback하던 버그.

#### Phase 5A: schema drift 4건 + critical 버그 동시 수정 (#20)
출시 차단 사유 2건 + 부수 drift 2건 발굴:
- 🔴 `updateDayCompletion` body mismatch — 매번 422 silent fail (운동 완료 토글이 서버에 저장 안 됨). day-level (`weekStart, date, completed`) 도메인으로 통일. statistics 일관성을 위해 day의 `isCompleted`와 해당 day의 모든 `exercises[*].completed`를 동시 갱신.
- 🔴 `getWeeklyPlanHistory` envelope/list 불일치 — Gson deserialization 실패로 HistoryScreen 깨짐. `WeeklyPlanHistoryResponse(plans, totalCount, page, size)` envelope 신규.
- 🟡 `UserProfileResponse`/`WeeklyPlanResponse`에 `userId`/`id` 누락 — Android에서 빈 string fallback. 필드 추가.

#### Phase 5B+5C: Repository를 generated client로 + EundunApi 제거 (#21)
- 4 Repository(Badge, Goal, User, Workout) + `AuthRepository.deleteAccount` → generated `BadgesApi`/`GoalsApi`/`ProfileApi`/`WeeklyPlanApi`/`AccountApi` 사용.
- 추가 silent drift 2건 동시 fix:
  - `POST /weekly-plan` 응답을 dict → `WeeklyPlanResponse` (Android Room cache의 `id=""` 저장 문제 해결).
  - `POST /badges/{key}` 응답을 dict → `BadgeResponse` (BadgeCatalog 룩업 실패로 빈 라벨 문제 해결).
- `EundunApi.kt`(70줄)+`ApiDtos.kt`(80줄) 제거, side-by-side 종료. 단일 진실 출처는 `backend/openapi.json`.
- 공통 helper `data/remote/util/ResponseExt.kt`의 `bodyOrThrow()` 도입.

#### Phase 6A: Compose 가독성 정리 (#22)
- `ui/components/ProfileSlider.kt` 신규 — Onboarding/Profile 양쪽 공유.
- `ProfileScreen` 분해: `BodyMetricsSliders` / `RestDaySelector` / `ProfileActionButtons`.
- `HomeScreen` 분해: `HomeTopBarActions` / `HealthConnectPromptCard`.
- ViewModel 패턴 통일은 분석 결과 over-refactoring으로 판단해 스킵 (각 ViewModel은 이미 단일 책임으로 모델링됨).

#### Dependabot 일괄 정리 (#2–#6, #12·#14, #23–#26)
의존성 위생 정리. 일부 dependabot PR이 main 변경 사이 반복 stale로 빠지는 race condition을 직접 결합 PR로 해결하는 패턴 정착.

**GitHub Actions** — `setup-python` 5→6, `upload-artifact` 4→7, `gradle/actions` 4→6

**Android Gradle**
- `gradle-wrapper` 9.4.1→9.5.1
- `sentry` 8.16.0→8.42.0
- `spotless` 7.0.4→8.5.1 (+ 새 룰에 맞춰 3 Kotlin file 자동 reformat)
- `coreKtx` 1.16.0→1.18.0, `lifecycleRuntimeKtx` 2.9.0→2.10.0
- `hiltNavigationCompose` 1.2.0→1.3.0, `navigationCompose` 2.9.0→2.9.8
- `dataStore` 1.1.4→1.2.1

**Python (backend)**
- `pytest` 8.3.0→9.0.3, `pytest-asyncio` 0.24.0→1.3.0, `pytest-cov` 6.0.0→7.1.0
- `uvicorn` 0.34.0→0.48.0, `sqlalchemy` 2.0.36→2.0.50, `alembic` 1.14.0→1.18.4
- `sentry-sdk` 2.19.0→2.60.0, `pydantic-settings` 2.7.0→2.14.1
- `PyJWT` 2.12.0→2.13.0, `asyncpg` 0.30.0→0.31.0
- `ruff` 0.8.0→0.15.14, `bandit` 1.8.0→1.9.4, `pip-audit` 2.7.3→2.10.0
- `httpx` 0.28.0→0.28.1, `aiosqlite` 0.20.0→0.22.1

**보류 (v0.1.0 출시 후 재검토)**
- `kotlin` 2.2.10→2.3.21 — Compose Compiler·Hilt·KSP 호환성 매트릭스 검증 필요.
- `starlette` 0.49.1→1.1.0 — INC-2026-05-24-03(lifespan 회귀) 위험 + fastapi 0.136.3이 starlette 1.x 지원하는지 확인 필요.
- `healthConnect` 1.1.0-rc01→1.2.0-alpha04 — rc → alpha 다운그레이드. 1.2 stable 릴리스 대기.

---

## v0.0.3-2 (2026-05-22)

### Sentry SDK 메이저 업그레이드
- Sentry Android SDK 7.14.0 → 8.16.0 (16KB 페이지 정렬 네이티브 라이브러리 포함)
- Sentry Gradle Plugin 4.14.1 → 5.8.0 (SDK 8.x 호환 필수)
- AndroidManifest에서 SentryInitProvider 자동 초기화 비활성화 (`tools:node="remove"`)
- EundunHealthApplication에서 DSN 빈값 시 `isEnabled = false` 처리 (크래시 방지)
- `isEnableAutoSessionTracking` 제거 (8.x 기본값)
- Sentry Gradle Plugin: 환경 변수 `SENTRY_AUTH_TOKEN` 우선 참조, 토큰 없으면 매핑 업로드 자동 비활성화
- `packaging.jniLibs.useLegacyPackaging = false` 추가 (16KB ZIP 정렬)

### 백엔드 JWT 인증 변경
- Supabase JWT 서명 알고리즘 변경 대응: HMAC256 → JWKS 기반 ES256 공개키 검증
- `com.auth0:jwks-rsa:0.22.1` 의존성 추가
- JwkProviderBuilder로 JWKS 엔드포인트 캐시 (10키, 24시간, 분당 10회 제한)
- `SUPABASE_JWT_SECRET` 환경 변수 더 이상 불필요 (공개키 자동 조회)

### 프로필 편집 기능 추가
- ProfileScreen / ProfileViewModel 신규 생성
- 홈 상단바에 Person 아이콘 추가 → 프로필 편집 화면 진입
- 서버에서 기존 프로필 로드 → 슬라이더 초기값 세팅 → 수정 후 저장
- Screen.kt에 Profile route 추가, AppNavigation에 라우팅 연결

### 인증 에러 UX 개선
- AuthRepositoryImpl에 `mapAuthError()` 추가
- Supabase 에러 코드를 한국어 사용자 메시지로 매핑 (invalid_credential, email_not_confirmed, weak_password 등)

### 시스템 UI 겹침 해결
- LoginScreen, SignupScreen: `imePadding()` + `verticalScroll()` 추가 (키보드가 입력 필드 가리는 문제)
- OnboardingScreen, ProfileScreen: `imePadding()` + `verticalScroll()` 추가, `Spacer(weight)` → `Spacer(height)` (스크롤과 weight 충돌 제거)

### 리팩토링
- OnboardingViewModel, ProfileViewModel: SupabaseClient 직접 의존 제거 → `AuthRepository.getCurrentUserId()` 사용
- ProfileViewModel: stringly-typed `saveResult: String?` → `SaveState` sealed class (Idle/Success/Error)
- ProfileSummaryCard 공통 컴포넌트 추출 (OnboardingScreen, ProfileScreen에서 재사용)
- OnboardingScreen, ProfileScreen에서 Card/CardDefaults 불필요 import 제거

### 문서
- CLAUDE.md 생성 및 업데이트 (배포 명령어, ViewModel 패턴, JWT 알고리즘, 시간대 등)

---

## v0.0.3 (2026-05-21)

### Android 17 (API 37) 대응
- compileSdk/targetSdk 36 → 37, AndroidManifest tools:targetApi 37
- 사이드 이펙트 분석: 앱 기능(인증, REST API, Health Connect, Room)에 영향 없음 확인

### 의존성 업데이트 (API 37 호환)
- Hilt 2.56.2 → 2.59.2 (AGP 9.x 호환성 개선)
- Compose BOM 2025.05.01 → 2026.05.01 (최신 Compose)
- Activity Compose 1.10.1 → 1.13.0 (edge-to-edge 대응)
- Room 2.7.1 → 2.8.4 (버그 수정)
- Supabase 3.1.4 → 3.6.0 (안정성 개선)
- Ktor 3.1.2 → 3.5.0 (Supabase 호환)

### 네트워크 보안 강화
- network_security_config.xml에 `base-config cleartextTrafficPermitted="false"` 추가
- Release 빌드에서 HTTP cleartext 통신 명시적 차단
- CLEARTEXT communication to 10.0.2.2 에러 방어 처리

### Sentry 설정 수정
- sentry-android-okhttp → sentry-okhttp 모듈 전환 (deprecated 해결)
- Sentry project slug `eundunhealth-android` → `eundunhealth` 수정 (404 에러 해결)

### 리팩토링
- AuthViewModel: SupabaseClient 직접 호출 제거 → AuthRepository 인터페이스 사용으로 전환
- AuthRepository.restoreSession() 추가: 자동 로그인 시 tokenHolder 설정 (401 에러 근본 원인 수정)
- WorkoutRepositoryImpl: `android.util.Log` → `Sentry.captureException()` 전환 (프로덕션 에러 추적)
- DayPlanJson/ExerciseJson → `PlanJsonModels.kt` 별도 파일 분리 (단일 책임 원칙)
- WeeklyPlanDao: 빈 userId 파라미터 제거, weekStart만으로 캐시 조회
- ExerciseDB OkHttpClient에 RetryInterceptor + 15초 타임아웃 추가

### 빌드 개선
- AGP 9.1.1 → 9.2.1 업데이트
- gradle.properties에서 불필요한 deprecated 옵션 정리
- AGP 9.x 호환성 모드 플래그 주석 문서화

---

## v0.0.2 (2026-05-21)

### Sentry 크래시 모니터링 통합
- Sentry Android SDK 7.14.0 통합 (크래시/ANR 자동 캡처, 세션 트래킹)
- Sentry OkHttp Interceptor로 네트워크 요청 트레이싱
- Sentry JVM SDK로 Ktor 백엔드 500 에러 자동 캡처 (StatusPages 연동)
- Release 빌드 시 ProGuard 매핑 자동 업로드 (난독화된 스택 트레이스 복원)

### 네트워크 안정성
- OkHttp RetryInterceptor 추가 (최대 3회, exponential backoff 500ms/1s/2s)
- OkHttp TokenAuthenticator 추가 (401 시 Supabase 토큰 자동 갱신)
- 연결/읽기 타임아웃 15초 설정
- Release 빌드에서 HTTP 로깅 비활성화

### 운동 완료 수동 체크
- Backend: `PATCH /weekly-plan/complete` 엔드포인트 추가
- DayPlanCard 탭으로 운동 완료/미완료 토글
- Optimistic update (즉시 UI 반영, 서버 실패 시 롤백)
- Health Connect 자동 감지 완료를 서버에 동기화

### 주간 진행률 대시보드
- HomeScreen 상단에 주간 완료율 카드 (LinearProgressIndicator)
- 완료/전체 운동일 수 및 퍼센트 표시

### 운동 기록 히스토리
- Backend: `GET /weekly-plan/history?page=0&size=10` 페이지네이션 API 추가
- HistoryScreen 신규 생성 (무한 스크롤, LazyColumn + derivedStateOf)
- 주별 완료율 + 요일별 체크 아이콘 표시
- HomeScreen TopAppBar에 히스토리 아이콘 추가

### 스켈레톤 UI
- ShimmerBox 컴포넌트 (shimmer 애니메이션)
- HomeScreen 로딩 시 스켈레톤 카드 5개 표시 (CircularProgressIndicator 대체)

### 입력 검증 강화
- 온보딩 ProfileSlider에 범위 초과 시 빨간색 에러 표시 + 안내 메시지
- 입력 요약 카드 추가 (등록 버튼 위에 현재 입력값 요약)
- 프로필 저장 실패 시 Sentry 에러 캡처

### 다크모드 수동 토글
- ThemePreferences (DataStore) 생성 — SYSTEM/DARK/LIGHT 3단계 순환
- HomeScreen TopAppBar에 테마 토글 아이콘 (BrightnessAuto/DarkMode/LightMode)
- 앱 재시작 시 설정 유지

### 배지 상세 강화
- BadgeDisplayItem에 earnedAt 필드 추가
- 배지 획득 날짜 표시 (yyyy.M.d 형식)

### GIF 로딩 개선
- Coil ImageLoader에 메모리/디스크 캐시 정책 활성화
- WorkoutDetailScreen AsyncImage → SubcomposeAsyncImage 전환
- 로딩 중 CircularProgressIndicator, 에러 시 안내 메시지 표시

### Health Connect 개선
- HealthConnectDataSource에 SDK 가용성 체크 추가
- 동기화 실패 시 Sentry 에러 캡처

### 인프라
- Sentry Gradle Plugin 4.14.1 추가 (ProGuard 매핑 자동 업로드)
- DataStore Preferences 1.1.4 의존성 추가
- proguard-rules.pro에 Sentry/DataStore keep 규칙 추가

---

## v0.0.1 (2026-05-21)

### MVP 초기 구현
- 이메일/비밀번호 회원가입 및 로그인 (Supabase Auth)
- 자동 로그인 (Supabase 세션 영속성 + Splash 화면)
- 신체정보 입력 온보딩 (키, 몸무게, 체지방률, 근육량 — Slider + 키보드 하이브리드)
- ExerciseDB API 기반 주간 운동 계획 자동 생성 (근력 + 유산소 + 휴식일)
- 운동 상세 화면 (GIF 애니메이션, 세트/횟수, 운동 방법)
- Health Connect 연동 (운동 세션 자동 감지 → 완료 표시)
- 챌린지 배지 시스템 (1주/2주/3주 연속 완료)
- Room 로컬 캐시 (오프라인 플랜 조회)

### 백엔드
- Ktor 3.4.3 + Exposed ORM + PostgreSQL (Azure Flexible Server)
- Supabase JWT 인증 (Bearer 토큰 검증)
- REST API: profile CRUD, weekly-plan CRUD, badges CRUD
- AppConfig 패턴으로 환경변수 중앙화 (System.getenv → dotenv 폴백)
- CORS 동적 설정 (AppConfig.allowedOrigins)
- Health check 엔드포인트 (`GET /health` — DB 연결 검증)

### 배포
- Docker 멀티스테이지 빌드 (gradle:8.14-jdk17 → eclipse-temurin:17-jre-alpine)
- Azure Container Registry + Azure Container Apps 배포
- non-root 유저, HEALTHCHECK, Shadow Fat JAR
- deploy.sh / redeploy.sh 스크립트

### 코드 리뷰 기반 리팩토링
- R8 missing classes 해결 (proguard-rules.pro)
- BuildConfig local.properties 연동 수정
- network_security_config.xml 추가 (실기기 HTTP 허용)
