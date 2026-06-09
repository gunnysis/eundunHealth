---
type: design
status: approved  # proposed → approved → in-progress → [holding|deferred] → shipped (→ ledger archive)
pr: null
related_inc: null
supersedes: null
target_version: infra-only  # backend deploy via CI + 소량 backend 코드(/health/ready). Android 무관
ledger_topic: process-infra  # scale/probe/alert/ops-doc 가 주축
tags: [container-apps, cold-start, health-probes, scale, performance, iac, key-vault]
---

# 백엔드 Cold Start 제거 + Warm Baseline 견고화 설계

- **작성일**: 2026-06-09
- **상태**: 승인 — **가안(Phase 1 + Phase 2 Key Vault full IaC) 채택**. fact-check 2회 + 다관점(테스트·성능·운영·보안) hardening 반영 (2026-06-09)
- **연관 작업**: 사용자 "로그인 느림" 신고 → 측정 진단. Entra External ID 전환 평가 후 **A안(현행 유지 + cold start 해결)** 채택. memory `login-slowness-coldstart-rootcause.md`, `entra-external-id-cost-review.md`
- **대상 버전**: infra-only (backend.yml 자동 배포 + `/health/ready` 신규. Android versionCode 무변)
- **선행 작업**: 없음

> **Fact-check 반영 (2026-06-09)** — 라이브 인프라 + 공식 문서 검증으로 v1 초안의 **프로덕션 파손 위험을 교정**:
> 1. `az containerapp update --yaml` 는 **지정 섹션 full-replace** (공식 CLI ref). v1 의 §5.1 YAML 은 `registries`(ACR pull)·`secrets`(5개) 블록을 빠뜨려 **이미지 pull 파손 + secret 소실** 위험이었음.
> 2. **scale(min/max + http rule)은 `az containerapp update` 플래그로 설정 가능** (help 예시 확인) → secret/registry 무손상 안전 경로.
> 3. **안전한 committed full IaC YAML 은 Key Vault 참조가 전제** (공식 manage-secrets 권장: "production 에서 secret 값 직접 명시 회피, Key Vault 참조 사용"). 값이 YAML 에 없으니 커밋·재적용 안전.
> 4. `Replicas` metric 이름 확인 (official metrics doc). 단 scale-to-zero 시 emit 모호 + IaC 재적용 self-heal → 회귀 알림은 보조 수단으로 강등(§5.6).
> → **2단계 리팩토링**: Phase 1 = 위험 0 핵심 fix(imperative scale), Phase 2 = 안전한 full IaC YAML(Key Vault + probes).

## 1. 배경

사용자가 "Supabase free 로그인 인증이 느리다"고 반복 신고. **측정으로 근본 원인을 확정**(2026-06-09, `Measure-Command`):

| 구간 | Cold (첫 호출) | Warm | 판정 |
|---|---|---|---|
| 백엔드 Container App `/health` | **21,506 ms** | 19 ms | **← 느림의 주범** |
| Supabase Auth JWKS | 446 ms (TLS만) | 28 ms | 정상, pause 아님 |

**진단**: 로그인 자체는 `AuthRepositoryImpl.signIn()` 이 Supabase 를 직접 호출(~28ms)이라 빠르다. 체감 "로그인 느림"은 로그인 직후 앱이 백엔드(`/profile` 등)를 처음 부를 때 발생하는 **백엔드 cold start 21.5초**다.

원인 추적 (소스 확정):
- Container App `eundunhealth-api`: `minReplicas=0` (KEDA scale-to-zero), `cooldownPeriod=300s`. idle 5분 후 replica 0 → 다음 요청이 컨테이너를 깨움.
- 깨우는 비용 = ACR 이미지 pull + 노드 스케줄링 + `backend/entrypoint.sh:8` 의 `alembic upgrade head`(Azure PG 왕복) + uvicorn 부팅 + lifespan(`main.py:24` async engine 생성).
- **인증 제공자와 무관한 인프라 문제** — Supabase→Entra 교체로는 해결 안 됨. (Entra 비용 검토: 수백 MAU 절감 0 + 마이그레이션 큼 → 보류. §9.)

