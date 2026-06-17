---
type: design
status: shipped  # 코드/IaC #127 머지 + 프로비저닝 완료(잡 생성·수동실행 Succeeded, 2026-06-17)
pr: https://github.com/gunnysis/eundunHealth/pull/127
related_inc: null
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [infra, container-apps-job, account-deletion, reaper, key-vault]
---

# orphan reaper — Container Apps Job(cron) 프로비저닝 설계

- **작성일**: 2026-06-17
- **상태**: 작성 중 (검토 대기)
- **연관 작업**: PR #126(orphan reaper 코드·스크립트·테스트 머지)의 후속 — 자동 스케줄링만 남음
- **대상 버전**: infra-only (앱/API 버전 무관)
- **선행 작업**: 없음 (reaper 코드는 `df65d91` 로 prod 배포 완료)

## 1. 배경

PR #126 에서 계정삭제 고아 데이터(Auth엔 없고 DB엔 남음 — delete_account Step2 실패 시) 정리 안전망 `AccountService.reap_orphaned_data()`(fail-safe: Auth 404 확정만 purge) + 실행 진입점 `backend/scripts/reap_orphaned_accounts.py` 를 구현·배포했다. **코드는 prod 이미지에 포함**되어 지금도 수동 실행 가능하나, **주기 자동 실행(wiring)**만 없다. 본 설계는 이를 **Azure Container Apps Job(Schedule/cron)**으로 자동화한다.

검증으로 발견한 선결 버그: 스크립트를 `python scripts/reap_orphaned_accounts.py` 로 실행하면 `sys.path[0]=scripts/` 라 `from app...` 가 `ModuleNotFoundError`(MEASURED: 실측 — form1 ModuleNotFoundError / form2 통과). → **반드시 `python -m scripts.reap_orphaned_accounts`**(모듈 형태). 스크립트 docstring + 잡 command 모두 `-m` 로 정정.

## 2. Scope

### In-scope
- Container Apps Job `eundunhealth-reaper`(RG `apps`, env `eundunhealth-env`) — Schedule 트리거, 주간 cron.
- 잡 전용 system MI + RBAC(AcrPull, Key Vault Secrets User) + KV 참조 시크릿 3개.
- IaC 산출물 `scripts/setup-reaper-job.sh`(idempotent, `--dry-run`, `--verify`).
- 검증(수동 1회 실행 + execution/log 확인) + 운영 문서 갱신.

