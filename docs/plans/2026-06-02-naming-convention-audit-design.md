---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: docs+backend-only
ledger_topic: process-infra
tags: [naming-convention, docstring, ruff, detekt, pep257, pep8, azure-caf]
---

# 명명/문서화 컨벤션 audit + 자동화 인프라 설계 (5종 공식 가이드 + PEP 257 enforce + Azure CAF + .claude/ 활용)

- **작성일**: 2026-06-02
- **상태**: 작성 중
- **연관 작업**: PR TBD
- **대상 버전**: backend 변경 + docs + 자동화 인프라 (versionCode 미증가, Android 코드 변경 0)
- **선행 작업**: 없음

## 1. 배경

요청의 3축:
1. **role 적용 검토** — 5종 공식 가이드 (Kotlin 2종 + Python 4종 PEP + Azure CAF) 대비 현재 코드/인프라 명명/문서화 준수도 audit 및 1차 enforce
2. **`.claude/` 활용** — 슬래시 명령 + CLAUDE.md 자동 로딩 + skill 인프라로 Claude Code 세션에서 컨벤션이 자동 컨텍스트로 들어오도록
3. **추후 작업 대비 효율성 증대** — pre-commit hook + PR template + prompt template + audit re-run 명령으로 신규 코드/리소스 추가 시점에 컨벤션이 자동 점검되도록

핵심 사실:
- Kotlin 명명은 detekt+ktlint 가 이미 95%+ cover → 룰 변경 0건
- Python 명명은 ruff `N` 이 cover, **PEP 257 docstring 만 gap** → 본 PR 에서 public API 한정 enforce
- Azure 명명은 5개 리소스 모두 CAF 권장 불일치이나, Microsoft 공식: *"Most Azure resource names can't be changed after creation"* + v0.1.7 Internal Testing 활성 → 인프라 변경 0건, audit 표만 보존
- 현 자동화 인프라 gap: `.githooks/pre-commit` 이 Kotlin 만 cover · PR template 이 `pytest` 만 명시 (ruff 없음) · `scripts/prompts/api-endpoint.md` 가 legacy (Ktor 시절 패턴 — openapi-generator 전환 미반영) · audit re-run 슬래시 명령 부재 · naming convention SSoT 부재

