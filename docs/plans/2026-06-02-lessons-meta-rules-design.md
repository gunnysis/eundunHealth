---
type: design
status: proposed
pr: null
related_inc: PR #68 lessons (naming convention audit)
supersedes: null
target_version: 0.1.x meta
ledger_topic: process-infra
tags: [process, sdd, review, claude-md, lessons]
---

# PR #68 lessons 재발방지 인프라 (meta 룰) 설계

- **작성일**: 2026-06-02
- **상태**: 작성 중
- **연관 작업**: PR #68 (`a47515c`) + #69 (`98cefd4`) + 같은 날 infra PR (`lessons-infra-guards`)
- **대상 버전**: meta-only (코드 변경 0, docs + memory + CLAUDE.md 만)
- **선행 작업**: infra PR (`lessons-infra-guards`) 먼저 머지 — workflow permissions 가드가 meta PR CI 에도 적용되어 검증 1세트 추가

## 1. 배경

PR #68 작업의 **7 lessons** 중 자동 가드 채널이 없는 2건 (L2/L6) 을 본 PR 에서 처리. 나머지 5건 = L1/L4/L5/L7 (infra PR `lessons-infra-guards` 에서 자동 채널 가드) + L3 (services 부수 minor fix — 작업 중 판단 사항이라 룰화 부적합 명시 제외). 본 PR 의 두 lesson 은 *프로세스 룰* 성격이라 CLAUDE.md 의 "운영 안전 규칙" + plan/design template 의 본문 룰 + memory feedback 으로 가드. 코드 변경 0.

대상 lessons (process-infra.md ledger entry §Lessons 인용):

- **L2** (D415 = 0 발견): 80건 baseline 의 D415 2건이 모두 `main.py` 안 (D 전체 ignore) → 실제 작성 대상 63 → 59. 산수 검증 없이 baseline 추정하면 chain 전체 drift. *위반 분포는 측정 후 결정, 추정 후 측정 X*.
- **L6** (subagent spec reviewer 측정 오류 — Task 3): implementer 측정이 정확했고 spec reviewer 가 잘못된 옵션 (`--select D107` 같은) 으로 false report. controller 가 직접 verify 로 해결. *reviewer 도 fact-check 대상*.

## 2. Scope

### In-scope
- L2: CLAUDE.md "운영 안전 규칙" 에 룰 9 신설 (산수 검증) + `_templates/design.md` 의 "추정값 → 측정 검증" 섹션 추가
- L6: CLAUDE.md 에 룰 10 신설 (subagent reviewer fact-check) + memory feedback `subagent-reviewer-fact-check.md` 신규
- MEMORY.md INDEX 에 신규 memory 1줄 추가
- ledger entry (PR 머지 후 후속 commit 으로 `docs/plans/logs/process-infra.md` 두 번째 entry)

### Out-of-scope
- superpowers SDD 변형 skill 작성 — 사용자 거절한 "깊음" 단계 (Q3=중간)
- brainstorming/writing-plans skill 의 변형 — 같은 이유
- 새 `.claude/commands/` slash — L2/L6 모두 룰 성격 (자동화 채널 없음). slash 는 측정 도구만 적합
- L1/L4/L5/L7 (자동화 가능한 lessons) — infra PR 에서 처리

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 룰 등록 위치 | CLAUDE.md "운영 안전 규칙" 섹션 (룰 9 + 룰 10) | 본 프로젝트 컨벤션 — 기존 룰 1~8 같은 형식. 모든 Claude Code 세션 자동 컨텍스트 로딩 |
| D2 | L2 강제 형식 | template `_templates/design.md` 의 "추정값 → 측정 검증" 섹션 + 3 라벨 (`MEASURED` / `DEFERRED` / `ESTIMATE-ONLY`) | 자동 측정 강제 (e.g., subagent 가 design 자동 작성 시 룰 무시 가능) 는 spec self-review 에서 보완 |
| D3 | L6 trigger 좁히기 | 측정 결과 (구체 수치) 보고 시만 controller fact-check 필수, 정성 평가는 면제 | 모든 reviewer 결과 verify 하면 controller 시간 ↑ + SDD 효율 무력화 |
| D4 | L6 memory type | `feedback` (rule 형식) | 향후 SDD 세션의 모든 reviewer 결과 처리 시 룰 적용 — 행동 가이드 |
| D5 | 두 PR ledger entry 분리 | infra entry + meta entry 같은 ledger (`process-infra.md`) 의 같은 날짜 | 페어 분리 (infra design D2) 와 동일 원칙. Recent 섹션 상위 2 entry |

