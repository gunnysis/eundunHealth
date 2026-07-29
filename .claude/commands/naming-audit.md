---
description: Naming convention audit re-run (ruff D + N + detekt naming + Azure CAF 표 sync). 분기당 1~2회 권장.
allowed-tools: Bash, Read, Grep, Glob, Edit, mcp__azure__group_resource_list, mcp__azure__group_list
argument-hint: [--update-doc] (audit 결과로 design doc §3 갱신 patch 제안 — auto-commit 안 함)
---

본 프로젝트의 명명/문서화 컨벤션 준수도를 재측정합니다. 시간 경과에 따른 drift 점검 + 신규 PR 머지 후 baseline 갱신 용도. 5단계 모두 시도, stop-on-failure 아님 — verify-deploy.md 와 동일 정책.

## 검증 단계

### 1. Python PEP 257 violation 측정 (config-driven)
```bash
cd backend && .venv/Scripts/ruff.exe check --statistics app/ tests/ alembic/
```
기대: `All checks passed!` (본 PR 머지 후 baseline). 잔존 시 카테고리별 list + 파일 경로 보고.

**주의**: `--select D` 단독 사용 금지 — pyproject 의 글로벌 `ignore` (D100/D104/D107/D203/D213) 와 per-file-ignore (schemas/models D101, main.py D 전체, alembic D/UP/I) 를 override 해서 false 위반이 카운트됨 (PR #68 Task 2 lesson). 항상 config-driven 측정.

### 2. Python PEP 8 naming (N) 측정 (config-driven)
```bash
cd backend && .venv/Scripts/ruff.exe check --select N --ignore N818 --statistics app/
```
기대: 0 errors. **주의**: `--select N` 단독 시 N818 (의도된 ignore, `AppException` 네이밍 유지) 가 false 위반 1건으로 출력됨. 항상 `--ignore N818` 명시 또는 그냥 `--statistics app/` (config-driven, 전체 룰셋).

### 3. Kotlin detekt naming 측정
```bash
./gradlew :app:detektDebug -q
```
build/reports/detekt/detekt.html 의 naming 카테고리 확인. baseline 외 신규 위반 0 기대. fail 시 `baseline.xml` vs `baseline-debug.xml` drift 가 원인 가능 — `detekt-baseline-drift.md` 메모리 참조.

### 4. Azure CAF 표 sync 안내
`docs/plans/logs/process-infra.md` 의 `2026-06-02 — naming convention audit` entry (또는 그 후속 audit entry) 의 Azure 리소스 매핑 vs 실측 비교:
```bash
az resource list -g rg-eundunhealth-prod-krc -o table
az containerapp env list -g rg-eundunhealth-prod-krc -o tsv
```
또는 Azure MCP (tenant 명시 필수, [[claude-code-mcp-install-gotchas]]):
- `mcp__azure__group_list --tenant <TENANT_ID>`
- `mcp__azure__group_resource_list --tenant <TENANT_ID> --resource-group rg-eundunhealth-prod-krc`

신규 리소스 발견 시 SSoT (`docs/conventions/naming.md` §5) 체크리스트 적용 + 후속 chore PR 으로 process-infra.md 의 audit entry 보강.

### 4.1 Azure portal auto-generated 명 탐지 (PR #68 lesson L7)

`az resource list -g rg-eundunhealth-prod-krc` 결과에서 아래 패턴 매칭 시 "확인 필요" 경고 (rename 불가 + 신규 deploy 시 명시 권장):

| 패턴 | Azure 리소스 | 학습 사례 |
|---|---|---|
| `workspace-.*` | Log Analytics workspace (Container Apps env 생성 시 portal 자동 생성) | PR #68 `workspace-appsDOlM` |
| `defaultkv-.*` | (예시 — 발견 시 추가) | — |
| `defaultstor-.*` | (예시 — 발견 시 추가) | — |

매칭 시 보고에 1줄 동봉: "신규 Container Apps env 생성 시 `--logs-workspace-id <id>` 또는 ARM/azd template 에 명시 권장 (rename 불가)".

미래 신규 auto-gen 패턴 발견 시 본 표에 1행 추가 + SSoT (`docs/conventions/naming.md` §5) 의 체크리스트 갱신.

### 5. 결과 보고
- 1~3 의 위반 개수 표로 정리.
- 4 의 drift (신규/누락 리소스) list.
- `--update-doc` flag 시: SSoT (`docs/conventions/naming.md` §3 Azure abbreviation) 갱신 patch + process-infra.md audit entry 보강 patch 를 **제안**. **자동 commit 안 함** — CLAUDE.md "NEVER commit unless explicitly asked" 룰. 사용자가 검토 후 `commit` 명시 시에만 진행.

예상 소요: ruff D+N (~2s) + detekt (~30~60s, gradle daemon hot) + Azure MCP 호출 (~5~10s) — 총 ~1~2 분.
