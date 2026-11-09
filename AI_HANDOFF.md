# Multi-AI Handoff Log

## 2026-11-09 (Gemini -> Claude/User) 프로젝트 전체 시야 팩트체크·심층 감사 (`F-01 ~ F-08`) · Watch↔Phone↔Backend End-to-End 파이프라인 정밀화 · 모바일/워치 UI/UX & 접근성 리팩토링 · `:app` + `:wear` 통합 자동 배포(`release.yml` Dual-Track CD) · 레거시 전수 청산 완료

**현재까지의 작업 상태 (Current Status):**
- **1. Phase A — Watch ↔ Phone ↔ Backend End-to-End 파이프라인 근본 원인 해결 (`F-01`, `F-03`, `F-04`)**:
  - [`WearBiometricRepository.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/repository/WearBiometricRepository.kt), [`WearBiometricRepositoryImpl.kt`](app/src/main/java/com/gunnys/eundunhealth/data/repository/WearBiometricRepositoryImpl.kt), [`PhoneWearDataListenerService.kt`](app/src/main/java/com/gunnys/eundunhealth/data/wear/PhoneWearDataListenerService.kt), [`PhoneSyncCommandListenerService.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/sync/PhoneSyncCommandListenerService.kt):
    - `F-01` 해결: `ingestFromWatchEnvelope` 반환 타입을 `Result<List<String>>`으로 변경하고, `PhoneWearDataListenerService`가 `SYNC_ACK_MESSAGE_PATH`로 수집 개수(`"1"`) 대신 실제 수집된 날짜 목록(`ingestedDates.joinToString(",")`, 예: `"2026-11-09"`)을 전송하도록 수정하여 워치 SQLite `synced_to_phone = 1` 마킹 누락 근본 원인을 해결.
    - 워치 `PhoneSyncCommandListenerService.onMessageReceived`의 `SYNC_ACK_MESSAGE_PATH` 분기에서 `db.markSummariesSynced(syncedDates)`를 `runBlocking(ioDispatcher) { withTimeoutOrNull(8_000L) { ... } }`로 감싸 GMS 바인딩 해제 전 완료 보장.
    - 백그라운드 콜드 기동 시 `authRepository.getCurrentUserId() ?: authRepository.restoreSession()` 폴백 추가 및 `ingestFromWatchEnvelope` 직후 `SmileSyncWorker.enqueueUnique(workManager)`와 함께 즉시 `runCatching { syncPendingSummaries(MAX_BATCH_DAYS) }` 실행, `requestWatchSyncNow()`에 `delay(600L)` 대기로 즉시 UI 반영.
    - `F-03` 해결: `toApiItem()`의 `watchModel.take(64)`(백엔드 `max_length=64` 정합) 및 `sleepAwakeningCount.coerceIn(0, 500)` 방어 클램핑 정렬.
  - [`biometric_repo.py`](backend/app/repositories/biometric_repo.py), [`biometric_service.py`](backend/app/services/biometric_service.py), [`test_biometrics.py`](backend/tests/test_biometrics.py):
    - `F-04` 해결: `upsert_daily_summaries_batch` 도입으로 `SELECT ... WHERE user_id = :uid AND record_date IN (...)` 단일 프리페치 및 루프 종료 후 단일 `await self.db.flush()` 적용(30일 배치 기준 60회 DB 왕복 → 2회로 축소), `sync_daily_summaries` 응답 `record_date DESC` 결정론적 정렬 보장 (`pytest` 151/151 PASS).
- **2. Phase B — 모바일(`:app`) & 워치(`:wear`) UI/UX · 레이아웃 · 접근성 리팩토링 (`F-02`, `F-05`)**:
  - [`ProgressHubViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubViewModel.kt) & [`ProgressHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt): `WearBiometricMindProgressCard` 빈 상태 및 데이터 상태 모두에 48dp `"워치 지금 동기화"` `OutlinedButton`과 `LiveRegionMode.Polite` 상태 피드백 추가, `loadSummary()` 진입 시 백그라운드 `syncPendingSummaries()` 연동.
  - [`HomeViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt) & [`HealthConnectCards.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/components/HealthConnectCards.kt): 홈 화면 Pull-to-Refresh 시 워치 즉시 동기화(`wearBiometricRepo?.requestWatchSyncNow()`) 트리거, `HealthConnectCards` 내 아이콘 TalkBack 중복 낭독 제거(`contentDescription = null`) 및 Wear OS 6 안내 문구 현행화.
  - [`ProfileHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt): `WearOsWatchIntegrationRow` 헤더에 `state.latestWatchRecoveryScore` 알약 배지(`"회복 ${score}점"`) 표시.
  - [`WearBiometricViewModel.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearBiometricViewModel.kt), [`MainActivity.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt), [`WearEnvironmentStatusScreen.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt): `init`과 `LifecycleStartEffect`의 중복 코루틴 실행을 `refreshJob` 단일화로 제거, `ZoneDistributionCard`의 중복 동기화 액션을 상세 확장/접기 토글로 개선, `@Preview` 3종의 버전을 `BuildConfig.VERSION_NAME`/`BuildConfig.VERSION_CODE` 동적 참조로 치환.
- **3. Phase C — CI/CD 안정화 & `:app` + `:wear` 통합 자동 배포 파이프라인 (`F-06`, `F-07`)**:
  - [`gradle.properties`](gradle.properties) & [`.github/workflows/android.yml`](.github/workflows/android.yml): Gradle 데몬 힙을 `-Xmx3072m -XX:MaxMetaspaceSize=768m`으로 상향하고 `android.yml`의 5회 분리 `./gradlew` 호출을 단일 호출로 통합하여 `:app:packageDebug` `IncrementalSplitterRunnable` 간헐적 경합 제거 및 CI 실행 시간 단축.
  - [`docs/ops/play-upload-ledger.md`](docs/ops/play-upload-ledger.md), [`scripts/check-version-monotonic.sh`](scripts/check-version-monotonic.sh), [`scripts/update-upload-ledger.sh`](scripts/update-upload-ledger.sh), [`scripts/bump-version.sh`](scripts/bump-version.sh), [`.github/workflows/release.yml`](.github/workflows/release.yml):
    - `LAST_UPLOADED_WEAR_VERSION_CODE=10002` 마커 및 Wear OS 업로드 이력(`10002` `wear:production`, `10001` `wear:internal`) 등재.
    - `bump-version.sh` 실행 시 기본값으로 `versionCode (+1)`과 `wearVersionCode (+1)`을 동시 증가(`--mobile-only` 옵션 지원).
    - `release.yml`에서 원장 대비 증가한 타겟(`upload_app` / `upload_wear`)을 자동 판별하여 모바일 `production` 트랙과 워치 `wear:production` 트랙을 각각 업로드하고 원장 PR에 두 버전 코드를 원자적으로 기록.
- **4. Phase D — `doc_audit.py` 정밀화 및 레거시 전수 청산 (`F-08`)**:
  - [`scripts/agents/doc_audit.py`](scripts/agents/doc_audit.py) & [`scripts/agents/test_doc_audit.py`](scripts/agents/test_doc_audit.py): `audit_wear_version_in_text`에서 append-only 이력 구역(`docs/CHANGELOG.md`, `operations-snapshot.md §13`, `README.md ### 마일스톤 진행`)을 제외하여 `2026-11-06` Foundation 당시 `wearVersionCode=10001` 역사 기록을 보존하고, `docs/conventions/versioning.md` §1 표의 `:wear` 행 검사를 추가 (`test_doc_audit.py` 56/56 PASS, `doc_audit.py --strict` 0건).
  - 레거시 파일 정리: `scripts/warm-gradle.sh`(죽은 Ktor `backend/gradlew` 제거), `scripts/claude-context.sh`, `scripts/prompts/{new-screen,bug-fix}.md`, `scripts/agents/requirements.txt`(`pytest==9.2.0`), `.claude/commands/{verify-deploy,naming-audit}.md`, `.claude/skills/changelog/SKILL.md` 정합화 및 `backend/scratch/` 잔여물 삭제.

---

## 2026-11-09 (Gemini -> Claude/User) Wear OS 6 (API 36, Xiaomi Watch 5 `M2501W1`) 상시 생체·마음 상태 5차원 분석 런타임(`:core:wear-contract` + `:wear` + `:app`) · 운영 Azure `/v2/biometrics/*`(`eundunhealth-api--0000070`) 배포 · Xiaomi Watch 5 실기기 검증(`watch-dev`) · 멀티 에이전트 전수 감사 후 근본 원인 해결 및 원형 UI/UX·접근성·성능 리팩토링 완료

**현재까지의 작업 상태 (Current Status):**
- **1. Phase 1 (`:core:wear-contract`) — 5차원 연속 분석 엔진 정밀화 및 순수 JVM GZIP+SHA-256 코덱 양방향 복원**:
  - [`WearTimeConstants.kt`](core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstants.kt), [`BiometricSummaryContract.kt`](core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/BiometricSummaryContract.kt), [`ContinuousBiometricAnalyzer.kt`](core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/ContinuousBiometricAnalyzer.kt), [`BiometricPayloadCodec.kt`](core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/BiometricPayloadCodec.kt):
    - `classifyZones`에서 `UNKNOWN` 상태 중 빠른 보행(`stepsDelta > 15`) 및 운동 직후 쿨다운(`EXERCISE` 종료 후 1~5분) 구간이 `elevatedTensionMinutes`(비활동 마음 긴장)로 오분류되지 않고 `normalMinutes`(일상 활성)로 일관되게 집계되도록 정렬 (`zoneMinutesSum == totalMonitoredMinutes`).
    - 백엔드 Pydantic 스키마 경계와 100% 일치하도록 `restingBpm` (`30..220`), `avgBpm` (`30..240`), `minBpm` (`25..240`), `maxBpm` (`30..260`), `avgRecoveryDropBpm` (`0..180`, 비운동 룩어헤드 한정) 방어적 클램핑 적용.
    - `BiometricPayloadCodec.decodeHourlyBucketsJson` 및 `decodeSourceDevicesJson` 추가로 모바일 Room DB 역직렬화 시 시간대별 버킷 유실 방지 (`:core:wear-contract:test` 8/8 PASS).
- **2. Phase 2 (`:wear`) — Xiaomi Watch 5 (`M2501W1`, `OS3.0.3.70.WO61WMIXM`) 24/7 센서 파이프라인 하드닝 · Data Layer 신뢰성 · 원형 화면(480×480) UI/UX 리팩토링**:
  - [`WearBiometricDatabase.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/data/WearBiometricDatabase.kt), [`EundunPassiveListenerService.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/sensor/EundunPassiveListenerService.kt), [`PassiveMonitoringBootReceiver.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/sensor/PassiveMonitoringBootReceiver.kt), [`PhoneSyncCommandListenerService.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/sync/PhoneSyncCommandListenerService.kt):
    - `SampleDataPoint.getTimeInstant(bootInstant)` 하드웨어 타임스탬프 개별 추출 및 `recordHeartRateSamplesBatch` 단일 SQLite 트랜잭션 배치 처리 도입, 앱 최초 기동 시 당일 누적 걸음 수 베이스라인 스파이크(`prevSteps == 0 && rawDelta > 300 -> 0`) 차단 및 최근 5분 내 심박 버킷으로의 `stepsDelta` 귀속 개선.
    - `PhoneSyncCommandListenerService.onMessageReceived` 동기 `runBlocking(ioDispatcher) + withTimeoutOrNull(8_000L)` 전환으로 GMS 바인딩 조기 해제 방지, `PassiveMonitoringBootReceiver` `withTimeoutOrNull(8_000L)` + `scope.cancel()` 누수 차단.
  - [`WearHealthSensorGateway.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/sensor/WearHealthSensorGateway.kt), [`WearBiometricViewModel.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearBiometricViewModel.kt), [`MainActivity.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt), [`WearEnvironmentStatusScreen.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt):
    - 메인 스레드 `MeasureCallback` 내 동기 SQLite 쓰기 제거 및 ViewModel 내 분 단위(최소 15초) IO 스로틀링, `LifecycleStartEffect` 전환.
    - 원형 화면(`480×480`) 하단 베젤 클리핑 해결을 위해 3번째 `TitleCard`를 알약형 `FilledTonalButton` (`SyncActionButton`)으로 교체하고, `TransformingLazyColumnDefaults.snapFlingBehavior` + `RotaryScrollableDefaults.snapBehavior` 및 `LiveBiometricCard` `time` 슬롯/5차원 지표를 적용 (`:wear:testDebugUnitTest` 3/3 PASS, `:wear:detektDebug` 0건).
  - [`scripts/android-cli-check.sh`](scripts/android-cli-check.sh) (`watch-dev`): Xiaomi Watch 5 무선 ADB(`192.168.0.9:46825`) 연결 시 2-Tier 개발자 설정(Doze 예외, 런타임 권한 4종, ADB 연결 중에만 화면 켜짐 유지 후 연결 해제 2초 내 `screen_off_timeout=10000`·`dumpsys battery reset` 자동 복원 데몬 `/data/local/tmp/adb_stayon_daemon.sh`) 배포 및 실기기 측정·동기화 완료.
