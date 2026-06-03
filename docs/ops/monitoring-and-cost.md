# 모니터링 & 비용 관리 (Azure Free Account)

> 작성일: 2026-05-24
> 대상: `eundunhealth-api` (Container Apps, RG `apps`), `eundunhealthacr` (ACR)

## 1. Sentry 설정

| 위치 | 환경 | tracesSampleRate | DSN 상태 |
|------|------|------------------|---------|
| Android `EundunHealthApplication.kt` | DEBUG | 1.0 | `local.properties:SENTRY_DSN` 설정됨 |
| Android `EundunHealthApplication.kt` | PROD | 0.2 | 동일 |
| Backend `app/main.py` (lifespan) | development | 1.0 | `.env:SENTRY_DSN` 빈 값 → init skip |
| Backend `app/main.py` (lifespan) | production | 0.2 | **Container App env `SENTRY_DSN=""` (비활성)** |

프로덕션 샘플링 0.2 = 트랜잭션 80% 절감. Sentry 무료 할당량(10K events/month) 안에서 안정 운영.

### 1.1 백엔드 DSN 활성화 절차 (선택)

마이그레이션 직후 백엔드 Sentry는 의도적으로 비활성 — Android와 같은 프로젝트로 보내면 이벤트가 섞임. 별도 프로젝트로 분리 권장:

1. **Sentry 대시보드에서 신규 프로젝트 생성**
   - Sentry → Projects → Create Project
   - Platform: **Python (FastAPI)** 선택
   - Project name: `eundunhealth-backend`
   - 생성 직후 표시되는 DSN을 복사 (`https://<key>@<org>.ingest.sentry.io/<project>`)

2. **Container App secret으로 등록**
   ```bash
   DSN="<위에서 복사한 DSN>"
   az containerapp secret set --name eundunhealth-api --resource-group apps \
     --secrets "sentry-dsn-backend=${DSN}"
   ```

3. **env var를 secretref로 전환**
   ```bash
   az containerapp update --name eundunhealth-api --resource-group apps \
     --set-env-vars "SENTRY_DSN=secretref:sentry-dsn-backend"
   ```

4. **헬스체크 + Sentry 대시보드에서 release health 확인**
   ```bash
   curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health
   ```

   첫 트랜잭션이 5~10초 안에 Sentry 대시보드에 표시되면 성공.

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

### 2.2 수동 점검 (분기별)

```bash
# 누적된 태그 확인
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api --orderby time_desc -o tsv

# 특정 태그 삭제 (예시) — 같은 manifest digest를 공유하는 다른 태그도 함께 사라지니 신중히
az acr repository delete --name eundunhealthacr --image eundunhealth-api:<old-tag> --yes

# manifest 단위 삭제는 더 안전 (특정 digest만 정리)
az acr manifest list-metadata --name eundunhealthacr --repository eundunhealth-api
az acr manifest delete --name eundunhealthacr --image eundunhealth-api@sha256:<digest> --yes
```

> Premium SKU로 업그레이드한다면 다음 명령으로 자동화 가능:
> ```bash
> az acr config retention update --registry eundunhealthacr --type UntaggedManifests --days 30 --status enabled
> ```

## 3. Azure 비용 알림 (월 예산 70,000원)

```bash
# 구독 ID
SUB_ID=$(az account show --query id -o tsv)

# 월 70,000원 예산 + 80% / 100% 알림
az consumption budget create \
  --amount 70000 \
  --budget-name eundunhealth-budget \
  --category Cost \
  --time-grain Monthly \
  --start-date "$(date +%Y-%m-01)" \
  --end-date "$(date -d '+12 months' +%Y-%m-01)" \
  --notifications "{
    'Actual_GreaterThan_80_Percent': {
      'enabled': true,
      'operator': 'GreaterThan',
      'threshold': 80,
      'contactEmails': ['qkr133456@gmail.com'],
      'notificationLanguage': 'ko-kr'
    },
    'Actual_GreaterThan_100_Percent': {
      'enabled': true,
      'operator': 'GreaterThan',
      'threshold': 100,
      'contactEmails': ['qkr133456@gmail.com'],
      'notificationLanguage': 'ko-kr'
    }
  }"
```

