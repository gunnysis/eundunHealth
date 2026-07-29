---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [azure, infra, resource-group, rbac, migration]
---

# 리소스 그룹 이관 (`apps` → `rg-eundunhealth-prod-krc`) Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 운영 리소스 17개를 `apps` → `rg-eundunhealth-prod-krc` 로 이관 (이동 7 + 재생성 9 + UAI 재배선 1) + RBAC 재부여 + repo/스크립트/문서 RG 참조 갱신.

**Architecture (요약):** 배치 이동은 이동 가능 타입만 필터링(all-or-nothing 회피). 알림·AG 는 IaC 스크립트로 삭제→재생성, UAI 는 신규 생성 후 job `--yaml` 재배선(B안, 실패 시 A안 폴백). RBAC 는 CLI 선시도→포털 폴백(회원님).

**Tech Stack:** az CLI / bash (Git Bash) / GitHub Actions

**참고:**
- Design: `docs/plans/2026-07-29-rg-migration-design.md`
- Branch: main 직접 (infra+docs, PR 불요 — 단 push 전 회원님 확인)
- 상수: `NEW_RG=rg-eundunhealth-prod-krc`, `SUB=6890144c-c79e-46fc-a830-33335e8b4165`

**중요 원칙:**
- 각 Azure 상태 변경 직전 Destructive 5문항(CLAUDE.md) 자문. 삭제 대상은 알림/AG/(B안)구 UAI 뿐.
- Task 3(이동) 이후 Task 5(RBAC) 완료까지 **배포·Container App 재시작 금지**.
- Windows 호스트: bash 스크립트는 Git Bash(`MSYS_NO_PATHCONV=1` 은 스크립트 내장), az 단독 명령은 pwsh.

**Task 순서:**

```
Task 0  사전 스냅샷 + LA workspace linked-service 확인 (읽기 전용)
Task 1  setup-azure-alerts.sh RG env-override 수정 (D6)
Task 2  알림+AG 삭제 → validateMoveResources 사전검증 (202→Location 폴링→204)
Task 3  7개 리소스 이동 + LA workspace 새 key 를 env 에 반영 + 이동 결과 검증
Task 4  [회원님] RBAC 재부여 (CLI 선시도 → 포털 체크리스트, 전파 ~10분 유의)
Task 5  UAI B안: 신규 생성 + RBAC + reaper job 재배선 + 구 UAI 삭제
Task 6  repo/로컬 RG 참조 일괄 갱신
Task 7  알림 재생성 + e2e 검증 (backend.yml·/health·reaper·LA 로그·warm-baseline)
Task 8  apps RG 삭제(빈 것 확인 후) + 문서 갱신 + 커밋/push
```

---

## Phase 1: 준비·이동

### Task 0: 사전 스냅샷 + LA 이동 전제조건 확인 (pwsh, 읽기 전용)

```powershell
az resource list -g apps -o json > pre-move-apps-inventory.json   # scratchpad 에 보관
az role assignment list --all -o json > pre-move-rbac.json
az containerapp show -n eundunhealth-api -g apps --query "properties.runningStatus"
# LA workspace 이동 전제조건: linked service 없어야 함 (공식 move-workspace 문서)
az monitor log-analytics workspace linked-service list -g apps --workspace-name workspace-appsDOlM -o table   # 기대: 빈 목록
# 이동 후 대조용 현재 로그 설정 스냅샷
az containerapp env show -n eundunhealth-env -g apps --query "properties.appLogsConfiguration" -o json
```
확인: 17개·RBAC 8건·앱 Running·linked service 0건. linked service 가 있으면 **STOP** — 제거 절차(공식 문서) 별도 검토 후 진행.

### Task 1: `scripts/setup-azure-alerts.sh` D6 수정

**Files:** `scripts/setup-azure-alerts.sh:36`

`RG="apps"` → `RG="${RG:-rg-eundunhealth-prod-krc}"`. 주석의 "RG `apps` 존재" 전제도 갱신. (커밋은 Task 8 에서 일괄)

### Task 2: 알림+AG 삭제 + 이동 사전검증 (bash)

```bash
RG=apps bash scripts/setup-azure-alerts.sh --dry-run   # 삭제 대상 확인
RG=apps bash scripts/setup-azure-alerts.sh --delete    # 알림 8 + AG 삭제
```