현재 인프라 상태 (live, 2026-06-09 `az containerapp show -o yaml`):
- scale: `minReplicas=0, maxReplicas=1, rules=null` (default HTTP scale rule), `cooldownPeriod=300, pollingInterval=30`
- probes: `null` → Container Apps **TCP-only 기본 프로브** (포트 open 검사일 뿐, 앱·DB 준비 미검증)
- `activeRevisionsMode=Single`, ingress targetPort 8080, container 0.25 vCPU / 0.5 GiB / ephemeral 1Gi
- **configuration.registries**: ACR pull `passwordSecretRef: eundunhealthacrazurecrio-eundunhealthacr` (admin 자격)
- **configuration.secrets**: 5개 (`database-url`, `supabase-url`, `supabase-service-role-key`, `sentry-dsn-backend`, ACR pull secret) — 직접 값(Container Apps Secret), Key Vault 미사용

## 2. Scope

### Phase 1 — 핵심 fix (위험 0, 즉시 배포 가능)
1. `minReplicas: 0 → 1` — warm replica 1개 상시 (idle cold start 영구 제거). **사용자 문제를 100% 해결.**
2. `maxReplicas: 1 → 3` + HTTP 동시성 scale rule — 배포/스파이크 헤드룸.
3. `backend.yml` deploy step 의 기존 `az containerapp update --image --set-env-vars` 에 **scale 플래그 추가** (`--min-replicas 1 --max-replicas 3 --scale-rule-name http-concurrency --scale-rule-http-concurrency 50`). registries/secrets/env 무손상.

### Phase 2 — 안전한 full IaC YAML + 견고화 (Key Vault 전제, **채택** — cost no object·full IaC 의도 부합)
4. **secret → Azure Key Vault 참조 마이그레이션**: 4개 앱 secret 을 Key Vault 에 저장, Container App 은 KeyVault 참조. system-assigned managed identity + `Key Vault Secrets User` RBAC.
5. **ACR pull 을 managed identity 로 전환** (admin password secret 제거) — 같은 MI 재사용.
6. **committed `backend/containerapp.yaml`** (완전 spec: registries via MI + secrets as KeyVault refs[값 없음] + ingress + scale + probes). git 안전(값 없음) + drift 0.
7. **앱-인지 HTTP 프로브 3종** (startup/liveness=`/health`, readiness=신규 `/health/ready`) — TCP 기본값 대체.
8. **신규 backend 엔드포인트** `GET /health/ready` — 경량 `SELECT 1` → 200/503.
9. `backend.yml` deploy 를 `--yaml` 경로로 전환 (이미지 태그 주입). 룰 6 secret precheck 를 KeyVault 기준으로 갱신.

### 공통
10. 회귀 가드(§5.6) + 운영 문서 갱신(§5.7) + Entra 보류 결정 명문화(§9).

### Out-of-scope (검토 후 의도적 제외 — 근본원인 규율)
- **alembic 을 entrypoint 에서 분리**: warm 후 재발 없음 + **룰 7(INC-2026-05-27-01)** row-lock 안전 설계 보존. 5분+ 백필 발생 시 재검토. → 제외.
- **이미지 슬림화**: warm baseline 에서 한계효용 낮음. → 제외.
- **Android OkHttp 타임아웃/RetryInterceptor 변경**: readiness 프로브가 미준비 라우팅 차단 → 불필요(YAGNI). → 제외.
- **인증 제공자 마이그레이션(Entra)**: §9 보류. → 제외.

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | cold start 해결 | `minReplicas=1` warm baseline | 측정상 21.5s 전 구간 제거. cost no object |
| D2 | scale 적용 방식 (Phase 1) | imperative `az containerapp update` 플래그 | **fact-check**: scale 플래그 지원 확인 + registries/secrets 무손상. `--yaml` clobber 위험 회피 |
| D3 | maxReplicas | 1 → 3 + HTTP 동시성 rule | 배포 무중단·스파이크 헤드룸. row-lock 으로 다중 replica 안전 |
| D4 | full IaC YAML 안전성 (Phase 2) | **Key Vault 참조** | **fact-check**: `--yaml` full-replace + 공식 권장. KeyVault ref 는 값 없음 → 커밋·재적용 안전. name-only 직접 secret 보존은 공식 미확정(gamble) |
| D5 | 프로브 종류 (Phase 2) | startup/liveness=`/health`, readiness=`/health/ready`(DB) | liveness 는 DB 비검사(PG 블립에 컨테이너 안 죽음), readiness 만 DB 검사(미준비 라우팅 차단) |
| D6 | readiness 실패 처리 | 503 직접 반환 + `logger.warning`, Sentry 미포착 | 5초 주기 프로브 → 글로벌 500 핸들러 Sentry flood 차단. 영속 실패는 5xx/replica 알림으로 surface |
| D7 | 회귀 가드 | **IaC 재적용 self-heal + 월간 점검**(주), Replicas 알림(보조·옵션) | **fact-check**: scale-to-zero 시 `Replicas` emit 모호 → 알림 단독은 불충분. Phase 2 IaC 가 매 배포 min=1 재적용 = self-healing |
| D8 | alembic 위치 | entrypoint 유지(제외) | 룰 7 안전 설계 보존. warm 후 재발 없음 |

