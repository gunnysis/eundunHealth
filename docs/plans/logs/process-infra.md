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

### 2026-09-02 — 레거시 잔여물 정리: 죽은 방화벽 규칙 제거 + KV 감사 공백 발견·신설

- **PR**: main 직접 (`059f72f` 설계·계획 + 실행 커밋)
- **Why**: 자격증명 회전 마무리 중 KV `database-url` 의 **옛 버전이 유출된 옛 암호를 담은 채 활성**인 것을 발견했다. 같은 성질의 잔여물을 전수 측정(L1~L8)했다.
- **What**: ① **L1** 옛 KV 버전만 `disabled`(삭제 아님 — 감사 추적 보존) ② **L3** PG 방화벽 `container-apps` 죽은 규칙 제거 ③ **L4** `kv-audit` 진단설정 **신설** ④ **L5** 문서 정정(secret 4→6) ⑤ **L8** 병합된 로컬 브랜치 2개 삭제.
- **Outcome**: 다운타임 0(`/health/ready` 200 × 3회 · reaper `eundunhealth-reaper-tjhwe9c` Succeeded). KV 활성 버전 2→1. 방화벽 규칙 2→1. **AC4 만 절반** — 진단설정은 생성됐으나 21분간 LA 에 `AzureDiagnostics` 가 생기지 않아 **로그 도착은 미실증**(공식 latency 상한 20분 초과). 설정은 유지한 채 다음 세션 재확인으로 넘겼다.
- **Lessons**:
  ① **"정리 대상" 목록을 만들면 "애초에 없는 것" 을 못 본다.** 이번 최대 발견은 지울 것이 아니라 **없던 것**이었다 — 문서 두 곳이 `kv-audit` 진단설정을 현재형으로 적었지만 실측은 4개 리소스 전부 **0건**이었다. 즉 자격증명을 방금 회전한 직후인데 "누가 언제 시크릿을 읽었는가" 를 조회할 수단이 없었다. 워크스페이스 이름까지 정확히 적혀 있어 더 안 보였다(그 워크스페이스는 실제로 쓰인다 — Container Apps `appLogsConfiguration` 용이고, **리소스 진단설정과는 별개**다).
  ② **죽은 안전장치는 없는 것보다 나쁘다.** `container-apps` 규칙은 **수신 IP** 를 등록해 아무 트래픽도 허용하지 않았는데, 목록에 있다는 이유로 "IP 로 좁혀져 있다" 는 오독을 만들었고 그 오독이 회전 설계 §5 에 그대로 박혔다. 그 전제로 `allow-azure-services` 를 지웠으면 프로덕션 장애였다. 제거하면서 **왜 죽은 규칙이었는지와 IP 를 좁힐 수 없는 구조적 이유**를 `operations-snapshot.md` 에 같이 박제했다.
  ③ **잔여물은 세 갈래로 갈린다** — 지울 것 / **남길 것**(코드의 `supabase` 문자열 6파일은 전부 이력 근거다. 지우면 왜 지금 구조인지가 사라진다) / 되돌릴 수 없어 **손대지 않을 것**(soft-delete 된 `supabase-*` 는 2026-12-01 자동 purge 로 해소되므로 아무것도 안 하는 게 기본안). 한 덩어리로 "청소" 하면 두 번째가 지워지고 세 번째가 사고가 된다.
  ④ **차단은 우회하지 않고 사람 몫으로 넘긴다.** `secret-file-guard` 훅이 두 번 막았고 같은 결과를 얻는 우회를 하지 않았다. 회원님이 직접 조회해 주셔서 절반이 채워졌고(환경 시크릿 6종 = 레거시 0건), 반대 방향 드리프트는 **미확인 항목으로 명시**해 남겼다.
