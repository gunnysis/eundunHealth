# API 엔드포인트 추가 작업 템플릿

> Phase 5 (Ktor → FastAPI 마이그레이션 + openapi-generator 전환) 이후 패턴.
> 이전 Ktor 시절 패턴 (`EundunApi.kt` 수동 수정, `backend/src/main/kotlin/...`) 은 deprecated.

## 0. 사전 확인
- backend `cd backend && docker compose up -d` 실행 중인지 확인.
- 신규 endpoint 가 v0.x 의 어느 SPEC 항목인지 확인 (`docs/SPEC.md`).

## 1. Backend (FastAPI) — primary

### 1.1 Pydantic schema (`backend/app/schemas/<domain>.py`)
- `CamelSchema` 상속 (alias_generator=to_camel). PEP 257 `D101` 은 per-file-ignore (`pyproject.toml`) 라 class docstring 불요.
- Field 에 `description="..."` 명시 — Pydantic 이 OpenAPI `description` 으로 자동 노출.

### 1.2 Repository (`backend/app/repositories/<domain>_repo.py`)
- public class/method 에 docstring 필수 (PEP 257, `docs/conventions/naming.md` §2).
- async + SQLAlchemy 2.0 `Mapped[T] = mapped_column(...)`.

### 1.3 Service (`backend/app/services/<domain>_service.py`)
- public class + public method 에 docstring 필수.

### 1.4 Router (`backend/app/routers/<domain>.py`)
- `@router.<verb>("/path", response_model=..., operation_id="<camelCase>")` — operation_id 가 Android client 함수명. 누락 시 generator 가 자동 생성 (덜 일관적).
- 함수 자체에 docstring 1-2줄 (FastAPI 가 endpoint `description` 으로 자동 노출).
- Query param 은 `Query(..., alias="camelCase")`.

### 1.5 main.py 등록
- `app.include_router(<domain>_router)` 추가.

### 1.6 OpenAPI sync (필수)
```bash
bash scripts/sync-openapi.sh
git diff backend/openapi.json    # router 변경분 확인
```
같은 PR 에 `backend/openapi.json` 커밋 — `backend.yml` drift detection step 이 미커밋 시 fast-fail.

### 1.7 Alembic (스키마 변경 시만)
- `bash scripts/alembic-autogen.sh "<message>"` (룰 3, INC-07 방지).
- 같은 PR 에 entrypoint 검증 + `docs/ops/operations-snapshot.md` head 갱신 (룰 7).

### 1.8 검증
```bash
cd backend
.venv/Scripts/ruff.exe check app/         # PEP 8 + 257 + import order
.venv/Scripts/mypy app/                    # PEP 484/526 type hints
.venv/Scripts/pytest tests/ -v            # 회귀 0
```

## 2. Android — auto-generated, 수동 수정 X

`./gradlew :app:assembleDebug` 시 `:app:openApiGenerate` task 가 `:preBuild` 의존성으로 자동 실행 → `app/build/generated/openapi/src/main/kotlin/com/gunnys/eundunhealth/api/generated/` 에 Kotlin client 생성.

### 2.1 Repository (`data/repository/<Domain>RepositoryImpl.kt`)
- generated `api.generated.<Domain>Api` 주입.
- `data/remote/util/ResponseExt.kt` 의 `bodyOrThrow()` 호출.

### 2.2 DI 바인딩 (`di/NetworkModule.kt`)
- generated Api provider 추가 (기존 5개 패턴 따라 — ProfileApi, WeeklyPlanApi 등).

### 2.3 ViewModel + UI
- `runCatching { ... }.onFailure { e -> val a = e.toAppError(); a.reportToSentry(); _error.value = a }`.
- 사용자 액션 실패 표시: 룰 8 (inline + persistent + a11y `liveRegion` + Sentry breadcrumb) 준수. `ui/components/AuthErrorBanner.kt` 같은 promote 된 컴포넌트 활용.

## 3. 명명/문서화 체크 (PR 머지 전)

- [ ] Backend public class/function 에 docstring (`docs/conventions/naming.md` 의 PEP 257 절)
- [ ] Router `operation_id` 가 Android 함수명과 일치
- [ ] Query param 에 `alias="camelCase"` 명시 (Android 가 camelCase 사용)
- [ ] `backend/openapi.json` sync 커밋 포함
- [ ] Android Repository 가 generated API 만 사용 (`EundunApi.kt` 수동 추가 금지 — 이미 deprecated/제거됨)
- [ ] Kotlin 명명: PascalCase 클래스 / camelCase 함수 / UPPER_SNAKE const — detekt+ktlint 가 차단

## 4. Azure 신규 리소스 추가 (해당 시)

- [ ] CAF abbreviation 사용 (예: `ca-`, `cae-`, `cr`, `psql-`, `rg-`) — `docs/conventions/naming.md` §3
- [ ] workload 명은 `eundunhealth` (기존 명명과 일관성)
- [ ] env suffix 명시 (예: `-prod`, `-dev`)
- [ ] ACR/Storage 처럼 alphanumeric only 리소스는 하이픈 제거 + 압축형
- [ ] design doc (`docs/plans/2026-06-02-naming-convention-audit-design.md`) §3.2 표에 신규 리소스 1행 추가

## 주의사항
- 모든 endpoint 는 JWT 인증 필수 (`/health` 제외). Supabase ES256 (JWKS, PyJWKClient 24h TTL).
- Token: NetworkModule 의 `AtomicReference`, `TokenAuthenticator` 가 401 자동 갱신.
- Android ↔ Backend 필드명 일치는 OpenAPI 가 자동 보장 — `@SerialName` 수동 명시 불요.
