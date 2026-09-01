# Azure Container Apps Job 프로비저닝 런북 (재발방지)

> orphan reaper(`eundunhealth-reaper`) 프로비저닝(2026-06-17) 중 발생한 4개 에러(E1~E4)를
> 공식·외부 문서로 교차검증하고, **재현 가능한 프로비저닝 패턴 + 함정 회피**를 박제한 durable 문서.
> 향후 Container Apps **Job**(private ACR 이미지 + Key Vault 시크릿 + MI) 추가 시 본 패턴을 따른다.
> 워크드 예시: `scripts/setup-reaper-job.sh` + `backend/reaper-job.yaml` (설계 `docs/plans/logs/process-infra.md`).

## 0. 권장 패턴 (공식 검증)

private ACR 이미지를 MI 로 pull + Key Vault 시크릿을 MI 로 읽는 Container Apps Job 은 **user-assigned identity(UAI) 선생성** 패턴을 쓴다. 공식 [Container Apps image pull with managed identity](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull) 가 IaC 시나리오로 명시한 순서:

1. **UAI 생성** — `az identity create -n id-<job> -g <rg>`.
2. **역할 부여** — UAI 에 `AcrPull`(ACR scope) + `Key Vault Secrets User`(KV scope). 공식 명령:
   `az role assignment create --assignee-object-id <UAI principalId> --assignee-principal-type ServicePrincipal --role AcrPull --scope <ACR id>` (KV 도 동일).
3. **`--yaml` 로 잡 생성** — `az containerapp job create -n <job> -g <rg> --yaml <job>.yaml`. YAML 에 `identity.userAssignedIdentities`, `configuration.registries[].identity=<UAI id>`, `configuration.secrets[].keyVaultUrl + identity=<UAI id>` 를 둔다. (이 `registries[].identity`/`secrets[].identity` 형태는 공식 image-pull 문서의 Bicep 예시와 동일한 **IaC 정석** — 동시에 job CLI 의 `--registry-identity` 문제(E4)도 회피.)
4. **갱신은 `--image` 만** — `az containerapp job update -n <job> -g <rg> --image <new>` (registry/secret/identity 는 YAML 로 박힌 기존 유지).
5. **검증** — `az containerapp job start` → `az containerapp job execution list -o table` 가 `Succeeded`.

> **왜 system-assigned 가 아니라 UAI?** job create 가 이미지를 **즉시 pull 검증**하는데 system MI 는 create 시점에 막 생겨 AcrPull 이 없어 실패한다(E2 chicken-egg). UAI 는 create 전에 만들어 역할을 줄 수 있어 이 순환을 끊는다.

## 1. 발생 에러 & 회피 (E1~E4, 공식/외부 문서 교차검증)

| # | 증상 | 원인 | 회피 | 근거 |
|---|---|---|---|---|
| **E1** | `az containerapp job create --args -m scripts.x` → `unrecognized arguments: -m …` | az argparse 가 `-`로 시작하는 값을 플래그로 오인 | `-` 로 시작 안 하는 인자 사용(`--args scripts/x.py`). 스크립트 self-locating 로 `-m` 불필요. (대안: `=` 부착 / `--` 종결자) | [azure-cli#18869](https://github.com/Azure/azure-cli/issues/18869), [#2588](https://github.com/Azure/azure-cli/issues/2588) |
| **E2** | job create → `UNAUTHORIZED: authentication required`(ACR) | create 가 이미지 즉시 pull 검증, system MI 는 create 시 막 생겨 AcrPull 없음(chicken-egg) | **UAI 선생성 → AcrPull → UAI 로 create**(§0). 역할 전파 지연 대비 create retry | [공식 image-pull MI(IaC 순서)](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull) |
| **E3** | `az role assignment create/list --scope …` → `MissingSubscription: … valid tenant level resource provider` | 로그인 계정이 **개인 MSA**(예: gmail) — `--scope` 지정 RBAC(Microsoft.Authorization write) 불가. ARM read/배포는 정상 | RBAC 는 **Azure Portal IAM**(MSA 소유자는 포털서 가능) 또는 **권한 있는 SP**(CI `AZURE_CREDENTIALS`)로. 스크립트는 mutation 전 preflight 진단 + best-effort + 안내 | 본 프로젝트 MEASURED 2026-06-17; [MSA/Entra RBAC 제약](https://learn.microsoft.com/en-us/azure/role-based-access-control/role-assignments-cli) |
| **E4** | `az containerapp job create --registry-identity <UAI id>` → `must be an identity resource ID or 'system'`(유효한 UAI resource id 거부) | az containerapp **job** 의 `--registry-identity` + user-assigned 는 **알려진 문제 영역**(확장 1.3.0b4·케이싱 무관). #1284 는 유사하나 증상이 다른(traceback `'userAssignedIdentities'`) job 버그 | CLI 파라미터를 안 쓰고 **`--yaml`** 의 `registries[].identity:<UAI id>` 로 생성 — 이는 우회가 아니라 **공식 IaC 형태**(공식 image-pull 문서의 Bicep 예시가 동일하게 `registries[].identity: identity.id` 사용). MI=AcrPull-only 최소권한 유지 | [#1284(유사 job 버그)](https://github.com/microsoft/azure-container-apps/issues/1284), [공식 image-pull Bicep `registries[].identity`](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull) |

## 2. 운영자 절차 (이 프로젝트 = 개인 MSA 계정)

E3 때문에 역할 부여만 포털/SP 로:

1. `bash scripts/setup-reaper-job.sh` → UAI `id-eundunhealth-reaper` 생성(preflight 가 RBAC 불가 진단·안내).
2. **Azure Portal**: UAI 에 역할 2개 부여 — ACR `eundunhealthacr` → IAM → **AcrPull**; Key Vault `kv-eundunhealth` → IAM → **Key Vault Secrets User**. (또는 권한 있는 SP 로 `az role assignment create … --role AcrPull --scope <ACR id>`.)
3. `bash scripts/setup-reaper-job.sh --verify` → `--yaml` 로 잡 생성 + 수동 1회 실행(`Succeeded` 확인).

## 3. 빠른 참조 명령

```bash
# 실행 이력/상태
az containerapp job execution list -n eundunhealth-reaper -g rg-eundunhealth-prod-krc -o table
# 수동 1회 실행
az containerapp job start -n eundunhealth-reaper -g rg-eundunhealth-prod-krc
# 로그(log-analytics 확장 필요)
WID=$(az containerapp env show -n eundunhealth-env -g rg-eundunhealth-prod-krc --query properties.appLogsConfiguration.logAnalyticsConfiguration.customerId -o tsv)
az monitor log-analytics query --workspace "$WID" \
  --analytics-query "ContainerAppConsoleLogs_CL | where ContainerGroupName_s startswith 'eundunhealth-reaper' | order by TimeGenerated desc | project Log_s | take 20"
```

## 4. 참고 자료

- [Container Apps: image pull from ACR with managed identity](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull) (UAI-first IaC 순서)
- [Managed identities in Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity)
- [Jobs in Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/jobs) / [Create a job (CLI)](https://learn.microsoft.com/en-us/azure/container-apps/jobs-get-started-cli)
- [microsoft/azure-container-apps#1284 — job `--registry-identity` 버그](https://github.com/microsoft/azure-container-apps/issues/1284)
- [Azure CLI#18869 / #2588 — leading-dash 인자](https://github.com/Azure/azure-cli/issues/18869)
- 관련 메모리/설계: `docs/plans/logs/process-infra.md` §10
