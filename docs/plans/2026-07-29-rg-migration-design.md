---
type: design
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [azure, infra, resource-group, rbac, migration]
---

# 리소스 그룹 이관 (`apps` → `rg-eundunhealth-prod-krc`) 설계

- **작성일**: 2026-07-29
- **상태**: 작성 중 (회원님 검토 대기)
- **연관 작업**: 회원님 Cloud Shell 배치 이동 시도 → `ResourceMoveValidationFailed` 9건 (tracking `4d6673d5-a2f9-4721-b418-cbf4681e7338`)
- **대상 버전**: infra-only (앱/백엔드 코드 변경 없음)
- **선행 작업**: 없음 (대상 RG `rg-eundunhealth-prod-krc` 생성 완료 — MEASURED: `az group show` → koreacentral / Succeeded)

## 1. 배경

- 운영 리소스 17개가 범용 이름의 RG `apps` 에 있음. CAF 명명 컨벤션(`rg-<workload>-<env>-<region>`)에 맞춘 `rg-eundunhealth-prod-krc` 로 이관 목적.
- 회원님의 전체 배치 이동 시도가 검증 단계에서 거부됨(전체 배치 all-or-nothing — **아무것도 이동되지 않음**, 현재 상태 무변화). 거부 원인 9건 = 이동 미지원 3타입:
  - `Microsoft.Insights/activityLogAlerts` 4개 + `Microsoft.Insights/metricalerts` 4개 (알림 8개)
  - `Microsoft.ManagedIdentity/userAssignedIdentities` 1개 (`id-eundunhealth-reaper`)
- **전제**: 서비스 사용자 0명 → 다운타임 허용 (회원님 확인, 2026-07-29). 단 데이터 손실 경로는 만들지 않는다(프로덕션 DB·KV 보존).

### 실측 리소스 인벤토리 (MEASURED: `az resource list -g apps` = 17개)

| 분류 | 리소스 | 타입 | 이동 |
|---|---|---|---|
| 이동 대상 (7) | `healthapp` | DBforPostgreSQL/flexibleServers | ✅ |
| | `eundunhealthacr` | ContainerRegistry/registries | ✅ |
| | `kv-eundunhealth` | KeyVault/vaults | ✅ |
| | `eundunhealth-env` | App/managedEnvironments | ✅ |
| | `eundunhealth-api` | App/containerApps | ✅ |
| | `eundunhealth-reaper` | App/jobs | ✅ |
| | `workspace-appsDOlM` | OperationalInsights/workspaces | ✅ |
| 삭제 후 재생성 (9) | `ag-eundunhealth-prod` | Insights/actiongroups | 재생성(D2) |
| | `alert-*` 8개 | Insights/activityLogAlerts·metricalerts | 이동 불가 → 재생성 |
| 별도 결정 (1) | `id-eundunhealth-reaper` | ManagedIdentity/userAssignedIdentities | 이동 불가 → D3 |

이동 가능 근거: ① ARM 배치 검증이 위 9건**만** 거부(= 나머지는 검증 통과, MEASURED — 회원님 실행 에러 원문) ② 공식 move-support 표 재확인 — `DBforPostgreSQL/flexibleServers` Yes·`App/managedEnvironments` Yes·알림(`activityLogAlerts`/`metricalerts`)·UAI No. **주의**: 표의 Microsoft.App 섹션에는 `managedEnvironments` 만 명시되어 있고 `containerApps`/`jobs` 는 미기재 — 이 둘의 실질 근거는 ARM 검증 통과이며, Task 2 의 `validateMoveResources` 사전검증(공식 REST 계약: 202 → Location 폴링 → 204=성공/409=실패)이 이를 실행 직전 재확인한다. env·app·job 은 **같은 배치로 함께 이동**한다(의존 리소스 동반 원칙).

### 이동해도 변하지 않는 것 (팩트체크 완료)

