---
type: design
status: proposed
pr: null
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
| D3 | 실행 커맨드 | `python -m scripts.reap_orphaned_accounts` | `python scripts/x.py` 는 `import app` 실패(MEASURED). `--command` 가 Dockerfile ENTRYPOINT(entrypoint.sh)를 덮어 reaper만 실행(마이그레이션 우회 — 적절) |
| D4 | 신원 | 잡 전용 **system-assigned MI** | 앱 MI와 분리(최소권한). KV Secrets User + AcrPull 부여 |
| D5 | 시크릿 | **Key Vault 참조**(`keyvaultref:…,identityref:system`) | 앱과 동일 패턴(직접값 아님). database-url/supabase-url/supabase-service-role-key 3개(`Settings` 필수 필드) |
| D6 | 이미지 | **현재 운영 Container App 이미지**(setup 시 조회) | 잡=앱 버전 동기화. 재실행 시 최신 앱 이미지로 갱신(`eundunhealth-api:<SHA>`) |
| D7 | 리소스 | 0.25 vCPU / 0.5Gi, replica-timeout 1800, retry 1 | 수 초 실행. 비용 ≈0(주간 실행 × 수초, 무료 grant 내) |
| D8 | IaC | 멱등 **셸 스크립트**(`--yaml` 아님) | 역할 부여가 별도 az 호출이라 단일 yaml 불가. `setup-azure-alerts.sh` 선례와 일치 |

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

1. `az containerapp job create … --mi-system-assigned --env-vars ENVIRONMENT=production --command python --args -m scripts.reap_orphaned_accounts`(registry/secret 제외 — MI 가 아직 권한 없음).
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
