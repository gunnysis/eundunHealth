---
type: design
status: shipped
pr: 48
related_inc: null
supersedes: null
target_version: docs-only
tags: [docs, tooling, conventions, meta]
---

# docs/plans/ 유지보수 인프라 설계 (Layer 1 + 2 + 4)

- **작성일**: 2026-05-28
- **상태**: 승인 완료 (D1~D4 + 추가 픽 모두 OK, 2026-05-28 채팅)
- **연관 작업**: 본 design doc 자체가 dogfood — 새 frontmatter 형식으로 작성됨
- **대상 버전**: 앱 버전 영향 없음 (docs/tooling only)
- **선행 작업**: 없음 (독립 작업)

## 1. 배경

`docs/plans/` 는 design+plan 페어 컨벤션(memory `design-plan-docs-convention.md`)으로 운영 중이고, 현재 9개 파일 (4 페어 + 1 RFC, 총 5536 lines, 2026-05-26~28) 로 채워져 있다. 실제 마찰 5가지가 누적됐다:

| # | 마찰 | 구체 증거 |
|---|---|---|
| 1 | **상태 drift** | schema-drift design "구현은 2026-05-28 별도 세션에서 진행" 인데 PR #47 까지 종료. applinks/signup-confirmation/mcp 도 "승인 완료" 까지만 적혀 shipped 표시 없음 |
| 2 | **PR ↔ 문서 추적 불가** | doc 자체에 PR 번호 없음. `git log --grep` + PR 본문 양쪽을 봐야 매핑 |
| 3 | **인덱스 부재** | `ls` 만으로 "shipped vs in-flight vs proposed" 구분 불가. 9개 다 열어봐야 알 수 있음 |
| 4 | **frontmatter 비일관** | applinks/signup/schema-drift = bullet list, mcp-integration = `**작성일:**` 콜론 스타일, RFC 또 다름. 기계 파싱 불가 |
| 5 | **메모리 mirror 중복** | `memory/inc-2026-05-27-01-schema-drift.md` 등이 plans 의 부분 복제. 한 곳 갱신하면 다른 곳 stale |

`docs/plans/` 가 20+ 파일로 성장할 때 위 마찰은 기하급수로 비용 ↑. 9개 규모일 때 인프라를 깔아두는 게 최저비용.

## 2. Scope