### Out-of-scope
- **잡 실패 알림**(Azure Monitor metric/activity alert) — 후속(현재 0 사용자라 우선순위 낮음; `scripts/setup-azure-alerts.sh` 에 추가 검토).
- reaper 의 Supabase 존재확인 **병렬화**(현재 user당 직렬 GET) — 사용자 증가 시 검토(코드리뷰에서 기록).

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 자동화 메커니즘 | Container Apps **Job(Schedule)** | 항상 떠 있는 API와 분리된 배치. in-app 스케줄러는 min/max 1/3 replica 중복 실행 위험 |
| D2 | 스케줄 | 주간 `0 18 * * 0`(UTC) = **월 03:00 KST** | 고아는 delete-with-DB-failure 시에만 발생(희소) → 주간 청소로 충분. cron 은 UTC 평가([공식](https://learn.microsoft.com/en-us/azure/container-apps/jobs)) |
| D3 | 실행 커맨드 | `python scripts/reap_orphaned_accounts.py` | 스크립트 self-locating(§5.3)이라 동작 + az `--args` 가 `-m` 을 플래그로 오인하는 파싱 문제 회피(MEASURED: `--args -m …` → `unrecognized arguments`). `--command` 가 Dockerfile ENTRYPOINT(entrypoint.sh)를 덮어 reaper만 실행(마이그레이션 우회 — 적절) |
| D4 | 신원 | 잡 전용 **user-assigned identity**(UAI `id-eundunhealth-reaper`) | job create 가 이미지를 즉시 검증(ACR pull)하므로 create 시점에 권한 보유한 신원 필요. system MI 는 create 시 막 생겨 AcrPull 없음 → UNAUTHORIZED(chicken-egg, E2 §10). UAI 를 먼저 만들어 역할 부여 후 그 UAI 로 생성 |
| D5 | 시크릿 | **Key Vault 참조**(`keyvaultref:…,identityref:system`) | 앱과 동일 패턴(직접값 아님). database-url/supabase-url/supabase-service-role-key 3개(`Settings` 필수 필드) |
| D6 | 이미지 | **현재 운영 Container App 이미지**(setup 시 조회) | 잡=앱 버전 동기화. 재실행 시 최신 앱 이미지로 갱신(`eundunhealth-api:<SHA>`) |
| D7 | 리소스 | 0.25 vCPU / 0.5Gi, replica-timeout 1800, retry 1 | 수 초 실행. 비용 ≈0(주간 실행 × 수초, 무료 grant 내) |
| D8 | IaC | **잡 정의=`backend/reaper-job.yaml`(`--yaml` create) + 오케스트레이션=멱등 셸 스크립트** | 잡 정의(registry/secret/identity)는 YAML 로(E4 CLI 버그 회피 + containerapp.yaml 패턴 일치). UAI 생성·역할 부여·image 치환·검증은 스크립트가 조율(역할은 별도 az 호출이라 순수 yaml 불가) |

## 4. 옵션 비교

| 옵션 | A. Container Apps Job(채택) | B. in-app APScheduler/BackgroundTask | C. 수동 실행만 |
|---|---|---|---|
| 중복 실행 | 없음(단일 잡) | min/max 1/3 replica 에서 N중 실행 위험 | 해당 없음 |
| 격리 | API와 분리 | API 프로세스에 배치 결합 | - |
| 자동화 | cron | cron-ish | ❌ 운영자 수동 |
| 비용 | 실행 시에만(≈0) | 상시(API 내) | 0 |
| 채택 | ✅ | ❌ 중복·결합 | 보조(스크립트는 수동도 가능) |

## 5. 구성

### 5.1 잡 스펙 + 부트스트랩 순서 (`scripts/setup-reaper-job.sh`)

역할을 **먼저** 부여해야 첫 run 에서 이미지 pull + 시크릿 resolve 가 성공하므로 순서가 중요:

1. `az containerapp job create … --mi-system-assigned --env-vars ENVIRONMENT=production --command python --args scripts/reap_orphaned_accounts.py`(registry/secret 제외 — MI 가 아직 권한 없음).
2. `principalId = az containerapp job show … --query identity.principalId`.
3. `az role assignment create … --role AcrPull --scope <ACR>` + `… --role "Key Vault Secrets User" --scope <KV>`.
4. `az containerapp job registry set … --server eundunhealthacr.azurecr.io --identity system`.
5. `az containerapp job secret set --secrets <name>=keyvaultref:<KV_URI>/secrets/<name>,identityref:system …` + `az containerapp job update --set-env-vars <NAME>=secretref:<name> …`.

(scale-to-zero 회귀 알림처럼 운영자 1회 셋업. 재실행은 update 분기로 멱등.)

### 5.2 스크립트 정정 (`backend/scripts/reap_orphaned_accounts.py`)

docstring 실행 안내를 `python -m scripts.reap_orphaned_accounts` 로 정정(+ `python scripts/x.py` 가 깨지는 이유 명시).

### 5.3 점검 중 발견·반영한 하드닝 (개선/디버깅/재발방지)

job 화 과정 점검에서 발견한 3건을 함께 반영:

- **reaper 트랜잭션 견고성**(디버깅): `reap_orphaned_data` 가 전체를 한 트랜잭션으로 처리해 orphan 1명 purge 실패 시 전체 sweep 중단 + 이미 정리한 사용자 롤백 → **orphan 단위 commit + 에러 격리**(try/except + rollback, 한 명 실패가 다른 청소를 막지 않음). 회귀 테스트 `test_reap_orphaned_data_isolates_per_user_failure`.
- **스크립트 self-locating**(재발방지): `python scripts/x.py` 가 `sys.path[0]=scripts/` 로 `import app` ModuleNotFoundError 나던 footgun → 스크립트가 backend 루트를 `sys.path` 에 추가(`-m` 도 그대로 동작). subprocess 가드 테스트 `test_reaper_script_imports_resolve_when_run_directly`.
- **requirements cp949 가드**(재발방지): em-dash 등 cp949 미지원 바이트가 들어가면 cp949 Windows 의 pip-audit 가 깨짐 → pre-commit §4 가 staged `requirements*.txt` 의 **cp949 디코드 가능성**으로 판정(한국어 주석은 cp949 OK 라 "non-ASCII 전부 차단"이 아닌 정밀 검사 — MEASURED: 현재 파일 PASS / em-dash BLOCK).

## 6. 검증 계획

| 단계 | 명령 / 기대 |
|---|---|
| 문법 | `bash -n scripts/setup-reaper-job.sh` → OK (MEASURED) |
| dry-run | `bash scripts/setup-reaper-job.sh --dry-run` → 명령 미리보기 (MEASURED) |
| 프로비저닝 | `bash scripts/setup-reaper-job.sh` → 잡 생성 + 역할 + 시크릿 |
| 수동 실행 | `az containerapp job start -n eundunhealth-reaper -g apps` → execution `Succeeded` |
| 로그 | Log Analytics `ContainerAppConsoleLogs_CL` 에 "Orphan reaper 완료 — purged 0 user(s)" (현재 0 사용자 → purge 0 = 정상) |
| 멱등 | 스크립트 재실행 → update 분기, 에러 없음 |

> 현재 user_profiles 0행 → reaper 가 청소할 후보 0 → "purged 0" 가 정상 동작 증거. 실제 고아 청소는 사용자 발생 후.

## 7. 롤백 절차

`az containerapp job delete -n eundunhealth-reaper -g apps --yes`. 역할 할당도 제거하려면 `az role assignment delete --assignee <principalId> --scope <ACR|KV>`. 코드/스크립트는 그대로 두고 잡만 제거 가능.

## 8. 잔여 리스크

- **이미지 핀**: 잡은 setup 시점의 앱 이미지(`<SHA>`)로 고정 → 백엔드(reaper 코드/모델) 변경 시 `setup-reaper-job.sh` 재실행으로 갱신 필요(문서화).
- **직렬 GET**: reaper 가 후보 user당 Supabase GET 직렬 → 사용자 多 시 wall-time 선형(코드리뷰 기록, 사용자 증가 시 병렬화).
- **실패 무알림**: 잡 실패 시 자동 알림 없음(로그만) → 후속 Azure Monitor alert.

## 9. 참고 자료 (공식)

- [Jobs in Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/jobs) (cron=UTC, Schedule 트리거)
- [Create a Job (CLI)](https://learn.microsoft.com/en-us/azure/container-apps/jobs-get-started-cli)
- [Manage secrets — Key Vault reference(`keyvaultref:…,identityref:…`)](https://learn.microsoft.com/en-us/azure/container-apps/manage-secrets)
- `az containerapp job create/update/secret/registry --help`(MEASURED — 플래그 확인)

## 10. 프로비저닝 중 발생한 에러 & 재발방지 (MEASURED, 라이브 실행)

setup 스크립트는 아래 3건을 코드/preflight 로 방지한다. 운영자 재발방지용 기록:

| # | 에러 | 원인 | 대응(스크립트 반영) |
|---|---|---|---|
| **E1** | `az containerapp job create … --args -m scripts.x` → `unrecognized arguments: -m …` | az argparse 가 `-`로 시작하는 `-m` 을 플래그로 오인(`--args` 가 값으로 못 받음) | `--command python --args scripts/reap_orphaned_accounts.py`(앞에 `-` 없는 인자). 스크립트 self-locating 이라 `-m` 불필요 |
| **E2** | job create → `InvalidParameterValueInContainerTemplate … UNAUTHORIZED: authentication required`(ACR) | job create 가 이미지를 즉시 pull 검증. system MI 는 create 시 막 생겨 AcrPull 없음(chicken-egg) | **UAI 선생성 → 역할 부여 → 그 UAI 로 create**(D4). 역할 전파 대비 create retry(5×30s) |
| **E3** | `az role assignment create/list --scope …` → `MissingSubscription: … or a valid tenant level resource provider` | 로그인 계정이 **개인 MSA**(예: gmail). `--scope` 미지정 RBAC 조회는 되나 **scope 지정 RBAC 작업(Microsoft.Authorization write)이 불가**. ARM read/배포는 정상 | preflight 가 `role assignment list --scope <ACR>` 로 **RBAC 가능 여부를 mutation 전에 진단** → 불가 시 역할 부여 best-effort(실패해도 중단 X) + **포털 수동 부여 안내**. 역할만 포털에서 주면 스크립트 재실행으로 완료(복구 가능) |
| **E4** | `az containerapp job create --registry-identity <UAI id>` → `must be an identity resource ID or 'system'`(유효 UAI id 거부) | az containerapp **job** 의 `--registry-identity` + user-assigned 는 알려진 문제 영역(확장 1.3.0b4·케이싱 무관). [#1284](https://github.com/microsoft/azure-container-apps/issues/1284) 는 유사하나 증상 다른(traceback) job 버그 — fact-check 로 "정확히 그 버그"가 아님 확인 | **`--yaml`** 의 `registries[].identity`/`secrets[].identity`(=`backend/reaper-job.yaml`)로 생성. 이는 우회가 아니라 **공식 IaC 형태**([공식 image-pull Bicep 예시](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull)와 동일). MI 최소권한 유지. 갱신은 `--image` 만 |

> **프로비저닝 결과 (MEASURED 2026-06-17)**: UAI `id-eundunhealth-reaper` 생성 + 포털 역할 부여(AcrPull·KV Secrets User) → `az containerapp job create --yaml backend/reaper-job.yaml` 성공 → 수동 실행 `eundunhealth-reaper-g6ngiz7` **status=Succeeded**(이미지 pull + KV 시크릿 resolve + DB 연결 + reaper 실행 + exit 0 전부 정상; 현재 0 사용자 → purged 0). 주간 cron `0 18 * * 0` 활성.

### 운영자 셋업 (E3 해당 — 현재 환경)

1. `bash scripts/setup-reaper-job.sh` → UAI `id-eundunhealth-reaper` 생성(역할은 preflight 가 불가 진단).
2. **Azure Portal** 에서 그 UAI 에 역할 2개 부여:
   - ACR `eundunhealthacr` → 액세스 제어(IAM) → 역할 할당 → **AcrPull** → `id-eundunhealth-reaper`
   - Key Vault `kv-eundunhealth` → 액세스 제어(IAM) → 역할 할당 → **Key Vault Secrets User** → `id-eundunhealth-reaper`
   - (또는 RBAC 가능한 계정/SP 로 `az role assignment create --assignee-object-id <UAI principalId> --assignee-principal-type ServicePrincipal --role AcrPull --scope <ACR id>`)
3. `bash scripts/setup-reaper-job.sh --verify` 재실행 → 역할 best-effort(이미 있음) 통과 후 잡 생성 + 수동 1회 실행 검증.
