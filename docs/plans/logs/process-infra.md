# Process & Infra 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-09-02 — PR #165: Entra 전환(v0.2.0) + 기술부채 T0~T7 + 하드닝 H1~H10 + Azure 정리 + 전수 점검 리팩토링

- **PR**: [#165](https://github.com/gunnysis/eundunHealth/pull/165) (merge commit `049b643`, 42커밋) + main 직접 `786692a`(배포 차단 해소)
- **Why**: 5개 작업 사이클이 같은 브랜치에 누적됐다. 인증 전환이 스키마·시크릿·문서를 동시에 건드려 쪼개면 중간 상태가 깨지고, 이후 사이클이 같은 파일(`build.gradle.kts`·`config/detekt/`·문서)을 공유했다. squash 대신 **merge commit** 으로 커밋별 근거를 보존했다(ledger 가 인용할 해시 유지).
- **What**:
  ① **Entra 전환(v0.2.0/34)** — JWT ES256→**RS256** + issuer(OIDC discovery)·`scp` 검증, 식별자 `sub`→**`oid`**, 계정삭제 Graph 이관(204 + `deletedItems` 파기), Android 인증화면 3종 폐기 → MSAL 브라우저 위임(ui/auth 959→283줄), App Links·`/auth/confirm` 삭제, 룰 5 일반화 + 룰 11 항목 5 개정.
  ② **기술부채 T0~T7** — 툴 버전 정본 단일화 · detekt 생성코드 제외(baseline 55→19→3→0) · openapi-generator 7.25 · **Python 3.14** · Gradle 9.7.1.
  ③ **하드닝 H1~H10** — 프로필 '실패'→'없음' 오판 제거 · 배지 캐시 3결함 · 422 로그 건강데이터 제거 + `request_id` 위조 차단 · Graph 커넥션 작업단위 재사용 · **R8 릴리스를 PR 게이트에 추가**.
  ④ **Azure 정리** — 빈 RG 삭제(단일 RG) · `acr purge` Task 2개(2.21→**0.60 GiB**) · alert CAF 재명명(생성→검증→삭제로 공백 0) · reaper Job 갱신을 `--yaml` 전체 적용으로.
  ⑤ **전수 점검** — AGP 폐기 플래그 6→2(APK −153 KB) · `android.yml` paths 구멍 · Sentry 보고 정본화 + 컨벤션 테스트 · `.venv` 3.13→3.14 · 422→500 뒤집힘 · 문서 드리프트 8종 + 수집기 확장.
- **Outcome**: CI 9체크 green(Android R8·CodeQL 3종 포함). 배포는 **Trivy 에서 1회 차단**(INC-2026-09-01-30) → 런타임 이미지에서 pip/setuptools/wheel 제거로 해소 후 성공. **라이브 실증**: revision `0000058`·이미지 `786692a`·**reaper Job == 앱 이미지**(B2-a 불변식 첫 작동, 직전 7주간 `de612e9` 로 드리프트)·secret `supabase-*`→`entra-*` 완전 교체·`/health`·`/health/ready` 200·위조토큰 401·삭제 라우트 404. 게이트: android @Test **131** · backend pytest **115** · coverage 97%.
- **Lessons**:
  ① **"게이트 green" 이 "건강함" 은 아니다.** 통상 지표가 전부 정상인 상태에서 6건이 나왔고 공통점은 **전부 빌드가 성공하는 상태로 잘못돼 있었다**는 것 — 폐기 플래그의 틀린 사유, CI 를 우회하는 paths 구멍, 잊을 수 있는 2단 보고, 잘못된 인터프리터의 측정값.
  ② **측정은 "무엇 위에서 쟀는가" 까지가 측정이다.** 3.13 `.venv` 에서 잰 coverage 98% 를 근거로 옳은 문서("~97%")를 고칠 뻔했다. 룰 9 의 MEASURED 라벨에 런타임을 포함해야 한다.
  ③ **차단 사유 주석은 실측으로 갱신하지 않으면 다음 사람을 잘못 보낸다.** "Hilt 미지원" 이라 적혀 있었지만 실제로는 detekt(1.23.8 이 AGP 9 새 DSL 에서 variant 태스크 미등록)였다. 전환을 직접 시도해 `kotlin.srcDir` 한 줄이면 컴파일이 통과함까지 확인해 재개 절차로 남겼다.
  ④ **가드는 자기 표기의 사각지대를 갖는다.** 전날 만든 링크 가드가 저장소에서 가장 많이 쓰는 `{design,plan}` 축약형을 정규식에 넣지 않아 죽은 참조 7건을 놓치고 있었다. 가드 도입 후 **그 가드가 무엇을 못 보는지**를 한 번 더 물어야 한다.
  ⑤ **스캐너가 우리 의존성 목록 밖의 것을 잡으면 "왜 이미지가 그걸 싣고 있는지" 를 먼저 묻는다.** vendored 사본은 버전 상향이 불가능해 제거 외 해법이 없다.
  ⑥ **IaC 파일이 "희망사항" 이 되는 경로** — `setup-reaper-job.sh` 가 갱신 시 `--image` 만 적용해 yaml 을 고쳐도 라이브에 전파되지 않았고, 잡이 7주간 옛 시크릿을 들고 있었다. 생성·갱신 **양쪽 다** 전체 적용이어야 파일이 단일 출처가 된다.
- **Files touched**: `gradle.properties`, `app/build.gradle.kts`, `app/src/**`(AppError 정본화·컨벤션 테스트), `backend/{Dockerfile,app/main.py,tests/**}`, `.github/workflows/{android,backend}.yml`, `scripts/{check-plans-links.sh,agents/doc_audit.py,agents/test_doc_audit.py,setup-reaper-job.sh,setup-azure-alerts.sh}`, `backend/{containerapp,reaper-job}.yaml`, CLAUDE.md/README/TRD/SPEC/PRD/CHANGELOG, `docs/ops/{operations-snapshot,incident-log,dependency-deferred,monitoring-and-cost}.md`, `docs/conventions/naming.md`, `version.properties`
- **설계 원문**: `2026-09-01-entra-external-id-migration-{design,plan}.md` · `2026-09-01-tech-debt-runtime-modernization-{design,plan}.md` · `2026-09-01-codebase-hardening-{design,plan}.md` · `2026-09-01-azure-resource-naming-and-legacy-{design,plan}.md` · `2026-09-01-legacy-modernization-program-design.md` · `2026-09-02-full-audit-refactor-{design,plan}.md` (본 entry 로 흡수, git rm)

### 2026-07-29 — RG 이관 apps → rg-eundunhealth-prod-krc (이동 7 + 재생성 9 + RBAC 8 + 구 RG 삭제)

- **PR**: main 직접 (`c954579` 이관 본체 + 후속 점검·개선 커밋)
- **Why**: 운영 리소스 17개가 범용 이름 RG `apps` 에 있어 CAF 컨벤션(`rg-<workload>-<env>-<region>`) RG 로 이관. 회원님 Cloud Shell 배치 이동이 `ResourceMoveValidationFailed` 9건(알림 8 + UAI = 이동 미지원 타입)으로 전체 거부된 것이 발단. 사용자 0명 = 다운타임 허용 전제.
- **What**: 공식 문서 조사 기반 design+plan 페어 승인 후 실행 — ① 알림 8+AG 를 IaC 스크립트 `--delete`(이동 불가+ID 참조 무효라 재생성이 정도) ② `validateMoveResources` REST 사전검증(202→Location 폴링→**204**) ③ 7개(PG·ACR·KV·env·app·job·LA) `az resource move` ④ **LA shared key 재생성 대응**: env 로그 설정에 새 key 반영(15분 내 292건 인제스천 실증) ⑤ orphan RBAC 8건 CLI 재부여(`--assignee-object-id`+`--assignee-principal-type`, 8/8 성공 — MSA 제약 미재현) + revision 재시작으로 pull/secret resolve 실증 ⑥ reaper job: `update --yaml` 이 identity 교체 불가(FailedIdentityOperation) → **삭제 + IaC 재생성**(신규 UAI, 전파 지연 1회 재시도 후 성공, 수동 실행 Succeeded, cron `0 18 * * 0` 보존) ⑦ repo 참조 갱신(기능 8곳+문서 7종, diff 전수 검토로 blind-replace 0건) + 빈 `apps` RG 삭제. 후속 점검에서 잔여 갭 3건(check-warm-baseline.sh 기본값·register-azure-credentials.ps1 기본값·verify-deploy.md) + setup-reaper-job.sh already-exists 오인 경고 수정.
- **Outcome**: 전 게이트 green — 이동 후 `/health`·`/health/ready` 200, backend.yml 자동 배포 run success(OIDC→AcrPush→새 RG update 전 경로), warm-baseline-check success, 알림 8/8 enabled(scope=새 RG 실측), reaper Succeeded, LA 로그 유입 정상. 단일 RG 상태(`apps` 삭제 완료). FQDN·PG 호스트·KV URI 불변 = 앱/Play URL 무영향.
- **Lessons**: ① **LA workspace 이동은 shared key 를 재생성** — Container Apps env 가 그 key 로 로그 전송하므로 이동 직후 `az containerapp env update --logs-workspace-key` 필수(사전 공식문서 조사로 발견, 실측 확인). ② **리소스 이동은 리소스 범위 역할 할당을 orphan** — 이동 전 `az role assignment list` 스냅샷을 떠 두면 objectId 를 Graph 조회 없이 확보해 CLI 재부여 가능. ③ `az containerapp job update --yaml` 은 **user-assigned identity 교체 불가** — 상태 없는 cron job 은 삭제+IaC 재생성이 정도. ④ 배치 이동은 all-or-nothing — 미지원 타입 제외 후 `validateMoveResources` 사전검증(204/409)이 2차 거부를 막는다. ⑤ 멱등 스크립트의 역할 부여는 `RoleAssignmentExists` 를 성공으로 처리해야 재실행 시 오해 유발 경고가 없다.
- **Files touched**: `.github/workflows/{backend,warm-baseline-check}.yml`, `backend/{containerapp,reaper-job}.yaml`, `scripts/{setup-azure-alerts,setup-reaper-job,check-warm-baseline}.sh`, `scripts/hooks/secretref-guard.sh`, `scripts/register-azure-credentials.ps1`, `.claude/commands/{naming-audit,verify-deploy}.md`, `docs/ops/{operations-snapshot,monitoring-and-cost,migration-runbook,azure-container-apps-jobs}.md`, CLAUDE.md/README/PR template, (저장소 외) `C:/programming/docker/eundunhealth-api/redeploy.sh`
- **설계 원문**: `2026-07-29-rg-migration-{design,plan}.md` (본 entry 로 흡수, git rm)

### 2026-07-03 — CI/CD 권장 개선 P1~P4 완결: concurrency + Azure OIDC(+AZURE_CREDENTIALS 완전 제거) + Android CD(태그 push→Play 내부 트랙) 실 e2e

- **PR**: [#140](https://github.com/gunnysis/eundunHealth/pull/140)(P1) + [#141](https://github.com/gunnysis/eundunHealth/pull/141)/[#142](https://github.com/gunnysis/eundunHealth/pull/142)(P2) + [#143](https://github.com/gunnysis/eundunHealth/pull/143)(P4) + main 직접(`6d9cff7` v0.1.19 릴리스, `ef47514` secret 제거, `f61254d`/`c4b717b` deprecation RCA)
- **Why**: 2026-06-29 CI/CD 전수 점검 design 의 우선순위 구현 — PR CI 중복 실행 낭비(P1), 장수명 SP secret 보안 부채(P2), Play 업로드 사람 의존 + 원장 갱신 갭 INC-2026-06-19-28(P4). P3(Docker 캐시)는 public 전환·trivy 기본 캐시로 동기 소멸 = 보류, P5 YAGNI.
- **What**: **P1** — android/backend yml `concurrency`(PR 이벤트만 cancel-in-progress, main push 보존). **P2** — Azure 로그인 OIDC 연합(federated `github-main`, subject=main ref; MSA CLI 로 생성 가능 실증) → push deploy·schedule cron 양 트리거 실측 green 후 **AZURE_CREDENTIALS 완전 제거**(GitHub secret + Entra 앱 비밀번호 keyId cef34a8e — ~07-16 게이트 조기 종결; 근거 = 사용 컨텍스트 100% 실측 + 워크플로 참조 0 + 롤백 스크립트가 매 실행 credential reset 이라 잔존 보험 가치 0. 동시갱신 7파일: 스크립트 헤더 긴급폴백 재정의·CLAUDE.md·snapshot·monitoring §5/§6.7·runbook·incident-log). **P4** — `release.yml`: 태그 `v*` push → environment `play-release` 승인 → preflight 단일 진입(룰 2·13·Sentry 매핑 게이트 상속) → r0adkll v1.1.5 내부 트랙 업로드 → `update-upload-ledger.sh` 원장 자동 커밋. dry-run 리허설 경로(러너-로컬 임시 versionCode + 매핑 생략) 별도. **v0.1.19/33 실 e2e**: preflight까지 전 게이트 1차에 green, Play 업로드만 403 ×2(서비스 계정 권한 전파/수준) → 회원님 Console 권한 조정 후 3차 성공 — **원장 자동 커밋 `32f0ebe`(LAST=33) 실증** = INC-28 사람 의존 갭 자동화로 폐쇄.
- **Outcome**: run 28641479092 green(빌드 8m40s±). 프로덕션 = v0.1.18/32 유지, 내부 트랙 = v0.1.19/33. 만료되는 장수명 Azure secret 0. 부수 픽스: preflight Sentry 토큰 폴백 set -e 무출력 즉사(`ec7535c`), bump-version.sh 잉여 인자 거부(--dry-run 후치 footgun), r0adkll `track`→`tracks`.
- **Lessons**: ① **잔존 "롤백 보험" secret 은 롤백 경로가 그 secret 에 의존할 때만 가치** — register 스크립트가 매 실행 credential 을 reset 하므로 보험 가치 0 = 조기 제거 가능(2주 안정 게이트는 트리거 커버리지 실측으로 대체). ② **Play 서비스 계정 403 은 인증 아닌 Console 권한** — 토큰 발급 성공 + Edit 생성 거부 패턴이면 전파 대기(24~48h)나 권한 수준(테스트 트랙 출시) 문제; 재개는 `gh run rerun <id> --failed`(태그 재생성 불요, versionCode 미소비 = 룰 13 안전). ③ **upstream README 예제도 드리프트** — r0adkll 예제가 자기 deprecated 입력(`track`)을 사용 → action 입력은 예제가 아닌 태그 시점 `action.yml` 선언과 대조. ④ 태그 push run 의 rerun 은 **태그 시점 yml** 실행 — 워크플로 픽스는 다음 태그부터 유효. ⑤ dry-run 리허설이 가드(룰 13)와 충돌하면 가드를 끄지 말고 **러너-로컬 임시값**으로 우회(실경로 가드 보존) — 그 리허설이 preflight 잠복버그(`set -e`+grep exit 1 무출력 즉사)까지 잡음.
- **Files touched**: `.github/workflows/{android,backend,warm-baseline-check,release}.yml`, `scripts/{update-upload-ledger,preflight-release,bump-version,check-version-monotonic}.sh`, `scripts/register-azure-credentials.ps1`, `docs/ops/{play-upload-ledger,operations-snapshot,monitoring-and-cost,migration-runbook,incident-log}.md`, CLAUDE.md/README/PRD/CHANGELOG, `version.properties`
- **설계 원문**: `2026-06-29-cicd-recommended-design.md` + `2026-07-02-android-cd-play-upload-{design,plan}.md` (본 entry 로 흡수, git rm)

### 2026-07-02 — repo public 전환(보안감사·스크럽) + open PR 전량 정리 + CodeQL java-kotlin 근본수정 (INC-29)

- **PR**: [#137](https://github.com/gunnysis/eundunHealth/pull/137)(식별자 스크럽) + #138/#139/#132 머지·#130/#131/#133/#134/#135 close + main 직접 `845b65c`(INC-29)
- **Why**: 회원님 요청 = repo private→public 전환 전 시크릿 유출/보안 위배 전수 점검 → 전환 완료 후 open PR 정리 → CodeQL 기본설정 `Analyze (java-kotlin)` 설정 에러 근본 해결. (배경: 2026-06-29 Play 프로덕션 정식 출시 LIVE.)
- **What**: ① **보안감사** — 추적 파일 314 + git 전 이력(24 refs·278 커밋) + GitHub 표면 + Docker. 유일한 실질 유출 = 커밋 `29c4202` 의 keystore 비밀번호 하드코딩(회전 + local.properties 반영 + `validateSigningRelease` 통과로 종결; **이력 재작성 안함** — GitHub 공식: 회전이 해법, SHA 캐시/PR 참조로 재작성 불완전). ② **스크럽(PR #137)** — Azure 구독 GUID → `__SUBSCRIPTION_ID__` 런타임 주입(`az account show`), PG admin 계정명 일반화, pre-commit `/subscriptions/<guid>` 가드, `backend/.dockerignore` 신설(`COPY . .` 가 .env/.venv 굽는 갭). ③ **public 전환 + secret scanning·push protection·CodeQL 기본설정**(회원님). ④ **dependabot 7→0** — 6/22 android CI 실패 전부 private **artifact storage quota**(public 전환으로 소멸, 의존성 무관 실증); #138 backend 5종 머지·배포, #139 android 배치(toml 충돌 연쇄 회피), #132 checkout v7, #133 kotlin 2.4 deferral 유지. ⑤ **INC-2026-07-02-29** — CodeQL autobuild(`./gradlew assemble`)가 clean checkout 의 release 서명 검증에서 실패 → release 서명 keystore **존재-조건부화**(없으면 unsigned) + preflight 서명 자료 fail-fast 가드 + java-kotlin 재등록.
- **Outcome**: open PR 0·open issue 0, backend deploy 3건 success, clean worktree `assembleRelease` unsigned 실증(`app-release-unsigned.apk`), 로컬 서명 경로 `validateSigningRelease` 통과 유지.
- **Lessons**: ① **private→public 은 CI 전제를 바꾼다** — artifact quota 소멸(만성 실패 원인 제거) + CodeQL autobuild 가 release variant 까지 빌드(clean-clone 빌드 가능성이 새 요구사항). ② **로컬 시크릿을 참조하는 빌드 설정은 존재-조건부**여야 clean checkout 이 안 깨진다(BuildConfig `getProperty(key, default)` 패턴과 동일 원칙) — 폴백이 출시 경로로 새지 않게 게이트(preflight) 동반 필수. ③ **원장(룰 13)의 사람 의존 갭 실측** — Play 업로드 성공(2026-06-29 LIVE) 후 `LAST_UPLOADED_VERSION_CODE` 미갱신(31 고착)을 문서 최신화 감사가 발견·소급(32). 업로드 주체가 사람인 한 문서 감사가 백스톱.
- **Files touched**: `app/build.gradle.kts`, `scripts/preflight-release.sh`, `backend/{containerapp,reaper-job}.yaml`, `.github/workflows/backend.yml`, `.githooks/pre-commit`, `backend/.dockerignore`, `scripts/setup-{reaper-job,azure-alerts}.sh`, `scripts/setup-sentry-alerts.ps1`, `docs/ops/{incident-log,play-upload-ledger,operations-snapshot,dependency-deferred,monitoring-and-cost}.md`, CLAUDE.md/README/PRD/TRD

### 2026-06-18 — 공개 출시 전 전체 감사 (PR #128, v0.1.17)

- **PR**: [#128](https://github.com/gunnysis/eundunHealth/pull/128) (shipped, squash `d227da3`)
- **Why**: Play Store 프로덕션 공개 출시 직전 7-도메인 전체 점검(보안·성능·에러UX·테스트·의존성·Play 컴플라이언스·코드품질). 출시 차단 0건이었고, 발견한 개선을 자율 구현(회원님 "보고/승인 없이 자율판단, 추상적 주장 금지, 재발방지 연구" 지시).
- **What**: **에러 UX(룰 8)** — Onboarding·Home·Profile 화면의 사용자액션 실패를 Snackbar→inline `AuthErrorBanner`(persistent+a11y liveRegion+Sentry breadcrumb). 상태 필드 신설(`OnboardingUiState.error`·`HomeUiState.Success.toggleError`·`ProfileUiState.Loaded.{saveError,deleteError}`; toggleError 는 다음 토글 시 null 리셋=persist). Profile 저장성공 toast 는 Snackbar 유지(룰8 예외). **a11y** — HistoryScreen 완료/미완료 아이콘 `contentDescription`(이전 null). **테스트** — BadgeViewModelTest 3 신설(@Test 139→142), Onboarding/ProfileVMTest 의 ShowSnackbar 검증→state error 검증, backend `test_edge_cases` 경계 2(weight>500·height>300→422; pytest 77→79). **backend** — account_service 삭제실패 로그 구조화(raw `resp.text` 보간 제거). **문서 드리프트 5건 정정**(CLAUDE/README/TRD/snap: Sentry Android 8.43.2·FastAPI 0.137.1·SQLAlchemy 2.0.51·Sentry SDK 2.63.0·Alembic head b78b256c2b20).
- **Outcome**: 전 게이트 green(android test+detekt+spotless / backend 79 PASS+ruff+mypy) + CI 6잡 green, squash 머지. **남음(Claude 불가)**: preflight v0.1.17 빌드+Play 업로드 + Flip3 실기기 a11y/배너 검증.
- **Lessons**: ① **죽은 코드의 자기 은폐**: `HomeSideEffect` 의 유일 emitter(ShowSnackbar) 제거 시 빈 sealed class + `_sideEffect` Channel 이 남았으나 빌드는 통과(Channel 이 import 를 여전히 "사용"하므로 spotless/detekt 미탐지) → pr-review-toolkit 코드리뷰가 포착, controller 가 grep 으로 fact-check(룰 10) 후 제거(`cb28450`). emitter 제거 PR 은 대응 Channel/sealed class/collector 도 함께 정리해야 함. ② 룰 8 위반은 신규 화면이 아니라 **기존 화면(Onboarding 은 에러 상태 0)** 에서 누적됨 — 감사가 신규뿐 아니라 baseline 전수를 봐야 함.
- **Files touched**: `app/src/main/.../ui/{onboarding,home,profile,history}/*`, `app/src/test/.../ui/{badge,onboarding,profile}/*Test.kt`(badge 신규), `backend/app/services/account_service.py`, `backend/tests/test_edge_cases.py`, `version.properties`, docs(CHANGELOG/PRD/TRD/operations-snapshot/CLAUDE.md/README.md)

### 2026-06-18 — 출시 critical 점검: App Links Play 서명키 누락 + Sentry 매핑 게이트

- **PR**: (main 직접 — 회원님 피드백 "출시 명시 시 late-critical 점검" 반영)
- **Why**: 회원님이 출시 점검을 여러 번 했는데도 문제 재발 → 핵심 지적: **로컬 테스트는 통과하지만 Play 배포본/승인에서만 터지는 critical** 을 봐야 한다("출시버전 빌드 후 승인 중 문제 = 가장 비쌈"). 코드품질 렌즈가 아니라 release-pipeline 렌즈 필요.
- **What (실측 발견 2건)**:
  - **App Links Play App Signing 키 누락 (CRITICAL)**: `auth.py _SHA256_FINGERPRINTS` 에 debug + 로컬 업로드키만 존재(`./gradlew :app:signingReport` 로 대조 확정). AAB 는 Play App Signing 으로 Google 재서명(opt-out 불가) → Play 설치본 인증서 = Play 키인데 assetlinks 에 없음 → **이메일 확인 딥링크/자동로그인이 Play 배포본에서만 깨짐**(로컬 APK 정상이라 테스트로 못 잡음). 코드 TODO 슬롯 + `play-store-release.md §4.1` 절차 추가. **값은 Play Console(앱 무결성→서명키 SHA-256)에만 있어 회원님이 추가 필요**. (App Links 나머지=호스트 `appLinksHost`=백엔드도메인·autoVerify·/auth/confirm 경로는 정확 — 유일 결함이 Play 키.)
  - **preflight 의 silent 매핑 누락**: `releaseArtifacts -PsentryRelease=true` 만 돌리고 `SENTRY_AUTH_TOKEN` 미검증. build.gradle `enableMapping=hasToken && sentryRelease` → 토큰 없으면 빌드 "성공"하지만 매핑 없는 AAB → 프로덕션 크래시 난독화 해제 영구 불가. preflight 에 **fail-fast 게이트**(`--allow-missing-sentry-mapping` 명시 override) 추가 — Rule 2 게이트의 거짓 안심 갭 차단.
- **Outcome**: 릴리스 빌드 양쪽 실증 — `assembleRelease`(APK 5.96MB)·`bundleRelease`(AAB 8.35MB) BUILD SUCCESSFUL. 백엔드 pytest 87. 출시 critical 잔여 = App Links Play 키(회원님 Console) + 콘솔작업(데이터안전·비공개테스트 12/14·자산).
- **Lessons**: ① "빌드 성공 = 출시 안전" 아님 — **로컬 서명 ≠ Play 서명**, 토큰 없어도 빌드는 됨. 로컬에서 재현 안 되는 것이 가장 비싸게 터진다. ② preflight 같은 release 게이트는 silent degradation 을 fail-fast 로 바꿔야 신뢰 가능. ③ "출시" 키워드 = release-pipeline 전용 경로(Play 재서명·매핑·데이터안전) 점검 신호([[release-critical-lens]]).
- **Files touched**: `scripts/preflight-release.sh`(Sentry 게이트), `backend/app/routers/auth.py`(Play 키 TODO+설명), `docs/ops/play-store-release.md`(§4.1 App Links)

### 2026-06-18 — 세션 작업 사이드이펙트 리팩토링: HTML 라우트 openapi 제외

- **PR**: (main 직접 — 직전 legal-routes 작업 자체 리뷰)
- **Why**: 이번 세션에 추가한 legal 라우트(`/privacy`·`/account-deletion`, `text/html`)를 리팩토링/사이드이펙트 관점 재점검 → openapi.json(= Android openapi-generator 입력)에 들어가 **앱이 절대 호출 않는 타입 없는 죽은 클라이언트 메서드(LegalApi)** 를 생성함을 실측. 같은 클래스 기존 문제 발견: `/auth/confirm`(text/html)도 `AuthApi.getAuthConfirmFallback` 죽은 메서드 생성(`AuthApi`·`HealthApi` 는 NetworkModule 어디서도 미참조 = 완전 죽은 코드).
- **What**: HTML 브라우저 라우트 3종(`/privacy`·`/account-deletion`·`/auth/confirm`)에 `include_in_schema=False`(공식 FastAPI — 라우트 동작 유지, 스키마·자동문서만 제외) → openapi 17→14 path, LegalApi 미생성·AuthApi 축소. 정적 법적 페이지 `Cache-Control: public, max-age=3600`. 스키마 제외 회귀 가드 테스트(`test_html_routes_excluded_from_openapi_schema`) + Cache-Control 단언. Android 코드가 해당 생성 메서드 미참조 실측 후 안전 제거. pytest 86→**87**.
- **Outcome**: openapi 재싱크 커밋, CLAUDE/README 엔드포인트 주석 갱신. 라우트는 prod 그대로 동작(브라우저·Play 크롤러 직접 접근). JSON infra 라우트(/health·ready·assetlinks)는 문서가치로 유지(범위 밖).
- **Lessons**: ① `text/html` 라우트를 JSON openapi 계약에 두면 openapi-generator 가 타입 없는 죽은 클라이언트 메서드를 양산 — 브라우저/크롤러용 라우트는 `include_in_schema=False`. ② 자기 작업도 리뷰 대상(SDD controller fact-check 정신) — 방금 추가한 feature 의 2차 효과(생성 클라이언트 오염)는 별도 점검에서만 드러남. ③ 제거 전 미참조 실측(grep NetworkModule/repos) 필수.
- **Files touched**: `backend/app/routers/legal.py`·`auth.py`, `backend/tests/test_legal.py`, `backend/openapi.json`, `CLAUDE.md`·`README.md`

### 2026-06-18 — 개인정보/계정삭제 페이지 백엔드 서빙 (출시 블로커 해소)

- **PR**: (main 직접 — 출시단계 재점검 후속)
- **Why**: 출시단계 readiness 재점검에서 GitHub Pages URL 3개 전부 **404**(미설정) 발견 — Play 는 개인정보 URL 필수 + 계정생성 앱은 계정삭제 URL 필수라 **출시 블로커**. 문서(`docs/store/*.md`)는 작성됐으나 호스팅이 안 됨.
- **What**: 회원님이 **백엔드 라우트** 방식 선택(AskUserQuestion) → FastAPI 공개 라우트 `GET /privacy`·`GET /account-deletion`(md→HTML 렌더, `markdown==3.10.2` 런타임 dep, 모바일 친화 템플릿). 백엔드 Docker 빌드 컨텍스트(`backend/`)가 repo 루트 `docs/store/` 에 접근 불가라 SSoT 유지하며 동기화: `scripts/sync-legal-docs.sh`(`docs/store/*.md` → `backend/app/legal/`) + drift 가드 `test_legal.py::test_legal_docs_in_sync_with_ssot`(openapi.json 패턴과 동일). 라우트 200/HTML/공개성 테스트 포함(pytest 79→**86**). openapi 재싱크(라우트 18→20).
- **Outcome**: 이미 배포된 always-on 백엔드 재사용이라 별도 호스팅·설정 0. Play 등록 URL = prod `.../privacy`·`.../account-deletion`. CLAUDE/README/operations-snapshot/play-store-release 엔드포인트·호스팅 섹션 갱신. **남은 출시 블로커 = 없음(코드/백엔드 측)**; 회원님 콘솔 작업(URL 등록·preflight 빌드·업로드·비공개테스트)만 남음.
- **Lessons**: ① **cp949 함정 재발(자책)**: requirements.txt 주석에 한국어·em-dash 넣어 pip-audit cp949 디코드 깨짐 — 문서화된 함정(INC-27)인데 또 만듦 → ASCII 영문으로 정정 + "keep ASCII" 주석 박제. requirements 주석은 항상 ASCII. ② **버전 추정 금지(자책)**: `types-Markdown` 버전을 검증 없이 추정해 존재하지 않는 버전 핀 → 설치 실패. pip index 로 실측 후 핀(회원님 지적). ③ 빌드 컨텍스트 제약(backend/)이 repo 루트 자산 접근을 막을 때 = openapi 식 sync+drift-guard 패턴이 정답(SSoT 유지).
- **Files touched**: `backend/app/routers/legal.py`(신규), `backend/app/legal/*`(동기화 사본), `scripts/sync-legal-docs.sh`(신규), `backend/app/main.py`, `backend/requirements.txt`(markdown)·`requirements-dev.txt`(types-Markdown), `backend/tests/test_legal.py`(신규), `backend/openapi.json`, docs(CLAUDE/README/operations-snapshot/play-store-release)

### 2026-06-18 — 백엔드 coverage 측정 코어 수정 (async 과소측정 root cause) + async 갭 테스트

- **PR**: (main 직접 — 출시 전 readiness 점검 후속, 커밋 별도)
- **Why**: 출시 전 테스트 커버리지 갭 분석 중 모순 발견 — `test_get_plan_not_found`(404 통과 = `raise NotFoundException` 반드시 실행)·`test_complete_*`(200 통과) 가 있는데도 coverage 가 해당 라인(service `raise`, router `return {"status":"ok"}`)을 미커버로 표시. 같은 함수에서 `await` 줄은 커버, **그 다음 줄만** 미커버 = 정상 실행 불가능한 패턴.
- **What (root cause)**: Python 3.12+ 의 coverage 기본 측정 코어 `ctrace`(sys.settrace 기반)가 **코루틴이 `await` 에서 재개된 직후 라인을 체계적으로 누락**. 실측 비교 — 기본 코어 87% vs `sysmon`(PEP 669 sys.monitoring) **96%**, 거짓 미커버 65줄이 전부 router/service 의 await 다음 줄. 공식 문서(coverage 7.14 `[run].core`) 확인 후 `pyproject.toml [tool.coverage.run] core = "sysmon"` 고정(env var 아닌 config → local+CI 일괄, branch/dynamic-context/concurrency 미사용이라 sysmon 제약 해당 없음). 거짓 미커버 제거로 드러난 **진짜 갭** 2건 테스트 추가: ① weekly_plan_service 7일 범위 검증(`0<=offset<7`, before/after 경계) ② account_service reaper fail-safe(Auth 확인 네트워크오류·비정상응답 → None=보존, 잘못된 삭제 방지). 결과 backend pytest 79→**81**, coverage 정확 측정 **~97%**.
- **Outcome**: CLAUDE/README/TRD 의 stale "~84%"(과소측정값) → "~97% (sysmon core)" 정정. CI Codecov 가 이제 정확한 async 커버리지 업로드. 남은 미커버는 대부분 infra(database.py engine 셋업·main.py lifespan) + account_service delete 중간실패 reraise(43-47, 강제 곤란).
- **Lessons**: ① async 코드 커버리지가 "실행되는데 미커버"로 보이면 측정 코어(ctrace vs sysmon)를 의심 — 코드/테스트 문제가 아니라 tracer 한계. ② 측정값 모순은 그 자체가 단서 — 404/200 이 나오면 해당 raise/return 은 반드시 실행됐다는 ground truth 로 tracer 를 fact-check(Rule 10 정신). ③ 거짓 미커버 노이즈가 진짜 갭을 가린다 — 정확한 tracer 가 갭 분석의 전제.
- **Files touched**: `backend/pyproject.toml`(core=sysmon + root cause 주석), `backend/tests/test_account.py`(reaper fail-safe), `backend/tests/test_edge_cases.py`(7일 범위), `CLAUDE.md`·`README.md`·`docs/TRD.md`(coverage 수치)

### 2026-06-17 — orphan reaper 운영화: Container Apps Job 프로비저닝 + 점검 하드닝 (PR #127)

- **PR**: [#127](https://github.com/gunnysis/eundunHealth/pull/127) (shipped, squash `0e6a99d`)
- **Why**: PR #126 머지된 orphan reaper 의 주기 자동화(Container Apps Job) + job 화 점검에서 발견한 하드닝.
- **What**: **하드닝** — reaper 트랜잭션 사용자단위 commit/에러격리(한 명 실패가 전체 sweep 안 막음) · 스크립트 self-locating(`import app` footgun 제거 + subprocess 가드) · requirements **cp949 디코드 가드** pre-commit(em-dash 차단; "non-ASCII 전부 차단"은 한국어 주석 오탐이라 cp949 디코드 가능성으로 정밀화). **프로비저닝** — `backend/reaper-job.yaml`(UAI registry/secret) + `scripts/setup-reaper-job.sh`(멱등 preflight/best-effort). UAI `id-eundunhealth-reaper` + 포털 역할(AcrPull·KV Secrets User) → `--yaml` create → 수동실행 **Succeeded**.
- **Outcome**: backend pytest 77 / android @Test 139, 전 게이트+CI green. 잡 주간 cron `0 18 * * 0` 가동(operations-snapshot §2). 프로비저닝 라이브 디버깅 4에러(E1 `--args -m` / E2 system MI chicken-egg→UAI-first / E3 개인 MSA RBAC CLI 불가→포털·SP / E4 job `--registry-identity`→`--yaml`)는 공식문서 fact-check 후 재발방지 런북 `docs/ops/azure-container-apps-jobs.md` 로 박제.
- **Lessons**: ① 인프라 프로비저닝은 라이브 실행이 가장 강한 fact-check(설계만으론 E2/E4 못 잡음). ② az containerapp **job** `--registry-identity`+UAI 는 문제 영역 → `--yaml registries[].identity`(공식 Bicep IaC 형태)로. ③ 개인 MSA 계정은 scope 지정 RBAC CLI 불가(포털/SP 필요, [[azure-cli-rbac-msa-limitation]]). ④ 인용은 페이지 단위 fact-check 후 확정(E4 "정확히 그 버그" 과장을 #1284 직접 확인으로 정정).
- **Files touched**: `backend/app/services/account_service.py`, `backend/scripts/reap_orphaned_accounts.py`, `backend/reaper-job.yaml`, `backend/tests/test_account.py`, `.githooks/pre-commit`, `scripts/setup-reaper-job.sh`, `docs/ops/azure-container-apps-jobs.md`(신규), `docs/ops/operations-snapshot.md`, `CLAUDE.md`

### 2026-06-17 — 출시 후 심층 감사 개선 (PR #126, v0.1.16)

- **PR**: [#126](https://github.com/gunnysis/eundunHealth/pull/126) (shipped, squash `df65d91`)
- **Why**: v0.1.15 출시 사이클(#122/#123/#125) 후 5-도메인 심층 재감사. 코드 건강·출시 차단 0건이었고, 신뢰성·성능·접근성·테스트 폴리시 개선 + 공식문서 fact-check 로 감사 발견 2건 정정.
- **What**: **Tier1(A~E)** — A JWKS 동기조회 `asyncio.to_thread` 오프로드+`PyJWKClient timeout 30→5s` / B `RetryInterceptor` 단위테스트 6(mockk Chain) / C `GoalScreen` silent-failure→`ErrorContent` / D `DayPlanCard` 포맷팅 `remember` / E 오늘의활동 a11y `mergeDescendants`. **Tier2/3** — 무테스트 VM 4종 특성화 테스트 · 백엔드 `pool_pre_ping` · **history COUNT `count(*) over()` 1쿼리화** · **`user_profile_history (user_id, recorded_at)` 복합 인덱스**(alembic `b78b256c2b20`) · **계정삭제 orphan reaper**(fail-safe + `_purge_app_data` DRY + `scripts/reap_orphaned_accounts.py`) · sentry-sdk 2.63.0 · i18n 의도 명문화. design+plan 페어 먼저 작성·검토 후 자율 구현(@Test 118→139, backend 71→75).
- **Outcome**: 전 게이트 green + CI green, squash 머지 → 백엔드 자동배포(룰7: entrypoint `alembic upgrade head` b78b256c2b20). Android C/D/E 는 preflight v0.1.16 빌드+Play 업로드 대기(회원님).
- **Lessons**: ① 감사 에이전트 보고는 공식문서로 재검증 필수 — PyJWKClient 기본 timeout 은 무한대 아닌 30s, Compose strong skipping(Kotlin 2.0.20+) 기본활성이라 stability config 불필요(Won't-do). ② `(col DESC)` 복합 인덱스 수식어는 단일방향 ORDER BY 엔 불필요(Postgres backward scan). ③ requirements 주석 em-dash(비-ASCII)가 cp949 환경 pip-audit 를 깨뜨림 → ASCII 유지. ④ 코드리뷰(high)가 GoalViewModel 부분실패 회귀(비핵심 차트 실패가 핵심 편집기 차단) 포착 → goals/history 분리로 근본수정. ⑤ alembic index-only 마이그레이션의 미사용 `import sqlalchemy as sa` 는 F401 — per-file ignore 확장 대신 제거(F401 은 스타일 UP/I 와 달리 실제 미사용 탐지라 보존).
- **Files touched**: `backend/app/{dependencies,main}.py`, `backend/app/services/{account,weekly_plan}_service.py`, `backend/app/repositories/{profile,weekly_plan}_repo.py`, `backend/app/models/user_profile_history.py`, `backend/alembic/versions/b78b256c2b20_*.py`, `backend/scripts/reap_orphaned_accounts.py`, `backend/requirements.txt`, `backend/tests/*`, `app/src/main/.../ui/{goal,home}/*`, `app/src/test/.../ui/*Test.kt`(6 신규), `version.properties`, docs(CHANGELOG/PRD/operations-snapshot/CLAUDE.md)

### 2026-06-16 — Sentry Alert 스크립트 점검·재발방지 개선 (commit d18e335)

- **Why**: `scripts/setup-sentry-alerts.ps1` 최초 실행 시 8개 룰 전부 404 실패. 스크립트 자체에 5개 버그가 잠복.
- **What**: ① B1 — PowerShell 변수명 case-insensitive 충돌: `$org`(API 응답)와 `$ORG`(org 슬러그)가 동일 변수로 취급되어 URI에 PS 객체 삽입 → `$orgInfo` 분리 + 모든 상수 `$script:` 명시. ② B2 — `environment="production"` 404: 첫 이벤트 수신 전 Sentry에 환경 미등록. environment 필드 제거. ③ B3 — `interval="30m"` 무효: `"1h"` 로 변경(유효값 `1m/5m/10m/1h/4h/24h/1w` 주석). ④ B4 — `dataset="transactions"` deprecated: `events_analytics_platform` + `is_transaction:true` + `p95(span.duration)`. ⑤ B5 — `targetType="team"`: 솔로 프로젝트에 팀 없음 → `targetType="user"` + `targetIdentifier=<sentry-user-id>`(user.id, 멤버 .id 아님). 구조 개선: `-DryRun` 플래그 + GET 기반 idempotency + `Get-SentryErrorDetail` 공통 헬퍼 + B1~B5 재발방지 주석블록.
- **Outcome**: Issue Alert 6 + Metric Alert 2 = 8개 알림 룰 Sentry UI 활성 확인. 잘못 생성된 Priority Notification 룰 2개(#3589906·#3589907) 삭제. `docs/ops/operations-snapshot.md §6·§13` + `CLAUDE.md` 스크립트 목록 + `docs/CHANGELOG.md` 갱신(commit `8978081`).
- **Lessons**: PowerShell 의 변수명 대소문자 무감지 특성은 외부 API 응답 수신 시 상수 변수명과 충돌 가능 — 응답 수신 변수는 항상 다른 이름 사용. Sentry API 는 첫 이벤트 전 environment 미등록·`transactions` dataset deprecated·`"30m"` 인터벌 무효 등 API 문서에서 찾기 어려운 계약이 있음 — 재발방지 주석이 필수. `-DryRun` 패턴은 실행 계획을 미리 검증하는 데 효과적.

### 2026-06-16 — 감사 후속 개선 백로그 구현 (TDD) — 코드·인프라

- **PR**: (branch `feature/audit-followup-backlog`) — 직전 동일자 "전수 점검" entry 의 **후속 개선 백로그 8항목**을 TDD 로 구현.
- **Why**: 전수 점검에서 발견·기록만 해둔 코드/인프라 개선을 사용자 요청("백로그 전부 TDD로")으로 실제 구현.
- **What**:
  - **[Backend][MED→done] day_plans 파싱 단일화**: `app/services/day_plans.py` 신규 — `parse_day_plans`(쓰기=400) + `parse_day_plans_or_none`(읽기=None). weekly_plan_service(`_validate_day_plans`·update_completion)·statistics_service(`_completion_rate`) 3중복 흡수. RED→GREEN(`test_day_plans.py` 7).
  - **[Backend][done] request-id 상관관계**: 모듈 레벨 `request_id_middleware`(룰 4) — `X-Request-ID` echo/생성 + `ContextVar` + 로그 포맷 `%(request_id)s` 필터. `logging.basicConfig` 를 lifespan→모듈 레벨(force=True) 이동(uvicorn 선점 no-op footgun 제거). RED→GREEN(`test_request_id.py` 2).
  - **[Android][MED→done] parseDayPlans 관측성**: `getOrDefault(emptyList)` → `getOrElse{ reportToSentry; empty }`. 손상 JSON 은 보고, 정상 `[]` 는 무보고. RED→GREEN(mockkStatic 2 테스트).
  - **[Android][test→done] PR #122 회귀 단언**: HomeViewModelTest 에 낙관적 토글 `manuallySet=true` + `updateDayCompletion(manual=true)` 직접 단언 2.
  - **[Android][refactor→done] createWeeklyPlan**: 3중복 fresh/seen 정렬을 `reorderExcludingPrevious` 헬퍼로 + 빈 풀 Sentry breadcrumb(부분 ExerciseDB 저하 관측).
  - **[Android][refactor→done] HomeScreen 분할**: 491→~230 LOC. `ui/home/components/`(HomeTopBarActions·TodayActivityCard·HealthConnectCards) 분리(`internal`, 동작 보존).
  - **[Infra][done] warm baseline 회귀 알림**: `scripts/check-warm-baseline.sh` + 스케줄드 `warm-baseline-check.yml`(매일) — `minReplicas<1`(scale-to-zero metric 사각지대) 감지 → 워크플로 실패 알림. operations-snapshot §10 유일 미관측 갭 해소.
  - **[CI/docs][done]**: backend.yml pip-audit `--strict` 의도 주석(보안 유지·INC-27 배경) / android.yml R8 미적용 갭 명시(룰 12=ProguardKeepRulesTest+실기기) / 버전 파일 표기 `backend/app/__init__.py:__version__` 정렬(glob 혼동 제거).
- **Outcome**: backend pytest **71 PASS**(62+day_plans 7+request_id 2) · ruff/mypy/bandit clean · openapi in-sync. Android `:app:testDebugUnitTest`+`detektDebug`+`spotlessCheck` 모두 BUILD SUCCESSFUL. TDD RED→GREEN 준수(behavior change), refactor 는 기존 green 하 유지.
- **Files touched**: backend/app/services/{day_plans(신규),weekly_plan_service,statistics_service}.py, backend/app/main.py, backend/tests/{test_day_plans,test_request_id}(신규), app/.../data/repository/WorkoutRepositoryImpl.kt(+Test), app/.../ui/home/HomeScreen.kt + ui/home/components/{HomeTopBarActions,TodayActivityCard,HealthConnectCards}(신규), app/.../ui/home/HomeViewModelTest.kt, scripts/check-warm-baseline.sh(신규), .github/workflows/{warm-baseline-check(신규),backend,android}.yml, CLAUDE/README/TRD/versioning/operations-snapshot(버전 표기)

### 2026-06-16 — 전수 점검 + 프로젝트 문서 최신화 (감사·드리프트 정정·bump-version 하드닝)

- **PR**: (docs/process — 본 커밋) · 트리거: 출시 사이클(PR #122/#123) 후 전체 점검 + 문서 최신화 요청
- **Why**: v0.1.14/v0.1.15 출시 후 **코드는 건강하나 문서가 5릴리스 드리프트**. 5-도메인 병렬 감사(Android·Backend·DB/API·Infra/CI·Docs) + controller fact-check(룰 10)로 확정.
- **What (정정)**: ① SSoT `operations-snapshot.md` — §1 버전 `0.1.13/27`→`0.1.15/29`, **`CORS_ORIGINS ["*"]`→`[]`**(보안 관련 stale, PR #123 반영), 산출물 v0.1.15(8.35MB·매핑 `1e11310d`)+단일 경로 명시, §13 이력 v0.1.11~v0.1.15 5행 추가. ② 테스트 수 `48/41`→`62`(CLAUDE/README×2/TRD), starlette `1.2.1`→`1.3.1`(CLAUDE/README/TRD), README versionCode 배지 `26`→`29`, CLAUDE 엔드포인트 `12개`→`14개` + 룰11 baseline `33`→`20`(2026-06-16 재측정), CLAUDE/PRD Play 상태 `0.1.14/28`→`0.1.15/29`. ③ TRD §4.4/§4.5 레거시 Ktor CORS/StatusPages → FastAPI 실제(`cors_origins=[]`·`@app.exception_handler`). ④ play-store-release 프로덕션 빌드 v0.1.13→v0.1.15(심사 취소 반영).
- **What (기록·재발방지)**: incident-log INC-25(R8 Gson keep 갭)·INC-26(토글 해제 미보존)·INC-27(bump-version blind-replace) 정식 기록. android/backend/dependencies ledger 에 PR #122/#123 entry 추가. **`bump-version.sh` 하드닝** — 전역 blind `s/OLD/NEW/g` 제거 → 앵커드 라인-스코프 치환(README 배지2·snap §1·PRD 제품버전 마커) + `git diff --stat` 출력. 임시본 시뮬레이션으로 "마커만 변경·과거 산문 무오염" 검증 후 적용. versioning.md §4/§7 에 경고 추가.
- **Decisions**: 코드 변경(리팩토링/silent-failure 수정 등)은 **본 작업에 미포함** — 요청 = "점검·분석 후 문서 최신화". 발견된 코드 개선은 아래 **백로그**로 기록(차후 별도 작업 시 권장 순서). 문서 편집은 outward-facing 아니라 push 전 사용자 확인만 대기.
- **후속 개선 백로그 (구현 미착수 — 감사 발견, severity 태그)**:
  - **[MED] silent empty data 관측성**: `WorkoutRepositoryImpl.parseDayPlans` 의 `getOrDefault(emptyList())` 가 손상 JSON 을 무음 폴백(INC-25 와 동일 실패 클래스, 텔레메트리 0). → 폴백 전 `toAppError().reportToSentry()`. 같은 파일 `createWeeklyPlan` 의 부분 풀 empty 도 breadcrumb.
  - **[테스트] PR #122 핵심수정 직접 커버 보강**: `HomeViewModelTest` 에 낙관적 토글 `manuallySet=true` 단언 + `CompletionRequest.manual` 전송 단언, `WorkoutRepositoryImplTest` 에 malformed-JSON→empty 폴백 테스트(현재 간접 커버).
  - **[리팩토링] Android**: `HomeScreen.kt`(491 LOC) → `TodayActivityCard`/HC 권한 프롬프트 분리. `createWeeklyPlan` 의 3중복 'exclude-then-reorder' 헬퍼 추출.
  - **[리팩토링] Backend**: `json.loads(day_plans)`+isinstance 가드 3곳(weekly_plan/statistics service) → 단일 `parse_day_plans()` 헬퍼(R8 빈계획과 동일한 brittle JSON 계약 표면).
  - **[모니터링] minReplicas 회귀 무알림**: warm baseline(`min=1`)이 scale-to-zero 로 무음 회귀해도 CA 가 metric 미emit → 알림 없음(헤드라인 신뢰성의 유일한 미관측 갭). → 스케줄드 `scale.minReplicas==1` 체크 또는 `/health` 지연 synthetic probe. (Task 8 미채택 이력과 연결.)
  - **[CI] 엣지**: `android.yml` 은 `assembleDebug` 만 → 실제 R8 stripping 미실행(룰 12 가드는 unit test 뿐). `backend.yml` pip-audit `--strict` 는 ignore allowlist 없음 → 무관 transitive CVE 가 deploy hard-block(의도면 문서화, INC-27 동반 bump 의 배경).
  - **[로깅] 백엔드**: plain string 로깅 + correlation-id 부재 → 다중 replica(1/3) 단일 요청 로그 상관 수동. `logging.basicConfig` 가 lifespan 내부라 uvicorn 선점 시 no-op 가능(footgun).
  - **[명료성] 버전 파일 표기**: 문서가 `backend/app/__version__` 로 부르나 실제 파일은 `backend/app/__init__.py`(`__version__`) — 미래 reader glob 혼동 방지로 표기 정렬 검토.
- **Outcome**: 8개 문서 정정 + 3 incident + 5 ledger entry + 스크립트 하드닝. 코드/계약/마이그레이션/openapi 는 감사 결과 **내부 일관**(alembic head `c849579de6c4` 단일 linear, openapi 18/18 synced, models=migrations). push 전 사용자 확인 대기.
- **Files touched**: docs/ops/{operations-snapshot,incident-log,play-store-release}.md, README.md, CLAUDE.md, docs/{PRD,TRD}.md, docs/conventions/versioning.md, docs/plans/logs/{android,backend,dependencies,process-infra}.md, scripts/bump-version.sh

### 2026-06-11 — 코드베이스 리팩토링 Bundle D (위생 정리)

- **PR**: [#107](https://github.com/gunnysis/eundunHealth/pull/107) (merged, squash `707d97f`)
- **Design/Plan**: `docs/plans/2026-06-11-codebase-refactoring-{design,plan}.md` (5-번들 이니셔티브 공통 페어 — 본 entry + backend/android ledger entry 로 아카이브)
- **Why**: "프로젝트 리팩토링" 진단(4-영역 병렬 감사) 결과 — 코드베이스는 건강(TODO 0·mypy strict·룰11 위반 0)하나 detekt 이중 baseline footgun(chronic CI 실패 history)·손수 404 처리·상수 미분리·hiltViewModel deprecation(1.3.0) 등 위생 항목 식별.
- **What**: ① detekt baseline 단일화(Option B) — CI 가 실제 쓰는 variant `baseline-debug.xml` 정식 추적 + vestigial `baseline.xml` 삭제 + gitignore 모순 해소(task wiring·분석 scope 무변경). ② `bodyOrNull404()` 헬퍼로 `UserRepositoryImpl.getProfile` 손수 404 when 사다리 통일. ③ NetworkModule 타임아웃·ExerciseDB URL 상수화. ④ `hiltViewModel` 신 패키지(`androidx.hilt.lifecycle.viewmodel.compose`) 이전 12파일 + `hilt-navigation-compose` 의존성 제거(잔여 0).
- **Decisions**: detekt = Option B(variant scope=generated srcDir 보존; A는 non-variant 재생성이 generated 미포함→`detektDebug` fail 위험으로 reject, 공식 precedence 폴백은 성립). hiltViewModel deprecation 은 메모리 주장을 공식 소스(androidx HiltViewModel.kt `@Deprecated`)로 fact-check 후 확정.
- **Outcome**: 게이트 green(spotlessCheck+detektDebug+testDebugUnitTest BUILD SUCCESSFUL) + CI Lint/Detekt/Test/Build·check-index pass. 동작 변화 0. design+plan 페어 동봉 머지.
- **Lessons**: subagent 측정 1건(baseline "52 entries·byte-identical")이 controller fact-check 에서 오류 판명(실제 67·줄바꿈만 상이·gitignore 모순) → 룰 10 적중. detekt AGP variant 는 base 에서 `baseline-<variant>.xml` 파생, 부재 시 base 폴백.
- **Files touched**: config/detekt/baseline-debug.xml(+baseline.xml 삭제), .gitignore, app/build.gradle.kts, gradle/libs.versions.toml, data/remote/util/ResponseExt.kt, data/repository/UserRepositoryImpl.kt, di/NetworkModule.kt, 12 ui Screen import
- **Postmortem**: (머지 + 7일 후. 특이사항 없으면 1줄.)

### 2026-06-10 — 앱 버전 명시 방식 종합 (version.properties SSoT + 백엔드 독립 버전 + 프론트 표시 + bump 자동화)

- **PR**: [#102](https://github.com/gunnysis/eundunHealth/pull/102) (merged, squash `1b3a06c`)
- **Design/Plan**: `docs/plans/2026-06-10-app-version-spec-{design,plan}.md` (본 entry 로 아카이브)
- **Why**: 버전 명시가 명문 정책 없이 수동 관리 → 마찰: `build.gradle.kts` 이력 주석블록(11줄)이 CHANGELOG 와 중복·drift / versionCode 수동 증가(과거 13/14 혼선) / 현재버전이 current-state 4문서에 산재(stale 정정 `e591877`) / 백엔드 `FastAPI()` version 미지정 → `openapi.json` info.version 이 기본값 `0.1.0` / 앱 UI 에 버전 표시 전무. 사용자 명시: "외부·공식 문서(kotlin, android) 참고하여 앱 버전 명시 방식 연구·설계" + 프론트 표시 포함.
- **What**: 루트 `version.properties` 앱 버전 SSoT(0.1.9/23 불변, `build.gradle.kts` 가 읽음, 주석블록 제거) / `backend/app/__version__="1.0.0"` 독립 API semver → `FastAPI(version=...)` + openapi 재싱크(info.version 0.1.0→1.0.0) / `ProfileScreen` 하단 `AppVersionLabel`(BuildConfig) / `scripts/bump-version.sh`(semver·단조 가드 + 문서 동기화 + `--dry-run`) / `docs/conventions/versioning.md` 정책 SSoT + CLAUDE.md 링크.
- **Decisions**: versionCode = **명시 정수 + 가드**(versionName 도출 공식 reject — 같은 versionName 재업로드 충돌, 과거 13→14) / 백엔드 = **독립 버전 1.0.0**(prod 운영 중, semver "in production → 1.0.0") / SSoT = `version.properties`(libs.versions.toml/buildSrc 대신 — 언어중립·스크립트친화) / 프론트 source = BuildConfig(컴파일 상수, `PackageInfoCompat` 는 대안) / 이력 SSoT = `docs/CHANGELOG.md` 단일.
- **Outcome**: PR CI 전부 green (android Lint/Detekt/Test/Build · backend Lint/Type/Test · Security · compose smoke · check-index). 게이트: 앱 spotless/detekt/test, 백엔드 pytest 48(+2)/cov 84%/ruff/mypy/bandit clean. BuildConfig 값 불변(0.1.9/23). 머지 후 백엔드 prod 배포는 **1차 Trivy 차단**(아래 Lessons 5) → Dockerfile 핫픽스(`5a78c69`) 후 재배포 성공. 라이브 검증: `/health` 200, openapi `info.version=1.0.0`.
- **Lessons**: (1) **팩트체크가 과장 교정**: 백엔드 버전→generated client "흘러들어감" 은 과장(openapi-generator 가 info.version 을 기능코드로 안 씀 = cosmetic) — 진짜 비용은 bump 시 openapi 재싱크(`backend.yml` drift 가드). (2) semver 1.0.0 은 강제 아닌 권고 — prod 인 백엔드가 1.0.0 의 더 강한 후보, 앱은 미-GA 라 0.x 정당. (3) **`.venv/Scripts/mypy.exe` 가 이 환경에서 0 bytes·exit 1 로 깨짐** → `python -m mypy` 사용(CLAUDE.md 명령 보강 후보). (4) drift 대상 = current-state 4문서(README/PRD/operations-snapshot/CLAUDE)뿐, append-only 이력문서는 보존. (5) **무고한 PR 이 base-image OS CVE 로 deploy 차단**: 버전 메타 변경뿐인 머지가 fresh 빌드를 트리거 → Trivy 가 base `python:3.12-slim` 의 openssl `CVE-2026-45447`(HIGH, fixed-available `deb13u1→deb13u2`)로 차단(HIGH:3). 의존성/Dockerfile 무변경인데 Trivy DB 일일 갱신 + Docker Hub base 재빌드 지연 탓. **해결 = Dockerfile 에 `apt-get update && apt-get upgrade` 레이어**(빌드 시 보안 패치 자가치유, 향후 base OS CVE 도 자동 흡수). Trivy 는 PR 에서 deploy job 자체가 skip → main 에서만 검증 가능(핫픽스 main-direct 정당화). 프로덕션은 push 전 차단이라 무영향.
- **Files touched**: `version.properties`(신규), `app/build.gradle.kts`, `app/.../ui/profile/ProfileScreen.kt`, `backend/app/__init__.py`, `backend/app/main.py`, `backend/openapi.json`, `backend/tests/test_app_version.py`(신규), `scripts/bump-version.sh`(신규), `docs/conventions/versioning.md`(신규), `CLAUDE.md`, `backend/Dockerfile`(Trivy 핫픽스 `5a78c69`), design+plan 페어(아카이브)
- **Postmortem**: (머지 + 7일 후 작성. 특이사항 없으면 1줄.)

### 2026-06-09 — Cold start 제거 + Key Vault full IaC (warm baseline + health probes)

- **PR**: [#92](https://github.com/gunnysis/eundunHealth/pull/92) scale fix · [#93](https://github.com/gunnysis/eundunHealth/pull/93) /health/ready · [#94](https://github.com/gunnysis/eundunHealth/pull/94) Task3 plan 하드닝 · [#95](https://github.com/gunnysis/eundunHealth/pull/95) plan sync · [#96](https://github.com/gunnysis/eundunHealth/pull/96) full IaC 컷오버 · [#97](https://github.com/gunnysis/eundunHealth/pull/97) deploy path hotfix — 모두 머지+배포완료
- **Design/Plan**: `docs/plans/2026-06-09-coldstart-warm-baseline-{design,plan}.md` (본 entry 로 아카이브)
- **Why**: 사용자 "로그인 느림" 반복 신고. 측정으로 근본원인 규명 — Supabase Auth 아님(warm 28ms), **백엔드 Container App scale-to-zero cold start 21,506ms**(로그인 직후 첫 백엔드 호출이 컨테이너 깨움). Entra External ID 전환 평가(수백 MAU 절감 $0 + 마이그레이션 큼) → **보류, A안(현행 유지 + cold start 해결)** 채택.
- **What**: Phase 1 = `min 1 / max 3` + http-concurrency scale rule(cold start 즉시 제거). `/health/ready` readiness probe(DB SELECT 1→200/503, overridable dependency 로 ASGITransport 테스트 가능성). Phase 2 = secret→Key Vault 참조(`kv-eundunhealth` + system MI + RBAC) · registries→MI pull · HTTP probe 3종 · committed `backend/containerapp.yaml`(라이브 spec 기반 단일 출처) · `backend.yml` `--yaml` 배포 전환 + KeyVault precheck. staging throwaway 앱으로 clobber/resolve 실증 후 정리. dep bump 5건 머지·2건 close.
- **Decisions**: A안(Entra 보류 — 트리거: 엔터프라이즈 SSO/Supabase 유료화/MFA·소셜 요구/출시 전) / KV = Standard·RBAC·90d·purge-protection / secret = **Key Vault 참조**(직접값/name-only gamble 회피) / YAML = **라이브 export 기반**(손YAML 의 CORS_ORIGINS·identity clobber 회피) / staging dry-run 게이트(`--yaml` what-if 부재) / **Task 8 Replicas 알림 미채택** — Replicas metric 이 scale-to-zero 시 미emit → min=0 회귀 감지 불가(false confidence). 회귀 가드 = IaC self-heal(매 배포 min=1 재적용) + 월간 점검.
- **Outcome**: prod 검증 — `/health`·`/health/ready` 200, secrets 4 KeyVault, registries identity=system, probes 3종, min1/max3, CORS_ORIGINS 등 env 보존(no clobber). 비용 ~37,700→~43,700원(budget 70,000 내). 로그인 느림 해소.
- **Lessons**: (1) **deploy path 버그**: staging 을 로컬(cwd=repo root)에서 테스트해 `backend/containerapp.yaml` 통과했으나 CI deploy job 은 `working-directory: backend` → `backend/backend/...` 로 실패(#96). → **CI invocation 경로까지 테스트**(아티팩트만 X). (2) **cp949 인코딩**: `az --yaml` 가 파일을 OS locale codec 으로 읽어 한글 주석에서 실패(Windows). CI(Ubuntu UTF-8) 무관하나 YAML 주석 ASCII 화. (3) **RBAC vault**: control-plane Owner ≠ data-plane → `secret set` 전에 self-grant Secrets Officer 필수(없으면 403). (4) **Git Bash MSYS**: resource-ID(`/subscriptions/...`) 인자가 망가짐 + `az keyvault show --query id` bash 에서 빈값 → resource-ID 명령은 PowerShell.
- **Files touched**: `backend/containerapp.yaml`(신규), `.github/workflows/backend.yml`, `backend/app/routers/health.py`, `backend/tests/test_health.py`, `backend/openapi.json`, `docs/ops/operations-snapshot.md`, `docs/ops/migration-runbook.md`(§7), design+plan 페어(아카이브)

## Older

- 2026-06-03 Azure Monitor Alerts (P1+P2) 프로비저닝 — Azure Monitor 알림 전무 — Sentry (앱 레벨) + GitHub Actions `/health` (배포 시점 1회) 만 존재.
- 2026-06-02 naming convention audit + PEP 257 enforce + automation infra ([#68](https://github.com/gunnysis/eundunHealth/pull/68)) — 5종 공식 명명/문서화 가이드 (JetBrains Kotlin / Google Android Style / PEP 8/257/484/526 / Microsoft CAF) 대비 코드+인프라 준수도 audit + PEP 257 docstring gap 해소 + 신규 코드/리소스 추가 시점에 자동 점검되는 인프라 보강.
- 2026-06-02 lessons-meta-rules (PR #68 lessons L2/L6 재발방지) ([#71](https://github.com/gunnysis/eundunHealth/pull/71)) — PR #68 작업의 7 lessons 중 자동 가드 채널 없는 2건 (L2 산수 미검증 / L6 subagent reviewer 측정 오류) 의 프로세스 룰화.
- 2026-06-02 lessons-infra-guards (PR #68 lessons L1/L4/L5/L7 재발방지) ([#70](https://github.com/gunnysis/eundunHealth/pull/70)) — PR #68 (naming convention audit) 작업의 7 lessons 중 자동 가드 채널 가능 4건을 가장 가까운 채널에 묶음.
- 2026-05-29 plans-ledger-restructure (hybrid 구조 도입) ([#57](https://github.com/gunnysis/eundunHealth/pull/NN)) — PR #48 의 frontmatter + INDEX 컨벤션 도입 후 운영 6주 동안 shipped 페어가 `docs/plans/` 루트에 누적되어 활성 plan 을 찾기 어려운 사용자 cognitive overload pain 발생.
- 2026-05-28 docs/plans/ frontmatter + 자동 INDEX + pre-commit hook + CI drift check 컨벤션 ([#48](https://github.com/gunnysis/eundunHealth/pull/48)) — docs/plans/ 의 design+plan 페어가 누적되며 status 추적 / 검색 / 인시던트 연관 정보가 흩어짐.
- 2026-05-28 MCP 통합 + 운영 자동화 (Phase 5 / 룰 6 / SessionStart) ([#46](https://github.com/gunnysis/eundunHealth/pull/46)) — Phase 5 운영 검증 (alembic head + 스키마 컬럼 + Sentry 신규 issue) 이 각 INC 마다 수동 반복.
