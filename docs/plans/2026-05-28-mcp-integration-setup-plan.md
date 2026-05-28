# MCP 통합 + 운영 자동화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sentry/Azure/GitHub/Context7 MCP 4종을 일관 운영 자동화에 통합 — (1) Phase 5
검증 1-command 화 (`/verify-deploy`), (2) 룰 6 secretref 위반 commit 차단,
(3) SessionStart 에 보류 검증 자동 노출. 사용자 추가 인증 정보 입력 없음.

**Architecture:** MCP 9개 모두 이미 연결됨 (`claude mcp list` 통과). 이 작업은 **권한
allowlist 확장 + Azure tenant env 주입 + 슬래시 명령 1개 + Bash hook 1개 + 기존
SessionStart 스크립트 확장 + 문서 갱신** 으로 구성. 모든 변경은 `feat/mcp-integration-setup`
브랜치 (이미 생성됨) 의 single PR.

**Tech Stack:** Claude Code settings.local.json / settings.json, .claude/commands/ 슬래시
명령, bash hook (Windows 호스트에서 Bash tool 경유), MCP tools (Sentry/Azure/GitHub).

**참고:**
- Design: `docs/plans/2026-05-28-mcp-integration-setup-design.md` (commit `0272d0c`)
- 관련 plan: `docs/plans/2026-05-27-schema-drift-recovery-plan.md` Phase 5
- Branch: `feat/mcp-integration-setup` (이미 main 기준 분기, no commits except design doc)

**중요 원칙:**
- 각 task 끝에 git commit (frequent commits, atomic by component)
- Permissions 추가는 read-only 만, write 는 ask 유지 (design §3.1)
- secretref-guard hook 은 fail-open (운영 차단 회피)
- 사용자 입력 필요 시점은 task 헤더에 **🚨 USER ACTION** 명시
- **Windows 호스트 명령**: PowerShell primary + Bash tool 보조 (CLAUDE.md PowerShell 섹션)
  - 각 Step 첫 줄에 사용 tool 코드 fence 언어로 명시 (`bash` / `pwsh`)
- shell script 신규 파일은 `*.sh text eol=lf` 처리 필요 (이미 `backend/.gitattributes` 패턴
  존재하지만 루트는 없음 — Task 4 에서 처리)

**Task 순서:**

```
Task 0   사전 확인 (브랜치 / MCP 연결 상태 / .gitignore 정책 재확인)
Task 0.5 .gitignore 조정 — .claude/commands/ + .claude/settings.json 만 unignore → commit #1
Task 1   §3.1 permissions allowlist 확장 (NO COMMIT — settings.local.json 은 gitignored 유지, 개인 로컬)
Task 2   §3.2 Azure tenant env 주입 — 🚨 USER ACTION (Claude Code 재시작) (NO COMMIT — git 외부)
Task 3   §3.3 /verify-deploy 슬래시 명령 → commit #2
Task 4   §3.4 secretref-guard hook (script + settings.json + .gitattributes) → commit #3
Task 5   §3.5 SessionStart 확장 (claude-context.sh) → commit #4 (timeout 상향은 settings.local.json, 개인 로컬, no commit)
Task 6   §3.6 CLAUDE.md + operations-snapshot.md → commit #5
Task 7   E2E 회귀 (각 컴포넌트 1회 검증)
Task 8   push + PR
```

**git 추적 정책 (중요):**
- `.claude/settings.local.json`: **gitignored 유지** — 개인 permissions, 다른 contributor 가
  관리할 수 있어야 함. Task 1 / Task 5 의 settings.local.json 변경은 commit 안 함.
- `.claude/settings.json`: **unignored** (Task 0.5 에서 .gitignore 조정) — 프로젝트 공유
  hook 등록 위치. Task 4 에서 commit.
- `.claude/commands/`: **unignored** (Task 0.5) — 프로젝트 공유 슬래시 명령. Task 3 에서 commit.
- `scripts/hooks/secretref-guard.sh`: **tracked** (scripts/ 는 이미 tracked).

---

## 사전 확인

### Task 0: 환경 sanity check

**Files:** 변경 없음

- [ ] **Step 1: 브랜치 확인** (pwsh)

```pwsh
git branch --show-current
```
Expected: `feat/mcp-integration-setup`. 아니면 `git checkout feat/mcp-integration-setup`.

- [ ] **Step 2: MCP 연결 상태 확인** (pwsh)

```pwsh
claude mcp list 2>&1 | Select-String "Connected"
```
Expected: 최소 9줄 (sentry, azure, github, context7, tavily, wikidocs, gmail, gcal, gdrive)
모두 `✓ Connected`. 1개라도 빠지면 사용자에게 보고 후 진행 중단.

- [ ] **Step 3: 인증 통과 확인** (각 MCP whoami 호출)

3개 MCP tool 직접 호출 (deferred tool 이므로 ToolSearch 먼저):
```
ToolSearch query="select:mcp__sentry__whoami,mcp__github__get_me"
mcp__sentry__whoami()    # → "qkr133456@gmail.com" 포함
mcp__github__get_me()    # → "login": "gunnysis"
```
Azure 는 Task 2 후에 검증 (현재는 tenant 명시 필요한 상태).

**No commit.**

---

### Task 0.5: `.gitignore` 조정 — `.claude/commands/` + `.claude/settings.json` unignore

**Files:** Modify `.gitignore`

- [ ] **Step 1: 현재 `.gitignore` 의 `.claude/` 라인 위치 확인**

기존 (line 7-8):
```
# Claude Code 로컬 설정
.claude/
```

- [ ] **Step 2: `.gitignore` Edit — 프로젝트 공유 파일 unignore** (Edit tool)

기존:
```
# Claude Code 로컬 설정
.claude/
```

