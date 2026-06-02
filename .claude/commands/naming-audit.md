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
