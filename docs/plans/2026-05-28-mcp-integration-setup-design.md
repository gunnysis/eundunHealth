---
type: design
status: shipped
pr: 46
related_inc: null
supersedes: null
target_version: infra-only
tags: [ops, mcp, automation]
---

# MCP 통합 + 운영 자동화 Design

**작성일:** 2026-05-28
**작성자:** Claude (with gunny)
**브랜치:** `feat/mcp-integration-setup`
**관련 plan:** `docs/plans/2026-05-27-schema-drift-recovery-plan.md` (INC-2026-05-27-01)
**관련 memory:** [[pending-mcp-integrations]] (`~/.claude/projects/.../memory/pending-mcp-integrations.md`)

---

## 1. 배경 / 동기

INC-2026-05-27-01 schema drift 사고 복구 플랜 §5 Phase 5 ("운영자 확인 필요") 항목이
사람 의존적이라 검증 누락 위험이 있다. 한편 Sentry / Azure / GitHub / Context7 MCP
4개가 이번 세션에서 모두 정상 연결 + 인증 확인됨 → 자동화 기반은 이미 마련됨.

이 작업은 **MCP 활용도를 코드화**해서 (1) Phase 5 검증을 1-command 로, (2) 룰 6
(secretref 가드) 위반을 commit 단계에서 차단, (3) 보류된 검증 항목을 SessionStart 에
자동 노출 — 3가지를 한 번에 처리한다.

### 1.1 현재 상태 (2026-05-28 18:00 KST 조사)

| 항목 | 상태 |
|---|---|
| Sentry MCP 인증 | ✓ qkr133456@gmail.com (`mcp__sentry__whoami` 통과) |
| GitHub MCP 인증 | ✓ gunnysis (`mcp__github__get_me` 통과) |
| Azure MCP 인증 | ⚠️ az CLI 로그인됨이나 default tenant 자동 해석 실패 — 매 호출 `tenant=` 명시 필요 |
| Context7 MCP | ✓ 공개 (인증 불필요) |
| `.claude/settings.local.json` MCP permissions | Context7 2개만 allow, 나머지 매 호출 prompt |
| 자동화 hooks | SessionStart / PreCompact 만 존재, MCP 관련 없음 |

### 1.2 의사 결정 기록

| 결정 | 옵션 | 선택 | 근거 |
|---|---|---|---|
| Scope | A 최소 / B 중간 / C 풀세트 | **C** | 룰 6 / Phase 5 / SessionStart 3가지 동시 효과 |
| 자동화 hook | 1 슬래시 / 2 PreToolUse / 3 SessionStart / 4 주기 Sentry | **1+2+3** | 4번은 비용/노이즈 대비 효용 낮음 — 별도 PR 로 분리 가능 |

---

## 2. Scope

### 2.1 In-scope (이 PR)

1. `.claude/settings.local.json` permissions allowlist 확장 (MCP read-only)
2. Azure MCP user-scope 재등록 → `AZURE_TENANT_ID` env 주입 (Claude Code 재시작 1회 필요)
3. `.claude/commands/verify-deploy.md` 슬래시 명령 신설
4. `scripts/hooks/secretref-guard.sh` + `.claude/settings.json` PreToolUse 등록
5. `scripts/claude-context.sh` 의 "Pending Phase 5 Verifications" 섹션 추가
6. `CLAUDE.md` 의 "자동화 스크립트" 섹션에 신규 hook + 슬래시 명령 등재
7. `docs/ops/operations-snapshot.md` MCP 섹션 신설 (어떤 MCP 가 어떤 운영 task 에 매핑되는지)

### 2.2 Out-of-scope (별도 PR / 후속)

- **주기적 Sentry 모니터링 (option 4)** — 비용 + 알람 피로 우려, INC 발생 빈도가 안정화된 후 재검토
- **MCP write 권한 자동 allow** — `create_pull_request`, `merge_pull_request`, `add_comment_*`, `containerapp secret set` MCP 등가 등은 ask 유지 (실수 위험)
- **Plan markdown 의 bash 명령 MCP 치환** — 플랜 문서는 fallback 용으로 유지, 실제 실행 시점에 MCP 우선 사용 (의사결정은 사용자/Claude 가)
- **WikiDocs/Tavily 활용 패턴 정립** — 현 INC 에 직접 연관 없음