validateMoveResources (pwsh) — 공식 REST 계약(202 Accepted + `Location` 헤더 → 폴링 202=진행/204=통과/409=실패+사유). `az rest` 는 응답 헤더를 노출하지 않으므로 토큰 + `Invoke-WebRequest` 사용:
```powershell
# 알림·AG 삭제 후 남는 제외 대상은 UAI 하나뿐 — 필터 단순화 + 개수 가드
$ids = az resource list -g apps --query "[?type!='Microsoft.ManagedIdentity/userAssignedIdentities'].id" -o json | ConvertFrom-Json
$ids.Count   # 기대: 7. 아니면 STOP (알림/AG 삭제 미완 또는 예상 밖 리소스)

$SUB = "6890144c-c79e-46fc-a830-33335e8b4165"
$token = az account get-access-token --query accessToken -o tsv
$body = @{ resources = $ids; targetResourceGroup = "/subscriptions/$SUB/resourceGroups/rg-eundunhealth-prod-krc" } | ConvertTo-Json
$resp = Invoke-WebRequest -Method Post -SkipHttpErrorCheck `
    -Uri "https://management.azure.com/subscriptions/$SUB/resourceGroups/apps/validateMoveResources?api-version=2022-09-01" `
    -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $body
$loc = $resp.Headers.Location   # 202 기대
do {
    Start-Sleep -Seconds 15
    $poll = Invoke-WebRequest -Method Get -Uri "$loc" -Headers @{ Authorization = "Bearer $token" } -SkipHttpErrorCheck
} while ($poll.StatusCode -eq 202)
$poll.StatusCode   # 204 = 통과. 409 = 실패 → $poll.Content 의 error 사유 확인 후 STOP
```
> `az resource list` 의 type 문자열은 실측 casing(`Microsoft.ManagedIdentity/userAssignedIdentities`) 기준 — JMESPath 는 대소문자 구분. 개수(7) 가드가 casing 오타의 안전망.

### Task 3: 이동 실행 + LA key 반영 + 검증 (pwsh)

**Step 1 — 이동** (CLI 는 완료까지 블로킹, 최대 4h 허용치 — 백그라운드 실행 + 완료 대기):
```powershell
az resource move --destination-group rg-eundunhealth-prod-krc --ids $ids   # 백그라운드로 실행, 수 분~수십 분
az resource list -g rg-eundunhealth-prod-krc --query "length(@)"   # = 7
az resource list -g apps --query "[].name"                          # = id-eundunhealth-reaper 만
curl https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health   # 200 (기존 replica 는 계속 동작)
```

**Step 2 — LA workspace 새 shared key 를 env 에 반영** (이동 시 key 재생성 — 공식 move-workspace 문서, design §1-3):
```powershell
$NEW_RG = "rg-eundunhealth-prod-krc"
$cid = az monitor log-analytics workspace show -g $NEW_RG -n workspace-appsDOlM --query customerId -o tsv
$key = az monitor log-analytics workspace get-shared-keys -g $NEW_RG -n workspace-appsDOlM --query primarySharedKey -o tsv
az containerapp env update -n eundunhealth-env -g $NEW_RG --logs-destination log-analytics --logs-workspace-id $cid --logs-workspace-key $key
```
검증: 수 분 후 `az containerapp logs show -n eundunhealth-api -g $NEW_RG --tail 5` 또는 LA `ContainerAppConsoleLogs_CL` 최근 레코드. Task 0 스냅샷의 customerId 와 동일해야 함(키만 변경).

## Phase 2: 권한·UAI

### Task 4: RBAC 재부여 — CLI 선시도, 실패 시 회원님 포털

CLI 시도 (pwsh — MissingSubscription 재현 시 즉시 중단·포털로):
```powershell
$ACR = "/subscriptions/$SUB/resourceGroups/rg-eundunhealth-prod-krc/providers/Microsoft.ContainerRegistry/registries/eundunhealthacr"
$KV  = "/subscriptions/$SUB/resourceGroups/rg-eundunhealth-prod-krc/providers/Microsoft.KeyVault/vaults/kv-eundunhealth"
az role assignment create --assignee 39b73fb8-b590-471f-9cb9-ab7a99b69657 --role Contributor --scope "/subscriptions/$SUB/resourceGroups/rg-eundunhealth-prod-krc"
# … (아래 표 순서대로)
```

포털 체크리스트 (Access control(IAM) → Add role assignment):