## 4. 옵션 비교 (scale/probe 선언 방식 — fact-check 후 재구성)

| | A. imperative scale (Phase 1) | B. full IaC YAML + Key Vault (Phase 2) | C. full YAML, 직접 secret name-only |
|---|---|---|---|
| cold start 해결 | ✅ | ✅ | ✅ |
| clobber 위험 | ✅ 없음 (지정 항목만 변경) | ✅ 없음 (KeyVault ref 값 없음) | ❌ secret 소실 gamble (공식 미확정) |
| 앱-인지 프로브 | ❌ TCP 기본값 | ✅ 커밋된 YAML | ✅ |
| 단일 출처/git 안전 | △ CI 플래그(scale 만) | ✅ 완전 spec, 값 미커밋 | ⚠️ 값 미커밋이나 보존 불확실 |
| 추가 스코프 | 없음 | Key Vault + MI + RBAC + ACR MI | 없음 |
| 배포 전 검증 | 불필요 | staging dry-run 권장 | **필수**(name-only 보존 확인) |

→ **채택: A(즉시) + B(project-optimal 목표)**. C 는 secret 소실 gamble 이라 기각. cost no object → KeyVault 비용 무시 가능($0~소액), 안전·정석 우선.

## 5. 구성 요소별 변경

### 5.1 [Phase 1] MODIFY: `.github/workflows/backend.yml` deploy step — scale 플래그 추가

기존 (`backend.yml:237-248`)에 플래그만 추가 (구조·secret 경로 불변):
```bash
az containerapp update \
  --name "$CONTAINER_APP_NAME" --resource-group "$RESOURCE_GROUP" \
  --image "$IMAGE_TAG" \
  --set-env-vars \
    "DATABASE_URL=secretref:database-url" \
    "SUPABASE_URL=secretref:supabase-url" \
    "SUPABASE_SERVICE_ROLE_KEY=secretref:supabase-service-role-key" \
    "SENTRY_DSN=secretref:sentry-dsn-backend" \
    "ENVIRONMENT=production" \
  --min-replicas 1 --max-replicas 3 \
  --scale-rule-name http-concurrency --scale-rule-http-concurrency 50
```
- registries/secrets 미지정 → 무손상 (update 는 지정 항목만 변경). 룰 6 precheck step 불변.
- **이 한 PR 로 사용자 문제(21.5s) 즉시 해소.** Phase 2 는 후속 PR.

### 5.2 [Phase 2] Key Vault + managed identity 전환 (운영자 1회 셋업 + IaC)
- Key Vault `kv-eundunhealth` 생성(Korea Central). 4개 앱 secret 저장: `database-url`, `supabase-url`, `supabase-service-role-key`, `sentry-dsn-backend`.
- Container App **system-assigned MI** 활성 → KeyVault `Key Vault Secrets User` + ACR `AcrPull` RBAC 부여.
- registries: `passwordSecretRef` → `identity: system` (admin password secret 제거).
- `scripts/register-azure-credentials.ps1` / 운영 runbook 에 1회 셋업 절차 추가.