> Azure Portal → Cost Management + Billing → Budgets에서 GUI로도 동일 작업 가능.

## 4. 예상 비용 (참고)

| 서비스 | 월 예상 |
|--------|---------|
| Container Apps (Min replicas 0, Scale to Zero) | ~0원 (무료 할당량) |
| Container Registry Basic | ~7,000원 |
| PostgreSQL Flexible B1ms + 32GB | ~30,000원 |
| Azure Monitor Alerts (metric 4개) | ~550-700원 |
| **합계** | **~37,700원** |

70,000원 budget 알림은 약 1.9배 buffer. Monitor Alert 비용 = metric alert 4개 × ~$0.10/월. Activity Log alert 4개는 무료.

## 5. 운영 점검 체크리스트 (월 1회)

- [ ] Sentry 대시보드: 에러율 < 1%, performance score 양호한지 확인
- [ ] `az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api`로 이미지 수 확인 (30개 미만)
- [ ] Container App revision 수 정리: `az containerapp revision list --name eundunhealth-api -o table` → 활성 외 inactive revision 정리
- [ ] PostgreSQL slow query 확인: Azure Portal → Insights
- [ ] 월간 비용 actual vs budget 비교
- [ ] Azure Monitor alert 확인: `az monitor metrics alert list -g apps -o table` + `az monitor activity-log alert list -g apps -o table` (8개 enabled)
- [ ] (분기별) GitHub Actions `AZURE_CREDENTIALS` service principal 만료 점검 — `az ad sp credential list --id <clientId> --query "[].endDate"`. 만료 6개월 전이면 갱신 (`az ad sp credential reset` + `gh secret set`). 참고: `docs/ops/incident-log.md` INC-17.

## 6. Destructive 명령 안전 패턴

운영 중 한 번의 잘못된 명령이 운영 이미지·secret·DB를 망가뜨릴 수 있다. 모든 사례별 안전 패턴을 모아둔다. **참조 인시던트는 `docs/ops/incident-log.md`.**

### 6.1 ACR — manifest 삭제 vs 태그 제거 (참고: INC-01)

| 의도 | 위험한 명령 | 안전한 대안 |
|------|------------|-------------|
| 특정 태그만 떼고 manifest는 보존 | `az acr repository delete --image <repo>:<tag> --yes` | `az acr repository untag --name <reg> --image <repo>:<tag>` |
| 특정 manifest digest 삭제 (다른 태그가 그걸 가리키지 않을 때만) | (위와 같이 manifest 모두 삭제 위험) | 사전 점검: `az acr manifest list-metadata -r <reg> -n <repo> --query "[?digest=='sha256:...'].tags"` → 비어있을 때만 삭제 |
| 옛 timestamp 태그 정리 | 수동 일괄 삭제 | `bash redeploy.sh`가 자동 untag (최근 5개 + 운영 중 태그 보존) |

> **요지**: `az acr repository delete --image <tag>`는 **태그가 가리키는 manifest 자체를 삭제**한다. 같은 manifest를 가리키는 모든 태그가 함께 사라진다. 옛 이미지를 untag만 하려면 반드시 `az acr repository untag`.

### 6.2 Container App secret 교체 (참고: INC-15)

