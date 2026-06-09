---
type: plan
status: approved  # proposed → approved → in-progress → [holding|deferred] → shipped (→ ledger archive)
pr: null
related_inc: null
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [container-apps, cold-start, health-probes, scale, key-vault, iac]
---

# 백엔드 Cold Start 제거 + Warm Baseline Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`(권장) 또는 `superpowers:executing-plans` 로 task-by-task 구현. Steps 는 checkbox(`- [ ]`).

**Goal:** 백엔드 Container App 의 scale-to-zero cold start(측정 21,506ms)를 제거해 "로그인 느림"을 해소하고, 안전한 full IaC(Key Vault) + 앱-인지 health probe 로 견고화한다.

**Architecture:** 단계적 (3 PR 진행됨 + 남은 1 PR). **Phase 1**(✅ PR #92 배포완료) = `backend.yml` deploy 에 imperative scale 플래그(`--min-replicas 1 --max-replicas 3 --scale-rule-http-concurrency 50`) → cold start 제거. **`/health/ready`+테스트**(✅ PR #93 배포완료) + Task 3 plan 하드닝(✅ PR #94) = readiness 엔드포인트를 기존 imperative 배포 경로로 선반영. **Phase 2 (남음)** = secret 을 Key Vault 참조로 전환(system MI + RBAC) + committed `backend/containerapp.yaml`(완전 spec, 값 미커밋) + HTTP 프로브 3종(이미 떠 있는 `/health/ready` 연결) + `--yaml` 배포 전환(PR2). **운영자 Key Vault 셋업(Task 3) 선행.**

**Tech Stack:** Azure Container Apps, Azure CLI(`az containerapp`), Azure Key Vault + system-assigned managed identity, GitHub Actions, FastAPI / Python 3.12 (SQLAlchemy async), pytest, bash.

**참고:**
- Design: `docs/plans/2026-06-09-coldstart-warm-baseline-design.md` (의사결정·옵션·fact-check 근거)
- Branch: Phase 1 `fix/coldstart-warm-baseline`, Phase 2 `feat/containerapp-iac-keyvault` (각 Task 0 / 3.0 에서 생성)

**중요 원칙:**
- TDD: 동작 변경 task(`/health/ready`)는 red → green → commit.
- 프로젝트 룰: 룰 6(secretref 3중 변경 — 본 plan 은 KeyVault precheck 로 적응), 룰 7(alembic entrypoint 불변), 룰 9(측정 라벨).
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시.
- main 직접 작업 X → 브랜치 + PR. **운영자 수동 task(Azure 리소스/시크릿/RBAC)는 Claude 실행 금지.**

**Task 순서:**
```
Phase 1  ✅ PR #92 배포완료
  Task 0~2  branch + scale 플래그(min=1/max=3/http-rule) + 머지/프로덕션 검증

선반영  ✅ PR #93 배포완료
  Task 4    /health/ready + 단위테스트(200/503)
  Task 5    sync-openapi (healthReady)

Phase 2 (남음 — PR2, 운영자 Task 3 선행)
  Task 3.0  ✅ Phase 2 branch feat/containerapp-iac-keyvault (최신 main 기준)
  Task 3    ← [운영자 수동] Key Vault + MI + RBAC 셋업 (진행 중)
  Task 6    backend/containerapp.yaml (라이브 spec 기반 — delta = probes+secrets+registries)
  Task 7    backend.yml → --yaml 전환 + precheck KeyVault 화
  Task 8    (옵션) Replicas 회귀 알림
  Task 9    operations-snapshot 갱신 (+ 머지된 dep bump 반영)
  Task 10   [게이트] staging 앱 true dry-run (--yaml clobber/resolve 실증)
  Task 11   push + PR2 + 머지 후 검증
```

---

## Phase 1 — Cold start 즉시 제거 (PR1)

### Task 0: 브랜치 + design/plan 페어 커밋

**Files:** `docs/plans/2026-06-09-coldstart-warm-baseline-{design,plan}.md`, `docs/plans/README.md`

- [ ] **Step 1 (pwsh):** 현재 main 최신화 확인 + 브랜치 생성
  Run: `git switch -c fix/coldstart-warm-baseline`
- [ ] **Step 2 (bash):** plans 인덱스 재생성 (v0.1.7 lesson — 페어 추가 commit 에 동봉 필수)
  Run: `bash scripts/gen-plans-index.sh`
  Expected: `docs/plans/README.md` 활성 1행(design+plan 페어, 스크립트 로그 `active: 1`) 로 갱신
- [ ] **Step 3 (bash):** 페어 + 인덱스 커밋
```bash
git add docs/plans/2026-06-09-coldstart-warm-baseline-design.md \
        docs/plans/2026-06-09-coldstart-warm-baseline-plan.md \
        docs/plans/README.md
git commit -m "docs(plans): cold start warm baseline design+plan 페어"
```

### Task 1: `backend.yml` deploy step 에 scale 플래그 추가

**Files:** Modify `.github/workflows/backend.yml:237-248` (deploy step)

- [ ] **Step 1 (Edit):** "Deploy to Container App" step 의 `az containerapp update` 에 scale 플래그 추가. 기존 `--image`/`--set-env-vars` 는 유지(registries/secrets 무손상).
```yaml
      - name: Deploy to Container App
        run: |
          az containerapp update \
            --name "$CONTAINER_APP_NAME" \
            --resource-group "$RESOURCE_GROUP" \
            --image "$IMAGE_TAG" \
            --set-env-vars \
              "DATABASE_URL=secretref:database-url" \
              "SUPABASE_URL=secretref:supabase-url" \
              "SUPABASE_SERVICE_ROLE_KEY=secretref:supabase-service-role-key" \
              "SENTRY_DSN=secretref:sentry-dsn-backend" \
              "ENVIRONMENT=production" \
            --min-replicas 1 \
            --max-replicas 3 \
            --scale-rule-name http-concurrency \
            --scale-rule-http-concurrency 50
```
- [ ] **Step 2 (bash):** workflow YAML 문법 검증 (actionlint 있으면)
  Run: `actionlint .github/workflows/backend.yml || python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/backend.yml')); print('yaml ok')"`
  Expected: 에러 없음
- [ ] **Step 3 (bash):** 커밋
```bash
git add .github/workflows/backend.yml
git commit -m "fix(infra): Container App min-replicas=1 로 cold start 제거 (max 3 + http scale rule)"
```

### Task 2: push + PR1 + 머지 후 검증

- [ ] **Step 1 (bash):** push + PR
```bash
git push -u origin fix/coldstart-warm-baseline
gh pr create --base main --title "fix(infra): 백엔드 cold start 제거 (min-replicas=1)" \
  --body "측정 21.5s cold start(scale-to-zero) 제거. design: docs/plans/2026-06-09-coldstart-warm-baseline-design.md"
```
- [ ] **Step 2:** CI 통과(backend.yml test/runtime-smoke/security) 확인 후 머지. main push → deploy job 이 scale 플래그 적용.
- [ ] **Step 3 (pwsh): 검증** — 배포 + cooldown(300s) 초과 idle 후 cold 측정. **이전 21,506ms 가 사라졌는지 (MEASURED, 룰 9).**
```powershell
$u = "https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health"
(Measure-Command { Invoke-WebRequest $u -UseBasicParsing }).TotalMilliseconds   # 기대: < 200ms
az containerapp show -n eundunhealth-api -g apps --query "properties.template.scale" -o json  # minReplicas=1
```
  Expected: `/health` < 200ms (warm 유지), `minReplicas: 1, maxReplicas: 3`.

> **Phase 1 종료 시점에 사용자 문제(로그인 느림)는 완전히 해소된다.** Phase 2 는 견고화/IaC.

---

## Phase 2 — 안전한 full IaC (Key Vault) + 견고화 (PR2)

### Task 3.0: Phase 2 브랜치  ✅ 완료

- [v] **Step 1:** `feat/containerapp-iac-keyvault` 브랜치 생성 완료 — **최신 `main` 기준**(Phase 1 + `/health/ready`(PR #93) + Task 3 하드닝(PR #94) + 머지된 dep bump 포함). 남은 Task 6·7·9 는 이 브랜치에서.

### Task 3: [운영자 수동 — Claude 실행 금지] Key Vault + MI + RBAC 토대

> 프로덕션 secret·RBAC 변경이라 운영자가 직접 수행. 본 task 는 절차 문서화 + 운영자 실행 확인.
> destructive 5문항(`monitoring-and-cost.md §6.8`) 통과 후 진행.
> **secret 의 Container App→KeyVault 전환(secretref 재정의)은 Task 11 의 `--yaml` 배포가 원자적으로 일괄 수행** — 여기선 KeyVault/MI/RBAC 토대만 만든다(중복 imperative flip 제거 + staging 선검증).

- [v] **Step 1:** Key Vault 생성. **불변 옵션(retention-days/purge-protection)은 생성 시 확정** — 공식 문서 기반 추천값(secret 전용·MI RBAC·VNet 미통합 전제):
```bash
az keyvault create \
  --name kv-eundunhealth --resource-group apps --location koreacentral \
  --sku standard \                         # secret 전용 → HSM(Premium) 불필요
  --retention-days 90 \                     # soft-delete 최대 복구창 (생성 후 변경 불가)
  --enable-purge-protection true \          # 영구삭제 방지 (한 번 켜면 끌 수 없음)
  --enable-rbac-authorization true \        # Azure RBAC (legacy access policy 금지)
  --enabled-for-deployment false \          # Resource access: VM/ARM/ADE 전부 미사용
  --enabled-for-template-deployment false \
  --enabled-for-disk-encryption false \
  --public-network-access Enabled \         # Container Apps Consumption 동적 IP → public + RBAC/MI 가 차단막
  --tags app=eundunhealth environment=production component=backend-secrets
```
> caveat: soft-delete 로 vault 복구해도 **RBAC 역할 할당은 복원 안 됨**(공식 문서 확인) → 복구 시 Step 5·6 재실행. 네트워크 하드닝(private endpoint)은 Container Apps 환경 VNet 통합 선행(별도 과제).

> **선행 권한**: Step 2·5·6 의 역할 할당은 `Microsoft.Authorization/roleAssignments/write` 필요 → 운영자가 **Owner 또는 User Access Administrator**(RG `apps`/구독)여야 함. Contributor 만으로는 불가(공식 RBAC 가이드).
> **역할 전파 지연**: 역할 할당 후 data-plane 반영까지 **수 분**(공식: "Allow several minutes for role assignments to refresh") → 직후 `secret set`/`list` 가 403 이면 2–5분 대기 후 재시도.

> **idempotency**: Step 2~8 은 재실행 안전 — role assignment 는 중복 시 기존 반환(무해), `secret set` 은 새 버전 생성, `identity assign` 은 no-op. 중간 실패는 KV 삭제 없이 해당 step 부터 재개.

- [ ] **Step 2 (신규 — RBAC vault 필수):** 운영자 자신에게 **Key Vault Secrets Officer**(secret 쓰기) 부여 + **전파 확인 루프**. **이게 없으면 Step 3 `secret set` 403** — RBAC vault 는 control-plane Owner 라도 data-plane 권한 자동 부여 X(공식: "Key Vault Contributor... does not allow access to keys, secrets and certificates").
```bash
KV_ID=$(az keyvault show -n kv-eundunhealth -g apps --query id -o tsv)
ME=$(az ad signed-in-user show --query id -o tsv)
# role ID = Key Vault Secrets Officer (공식 권장: 이름 대신 ID — rename 내성)
az role assignment create --assignee-object-id "$ME" --assignee-principal-type User \
  --role "b86a8fe4-44ce-4948-aee5-eccb2c155cd7" --scope "$KV_ID" -o none
# 고정 대기 대신 data-plane 동작까지 폴링 (전파 ~수 분)
for i in $(seq 1 20); do
  az keyvault secret list --vault-name kv-eundunhealth -o none 2>/dev/null && { echo "RBAC 전파 완료"; break; }
  echo "  전파 대기... ($i/20)"; sleep 15
done
```
- [ ] **Step 3:** 4개 앱 secret 을 KeyVault 에 저장. **`-o none` 으로 secret 값 출력 차단**(`secret set` 기본 출력은 value 노출) + 빈 값 가드. **`set -x` 디버그 금지**(값 누출).
```bash
for s in database-url supabase-url supabase-service-role-key sentry-dsn-backend; do
  V=$(az containerapp secret show -n eundunhealth-api -g apps --secret-name "$s" --query value -o tsv)
  [ -n "$V" ] || { echo "ERROR: '$s' 값 비어있음 — 중단(잘못 저장 방지)"; break; }
  az keyvault secret set --vault-name kv-eundunhealth --name "$s" --value "$V" --content-type "text/plain" -o none
  echo "stored: $s"
done
```
- [ ] **Step 4:** Container App system-assigned MI 활성 + principalId 확보 (+가드)
```bash
az containerapp identity assign -n eundunhealth-api -g apps --system-assigned -o none
PID=$(az containerapp show -n eundunhealth-api -g apps --query identity.principalId -o tsv)
[ -n "$PID" ] || echo "ERROR: principalId 없음 — MI 활성 실패, 중단"
```
- [ ] **Step 5:** MI 에 RBAC — **Key Vault Secrets User**(secret 읽기) + ACR **AcrPull**. MI 는 Graph 전파 지연 대비 `--assignee-object-id`+`--assignee-principal-type ServicePrincipal`(`PrincipalNotFound` 회피). **주의: AcrPull 부여만으론 MI pull 이 켜지지 않음** — registries `passwordSecretRef`→`identity:system` 전환은 **Task 6·7 `--yaml` 배포**에서 발생(그 전까진 admin password pull 유지).
```bash
ACR_ID=$(az acr show -n eundunhealthacr --query id -o tsv)
az role assignment create --assignee-object-id "$PID" --assignee-principal-type ServicePrincipal \
  --role "4633458b-17de-408a-b874-0445c86b69e6" --scope "$KV_ID" -o none   # Key Vault Secrets User
az role assignment create --assignee-object-id "$PID" --assignee-principal-type ServicePrincipal \
  --role "AcrPull" --scope "$ACR_ID" -o none
```
- [ ] **Step 6:** CI service principal 에 **Key Vault Secrets User** 부여 (Task 7 precheck `az keyvault secret list` 용. list 전용 data-plane 역할은 없어 Secrets User — CI 는 이미 앱 배포 권한 보유라 실질 확대 아님)
```bash
SP_ID=$(az ad sp list --display-name eundunhealth-github-deploy --query "[0].id" -o tsv)
az role assignment create --assignee-object-id "$SP_ID" --assignee-principal-type ServicePrincipal \
  --role "4633458b-17de-408a-b874-0445c86b69e6" --scope "$KV_ID" -o none   # Key Vault Secrets User
```
- [ ] **Step 7:** 검증 — secret 4개 + data-plane read(전파 확인) + 역할 3건. 앱 실제 resolve 는 Task 10 staging 에서.
```bash
az keyvault secret list --vault-name kv-eundunhealth --query "[].name" -o tsv               # 4개 이름
az keyvault secret show --vault-name kv-eundunhealth --name database-url --query id -o tsv   # data-plane read OK = 전파 확인 (값 미출력)
az role assignment list --scope "$KV_ID" --query "[].roleDefinitionName" -o tsv             # Secrets Officer(운영자) + Secrets User(MI, CI SP)
```
- [ ] **Step 8 (옵션 — 관측성, best-practices):** KV audit 로그를 Log Analytics 로(공식 secure-key-vault "Enable audit logging"). workspace ID 는 환경에 맞게 확인 후 연결.
```bash
WS_ID=$(az monitor log-analytics workspace list -g apps --query "[0].id" -o tsv)   # 프로젝트 workspace (RG/이름 확인)
az monitor diagnostic-settings create --name kv-audit --resource "$KV_ID" \
  --workspace "$WS_ID" --logs '[{"category":"AuditEvent","enabled":true}]' -o none
```

> **복구 caveat**: KV 삭제 시 purge-protection 으로 90일간 같은 이름 재생성 불가(soft-delete). 중간 실패는 KV 삭제 없이 해당 step 재개(전부 idempotent). 다중 운영자 환경이면 PIM/JIT + MFA 권장(현 단독 운영자엔 옵션 — 공식 secure-key-vault).

### Task 4: `/health/ready` 엔드포인트 + overridable dependency (TDD)  ✅ 완료 (PR #93)

> **배포·검증 완료** — `/health/ready` 프로덕션 200(DB reachable). 단위테스트 2건(200/503) + ruff/mypy clean + 전체 46 passed. CI 가 `no-any-return` 1건 잡아 명시 타입 로컬로 수정(`5f6ad2f`). 아래 Step 은 **구현 기록**(이미 `main` 반영).

**Files:** Modify `backend/app/routers/health.py`, Modify `backend/tests/test_health.py`

- [ ] **Step 1 (bash): 실패 테스트 작성** — `backend/tests/test_health.py` 에 추가 (기존 `client`/`db_engine` fixture 재사용). **import 3줄은 파일 상단(`import pytest` 근처), 테스트 함수는 기존 `test_health` 아래에** (E402 회피)
```python
import pytest
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker
from app.main import app
from app.routers.health import get_session_factory


@pytest.mark.asyncio
async def test_health_ready_ok(client, db_engine):
    factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)
    app.dependency_overrides[get_session_factory] = lambda: factory
    resp = await client.get("/health/ready")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ready"}


@pytest.mark.asyncio
async def test_health_ready_db_down_returns_503(client):
    class _BoomSession:
        async def __aenter__(self):
            return self
        async def __aexit__(self, *exc):
            return False
        async def execute(self, _stmt):
            raise RuntimeError("db down")

    app.dependency_overrides[get_session_factory] = lambda: (lambda: _BoomSession())
    resp = await client.get("/health/ready")
    assert resp.status_code == 503
    assert resp.json() == {"status": "not ready"}
```
- [ ] **Step 2 (bash): 실패 확인**
  Run: `cd backend && .venv/Scripts/pytest tests/test_health.py -v`
  Expected: FAIL — `ImportError: cannot import name 'get_session_factory'`
- [ ] **Step 3 (Edit): 최소 구현** — `backend/app/routers/health.py` 를 아래로 교체
```python
import logging

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

logger = logging.getLogger(__name__)
router = APIRouter(tags=["health"])


def get_session_factory(request: Request) -> async_sessionmaker[AsyncSession]:
    """app.state 의 session factory 주입. 테스트에서 dependency_overrides 로 교체 가능.

    `request.app.state.session_factory` 직접 참조는 pytest ASGITransport(lifespan 우회)에서
    unset 이라, dependency 로 감싸 테스트 가능성을 확보한다.
    """
    return request.app.state.session_factory


@router.get("/health", operation_id="healthCheck")
async def health() -> dict[str, str]:
    """프로세스 가동 상태. JWT 불필요 — startup/liveness probe 전용 (DB 비검사)."""
    return {"status": "ok"}


@router.get("/health/ready", operation_id="healthReady")
async def health_ready(
    session_factory: async_sessionmaker[AsyncSession] = Depends(get_session_factory),
) -> JSONResponse:
    """readiness probe — DB 연결 가능 시에만 200, 아니면 503 → 트래픽 차단.

    5초 주기 probe 라 글로벌 500 핸들러(Sentry 포착)를 안 타도록 여기서 직접 503.
    두 분기 모두 JSONResponse — union 반환의 OpenAPI/mypy 잡음 회피.
    """
    try:
        async with session_factory() as session:
            await session.execute(text("SELECT 1"))
    except Exception as e:  # noqa: BLE001 — probe 는 모든 DB 오류를 503 으로 환원
        logger.warning("readiness check failed: %r", e)
        return JSONResponse(status_code=503, content={"status": "not ready"})
    return JSONResponse(content={"status": "ready"})
```
- [ ] **Step 4 (bash): green 확인 + 회귀 없음 + 정적검사**
  Run: `cd backend && .venv/Scripts/pytest tests/test_health.py -v && .venv/Scripts/ruff check app/ tests/ && .venv/Scripts/mypy app/`
  Expected: 신규 2 PASS + 기존 `test_health` PASS, ruff/mypy clean
- [ ] **Step 5 (bash): commit**
```bash
git add backend/app/routers/health.py backend/tests/test_health.py
git commit -m "feat(health): /health/ready readiness probe (DB SELECT 1 → 200/503)"
```

### Task 5: OpenAPI 스펙 동기화  ✅ 완료 (PR #93)

> `backend/openapi.json` 에 `healthReady` operation 반영·커밋 완료(15 paths). 아래는 기록.

**Files:** Modify `backend/openapi.json`

- [ ] **Step 1 (bash):** 스펙 재생성 (operation_id `healthReady` 노출)
  Run: `bash scripts/sync-openapi.sh`
- [ ] **Step 2 (bash):** drift 없는지 확인 + 커밋 (backend.yml drift step 통과 보장)
```bash
git add backend/openapi.json
git commit -m "chore(openapi): /health/ready 반영"
```

### Task 6: `backend/containerapp.yaml` (라이브 spec 기반 완전 spec, 값 미커밋)

**Files:** Create `backend/containerapp.yaml`

> **핵심(점검 발견 #1)**: 손으로 부분 YAML 을 쓰면 `--yaml` full-replace 가 `identity`(system MI)·`ingress.traffic`·`ephemeralStorage` 등 미기재 블록을 reset 할 수 있다. **특히 system MI 가 꺼지면 KeyVault resolve 가 붕괴**한다. 따라서 **라이브 spec 을 export → read-only 제거 → 의도분만 수정**한다.

> **현 상태 반영(변경 사항)**: Phase 1 이 이미 scale(min1/max3 + http-rule)을 적용했고 `/health/ready` 도 배포됨(PR #93) → 라이브 export 에 **scale·이미지(/health/ready 포함)는 이미 반영**되어 있다. 따라서 Task 6 의 실제 delta 는 **① probes 3종(신규) + ② secrets→KeyVault refs + ③ registries→`identity:system`** 3가지뿐. readiness 프로브 대상(`/health/ready`)은 이미 프로덕션에 존재 — 프로브에서 연결만 하면 됨.

- [ ] **Step 1 (bash):** 라이브 spec export (CLI 가 수용하는 완전한 입력 스키마 확보. show 는 secret 값을 반환 안 함 → 안전)
```bash
az containerapp show -n eundunhealth-api -g apps -o yaml > backend/containerapp.yaml
```
- [ ] **Step 2 (Edit):** read-only/status 필드 제거 — `id`, `systemData`, `properties.provisioningState`, `properties.latestRevisionName`, `properties.latestReadyRevisionName`, `properties.configuration.ingress.fqdn`, `properties.outboundIpAddresses`, `properties.eventStreamEndpoint`, `properties.customDomainVerificationId` 등. `containers[0].image:` 값을 `__IMAGE__` 로 치환.
- [ ] **Step 3 (Edit):** 의도 변경분만 반영 (그 외 블록 — 특히 `identity`/`ingress`/`ephemeralStorage` — 보존):
  - `properties.template.scale`: `minReplicas: 1`, `maxReplicas: 3`, `rules: [{name: http-concurrency, http: {metadata: {concurrentRequests: "50"}}}]`
  - `properties.template.containers[0].probes`: Startup/Liveness(`/health`) + Readiness(`/health/ready`) — Design §5.3 값(Startup failureThreshold 12 / Liveness 3 / Readiness 6).
  - `properties.configuration.registries[0]`: `passwordSecretRef` 제거 → `identity: system`.
  - `properties.configuration.secrets`: 4개 앱 secret 을 `{name, keyVaultUrl: https://kv-eundunhealth.vault.azure.net/secrets/<name>, identity: system}` 로 교체. **ACR pull secret `eundunhealthacrazurecrio-eundunhealthacr` 는 MI 전환으로 제거.**
  - `identity` 블록이 export 에 있는지 확인 — 없으면 `identity: {type: SystemAssigned}` 추가.
- [ ] **Step 4 (bash):** YAML 문법 + 값 미누출 확인 + commit
```bash
python -c "import yaml; yaml.safe_load(open('backend/containerapp.yaml')); print('ok')"
grep -iE 'postgresql://|supabase\.co|ingest\.us\.sentry|passwordSecretRef' backend/containerapp.yaml \
  && echo "WARN: 값/직접secret 누출 의심 — 재확인" || echo "값 없음 OK"
git add backend/containerapp.yaml
git commit -m "feat(infra): containerapp.yaml (라이브 spec 기반, KeyVault refs + probes + scale)"
```

### Task 7: `backend.yml` → `--yaml` 전환 + precheck KeyVault 화

**Files:** Modify `.github/workflows/backend.yml` (deploy job: secret precheck + deploy step)

- [ ] **Step 1 (Edit):** "Verify required Container App secrets exist" step 을 **KeyVault secret 존재 확인**으로 교체 (CI SP 가 Task 3 Step 6 으로 KeyVault 읽기 권한 보유)
```yaml
      - name: Verify required Key Vault secrets exist
        run: |
          REQUIRED="database-url supabase-url supabase-service-role-key sentry-dsn-backend"
          EXISTING=$(az keyvault secret list --vault-name kv-eundunhealth --query "[].name" -o tsv)
          MISSING=""
          for s in $REQUIRED; do echo "$EXISTING" | grep -qx "$s" || MISSING="$MISSING $s"; done
          if [ -n "$MISSING" ]; then echo "::error::Missing Key Vault secrets:$MISSING"; exit 1; fi
          echo "All required Key Vault secrets present: $REQUIRED"
```
- [ ] **Step 2 (Edit):** "Deploy to Container App" step 을 `--yaml` 경로로 교체 (Phase 1 의 scale 플래그·`--set-env-vars` 흡수)
```yaml
      - name: Deploy to Container App
        run: |
          sed "s|__IMAGE__|${IMAGE_TAG}|" backend/containerapp.yaml > /tmp/containerapp.rendered.yaml
          az containerapp update \
            --name "$CONTAINER_APP_NAME" --resource-group "$RESOURCE_GROUP" \
            --yaml /tmp/containerapp.rendered.yaml
```
- [ ] **Step 3 (bash):** YAML 문법 검증 + commit
```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/backend.yml')); print('ok')"
git add .github/workflows/backend.yml
git commit -m "feat(infra): deploy 를 containerapp.yaml(--yaml) 로 전환 + KeyVault precheck"
```

### Task 8: (옵션) Replicas 회귀 알림

> Design §5.6 — IaC self-heal + 월간 점검이 1차. 알림은 보조(scale-to-zero emit 모호). 채택 시에만.

**Files:** Modify `scripts/setup-azure-alerts.sh` (생성 + `--delete` 양쪽)

- [ ] **Step 1 (Edit):** 9번째 metric alert 추가. **`$CONTAINER_APP_ID`/`$ACTION_GROUP_ID` 는 스크립트 기존 변수명에 맞춰 사용**(상단 정의 확인). `Replicas` `min` 집계 + `--auto-mitigate` 로 scale-to-zero 미emit 보완
```bash
az monitor metrics alert create \
  --name "alert-ca-replicas-eundunhealth-prod" --resource-group apps \
  --scopes "$CONTAINER_APP_ID" \
  --condition "min Replicas < 1" \
  --window-size 15m --evaluation-frequency 5m --severity 2 \
  --auto-mitigate true \
  --description "Container App replica 0 강하 — minReplicas=1 regression" \
  --action "$ACTION_GROUP_ID"
```
- [ ] **Step 2 (bash):** idempotent 재실행 확인 + commit
  Run: `bash scripts/setup-azure-alerts.sh --dry-run`
```bash
git add scripts/setup-azure-alerts.sh
git commit -m "feat(ops): replica 회귀 감지 알림 (옵션)"
```

### Task 9: 운영 문서 갱신

**Files:** Modify `docs/ops/operations-snapshot.md`

- [ ] **Step 1 (Edit):** §2(Min/Max 1/3 + probes 3종 + registries=MI + secrets=KeyVault), §5(신규 Key Vault `kv-eundunhealth`), §9(Container Apps ~6,000원/월 + KV ~$0, 합계 갱신), §10(월간 `minReplicas==1` 확인 + idle-후 `/health` 측정), §12(옵션 채택 시 알림 8→9), §13(변경 이력 1줄 + **머지된 dep bump 반영**: sentry 8.43.1 · spotless 8.6.0 · detekt 1.23.8 · androidx core-ktx 1.19.0 · codecov-action 7). **추가**: `docs/ops/migration-runbook.md` 에 Task 3 KeyVault/MI 1회 셋업 절차 + Task 10 staging dry-run 절차 기록.
- [ ] **Step 2 (bash): commit**
```bash
git add docs/ops/operations-snapshot.md
git commit -m "docs(ops): warm baseline + KeyVault + probes 운영 상태 갱신"
```

### Task 10: [게이트] staging Container App 에서 `--yaml` true dry-run

> 점검 발견 #2: `az containerapp update --yaml` 는 what-if 가 없어 **prod 적용 = 실배포**다. KeyVault 전환 후 rollback 도 어렵다. 따라서 **throwaway staging 앱**에 동일 YAML 을 먼저 적용해 clobber/resolve 를 실증한 뒤 폐기한다. (cost no object — 단명 staging 정당). 운영자/Claude 협업: 리소스 생성·삭제는 운영자 승인 하에.

- [ ] **Step 1 (bash):** 같은 managed environment 에 staging 앱 생성(현 prod 이미지 + system MI)
```bash
ENV_ID=$(az containerapp show -n eundunhealth-api -g apps --query properties.managedEnvironmentId -o tsv)
IMG=$(az containerapp show -n eundunhealth-api -g apps --query "properties.template.containers[0].image" -o tsv)
az containerapp create -n eundunhealth-api-staging -g apps --environment "$ENV_ID" \
  --image "$IMG" --system-assigned --min-replicas 1 --max-replicas 1 --ingress external --target-port 8080
SPID=$(az containerapp show -n eundunhealth-api-staging -g apps --query identity.principalId -o tsv)
```
- [ ] **Step 2 (bash):** staging MI 에 KeyVault + ACR RBAC (prod 와 동일 조건)
```bash
KV_ID=$(az keyvault show -n kv-eundunhealth -g apps --query id -o tsv)
ACR_ID=$(az acr show -n eundunhealthacr --query id -o tsv)
az role assignment create --assignee-object-id "$SPID" --assignee-principal-type ServicePrincipal --role "Key Vault Secrets User" --scope "$KV_ID"
az role assignment create --assignee-object-id "$SPID" --assignee-principal-type ServicePrincipal --role "AcrPull" --scope "$ACR_ID"
```
- [ ] **Step 3 (bash):** 본 PR 의 containerapp.yaml 을 staging 이름으로 렌더해 적용
```bash
sed -e "s|__IMAGE__|$IMG|" -e "s|eundunhealth-api$|eundunhealth-api-staging|" \
  backend/containerapp.yaml > /tmp/staging.yaml
az containerapp update -n eundunhealth-api-staging -g apps --yaml /tmp/staging.yaml
```
- [ ] **Step 4 (bash):** 검증 — secrets=KeyVault·registries=MI 손상 없음 + KeyVault resolve(앱 기동) + probe
```bash
az containerapp show -n eundunhealth-api-staging -g apps \
  --query "configuration:properties.configuration.{secrets:secrets[].name, registriesIdentity:registries[0].identity}" -o json
SFQDN=$(az containerapp show -n eundunhealth-api-staging -g apps --query properties.configuration.ingress.fqdn -o tsv)
curl -sf "https://$SFQDN/health" && curl -sf "https://$SFQDN/health/ready"
```
  Expected: secrets 4개(KeyVault) + registries identity=system + `/health`·`/health/ready` 200. **실패 시 YAML 보완 후 재시도(prod 미적용).**
- [ ] **Step 5 (bash): cleanup** — staging 앱 + role assignment 삭제
```bash
az role assignment delete --assignee "$SPID" --scope "$KV_ID" 2>/dev/null || true
az role assignment delete --assignee "$SPID" --scope "$ACR_ID" 2>/dev/null || true
az containerapp delete -n eundunhealth-api-staging -g apps --yes
```

### Task 11: push + PR2 + 머지 후 검증

- [ ] **Step 1 (bash):** 전체 회귀
  Run: `cd backend && .venv/Scripts/pytest tests/ -v && docker compose up -d --build && curl -sf localhost:8080/health/ready && docker compose down -v`
- [ ] **Step 2 (bash):** push + PR
```bash
git push -u origin feat/containerapp-iac-keyvault
gh pr create --base main --title "feat(infra): full IaC(KeyVault) + health probes + /health/ready" \
  --body "design: docs/plans/2026-06-09-coldstart-warm-baseline-design.md. Task 10 staging dry-run 통과 첨부."
```
- [ ] **Step 3 (pwsh): 머지 후 검증** — 배포 중 무중단(readiness 전환) + KeyVault resolve + probe 동작
```powershell
$f = "https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io"
Invoke-WebRequest "$f/health" -UseBasicParsing | Select-Object StatusCode
Invoke-WebRequest "$f/health/ready" -UseBasicParsing | Select-Object StatusCode
az containerapp show -n eundunhealth-api -g apps --query "properties.template.containers[0].probes[].type" -o tsv  # Startup/Liveness/Readiness
```
  Expected: 둘 다 200, probes 3종 등록.

---

## 잔여 리스크 / 후속 작업
- Task 10 staging 에서 secrets/registries/probe/identity 이상 시 = `--yaml` 가 미기재 블록 reset → Task 6 의 라이브 export 에서 누락 블록(dapr/identitySettings/identity 등)을 YAML 에 복원 후 재시도(prod 미적용 상태에서 수렴).
- Android generated client 에 `healthReady` 메서드 추가됨(미사용, 무해). Repository 변경 불요.
- 옵션 Task 8 미채택 시 회귀 감지는 월간 점검(Task 9 §10)에 의존.

## Postmortem
> (PR2 머지 + 7일 후 채움. 계획 대비 차이 / 새 위험 / 다음 plan 교훈. 없으면 "특이사항 없음" 1줄.)

---

## PR 머지 후 (수동, 컨벤션 — plans-ledger-restructure)
Phase 1(#92) + /health/ready(#93) + Task 3 하드닝(#94) + Phase 2(PR2) 모두 머지 후 본 페어(design+plan)의 핵심 결정 + outcome 을 압축 entry(15-30줄)로
`docs/plans/logs/process-infra.md` 의 `## Recent (last 90 days)` 맨 위에 추가 → 페어 2 파일 `git rm`.
`bash scripts/gen-plans-index.sh` 재실행. Entry: Why(cold start 21.5s) / What(min=1 + /health/ready + KeyVault IaC + probes) / Outcome(검증 결과) / Files touched + PR 목록(#92/#93/#94/PR2).
