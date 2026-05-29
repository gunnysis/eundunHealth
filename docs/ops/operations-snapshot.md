# 운영 상태 스냅샷

> 작성일: 2026-05-25 / 최근 갱신: 2026-05-29 v0.1.5 (versionCode 19) 빌드 시점
> 작성 기준: v0.1.5 (versionCode 19) — vico 2.1→3.1 chart migration + healthConnect 1.1.0 stable + starlette 1.1.0
> 갱신 정책: 인프라 / 시크릿 / 외부 통합 변경 시 본 문서 동시 갱신. 운영 결정의 단일 출처.

---

## 1. 클라이언트

| 항목 | 값 |
|------|---|
| Application ID | `com.gunnys.eundunhealth` |
| versionName / versionCode | **`0.1.5` / `19`** (14=v0.1.0 출시 / 15=v0.1.1 / 16=v0.1.2 / 17=v0.1.3 / 18=v0.1.4 / 19=v0.1.5) |
| Min SDK / Target SDK | 26 / 37 |
| Kotlin / AGP / Gradle | 2.2.10 / 9.2.1 / 9.5.1 |
| Compose BOM | 2026.05.01 |
| 차트 | Vico 3.1.0 (compose-m3) — v0.1.5 에서 2.1.0 → 3.1.0 마이그레이션 |
| Health Connect | 1.1.0 stable — v0.1.5 에서 1.1.0-rc01 → 1.1.0 stable 승격 |
| 정적 분석 | Detekt 1.23.7 (baseline.xml — generated 포함) + Spotless 8.5.1 + ktlint 1.5.0 |
| API client | openapi-generator 7.10.0 (jvm-retrofit2 + gson + coroutines) — preBuild 자동 |
| Sentry SDK | Android 8.42.0 |
| Keystore | `.key/eundunhealth_upload_key` (alias `eundunhealth_sign_key`) |

산출물 경로 (v0.1.5 빌드 시점 기준):
- AAB: `app/build/outputs/bundle/release/app-release.aab` (7.96 MB)
- APK: `app/build/outputs/apk/release/app-release.apk` (5.76 MB)
- ProGuard mapping: `app/build/outputs/mapping/release/mapping.txt` (Sentry 자동 업로드)

---

## 2. 백엔드 (Azure Container Apps)