---

## 3. 컴포넌트 설계

### 3.1 Permissions allowlist (`.claude/settings.local.json`)

**현 상태:**
```json
"allow": [
  ...
  "mcp__plugin_context7_context7__resolve-library-id",
  "mcp__plugin_context7_context7__query-docs"
]
```

**추가 (read-only 만):**
```
# Sentry — 모두 read
mcp__sentry__whoami
mcp__sentry__find_organizations
mcp__sentry__find_projects
mcp__sentry__find_releases
mcp__sentry__find_dsns
mcp__sentry__find_teams
mcp__sentry__search_issues
mcp__sentry__search_events
mcp__sentry__search_issue_events
mcp__sentry__search_docs
mcp__sentry__get_doc
mcp__sentry__get_event_attachment
mcp__sentry__get_issue_tag_values
mcp__sentry__get_profile_details
mcp__sentry__get_replay_details
mcp__sentry__get_sentry_resource
mcp__sentry__analyze_issue_with_seer

# Azure — read-only (네임스페이스 매크로 패턴 X, 개별 명시)
mcp__azure__subscription_list
mcp__azure__group_list
mcp__azure__group_resource_list
mcp__azure__containerapps
mcp__azure__acr
mcp__azure__postgres
mcp__azure__monitor
mcp__azure__keyvault
mcp__azure__applicationinsights
mcp__azure__resourcehealth
mcp__azure__role
mcp__azure__quota
mcp__azure__pricing
mcp__azure__documentation
mcp__azure__get_azure_bestpractices

# GitHub — read 만 (write 는 ask)
mcp__github__get_me
mcp__github__get_commit
mcp__github__get_file_contents
mcp__github__get_label
mcp__github__get_latest_release
mcp__github__get_release_by_tag
mcp__github__get_tag
mcp__github__get_team_members
mcp__github__get_teams
mcp__github__list_branches
mcp__github__list_commits
mcp__github__list_issues
mcp__github__list_issue_types
mcp__github__list_pull_requests
mcp__github__list_releases
mcp__github__list_repository_collaborators
mcp__github__list_tags
mcp__github__issue_read
mcp__github__pull_request_read
mcp__github__search_code
mcp__github__search_issues
mcp__github__search_pull_requests
mcp__github__search_repositories
mcp__github__search_users
```

**ask 로 분리 (write/destructive):**
```
mcp__github__create_branch
mcp__github__create_or_update_file
mcp__github__create_pull_request
mcp__github__create_repository
mcp__github__delete_file
mcp__github__update_pull_request
mcp__github__merge_pull_request
mcp__github__add_comment_to_pending_review
mcp__github__add_issue_comment
mcp__github__add_reply_to_pull_request_comment
mcp__github__pull_request_review_write
mcp__github__push_files
mcp__github__issue_write
mcp__github__sub_issue_write
mcp__github__fork_repository
mcp__github__request_copilot_review
mcp__github__run_secret_scanning
mcp__sentry__update_issue
mcp__sentry__update_project
mcp__sentry__create_dsn
mcp__sentry__create_project
mcp__sentry__create_team
```

> **참고:** Azure MCP 의 `containerapps` / `postgres` 같은 단일 tool 안에서 read/write 가 섞임
> (e.g., `containerapps` 안에 list / show / update / delete 모두 존재). MCP 권한 모델이
> tool 단위라 더 세분화 불가 → Claude 가 호출 직전 사용자에게 의도 확인 필요. 이는 Hook 4
> (룰 6 가드) 와 사용자의 PR 리뷰가 2차 방어선.

### 3.2 Azure tenant 자동 주입

**현 상태 (`claude mcp get azure`):**
```
Scope: User config
Command: npx
Args: @azure/mcp@latest server start
Environment: (empty)
```

**변경:**
```pwsh
claude mcp remove azure -s user
claude mcp add azure -s user `
  --env AZURE_TENANT_ID=f11653ca-c627-4753-ae79-ad0d9689dbc8 `
  -- npx @azure/mcp@latest server start
```