신규:
```
# Claude Code 로컬 설정 — settings.local.json + 캐시만 ignore.
# 프로젝트 공유: settings.json (hooks), commands/ (슬래시 명령), skills/ (스킬) 은 tracked.
.claude/*
!.claude/settings.json
!.claude/commands/
!.claude/commands/**
!.claude/skills/
!.claude/skills/**
```

> `.claude/skills/` 는 현재 `changelog/` 하나 있음 (memory MEMORY.md 의 `.claude/skills/changelog/SKILL.md`).
> `.claude/settings.local.json` 은 명시적으로 unignore 하지 않음 → 계속 gitignored 상태 유지.

- [ ] **Step 3: 검증** (pwsh)

`git check-ignore -v` 는 winning rule 을 항상 출력 — negation rule (`!...`) 이 winning 이어도
해당 라인이 출력됨. unignored 판정은 출력 라인이 `!` 로 시작하는지 (또는 exit code 가 1인지) 로 판단.

```pwsh
git check-ignore -v .claude/settings.json; "exit: $LASTEXITCODE"
git check-ignore -v .claude/settings.local.json; "exit: $LASTEXITCODE"
git check-ignore -v .claude/commands/test.md; "exit: $LASTEXITCODE"
git check-ignore -v .claude/skills/changelog/SKILL.md; "exit: $LASTEXITCODE"
```
Expected:
- `.claude/settings.json` → `.gitignore:N:!.claude/settings.json` + `exit: 1` (negation = NOT ignored)
- `.claude/settings.local.json` → `.gitignore:N:.claude/*` + `exit: 0` (positive match = ignored)
- `.claude/commands/test.md` → `.gitignore:N:!.claude/commands/**` + `exit: 1` (NOT ignored)
- `.claude/skills/changelog/SKILL.md` → `.gitignore:N:!.claude/skills/**` + `exit: 1` (NOT ignored)

> exit code 1 = "not ignored", exit code 0 = "ignored". `-q` 옵션을 추가하면 출력 없이 exit
> code 만 반환되어 더 명확.

- [ ] **Step 4: 기존 .claude/skills/ 추적 시작** (필요 시 — Task 6 의 changelog 스킬 보존)

```pwsh
git status .claude/
```
신규 untracked 파일 목록 확인. 의도한 unignore 결과인지 확인 후:

```bash
git add .claude/skills/changelog/SKILL.md  # 있으면
```

(이번 task 에서는 .gitignore 만 commit. 다른 .claude 파일은 각자 task 에서 추가.)

- [ ] **Step 5: commit** (bash)

```bash
git add .gitignore
git commit -m "$(cat <<'EOF'
chore: .gitignore 조정 — .claude/settings.json + commands/ + skills/ unignore

Claude Code 의 프로젝트 공유 설정 (PreToolUse hook 등록, 슬래시 명령, 스킬) 을
다른 contributor 와 공유 가능하게 함. 개인 설정 (settings.local.json — 권한) 은
계속 gitignored 유지.

후속 PR 의 .claude/settings.json (secretref-guard PreToolUse) + .claude/commands/
verify-deploy.md 가 이 변경을 전제로 함.

design: docs/plans/2026-05-28-mcp-integration-setup-design.md

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 1: Permissions

### Task 1: `.claude/settings.local.json` allowlist 확장 (NO COMMIT — 개인 로컬)

**Files:** `.claude/settings.local.json` (Modify)

- [ ] **Step 1: 현재 settings.local.json 의 allow 배열 끝 위치 확인** (이미 file state 알고 있음)

기존 마지막 항목: `"PowerShell($ErrorActionPreference='Continue'; npx \"@azure/mcp@latest\" --help 2>&1 | Select-Object -First 30)"`
(line 188). 다음 줄 `]` 가 allow 배열 닫는 위치.

- [ ] **Step 2: allow 배열 끝에 MCP read-only tool 56개 일괄 추가** (Edit tool)

마지막 PowerShell 항목 뒤에 `,` 추가하고 다음 entries 삽입 (각 entry 는 `"<tool-name>"` 형식).
Sentry 17개 + Azure 15개 + GitHub 24개 = 56개 (Context7 2개는 이미 있음).

기존:
```json
      "PowerShell($ErrorActionPreference='Continue'; npx \"@azure/mcp@latest\" --help 2>&1 | Select-Object -First 30)"
    ],
```

신규:
```json
      "PowerShell($ErrorActionPreference='Continue'; npx \"@azure/mcp@latest\" --help 2>&1 | Select-Object -First 30)",
      "mcp__sentry__whoami",
      "mcp__sentry__find_organizations",
      "mcp__sentry__find_projects",
      "mcp__sentry__find_releases",
      "mcp__sentry__find_dsns",
      "mcp__sentry__find_teams",
      "mcp__sentry__search_issues",
      "mcp__sentry__search_events",
      "mcp__sentry__search_issue_events",
      "mcp__sentry__search_docs",
      "mcp__sentry__get_doc",
      "mcp__sentry__get_event_attachment",
      "mcp__sentry__get_issue_tag_values",
      "mcp__sentry__get_profile_details",
      "mcp__sentry__get_replay_details",
      "mcp__sentry__get_sentry_resource",
      "mcp__sentry__analyze_issue_with_seer",
      "mcp__azure__subscription_list",
      "mcp__azure__group_list",
      "mcp__azure__group_resource_list",
      "mcp__azure__containerapps",
      "mcp__azure__acr",
      "mcp__azure__postgres",
      "mcp__azure__monitor",
      "mcp__azure__keyvault",
      "mcp__azure__applicationinsights",
      "mcp__azure__resourcehealth",
      "mcp__azure__role",
      "mcp__azure__quota",
      "mcp__azure__pricing",
      "mcp__azure__documentation",
      "mcp__azure__get_azure_bestpractices",
      "mcp__github__get_me",
      "mcp__github__get_commit",
      "mcp__github__get_file_contents",
      "mcp__github__get_label",
      "mcp__github__get_latest_release",
      "mcp__github__get_release_by_tag",
      "mcp__github__get_tag",
      "mcp__github__get_team_members",
      "mcp__github__get_teams",
      "mcp__github__list_branches",
      "mcp__github__list_commits",
      "mcp__github__list_issues",
      "mcp__github__list_issue_types",
      "mcp__github__list_pull_requests",
      "mcp__github__list_releases",
      "mcp__github__list_repository_collaborators",
      "mcp__github__list_tags",
      "mcp__github__issue_read",
      "mcp__github__pull_request_read",
      "mcp__github__search_code",
      "mcp__github__search_issues",
      "mcp__github__search_pull_requests",
      "mcp__github__search_repositories",
      "mcp__github__search_users"
    ],