- 리소스 **이름·데이터·설정** 전부 보존 — 이동은 메타데이터 작업, 리소스 ID 의 RG 경로만 변경.
- **FQDN** `eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io` (env defaultDomain 기반, RG 무관) → **Play 등록 URL(/privacy·/account-deletion)·App Links assetlinks·Supabase redirect·앱 BACKEND_URL 전부 무영향**.
- PG 호스트명(`healthapp.postgres.database.azure.com`)·ACR login server·KV URI — 이름 기반이라 불변.
- system MI **principalId 불변**(identity 는 리소스를 따라감 — 공식 문서 확인). GitHub OIDC federated credential 도 RG 무관(Entra 앱 대상).

### 이동이 깨뜨리는 것 (본 설계가 다루는 전부)

1. **리소스 범위 RBAC 역할 할당 orphan** — 공식 문서: "이동 시 역할 할당은 이동되지 않고 orphan, 재생성 필요". MEASURED (`az role assignment list --all`) 영향 8건:

   | 주체 | 역할 | 범위 | 비고 |
   |---|---|---|---|
   | CI SP (`39b73fb8-…`) | Contributor | RG `apps` | RG 자체는 안 옮기므로 orphan 은 아니나 무용 → **새 RG 에 Contributor 신규 부여** |
   | CI SP | AcrPush | ACR | orphan → 재부여 |
   | CI SP | Key Vault Secrets User | KV | orphan → 재부여 |
   | system MI `eundunhealth-api` (`a4784428-…`) | AcrPull | ACR | orphan → 재부여 (미부여 시 재시작/배포에서 이미지 pull 실패) |
   | system MI | Key Vault Secrets User | KV | orphan → 재부여 (미부여 시 secret resolve 실패) |
   | reaper UAI (`8f560813-…`) | AcrPull | ACR | orphan → 재부여 (D3 채택안의 주체에게) |
   | reaper UAI | Key Vault Secrets User | KV | orphan → 재부여 |
   | 회원님 계정 | Key Vault Secrets Officer | KV | orphan → 재부여 (secret 수동 관리용) |

2. **알림 8개 + Action Group** — 알림은 이동 불가이고, 이동 가능하더라도 감시 대상 리소스 ID(RG 경로 포함)가 바뀌어 무효. 전부 삭제 후 새 RG 기준으로 재생성.
3. **Log Analytics workspace shared key 재생성** (공식 문서 확인, 조사로 신규 발견) — workspace 이동 시 primary/secondary key 가 **재생성**된다. Container Apps env(`eundunhealth-env`)는 `customerId + sharedKey` 로 로그를 전송하므로(customerId 는 불변, key 는 무효화) 이동 후 `az containerapp env update --logs-workspace-id/--logs-workspace-key` 로 새 key 반영 필요. 미반영 시 콘솔 로그 유입 중단(데이터플레인은 정상). 또한 workspace 이동은 **linked service(Automation 등)가 없어야** 가능 — Task 0 에서 사전 확인(DEFERRED — verify at Task 0). 기존 로그 데이터·보존 설정은 무영향.
4. **저장소·로컬 스크립트의 RG 하드코딩** — MEASURED (grep): 기능 파일 8곳 + 문서 6종 + 저장소 외 1곳(§5.3).

## 2. Scope

### In-scope
- 이동 가능 7개 리소스의 RG 이동 + 사전 validate
- RBAC 재부여 체크리스트 (실행 주체: 회원님 포털 — CLI 선시도 후 폴백)
- 알림 8개+AG 삭제·재생성 (`setup-azure-alerts.sh` RG 파라미터화)
- reaper UAI 처리 (D3)
- 저장소/로컬 스크립트/문서의 RG 참조 일괄 갱신 + 커밋

