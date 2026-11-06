# 모니터링 & 비용 관리 (Azure Free Account)

> 작성일: 2026-05-24
> 대상: `eundunhealth-api` (Container Apps, RG `rg-eundunhealth-prod-krc`), `eundunhealthacr` (ACR)

## 1. Sentry 설정

| 위치 | 환경 | tracesSampleRate | DSN 상태 |
|------|------|------------------|---------|
| Android `EundunHealthApplication.kt` | DEBUG | 1.0 | `local.properties:eundunhealth-app_SENTRY_DSN` 설정됨 |
| Android `EundunHealthApplication.kt` | PROD | 0.2 | 동일 |
| Backend `app/main.py` (lifespan) | development | 1.0 | `.env:SENTRY_DSN` 빈 값 → init skip |
| Backend `app/main.py` (lifespan) | production | 0.2 | **활성 (`kv-eundunhealth` Key Vault `sentry-dsn-backend` 참조)** |

프로덕션 샘플링 0.2 = 트랜잭션 80% 절감. Sentry 무료 할당량(10K events/month) 안에서 안정 운영.

### 1.1 백엔드 DSN Key Vault 연동 절차 (완료 — 2026-06-09 Key Vault IaC 전환)

백엔드 Sentry는 `gunnys/eundunhealth-backend` 독립 프로젝트로 분리되어 있으며, DSN은 Azure Key Vault(`kv-eundunhealth`)의 `sentry-dsn-backend` 시크릿에서 Managed Identity(`identity: system`)로 주입된다(`backend/containerapp.yaml`).

1. **Key Vault 시크릿 갱신 시**
   ```bash
   az keyvault secret set --vault-name kv-eundunhealth --name sentry-dsn-backend --value "<DSN>"
   ```
   > 즉시 반영이 필요하면 `docs/ops/operations-snapshot.md §2` 의 버전 고정 후 리비전 재시작 절차를 따른다.

2. **헬스체크 + Sentry 대시보드에서 release health 확인**
   ```bash
   curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health
   ```

### 1.2 검증 — DSN 미설정 시 백엔드 동작

`app/main.py`의 lifespan은 `settings.sentry_dsn`이 빈 문자열이면 `sentry_sdk.init`를 건너뛴다. 따라서 SENTRY_DSN="" 상태에서도 백엔드는 정상 동작하며, exception_handler가 `sentry_sdk.is_initialized()`로 체크해 캡처 호출도 안전하게 스킵된다.

## 2. ACR 이미지 보존 기간 (Basic SKU 한계 + 우회)

오래된 이미지가 누적되면 ACR Basic 7,000원/월의 스토리지 한도(10GB)를 초과할 수 있다.

⚠️ **`az acr config retention update`는 Basic SKU에서 미지원** — 시도 시 다음 에러:

```
Policies are only supported for managed registries in Premium SKU.
```

Premium SKU 업그레이드(~17,000원/월 이상)는 비용 부담이 있어, 이 프로젝트는 다음 두 방식으로 우회:

### 2.1 redeploy.sh 자동 정리 후크 (도입 완료)

`C:/programming/docker/eundunhealth-api/redeploy.sh`가 매 배포 후크에서 다음을 수행:

- 대상: `YYYYMMDD-HHMMSS` 패턴 timestamp 태그만
- 보존: 최근 5개 + 운영 중인 태그
- 정리 방식: `az acr repository untag` (manifest 자체는 다른 별칭 태그가 가리키지 않을 때만 자연 정리됨)
- 헬스체크 실패 시엔 정리 안 함 (롤백 안전망)

별도 명령 실행 불필요 — `bash redeploy.sh` 호출 시 자동 동작.

### 2.2 정기 자동 정리 (`acr purge` ACR Task 2종) 및 수동 점검

