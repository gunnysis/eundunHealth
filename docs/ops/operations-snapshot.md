# 운영 상태 스냅샷

> 작성일: 2026-05-25 / 최근 갱신: 2026-06-10 v0.1.9 릴리즈 + 버전 명시 방식 종합(version.properties SSoT · 백엔드 API 1.0.0 · bump-version.sh · PR #102)
> 작성 기준: v0.1.9 (versionCode 23) — Health Connect 체중·체지방 가져오기 + 홈 "오늘의 활동" 요약(걸음·칼로리·심박) + HC 동기화 경로 정리/갤럭시 워치 온보딩 (이전: v0.1.8 UDF-Enhanced 12 VM + OkHttp 5 / Coil 3)
> 갱신 정책: 인프라 / 시크릿 / 외부 통합 변경 시 본 문서 동시 갱신. 운영 결정의 단일 출처.

---

## 1. 클라이언트

| 항목 | 값 |
|------|---|
| Application ID | `com.gunnys.eundunhealth` |
| versionName / versionCode | **`0.1.9` / `23`** — SSoT 루트 `version.properties` (bump: `scripts/bump-version.sh`, 이력: `docs/CHANGELOG.md`) |
| Min SDK / Target SDK | 26 / 37 |
| Kotlin / AGP / Gradle | 2.2.10 / 9.2.1 / 9.5.1 |
| Compose BOM | 2026.05.01 |
| 차트 | Vico 3.2.2 (compose-m3) — v0.1.5 에서 2.1.0 → 3.1.0 마이그레이션 → 3.2.2 dependabot |
| Health Connect | 1.1.0 stable — v0.1.5 에서 1.1.0-rc01 → 1.1.0 stable 승격 |
| 정적 분석 | Detekt 1.23.8 (baseline.xml — generated 포함) + Spotless 8.6.0 + ktlint 1.5.0 |
| API client | openapi-generator 7.10.0 (jvm-retrofit2 + gson + coroutines) — preBuild 자동 |
| Sentry SDK | Android 8.43.1 / Gradle plugin 6.10.0 |
| Keystore | `.key/eundunhealth_upload_key` (alias `eundunhealth_sign_key`) |

산출물 경로 (v0.1.9 빌드 시점 기준):
- AAB: `app/build/outputs/bundle/release/app-release.aab` (7.79 MB)
- APK: `app/build/outputs/apk/release/app-release.apk` (5.56 MB)
- ProGuard mapping: `app/build/outputs/mapping/release/mapping.txt` (Sentry 자동 업로드)

---

## 2. 백엔드 (Azure Container Apps)

| 항목 | 값 |
|------|---|
| Container App | `eundunhealth-api` |
| Resource Group | `apps` |
| Region | Korea Central |
| FQDN | `eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io` |
| 활성 revision | latest auto-deploy (100% traffic, **warm — min=1**) |
| 이미지 | `eundunhealthacr.azurecr.io/eundunhealth-api:<SHA>` (latest auto-deploy, **MI pull**) |
| Min / Max replicas | **1 / 3** (warm baseline + http-concurrency 50 scale rule) — cold start 제거 |
| Health probes | Startup/Liveness `/health` + Readiness `/health/ready`(DB SELECT 1) |
| Identity | System-assigned MI (`a4784428…`) — Key Vault resolve + ACR pull |
| API version | `1.0.0` (`backend/app/__version__` → OpenAPI `info.version`, 앱과 독립 — bump 시 `sync-openapi.sh` 재싱크) |
| Dockerfile | `python:3.12-slim` + `apt-get upgrade` 레이어(base-image OS CVE 자가치유, Trivy HIGH 차단 회피) |

### env vars

| Name | 형태 | 값/참조 |
|------|------|--------|
| `DATABASE_URL` | secretref | `database-url` (asyncpg URL) |
| `SUPABASE_URL` | secretref | `supabase-url` |
| `SUPABASE_SERVICE_ROLE_KEY` | secretref | `supabase-service-role-key` |
| `SENTRY_DSN` | secretref | `sentry-dsn-backend` |
| `ENVIRONMENT` | value | `production` |
| `CORS_ORIGINS` | value | `["*"]` |

### Secrets (4 앱 secret — **Key Vault 참조**, `identity: system`)

Container App secret 은 `kv-eundunhealth` Key Vault 참조(값은 KeyVault 에만, 직접값 아님). ACR pull secret 은 **MI 전환으로 제거**.

- `database-url` → KeyVault `database-url` (`postgresql+asyncpg://…healthapp…`)
- `supabase-url` → KeyVault `supabase-url`
- `supabase-service-role-key` → KeyVault `supabase-service-role-key`
- `sentry-dsn-backend` → KeyVault `sentry-dsn-backend`
- ~~`eundunhealthacrazurecrio-eundunhealthacr`~~ (ACR pull) — **제거**: registries 가 MI(`identity: system`) pull 로 전환.

> backend.yml deploy job 직전 "Verify required **Key Vault** secrets exist" step 이 4개 KeyVault secret 존재를 사전 점검(CI SP = Key Vault Secrets User). 누락 시 fast-fail (INC-18 재발 방지 — 룰 6 KeyVault 적응).

### Key Vault (`kv-eundunhealth`)

| 항목 | 값 |
|------|---|
| Vault | `kv-eundunhealth` (RG `apps`, Korea Central) |
| SKU / 권한 모델 | Standard / **Azure RBAC** (legacy access policy 미사용) |
| Soft-delete / Purge protection | 90일 / **활성**(생성 후 불변) |
| Network | public + RBAC/MI 가 실질 차단막 (Container Apps Consumption 동적 IP → VNet 미통합) |
| Secrets (4) | database-url, supabase-url, supabase-service-role-key, sentry-dsn-backend |
| RBAC | 운영자=Secrets Officer · Container App MI=Secrets User · CI SP=Secrets User · MI=AcrPull(ACR) |
| Audit | `kv-audit` 진단설정 → Log Analytics `workspace-appsDOlM` (AuditEvent) |

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
- **`.github/workflows/android.yml`** — `app/**` 변경 시 spotlessCheck + **collectAsState anti-pattern 검사** (룰 11) + detektDebug + testDebugUnitTest + assembleDebug + PR이면 APK artifact 업로드
- **`.github/dependabot.yml`** — pip + github-actions + gradle 주간 PR (KST 월 06:00, 보안 패치는 단일 PR로 그룹화)

로컬 자동화:
- **`scripts/preflight-release.sh`** — Spotless + Detekt + Tests + `releaseArtifacts`(AAB+APK 동시) 일괄 (INC-04 차단)
- **`scripts/bump-version.sh`** — 앱 버전 bump (versionName + versionCode +1 + semver/단조 가드 + 문서 동기화, `--dry-run`). 정책: `docs/conventions/versioning.md`
- **`scripts/alembic-autogen.sh`** — postgres:16-alpine 컨테이너에 autogen 실행 (INC-07 차단)
- **`scripts/register-azure-credentials.ps1`** — SP 생성/패치 + AcrPush role + GitHub secret 등록 (INC-17 운영자 1회/만료 갱신용)
- **`.githooks/pre-commit`** — 로컬 .kt 변경 시 spotlessApply + detektDebug + **collectAsState anti-pattern 검사** (룰 11)

---

## 9. 비용 (2026-05 기준 추정)

| 서비스 | 월 예상 | 비고 |
|--------|--------|------|
| Container Apps (Min 1 warm) | ~6,000원 | warm baseline (cold start 제거) — idle 단가, free grant 차감 |
| Key Vault Standard | ~0원 | secret 4 + 저빈도 read(revision 기동 시) — 거래 무료한도 내 |
| Container Registry Basic | ~7,000원 | 10GB 한도 |
| PostgreSQL Flexible B1ms + 32GB | ~30,000원 | |
| Azure Monitor Alerts (metric 4) | ~550-700원 | Activity Log 4개 무료 |
| Sentry | 0원 | 무료 plan (10K events/mo) |
| Supabase | 0원 | 무료 plan |
| **합계** | **~43,700원** | budget 70,000원(1.6배 buffer) |

---

## 10. 운영 점검 명령

### 월간

```bash
# /health
curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health

# revision 상태 + warm baseline 회귀 감지 (minReplicas 가 1 이어야 — cold start 방지)
az containerapp revision list --name eundunhealth-api --resource-group apps -o table
az containerapp show -n eundunhealth-api -g apps --query "properties.template.scale.minReplicas" -o tsv  # 1 이어야

# readiness (DB 연결까지 확인)
curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health/ready

# ACR 태그 (timestamp 태그가 redeploy.sh 후크로 최근 5개만 유지되는지)
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api --orderby time_desc -o tsv

# Key Vault secret 목록 (4개여야 함 — KeyVault 참조 전환 후)
az keyvault secret list --vault-name kv-eundunhealth --query "[].name" -o tsv

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

## 12. Azure Monitor Alerts

> 프로비저닝: `bash scripts/setup-azure-alerts.sh` (idempotent)
> 롤백: `bash scripts/setup-azure-alerts.sh --delete`
> 설계: `docs/plans/2026-06-03-azure-monitor-alerts-design.md`

### Action Group

`ag-eundunhealth-prod` → Email `qkr133456@gmail.com`

### Alert 인벤토리 (총 8개)

| Name | Type | Severity | Enabled |
|---|---|---|---|
| `alert-servicehealth-eundunhealth-prod` | Activity Log (ServiceHealth) | Sev3 | True |
| `alert-resourcehealth-eundunhealth-prod` | Activity Log (ResourceHealth) | Sev1 | True |
| `alert-deletion-eundunhealth-prod` | Activity Log (Administrative) | Sev1 | True |
| `alert-psql-firewall-eundunhealth-prod` | Activity Log (Administrative) | Sev3 | True |
| `alert-psql-cpu-eundunhealth-prod` | Metric (cpu_percent avg > 80%) | Sev2 | True |
| `alert-psql-storage-eundunhealth-prod` | Metric (storage_percent avg > 80%) | Sev1 | True |
| `alert-psql-connections-eundunhealth-prod` | Metric (active_connections avg > 20) | Sev2 | True |
| `alert-ca-5xx-eundunhealth-prod` | Metric (Requests 5xx total > 3) | Sev1 | True |

비용: metric 4개 × ~$0.10/월 = ~$0.40/월 (~550-700원). Activity Log 4개 무료.

---

## 13. 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-05-25 | 초안 작성. v0.1.0 출시 직전 상태 |
| 2026-05-25 (자동 배포) | INC-17·18 해결 + GitHub Actions 자동 배포 end-to-end 정상 동작. revision `0000007` 활성. secret `supabase-url` 추가(총 5개). backend.yml에 secret precheck step + workflow_dispatch trigger 추가. `scripts/register-azure-credentials.ps1` 신규. PR #15·#16·#17 머지 |
| 2026-06-03 | Azure Monitor Alerts 프로비저닝. Action Group `ag-eundunhealth-prod` + Activity Log alert 4개 (ServiceHealth/ResourceHealth/Deletion/PG Firewall) + Metric alert 4개 (PG CPU/Storage/Connections + CA 5xx). §12 신설. `scripts/setup-azure-alerts.sh` 신규 |
| 2026-06-06 | 프론트엔드 UDF-Enhanced 마이그레이션 (12 VM + 11 Screen). `@Immutable` 45건, `collectAsStateWithLifecycle` 33건, SideEffect Channel 7건. AuthVM 분리 → Login/Signup/ForgotPasswordVM 신규. OkHttp 4→5, Coil 2→3 의존성 메이저 업그레이드. CLAUDE.md 룰 11 + CI collectAsState 가드 + pre-commit collectAsState 검사 추가 |
| 2026-06-06 | GitHub Actions Node.js 20→24 런타임 업그레이드 (checkout v6, gitleaks v3, trivy v0.36.0 pin). Dependabot PR 7건 일괄 정리: merged 4건 (foojay-resolver 1.0, sentry-gradle 6.10, vico 3.2.2, mypy 2.1), 수동 적용 1건 (starlette 1.2.1, uvicorn 0.49.0, sentry-sdk 2.61.1, pytest-asyncio 1.4.0, ruff 0.15.16 — fastapi 0.136.3 MAL-2026-4750 제외), closed 2건 (kotlin 2.4.0 / openapi-generator 7.22 — CI 실패) |
| 2026-06-09 | **Cold start 제거 + Key Vault full IaC**. 로그인 느림 원인 = 백엔드 cold start 21.5s(scale-to-zero) 규명 → `min 1 / max 3` + http scale rule(PR #92). `/health/ready` readiness probe(PR #93). secret → Key Vault 참조(`kv-eundunhealth` + system MI + RBAC) · registries MI pull · HTTP probe 3종 · `--yaml` 배포 전환(PR2, `backend/containerapp.yaml` 단일 출처). staging dry-run 으로 clobber/resolve 실증 후 정리. Dependabot 5건 머지(sentry 8.43.1/spotless 8.6.0/detekt 1.23.8/androidx core-ktx 1.19.0/codecov 7) + 2건 close(kotlin CI fail, fastapi MAL). 비용 ~37,700 → ~43,700원 |
| 2026-06-10 | **v0.1.9 릴리즈**. Health Connect 체중·체지방 가져오기(#84) + 홈 "오늘의 활동" 요약(#85) + HC 동기화 경로 정리·갤럭시 워치 온보딩(#83) + 사전점검 수정. versionCode 22→23 (산출물 AAB 7.79 MB / APK 5.56 MB, Sentry 매핑 `ab61f3a3`) |
| 2026-06-10 | **앱 버전 명시 방식 종합 (PR #102)**. 앱 버전 SSoT = `version.properties`(`build.gradle.kts` 가 읽음, 이력 주석블록 제거) · 백엔드 독립 API 버전 `1.0.0`(`__version__` → FastAPI `version` → openapi 재싱크) · `ProfileScreen` 버전 라벨(BuildConfig) · `scripts/bump-version.sh`(semver/단조 가드) · `docs/conventions/versioning.md`. 배포 시 무관한 base-image openssl `CVE-2026-45447`(HIGH)로 Trivy 1차 차단 → Dockerfile `apt-get upgrade` 핫픽스(`5a78c69`) 자가치유. 라이브 검증 `info.version=1.0.0` |
