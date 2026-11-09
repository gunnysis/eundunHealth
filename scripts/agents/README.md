# scripts/agents — 결정론적 문서 일관성 감사 도구 (Deterministic Doc Audit)

본 디렉토리는 외부 AI SDK(`claude-agent-sdk`)나 API 시크릿 없이 **Python 3.12+ 표준 라이브러리(stdlib)** 만으로 동작하는 100% 결정론적 문서 일관성 감사 린터를 담는다.

> **배경 및 전환 이력**: 초기(2026-06-16)에는 결정론적 수집기(collector) + Claude Agent SDK 감사 에이전트(auditor) 2단계로 설계했으나, 외부 OAuth/API 토큰 의존성과 주간 CI 지연(4분)을 근본적으로 제거하기 위해 **2026-11-02에 100% 결정론적 stdlib 린터 엔진(`doc_audit.py`)으로 전면 전환**했다(실행 시간 ~0.05초, 외부 비용 $0.0000, 시크릿 0개). 상세: `docs/plans/logs/process-infra.md`.

---

## `doc_audit.py` — 문서 드리프트 감사 린터 (read-only)

버전(`versionName`, `versionCode`)·백엔드 API 버전(`__version__`)·DB 스키마(`02_schema_create_all.sql`)·CORS 기본값·Python 런타임 버전·API 엔드포인트 총수·테스트 수 같은 SSoT 값은 코드와 6대 핵심 문서(`CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/TRD.md`, `docs/PRD.md`, `docs/CHANGELOG.md`)에 함께 서술된다.

| 단계 | 역할 | 의존성 | 비고 |
|---|---|---|---|
| **1. Collector** | `version.properties`, `backend/app/__init__.py`, `backend/Dockerfile`, `backend/pyproject.toml`, `backend/app/routers/*.py` 등 canonical 소스를 파싱해 ground-truth 사실 수집 | Python stdlib | `--collect-only`로 JSON 출력 가능 |
| **2. Deterministic Linter** | 수집된 ground-truth 사실과 6대 문서의 현재형 서술·배지·표·메타데이터를 정밀 대조하여 드리프트 보고 | Python stdlib | **read-only** — 문서를 임의로 덮어쓰지 않고 구조화 리포트 출력 |

### 실행

```bash
# 결정론적 사실만 JSON 출력 (디버깅용)
python scripts/agents/doc_audit.py --collect-only

# 전체 문서 감사 리포트 출력 (advisory: exit 0)
python scripts/agents/doc_audit.py
bash scripts/agents/doc-audit.sh

# 하드 게이트 모드 (드리프트 발견 시 exit 2 반환 — preflight / CI 검증용)
python scripts/agents/doc_audit.py --strict

# 기계용 전체 결과 JSON 출력
python scripts/agents/doc_audit.py --json

# 단위 테스트 (collector 파서 및 린터 규칙 검증)
backend/.venv/Scripts/python.exe -m pytest scripts/agents/test_doc_audit.py -q
```

### 새 SSoT / 감사 규칙 추가 시

1. `scripts/agents/doc_audit.py`의 `collect_facts()` 및 pure 파서 함수에 수집 로직 추가.
2. `audit_*` 함수에 결정론적 대조 규칙 추가 및 `audit_documents()`에 연결.
3. `scripts/agents/test_doc_audit.py`에 단위 테스트(정상 케이스 + 드리프트 감지 케이스) 추가.

---

## CI 워크플로우 (`.github/workflows/doc-audit.yml`)

- **단일 `doc-audit` job**: 외부 시크릿 불필요, 실행 시간 ~15초.
- **트리거**: `scripts/agents/**`, `.github/workflows/doc-audit.yml`, `version.properties`, `CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/TRD.md`, `docs/PRD.md` 변경 PR/push + 매주 월요일 09:00 KST 정기 스케줄 + `workflow_dispatch`.
- **동작**: `pytest==9.1.1`로 `test_doc_audit.py` 단위 테스트를 수행한 뒤 `doc_audit.py` 리포트를 `$GITHUB_STEP_SUMMARY`와 아티팩트로 게시한다.