검토 대상 공식 가이드:
- **Kotlin** — JetBrains Coding Conventions (https://kotlinlang.org/docs/coding-conventions.html), Google Android Kotlin Style Guide (https://developer.android.com/kotlin/style-guide)
- **Python** — PEP 8 (https://peps.python.org/pep-0008/), PEP 257 (https://peps.python.org/pep-0257/), PEP 484 (https://peps.python.org/pep-0484/), PEP 526 (https://peps.python.org/pep-0526/)
- **Azure 인프라** — Microsoft Cloud Adoption Framework Resource Naming (https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-naming), Resource Abbreviations (https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-abbreviations)

브레인스토밍 + 후속 리팩토링에서 결정된 7 가지 (D5 는 1차 OK, D8 은 Azure 추가, D9/D10 은 자동화 인프라 리팩토링 단계에서 추가):
1. **혼합 접근** — gap report 작성 → 우선순위 결정 → 합의된 룰만 enforce
2. **PEP 257**: public API 한정 (router + Service public + Repository public)
3. **Kotlin**: gap 스캔만, 룰 추가 0건
4. **PR 분할**: 1개 PR 로 통합 (audit doc + 룰 변경 + docstring 추가 + 자동화 인프라)
5. **Azure CAF**: audit only, 인프라 변경 0건 — rename 불가능성 + Internal Testing 활성 운영 위험
6. **지속 자동화 인프라 범위** (D9): 5개 (pre-commit hook · PR template · `/naming-audit` slash command · prompt template 갱신 · `docs/conventions/naming.md` SSoT). skill 신설은 over-investment 로 out-of-scope.
7. **컨벤션 SSoT 위치** (D10): `docs/conventions/naming.md` + CLAUDE.md 에 link. design doc 은 의사결정 기록 + audit 데이터 보존 용도. SSoT 는 짧고 항시 참조 가능하게 분리.

## 2. Scope

### In-scope

**audit + enforce (코드)**
- 5종 공식 가이드 대비 현재 코드베이스 + 인프라의 명명/문서화 준수도 audit matrix 작성 (§3)
- `backend/pyproject.toml` 에 ruff `D` 룰 + `pydocstyle convention = "pep257"` + per-file-ignore 추가
- `backend/app/` public class/function/method 에 docstring 추가 (실측 59건, §5 — 80 위반 중 16 ignore (schemas/models D101 10 + main.py D 전체 6, D415 2건 포함) + 5 minor fix 제외 후)
- minor formatting 위반 (D205 1 + D400 2 manual + D209 1 + D403 1 auto, 5건) fix. D415 는 baseline 2건이 모두 main.py 안에 있어 per-file-ignore 와 함께 제외 (Task 1 실측으로 확인)

**audit only (인프라)**
- Azure 리소스 CAF 권장 명명 vs 현재 명 비교표 (audit only, §3.2)

**지속 자동화 인프라 (신규 — §5.8 ~ §5.12)**
- `.githooks/pre-commit` 보강 — backend `.py` staged 시 ruff check (D 룰 포함) 자동 실행. 현재 Kotlin 만 cover 인 gap 해소.
- `.github/pull_request_template.md` Backend 섹션에 `ruff check` 추가 + Azure 신규 리소스 추가 시 CAF abbreviation 가이드 체크박스 추가
- `.claude/commands/naming-audit.md` 신규 슬래시 명령 — `/naming-audit` 로 ruff D + detekt naming + Azure CAF 표 갱신 안내를 1-command 으로 실행 (시간 경과 drift 점검)
- `scripts/prompts/api-endpoint.md` 갱신 — Ktor legacy 패턴 제거 + FastAPI/openapi-generator/PEP 257 docstring/CAF 명명 체크 항목 반영
- `docs/conventions/naming.md` 신규 SSoT — 5종 가이드 요약 + 본 PR 의 D1~D10 결정 + 신규 리소스/코드 추가 시 체크리스트. CLAUDE.md 에서 link.
- `CLAUDE.md` 갱신 — naming convention SSoT link + `/naming-audit` 슬래시 명령 안내

**검증**
- 검증 절차 (ruff/mypy/pytest 회귀 없음 + smoke + sync-openapi)
- 자동화 인프라 작동 확인 (pre-commit hook 가짜 위반 commit 시도 → 차단됨, `/naming-audit` 실행 시 audit 표 재현, PR template 렌더링)

### Out-of-scope
- Kotlin lint 룰 추가/변경 — audit 결과 detekt+ktlint default 가 JetBrains/Android 명명 룰을 95%+ cover. 단 1 gap (BooleanPropertyNaming) 은 사용자 결정에 따라 보강 안 함, audit 표에만 기록.
- 모듈/패키지 docstring (D100/D104) 강제화 — 현 모듈 헤더 주석 스타일 유지.
- `tests/`, `alembic/`, `app/schemas/`, `app/models/`, `app/main.py` — per-file-ignore 로 제외 (이유 §5.1).
- detekt `baseline-debug.xml` 의 비-naming 잔존 위반 청소 — 별도 PR.
- line length / complexity threshold 등 non-naming lint 항목 — 별도 스코프.
- **Azure 기존 리소스 rename / 재생성** — Microsoft 공식 문서: "Most Azure resource names can't be changed after creation". rename = 신규 리소스 생성 + 데이터/이미지 마이그레이션 + DNS/secret/Container App URL 전환 + 기존 삭제. v0.1.7 Internal Testing 활성 상태에서 다운타임 + 사용자 영향 + GitHub Actions secret (`AZURE_CREDENTIALS`/`ACR_*`) 일제 갱신 비용이 audit 가치 대비 큼. 향후 신규 리소스에만 적용 (별도 chore PR 검토 가능 — §8 잔여 리스크).
- Azure tag 정책 / RBAC 명명 규칙 — Azure CAF 의 다른 영역, 별도 스코프.
- **`.claude/skills/naming-convention/` 신규 skill** — over-investment. PEP 257 위반은 ruff 가 자동 차단 (pre-commit + CI) 이라 별도 skill 불요. CAF 명명은 IaC 미도입 상태로 신규 리소스 추가 빈도 매우 낮음 → SSoT 문서 + prompt template + CLAUDE.md link 로 충분. (D10 결정 — skill 은 향후 IaC 도입 시점에 재검토)
- `scripts/prompts/new-screen.md`, `bug-fix.md` 의 naming 보강 — `api-endpoint.md` 가 PEP 257 enforce 핫스팟이라 본 PR 1순위. 나머지 2개는 별도 chore PR.

## 3. Audit Matrix (검증된 매핑)

### 3.1 코드 명명/문서화 (Kotlin + Python)

| 가이드 | 핵심 룰 카테고리 | 현재 enforce 도구 | 지속 자동 차단 채널 | 상태 |
|---|---|---|---|---|
| JetBrains Kotlin Conventions | Class/Function/Variable/Const PascalCase·camelCase·UPPER_SNAKE | detekt `ClassNaming` / `FunctionNaming` / `TopLevelPropertyNaming` (default active) + ktlint `standard:function-naming` / `standard:property-naming` | pre-commit (`.kt` → detektDebug) + CI (`android.yml`) | ✅ enforced |
| Google Android Kotlin Style | `@Composable` 함수 PascalCase | detekt `FunctionNaming` + `ignoreAnnotated: Composable` override | pre-commit + CI | ✅ enforced |
| Google Android Kotlin Style | Backing property `_field` / `field` | ktlint `standard:backing-property-naming` | pre-commit + CI | ✅ enforced |
| Google Android Kotlin Style | Boolean property `is` / `has` / `are` 접두 | detekt `BooleanPropertyNaming` **default NOT active** | ❌ 차단 채널 없음 | ⚠️ gap — 보강 안 함 (D3), audit 표에 기록 + SSoT 에 권장 가이드만 |
| PEP 8 (Python naming) | snake_case/PascalCase/UPPER_SNAKE | ruff `N` | **본 PR 에서 pre-commit `.py` 분기 추가 (§5.8)** + CI (`backend.yml`) | ✅ enforced + 자동 차단 추가 |
| PEP 257 (docstring) | public class/function docstring | (본 PR 에서 enforce) | **본 PR 에서 pre-commit `.py` 분기 추가 (§5.8)** + CI | ⚠️ → ✅ |
| PEP 484 (type hints) | function signature typing | mypy strict | CI (`backend.yml`) — pre-commit 미포함 (수행 시간 5~10s) | ✅ enforced |
| PEP 526 (variable annotations) | `var: type = value` | mypy strict + Pydantic plugin | CI | ✅ enforced |

**Kotlin 95%+ cover 의 근거**:
- detekt `buildUponDefaultConfig = true` (`app/build.gradle.kts`) → 위 default-active 룰 모두 활성
- ktlint 1.5.0 standard ruleset → backing-property-naming 포함
- 단 1 gap (`BooleanPropertyNaming`) 은 type resolution 필요라 detekt 가 default 로 비활성. Gradle classpath 설정 추가 + active 로 돌리면 enforce 가능하나, 사용자 결정에 따라 본 작업 범위 밖.

### 3.2 Azure 인프라 명명 (Microsoft CAF)

**공식 권장 패턴** (CAF resource-naming.md 인용): `<resource type>-<workload, application, or project>-<environment>-<region>-<###>`. 단 ACR/Storage 등 alphanumeric only 리소스는 하이픈 제거 + 압축형.

**공식 abbreviation** (CAF resource-abbreviations.md, fact-checked 2026-06-02):
- Resource group → `rg`
- Container Apps → `ca` (`Microsoft.App/containerApps`)
- Container Apps environment → `cae` (`Microsoft.App/managedEnvironments`)
- Container Registry → `cr` (`Microsoft.ContainerRegistry/registries`)
- PostgreSQL database → `psql` (`Microsoft.DBforPostgreSQL/servers`)

**현재 리소스 vs CAF 권장** (출처: CLAUDE.md §Infrastructure):

| 리소스 타입 | 현재 명 | CAF 권장 패턴 | 권장 예시 | 상태 |
|---|---|---|---|---|
| Resource group | `apps` | `rg-<workload>-<env>` | `rg-eundunhealth-prod` | ⚠️ workload+env+abbr 누락 |
| Container App | `eundunhealth-api` | `ca-<workload>-<env>` | `ca-eundunhealth-prod` | ⚠️ `ca-` abbreviation 누락 + env 누락 |
| Container Apps environment | (CLAUDE.md 미기재 — Azure portal 확인 필요) | `cae-<workload>-<env>` | `cae-eundunhealth-prod` | ⚠️ 실측 필요 |
| Container Registry | `eundunhealthacr` | `cr<workload><env><###>` (alphanumeric only) | `creundunhealthprod001` | ⚠️ `cr` prefix 대신 `acr` suffix 사용 + env+instance 누락 |
| PostgreSQL Flexible Server | `healthapp` | `psql-<workload>-<env>` | `psql-eundunhealth-prod` | ⚠️ `psql-` abbr 누락 + workload 명 불일치 (`healthapp` ≠ `eundunhealth`) |

**핵심 사실**:
- Microsoft 공식 문서 직접 인용: *"Most Azure resource names can't be changed after creation. Include only information that remains constant in the name."*
- 따라서 위 5개 리소스 모두 **rename 불가** → 변경하려면 신규 리소스 + 마이그레이션 + 기존 삭제 필요.
- v0.1.7 Internal Testing track 활성 + Container App URL 이 Android `BACKEND_URL` BuildConfig 에 baked → rename 시 신규 release build + Play Console 업데이트 + tester 일제 갱신 필요.
- ACR rename = 모든 이미지 재push + `backend.yml` workflow 변경 + `AZURE_CREDENTIALS` SP role assignment 재구성.
- PG rename = `pg_dump`/`pg_restore` 또는 logical replication + Container App secretref (`DATABASE_URL`) 갱신.

**결론**: §2 Out-of-scope 명시한 대로 인프라 rename 0건. 본 audit 결과는 (a) 향후 신규 리소스 명명 가이드 + (b) v1.x 이후 안정화 시점의 마이그레이션 검토 자료로 사용.

### 3.3 Azure 실측 검증 절차 (fact check)

본 audit 표의 "현재 명" 컬럼은 CLAUDE.md 의 인프라 섹션을 1차 출처로 사용. 운영자 확인용 검증 명령 (Azure MCP `tenant` 명시 필수, [[claude-code-mcp-install-gotchas]]):

```bash
# Resource group + 전체 리소스 실측 (sensitive 정보 출력 — local 만)
az group list --query "[].name" -o tsv
az resource list --resource-group apps --query "[].{name:name,type:type,location:location}" -o table

# Container Apps environment 명 (CLAUDE.md 미기재)
az containerapp env list --resource-group apps --query "[].name" -o tsv

# 또는 Azure MCP (tenant 명시):
# mcp__azure__group_list --tenant <TENANT_ID>
# mcp__azure__group_resource_list --tenant <TENANT_ID> --resource-group apps
```

위 명령 결과로 §3.2 표 갱신 (특히 Container Apps environment 명) 후 본 doc PR 에 commit.

### 3.4 지속 자동화 인프라 audit (현재 vs 목표)

본 PR 의 효율성 인프라 축의 baseline. 7개 채널을 한 번에 본다 — 본 PR 의 작업 산출물은 그 중 5개 + CLAUDE.md link 1줄 (D9). 나머지 2개 (`CI`, `Kotlin pre-commit`) 는 이미 working state 라 reference 만 — drift 점검 시 baseline 확인용.

| 채널 | 현재 상태 | 목표 (본 PR 후) | 주 호출자 / 트리거 |
|---|---|---|---|
| pre-commit hook | `.kt`/`.kts` → spotless+detekt, `docs/plans/*.md` → README 갱신. **`.py` 분기 없음** | `.py` staged → `ruff check` (E/F/I/N/UP/D 전체) 자동 실행. fail 시 commit 차단 | 개발자 로컬 |
| GitHub Actions CI | `backend.yml` 의 ruff step 이 `pyproject.toml` 전체 룰셋 적용 (본 PR 의 `D` 추가가 자동 반영) | 그대로 — pyproject 변경만으로 CI 도 D 룰 적용 | CI |
| PR template | `pytest tests/ -v` 만 명시. **ruff/mypy 미명시** + Azure 신규 리소스 시 CAF 가이드 없음 | Backend 섹션에 `ruff check app/` + Azure 신규 리소스 추가 시 CAF abbreviation 체크박스 | reviewer 인지 |
| `/naming-audit` 슬래시 명령 | 부재 | `.claude/commands/naming-audit.md` — ruff D + detekt naming + Azure CAF 표 갱신 안내 1-command | Claude / 운영자 |
| `scripts/prompts/api-endpoint.md` | **legacy 상태**: Ktor `EundunApi.kt` 수정 안내 (이미 openapi-generator 로 대체됨, 실제로 작동 안 함). 백엔드 Kotlin path 안내. | FastAPI + openapi-generator workflow + router docstring PEP 257 요구 + Azure 신규 리소스 시 CAF 명명 권장 | 새 endpoint 작성자 |
| `docs/conventions/naming.md` SSoT | 부재 — D1~D10 결정이 design doc 안에만 갇혀 있음 → CLAUDE.md 에 link 없음 → Claude Code 자동 컨텍스트 미포함 | 신규 — 5종 가이드 요약 + 본 PR 결정 + 신규 코드/리소스 체크리스트. CLAUDE.md 에서 link → 모든 세션에서 자동 로딩 | 모든 세션 |
| CLAUDE.md | 운영 규칙 8개 + 슬래시 명령 `/verify-deploy` 만 안내 | naming SSoT link + `/naming-audit` 안내 1줄 추가 | 모든 세션 |

## 4. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 1차 산출물 형태 | 혼합 (audit + 선별 enforce) | 룰 강화 폭을 데이터로 결정 — 위반 분포 보지 않고 enforce 시 baseline 과잉 박제 위험 |
| D2 | PEP 257 적용 범위 | public API 만 (D100/D104/D107 ignore) | 모듈/패키지 헤더 강제는 boilerplate 양산. 사용자/유지보수에 valuable 한 layer 는 router/Service/Repository |
| D3 | Kotlin 보강 폭 | gap 스캔만, 룰 추가 0건 | detekt+ktlint 가 95%+ cover. 단 1 gap (BooleanPropertyNaming) 은 type resolution 필요 + baseline-debug.xml 의 기존 위반과 충돌 우려. v0.1.x 단계에서 추가 부담 회피 |
| D4 | PR 분할 | 1개 PR 통합 | 위반 fix 분량 ~59건 + 룰 변경 1 파일 — 한 PR 로 review 가능. 분할은 cycle time 증가만 |
| D5 | docstring convention 선택 | `pep257` | 프로젝트가 PEP 8 기반 + Google/NumPy 스타일은 Args/Returns/Raises 헤더 강제 → boilerplate 증가. `pep257` 이 가장 minimal |
| D6 | schemas/models D101 ignore | ignore | Pydantic `Field(description=...)` 과 SQLAlchemy table 정의가 1차 도그. class docstring 중복 |
| D7 | tests/alembic D 전체 ignore | per-file-ignore | test 함수명 자체가 spec, alembic 은 auto-generated |
| D8 | Azure CAF 적용 범위 | audit only (인프라 변경 0) | Microsoft 공식 "Most resources can't be renamed" + v0.1.7 Internal Testing 활성 + ACR/Container App URL/DB connection 일제 전환 비용 > audit 가치. 신규 리소스에만 적용 (별도 chore PR) |
| D9 | 지속 자동화 인프라 범위 | 5개 (pre-commit `.py` 분기 / PR template / `/naming-audit` 슬래시 / `api-endpoint.md` prompt 갱신 / `docs/conventions/naming.md` SSoT) + CLAUDE.md link 1줄 | role 적용 = 1회성, 자동화 = 지속적. 5개 중 pre-commit 이 가장 임팩트 (commit-time 차단), SSoT + CLAUDE.md 가 두 번째 (모든 세션 자동 컨텍스트), 나머지는 인지/리뷰 보조. `new-screen.md`/`bug-fix.md` 갱신은 본 PR scope 초과 — 별도 chore |
| D10 | `.claude/skills/naming-convention/` 신규 skill | 만들지 않음 (out-of-scope) | PEP 257 위반은 ruff 가 pre-commit + CI 에서 자동 차단 → skill 별도 invocation 가치 ↓. CAF 명명은 IaC 미도입 상태에서 신규 리소스 추가 빈도 매우 낮음 → SSoT 문서 link 로 충분. 향후 IaC 도입 시점에 재검토. skill 인프라 유지비 (description 튜닝 + 변경 시 동기화) 절약 |

## 5. 구성 요소별 변경

### 5.1 MODIFY: `backend/pyproject.toml`

```toml
[tool.ruff]
line-length = 120
target-version = "py312"

[tool.ruff.lint]
select = ["E", "F", "I", "N", "UP", "D"]
ignore = [
    "N818",         # AppException 네이밍 유지 (기존)
    "D100", "D104", # 모듈/패키지 헤더 docstring 의무 X — 모듈 헤더 주석 스타일 유지
    "D107",         # __init__ docstring 의무 X — 클래스 docstring 으로 대체
    "D203", "D213"  # convention="pep257" 와 redundant 하지만 의도 명확화 위해 명시
]

[tool.ruff.lint.pydocstyle]
convention = "pep257"

[tool.ruff.lint.per-file-ignores]
"tests/**"        = ["D"]      # test 이름이 spec — 48건 제외
"alembic/**"      = ["D"]      # auto-generated — 16건 제외
"app/main.py"    = ["D"]       # FastAPI 앱 인스턴스
"app/schemas/**" = ["D101"]   # Pydantic schema — Field(description=) 대체, 7건 제외
"app/models/**"  = ["D101"]   # SQLAlchemy ORM table — 3건 제외

[tool.mypy]
python_version = "3.12"
strict = true
plugins = ["pydantic.mypy"]

# (이하 pytest 섹션 변경 없음)
```

차이 설명:
- `select` 에 `D` 추가 (PEP 257 pydocstyle)
- `ignore` 에 D100/D104/D107/D203/D213 5개 추가
- `[tool.ruff.lint.pydocstyle] convention = "pep257"` 신규 — Google/NumPy 와 달리 Args/Returns 헤더 강제 안 함
- `[tool.ruff.lint.per-file-ignores]` 신규 — 5개 path 패턴

### 5.2 MODIFY: `backend/app/` public class/function docstring 추가

실측 분포 (`backend/.venv/Scripts/ruff.exe check --select D --ignore D100,D104,D107,D203,D213 --statistics app/`):

| 카테고리 | 건수 | 대응 |
|---|---:|---|
| D101 public class | 26 | schemas/ 7 + models/ 3 = 10 추가 ignore → **16 docstring 작성** |
| D102 public method | 25 | 25 docstring 작성 |
| D103 public function | 22 | 22 docstring 작성 |
| D205 missing-blank-line-after-summary | 1 | manual fix (docstring 작성 시 동시) |
| D209 new-line-after-last-paragraph | 1 | ruff `--fix` 자동 |
| D400 missing-trailing-period | 2 | manual fix |
| D403 first-word-uncapitalized | 1 | ruff `--fix` 자동 |
| D415 missing-terminal-punctuation | 2 | **모두 `main.py` 안 → per-file-ignore 와 함께 제외 (작업 대상 0)** |
| **합계** | **80** | **59 신규 작성 + 3 manual fix (D205+D400) + 2 auto-fix (D209+D403) + 16 추가 ignore (schemas D101 7 + models D101 3 + main.py D 전체 6 — 그 중 D415 2 포함) = 80 ✓** (Task 1 실측 64건 = 59 작성 + 5 fix 의 합과 일치) |

**파일 디렉토리별 분포** (D101+D102+D103, app/ 기준):
- `app/repositories/` 21건
- `app/routers/` 15건
- `app/services/` 14건
- `app/exceptions.py` 4건
- `app/schemas/` 7건 → 모두 ignore
- `app/models/` 3건 → 모두 ignore
- 기타 (`app/config.py`, `app/database.py`, `app/dependencies.py`) 5건

**docstring 작성 정책**:
- 1-2 줄 한국어 요약 + 필요시 Args/Returns/Raises 섹션. boilerplate 금지.
- behavioral "why" 중심. type 정보 중복 금지 (mypy 가 cover).
- 한/영 mixed OK — 도메인 용어 (Supabase, JWT, Container App 등) 는 영문 유지.
- D209/D403 은 `ruff check --fix` 로 자동 해결. D205/D400 은 manual fix.

예시 (router):
```python
@router.get("/profile", response_model=ProfileResponse)
async def get_profile(
    user_id: UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> ProfileResponse:
    """현재 인증 사용자의 프로필을 반환한다. 없으면 404."""
    service = ProfileService(db)
    return await service.get_profile(user_id)
```

예시 (Service public method):
```python
class GoalService:
    """사용자 목표 (체중 / 체지방) 의 CRUD + 달성 판정."""

    async def upsert_goal(self, user_id: UUID, goal: GoalUpsert) -> GoalResponse:
        """기존 goal 이 있으면 갱신, 없으면 생성. 달성 시 badge 자동 부여."""
        ...
```

### 5.3 NEW: `docs/plans/2026-06-02-naming-convention-audit-design.md`

본 문서.

### 5.4 NEW: `docs/plans/2026-06-02-naming-convention-audit-plan.md`

writing-plans 단계에서 작성. 단계별 구현 task + verification gate.

### 5.5 MODIFY: `docs/plans/README.md`

`bash scripts/gen-plans-index.sh` 자동 갱신 (pre-commit hook + CI drift check). design+plan 페어 추가 시 같은 commit 에 포함 — v0.1.7 lesson (Task 1 누락 → CI check-index fail 사례).

### 5.6 변경 0건: Android (`app/`)

audit 결과 cover 확인. detekt+ktlint 룰 변경 0건, `.kt` 파일 변경 0건.

### 5.7 변경 0건: Azure 인프라

§3.2 audit 결과 5개 리소스 모두 CAF 권장 패턴 불일치 확인. 그러나 §2 Out-of-scope + D8 결정에 따라 인프라 변경 0건. ARM template / azd / IaC 도입 안 됨 — 향후 IaC 도입 시점에 CAF 패턴 적용 검토. 본 PR 산출물은 §3.2 audit 표 (향후 reference) 만.

### 5.8 MODIFY: `.githooks/pre-commit` — backend `.py` 분기 추가

현재 hook 은 Kotlin `.kt`/`.kts` + `docs/plans/*.md` 만 cover. backend `.py` 변경은 ruff 통과 없이 commit 가능 — D9 의 첫 번째 우선순위 (commit-time 차단).

기존 hook 의 Kotlin 블록 패턴을 그대로 따라 새 블록 추가:

```bash
# ---------- 3) Backend Python ----------
CHANGED_PY=$(git diff --cached --name-only --diff-filter=ACM \
    | grep -E '^backend/.*\.py$' || true)

if [ -n "$CHANGED_PY" ]; then
    echo "[pre-commit] backend/*.py changes detected → ruff check"
    cd "$REPO_ROOT/backend"

    # venv ruff 우선, 없으면 pip 글로벌 ruff 시도 (CI 와 동일 룰셋)
    RUFF=".venv/Scripts/ruff.exe"
    [ -x "$RUFF" ] || RUFF="ruff"

    # 변경된 파일만 검사 (전체 app/ 스캔보다 빠름)
    echo "$CHANGED_PY" | sed 's|^backend/||' | xargs "$RUFF" check
fi
```

**설계 선택**:
- **변경된 파일만 lint** — 전체 `app/` 보다 commit time 빠름. 단점: 다른 파일과의 cross-cutting 위반 (예: import 순서) 은 CI 에서만 발견. 그러나 N/D 룰은 file-local 이라 충분.
- **`ruff format` 미실행** — Kotlin spotlessApply 와 다르게 ruff format 은 별도 의사결정 (현재 pyproject 에 `format` 설정 없음). 본 PR scope 밖.
- **mypy 미실행** — 5~10s 소요로 commit time 느려짐. CI 가 cover. 룰 6 의 pre-commit 패턴 (빠른 차단 + CI 깊은 검증) 따름.
- **fail-open 안 함** — Kotlin 블록과 동일하게 `set -euo pipefail` → ruff fail 시 commit 차단.

### 5.9 MODIFY: `.github/pull_request_template.md` — Backend 섹션 보강

기존 "테스트" 섹션의 backend 라인을 다음으로 교체:

```markdown
- [ ] `cd backend && .venv/Scripts/ruff.exe check app/ tests/` 통과 (PEP 8/257/import order)
- [ ] `cd backend && .venv/Scripts/mypy app/` 통과 (PEP 484/526 type hints)
- [ ] `cd backend && .venv/Scripts/pytest tests/ -v` 통과
```

기존 "Destructive 명령" 섹션 뒤에 새 항목 추가:

```markdown
## Azure 신규 리소스 추가 (해당 시)
<!-- 새 RG / Container App / ACR / DB 등을 생성하는 PR 일 때 -->
- [ ] 해당 없음
- [ ] 또는 다음을 확인했음 (Microsoft CAF, `docs/conventions/naming.md`):
  1. 리소스 타입 abbreviation 사용 (예: `ca-`, `cae-`, `cr`, `psql-`, `rg-`)
  2. workload 명은 `eundunhealth` (기존 명명과 일관성 유지)
  3. environment suffix (예: `-prod`, `-dev`) 명시
  4. ACR/Storage 처럼 alphanumeric only 리소스는 하이픈 제거 + 압축형
  5. `docs/plans/2026-06-02-naming-convention-audit-design.md` §3.2 audit 표에 신규 리소스 1행 추가
```

### 5.10 NEW: `.claude/commands/naming-audit.md` — 슬래시 명령

`verify-deploy.md` 패턴 따라 frontmatter + 단계별 안내. `/naming-audit` 으로 호출 시 Claude 가:

```markdown
---
description: Naming convention audit re-run (ruff D + detekt naming + Azure CAF 표 sync)
allowed-tools: Bash, Read, Grep, Glob, Edit, mcp__azure__group_resource_list, mcp__azure__group_list
argument-hint: [--update-doc] (audit 결과로 design doc §3 갱신 commit)
---

본 프로젝트의 명명/문서화 컨벤션 준수도를 재측정합니다. 시간 경과에 따른 drift 점검 + 신규 PR 머지 후 baseline 갱신 용도.

## 검증 단계

### 1. Python PEP 257 violation 측정
`cd backend && .venv/Scripts/ruff.exe check --select D --statistics app/ tests/ alembic/` 실행.
기대: 0 errors (본 PR 머지 후). 잔존 시 카테고리별 list.

### 2. Python PEP 8 naming (N) 측정
`cd backend && .venv/Scripts/ruff.exe check --select N --statistics app/` 실행.
기대: N818 외 0.

### 3. Kotlin detekt naming 측정
`./gradlew :app:detektDebug` 실행 후 build/reports/detekt/detekt.html 의 naming 카테고리 확인.
baseline 외 신규 위반 0 기대.

### 4. Azure CAF 표 sync 안내
`docs/plans/2026-06-02-naming-convention-audit-design.md` §3.2 표 vs 실측 Azure 리소스 명 비교.
실측: `az resource list -g apps -o table` + `az containerapp env list -g apps -o tsv` (또는 Azure MCP, tenant 명시).
신규 리소스 발견 시 §3.2 표에 1행 추가 + CAF 권장 매핑 작성.

### 5. 결과 보고
- 1~3 의 위반 개수 표로 정리.
- 4 의 drift (신규/누락 리소스) list.
- `--update-doc` flag 시: design doc §3.4 의 "현재 상태" 컬럼 + §3.2 Azure 표를 갱신할 patch 를 **제안**한다. **자동 commit 하지 않음** — CLAUDE.md "NEVER commit unless explicitly asked" 룰 준수. 사용자가 검토 후 `commit` 명시 시에만 진행.
```

**slash command 호출 시 예상 동작 시간**: ruff D + N (~2s) + detekt (~30~60s, gradle daemon hot) + Azure MCP 호출 (~5~10s) — 총 ~1~2 분. 매 PR 마다 호출 X, 분기당 1~2회 drift 점검 의도.

### 5.11 MODIFY: `scripts/prompts/api-endpoint.md` — legacy 제거 + naming 보강

현 파일은 **Ktor 백엔드 시절 (마이그레이션 전) 패턴** 그대로 — `EundunApi.kt` 수동 수정 + `backend/src/main/kotlin/...` Kotlin path 안내. 실제로는 openapi-generator 전환 (Phase 5B+5C) 후 자동 생성. 이 prompt 를 그대로 따르면 잘못된 코드 작성 위험.

전면 재작성 — 신규 endpoint 추가 시 작업 순서:

```markdown
# API 엔드포인트 추가 작업 템플릿

## 0. 사전 확인
- backend `cd backend && docker compose up -d` 실행 중인지 확인.
- 신규 endpoint 가 v0.x 의 어느 SPEC 항목인지 확인 (`docs/SPEC.md`).

## 1. Backend (FastAPI) — primary

### 1.1 Pydantic schema (`backend/app/schemas/<domain>.py`)
- `CamelSchema` 상속 (alias_generator=to_camel). PEP 257 D101 ignore 대상.
- Field 에 `description=...` 명시 (OpenAPI 노출).

### 1.2 Repository (`backend/app/repositories/<domain>_repo.py`)
- public class/method 에 docstring 필수 (PEP 257, `docs/conventions/naming.md`).
- async + SQLAlchemy 2.0 `Mapped[T]`.

### 1.3 Service (`backend/app/services/<domain>_service.py`)
- public method 에 docstring 필수.

### 1.4 Router (`backend/app/routers/<domain>.py`)
- `@router.<verb>("/path", response_model=..., operation_id="<camelCase>")` — operation_id 가 Android client 함수명.
- 함수 자체에 docstring 1-2 줄 (FastAPI 가 `description` 으로 자동 노출).
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
- 같은 PR entrypoint 검증 + operations-snapshot.md head 갱신 (룰 7).

### 1.8 검증
```bash
cd backend
.venv/Scripts/ruff.exe check app/         # PEP 8/257 통과
.venv/Scripts/mypy app/                    # PEP 484/526 통과
.venv/Scripts/pytest tests/ -v            # 회귀 0
```

## 2. Android — auto-generated, 수동 수정 X

`./gradlew :app:assembleDebug` 시 `:app:openApiGenerate` 가 자동 실행 → `app/build/generated/openapi/...` 에 Kotlin client 생성.

### 2.1 Repository (`data/repository/<Domain>RepositoryImpl.kt`)
- generated `api.generated.<Domain>Api` 주입.
- `data/remote/util/ResponseExt.kt` 의 `bodyOrThrow()` 호출.

### 2.2 DI 바인딩 (`di/NetworkModule.kt`)
- generated Api provider 추가 (기존 5개 패턴 따라).

### 2.3 ViewModel + UI
- `runCatching { ... }.onFailure { e -> val a = e.toAppError(); a.reportToSentry(); _error.value = a }`.
- 사용자 액션 실패 표시: 룰 8 (inline + persistent + a11y + Sentry breadcrumb) 준수.

## 3. 명명/문서화 체크 (PR 머지 전)

- [ ] Backend public class/function 에 docstring (`docs/conventions/naming.md` 의 PEP 257 절)
- [ ] Router `operation_id` 가 Android 함수명과 일치
- [ ] Query param 에 `alias="camelCase"` 명시
- [ ] `backend/openapi.json` sync 커밋
- [ ] Android Repository 가 generated API 만 사용 (`EundunApi.kt` 추가 금지 — 이미 deprecated)
- [ ] Kotlin 명명: PascalCase 클래스 / camelCase 함수 / UPPER_SNAKE const — detekt 가 차단

## 주의사항
- 모든 endpoint 는 JWT 인증 필수 (/health 제외). Supabase ES256 (JWKS).
- Token: NetworkModule 의 `AtomicReference`, `TokenAuthenticator` 가 401 자동 갱신.
- Android ↔ Backend 필드명 일치는 OpenAPI 가 자동 보장 (수동 `@SerialName` 불요).
```

### 5.12 NEW: `docs/conventions/naming.md` — SSoT

5종 가이드 요약 + 본 PR 의 D1~D10 결정 + 신규 코드/리소스 체크리스트. CLAUDE.md 에서 link → Claude Code 모든 세션에서 자동 컨텍스트. **신규 개발자 onboarding 1차 entry point** + 운영 중 룰 lookup 의 최단 경로.

구성 (실제 작성 outline — plan 단계에서 inline 본문 채움):
```markdown
# 명명/문서화 컨벤션 (SSoT)

> 본 문서는 single source of truth.
> - 변경 시 `docs/plans/2026-06-02-naming-convention-audit-design.md` 의 §3 + §4 와 동기.
> - 신규 개발자: 본 문서만 읽으면 컨벤션 파악 가능. 의사결정 배경은 design doc 참조.

## 1. Kotlin (Android)
- 모든 룰: detekt + ktlint 가 자동 차단 (pre-commit + CI).
- `@Composable` 만 PascalCase 예외 (config/detekt/detekt.yml).
- 권장 (미 enforce): boolean property `is`/`has`/`are` 접두 — Google Android Style.

## 2. Python (Backend)
- naming: ruff `N` (PEP 8). N818 만 의도적 ignore.
- docstring: ruff `D` + `convention="pep257"`. public class/function 필수. tests/alembic/main.py/schemas/models 제외 (`pyproject.toml`).
- type hints: mypy strict (PEP 484/526).
- 작성 정책: 1-2 줄 한국어 요약, behavioral "why", 도메인 용어는 영문 유지.

## 3. Azure 인프라
- Microsoft CAF: `<type>-<workload>-<env>-<region>-<###>`.
- 권장 abbreviation: `rg` / `ca` / `cae` / `cr` / `psql`.
- **기존 리소스 rename 금지** (CAF 공식: not renameable). 신규 리소스만 적용.

## 4. 신규 코드 추가 시 체크리스트
(아래 5.11 의 "§3. 명명/문서화 체크" 항목 6개를 inline 으로 복사. 외부 reference 보다 SSoT 안에서 self-contained 하게.)

## 5. 신규 Azure 리소스 추가 시 체크리스트
(아래 5.9 의 "Azure 신규 리소스 추가" 섹션 항목 5개를 inline 으로 복사.)

## 6. 참고
- 의사결정 기록 (배경): `docs/plans/2026-06-02-naming-convention-audit-design.md`
- 슬래시 명령: `/naming-audit` (drift 점검, 분기당 1~2회 권장)
- 공식 문서 URL 5개 (design doc §9 와 동일)
```

### 5.13 MODIFY: `CLAUDE.md` — SSoT link + 슬래시 명령 안내

기존 "### Claude Code 슬래시 명령 (`.claude/commands/`)" 섹션의 `/verify-deploy` 항목 다음 줄에 추가:

```markdown
- `/naming-audit` — 명명/문서화 컨벤션 drift 점검 (ruff D + detekt naming + Azure CAF 표 sync).
  자세한 룰: `docs/conventions/naming.md`. 결정 기록: `docs/plans/2026-06-02-naming-convention-audit-design.md`. 분기당 1~2회 권장.
```

기존 "## Documentation" 섹션의 마지막 (또는 `@docs/ops/...` 그룹 직후) 에 1줄 추가:

```markdown
- `@docs/conventions/naming.md` — 명명/문서화 SSoT (5종 공식 가이드 + 본 프로젝트 결정 D1~D10)
```

**`@` prefix**: CLAUDE.md 의 기존 패턴 — Claude Code 가 세션 시작 시 자동 로딩. 즉 본 link 추가만으로 모든 세션에서 naming.md 가 자동 컨텍스트로 들어옴.

## 6. 검증 계획

### 6.1 자동 검증
```bash
cd backend
.venv/Scripts/ruff.exe check app/ tests/ alembic/    # D 위반 0, 기존 E/F/I/N/UP 회귀 0
.venv/Scripts/ruff.exe format --check app/            # 포맷 회귀 0
.venv/Scripts/mypy app/                                # PEP 484/526 회귀 0
.venv/Scripts/pytest tests/ -v --cov=app             # 41/41 PASS 유지, coverage 82%+ 유지
```

### 6.2 OpenAPI description 회귀 확인
FastAPI 가 router 함수의 docstring 을 endpoint `description` 으로 자동 노출하므로, `backend/openapi.json` 에 description 필드 변화가 발생함. drift detection step (`backend.yml`) 통과 + Android `openapi-generator` 입력 정상.
```bash
bash scripts/sync-openapi.sh
git diff backend/openapi.json  # description 변경만 포함, schema 변경 없음 확인
```

### 6.3 runtime smoke (룰 6/7 가드 일관성)
```bash
cd backend && docker compose up -d --build
curl http://localhost:8080/health  # 200 OK
docker compose logs api | grep -E "alembic upgrade head|Uvicorn running"
docker compose down
```

### 6.4 manual spot-check (5건 sampling)
무작위로 작성된 docstring 중 5건 — 한국어 자연스러움 + behavioral "why" 중심 확인.

### 6.5 자동화 인프라 작동 확인 (D9 산출물)

- **pre-commit `.py` 분기**: 일부러 PEP 257 위반 (docstring 없는 새 함수) 을 staged → `git commit -m test` 시도 → "ruff check failed" 로 차단되는지 확인. 이후 docstring 추가 후 commit 성공. **테스트 후 dummy commit revert**.
- **PR template 렌더링**: 본 PR 자체가 PR template 의 첫 번째 사용 사례. `gh pr create` 시 (또는 github.com PR 작성 화면에서) Backend 섹션의 ruff/mypy/pytest 3줄 체크박스 + Azure 신규 리소스 섹션 노출 확인. 별도 dummy branch 불요.
- **`/naming-audit` 슬래시 명령**: 새 Claude Code 세션에서 `/naming-audit` 호출 → frontmatter 의 5단계 모두 실행되고 결과 표 출력되는지 확인. mcp__azure__* unavailable 시 4단계만 fail 하고 1~3, 5 단계 결과는 정상 (frontmatter 의 fail-continue 정책).
- **`scripts/prompts/api-endpoint.md`**: `grep -E "EundunApi\.kt|backend/src/main/kotlin" scripts/prompts/api-endpoint.md` → 0건 잔존 확인. PEP 257 / `operation_id` / `bash scripts/sync-openapi.sh` keyword grep → 모두 hit 확인.
- **`docs/conventions/naming.md` link**: `grep "docs/conventions/naming.md" CLAUDE.md` → 2 hit (슬래시 명령 안내 + Documentation 섹션). 새 Claude Code 세션 시작 시 SessionStart hook 의 자동 컨텍스트에 포함되는지 (CLAUDE.md 가 항상 로딩되므로 자동 — 별도 검증 불요).

## 7. 롤백 절차

- 본 PR 머지 후 회귀 발견 시: `git revert <merge-commit>` 단일 명령으로 다음 모두 동시 롤백:
  - `backend/pyproject.toml` (ruff D 룰 비활성화)
  - `backend/app/` docstring 추가 (lint 동작 영향 없음 — docstring 은 코드 동작에 영향 없으므로 잔존해도 안전, 하지만 revert 일관성 위해 함께)
  - `.githooks/pre-commit` (`.py` 분기 제거 → Kotlin only 로 복원)
  - `.github/pull_request_template.md` (Backend 섹션 + Azure 섹션 복원)
  - `.claude/commands/naming-audit.md` (슬래시 명령 제거)
  - `scripts/prompts/api-endpoint.md` (Ktor legacy 복원 — 단 legacy 복원이 오히려 손해, **이 파일만 revert 안 함** 권장 if 회귀 원인이 다른 곳이라면 부분 revert)
  - `docs/conventions/naming.md` (SSoT 제거)
  - `CLAUDE.md` (SSoT link 제거)
  - audit doc 자체
- Container Apps deploy 무관 (코드 동작 변경 없음, docstring 만 추가). 트래픽 영향 0.
- ACR 이미지 캐시 / Alembic 미관여.
- **부분 revert**: 회귀 원인이 1개 채널 (예: pre-commit hook 만) 로 좁혀지면 해당 파일만 `git checkout <merge-commit>^ -- <file>` 로 복원. 머지 commit 1개라 추적 명확.

## 8. 잔여 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| docstring 자연어 품질 일관성 부족 | 가독성 ↓ | spot-check (§6.4) + reviewer 1 round 명시 |
| FastAPI 가 docstring 을 `description` 으로 노출 → OpenAPI 가 커짐 | Android 빌드 영향 | drift detection step 통과 확인 (§6.2). description 은 generator 동작에 영향 없음 (operation_id 기반) |
| 향후 추가되는 public API 가 docstring 누락 시 CI fail | dev velocity | ruff check 가 빠른 fail (CI 30초 이내). 개발자 IDE 통합으로 즉시 알림 |
| BooleanPropertyNaming gap 미보강 | naming inconsistency 잔존 | audit doc 에 기록. 향후 별도 chore PR 에서 `requires-type-resolution` 설정 추가 검토 가능 |
| convention="pep257" + 명시 `ignore` 의 중복 | warning 출력 | ruff 가 warning 만 출력하고 정상 동작. 의도 명확화 트레이드오프 — 유지 |
| pyproject 변경이 dev/CI 의 lint 동작 차이 유발 | false negative | `backend.yml` 의 ruff step 이 동일 pyproject 사용 — 차이 발생 안 함 |
| Azure 리소스 명명 inconsistency 잔존 (5개 리소스 CAF 불일치) | 향후 신규 리소스와 명명 패턴 혼재 — 운영자 cognitive load ↑ | audit 표 보존 (§3.2). v1.x 안정화 시 별도 chore PR 로 (a) 새 RG 생성 + CAF 명명 + 리소스 move (불가능한 것은 신규 생성) (b) 또는 신규 리소스만 CAF 적용. 마이그레이션 시 §6.3 smoke + Sentry/Container Apps health 가드 필수 |
| §3.2 표의 Container Apps environment 명 미실측 (Azure MCP tenant 미설정으로 빈 응답) | 표 정확도 ↓ | 운영자가 `az containerapp env list -g apps` 또는 Azure MCP `tenant` 명시 후 실측 → 본 doc 갱신 commit. 차단 사유 아님 (코드 변경 영향 0) |
| pre-commit `.py` 분기가 venv 없는 환경에서 fail (CI 환경 / 신규 개발자) | 로컬 commit 차단 | `RUFF=.venv/Scripts/ruff.exe` 가 없으면 `ruff` (PATH) fallback. 그래도 없으면 hook 자체 fail → 개발자가 venv setup 안내 README 확인 후 해결. CI 는 hook 안 돌리므로 영향 없음 |
| 자동화 인프라 5종 추가가 향후 maintenance 부담 (pyproject 변경 시 SSoT/design doc 양쪽 동기 필요) | 문서-룰 drift | `/naming-audit` 슬래시 명령이 sync 보조 + SSoT 의 §6 에 design doc reference 1줄로 lookup 비용 ↓. drift 검출 자동화는 over-engineering, 분기별 수동 점검으로 충분 |
| `scripts/prompts/api-endpoint.md` 전면 재작성 후 미사용 발견 가능성 | 작업 낭비 | 본 PR 의 §5.11 작업이 동시에 legacy bug fix (잘못 따라하면 deprecated Kotlin path 안내) 도 겸함 → 재작성 자체가 가치. 사용 빈도 ↓ 시 새 v0.2 작업에서 자연 검증 |
| `/naming-audit` 슬래시 명령이 mcp__azure__* tools 미설정 환경에서 4단계 fail | command UX 저하 | frontmatter 의 단계별 "fail 시 다음 단계 계속" 정책 (`verify-deploy.md` 와 동일). 부분 결과라도 보고 |
| `docs/conventions/naming.md` 와 design doc §3/§4 가 drift | SSoT 신뢰성 ↓ | SSoT 의 §6 에 design doc reference + "변경 시 양쪽 동기" 명시. 자동 drift check 는 over-engineering — 변경 빈도 낮음 (룰셋 변경 = 분기당 0~1회). `/naming-audit --update-doc` flag 가 1-command sync 보조 |
| ruff 버전 업 시 D 룰셋 추가/제거로 본 PR 의 80건 baseline 변동 | 본 doc §5.2 표 수치 신뢰성 ↓ | `backend/requirements-dev.txt` 에 ruff 버전 pin (현재 관행). 버전 업 PR 시 `/naming-audit` 재실행 → 본 doc §3 갱신 commit. 분기당 1회 ruff 메이저 업데이트 가정 — 유지 가능 |
| 본 PR 분량 (pyproject 1 + docstring 63 + 자동화 인프라 5 + audit doc 1 = ~70 file change) 가 review fatigue | 리뷰어 burden ↑ | **PR 분할 fallback**: D4 (1 PR 통합) 는 default. review 지연 시 다음 순서로 분할 — (a) audit doc + pyproject + per-file-ignore (작은 PR, baseline lock), (b) docstring 63건 + minor fix (디렉토리별 sub-PR 3개로 더 잘게: repos/routers/services), (c) 자동화 인프라 5종 + CLAUDE.md (작은 PR). plan 단계에서 reviewer feedback 받아 결정 |
| 신규 개발자 onboarding 시 SSoT 만으로 충분한가 | onboarding friction | `docs/conventions/naming.md` 의 §6 에 design doc + `/naming-audit` 슬래시 명령 + 공식 가이드 URL 5개 모두 link → drill-down 가능. 1차 entry point + 추가 정보 path 명확 |

## 9. 참고 자료

- JetBrains Kotlin Coding Conventions — https://kotlinlang.org/docs/coding-conventions.html
- Google Android Kotlin Style Guide — https://developer.android.com/kotlin/style-guide
- PEP 8 Style Guide for Python Code — https://peps.python.org/pep-0008/
- PEP 257 Docstring Conventions — https://peps.python.org/pep-0257/
- PEP 484 Type Hints — https://peps.python.org/pep-0484/
- PEP 526 Syntax for Variable Annotations — https://peps.python.org/pep-0526/
- ruff pydocstyle 규칙 — https://docs.astral.sh/ruff/rules/#pydocstyle-d
- ruff convention 설정 — https://docs.astral.sh/ruff/settings/#lint_pydocstyle_convention
- detekt naming ruleset — https://detekt.dev/docs/rules/naming
- Microsoft CAF Define Naming Convention — https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-naming
- Microsoft CAF Resource Abbreviations — https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-abbreviations
- Azure resource name rules (per-resource length/character) — https://learn.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules
- 프로젝트 컨벤션: [[design-plan-docs-convention]] (frontmatter + 자동 INDEX + ledger absorb)
- 사용자 경험 lesson: v0.1.7 release ledger entry (`docs/plans/logs/process-infra.md` — gen-plans-index.sh task 누락 사례)
- Claude Code 환경 가이드: [[claude-code-mcp-install-gotchas]] (Azure MCP `tenant` 명시 필수)

### 내부 인프라 참조
- 기존 pre-commit hook 패턴: `.githooks/pre-commit` (Kotlin + docs/plans 분기) — 본 PR §5.8 이 같은 패턴으로 `.py` 분기 추가
- 기존 슬래시 명령 패턴: `.claude/commands/verify-deploy.md` — 본 PR §5.10 이 같은 패턴으로 `naming-audit.md` 작성
- 기존 PR template: `.github/pull_request_template.md` — 본 PR §5.9 가 Backend 섹션 보강 + Azure 신규 리소스 섹션 추가
- 기존 prompt template: `scripts/prompts/api-endpoint.md` (legacy, Ktor 시절) — 본 PR §5.11 이 FastAPI + openapi-generator 로 전면 재작성

## 10. 구현 순서 (high-level) — plan 단계 anchor

본 design doc 만으로 plan 단계 (writing-plans) 가 진행 가능하도록 구현 mental model 을 단계별로 명시. 실제 step-by-step task + verification gate 는 plan doc 에서 작성.

1. **Pyproject baseline lock** — `backend/pyproject.toml` 에 ruff `D` + per-file-ignore 추가. 이 시점에 `ruff check app/` 가 80건 D 위반 표시 (예측 baseline). plan 의 첫 verification gate.
2. **Docstring 추가 (대량 변경)** — 우선순위: routers (15) → services (14) → repositories (21) → exceptions/config/database/dependencies (9) → minor fix (5+2). batch 단위 — 한 batch 끝나면 `ruff check --select D` 잔존 0 확인. 약 2~4 시간 작업.
3. **Pre-commit hook `.py` 분기** (§5.8) — 위 2 완료 후 (그 전에 hook 활성화하면 본 PR 의 모든 docstring 추가 commit 이 hook 검증 통과해야 → 닭과 달걀). 활성화 후 가짜 violation commit 시도로 작동 확인.
4. **CLAUDE.md + SSoT** (§5.12, §5.13) — 자동화 인프라가 모두 자리 잡은 뒤 SSoT 작성 + CLAUDE.md link. SSoT 가 본 PR 의 실제 산출물을 정확히 기술.
5. **PR template** (§5.9) — Backend 섹션 + Azure 섹션 보강. 본 PR 자체가 첫 사용 사례.
6. **`/naming-audit` 슬래시 명령** (§5.10) — 마지막 단계. 위 산출물을 audit 대상으로 한 번 실행해서 0 violation + Azure 표 drift 0 확인.
7. **api-endpoint.md 재작성** (§5.11) — 위 작업과 독립 (legacy bug fix 성격). 어느 단계에 끼워도 OK.
8. **검증** (§6 전체) — ruff/mypy/pytest + smoke + sync-openapi + 자동화 인프라 5종 작동 확인.
9. **audit doc + plan doc + README.md** — 본 design doc + plan doc 커밋 + `bash scripts/gen-plans-index.sh` 자동 갱신 (v0.1.7 lesson — 같은 commit 에 포함 필수).

**예상 영향 분석 (PR 크기 추산)**:
- 변경 파일 수: ~10개 (pyproject 1 + docstring 잠재 25개 파일 + pre-commit 1 + PR template 1 + slash command 1 신규 + api-endpoint.md 1 + SSoT 1 신규 + CLAUDE.md 1 + audit doc 1 + plan doc 1 + README.md 자동 갱신 = ~33 files staged, ~10 unique 파일 + 디렉토리 다수)
- 변경 라인 수: docstring 59건 × 평균 2줄 ≈ 120 LOC + 자동화 인프라 ~200 LOC + audit doc ~700 LOC = 약 **1000 LOC**. 그 중 docstring 추가 + audit doc 이 80%.
- review 시간 추산: 30~60분 (대부분 docstring 자연어 검토).
