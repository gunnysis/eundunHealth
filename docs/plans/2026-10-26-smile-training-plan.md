---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: v0.5.0
ledger_topic: android
tags: [smile-training, camera, ml-kit, camerax, workmanager, backend-sync, implementation-plan]
---

# 스마일 트레이닝 구현 계획 (Smile Training Implementation Plan)

- **작성일**: 2026-10-26
- **페어 설계 문서**: `docs/plans/2026-10-26-smile-training-design.md`
- **목표**: 전면 카메라 + 온디바이스 ML Kit Face Detection 기반의 스마일 트레이닝 기능(오프라인 우선 Room SSOT + WorkManager 멱등 동기화 + FastAPI 백엔드 API + PostgreSQL Alembic 마이그레이션 + 루트 모달 Compose UI)을 엔드투엔드로 구현하고 검증한다.

---

## 1. 단계별 구현 체크리스트 (Phases)

### Phase 1: Backend API, PostgreSQL Migration & OpenAPI Sync (완료)
- [x] `backend/app/models/smile_training.py`: `SmileTrainingSession` 모델(`smile_training_sessions` 테이블, `sa.Uuid(as_uuid=True)` PK, `user_id`, `routine`, `difficulty`, `duration_sec`, `completed_reps`, `reflection_emoji`, `occurred_at`, `created_at`, 체크 제약 3종, 복합 인덱스 `ix_smile_training_sessions_user_occurred`) 생성 및 `backend/app/models/__init__.py` 등록.
- [x] `backend/alembic/versions/a3869f575d6a_create_smile_training_sessions.py`: `scripts/alembic-autogen.sh`로 PostgreSQL 16 컨테이너에서 마이그레이션 생성 및 upgrade/downgrade 검증(룰 3).
- [x] `backend/app/schemas/smile_training.py`, `backend/app/repositories/smile_training_repo.py`, `backend/app/services/smile_training_service.py`, `backend/app/routers/smile_training.py`:
  - `POST /smile-training/sessions`: 클라이언트 UUID 기반 멱등 생성(신규 201, 동일 payload 재전송 200, 상이 payload 또는 타 사용자 UUID 충돌 409).
  - `GET /smile-training/sessions`: `(occurred_at DESC, id DESC)` URL-safe Base64 커서 페이지네이션.
  - `GET /smile-training/statistics`: KST(`UTC+9`) 기준 당일 완료 여부(`completedToday`), `currentStreakDays`, `longestStreakDays`, 누적 횟수/시간 계산.
  - `DELETE /smile-training/sessions`: 인증 사용자의 전체 세션 삭제(`SmileTrainingDeleteResponse`).
- [x] 계정 삭제 및 고아 정리 연동:
  - `AccountService._purge_app_data(user_id)`에 `SmileTrainingRepository.delete_all_by_user(user_id)` 추가.
  - `ProfileRepository.list_all_user_ids()`가 7개 사용자별 테이블 전체의 `UNION`을 조회하도록 확장하여 프로필 없이 스마일 세션만 남은 고아 계정도 reaper가 감지하도록 보강.
- [x] `backend/tests/test_smile_training.py` (7개 테스트) 및 `backend/tests/test_account.py` 시드 확장: `pytest` 126 passed, `ruff`, `mypy`, `bandit`, Docker Compose 런타임 스모크(`alembic upgrade head` + `/health/ready` 200) 통과(룰 7).
- [x] `scripts/sync-openapi.sh` 및 `./gradlew :app:openApiGenerate` 실행으로 `backend/openapi.json` 및 Retrofit `SmileTrainingApi` 동기화 완료.

### Phase 2: Android Persistence, WorkManager Sync & Account Deletion Wipe (완료)
- [x] `gradle/libs.versions.toml` & `app/build.gradle.kts`: CameraX `1.6.2`, ML Kit Face Detection `17.1.0`, Play Services Base `18.11.1`, WorkManager `2.12.0`, Hilt Work `1.4.0`, Room Testing `2.8.5` 및 KSP `room.schemaLocation` 설정.
- [x] `SmileSessionEntity`, `SmileSessionDao`, `SmileTrainingDatabase` (`smile_training.db`, `exportSchema = true`) 구현.
- [x] `SmileSyncWorker` (`@HiltWorker`, `ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`, 지수 백오프) 및 `EundunHealthApplication`의 `Configuration.Provider` 구현.
- [x] `LocalDataWiper` (`DefaultLocalDataWiper`) 구현 및 `AuthRepositoryImpl.deleteAccount()` 연동(서버 계정 삭제 호출 전 `userId` 선캡처 후 로컬 DB 2종, DataStore, `smile-sync` WorkManager 작업 일괄 정리).
- [x] `SmileTrainingRepositoryImpl` 구현: Room SSOT 관찰(`observeSessions`, `observeSummaryStats`), 빈 로컬 DB 원격 복원(`refreshRemoteHistoryIfEmpty`), KST 연속일 계산, `PENDING`/`SYNCED`/`BLOCKED` 상태 전이, `retryBlockedSessions`, `deleteAllSessions`.

