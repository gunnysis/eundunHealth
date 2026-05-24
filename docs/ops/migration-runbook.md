# Migration Runbook: Ktor → FastAPI

> 작성일: 2026-05-24 (cutover 완료) / 갱신: 2026-05-25 (자동 배포 정착)
> 대상 환경: Azure Container Apps (`eundunhealth-api`, RG `apps`), Azure PostgreSQL (`healthapp`)
> 기반: 옛 `docs/plans/expected/2026-05-24-implementation-spec.md` §O (현재는 삭제됨)
> 상태: **cutover 완료**. 본 문서는 historical record 겸 향후 동급 마이그레이션 시 reference.

> **현행 배포 절차는 `operations-snapshot.md §2`와 `CLAUDE.md "Deployment"` 섹션 참조.** 본 문서의 4.1·4.2(수동 cutover)는 2026-05-24 단일 실행분으로 종결. 이후의 모든 backend 변경은 GitHub Actions 자동 배포(`backend.yml`)로 진행.

---

## 0. 마이그레이션 원칙

1. **DB 스키마 무변경**. 3개 테이블(`user_profiles`, `weekly_plans`, `badges`)을 그대로 사용한다. SQLAlchemy 모델은 기존 컬럼·제약을 1:1 매핑한다.
2. **롤백은 이미지 태그 교체로 즉시 가능**. Ktor 최종 이미지에 `ktor-final` 태그를 부여해 보존한다.
3. **전환 직전 단일 작업으로 환경변수 교체**. 부분 교체 상태에서는 어느 백엔드도 정상 동작하지 않는다.
4. **Health Check 동일**(`GET /health`) → Container App revision swap만으로 무중단 전환 가능.

---

## 1. 환경변수 매핑

현재 Container App에 설정된 Ktor 환경변수와 FastAPI 환경변수의 대응:

| Ktor (현재) | FastAPI (신규) | 변환 방법 |
|-------------|----------------|----------|
| `AZURE_DB_URL` (`jdbc:postgresql://...`) | `DATABASE_URL` (`postgresql+asyncpg://...`) | JDBC URL → asyncpg URL 변환 (아래 참조) |
| `AZURE_DB_USER` | `DATABASE_URL`에 내장 | URL `user:pass@host` 형태로 통합 |
| `AZURE_DB_PASSWORD` | `DATABASE_URL`에 내장 | URL `user:pass@host` 형태로 통합 |
| `DB_POOL_SIZE` (선택) | `pool_size` (main.py 코드에 하드코딩, 기본 3) | 환경변수로 빼지 않음 |
| `SUPABASE_JWT_SECRET` | **삭제** | FastAPI는 JWKS 공개키 검증 (ES256) 사용 |
| `SUPABASE_URL` | `SUPABASE_URL` | 동일 |
| (없음) | `SUPABASE_SERVICE_ROLE_KEY` | **신규 추가** — 회원 탈퇴 시 Supabase Admin API 호출용 |
| `ALLOWED_ORIGINS` | `CORS_ORIGINS` | 키 이름 변경 (JSON list 형식: `["https://...","https://..."]`) |
| `ENV` (`production`/`development`) | `ENVIRONMENT` | 키 이름 변경, 동일 값 |
| `SENTRY_BACKEND_DSN` | `SENTRY_DSN` | 키 이름 변경 |

### 1.1 JDBC → asyncpg URL 변환

```
# Ktor (JDBC + 별도 user/password)
AZURE_DB_URL=jdbc:postgresql://healthapp.postgres.database.azure.com:5432/postgres?ssl=true&sslmode=require
AZURE_DB_USER=gunny
AZURE_DB_PASSWORD=<password>

# FastAPI (asyncpg, 단일 URL)
DATABASE_URL=postgresql+asyncpg://gunny:<password>@healthapp.postgres.database.azure.com:5432/postgres?ssl=require
```

**변환 규칙**:
- 프리픽스: `jdbc:postgresql://` → `postgresql+asyncpg://`
- 인증정보: `user:password@` 형식으로 호스트 앞에 삽입
- SSL: `?ssl=true&sslmode=require` → `?ssl=require` (asyncpg는 `ssl=require` 단일 파라미터)
- 비밀번호에 `@`, `/`, `:` 같은 특수문자가 있으면 URL-encode 필요 (`urllib.parse.quote_plus`)

