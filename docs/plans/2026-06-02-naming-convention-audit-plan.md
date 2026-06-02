---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: docs+backend-only
ledger_topic: process-infra
tags: [naming-convention, docstring, ruff, detekt, pep257, pep8, azure-caf, automation-infra]
---

# 명명/문서화 컨벤션 audit + 자동화 인프라 Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 5종 공식 명명/문서화 가이드 (JetBrains Kotlin / Android Style / PEP 8/257/484/526 / Azure CAF) 의 audit 결과를 코드+인프라에 반영 — backend public API 에 PEP 257 docstring enforce (~59건) + 지속 자동화 인프라 5종 (pre-commit `.py` 분기 / PR template / `/naming-audit` slash / `api-endpoint.md` 재작성 / `docs/conventions/naming.md` SSoT) + CLAUDE.md link 1줄. Kotlin/Azure 변경 0.

**Architecture (요약):** ruff `D` rule + `convention="pep257"` + per-file-ignore (`tests`, `alembic`, `app/main.py`, `app/schemas`, `app/models`) 로 ~80건 위반 중 59건 docstring 작성 + 5건 minor fix (D205 1 + D400 2 manual + D209 1 + D403 1 auto) + 16건 ignore (schemas D101 7 + models D101 3 + main.py D 전체 6 — D415 2건 포함). 자동화는 기존 패턴 (Kotlin pre-commit 분기, `verify-deploy.md` slash command) 을 그대로 따라 backend `.py` 와 naming audit 으로 확장.

**Tech Stack:** Python 3.12 / FastAPI / ruff `D`+`N`+`E`+`F`+`I`+`UP` / mypy strict / Pydantic v2 / pre-commit bash hook / `.claude/commands/` slash command

**참고:**
- Design: `docs/plans/2026-06-02-naming-convention-audit-design.md`
- Branch: `chore/naming-convention-audit` (Task 0 에서 생성)

**중요 원칙:**
- Plan 의 verification gate = ruff D 잔존 카운트. red → green → commit 패턴은 docstring 위반 → 작성 → ruff 통과로 매핑.
- 모든 commit 은 `chore/naming-convention-audit` 브랜치, 최종 PR 1개.
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시. Backend venv ruff 는 PowerShell 에서 직접 실행, hook scripts 는 bash.
- design doc §10 의 9-step 구현 순서 그대로 — pre-commit hook 활성화 (Task 6) 는 docstring 추가 (Task 2~5) **이후** 에 (닭과 달걀 회피).

**Task 순서:**

```
Task 0   branch + 환경 baseline (~5분)
Task 1   pyproject ruff D 룰 + per-file-ignore (~10분)
Task 2   docstring batch: routers/ (~25분)
Task 3   docstring batch: services/ (~25분)
Task 4   docstring batch: repositories/ (~35분)
Task 5   docstring batch: 기타 + minor formatting fix (~20분)
Task 6   pre-commit hook .py 분기 (~15분)
Task 7   /naming-audit slash command (~20분)
Task 8   api-endpoint.md 전면 재작성 (~25분)
Task 9   docs/conventions/naming.md SSoT (~25분)
Task 10  CLAUDE.md link 추가 (~5분)
Task 11  PR template 보강 (~10분)
Task 12  전체 회귀 검증 + 자동화 인프라 작동 확인 (~20분)
Task 13  README 자동 갱신 + push + PR 생성 (~10분)
총 ~4 시간
```

---

## Phase 1: 작업

### Task 0: Branch + 환경 baseline

**Files:**
- Modify: 없음 (조회만)

**Step 0.1: git status clean 확인** (pwsh)

```pwsh
git status --short
```
Expected: 빈 출력 (uncommitted 변경 없음). 있으면 stash/commit 먼저.

**Step 0.2: branch 생성** (pwsh)

```pwsh
git checkout -b chore/naming-convention-audit
```
Expected: `Switched to a new branch 'chore/naming-convention-audit'`.

**Step 0.3: backend venv ruff 가용성 확인** (pwsh)

```pwsh
backend\.venv\Scripts\ruff.exe --version
```
Expected: `ruff X.Y.Z` 출력. 실패 시 `cd backend && python -m venv .venv && .venv\Scripts\pip install -r requirements-dev.txt` 로 setup.

**Step 0.4: ruff D baseline 측정 (예상 80건 — per-file-ignore 적용 전 시점)** (pwsh)

```pwsh
cd backend
.venv\Scripts\ruff.exe check --select D --ignore D100,D104,D107,D203,D213 --statistics app/
```
Expected: 80 errors. 카테고리별 분포 (D101=26, D102=25, D103=22, D205=1, D209=1, D400=2, D403=1, D415=2). 이 시점에 pyproject 의 per-file-ignore 미설정 → schemas D101 7 + models D101 3 + main.py 4 = 14 가 80 안에 포함되어 있음. **Task 1 후 per-file-ignore 적용 시 66 으로 줄어듦** (66 = 80 - 14).

다르면 design doc 의 80건 baseline 이 drift — 해당 batch task 의 예상 카운트 갱신 필요.

**Step 0.5: 환경 확인 commit (no-op)**

이 task 는 조회만이라 commit 없음. 다음 task 부터 시작.

---

### Task 1: `backend/pyproject.toml` 에 ruff D 룰 + per-file-ignore

**Files:**
- Modify: `backend/pyproject.toml` (전체 ~25줄 → ~50줄)

**Step 1.1: pyproject 변경** (pwsh — Edit tool)

기존 파일:
```toml
[tool.ruff]
line-length = 120
target-version = "py312"

[tool.ruff.lint]
select = ["E", "F", "I", "N", "UP"]
# N818: 예외 클래스 이름은 "Error" 접미사를 권장하지만,
# 설계서가 AppException/NotFoundException 등으로 명명하므로 유지
ignore = ["N818"]

[tool.mypy]
python_version = "3.12"
strict = true
plugins = ["pydantic.mypy"]
```