- **CLI 함정 (실측)**:
  - `az postgres flexible-server firewall-rule delete` 의 규칙 이름은 **`-n`**, 서버는 **`--server-name`** 이다. `list` 는 `-s`/`-r` 이 통하는데 `delete` 는 안 되고, 에러가 `unrecognized arguments` 라 플래그 오타로 오인하기 쉽다.
  - `az monitor diagnostic-settings *` 는 Git Bash 에서 **`MSYS_NO_PATHCONV=1`** 없이는 리소스 ID(`/subscriptions/...`)를 Windows 경로로 변환해 실패한다. 이때도 메시지가 `usage error: --resource ID | ...` 라 인자 문제로 보인다 — **경로 변환이 원인**이다.
- **Files touched**: `CLAUDE.md`, `docs/ops/operations-snapshot.md`(방화벽 실상 박제 + audit 생성 시점 명기), `docs/plans/2026-09-02-legacy-residue-cleanup-{design,plan}.md`(본 entry 로 흡수, git rm)
- **남은 것**: AC4 로그 도착 재확인 · L2 purge 판단(기본안=방치) · L6 워크스페이스 재명명(Won't-do) · L8 `release.yml` 시크릿 드리프트(사람 몫)
- **설계 원문**: `2026-09-02-legacy-residue-cleanup-{design,plan}.md` (본 entry 로 흡수, git rm)

### 2026-09-02 — 유출된 운영 DB 자격증명 회전 (백업 아카이브 평문 노출)

- **PR**: main 직접 (`a26d9d2` 설계·계획·런북 + `c863631` 완료 마감)
- **Why**: 로컬 백업 아카이브 **두 벌**의 `.env` 에 현재 유효한 운영 PG 자격증명이 평문으로 있었다(sha256 대조로 동일 확인, 값 미출력). git 노출은 0 이었지만 **DB 가 공개 엔드포인트**였다 — `publicNetworkAccess: Enabled` + 방화벽 `allow-azure-services` `0.0.0.0–0.0.0.0` = **임의 Azure 테넌트의 리소스 통과**. 그 자격증명의 암호가 10자였다. 삭제만으로는 암호가 계속 유효하므로 회전이 필요했다.
- **What**: ① `.env` 2개 삭제(A2) ② PG 관리자 암호 회전 **10자→32자**(A3) ③ KV `database-url` 새 버전 ④ 소비자 **둘** 반영 — Container App `eundunhealth-api`(system MI) + Container Apps Job `eundunhealth-reaper`(UAI) ⑤ 30분 경과 후 버전 고정 해제(C1).
- **Outcome**: **다운타임 0** — 옛 복제본이 기존 SQLAlchemy 풀로 계속 서빙하는 동안 새 복제본이 올라왔다. `/health`·`/health/ready` 전 구간 200, reaper Job `Succeeded`, Sentry 신규 DB 인증 이슈 0건, 새 암호는 어느 로그·커밋에도 평문으로 남지 않았다(해시·길이만).
- **Lessons**:
  ① **버전 없는 KV 참조는 `revision restart` 로 갱신되지 않는다.** 공식 문서는 시크릿이 바뀌면 "Deploy a new revision" 또는 "Restart an existing revision" 하라고만 적었는데, 재시작한 새 복제본과 reaper Job 이 **둘 다** `asyncpg.exceptions.InvalidPasswordError` 로 죽었다. 30분 캐시가 옛 값을 들고 있다. 해법은 **시크릿 정의 자체를 바꾸는 것**(버전 id 로 고정 → 재해석 강제)이고, 30분 뒤 버전 없는 URI 로 되돌려 IaC 와 일치를 복원한다. 문서에 없는 동작이라 `operations-snapshot.md` §Key Vault 에 명령까지 런북화했다.
  ② **소비자를 하나로 세면 하나가 조용히 죽는다.** 같은 KV 시크릿을 앱과 주간 cron Job 이 공유하는데 Job 은 신원이 UAI 라 명령 형태가 다르다. 앱만 고쳤다면 그 주 일요일 18:00 UTC 에 reaper 가 실패했을 것이고, 그때는 회전과 연결짓기 어려웠을 것이다.
  ③ **FQDN 을 조립하지 말고 문서를 본다.** 검증 중 `/health` 가 `000`(연결 실패)로 나와 5분을 태웠다. 원인은 장애가 아니라 내가 환경 접미사(`livelyriver-782a792f`)를 빼고 FQDN 을 조립한 것이었다 — 값은 `operations-snapshot.md:45` 에 이미 있었다. 이 오류는 **HTTP 에러와 구별되지 않는 모양**으로 나타난다.
  ④ **롤백이 "앞으로만" 인 작업은 T3~T5 사이에 사람의 판단을 넣지 않는다.** 계획에 그렇게 박아두고 실행했더니, 중간의 검증 스크립트가 az 인용 문제로 죽었는데도 이미 성공한 T3 뒤에서 멈추지 않고 진행할 수 있었다.
- **Files touched**: `docs/plans/2026-09-02-db-credential-rotation-{design,plan}.md`(본 entry 로 흡수, git rm), `docs/ops/operations-snapshot.md`(§4 회전 이력 + §Key Vault 캐시 런북)
- **범위 밖(별건)**: `allow-azure-services` 규칙 제거(reaper 송신 IP 확인 선행) · `minimalTlsVersion` 설정 · Managed Identity 기반 PG 인증 전환(코드 변경 필요)
- **설계 원문**: `2026-09-02-db-credential-rotation-{design,plan}.md` (본 entry 로 흡수, git rm)

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

## Older

- 2026-06-18 출시 critical 점검: App Links Play 서명키 누락 + Sentry 매핑 게이트 — 회원님이 출시 점검을 여러 번 했는데도 문제 재발 → 핵심 지적: **로컬 테스트는 통과하지만 Play 배포본/승인에서만 터지는 critical** 을 봐야 한다("출시버전 빌드 후 승인 중 문제 = 가장 비쌈").
- 2026-06-18 세션 작업 사이드이펙트 리팩토링: HTML 라우트 openapi 제외 — 이번 세션에 추가한 legal 라우트(`/privacy`·`/account-deletion`, `text/html`)를 리팩토링/사이드이펙트 관점 재점검 → openapi.
- 2026-06-18 백엔드 coverage 측정 코어 수정 (async 과소측정 root cause) + async 갭 테스트 — 출시 전 테스트 커버리지 갭 분석 중 모순 발견 — `test_get_plan_not_found`(404 통과 = `raise NotFoundException` 반드시 실행)·`test_complete_*`(200 통과) 가 있는데도 coverage 가 해당 라인(service `raise`, router `return {"status":"ok"}`)을 미커버로 표시.
- 2026-06-18 공개 출시 전 전체 감사 (PR #128, v0.1.17) ([#128](https://github.com/gunnysis/eundunHealth/pull/128)) — Play Store 프로덕션 공개 출시 직전 7-도메인 전체 점검(보안·성능·에러UX·테스트·의존성·Play 컴플라이언스·코드품질).
- 2026-06-18 개인정보/계정삭제 페이지 백엔드 서빙 (출시 블로커 해소) — 출시단계 readiness 재점검에서 GitHub Pages URL 3개 전부 **404**(미설정) 발견 — Play 는 개인정보 URL 필수 + 계정생성 앱은 계정삭제 URL 필수라 **출시 블로커**.
- 2026-06-17 출시 후 심층 감사 개선 (PR #126, v0.1.16) ([#126](https://github.com/gunnysis/eundunHealth/pull/126)) — v0.
- 2026-06-17 orphan reaper 운영화: Container Apps Job 프로비저닝 + 점검 하드닝 (PR #127) ([#127](https://github.com/gunnysis/eundunHealth/pull/127)) — PR #126 머지된 orphan reaper 의 주기 자동화(Container Apps Job) + job 화 점검에서 발견한 하드닝.
- 2026-06-16 전수 점검 + 프로젝트 문서 최신화 (감사·드리프트 정정·bump-version 하드닝) — v0.
- 2026-06-16 감사 후속 개선 백로그 구현 (TDD) — 코드·인프라 — 전수 점검에서 발견·기록만 해둔 코드/인프라 개선을 사용자 요청("백로그 전부 TDD로")으로 실제 구현.
- 2026-06-16 Sentry Alert 스크립트 점검·재발방지 개선 (commit d18e335) — `scripts/setup-sentry-alerts.
- 2026-06-11 코드베이스 리팩토링 Bundle D (위생 정리) ([#107](https://github.com/gunnysis/eundunHealth/pull/107)) — "프로젝트 리팩토링" 진단(4-영역 병렬 감사) 결과 — 코드베이스는 건강(TODO 0·mypy strict·룰11 위반 0)하나 detekt 이중 baseline footgun(chronic CI 실패 history)·손수 404 처리·상수 미분리·hiltViewModel deprecation(1.
- 2026-06-10 앱 버전 명시 방식 종합 (version.properties SSoT + 백엔드 독립 버전 + 프론트 표시 + bump 자동화) ([#102](https://github.com/gunnysis/eundunHealth/pull/102)) — 버전 명시가 명문 정책 없이 수동 관리 → 마찰: `build.
- 2026-06-09 Cold start 제거 + Key Vault full IaC (warm baseline + health probes) ([#92](https://github.com/gunnysis/eundunHealth/pull/92)) — 사용자 "로그인 느림" 반복 신고.
- 2026-06-03 Azure Monitor Alerts (P1+P2) 프로비저닝 — Azure Monitor 알림 전무 — Sentry (앱 레벨) + GitHub Actions `/health` (배포 시점 1회) 만 존재.
- 2026-06-02 naming convention audit + PEP 257 enforce + automation infra ([#68](https://github.com/gunnysis/eundunHealth/pull/68)) — 5종 공식 명명/문서화 가이드 (JetBrains Kotlin / Google Android Style / PEP 8/257/484/526 / Microsoft CAF) 대비 코드+인프라 준수도 audit + PEP 257 docstring gap 해소 + 신규 코드/리소스 추가 시점에 자동 점검되는 인프라 보강.
- 2026-06-02 lessons-meta-rules (PR #68 lessons L2/L6 재발방지) ([#71](https://github.com/gunnysis/eundunHealth/pull/71)) — PR #68 작업의 7 lessons 중 자동 가드 채널 없는 2건 (L2 산수 미검증 / L6 subagent reviewer 측정 오류) 의 프로세스 룰화.
- 2026-06-02 lessons-infra-guards (PR #68 lessons L1/L4/L5/L7 재발방지) ([#70](https://github.com/gunnysis/eundunHealth/pull/70)) — PR #68 (naming convention audit) 작업의 7 lessons 중 자동 가드 채널 가능 4건을 가장 가까운 채널에 묶음.
- 2026-05-29 plans-ledger-restructure (hybrid 구조 도입) ([#57](https://github.com/gunnysis/eundunHealth/pull/NN)) — PR #48 의 frontmatter + INDEX 컨벤션 도입 후 운영 6주 동안 shipped 페어가 `docs/plans/` 루트에 누적되어 활성 plan 을 찾기 어려운 사용자 cognitive overload pain 발생.
- 2026-05-28 docs/plans/ frontmatter + 자동 INDEX + pre-commit hook + CI drift check 컨벤션 ([#48](https://github.com/gunnysis/eundunHealth/pull/48)) — docs/plans/ 의 design+plan 페어가 누적되며 status 추적 / 검색 / 인시던트 연관 정보가 흩어짐.
- 2026-05-28 MCP 통합 + 운영 자동화 (Phase 5 / 룰 6 / SessionStart) ([#46](https://github.com/gunnysis/eundunHealth/pull/46)) — Phase 5 운영 검증 (alembic head + 스키마 컬럼 + Sentry 신규 issue) 이 각 INC 마다 수동 반복.