### In-scope (Layer 1 + 2 + 4)
- **Layer 1**: 9개 doc 에 YAML frontmatter 백필 + `scripts/gen-plans-index.sh` (Python) + `docs/plans/README.md` 자동 생성 + pre-commit hook 통합 + CI drift check
- **Layer 2**: `docs/plans/_templates/design.md`, `_templates/plan.md` 2개 템플릿
- **Layer 4**: shipped plan 의 `## Postmortem` 섹션 컨벤션 (별도 파일 X, 같은 plan 끝에 append). 이번 PR 에선 컨벤션만 정의 + 1개 PoC (schema-drift PR #47 가 merge 되면 첫 사례로 활용)
- 부가: `CLAUDE.md` Documentation 섹션 + memory `design-plan-docs-convention.md` 갱신

### Out-of-scope
- **Layer 3** (archive 디렉토리 이동): 9개 → 20+ 되기 전엔 외부 참조 5개 깨지는 비용 > 정리 가치. 다음 분기 재검토.
- **자동 PR 상태 감지** (gh CLI 로 PR open/merged 자동 동기화): 매력적이지만 offline 동작 X + token 의존. 사람이 frontmatter 갱신 + INDEX 의 PR 컬럼이 cross-check 로 충분.
- **RFC / postmortem 템플릿**: RFC 사례 1개라 패턴화 이르고, postmortem 은 첫 사용 후 컨벤션 굳히고 템플릿화.
- **메모리 mirror 자동 sync**: 메모리는 의도적으로 plans 의 요약 + link 형태로 유지. 양방향 sync 는 over-engineering.
- **frontmatter schema 검증 (CI)**: lint 추가는 가치 < 비용. README 생성 시 파싱 실패하면 거기서 catch.

## 3. 의사결정 요약 (D1~D4 + 추가 픽)

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | INDEX 생성 트리거 | pre-commit hook + CI drift check | spotless/detekt 와 동일 패턴, 로컬 즉시 + CI 차단 이중 |
| D2 | INDEX.md git tracking | track + commit | GitHub 에서 docs/plans/ 클릭 시 README 자동 표시, drift 는 CI 가드 |
| D3 | Layer 2 템플릿 수 | 2개 (design, plan) | 자주 쓰는 둘만. RFC/postmortem 은 사례 누적 후 |
| D4 | postmortem 위치 | 같은 plan 파일 `## Postmortem` 섹션 | 일관 읽기 + status: shipped + Postmortem 섹션 존재 = 회고 완료 신호 |
| D5 | missing frontmatter 처리 | silent skip + stderr warn (fail X) | malformed frontmatter 만 fail. 다중 PR coordination (예: PR #47 후속 머지) 시 main 안 깨짐. 점진 도입 가능 |
| + | frontmatter 파서 | Python stdlib + PyYAML 6.0.3 (backend venv) | yaml 이미 설치됨 (확인 완료) — 외부 의존 0 |
| + | INDEX 그룹핑 | status 별 묶음, 그룹 내 날짜 desc | shipped/in-progress/proposed 시각적 분리 |
| + | status SoT | 사람이 직접 frontmatter 갱신 (PR merge 후) | INDEX 의 PR 컬럼이 cross-check |

## 4. 옵션 비교 (메인 대안만)

| 영역 | 채택안 | 기각안 1 | 기각안 2 | 기각 이유 |
|---|---|---|---|---|
| frontmatter 형식 | YAML (---) | TOML (+++) | JSON | YAML 은 markdown 생태계 표준 (Jekyll/Hugo/MkDocs). GitHub 도 일부 frontmatter 렌더링 지원 |
| INDEX 생성 언어 | Python | Bash + awk | Node | Python 은 backend venv 활용 + Windows/Linux 양쪽. Bash + awk 는 frontmatter 멀티라인 파싱 취약. Node 는 추가 의존 |
| INDEX 트리거 | pre-commit | CI only | manual | pre-commit 은 즉시 피드백 + 잊을 위험 0. CI only 는 PR 마지막에 INDEX commit 따로 |
| Layer 4 형식 | plan 끝 `## Postmortem` | 별도 `-postmortem.md` 파일 | incident-log 위임 | 한 파일 일관 읽기 + incident-log 와 역할 분리 (incident-log = 사고 회고, postmortem = 계획 vs 실제 회고) |

## 5. 구성 요소별 변경

### 5.1 Frontmatter schema (YAML)

```yaml
---
type: design | plan | rfc | postmortem    # 필수
status: proposed | approved | in-progress | shipped | superseded | abandoned  # 필수
pr: 47                                     # int 또는 null. shipped 면 필수, 그 외 null
related_inc: INC-2026-05-27-01             # string 또는 null. 인시던트 트리거면 채움
supersedes: 2026-05-26-foo                 # string 또는 null. 이전 doc replace 시 파일명 (확장자 제외)
superseded_by: 2026-06-01-bar              # string 또는 null. 다음 doc 가 이걸 대체할 때 역링크
target_version: v0.1.5                     # string. "docs-only" / "infra-only" 도 허용
tags: [backend, alembic, ops]              # YAML list. 최소 1개
---
```

**필수 vs 선택 규칙**:
- 필수: `type`, `status`, `target_version`, `tags` (≥1)
- 조건부 필수: `status: shipped` 면 `pr` 필수 (PR 머지 흔적). `status: superseded` 면 `superseded_by` 필수
- 모두 선택: `related_inc`, `supersedes` (없으면 `null`)

### 5.2 7개 doc 백필 매핑 (PR coordination 반영)

이 PR 의 branch (`chore/plans-folder-maintenance`) 는 main 기반. main 에 존재하는 7개 doc 만 backfill:

| 파일 | type | status | pr | related_inc | tags |
|---|---|---|---|---|---|
| 2026-05-26-applinks-deep-link-design.md | design | shipped | 42 | null | [android, auth, deep-link] |
| 2026-05-26-applinks-deep-link-plan.md | plan | shipped | 42 | null | [android, auth, deep-link] |
| 2026-05-26-signup-confirmation-flow-design.md | design | shipped | 40 | null | [android, auth, supabase] |
| 2026-05-26-signup-confirmation-flow-plan.md | plan | shipped | 40 | null | [android, auth, supabase] |
| 2026-05-27-signup-failed-ux-visibility-rfc.md | rfc | proposed | null | INC-2026-05-26-01 | [android, ux] |
| 2026-05-28-mcp-integration-setup-design.md | design | shipped | 46 | null | [ops, mcp, automation] |
| 2026-05-28-mcp-integration-setup-plan.md | plan | shipped | 46 | null | [ops, mcp, automation] |

> **schema-drift 페어 (`2026-05-27-schema-drift-recovery-{design,plan}.md`) 는 PR #47 머지 후 별도 followup commit 으로 frontmatter 추가** — D5 결정 덕에 그 사이엔 silent skip 되어 main 안 깨짐.
> 본 design + 후속 plan 은 새로 작성되는 거라 처음부터 frontmatter 포함 (별도 backfill 없음).

### 5.3 `scripts/gen-plans-index.sh` (Python 래퍼)

```bash
#!/usr/bin/env bash
# docs/plans/*.md 의 frontmatter 를 읽어 docs/plans/README.md 자동 생성.
# pre-commit hook 과 CI 양쪽에서 호출. --check 모드는 drift 만 감지 (생성 X).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PY="${REPO_ROOT}/backend/.venv/Scripts/python.exe"
[ ! -x "$PY" ] && PY="${REPO_ROOT}/backend/.venv/bin/python"
[ ! -x "$PY" ] && { echo "ERROR: backend venv 없음" >&2; exit 1; }

"${PY}" "${REPO_ROOT}/scripts/gen_plans_index.py" "$@"
```

Python script (`scripts/gen_plans_index.py`) 책임:
1. `docs/plans/*.md` (단, `README.md` + `_templates/` 제외) walk
2. 각 파일에서 frontmatter (--- … ---) 파싱 (`yaml.safe_load`)
3. 필수 필드 검증 — 누락 시 `::error file=path::message` (GitHub Actions 호환) + exit 1
4. `--check` 모드: 기존 README.md 와 새로 생성한 내용 diff. 다르면 exit 1.
5. 기본 모드: README.md 작성 (status 별 그룹핑, 그룹 내 날짜 desc 정렬)

### 5.4 `docs/plans/README.md` 출력 형식

```markdown
<!-- AUTO-GENERATED by scripts/gen-plans-index.sh — do not edit manually -->
# docs/plans/ 인덱스

> `docs/plans/` 는 비-trivial 작업의 design + plan 페어를 모은 폴더. 컨벤션: `memory/design-plan-docs-convention.md` 참조.

## In progress (1)

| 날짜 | 주제 | type | PR | 인시던트 | tags |
|---|---|---|---|---|---|
| 2026-05-27 | schema-drift-recovery | design + plan | [#47](https://github.com/gunnysis/eundunHealth/pull/47) | INC-2026-05-27-01 | backend, alembic, ops |

## Shipped (3)

| 날짜 | 주제 | type | PR | 인시던트 | tags |
|---|---|---|---|---|---|
| 2026-05-28 | mcp-integration-setup | design + plan | [#46](.../pull/46) | — | ops, mcp, automation |
| 2026-05-26 | signup-confirmation-flow | design + plan | [#40](.../pull/40) | — | android, auth, supabase |
| 2026-05-26 | applinks-deep-link | design + plan | [#42](.../pull/42) | — | android, auth, deep-link |

## Proposed (1)

| 날짜 | 주제 | type | PR | 인시던트 | tags |
|---|---|---|---|---|---|
| 2026-05-27 | signup-failed-ux-visibility | rfc | — | INC-2026-05-26-01 | android, ux |
```

design + plan 페어는 한 row 로 묶어서 표시 (날짜·주제·tags 동일하면 페어로 인식, type 컬럼만 `design + plan`).

### 5.5 `docs/plans/_templates/design.md`

```markdown
---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: vX.Y.Z
tags: [TBD]
---

# {제목} 설계

- **작성일**: YYYY-MM-DD
- **상태**: 작성 중
- **연관 작업**: (관련 PR / 인시던트 / 이전 design)
- **대상 버전**: (versionCode 또는 docs-only)
- **선행 작업**: (의존성 있는 작업, 없으면 "없음")

## 1. 배경

(왜 지금 / 사용자 문제 / 인시던트 트리거)

## 2. Scope

### In-scope
- 항목 1

### Out-of-scope
- 항목 1 (이유: ...)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|

## 4. 옵션 비교

| 옵션 | A. ... | B. ... | C. ... |
|---|---|---|---|

## 5. 구성 요소별 변경

### 5.1 NEW/MODIFY: `path/to/file`

(코드 블록 + 차이 설명)

## 6. 검증 계획

## 7. 롤백 절차

## 8. 잔여 리스크

## 9. 참고 자료
```

### 5.6 `docs/plans/_templates/plan.md`

```markdown
---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: vX.Y.Z
tags: [TBD]
---

# {제목} Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** (한두 문장 — 무엇을 / 왜)

**Architecture (요약):** (핵심 패턴 한 문단)

**Tech Stack:** Python 3.12 / Kotlin 2.2.10 / ...

**참고:**
- Design: `docs/plans/YYYY-MM-DD-{topic}-design.md`
- Branch: `{branch-name}` (Task 0 에서 생성)

**중요 원칙:**
- TDD: 동작 변경 task 는 red → green → commit
- 모든 commit 은 `{branch}` 브랜치
- ...

**Task 순서:**

```
Task 0  branch + 환경 확인
Task 1  ...
Task N  push + PR
```

---

## Phase 1: ...

### Task 1: ...

**Files:** ...

**Step 1:** ...

**Step N: commit**

```bash
git commit -m "..."
```

---

## Phase 2: 최종 검증 + PR

### Task N-1: 전체 회귀
### Task N:   push + PR

---

## Phase 3 (선택): 머지 후 운영 검증

### Task X: ...

---

## 잔여 리스크 / 후속 작업

## Postmortem

> (PR 머지 + 1주 후 채움. 계획과 다르게 갔던 점 / 발견된 새 위험 / 다음 plan 에 적용할 교훈.
>  없으면 "특이사항 없음" 1줄. 비워두지 말 것.)
```

### 5.7 Postmortem 컨벤션 (Layer 4)

- **위치**: shipped plan 의 맨 끝 `## Postmortem` 섹션
- **언제**: PR merge 후 7일 (Phase 5 운영 검증까지 완료된 시점)
- **내용**: 계획 vs 실제, 발견된 새 리스크, 다음 plan 에 적용할 교훈. **없으면 "특이사항 없음" 1줄 작성** (비워두면 미작성과 구분 안 됨)
- **incident-log 와 역할 분리**: incident-log = 사고 회고 (외부 트리거), postmortem = 계획 자체 회고 (내부 트리거)
- **PoC**: schema-drift-recovery-plan (PR #47 머지 후 + 7일 = 2026-06-04) 가 첫 사례. 이번 PR 에선 컨벤션만 정의.

### 5.8 `.githooks/pre-commit` 변경

기존 hook 은 `.kt/.kts` 변경 시만 spotless+detekt 실행. 신규 분기 추가:

```bash
# 3) docs/plans/*.md 변경 시 INDEX 재생성
CHANGED_PLANS=$(git diff --cached --name-only --diff-filter=ACM \
    | grep -E '^docs/plans/.*\.md$' \
    | grep -v 'docs/plans/README\.md$' \
    | grep -v 'docs/plans/_templates/' || true)

if [ -n "$CHANGED_PLANS" ]; then
    echo "[pre-commit] docs/plans/ changes detected → gen-plans-index"
    bash "$(git rev-parse --show-toplevel)/scripts/gen-plans-index.sh"
    git add "$(git rev-parse --show-toplevel)/docs/plans/README.md"
fi
```

### 5.9 CI drift check

새 workflow step 추가 (어디에 — 선택지):
- `backend.yml` 의 기존 `runtime-smoke` 다음 — 백엔드 PR 만 검증
- 별도 minimal workflow `docs-plans-index.yml` — paths: `docs/plans/**` 트리거

**채택**: 후자. 이유: docs 변경은 backend 변경과 독립이라 backend.yml trigger paths 와 안 맞음. 그리고 backend.yml 이 이미 크고, 단순 docs check 를 거기 끼우면 backend dev 의 CI 대기 시간만 늘림.

```yaml
# .github/workflows/docs-plans-index.yml
name: docs-plans-index
on:
  pull_request:
    paths:
      - 'docs/plans/**'
      - 'scripts/gen-plans-index.sh'
      - 'scripts/gen_plans_index.py'
  push:
    branches: [main]
    paths: [...]

jobs:
  check-index:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-python@v6
        with: { python-version: '3.12' }
      - run: pip install PyYAML==6.0.3
      - run: bash scripts/gen-plans-index.sh --check
```

(`scripts/gen-plans-index.sh` 가 backend venv 없을 때 fallback 으로 system python 사용하도록 분기 — CI 호환).

### 5.10 `CLAUDE.md` Documentation 섹션 갱신

기존 `## Documentation` 의 plans 항목 추가:
```diff
+ - `@docs/plans/README.md` — design+plan 페어 인덱스 (자동 생성, frontmatter SoT)
```

### 5.11 memory `design-plan-docs-convention.md` 갱신

- frontmatter 형식 추가 (5.1 의 schema)
- README.md 자동 생성 + pre-commit hook 언급
- postmortem 섹션 컨벤션 추가
- `[[plans-folder-maintenance-design]]` 역링크

## 6. 검증 계획

### 6.1 로컬
1. 9개 doc 백필 후 `bash scripts/gen-plans-index.sh` → README.md 생성. 수동 확인:
   - 3개 status 그룹 (In progress, Shipped, Proposed) 다 표시
   - 페어가 한 row 로 묶임 (date + topic + tags 일치)
   - PR 링크 클릭 가능 (절대 URL)
2. `bash scripts/gen-plans-index.sh --check` → exit 0 (no drift)
3. 한 doc 의 frontmatter 일부러 깨뜨림 (status 누락) → exit 1 + 에러 메시지 명확
4. pre-commit hook: `docs/plans/foo.md` 더미 staging → README 자동 regenerate + git add

### 6.2 CI
1. PR 이 `docs/plans/` 만 건드릴 때 `docs-plans-index.yml` 만 트리거 (backend.yml 트리거 안 됨)
2. README 미커밋 PR 만들기 → CI fail + 메시지 명확

### 6.3 dogfood
이 design 의 후속 plan 자체가 새 frontmatter + 템플릿 사용 → 컨벤션이 즉시 1회 더 검증됨

## 7. 롤백 절차

### 7.1 INDEX 생성 스크립트 자체 버그
`scripts/gen-plans-index.sh` 가 잘못된 README 생성 → `git revert <commit>` 1줄. README 는 자동 생성물이라 손실 0.

### 7.2 frontmatter schema 후 변경 필요
필드 추가/제거 시 9개 doc 모두 update 필요. 마이그레이션 PR 1개. 백엔드/앱 영향 없음.

### 7.3 컨벤션 자체 철회 (극단)
pre-commit 분기 제거 + CI workflow 삭제 + README.md gitignore. frontmatter 는 남아도 무해 (markdown 본문 위 단순 텍스트로 렌더링).

## 8. 잔여 리스크

1. **frontmatter 누락 PR** — 컨벤션 강제는 CI 가드뿐. 신규 plan 작성자가 템플릿 안 쓰면 누락 → CI 가 차단하니 머지는 막힘. 인지 비용 ↑.
2. **status 수동 갱신 부담** — PR merge 후 사람이 직접 `in-progress → shipped + pr 채움` 해야 함. 잊으면 INDEX 가 영구 stale. **완화**: PR template Backend/Android 섹션과 동일하게 docs-plan 변경 PR 체크박스 추가 검토 (이번 PR 에선 미반영, 사용 빈도 보고).
3. **Postmortem 작성 동기 부족** — Layer 4 컨벤션이 강제력 없음. 첫 PoC (schema-drift) 가 실제 작성되는지 관찰 → 안 쓰면 컨벤션 재검토.
4. **PyYAML 의존성** — backend venv 가 없는 환경 (예: 새 contributor 가 백엔드 안 깔고 docs 만 만지는 경우) 에서 pre-commit fail. **완화**: 스크립트 fallback 으로 system python 시도 + PyYAML 없으면 `pip install --user PyYAML` 안내 메시지.
5. **README 자동 생성이 manual edit 욕구를 막음** — 가끔은 사람이 "이 plan 은 deprecated 라 인덱스에서 가리기" 같은 manual override 가 필요할 수 있음. **현재는 미지원** — frontmatter `status: abandoned` 면 별도 그룹으로 표시하는 정도로 대응. 그 이상은 후속.

## 9. 참고 자료

- 기존 design 참고: `docs/plans/2026-05-27-schema-drift-recovery-design.md` (구조 참조)
- 메모리 컨벤션: `~/.claude/projects/.../memory/design-plan-docs-convention.md`
- YAML frontmatter 생태계: Jekyll, Hugo, MkDocs 모두 동일 형식 — 향후 정적 사이트 생성 시 직접 활용 가능
- 후속 plan 문서: `docs/plans/2026-05-28-plans-folder-maintenance-plan.md` (작성 예정)