2026-09-01부터 매주 일요일 UTC 새벽에 실행되는 **ACR 스케줄 Task 2개**(`purge-eundunhealth-api`, `purge-eundunhealth-api-untagged`)가 최근 30일·최대 10개 태그만 남기고 미사용 manifest를 자동 정리한다(`operations-snapshot.md §3` 참조).

```bash
# 누적된 태그 및 정리 Task 실행 이력 확인
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api --orderby time_desc -o tsv
az acr task list-runs --registry eundunhealthacr -o table

# 특정 태그만 제거 (룰 1 준수: az acr repository delete --image <tag> 절대 금지 — 공유 manifest 파괴 방지)
az acr repository untag --name eundunhealthacr --image eundunhealth-api:<old-tag>

# 태그 없는(untagged) manifest digest 확인 및 삭제
az acr manifest list-metadata --name eundunhealthacr --repository eundunhealth-api --query "[?tags==null].digest" -o tsv
az acr repository delete --name eundunhealthacr --image eundunhealth-api@sha256:<untagged-digest> --yes
```

> Premium SKU로 업그레이드한다면 다음 명령으로 자동화 가능:
> ```bash
> az acr config retention update --registry eundunhealthacr --type UntaggedManifests --days 30 --status enabled
> ```

## 3. Azure 비용 알림 (구성 완료)

2026-10-16 Microsoft Cost Management REST API로 subscription budget을 생성하고 GET으로 재검증했다.

| 항목 | 값 |
|---|---|
| 이름 | `eundunhealth-monthly` |
| 금액 / 주기 | 70,000 / Monthly (구독 청구 통화 기준) |
| 기간 | 2026-10-01 ~ 2036-10-01 |
| 실제 비용 알림 | 80%, 100% (`GreaterThan`) |
| 수신자 | `qkr133456@gmail.com` |
| 언어 | `ko-kr` |

`az consumption budget` 명령 그룹은 Preview이고 현재 CLI의 create 인자는 notification 설정을
지원하지 않아, 공식 `Microsoft.CostManagement/budgets` REST API `2026-06-01`을 사용했다.
운영 확인 경로는 Azure Portal → Cost Management + Billing → Budgets이다.

## 4. 예상 비용 (참고)

| 서비스 | 월 예상 |
|--------|---------|
| Container Apps (Min 1 warm — cold start 제거) | ~6,000원 |
| Key Vault Standard | ~0원 (저빈도 read, 무료한도 내) |
| Container Registry Basic | ~7,000원 |
| PostgreSQL Flexible B1ms + 32GB | ~30,000원 |
| Azure AI Foundry (`gpt-5.4-mini`, `GlobalStandard` 100K TPM) | 종량제 (~수백원, 유휴 비용 $0) |
| Azure Monitor Alerts (metric 4개) | ~550-700원 |
| **합계** | **~43,700원** |

70,000원 budget은 약 1.6배 buffer (cold start 제거로 Container Apps warm 단가 ~6,000원 반영)다.
Monitor Alert 비용 = metric alert
4개 × ~$0.10/월. Activity Log alert 4개는 무료.

## 5. 운영 점검 체크리스트 (월 1회)