```

- [ ] **Step 3: ask 배열 끝에 MCP write tool 21개 추가** (Edit tool)

기존 ask 배열 마지막 항목:
```json
      "Bash(docker rmi:*)"
    ],
```

신규:
```json
      "Bash(docker rmi:*)",
      "mcp__github__create_branch",
      "mcp__github__create_or_update_file",
      "mcp__github__create_pull_request",
      "mcp__github__create_repository",
      "mcp__github__delete_file",
      "mcp__github__update_pull_request",
      "mcp__github__merge_pull_request",
      "mcp__github__add_comment_to_pending_review",
      "mcp__github__add_issue_comment",
      "mcp__github__add_reply_to_pull_request_comment",
      "mcp__github__pull_request_review_write",
      "mcp__github__push_files",
      "mcp__github__issue_write",
      "mcp__github__sub_issue_write",
      "mcp__github__fork_repository",
      "mcp__github__request_copilot_review",
      "mcp__github__run_secret_scanning",
      "mcp__sentry__update_issue",
      "mcp__sentry__update_project",
      "mcp__sentry__create_dsn",
      "mcp__sentry__create_project",
      "mcp__sentry__create_team"
    ],
```

- [ ] **Step 4: JSON 문법 검증** (bash)

```bash
node -e "JSON.parse(require('fs').readFileSync('.claude/settings.local.json','utf8'))" \
  && echo "OK: settings.local.json valid"
```
Expected: `OK: settings.local.json valid`. parse error 면 trailing comma / 따옴표 누락.

- [ ] **Step 5: NO COMMIT** — settings.local.json 은 gitignored. 변경 사항은 즉시 활성화됨 (다음 권한 확인 시점부터).

> 다른 contributor 가 동일 권한 적용하려면 이 plan 또는 별도 docs (e.g.,
> `docs/ops/mcp-setup.md`) 의 가이드 따라 본인 settings.local.json 직접 갱신 필요.
> 이는 Claude Code 의 의도된 분리 — 권한은 개인 단위.

---

## Phase 2: Azure tenant 주입

### Task 2: Azure MCP user-scope 재등록 — 🚨 USER ACTION 필요

**Files:** `~/.claude.json` (자동 갱신 by `claude mcp` CLI, git 미추적)

- [ ] **Step 1: 현재 azure MCP config 백업 출력** (pwsh)

```pwsh
claude mcp get azure
```
Expected output:
```
azure:
  Scope: User config
  Status: ✓ Connected
  Type: stdio
  Command: npx
  Args: @azure/mcp@latest server start
  Environment:
```

- [ ] **Step 2: azure MCP 제거 + tenant env 포함 재등록** (pwsh)

```pwsh
claude mcp remove azure -s user
claude mcp add azure -s user `
  --env AZURE_TENANT_ID=f11653ca-c627-4753-ae79-ad0d9689dbc8 `
  -- npx @azure/mcp@latest server start
```

> tenant ID 는 비밀 아님 — Microsoft Entra ID directory identifier 이며 `az account show` 로
> 항상 노출되는 값.

- [ ] **Step 3: 재등록 확인** (pwsh)

```pwsh
claude mcp get azure
```
Expected: `Environment:` 다음 줄에 `AZURE_TENANT_ID=f11653...` 표시.

- [ ] **Step 4: 🚨 USER ACTION — Claude Code 재시작 안내**

사용자에게 보고:
> "Azure MCP 재등록 완료. 변경 사항 반영하려면 **Claude Code 를 재시작해주세요**
> (현재 세션 종료 후 새 세션 시작). 재시작 후 다음 명령으로 검증:
> `mcp__azure__subscription_list()` (인자 없음) → `isDefault: true` 인 subscription 반환.
> 검증 통과하면 Task 3 으로 진행."

**작업 잠시 정지.** 사용자가 재시작 + 검증 결과 회신할 때까지 대기.

- [ ] **Step 5: (재시작 후) 검증** (deferred tool 이므로 ToolSearch 먼저)

```
ToolSearch query="select:mcp__azure__subscription_list"
mcp__azure__subscription_list()    # 인자 없음
```
Expected: `subscriptions: [{"subscriptionId": "6890144c-...", "isDefault": true, ...}]`

검증 실패 시:
- `~/.claude.json` 의 `mcpServers.azure.env.AZURE_TENANT_ID` 확인
- 다른 Claude Code instance 가 열려 있는지 확인 (충돌 가능)

**No commit.** (git 미추적 파일 변경만)

---

## Phase 3: 슬래시 명령

### Task 3: `/verify-deploy` 슬래시 명령

**Files:** Create `.claude/commands/verify-deploy.md`

- [ ] **Step 1: `.claude/commands/` 디렉토리 확인** (pwsh)

```pwsh
Test-Path .claude/commands
```
Expected: `False` 일 가능성 (없으면 다음 step 에서 생성).

- [ ] **Step 2: 디렉토리 생성** (pwsh, Step 1 이 False 인 경우만)

```pwsh
New-Item -ItemType Directory -Force .claude/commands | Out-Null
```

- [ ] **Step 3: `.claude/commands/verify-deploy.md` 작성** (Write tool)

```markdown
---
description: Phase 5 운영 검증을 MCP 로 자동 수행 (alembic head + 스키마 컬럼 + Sentry 신규 issue)
allowed-tools: mcp__azure__containerapps, mcp__azure__postgres, mcp__sentry__search_issues, mcp__sentry__search_events, Read, Glob, Grep, Edit
argument-hint: <inc-id> [--columns user_profiles.rest_day,user_profiles.foo] [--since 24h]
---