## 4. 옵션 비교

| 옵션 | A. CLAUDE.md 룰 + template (선택) | B. 신규 skill 작성 | C. memory feedback only |
|---|---|---|---|
| L2 채널 | CLAUDE.md 룰 9 + design template 섹션 | superpowers:writing-plans 변형 | memory `baseline-arithmetic-check.md` |
| L6 채널 | CLAUDE.md 룰 10 + memory feedback | superpowers:subagent-driven-development 변형 | memory feedback only |
| 강제력 | 중 (자동 컨텍스트 로딩 + template gate) | 강 (skill instruction 강제) | 약 (memory 만 — 세션마다 reload) |
| 변경 폭 | 3 파일 | 5+ 파일 + skill 인프라 도입 | 2 파일 |
| YAGNI | 부합 | 위반 (skill 작성 비용) | 너무 얕음 |
| 사용자 의도 (Q3=중간) | ✅ | ⚠️ 너무 깊음 | ⚠️ 너무 얕음 |

## 5. 구성 요소별 변경

### 5.1 MODIFY: `CLAUDE.md` 룰 9 추가 (L2)

"운영 안전 규칙" 섹션 (룰 1~8) 뒤에 룰 9:

```markdown
### 룰 9 — Design doc 의 baseline / 추정값은 측정 후 결정 (PR #68 lesson L2)
Design 또는 plan 작성 시 "약 N건", "~M 파일" 같은 정량 표현은 **측정 명령으로 확정 후 기록**. 추정 후 측정하면 chain 전체 drift (예: PR #68 — D415 2건이 모두 `main.py` ignore 안에 있어 실제 작성 대상 63 → 59, plan task scope 가 drift).

**체크리스트**:
1. Design doc 의 정량 표현마다 측정 명령 1줄 동봉 (e.g., `grep -c ... | wc -l` 결과 = N).
2. 측정 환경 부재 시 3 라벨 명시 — `MEASURED` / `DEFERRED — verify at Phase N` / `ESTIMATE-ONLY` (`_templates/design.md` 참조).
3. spec self-review step 에서 controller 가 측정값 1회 재확인 — drift 시 fix.

**예외**: 정성 표현 (e.g., "복잡한 case", "trivial fix") 은 본 룰 비대상.
```

### 5.2 MODIFY: `CLAUDE.md` 룰 10 추가 (L6)

룰 9 뒤에 룰 10:

```markdown
### 룰 10 — Subagent reviewer 의 측정 결과는 controller 가 직접 fact-check (PR #68 lesson L6)
SDD (superpowers:subagent-driven-development) 의 spec reviewer / code quality reviewer 가 **측정 수치** (lint 위반 수, 테스트 수, 커버리지 등) 보고 시 controller 가 같은 명령 1회 실행 + 결과 일치 확인. 불일치 시 reviewer 의 명령 형태 (e.g., L1 `--select` 함정) 의심.

**Trigger 좁히기** (D3):
- 측정 수치 보고 시 → fact-check 필수
- 정성 평가 (e.g., "코드 깔끔", "스타일 OK") → fact-check 면제 (verify 비용 > 효용)
- 일반 Agent tool (Explore / general-purpose) 호출 결과 → 측정 수치 보고 시만

**사례**: PR #68 Task 3 spec reviewer 가 D107 위반 85건 보고 → controller 가 직접 측정 = 32건. D107 글로벌 ignore 누락 (L1 `--select` 함정). controller 재측정 + plan fix.

**예외**: SDD 외 일반 대화의 답변, code-explorer 의 발견 사항 등은 비대상 (별도 verify 룰).
```

### 5.3 MODIFY: `docs/plans/_templates/design.md` "추정값 → 측정 검증" 섹션 추가 (L2)