> `tenant` ID 는 민감 정보 아님 — Microsoft Entra ID tenant 식별자이며 user 의 organization
> directory ID. 이미 `az account show` 로 공개적으로 노출되는 값.

**검증:**
- Claude Code 재시작 후 `mcp__azure__subscription_list` (no args) → `isDefault: true` 인
  subscription 반환되어야 함
- 검증 안 되면 `~/.claude.json` 의 `mcpServers.azure.env` 가 정상 등록됐는지 확인

**대안 (검토했으나 채택 안 함):**
- 시스템 환경변수 `AZURE_TENANT_ID` 설정 — Windows 전역 영향 + 다른 az CLI 사용처에 영향
- MCP 호출마다 `tenant=` 명시 — 보일러플레이트, 잊기 쉬움
- `~/.claude/settings.json` 의 `env` — 이건 hook/Bash 환경용, MCP 서버에는 안 전달됨

### 3.3 `/verify-deploy` 슬래시 명령

**파일:** `.claude/commands/verify-deploy.md`

**Front matter:**
```markdown
---
description: Phase 5 운영 검증을 MCP 로 자동 수행 (alembic head + 스키마 컬럼 + Sentry 신규 issue)
allowed-tools: mcp__azure__containerapps, mcp__azure__postgres, mcp__sentry__search_issues, mcp__sentry__search_events, Read, Glob, Grep
argument-hint: <inc-id> [--columns user_profiles.rest_day,user_profiles.foo] [--since 24h]
---
```

**본문 (Claude 가 슬래시 호출 시 받는 prompt):**
1. `$1` = INC ID (필수). 형식 `INC-YYYY-MM-DD-NN`
2. `--columns` 옵션 미지정 시 `docs/ops/incident-log.md` 의 `$1` 섹션에서 "관련 컬럼" 자동 추출 시도
3. 검증 항목:
   - **alembic head 확인** — `mcp__azure__containerapps` 의 exec 기능으로
     `eundunhealth-api` 에서 `alembic current` 실행 (subscription/group/name 은 fixed)
     → output 에 `(head)` 표시 있는지
   - **스키마 컬럼 실재** — `mcp__azure__postgres` 로
     `SELECT column_name, is_nullable, column_default FROM information_schema.columns
      WHERE table_name = ? AND column_name = ?` (PG firewall 통과 가정;
     실패 시 containerapps exec 로 fallback)
   - **Sentry 신규 issue** — `mcp__sentry__search_issues` organization=eundunhealth,
     project=eundunhealth-backend, query 키워드 = `["rest_day", "UndefinedColumn",
     "ProgrammingError", "$1"]`, age=`--since` (default 24h). 결과 0 건이어야 통과
4. 결과 markdown table 출력:
   ```
   | Check | Result | Detail |
   |---|---|---|
   | alembic head | ✓ | `a1b2c3d4e5f6 (head)` |
   | rest_day 컬럼 | ✓ | NOT NULL, default 7 |
   | Sentry 24h | ✓ | 0 new issues matching keywords |
   ```
5. 모두 통과 시 `docs/ops/incident-log.md` 의 INC 섹션 끝에 "검증 완료: 2026-MM-DD HH:MM
   KST (자동 검증, /verify-deploy)" 한 줄 추가 patch 를 Edit tool 로 제안