- [ ] Sentry 대시보드: 에러율 < 1%, performance score 양호한지 확인
- [ ] `az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api`로 이미지 수 확인 (30개 미만)
- [ ] Container App revision 수 정리: `az containerapp revision list --name eundunhealth-api -o table` → 활성 외 inactive revision 정리
- [ ] PostgreSQL slow query 확인: Azure Portal → Insights
- [ ] `eundunhealth-monthly` 월간 actual vs 70,000 budget 비교, 80%·100% 알림 수신 확인
- [ ] Azure Monitor alert 확인: `az monitor metrics alert list -g rg-eundunhealth-prod-krc -o table` + `az monitor activity-log alert list -g rg-eundunhealth-prod-krc -o table` (8개 enabled)
- [x] ~~(분기별) GitHub Actions `AZURE_CREDENTIALS` service principal 만료 점검~~ — **소멸(2026-07-03)**: OIDC 연합 전환(PR #141/#142) + `AZURE_CREDENTIALS` 완전 제거(GitHub secret + Entra 앱 비밀번호)로 만료되는 장수명 secret 없음. §6.7 참조.

## 6. Destructive 명령 안전 패턴

운영 중 한 번의 잘못된 명령이 운영 이미지·secret·DB를 망가뜨릴 수 있다. 모든 사례별 안전 패턴을 모아둔다. **참조 인시던트는 `docs/ops/incident-log.md`.**

### 6.1 ACR — manifest 삭제 vs 태그 제거 (참고: INC-01)

| 의도 | 위험한 명령 | 안전한 대안 |
|------|------------|-------------|
| 특정 태그만 떼고 manifest는 보존 | `az acr repository delete --image <repo>:<tag> --yes` | `az acr repository untag --name <reg> --image <repo>:<tag>` |
| 특정 manifest digest 삭제 (다른 태그가 그걸 가리키지 않을 때만) | (위와 같이 manifest 모두 삭제 위험) | 사전 점검: `az acr manifest list-metadata -r <reg> -n <repo> --query "[?digest=='sha256:...'].tags"` → 비어있을 때만 삭제 |
| 옛 태그·dangling 정리 | 수동 일괄 삭제 | **`acr purge` 스케줄 ACR Task 2개**(2026-09-01~). 로컬 경로는 `redeploy.sh` 가 timestamp 최근 5개 보존 |

> **요지**: `az acr repository delete --image <tag>`는 **태그가 가리키는 manifest 자체를 삭제**한다. 같은 manifest를 가리키는 모든 태그가 함께 사라진다. 옛 이미지를 untag만 하려면 반드시 `az acr repository untag`.

### 6.2 Container App secret 교체 (참고: INC-15, Key Vault IaC)

모든 운영 시크릿(8종)은 Azure Key Vault(`kv-eundunhealth`)를 단일 출처로 참조한다(`identity: system` / reaper Job은 `id-eundunhealth-reaper`). 직접 값을 Container App에 넣지 않는다.

```bash
# 1) Key Vault에 새 시크릿 버전 등록
az keyvault secret set --vault-name kv-eundunhealth --name <secret-name> --value "$NEW_VALUE"

# 2) 즉시 반영이 필요한 경우(30분 캐시 우회): 버전 ID를 포함한 keyvaultref로 갱신 후 리비전 재시작
# 상세 절차는 docs/ops/operations-snapshot.md §2 "시크릿 값을 바꿨을 때" 참조
```

### 6.3 프로덕션 DB 스키마 부트스트랩 / 하드 리셋 (참고: INC-06, INC-14, v0.7.0 PG18 전환)

> **2026-10-29(v0.7.0) 변경**: 과거 Alembic 점진 마이그레이션(`alembic upgrade head`)은 완전 폐기되고, PostgreSQL 18.4 **Drop & Recreate 전용 파이프라인(`backend/sql/02_schema_create_all.sql`, `python -m app.schema_bootstrap`, `backend/scripts/hard_reset_db.py`)** 으로 일원화되었다. 운영 DB에서 파괴적 리셋을 수행할 때는 반드시 룰 5 및 사용자 사전 승인을 거친다.

```bash
# 1) firewall 임시 허용
MY_IP=$(curl -sf https://api.ipify.org)
RULE_NAME="temp-$(date +%s)"
az postgres flexible-server firewall-rule create \
  --resource-group rg-eundunhealth-prod-krc --server-name healthapp \
  --name "$RULE_NAME" --start-ip-address "$MY_IP" --end-ip-address "$MY_IP"

# 2) 작업 (멱등 스키마 검증 또는 승인된 하드 리셋)
cd backend
export DATABASE_URL="postgresql+asyncpg://<db-admin-user>:****@healthapp.postgres.database.azure.com:5432/postgres?ssl=require"
.venv/Scripts/python.exe -m app.schema_bootstrap

# 3) 반드시 회수 (성공/실패 무관)
az postgres flexible-server firewall-rule delete \
  --resource-group rg-eundunhealth-prod-krc --server-name healthapp \
  --name "$RULE_NAME" --yes
```

> **요지**: `az containerapp exec --command "..."`로 비대화형 명령 결과를 받기는 불안정(INC-06). 로컬 firewall 임시 허용 패턴이 정석.

### 6.4 Auth 제공자/테넌트 교체 시 (참고: INC-14, 룰 5)

실사용자 확보 후에는 **Auth 제공자·테넌트 교체를 절대 자유롭게 하지 말 것**(IdP 무관). user_id namespace가 갈아엎혀 옛 사용자가 "user not found"가 된다.

- ~~출시 전: 5개 사용자 테이블 `TRUNCATE`로 정리 가능~~ — **2026-06-29 프로덕션 정식 출시로 닫힌 경로**(2026-05 수행 이력만 해당).
- 출시 후(현 단계) 불가피한 경우: 옛 user_id → 새 user_id 매핑 테이블 + 백필 스크립트 + 사용자 공지가 필수.

### 6.5 Sentry 프로젝트 교체 시 (참고: INC-09, INC-10)

- DSN을 `local.properties`에서 읽는 키 이름이 바뀌면 `app/build.gradle.kts`의 fallback 체인으로 흡수 (이미 적용: `eundunhealth-app_SENTRY_DSN` → `SENTRY_DSN`).
- Sentry project slug (sentry-gradle plugin의 `projectName`)가 바뀌면 `local.properties:SENTRY_PROJECT_ANDROID=<new-slug>`로 override.
- 새 APK/AAB를 빌드 → 단말 재설치해야 새 DSN 적용. 옛 빌드는 죽은 DSN으로 이벤트 발송 → 무시됨.

### 6.6 `containerapp.yaml` / `reaper-job.yaml` `secretref` 신규/변경 시 Key Vault 동기화 (참고: INC-18, 룰 6)

`.github/workflows/backend.yml`의 배포는 `backend/containerapp.yaml` 및 `backend/reaper-job.yaml`(`--yaml`)을 단일 출처로 사용한다. **YAML에 `secretref`만 추가하고 Key Vault(`kv-eundunhealth`) 및 YAML `secrets:` 섹션에 등록하지 않으면 배포가 실패한다.**

새 `secretref`를 추가하는 PR은 반드시 한 단위로 다음 네 곳을 함께 처리한다(룰 6):

1. **Key Vault(`kv-eundunhealth`)에 시크릿 등록** (PR 머지 *전*에):
   ```bash
   az keyvault secret set --vault-name kv-eundunhealth --name <secret-name> --value "<value>"
   ```
2. **`backend/containerapp.yaml` (및 필요 시 `backend/reaper-job.yaml`)** 의 `secrets:`(`keyVaultUrl` + `identity`)와 `env:`(`secretRef`) 동시 추가
3. **`.github/workflows/backend.yml`** 의 "Verify required Key Vault secrets exist" step `REQUIRED` 배열에 시크릿 이름 추가
4. **`docs/ops/operations-snapshot.md` §2 Secrets 목록 갱신**

### 6.7 GitHub repo secret 만료 (참고: INC-17) — 2026-07-03 소멸

~~`AZURE_CREDENTIALS`(service principal JSON)는 기본 2년 만료~~ → **OIDC 연합 전환(PR #141/#142) 후 2026-07-03 완전 제거**(GitHub secret + Entra 앱 비밀번호 credential 모두). Azure 로그인은 federated credential `github-main`(subject=`repo:gunnysis/eundunHealth:ref:refs/heads/main`) 기반 단기 토큰 — **만료 개념 없음**, 분기별 점검 불요.

OIDC 장애 시 긴급 폴백(장수명 secret 방식으로 임시 복귀):

```bash
# 1) 워크플로 revert: azure/login 을 creds: ${{ secrets.AZURE_CREDENTIALS }} 방식으로 되돌림
# 2) SP credential 재생성 + GitHub secret 재등록 (기존 secret 불요 — 스크립트가 reset)
pwsh -File scripts\register-azure-credentials.ps1 -Verify
```

스크립트가 SP를 patch 모드로 동작하므로 GitHub Actions 측은 secret만 새 JSON으로 덮어쓰면 바로 동작.

### 6.8 destructive 명령 실행 전 5-second sanity check

명령 입력 직전에:

1. **대상이 운영 리소스인가?** (RG `rg-eundunhealth-prod-krc`, registry `eundunhealthacr` 등 — 맞다면 한 번 더 의심)
2. **`--yes` 또는 `--no-confirm` 플래그가 있는가?** 있다면 무엇이 삭제되는지 미리 dry-run.
3. **연쇄 영향이 있는가?** (manifest 공유, secretref 연결, firewall rule 의존성)
4. **롤백 경로가 있는가?** (이미지 캐시·git 백업·DB PITR)
5. **에러 시 대비책이 있는가?** Sentry/Health Check로 즉시 인지 가능?

## 7. Azure Monitor Alerts

> 설계 문서: `docs/plans/logs/process-infra.md`
> 프로비저닝 스크립트: `scripts/setup-azure-alerts.sh`

### 7.1 Action Group

| 항목 | 값 |
|------|---|
| Name | `ag-eundunhealth-prod` |
| Short name | `ag-eundun` (12자 제한) |
| Notification | Email → `qkr133456@gmail.com` |

### 7.2 Alert 인벤토리

**P1 — Activity Log (무료)**

| Alert | 유형 | 조건 | Severity |
|---|---|---|---|
| `alert-servicehealth-eundunhealth-prod` | ServiceHealth | Korea Central + Container Apps / PostgreSQL 서비스 장애/유지보수 | Sev3 |
| `alert-resourcehealth-eundunhealth-prod` | ResourceHealth | `eundunhealth-api` 또는 `healthapp` Degraded/Unavailable | Sev1 |
| `alert-deletion-eundunhealth-prod` | Administrative | RG `rg-eundunhealth-prod-krc` 내 리소스 삭제 이벤트 | Sev1 |

**P2 — Metric (~550-700원/월)**

| Alert | Metric | 조건 | Sev | Eval | Window |
|---|---|---|---|---|---|
| `alert-pgsql-cpu-eundunhealth-prod` | `cpu_percent` | avg > 80% | Sev2 | 1m | 5m |
| `alert-pgsql-storage-eundunhealth-prod` | `storage_percent` | avg > 80% | Sev1 | 5m | 15m |
| `alert-pgsql-connections-eundunhealth-prod` | `active_connections` | avg > 20 | Sev2 | 1m | 5m |
| `alert-ca-5xx-eundunhealth-prod` | `Requests` (5xx) | total > 3 | Sev1 | 1m | 5m |

**P2 — Activity Log (무료)**

| Alert | 조건 | Severity |
|---|---|---|
| `alert-pgsql-firewall-eundunhealth-prod` | PostgreSQL firewall rule 변경 (write) | Sev3 |

### 7.3 관리

```bash
# 전체 재생성 (idempotent)
bash scripts/setup-azure-alerts.sh

# dry-run (명령 출력만)
bash scripts/setup-azure-alerts.sh --dry-run

# 전체 삭제 (롤백)
bash scripts/setup-azure-alerts.sh --delete

# 상태 확인
az monitor metrics alert list -g rg-eundunhealth-prod-krc --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled}" -o table
az monitor activity-log alert list -g rg-eundunhealth-prod-krc --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled}" -o table
```

### 7.4 비용

Metric alert 4개 × ~$0.10/월 = ~$0.40/월 (~550-700원). Activity Log alert 4개는 무료. 현 ~43,700원 대비 ~1.6% 증가.