### 1.2 SUPABASE_SERVICE_ROLE_KEY 획득

Supabase 대시보드 → Project Settings → API → `service_role` (secret) 키를 복사.
**절대 클라이언트(Android)에 노출하지 말 것**. Backend 환경변수로만 관리.

---

## 2. 사전 준비 (Pre-Migration)

### 2.1 Ktor 최종 이미지 보존 (롤백용)

```bash
# 현재 latest 태그를 ktor-final로 복제
az acr login --name eundunhealthacr
docker pull eundunhealthacr.azurecr.io/eundunhealth-api:latest
docker tag eundunhealthacr.azurecr.io/eundunhealth-api:latest \
           eundunhealthacr.azurecr.io/eundunhealth-api:ktor-final
docker push eundunhealthacr.azurecr.io/eundunhealth-api:ktor-final
```

확인:
```bash
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api -o tsv | grep ktor-final
```

### 2.2 DB 백업 (안전망)

```bash
# Azure PostgreSQL Flexible Server에서 PITR 활성화되어 있는지 확인
az postgres flexible-server backup list \
  --resource-group apps \
  --name healthapp

# 필요 시 수동 백업 — 로컬 pg_dump (네트워크 허용 필요)
pg_dump "postgres://gunny:<password>@healthapp.postgres.database.azure.com:5432/postgres?sslmode=require" \
  > backup-pre-fastapi-$(date +%Y%m%d-%H%M%S).sql
```

### 2.3 Container App 현재 환경변수 스냅샷

```bash
az containerapp show --name eundunhealth-api --resource-group apps \
  --query "properties.template.containers[0].env" -o json \
  > containerapp-env-ktor-backup.json
```

---

## 3. Alembic 프로덕션 초기화

**핵심**: 기존 테이블이 이미 존재하므로 `alembic upgrade head`로 테이블을 생성하지 않는다. **`alembic stamp head`**로 "현재 DB 상태가 최신 마이그레이션과 동일하다"는 메타 정보만 기록한다.

### 3.1 절차

```bash
# 로컬에서 프로덕션 DB에 stamp 실행 (1회만)
cd backend
export DATABASE_URL="postgresql+asyncpg://gunny:<password>@healthapp.postgres.database.azure.com:5432/postgres?ssl=require"
.venv/Scripts/alembic stamp head
```

### 3.2 검증

```bash
# alembic_version 테이블이 생성되고 현재 버전이 기록되어야 함
psql "postgres://gunny:<password>@healthapp.postgres.database.azure.com:5432/postgres?sslmode=require" \
  -c "SELECT * FROM alembic_version;"
```

기대 출력:
```
 version_num
--------------
 <hash>
```

### 3.3 주의

- **절대 `alembic upgrade head` 먼저 실행하지 말 것**. `--autogenerate`로 만든 마이그레이션은 기존 테이블을 `CREATE TABLE`하려 하므로 충돌한다.
- 향후 스키마 변경은 정상적으로 `alembic revision --autogenerate -m "..."` → `alembic upgrade head` 흐름을 따른다.

---

## 4. 전환 실행 (Cutover)

### 4.1 새 FastAPI 이미지 빌드 & 푸시

배포 스크립트(`C:/programming/docker/eundunhealth-api/redeploy.sh`)를 Task 21 설계대로 Python 기반으로 교체한 뒤:

```bash
bash C:/programming/docker/eundunhealth-api/redeploy.sh
```

이미지가 ACR에 푸시되었는지 확인:
```bash
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api -o tsv | head -5
```

### 4.2 환경변수 일괄 교체

**한 번의 명령으로 전환** — 부분 교체 상태를 만들지 않는다.