변경 후:
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
    "D203", "D213", # convention="pep257" 와 redundant 하지만 의도 명확화 위해 명시
]

[tool.ruff.lint.pydocstyle]
convention = "pep257"

[tool.ruff.lint.per-file-ignores]
"tests/**" = ["D"]              # test 이름이 spec — 48건 제외
"alembic/**" = ["D"]             # auto-generated — 16건 제외
"app/main.py" = ["D"]            # FastAPI 앱 인스턴스
"app/schemas/**" = ["D101"]      # Pydantic schema — Field(description=) 대체, 7건 제외
"app/models/**" = ["D101"]       # SQLAlchemy ORM table — 3건 제외

[tool.mypy]
python_version = "3.12"
strict = true
plugins = ["pydantic.mypy"]
```

(pytest 섹션 그대로 유지.)

**Step 1.2: 측정 — ruff D 위반 잔존 확인** (pwsh)

```pwsh
cd backend
.venv\Scripts\ruff.exe check --statistics app/
```
Expected: **64 D errors** (per-file-ignore 적용 → schemas D101 7 + models D101 3 + main.py D 전체 6 = 16 제외, 80 - 16 = 64). main.py D 전체 6건에는 D415 2건이 포함되어 baseline 의 D415 가 0 으로 사라짐. N818 은 0 (글로벌 ignore). warning 2줄 (`D203/D211 incompatible`, `D212/D213 incompatible`) 출력은 무해 (의도 명확화 trade-off, design doc §8 잔여 리스크 기록됨).

**Step 1.3: tests/alembic 에서 D 위반 0건 확인 (per-file-ignore 작동)** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --statistics tests/ alembic/
```
Expected: 0 D errors (각 48, 16건이 per-file-ignore 로 모두 제외).

**Step 1.4: commit** (pwsh)

```pwsh
git add backend/pyproject.toml
git commit -m "chore(backend): enable ruff D rule for PEP 257 docstring (audit baseline)"
```

---

### Task 2: Docstring batch — `backend/app/routers/` (15건)

**Files:**
- Modify: `backend/app/routers/*.py` (badge.py, goal.py, profile.py, weekly_plan.py 등)

**Step 2.1: routers 의 D 위반 list 확보** (pwsh)

```pwsh
cd backend
.venv\Scripts\ruff.exe check --select D101,D102,D103 --output-format=concise app/routers/
```
Expected: 15건 출력 — 각 line `app/routers/<file>.py:<line>:<col>: D10[123] Missing docstring in public <X>`. 함수 이름 + line 번호 list 확보. **주의**: `--select D` 단독 사용 금지 — config 의 D100/D104 ignore 가 override 되어 모듈/패키지 헤더가 잘못 위반으로 잡힘 (D2 결정 위반 위험).

**Step 2.2: 각 router 함수에 docstring 추가**

작성 정책 (design doc §5.2 + §5.12):
- 1-2 줄 한국어 요약 + 필요시 Args/Returns/Raises 섹션.
- behavioral "why" 중심. type 정보 중복 금지 (mypy + response_model 이 cover).
- 한/영 mixed OK — 도메인 용어 (Supabase, JWT, Container App 등) 는 영문 유지.

예시 1 (router 함수):
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

예시 2 (PATCH endpoint):
```python
@router.patch("/weekly-plan/complete", response_model=WeeklyPlanResponse)
async def mark_complete(
    body: WeeklyPlanCompleteRequest,
    user_id: UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse:
    """주간 plan 의 특정 운동 완료 표시. 모든 항목 완료 시 badge 자동 부여."""
    ...
```

각 함수마다 1-2줄 docstring 을 추가. 어떤 단어 선택할지는 함수 의도를 코드에서 읽어 결정.

**Step 2.3: 측정 — routers/ D 위반 0건 확인** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --statistics app/routers/
```
Expected: 0 errors. config-driven 측정 (D100/D104 자동 ignore 적용).

**Step 2.4: 회귀 — 전체 ruff 잔존 카운트 측정** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --statistics app/
```
Expected: 49 errors (64 - 15). routers 외 디렉토리만 남음.

**Step 2.5: commit** (pwsh)

```pwsh
git add backend/app/routers/
git commit -m "docs(backend): add PEP 257 docstrings to routers (15 functions)"
```

---

### Task 3: Docstring batch — `backend/app/services/` (14건)

**Files:**
- Modify: `backend/app/services/*.py` (account_service.py, badge_service.py, goal_service.py, profile_service.py, statistics_service.py, weekly_plan_service.py)

**Step 3.1: services D 위반 list 확보** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --select D101,D102,D103 --output-format=concise app/services/
```
Expected: 14건 (class + public method 혼합). **주의**: `--select D` 단독 사용 금지 (D100/D104 가 잘못 잡힘 — Task 2 에서 발견).

**Step 3.2: 각 service 클래스 + public method 에 docstring 추가**

예시 1 (Service 클래스):
```python
class GoalService:
    """사용자 목표 (체중 / 체지방) 의 CRUD + 달성 판정."""

    def __init__(self, db: AsyncSession):
        self.repo = GoalRepository(db)
```

예시 2 (public method):
```python
async def upsert_goal(self, user_id: UUID, goal: GoalUpsert) -> GoalResponse:
    """기존 goal 이 있으면 갱신, 없으면 생성. 달성 시 badge 자동 부여."""
    ...
```

`__init__` 는 D107 ignore 라 docstring 불요. private method (`_` prefix) 도 D102 미적용.

**Step 3.3: 측정 — services/ D 위반 0건** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --statistics app/services/
```
Expected: 0 errors. config-driven.