INC `$ARGUMENTS` 에 대한 Phase 5 운영 검증을 수행합니다.

## 사전 확인
- `$ARGUMENTS` 첫 토큰은 INC ID (형식 `INC-YYYY-MM-DD-NN`). 아니면 오류 보고 후 중단.
- 옵션 `--columns <table.col,...>` 와 `--since <duration>` 파싱. 미지정 시:
  - columns: `docs/ops/incident-log.md` 의 해당 INC 섹션에서 "관련 컬럼:" 라인 정규식 추출. 못 찾으면 사용자에게 질문.
  - since: `24h` (Sentry 검색 기간 기본값)

## 검증 단계 (모두 시도, stop-on-failure 아님)

### 1. Alembic head 확인 (운영 Container App)
`mcp__azure__containerapps` 의 exec 기능으로 다음 실행:
- subscription: `6890144c-c79e-46fc-a830-33335e8b4165`
- resourceGroup: `apps`
- containerAppName: `eundunhealth-api`
- command: `alembic current`

기대: output 에 12자 hex hash + ` (head)` 표시. 실패면 Container App 재배포 미완 / DB 연결 실패.

### 2. 스키마 컬럼 실재 확인
`mcp__azure__postgres` 로 다음 쿼리 실행:
```sql
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE (table_name, column_name) IN ( {{columns_tuple}} );
```
- PG firewall 통과 가정 (이미 Container App IP allow). 실패 시 `mcp__azure__containerapps` exec fallback (`python -c "..."` 형태).

기대: 요청한 모든 컬럼 row 반환. 빠진 컬럼은 ✗.

### 3. Sentry 신규 issue 확인
`mcp__sentry__search_issues` 호출:
- organizationSlug: `eundunhealth`
- projectSlug: `eundunhealth-backend`
- query 키워드 union: `["rest_day", "UndefinedColumn", "ProgrammingError", "$ARGUMENTS의 첫 토큰"]`
- statsPeriod: `--since` 값 (default `24h`)

기대: 0건. 1건 이상이면 issue ID + title + 발생 횟수 + permalink 보고.

## 결과 보고 (markdown table)
```
| Check | Result | Detail |
|---|---|---|
| alembic head | ✓ / ✗ | `<hash> (head)` 또는 에러 |
| <table.col> | ✓ / ✗ | <type>, NULL/NOT NULL, default <default> |
| Sentry <since> | ✓ / ✗ | 0 new issues / <count> issues + links |
```

## 통과 시 후속 액션
모든 항목 ✓ 이면 다음 markdown 한 줄을 `docs/ops/incident-log.md` 의 해당 INC 섹션 끝에
Edit tool 로 제안 (자동 commit 하지 않음 — 사용자가 검토 후 적용):

```
- **검증 완료**: YYYY-MM-DD HH:MM KST (자동 검증, /verify-deploy)
```

## 실패 시 troubleshooting hint
각 ✗ 항목마다 다음 액션 제안:
- alembic head 불일치 → `az containerapp logs show --tail 100 | Select-String "alembic"` 로 entrypoint 로그 확인
- 컬럼 누락 → 마이그레이션 PR 머지 + Container App 재시작 (`az containerapp restart`) 확인
- Sentry 신규 issue → issue permalink 열어서 stack trace 확인, 필요 시 `mcp__sentry__analyze_issue_with_seer` 추가 실행
```

- [ ] **Step 4: 슬래시 명령 등록 확인** (Claude Code 가 .claude/commands 자동 스캔)

별도 reload 불필요. 이번 세션에서는 file 추가만으로는 안 잡힐 수 있음 — 검증은 Task 7 에서.

- [ ] **Step 5: commit** (bash)

```bash
git add .claude/commands/verify-deploy.md
git commit -m "$(cat <<'EOF'
feat(claude): /verify-deploy 슬래시 명령 — Phase 5 자동 검증

INC 의 Phase 5 운영 검증 (alembic head + 스키마 컬럼 + Sentry 신규 issue) 을
MCP 로 1-command 자동화. 사용자가 머지 후 '/verify-deploy INC-YYYY-MM-DD-NN'
호출하면 3단계 검증 후 markdown table 보고 + incident-log.md 업데이트 patch
제안.

design: docs/plans/2026-05-28-mcp-integration-setup-design.md §3.3

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: SecretRef 가드 hook

### Task 4: `scripts/hooks/secretref-guard.sh` + `.gitattributes` + settings.json 등록

**Files:**
- Create `.gitattributes` (루트, LF 강제)
- Create `scripts/hooks/secretref-guard.sh`
- Modify `.claude/settings.json` (hooks 섹션에 PreToolUse 추가)

- [ ] **Step 1: 루트 `.gitattributes` 작성** (Write tool)

호스트 `git config core.autocrlf=true` 환경에서 shell script 가 CRLF 로 체크아웃되면
Bash tool 이 인터프리터 못 찾음. backend/.gitattributes 와 같은 패턴 루트에 적용.

내용:
```
# shell scripts: 항상 LF (Bash tool 호환)
*.sh text eol=lf
scripts/**/*.sh text eol=lf
```

> 기존 backend/.gitattributes 는 backend/ 하위에만 적용. 루트 .gitattributes 가 우선시되며
> 더 넓은 범위 커버.

- [ ] **Step 2: `scripts/hooks/` 디렉토리 생성** (pwsh)

```pwsh
New-Item -ItemType Directory -Force scripts/hooks | Out-Null
```

- [ ] **Step 3: `scripts/hooks/secretref-guard.sh` 작성** (Write tool)