| 항목 | 값 |
|------|---|
| Container App | `eundunhealth-api` |
| Resource Group | `apps` |
| Region | Korea Central |
| FQDN | `eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io` |
| 활성 revision | `eundunhealth-api--0000007` (100% traffic, ScaledToZero) |
| 이미지 | `eundunhealthacr.azurecr.io/eundunhealth-api:38ca830` (PR #17 SHA, latest auto-deploy) |
| Min / Max replicas | 0 / 1 (KEDA scale-to-zero) |

### env vars

| Name | 형태 | 값/참조 |
|------|------|--------|
| `DATABASE_URL` | secretref | `database-url` (asyncpg URL) |
| `SUPABASE_URL` | secretref | `supabase-url` |
| `SUPABASE_SERVICE_ROLE_KEY` | secretref | `supabase-service-role-key` |
| `SENTRY_DSN` | secretref | `sentry-dsn-backend` |
| `ENVIRONMENT` | value | `production` |
| `CORS_ORIGINS` | value | `["*"]` |

### Secrets (총 5 — backend.yml deploy step의 secretref 4개 + ACR pull 1개)

- `database-url` — `postgresql+asyncpg://gunny:****@healthapp.postgres.database.azure.com:5432/postgres?ssl=require`
- `supabase-url` — `https://ttzzbfoksncqazvcsfiu.supabase.co` (INC-18 후 추가)
- `supabase-service-role-key` — Supabase Admin API 키 (한국 리전 발급)
- `sentry-dsn-backend` — Sentry `eundunhealth-backend` 프로젝트 DSN
- `eundunhealthacrazurecrio-eundunhealthacr` — ACR pull (Azure 자동 관리)

> backend.yml deploy job 직전 "Verify required Container App secrets exist" step이 위 4개(ACR pull 제외)를 사전 점검. 누락 시 fast-fail로 회귀 차단 (INC-18 재발 방지).

---

## 3. ACR (Azure Container Registry)

| 항목 | 값 |
|------|---|
| Registry | `eundunhealthacr` |
| SKU | Basic (retention 정책 미지원) |
| Tagged manifests | 2 (총 3 태그) |
| Untagged manifests | 12 (OCI artifact 메타 충돌로 Basic SKU에선 직접 삭제 불가, 운영 무관) |

태그:
- `latest` — 현재 운영 (manifest `sha256:04fe…`)
- `20260524-191501` + `fastapi-latest` — 같은 manifest (`sha256:1c5c…`)

자동 정리 후크: `redeploy.sh`가 헬스체크 통과 후 timestamp 태그(`YYYYMMDD-HHMMSS`) 최근 5개 + 운영 중 태그만 보존하고 나머지를 untag.

---

## 4. Azure PostgreSQL

| 항목 | 값 |
|------|---|
| Flexible Server | `healthapp` |
| Tier | B1ms / 32GB |
| Region | Korea Central |
| Database | `postgres` |
| User | `gunny` |
| Alembic head | **`fa3915deab2f`** (rest_day 컬럼 추가 — INC-2026-05-27-01, [PR fix/schema-drift-rest-day](https://github.com/gunnysis/eundunHealth/tree/fix/schema-drift-rest-day)) |
| Firewall | Container Apps IP allowlist + `allow-azure-services` 만 허용 |

### 테이블 row 카운트 (2026-05-25 출시 전 정리 직후)

| 테이블 | rows |
|--------|------|
| `user_profiles` | 0 |
| `weekly_plans` | 0 |
| `badges` | 0 |
| `user_profile_history` | 0 |
| `goals` | 0 |
| `alembic_version` | 1 (head 마커) |

> 운영 중 외부 접근이 필요할 때는 임시 firewall rule 추가 후 즉시 제거:
> ```bash
> MY_IP=$(curl -sf https://api.ipify.org)
> az postgres flexible-server firewall-rule create --resource-group apps --name healthapp --rule-name temp-debug --start-ip-address "$MY_IP" --end-ip-address "$MY_IP"
> # ... 작업 ...
> az postgres flexible-server firewall-rule delete --resource-group apps --name healthapp --rule-name temp-debug --yes
> ```

---

## 5. Supabase

| 항목 | 값 |
|------|---|
| Project ID | `ttzzbfoksncqazvcsfiu` |
| URL | `https://ttzzbfoksncqazvcsfiu.supabase.co` |
| Region | Korea |
| Auth users | 0 (출시 전 초기 상태) |
| 사용 기능 | Auth 전용 (anon 로그인/회원가입 + Admin API 사용자 삭제). DB는 Azure PG 사용 |
| JWKS endpoint | `${URL}/auth/v1/.well-known/jwks.json` (백엔드 24h TTL 캐시) |

> 옛 프로젝트 `hcowzkqapzlvrvmawfcd` (US 리전)은 사용 안 함. 사용자 직접 삭제 가능.

---

## 6. Sentry

| Project | 용도 | tracesSampleRate |
|---------|------|------------------|
| `gunnys/eundunhealth` | Android 클라이언트 | DEBUG=1.0 / PROD=0.2 |
| `gunnys/eundunhealth-backend` | FastAPI 백엔드 | development=1.0 / production=0.2 |

DSN 분리 보관:
- Android: `local.properties:eundunhealth-app_SENTRY_DSN` → `BuildConfig.SENTRY_DSN`
- Backend: Container App secret `sentry-dsn-backend` (사용자가 `local.properties:eundunhealth-backend_SENTRY_DSN`로도 보관)

ProGuard mapping: release 빌드 시 sentry-gradle plugin이 자동 업로드 (`projectName.set("eundunhealth")`, `SENTRY_AUTH_TOKEN` 사용).

---

## 7. 외부 통합

| 서비스 | URL | 인증 | 비고 |
|--------|-----|------|------|
| OSS ExerciseDB | `https://oss.exercisedb.dev/api/v1/` | 없음 | 부위 카탈로그 10종, 운동 ~1500개 |
| Supabase Auth | 위 §5 | anon key / service_role | JWKS ES256 |
| Sentry | `o4510919956430848.ingest.us.sentry.io` | DSN | US org, 두 project |

---

## 8. CI / 자동화

GitHub Actions:
- **`.github/workflows/backend.yml`** — `backend/**` 또는 `backend.yml` 변경 시: ruff + mypy + pytest + Codecov → docker compose runtime smoke (INC-03 차단) → pip-audit + bandit + gitleaks → (main push) Trivy + ACR push + **secret precheck (INC-18 차단)** → Container App 배포 → /health
  - `workflow_dispatch` 지원 — 수동 실행 가능 (`gh workflow run backend.yml --ref main`)
  - GitHub repo secret `AZURE_CREDENTIALS` 필요 (service principal JSON, scope `RG apps` + `AcrPush` on ACR)
- **`.github/workflows/android.yml`** — `app/**` 변경 시 spotlessCheck + detektDebug + testDebugUnitTest + assembleDebug + PR이면 APK artifact 업로드
- **`.github/dependabot.yml`** — pip + github-actions + gradle 주간 PR (KST 월 06:00, 보안 패치는 단일 PR로 그룹화)

로컬 자동화:
- **`scripts/preflight-release.sh`** — Spotless + Detekt + Tests + `releaseArtifacts`(AAB+APK 동시) 일괄 (INC-04 차단)
- **`scripts/alembic-autogen.sh`** — postgres:16-alpine 컨테이너에 autogen 실행 (INC-07 차단)
- **`scripts/register-azure-credentials.ps1`** — SP 생성/패치 + AcrPush role + GitHub secret 등록 (INC-17 운영자 1회/만료 갱신용)
- **`.githooks/pre-commit`** — 로컬 .kt 변경 시 spotlessApply + detektDebug

---

## 9. 비용 (2026-05 기준 추정)

| 서비스 | 월 예상 | 비고 |
|--------|--------|------|
| Container Apps (Min 0) | ~0원 | scale-to-zero, 무료 할당량 |
| Container Registry Basic | ~7,000원 | 10GB 한도 |
| PostgreSQL Flexible B1ms + 32GB | ~30,000원 | |
| Sentry | 0원 | 무료 plan (10K events/mo) |
| Supabase | 0원 | 무료 plan |
| **합계** | **~37,000원** | budget 70,000원(2배 buffer) 설정 권장 |

---

## 10. 운영 점검 명령

### 월간

```bash
# /health
curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health

# revision 상태
az containerapp revision list --name eundunhealth-api --resource-group apps -o table

# ACR 태그 (timestamp 태그가 redeploy.sh 후크로 최근 5개만 유지되는지)
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api --orderby time_desc -o tsv

# Container App secret 목록 (5개 + ACR pull 1개 = 6개여야 함. INC-18 재발 감지)
az containerapp secret list --name eundunhealth-api --resource-group apps --query "[].name" -o tsv

# 비용 actual vs budget
az consumption usage list --start-date $(date -d '-30 days' +%Y-%m-%d) --end-date $(date +%Y-%m-%d) -o table

# Sentry: 대시보드에서 5xx 에러율 < 1% 확인
```

### 분기별

```bash
# Service principal credential 만료 점검 (AZURE_CREDENTIALS, INC-17)
# clientId는 az ad sp list --display-name eundunhealth-github-deploy로 찾을 수 있음
az ad sp credential list --id <clientId> --query "[].endDate" -o tsv
# 6개월 이내 만료라면: pwsh -File scripts\register-azure-credentials.ps1 -Verify

# 옛 untagged manifest 누적 확인 (Basic SKU에선 직접 삭제 불가)
az acr manifest list-metadata --name eundunhealthacr --repository eundunhealth-api \
  --query "[?tags==null].digest" -o tsv | wc -l

# Azure PostgreSQL slow query / 활성 connection
# Azure Portal → healthapp → Monitoring → Insights
```

### Secret rotation 후 즉시 검증

```bash
# paths 필터(backend/**) 우회. workflow_dispatch trigger로 deploy 강제 실행.
gh workflow run backend.yml --ref main
# 또는 GitHub Actions UI → "Backend CI/CD" → Run workflow
```

---

## 11. MCP 통합 (2026-05-28)

Claude Code MCP 서버 4종 운영 활용:

| MCP 서버 | 주 사용 시나리오 | 인증 |
|---|---|---|
| `mcp__sentry__*` | INC root cause 분석, Phase 5 검증, 신규 issue 알림 | OAuth (qkr133456@gmail.com) |
| `mcp__azure__*` | Container App 상태/로그, ACR 정리, PG 쿼리, secret list 검증 | az CLI shared, `AZURE_TENANT_ID` env 주입 |
| `mcp__github__*` | PR 작성/조회, CI run polling, 코드 검색 | GitHub Copilot OAuth (gunnysis) |
| `mcp__plugin_context7_context7__*` | 공식 docs fetch (Alembic, PG, Docker, Container Apps) | 없음 (public) |

권한 분리 (`.claude/settings.local.json`):
- read-only tool 56개 → allow (prompt 없음)
- write tool 23개 → ask (실수 차단)

자동화:
- `/verify-deploy <INC-ID>` — Phase 5 운영 검증 1-command
- `scripts/hooks/secretref-guard.sh` — 룰 6 commit-time 가드 (PreToolUse hook)
- `scripts/claude-context.sh` — SessionStart 보류 검증 리마인더

자세한 설계: `docs/plans/2026-05-28-mcp-integration-setup-design.md`.

---

## 12. 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-05-25 | 초안 작성. v0.1.0 출시 직전 상태 |
| 2026-05-25 (자동 배포) | INC-17·18 해결 + GitHub Actions 자동 배포 end-to-end 정상 동작. revision `0000007` 활성. secret `supabase-url` 추가(총 5개). backend.yml에 secret precheck step + workflow_dispatch trigger 추가. `scripts/register-azure-credentials.ps1` 신규. PR #15·#16·#17 머지 |