### 5.3 [Phase 2] NEW: `backend/containerapp.yaml` (완전 spec, 값 미포함)
```yaml
# az containerapp update -n eundunhealth-api -g apps --yaml <rendered>
properties:
  configuration:
    activeRevisionsMode: Single
    ingress: { external: true, targetPort: 8080, transport: Auto }
    registries:
      - server: eundunhealthacr.azurecr.io
        identity: system            # MI pull (admin password secret 제거)
    secrets:                         # KeyVault 참조 — 값 없음, 커밋 안전
      - { name: database-url,              keyVaultUrl: https://kv-eundunhealth.vault.azure.net/secrets/database-url,              identity: system }
      - { name: supabase-url,              keyVaultUrl: https://kv-eundunhealth.vault.azure.net/secrets/supabase-url,              identity: system }
      - { name: supabase-service-role-key, keyVaultUrl: https://kv-eundunhealth.vault.azure.net/secrets/supabase-service-role-key, identity: system }
      - { name: sentry-dsn-backend,        keyVaultUrl: https://kv-eundunhealth.vault.azure.net/secrets/sentry-dsn-backend,        identity: system }
  template:
    scale:
      minReplicas: 1
      maxReplicas: 3
      rules:
        - { name: http-concurrency, http: { metadata: { concurrentRequests: "50" } } }
    containers:
      - name: eundunhealth-api
        image: __IMAGE__            # CI 가 ${ACR}/eundunhealth-api:${SHA::7} 로 치환
        resources: { cpu: 0.25, memory: 0.5Gi }
        env:
          - { name: DATABASE_URL,              secretRef: database-url }
          - { name: SUPABASE_URL,              secretRef: supabase-url }
          - { name: SUPABASE_SERVICE_ROLE_KEY, secretRef: supabase-service-role-key }
          - { name: SENTRY_DSN,                secretRef: sentry-dsn-backend }
          - { name: ENVIRONMENT, value: production }
        probes:
          - type: Startup     # 부팅(alembic+uvicorn) 동안 조기 재시작 방지
            httpGet: { path: /health, port: 8080 }
            initialDelaySeconds: 5
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 12   # 5 + 5×12 ≈ 65s 예산 (측정 cold 21.5s + 여유)
          - type: Liveness    # 멈춘 프로세스 재시작 (DB 비검사)
            httpGet: { path: /health, port: 8080 }
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          - type: Readiness   # DB 준비 시에만 트래픽 (Single 모드 자동 전환)
            httpGet: { path: /health/ready, port: 8080 }
            initialDelaySeconds: 3
            periodSeconds: 5
            timeoutSeconds: 5
            failureThreshold: 6
```

### 5.4 [Phase 2] MODIFY: `.github/workflows/backend.yml` → `--yaml` 경로
```bash
sed "s|__IMAGE__|${IMAGE_TAG}|" backend/containerapp.yaml > /tmp/containerapp.rendered.yaml
az containerapp update -n "$CONTAINER_APP_NAME" -g "$RESOURCE_GROUP" --yaml /tmp/containerapp.rendered.yaml
```
- Phase 1 의 scale 플래그·`--set-env-vars` 는 YAML 로 흡수(대체).
- 룰 6 precheck: Container App secret 존재 확인 → **KeyVault secret 존재 + MI 접근 확인**으로 갱신.

### 5.5 [Phase 2] MODIFY: `backend/app/routers/health.py` — `/health/ready` 추가
```python
from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text
import logging

logger = logging.getLogger(__name__)
router = APIRouter(tags=["health"])


@router.get("/health", operation_id="healthCheck")
async def health() -> dict[str, str]:
    """프로세스 가동 상태. JWT 불필요 — startup/liveness probe 전용 (DB 비검사)."""
    return {"status": "ok"}


@router.get("/health/ready", operation_id="healthReady")
async def health_ready(request: Request) -> JSONResponse | dict[str, str]:
    """readiness probe — DB 연결 가능 시에만 200. 미준비면 503 → 트래픽 차단.

    5초 주기 probe 라 글로벌 500 핸들러(Sentry 포착)를 안 타도록 여기서 직접 503.
    영속 실패는 5xx + replica 알림(§5.6)으로 surface — silent failure 아님.
    """
    try:
        async with request.app.state.session_factory() as session:
            await session.execute(text("SELECT 1"))
    except Exception as e:  # noqa: BLE001 — probe 는 모든 DB 오류를 503 으로 환원
        logger.warning("readiness check failed: %r", e)
        return JSONResponse(status_code=503, content={"status": "not ready"})
    return {"status": "ready"}
```
- `bash scripts/sync-openapi.sh` + `backend/openapi.json` 같은 PR 커밋 (operation_id `healthReady`).
- **테스트 가능성 (testing 관점 — fact-check 발견)**: `request.app.state.session_factory` 직접 참조는 pytest `ASGITransport`(lifespan 우회 — `backend.yml:95` 코멘트)에서 **unset → AttributeError**. 따라서 **overridable dependency** `get_session_factory(request)` 로 주입하고 테스트에서 fake factory 로 `app.dependency_overrides`. 단위 테스트 2개: DB 정상→200 / `execute` 예외→503 (+ Sentry 미포착 단언). TDD red→green.

