---
description: Phase 5 운영 검증을 MCP 로 자동 수행 (alembic head + 스키마 컬럼 + Sentry 신규 issue)
allowed-tools: mcp__azure__containerapps, mcp__azure__postgres, mcp__sentry__search_issues, mcp__sentry__search_events, mcp__sentry__analyze_issue_with_seer, Read, Glob, Grep, Edit
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
- subscription: 런타임 조회 — `mcp__azure__subscription_list` 의 단일 구독 (또는 `az account show --query id -o tsv`). 구독 GUID 는 repo 에 커밋하지 않는다(public repo 정찰면 축소).
- resourceGroup: `rg-eundunhealth-prod-krc`
- containerAppName: `eundunhealth-api`
- command: `alembic current`

기대: output 에 12자 hex hash + ` (head)` 표시. 실패면 Container App 재배포 미완 / DB 연결 실패.

### 2. 스키마 컬럼 실재 확인
`mcp__azure__postgres` 로 다음 쿼리 실행 (`{{columns_tuple}}` 자리에는 파싱한 `--columns` 값을
SQL row constructor 형태 `('user_profiles','rest_day'), ('user_profiles','foo')` 로 치환):
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