```bash
#!/bin/bash
# PreToolUse hook: backend.yml 신규 secretref 가 Container App 에 등록되어 있는지 검증
# 룰 6 (CLAUDE.md) 위반 commit 차단. fail-open 설계 (운영 차단 회피).
#
# Hook contract (Claude Code):
#   stdin: { "tool_name": "Bash", "tool_input": { "command": "..." }, ... }
#   exit 0: pass, exit 2: block + stderr message to user

set -u  # set -e 는 의도적 미사용 (fail-open 위해 명시적 처리)

# Hook payload 읽기 (jq 없으면 grep fallback)
PAYLOAD=$(cat)

# 1. tool_name == Bash 확인 (다른 tool 은 즉시 통과)
TOOL_NAME=$(echo "$PAYLOAD" | grep -oE '"tool_name"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed -E 's/.*"([^"]+)"$/\1/')
[ "$TOOL_NAME" = "Bash" ] || exit 0

# 2. command 에 'git commit' 포함 확인
COMMAND=$(echo "$PAYLOAD" | grep -oE '"command"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed -E 's/.*"([^"]+)"$/\1/')
case "$COMMAND" in
  *"git commit"*) ;;
  *) exit 0 ;;
esac

# 3. backend.yml 변경 여부 확인 (staged + unstaged 모두 — commit -a 케이스)
cd "C:/programming/apps/eundunHealth" 2>/dev/null || exit 0
CHANGED=$(git diff --cached --name-only 2>/dev/null; git diff --name-only 2>/dev/null)
echo "$CHANGED" | grep -q "^\.github/workflows/backend\.yml$" || exit 0

# 4. 신규 secretref 추출 (+ prefix 라인만, - 제외, context line 도 제외)
NEW_SECRETREFS=$(git diff --cached -- .github/workflows/backend.yml 2>/dev/null \
  | grep -E "^\+[^+].*secretref:" \
  | grep -oE "secretref:[a-zA-Z0-9_-]+" \
  | sed 's/secretref://' \
  | sort -u)

[ -z "$NEW_SECRETREFS" ] && exit 0

# 5. Container App 의 등록된 secret 목록 조회 (timeout 10s)
REGISTERED=$(timeout 10 az containerapp secret list \
  -n eundunhealth-api -g apps --query "[].name" -o tsv 2>/dev/null)

# 5a. az 실패 시 fail-open (운영 차단 회피)
if [ -z "$REGISTERED" ]; then
  echo "[secretref-guard] WARN: az containerapp secret list 실패 — fail-open. backend.yml 변경 사항 deploy 단계 (backend.yml CI step) 에서 검증됨." >&2
  exit 0
fi

# 6. 미등록 secretref 검출
MISSING=""
while IFS= read -r ref; do
  echo "$REGISTERED" | grep -q "^${ref}$" || MISSING="${MISSING}${ref}\n"
done <<< "$NEW_SECRETREFS"

if [ -n "$MISSING" ]; then
  echo "" >&2
  echo "🚫 룰 6 위반 (CLAUDE.md): backend.yml 에 미등록 secretref 추가됨" >&2
  echo "" >&2
  echo "누락된 secret(s):" >&2
  printf "  - %s\n" $(echo -e "$MISSING" | sed '/^$/d') >&2
  echo "" >&2
  echo "해결: commit 전에 다음을 먼저 실행:" >&2
  echo "  az containerapp secret set --name eundunhealth-api --resource-group apps \\" >&2
  echo "    --secrets \"<name>=<value>\"" >&2
  echo "" >&2
  echo "이후 docs/ops/operations-snapshot.md §2 Secrets 목록도 갱신." >&2
  exit 2
fi

exit 0
```

- [ ] **Step 4: 실행 권한 부여 + LF 확정** (bash)

```bash
git add .gitattributes
git add scripts/hooks/secretref-guard.sh
git update-index --chmod=+x scripts/hooks/secretref-guard.sh
git ls-files --stage scripts/hooks/secretref-guard.sh
```
Expected: `100755 <sha> 0\tscripts/hooks/secretref-guard.sh`

- [ ] **Step 5: `.claude/settings.json` 의 hooks 섹션에 PreToolUse 추가** (Edit tool)

현 settings.json 의 `hooks` 객체에는 SessionStart / PreCompact 만 있음. PreToolUse 추가.

기존:
```json
  "hooks": {
    "SessionStart": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "bash C:/programming/apps/eundunHealth/scripts/claude-context.sh",
            "timeout": 10
          }
        ]
      }
    ],
    "PreCompact": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "bash C:/programming/apps/eundunHealth/scripts/claude-precompact.sh",
            "timeout": 10
          }
        ]
      }
    ]
  }
```

> ⚠️ 위 JSON 은 현재 `.claude/settings.local.json` 의 hooks (line 233-256). `.claude/settings.json`
> (line 1-7) 은 enabledPlugins 만 있어 hooks 가 없음. **PreToolUse hook 은
> .claude/settings.json (project-shared) 에 추가하는 게 적절** — 다른 개발자도 자동 적용.
> settings.local.json 은 개인 권한 / 로컬 hook 용도.

`.claude/settings.json` 을 다음으로 교체 (enabledPlugins 유지 + hooks 신설):
```json
{
  "enabledPlugins": {
    "kotlin-lsp@claude-plugins-official": true,
    "vtsls@claude-code-lsps": true
  },
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "bash C:/programming/apps/eundunHealth/scripts/hooks/secretref-guard.sh",
            "timeout": 15
          }
        ]
      }
    ]
  }
}
```

- [ ] **Step 6: 검증 — mock 시나리오로 hook 동작 확인** (bash)

mock secretref 추가 → commit 시도 → block 확인 → revert. **사용자 동의 받고 진행**:

```bash
# 임시 변경
cat >> .github/workflows/backend.yml <<'EOF'

# === SECRETREF-GUARD TEST (revert immediately) ===
# FAKE_SECRET=secretref:fake-not-exist-12345
EOF
git add .github/workflows/backend.yml
git commit -m "test(guard): verify secretref-guard blocks unregistered secret"
# Expected: hook 가 stderr 에 "🚫 룰 6 위반" + "fake-not-exist-12345" 출력하고 commit 차단

# revert
git restore --staged .github/workflows/backend.yml
git checkout -- .github/workflows/backend.yml
```

검증 실패 시 (hook 가 통과시킴):
- `bash scripts/hooks/secretref-guard.sh < <(echo '{"tool_name":"Bash","tool_input":{"command":"git commit -m test"}}')`
  직접 실행해서 디버깅
- `git diff --cached` 결과 확인
- `az` 인증 만료 가능성 (`az account show`)

> **🚨 USER ACTION 가능 영역**: 위 mock 검증을 Claude 가 직접 하는 게 자연스럽지만, hook 가
> Claude 의 Bash tool 호출 자체를 막는 경우라 self-test 가 까다로움. 사용자가 직접 PowerShell
> terminal 에서 위 명령 실행 권장 (5분).

- [ ] **Step 7: commit** (bash — heredoc)

```bash
git add .gitattributes scripts/hooks/secretref-guard.sh .claude/settings.json
git commit -m "$(cat <<'EOF'
feat(hooks): secretref-guard PreToolUse hook (룰 6 자동 차단)

backend.yml 에 미등록 secretref 가 추가된 commit 을 차단. INC-2026-04-XX 의
ContainerAppSecretRefNotFound 재발 방지 — 룰 6 의 1차 자동 가드 (deploy 단계
backend.yml CI step 은 2차 방어 유지).

설계:
- Trigger: Bash + 'git commit' 포함 + backend.yml 변경 시에만 활성
- Az 실패 / 네트워크 오류 시 fail-open (운영 차단 회피)
- timeout 15s, exit 2 → Claude Code hook contract 로 tool 차단
- .gitattributes 루트 신설: *.sh eol=lf (Windows CRLF 안전망)

design: docs/plans/2026-05-28-mcp-integration-setup-design.md §3.4

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 5: SessionStart 확장

### Task 5: `scripts/claude-context.sh` 의 보류 검증 섹션 추가

**Files:** Modify `scripts/claude-context.sh`

- [ ] **Step 1: 기존 파일 끝에 새 섹션 append** (Edit tool)

기존 마지막 라인 (line 22):
```bash
grep -E "^(kotlin|agp|ktor|composeBom|sentry)" gradle/libs.versions.toml 2>/dev/null | head -10
```

다음을 끝에 추가:
```bash

echo "=== Pending Phase 5 Verifications ==="
# 최근 7일 main 머지 commit 중 INC 등재 있고 "검증 완료" 라인 부재인 항목 찾기
PENDING_OUTPUT=$(
  git log main --merges --since="7 days ago" --pretty=format:'%h %s' 2>/dev/null | \
  while IFS= read -r line; do
    # subject 에서 PR 번호 추출 (예: "Merge pull request #45 from ...")
    PR_NUM=$(echo "$line" | grep -oE '#[0-9]+' | head -1 | tr -d '#')
    [ -z "$PR_NUM" ] && continue

    # PR body 에서 INC ID 추출 (gh CLI 사용, 2s timeout)
    INC_ID=$(timeout 5 gh pr view "$PR_NUM" --json body --jq '.body' 2>/dev/null | \
      grep -oE 'INC-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}' | head -1)
    [ -z "$INC_ID" ] && continue

    # incident-log.md 에서 해당 INC 섹션의 "검증 완료" 라인 존재 확인
    if [ -f docs/ops/incident-log.md ]; then
      VERIFIED=$(grep -A 200 "^### .*${INC_ID}" docs/ops/incident-log.md 2>/dev/null | \
        grep -B 1000 -m1 "^### " | grep -c "검증 완료" || true)
      if [ "$VERIFIED" = "0" ]; then
        MERGE_DATE=$(echo "$line" | grep -oE '^[a-f0-9]+' | head -1 | \
          xargs -I {} git show -s --format=%ci {} 2>/dev/null | cut -d' ' -f1)
        echo "- ${INC_ID} (PR #${PR_NUM}, merged ${MERGE_DATE}): /verify-deploy ${INC_ID}"
      fi
    fi
  done
)

if [ -n "$PENDING_OUTPUT" ]; then
  echo "$PENDING_OUTPUT"
else
  echo "(no pending verifications in last 7 days)"
fi
```

- [ ] **Step 2: SessionStart hook timeout 상향** (Edit tool — `.claude/settings.local.json`, NO COMMIT — gitignored)

현재 `.claude/settings.local.json` 의 SessionStart hooks 섹션:
```json
            "command": "bash C:/programming/apps/eundunHealth/scripts/claude-context.sh",
            "timeout": 10
```

`timeout` 을 `20` 으로 변경:
```json
            "command": "bash C:/programming/apps/eundunHealth/scripts/claude-context.sh",
            "timeout": 20
```

(gh pr view 호출이 추가되어 기존 10s 로는 부족 가능. 20s 가 safe margin.)

> NO COMMIT. settings.local.json 은 gitignored. 다른 contributor 는 본인 settings.local.json
> 의 SessionStart timeout 동일하게 갱신 필요.

- [ ] **Step 3: 수동 실행 검증** (bash)

```bash
bash scripts/claude-context.sh
```
Expected: 기존 출력 + `=== Pending Phase 5 Verifications ===` 섹션 추가.
- 최근 7일 머지 PR 중 INC 관련 없으면 `(no pending verifications in last 7 days)`
- 있으면 list

검증 실패 모드:
- `gh: command not found` → `gh auth status` 확인
- timeout — gh 호출이 5s 넘으면 해당 PR skip (안전한 fail mode)

- [ ] **Step 4: commit** (bash — settings.local.json 제외, gitignored)

```bash
git add scripts/claude-context.sh
git commit -m "$(cat <<'EOF'
feat(hooks): SessionStart 에 보류 Phase 5 검증 자동 리마인더