### 5.6 회귀 가드 (fact-check 후 재설계)
- **1차(주): IaC self-heal** — Phase 2 후 매 배포가 `containerapp.yaml`(min=1) 재적용 → 수동 drift 가 다음 배포에서 자동 원복.
- **2차: 월간 점검** — `operations-snapshot.md §10` 에 `az containerapp show --query properties.template.scale.minReplicas` == 1 확인 1줄.
- **3차(옵션): Replicas 알림** — `setup-azure-alerts.sh` 에 `Replicas` min<1 알림. 단 **scale-to-zero 시 metric emit 모호**(0 vs null) → "no data = breach" 설정 필요. 우선순위 낮음(1·2차로 충분). 추가 시 `--delete` 경로 포함.

### 5.7 MODIFY: `docs/ops/operations-snapshot.md`
- §2: `Min / Max replicas 0 / 1` → `1 / 3`, probes 3종, (Phase 2) registries=MI / secrets=KeyVault.
- §5(신규 가능): Key Vault `kv-eundunhealth` 항목.
- §9: Container Apps `~0원` → `~6,000원/월` + Key Vault(~$0, 거래 소액). 합계/budget 갱신.
- §10: 월간 `minReplicas==1` 확인 + idle-후 `/health` 측정.
- §12: (옵션 채택 시) 알림 8 → 9.

## 6. 검증 계획

### 6.A 동작 검증
- **Phase 1 머지 후**: 배포 후 300s(cooldown) 초과 idle → `Measure-Command { curl .../health }` < 100ms (scale-to-zero 없음) + `az containerapp show --query properties.template.scale` → minReplicas 1.
- **Phase 2**: ① **단위 테스트** `/health/ready` 200(DB ok)/503(DB 예외, Sentry 미포착) + PR runtime-smoke 에 `/health/ready` 200 추가 ② **staging/dry-run** 에서 `--yaml` 적용 전후 `az containerapp show --query "properties.configuration.{secrets:secrets[].name, registries:registries[].server}"` 비교 → secret/registry 무손상 확인 ③ 배포 중 무중단(readiness 자동 전환) ④ Key Vault 참조 resolve 확인(앱 정상 기동).

### 6.X 추정값 → 측정 검증 (룰 9)

| 항목 | 라벨 | 명령/근거 |
|---|---|---|
| cold start 21,506ms / warm 19ms | `MEASURED` | 2026-06-09 `Measure-Command` (배경 §1) |
| Container Apps idle 단가 $0.000003/vCPU-s·GiB-s | `MEASURED` | Azure Retail Prices API (koreacentral, 2026-06-09) |
| min=1 월 비용 ~$4.3 (≈6,000원) | `MEASURED` | 0.25vCPU/0.5GiB × 2,628,000s − free grant × idle 단가 |
| `--yaml` 가 KeyVault-ref secret/registry 무손상 적용 | `DEFERRED — verify at Phase 2 (staging dry-run)` | §6.A ② 명령 |
| `Replicas` metric 이름 | `MEASURED → 해소` | official metrics doc (Metric ID `Replicas`). DEFERRED #2 종결 |
| `az containerapp update` scale 플래그 지원 | `MEASURED → 해소` | `az containerapp update --help` (예시 `--min-replicas 4 --max-replicas 8`) |

> spec self-review 에서 `MEASURED` 명령 1회 재실행 + 결과 일치 확인(룰 10).

## 7. 롤백 절차
- **Phase 1**: `git revert` 후 backend.yml 재배포(scale 플래그 제거) 또는 즉시 `az containerapp update --min-replicas 0 --max-replicas 1`.
- **Phase 2**: `--yaml` 회귀 시 Phase 1 imperative 경로로 복귀(`--image --set-env-vars --min-replicas`). secret 은 KeyVault 에 보존되므로 Container App secret 직접 값으로 복원 가능(`az containerapp secret set`). `/health/ready` 제거는 호환성 영향 0.
- 알림: `bash scripts/setup-azure-alerts.sh --delete`.