**실패 시 동작:**
- 단계별 실패는 stop-on-first-failure 아님 — 모든 항목 시도 후 종합 보고
- table 의 ✗ 항목마다 troubleshooting hint (e.g., "alembic head 안 맞음 →
  `az containerapp logs show` 로 entrypoint 로그 확인" 같은 다음 액션)

### 3.4 SecretRef 가드 (PreToolUse hook)

**신규 파일:** `scripts/hooks/secretref-guard.sh`

**Trigger 조건 (settings.json hook matcher 로):**
```json
"PreToolUse": [
  {
    "matcher": "Bash",
    "hooks": [
      { "type": "command", "command": "bash C:/programming/apps/eundunHealth/scripts/hooks/secretref-guard.sh", "timeout": 15 }
    ]
  }
]
```

**스크립트 흐름:**
1. stdin 으로 받은 hook payload JSON 파싱 (`tool_input.command`)
2. command 가 `git commit` 포함 아니면 즉시 exit 0 (overhead 거의 없음)
3. `git diff --cached --name-only` → `.github/workflows/backend.yml` 포함 아니면 exit 0
4. `git diff --cached -- .github/workflows/backend.yml` 에서 새로 추가된 `secretref:<name>`
   추출 (`+` prefix 라인만, `^-` 제외)
5. `az containerapp secret list -n eundunhealth-api -g apps --query "[].name" -o tsv` 실행
6. 추출된 secretref name 중 list 에 없는 것 있으면:
   - stderr 에 한국어 메시지 출력 ("룰 6 위반: <name> 미등록. `az containerapp secret set
     --name eundunhealth-api --resource-group apps --secrets \"<name>=<value>\"` 먼저
     실행하고 commit 재시도.")
   - exit code 2 (Claude Code hook contract: tool 호출 차단)
7. 통과 시 exit 0

**Failure modes 대응:**
- `az` 인증 만료 / 네트워크 실패 → stderr 경고만 + exit 0 (commit 통과). 운영자가 PR 단계에서
  catch. 자체적으로 fail-open: hook 가 운영 차단 원인 되면 안 됨.
- 스크립트 timeout (15s) → fail-open 동일
- Windows 환경: shebang `#!/bin/bash` + Bash tool 경유. PowerShell 환경에서도 git
  commit 시 hook 발동. settings.json 의 `command` 가 `bash ...` 로 명시되어 있어 OK.

**검증 시나리오 (사용자가 1회 수행):**
```pwsh
# 1. mock 변경: 가짜 secretref 추가
# .github/workflows/backend.yml 에 임시로 NEW_FAKE_SECRET=secretref:fake-not-exist 추가
git add .github/workflows/backend.yml
git commit -m "test: secretref guard"  # → block 되어야 함
git restore --staged .github/workflows/backend.yml
git checkout .github/workflows/backend.yml  # revert
```

### 3.5 SessionStart 확장 (`scripts/claude-context.sh`)

**현 출력 끝에 추가:**
```
=== Pending Phase 5 Verifications ===
- INC-2026-05-27-01 (merged 2026-05-29): /verify-deploy INC-2026-05-27-01
```

**판정 로직 (bash, jq 사용):**
1. `git log main --merges --since="7 days ago" --pretty=format:'%h %s %ci'` 추출
2. 각 merge commit 에서 PR number 추출 (subject 의 `(#NN)` 정규식)
3. PR 의 body 에서 INC ID 추출 — `gh pr view <NN> --json body --jq '.body'` 후
   `grep -oE 'INC-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}'` (없으면 skip)
4. 해당 INC 가 `docs/ops/incident-log.md` 에서 "검증 완료" 라인 보유 여부 확인
   - `grep -A 100 "^### $inc_id" docs/ops/incident-log.md | grep -m1 "검증 완료"`
   - 없으면 pending 목록에 추가
5. pending 0건이면 섹션 자체 출력 생략 (noise 최소화)

**Failure modes:**
- `gh` 미인증 / network 실패 → 섹션에 `(could not check — gh auth required?)` 한 줄로 표시
- timeout 10s 초과 → SessionStart 자체 hook 의 timeout 가 10s 라 여유 부족 가능 → hook timeout 을 20s 로 늘리거나, 이 검증 부분만 별도 hook 으로 분리

### 3.6 CLAUDE.md / operations-snapshot.md 갱신

**CLAUDE.md "### 자동화 스크립트 (`scripts/`)" 섹션 끝에:**
```markdown
- `scripts/hooks/secretref-guard.sh` — git commit 시 backend.yml 신규 secretref 자동
  검증 (룰 6 위반 차단). PreToolUse hook 으로 자동 실행. fail-open.

### Claude Code 슬래시 명령 (`.claude/commands/`)

- `/verify-deploy <inc-id>` — MCP 로 Phase 5 검증 자동화 (alembic head + 스키마 + Sentry).
  INC 의 [[pending-mcp-integrations]] 매핑 참조.
```

**operations-snapshot.md 새 섹션 (현 §2 Secrets 다음에 §3 신설, 기존 §3 이후는 +1 번호 시프트):**
```markdown
## MCP 서버 매핑 (2026-05-28 도입)

| MCP 서버 | 주 사용 시나리오 | 인증 |
|---|---|---|
| `mcp__sentry__*` | INC root cause 분석, Phase 5 검증, 신규 issue 알림 | OAuth (현 qkr133456@gmail.com) |
| `mcp__azure__*` | Container App 상태/로그, ACR 정리, PG 쿼리, secret list 검증 | az CLI shared, tenant ID env 주입 |
| `mcp__github__*` | PR 작성/조회, CI run polling, 코드 검색 | GitHub Copilot OAuth (gunnysis) |
| `mcp__plugin_context7_context7__*` | 공식 docs fetch (Alembic, PG, Docker, Container Apps) | 없음 (public) |

자세한 task 별 매핑: 메모리 [[pending-mcp-integrations]] 참조 (project memory).
```

---

## 4. Data Flow / 통합 동작 시나리오

### 시나리오 A: INC schema drift 복구 PR 머지 후 Phase 5 검증

```
사용자 → "/verify-deploy INC-2026-05-27-01"
  ↓
Claude:
  1. mcp__azure__containerapps(action=exec, command="alembic current")
     → Azure MCP (with AZURE_TENANT_ID env) → az credentials → Container Apps API
     → "a1b2c3d4 (head)" 응답
  2. mcp__azure__postgres(query="SELECT ... WHERE column_name='rest_day'")
     → PG Flexible Server `healthapp` → row 반환
  3. mcp__sentry__search_issues(project="eundunhealth-backend", query="rest_day",
       age="24h") → 0건
  4. Edit(docs/ops/incident-log.md, +"검증 완료: 2026-05-29 ...")
  5. 사용자에게 markdown table 보고
```

### 시나리오 B: backend.yml 의 secretref 신규 추가 commit 시도

```
Claude → Bash("git commit -m ...")
  ↓
PreToolUse hook → secretref-guard.sh
  ↓
- backend.yml 변경 감지 → diff 추출
- 신규 +SECRETREF:new-sentry-dsn 발견
- az containerapp secret list → ["sentry-dsn-backend", ...] (new-sentry-dsn 없음)
- stderr: "룰 6 위반: new-sentry-dsn 미등록..."
- exit 2 → Bash tool block
  ↓
Claude → 사용자에게 보고, az containerapp secret set 먼저 수행 안내
```

### 시나리오 C: SessionStart 시 보류 검증 자동 노출

```
Claude Code 시작
  ↓
SessionStart hook → claude-context.sh
  ↓
기존 출력 (branch, commits, versions, ...)
  ↓ (확장 부분)
git log main --merges --since="7d" → PR #45 (merged 2026-05-29)
gh pr view 45 --json body → "fix INC-2026-05-27-01"
grep INC-2026-05-27-01 in docs/ops/incident-log.md → "검증 완료" 라인 없음
→ "=== Pending Phase 5 Verifications ===" 섹션 출력
  ↓
Claude 가 첫 응답에서 사용자에게 "/verify-deploy INC-2026-05-27-01 권장" 안내 가능
```

---

## 5. Error handling

| 실패 지점 | 동작 |
|---|---|
| MCP 서버 down | Claude 가 fallback 으로 `az`/`gh` CLI 사용 (이미 권한 있음) |
| Azure MCP tenant env 미적용 | 사용자에게 Claude Code 재시작 요청 + 임시로 `tenant=` 명시 |
| Sentry MCP rate limit | `analyze_issue_with_seer` 만 rate limit 영향 큼. 일반 search 는 여유 |
| GitHub MCP API limit | Copilot OAuth 기반이라 일반 PAT 보다 한도 높음. 초과 시 fallback `gh` |
| secretref-guard fail-open | 차단 못해도 backend.yml CI step 이 deploy 시점에 catch (룰 6 의 2차 방어 이미 존재) |
| SessionStart hook timeout | 10s → 20s 로 상향 (큰 영향 없음, network 호출만 추가) |

---

## 6. Testing

**Unit-style 검증 (개별 컴포넌트):**
- §3.1 Permissions: 의도한 tool 호출이 prompt 없이 통과하는지 — `mcp__sentry__search_issues` 1회 호출 후 prompt 부재 확인
- §3.2 Azure tenant: 재시작 후 `mcp__azure__subscription_list` (no args) 가 결과 반환
- §3.3 /verify-deploy: 임시 stub INC ID 로 호출 → 각 검증 단계 trace 확인
- §3.4 secretref-guard: 시나리오 B 의 mock 변경 + commit 시도 → exit 2 확인
- §3.5 SessionStart: 새 세션 시작 → "Pending Phase 5 Verifications" 섹션 노출 확인

**Integration 검증 (E2E):**
- INC-2026-05-27-01 의 Phase 5 가 실제로 1-command 로 종료되는지 확인
  (단, schema drift recovery plan 의 Task 9 까지 완료된 이후에야 가능 — 시점 의존)

---

## 7. 사용자가 추가로 전달할 정보

| 시점 | 항목 | 비고 |
|---|---|---|
| §3.2 적용 후 | Claude Code 재시작 1회 | 사용자가 수동 (CLI/IDE 양쪽) |
| §3.4 첫 적용 후 | 시나리오 B mock 검증 1회 | 사용자가 수동 commit 시도 — 5분 |
| §3.5 적용 후 | 다음 세션 시작 시 출력 확인 | 자연 발생 |
| 운영 중 | (없음) | 모든 MCP 인증은 이미 완료 |

> **사용자가 사전 제공해야 하는 비밀/토큰: 없음.** 모든 인증은 이미 통과 확인됨.
> §3.2 의 tenant ID 도 비밀 아님 (`az account show` 결과).

---

## 8. 잔여 리스크

1. **Azure MCP tool 의 read/write 미구분** — `containerapps`, `postgres` 등 단일 tool 안에
   list/show/update/delete 가 모두 있어 permission allow 가 write 도 같이 허용함. 특히
   `mcp__azure__keyvault` 는 `get-secret-value` subaction 노출 여부 미확인 — 실제 사용 직전
   첫 호출 시 subaction 표 확인 필요. 노출되면 ask 로 이동. 완화책: 사용자 PR 리뷰 + Claude
   의 호출 직전 확인 + 룰 6 가드.
1a. **Sentry read tool 의 user PII 자동 접근** — `get_event_attachment` /
   `get_profile_details` / `get_replay_details` 는 read-only 분류가 옳지만 Health Connect
   body metrics / auth token 등 사용자 PII 를 포함한 payload 를 prompt 없이 조회 가능. 현
   solo-dev / internal testing 단계에서는 허용 가능 (사용자가 세션 리뷰) — v1.0 출시 + 실
   사용자 데이터 적재 후에는 ask 로 격상 검토. 한국 PIPA / GDPR 고려.
2. **secretref-guard fail-open** — `az` 인증 만료 등으로 hook 가 실패하면 commit 통과. 의도된
   설계 (운영 차단 회피) 지만 deploy 단계의 backend.yml CI step 가드가 2차 방어로 작동해야 함.
3. **SessionStart hook timeout** — `gh pr view` 가 느릴 경우 10s → 20s 상향에도 부족 가능.
   완화책: pending check 부분만 background job 으로 분리 (별도 PR).
4. **MCP 서버 의존성** — Sentry/GitHub MCP 가 외부 HTTPS endpoint. 일시 down 시 Phase 5
   자동화 불가 → 사용자가 수동 fallback (이미 익숙한 sentry.io / gh CLI).

---

## 9. 참고

- 메모리: [[pending-mcp-integrations]] (현재 MCP 연결 상태 + plan task 매핑)
- 메모리: [[design-plan-docs-convention]] (이 문서가 따르는 컨벤션)
- 메모리: [[inc-2026-05-27-01-schema-drift]] (이 작업의 trigger 가 된 INC)
- 메모리: [[claude-code-mcp-install-gotchas]] (`@azure/mcp server start` subcommand 함정)
- Plan: `docs/plans/2026-05-27-schema-drift-recovery-plan.md` Phase 5
- Plan (예정): `docs/plans/2026-05-28-mcp-integration-setup-plan.md` (이 design 의 implementation plan)