기존 §6 "검증 계획" 또는 별도 신규 §X 로 1단락 + 3 라벨:

```markdown
### X. 추정값 → 측정 검증 (PR #68 lesson L2)

Design doc 의 정량 표현마다 라벨 1개 명시 + 측정 명령 동봉:

| 라벨 | 의미 | 작성 예시 |
|---|---|---|
| `MEASURED` | 측정 완료, 명령 + 결과 동봉 (default) | "변경 파일 14개 (MEASURED: `ls ... \| wc -l` = 14)" |
| `DEFERRED — verify at Phase N` | 환경 부재로 보류, plan Task N 에서 검증 의무 | "Sentry 신규 issue 수 (DEFERRED — verify at Phase 5)" |
| `ESTIMATE-ONLY` | 정량 의미 없는 추정 (e.g., "수십 건") | "후속 작업 ESTIMATE-ONLY: 수십 줄 추가 예상" |

spec self-review step (controller) 가 `MEASURED` 라벨의 명령 1회 재실행 + 결과 일치 확인.
```

### 5.4 NEW: `~/.claude/projects/C--programming-apps-eundunHealth/memory/subagent-reviewer-fact-check.md` (L6)

memory frontmatter + 본문:

```markdown
---
name: subagent-reviewer-fact-check
description: SDD subagent reviewer 의 측정 수치 보고 시 controller 가 직접 같은 명령 1회 실행으로 fact-check
metadata:
  type: feedback
---

SDD (superpowers:subagent-driven-development) 의 spec reviewer / code quality reviewer 가 측정 수치 (lint 위반 수, 테스트 수, 커버리지 등) 보고 시 controller 가 같은 명령 1회 실행 + 결과 일치 확인.

**Why**: PR #68 Task 3 spec reviewer 가 D107 위반 85건 false report (잘못된 ruff `--select D107` 명령 — L1 `--select` 함정과 같은 root cause). controller 직접 측정 = 32건. fact-check 안 했으면 plan 의 Task scope 가 drift.

**How to apply**:
- Trigger: subagent 의 측정 수치 (구체 숫자) 보고
- 면제: 정성 평가 ("코드 깔끔", "스타일 OK") + SDD 외 일반 Agent 호출
- 검증 방식: 같은 명령 1회 controller 실행 → 결과 일치 확인. 불일치 시 명령 형태 검토 (L1 `--select` flag, `--ignore` 누락 등)
- 비용: 명령 1회 / reviewer / task → SDD 효율 거의 무손실

[[ruff-select-flag-pitfall]] (L1 함정과 같은 root cause 자주 발생).
CLAUDE.md 룰 10 (본 메모리의 일반 룰화).
```

### 5.5 MODIFY: `MEMORY.md` INDEX 에 1줄 추가 (L6)

Quick Reference 섹션의 적절한 위치 (lessons section 근처):

```markdown
- **Subagent reviewer fact-check**: [subagent-reviewer-fact-check.md](subagent-reviewer-fact-check.md) — SDD 측정 수치 보고 시 controller 1회 verify 룰 (CLAUDE.md 룰 10)
```

### 5.6 Ledger entry (PR 머지 후 후속 commit)

`docs/plans/logs/process-infra.md` 의 Recent 섹션 상단에 두 번째 entry (infra entry 직전 또는 직후 — 머지 순서 따름).

## 6. 검증 계획

### 6.1 L2 룰 9 자동 컨텍스트 로딩 검증

PR 머지 후 새 Claude Code 세션 시작 → CLAUDE.md 자동 로딩 → 룰 9 텍스트 컨텍스트 안 포함 확인. (수동 시각 검증.)

### 6.2 L2 template 적용 검증

`docs/plans/_templates/design.md` 에 "추정값 → 측정 검증" 섹션 존재 + 3 라벨 명시 + 측정 명령 동봉 가이드 1단락. grep:

```bash
grep -c 'MEASURED\|DEFERRED\|ESTIMATE-ONLY' docs/plans/_templates/design.md
# 기대: ≥3
```

### 6.3 L2 본 design 자체 dogfood (eat own arithmetic)