## 8. 잔여 리스크
- **`--yaml` clobber (Phase 2)**: 완전 spec + KeyVault ref 로 설계상 제거, 단 §6.A ② staging dry-run 으로 배포 전 실증 **필수**.
- **Key Vault 참조 resolve 실패** (MI 권한 누락 등): 앱 기동 실패 → 배포 health check 차단. RBAC 부여 선행 + dry-run 으로 차단. troubleshooting: 공식 manage-secrets §Key Vault.
- **readiness 503 가 DB 장애를 가릴 수 있음**: liveness(DB 비검사) 유지 + 영속 not-ready 는 5xx 알림으로 surface → silent failure 아님(D6).
- **maxReplicas=3 다중 alembic 동시 기동**: row-lock 직렬화(룰 7)로 안전. 신규 마이그레이션 배포 시 첫 replica 적용·나머지 no-op 확인 권장.
- **배포마다 1회 cold(신 revision)**: warm baseline 무관. startup probe(65s 예산) 흡수 + Single 모드 무중단 전환.
- **PG 커넥션 수 (performance 관점)**: maxReplicas=3 × `pool_size=3`(`main.py:24`, max_overflow 0) = 최대 9 connection + readiness probe. Azure PG B1ms 한도 대비 여유, 기존 `active_connections>20` 알림 임계 하회. 상시 3-replica 화 시 재점검.
- **Key Vault 운영 의존성 (operational 관점)**: KV 장애/RBAC 누락 시 secret resolve 실패 → 기동 차단(health check 가 배포 게이트). 이점: rotation 이 KV 단일 지점(앱 30분 내 자동 반영). KV 가용성은 기존 ServiceHealth 알림 범주.
- **`/health/ready` 공개 노출 (security 관점)**: 인증 없는 DB-touch 엔드포인트. 쿼리는 `SELECT 1`(무시할 부하) + probe 는 컨테이너 내부 접근이라 ingress 노출 불필수. 남용 시 pool(3) 경합 가능하나 trivial. 모니터링으로 충분, 별도 차단 불요(YAGNI).

## 9. Entra External ID 전환 — 보류 결정 + 트리거 조건

수백 MAU·이메일+비밀번호·동기=Azure 비용 일원화 전제 평가(memory `entra-external-id-cost-review.md`):
- **비용**: 수백 MAU 에서 Supabase $0 = Entra External ID $0(절감 0). 5만 MAU 초과 시 Entra 가 오히려 비쌈(100k 시 $275 vs Supabase Pro $25).
- **마이그레이션 비용**: Android MSAL + 백엔드 JWT(issuer/audience/`sub`→`oid`) + App Links + account 삭제(Graph) 전면 교체 — 큼.
- **결론**: cold start 도 안 풀리고 비용 절감 0 → **보류**.

**재검토 트리거** (하나라도 성립 시):
1. 엔터프라이즈/B2B SSO(조직 계정 로그인) 요구 발생.
2. Supabase 유료화 또는 무료 등급 제약(pausing 등)이 운영을 실제 저해.
3. MFA·소셜 등 Supabase 가 못 주는 기능이 제품 요구가 됨.
4. **출시(인증 사용자 >0) 전** — user_id namespace 교체가 무비용인 유일한 창(룰 5). 전환할 거면 이 창에서.

## 10. 참고 자료
- Microsoft Learn — Container Apps **Health probes** (updated 2026-03-25): default TCP 프로브, Single 모드 readiness 자동 전환, 긴 startup 시 probe 튜닝.
- Microsoft Learn — Container Apps **Metrics** (updated 2026-04-03): Metric ID `Replicas`(active replica 수), `RestartCount`, namespace `Microsoft.App/containerapps`.
- Microsoft Learn — **Manage secrets** (updated 2026-04-14): production 권장 = Key Vault 참조, MI + `Key Vault Secrets User` RBAC.
- Azure CLI — `az containerapp update` (`--min-replicas`/`--max-replicas`/`--scale-rule-*` 지원, `--yaml` = 지정 섹션 full-replace).
- Azure Retail Prices API — koreacentral Container Apps + AAD B2C MAU 단가 (2026-06-09 실측).
- memory: `login-slowness-coldstart-rootcause.md`, `entra-external-id-cost-review.md`.
- 룰 6(secretref 3중 변경), 룰 7(alembic entrypoint row-lock), 룰 9(측정 라벨).