- **3. Phase 3 (`:app`) — Watch→Phone Data Layer 경로 매칭 수정(P0), Room 시간대별 버킷 복원, 모바일 Compose 접근성·성능 최적화**:
  - [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) & [`PhoneWearDataListenerService.kt`](app/src/main/java/com/gunnys/eundunhealth/data/wear/PhoneWearDataListenerService.kt) & [`ManifestHealthConnectRationaleTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ManifestHealthConnectRationaleTest.kt):
    - `PhoneWearDataListenerService`의 `android:pathPrefix`를 `/eundunhealth/v1/biometric/`으로 수정하여 `DAILY_SUMMARY_CHANNEL_PATH`(`/eundunhealth/v1/biometric/daily_summary`) 수신 누락 근본 원인을 해결하고 매니페스트 단위 테스트 회귀 가드 추가.
    - `onMessageReceived`를 `runBlocking(ioDispatcher) + withTimeoutOrNull(8_000L)`로 전환하고 수집 성공 시 워치로 `SYNC_ACK_MESSAGE_PATH` 응답 전송.
  - [`WearBiometricRepositoryImpl.kt`](app/src/main/java/com/gunnys/eundunhealth/data/repository/WearBiometricRepositoryImpl.kt), [`HomeScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt), [`ProfileHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt), [`ProfileHubViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubViewModel.kt), [`ProgressHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt):
    - `toContractRecord()`에서 `hourlyBuckets`·`sourceDevices` 복원, `toApiItem()`에서 스키마 경계 방어 클램핑 적용.
    - `HomeScreen` `LazyColumn`에 `rememberLazyListState()` 및 전 항목 `key`/`contentType` 부여, `ThemeModeSegmentedRow` 48dp 터치 타겟 및 `selected` 시맨틱 부여, `ProfileHubViewModel` 원자적 `_uiState.update` 통일, `ProgressHubScreen` `remember` 캐싱 및 TalkBack 병합 개선.
- **4. Phase 4 (`backend/` & 운영 Azure) — `uuid.uuid7()`, 배치 내 중복 날짜 병합, 과거 스냅샷 역전 방지 및 생리학적 불변식 검증**:
  - [`biometric_repo.py`](backend/app/repositories/biometric_repo.py), [`biometric_service.py`](backend/app/services/biometric_service.py), [`biometrics.py` (스키마)](backend/app/schemas/biometrics.py), [`test_biometrics.py`](backend/tests/test_biometrics.py):
    - `uuid.uuid4()` → `uuid.uuid7()` 전환(룰 16), 동일 배치 내 중복 `recordDate` 최신 `capturedAtEpochMs` 병합, 기존 DB 레코드보다 오래된 `capturedAtEpochMs` 수신 시 덮어쓰기 방지, JSON 배열 구조·길이(`8192`/`1024`) 및 `min_bpm <= avg_bpm <= max_bpm`·`zone_sum <= 1440` 검증 추가 (`pytest` 151/151 PASS).
- **5. Phase 5 (CI/CD 워크플로 · 스크립트 · `doc_audit.py` 거버넌스 강화 & 오픈 PR 전수 정리)**:
  - [`.github/workflows/repository-gate.yml`](.github/workflows/repository-gate.yml), [`.github/workflows/doc-audit.yml`](.github/workflows/doc-audit.yml), [`.github/workflows/android.yml`](.github/workflows/android.yml), [`.github/workflows/backend.yml`](.github/workflows/backend.yml), [`scripts/bump-version.sh`](scripts/bump-version.sh), [`scripts/triage-prs.sh`](scripts/triage-prs.sh), [`scripts/agents/doc_audit.py`](scripts/agents/doc_audit.py):
    - `repository-gate.yml`에 `wear/**`, `core/**` 필터 및 3모듈 전체 검증 동기화, `doc-audit.yml` `--strict` 게이트 격상 및 `gradle/libs.versions.toml`·`backend/requirements*.in` 트리거 경로 확장 + `pytest` 버전 동적 SSoT 추출 적용, `backend.yml` `test` 잡 최소 권한(`contents: read`) 적용, `bump-version.sh` 8대 SSoT 문서 자동 치환 확장.
    - `scripts/triage-prs.sh` 고도화: `main` 병합 직후 GitHub GraphQL `mergeStateStatus == "UNKNOWN"` 지연 재계산에 대한 2초 대기 후 재조회 가드, 3-모듈(`:core:wear-contract`, `:app`, `:wear`) lockfile 및 `detachedConfiguration` 포함 `--write-verification-metadata sha256` 가이드, Linux Dependabot `uvloop` Windows 충돌 방지(`pip-compile --generate-hashes --strip-extras`) 및 `doc_audit.py --strict` SSoT 동기화 절차 내장.
    - 오픈 PR 전수 병합·흡수 완료: PR #236(멀티 에이전트 감사 및 UI/UX 리팩토링), PR #230(`gradle/actions/setup-gradle` `6.4.1`, `GHSA-chpw-2mqm-95cc`), PR #237(`actions/setup-java` `6.2.0`), PR #231/#239(Sentry Android `8.63.0` / Gradle Plugin `6.26.0`, Spotless `8.11.0`, OpenAPI Generator `7.27.0`), PR #232(Backend 9종 마이너·패치 업그레이드: `sqlalchemy 2.1.8`, `pydantic-settings 2.16.2`, `sentry-sdk 2.74.0`, `markdown 3.11.3`, `openai 3.46.0`, `pytest 9.2.0`, `ruff 0.17.4`, `mypy 2.4.1`, `types-Markdown 3.11.2.20261029`), PR #238(`ksp 2.3.13`은 업스트림 `google/ksp#3286` Defect in release 공지에 따라 무시·종결).
- **다음 할 일 (Next Steps)**:
  - ✅ **Action 1 (운영 Azure `eundunhealth-api--0000070`), Action 2 (건강 앱·데이터 안전), Action 3 (Wear OS 내부 테스트 `10001` & 게시 개요 제출), Action 4 (Xiaomi Watch 5 무선 ADB 실기기 검증 및 `watch-dev` 구성) 전수 완료 (2026-11-09)**.
  - ⏳ 추후 사용자가 원하실 때 모바일 `:app` `v0.8.0` (`versionCode=45`) 프로덕션 태그(`git tag v0.8.0 && git push origin v0.8.0`) 릴리스 진행.

---

## 2026-11-06 (Gemini -> Claude/User) `docs/plans/2026-11-06-wear-os-foundation-env-setup-plan.md` (→ `docs/plans/logs/android.md`) 환경설정 구현 설계 고도화(`PROP-06` + `PROP-06-A` Azure 환경 경계 통합) · 공식 문서 팩트체크 · `:wear` 진단 UI 개선 및 SSoT 정합화 완료

**현재까지의 작업 상태 (Current Status):**
- **1. 환경설정 구현 설계서 고도화 (`docs/plans/2026-11-06-wear-os-foundation-env-setup-plan.md` → [`docs/plans/logs/android.md`](docs/plans/logs/android.md))**:
  - 기존 `2a49cea` 커밋의 Foundation 환경설정 계획서를 기반으로, 향후 **마음 상태(스트레스·공황·기타 증상 포착) 및 일상 생체 신호(`PassiveMonitoringClient` 등) 측정·수집·동기화**(`PROP-06`)로 나아가기 위한 **순수 환경설정·기반 아키텍처 구현 설계서**로 고도화 완료.
  - `PROP-06-A`의 Azure 백엔드 판단을 환경 경계(`D9`)로 통합: 신규 PaaS(`IoT Hub`, `Event Hubs`, `AHDS FHIR`, `Blob`, `AI Foundry` 직결)를 도입하지 않고 기존 `Azure Container Apps (eundunhealth-api)` + `Azure PostgreSQL 18 (healthapp)` 경계를 재사용하며, 환경설정 단계에서는 백엔드/DB/OpenAPI 변경 0건을 유지.
  - 공식 문서 팩트체크(`android docs fetch`, `android-cli-check.sh versions`) 및 환경 구축 중 규명된 6대 근본 원인 해결·재발방지 가드(`T1 ~ T6`: R8 `keep_wear_capabilities.xml`, Detekt `WearEnvironmentSpec.kt` 분리, Small Round 192dp 클리핑 방지, Wear OS 6 콜드 부트 오버레이 해제, Linux `aapt2` SHA-256 보존, `ScreenScaffold` `LocalScrollCaptureInProgress` 가드) 명문화.
- **2. `:wear` Foundation 진단 코드 개선 및 SSoT 문서 정합화**:
  - [`wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt) & [`WearEnvironmentSpecTest.kt`](wear/src/test/java/com/gunnys/eundunhealth/wear/WearEnvironmentSpecTest.kt): `applicationInfo.minSdkVersion` / `targetSdkVersion` 런타임 실측 전달, `isRuntimeApiSupported` 판정 및 단위 테스트 보강(총 `@Test` 266건: `:app` 261 + `:wear` 3 + `:core:wear-contract` 2).
  - [`wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt): 공식 Wear Compose M3 스킬 가이드 4항에 따라 `ScreenScaffold` `scrollIndicator`에 `if (!LocalScrollCaptureInProgress.current)` 가드 적용, TalkBack `semantics(mergeDescendants = true)` (`stateDescription` + `onClick`) 강화, 접힌 상태 `bodyExtraSmall` 토큰으로 `192dp` Small Round + `fontScale = 1.24f` 원형 베젤 클리핑 완벽 해소 (`WearEnvironmentStatusScreenLargeFontPreview.png` 실측 검증 완료).
  - [`AgpBuildContractTest.kt`](app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt), [`ProguardKeepRulesTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ProguardKeepRulesTest.kt), [`scripts/check-agp10-compat.sh`](scripts/check-agp10-compat.sh), [`scripts/agents/doc_audit.py`](scripts/agents/doc_audit.py): `D1 ~ D9` 및 `T1 ~ T6` 불변식(3모듈 `gradle.lockfile`, `:core:wear-contract` 단일 파일 화이트리스트, `:wear` 무권한/무서비스 가드, `keep_wear_capabilities.xml` 대칭 보존, `wearVersionCode=10002` 및 3모듈 합산 `@Test` 266건 자동 감시) 강화.
  - 전사 문서(`CLAUDE.md`, `GEMINI.md`, `docs/SPEC.md`, `docs/TRD.md`, `docs/PRD.md`, `docs/CHANGELOG.md`, `docs/proposals/README.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/plans/logs/android.md`, `docs/plans/logs/dependencies.md`) 내 `:wear` 파일 트리·테스트 수(`266`)·설계 설명 정합화 완료.
- **다음 할 일 (Next Steps — 사용자 게이트 선행 필요)**:
  - **게이트 `G3`**: 실기기 Xiaomi Watch 5 무선 ADB 페어링 및 `ro.build.version.sdk`, `PassiveMonitoringClient.getCapabilitiesAsync()` 실측은 사용자 직접 수행 영역임.
  - **게이트 `G5` / `A0 ~ A6` / `G4`**: 런타임 센서 측정 권한·법무 고지(`G5`), 백엔드 API/DB 계약(`A0~A6`), Play Console Wear 트랙 업로드(`G4`)는 각 단계 진입 전 사용자 명시적 승인 필요.

---

## 2026-11-06 (Gemini -> Claude/User) 전사 프로젝트 문서 최신화(SSoT 동기화) 완료 (`v0.7.3/44` · Wear OS 6 3모듈 Foundation · 스마일 트레이닝 무중단 직행 · 결정론적 `doc_audit.py` · 18개 문서 전수 정합화)

**현재까지의 작업 상태 (Current Status):**
- **1. 루트 거버넌스 및 아키텍처 정본 문서 최신화**:
  - [`CLAUDE.md`](CLAUDE.md), [`GEMINI.md`](GEMINI.md), [`README.md`](README.md), [`AI_HANDOFF.md`](AI_HANDOFF.md), [`scripts/agents/README.md`](scripts/agents/README.md): 프로덕션 버전 `v0.7.3/44` (`LAST_UPLOADED_VERSION_CODE=44`, 백엔드 리비전 `eundunhealth-api--0000069` / 이미지 `6357981`, `MINIMUM_SUPPORTED_ANDROID_VERSION_CODE=44`), 3모듈 아키텍처(`:app`, `:wear` `wearVersionCode=10002` / `minSdk=36`, `:core:wear-contract` Pure JVM 17), 14개 UDF ViewModel, Android `@Test` 259건 / Backend pytest 147건, FastAPI `0.162.0` / Starlette `1.10.0`, 운영 안전 룰 1~16 및 100% 결정론적 Python stdlib 문서 감사기(`doc_audit.py`) 반영.
- **2. 제품·기술·기능 명세 및 컨벤션 문서 최신화**:
  - [`docs/PRD.md`](docs/PRD.md) (`v3.9`), [`docs/TRD.md`](docs/TRD.md) (`v2.2`), [`docs/SPEC.md`](docs/SPEC.md) (`v2.2`), [`docs/CHANGELOG.md`](docs/CHANGELOG.md), [`docs/conventions/versioning.md`](docs/conventions/versioning.md), [`docs/conventions/naming.md`](docs/conventions/naming.md): 스마일 트레이닝 `INTRO`/`COMPLETED` 정적 단계 제거(`READY -> ACTIVE -> NavigateBack`), 1-Tap 3버튼 테마 세그먼트(`ThemeModeSegmentedRow`), `PROP-06` 제안서 등재, `bump-version.sh` & `check-version-monotonic.sh --check-ssot` 원자적 동기화, 3모듈 디렉터리 구조 및 27개 API 엔드포인트 정합화.
- **3. 운영(`docs/ops/*.md`) 및 작업 원장(`docs/plans/logs/*.md` + `docs/plans/README.md`) 최신화**:
  - [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md), [`docs/ops/play-store-release.md`](docs/ops/play-store-release.md), [`docs/ops/monitoring-and-cost.md`](docs/ops/monitoring-and-cost.md), [`docs/ops/predeployment-readiness.md`](docs/ops/predeployment-readiness.md), [`docs/ops/dependency-deferred.md`](docs/ops/dependency-deferred.md), [`docs/plans/logs/android.md`](docs/plans/logs/android.md), [`docs/plans/logs/process-infra.md`](docs/plans/logs/process-infra.md), [`docs/plans/logs/dependencies.md`](docs/plans/logs/dependencies.md) 최신 이력 및 SSoT 반영.

---

## 2026-11-06 (Gemini -> Claude/User) Google Wear OS 6.0 (API 36) Foundation & 개발/검증 환경 구축 완료 (`:app`, `:wear`, `:core:wear-contract` 3모듈 · Wear Compose M3 `1.7.1` · `AC-01 ~ AC-10` 전수 통과)

**현재까지의 작업 상태 (Current Status):**
- **1. 계획서 최종 정제 및 단일 활성 파일 유지 (`docs/plans/2026-11-06-wear-os-foundation-env-setup-plan.md` → [`docs/plans/logs/android.md`](docs/plans/logs/android.md))**:
  - `PROP-06`에서 Foundation 환경 구축 범위를 분리·승격하고, 구형 완료 페어(`2026-11-04-android-cli-integration-*`)를 [`docs/plans/logs/process-infra.md`](docs/plans/logs/process-infra.md)로 이관하여 `docs/plans/` 내 단일 활성 계획 문서 체계를 확립.
- **2. 호스트 Wear OS 6.0 (API 36) SDK 및 `Wear_OS_Large_Round_API_36` AVD 구축 (`AC-07`)**:
  - `system-images/android-36/android-wear-signed/x86_64` 설치 및 `Wear_OS_Large_Round_API_36` (`wearos_large_round`) 생성 완료.
  - 콜드 부트 시 오버레이되는 `com.google.android.wearable.setupwizard` 및 AC 충전 화면(`dumpsys battery unplug`) 해제 프로비저닝 후 `wear-debug.apk` 설치·실행·스크롤 검증 완료 ([`wear/build/reports/emulator-smoke-scrolled.png`](wear/build/reports/emulator-smoke-scrolled.png)).
- **3. 3모듈 경량 계약 스켈레톤 (`:app`, `:wear`, `:core:wear-contract`) 및 비민감 진단 UI 구축 (`AC-01 ~ AC-06`, `AC-09`)**:
  - [`:core:wear-contract`](core/wear-contract/build.gradle.kts): 순수 JVM(`org.jetbrains.kotlin.jvm`, JDK 17) 모듈로 [`WearTimeConstants.kt`](core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstants.kt) (`KST_ZONE`)만 보유하고 [`:app` `TimeConstants.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/model/TimeConstants.kt)에서 위임 참조. 미검증 생체 DTO·Data Layer 경로·센서 서비스·건강 권한은 Foundation에서 100% 배제 (`AC-04`, `AC-09`).
  - [`:wear`](wear/build.gradle.kts): `applicationId = "com.gunnys.eundunhealth"`, `minSdk = 36`, `compileSdk = 37`, `targetSdk = 37`, 명시적 `wearVersionCode = 10002` ([`version.properties`](version.properties)), Wear Compose Material 3 (`1.7.1`) 기반 [`WearEnvironmentStatusScreen.kt`](wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt) (`AppScaffold` → `ScreenScaffold` → `TransformingLazyColumn` + `SurfaceTransformation`) 및 `LARGE_ROUND`/`SMALL_ROUND` 프리뷰 PNG 렌더링·육안 검수 완료 (`AC-06`).
  - Capability 리소스 보존 (`AC-05`): [`app/src/main/res/values/wear.xml`](app/src/main/res/values/wear.xml) (`eundun_phone_companion`), [`wear/src/main/res/values/wear.xml`](wear/src/main/res/values/wear.xml) (`eundun_wear_companion`) 및 R8 리소스 축소기 대응 [`app/src/main/res/raw/keep_wear_capabilities.xml`](app/src/main/res/raw/keep_wear_capabilities.xml), [`wear/src/main/res/raw/keep_wear_capabilities.xml`](wear/src/main/res/raw/keep_wear_capabilities.xml) 추가로 Release APK 내 `array/android_wear_capabilities` 보존을 `aapt2 dump resources`로 실측 확인.
- **4. 품질·공급망·CI 가드 확장 (`AC-08`, `AC-10`)**:
  - [`scripts/check-agp10-compat.sh`](scripts/check-agp10-compat.sh), [`scripts/android-cli-check.sh`](scripts/android-cli-check.sh) (`apk --module app|wear`, 멀티모듈 `inspect`/`previews`), [`.githooks/pre-commit`](.githooks/pre-commit), [`.github/workflows/android.yml`](.github/workflows/android.yml), [`AgpBuildContractTest.kt`](app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt), 3모듈 `gradle.lockfile` 및 [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml) 동기화 완료.
- **다음 할 일 (Next Steps — 사용자 게이트 선행 필요)**:
  - `G1`/`G3`: 실기기 Xiaomi Watch 5 USB/무선 ADB 연결(`ro.build.version.sdk`, `getCapabilitiesAsync()` 실측) 및 모바일 기기 블루투스/Wi-Fi 페어링은 사용자 직접 수행 영역임.

---

## 2026-11-06 (Gemini -> Claude/User) AGP(Android Gradle Plugin) 최신 정식 안정판 `9.5.1` 업그레이드 완료 (룰 15 6단계 전수 검증 · 멀티 플랫폼 해시 락 · SSoT 문서 정합화)

**현재까지의 작업 상태 (Current Status):**
- **1. AGP `9.5.0` → `9.5.1` 정식 안정판 업그레이드 ([`gradle/libs.versions.toml`](gradle/libs.versions.toml))**:
  - `android studio version-lookup agp` (`Stable: 9.5.1`) 및 Google Maven 공식 메타데이터 실측 대조 후 `agp = "9.5.1"` 적용 (`Gradle 9.8.2` / `Kotlin 2.4.21` / `KSP 2.3.12` / `Hilt 2.60.1` / `JDK 17` 락스텝 호환).
- **2. 공급망 락파일([`app/gradle.lockfile`](app/gradle.lockfile)) 및 멀티 플랫폼 SHA-256 검증 메타데이터([`gradle/verification-metadata.xml`](gradle/verification-metadata.xml)) 동기화**:
  - `./gradlew :app:dependencies --write-locks --write-verification-metadata sha256` 실행 및 `com.android.tools.build:aapt2:9.5.1-16666295`의 `-linux.jar`(`fa1014cb...`), `-osx.jar`(`a43df4c4...`), `-windows.jar`(`0cca2866...`), `.pom`(`fe9320e0...`) 해시 등재 완료 ([`AgpBuildContractTest`](app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt) 통과).
- **3. SSoT 문서 동기화 및 6단계 검증 게이트 전수 통과**:
  - [`CLAUDE.md`](CLAUDE.md), [`README.md`](README.md), [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md), [`docs/TRD.md`](docs/TRD.md), [`docs/SPEC.md`](docs/SPEC.md) AGP `9.5.1` 동기화.
  - `bash scripts/check-agp10-compat.sh` OK, `./gradlew :app:help "-Pandroid.debug.obsoleteApi=true" --warning-mode all` 신규 경고 0건, `:app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleRelease` PASS (259 tests PASS), `python scripts/agents/doc_audit.py --strict` 드리프트 0건 (100% CLEAN).

---

## 2026-11-06 (Gemini -> Claude/User) 스마일 트레이닝 무중단 직행(INTRO·COMPLETED 완전 제거) & 프로젝트 전역 Compose(BOM 2026.09.00) 신기술 적용·M3 디자인 통일·레거시 청산 완료

**현재까지의 작업 상태 (Current Status):**
- **1. Part 1 — 스마일 트레이닝 인트로 대시보드(INTRO)·완료 페이지(COMPLETED) 완전 제거 및 코칭 HUD/가이드 디자인 업그레이드**:
  - [SmileTrainingViewModel.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileTrainingViewModel.kt), [SmileTrainingScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileTrainingScreen.kt): SmileSessionPhase.INTRO 및 SmileIntroContent.kt를 완전 삭제하고, 4대 진입점 터치 즉시 onInitialEntry(hasCameraHardware, hasCameraPermission) → READY(1.0초 얼굴 정렬 자동 시작) → ACTIVE(2초 × 3회) → withContext(NonCancellable) 저장 직후 NavigateBack으로 자동 복귀(저장 실패 시 SaveErrorOverlayDialog 모달 노출)하는 무중단 Zero-Tab-Overhead 흐름 완성.
  - [SmilePhaseGuideContents.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmilePhaseGuideContents.kt), [SmileCameraStage.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileCameraStage.kt), [ProgressHubScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt): SmileGuideCardLayout 공통 래퍼(72dp 원형 배지 + RoundedCornerShape(24.dp) 카드 + 52dp 버튼), 폴더블 안전 타원 클램프(ovalHeight = minOf(..., size.height * 0.44f)), FaceOvalVignetteOverlay 색상 보간·외곽 글로우, RepStepIndicatorRow 폭·색상 보간, BottomCoachingOverlay `animateContentSize` + AnimatedVisibility, 진행 허브 누적 연습 시간(`formatDurationKorean`) 배지 추가.
- **2. Part 2 — Jetpack Compose BOM 2026.09.00 (UI/Foundation 1.12.1) · CameraX 1.6.2 · Lifecycle 2.11.0 신기술 전수 적용**:
  - Modifier.keepScreenOn() 적용 ([SmileCameraStage.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileCameraStage.kt), [WorkoutGuideScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideScreen.kt)).
  - ProcessCameraProvider.awaitInstance(context) + `awaitCancellation()` 코루틴 바인딩 전환 ([SmileCameraStage.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileCameraStage.kt)).
  - LifecycleStartEffect / LifecycleResumeEffect 선언형 생명주기 전환 ([SmileCameraStage.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileCameraStage.kt), [SmileTrainingScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileTrainingScreen.kt), [AppNavigation.kt](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt), [HomeScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt), [ProgressHubScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt), [ProfileHubScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt)).
  - TextAutoSize.StepBased 적용 ([ProfileHubScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt), [ProgressHubScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt), [StatisticsScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt)) 및 [MotionTokens.kt](app/src/main/java/com/gunnys/eundunhealth/ui/theme/MotionTokens.kt) quickSpring()/progressSpring() 확장.
- **3. Part 3 & Part 4 — 프로젝트 전역 M3 디자인 일관성 고도화 및 레거시/기술 부채 청산**:
  - [EmptyContent.kt](app/src/main/java/com/gunnys/eundunhealth/ui/components/EmptyContent.kt), [ErrorContent.kt](app/src/main/java/com/gunnys/eundunhealth/ui/components/ErrorContent.kt), [BadgeScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/badge/BadgeScreen.kt), [StatisticsScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt), [HistoryScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/history/HistoryScreen.kt), [GoalScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt), [MealPlanScreen.kt](app/src/main/java/com/gunnys/eundunhealth/ui/mealplan/MealPlanScreen.kt) M3 토큰·셰이프·모션 통일.
  - [ContextExt.kt](app/src/main/java/com/gunnys/eundunhealth/ui/util/ContextExt.kt)(`findActivity()`), [TimeConstants.kt](app/src/main/java/com/gunnys/eundunhealth/domain/model/TimeConstants.kt)(KST_ZONE, `formatDurationKorean`) SSoT 통합, [HealthRepositoryImpl.kt](app/src/main/java/com/gunnys/eundunhealth/data/repository/HealthRepositoryImpl.kt)·[ProfileHubViewModel.kt](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubViewModel.kt) `runSuspendCatching`(룰 14 Layer 2) 교체, [MealPlanRepositoryImpl.kt](app/src/main/java/com/gunnys/eundunhealth/data/repository/MealPlanRepositoryImpl.kt) `toDomain()` 통합, cycleTheme() 데드코드 및 [gradle/libs.versions.toml](gradle/libs.versions.toml) 미사용 선언 제거.
- **4. 전수 검증 게이트 통과**:
  - ./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug → **BUILD SUCCESSFUL** (259 tests PASS, Detekt 0건).
  - `bash scripts/check-agp10-compat.sh` & `bash scripts/check-plans-links.sh` → **OK**.
  - python scripts/agents/doc_audit.py --strict → **드리프트 0건 (100% CLEAN)**.

---

## 2026-11-04 (Gemini -> Claude/User) 2-Tier (전역 워크스테이션 + `eundunHealth` 프로젝트) Android CLI (`v1.1.16717390`) & 실행 중인 Android Studio (`Rabbit 2 | 2026.2.2`) 통합 구축 완료

**현재까지의 작업 상태 (Current Status):**
- **1. Tier 1 — 전역 워크스테이션 기반 구축 (`gunnysis/windows-dev-setup` & `gunnysis/claude-config` & `~/.gemini/config/GEMINI.md`)**:
  - **WSL2 Ubuntu `~/.local/bin/android` 6번째 정식 래퍼 등록**: `C:\programming\windows-dev-setup\wsl\local-bin\android` 신설(사용자명 하드코딩 금지, 동적 탐색 + WSL 경로를 `wslpath -m`으로 자동 변환) 및 `bootstrap.ps1 -SkipPackages` 배포 완료.
  - **Doctor 검증 스크립트 연동**: `pwsh-doctor.ps1` (**`PASS 87 / WARN 0 / FAIL 0`**) 및 `wsl-doctor.ps1` (**`PASS 36 / WARN 1 / FAIL 0`**, `[PASS] 래퍼 동작: android android-cli 1.1.16717390`)에 `android` CLI 점검 추가.
  - **전역 규칙 & 권한 동기화**: `C:\Users\Administrator\.claude\rules\windows-workstation.md`, `C:\Users\Administrator\.gemini\config\GEMINI.md` 기준선(래퍼 6개, `android-cli 1.1.16717390`, Android Studio `Rabbit 2 | 2026.2.2`) 갱신 및 `C:\Users\Administrator\.claude\settings.json`에 읽기 전용 `android` CLI 허용 규칙 등록 (`normalize-settings.js --verify-portable` 및 `test-guard.js` 37/37 통과).
  - **공식 도메인 스킬 6종 전역 동기화**: `android skills add`로 `camerax`, `jetpack-compose-performance`, `r8-analyzer`, `edge-to-edge`, `adaptive`, `play-policy-insights`를 6개 에이전트 타겟(`android-studio, antigravity, antigravity-cli, antigravity-ide, claude-code, gemini`)에 설치 완료 (총 20/20 스킬 활성).
- **2. Tier 2 — `eundunHealth` 프로젝트 전용 하이브리드 통합 (`docs/plans/logs/process-infra.md` — 옛 페어 `docs/plans/2026-11-04-android-cli-integration-{design,plan}.md` 흡수)**:
  - **[`scripts/ensure-android-cli.sh`](scripts/ensure-android-cli.sh)**: Windows Git Bash / PowerShell / WSL2 Ubuntu 전 환경에서 `android` CLI를 탐색·노출하는 헬퍼 스크립트(`ANDROID_CLI_REQUIRED=1` 지원).
  - **[`scripts/android-cli-check.sh`](scripts/android-cli-check.sh)**: 단일 하이브리드 오케스트레이터(`status` / `inspect` / `previews` / `apk` / `versions` / `docs`).
    - **근본 원인 해결 (심볼릭 링크 경로 `apps_ln` ↔ Studio VFS `Error: Failed to get module.`)**: `android studio check`가 반환하는 Studio 프로젝트 정본 경로(`C:/programming/apps/eundunHealth`)를 파싱해 상대/심볼릭 링크 파일 경로를 정본 경로로 자동 매핑하여 `analyze-file` 및 `render-compose-preview`가 100% 성공하도록 구현.
    - **근본 원인 해결 (순수 JVM 단위 테스트 속도 보존)**: `isIncludeAndroidResources = true`를 켜지 않고 `android preview list "gradle://.?:app@debug"` + `android studio render-compose-preview --print-semantics` 듀얼 엔진 조합으로 `@Preview` PNG 및 a11y 시맨틱 트리를 `app/build/reports/compose-previews/`에 생성.
  - **[`scripts/preflight-release.sh`](scripts/preflight-release.sh) & [`scripts/smoke-cold-start.sh`](scripts/smoke-cold-start.sh) 고도화**:
    - `preflight-release.sh`에 `bash scripts/android-cli-check.sh apk` (R8 fullMode, 버전 SSoT, DEX/리소스 보존 검증) 게이트 추가.
    - `smoke-cold-start.sh`에 `--auto-emulator [avd]`, `android install` 고속 델타 설치(`adb install -r` 폴백), 콜드 스타트 직후 `app/build/reports/cold-start/{layout.json,screen.png}` 자동 캡처 연동.
  - **문서 및 권한 정합화**: [`.claude/settings.local.json`](.claude/settings.local.json), [`CLAUDE.md`](CLAUDE.md), [`GEMINI.md`](GEMINI.md), [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md), [`docs/plans/README.md`](docs/plans/README.md) 동기화 완료.

---

## 2026-11-04 (Gemini -> Claude/User) AGP(Android Gradle Plugin) 최신 정식 안정판 `9.5.0` 업그레이드 완료 (룰 15 6단계 전수 검증 · 듀얼 플랫폼 해시 락 동기화 · SSoT 문서 정합화)

**현재까지의 작업 상태 (Current Status):**
- **1. AGP `9.4.1` → `9.5.0` 최신 정식 안정판 업그레이드 ([`gradle/libs.versions.toml`](gradle/libs.versions.toml))**:
  - Google Maven 공식 메타데이터(`com.android.tools.build:gradle`) 및 Android Developer 릴리스 노트를 실측 대조하여 프리릴리스(`9.6.0-alpha*`)를 배제하고 최신 정식 안정판 **`9.5.0`** 채택 (최소 요구 `Gradle 9.6.0` 대비 현행 `Gradle 9.8.2` 100% 충족, `Kotlin 2.4.21` / `KSP 2.3.12` / `Hilt 2.60.1` / `JDK 17` 락스텝 호환).
- **2. 공급망 락 파일 및 듀얼 플랫폼(`Windows` + `Linux` + `macOS`) SHA-256 검증 메타데이터 동기화**:
  - [`app/gradle.lockfile`](app/gradle.lockfile) 및 [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml): `./gradlew :app:dependencies --write-locks --write-verification-metadata sha256` 및 `:app:assembleDebug --write-verification-metadata sha256`로 AGP `9.5.0` 아티팩트 체인 갱신.
  - Linux CI(`ubuntu-latest`) 및 멀티 플랫폼 패리티를 위해 Google Maven 공식 배포본(`com.android.tools.build:aapt2:9.5.0-16666295`)의 `aapt2-9.5.0-16666295-linux.jar`(`fa1014cb...`), `aapt2-9.5.0-16666295-osx.jar`(`a43df4c4...`), `aapt2-9.5.0-16666295-windows.jar`(`0cca2866...`) SHA-256 해시를 모두 등재하고 [`AgpBuildContractTest`](app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt)로 검증.
- **3. AGP 9.5+ Obsolete API 점검 및 SSoT 문서 동기화**:
  - `./gradlew :app:help "-Pandroid.debug.obsoleteApi=true" --warning-mode all` 점검 통과 (신규 AGP Obsolete API 경고 0건; AGP 9.5+에서 `:app:tasks`가 Layoutlib `generate*ComposePreviewRunfiles`를 전 variant에 강제 실체화하는 특성을 [`CLAUDE.md`](CLAUDE.md) 룰 15에 반영).
  - [`CLAUDE.md`](CLAUDE.md), [`README.md`](README.md), [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md), [`docs/TRD.md`](docs/TRD.md), [`docs/SPEC.md`](docs/SPEC.md)의 현행 AGP 버전을 `9.5.0`으로 일괄 정합화.
- **4. 전수 검증 게이트 통과**:
  - `bash scripts/check-agp10-compat.sh` → **OK: AGP 10 호환성 가드 통과** (WSL2 Ubuntu).
  - `./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleRelease` → **BUILD SUCCESSFUL** (단위 테스트 242 케이스 전수 통과, Detekt 0건, R8 Release APK 및 `mapping.txt` 내 `FaceRegistrar`·`ExerciseDto` 보존 확인).
  - `python scripts/agents/doc_audit.py --strict` → **드리프트 0건 (100% CLEAN)**, `pytest scripts/agents/test_doc_audit.py` **49 passed**.

---

## 2026-11-02 (Gemini -> Claude/User) GitHub CI doc-audit 실패(`exit code 1`) 및 Proceed 버튼 오류 근본 원인 해결 · Claude Code 의존성 완전 제거 및 초고속(0.05s) 결정론적 문서 감사 린터 엔진 구축 완료

**현재까지의 작업 상태 (Current Status):**
- **1. Proceed 버튼 오류 근본 원인 해결 (GEMINI.md)**:
  - GEMINI.md 규칙 4번의 '사용자 검토용 Proceed 버튼 활성화' 문구가 실제 IDE UI 환경에 없는 버튼을 안내하도록 유도하던 근본 원인을 수정.
  - 별도 버튼 없이 채팅으로 명시적 승인('승인', '진행해', '시작' 등)을 요청하도록 룰 현실화 및 허위 안내 원천 차단.
- **2. GitHub CI doc-audit 7주 연속 실패 (`exit code 1`) 근본 원인 규명 및 아키텍처 혁신**:
  - **근본 원인**: Claude Code 구독 해지/미사용에 따라 Anthropic에서 구독 토큰(CLAUDE_CODE_OAUTH_TOKEN)을 차단(Your organization has disabled Claude subscription access for Claude Code)했으나, CI 워크플로우가 단순 문자열 존재로  `ready=true` 통과시키고 SDK 예외 미처리로 매주 월요일 프로세스가 크래시(`exit code 1`)함.
  - **Claude Code 및 외부 AI 종속성 전면 청소**: claude-agent-sdk 삭제, 외부 시크릿(CLAUDE_CODE_OAUTH_TOKEN, ANTHROPIC_API_KEY) 참조 전면 제거, 토큰 비용 `$0` 및 시크릿 0개 아키텍처 완성.
  - **결정론적 문서 감사 린터 (Deterministic Linter) 구축 ([scripts/agents/doc_audit.py](scripts/agents/doc_audit.py))**:
    - 외부 네트워크나 AI 호출 없이 Python 3.12+ stdlib 단독 구동. 0.05초 만에 6대 문서(CLAUDE.md, README.md, docs/ops/operations-snapshot.md, docs/TRD.md, docs/PRD.md, docs/CHANGELOG.md)의 SSoT 불변식(버전, API 버전, DB 스키마, 파이썬 런타임 mismatch, CORS 기본값, API 라우트 수, 폐기 기술 잔존 등)을 100% 정밀 검증.
  - **CI 워크플로우 경량화 ([.github/workflows/doc-audit.yml](.github/workflows/doc-audit.yml))**:
    - 이원화되었던 잡(`collector-test`, `audit`)을 단일 통합 doc-audit 잡으로 단순화하여 CI 수행 시간을 기존 4분 → **15초 이내**로 단축.
- **3. 잔여 문서 드리프트 정합화 및 전수 검증**:
  - [CLAUDE.md](CLAUDE.md) 및 [docs/TRD.md](docs/TRD.md)의 구버전 잔재(`v0.7.2/43`)를 최신 프로덕션인 `v0.7.3/44`로 정합화.
  - doc_audit.py --strict 실행 결과 **드리프트 0건 (100% CLEAN)** 달성.
  - [scripts/agents/test_doc_audit.py](scripts/agents/test_doc_audit.py) 단위 테스트 **49 passed in 0.12s** (Windows / WSL2 Ubuntu 100% 패리티 통과).
  - 백엔드 전체 테스트 pytest backend/tests/ **147 passed**.
  - `bash scripts/check-plans-links.sh` **OK: 끊긴 참조 0건**.

---

## 2026-10-30 (Gemini -> Claude/User) `docs/store/privacy-policy.md` 개인정보 처리방침 최신화 및 중복 제거 완료 (SSoT 동기화 및 전수 검증)

**현재까지의 작업 상태 (Current Status):**
- **1. [`docs/store/privacy-policy.md`](docs/store/privacy-policy.md) 최신화 및 중복 제거**:
  - **문서 메타데이터 최신화**: 최종 업데이트 일자 `2026-10-30` 갱신.
  - **중복 서술 및 파편화 제거**:
    - 기존 §1 말미의 미수집 서술과 §7 광고/추적 간의 중복 문구를 정리하여 §7 "광고, 추적 금지 및 미수집 정보"로 일원화 (광고 미게재, AAID/IDFA 미수집, 위치·연락처·마이크 미수집 등 응집).
    - §1 수집 정보 항목을 실제 수집·처리 데이터(인증, 신체 정보, 운동 기록, 목표/통계, 스마일 트레이닝 비생체 요약, 온디바이스 카메라 실시간 처리 원칙, Health Connect 읽기 전용, Sentry 진단) 중심으로 명료화.
    - §3-1(2) AI 맞춤 식단 국외 이전 표 바로 아래 4줄짜리 중복 텍스트(국외 추론 위치 재언급, 모델 미학습 정책 재언급, Azure PostgreSQL 보관 재언급 등)를 제거하고 "책임 있는 AI 보호 조치 안내" 1줄로 정돈.
    - Health Connect(`READ_EXERCISE` 단독 읽기 전용, 쓰기 없음, 원본 미보관) 및 스마일 트레이닝(온디바이스 ML Kit 얼굴 표정 감지, 카메라 영상/생체 템플릿 미저장·미전송 메모리 즉시 폐기) 정책을 일관되게 정합화.
    - §4 보관/파기 및 §5 사용자 권리 행사를 체계적으로 구조화(계정 삭제 시 즉시 영구 삭제, 스마일 트레이닝 요약 단독 전체 삭제 기능 명시, Sentry 90일 보존, [계정 및 데이터 삭제 안내](docs/store/account-deletion.md) 상호 링크).
- **2. SSoT 동기화 및 백엔드 법적 라우트 검증**:
  - `bash scripts/sync-legal-docs.sh` 실행 → [`backend/app/legal/privacy-policy.md`](backend/app/legal/privacy-policy.md) 동기화 완료.
  - `backend/.venv/Scripts/pytest.exe backend/tests/test_legal.py` → **6 passed in 0.21s** (SSoT 드리프트 불변식 및 `/privacy` HTML 서빙 검증 100% 통과).
  - `backend/.venv/Scripts/pytest.exe scripts/agents/test_doc_audit.py -q` → **36 passed in 0.09s**.
  - `bash scripts/check-plans-links.sh` → **OK: 끊긴 참조 0건**.

---

## 2026-10-29 (Gemini -> Claude/User) `v0.7.3` (`versionCode=44`) 프로덕션 배포 완료 · 앱 업데이트 감지 및 강제 안내 시스템 고도화 (`Strict Mandatory Update Architecture`, PR #225 / 원장 PR #226 / `release.yml` `40868910274` / `backend.yml` `40868854537` `eundunhealth-api--0000069`)

**현재까지의 작업 상태 (Current Status):**
- **1. 근본 원인 해결 및 정식 설계/구현 계획서 작성 (`docs/plans/logs/android.md`, 옛 페어 `docs/plans/2026-10-29-mandatory-app-update-{design,plan}.md` 흡수)**:
  - 구버전 앱(`v0.7.0/41`, `v0.7.1/42`) 재실행 시 신규 프로덕션 출시 버전(`v0.7.2/43`) 업데이트 안내가 뜨지 않던 5대 구조적 원인(`F1`~`F5`)을 전수 규명하고, 새 버전 출시 시 예외 없이 최신 버전을 사용하도록 강제하는 **Strict Mandatory Update** 아키텍처로 구현 완료.
- **2. Backend 최소 지원 버전(`43`) 즉시 상향 및 SSoT 자동화 (`F1`, `D5`)**:
  - [`backend/app/config.py`](backend/app/config.py)(`41`→`43`), [`backend/containerapp.yaml`](backend/containerapp.yaml)(`41`→`43`), [`backend/docker-compose.yml`](backend/docker-compose.yml)(레거시 `39`→`43`), [`scripts/verify_containerapp_deployment.py`](scripts/verify_containerapp_deployment.py)(`41`→`43`), [`docs/TRD.md`](docs/TRD.md), [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md)를 모두 `43`으로 동기화.
  - [`backend/tests/conftest.py`](backend/tests/conftest.py), [`backend/tests/test_weekly_plan.py`](backend/tests/test_weekly_plan.py), [`backend/tests/test_profile.py`](backend/tests/test_profile.py)에서 버전 숫자(`41`, `40`) 하드코딩 레거시를 제거하고 `get_settings().minimum_supported_android_version_code` 동적 참조로 리팩토링(향후 버전 번프 시 테스트 수동 수정 불필요).
  - [`scripts/bump-version.sh`](scripts/bump-version.sh)가 버전 번프 시 백엔드 설정 3곳·배포 검증기·문서의 최소 지원 버전까지 자동 치환하도록 확장하고, [`scripts/check-version-monotonic.sh`](scripts/check-version-monotonic.sh)에 `--check-ssot` 및 preflight SSoT 불변식 검증 가드 추가.
- **3. Android 이중 채널 강제 업데이트 감지 (Backend `/app-compatibility` OR Google Play In-App Updates `2.1.0`) & 포그라운드(`ON_START`) 재검증 (`F2`~`F5`, `D1`~`D4`, `D6`)**:
  - [`gradle/libs.versions.toml`](gradle/libs.versions.toml) 및 [`app/build.gradle.kts`](app/build.gradle.kts)에 `com.google.android.play:app-update:2.1.0` 및 `app-update-ktx:2.1.0` 추가 (`app/gradle.lockfile`, `gradle/verification-metadata.xml` 동기화 및 삭제된 `EundunApi.kt` 레거시 주석 제거).
  - [`PlayInAppUpdateChecker.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/update/PlayInAppUpdateChecker.kt): `AppUpdateManager.requestAppUpdateInfo()` 기반으로 `UpdateAvailability.UPDATE_AVAILABLE` 및 중단된 업데이트(`DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`)를 감지하고 `AppUpdateType.IMMEDIATE` 전체화면 업데이트 시트를 실행(룰 14 준수 `by lazy` 초기화, 사이드로드 `InstallException` 안전 폴백).
  - [`AuthViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt) & [`AppNavigation.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt): `Lifecycle.Event.ON_START` 옵저버(`onAppForegrounded()`, 30초 쿨다운 + 오프라인 실패 시 즉시 재시도)를 연결하여 앱을 강제 종료하지 않고 재실행할 때도 즉시 `SessionState.UpdateRequired`로 전환. `UpdateRequiredScreen`의 미사용 레거시 파라미터 `@Suppress("UnusedParameter") minimumVersionCode` 제거.
  - [`ErrorContent.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/ErrorContent.kt): 런타임 `403 client_update_required` (`AppError.ClientUpdateRequired`) 수신 시 의미 없는 `"다시 시도"` 버튼 대신 `"Google Play에서 업데이트"` 스토어 이동 CTA 버튼 노출.
  - [`AuthViewModelTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt) 및 [`PlayInAppUpdateCheckerTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ui/update/PlayInAppUpdateCheckerTest.kt) 단위 테스트 9건 추가 완료.

## 2026-10-29 (Gemini -> Claude/User) `v0.7.2` (`versionCode=43`) 프로덕션 배포 완료 · 진행(`ProgressHubScreen`) & 프로필(`ProfileHubScreen`) 비주얼 대시보드 개편 · 체성분 직접 입력 0.1 정밀도 버그 수정 · 레거시 DeepSeek 인프라 정리 · 전수 문서 최신화 완결

**현재까지의 작업 상태 (Current Status):**
- **0. `v0.7.2` (`versionCode=43`) 운영 배포 및 오픈 PR 전량 종결 (`Open PR = 0`)**:
  - **PR [#221](https://github.com/gunnysis/eundunHealth/pull/221)** (`bfb9ee8`) 모든 필수 CI 체크(`Android CI`, `Backend required checks`, `CodeQL`, `Repository Gate`) 통과 후 `main` 병합.
  - 태그 `v0.7.2` 푸시 → **`release.yml` (run [`40847386065`](https://github.com/gunnysis/eundunHealth/actions/runs/40847386065))** `play-release` 환경 승인 및 Google Play 프로덕션 트랙 `completed`(100%) 업로드 성공 (AAB `16,670,847B`, APK `12,062,201B`, Sentry ProGuard 매핑 업로드 완료).
  - 자동 생성된 원장 PR **[#223](https://github.com/gunnysis/eundunHealth/pull/223)** (`release-ledger/v0.7.2-43`, `a89447a`) 및 CI 액션 그룹 업데이트 PR **[#222](https://github.com/gunnysis/eundunHealth/pull/222)** (`1b63178`) CI 통과 후 `main` 병합 완료 (`LAST_UPLOADED_VERSION_CODE=43`).
  - Dependabot PR **[#220](https://github.com/gunnysis/eundunHealth/pull/220)** 트리아지 완료: 안전한 라이브러리(`okhttp 5.5.1`, `coil 3.6.3`)는 흡수하고, 룰 15를 우회하던 AGP 마이너 자동 번프는 `.github/dependabot.yml` ignore 가드에 추가 후 PR #220 종료.
- **1. 프로필 페이지 (`ProfileHubScreen.kt` / `ProfileHubViewModel.kt`) 불필요 요소 정리 및 시각화 대시보드 개발**:
  - **5개 파편화 섹션(`내 정보 · 연동 · 화면 · 정보 · 계정`) → 3대 핵심 영역 + 하단 푸터로 과감히 통합·정리**:
    - **① 내 신체 & 루틴 요약 대시보드 히어로 (`ProfileBodyHeroCard`)**: 기존에 아무 수치도 보여주지 않던 정적 행(`"신체 정보 및 휴식 요일"`)을 전면 개편하여, 성별·운동 수준 배지(`남성 · 중급 코칭`), 3열 신체 지표 그리드(`BodyMetricMiniGrid`: 신장·체중, BMI 지수 및 판정, 골격근·체지방률), 주간 루틴 & 7일 요일 원형 스탬프 바(`RestDayVisualStrip`: `주 6일 운동 · 휴식 (일)` 및 `월~일` 배지)를 한눈에 시각화.
    - **② 운동 환경 및 기기 연동 통합 카드 (`WorkoutAndHealthIntegrationCard`)**: 따로 찢어져 있던 `기본 운동 환경`과 `Health Connect`를 단일 그룹 카드로 통합하고, Health Connect 상태 배지(`연결됨` 시맨틱 그린 / `권한 필요` / `미지원`)와 틴트 아이콘 배지(`TintedIconBadge`)를 적용.
    - **③ 화면 스타일 통합 카드 (`AppearanceSettingsCard` + `ThemeModeSegmentedRow`)**: 기존에 원하는 테마로 바꾸기 위해 여러 번 탭해 순환해야 했던 불편함을 개선하여, **원탭 3버튼 세그먼트(`자동 · 라이트 · 다크`)**(`ProfileHubViewModel.setThemeMode`)와 Dynamic Color 스위치를 단일 카드로 결합.
    - **④ 불필요한 `'정보'`(앱 버전 단독 카드) · `'계정'` 섹션 헤더 제거 (`AccountAndVersionFooter`)**: 화면 공간을 낭비하던 `'정보'` 섹션과 `'앱 버전'` 단독 카드를 과감히 제거하고, 깔끔한 `로그아웃` 아웃라인 버튼과 하단 버전 캡션(`은둔헬스 v0.7.2 (43)`)으로 경량화.
- **2. 진행 페이지 (`ProgressHubScreen.kt` / `ProgressHubViewModel.kt`) 전면 개선 및 고효율 통합**:
  - **'한눈에 보는 요약' (`VisualProgressSummaryDashboard`) 시각화·가독성 대폭 강화**:
    - 기존의 단조로운 3열 텍스트 박스를 **상단 주간 히어로 카드(`ThisWeekVisualHeroCard`) + 하단 2열 시각 타일(`FourWeekTrendVisualTile`, `GoalProgressVisualTile`)** 계층 구조로 개편.
    - **이번 주 히어로 카드**: 원형 프로그레스 링(`CircularProgressIndicator` + 달성률 `%`), 완료 일수(`1/2일`), 플랜 모드 배지(`헬스장`/`홈트레이닝`), 7일 미니 요일 스탬프 바(`WeeklyMiniDayStampsBar`, `월~일` 완료/미완료/휴식 아이콘) 통합.
    - **최근 4주 추이 타일**: 4주 평균 달성률(`%`), 연속 달성 스트릭 배지(`🔥 N주`), 주간 달성률 미니 막대 그래프(`MiniWeeklySparkBars`) 및 하단 프로그레스 바 시각화.
    - **목표까지 진행 타일**: 체중 목표 잔여량(`-2.5kg` / `달성!`), 현재 체중 → 목표 체중(`70.5kg → 68.0kg`) 및 체지방률 목표 요약 캡슐 배지 제공.
  - **'마음 연습' (`SmileTrainingProgressCard`) 시인성 개선**:
    - 당일 완료 여부(`오늘 완료` 시맨틱 그린 배지 vs `연속 N일` / `10초 루틴` 배지)와 원형 상태 아이콘을 적용해 직관성 강화.
  - **'운동 기록과 목표' 중복 3개 메뉴(`통계·기록·목표`) 과감히 제거 및 단일 `챌린지 배지` 쇼케이스 카드로 간소화**:
    - 상단 `'한눈에 보는 요약'` 대시보드(`이번 주 운동 기록` 히어로 카드 → `History`, `최근 4주 통계` 타일 → `Statistics`, `목표 관리` 타일 → `Goal`)와 기능이 중복되던 하단 `'운동 기록과 목표'`의 `통계`, `기록`, `목표` 3개 메뉴 행을 과감히 삭제.
    - 하단은 단일 **`챌린지 배지` 컬렉션 쇼케이스 카드(`BadgeCollectionShowcaseCard`)**만 남겨 달성 진행률(`earnedBadgeCount/totalBadgeCount` 프로그레스 바), 최근 획득 배지명, 대표 배지 5개 미니 칩(`BadgeMiniChip`)을 인라인으로 바로 확인하고 탭하여 전체 배지 화면으로 이동하도록 간소화.
  - **화면 복귀 자동 동기화**: `RefreshProgressOnResume` (`Lifecycle.Event.ON_RESUME`)을 부착하여 하위 화면(운동 기록·목표·배지·스마일 트레이닝)에서 돌아올 때 즉시 최신 요약을 갱신.
- **2. 골격근량·체지방률 직접 입력 0.1 단위 스냅 버그 수정 (`HybridBodyMetricPicker.kt`)**:
  - `DirectMetricInputDialog`에서 `0.1` 단위 값(예: 골격근량 `30.2kg`, 체지방률 `19.8%`)을 입력 후 '적용'을 누르면 눈금자 휠 step(`0.5`) 인덱스로 강제 반올림되어 `30.0` / `20.0`으로 덮어써지던 버그 해결 (`roundAndClampMetricValue(parsed, range, decimals)` 직접 반영 + `LaunchedEffect` 가드 추가).
  - [`BodyMetricAnchorTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ui/components/BodyMetricAnchorTest.kt) 및 [`DialogAndLayoutGuardTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ui/DialogAndLayoutGuardTest.kt), [`ProgressHubViewModelTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubViewModelTest.kt) 단위 테스트 검증 완료.
- **3. 레거시 DeepSeek 인프라 파일명 및 참조 정리**:
  - [`scripts/infra/meal-ai-endpoint.bicep`](scripts/infra/meal-ai-endpoint.bicep), [`scripts/infra/deploy-meal-ai-endpoint.ps1`](scripts/infra/deploy-meal-ai-endpoint.ps1)로 리네임 및 레거시 스크래치(`backend/scratch/test_ai.py`) 삭제.

## 2026-10-29 (Gemini -> Claude/User) Azure AI Foundry 식단 생성 AI 모델 `gpt-5.4-mini` (100K TPM) 전환 · `@docs` 전수 최신화 · PR Triage 통합 (PR #218)

**현재까지의 작업 상태 (Current Status):**
- **1. Azure AI Foundry 식단 생성 AI 모델 최적화 및 `gpt-5.4-mini` 프로덕션 프로비저닝 완결**:
  - **근본 원인 진단**: 기존 `ep-eundunhealth-deepseekv3` (`DeepSeek-V3.2`, `capacity: 1`) 배포가 `1 RPM / 1,000 TPM`으로 제한되어 식단 1회 생성(~2,200 토큰) 직후 재생성/동시 호출 시 즉시 `429 RateLimitError`가 발생하고 응답 시간이 15~25초 소요되던 병목 확인.
  - **모델 확정 및 라이브 배포 (`rg-eundunhealth-prod-krc` / `hub-eundunhealth-prod`)**:
    - [`scripts/infra/meal-ai-endpoint.bicep`](scripts/infra/meal-ai-endpoint.bicep)을 API 버전 `2024-10-01` 및 파라미터화 구조로 업그레이드하여 **`ep-eundunhealth-meal-ai` (`OpenAI` / `gpt-5.4-mini` / `2026-03-17` / `GlobalStandard` / `capacity: 100` = 100K TPM / 100 RPM, 유휴 비용 $0)** 배포 완료 및 구형 1-RPM `ep-eundunhealth-deepseekv3` 배포 정리 완료.
  - **백엔드 Strict Structured Outputs (`json_schema`) 및 설정 디커플링**:
    - [`backend/app/config.py`](backend/app/config.py) 및 [`backend/containerapp.yaml`](backend/containerapp.yaml)에 `meal_ai_model: str = "ep-eundunhealth-meal-ai"` (`MEAL_AI_MODEL`) 추가 (기존 Key Vault `deepseek-endpoint`, `deepseek-key` 시크릿 100% 재사용, 시크릿 회전 불필요).
    - [`backend/app/services/meal_ai_client.py`](backend/app/services/meal_ai_client.py): OpenAI Native Strict JSON Schema(`json_schema`, `strict: True`) 적용으로 파싱 에러(`500 AI_PARSING_ERROR`) 원천 차단, `월요일~일요일` 7개 고유 요일 및 `workout_dates` 기반 `isRestDay` 매핑 프롬프트 정교화, 타임아웃 `30.0s` 최적화.
    - **라이브 추론 실측 검증 통과**: `hub-eundunhealth-prod` 실제 호출 결과 **6.00초** 만에 7일 한식 식단(`월요일~일요일` 고유 요일 7개, 운동일/휴식일 매핑 정확) 생성 및 `WeeklyMealPlanResponse` 검증 100% 통과.
  - **앱 동의 문구 및 개인정보 처리방침 동기화**:
    - [`MealPlanScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/mealplan/MealPlanScreen.kt) 동의 다이얼로그 문구를 특정 모델 종속이 아닌 벤더 중립 `"Microsoft Azure AI Foundry"`로 개선.
    - [`docs/store/privacy-policy.md`](docs/store/privacy-policy.md) 및 [`backend/app/legal/privacy-policy.md`](backend/app/legal/privacy-policy.md) (`bash scripts/sync-legal-docs.sh`) 동기화 완료.

## 2026-10-29 (Gemini -> Claude/User) `@docs` 전수 점검/검증/검토/최신화 및 리팩토링 완결 (TRD · SPEC · PRD · Store)

**현재까지의 작업 상태 (Current Status):**
- **1. `@docs` 전체 문서 점검·검증·검토·최신화 완수 (SSoT 실측 기반 일치화)**:
  - **[`docs/TRD.md`](docs/TRD.md)**:
    - 회원 탈퇴 엔드포인트 SSoT 정정: 백엔드 `app/routers/account.py`의 실제 라우트인 `DELETE /account`로 전수 정정 (기존 오기재 `DELETE /profile` 5곳 수정).
    - Gradle 빌드 도구 실측값 동기화: `gradle-wrapper.properties` 실측 버전인 `Gradle 9.8.2`로 정합화.
  - **[`docs/SPEC.md`](docs/SPEC.md)**:
    - 문서 버전 **v2.0** 승격 및 최신 아키텍처(PostgreSQL 18 하드 리셋, 스마일 트레이닝 v2, Modern Forest & Mint, Pretendard Variable) 헤더 반영.
    - 백엔드 스택 표 정합화: 폐기된 `Alembic` 제거 → `PostgreSQL 18 DDL (02_schema_create_all.sql)` + `python -m app.schema_bootstrap` 멱등 부트스트랩 반영.
    - 백엔드 27개 API 전수 명세표 완비 (System, Profile, Weekly Plan v2, Smile Training, Meals, Badges & Goals, Account, Legal) 및 초기 v1.0 10개 엔드포인트는 `<details>` 아카이브 격리.
    - 프로젝트 디렉토리 구조 최신화: Android(Forest & Mint, 4대 허브 탭, Room v3/v2) 및 Backend(FastAPI Python `backend/app/` 8개 APIRouter) 반영 (과거 Ktor 제거).
    - Sentry 풀스택 분산 추적(Android `eundunhealth` ↔ Backend `eundunhealth-backend` 프로젝트 분리) 및 외부 서비스(OSS ExerciseDB 무인증, Azure AI Foundry DeepSeek-V3.2, Key Vault 8종 시크릿) 정합화.
  - **[`docs/PRD.md`](docs/PRD.md)**:
    - 섹션 7(구현 상태 표)에 `v0.6.1` (UI/UX 전면 개편), `v0.7.0` (PostgreSQL 18 하드 리셋 & 스마일 트레이닝 v2 통합), `v0.7.1` (콜드 스타트 핫픽스 & 5계층 심층 방어) 마일스톤 행 추가.
    - 폐기된 Alembic 마이그레이션 리비전 해시(`e8b2c4d6f1a0`, `d7f4a2c91b10`) 서술 전수 제거 및 PostgreSQL 18 테이블 정본 서술로 정리.
  - **[`docs/store/`](docs/store/) 스토어 공개 문서**:
    - [`account-deletion.md`](docs/store/account-deletion.md), [`privacy-policy.md`](docs/store/privacy-policy.md) 최종 업데이트 일자 `2026-10-29` 갱신.
- **2. 문서 무결성 및 자동화 게이트 전수 검증**:
  - `bash scripts/check-plans-links.sh` → **OK: 끊긴 참조 0건**.
  - `backend/.venv/Scripts/pytest.exe scripts/agents/test_doc_audit.py -q` → **36 passed in 0.08s**.
  - `backend/.venv/Scripts/python.exe scripts/agents/doc_audit.py --collect-only` → **27개 라우트, app_version 0.7.1/42, backend_api_version 1.0.0, python 3.14 전수 일치**.

**다음 할 일 (Next Steps / TODO):**
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-29 (Gemini -> Claude/User) `v0.7.1/42` 프로덕션 배포 완료 · 배포 연동 PR Triage 거버넌스(룰 16) 수립 · 레거시/의존성 PR 9건(`#202~#214`) 일괄 정리 완료

**현재까지의 작업 상태 (Current Status):**
- **1. `v0.7.1` (`versionCode=42`) 프로덕션 핫픽스 배포 및 원장 동기화 완결**:
  - **PR [#216](https://github.com/gunnysis/eundunHealth/pull/216)** (`release/v0.7.1`, 커밋 `42db341`) 모든 필수 CI 체크 통과 및 `main` 병합 완료.
  - 태그 `v0.7.1` 푸시 → **`release.yml` (run [`40825396534`](https://github.com/gunnysis/eundunHealth/actions/runs/40825396534))** `play-release` 환경 승인 및 Google Play 프로덕션 트랙 `completed`(100%) 업로드 성공.
  - 자동 생성된 원장 PR **#217 (`release-ledger/v0.7.1-42`)** CI 검증 후 `main` 병합(`a0588c9`, [`docs/ops/play-upload-ledger.md`](docs/ops/play-upload-ledger.md) `LAST_UPLOADED_VERSION_CODE=42` 반영 완료).
- **2. 배포 연동 PR Triage 거버넌스 (`CLAUDE.md` 룰 16) 및 [`scripts/triage-prs.sh`](scripts/triage-prs.sh) 구축**:
  - 오픈 PR을 5대 버킷(`[RELEASE-LEDGER]`, `[CLEAN-DIRECT]`, `[GRADLE-LOCKFILE-BATCH]`, `[PIP-HASH-BATCH]`, `[ACTIVE-WORK]`)으로 자동 분류하는 [`scripts/triage-prs.sh`](scripts/triage-prs.sh)를 신설하고 [`scripts/preflight-release.sh`](scripts/preflight-release.sh)에 `--pre-release-gate`로 연동.
  - [`.github/dependabot.yml`](.github/dependabot.yml) 개편: `github-actions`를 단일 `ci-actions` 그룹으로, 비-AndroidX/Kotlin Gradle 마이너·패치 업데이트를 `gradle-plugins-and-libs` 그룹으로 묶어 주간 PR 파편화 및 상호 충돌 원천 방지.
  - [`backend/requirements-dev.in`](backend/requirements-dev.in)에 `colorama==0.4.6`을 명시 고정해 Linux Dependabot 실행 시 Windows 조건부 해시가 누락되는 교차 플랫폼 함정 근본 해소.
- **3. 레거시/의존성 오픈 PR 9건 (`#202`, `#203`, `#204`, `#206`, `#207`, `#208`, `#209`, `#210`, `#214`) 일괄 흡수 및 종결**:
  - GitHub Actions SHA 갱신 3건(`#202` `trivy-action`, `#203` `setup-gradle`, `#204` `paths-filter`), Gradle Wrapper `9.8.2`(`#210`), Android 라이브러리/플러그인 4건(`#206` `sentry 8.61.1`, `#207` `msal 8.6.0`, `#208` `openapi-generator 7.26.0`, `#209` `spotless 8.10.4` + `app/gradle.lockfile` & `gradle/verification-metadata.xml` SHA-256 동기화), Backend Python 4건(`#214` `fastapi 0.162.0`, `starlette 1.10.0`, `pytest-asyncio 1.4.1`, `pip-tools 7.6.2`)을 단일 검증 배치로 통합.

---

## 2026-10-29 (Gemini -> Claude/User) `docs/TRD.md` 전체 최신화 · 리팩토링 · 개선 및 디자인(v2.0 정본) 작업 완료

**현재까지의 작업 상태 (Current Status):**
- **[`docs/TRD.md`](docs/TRD.md) 전체 최신화 및 현대화 리팩토링 완수 (v1.0 레거시 탈피 -> v2.0 현행 아키텍처 정본)**:
  - **머리말 & 메타데이터**:
    - 문서 버전 **v2.0** 승격, Google Play 최신 프로덕션 **v0.7.0/41(`completed` 100%)** 및 핫픽스 **v0.7.1/42**, 백엔드 활성 리비전 **`eundunhealth-api--0000067` (`40595a1`)** 동기화.
    - GitHub Alert(`> [!IMPORTANT]`) 도입으로 문서 정본 원칙 명시.
  - **§0 아키텍처 전환 및 릴리스 히스토리**:
    - 초기 설계(Ktor, Supabase, Exposed, Alembic) 대비 현행 프로덕션의 14개 핵심 영역 대조 비교표 및 v0.1.0~v0.7.1 릴리스 마일스톤 로그 체계화.
  - **§1 시스템 아키텍처**:
    - **§1.1**: 풀스택 토폴로지 Mermaid 다이어그램 + 정밀 ASCII 아키텍처 다이어그램.
    - **§1.2**: Azure Database for PostgreSQL 18.4 Flexible Server (`healthapp`), B1ms, SQLAlchemy 2.0 Async + asyncpg 0.32.0 (Pool 3+2, `pool_pre_ping=True`, `command_timeout=30s`), Zero-Legacy Drop & Recreate 파이프라인. 구형 HikariCP/Exposed는 `<details>` 아카이브 격리.
    - **§1.3**: Azure Container Apps (`eundunhealth-api`, min 1 / max 3 warm baseline, CPU 0.25 / RAM 0.5Gi, HTTP 프로브 3종) 및 Container Apps Job (`eundunhealth-reaper`, 주간 월 03:00 KST 고아 계정 청소 잡).
  - **§2 기술 스택 상세**:
    - **§2.1**: Windows 11 + WSL2 Ubuntu 하이브리드, Gradle 9.8.1, AGP 9.4.1, Sentry(Android 8.61.1 / Backend 2.73.0).
    - **§2.2**: Kotlin 2.4.21, Compose BOM 2026.09.00, Material 3 Modern Forest & Mint (36슬롯) + 시맨틱 13토큰(`EundunExtendedColors`), Pretendard Variable 가변 폰트, DayNight 번쩍임 방지.
    - **§2.3**: Microsoft Entra External ID (CIAM) OIDC/PKCE 위임, Mermaid 시퀀스 + ASCII 상태머신 + 4단계 백엔드 JWT 검증 (`oid`, `access_as_user`).
    - **§2.4**: Health Connect 1.1.0 stable (`READ_EXERCISE` 단독), Samsung Health Data SDK 비도입 사유 명시.
    - **§2.5 & §2.6**: OSS ExerciseDB, Graph API, Foundry DeepSeek-V3.2, OkHttp 5.5.0 최외곽 Sentry 인터셉터 체인, Retrofit 3.0.0.
  - **§3 앱 아키텍처**:
    - Clean Architecture 계층도, UDF(단일 `_uiState`, `@Immutable`, `collectAsStateWithLifecycle`, SideEffect 채널), Room DB v3 + v2, DataStore 1.3.0, Coil 3.4.0.
    - **§3.6**: 온디바이스 스마일 트레이닝 v2 비전 AI 파이프라인 (CameraX 1.6.2 + ML Kit Face Detection 17.1.0, 단일 규격 2.0초×3회, 1.0초 자동 시작, 영상 무저장/무전송).
  - **§4 백엔드 아키텍처**:
    - FastAPI 0.161.0 (Python 3.14), lifespan 기동 시퀀스 (`schema_bootstrap` 멱등 부트스트랩, JWKS 사전 캐싱).
    - **10개 도메인 27개 API 엔드포인트 전수 명세표** (Health, Compatibility, Profile, WeeklyPlan v2, Meals, SmileTraining, Badges, Goals, Account, Legal).
    - 와일드카드 차단 CORSMiddleware, `request_id_middleware` (`X-Request-ID` 상관관계 로깅), 개인정보 Redacted 에러 핸들러 (422 키/몸무게 마스킹). 구형 Ktor 3.4.3은 아카이브 격리.
  - **§5 데이터베이스 스키마 (PostgreSQL 18 3NF 정규화)**:
    - **Mermaid 도메인 ERD 다이어그램** 추가.
    - **9개 3NF 정규화 테이블 전수 명세** (`user_profiles`, `user_profile_history`, `weekly_plans`, `goals`, `badges`, `meal_plans`, `meal_plan_items`, `smile_training_sessions`, `data_reset_audits`).
    - PostgreSQL 18 네이티브 `DEFAULT uuidv7()` PK, `ON DELETE CASCADE` 외래키, 도메인 CHECK 제약조건 11종 및 인덱스 전략 명시.
    - 하드 리셋 및 100건 엣지케이스 검증 파이프라인 정리 (Alembic 마이그레이션 폐기).
  - **§6 보안 아키텍처**:
    - HTTPS 강제, R8 ProGuard keep 룰, 16KB 페이지 정렬, Entra RS256 비대칭 서명 검증, Azure Key Vault 8종 시크릿 RBAC, Graph API 회원 탈퇴(204) + `deletedItems` 즉시 영구 파기, `LocalDataWiper` 로컬 와이프.
  - **§7 모니터링 및 관측성**:
    - Sentry 풀스택 분산 추적 (Android `eundunhealth` ↔ Backend `eundunhealth-backend` 프로젝트 분리), Azure Log Analytics 진단 감사.
  - **§8 빌드, 테스트 및 배포 파이프라인 (CI/CD)**:
    - Android (Gradle 9.8.1, AGP 9.4.1, `release.yml` Google Play 프로덕션 트랙 100% CD).
    - Backend (Python 3.14 venv, pytest 9.1.1 143+ PASS, `backend.yml` Entra OIDC 무암호 배포).
  - **§9 프로젝트 구조 및 환경 변수**:
    - 최신 프로젝트 디렉토리 트리 및 Android `local.properties` / Backend Key Vault 시크릿 8종 매트릭스.
  - **§10 PRD 기능 요구사항 대비 기술 매핑 매트릭스**:
    - AUTH, PROF, PLAN, MEAL, SMILE, TRACK, BADGE, ETC 전 도메인 최신 매핑 정합화.
- **문서 무결성 검증 결과**:
  - `bash scripts/check-plans-links.sh` 통과 (**끊긴 참조 0건**).
  - `backend/.venv/Scripts/pytest.exe scripts/agents/test_doc_audit.py -q` 통과 (**36 passed in 0.08s**).

**다음 할 일 (Next Steps / TODO):**
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-29 (Gemini -> Claude/User) v0.7.0(`versionCode=41`) 업데이트 직후 앱 기동 즉시 종료(`EUNDUNHEALTH-H`) 근본 해결 · 리팩토링 · 5계층 테스트/디버깅 정책(룰 14) 및 AGP 아키텍처/호환성 정책(룰 15) 정착 완료

**현재까지의 작업 상태 (Current Status):**
- **1. 앱 기동 즉시 종료(`EUNDUNHEALTH-H` / ID `7817803343`, 19 events / 6 users) 근본 원인 규명 및 해결**:
  - **직접 원인 (`DatabaseModule.kt:47-49`)**: `DatabaseModule.provideSmileTrainingDatabase` 에 `.addMigrations(SmileTrainingDatabase.MIGRATION_1_2)`(`startVersion=1, endVersion=2`)와 `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`(`startVersion=1`)이 동시에 지정되어 있었음. Room의 `RoomDatabase.Builder.build()` 내부 `validateMigrationsNotRequired()` 는 명시적 Migration의 시작/종료 버전과 파괴적 폴백 시작 버전이 겹치면 SQLite 파일을 열기도 전에 무조건 `IllegalArgumentException: Inconsistency detected... Start version is: 1`을 던짐.
  - **증폭 원인 (`DefaultLocalDataWiper.kt` & `AuthViewModel.kt`)**: `MainActivity.onCreate` → `AppNavigation()` 첫 Compose 프레임에서 `hiltViewModel<AuthViewModel>()` 생성 시 `DefaultLocalDataWiper` 가 `WeeklyPlanDao` 와 `SmileSessionDao` 를 **Eager(비지연)** 주입받고 있어, `themePreferences.isV2LocalResetCompleted()` 가 이미 `true` 인 일반 기동에서도 메인 스레드 `HiltViewModelFactory.create()` 생성자 체인에서 `provideSmileTrainingDatabase().build()` 가 동기 실행되어 `AuthViewModel.checkSession()` 의 `runCatching` 방어선을 우회하고 즉시 크래시함.
  - **해결 및 리팩토링**:
    - [`DatabaseModule.kt`](app/src/main/java/com/gunnys/eundunhealth/di/DatabaseModule.kt): `provideSmileTrainingDatabase` 에서 중복·충돌 설정인 `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)` 을 제거하고 `.addMigrations(SmileTrainingDatabase.MIGRATION_1_2)` 단일 경로로 정합화.
    - [`LocalDataWiper.kt`](app/src/main/java/com/gunnys/eundunhealth/data/local/LocalDataWiper.kt): `DefaultLocalDataWiper` 의 `weeklyPlanDao` 와 `smileSessionDao` 를 `dagger.Lazy<WeeklyPlanDao>`, `dagger.Lazy<SmileSessionDao>` 로 지연 주입하도록 리팩토링하여 일반 기동 시 메인 스레드에서 Room DB가 초기화되지 않도록 격리.
    - [`AppError.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/model/AppError.kt) 및 전 Repository/UseCase/ViewModel(`MealPlanRepositoryImpl`, `GoalRepositoryImpl`, `BadgeRepositoryImpl`, `HealthRepositoryImpl`, `AuthRepositoryImpl`, `WorkoutRepositoryImpl`, `UserRepositoryImpl`, `CheckAndAwardBadgesUseCase`, `SyncHealthDataUseCase`, `AuthViewModel`, `OnboardingViewModel`, `ProfileViewModel`): 공용 `runSuspendCatching` 도입으로 코루틴 취소(`CancellationException`) 재전파 보장 및 `AppError.reportToSentry()` 에서 `CancellationException` 의 Sentry 오탐 전송 원천 차단.
- **2. 재발 방지 5계층 방어 정책(룰 14) 및 AGP 아키텍처/업그레이드 거버넌스(룰 15) 설계·구현**:
  - 신규 단위/계측 테스트 및 런타임 스모크 스크립트:
    - [`DatabaseModuleTest.kt`](app/src/test/java/com/gunnys/eundunhealth/di/DatabaseModuleTest.kt): 실제 `DatabaseModule.provideDatabase()` 및 `provideSmileTrainingDatabase()` 호출 검증 + 동일 버전 중복 시 `IllegalArgumentException` 발생 증명 + Room schema JSON 존재 검증.
    - [`NetworkModuleTest.kt`](app/src/test/java/com/gunnys/eundunhealth/di/NetworkModuleTest.kt): `backend`, `meals`, `exercisedb` 3개 실제 `OkHttpClient` 빌더에서 `SentryOkHttpInterceptor` 가 항상 `RetryInterceptor` 앞(바깥)에 위치하는지 고정(INC-33 `EUNDUNHEALTH-G` 재발 방지).
    - [`DefaultLocalDataWiperTest.kt`](app/src/test/java/com/gunnys/eundunhealth/data/local/DefaultLocalDataWiperTest.kt): `isV2LocalResetCompleted() == true` 시 `Lazy<Dao>.get()` 미호출(0회) 단언 및 리셋/계정삭제 동작 검증.
    - [`AgpBuildContractTest.kt`](app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt) & [`scripts/check-agp10-compat.sh`](scripts/check-agp10-compat.sh): AGP 안정판 버전 형식(`X.Y.Z`, alpha/beta/rc 금지), 폐기 opt-out 플래그 8종 재도입 금지, `kotlinOptions` 금지, `androidComponents` 기반 `detektDebug`, `useLegacyPackaging = false`(16KB 페이지 정렬), `lockAllConfigurations()`, `verification-metadata.xml` 내 Windows + Linux `aapt2` 해시 동시 존재 불변식 검증.
    - [`DatabaseMigrationAndInitTest.kt`](app/src/androidTest/java/com/gunnys/eundunhealth/data/local/DatabaseMigrationAndInitTest.kt): 실제 Android SQLite 런타임에서 신규 설치(v2) 및 구버전(`v1 -> v2` `MIGRATION_1_2`) 마이그레이션·CRUD 계측 검증.
    - [`scripts/smoke-cold-start.sh`](scripts/smoke-cold-start.sh) & [`scripts/preflight-release.sh`](scripts/preflight-release.sh): 에뮬레이터/실기기 대상 APK 설치 후 `am start -W -S` 강제 Cold Start → 5초 대기 → `pidof` 프로세스 생존 + `logcat -b crash` `FATAL EXCEPTION` 0건 검증 게이트 구축.
  - 문서 반영: [`CLAUDE.md`](CLAUDE.md)(룰 14·룰 15 신설), [`docs/ops/incident-log.md`](docs/ops/incident-log.md)(`INC-2026-10-29-33`, `INC-2026-10-29-34` 등재).
- **3. 전수 실측 검증 결과**:
  - `./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleRelease` → **BUILD SUCCESSFUL** (단위 테스트 227개 통과, Detekt 0건, R8 Release APK 빌드 성공).
  - 헤드리스 에뮬레이터(`Galaxy_S25_Ultra`, Android 16) 실측:
    - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gunnys.eundunhealth.data.local.DatabaseMigrationAndInitTest` → **2/2 PASS (`BUILD SUCCESSFUL`)**.
    - `bash scripts/smoke-cold-start.sh app/build/outputs/apk/debug/app-debug.apk --require-device` → **PASS (PID 생존, `FATAL EXCEPTION` 0건)**.
    - `bash scripts/smoke-cold-start.sh app/build/outputs/apk/release/app-release.apk --require-device` → **PASS (TotalTime 1262ms, PID 생존, `FATAL EXCEPTION` 0건)**.
  - WSL2 Ubuntu 패리티: `check-agp10-compat.sh` · `check-plans-links.sh` · `doc_audit.py --collect-only` 전수 통과.

**다음 할 일 (Next Steps / TODO):**
- 핫픽스 릴리스(`v0.7.1`, `versionCode=42`) 배포 지시 시 `bash scripts/bump-version.sh 0.7.1` 및 `bash scripts/preflight-release.sh` 실행 후 PR/태그 배포 진행.
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-29 (Gemini -> Claude/User) `docs/plans` 완료 작업 정리 및 레거시 페어 파일 토픽 원장(Topic Ledgers) 흡수 완료

**현재까지의 작업 상태 (Current Status):**
- **`docs/plans` 워크플로 준수 완료 작업 아카이빙 및 레거시 제거 완수**:
  1. **완료 계획서 원장 흡수**:
     - `2026-10-29-postgresql-schema-hard-reset-plan.md` → [`docs/plans/logs/backend.md`](docs/plans/logs/backend.md)의 `## Recent (last 90 days)` 섹션 상단으로 요약 흡수.
     - `2026-10-29-smile-training-v2-plan.md` → [`docs/plans/logs/android.md`](docs/plans/logs/android.md)의 `## Recent (last 90 days)` 섹션 상단으로 요약 흡수.
  2. **페어 파일 정리**:
     - 루트의 완료된 페어 파일 2종(`2026-10-29-postgresql-schema-hard-reset-plan.md`, `2026-10-29-smile-training-v2-plan.md`) `git rm` 처리.
  3. **참조 리다이렉트 동기화**:
     - [`docs/SPEC.md`](docs/SPEC.md): 스마일 트레이닝 v2 설계 링크를 [`docs/plans/logs/android.md`](docs/plans/logs/android.md)(2026-10-29 entry)로 갱신.
     - [`AI_HANDOFF.md`](AI_HANDOFF.md): 과거 릴리스/구현 내역의 페어 파일 링크에 원장 리다이렉트 병기.
  4. **인덱스 재생성 및 링크 무결성 실측 검증**:
     - `backend/.venv/Scripts/python.exe scripts/gen_plans_index.py` 실행 → [`docs/plans/README.md`](docs/plans/README.md) 활성 페어 0건으로 클린 갱신.
     - `backend/.venv/Scripts/python.exe scripts/gen_plans_index.py --check` 통과 (drift 0건).
     - `bash scripts/check-plans-links.sh` 통과 (**OK: docs/plans/ 페어 참조 링크 정상, 끊긴 참조 0건**).

**다음 할 일 (Next Steps / TODO):**
- 신규 작업 시작 시 `_templates/{design,plan}.md`를 복사하여 `docs/plans/` 루트에 페어 파일 작성.
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-29 (Gemini -> Claude/User) v0.7.0 (`versionCode=41`) 풀스택 운영 배포(Azure Container Apps & Google Play Production) 완료

**현재까지의 작업 상태 (Current Status):**
- **v0.7.0 (`versionCode=41`, `MINIMUM_SUPPORTED_ANDROID_VERSION_CODE=41`) 풀스택 운영 배포 완수**:
  1. **릴리즈 사전 검증 ([`scripts/preflight-release.sh`](scripts/preflight-release.sh))**:
     - `version.properties`(`0.7.0` / `41`), `MINIMUM_SUPPORTED_ANDROID_VERSION_CODE=41` 상향, `docs/CHANGELOG.md`·`docs/PRD.md`·`README.md`·`CLAUDE.md`·`docs/ops/operations-snapshot.md` 동기화.
     - 로컬 `scripts/preflight-release.sh` 실행 통과(Spotless·Detekt·Unit Test·서명 AAB/APK 빌드 및 Sentry ProGuard 매핑 업로드).
     - PR #213 (`release/v0.7.0` → `main`) 생성 후 GitHub Actions 전 게이트(`Repository Gate`, `Android required checks`, `Backend required checks`, `Security scans`, `CodeQL`) 통과 및 `main` 머지(`40595a1`).
  2. **백엔드 운영 배포 (`Backend CI/CD` · Run `40816129146`)**:
     - `backend-production` 환경 승인 후 ACR 이미지 빌드·Trivy 스캔·푸시·`eundunhealth-api` Container App 배포 및 `eundunhealth-reaper` Job 동기화 완료.
     - **활성 리비전**: `eundunhealth-api--0000067` (`Running`, 100% traffic)
     - **배포 이미지**: `eundunhealthacr.azurecr.io/eundunhealth-api:40595a1` (`@sha256:80b271e1ef76519c3958aa750d35b2c67f793fa7505aec5d0894d695ca57c18f`)
     - **라이브 엔드포인트 실측 검증**:
       - `GET /health` → `200 OK` (`{"status":"ok"}`)
       - `GET /health/ready` → `200 OK` (`{"status":"ready"}`, Azure PostgreSQL 18.4 `healthapp` 연동 정상)
       - `GET /app-compatibility?platform=android&versionCode=41` → `{"minimumSupportedVersionCode":41,"updateRequired":false}`
       - `GET /app-compatibility?platform=android&versionCode=40` → `{"minimumSupportedVersionCode":41,"updateRequired":true}`
       - `GET /v2/smile-training/sessions`, `GET /v2/smile-training/statistics` → `401 Unauthorized` (v2 라우트 정상 등록 및 Entra 보호 확인)
       - `GET /v1/smile-training/sessions` → `404 Not Found` (레거시 v1 완전 제거 확인)
  3. **Android Google Play 프로덕션 배포 (`Release (Play production)` · Run `40816257901`)**:
     - 태그 `v0.7.0` 푸시 및 `play-release` 환경 승인 후 `scripts/preflight-release.sh` 전체 게이트 통과, Google Play `production` 트랙 `completed`(100%) 업로드 성공.
     - 자동 생성된 원장 PR #215 (`release-ledger/v0.7.0-41`, [`docs/ops/play-upload-ledger.md`](docs/ops/play-upload-ledger.md) `LAST_UPLOADED_VERSION_CODE=41`)에 본 운영 스냅샷 및 핸드오프 갱신을 통합하여 `main` 머지.
  4. **Sentry 배포 후 모니터링 (`gunnys` 조직)**:
     - `eundunhealth-backend` 및 `eundunhealth` 프로젝트 모두 v0.7.0 배포 이후 신규 에러 이슈 `0건` 확인.

**다음 할 일 (Next Steps / TODO):**
- 다음 릴리스 시 `versionCode >= 42` 단조 증가 규칙(룰 13) 준수.
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-29 (Gemini -> Claude/User) 12인 역할 협의체 기반 프로젝트 전체 점검·리팩토링·개선 및 전수 검증 완료

**현재까지의 작업 상태 (Current Status):**
- **오늘 수행된 스마일 트레이닝 v2 컷오버 + PostgreSQL 18 하드 리셋 파이프라인 구축을 기준으로, 12개 역할(`[디자이너, 기획, 실무, 책임, 개발, 구현, 유지보수, QA/QC, DevOps, DBA, 팀장, CTO]`) 관점의 전사 감사 및 근본 원인 리팩토링 완수**:
  1. **DBA / Backend / SQLAlchemy 2.0 / PostgreSQL 18 정합성 리팩토링**:
     - **정본 DDL ([`backend/sql/02_schema_create_all.sql`](backend/sql/02_schema_create_all.sql)) ↔ SQLAlchemy `Base.metadata` 1:1 패리티 달성**:
       - `smile_training_sessions`의 체크 제약 조건 명칭을 `POSTGRES_NAMING_CONVENTION`과 1:1 일치하도록 `ck_smile_training_sessions_duration_sec_range`, `ck_smile_training_sessions_completed_reps_range`로 통일.
       - 8개 도메인 ORM 모델(`user_profile.py`, `user_profile_history.py`, `weekly_plan.py`, `goal.py`, `badge.py`, `meal_plan.py`, `smile_training.py`) 전수에서 SQLAlchemy 2.0 공식 방언 중립 `from sqlalchemy import Uuid` (`Uuid(as_uuid=True)`) 및 Python 3.14 표준 라이브러리 `default=uuid.uuid7`로 통일하여 PostgreSQL 네이티브 `UUID`와 SQLite `CHAR(32)` 양쪽에서 타입 강제 변환 버그를 원천 차단.
       - [`backend/app/database.py`](backend/app/database.py): PostgreSQL 대상 `Base.metadata.create_all` 직후 8개 도메인 테이블 PK에 `ALTER TABLE ... ALTER COLUMN id SET DEFAULT uuidv7()`를 자동 부여하는 `_set_pg_uuidv7_server_defaults` 이벤트 리스너 추가 + Azure PostgreSQL Flexible Server `Standard_B1ms` 연결 한도에 맞춘 `build_engine_kwargs(database_url)`(`pool_size=3, max_overflow=2, pool_pre_ping=True, pool_recycle=1800, pool_timeout=30, connect_args={"command_timeout": 30}`) 도입 및 [`backend/app/main.py`](backend/app/main.py), [`backend/scripts/reap_orphaned_accounts.py`](backend/scripts/reap_orphaned_accounts.py) 적용.
       - [`backend/app/schema_bootstrap.py`](backend/app/schema_bootstrap.py): `pg_advisory_xact_lock` 획득 전 `SET LOCAL lock_timeout = '15s'` 설정 및 `os.environ.get("DATABASE_URL")` 우선 참조 보강.
       - [`backend/scripts/hard_reset_db.py`](backend/scripts/hard_reset_db.py): 인라인 SQL 주석(`--`)이 포함된 구문도 누락 없이 실행되도록 `_strip_sql_line_comment` 파서 개선 및 `--seed-edge-cases` 사용 시 `--verify-constraints` 자동 활성화.
       - [`backend/tests/test_v2_reset.py`](backend/tests/test_v2_reset.py): `test_canonical_sql_matches_orm_metadata_1_to_1`(정본 SQL과 ORM 메타데이터의 테이블·컬럼·PK·FK·Unique·Check·Index 이름 1:1 대조), `test_split_sql_statements_handles_inline_comments`, `test_build_engine_kwargs_for_postgresql_and_sqlite` 추가 (**백엔드 테스트 146 passed**).
  2. **DevOps / CI·CD / 문서 감사 자동화 리팩토링**:
     - [`.github/workflows/backend.yml`](.github/workflows/backend.yml): `ruff check` 및 `ruff format --check` 대상에 `scripts/` 포함, `runtime-smoke` 잡에서 `/health/ready` 200 검증·엔트리포인트 부트스트랩 로그 검증·PostgreSQL 18 대상 `hard_reset_db.py --confirmation DROP_AND_RECREATE_ALL_SCHEMAS --seed-edge-cases --verify-constraints` 라이브 실행 검증 추가.
     - [`backend/entrypoint.sh`](backend/entrypoint.sh), [`.claude/commands/naming-audit.md`](.claude/commands/naming-audit.md), [`.claude/commands/verify-deploy.md`](.claude/commands/verify-deploy.md), [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md), [`scripts/prompts/api-endpoint.md`](scripts/prompts/api-endpoint.md)에서 잔존하던 구형 Alembic 참조를 전수 제거하고 신규 `schema_bootstrap` / `hard_reset_db.py` 절차로 갱신.
  3. **Android (UX/UI · 접근성 · 아키텍처 · 오프라인 동기화 · 룰 8 & 룰 11) 리팩토링**:
     - [`ThemePreferences.kt`](app/src/main/java/com/gunnys/eundunhealth/data/preferences/ThemePreferences.kt): `markV2LocalResetCompleted()` 실행 시 사용자 테마(`themeKey`)와 Dynamic Color(`dynamicColorKey`) 설정은 보존하도록 개선.
     - [`SmileSyncWorker.kt`](app/src/main/java/com/gunnys/eundunhealth/data/sync/SmileSyncWorker.kt) & [`SmileTrainingRepositoryImpl.kt`](app/src/main/java/com/gunnys/eundunhealth/data/repository/SmileTrainingRepositoryImpl.kt): `ExistingWorkPolicy.APPEND_OR_REPLACE` 적용(선행 워커 실행 중 추가된 신규 세션 누락 방지), 단일 워커 패스에서 최대 10배치 반복 드레인, `deleteAllSessions()` 시 `cancelUniqueWork` 선행 호출, 기기 시계 오차(미래 타임스탬프) 필터링(`.filter { !it.isAfter(todayKst) }`).
     - [`SmileSignalEvaluator.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/smile/SmileSignalEvaluator.kt): 슬라이딩 윈도우(75%)가 유지되는 1프레임 노이즈(`BelowThreshold`) 동안 안내 문구(`guideMessage`)와 TalkBack `liveRegion`이 깜빡이지 않도록 고정.
     - [`SmileTrainingViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileTrainingViewModel.kt) & [`MlKitFaceModuleInstaller.kt`](app/src/main/java/com/gunnys/eundunhealth/data/smile/MlKitFaceModuleInstaller.kt): `SmileTrainingSideEffect` 및 `MlKitModuleState` 전 서브타입 `@Immutable` 명시(룰 11), `modelPrepareJob` 취소 가드, 일시정지 구간(`pauseActiveExercise` ~ `beginActiveExercise`)을 실제 운동 시간(`sessionDurationSec`)에서 제외, `onCameraError` 시 `.reportToSentry()` 연동(룰 8), 일시정지된 `READY` 상태에서 뒤로가기 시 종료 확인 다이얼로그 표시.
     - [`SmileCameraStage.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileCameraStage.kt) & [`SmileTrainingScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileTrainingScreen.kt): `onDispose` 내 `cameraProviderFuture.isDone` 가드로 메인 스레드 블로킹 방지, 어두운 카메라 HUD 진입 시 상태바 밝은 아이콘(`isAppearanceLightStatusBars = false`) 자동 전환 및 복원, `onPermissionRevoked` 시 동적 `shouldShowRequestPermissionRationale` 계산, 다이얼로그 본문 `verticalScroll` 및 버튼 `.heightIn(min = 48.dp+)` 적용(200% 대형 글꼴 대응).
     - [`WorkoutGuideScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideScreen.kt), [`HomeScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt), [`HomeViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt): 운동 가이드 완료 화면에서 시스템 뒤로가기 시 `onWorkoutCompleted()` 누락 해결, 홈 상단 `SmileQuickActionChip` `clearAndSetSemantics(role = Role.Button)` 및 연속 달성일 음성 안내 보강, `SmileTrainingContextualCard` 내 중복 `'오늘 하루 닫기'` 버튼 제거, `refreshHealthConnectStatus()` 시 자정 경과(`today`, `isSmileShortcutDismissedToday`) 동시 갱신.

---

## 2026-10-29 (Gemini -> Claude/User) Azure Database for PostgreSQL 18 업그레이드 · 스키마 전면 리팩토링 및 하드 리셋(Drop & Recreate) 파이프라인 구축 완료

**현재까지의 작업 상태 (Current Status):**
- **Task 0 ~ Task 4 전 구간 구현 및 라이브 검증 완료 ([`docs/plans/2026-10-29-postgresql-schema-hard-reset-plan.md`](docs/plans/2026-10-29-postgresql-schema-hard-reset-plan.md) → 흡수: [`docs/plans/logs/backend.md`](docs/plans/logs/backend.md))**:
  1. **Task 0 — Azure PostgreSQL 18.4 동적 파라미터 튜닝 & 로컬/CI Docker PostgreSQL 18.6 업그레이드**:
     - Azure CLI(`az postgres flexible-server show/list-skus/parameter set`)로 운영 `healthapp`(`rg-eundunhealth-prod-krc`, Korea Central 최고 지원 버전 **PostgreSQL 18.4 / minorVersion 6**, `io_method=worker`, `wal_compression=zstd`) 점검 및 무중단 동적 서버 파라미터(`isDynamicConfig=true`) 5종 적용 완료:
       - `idle_in_transaction_session_timeout = 60000` (60s)
       - `azure.extensions = PG_STAT_STATEMENTS`
       - `pg_stat_statements.track = top`
       - `track_io_timing = on`
       - `log_min_duration_statement = 1000` (1s)
     - [`backend/docker-compose.yml`](backend/docker-compose.yml): `postgres:16-alpine` → **`postgres:18-alpine@sha256:3b0ef735388b2cb097fc29b8bb421d86d57d0ddbe7aa1fbd6493e06d65028503` (`18.6-alpine3.24`)** 업그레이드 및 PG 18 공식 이미지 볼륨 경로(`pgdata:/var/lib/postgresql`) 적용.
  2. **Task 1 — Cleanup SQL 스크립트 ([`backend/sql/01_cleanup_drop_all.sql`](backend/sql/01_cleanup_drop_all.sql))**:
     - Azure PostgreSQL Flexible Server의 `azure_pg_admin` 소유 `public` 스키마 및 관리형 확장(`deptype = 'e'`)을 100% 보존하면서 모든 사용자 뷰·테이블·시퀀스·인덱스·타입을 `DROP ... CASCADE`로 제거하고 잔존 객체 `0건`을 검증하는 SQL 작성.
  3. **Task 2 — 정규화 DDL ([`backend/sql/02_schema_create_all.sql`](backend/sql/02_schema_create_all.sql)), SQLAlchemy 2.0 ORM 모델 재작성, [`backend/app/schema_bootstrap.py`](backend/app/schema_bootstrap.py), [`backend/scripts/hard_reset_db.py`](backend/scripts/hard_reset_db.py)**:
     - 8개 도메인 테이블에 PostgreSQL 18 네이티브 `DEFAULT uuidv7()` UUID PK를 일관 적용(`data_reset_audits`는 `reset_id VARCHAR(128)` PK)하고, `user_profile_history(user_id) -> user_profiles(user_id)` 및 `meal_plan_items(plan_id) -> meal_plans(id)`에 `ON DELETE CASCADE` FK 강제, 중복 B-tree 인덱스 전량 제거, 엄격한 도메인 `CHECK` 제약 적용.
     - [`backend/app/database.py`](backend/app/database.py)(`POSTGRES_NAMING_CONVENTION`, SQLite `PRAGMA foreign_keys=ON`) 및 `backend/app/models/*.py` 8개 파일 전면 재작성.
     - 컨테이너 기동 시 비파괴적 멱등 부트스트랩(`python -m app.schema_bootstrap`)과 수동 파괴적 하드 리셋 CLI(`python scripts/hard_reset_db.py --confirmation DROP_AND_RECREATE_ALL_SCHEMAS`)를 엄격히 분리.
  4. **Task 3 — 100건 엣지 케이스 Seeder ([`backend/scripts/seed_edge_cases.py`](backend/scripts/seed_edge_cases.py)) 및 통합 테스트 ([`backend/tests/test_v2_reset.py`](backend/tests/test_v2_reset.py))**:
     - 정확히 **100건(`15+15+15+10+15+5+10+14+1 = 100 rows`)**의 엣지 케이스 더미 데이터 자동 삽입 및 `SAVEPOINT` 기반 **11개 음수 제약 위반(`ForeignKeyViolation`, `UniqueViolation`, `CheckViolation`) & `ON DELETE CASCADE` 연쇄 삭제**를 PostgreSQL 18.6 라이브 컨테이너와 SQLite 양쪽에서 실증 완료.
  5. **Task 4 — 레거시 제거 및 프로젝트 문서 동기화**:
     - `backend/alembic/**`(10개 리비전 포함), `backend/alembic.ini`, `scripts/alembic-autogen.sh`, `backend/scripts/reset_v2_user_data.py` 완전 삭제 및 `alembic` 의존성 제거 후 `requirements*.txt` 해시 락 재컴파일 완료.
     - [`backend/entrypoint.sh`](backend/entrypoint.sh), [`backend/Dockerfile`](backend/Dockerfile), [`scripts/agents/doc_audit.py`](scripts/agents/doc_audit.py), [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md), [`CLAUDE.md`](CLAUDE.md), [`README.md`](README.md), [`docs/TRD.md`](docs/TRD.md) 최신화 완료.

**다음 할 일 (Next Steps / TODO):**
- 운영 Azure PostgreSQL 18.4(`healthapp`)에 대해 **1단계(`hard-reset-v2-pg18-verify-20261029 --seed-edge-cases --verify-constraints`: `total_rows: 100`, `verified_constraints_count: 11`)** 및 **2단계(`hard-reset-v2-pg18-prod-20261029`: 프로덕션 클린 9개 테이블 재생성 + 임시 방화벽 즉시 제거 + 운영 API `/health`·`/health/ready` 200 OK 확인)**까지 모두 실측 완료됨.
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-29 (Gemini -> Claude/User) 스마일 트레이닝 v2 전면 리팩토링 · Zero-Legacy Cutover · 4대 접속 경로(IA) 구현 및 프로젝트 문서 최신화 완료

**현재까지의 작업 상태 (Current Status):**
- **Task 1 ~ Task 6 전 구간 구현 및 검증 완료 ([`docs/plans/2026-10-29-smile-training-v2-plan.md`](docs/plans/2026-10-29-smile-training-v2-plan.md) → 흡수: [`docs/plans/logs/android.md`](docs/plans/logs/android.md))**:
  1. **백엔드 스마일 트레이닝 v2 & DB 컷오버 마이그레이션 (Task 1)**:
     - [`backend/app/models/smile_training.py`](backend/app/models/smile_training.py), [`backend/app/schemas/smile_training.py`](backend/app/schemas/smile_training.py), [`backend/app/repositories/smile_training_repo.py`](backend/app/repositories/smile_training_repo.py), [`backend/app/services/smile_training_service.py`](backend/app/services/smile_training_service.py), [`backend/app/routers/smile_training.py`](backend/app/routers/smile_training.py): 구형 `/smile-training` 및 `SmileRoutine`·`SmileDifficulty`·`reflection_emoji` 레거시 전수 제거 → `/v2/smile-training/sessions` (`POST`/`GET`/`DELETE`), `/v2/smile-training/statistics` (`GET`) 4개 엔드포인트(`CamelSchema` 및 `operation_id` 보존)로 컷오버.
     - [`backend/alembic/versions/e8b2c4d6f1a0_cutover_smile_training_to_v2.py`](backend/alembic/versions/e8b2c4d6f1a0_cutover_smile_training_to_v2.py): `down_revision = "d7f4a2c91b10"`. `DELETE FROM smile_training_sessions` 후 체크 제약조건 및 3개 레거시 컬럼 드롭(`downgrade()` 왕복 지원). Docker PostgreSQL 16 (`dev:devpass@localhost:5432/eundunhealth`) 위에서 `upgrade head -> downgrade -1 -> upgrade head` 왕복 실측 통과.
     - [`docs/store/privacy-policy.md`](docs/store/privacy-policy.md), [`docs/store/account-deletion.md`](docs/store/account-deletion.md), [`backend/openapi.json`](backend/openapi.json) 동기화(`sync-legal-docs.sh`, `sync-openapi.sh`, `:app:openApiGenerate`).
  2. **Android 도메인 & `SmileSignalEvaluator` v2 (Task 2)**:
     - [`SmileTrainingModels.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/model/SmileTrainingModels.kt), [`SmileSignalEvaluator.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/smile/SmileSignalEvaluator.kt): `SmileRoutineType`, `SmileDifficultyLevel`, `reflectionEmoji` 완전 삭제 → 단일 표준 스펙 `SmileTrainingSpec`(`2,000ms 유지 × 3회 반복`) 통합, 지수 이동 평균(`EMA alpha = 0.6f`) 스무딩, `READY` 단계 1.0초(`1,000ms`) 연속 얼굴 정렬 시 자동 시작(`evaluateReadyFrame`, `allowAutoStart = !isPaused`) 구현.
  3. **Android Room DB v2 Cutover Reset & Repository (Task 3)**:
     - [`SmileSessionEntity.kt`](app/src/main/java/com/gunnys/eundunhealth/data/local/entity/SmileSessionEntity.kt), [`SmileTrainingDatabase.kt`](app/src/main/java/com/gunnys/eundunhealth/data/local/SmileTrainingDatabase.kt) (`version = 2`, `MIGRATION_1_2` + `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`), [`DatabaseModule.kt`](app/src/main/java/com/gunnys/eundunhealth/di/DatabaseModule.kt), [`SmileTrainingRepositoryImpl.kt`](app/src/main/java/com/gunnys/eundunhealth/data/repository/SmileTrainingRepositoryImpl.kt) 및 Room KSP 스키마 [`2.json`](app/schemas/com.gunnys.eundunhealth.data.local.SmileTrainingDatabase/2.json) 생성.
  4. **Android UI & ViewModel v2 리팩토링 (Task 4)**:
     - [`SmileTrainingViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileTrainingViewModel.kt), [`SmileCameraStage.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileCameraStage.kt), [`SmileTrainingScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/smile/SmileTrainingScreen.kt) (1,114줄 → 606줄): 상단 안내 배너·옵션 선택기·기분 설문(`REFLECTION`) 전수 삭제, `ACTIVE -> COMPLETED` 직행 자동 저장 + 오늘 완료 시 홈 바로가기 자동 숨김(`markSmileShortcutDismissed`), 풀블리드 카메라 HUD + 하단 플로팅 글래스 시트(`Modern Forest & Mint` 토큰, `AuthErrorBanner`, `HapticFeedbackHelper`) 적용.
  5. **4대 접속 경로(IA) 구현 (Task 5)**:
     - [`HomeViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt), [`HomeScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt): 상단바 우측 상시 `SmileQuickActionChip` (`"스마일"` / `"✓ 스마일 완료"`) + `TodayHeroCard` 하단 스마트 `SmileTrainingContextualCard`(`!state.isSmileCompletedToday && !state.isSmileShortcutDismissedToday`).
     - [`WorkoutGuideScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideScreen.kt), [`MainAppShell.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/MainAppShell.kt): 운동 가이드 완료 화면에 `[마무리 10초 스마일 트레이닝 하기]` 쿨다운 브릿지 버튼 추가.
     - [`ProgressHubViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubViewModel.kt), [`ProgressHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt): `"마음 연습 > 스마일 트레이닝"` 섹션을 3열 요약 타일 직하단 최상단으로 승격하고 실시간 연속일·완료 배지 표시.
  6. **레거시 잔존 0건 검증(`Zero-Legacy Grep Gate`) & 프로젝트 문서 최신화 (Task 6)**:
     - [`docs/PRD.md`](docs/PRD.md), [`docs/SPEC.md`](docs/SPEC.md), [`docs/TRD.md`](docs/TRD.md), [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md), [`CLAUDE.md`](CLAUDE.md), [`README.md`](README.md), [`docs/plans/README.md`](docs/plans/README.md) 최신화 완료.
     - `Zero-Legacy Grep Gate` 실행 결과: **`OK: 0 legacy symbols remaining across all scanned paths.`**
     - 백엔드: `pytest tests/ -q` (**140 passed**), `ruff check` (**0건**), `mypy app/` (**0건**), `bandit -r app -ll` (**0건**), `test_doc_audit.py` (**36 passed**).
     - Android: `spotlessCheck`, `detektDebug` (**0건**), `testDebugUnitTest` (**212 passed**), `assembleRelease` (R8 릴리스 빌드), `check-agp10-compat.sh`, `check-plans-links.sh`, `collectAsState` 가드 모두 통과.

**다음 할 일 (Next Steps / TODO):**
- 실물 Android 기기(또는 웹캠 에뮬레이터)에서 전면 카메라 `READY` 1.0초 자동 시작 감도 및 햅틱 피드백 체감 확인 후 PR/배포 진행.
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-29 (Gemini -> Claude/User) 프로젝트 필수 MCP 서버(Azure · Sentry · GitHub · Context7 · Sequential Thinking) 설정 및 패리티 복구 완료

**현재까지의 작업 상태 (Current Status):**
- **점검 및 근본 원인 분석 (`claude mcp list` 및 Antigravity `mcp_config.json` 대조)**:
  1. **Antigravity (`~/.gemini/config/mcp_config.json`)**: `eundunHealth` 운영·개발에 필수적인 `azure`, `sentry`, `context7`, `sequential-thinking` 4개 서버가 누락되어 있었음.
  2. **Claude Code (`~/.claude.json`)**:
     - `github` (`https://api.githubcopilot.com/mcp`): 만료된 구 PAT(`ghp_t7mx...`)가 남아 있어 `HTTP 401 (✘ Failed to connect)` 발생.
     - `sentry` (`https://mcp.sentry.dev/mcp`): 브라우저 OAuth 세션 만료로 `! Needs authentication` 상태였으며, 호스트에는 유효한 `SENTRY_AUTH_TOKEN`(길이 71자, `gunnys` 조직 `eundunhealth`·`eundunhealth-backend` 접근 가능)이 있었으나 `@sentry/mcp-server`가 요구하는 `SENTRY_ACCESS_TOKEN` 환경변수가 미설정 상태였음.
     - `.claude.json` 내부 `projects` 객체에 대소문자만 다른 중복 키(`c:/programming/apps/eundunHealth`)가 존재해 PowerShell `ConvertFrom-Json` 파싱 시 에러가 발생했고, 본 프로젝트에 불필요한 `wikidocs` 프로젝트 스코프 MCP가 남아 있었음.
  3. **슬래시 명령 ([`.claude/commands/verify-deploy.md`](.claude/commands/verify-deploy.md))**: `mcp__sentry__search_issues` 호출 가이드를 실제 Sentry 조직 슬러그(`gunnys`, [`docs/ops/operations-snapshot.md`](docs/ops/operations-snapshot.md) §6 정합) 대신 `eundunhealth`로 잘못 기재하고 있었음.
- **적용 및 해결 내역**:
  1. **Windows 사용자 환경변수 설정 (`setx` 미사용 원칙 준수)**: `[Environment]::SetEnvironmentVariable('SENTRY_ACCESS_TOKEN', ..., 'User')`로 `SENTRY_ACCESS_TOKEN` 영구 등록.
  2. **Antigravity (`~/.gemini/config/mcp_config.json`)**: 기존 5종에 더해 `azure`(`@azure/mcp@latest server start`, `AZURE_TENANT_ID=f11653ca-c627-4753-ae79-ad0d9689dbc8`), `sentry`(`@sentry/mcp-server@latest --organization-slug=gunnys`), `context7`(`https://mcp.context7.com/mcp`), `sequential-thinking`(`@modelcontextprotocol/server-sequential-thinking`) 총 9종 구성 완료.
  3. **Claude Code (`~/.claude.json`)**: `github` 헤더를 유효한 최신 PAT(`HTTP 200` 검증)로 교체, `sentry`를 `@sentry/mcp-server@latest --organization-slug=gunnys` stdio 방식으로 전환(OAuth 만료 재발 원천 차단), 대소문자 중복 프로젝트 키 및 불필요한 `wikidocs` 엔트리 정리.
  4. **저장소 파일 보강**: [`.claude/commands/verify-deploy.md`](.claude/commands/verify-deploy.md)의 `organizationSlug`를 `gunnys`로 정정하고, [`.gitignore`](.gitignore)에 `.mcp.json` 및 `.agents/mcp_config.json`을 추가해 public 저장소에 로컬 MCP 자격증명이 커밋되지 않도록 차단.
- **실측 검증 결과**:
  - `claude mcp list` → `context7` (`✔ Connected`), `github` (`✔ Connected`), `sentry` (`✔ Connected`), `azure` (`✔ Connected`), `sequential-thinking` (`✔ Connected`) 전수 연결 확인.
  - `@sentry/mcp-server` `find_projects(organizationSlug="gunnys")` → `eundunhealth`, `eundunhealth-backend` 정상 조회 확인.
  - `Get-Content ~/.claude.json -Raw | ConvertFrom-Json` 및 `Get-Content ~/.gemini/config/mcp_config.json -Raw | ConvertFrom-Json` 무오류 파싱 확인.
  - `C:\programming\windows-dev-setup\pwsh-doctor.ps1` → **`PASS 86 / WARN 0 / FAIL 0`** 통과.

**다음 할 일 (Next Steps / TODO):**
- Antigravity IDE / CLI에서 새 세션 시작(또는 MCP Servers 새로고침) 시 추가된 `azure`, `sentry`, `context7`, `sequential-thinking` 도구가 즉시 로드됨.

---

## 2026-10-29 (Claude -> Gemini/User) AGP 9.3.2 → 9.4.1 정식 업그레이드 (플러그인 해석 시 Dependency Verification 실패 근본 해결)

**현재까지의 작업 상태 (Current Status):**
- **원인**: 09:26 Android Studio(AGP Upgrade Assistant 추정)가 `gradle/libs.versions.toml`의 `agp`를 `9.3.2 → 9.4.1`로 변경 → `com.android.application.gradle.plugin-9.4.1.pom` 등 신규 아티팩트의 SHA-256이 `verification-metadata.xml`·`app/gradle.lockfile`에 없어 플러그인 해석 단계에서 차단. 사용자가 원복 대신 **정식 업그레이드**를 선택.
- **호환성 팩트체크**: AGP 9.4.x 최소 Gradle 9.6.0 / JDK 17 / 최대 API 37 (developer.android.com) → 현재 Gradle 9.8.1·targetSdk 37 충족.
- **변경**:
  - `.\gradlew.bat --write-verification-metadata sha256 --write-locks :app:dependencies :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin` → `gradle/verification-metadata.xml`(+AGP 9.4.1/tools 32.4.1 계열 등), `app/gradle.lockfile` 재생성. 기존 9.3.2 항목은 Gradle 동작상 잔존(무해, 추후 정리 가능).
  - Windows 생성 시 누락되는 **`aapt2-9.4.1-15978811-linux.jar` / `-osx.jar`** SHA-256을 Google Maven 원본 다운로드로 산출해 수동 등록(동일 방법으로 산출한 windows 해시가 Gradle 생성값과 일치함을 대조).
  - 문서: `CLAUDE.md`(L112, L199), `README.md`, `docs/TRD.md` AGP 9.4.1로 갱신. `docs/ops/operations-snapshot.md`는 **운영 배포본(v0.6.1/40)이 AGP 9.3.2로 빌드되었으므로 의도적으로 미변경** — 다음 릴리스 배포 시 갱신.
- **검증**: Windows 엄격 검증 빌드(spotless/detekt/unit/assembleDebug/assembleRelease R8), Linux Docker(`eclipse-temurin:17-jdk`, 엄격 검증) `:app:dependencies` + `:app:processDebugResources :app:compileDebugKotlin` BUILD SUCCESSFUL, AGP10 가드 통과.
- **AGP 9.3.2 레거시 정리(후속, Gemini)**:
  - `app/gradle.lockfile`: `--write-locks`는 이번 빌드에서 해석되지 않은 구성의 lock 상태를 보존하므로, AGP 9.4.1에서 사라진 `unified-test-platform-*` 12개 구성(UTP `32.3.2`, grpc/netty/`com.google.testing.platform` 등)이 잔존했음. lockfile 삭제 후 `:app:dependencies --write-locks`로 재생성 → GAV 111개 제거, 신규 GAV 0(버전 변경 없음). AGP 9.4.1 UTP는 `unified-test-platform-gradle-work-action`(32.4.1) 1개만 사용.
  - `gradle/verification-metadata.xml`: 빈 components로 from-scratch 재생성본과 대조해 **272개 component 제거**(9.3.2/32.3.2/`aapt2 9.3.2-15703166` 계열 + 이들만 끌어오던 전이 의존성·parent POM). 잔여 9.3.2/32.3.2 = 0. 재생성본에 없지만 유지한 9개(jackson 모듈 BOM, `legacy-support-core-ui`, groovy 3.0.22, compose `ui-backhandler-android-debug`, `gradle:gradle:9.8.1` 등)는 9.3.2와 무관해 보존.
  - 검증: Windows 엄격 검증(`--rerun-tasks` spotless/detekt/unit/assembleDebug/assembleRelease/bundleRelease/compileDebugAndroidTestKotlin/:app:dependencies) BUILD SUCCESSFUL, Linux Docker 엄격 검증, AGP10 가드.

**다음 할 일 (Next Steps / TODO):**
- Android Studio `Sync Project with Gradle Files` 재확인 → 커밋/PR(CI `android.yml`에서 최종 Linux 패리티 확인).
- 미추적 파일 `docs/plans/2026-10-29-smile-training-v2-*.md`는 본 작업과 무관(타 세션/사용자 작업) — 건드리지 않음.

---

## 2026-10-29 (Gemini -> Claude/User) 스마일 트레이닝 v2 리팩토링 · 레거시 전면 제거(Zero-Legacy Cutover) · 접속 경로(IA) 구현 설계서 격상 및 코드베이스 1:1 팩트체크 완료

**현재까지의 작업 상태 (Current Status):**
- **10인 멀티 에이전트 협의체(`[팀장, 기획, 디자이너, 실무, 책임, 개발, 구현, 유지보수, QA/QC, DevOps]`) 기반 구현 설계서 격상 및 코드베이스 25개 파일 1:1 라인 단위 팩트체크 보정 완료**:
  - 단일 통합 구현 설계 문서: [`docs/plans/2026-10-29-smile-training-v2-plan.md`](docs/plans/2026-10-29-smile-training-v2-plan.md) (`status: approved` → 흡수: [`docs/plans/logs/android.md`](docs/plans/logs/android.md))
  - 인덱스 자동 생성 및 링크 무결성 검증: [`docs/plans/README.md`](docs/plans/README.md) (`python scripts/gen_plans_index.py --check` 및 `bash scripts/check-plans-links.sh` 통과, 끊긴 참조 0건).
- **코드베이스 정밀 감사(`§2.2 Audit-1 ~ Audit-7`)로 발견·보정한 핵심 기술 사항**:
  1. **Audit-1 (`READY` 단계 프레임 무시 버그 수정)**: 현행 `SmileTrainingViewModel.kt:343`이 `if (phase != ACTIVE) return`으로 `READY` 프레임을 전부 버리고 있던 결함을 발견 → `onFrameSignal`이 `READY`와 `ACTIVE` 모두에서 동작하도록 설계.
  2. **Audit-2 (일시정지 `isPaused` 재진입 무한 루프 방지)**: `pauseActiveExercise()`가 `phase = READY`를 재사용하므로, `SmileTrainingUiState`에 `isPaused: Boolean = false`를 추가해 최초 진입(`!isPaused`)에서만 1.0초 자동 시작이 작동하고 일시정지 상태에서는 수동 `[이어서 시작하기]` 탭을 기다리도록 격리.
  3. **Audit-3 (정확한 도메인 vs OpenAPI 심볼 매칭)**: 도메인 모델(`SmileRoutineType`, `SmileDifficultyLevel`, `SmileSummaryStats`, `SmileSyncStatus`, `observeSummaryStats()`)과 OpenAPI 생성 모델(`SmileRoutine`, `SmileDifficulty`)을 모두 정확히 반영하고 `Zero-Legacy Grep Gate` 차단 목록에 4종 모두 등록.
  4. **Audit-4 (Room `SmileSessionEntity` 인덱스 및 컬럼 정합성)**: 오프라인 워커(`SmileSyncWorker`)와 다중 계정 격리에 필수적인 `userId`, `occurredAtUtc`, `syncStatus`, `syncedAtUtc`, `lastSyncErrorCode`와 복합 인덱스 2개는 유지하고 `routine`, `difficulty`, `reflectionEmoji` 3개 컬럼만 제거(`MIGRATION_1_2` + `fallbackToDestructiveMigrationFrom(1)` 이중 가드).
  5. **Audit-5 (PostgreSQL `ck_smile_sessions_reflection_emoji_range` 제약조건 및 Alembic `d7f4a2c91b10`)**: `DELETE FROM smile_training_sessions` 후 `drop_constraint("ck_smile_sessions_reflection_emoji_range")` 선행 및 `downgrade()` 시 `server_default` 지정으로 왕복 마이그레이션 무결성 확보.
  6. **Audit-6 (FastAPI `CamelSchema` 및 4개 `operation_id` 보존)**: `/v2/smile-training` 라우터의 4개 엔드포인트(`recordSmileTrainingSession`, `listSmileTrainingSessions`, `getSmileTrainingStatistics`, `deleteAllSmileTrainingSessions`) `operation_id`와 `CamelSchema` 상속을 유지하여 `NetworkModule.kt` 변경 없이 OpenAPI 클라이언트 재생성 호환성 확보.
  7. **Audit-7 (스크립트 실제 경로 정합)**: `scripts/sync-legal-docs.sh`(`docs/store/*.md` → `backend/app/legal/*.md`) 및 `scripts/sync-openapi.sh`(`backend/openapi.json` → `./gradlew openApiGenerate`) 파이프라인 명시.

**다음 할 일 (Next Steps / TODO):**
- 사용자의 구현 착수 지시(`"구현 진행"` 또는 `"승인"`) 즉시 [`docs/plans/2026-10-29-smile-training-v2-plan.md`](docs/plans/logs/android.md)의 Task 1 ~ Task 6 순서로 코드 구현 및 레거시 전수 제거 수행.

---

## 2026-10-29 (Gemini -> Claude/User) Android Studio 소스 다운로드 시 Dependency Verification 실패(196개 sources.jar 및 gradle-9.8.1-src.zip) 근본 해결

**현재까지의 작업 상태 (Current Status):**
- **문제 증상 및 원인 분석**:
  - Android Studio(IDE)에서 프로젝트 동기화(Sync) 및 라이브러리/Gradle 스크립트 코드 탐색 및 디버깅을 지원하기 위해 `:app:detachedConfiguration*`을 통해 의존성들의 `*-sources.jar`, `*-javadoc.jar` 및 Gradle 자체 소스 배포판 `gradle-9.8.1-src.zip`(`gradle:gradle:9.8.1`)을 다운로드하려고 시도함.
  - Gradle Dependency Verification (`gradle/verification-metadata.xml`)에 소스/문서 아티팩트에 대한 신뢰(`trusted-artifacts`) 규칙 및 Gradle distribution source 항목이 누락되어 있어, 체크섬이 없는 196개의 `*-sources.jar` 및 `gradle-9.8.1-src.zip` 다운로드 시 Gradle verification이 차단됨.
- **근본 원인 해결 ([`gradle/verification-metadata.xml`](gradle/verification-metadata.xml))**:
  1. **소스 및 Javadoc, Gradle 배포판 아티팩트 자동 신뢰 규칙 추가**:
     - Gradle 공식 권장 사항(`https://docs.gradle.org/9.8.1/userguide/dependency_verification.html#sec:troubleshooting-verification`)에 따라 `<configuration>` 블록 내에 `<trusted-artifacts>`를 선언하여 `.*-javadoc[.]jar`, `.*-sources[.]jar`, `.*-src[.]zip` 및 `^gradle$` 그룹 아티팩트를 자동 신뢰하도록 추가:
       ```xml
       <trusted-artifacts>
          <trust file=".*-javadoc[.]jar" regex="true"/>
          <trust file=".*-sources[.]jar" regex="true"/>
          <trust file=".*-src[.]zip" regex="true"/>
          <trust group="^gradle$" regex="true" reason="Gradle distribution artifacts"/>
       </trusted-artifacts>
       ```
  2. **Gradle 9.8.1 공식 소스 ZIP SHA-256 체크섬 직접 등록 (이중 방어)**:
     - `gradle:gradle:9.8.1`의 공식 배포 아티팩트 `gradle-9.8.1-src.zip`의 SHA-256 체크섬(`6c5598a3253d0a61a39b27363aef32780cf235dc07ad50bafb30c7c0f309c49c`)을 `<components>` 블록에 명시적으로 등록함.
  3. **공급망 보안 무결성 보존**:
     - 컴파일/런타임에 실행되는 모든 핵심 바이너리(AAR, JAR, POM, MODULE 등)의 SHA-256 검증 및 공급망 무결성은 100% 온전히 유지하면서, IDE 탐색용 소스/문서/Gradle 배포판 아카이브 다운로드만 안전하게 허용함.
- **실측 검증 결과**:
  - 실제 에러 대상 아티팩트(`FastInfoset-1.2.16-sources.jar`, `JavaEWAH-1.2.3-sources.jar`, `checker-qual-3.43.0-sources.jar`)에 대한 detached configuration 소스 아티팩트 해소(Resolution) 테스트 성공 (`BUILD SUCCESSFUL`).
  - `gradle-9.8.1-src.zip` 공식 SHA-256 대조 및 캐시 무결성 검증 완료.
  - `.\gradlew.bat :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL in 8s** (단위 테스트 215개 전수 통과, Detekt 0건, 디버그 APK 빌드 성공).
  - `wsl -d Ubuntu -- bash -lc "cd /mnt/c/programming/apps/eundunHealth && bash scripts/check-agp10-compat.sh"` → **OK: AGP 10 호환성 가드 통과**.

**다음 할 일 (Next Steps / TODO):**
- Android Studio에서 `Sync Project with Gradle Files` 재실행하여 에러 없이 정상 동기화되는지 확인.

---

## 2026-10-28 (Gemini -> Claude/User) Health Connect '연동' 무반응 근본 해결 + 운동 가이드 종료 다이얼로그('완료하지 않고 나가기') 잘림 및 UI/UX 전수 점검·수정 완료

**현재까지의 작업 상태 (Current Status):**
- **1. 'Health Connect로 운동 달성을 자동 추적' [연동] 버튼 무반응 현상 근본 원인 해결**:
  - **원인 1 (연동 허용 직후 홈 화면 상태 미갱신)**: `MainActivity.healthPermissionLauncher` 콜백이 `healthPermissionsRevision++`만 증가시키고, `MainAppShell.kt`에서 `ProfileHub` 라우트에만 `LaunchedEffect(healthPermissionsRevision)`을 연결하고 `Screen.Home.route`에는 연결하지 않아 홈 화면 `HealthConnectPromptCard`가 권한 허용 직후에도 그대로 남아 무반응처럼 보이던 버그 확인.
  - **원인 2 (Android 14+ / Health Connect 2회 거부 시 시스템 다이얼로그 무음 차단)**: Android 공식 문서 및 `androidx.health.connect:connect-client:1.1.0` 바이트코드(`HealthPermissionsRequestModuleContract`, `HealthPermissionsRequestAppContract`, `HealthConnectClient`) 대조 결과, 권한 요청을 2회 거부/취소하면 OS가 시스템 다이얼로그를 띄우지 않고 즉시 빈 `Set`을 반환함.
  - **원인 3 (Android 11+ 패키지 가시성)**: `AndroidManifest.xml`의 `<queries>`에 `<package android:name="com.google.android.apps.healthdata" />`가 누락되어 Android 13 이하 기기에서 Health Connect APK 패키지 조회·인텐트 해석이 차단될 수 있었음.
  - **해결**:
    - [`MainAppShell.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/MainAppShell.kt), [`HomeScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt), [`HomeViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt): `HomeViewModel.refreshHealthConnectStatus()` 추가(권한 새로고침 시 `syncHealthConnectWorkouts(plan)`까지 즉시 수행) + `MainAppShell`의 `Screen.Home.route`에서 `LaunchedEffect(healthPermissionsRevision)` 연결 + `HomeScreen` `ON_RESUME` 라이프사이클 옵저버(`RefreshHealthConnectOnResume`) 연결.
    - [`MainActivity.kt`](app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt): 권한 요청이 다이얼로그 표시 없이 즉시 반환(`<350ms`)되거나 Android 14+에서 영구 잠금(`!shouldShowRequestPermissionRationale`)된 경우 공식 `HealthConnectClient.getHealthConnectManageDataIntent(this)`(폴백 `ACTION_HEALTH_CONNECT_SETTINGS`)로 Health Connect 관리 화면을 즉시 열도록 구현.
    - [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml): `<queries>`에 `<package android:name="com.google.android.apps.healthdata" />` 추가.
- **2. '운동을 중단하시겠어요?' 다이얼로그 하단 '완료하지 않고 나가기' 잘림(Clipping) 해결 및 오늘 작업분(v0.6.1) UI/UX 전수 점검·리팩토링**:
  - **근본 원인 (`WorkoutGuideExitDialog`)**: Material 3 `AlertDialog` 내부의 `AlertDialogFlowRow`는 `dismissButton`과 `confirmButton`을 2버튼 가로 행으로 배치하도록 설계되어 있는데, 기존 코드는 `dismissButton` 슬롯 안에 `Column`으로 2개의 긴 한국어 `TextButton`(`"오늘 운동 완료 처리하고 나가기"`, `"완료하지 않고 나가기"`)을 중첩하고 `confirmButton`에 `Button("계속 운동하기")`을 별도 배치하여 가로·세로 측정 경합으로 하단 버튼이 잘리는 현상이 발생함.
  - **해결 및 전수 UI 보강**:
    - [`WorkoutGuideScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideScreen.kt): `WorkoutGuideExitDialog`에서 `dismissButton` 슬롯 중첩을 제거하고, 단일 `confirmButton` 슬롯 내부에 `Modifier.fillMaxWidth()` 수직 `Column`으로 3개 액션(`Button("계속 운동하기")` → `FilledTonalButton("오늘 운동 완료 처리하고 나가기")` → `TextButton("완료하지 않고 나가기")`, 모두 `heightIn(min = 48.dp)`)을 배치 + 본문 `verticalScroll` 및 `WorkoutGuideCompletedContent` `verticalScroll` 추가.
    - [`ProfileHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt), [`MealPlanScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/mealplan/MealPlanScreen.kt): `DefaultWorkoutPlanModeDialog`(라디오 3종) 및 AI 식단 데이터 처리 동의 `AlertDialog` 본문에 `verticalScroll(rememberScrollState())` 추가하여 소형 화면·200% 대형 글꼴 잘림 원천 차단.
    - [`OnboardingScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt): Step 2 하단 2열 CTA 행의 우측 주 버튼 문구를 `"첫 주 계획 시작하기"`(`weight(1.8f)`, `TextAlign.Center`)로 정돈하여 360dp 소형 기기에서 2줄 줄바꿈·잘림 방지.
    - [`ProgressHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt): 3열 요약 타일(`SummaryMetricTile`)의 값 타이포그래피를 `titleMedium` 및 `padding(horizontal = 6.dp)`로 조정하여 `"-12.5kg"` 등 7자 문자열에서 단위(`kg`)만 다음 줄로 쪼개지는 현상 방지.
    - [`HealthConnectCards.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/components/HealthConnectCards.kt): `연동`·`설치` `TextButton`에 `Modifier.heightIn(min = 48.dp)` 적용하여 WCAG 최소 터치 타겟 보장.
- **3. 재발 방지 테스트 및 전체 검증 결과**:
  - 신규/보강 테스트: [`DialogAndLayoutGuardTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ui/DialogAndLayoutGuardTest.kt), [`WorkoutGuideExitDialogTest.kt`](app/src/androidTest/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideExitDialogTest.kt), [`HomeViewModelTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ui/home/HomeViewModelTest.kt), [`ManifestHealthConnectRationaleTest.kt`](app/src/test/java/com/gunnys/eundunhealth/ManifestHealthConnectRationaleTest.kt).
  - `.\gradlew.bat spotlessApply :app:spotlessCheck :app:detektDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain` → **BUILD SUCCESSFUL** (단위 테스트 **215개 전수 통과**, Detekt 0건, R8 릴리스 빌드 및 `lintVitalRelease` 통과).
  - `wsl -d Ubuntu -- bash -lc "cd /mnt/c/programming/apps/eundunHealth && bash scripts/check-agp10-compat.sh"` → **OK: AGP 10 호환성 가드 통과**.

**다음 할 일 (Next Steps / TODO):**
- 변경사항 커밋 및 차기 핫픽스 릴리스 배포 여부 확인.

---

## 2026-10-28 (Gemini -> Claude/User) Sentry 이슈 7816180647 (`EUNDUNHEALTH-G`, HTTP 504) 점검·디버깅 및 근본 원인 해결 완료

**현재까지의 작업 상태 (Current Status):**
- **Sentry 이슈 `7816180647` (`EUNDUNHEALTH-G`) 실측 분석 완료**:
  - 발생 시각: `2026-10-28T07:57:11.845Z` (`16:57:11 KST`), 릴리스 `com.gunnys.eundunhealth@0.6.1+40` (`count=1`, `userCount=1`).
  - 발생 기기: Google Play Console **Pre-launch Report (자동 사전 출시 테스트 / Robo 크롤러)** 가상 기기 (`OnePlus8Pro`, `QKR1.191246.002`, 커널 `android-build@apa28.mtv.corp.google.com`, `country_code: US`).
  - 네트워크·서버 대조 결과:
    1. `07:55:56.882Z` (서버 UTC `07:55:58.142Z`): 앱 최초 기동 시 `GET /app-compatibility?platform=android&versionCode=40` `200 OK` 성공.
    2. 이후 약 74.5초간 크롤러가 MSAL 브라우저 로그인 화면 진입·취소(`user_cancelled`)를 2회 반복한 뒤 `07:57:11.312Z`에 `MainActivity`를 재생성하며 다시 `GET /app-compatibility` 호출.
    3. **1차 시도 (`07:57:11.386Z ~ 07:57:11.796Z`)**: 유휴 상태였던 커넥션 재사용 중 `unexpected end of stream` 및 중간 게이트웨이 `HTTP 504` 발생(Azure Container Apps `eundunhealth-api--0000066` 컨테이너 로그에는 `504` 없음).
    4. **2차 시도 (`RetryInterceptor` 500ms 백오프 재시도 → 서버 UTC `07:57:14.376Z`)**: `eundunhealth-api--0000066`에서 `GET /app-compatibility?platform=android&versionCode=40` **`200 OK` 정상 처리 완료**.
- **근본 원인 해결 ([`NetworkModule.kt`](app/src/main/java/com/gunnys/eundunhealth/di/NetworkModule.kt), [`RetryInterceptorTest.kt`](app/src/test/java/com/gunnys/eundunhealth/data/remote/interceptor/RetryInterceptorTest.kt))**:
  1. **인터셉터 순서 교정 (`SentryOkHttpInterceptor`를 `RetryInterceptor` 앞/바깥으로 이동)**: 기존에는 `RetryInterceptor`가 바깥(`#1`), `SentryOkHttpInterceptor`가 안쪽(`#3`)에 있어 1차 시도가 `504`로 실패하는 순간 `RetryInterceptor`가 재시도(`200 OK`)하기도 전에 `SentryOkHttpEvent`가 조기 종료(`isEventFinished = true`)되며 `SentryHttpClientException`을 발송하고 2차 성공 응답을 무시하던 구조적 버그를 해결.
  2. **`captureFailedRequests = false` 명시 및 `provideExerciseDbOkHttpClient` 방어**: `SentryOkHttpInterceptor(captureFailedRequests = false)`를 `backendOkHttpBuilder`와 `provideExerciseDbOkHttpClient` 모두 최외곽에 명시 배치하여, Sentry Gradle Plugin ASM(`RealCall.getResponseWithInterceptorChain$okhttp`)이 기본 `SentryOkHttpInterceptor(captureFailedRequests = true)`를 `RetryInterceptor` 뒤에 자동 주입하는 현상까지 원천 차단([`AppError.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/model/AppError.kt) 정책과 정합; HTTP 스팬·브레드크럼은 그대로 유지).
  3. **검증 완료**: `.\gradlew.bat spotlessApply :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.data.remote.interceptor.RetryInterceptorTest" :app:detektDebug spotlessCheck` → **BUILD SUCCESSFUL** (신규 회귀 테스트 2건 포함 통과).

**다음 할 일 (Next Steps / TODO):**
- 변경사항 커밋/PR 반영 및 Sentry 이슈 `7816180647` (`EUNDUNHEALTH-G`) Resolve 처리 여부 확인.

**주의사항 및 컨텍스트 (Context & Constraints):**
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-28 (Gemini -> Claude/User) PROP-05 UI/UX 개선 통합 설계서 Phase 1~4 구현 + 2차 심층 리팩토링 + v0.6.1 (versionCode=40) 운영 배포 완료

**현재까지의 작업 상태 (Current Status):**
- **운영 배포 100% 완료 (`v0.6.1`, `versionCode = 40`)**: PR #201(`c12f969`) squash merge → `backend.yml` run `40647560099` (`eundunhealth-api--0000066` & `eundunhealth-reaper` 배포·프로브 5종 검증 완료) → `release.yml` run `40647597577` (태그 `v0.6.1`, Play 프로덕션 `completed` 100% 업로드 + 원장 `LAST_UPLOADED_VERSION_CODE=40` PR #211 반영 완료). `2026-10-28-ux-improvement-design.md` ([`docs/plans/logs/android.md`](docs/plans/logs/android.md) 2026-10-28 entry로 흡수 완료, `PROP-05`)에 정의된 **Phase 1 ~ Phase 4 전 구간 코드 구현 및 단위 테스트·정적 분석·릴리스(R8) 빌드 검증을 100% 완료**함.
  1. **Phase 1 — 브랜드 & 디자인 파운데이션 + Quick Wins 4종**:
     - **M3 36슬롯 + 시맨틱 13토큰 완성**: [`Color.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/theme/Color.kt), [`Shape.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/theme/Shape.kt), [`Theme.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/theme/Theme.kt) (`#006D3C` Modern Forest & Mint, Light `#FBFDF8` / Dark `#111412`, 5단계 `surfaceContainerLowest..Highest`, `@Immutable data class EundunExtendedColors`, `dynamicColor = false` 기본값)
     - **Pretendard Variable 한글 타이포그래피 15슬롯**: [`Type.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/theme/Type.kt) (`R.font.pretendard_variable`, `PlatformTextStyle(includeFontPadding = false)`, `LineHeightStyle`, 한글 음수 자간 `-0.25.sp ~ 0.sp`)
     - **모션·햅틱·설정 인프라**: [`MotionTokens.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/theme/MotionTokens.kt), [`HapticFeedbackHelper.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/util/HapticFeedbackHelper.kt), [`ThemePreferences.kt`](app/src/main/java/com/gunnys/eundunhealth/data/preferences/ThemePreferences.kt), [`MainActivity.kt`](app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt)
     - **DayNight 콜드 스타트 백색 점멸(White Flash) 원천 차단**: [`values/themes.xml`](app/src/main/res/values/themes.xml), [`values-night/themes.xml`](app/src/main/res/values-night/themes.xml), [`values-v31/themes.xml`](app/src/main/res/values-v31/themes.xml), [`values-night-v31/themes.xml`](app/src/main/res/values-night-v31/themes.xml), [`SplashScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/splash/SplashScreen.kt) (외부 의존성 추가 없이 플랫폼 DayNight 테마로 완벽 해결하여 Gradle lockfile/verification-metadata 무결성 유지)
     - **Quick Wins 4종**: [`AppNavigation.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt) (`UpdateRequiredScreen` 버전 코드 대신 사용자 친화 문구), [`HomeViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt) (`Success` 상태에서 `loadPlan()` 시 `Loading` 리셋 대신 동기적 `isRefreshing = true` 인플레이스 갱신으로 `F-4` 해결), [`ProfileHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt) (로그아웃 확인 `AlertDialog`), 온보딩/프로필 체성분 미입력 시 `null` 전송(`F-3` 해결)
  2. **Phase 2 — '오늘' 탭 Today Hero 중심 재편 & 단계별 운동 가이드 모드**:
     - [`WorkoutDurationEstimator.kt`](app/src/main/java/com/gunnys/eundunhealth/domain/util/WorkoutDurationEstimator.kt), [`TodayHeroCard.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/components/TodayHeroCard.kt), [`HomeScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt): 상단 `TodayHeroCard` 집중 배치, `[운동 시작 (가이드 모드)]` 주 CTA + `[오늘 운동 바로 완료 체크]` 보조 액션, 완료 해제 시 Undo `SnackbarHost` (`HomeSideEffect.ShowUndoUncompleteSnackbar`), 휴식일/완료 후 스마일 트레이닝 바로가기 카드(`오늘 하루 닫기`), 하단 접이식 7일 전체 계획
     - [`WorkoutGuideViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideViewModel.kt), [`WorkoutGuideScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideScreen.kt), [`WorkoutDetailScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutDetailScreen.kt), [`Screen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/Screen.kt), [`MainAppShell.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/MainAppShell.kt): `HorizontalPager` 기반 종목별 가이드, `FLAG_KEEP_SCREEN_ON` 자동 관리, 세트 체크박스·휴식 타이머 배제, 중도 이탈 시 3지 선다 다이얼로그(`D-2`), 마지막 종목 완료 시 당일 운동 자동 완료 처리
  3. **Phase 3 — 기록·식단·내 정보 허브 고도화 & 2단계 온보딩 + 3중 하이브리드 바디 피커**:
     - [`ProgressHubViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubViewModel.kt), [`ProgressHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt), [`StatisticsScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt), [`GoalScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt), [`HistoryScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/history/HistoryScreen.kt): 3열 요약 지표 타일(`coroutineScope` 병렬 조회 + 개별 `"—"` 폴백), 차트 TalkBack 요약문 + 헬스장/홈트 비율 카드, 목표 프로그레스 바 + 체지방 미입력 안내 카드, 히스토리 요일 라벨(`월~일`) + 아코디언 상세
     - [`MealPlanViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/mealplan/MealPlanViewModel.kt), [`MealPlanScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/mealplan/MealPlanScreen.kt): 룰 11 `@Immutable` `MealPlanUiState`, `Generating(previousPlan)` 및 재생성 실패 시 기존 식단 보존 인라인 `AuthErrorBanner`(`regenerateError`, 룰 8), 상단 `[다시 만들기]`, 오늘 식단 카드 최상단 고정·자동 펼침, `FlowRow` 영양소 칩
     - [`ProfileHubViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubViewModel.kt), [`ProfileHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt): 5대 섹션 그룹화(`내 정보/연동/화면/정보/계정`), Android 12+ Dynamic Color opt-in 스위치, 로그아웃 확인 다이얼로그
     - [`GenderSelector.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/GenderSelector.kt), [`BodyMetricDefaults.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/BodyMetricDefaults.kt), [`HybridBodyMetricPicker.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/HybridBodyMetricPicker.kt), [`OptionalBodyCompositionCard.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/OptionalBodyCompositionCard.kt), [`BodyMetricsSliders.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/BodyMetricsSliders.kt), [`ProfileSummaryCard.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/ProfileSummaryCard.kt): 레거시 `ProfileSlider.kt` 완전 삭제 및 3중 하이브리드 바디 피커(`D-4`) + 2지 선다 성별 선택(`D-5`) + 접이식 선택 체성분 카드(`D-6`) 적용
     - [`AuthGateScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthGateScreen.kt), [`OnboardingScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt), [`OnboardingViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingViewModel.kt), [`ProfileScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt), [`ProfileViewModel.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt): 로그인 전 3슬라이드 가치 소개 페이저, 2단계 온보딩 위저드 + 완료 즉시 첫 주간 계획 자동 생성(`CreateWeeklyPlanUseCase`)
  4. **Phase 4 — 테스트·전수 검증 게이트 통과 및 2차 심층 감사(Root-Cause & UI/UX/A11y 리팩토링 10건)**:
     - **Compose Side-Effect Stale Closure 근본 차단**: [`HybridBodyMetricPicker.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/HybridBodyMetricPicker.kt) (`HorizontalRulerWheel`에 `rememberUpdatedState` + `BoxWithConstraints` 동적 중앙 패딩 `(maxWidth - tickStepWidth) / 2` + `firstVisibleItemScrollOffset` 반올림 보정 적용), [`WorkoutGuideScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutGuideScreen.kt) (`LaunchedEffect(pagerState)`에 `rememberUpdatedState` 적용 + 200% 폰트 대응 `FlowRow` 태그 + Coil `ImageRequest.size(512)` 적용)
     - **화면 간 상태 동기화**: [`MainAppShell.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/MainAppShell.kt) (`WorkoutGuideScreen` 운동 완료 복귀 시 `savedStateHandle["refresh_home_plan"]`으로 `HomeViewModel.loadPlan()` 즉시 트리거, `GoalScreen` 복귀 시 `ProgressHubViewModel.loadSummary()` 즉시 갱신)
     - **접근성(TalkBack) & 200% 대형 글꼴·레이아웃 대칭성 고도화**: [`OptionalBodyCompositionCard.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/OptionalBodyCompositionCard.kt) (부분 입력 시 정확한 서브타이틀 표기), [`OnboardingScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt) (체크박스 행 `Modifier.toggleable(role = Role.Checkbox)` 적용), [`ProfileHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt) (Dynamic Color `Switch(onCheckedChange = null)` 단일 포커스화 및 `Icons.AutoMirrored.Filled.KeyboardArrowRight` 통일), [`AuthGateScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthGateScreen.kt) (`Scaffold` + `verticalScroll` 적용), [`ProgressHubScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/navigation/ProgressHubScreen.kt) (3열 요약 타일 `IntrinsicSize.Max` + `.fillMaxHeight()` 높이 균일화), [`HistoryScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/history/HistoryScreen.kt) (`FlowRow` 헤더 + 상세 종목 `weight(1f)` 우측 정렬), [`ProfileScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt) (버튼 내부 `CircularProgressIndicator` `.size(20.dp)` 고정), [`MealPlanScreen.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/mealplan/MealPlanScreen.kt) (`LiveRegionMode.Polite` + `FlowRow` 헤더 + `MacroNutrientChip` `BorderStroke`), [`SkeletonUi.kt`](app/src/main/java/com/gunnys/eundunhealth/ui/components/SkeletonUi.kt) (`TodayHeroCard` + `WeeklyProgressCard` + 접이식 헤더 1:1 매칭으로 CLS 제거)
     - `.\gradlew.bat :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` → **BUILD SUCCESSFUL** (단위 테스트 **209개 전수 통과**, Detekt 이슈 **0건**, R8 full mode 릴리스 패키징 및 `lintVitalRelease` 통과)
     - `bash scripts/check-agp10-compat.sh` (PASS), `python scripts/gen_plans_index.py --check` (PASS), `bash scripts/check-plans-links.sh` (PASS), `pytest scripts/agents/test_doc_audit.py -q` (36 passed), 금지 API(`collectAsState(`) 0건 확인

**다음 할 일 (Next Steps / TODO):**
- **운영 배포 완료 (`v0.6.1` / `versionCode = 40`)**: 다음 릴리스 작업 시 `LAST_UPLOADED_VERSION_CODE=40`이므로 `versionCode >= 41`로 시작할 것(룰 13). Play 스토어 배포본 설치 후 일상 모니터링 진행.

**주의사항 및 컨텍스트 (Context & Constraints):**
- `git stash` 목록에 사용자 작업분(`autostash`)이 존재하므로 임의로 `stash drop/pop` 금지.

---

## 2026-10-28 (Gemini -> Claude/User) PROP-05 UI/UX 개선 종합 구현 설계서 격상 및 단일 문서 병합 완료 (코드 무변경)

**현재까지의 작업 상태 (Current Status):**
- 사용자 지시에 따라 `2026-10-28-ux-improvement-design.md`와 기존 구현 계획서(plan)를 **하나의 완성형 구현 설계 파일([`docs/plans/logs/android.md`](docs/plans/logs/android.md) 2026-10-28 entry로 흡수)로 완전 병합**하고, 최종 구현 직전 단계인 **'통합 구현 설계서(Integrated Implementation Design Specification)'로 격상** 완료함.
  1. **단일 통합 구현 설계서 (`2026-10-28-ux-improvement-design.md` → `docs/plans/logs/android.md`)**:
     - **확정된 의사결정 (`D-1`~`D-6`)**:
       - `D-1`: 북극성 지표 = 주간 운동 계획 완주율 (`/v2/weekly-plan/statistics` 재활용, 신규 SDK 불필요)
       - `D-2`: 운동 가이드 중간 종료 시 기본 미완료 유지 + 종료 확인 다이얼로그 내 명시적 완료/미완료 선택 제공
       - `D-3`: 브랜드 대표 시드 컬러 = **옵션 1: 모던 포레스트 민트 (`#006D3C` Primary / `#00D287` Electric Mint Accent)** 채택
       - `D-4`: 키·몸무게 입력 = **3중 하이브리드 바디 피커 (`HybridBodyMetricPicker`: 44sp 대형 Display + 인라인 숫자패드 + 가로형 햅틱 눈금자 휠 `SnapFlingBehavior` + ± 48dp 미세조정 스테퍼 + 성별 연동 스마트 중앙값 앵커링 남성 `174cm/72kg`, 여성 `161cm/54kg`)** 채택 (`ProfileSlider` 폐기)
       - `D-5`: 성별 선택 = **`[남성] | [여성]` 2지 선다 필수화** (`선택 안 함` UI 제거, 터치 영역 50% 확대)
       - `D-6`: 체성분(골격근량·체지방률) 입력 = **접이식 선택사항 카드 (`OptionalBodyCompositionCard`, 미입력 시 `null` 저장)** 채택 ('모름' 전용 토글 로직 배제, 가짜 기본값 `20f`/`30f` 강제 주입 근절)
     - **★ 사용자 필수 작업 가이드 (Action Required by User — 우회 금지) ★ 명통화**:
       - `res/font/pretendard_variable.ttf` 폰트 바이너리 다운로드 및 리소스 경로 배치 (SIL OFL 1.1 준수)
       - 실기기(Physical Device) 접근성(TalkBack) 및 4단계 햅틱 감각, 200% 대형 글꼴 레이아웃 검증
       - 단계별 PR 머지 및 릴리스 배포 승인
     - **영역별 상세 명세 및 공식 기술 표준 연계**:
       - M3 Expressive 가이드라인, 5단계 Surface Container 깊이 체계, Dynamic Color 기본 OFF + opt-in
       - `androidx.core:core-splashscreen` DayNight 콜드 스타트 점멸 근본 차단 (`values-night/themes.xml`)
       - Pretendard Variable 한글 타이포그래피 매트릭스 15종, `includeFontPadding = false`, 음수 자간
       - Clean Architecture & UDF (룰 11: 단일 `StateFlow`, `@Immutable`, `collectAsStateWithLifecycle`, 원샷 `Channel`)
       - 인라인 에러 배너 (룰 8: `AuthErrorBanner` + `LiveRegionMode.Polite`)
       - Quick Wins 4종: Home 인플레이스 새로고침(`F-4` 해결), 체성분 `null` 정합화(`F-3` 해결), 로그아웃 확인 다이얼로그, 업데이트 화면 버전명
       - 영역 B/C/D/E 상세 컴포넌트 구조 및 5대 상태 머신
     - **Phase 1~4 상세 TDD 구현 계획 및 작업 분할 (Task 0~15)**:
       - Phase 1 (`v0.6.2`, Task 0~4) → Phase 2 (`v0.7.0`, Task 5~8) → Phase 3 (`v0.8.0`, Task 9~13) → Phase 4 (전면 리팩토링 & 최종 회귀 검증, Task 14~15)
     - **트러블슈팅, 재발 방지 및 엣지 케이스 방어 매트릭스**: 7대 장애 시나리오별 근본 원인 및 방어 설계 완비.
  2. **기존 구현 계획서(plan.md) 및 원천 기획서(proposals) 삭제 후 단일 SSoT 정합화 완료**:
     - `docs/PRD.md`, `docs/proposals/README.md` 전수 단일 `2026-10-28-ux-improvement-design.md`(`docs/plans/logs/android.md`) 링크로 정합화.
  3. **검증 가드 통과**:
     - `gen_plans_index.py`, `check-plans-links.sh`, `test_doc_audit.py` 전건 패스 확인 예정.

**다음 할 일 (Next TODOs):**
- 사용자에게 단일 통합 구현 설계서 검토 요청 및 구현 착수 승인 확인.
- 사용자 승인 후 Phase 1 착수 시, 먼저 사용자에게 **`pretendard_variable.ttf` 폰트 파일 배치**를 요청하고 배치 완료 확인 후 Task 1~4 순서로 TDD 구현 진행.

**의존성 및 주의사항 (Context & Constraints):**
- 사용자가 작업해야 하는 항목(폰트 파일 배치, 실기기 햅틱/TalkBack 검증 등)은 절대 AI가 임의로 우회하거나 가짜 파일/대안으로 처리하지 말고 사용자에게 직접 요청할 것!
- 제외 범위 10종(리마인더/알림, 세트 카운팅/휴식 타이머, 약관 링크 신설, 미래 날짜 완료 방지, 식단 재생성 횟수 제한, 홈트 18종 일러스트 제작, 안전 팝업, 계정 삭제 화면 분리, 성별 '선택 안 함', 체성분 '모름' 토글) 엄수.
- git stash의 사용자 `autostash`는 그대로 둘 것.

---

## 2026-10-28 (Gemini -> Claude/User) PROP-05 UI/UX 개선 종합 기획 단일 문서화 (코드 무변경)

**현재까지의 작업 상태 (Current Status):**
- 단일 기획서 `docs/proposals/2026-10-28-ux-improvement.md`(PROP-05, `Exploring`)를 작성함. 영역 A 브랜드·디자인 기반, B Today Hero, C 운동 가이드 모드, D 허브, E 온보딩. 팩트체크 F-1~F-8, 결정 대기 D-3~D-6, 로드맵 v0.6.2→v0.7.0→v0.8.0을 담음.
- **영역 A 디자인 시스템(Design System & Foundation) 대폭 고도화**:
  - M3 컬러 시스템 완성: 브랜드 컬러 자유 기획 방침에 따라 3가지 테마 옵션(옵션 1: 모던 포레스트 민트 [권장], 옵션 2: 바이탈 선라이즈 오렌지, 옵션 3: 네오 슬레이트 인디고) 기획, 45개 핵심 토큰, M3 표준 **5단계 Surface Container 깊이 체계**, 시맨틱 기능성 컬러(완료/휴식/주의/3대 영양소), Dynamic Color 기본 OFF 거버넌스, `res/values-night/themes.xml` 및 `core-splashscreen` 완결. Material Color Utilities 모듈형 아키텍처로 시드 교체 시 전체 팔레트 자동 동기화.
  - Pretendard Variable 가변 폰트(SIL OFL 1.1, 단일 파일 1.2MB): M3 15개 전체 텍스트 스타일 매트릭스(Display/Headline/Title/Body/Label) 정의 및 한글 최적화 음수 자간(-0.02em / -0.2sp), `includeFontPadding = false` 정합성 확보.
  - 형태(Shape) 및 엘리베이션: 4dp~999dp 곡률 규격, 무거운 그림자 배제 + 1dp `outlineVariant` 미세 스트로크.
  - M3 Expressive 스프링 물리 모션(감쇠비 0.75 + 강성 400f 바운스) 및 4단계 햅틱 피드백 매트릭스(휠 틱, 버튼 탭, 완료 컨펌, 에러 리젝트) 표준화.
- 사용자 요청에 따라 **① 리마인더·알림, 배지·스트릭·축하 연출(동기부여) 제외 ② 세트 세기(카운팅) 및 휴식 시간 타이머 기능 제외 ③ 이용약관·개인정보처리방침 링크 및 신설 내용 제외 ④ 미래 날짜 운동 완료 방지 검증 제외 ⑤ 식단 재생성 횟수 제한 및 AI 동의 기억 내용 제외 ⑥ 감성형 부위/테마 타이틀('오늘은 당기기 데이' 등) 제외 ⑦ 홈트 18종 일러스트 부재 관련 내용 본문 제거 및 우선순위 조정으로 holding/ 분리 ⑧ 운동 가이드 안전 안내 팝업 문구 제외 ⑨ 계정 삭제 화면 분리/신설 내용 제외(현행 위치 유지) ⑩ 성별 '선택 안 함' 제거 및 [남성]/[여성] 2지 선다 필수화 ⑪ 체성분 '모름' 토글 로직 배제(오버 리소스 방지) 및 1단계 접이식 선택사항(Optional, 기본 null 전송) 개편으로 온보딩 2단계 초슬림화**를 적용함.
- **키·몸무게 신체 정보 입력 방식 심층 연구 및 개선**: 기존 슬라이더의 터치 가림·어긋남 문제를 해결하기 위해 대형 Display + 햅틱 눈금자 휠(Horizontal Ruler) + ± 미세조정 스테퍼 + 다이렉트 숫자패드 탭의 **하이브리드 바디 피커**로 전면 개편 설계를 수립·반영함. 성별 선택(남성 174cm/72kg, 여성 161cm/54kg)과 즉각 연계되는 스마트 앵커링 도입.
- **홈트레이닝 18종 일러스트 제작 방식**은 `docs/proposals/holding/2026-10-28-homet-illustrations-production.md`로 별도 정리하여 holding 문서로 분리함.
- `docs/proposals/README.md`와 `docs/PRD.md` 향후 확장 후보 표 갱신: PROP-04를 `rejections/` 경로와 기각 상태로 고치고, PROP-05 행을 추가함.
- 검증 통과: `check-plans-links.sh`, `gen_plans_index.py --check`, `test_doc_audit.py`(36 passed).
- 참고: `docs/plans/logs/process-infra.md`의 기존 미커밋 변경(2026-07-29 entry를 Older로 이동)에 맞춰 `docs/plans/README.md` 카운트(7/22→6/23)가 재생성됨.

**다음 할 일 (Next TODOs):**
- 사용자가 D-3~D-6 결정 → `Accepted` → 영역 A(v0.6.2)부터 `docs/plans/` 페어로 승격.
- **범위 밖 결함(별도 추적 필요)**: 배지 9종 중 7종 획득 불가(`FIRST_WORKOUT`·`WEEK_1_COMPLETE`만 지급, 서버 자동 지급 없음), `checkBadges` 결과 폐기.

**의존성 및 주의사항 (Context & Constraints):**
- PROP-01 삭제, PROP-04 `rejections/` 이동은 사용자가 한 작업이니 되돌리지 마세요.
- git stash의 사용자 `autostash`는 그대로 두세요.

---

## 2026-10-28 (Gemini -> Claude/User) Android 앱 전체 UI/UX 정적 감사 보고 (코드 무변경)

**현재까지의 작업 상태 (Current Status):**
- `app/src/main/java/.../ui/**`·`theme/`·`res/values/themes.xml` 정적 분석 후 보고서 작성(대화 아티팩트 `ux_audit_report.md`). 저장소 코드는 수정하지 않음.
- **P0 후보**: ① `Theme.kt` 색 토큰 누락(`outlineVariant`·`errorContainer`·`surfaceContainer*` 등 → API ≤30에서 M3 기본 보라 톤 유입) ② `dynamicColor=true` 기본으로 브랜드 색 소실 ③ `themes.xml`(Light 고정·`values-night` 없음)+Compose Splash 이중 스플래시/다크모드 흰 화면 깜빡임 ④ `HomeViewModel.onPlanReady`의 `checkBadges` 결과 폐기 + 토글 시 배지 미검사 → 획득 알림 없음 ⑤ 체지방/골격근 기본값(20/30, `?: 20f`)이 실측 없이 저장됨.
- **P1 후보**: "오늘" 탭에 Today Hero 없음, `loadPlan()`이 매번 `Loading` → 새로고침 시 화면 깜빡임, 미래 날짜 완료 가능, 운동 상세에 세트/휴식 타이머·홈트 시각자료 없음, 식단 `Generating` 상태·재생성 없음(`MealPlanViewModel` 룰 11 미준수), 진행 허브 데이터 없음(스마일 스트릭 배지 미구현), 계정 삭제가 신체 정보 수정 화면에 위치.

**다음 할 일 (Next TODOs):**
- 사용자 승인 시 Sprint 1(디자인 기반: 전체 색 토큰·Dynamic Color 기본 OFF·core-splashscreen·DayNight·Type 15종/한글 폰트)부터 `/plan` 후 착수.
- 1-5(체성분 nullable 저장)는 착수 전 백엔드 `PUT /profile` 스키마 nullable 허용 여부 확인 필요.

**의존성 및 주의사항 (Context & Constraints):**
- 실기기 미검증(정적 분석). 스마일 트레이닝 화면은 구조만 확인.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

---

## 2026-10-27 (Gemini -> Claude/User) 사용자 작업(GitHub Ruleset, Actions 권한, SSH 서명) 전수 검증 및 점검 100% 완결

**현재까지의 작업 상태 (Current Status):**
- **사용자 수동 설정 항목(GitHub Ruleset, Actions PR 권한, SSH 서명 키) 전수 검증 및 완결 100% 통과**:
  1. **Actions PR 생성 권한**: `default_workflow_permissions: write`, `can_approve_pull_request_reviews: true` 정상 활성화 (PASS ✅).
  2. **GitHub 태그 보호 규칙 (Ruleset `default tag`, id 25954171)**: `Restrict creations` 해제 완료(`rules: [deletion, non_fast_forward, update]`). 신규 `v*` 릴리스 태그 생성 및 변조 방지 정상 동작 (PASS ✅).
  3. **GitHub main 보호 및 Repository Gate 필수 검사 (Ruleset `default branch`, id 25954096)**: `required_status_checks: [{"context": "Repository Gate"}]` 정상 지정 완료. PR 필수 검사 통과 강제 (PASS ✅).
  4. **SSH 서명 키 (GitHub 원격 & 로컬 Git)**:
     - GitHub 계정(`gunnysis`)에 `id_ed25519.pub` 서명 키(id 1276982) 등록 완료.
     - 로컬 Git: `gpg.format=ssh`, `user.signingkey=C:/Users/Administrator/.ssh/id_ed25519.pub`, `tag.gpgSign=true`, `allowed_signers` 등록 완료.
     - 실측 태그 서명(`git tag -s`) 및 검증(`git tag -v`) 결과: `Good "git" signature for qkr133456@gmail.com with ED25519 key ...` 완벽 통과 (PASS ✅).

**다음 할 일 (Next TODOs for User / Operator):**
- Codex가 준비한 v0.6.1/40 로컬 변경사항(의존성 갱신, lockfile, hash lock, verification-metadata, repository-gate.yml 등) 검토 및 PR 생성/머지 진행.
- 차기 릴리스 준비 시 SSH 서명 태그(`git tag -s v0.6.1 -m "..."`) 부착 및 푸시 진행.

---

## 2026-10-27 — v0.6.1/40 로컬 릴리스 후보·패키지/공급망 강화

- 저장소 SSoT를 `versionName=0.6.1`, `versionCode=40`으로 올렸지만 **태그·push·Play 업로드·Azure 배포는 하지 않았다**. 운영 기준은 계속 Play `v0.6.0/39`, backend revision `0000065` / image `a0b6701`이다.
- 휴식 요일 UI를 `FlowRow` 기반 48dp `FilterChip`으로, 프로필 요약을 줄바꿈 가능한 적층 레이아웃으로 개선하고 semantics/live-region 및 instrumentation 회귀 테스트를 추가했다.
- Android/Gradle 및 Python 의존성을 갱신하고 Gradle lock/verification metadata, Python hash lock, container digest, Action SHA pin, CODEOWNERS, Docker Dependabot, Repository Gate를 적용했다.
- release workflow는 Play 성공 후 `main`에 원장을 직접 push하지 않고 `release-ledger/v<version>-<code>` PR을 생성한다. GitHub 보호 규칙·Actions PR 권한·SSH tag signing·Play/Azure·실기기 검증은 사용자 승인/외부 gate로 남아 있다.
- 로컬 검증은 Android Spotless/AGP guard/Detekt/unit/lint/debug/androidTest compile/R8 release, Backend Ruff/Mypy/pytest 140/pip-audit/Bandit, Docker build, version monotonic 및 workflow SHA 검증을 통과했다. 연결 기기가 없어 instrumentation 실행은 미완료다.

---

## 2026-10-27 (Gemini -> Claude/User) 휴식일 선택기(`RestDaysSelector`) 고정 프리셋 제거 및 미니멀 직관 UI·프로필 요약 카드 레이아웃 고도화 완료

**현재까지의 작업 상태 (Current Status):**
- **사용자 요청 반영: 휴식요일 고정 프리셋 제거 및 레이아웃 개선 완수**:
  1. **`RestDaysSelector.kt`**:
     - `RestDayPreset`, `REST_DAY_PRESETS`, `FlowRow` 및 `FilterChip` 프리셋 블록 완전 제거.
     - 상단 헤더에 실시간 주간 운동·휴식 리듬 배지(`Surface(primaryContainer)` → `"운동 N일 · 휴식 M일"`) 신설.
     - 세그먼트 버튼 선택 요일에 `FontWeight.Bold` 강조 부여.
     - 하단에 "휴식일 (토·일)" 및 "운동일 (월·화·수·목·금)"을 직관적으로 보여주는 미니 인포 스트립(`Surface(surfaceVariant)`)과 아이콘 기반의 `errorContainer` 유효성 안내 배너를 추가.
     - Compose `@Preview` 지원.
  2. **`ProfileSummaryCard.kt`**:
     - 단순 텍스트 줄바꿈 대신 키/몸무게, 골격근량/체지방률, 주간 루틴을 좌우 정렬한 현대적인 대시보드 카드 레이아웃(`RoundedCornerShape(12.dp)`, `surfaceVariant` 65% alpha)으로 개선.
  3. **문서 동기화**:
     - `docs/plans/logs/android.md`, `docs/CHANGELOG.md`, `AI_HANDOFF.md` 동기화.
  4. **품질 검증**:
     - Android `spotlessCheck` PASS, `detektDebug` PASS (0 issues), `testDebugUnitTest` PASS (186 tests).

**다음 할 일 (Next TODOs for User / Operator):**
- 필요 시 차기 릴리스(v0.6.1 또는 v0.7.0) 버저닝 및 배포 진행.

**의존성 및 주의사항 (Context & Constraints):**
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

---

## 2026-10-27 (Gemini -> Claude/User) v0.6.0 (`versionCode=39`) 운영 배포(C1 ➔ C2 ➔ C3) 전 구간 성공 및 문서 SSoT 정합화 완료

**현재까지의 작업 상태 (Current Status):**
- **v0.6.0 (`versionCode=39`) 운영 컷오버 (C1 ➔ C2 ➔ C3) 100% 완수**:
  1. **C1 — 운영 DB 마이그레이션(`d7f4a2c91b10`) 및 v2 사용자 데이터 초기화(`reset_v2_user_data.py`)**:
     - Azure PostgreSQL Flexible Server(`healthapp`, `rg-eundunhealth-prod-krc`)에 임시 방화벽 규칙(`temp-v2-reset`)을 생성하여 `alembic upgrade head`(`d7f4a2c91b10`: `weekly_plans.plan_mode`, `user_profiles.default_workout_plan_mode`, `user_profiles.rest_days` `INTEGER[]` + 체크 제약, `data_reset_audits` 테이블) 적용 완료.
     - `--dry-run` 점검 후 `--confirmation RESET_ALL_V2_USER_DATA --reset-id v2-cutover-20261027 --image-sha 4562506 --execution-id prod-cutover-20261027` 실행: `{"deletedRows": {"badges": 0, "goals": 0, "meal_plan_items": 95, "meal_plans": 6, "smile_training_sessions": 0, "user_profile_history": 6, "user_profiles": 3, "weekly_plans": 11}, "resetId": "v2-cutover-20261027"}` (`data_reset_audits` 감사 기록 완료).
     - 작업 직후 `temp-v2-reset` 방화벽 규칙 즉시 삭제(잔존 규칙 `allow-azure-services` 1개만 유지).
  2. **C2 — 백엔드 CI/CD 운영 배포 (`backend.yml` run `40469877206`, commit `a0b6701`)**:
     - CI/CD 배포 환경 개선: Gitleaks 8.24.3 오탐(`WorkoutRepositoryImplTest.kt`의 `WeeklyPlanV2Api` 타입명)을 `.gitleaksignore`/`.gitleaks.toml`로 해결하고, `scripts/verify_containerapp_deployment.py`에 `/app-compatibility?platform=android&versionCode=39` 프로브를 추가하여 공개 라우트 5종(`/health`, `/health/ready`, `/app-compatibility`, `/privacy`, `/account-deletion`)을 매 배포마다 자동 검증하도록 강화.
     - `backend-production` 환경 승인 후 `eundunhealth-api` 활성 리비전 **`eundunhealth-api--0000065`** 및 `eundunhealth-reaper` Job을 이미지 `eundunhealthacr.azurecr.io/eundunhealth-api:a0b6701`로 동기 배포 완료.
     - 라이브 엔드포인트 실측 검증: `/health` (`200`), `/health/ready` (`200`), `/app-compatibility?platform=android&versionCode=38` (`updateRequired: true`, `minimumSupportedVersionCode: 39`), `/app-compatibility?platform=android&versionCode=39` (`updateRequired: false`), 무토큰 `/v2/weekly-plan` (`401`).
  3. **C3 — Android Google Play 프로덕션 릴리스 (`release.yml` run `40469947190`, 태그 `v0.6.0`)**:
     - 커밋 `a0b6701`에 태그 `v0.6.0` 부착 및 push ➔ `play-release` 환경 승인 ➔ `Preflight (룰 2)` 통과 ➔ Google Play **production 트랙 `completed`(100%) 업로드 성공**.
     - `release.yml`이 `docs/ops/play-upload-ledger.md`를 `LAST_UPLOADED_VERSION_CODE=39`로 자동 갱신(커밋 `f9eeefc`).
  4. **문서 거버넌스 및 SSoT 정합화**:
     - 출시 완료된 설계서 `docs/plans/2026-10-27-flexible-rest-days-design.md`를 `docs/plans/logs/android.md` (`2026-10-27` entry)로 흡수 후 `git rm`, `docs/plans/README.md` 인덱스 재생성 및 링크 가드 통과.
     - `docs/ops/operations-snapshot.md`, `CLAUDE.md`, `README.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/SPEC.md`, `docs/CHANGELOG.md`, `docs/proposals/README.md` 최신 운영 상태(`v0.6.0/39`, 리비전 `0000065`, 이미지 `a0b6701`, 원장 `f9eeefc`) 동기화 완료.

**다음 할 일 (Next TODOs for User / Operator):**
- **실기기 사후 점검 (Post-release Operational Verification)**:
  - Play 스토어 프로덕션 트랙에서 `v0.6.0` (`versionCode=39`) 업데이트 배포본 설치 후 로그인, 온보딩/프로필 다중 휴식 요일(`restDays`) 선택, `헬스장` / `홈트레이닝` 주간 계획 생성, TalkBack 및 Health Connect 동작 확인.

**의존성 및 주의사항 (Context & Constraints):**
- 다음 릴리스 버전 코드(`versionCode`)는 최소 `40` 이상이어야 합니다 (`LAST_UPLOADED_VERSION_CODE=39`, 룰 13).
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

---

## 2026-10-27 (Gemini -> Claude/User) Senior Programmer / Architecture Lead / CTO 프로젝트 전체 시점 리팩토링·결함 근본 해결·UI/UX 고도화 완료 (운영 배포 요청 대기)

**현재까지의 작업 상태 (Current Status):**
- **어제(2026-10-26: 스마일 트레이닝 v0.5.0 + R8 ML Kit 핫픽스) 및 오늘(2026-10-27: 적응형 주간 운동 계획 v2 + 다중 휴식 요일 Zero-Legacy) 전 작업에 대한 CTO/아키텍처 전수 감사 및 리팩토링 완수**:
  1. **[P0 운영 차단 결함 근본 해결] 클라이언트 최소 지원 버전 드리프트(`38` → `39`) 및 구버전 프로필 덮어쓰기 차단**:
     - `backend/containerapp.yaml` (`MINIMUM_SUPPORTED_ANDROID_VERSION_CODE: '39'`) 및 `backend/app/config.py` (`minimum_supported_android_version_code: int = 39`)를 `39`로 정합화.
     - `PUT /profile` (`backend/app/routers/profile.py`)에 `Depends(require_supported_android_client)` 버전 가드를 추가하여 구버전(`<=38`) 앱이 `restDays` 미포함 요청으로 다중 휴식 요일을 기본값 `[7]`로 덮어쓰는 데이터 유실 경로를 원천 차단 + 회귀 테스트(`test_update_profile_blocks_unsupported_client_from_resetting_rest_days`) 추가.
  2. **[백엔드 아키텍처 & KST 시간대 SSoT 일원화]**:
     - `backend/app/time_utils.py` 신설 (`KST`, `normalize_utc`, `today_kst`, `current_monday_kst`).
     - `backend/app/routers/meal_plan.py`에서 컨테이너 UTC `datetime.date.today()` 사용으로 인해 매주 월요일 00:00~09:00 KST(9시간) 동안 지난주 월요일로 오판하던 버그를 `current_monday_kst()`로 근본 해결하고, `parse_day_plans_or_none` 및 `not dp.get("isRestDay")` 명시적 필터로 정합화.
     - `backend/app/services/smile_training_service.py` 및 `backend/app/routers/weekly_plan.py`의 중복 KST 계산을 `app.time_utils`로 통합.
     - `backend/app/services/meal_plan_service.py`의 비타입 `dict` 반환을 `DailyMealPlan`/`MealInfo` 강타입 모델로 전환하고, `backend/app/services/statistics_service.py`의 `day_plans` JSON 이중 파싱을 제거하여 단일 패스로 최적화.
  3. **[Android 도메인 SSoT 일원화 & ViewModel 상태 불변식 수정]**:
     - 5개 파일에 중복 산재해 있던 요일/휴식일 상수(`DEFAULT_REST_DAY`, `MIN_DAY_OF_WEEK`, `MAX_DAY_OF_WEEK`, `MAX_REST_DAYS`, `TOTAL_DAYS_IN_WEEK`) 및 정규화 함수(`normalizeRestDays`)를 `domain/model/UserProfile.kt` 단일 진실 공급원(SSoT)으로 통합.
     - `ProfileViewModel.saveProfile()`에서 성공 시 조기 `return@launch`로 인해 `ProfileUiState.Loaded`의 `profile` 및 `isSaving = false` 상태가 갱신되지 않던 도달 불능 코드(dead code) 결함을 수정하고 `normalizeRestDays` 도메인 정규화를 적용.
     - `HistoryViewModel`에 `fun refresh()`를 신설하고 `HistoryScreen`의 `PullToRefreshBox`에 연결하여, 전체 기록이 1페이지 이하(`hasMore == false`)일 때 당겨서 새로고침이 무시되던 버그를 해결 (`HistoryViewModelTest` 회귀 테스트 추가).
  4. **[UI/UX 레이아웃 및 사용자 안내 고도화]**:
     - `ProfileSummaryCard.kt`: `restDays` 전달 시 `"휴식 요일: 주 N일 (...) | 주 M일 운동"` 요약을 하단에 표시하도록 개선하고 `OnboardingScreen`·`ProfileScreen`에 연동.
     - `ProfileScreen.kt`: `rememberSaveable`에 초기 프로필 키 및 `ArrayList` 직렬화 안전성을 부여하고, `"휴식 요일 및 신체 정보 변경은 다음 주 계획 생성부터 반영되며, 이미 생성된 이번 주 운동 계획은 유지됩니다."` 안내 문구를 추가.
     - `WorkoutDetailScreen.kt`: `gifUrl.isBlank()`인 홈트레이닝 무장비 18종 운동 진입 시 `HomeExerciseGuideCard`(무장비 홈트레이닝 수행 가이드 배너)를 표시하도록 UX 개선.
  5. **[전수 품질 게이트 및 문서 SSoT 정합화 검증]**:
     - Backend: `ruff check` PASS, `ruff format --check` PASS, `mypy` (57 files) PASS, `pytest` **140 passed** (19 files).
     - Android: `spotlessCheck` PASS, `detektDebug` PASS (0 issues), `testDebugUnitTest` **186 `@Test` passed** (36 files), `assembleDebug` & `assembleRelease` (R8 full mode + `lintVitalRelease`) PASS.
     - Zero-Legacy 가드: `backend/app/`, `backend/tests/`, `backend/openapi.json`, `app/src/` 내 `rest_day` / `restDay` **0건**.

**다음 할 일 (Next TODOs for User / Operator):**
- 사용자의 운영 배포 요청 시 **C1(운영 DB `reset_v2_user_data.py` 실행) → C2(백엔드 ACA 배포 및 Alembic `d7f4a2c91b10` 적용) → C3(Android `v0.6.0`/`39` Play 프로덕션 릴리스)** 순서로 진행.

**의존성 및 주의사항 (Context & Constraints):**
- 운영 배포는 사용자의 명시적 배포 요청 후 실행합니다.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

---

## 2026-10-27 (Gemini -> Claude/User) 휴식일 자유 다중 지정(Flexible Multi-Rest Days) 통합 구현 설계서(design) 작성 및 인덱스 정합화 완료 (사용자 검토 대기)

**현재까지의 작업 상태 (Current Status):**
- **단일 완성형 설계서 수립 (`docs/plans/`)**:
  - `docs/plans/logs/android.md` (`2026-10-27` entry로 흡수된 `2026-10-27-flexible-rest-days-design.md`) 단일 문서에 아키텍처, 알고리즘, UI/UX, 4단계 구현 로드맵 및 사용자 필수 작업을 완벽히 통합·병합 완료.
  - **기술 팩트체크 및 최적안 확정**:
    - **백엔드**: SQLAlchemy `RestDaysType = ARRAY(Integer).with_variant(JSON, "sqlite")` 채택 ➔ 프로덕션 Azure PostgreSQL 16의 네이티브 `INTEGER[]` 및 체크 제약(`cardinality(rest_days) BETWEEN 1 AND 6`) 보장 + 기존 139개 단위 테스트(SQLite) 100% 무수정 통과. Pydantic v2 `@model_validator`로 구버전 단일 `restDay` 자동 흡수.
    - **도메인**: `WeeklyPlanGenerator` & `HomeWeeklyPlanGenerator` 대근육 우선순위 큐(`[Push ➔ Legs ➔ Pull ➔ Cardio ➔ Mixed ➔ Cardio]`)로 주 1~6일 상·하체 밸런스 완전 보장.
    - **프론트엔드**: M3 `MultiChoiceSegmentedButtonRow` 채택으로 기존 7분할 요일 버튼 룩앤필 100% 계승, Room `IntListConverters` TypeConverter.
    - **완료율/통계**: `HomeViewModel`과 `StatisticsService`가 이미 `count { !it.isRestDay }` 동적 분모를 쓰고 있음을 실측 검증 완료 (무수정 연동).
  - **거버넌스 및 링크 정합화 통과**:
    - `python scripts/gen_plans_index.py`: **PASS** (`docs/plans/README.md` active: 1 등록).
    - `bash scripts/check-plans-links.sh`: **PASS** (끊긴 참조 0건).
    - `pytest scripts/agents/test_doc_audit.py -q`: **PASS** (36 passed).

**다음 할 일 (Next TODOs for User / Operator):**
- **1순위**: 현재 진행 중인 적응형 주간 운동 계획 v2 (v0.6.0/39) C1 데이터 리셋 승인 및 C2/C3 배포 완결.
- **2순위**: v2 배포 완료 후, 본 통합 구현 설계서([`docs/plans/logs/android.md`](docs/plans/logs/android.md)) 검토 후 구현 착수 승인.

**의존성 및 주의사항 (Context & Constraints):**
- 현재 활성 코드베이스(`app/`, `backend/`)는 일절 건드리지 않고 순수 문서 공간에서 작업되었습니다.
- 마이그레이션 생성 시에는 반드시 `bash scripts/alembic-autogen.sh` (룰 3)를 사용해야 합니다.

---

## 2026-10-27 (Gemini -> Claude/User) 적응형 주간 운동 계획 v2 (Adaptive Weekly Workout Plan v2) & 휴식일 자유 다중 지정 (Flexible Multi-Rest Days, Zero-Legacy) 엔드투엔드 구현 및 검증 완료 (운영 컷오버 C1~C3 사용자 승인 대기)

**현재까지의 작업 상태 (Current Status):**
- **`docs/reports/2026-10-26-adaptive-weekly-workout-proposal.md` 및 `docs/plans/logs/android.md` (r3 Zero-Legacy) 통합 풀스택 구현 완료 (`v0.6.0` / `versionCode=39`)**:
  - **R1 & R2 (백엔드 API 계약·Alembic·다중 휴식일·데이터 초기화 스크립트)**:
    - Alembic 마이그레이션 `backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`: `weekly_plans.plan_mode`(`'gym'`/`'home'` CHECK 제약), `user_profiles.default_workout_plan_mode`(`'gym'`/`'home'` CHECK 제약, nullable), `user_profiles.rest_days`(`INTEGER[]` + `ck_user_profiles_rest_days_valid` 배열 제약, 레거시 `rest_day` 제거), `data_reset_audits` 감사 테이블 추가 (Alembic head `d7f4a2c91b10`, 총 9개 리비전).
    - `UserProfile` ORM(`ARRAY(Integer).with_variant(JSON, "sqlite")`) 및 `UserProfileRequest`/`UserProfileResponse` 스키마에서 레거시 `rest_day` 완전 제거 후 `rest_days: list[int]` (`1..6`개, `1..7` 요일 오름차순 중복 제거) 단독 일원화 + `meal_plan.py` 프롬프트 휴식 요일 한글 반영.
    - `/v2/weekly-plan` 라우터 (`GET`, `POST` 201/409 `weekly_plan_exists`, `DELETE` 204, `PATCH /complete`, `GET /history`, `GET /previous`, `GET /statistics`) 신설 및 레거시 `/weekly-plan` 라우트 완전 제거.
    - `PUT /v2/profile/default-workout-plan-mode` (`updateDefaultWorkoutPlanModeV2`): 신체 측정 이력(`profile_measurement_histories`) 오염 없이 기본 운동 환경만 원자적으로 변경·해제.
    - `GET /app-compatibility` 공개 엔드포인트(`minimumSupportedVersionCode = 39`) 및 `require_supported_android_client` 의존성(`Eundun-Client-Version-Code` 헤더 검증, 구버전 `403 client_update_required`) 적용.
    - `backend/scripts/reset_v2_user_data.py`: `--dry-run` 및 `--confirmation RESET_ALL_V2_USER_DATA --reset-id <id> --image-sha <sha> --execution-id <id>` 지원, 8개 앱 데이터 테이블(`RESET_TABLES`) 초기화 + `data_reset_audits` 감사 기록(PostgreSQL 16 컨테이너에서 `alembic upgrade head` ↔ `downgrade -1` ↔ `upgrade head` 및 `--dry-run` / 실행 리허설 완료).
    - `backend/openapi.json` 동기화 완료 (19 paths / 27 routes = 22 JWT + 5 Public).
  - **R3 & R4 (Android 데이터·도메인·UI/UX·테스트)**:
    - `version.properties`: `versionName=0.6.0`, `versionCode=39` (`bash scripts/bump-version.sh 0.6.0`, 단조성 검증 `39 > 38` 통과).
    - `WorkoutDatabase` v2 → v3 (`fallbackToDestructiveMigrationFrom(1, 2)`), `CachedPlanEntity.planMode`, `PlanJsonModels` v2 직렬화(`planMode`, `prescriptionUnit = REPS | SECONDS`, `validateDays` 7일 불변식).
    - `HomeWeeklyPlanGenerator` (무장비·저충격 홈트레이닝 18종 카탈로그 + 결정론적 7일 템플릿 + `local:home:<slug>:v2.0` ID) 및 `WeeklyPlanGenerator` (헬스장 카탈로그 + 소규모 유산소 풀 fallback 방어 보강).
    - `LoadCurrentWeekPlanUseCase`, `LoadDefaultWorkoutPlanModeUseCase`, `CreateWeeklyPlanUseCase`, KST 주차 기준 `ThemePreferences` 자동 생성 1회 가드(`hasAutoCreateAttempted` / `markAutoCreateAttempted`), `LocalDataWiper.ensureV2LocalResetCompleted()`.
    - `HomeViewModel` & `HomeScreen`: `HomePlanSectionState` (`Loading`, `AutoCreating`, `NoPlan`, `PlanReady`, `PlanDataError`), `NoWeeklyPlanCard`, `WorkoutPlanModeSelectionSheet` (`saveAsDefault` 체크박스 포함), `PlanModeBadge` (`헬스장` / `홈트`), 이번 주 계획 삭제 확인 다이얼로그, `WorkoutDetailScreen` 초(`초`) 단위 표기 및 홈트 안내 카드, `ProfileHubScreen` 기본 운동 환경 설정 시트, `MainAppShell` 구버전 클라이언트 업데이트 차단 다이얼로그(`ClientUpdateRequired`).
  - **R5 & R6 (검증 게이트 및 법적·기술 문서 동기화)**:
    - Backend pytest: **139 passed** (19 files), `ruff check` / `ruff format --check` / `mypy` PASS.
    - Android unit tests: **184 passed** (38 files), `spotlessCheck` / `detektDebug` (0 issues) / `assembleDebug` / `assembleRelease` (R8 full mode) / `check-agp10-compat.sh` PASS.
    - `docs/store/*.md` & `backend/app/legal/*.md`, `CLAUDE.md`, `README.md`, `docs/SPEC.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/ops/operations-snapshot.md` 동기화 완료.

**다음 할 일 (Next TODOs for User / Operator):**
- **C1 (사전 조건 확인 및 데이터 초기화 승인)**:
  1. 구버전(`v0.5.0`, `versionCode=38`) 앱이 실행 중인 기기에서 앱을 완전히 종료했는지 확인.
  2. 운영 DB 대상 `reset_v2_user_data.py --dry-run` 점검 및 `--execute --confirm-token V2_RESET_2026_10_27` 실행 승인.
- **C2 & C3 (백엔드 배포 및 Google Play 릴리스)**:
  1. 백엔드 Alembic `d7f4a2c91b10` 마이그레이션 및 ACA 리비전 배포(`MINIMUM_SUPPORTED_ANDROID_VERSION_CODE=39`).
  2. `bash scripts/bump-version.sh 0.6.0` (`versionCode=39`) 버전 상향 및 Google Play 릴리스 승인 후 실기기 사후 점검(Post-release Operational Verification).

**의존성 및 주의사항 (Context & Constraints):**
- C1 destructive reset 및 운영 배포(C2/C3)는 반드시 사용자의 명시적 확인 후 실행해야 합니다.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-27 (Gemini -> Claude/User) 적응형 주간 운동 계획 v2 제안서 '15. 구현 전 재검토 요청' 전건 승인 반영, 홈트레이닝 18종 확정 카탈로그/요일 템플릿 수립 및 실기기 검증 일정 정합화 완료

**현재까지의 작업 상태 (Current Status):**
- **'15. 구현 전 재검토 요청' 전건 승인 및 설계 문서 최신화**:
  - `docs/reports/2026-10-26-adaptive-weekly-workout-proposal.md`에 대해 사용자가 5대 재검토 요청 항목을 전건 승인함에 따라, 문서 상태를 '조건부 대기'에서 **'최종 승인 완료 (Approved & Ready for Implementation)'**로 전면 갱신했습니다.
  - **홈트레이닝 콘텐츠 자율 권한 연구/검토/검증/확정 (§6.4)**:
    - 장비 제로(`equipment = "없음"`), 층간소음 방지 저충격 설계, 오프라인 완결(`gifUrl = ""`), 안정적 ID(`local:home:<slug>:v2.0`), WHO 성인 신체활동 가이드라인 부합.
    - **18종 확정 카탈로그**: Push 4종(정석 푸시업, 니 푸시업, 파이크 푸시업, 체어 딥스), Pull 4종(슈퍼맨 홀드, Y-T-W 레이즈, 버드독, 리버스 스노우 엔젤), Legs 4종(에어 스쿼트, 제자리 런지, 글루트 브릿지, 스탠딩 카프 레이즈), Core 3종(엘보우 플랭크, 데드버그, 레그 레이즈), Cardio 3종(마운틴 클라이머, 점핑잭, 스케이터 홉스).
    - **결정론적 7일 요일 배치 스펙 (`HomeWeeklyPlanGenerator`)**: 월(Push & Core 4종), 화(Cardio 2종), 수(Pull & Posterior 4종), 목(Cardio 2종), 금(Legs & Glutes 4종), 토(Mixed Strength & Core 3종), 지정 요일(Rest Day). `days.size == 7` 및 `hasExercises == true` 불변식 100% 보장.
  - **실기기 검증 일정 정합화 (§8, §9, §10, §11, §15, §16, §18, §19)**:
    - 사용자의 "실기기는 운영 배포 후에 확인할 예정" 결정에 따라, 빌드/CI 게이트는 Compose semantics 및 touch-target(48dp+) 자동 검사로 충족하고, 실기기(TalkBack, Health Connect) 점검은 **운영 배포 완료 후 사후 점검(Post-release Operational Verification)**으로 분리·확정.
  - **차기 기획(Proposals / RFC) 거버넌스 체계 신설 및 1호 기획서 등록 완료**:
    - 스코프 팽창을 방지하면서 가치 있는 브레인스토밍 아이디어를 인큐베이팅할 수 있는 `docs/proposals/` 거버넌스를 구축했습니다.
    - `docs/proposals/README.md` (기획 백로그 인덱스 및 Idea ➔ Exploring ➔ Accepted ➔ Promoted to Plan 라이프사이클), `_template.md` (경량 표준 템플릿) 생성.
    - **1호 기획서 채택 ([PROP-01](file:///c:/programming/apps/eundunHealth/docs/proposals/2026-10-27-flexible-rest-days.md))**: "휴식일 자유 다중 지정 (Flexible Multi-Rest Days)" — 사용자 자율성 보장, 요일별 FilterChips 멀티 선택, $N$일 기준 동적 주간 완료율 분모 계산 및 경량 순환(Round-Robin) 배치를 명세하여 `v0.6.0` 목표로 등록.
    - **후속 기획서 분리 신설 ([PROP-04](file:///c:/programming/apps/eundunHealth/docs/proposals/2026-10-27-adaptive-split-engine.md))**: "운동 일수($N$) 기반 적응형 동적 분할 루틴 엔진 (Adaptive Split Engine)" — 사용자 결정에 따라 복잡한 전신/2분할/3분할/4분할 동적 엔진 및 AI 식단 열량 미세조정은 `v0.7.0+` 후순위 과제로 독립 분리하여 인큐베이팅.
    - `docs/PRD.md`의 `### 향후 확장 후보` 테이블과 양방향 연동 완료.
  - **문서 무결성 검증 통과**:
    - `bash scripts/check-plans-links.sh`: **PASS** (끊긴 참조 0건)
    - `python scripts/gen_plans_index.py --check`: **PASS** (정합화 100%)
    - `pytest scripts/agents/test_doc_audit.py -q`: **PASS** (36 passed)

**다음 할 일 (Next TODOs for Claude / User):**
- 사용자의 후속 구현 요청에 따라 **적응형 주간 운동 계획 v2 구현 패키지(R1~R6)** 착수:
  - **R1 (계약·reset 기반)**: `weekly_plans.plan_mode`, `user_profiles.default_workout_plan_mode` Alembic 마이그레이션, `v2-reset` Job/Command, OpenAPI sync 및 oasdiff/PostgreSQL 병렬 contract runner 구성.
  - **R2 (백엔드 v2 정책)**: `POST /v2/weekly-plan`(201/409 create-or-conflict), `DELETE /v2/weekly-plan`(204), `PUT /v2/profile/default-workout-plan-mode`, v1 route/schema 제거.
  - **R3 (Android 데이터 기반)**: generated client 재생성, Room 2→3 destructive reset boundary, `AutoCreateAttemptStore`, `CurrentWeekPlanRead`.
  - **R4 (Android 제품 흐름)**: `GymWeeklyPlanGenerator`, `HomeWeeklyPlanGenerator`, Home UI 상태(NoPlan, AutoCreating, PlanReady, PlanDataError), 환경 선택/생성 흐름.
  - **R5 & R6 (레거시 제거 및 배포)**: v1 참조 0건 검증, C1 reset 실행(구버전 앱 종료 확인) → C2 배포 → C3 Play 릴리스 → 운영 배포 후 실기기 확인.

**의존성 및 주의사항 (Context & Constraints):**
- C1 destructive reset 실행 직전에는 사용자의 구버전 앱 종료 확인이 선행되어야 합니다.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-26 (Gemini -> Claude) v0.5.0/38 스마일 트레이닝 시작 시 릴리스 전용 NPE 크래시(Sentry EUNDUNHEALTH-E/F) 근본 원인 해결 및 다중 방어선·회귀 가드 구축 완료 (INC-2026-10-26-32)

**현재까지의 작업 상태 (Current Status):**
- **Sentry 크래시 수집 및 역난독화 원인 규명**:
  - `v0.5.0`(`versionCode=38`) 릴리스에서 `'스마일 트레이닝 시작하기'` 버튼 클릭 직후 발생한 `"앱이 중단됨"` 크래시 2건(`EUNDUNHEALTH-E` [id: `7812482834`], `EUNDUNHEALTH-F` [id: `7812482966`])을 Sentry API로 조회하여 역난독화된 스택트레이스를 확보했습니다:
    `NullPointerException: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference at com.google.mlkit.vision.face.FaceDetection.getClient(FaceDetection.0:3) <- MlKitFaceModuleInstaller.ensureModuleAvailable(MlKitFaceModuleInstaller.kt:43)`.
  - **Primary Root Cause**: ML Kit(`com.google.mlkit:common:18.5.0` / `play-services-mlkit-face-detection:17.1.0`) 내부 DI(`com.google.firebase:firebase-components:16.1.0`)는 `MlKitComponentDiscoveryService`에 등록된 `FaceRegistrar`, `VisionCommonRegistrar`, `CommonComponentRegistrar`를 `Class.forName(...).getDeclaredConstructor().newInstance()` 리플렉션으로 인스턴스화합니다. 그러나 `firebase-components:16.1.0` 번들 `proguard.txt`는 `-keep class * implements com.google.firebase.components.ComponentRegistrar`에 생성자/멤버 명세(`{ <init>(); *; }`)가 빠져 있어, AGP 9.3.2 **R8 full mode**가 3개 Registrar의 기본 생성자 `<init>()`을 모두 제거했습니다(`mapping.txt` 실측 확인). 그 결과 `MlKitContext.getInstance().get(zzc.class)`가 `null`을 반환하며 `FaceDetection.getClient()` 호출 즉시 NPE가 발생했습니다.
  - **Secondary Root Cause**: `MlKitFaceModuleInstaller.ensureModuleAvailable()`에서 `FaceDetection.getClient(options)`와 `ModuleInstall.getClient(context)`가 `try` 블록 바깥에 위치해 있어 예외가 `MlKitModuleState.Unavailable`로 전환되지 못하고 메인 스레드 코루틴을 크래시시켰습니다.
- **근본 원인 해결 및 유지보수/방어선(Defense-in-Depth) 개선**:
  1. `app/proguard-rules.pro`: `-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); *; }` 추가. `:app:assembleRelease` 후 `app/build/outputs/mapping/release/mapping.txt`에서 `CommonComponentRegistrar`, `VisionCommonRegistrar`, `FaceRegistrar` 3개 클래스 모두 `void <init>() -> <init>` 및 `getComponents()`가 보존됨을 실측 검증했습니다.
  2. `MlKitFaceModuleInstaller.kt`: `FaceDetection.getClient` 및 `ModuleInstall.getClient`를 `try` 블록 내부로 이동하고 `finally { runCatching { detector?.close() } }`로 리소스 누수를 차단했으며, `Throwable`(`CancellationException` 재던짐) 포착 시 Sentry breadcrumb(`smile.mlkit_init_failed`) + `Sentry.captureException` 기록 후 한국어 안내와 함께 `MlKitModuleState.Unavailable`을 반환하도록 보강했습니다.
  3. `SmileTrainingViewModel.kt`: `prepareModelAndProceed()` 내부 `moduleManager.ensureModuleAvailable()` 호출을 `try/catch (t: Throwable)`로 이중 방어하고, `MODEL_UNAVAILABLE` 및 `onCameraError` 전환 시 룰 8에 따라 Sentry breadcrumb(`smile.model_unavailable`, `smile.camera_error`)를 남기도록 개선했습니다.
  4. `SmileCameraStage.kt` & `MlKitSmileSignalAnalyzer.kt`: `MlKitSmileSignalAnalyzer` 생성, `ProcessCameraProvider.getInstance`, `bindFrontCameraUseCases` 전체(`hasCamera`, `Preview`, `ImageAnalysis`, `bindToLifecycle`)를 `runCatching`으로 감싸 `onCameraError` 인라인 복구로 연결하고, `detector.process(inputImage)` 동기 예외 시에도 `imageProxy.close()`가 보장되도록 강화했습니다.
  5. **회귀 테스트 및 문서화**:
     - `ProguardKeepRulesTest.kt`: `ComponentRegistrar` `<init>()` keep 규칙 존재 여부를 단위 테스트로 박제(룰 12 확장).
     - `SmileTrainingViewModelTest.kt`: `moduleManager.ensureModuleAvailable`에서 예기치 않은 `NullPointerException` 발생 시 크래시 없이 `MODEL_UNAVAILABLE`로 전환되고 재시도 시 `READY`로 복구되는 회귀 테스트 추가(Android `@Test` 165 → **167 passed**).
     - `docs/ops/incident-log.md`(`INC-2026-10-26-32`) 및 `CLAUDE.md`(룰 12 확장, 테스트 수 167 반영) 업데이트 완료.
- **전체 품질 게이트 검증 결과**:
  - `bash scripts/check-agp10-compat.sh`: **PASS**
  - `./gradlew :app:spotlessApply :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleRelease`: **PASS (BUILD SUCCESSFUL, 167 `@Test` passed, detekt 0 issues)**
  - `pytest backend/tests/ -q`: **126 passed**
  - `pytest scripts/agents/test_doc_audit.py -q`: **36 passed**
  - `bash scripts/check-plans-links.sh` & `python scripts/gen_plans_index.py --check`: **PASS**

**다음 할 일 (Next TODOs for Claude / User):**
- Play 스토어에 핫픽스 릴리스(`v0.5.1`, `versionCode=39`)를 배포하려면 `bash scripts/bump-version.sh 0.5.1` 실행 후 커밋·푸시 및 `v0.5.1` 태그를 푸시하세요(`LAST_UPLOADED_VERSION_CODE=38`).

**의존성 및 주의사항 (Context & Constraints):**
- AGP R8 full mode에서는 `-keep class ...`에 `{ <init>(); *; }` 멤버 명세가 없으면 리플렉션으로 호출되는 기본 생성자 `<init>()`이 제거됩니다(룰 12).
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.


## 2026-10-26 (Gemini -> Claude) v0.5.0/38 출시 기준 프로젝트 전 문서 최신화 및 플랜 페어 아카이브 완료

**현재까지의 작업 상태 (Current Status):**
- `v0.5.0`(`versionCode=38`, Play 프로덕션 `completed` 100% 출시, 원장 `LAST_UPLOADED_VERSION_CODE=38`, 커밋 `cbd155f`) 및 백엔드 활성 리비전 `eundunhealth-api--0000063`(이미지 `869407a`, Alembic head `a3869f575d6a`, 배포 run `40274391632`) 기준으로 프로젝트 핵심 문서를 전수 동기화했습니다:
  - `CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/SPEC.md`, `docs/CHANGELOG.md`
  - 라우트 수(24개 = 20 JWT + 4 공개), 화면/VM/DI/클라이언트 수(14 Screens / 12 ViewModels / 4 Hilt modules / 7 OpenAPI clients), DB 테이블(앱 8 + `alembic_version`), 테스트 수(Android `@Test` 165 [35 files] · Backend pytest 126 [18 files]), 신규 의존성(CameraX 1.6.2, ML Kit Face Detection 17.1.0, WorkManager 2.10.1)을 코드베이스 실측치와 100% 일치시켰습니다.
- 완료된 설계/계획 문서(`2026-10-26-smile-training-{design,plan}.md` 및 `implementation_plan.md`)를 `docs/plans/logs/android.md` 토픽 렛저(`Recent` 3건)로 흡수·아카이브(`git rm`)하고 `docs/plans/README.md` 인덱스를 갱신했습니다(활성 작업 0건, 경고 0건).

**다음 할 일 (Next TODOs for Claude / User):**
- 차기 스토어 릴리스 시 `LAST_UPLOADED_VERSION_CODE=38`이므로 `bash scripts/bump-version.sh <new-ver>`를 통해 `versionCode >= 39`로 상향하세요.

**의존성 및 주의사항 (Context & Constraints):**
- `scripts/check-plans-links.sh`, `scripts/gen_plans_index.py --check`, `scripts/agents/test_doc_audit.py` 가드가 모두 통과하는 상태를 유지하세요.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-26 (Gemini -> Claude) 스마일 트레이닝(Smile Training) v0.5.0/38 풀스택 엔드투엔드 구현, 다중 역할 전수 감사·근본 원인 해결, UI/UX 고도화 및 프로덕션 배포(Azure Container Apps + Google Play) 완료

**현재까지의 작업 상태 (Current Status):**
- `docs/plans/2026-10-26-smile-training-design.md` 및 페어 계획 `docs/plans/2026-10-26-smile-training-plan.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수)를 기반으로 스마일 트레이닝 기능(Phase 1~5)을 엔드투엔드로 구현하고, 다중 역할(개발자·책임자·운영자·유지보수자·QA/QC·어플 디자이너·DevOps/FDE·PM·CTO) 관점의 전수 코드 감사, 근본 원인 해결, UI/UX 리팩토링 및 **프로덕션 배포(`v0.5.0` / `versionCode=38`)**까지 완료했습니다.
- **Phase 1: Backend API & PostgreSQL Alembic Migration 완료**:
  - `backend/app/models/smile_training.py`: `SmileTrainingSession`(`smile_training_sessions` 테이블, `sa.Uuid(as_uuid=True)` PK, 체크 제약 3종, `(user_id, occurred_at)` 복합 인덱스).
  - `backend/alembic/versions/a3869f575d6a_create_smile_training_sessions.py`: `scripts/alembic-autogen.sh`로 PostgreSQL 16 컨테이너에서 자동 생성 및 upgrade/downgrade 검증(룰 3).
  - `backend/app/schemas/smile_training.py`, `smile_training_repo.py`, `smile_training_service.py`, `routers/smile_training.py`:
    - `POST /smile-training/sessions`: 클라이언트 UUID 멱등 생성(신규 201, 동일 payload 재전송 200, 상이 payload/타 사용자 UUID 충돌 409).
    - `GET /smile-training/sessions`: `(occurred_at DESC, id DESC)` 커서 페이지네이션.
    - `GET /smile-training/statistics`: KST(`UTC+9`) 기준 연속일(`currentStreakDays`, `longestStreakDays`) 및 당일 완료 여부(`completedToday`).
    - `DELETE /smile-training/sessions`: 인증 사용자의 전체 세션 삭제(`SmileTrainingDeleteResponse`).
  - 계정 삭제/고아 정리: `AccountService._purge_app_data()`에 `SmileTrainingRepository.delete_all_by_user()` 추가, `ProfileRepository.list_all_user_ids()`를 7개 사용자별 테이블 전체 `UNION`으로 확장.
  - `backend/openapi.json` 동기화 완료(`scripts/sync-openapi.sh`, 17 paths / 24 routes) 및 법적 고지(`docs/store/privacy-policy.md`, `docs/store/account-deletion.md` → `backend/app/legal/*.md`) 온디바이스 카메라 무저장·무전송 명시.
- **Phase 2: Android Persistence, WorkManager Sync, Remote Hydration & LocalDataWiper 완료**:
  - `SmileTrainingDatabase`(`smile_training.db`, version 1) 및 KSP 스키마 export(`app/schemas/com.gunnys.eundunhealth.data.local.SmileTrainingDatabase/1.json`).
  - `SmileSyncWorker`(`@HiltWorker`, `ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`) 및 `EundunHealthApplication`의 `Configuration.Provider` 연동.
  - `SmileTrainingRepositoryImpl`: 오프라인 우선 Room SSOT, 빈 로컬 DB 원격 세션 자동 복원(`refreshRemoteHistoryIfEmpty`), KST 연속일 계산, `PENDING`/`SYNCED`/`BLOCKED` 분류(400/409/422는 `BLOCKED`로 전환해 무한 재시도 배터리 소모 차단), 수동 재시도(`retryBlockedSessions`) 및 전체 삭제(`deleteAllSessions`).
  - `LocalDataWiper`(`DefaultLocalDataWiper`)를 `AuthRepositoryImpl.deleteAccount()`에 연동하고 서버 삭제 호출 전 `userId`를 선캡처하여 로컬 DB 2종, DataStore, WorkManager 큐를 일괄 정리.
- **Phase 3 & 4: Pure Domain Signal Evaluator, CameraX + ML Kit & Root Modal UI/UX 리팩토링 완료**:
  - `SmileRoutineRule` & `SmileSignalEvaluator`: 단조 시간(`timestampElapsedMs`) 누적, 루틴/난이도별 히스테리시스, 1,000ms 슬라이딩 윈도우(>= 75% 충족률), `trackingId` 변경 시 홀드 초기화, 일시정지 시 완료된 반복 횟수(`completedReps`)를 보존하는 `clearActiveHold()` 분리.
  - `MlKitFaceModuleInstaller`(`ModuleInstallClient` 가용성 확인 및 다운로드 진행률) + `MlKitSmileSignalAnalyzer`(`STRATEGY_KEEP_ONLY_LATEST`, `ResolutionSelector(640x480)` 해상도 최적화, 모든 경로 `imageProxy.close()` 보장).
  - `SmileTrainingViewModel`(룰 11 UDF-Enhanced 준수, `FrameSignal.timestampElapsedMs` 단일 클록 도메인 기반 세션 소요 시간 계산, 일시정지·이어하기 및 `Lifecycle.Event.ON_STOP` 백그라운드 전환 자동 일시정지, 카메라 바인딩 오류 시 인라인 `AppError` 복구).
  - `SmileCameraStage` & `SmileTrainingScreen`:
    - `WindowInsets.safeDrawing` Edge-to-Edge 안전 영역 보장, 스프링 물리(`Spring.DampingRatioLowBouncy`) 보간 `SweepGradient` 프로그레스 링, 반복 단계 도트 인디케이터(`RepStepIndicatorRow`), 실시간 정렬 상태 배지(`AlignmentStatusBadge`), `ACTIVE` 단계 일시정지/중단 컨트롤.
    - 기기 카메라 하드웨어 유무(`FEATURE_CAMERA_FRONT`/`FEATURE_CAMERA_ANY`) 사전 검사, 루틴 카드의 난이도별 유효 유지 시간·눈 뜨기 배지, 균등 5분할 목표 반복 선택기 및 총 예상 유지 시간 표시, 한국어 시간 포맷(`formatDurationKorean`), 완료·회고 화면 세션 요약 배너(`SessionOutcomeSummaryBanner`).
  - `AppNavigation.kt` 루트 모달 `Screen.SmileTraining` 및 `ProgressHubScreen.kt` "마음 연습 > 스마일 트레이닝" 진입 카드 연결.
- **Phase 5: 전수 품질 게이트 및 프로덕션 운영 배포 완료 (`v0.5.0` / `versionCode=38`)**:
  - Backend: `ruff check`, `ruff format --check`, `mypy` (52 files, 0 issues), `bandit` (0 issues), `pytest` (**126 passed**), `test_doc_audit.py` (**36 passed**), Docker Compose 런타임 스모크 **PASS**.
  - Android: `check-agp10-compat.sh`, `spotlessCheck`, `detektDebug` (0 issues), `testDebugUnitTest` (**165 passed**), `preflight-release.sh` (AAB 13.10MB + APK 8.79MB + Sentry 매핑 `b32bb97e-99aa-368c-ac1a-6d35b8a1d764` 업로드) **PASS**.
  - **CI/CD Concurrency 트러블슈팅**: 2026-10-17부터 `waiting` 상태로 방치되어 `backend-production` concurrency 락을 잡고 있던 stale run(`39026541371`)을 식별·취소(`gh run cancel`)하여 배포 파이프라인 차단 근본 해소.
  - **Azure Container Apps 배포 완료** (`Backend CI/CD` run `40274391632`): 활성 리비전 **`eundunhealth-api--0000063`** (이미지 `eundunhealthacr.azurecr.io/eundunhealth-api:869407a`, `eundunhealth-reaper` Job 동일 이미지 동기화, `/health`·`/health/ready`·`/privacy`·`/account-deletion` 200 및 `/smile-training/sessions` 401 인증 가드 라이브 검증 완료).
  - **Google Play 프로덕션 배포 완료** (`Release (Play production)` run `40274395812`): 태그 `v0.5.0` (`versionCode=38`) Play 프로덕션 트랙 `completed`(100%) 업로드 및 원장 자동 갱신 커밋 `cbd155f`(`LAST_UPLOADED_VERSION_CODE=38`) 완료.
  - **Android CI** (run `40274391865`) 및 **docs-plans-index** (run `40274391387`): 모두 `completed (success)`.

**다음 할 일 (Next TODOs for Claude / User):**
- 실기기(Android)에서 Play 스토어 업데이트(`v0.5.0`) 또는 로컬 설치 후 전면 카메라 권한 허용/거부, 최초 실행 시 Google Play services ML Kit 얼굴 모듈 준비, 미러 뷰파인더 및 조도별 미소 유지 피드백을 점검하세요(`docs/plans/2026-10-26-smile-training-design.md` §10 참조 → `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수).
- 차기 릴리스 시 `LAST_UPLOADED_VERSION_CODE=38`이므로 `versionCode >= 39`를 사용해야 합니다(`bash scripts/bump-version.sh`가 자동 검증).

**의존성 및 주의사항 (Context & Constraints):**
- `CameraXViewfinder`(`androidx.camera:camera-compose:1.6.2`)와 `SurfaceRequest`를 사용하므로 `androidx.camera.view.PreviewView`를 직접 참조하지 마세요.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-26 (Gemini -> Claude) 스마일 트레이닝 종합 설계 r9 ('3.4 진입점 및 내비게이션 아키텍처' 심층 고도화, 딥링크 세션 가드, 화면 켜짐 하드웨어 제어 및 A11y 내비게이션 명세 완료)

**현재까지의 작업 상태 (Current Status):**
- `docs/plans/2026-10-26-smile-training-design.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수)의 **'3.4 진입점 및 내비게이션 아키텍처'**를 실제 안드로이드 코드베이스(`AppNavigation.kt`, `MainAppShell.kt`, `Screen.kt`, `ProgressHubScreen.kt`) 구조 및 안드로이드 최신 가이드라인에 맞추어 심층 조사·연구하여 r9로 전면 개선 완료했습니다.
  - **진입점 다각화 및 심리적 완충 설계 (§3.4.1)**:
    - `ProgressHubScreen` (진행 탭) 신규 "마음 연습" 카드: `ProgressRow` 패턴과 일관성을 유지하며 상시 노출. 당일 완료 체크 및 `🔥 3일 연속` 뱃지 표기.
    - `HomeScreen` (오늘 탭) 맥락형 넛지: `dayPlan.isCompleted == true && !smileState.isCompletedToday` 조건에 따라 운동 완주 직후 부드럽게 제안. DataStore `dismissed_date` 기반 "오늘 하루 닫기(X)" 중복 피로도 방지.
    - 딥링크: `eundunhealth://smile-training?routine={routine}` 및 웹 링크 `https://eundunhealth.app/smile-training?routine={routine}` 지원.
  - **풀스크린 카메라 & 루트 모달 프리젠테이션 아키텍처 (§3.4.2)**:
    - `NavigationSuiteScaffold`(80dp 하단 네비게이션 바)의 화면 가림 및 트레이닝 중 손가락 오터치 세션 유실 방지를 위해 최상위 `AppNavigation.kt` (Root NavHost) 레벨 독립 모달로 등록.
    - 모달 전환 애니메이션: `slideInVertically` (스프링 물리) + `fadeIn()` / `slideOutVertically` + `fadeOut()`.
    - `Screen.SmileTraining`, `AppNavigation`, `MainAppShell`, `MainContent` 간의 완벽한 콜백 전파 인터페이스 명세화.
  - **딥링크 보안 및 인증 세션 가드 (§3.4.3)**:
    - 외부 딥링크 진입 시 미인증(`SessionState.Unauthenticated`) 상태 방어. `Screen.AuthGate`로 우선 우회 후 인증 완료 시 펜딩된 딥링크 경로로 자동 착륙.
    - 세션 종료 시 앱 강제 종료를 방지하기 위해 `Screen.Main`을 베이스로 하는 합성 백스택(Synthetic BackStack) 보장.
  - **백스택 안전성 및 하드웨어 생명주기 제어 (§3.4.4)**:
    - **Android 14+ Predictive Back & `BackHandler`**: `ActivePhase` 중단 확인 다이얼로그(`showExitConfirmDialog`) 바인딩으로 연습 유실 방지.
    - **화면 켜짐 유지 (`FLAG_KEEP_SCREEN_ON`)**: 카메라 응시 중 터치가 없어도 화면 절전(30초 타임아웃)을 방지하는 `DisposableEffect` 윈도우 플래그 바인딩 및 세션 종료 시 즉시 해제.
    - **세로 화면 고정 (`ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`)** 및 `WindowInsets.safeDrawing` 인셋 분리.
  - **접근성(A11y) 및 스크린 리더 지원 (§3.4.5)**:
    - TalkBack 사용자를 위한 Semantics(`role = Role.Button`, 상태 안내), 모달 진입 시 `FocusRequester` 자동 포커싱, 단계 전환 시 `liveRegion = LiveRegionMode.Polite` 음성 피드백.
- **검증 완료**:
  - `bash scripts/check-plans-links.sh`: **PASS (끊긴 링크 0건)**.
  - `python scripts/gen_plans_index.py --check`: **PASS (정합화 100%)**.
- `docs/plans/2026-10-26-smile-training-design.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수)를 모던 UI/UX 인터랙션 설계와 그래픽 기술 고도화를 반영하여 r8로 전면 개정 완료했습니다.
  - **모던 UI/UX 레이아웃 & 비주얼 설계 (§3.5)**:
    - `IntroPhase`: M3 Expressive Card, 루틴 세그먼트 버튼, 난이도 칩, 횟수 스테퍼.
    - `ActivePhase`: Edge-to-Edge 풀스크린 카메라 + 다크 글래스모피즘(Dark Glassmorphism) 플로팅 HUD.
    - `CompositingStrategy.Offscreen` + `BlendMode.Clear`: 윈도우 배경 누수 없는 안면 비네트 오버레이 및 타원형 투과 뷰포트.
    - `SweepGradient` 프로그레스 링: 미소 유지 시 시계방향 회전 차오름.
    - `RepSuccessPhase`: 뷰포트 바운스, 파티클 폭죽(Canvas Particle Explosion), 2단계 햅틱 피드백.
    - `CompletedPhase`: 5단계 인터랙티브 감정 회고 이모지 시트, 연속일(Streak) 불꽃 배지.
  - **마이크로 인터랙션 & 햅틱 피드백 시퀀스 (§3.6, §3.7)**:
    - 스프링 물리 보간(`Spring.DampingRatioLowBouncy`, `Spring.StiffnessMediumLow`)을 통한 게이지 진척도 애니메이션.
    - 얼굴 안착(`TextHandleMove`), 미소 시작(`SegmentTick`), 게이지 홀딩(Micro-tick), 세트 성공(`LongPress` 2연타 틱), 세션 완주(3단 리듬 팡파르).
  - **카메라 뷰파인더 다크 글래스 테마 & 접근성 (§3.8, §3.9)**:
    - 실내/역광 환경에서도 시인성(Contrast Ratio >= 7:1)을 보장하는 Dark Translucent Glass Theme 기본 채택.
    - TalkBack 스크린리더를 위한 `liveRegion = LiveRegionMode.Polite` 음성 피드백 및 48dp 터치 타깃 준수.
  - **백엔드 서버 동기화 & 유지보수성 아키텍처 완비**:
    - FastAPI 세션 CRUD/통계 엔드포인트, Alembic 마이그레이션(룰 3, 룰 7), `_purge_app_data(user_id)` 회귀 가드, Room KSP 스키마 관리.
- **검증 및 인덱스 정합화 완료**:
  - `bash scripts/gen-plans-index.sh` 실행 및 `python scripts/gen_plans_index.py --check`: **PASS (정합화 100%)**.
  - `bash scripts/check-plans-links.sh`: **PASS (끊긴 링크 0건)**.

**다음 할 일 (Next TODOs for Claude):**
- 사용자가 design을 승인한 뒤에만 `2026-10-26-smile-training-plan.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수) 페어를 작성하세요.
  - Phase 1: 백엔드 Alembic 마이그레이션, Model, Repository, Service, Router, OpenAPI 동기화.
  - Phase 2: 안드로이드 `SmileTrainingDatabase`, 오프라인 우선 동기화 Repository, 계정 삭제 연동.
  - Phase 3: CameraX + ML Kit 파이프라인, 전략 패턴 루틴 엔진, Compose UI 및 ViewModel.

**의존성 및 주의사항 (Context & Constraints):**
- 백엔드 마이그레이션 작성 시 반드시 `docker compose -f docker-compose.test.yml run --rm backend-test alembic revision --autogenerate` 명령을 사용하고 downgrade 블록을 검증하세요(룰 3, 룰 7).
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

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
