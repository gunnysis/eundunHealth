# Multi-AI Handoff Log

## 2026-10-16 (Gemini -> Claude) '오늘의 활동' 완전 제거 및 메인페이지(HomeScreen) UI/UX 리디자인

**현재까지의 작업 상태 (Current Status):**
- **'오늘의 활동' 및 레거시 코드 완전 제거 (6개 파일 삭제)**:
  - 삭제 파일: `TodayActivityCard.kt`, `DailyActivity.kt`, `GetTodayActivityUseCase.kt`, `HealthConnectMappers.kt`, `GetTodayActivityUseCaseTest.kt`, `HealthConnectMappersTest.kt`.
  - Health Connect 권한 축소(4개 → 1개 `READ_EXERCISE` 단독): `AndroidManifest.xml`, `HealthConnectDataSource.kt`, `HealthRepository.kt`, `HealthRepositoryImpl.kt`, `PermissionsRationaleActivity.kt`, `HomeViewModel.kt`에서 `READ_STEPS`·`READ_TOTAL_CALORIES_BURNED`·`READ_HEART_RATE` 및 일일 활동 조회 로직을 전수 제거했습니다.
- **메인페이지(`HomeScreen.kt`, `SkeletonUi.kt`) Material 3 UI/UX 리디자인 (`PRD PLAN-03` 완수)**:
  - `WeeklyProgressCard`: 주차 날짜 범위(`M/d - M/d`), 완료율 배지, 애니메이션 `LinearProgressIndicator`(`StrokeCap.Round`), 월~일 7일 상태를 한눈에 보여주는 `WeeklyDayStampRow` 추가.
  - `DayPlanCard`: KST(`Asia/Seoul`) 기준 오늘 날짜(`isToday: Boolean` 원시 파라미터 전달로 Compose Strong Skipping 리컴포지션 최적화)에 Primary 2.dp 테두리 및 `"오늘"` 배지 표시, 운동 종목 수 서브타이틀 추가.
  - `ExerciseRow`: 운동 유형 아이콘(`STRENGTH`/`CARDIO`), 운동명, 부위·장비(`bodyPart · equipment`) 서브타이틀, `세트×횟수` 배지 및 `KeyboardArrowRight` 어포던스를 갖춘 구조화된 카드 행으로 개선.
- **재발방지 가드 테스트 및 문서/법적 고지 SSoT 동기화**:
  - `ManifestHealthConnectRationaleTest.kt`에 `READ_EXERCISE` 단독 권한 선언 가드 및 `PermissionsRationaleActivity` 레거시 문구 부재 검증 테스트(+2 `@Test`) 추가.
  - `HomeViewModelTest.kt`에 `today`(KST) 상태 보존 및 `toggleError` 복구 테스트 보강 (현재 총 Android 단위 테스트: **30개 파일 / 146 `@Test` PASS**).
  - `docs/store/privacy-policy.md`, `docs/store/account-deletion.md`, `docs/store/health-connect-permissions.md` 수정 및 `bash scripts/sync-legal-docs.sh`로 `backend/app/legal/*.md` 동기화 완료 (`pytest tests/test_legal.py` 통과).

**다음 할 일 (Next TODOs for Claude):**
- 차기 스토어 릴리스(예: `v0.3.2`) 시 Play Console의 **앱 콘텐츠 → 건강 앱 선언(Health Connect 권한)** 에서 `READ_STEPS`·`READ_TOTAL_CALORIES_BURNED`·`READ_HEART_RATE` 체크를 해제하고 `READ_EXERCISE` 1종만 남기세요 (`docs/store/health-connect-permissions.md` 참조).

**의존성 및 주의사항 (Context & Constraints):**
- `HealthConnectDataSource.kt`의 `private val client: HealthConnectClient by lazy` 명시적 타입 어노테이션은 detekt `RedundantSuspendModifier` 오탐 방지를 위해 반드시 유지해야 합니다.
- `ManifestHealthConnectRationaleTest`가 매니페스트의 `android.permission.health.*` 권한이 `READ_EXERCISE` 1종뿐인지 자동 검사하므로, 미사용 건강 권한을 매니페스트에 추가하지 마십시오.

## 2026-10-16 (Gemini -> Claude) 프로젝트 문서 전수 최신화 및 플랜 아카이브

**현재까지의 작업 상태 (Current Status):**
- `v0.3.1`(`versionCode=36`, Play 프로덕션 10% 단계적 출시 LIVE) 및 백엔드 revision `eundunhealth-api--0000062`(이미지 `2610d18`, Alembic head `0dda67f1638a`) 기준으로 프로젝트 전 문서를 동기화했습니다:
  - `CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/SPEC.md`, `docs/CHANGELOG.md`
  - 라우트 수(20개 = 16 JWT + 4 공개), 화면/VM 수(11 Screens / 10 ViewModels / 5 Hilt modules / 6 OpenAPI clients), DB 테이블(앱 7 + `alembic_version`), Key Vault 활성 시크릿(8종), GitHub Actions 워크플로(7종), 테스트 수(Android `@Test` 151 · Backend pytest 119), 최신 의존성 버전을 실측치와 일치시켰습니다.