최근 7일 main 머지 PR 중 INC 등재 + incident-log.md 의 '검증 완료' 라인 부재인
항목을 SessionStart 시점에 자동 리스트업. 사용자가 머지 후 검증을 잊는 패턴
완화 — '/verify-deploy <INC-ID>' 즉시 호출 가능.

판정 로직:
- git log main --merges --since='7 days ago' → PR 번호 추출
- gh pr view --json body → INC ID 정규식 추출 (5s timeout)
- docs/ops/incident-log.md 의 해당 INC 섹션에 '검증 완료' grep
- pending 0건이면 안내 한 줄, 1건+ 면 list

SessionStart hook timeout 10s → 20s 상향 (gh 호출 안전 마진).

design: docs/plans/2026-05-28-mcp-integration-setup-design.md §3.5

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 6: 문서 갱신

### Task 6: CLAUDE.md + operations-snapshot.md

**Files:** Modify `CLAUDE.md`, `docs/ops/operations-snapshot.md`

- [ ] **Step 1: CLAUDE.md "### 자동화 스크립트 (`scripts/`)" 섹션 끝에 추가** (Edit tool)

기존:
```markdown
- `scripts/claude-context.sh` / `claude-precompact.sh` — SessionStart/PreCompact 훅
```

다음 두 항목 추가:
```markdown
- `scripts/claude-context.sh` / `claude-precompact.sh` — SessionStart/PreCompact 훅
- `scripts/hooks/secretref-guard.sh` — git commit 시 backend.yml 신규 secretref 가 Container
  App 에 등록됐는지 자동 검증 (룰 6 1차 가드). PreToolUse hook 으로 자동 실행. fail-open.

### Claude Code 슬래시 명령 (`.claude/commands/`)

- `/verify-deploy <inc-id>` — MCP (Sentry/Azure) 로 Phase 5 운영 검증 자동화 (alembic head
  + 스키마 컬럼 + Sentry 신규 issue). INC 별 검증 1-command. 자세한 내용:
  `docs/plans/2026-05-28-mcp-integration-setup-design.md` §3.3.
```

- [ ] **Step 2: operations-snapshot.md §2 다음에 §3 신설** (Edit tool)

먼저 현재 §2 / §3 위치 확인 (Read tool 로 부분 확인 권장):

```pwsh
Get-Content docs/ops/operations-snapshot.md | Select-String "^##" | Select-Object -First 10
```