### Phase 3: Pure Domain Signal Evaluator & CameraX + ML Kit Adapter (완료)
- [x] `SmileRoutineRule` & `SmileSignalEvaluator`:
  - 프레임 수가 아닌 단조 증가 타임스탬프(`timestampElapsedMs`) 기반 유지 시간 누적.
  - 루틴/난이도별 진입·이탈 히스테리시스(`enterSmileThreshold` / `exitSmileThreshold`) 및 1,000ms 슬라이딩 윈도우 충족률(>= 75%)로 미세 노이즈 방어.
  - `trackingId` 변경 시 진행 중이던 홀드 게이지 즉시 초기화(타인 얼굴 교차 오염 방지) 및 일시정지 시 완료된 반복 횟수(`completedReps`) 보존(`clearActiveHold()`).
- [x] `MlKitFaceModuleInstaller`: Google Play services `ModuleInstallClient`로 unbundled 얼굴 인식 모듈 가용성 사전 확인 및 진행률 콜백 설치.
- [x] `MlKitSmileSignalAnalyzer`: `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`, `ResolutionSelector`(`640x480`), `CLASSIFICATION_MODE_ALL`, 모든 경로에서 `imageProxy.close()` 보장.

### Phase 4: UDF ViewModel, Compose UI & Root Modal Navigation (완료)
- [x] `SmileTrainingViewModel`: 룰 11(UDF-Enhanced) 준수(`@Immutable` `SmileTrainingUiState` & `SmileTrainingSideEffect`, 단일 `_uiState: MutableStateFlow`, `Channel<SmileTrainingSideEffect>(Channel.BUFFERED)`, 단조 시간 기반 세션 소요 시간 산출, 일시정지·재개 및 생명주기 `ON_STOP` 보호, 카메라 바인딩 에러 인라인 배너 전환).
- [x] `SmileCameraStage` & `SmileTrainingScreen`:
  - `Intro`, `PermissionRationale`, `PermissionBlocked`, `ModelPreparing`, `ModelUnavailable`, `Ready`, `Active`, `RepSuccess`, `Reflection`, `Completed` 전 단계 구현.
  - `WindowInsets.safeDrawing` 인셋 보호, `CompositingStrategy.Offscreen` + `BlendMode.Clear` 타원 비네트 가이드, 스프링 물리 보간 `SweepGradient` 프로그레스 링, 반복 단계 도트 인디케이터, 실시간 정렬 상태 배지, `ACTIVE` 단계 일시정지/중단 버튼.
  - 기기 카메라 하드웨어 유무 사전 점검(`FEATURE_CAMERA_FRONT`/`FEATURE_CAMERA_ANY`), 루틴별 난이도 반영 유지 시간·눈 뜨기 배지, 균등 5분할 목표 반복 선택기, 한국어 시간 포맷(`X분 Y초`), 세션 완료 요약 카드.
  - 카메라 활성 단계에서 `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` 유지 및 `BackHandler` 중단 확인 다이얼로그.
  - TalkBack `LiveRegionMode.Polite` 및 단계 전환 접근성 안내(프레임마다 음성 스팸 금지).
- [x] 내비게이션 연결: `Screen.SmileTraining`, `ProgressHubScreen`의 "마음 연습 > 스마일 트레이닝" 카드, `MainAppShell` 루트 콜백, `AppNavigation` 루트 모달 route 등록.

### Phase 5: Verification, Quality Gates & Multi-Role Audit Refactoring (완료)
- [x] 단위 테스트(`SmileSignalEvaluatorTest`, `SmileTrainingRepositoryImplTest`, `SmileTrainingViewModelTest`) 및 기존 테스트 전체(165 `@Test` PASS) 통과 확인.
- [x] `./gradlew :app:spotlessApply :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 및 `bash scripts/check-agp10-compat.sh` 통과.
- [x] `CLAUDE.md`, `docs/ops/operations-snapshot.md`, `AI_HANDOFF.md`, `docs/plans/README.md` 인덱스 및 링크 검증 스크립트 통과.
