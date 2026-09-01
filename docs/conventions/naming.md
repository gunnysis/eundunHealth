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
  - 도메인 용어 (Entra, JWT, Container App, RS256 등) 는 영문 유지.
- **Auto-generated 디렉토리** (alembic, openapi-generator 출력 등): per-file-ignore 작성 시 D 외 lint 룰 (UP/I/N) 도 보일러플레이트로 위반 가능 — 모두 검토 (PR #68 lesson L4 — alembic UP007/UP035/I001 16건 사례).

## 3. Azure 인프라

Microsoft Cloud Adoption Framework: `<resource-type>-<workload>-<environment>-<region>-<###>`.

### 3.1 CAF 가 먼저 말하는 것 — 이름은 바뀌지 않는다

> "Most Azure resource names **can't be changed after creation**. Include only information that
> remains constant in the name. **Use tags to capture other details.**"
> — [CAF Define your naming convention](https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-naming)

두 문장이 이어져 있다는 점이 중요하다. "바꿀 수 없으니 신중히" 가 아니라 **"바뀔 수 있는 정보는
애초에 이름에 넣지 말고 태그로 빼라"** 가 규칙이다. 비용센터·소유자·릴리스 같은 변동 정보는 태그다.

### 3.2 이름 유일성 범위 (rename 가능 여부를 좌우한다)

| 범위 | 의미 | 본 프로젝트 해당 |
| --- | --- | --- |
| **Global** | 전 Azure 유일. public DNS 이름이 되는 PaaS | ACR · PostgreSQL · Container App FQDN |
| **Resource group** | RG 안에서만 유일 | Key Vault·UAI·Log Analytics 등 대부분 |
| Resource | 부모 리소스 안에서 유일 | (해당 없음) |

Global 범위 리소스는 **이름 = 공개 DNS 이름**이라, 이름을 바꾸면 엔드포인트가 바뀐다.
즉 rename 은 "정리" 가 아니라 **마이그레이션**이다. 재명명 검토 시 이 표부터 볼 것.

### 3.3 약어 — 공식 표를 그대로 쓴다 (추측 금지)

정본: [CAF Resource abbreviations](https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-abbreviations).
아래는 **본 프로젝트가 실제로 쓰는 타입만** 발췌한 것이다(2026-09-01 공식 표 대조).

| 리소스 | Provider namespace | 약어 |
| --- | --- | --- |
| Resource group | `Microsoft.Resources/resourceGroups` | `rg` |
| Container apps | `Microsoft.App/containerApps` | `ca` |
| Container apps environment | `Microsoft.App/managedEnvironments` | `cae` |
| Container apps **job** | `Microsoft.App/jobs` | `caj` |
| Container registry | `Microsoft.ContainerRegistry/registries` | `cr` (영숫자만 — 하이픈 불가, 압축형 `cr<workload><env><###>`) |
| **PostgreSQL flexible server** | `Microsoft.DBforPostgreSQL/flexibleServers` | **`pgsql`** |
| Key vault | `Microsoft.KeyVault/vaults` | `kv` |
| Log Analytics workspace | `Microsoft.OperationalInsights/workspaces` | `log` (포털 자동생성 `workspace-*` 회피) |
| Managed identity | `Microsoft.ManagedIdentity/userAssignedIdentities` | `id` |
| Azure Monitor action group | `Microsoft.Insights/actionGroups` | `ag` |
| ACR **Task** | `Microsoft.ContainerRegistry/registries/tasks` | (CAF 표에 **없음**) — 하우스: `<동작>-<대상repo>` |

> **정정 이력 (2026-09-01)**: 이 표는 이전에 PostgreSQL 을 `psql` 로 적고 있었다. 공식 표의 값은
> **`pgsql`** 이다(`psql` 은 Postgres **CLI 클라이언트** 이름이라 혼동하기 쉽다). 그 틀린 값이
> 이미 배포된 알림 이름(`alert-psql-*` 4건)에 박혔다 — **약어는 기억이 아니라 공식 표에서
> 복사할 것.** 알림 규칙은 재생성이 싸므로 정리 대상에 포함한다(§3.6).
>
> **ACR Task 도 CAF 표에 없다.** 레지스트리의 **자식 리소스**라 유일성 범위가 부모 안이고,
> 이름에 workload(`eundunhealth`)를 다시 넣으면 중복이다. 그래서 접두 약어 대신
> **동작-대상** 서술형으로 짓는다 — 실례: `purge-eundunhealth-api` ·
> `purge-eundunhealth-api-untagged`(2026-09-01 도입). 하우스 결정이며 공식이 아니다.
>
> CAF 표에 "알림 규칙(alert rule)" 자체의 약어는 **없다**. 본 프로젝트의 `alert-<type>-<workload>-<env>`
> 는 하우스 컨벤션이며, 그 사실을 여기 명시해 둔다(공식으로 오인 금지).

### 3.4 리전 약어는 하우스 결정이다

CAF 예시는 `westus`·`eastus2` 처럼 **전체 리전명**을 쓴다. 공식 단축 코드표는 없다.
본 프로젝트는 길이 절약을 위해 `koreacentral` → **`krc`** 를 쓰기로 한다(하우스 결정).
새 리전을 쓰게 되면 여기에 매핑을 1행 추가한다 — 임의 축약 금지.

또한 CAF 의 Resource group 예시 형식은 `rg-<workload>-<component>-<environment>` 로
**리전을 포함하지 않는다**. 본 프로젝트의 `rg-eundunhealth-prod-krc` 는 리전을 붙인 변형이며,
단일 리전 운영에서 리전 표기는 중복이지만 이미 생성된 이름이라 유지한다.

### 3.5 기존 리소스 rename 금지 (원칙)

CAF 공식 "Most Azure resource names can't be changed after creation".
Container App URL 이 Android `BuildConfig.BACKEND_BASE_URL` 에 baked 되어 있어, 이름 변경은
**앱 재배포 + 구버전 사용자 전원 이탈**을 뜻한다. 프로덕션 LIVE 이후로는 더더욱 불가.

- **신규 리소스에만 적용**한다.
- 예외 검토가 필요하면 반드시 design+plan 페어를 먼저 쓴다 →
  `docs/plans/2026-09-01-azure-resource-naming-and-legacy-design.md`(→ `docs/plans/logs/process-infra.md` 2026-09-02 entry 로 흡수)
- 실측은 `/naming-audit` 슬래시 명령 또는 `docs/plans/logs/process-infra.md` 의 audit entry 참조
  (SSoT inline 시 drift 위험).

### 3.6 현행 준수 현황과 정리 계획

전수 대조·재명명 가능성 판정·레거시 정리 계획은 설계 문서 한 곳에 둔다(여기 inline 시 drift):
`docs/plans/2026-09-01-azure-resource-naming-and-legacy-design.md`(→ `docs/plans/logs/process-infra.md` 2026-09-02 entry 로 흡수).

## 4. 신규 코드 추가 시 체크리스트

- [ ] Backend public class/function 에 docstring (1-2줄, behavioral "why")
- [ ] Router `operation_id` 가 Android 함수명과 일치
- [ ] Query param 에 `alias="camelCase"` 명시
- [ ] `bash scripts/sync-openapi.sh` 실행 + `backend/openapi.json` 같은 PR 에 커밋
- [ ] Android Repository 가 generated API 만 사용 (수동 `EundunApi.kt` 추가 금지 — deprecated/제거됨)
- [ ] Kotlin: PascalCase 클래스 / camelCase 함수 / UPPER_SNAKE const (detekt+ktlint 자동 차단)

## 5. 신규 Azure 리소스 추가 시 체크리스트

- [ ] CAF abbreviation 을 **공식 표에서 복사** (§3.3 발췌표 또는 원문). 기억으로 쓰지 말 것 — `psql`/`pgsql` 오류 전례
- [ ] workload 명 = `eundunhealth` (기존 명명 일관성)
- [ ] env suffix (`-prod`, `-dev`) 명시 + 리전은 §3.4 매핑 사용 (`koreacentral` → `krc`)
- [ ] 변동 가능한 정보(소유자·비용센터·릴리스)는 이름이 아니라 **태그**로 (§3.1)
- [ ] Global 범위 리소스인지 확인 (§3.2) — 이름이 곧 공개 DNS 라 나중에 못 바꾼다
- [ ] ACR/Storage 처럼 alphanumeric only 리소스는 하이픈 제거 + 압축형
- [ ] Azure portal 자동 생성 이름 (예: `workspace-*`) 그대로 두지 말고 deploy 시 명시
- [ ] 머지 후 `/naming-audit` 1회 실행 → `docs/plans/logs/process-infra.md` 의 최신 audit entry 또는 신규 entry 에 1행 추가
- [ ] Container Apps environment 생성 시 `--logs-workspace-id <id>` 명시 (auto-gen `workspace-*` suffix 회피 — PR #68 lesson L7, `workspace-appsDOlM` 사례)

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