```bash
# 1) 새 시크릿 등록
az containerapp secret set --name eundunhealth-api --resource-group apps \
  --secrets \
    "database-url=postgresql+asyncpg://gunny:<password>@healthapp.postgres.database.azure.com:5432/postgres?ssl=require" \
    "supabase-service-role-key=<service_role_key>" \
    "sentry-dsn-backend=<new_or_existing_dsn>"

# 2) 환경변수 교체 + 새 이미지 배포 (Ktor 키 제거, FastAPI 키 추가)
az containerapp update --name eundunhealth-api --resource-group apps \
  --image "eundunhealthacr.azurecr.io/eundunhealth-api:<NEW_TAG>" \
  --set-env-vars \
    "DATABASE_URL=secretref:database-url" \
    "SUPABASE_URL=secretref:supabase-url" \
    "SUPABASE_SERVICE_ROLE_KEY=secretref:supabase-service-role-key" \
    "SENTRY_DSN=secretref:sentry-dsn-backend" \
    "ENVIRONMENT=production" \
    "CORS_ORIGINS=[\"https://your-android-app-domain\"]" \
  --remove-env-vars \
    "AZURE_DB_URL" "AZURE_DB_USER" "AZURE_DB_PASSWORD" \
    "SUPABASE_JWT_SECRET" "SENTRY_BACKEND_DSN" "ALLOWED_ORIGINS" "ENV" "DB_POOL_SIZE"
```

> `supabase-url`은 이미 시크릿으로 존재한다고 가정. 없으면 같은 방식으로 `--secrets`에 추가.

### 4.3 헬스체크

```bash
FQDN=$(az containerapp show --name eundunhealth-api --resource-group apps \
  --query "properties.configuration.ingress.fqdn" -o tsv)

# 새 revision이 healthy 되기까지 30초~1분 대기
for i in 1 2 3 4 5 6; do
  if curl -sf "https://${FQDN}/health"; then
    echo "OK"; break
  fi
  echo "Attempt $i failed, retrying in 10s..."
  sleep 10
done
```

기대 응답:
```json
{"status":"ok"}
```

### 4.4 스모크 테스트 (인증 필요 엔드포인트)

Android 앱에서 로그인 → 프로필 조회 → 주간 계획 조회/생성 → 배지 조회까지 골든 경로 1회 통과 확인.

또는 curl + 유효한 JWT로:
```bash
TOKEN="<유효한 supabase access token>"
curl -sf -H "Authorization: Bearer ${TOKEN}" "https://${FQDN}/profile"
curl -sf -H "Authorization: Bearer ${TOKEN}" "https://${FQDN}/weekly-plan?week_start=2026-05-25"
curl -sf -H "Authorization: Bearer ${TOKEN}" "https://${FQDN}/badges"
```

### 4.5 모니터링 관찰 (전환 후 30분)

- Sentry: 5xx 에러 / `AppException` 핸들러로 가지 않는 예외 발생률
- Container App 로그: `az containerapp logs show --name eundunhealth-api --resource-group apps --follow`
- Azure Postgres: 활성 커넥션 수가 `pool_size=3` 범위 내인지

---

## 5. 롤백 절차

### 5.1 트리거 조건 (다음 중 하나라도 해당 시 즉시 롤백)

- `/health`가 60초 이상 연속 실패
- 5xx 에러율이 1% 초과 (Sentry/로그 기준)
- DB 커넥션 풀 exhaustion 또는 데드락 발생
- Android 앱 골든 플로우 중 어느 한 단계라도 실패

### 5.2 이미지 롤백 (즉시 복구, ~30초)

```bash
# 1) 이미지를 ktor-final로 교체 + Ktor 환경변수 복구
az containerapp update --name eundunhealth-api --resource-group apps \
  --image "eundunhealthacr.azurecr.io/eundunhealth-api:ktor-final" \
  --set-env-vars \
    "AZURE_DB_URL=jdbc:postgresql://healthapp.postgres.database.azure.com:5432/postgres?ssl=true&sslmode=require" \
    "AZURE_DB_USER=gunny" \
    "AZURE_DB_PASSWORD=secretref:azure-db-password" \
    "SUPABASE_JWT_SECRET=secretref:supabase-jwt-secret" \
    "SUPABASE_URL=secretref:supabase-url" \
    "SENTRY_BACKEND_DSN=secretref:sentry-dsn-backend" \
    "ENV=production" \
  --remove-env-vars \
    "DATABASE_URL" "SUPABASE_SERVICE_ROLE_KEY" "SENTRY_DSN" "CORS_ORIGINS" "ENVIRONMENT"

# 2) 헬스체크 재확인
curl -sf "https://${FQDN}/health"
```