```bash
# 1) secret 등록 (값을 shell history에 안 남기려면 환경변수 경유)
NEW_VALUE=$(cat /dev/stdin)   # 또는 password manager에서 가져와 NEW_VALUE 변수에 담기
az containerapp secret set --name eundunhealth-api --resource-group apps \
  --secrets "<secret-name>=${NEW_VALUE}"

# 2) env var를 secretref로 연결 (잊으면 빈 문자열로 작동)
az containerapp update --name eundunhealth-api --resource-group apps \
  --set-env-vars "<ENV_NAME>=secretref:<secret-name>"

# 3) 새 revision이 적용됐는지 확인
az containerapp show --name eundunhealth-api --resource-group apps \
  --query "{revision: properties.latestRevisionName, env: properties.template.containers[0].env[?name=='<ENV_NAME>']}"

# 4) /health 헬스체크
curl -sf https://<FQDN>/health
```

### 6.3 프로덕션 DB 마이그레이션 / 데이터 정리 (참고: INC-06, INC-14)

```bash
# 1) firewall 임시 허용
MY_IP=$(curl -sf https://api.ipify.org)
az postgres flexible-server firewall-rule create \
  --resource-group apps --name healthapp \
  --rule-name temp-$(date +%s) --start-ip-address "$MY_IP" --end-ip-address "$MY_IP"

# 2) 작업 (alembic / SQL)
cd backend
export DATABASE_URL="postgresql+asyncpg://gunny:****@healthapp.postgres.database.azure.com:5432/postgres?ssl=require"
.venv/Scripts/alembic upgrade head

# 3) 반드시 회수 (성공/실패 무관)
az postgres flexible-server firewall-rule delete \
  --resource-group apps --name healthapp \
  --rule-name temp-XXX --yes
```

> **요지**: `az containerapp exec --command "..."`로 비대화형 명령 결과를 받기는 불안정(INC-06). 로컬 firewall 임시 허용 패턴이 정석.

### 6.4 Supabase 프로젝트 교체 시 (참고: INC-14)

v1.0 정식 출시 후에는 **Supabase 프로젝트 교체를 절대 자유롭게 하지 말 것.** user_id namespace가 갈아엎혀 옛 사용자가 "user not found"가 된다.

- 출시 전(현 단계): 5개 사용자 테이블 `TRUNCATE`로 정리 가능 (이미 수행).
- 출시 후 불가피한 경우: 옛 user_id → 새 user_id 매핑 테이블 + 백필 스크립트 + 사용자 공지가 필수.

### 6.5 Sentry 프로젝트 교체 시 (참고: INC-09, INC-10)

- DSN을 `local.properties`에서 읽는 키 이름이 바뀌면 `app/build.gradle.kts`의 fallback 체인으로 흡수 (이미 적용: `eundunhealth-app_SENTRY_DSN` → `SENTRY_DSN`).
- Sentry project slug (sentry-gradle plugin의 `projectName`)가 바뀌면 `local.properties:SENTRY_PROJECT_ANDROID=<new-slug>`로 override.
- 새 APK/AAB를 빌드 → 단말 재설치해야 새 DSN 적용. 옛 빌드는 죽은 DSN으로 이벤트 발송 → 무시됨.

### 6.6 backend.yml `secretref` 신규/변경 시 Container App secret 동기화 (참고: INC-18)

`.github/workflows/backend.yml`의 `Deploy to Container App` step은 `--set-env-vars`에서 `<ENV>=secretref:<secret-name>` 형태로 Container App에 미리 등록된 secret을 참조한다. **secretref만 추가하고 secret을 등록하지 않으면 deploy step이 `ContainerAppSecretRefNotFound`로 실패한다.**

backend.yml에 새 `secretref` 라인을 추가하는 PR은 반드시 한 단위로 다음 세 단계를 함께 처리한다:

1. **backend.yml**에 `--set-env-vars`에 새 `<ENV>=secretref:<secret-name>` 추가
2. **운영자가 Container App에 secret 등록** (PR 머지 *전*에):
   ```bash
   az containerapp secret set --name eundunhealth-api --resource-group apps \
     --secrets "<secret-name>=<value>"
   ```
3. **`docs/ops/operations-snapshot.md` §2 Secrets 목록 갱신**