### Out-of-scope
- 리소스 이름 변경 (이유: rename 은 Azure 미지원, 별도 재생성 작업이며 본 건 목적 아님)
- 리전 이동 (이유: 동일 koreacentral)
- 앱/백엔드 코드·버전 변경 (이유: 순수 인프라 메타데이터 작업)
- 과거 기록 문서(incident-log, ledger, CHANGELOG)의 `apps` 표기 수정 (이유: 역사적 사실 — 당시 RG 이름이 맞음)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 이동 vs 전체 재생성 | **이동** | PG 데이터·KV secret·ACR 이미지 보존. 재생성은 데이터 마이그레이션 필요 = 리스크·작업량 과다 |
| D2 | 알림+AG 처리 | **이동 전 `--delete` → 이동 후 새 RG 재생성** | 알림은 이동 불가+ID 참조 무효. AG 는 이동 가능하지만 스크립트가 완전 IaC 라 삭제·재생성이 단순·일관. 공식 문서도 orphan 참조는 이동 전 정리 권장 |
| D3 | reaper UAI | **B안: 새 RG 에 신규 생성 + job 재배선 + 구 UAI 삭제** (§4) | 이관 목적 = RG 정리. A안(잔류)은 `apps` RG 가 영구 존속해 목적 반감. 실패 시 A안 폴백 명시 |
| D4 | RBAC 재부여 방법 | **CLI 1회 시도 → 실패 시 포털(회원님)** | 개인 MSA 계정의 `az role assignment create` 제약([[azure-cli-rbac-msa-limitation]]) — 단 이번 세션에서 `list` 는 정상 동작 확인, create 도 시도 가치 있음. 실패해도 무해(즉시 포털 폴백) |
| D5 | 실행 순서 | 알림 삭제 → validate → 이동 → LA key 갱신 → RBAC → repo 갱신 → 알림 재생성 → e2e | validate(사전검증) 로 2차 거부 방지, LA key 는 이동 즉시 무효화되므로 최우선 복구, RBAC 를 repo 갱신보다 먼저 해 배포 검증 가능 상태 확보 |
| D6 | `setup-azure-alerts.sh` RG | **`RG="${RG:-rg-eundunhealth-prod-krc}"` env-override 로 변경** | 구 RG 삭제 시 `RG=apps bash … --delete` 1회성 실행 가능 + 하드코딩 제거 |

## 4. 옵션 비교 (D3 — reaper UAI)

| 옵션 | A. `apps` 에 잔류 | B. 새 RG 신규 생성 + 재배선 (채택) |
|---|---|---|
| 작업량 | 0 | UAI 생성(CLI 가능) + RBAC 2건 + `reaper-job.yaml` identity 교체 + job `--yaml` 업데이트 + 구 UAI 삭제 |
| 결과 상태 | RG 2개 존속(apps 에 UAI 1개) | 단일 RG, `apps` 삭제 가능 |
| 리스크 | 없음 | job identity 교체가 az CLI 함정(E4 계열) 만날 가능성 → `--yaml` 경로 사용(이미 실증된 우회) |
| 폴백 | — | 재배선 실패 시 A안으로 전환(구 UAI 유지, cross-RG 참조는 지원됨) |

## 5. 구성 요소별 변경

### 5.1 Azure (스크립트 아닌 1회성 CLI — plan 에 명령 전문)