**Step 3.4: 회귀 — 전체 잔존 카운트** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --statistics app/
```
Expected: 35 errors (49 - 14). **실측 (Task 3 commit `91b39bb` 후): 32 errors** — implementer 가 services 안 기존 docstring 의 minor formatting (D205 1 + D209 1 + D400 1) 도 동시 fix 함 (Task 5 scope 와 overlap, net positive). 후속 회귀 카운트 chain: Task 4 후 11 (32 - 21), Task 5 후 0.

**Step 3.5: commit** (pwsh)

```pwsh
git add backend/app/services/
git commit -m "docs(backend): add PEP 257 docstrings to services (14 classes/methods)"
```

---

### Task 4: Docstring batch — `backend/app/repositories/` (21건)

**Files:**
- Modify: `backend/app/repositories/*.py` (badge_repo.py, goal_repo.py, profile_history_repo.py, profile_repo.py, weekly_plan_repo.py)

**Step 4.1: repositories D 위반 list 확보** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --select D101,D102,D103 --output-format=concise app/repositories/
```
Expected: 21건. **주의**: `--select D` 단독 사용 금지.

**Step 4.2: 각 repository 클래스 + public method 에 docstring 추가**

예시 1 (Repository 클래스):
```python
class WeeklyPlanRepository:
    """주간 운동 plan 의 DB 접근. user_id + week_start 복합 키."""

    def __init__(self, db: AsyncSession):
        self.db = db
```

예시 2 (public method):
```python
async def get_plan(self, user_id: UUID, week_start: date) -> WeeklyPlan | None:
    """userId + weekStart 로 plan 1건 조회. v0.1 INC: userId 필터링 누락 시 다른 사용자 데이터 노출 위험."""
    ...
```

historical context (INC 트리거된 함수) 는 그 history 를 짧게 docstring 에 reference — code 의 hidden constraint 명시.

**Step 4.3: 측정 — repositories/ D 위반 0건** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --statistics app/repositories/
```
Expected: 0 errors. config-driven.

**Step 4.4: 회귀 — 전체 잔존 카운트** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --statistics app/
```
Expected: **11 errors (32 - 21)** — Task 3 의 services 부수 fix 3건 반영. 남은 11 = 기타 9 (exceptions+config+database+dependencies) + minor 2 (D400 1 + D403 1 — D205/D209 1건씩은 Task 3 에서 처리됨, D415 는 main.py ignore).

**Step 4.5: commit** (pwsh)

```pwsh
git add backend/app/repositories/
git commit -m "docs(backend): add PEP 257 docstrings to repositories (21 classes/methods)"
```

---

### Task 5: Docstring batch — 기타 + minor formatting fix

**Files:**
- Modify: `backend/app/exceptions.py` (4건), `backend/app/config.py` (2건), `backend/app/database.py` (2건), `backend/app/dependencies.py` (1건)
- Minor formatting fix: D205 (1) + D400 (2) + D415 (2) — manual, D209 (1) + D403 (1) — auto-fix

**Step 5.1: 기타 디렉토리 D 위반 list 확보** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --select D101,D102,D103 --output-format=concise app/exceptions.py app/config.py app/database.py app/dependencies.py
```
Expected: 9건 (exceptions 4 + config 2 + database 2 + dependencies 1).

**Step 5.2: exceptions.py 의 4개 클래스에 docstring 추가**

예시 (exceptions.py):
```python
class AppException(Exception):
    """프로젝트 전역 base exception. detail + status_code 보유."""

    def __init__(self, detail: str, status_code: int = 500):
        ...

class NotFoundException(AppException):
    """리소스 미존재 (404). 라우터가 자동으로 404 응답으로 변환."""

    def __init__(self, detail: str = "Not Found"):
        super().__init__(detail, status_code=404)

class ConflictException(AppException):
    """리소스 충돌 (409). 중복 생성 / version mismatch 등."""
    ...

class BadRequestException(AppException):
    """잘못된 요청 (400). 입력 검증 실패 등 client-side error."""
    ...
```

**Step 5.3: config.py / database.py / dependencies.py 의 잔여 6건 docstring 추가**

각 함수/클래스의 의도를 코드에서 읽고 1-2줄 docstring. 예시 (database.py):
```python
async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """FastAPI dependency — request-scoped DB session 제공. UoW 패턴."""
    ...
```

**Step 5.4: minor formatting auto-fix 실행** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --fix app/
```
Expected: **0 fixed** (D209 1건은 Task 3 services minor fix 에서, D403 1건은 Task 4 repositories 작업 중 자가 fix 됨 — 모두 이미 해결됨). 명령 실행 자체는 안전 (no-op).

**Step 5.5: minor formatting manual fix — D400**

```pwsh
.venv\Scripts\ruff.exe check --select D205,D400 --output-format=concise app/
```
Expected: **1건 (D400 1)** — Task 3 의 services 부수 fix 가 D205 1 + D400 1 을 이미 처리. 남은 D400 1건의 docstring 을 manual 로 수정:
- D400: 첫 줄 끝에 마침표 추가

**Step 5.6: 측정 — app/ D 위반 0건 (최종)** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check --select D --statistics app/
```
Expected: 0 errors. **이 시점에 backend 전체가 PEP 257 통과.**

**Step 5.7: 회귀 — ruff (기존 룰 회귀 0)** (pwsh)

```pwsh
.venv\Scripts\ruff.exe check app/ tests/
```
Expected: **All checks passed** (E/F/I/N/UP 회귀 0 + D 0). CI (`backend.yml` ruff step) 와 동일 scope.

**참고**: `alembic/` 디렉토리에는 본 PR 머지 전부터 존재하는 pre-existing 위반 16건 (I001 / UP007 / UP035 — typing union 모던 syntax) 있음. backend.yml 의 ruff step 이 alembic 를 scope 에서 제외하므로 본 PR CI fail 위험 없음. 별도 chore PR 에서 `ruff check --fix alembic/` 으로 일괄 처리 — 본 plan 외 (잔여 리스크 §섹션 참조).

**Step 5.8: mypy 회귀 0** (pwsh)

```pwsh
.venv\Scripts\mypy app/
```
Expected: Success: no issues found in N source files (docstring 추가가 type signature 변경 0 — mypy 영향 0 기대).

**Step 5.9: pytest 회귀 0** (pwsh)

```pwsh
.venv\Scripts\pytest tests/ -v --cov=app
```
Expected: 41 passed in Xs. coverage 82%+ 유지.

**Step 5.10: commit** (pwsh)

```pwsh
git add backend/app/
git commit -m "docs(backend): add PEP 257 docstrings to remaining modules + minor formatting fix"
```

---

### Task 6: Pre-commit hook `.py` 분기 추가

**Files:**
- Modify: `.githooks/pre-commit` (기존 47줄 → ~70줄)

**Step 6.1: hook 의 docs/plans 블록 직후에 새 블록 추가** (Edit tool)

기존 코드 (line 32~45 직후) 끝에 다음 추가:
```bash

# ---------- 3) Backend Python ----------
CHANGED_PY=$(git diff --cached --name-only --diff-filter=ACM \
    | grep -E '^backend/.*\.py$' || true)

if [ -n "$CHANGED_PY" ]; then
    echo "[pre-commit] backend/*.py changes detected → ruff check"
    cd "$REPO_ROOT/backend"

    # venv ruff 우선, 없으면 PATH ruff fallback (CI 와 동일 룰셋, pyproject 자동 로딩)
    RUFF=".venv/Scripts/ruff.exe"
    [ -x "$RUFF" ] || RUFF="ruff"

    # 변경된 파일만 검사 (전체 app/ 스캔보다 빠름, file-local 룰만 적용되므로 충분)
    echo "$CHANGED_PY" | sed 's|^backend/||' | xargs "$RUFF" check
fi
```

**Step 6.2: 가짜 violation 으로 차단 확인** (pwsh)

테스트용 dummy file 작성:
```pwsh
@'
"""Dummy file for hook test."""

def function_without_docstring():
    return 1
'@ | Out-File -FilePath backend\app\_hook_test.py -Encoding utf8
git add backend\app\_hook_test.py
git commit -m "test: should fail"
```
Expected: pre-commit 출력 `[pre-commit] backend/*.py changes detected → ruff check` + ruff 가 `D103 Missing docstring in public function` 출력 + commit 차단 (exit code 1).

**Step 6.3: dummy file revert** (pwsh)

```pwsh
git reset HEAD backend\app\_hook_test.py
Remove-Item backend\app\_hook_test.py
git status --short  # clean 확인
```

**Step 6.4: 정상 commit 통과 확인** (pwsh)

hook 자체 변경은 `.py` 아니므로 hook 분기 조건 안 걸림 — git commit 정상 진행:
```pwsh
git add .githooks/pre-commit
git commit -m "chore(hooks): add backend .py branch to pre-commit (ruff check)"
```
Expected: pre-commit 출력에 `[pre-commit] backend/*.py changes detected` 없음 (hook 자체는 .sh, .py 아님). commit 성공.

---

### Task 7: `/naming-audit` slash command 신규

**Files:**
- Create: `.claude/commands/naming-audit.md`

**Step 7.1: slash command 파일 작성** (Write tool — 신규 파일)

```markdown
---
description: Naming convention audit re-run (ruff D + N + detekt naming + Azure CAF 표 sync). 분기당 1~2회 권장.
allowed-tools: Bash, Read, Grep, Glob, Edit, mcp__azure__group_resource_list, mcp__azure__group_list
argument-hint: [--update-doc] (audit 결과로 design doc §3 갱신 patch 제안 — auto-commit 안 함)
---

본 프로젝트의 명명/문서화 컨벤션 준수도를 재측정합니다. 시간 경과에 따른 drift 점검 + 신규 PR 머지 후 baseline 갱신 용도. 5단계 모두 시도, stop-on-failure 아님 — verify-deploy.md 와 동일 정책.

## 검증 단계

### 1. Python PEP 257 violation 측정
```bash
cd backend && .venv/Scripts/ruff.exe check --select D --statistics app/ tests/ alembic/
```
기대: 0 errors (본 PR 머지 후 baseline). 잔존 시 카테고리별 list + 파일 경로 보고.

### 2. Python PEP 8 naming (N) 측정
```bash
cd backend && .venv/Scripts/ruff.exe check --select N --statistics app/
```
기대: 0 errors (N818 은 글로벌 ignore 라 count 0).

### 3. Kotlin detekt naming 측정
```bash
./gradlew :app:detektDebug -q
```
build/reports/detekt/detekt.html 의 naming 카테고리 확인. baseline 외 신규 위반 0 기대. fail 시 `baseline.xml` vs `baseline-debug.xml` drift 가 원인 가능 — `detekt-baseline-drift.md` 메모리 참조.

### 4. Azure CAF 표 sync 안내
`docs/plans/2026-06-02-naming-convention-audit-design.md` §3.2 표 vs 실측 Azure 리소스 명 비교:
```bash
az resource list -g apps -o table
az containerapp env list -g apps -o tsv
```
또는 Azure MCP (tenant 명시 필수, [[claude-code-mcp-install-gotchas]]):
- `mcp__azure__group_list --tenant <TENANT_ID>`
- `mcp__azure__group_resource_list --tenant <TENANT_ID> --resource-group apps`

신규 리소스 발견 시 §3.2 표에 1행 추가 + CAF 권장 매핑 작성.

### 5. 결과 보고
- 1~3 의 위반 개수 표로 정리.
- 4 의 drift (신규/누락 리소스) list.
- `--update-doc` flag 시: design doc §3.2 (Azure 표) + §3.4 (자동화 인프라 채널 현재 상태) 갱신 patch 를 **제안**. **자동 commit 안 함** — CLAUDE.md "NEVER commit unless explicitly asked" 룰. 사용자가 검토 후 `commit` 명시 시에만 진행.

예상 소요: ruff D+N (~2s) + detekt (~30~60s, gradle daemon hot) + Azure MCP 호출 (~5~10s) — 총 ~1~2 분.
```

**Step 7.2: 작동 확인 — 새 세션에서 호출**

이 단계는 Claude Code 새 세션 또는 현 세션의 다음 turn 에서 `/naming-audit` 호출. 본 plan 실행 중에는 시간 소요만 측정.

알아둘 점: slash command 작동 검증은 PR 머지 + CLAUDE.md link 추가 후 새 Claude Code 세션에서. **본 task 에서는 file 작성 자체만 commit**.

**Step 7.3: commit** (pwsh)

```pwsh
git add .claude/commands/naming-audit.md
git commit -m "chore(claude): add /naming-audit slash command (PEP 257 + detekt + Azure CAF drift)"
```

---

### Task 8: `scripts/prompts/api-endpoint.md` 전면 재작성

**Files:**
- Modify (전면 재작성): `scripts/prompts/api-endpoint.md` (기존 41줄 → ~85줄)

**Step 8.1: 기존 파일 백업 (mental note)** — git 추적 중이라 별도 백업 불요. revert 가능.

**Step 8.2: 파일 전체 교체** (Write tool — 기존 내용 모두 대체)

```markdown
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
```

**Step 8.3: legacy 패턴 0건 잔존 확인** (pwsh)

```pwsh
Select-String -Path scripts/prompts/api-endpoint.md -Pattern "EundunApi\.kt|backend/src/main/kotlin"
```
Expected: 빈 출력 (legacy reference 0건). 단 `EundunApi.kt 수동 추가 금지` 같이 negative reference 1건 OK — context 확인.

**Step 8.4: 새 keyword 포함 확인** (pwsh)

```pwsh
Select-String -Path scripts/prompts/api-endpoint.md -Pattern "PEP 257|operation_id|sync-openapi"
```
Expected: 모두 hit.

**Step 8.5: commit** (pwsh)

```pwsh
git add scripts/prompts/api-endpoint.md
git commit -m "docs(prompts): rewrite api-endpoint.md (Ktor legacy → FastAPI+openapi-generator+PEP 257)"
```

---

### Task 9: `docs/conventions/naming.md` SSoT 신규

**Files:**
- Create: `docs/conventions/naming.md` (신규 폴더 + 신규 파일)

**Step 9.1: 폴더 + 파일 작성** (Write tool — 신규)

```markdown
# 명명/문서화 컨벤션 (SSoT)

> 본 문서는 single source of truth.
> - 변경 시 `docs/plans/2026-06-02-naming-convention-audit-design.md` 의 §3 + §4 와 동기.
> - 신규 개발자: 본 문서만 읽으면 컨벤션 파악 가능. 의사결정 배경은 design doc 참조.

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
- **기존 리소스 rename 금지**: CAF 공식 "Most Azure resource names can't be changed after creation". v0.1.7 Internal Testing 활성 + Container App URL 이 Android `BACKEND_URL` baked → 다운타임 + tester 일제 갱신 비용 큼.
- **신규 리소스에만 적용**.

## 4. 신규 코드 추가 시 체크리스트

- [ ] Backend public class/function 에 docstring (1-2줄, behavioral "why")
- [ ] Router `operation_id` 가 Android 함수명과 일치
- [ ] Query param 에 `alias="camelCase"` 명시
- [ ] `bash scripts/sync-openapi.sh` 실행 + `backend/openapi.json` 같은 PR 에 커밋
- [ ] Android Repository 가 generated API 만 사용 (수동 `EundunApi.kt` 추가 금지 — deprecated/제거됨)
- [ ] Kotlin: PascalCase 클래스 / camelCase 함수 / UPPER_SNAKE const (detekt+ktlint 자동 차단)

## 5. 신규 Azure 리소스 추가 시 체크리스트

- [ ] CAF abbreviation 사용 (`ca-`, `cae-`, `cr`, `psql-`, `rg-`)
- [ ] workload 명 = `eundunhealth` (기존 명명 일관성)
- [ ] env suffix (`-prod`, `-dev`) 명시
- [ ] ACR/Storage 처럼 alphanumeric only 리소스는 하이픈 제거 + 압축형
- [ ] `docs/plans/2026-06-02-naming-convention-audit-design.md` §3.2 표에 신규 리소스 1행 추가

## 6. 참고

- **의사결정 기록 (배경 + audit 데이터)**: `docs/plans/2026-06-02-naming-convention-audit-design.md`
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
```

**Step 9.2: 파일 존재 + 내용 sanity 확인** (pwsh)

```pwsh
Test-Path docs/conventions/naming.md
(Get-Content docs/conventions/naming.md | Measure-Object -Line).Lines
```
Expected: `True` + ~75 줄.

**Step 9.3: commit** (pwsh)

```pwsh
git add docs/conventions/naming.md
git commit -m "docs(conventions): add naming.md SSoT (5종 공식 가이드 + D1~D10 결정)"
```

---

### Task 10: CLAUDE.md link 추가

**Files:**
- Modify: `CLAUDE.md` (2줄 추가 — 슬래시 명령 + Documentation 섹션)

**Step 10.1: 슬래시 명령 섹션에 `/naming-audit` 추가** (Edit tool)

기존 (`### Claude Code 슬래시 명령` 섹션):
```markdown
- `/verify-deploy <inc-id>` — MCP (Sentry/Azure) 로 Phase 5 운영 검증 자동화 (alembic head
  + 스키마 컬럼 + Sentry 신규 issue). INC 별 검증 1-command. 자세한 내용:
  `docs/plans/2026-05-28-mcp-integration-setup-design.md` §3.3.
```

직후에 1 entry 추가:
```markdown
- `/naming-audit` — 명명/문서화 컨벤션 drift 점검 (ruff D + detekt naming + Azure CAF 표 sync).
  자세한 룰: `docs/conventions/naming.md`. 결정 기록:
  `docs/plans/2026-06-02-naming-convention-audit-design.md`. 분기당 1~2회 권장.
```

**Step 10.2: Documentation 섹션에 SSoT link 추가** (Edit tool)

기존 `## Documentation` 섹션 마지막 (또는 `@docs/ops/...` 그룹 직후) 에 1줄 추가:
```markdown
- `@docs/conventions/naming.md` — 명명/문서화 SSoT (5종 공식 가이드 + 본 프로젝트 결정 D1~D10)
```

**Step 10.3: link 존재 확인** (pwsh)

```pwsh
Select-String -Path CLAUDE.md -Pattern "naming-audit|docs/conventions/naming.md"
```
Expected: 2 hits (슬래시 명령 entry + Documentation entry).

**Step 10.4: commit** (pwsh)

```pwsh
git add CLAUDE.md
git commit -m "docs(claude): link naming SSoT + /naming-audit slash command"
```

---

### Task 11: PR template 보강

**Files:**
- Modify: `.github/pull_request_template.md` (Backend 테스트 1줄 → 3줄 + 새 "Azure 신규 리소스" 섹션)

**Step 11.1: 테스트 섹션 backend 라인 교체** (Edit tool)

기존:
```markdown
- [ ] `cd backend && pytest tests/ -v` 통과
```

교체:
```markdown
- [ ] `cd backend && .venv/Scripts/ruff.exe check app/ tests/` 통과 (PEP 8/257/import order)
- [ ] `cd backend && .venv/Scripts/mypy app/` 통과 (PEP 484/526 type hints)
- [ ] `cd backend && .venv/Scripts/pytest tests/ -v` 통과
```

**Step 11.2: "Destructive 명령" 섹션 뒤에 새 섹션 추가** (Edit tool)

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

**Step 11.3: 렌더링 확인** (pwsh)

```pwsh
Select-String -Path .github/pull_request_template.md -Pattern "ruff check|mypy|Azure 신규 리소스"
```
Expected: 3+ hits.

**Step 11.4: commit** (pwsh)

```pwsh
git add .github/pull_request_template.md
git commit -m "chore(pr-template): add ruff/mypy + Azure CAF naming checklist"
```

---

## Phase 2: 검증 + PR

### Task 12: 전체 회귀 검증 + 자동화 인프라 작동 확인

**Files:**
- Modify: 없음 (검증만)

**Step 12.1: ruff 전체 통과** (pwsh)

```pwsh
cd backend
.venv\Scripts\ruff.exe check app/ tests/
```
Expected: `All checks passed!`. D + E + F + I + N + UP 모두 0. CI (`backend.yml`) 와 동일 scope — `alembic/` 의 pre-existing 16건은 본 PR 외 (Task 5 Step 5.7 노트 참조).

**Step 12.2: ruff format 회귀 0** (pwsh)

```pwsh
.venv\Scripts\ruff.exe format --check app/
```
Expected: format 변경 필요 파일 0. fail 시 docstring 작성 과정에서 줄바꿈/들여쓰기 변경된 곳 확인.

**Step 12.3: mypy 회귀 0** (pwsh)

```pwsh
.venv\Scripts\mypy app/
```
Expected: `Success: no issues found in N source files`.

**Step 12.4: pytest 41/41 PASS + coverage 82%+** (pwsh)

```pwsh
.venv\Scripts\pytest tests/ -v --cov=app
```
Expected: `41 passed`. coverage 82%+.

**Step 12.5: OpenAPI sync** (bash)

```bash
cd /c/programming/apps/eundunHealth
bash scripts/sync-openapi.sh
git diff backend/openapi.json | head -100
```
Expected: docstring 추가가 `description` 필드로 노출되어 router endpoint 의 `description` 만 변경. schema/path/operationId 변경 0. 변경 사이즈 큼 (15개 endpoint 의 description 모두 새로 들어감) — 정상.

**Step 12.6: backend/openapi.json 변경 commit** (pwsh)

```pwsh
git add backend/openapi.json
git commit -m "chore(openapi): sync router docstrings to openapi description"
```

**Step 12.7: docker compose smoke** (bash)

```bash
cd /c/programming/apps/eundunHealth/backend
docker compose up -d --build
sleep 10
curl -fsS http://localhost:8080/health
docker compose logs api 2>&1 | grep -E "alembic upgrade head|Uvicorn running"
docker compose down
```
Expected: `{"status":"ok"}` (200) + log 에 `alembic upgrade head` (no-op, head=fa3915deab2f) + `Uvicorn running` 둘 다. fail 시 룰 6/7 가드 (CORS / lifespan / alembic) 회귀 가능 — design doc §8 잔여 리스크 참조.

**Step 12.8: pre-commit hook `.py` 분기 작동 확인 (Task 6 재검증)** (pwsh)

```pwsh
@'
def hook_test_func():
    return 1
'@ | Out-File -FilePath backend\app\_hook_test2.py -Encoding utf8
git add backend\app\_hook_test2.py
git commit -m "test: should fail (no docstring)"
# Expected: hook 차단, exit 1
git reset HEAD backend\app\_hook_test2.py
Remove-Item backend\app\_hook_test2.py
git status --short
```
Expected: commit 차단 (`D100 / D103` 출력) → revert → clean. 단 dummy file 의 D100 module header 가 글로벌 ignore 라 안 잡힘 — D103 만 잡혀야 정상.

**Step 12.9: slash command file 존재 확인** (pwsh)

```pwsh
Test-Path .claude/commands/naming-audit.md
Get-Content .claude/commands/naming-audit.md -TotalCount 5
```
Expected: True + frontmatter 첫 5줄 출력 (`description`, `allowed-tools`, `argument-hint` 가 포함된).

**Step 12.10: api-endpoint.md legacy 0건 잔존** (pwsh)

```pwsh
Select-String -Path scripts/prompts/api-endpoint.md -Pattern "EundunApi\.kt|backend/src/main/kotlin" -CaseSensitive
```
Expected: `EundunApi.kt 수동 추가 금지` 같은 negative reference 1건만 출력 (deprecation 경고 용도). positive reference (수정 안내) 0건.

**Step 12.11: SSoT + CLAUDE.md link 확인** (pwsh)

```pwsh
Test-Path docs/conventions/naming.md
Select-String -Path CLAUDE.md -Pattern "naming-audit|docs/conventions/naming.md"
```
Expected: True + 2 hits.

**Step 12.12: docstring 자연어 spot-check (5건 sampling)**

routers/services/repositories 중 무작위 5건 — docstring 이 1-2줄 한국어 + behavioral "why" 중심인지 manual 검토. boilerplate 있으면 revise + amend commit (`git commit --amend --no-edit`) — 단 단일 batch commit 의 amend 만 허용, cross-task amend 금지.

이 task 에는 commit 없음 (검증만).

---

### Task 13: design + plan doc + README 자동 갱신 + push + PR 생성

**Files:**
- New: `docs/plans/2026-06-02-naming-convention-audit-design.md` (이미 작성됨, untracked)
- New: `docs/plans/2026-06-02-naming-convention-audit-plan.md` (본 파일, untracked)
- Modify: `docs/plans/README.md` (gen-plans-index.sh 자동 갱신)

**Step 13.1: 두 doc 파일 staged 상태 확인** (pwsh)

```pwsh
git status --short docs/plans/
```
Expected: 2개 `??` (untracked) — design + plan.

**Step 13.2: pre-commit hook 의 `docs/plans/*.md` 분기 작동 — README 자동 갱신**

다음 step 의 commit 시점에 hook 이 `bash scripts/gen-plans-index.sh` 실행 + `docs/plans/README.md` auto-stage. v0.1.7 lesson 회피 (Task 1 누락 시 CI check-index job fail).

**Step 13.3: docs commit** (pwsh)

```pwsh
git add docs/plans/2026-06-02-naming-convention-audit-design.md docs/plans/2026-06-02-naming-convention-audit-plan.md
git commit -m "docs(plans): naming convention audit design + plan"
```
Expected: pre-commit hook 출력 `[pre-commit] docs/plans/ changes detected → regenerating README.md` + README 자동 추가됨 + commit 성공.

**Step 13.4: README 갱신 확인** (pwsh)

```pwsh
Select-String -Path docs/plans/README.md -Pattern "2026-06-02-naming-convention"
```
Expected: 2 hits (design + plan 페어 entry).

**Step 13.5: push** (pwsh)

```pwsh
git push -u origin chore/naming-convention-audit
```
Expected: push 성공 + branch tracking setup.

**Step 13.6: PR 생성** (bash)

```bash
gh pr create --title "chore: naming convention audit + PEP 257 enforce + automation infra" --body "$(cat <<'EOF'
## Summary

- 5종 공식 명명/문서화 가이드 (JetBrains Kotlin / Android Style / PEP 8/257/484/526 / Azure CAF) audit + 우선순위 결정 + 선별 enforce + 자동화 인프라 5종 추가
- Backend public API ~59건 PEP 257 docstring + minor formatting fix 5건
- 자동화 인프라: pre-commit `.py` 분기 / PR template 보강 / `/naming-audit` slash command / `api-endpoint.md` Ktor legacy → FastAPI 재작성 / `docs/conventions/naming.md` SSoT + CLAUDE.md link

## Test plan

- [ ] CI ruff/mypy/pytest 모두 green
- [ ] `backend/openapi.json` drift detection step 통과 (description 변경만, schema 변경 0)
- [ ] 로컬 docker compose smoke: `/health` 200 + alembic upgrade head + Uvicorn running
- [ ] pre-commit hook `.py` 분기: 가짜 violation commit 차단 확인
- [ ] `gh pr view` 에서 PR template 의 ruff/mypy/Azure 섹션 렌더링 확인 (본 PR 이 첫 사용 사례)
- [ ] docstring 자연어 reviewer 1-round 검토 (sampling)

## 결정 + 산출물

자세한 내용: `docs/plans/2026-06-02-naming-convention-audit-design.md`
- D1~D8: audit + enforce 결정
- D9: 자동화 인프라 5종 + CLAUDE.md link
- D10: skill 신설 거부 (PEP 257 = ruff 자동 차단 / CAF = IaC 미도입 + 빈도 ↓)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
Expected: PR URL 출력. design doc frontmatter `pr: <URL>` 갱신 + plan doc 동일 갱신은 별도 follow-up commit (PR 머지 전 또는 후 — 컨벤션은 PR 머지 후 ledger absorb 시 정리).

---

## Phase 3 (선택): 머지 후 운영 검증

본 PR 은 인프라 변경 0 / Container Apps deploy 무관 / DB schema 변경 0 — Phase 5 운영 검증 (`/verify-deploy`) 비해당. 머지 후 검증:

- GitHub Actions `backend.yml` CI: ruff/mypy/pytest 모두 green (`gh run watch`).
- 새 Claude Code 세션 시작: CLAUDE.md 의 `@docs/conventions/naming.md` link 가 자동 로딩되는지 (세션 SystemContext 에 SSoT 본문 포함 확인).
- 별도 dummy PR 으로 PR template 렌더링 검증 — 본 PR 머지 후 다음 PR 자체가 검증 환경.

---

## 잔여 리스크 / 후속 작업

- **CI 의 ruff D 회귀 catch**: `backend.yml` ruff step 이 `pyproject.toml` 자동 로딩 → D 룰 추가 자동 반영. 단 CI 환경 venv 의 ruff 버전이 로컬과 다르면 룰 deltas 가능 — `requirements-dev.txt` 의 ruff pin 확인 권장 (별도 PR 가능).
- **docstring 한국어 일관성**: 본 PR 후 새 public API 추가 시 자동 차단 (ruff D) 만 됨 — 한국어 자연스러움 / behavioral "why" 정책은 reviewer 책임. SSoT (`docs/conventions/naming.md` §2 작성 정책) link 가 PR template 에서 안내됨.
- **BooleanPropertyNaming gap 미보강** (D3): 향후 별도 chore PR 에서 detekt type resolution 활성화 검토. v0.2 안정화 후가 적정 (현 단계는 baseline-debug.xml 의 기존 위반과 충돌 우려).
- **Azure rename 검토 시점** (D8): v1.0 안정화 후 별도 chore PR. 마이그레이션 시 design doc §3.2 audit 표를 baseline 으로 활용.
- **`new-screen.md` / `bug-fix.md` naming 보강** (D9 out-of-scope): `api-endpoint.md` 가 PEP 257 enforce 핫스팟이라 본 PR 1순위. 나머지 2개는 별도 chore PR — `new-screen.md` 는 Compose @Composable PascalCase 강조 + `AuthErrorBanner` 룰 8 reference 보강 candidate.
- **ruff 버전 업 시 D 룰셋 변경**: design doc §8 잔여 리스크. 분기당 1회 `/naming-audit` 호출로 drift 점검.
- ~~**alembic/ pre-existing 16건 위반** (I001/UP007/UP035 — typing union 모던 syntax)~~ **해소** (final code review 권장 후 본 PR 에 포함): `pyproject.toml` 의 `"alembic/**"` per-file-ignore 를 `["D"]` → `["D", "UP", "I"]` 확장. alembic init 보일러플레이트는 의도적으로 modern syntax 적용 안 함 (alembic upstream 패턴 따름). Task 5 실행 중 발견 (2026-06-02) → final review 시점에 fix.
- **ruff format drift 가능성**: Task 12 검증 단계에서 11 files 의 `ruff format` 회귀 발견 (docstring 추가 시 line-length 초과로 줄바꿈 자동화 필요). 본 PR 의 Task 12 Step 12.6 commit 에 함께 fix 묶음 처리. 향후 회귀 방지: `.githooks/pre-commit` 의 backend `.py` 분기에 `ruff format --check` (또는 `ruff format` 자동 적용) 추가 검토 — 별도 chore PR. CI (`backend.yml`) 에는 `ruff format --check` step 추가도 동시 권장.
- **Step 12.8 hook test 시나리오 정정**: 단순 함수 (module docstring 없음 + 함수 docstring 없음) 만으로는 hook 차단 안 됨 — D100 글로벌 ignore + D103 이 D100 의존이라 module docstring 없는 파일에서 D103 도 안 잡힘. Hook 자체는 정상 작동 (F401 unused import 같은 다른 위반으로 차단 검증 됨, Task 6 implementer 보고). 향후 hook 작동 검증 시 F401 또는 N802 같은 룰 사용 권장.

---

## Postmortem

> (PR 머지 + 7일 후 채움. 계획과 다르게 갔던 점 / 발견된 새 위험 / 다음 plan 에 적용할 교훈.
>  없으면 "특이사항 없음" 1줄. 비워두지 말 것.)

---

## PR 머지 후 (수동, 컨벤션 — 2026-05-29 plans-ledger-restructure)

본 페어 파일 (design + plan) 의 핵심 결정 + outcome 을 압축 entry (15-30 줄) 로 작성 → `docs/plans/logs/process-infra.md` 의 `## Recent (last 90 days)` 섹션 맨 위에 추가 → 페어 2 파일 `git rm`. 같은 commit 또는 PR 머지 후속 mechanical commit.

Entry 형식 (template):

```markdown
### 2026-06-02 — Naming convention audit + PEP 257 enforce + automation infra

- **PR**: [#NN](url) (merged)
- **Why**: 5종 공식 명명/문서화 가이드 (Kotlin / Python PEP / Azure CAF) 대비 준수도 audit + PEP 257 docstring gap 해소 + 신규 코드 추가 시점에 자동 점검되도록 인프라 보강 (사용자 3축 의도: role / `.claude/` / 효율성)
- **What**: ruff `D` rule + `convention="pep257"` + per-file-ignore 활성화 / backend public API 59건 docstring 추가 + minor fix 5건 / pre-commit `.py` 분기 / PR template 보강 / `/naming-audit` slash command / `api-endpoint.md` Ktor legacy → FastAPI 재작성 / `docs/conventions/naming.md` SSoT + CLAUDE.md link
- **Outcome**: ruff D 0 errors + mypy/pytest 41/41 PASS + docker compose smoke green. PR template 렌더링 + slash command 호출 OK. v0.1.7 LV 유지 (versionCode 미증가).
- **Lessons**: (postmortem 발생 시 추가)
- **Files touched**: backend/pyproject.toml, backend/app/{routers,services,repositories,exceptions,config,database,dependencies}/*.py, backend/openapi.json, .githooks/pre-commit, .github/pull_request_template.md, .claude/commands/naming-audit.md, scripts/prompts/api-endpoint.md, docs/conventions/naming.md, CLAUDE.md
```

`bash scripts/gen-plans-index.sh` 가 ledger 의 Recent/Older 90일 기준 자동 재정렬 + INDEX 갱신.