- `scripts/agents/doc_audit.py`의 `count_routes_in_text()`가 `APIRouter(..., prefix="/meals")`의 prefix를 라우트 경로에 결합하도록 보강하고 단위 테스트(`scripts/agents/test_doc_audit.py`)를 추가했습니다.
- 완료된 2026-10 설계/계획 문서 4건을 토픽 렛저로 아카이브(흡수 후 원본 삭제)하고 `docs/plans/README.md` 인덱스를 갱신했습니다:
  - `docs/plans/2026-10-15-exercise-korean-localization-design.md`(→ `docs/plans/logs/android.md` 2026-10-15 entry 로 흡수)
  - `docs/plans/2026-10-15-agp10-build-modernization-design.md`(→ `docs/plans/logs/process-infra.md` 2026-10-15 entry 로 흡수)
  - `docs/plans/2026-10-16-production-deployment-hardening-{design,plan}.md`(→ `docs/plans/logs/process-infra.md` 2026-10-16 entry 로 흡수)

**다음 할 일 (Next TODOs for Claude):**
- Play Console에서 `v0.3.1`(`versionCode=36`) 10% 단계적 출시 모니터링 후 이상 없을 시 100% 롤아웃 승격 시점에 `docs/ops/operations-snapshot.md` §1 표기만 갱신하세요.
- 신규 라우터에 `APIRouter(prefix=...)`를 사용할 경우 `doc_audit.py`가 자동으로 prefix를 결합하므로 문서의 라우트 표에는 전체 경로(`/meals/generate` 등)를 기재하세요.

**의존성 및 주의사항 (Context & Constraints):**
- `docs/plans/logs/*.md` 및 `docs/plans/README.md`는 `.gitattributes`에 의해 `eol=lf`가 강제됩니다.
- `scripts/check-plans-links.sh`, `scripts/gen_plans_index.py --check`, `scripts/agents/doc_audit.py --collect-only` 가드가 모두 통과하는 상태를 유지하세요.

## 2026-10-15 배포 전 전체 감사

- 배포는 수행하지 않았다. Android 151 unit tests/Lint/Detekt/R8와 backend 117 pytest/94% coverage/Ruff/mypy/Bandit/pip-audit, Docker PostgreSQL→Alembic→Uvicorn health smoke를 통과했다.
- PyJWT 2.14.0의 공개 취약점 2건을 2.15.1로 해소했고, backend CI에 `ruff format --check`를 추가했다.
- OpenAPI 입력을 로컬 `RegularFile`로 추적하고 generator를 process worker로 격리해 stale-cache 및 Gradle daemon Metaspace 문제를 제거했다.
- Windows/WSL에서 plans index와 OpenAPI sync가 각각 경로/환경 전달 때문에 실패하던 문제를 수정했다.
- 식단 AI는 중첩 retry와 비멱등 POST 재시도를 제거하고 timeout/token/schema/의료 면책을 보강했다. 근거 없는 30세·활동계수·4:4:2 목표 계산은 제거했다.
- v0.3 배포 전 사용자 확인 필요: Azure AI Foundry 모델의 실제 처리 지역·보존 조건을 확정하고 개인정보 처리방침/Play 데이터 안전 고지를 일치시킬 것.

## 2026-10-15 AGP 10 빌드 현대화

- AGP 10에서 삭제되는 `android.builtInKotlin=false`와 `android.newDsl=false`를 제거하고 AGP 9 built-in Kotlin/new DSL을 사용한다.
- Kotlin Android 플러그인 alias는 제거했다. OpenAPI Kotlin 생성 소스는 `AndroidSourceSet.kotlin.directories`로 등록한다.
- 근본 원인은 Hilt가 아니라 detekt 1.23.8 플러그인의 legacy variant API 및 Gradle 10 `ReportingExtension.file` 호출이었다.
- app에는 detekt 플러그인을 적용하지 않는다. 루트 `apply false`로 task type만 제공하고, `androidComponents` 안에서 `configureJavaCompileTask`의 classpath/destination + SDK boot classpath를 쓰는 `detektDebug`/`detektBaselineDebug` custom task를 유지한다. AGP 10에서 삭제되는 직접 `getJavaCompile()`과 `Variant.getCompileClasspath()`는 쓰지 않는다.
- detekt 2.x는 아직 alpha라 도입하지 않았다. 정식 릴리스 후 custom wiring 제거와 baseline drift를 함께 재검토한다.
- `android.dependency.useConstraints=true`와 `android.uniquePackageNames=false`도 제거해 AGP 9 기본값과 sync 최적화를 따른다.
- 재발 방지: `scripts/check-agp10-compat.sh`를 Android CI와 pre-commit에 연결했다.
- 상세: `docs/plans/2026-10-15-agp10-build-modernization-design.md`(→ `docs/plans/logs/process-infra.md` 2026-10-15 entry 로 흡수). 배포는 수행하지 않았다.

## 2026-10-15 운동 콘텐츠 한국어 로컬라이징

