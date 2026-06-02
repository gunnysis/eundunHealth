# 명명/문서화 컨벤션 (SSoT)

> 본 문서는 single source of truth.
> - 의사결정 배경 + audit 데이터: `docs/plans/logs/process-infra.md` 의 `2026-06-02 — naming convention audit + PEP 257 enforce + automation infra` entry (#68 ledger absorb).
> - 신규 개발자: 본 문서만 읽으면 컨벤션 파악 가능.

## 1. Kotlin (Android)

- 모든 명명 룰: detekt + ktlint 가 자동 차단 (pre-commit + CI).
  - `ClassNaming` / `FunctionNaming` / `TopLevelPropertyNaming` (detekt default-active)
  - `standard:function-naming` / `standard:property-naming` / `standard:backing-property-naming` (ktlint 1.5.0 standard ruleset)
- `@Composable` 함수 PascalCase 예외: `config/detekt/detekt.yml` 에 `ignoreAnnotated: ['Composable']` override.
- Backing property: `_field` (private MutableStateFlow) + `field` (public StateFlow) — ktlint 가 enforce.
- 권장 (미 enforce): boolean property `is`/`has`/`are` 접두 (Google Android Style). detekt `BooleanPropertyNaming` 은 type resolution 필요라 default 비활성.

## 2. Python (Backend)

- **Naming**: ruff `N` (PEP 8). N818 만 의도적 ignore (`AppException`/`NotFoundException` 등 네이밍 유지).
- **Docstring**: ruff `D` + `convention = "pep257"`. public class/function 필수.
  - Per-file-ignore: `tests/**` (test 명이 spec), `alembic/**` (auto-generated), `app/main.py` (FastAPI 앱 인스턴스), `app/schemas/**` D101 (Pydantic `Field(description=...)` 대체), `app/models/**` D101 (ORM table).
  - Global ignore: D100 (모듈 헤더 비강제), D104 (패키지 헤더 비강제), D107 (`__init__` 비강제 — 클래스 docstring 으로 대체).
- **Type hints**: mypy strict (PEP 484/526) + Pydantic plugin.
- **작성 정책**:
  - 1-2 줄 한국어 요약, behavioral "why" 중심.
  - type 정보 중복 금지 (mypy + Pydantic 이 cover).
  - 도메인 용어 (Supabase, JWT, Container App, ES256 등) 는 영문 유지.

## 3. Azure 인프라

- Microsoft Cloud Adoption Framework: `<resource-type>-<workload>-<environment>-<region>-<###>`.
- 권장 abbreviation:
  - Resource group → `rg`
  - Container Apps → `ca`
  - Container Apps environment → `cae`
  - Container Registry → `cr` (alphanumeric only — 하이픈 제거, 압축형)
  - PostgreSQL Flexible Server → `psql`
  - Log Analytics workspace → `log` (auto-generated `workspace-*` suffix 회피)
- **기존 리소스 rename 금지**: CAF 공식 "Most Azure resource names can't be changed after creation". v0.1.7 Internal Testing 활성 + Container App URL 이 Android `BACKEND_URL` baked → 다운타임 + tester 일제 갱신 비용 큼.
- **신규 리소스에만 적용**. 실측은 `/naming-audit` 슬래시 명령 또는 process-infra.md 의 audit entry 참조 (SSoT inline 시 drift 위험).

## 4. 신규 코드 추가 시 체크리스트

- [ ] Backend public class/function 에 docstring (1-2줄, behavioral "why")
- [ ] Router `operation_id` 가 Android 함수명과 일치
- [ ] Query param 에 `alias="camelCase"` 명시
- [ ] `bash scripts/sync-openapi.sh` 실행 + `backend/openapi.json` 같은 PR 에 커밋
- [ ] Android Repository 가 generated API 만 사용 (수동 `EundunApi.kt` 추가 금지 — deprecated/제거됨)
- [ ] Kotlin: PascalCase 클래스 / camelCase 함수 / UPPER_SNAKE const (detekt+ktlint 자동 차단)

## 5. 신규 Azure 리소스 추가 시 체크리스트

- [ ] CAF abbreviation 사용 (`ca-`, `cae-`, `cr`, `psql-`, `rg-`, `log-` 등)
- [ ] workload 명 = `eundunhealth` (기존 명명 일관성)
- [ ] env suffix (`-prod`, `-dev`) 명시
- [ ] ACR/Storage 처럼 alphanumeric only 리소스는 하이픈 제거 + 압축형
- [ ] Azure portal 자동 생성 이름 (예: `workspace-*`) 그대로 두지 말고 deploy 시 명시
- [ ] 머지 후 `/naming-audit` 1회 실행 → `docs/plans/logs/process-infra.md` 의 최신 audit entry 또는 신규 entry 에 1행 추가

## 6. 참고

- **의사결정 기록 (배경 + audit 데이터)**: `docs/plans/logs/process-infra.md` 의 `2026-06-02 — naming convention audit + PEP 257 enforce + automation infra` entry
- **Drift 점검 슬래시 명령**: `/naming-audit` (분기당 1~2회 권장)
- **공식 문서 URL**:
  - JetBrains Kotlin Conventions — https://kotlinlang.org/docs/coding-conventions.html
  - Google Android Kotlin Style — https://developer.android.com/kotlin/style-guide
  - PEP 8 — https://peps.python.org/pep-0008/
  - PEP 257 — https://peps.python.org/pep-0257/
  - PEP 484 — https://peps.python.org/pep-0484/
  - PEP 526 — https://peps.python.org/pep-0526/
  - Azure CAF Resource Naming — https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-naming
  - Azure CAF Resource Abbreviations — https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-abbreviations