`## 2.` 섹션 끝 다음에 다음 신설 (이후 §3, §4, ... 번호 +1 시프트는 별도 — 헤더 번호 미사용
스타일이면 단순 ## 추가):

```markdown
## 3. MCP 통합 (2026-05-28)

Claude Code MCP 서버 4종 운영 활용:

| MCP 서버 | 주 사용 시나리오 | 인증 |
|---|---|---|
| `mcp__sentry__*` | INC root cause 분석, Phase 5 검증, 신규 issue 알림 | OAuth (qkr133456@gmail.com) |
| `mcp__azure__*` | Container App 상태/로그, ACR 정리, PG 쿼리, secret list 검증 | az CLI shared, `AZURE_TENANT_ID` env 주입 |
| `mcp__github__*` | PR 작성/조회, CI run polling, 코드 검색 | GitHub Copilot OAuth (gunnysis) |
| `mcp__plugin_context7_context7__*` | 공식 docs fetch (Alembic, PG, Docker, Container Apps) | 없음 (public) |

권한 분리 (`.claude/settings.local.json`):
- read-only tool 56개 → allow (prompt 없음)
- write tool 21개 → ask (실수 차단)

자동화:
- `/verify-deploy <INC-ID>` — Phase 5 운영 검증 1-command
- `scripts/hooks/secretref-guard.sh` — 룰 6 commit-time 가드 (PreToolUse hook)
- `scripts/claude-context.sh` — SessionStart 보류 검증 리마인더

자세한 설계: `docs/plans/2026-05-28-mcp-integration-setup-design.md`.
```

> 기존 ## 번호 매김 스타일 확인 후 일관성 유지. 만약 `## Secrets` 처럼 번호 없는 스타일이면
> `## MCP 통합 (2026-05-28)` 로 변경.

- [ ] **Step 3: commit** (bash)

```bash
git add CLAUDE.md docs/ops/operations-snapshot.md
git commit -m "$(cat <<'EOF'
docs: CLAUDE.md / operations-snapshot.md 에 MCP 자동화 등재

자동화 스크립트 섹션에 secretref-guard.sh + /verify-deploy 슬래시 명령 추가.
operations-snapshot.md 에 MCP 서버 4종 + 권한 분리 + 자동화 3가지 정리 신설.

design: docs/plans/2026-05-28-mcp-integration-setup-design.md §3.6

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 7: E2E 회귀

### Task 7: 각 컴포넌트 1회 검증

**Files:** 변경 없음

- [ ] **Step 1: Permissions — Sentry tool 호출 시 prompt 없는지** (deferred tool)

```
ToolSearch query="select:mcp__sentry__search_issues"
mcp__sentry__search_issues(organizationSlug="eundunhealth", query="is:unresolved", limit=1)
```
Expected: 결과 반환 (prompt 없음). prompt 뜨면 Task 1 의 settings.local.json 적용 안 됨.

- [ ] **Step 2: Azure tenant — 인자 없이 subscription_list** (Task 2 이후 가능)

```
mcp__azure__subscription_list()
```
Expected: `isDefault: true` 인 subscription 반환. 빈 array 면 Task 2 미적용.

- [ ] **Step 3: /verify-deploy 슬래시 명령 — 더미 호출**

```
/verify-deploy INC-2026-05-27-01
```
Expected: 슬래시 명령이 인식되고 verify-deploy.md 내용대로 동작. 명령 인식 안 되면
`.claude/commands/` 위치 확인 + Claude Code 슬래시 명령 reload.

> 단, INC-2026-05-27-01 의 Phase 5 가 실제로 통과될지는 schema drift recovery plan
> 완료 후에만 valid. 이 task 에서는 슬래시 명령 invoke 자체와 검증 단계 시작 여부만 확인.

- [ ] **Step 4: secretref-guard hook — mock 시나리오 (이미 Task 4 Step 6 에서 수행)**

사용자가 수동 검증 안 했다면 지금 1회 수행. 결과 보고.

- [ ] **Step 5: SessionStart 확장 — 새 세션 시작 확인** (🚨 USER ACTION)

사용자에게 보고:
> "Task 5 검증: Claude Code 를 한 번 더 재시작 (또는 새 세션 열기) 해서 SessionStart 출력에
> `=== Pending Phase 5 Verifications ===` 섹션이 보이는지 확인해주세요. (INC-2026-05-27-01
> 의 fix PR 이 아직 머지 안 됐으면 `(no pending verifications in last 7 days)` 표시 정상)"

**No commit.** 검증만 수행.

---

## Phase 8: PR 생성

### Task 8: push + PR

- [ ] **Step 1: 커밋 로그 확인** (pwsh)

```pwsh
git log --oneline main..HEAD
```
Expected: 6 commits — design (`0272d0c`) + Task 0.5 (.gitignore) + Task 3 (verify-deploy) +
Task 4 (secretref-guard + settings.json + .gitattributes) + Task 5 (claude-context.sh) +
Task 6 (CLAUDE.md + operations-snapshot).
Task 1 / Task 2 / Task 5 timeout 은 git 외부 변경 (no commit).

- [ ] **Step 2: push** (bash)

```bash
git push -u origin feat/mcp-integration-setup
```

- [ ] **Step 3: PR 생성** (bash — heredoc 또는 mcp__github__create_pull_request)

옵션 A — gh CLI:
```bash
gh pr create --title "feat(ops): MCP 통합 + 운영 자동화 (Phase 5 / 룰 6 / SessionStart)" \
  --body "$(cat <<'EOF'
## Summary
- Sentry / Azure / GitHub / Context7 MCP 4종을 일관 운영 자동화에 통합
- Phase 5 운영 검증을 `/verify-deploy <INC-ID>` 1-command 화
- 룰 6 secretref 위반 commit 을 PreToolUse hook 으로 자동 차단 (fail-open)
- SessionStart 에 보류 검증 자동 리마인더

## Design / Plan
- Design: `docs/plans/2026-05-28-mcp-integration-setup-design.md`
- Plan: `docs/plans/2026-05-28-mcp-integration-setup-plan.md`

## Components
1. `.claude/settings.local.json` — MCP read 56개 allow, write 21개 ask
2. Azure MCP user-scope `AZURE_TENANT_ID` env 주입 (git 외부 — `claude mcp` CLI)
3. `.claude/commands/verify-deploy.md` — Phase 5 자동화 슬래시 명령
4. `scripts/hooks/secretref-guard.sh` + `.claude/settings.json` PreToolUse 등록
5. `scripts/claude-context.sh` — Pending Phase 5 Verifications 섹션
6. `CLAUDE.md` + `docs/ops/operations-snapshot.md` 갱신

## Test plan
- [x] `claude mcp list` → 9개 모두 Connected
- [x] `mcp__sentry__whoami` / `mcp__github__get_me` / `mcp__azure__subscription_list` 인증 통과
- [x] Sentry/GitHub MCP read tool 호출 시 prompt 없음
- [ ] **사용자 확인 필요**: Azure MCP 재등록 + Claude Code 재시작 후 `subscription_list` 인자 없이 결과 반환
- [x] secretref-guard hook mock 시나리오: 미등록 secretref commit 시도 → exit 2 + stderr 메시지
- [ ] **사용자 확인 필요**: 새 세션에서 `=== Pending Phase 5 Verifications ===` 섹션 노출

## 사용자 추가 입력
**비밀/토큰 추가 입력 없음** (Sentry/GitHub/Azure 인증 모두 통과 확인됨).
- Component 2 적용 후 Claude Code 재시작 1회
- Component 4 첫 mock 검증 1회 (5분)

## References
- INC-2026-05-27-01 schema drift recovery plan (Phase 5 자동화의 trigger)
- pending-mcp-integrations memory (현재 MCP 연결 상태 + plan task 매핑)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

옵션 B — MCP (write 라 ask 발생 예상):
```
ToolSearch query="select:mcp__github__create_pull_request"
mcp__github__create_pull_request(...)
```

- [ ] **Step 4: PR URL 사용자에게 보고**

---

## 잔여 리스크 / 후속 작업

1. **주기적 Sentry 모니터링 (option 4)** — 별도 PR 로 `ScheduleWakeup` 기반 도입 검토.
2. **MCP write 권한 세분화** — Azure MCP 의 단일 tool 안 read/write 혼재 문제 (e.g.,
   `containerapps` 안에 list/show/update/delete 모두). 현재 PR 리뷰 + 사용자 확인이 방어선.
3. **/verify-deploy 결과 PR 댓글 자동화** — `mcp__github__add_comment_to_pending_review` 활용
   해서 검증 결과를 PR 에 자동 게시. 별도 PR.
4. **secretref-guard fail-open 모니터링** — hook fail-open 시 silent. Sentry/로그 통해
   가시성 추가 검토.

---

## 참고

- Design: `docs/plans/2026-05-28-mcp-integration-setup-design.md`
- 관련 INC: `docs/plans/2026-05-27-schema-drift-recovery-design.md` (이 작업의 trigger)
- 메모리: pending-mcp-integrations (current MCP state)
- 메모리: claude-code-mcp-install-gotchas (`@azure/mcp server start` 함정)