### 5.3 DB 호환성

DB 스키마를 변경하지 않았으므로 별도 데이터 롤백은 불필요. `alembic_version` 테이블은 남아있어도 Ktor 백엔드 동작에 영향 없음.

### 5.4 Container App revision swap (대안)

활성 revision이 여러 개일 경우, traffic split을 0%로 돌려 즉시 격리:

```bash
# 현재 revision 확인
az containerapp revision list --name eundunhealth-api --resource-group apps -o table

# 이전(Ktor) revision에 100% 트래픽 라우팅
az containerapp ingress traffic set --name eundunhealth-api --resource-group apps \
  --revision-weight "<ktor-revision-name>=100"
```

---

## 6. 정리 (Post-Migration)

마이그레이션 cutover(2026-05-24) 직후 진행된 정리 내역:

1. ✅ **`backend-ktor/` 디렉토리** — `D:\backup\dev\project\eundunHealth\`로 이관 (코드 롤백 필요 시 그곳에서 가져옴)
2. ✅ **ACR Ktor 이미지** — `20260521232116` + `latest`(Ktor manifest) 제거. 현재 ACR에 FastAPI 태그(`20260524-191501`, `fastapi-latest`)만 잔존
3. ✅ **사용 안 되는 Ktor 시크릿 제거** — `db-password`, `jwt-secret` Container App secrets에서 삭제. `database-url`(asyncpg URL에 비번 내장)과 `supabase-service-role-key`만 남음
4. ✅ **`CLAUDE.md` Backend 섹션** — FastAPI(Python 3.12) 기준으로 재작성됨 (Task 19 커밋)
5. ✅ **ACR 자동 정리 후크** — Basic SKU는 retention 정책 미지원이라 `redeploy.sh`에 "타임스탬프 태그 최근 5개 + 운영 중 태그 보존, 나머지 untag" 후크 추가됨

### 후속 권장 사항 (분기별 점검)

- `az acr manifest list-metadata --name eundunhealthacr --repository eundunhealth-api`로 untagged manifest 누적 확인. 필요 시 `az acr manifest delete`로 정리.
- Azure PostgreSQL 백업 정책(PITR retention) 확인 및 비용 모니터링.
- Sentry 백엔드 DSN: ✅ 2026-05-25에 별도 프로젝트 `eundunhealth-backend`로 분리됨. Container App secret `sentry-dsn-backend`로 활성. (INC-09 후속)
- GitHub Actions `AZURE_CREDENTIALS` SP credential은 2년 만료. 6개월 전 알림은 `monitoring-and-cost.md §5`+`§6.7` 참조.

### 마이그레이션 사후 발견 사항 (2026-05-25, 자동 배포 첫 동작 시)

PR #15 머지로 GitHub Actions `Build, Scan & Deploy` job이 처음 실제 동작했을 때 두 가지 잠재 결함이 표면화. 둘 다 본 마이그레이션 cutover 당시 `redeploy.sh`(수동 흐름)만 사용해서 노출되지 않은 것:

- **INC-17 `AZURE_CREDENTIALS` 부재** — GitHub repo secret 미등록. `scripts/register-azure-credentials.ps1`로 SP 생성/패치 + AcrPush role + secret 등록 후 해결. SP 만료 갱신도 동일 스크립트.
- **INC-18 `supabase-url` Container App secret 누락** — `backend.yml`의 `--set-env-vars`가 `secretref:supabase-url`을 참조하나 실제 등록 안 됨. 운영자가 `az containerapp secret set`으로 추가 후 해결. 재발 방지로 `backend.yml`에 "Verify required Container App secrets exist" step + `workflow_dispatch` trigger 추가.

상세 + 복구 절차는 `incident-log.md` INC-2026-05-25-17, -18 참조.

---

## 7. 변경 이력

| 날짜 | 작성자 | 변경 |
|------|--------|------|
| 2026-05-24 | gunny | 초안 작성 (Task 20) |
| 2026-05-24 | gunny | cutover 실행 완료 및 §6 사후 정리 반영 |
| 2026-05-25 | gunny | 자동 배포(`backend.yml`) 첫 정상 동작 + INC-17·18 후속 발견 사항 §6에 추가. 본 문서는 historical reference로 전환 |