1. 알림+AG 삭제: `RG=apps bash scripts/setup-azure-alerts.sh --delete` (D6 반영 후)
2. 사전검증: `validateMoveResources` REST (공식 계약 — POST body `{resources, targetResourceGroup}` → 202 + `Location` 헤더 → 폴링 202(진행)→**204=통과 / 409=실패+사유**). `az rest` 는 응답 헤더 접근이 불편하므로 `az account get-access-token` + `Invoke-WebRequest` 사용 (plan Task 2 스니펫)
3. 이동: `az resource move --destination-group rg-eundunhealth-prod-krc --ids <7개>` (이동 중 양쪽 RG 관리 잠금 최대 4h — 데이터플레인 정상, 배포 금지. CLI 는 완료까지 블로킹 — 장시간 대비 백그라운드 실행)
4. LA workspace 새 shared key 조회 → `az containerapp env update -n eundunhealth-env -g <새RG> --logs-workspace-id <customerId> --logs-workspace-key <새key>` → 로그 유입 재확인
5. RBAC 재부여: §1 표 8건 (CLI 시도 → 포털). **전파 지연 최대 ~10분 + identity token cache 최대 24h**(공식) — 검증 실패 시 대기 후 재시도 or 새 revision
6. UAI B안: `az identity create -n id-eundunhealth-reaper -g rg-eundunhealth-prod-krc` → RBAC 2건 → job `--yaml` 업데이트 → 구 UAI 삭제
7. 알림 재생성: `bash scripts/setup-azure-alerts.sh` (새 기본 RG)
8. (B안 완료 후) `az group delete -n apps` — **빈 RG 확인 후에만**

### 5.2 저장소 파일 (MEASURED: grep `-g apps|--resource-group apps|RESOURCE_GROUP|resourceGroups/apps`)

| 파일 | 변경 |
|---|---|
| `.github/workflows/backend.yml:29` | `RESOURCE_GROUP: apps` → 새 RG |
| `.github/workflows/warm-baseline-check.yml:39` | 동일 |
| `backend/containerapp.yaml:15` | `managedEnvironmentId` 경로의 RG |
| `backend/reaper-job.yaml` (5곳) | environmentId + UAI ID 경로 (B안: UAI 도 새 RG) |
| `scripts/setup-azure-alerts.sh:36` | D6 env-override |
| `scripts/setup-reaper-job.sh:42` | `RG=apps` → 새 RG |
| `scripts/hooks/secretref-guard.sh:45,67` | `-g apps` 2곳 |
| `.claude/commands/naming-audit.md` | 조회 명령 3곳 |
| 문서: `CLAUDE.md`·`README.md`·`docs/ops/operations-snapshot.md`·`monitoring-and-cost.md`·`migration-runbook.md`·`azure-container-apps-jobs.md`·PR template | 현재 상태 서술의 RG 표기 (과거 기록 제외 — Out-of-scope) |

### 5.3 저장소 외 로컬

- `C:/programming/docker/eundunhealth-api/redeploy.sh:15` — `RESOURCE_GROUP="apps"` → 새 RG

## 6. 검증 계획

| 항목 | 방법 |
|---|---|
| 이동 완료 | `az resource list -g rg-eundunhealth-prod-krc` = 7개(+재생성분), `-g apps` = 0개(B안 완료 시) |
| RBAC | `az role assignment list --all` 로 8건 신규 범위 확인 (list 는 MSA 로 동작 — 본 세션 MEASURED) |
| 서비스 | prod `/health`·`/health/ready` 200 |
| 배포 경로 e2e | `gh workflow run backend.yml` → deploy job success |
| 알림 | metric 4 + activity-log 4 enabled (새 RG 조회) |
| reaper | `az containerapp job start` 수동 실행 → Succeeded |
| LA 로그 유입 | env key 갱신 후 Log Analytics 에서 `ContainerAppConsoleLogs_CL` 최근 레코드 조회 (또는 `az containerapp logs show` 정상) |
| 정기 가드 | `warm-baseline-check.yml` 수동 트리거 green |

### 6.X 추정값 라벨 (룰 9)
- 리소스 17개 / 이동 7개 / 알림+AG 9개 / RBAC 8건 / repo 기능 파일 8곳: **MEASURED** (명령 §1·§5)
- validateMoveResources 통과 여부: **DEFERRED — verify at Phase 1** (plan Task 2)
- LA workspace linked service 부재(이동 전제조건): **DEFERRED — verify at Task 0** (`az monitor log-analytics workspace linked-service list`)
- 이동 소요 시간: **ESTIMATE-ONLY** (수 분~수십 분, 최대 4h 잠금 허용치; LA workspace 이동은 공식 문서상 "a few hours" 가능)