PR 머지 후 첫 push에서 deploy job의 **"Verify required Container App secrets exist"** step(INC-18 재발 방지로 추가됨)이 누락된 secret을 fast-fail로 잡아낸다. 단, 이 step은 PR 단계에는 실행되지 않으니(deploy job 자체가 main push에서만 실행) PR 단계의 양쪽 동시화 책임은 reviewer가 진다.

> **새 필수 secret을 추가했다면** `backend.yml`의 "Verify required Container App secrets exist" step의 `REQUIRED` 문자열에도 그 secret 이름을 추가해야 한다. 누락 시 사전 점검을 우회.

### 6.7 GitHub repo secret 만료 (참고: INC-17)

`AZURE_CREDENTIALS`(service principal JSON)는 기본 2년 만료. 만료 6개월 전이 되면 다음 명령으로 점검:

```bash
# clientId 확인 (필요 시 az ad sp list로 찾기)
az ad sp credential list --id <clientId> --query "[].endDate" -o tsv

# 갱신: 스크립트 한 줄로 SP secret + GitHub secret 동시 재발급
pwsh -File scripts\register-azure-credentials.ps1 -Verify
```

스크립트가 SP를 patch 모드로 동작하므로 GitHub Actions 측은 secret만 새 JSON으로 덮어쓰면 바로 동작. 이전 SP secret은 자동 invalidate.

### 6.8 destructive 명령 실행 전 5-second sanity check

명령 입력 직전에:

1. **대상이 운영 리소스인가?** (RG `apps`, registry `eundunhealthacr` 등 — 맞다면 한 번 더 의심)
2. **`--yes` 또는 `--no-confirm` 플래그가 있는가?** 있다면 무엇이 삭제되는지 미리 dry-run.
3. **연쇄 영향이 있는가?** (manifest 공유, secretref 연결, firewall rule 의존성)
4. **롤백 경로가 있는가?** (이미지 캐시·git 백업·DB PITR)
5. **에러 시 대비책이 있는가?** Sentry/Health Check로 즉시 인지 가능?

## 7. Azure Monitor Alerts

> 설계 문서: `docs/plans/2026-06-03-azure-monitor-alerts-design.md`
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
| `alert-deletion-eundunhealth-prod` | Administrative | RG `apps` 내 리소스 삭제 이벤트 | Sev1 |

**P2 — Metric (~550-700원/월)**

| Alert | Metric | 조건 | Sev | Eval | Window |
|---|---|---|---|---|---|
| `alert-psql-cpu-eundunhealth-prod` | `cpu_percent` | avg > 80% | Sev2 | 1m | 5m |
| `alert-psql-storage-eundunhealth-prod` | `storage_percent` | avg > 80% | Sev1 | 5m | 15m |
| `alert-psql-connections-eundunhealth-prod` | `active_connections` | avg > 20 | Sev2 | 1m | 5m |
| `alert-ca-5xx-eundunhealth-prod` | `Requests` (5xx) | total > 3 | Sev1 | 1m | 5m |

**P2 — Activity Log (무료)**

| Alert | 조건 | Severity |
|---|---|---|
| `alert-psql-firewall-eundunhealth-prod` | PostgreSQL firewall rule 변경 (write) | Sev3 |

### 7.3 관리

```bash
# 전체 재생성 (idempotent)
bash scripts/setup-azure-alerts.sh

# dry-run (명령 출력만)
bash scripts/setup-azure-alerts.sh --dry-run

# 전체 삭제 (롤백)
bash scripts/setup-azure-alerts.sh --delete

# 상태 확인
az monitor metrics alert list -g apps --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled}" -o table
az monitor activity-log alert list -g apps --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled}" -o table
```

### 7.4 비용

Metric alert 4개 × ~$0.10/월 = ~$0.40/월 (~550-700원). Activity Log alert 4개는 무료. 현 37,000원 대비 1.9% 증가.