- `korean_exercises.json`은 앱이 현재 요청하는 추천 풀 46개를 `exerciseId` 기준으로 번역한다.
- 이름과 지침을 결합·정규화한 원문의 SHA-256이 일치할 때만 한글 이름/지침으로 교체하며, 변경·신규 운동은 영문으로 폴백한다.
- 서버/Room plan JSON은 영문 canonical로 유지하고 `WorkoutRepositoryImpl` 반환 경계에서만 한글화한다.
- asset 로드는 suspend single-flight이며 손상 시 빈 catalog를 캐시하고 coroutine 취소는 전파한다.
- 공식 Free V1의 AscendAPI 출처 링크를 프로필에 추가했다. 2026-10-15 현재는 무료·비상업 앱이나 운영 조건은 확정하지 않으며, 공개 범위·수익 모델·상용 운영 변경 시 AscendAPI 플랜을 재검토한다(Azure 비용과 무관).
- 실 API ID/지문 46/46 일치 및 translation/repository 단위 테스트 통과. 상세: `docs/plans/2026-10-15-exercise-korean-localization-design.md`(→ `docs/plans/logs/android.md` 2026-10-15 entry 로 흡수).
- 비용 정책: 런타임 번역/추가 DB/백엔드 호출 없음. Free V1 사용 적합성은 운영 조건이 바뀔 때마다 재평가하고, 누락 관측은 고유 50개로 제한한다.
- 릴리스 전 `pwsh scripts/verify-exercise-translations.ps1`로 현재 추천 풀 ID/이름·지침 지문 드리프트를 확인한다. 일시 오류는 제한 재시도하며, 외부 API rate limit 때문에 매 CI에는 배선하지 않았다.

## 2026-10-10 (Gemini -> Claude)

**현재까지의 작업 상태 (Current Status):**
- `HomeTopBarActions.kt` 내 오버플로 메뉴를 Material Design 3 가이드라인에 기반하여 3개의 역할별 그룹(운동 및 활동, 식단, 설정 및 계정)으로 재정렬하고, `HorizontalDivider`를 사용해 시각적으로 분리하는 리팩토링을 완료했습니다.
- 메뉴 가독성 및 개연성 향상을 위한 구조 개선 완료 (기록/통계/목표/배지 -> 식단 -> 프로필/테마/로그아웃).

**다음 할 일 (Next TODOs for Claude):**
- 메뉴에 추가 항목이 발생할 시 현재의 그룹핑(운동/식단/계정) 규칙을 유지하고 `HorizontalDivider`를 활용하세요.

**의존성 및 주의사항 (Context & Constraints):**
- Material 3의 `HorizontalDivider`가 적용되었습니다.

## 2026-09-26 (Gemini -> Claude)

**현재까지의 작업 상태 (Current Status):**
- 루트 `.env` 파일(DeepSeek Key 등 포함)이 공개 저장소에 유출되지 않도록 `.gitignore`에 등록 완료.
- `2026-09-20-meal-plan-api-selection-design.md` 문서(v0.3.0 완료 건)를 프로젝트 컨벤션(`docs/plans/README.md`)에 따라 `docs/plans/logs/backend.md` 토픽 렛저로 아카이브(흡수) 완료.
- `docs/SPEC.md` 내 설계 문서 참조에 리다이렉트(`(→ docs/plans/logs/backend.md 2026-09-20 entry 로 흡수)`)를 병기하여 링크 무결성 검증(`check-plans-links.sh`) 통과.
- `gen_plans_index.py`를 통해 `docs/plans/README.md` 인덱스 갱신 완료(활성 작업 0건).

**다음 할 일 (Next TODOs for Claude):**
- 추가적인 식단 기능 백엔드/클라이언트 작업 필요 시 `docs/plans/logs/backend.md`의 2026-09-20 엔트리 및 `docs/SPEC.md` 참조.

## 2026-09-24 (Gemini -> Claude)

**현재까지의 작업 상태 (Current Status):**
- ExerciseDB를 오픈소스 wger API로 전환하거나 정적 자체 DB(JSON)로 내재화하는 방안을 모두 검토 및 기획하였으나, **사용자(Product Owner)의 결정에 따라 기획이 반려(Reject)되었습니다.**
- 주된 반려 사유: wger API의 한국어 지원 미비 및 마땅한 한국어 대체 API의 부재.
- 작성되었던 마이그레이션 기획 문서(`2026-09-24-exercisedb-to-wger-migration-design.md`)는 파기되었습니다.

**다음 할 일 (Next TODOs for Claude):**
- 현재는 마이그레이션 없이 **기존 ExerciseDB 연동 방식을 그대로 유지**합니다. 
- 이와 관련하여 추가적인 코드 수정이나 아키텍처 변경 작업을 수행하지 마십시오.

**의존성 및 주의사항 (Context & Constraints):**
- 운동 데이터 소스는 당분간 기존 `ExerciseDbApi` 및 `ExerciseDbDataSource` (RapidAPI/AscendAPI 기반) 구조를 그대로 유지합니다.