## 7. 롤백 절차

1. 이동 실패(부분 이동): 실패 리소스는 소스에 잔류(공식 동작) → 남은 것만 재시도 or 이동분을 `--destination-group apps` 로 역이동(7개 전 타입 양방향 이동 가능).
2. 역이동 시 RBAC: 재부여한 8건을 동일 절차로 원위치(포털).
3. repo: 커밋 전이면 `git checkout -- .`, 커밋 후면 revert 커밋. 알림: `RG=<원위치> setup-azure-alerts.sh` 재실행.
4. 데이터 리스크 없음 — 전 과정에 삭제 대상은 알림/AG(재생성 가능)와 (B안) 구 UAI뿐. PG/KV/ACR 은 삭제 경로 자체가 없음.

## 8. 잔여 리스크

- 이동 작업 중 양쪽 RG 관리 잠금(최대 4h) — 그동안 배포·설정 변경 불가. 사용자 0명 전제로 수용.
- RBAC 재부여 전 Container App 이 재시작되면 이미지 pull/secret resolve 실패로 replica 기동 실패 가능 — 수용(다운타임 허용) + 재부여 후 revision 재시작으로 복구. 참고: KV versionless 참조는 30분 주기로 최신 버전 sync + 자동 재시작(공식) — sync 실패는 기존 캐시 값 유지이므로 러닝 replica 를 죽이지는 않으나, 재부여를 이동 직후 신속히 완료할 근거.
- RBAC 전파 지연: 역할 부여 후 최대 ~10분 + managed identity token cache 최대 24h(공식 트러블슈팅 문서) — 재부여 직후 검증 실패는 "미부여" 아니라 전파 지연일 수 있음. 대기 후 재시도 or 새 revision 배포로 강제 갱신.
- LA env key 갱신(§5.1-4) 전까지 콘솔 로그 유입 공백 — 데이터플레인 무영향, Sentry(별도 채널)는 정상이므로 관측성 공백은 짧게 수용.
- job identity 재배선(B안)의 az CLI 함정 가능성 — `--yaml` 경로 + A안 폴백으로 경감.
- Cloud Shell 과 로컬 az 컨텍스트 차이 없음(동일 구독 `6890144c-…` 확인).

## 9. 참고 자료 (2026-07-29 공식 문서 조사)

- Move resources to a new resource group or subscription — learn.microsoft.com/azure/azure-resource-manager/management/move-resource-group-and-subscription (checklist #3 역할 할당 orphan / REST validateMoveResources 202→204·409 / FAQ 4h 잠금·800개 제한)
- Move operation support for resources — …/move-support-resources (DBforPostgreSQL flexibleServers Yes · Microsoft.App managedEnvironments Yes · Insights 알림 계열 No · ManagedIdentity No)
- Move a Log Analytics workspace — learn.microsoft.com/azure/azure-monitor/logs/move-workspace (**key 재생성** · linked service 사전 제거 · 데이터/보존 무영향 · 소요 few hours)
- Troubleshoot Azure RBAC — …/role-based-access-control/troubleshooting ("Role assignment isn't moved after moving a resource" → orphan, 재생성 필요)
- Container Apps: manage-secrets (KV versionless 참조 30분 sync + 자동 재시작) / deployment-errors (KV 접근 실패 원인표 · RBAC 전파 ~10분 · token cache 24h)
- Troubleshoot: resource type not supported for move — 공식 해법 = 미지원 타입 제외 후 이동 + 이동 후 재생성 (본 설계 D2 와 동일 접근)
- 본 저장소: `docs/ops/azure-container-apps-jobs.md` (E1~E4), `scripts/setup-azure-alerts.sh`, 메모리 [[azure-cli-rbac-msa-limitation]]