| # | 어디서 | 역할 | 누구에게 |
|---|---|---|---|
| 1 | RG `rg-eundunhealth-prod-krc` | Contributor | CI SP (appId `39b73fb8-b590-471f-9cb9-ab7a99b69657`) |
| 2 | ACR `eundunhealthacr` | AcrPush | CI SP |
| 3 | KV `kv-eundunhealth` | Key Vault Secrets User | CI SP |
| 4 | ACR | AcrPull | `eundunhealth-api` (system MI — Managed identity 탭에서 Container App 선택) |
| 5 | KV | Key Vault Secrets User | `eundunhealth-api` system MI |
| 6 | ACR | AcrPull | UAI `id-eundunhealth-reaper` (Task 5 신규 생성분) |
| 7 | KV | Key Vault Secrets User | 위 UAI |
| 8 | KV | Key Vault Secrets Officer | 회원님 계정 |

검증 (Claude): `az role assignment list --all` 로 8건 신규 scope 확인 → Container App revision 재시작(`az containerapp revision restart`) 후 `/health` 200 = pull/secret resolve 정상.
> **전파 지연 주의** (공식 deployment-errors 문서): RBAC 부여 후 전파 최대 ~10분 + managed identity token cache 최대 24h. 재시작 직후 pull/secret 실패 시 "미부여" 로 단정하지 말고 10분 대기 → 재시도, 그래도 실패 시 새 revision 배포로 토큰 강제 갱신. 참고: KV versionless 참조는 30분 주기 자동 sync 라, 러닝 replica 는 sync 실패에도 기존 값으로 계속 동작(죽지 않음).

### Task 5: UAI B안 재배선

```powershell
az identity create -n id-eundunhealth-reaper -g rg-eundunhealth-prod-krc -l koreacentral
# → 새 principalId/clientId 기록 → Task 4 의 #6·#7 부여(신규 principalId 대상)
```
`backend/reaper-job.yaml` 의 UAI ID·environmentId 경로를 새 RG 로 수정 → `az containerapp job update -n eundunhealth-reaper -g rg-eundunhealth-prod-krc --yaml backend/reaper-job.yaml` → 수동 실행 Succeeded 확인 → 구 UAI 삭제(`az identity delete -n id-eundunhealth-reaper -g apps`).
**폴백(A안):** job update 가 CLI 함정으로 실패하면 구 UAI 잔류 + `apps` RG 유지, Task 8 의 RG 삭제 생략.

## Phase 3: 코드·문서·검증

### Task 6: RG 참조 일괄 갱신

**Files:** design §5.2/§5.3 목록 (backend.yml, warm-baseline-check.yml, containerapp.yaml, reaper-job.yaml, setup-reaper-job.sh, secretref-guard.sh, naming-audit.md, redeploy.sh[저장소 외], 문서 6종+PR template). 과거 기록(incident-log·ledger·CHANGELOG)은 제외.

### Task 7: 알림 재생성 + e2e (bash/pwsh)

```bash
bash scripts/setup-azure-alerts.sh --dry-run && bash scripts/setup-azure-alerts.sh   # 새 기본 RG
gh workflow run backend.yml --ref main   # 커밋·push 후 → deploy success 확인
gh workflow run warm-baseline-check.yml
```
검증: metric 4+activity-log 4 enabled / backend deploy green / prod `/health`·`/health/ready` 200 / reaper Succeeded(Task 5 에서 완료) / LA 로그 유입 지속(Task 3 Step 2 이후 재확인).

### Task 8: 마무리

1. `az resource list -g apps` 빈 것 확인 → `az group delete -n apps --yes` (**회원님 확인 후 실행** — Destructive 5문항)
2. 문서·스크립트 변경 커밋: pre-commit(gen-plans-index 포함) 통과 → 본 페어 index 반영 → **push 전 회원님 확인** → push
3. `operations-snapshot.md` 에 이관 결과 반영, 본 페어는 머지 규칙대로 유지(shipped 시 ledger 흡수)

---

## 잔여 리스크 / 후속 작업

- 이동 중 관리 잠금 최대 4h(데이터플레인 정상) — Task 3~5 사이 배포 금지 원칙으로 커버.
- RBAC 재부여 전 replica 재시작 시 기동 실패 가능 — 사용자 0명 수용, Task 4 완료 후 복구 확인. 전파 지연(~10분)·token cache(24h)는 Task 4 주의사항으로 커버.
- 이동 완료~Task 3 Step 2 사이 LA 콘솔 로그 유입 공백(key 재생성) — Sentry 채널은 무영향, 공백 짧게 수용.
- 후속: Azure 비용 뷰·북마크 등 포털 개인 설정의 RG 필터(회원님 개인 영역, Claude 범위 밖).

## Postmortem

> (실행 완료 + 7일 후 채움)