본 design §1 의 "7 lessons" + "L3 룰화 부적합 1건" + "infra 4건 (L1/L4/L5/L7) + meta 2건 (L2/L6)" 산수 검증:

```bash
# ledger entry 의 §Lessons bullet 수 (실측)
awk '/^### 2026-06-02 — naming/,/^### 2026-05-29/' docs/plans/logs/process-infra.md \
  | grep -cE '^  - \*\*[a-zA-ZD]'
# 기대: 7 (Self-review 단계 실측 통과)

# Lesson ID 매핑 검증 (육안)
# L1 ruff `--select D` 함정 / L2 D415=0 / L3 services minor fix / L4 alembic UP/I
# L5 gitleaks permission / L6 subagent reviewer / L7 Azure portal auto-gen
# infra design (L1+L4+L5+L7) ∪ meta design (L2+L6) ∪ 제외 (L3) = 7 ✓
```

산수 일관성 — infra design §1 도 동일 표현 (`7 lessons 중 자동 채널 4건 + L3 제외 1건`) 으로 작성.

### 6.4 L6 룰 10 자동 컨텍스트 로딩 검증

PR 머지 후 새 SDD 세션의 reviewer 측정 수치 보고 시 controller 의 fact-check 흔적 (conversation log) — 본 PR 머지 후 발생.

### 6.5 L6 memory 등록 검증

```bash
ls ~/.claude/projects/C--programming-apps-eundunHealth/memory/subagent-reviewer-fact-check.md  # 기대: 존재
grep -c 'subagent-reviewer-fact-check' ~/.claude/projects/C--programming-apps-eundunHealth/memory/MEMORY.md
# 기대: ≥1 (INDEX 1줄 추가)
```

### 6.6 통합 검증 (PR 머지 후 +7일)

본 프로젝트 컨벤션 — ledger entry 의 Lessons 섹션은 머지 + 7일 후 작성 (운영 사례 누적 반영). 첫 적용 사례 1건 (룰 10 의 SDD reviewer fact-check 실 발화) 추가.

## 7. 롤백 절차

문서 + 메모리 + CLAUDE.md 변경 — 롤백 trivial.

- **PR 단위 revert**: `gh pr revert <number>`.
- **부분 롤백 (룰 10 만)**: CLAUDE.md 의 룰 10 섹션만 `git restore -p`. memory 와 template 은 독립.
- **데이터/스키마/인프라 변경 없음**.

## 8. 잔여 리스크

- **L2 자동 강제 없음**: spec self-review step 의 controller 가 측정값 재확인 안 하면 룰 무력. 본 design 자체가 dogfood (§6.3) — 사례 1건이 룰 의식 보강.
- **L6 trigger 경계 모호**: 정성 평가 ("코드 깔끔") 와 측정 수치 ("3건") 의 경계가 reviewer 마다 다름. 의심 시 fact-check default — 비용 무손실.
- **MEMORY.md 줄 수 200 초과 risk**: 본 PR 의 INDEX 1줄 추가는 안전 (현재 ~60줄). 향후 lessons 누적 시 200줄 초과하면 별도 정리 PR.
- (해소됨 — spec self-review 단계에서 §1 + §6.3 산수 정정 완료. ledger entry §Lessons 실측 7건 → infra 4 + meta 2 + L3 제외 1 = 7 확인.)
- **CLAUDE.md 길이**: 룰 1~10 까지 늘어남. 향후 룰 추가 시 "운영 안전 규칙" 섹션 분할 검토.

## 9. 참고 자료

- PR #68 ledger entry: `docs/plans/logs/process-infra.md` 의 `2026-06-02 — naming convention audit + PEP 257 enforce + automation infra` §Lessons
- 같은 날 infra design: `docs/plans/2026-06-02-lessons-infra-guards-design.md`
- superpowers SDD: `superpowers:subagent-driven-development` skill
- 관련 memory:
  - [[ruff-select-flag-pitfall]] (L1 — L6 의 root cause 자주 공유)
  - [[naming-convention-ssot]] (PR #68 인프라 5종)
  - [[subagent-reviewer-fact-check]] (본 PR 신규)
