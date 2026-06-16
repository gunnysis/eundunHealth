# scripts/agents — Claude Agent SDK 기반 운영 자동화

본 디렉토리는 **Claude Agent SDK**(Python)로 만든 헤드리스 dev/ops 에이전트를 담는다.
대화형 Claude Code(CLI)와 달리, 여기 스크립트는 **CI·cron·로컬에서 무인 실행**되는 게 목적이다.

> 적용 검토 배경: Agent SDK 의 비교우위는 "bash/CI 가 못 하는 *의미 판단*"뿐이다. 단일 값=단일 값
> 비교(openapi drift·secretref·collectAsState)는 이미 결정론적 가드가 한다. 그래서 첫 적용 대상은
> regex 로 위험(INC-27)·불가능한 **문서 일관성 감사** 하나로 좁혔다.
> 상세: `docs/plans/logs/process-infra.md` (2026-06-16 Agent SDK 적용 검토 entry).

---

## doc_audit.py — 문서 드리프트 감사관 (read-only)

버전·alembic head·CORS·테스트 수 같은 값은 코드(SSoT)와 여러 문서(CLAUDE.md / README /
operations-snapshot / TRD / PRD / CHANGELOG)에 중복 서술된다. `bump-version.sh` 의 blind
replace(INC-27)·5-릴리스 누적 드리프트가 반복 통증이었다.

**2단계 설계** — LLM 은 의미 판단에만, 나머지는 결정론적:

| 단계 | 무엇 | SDK/인증 | 비고 |
|---|---|---|---|
| **collector** | canonical 소스를 파싱해 ground-truth 사실 수집 | 불필요(stdlib) | 단위테스트 대상, `--collect-only` |
| **auditor** | 사실 + 문서 사본을 읽고 모순/stale 서술 보고 | 필요(Agent SDK) | **read-only**(Read/Grep/Glob, `dontAsk`) — 문서 직접 수정 안 함 |

auditor 는 `permission_mode="dontAsk"` + `allowed_tools=[Read, Grep, Glob]` 라 **파일 변경이
물리적으로 불가**하다. 발견은 구조화 리포트로만 내고, 수정은 사람이 검토 후 한다(INC-27 교훈).

### 실행

```bash
# 결정론적 사실만 — SDK·인증 불필요 (CI 단위테스트·디버깅)
bash scripts/agents/doc-audit.sh --collect-only
python scripts/agents/doc_audit.py --collect-only

# 전체 감사 (collector → SDK auditor → 사람용 리포트)
bash scripts/agents/doc-audit.sh

# 기계용 JSON / 하드 게이트(드리프트 시 exit 2)
python scripts/agents/doc_audit.py --json
python scripts/agents/doc_audit.py --strict

# 단위 테스트 (collector/파서)
backend/.venv/Scripts/python.exe -m pytest scripts/agents/test_doc_audit.py -q
```

### 설치 (auditor 경로만)

```bash
pip install -r scripts/agents/requirements.txt   # claude-agent-sdk (Python 3.10+)
```

Python SDK 패키지가 Claude Code 바이너리를 번들하므로 **별도 CLI/Node 설치 불필요**
(출처: code.claude.com/docs/en/agent-sdk/hosting · Runtime dependencies).

### 인증 & 비용

| 경로 | 방법 | 비용 |
|---|---|---|
| 로컬 | 이미 로그인된 Claude Code 구독 세션 그대로 사용 | **구독 한도 내 — 추가 0** |
| CI(권장) | `claude setup-token` → `CLAUDE_CODE_OAUTH_TOKEN` repo secret | **구독 — 추가 0** |
| CI(대안) | `ANTHROPIC_API_KEY` repo secret | 토큰 종량 과금 |

read-only·`max_turns` 제한이라 1회 감사 비용은 수 센트 수준. `ResultMessage.total_cost_usd`
(추정)로 확인.

### 새 SSoT/사본 추가 시

1. collector 에 파서 추가 → `doc_audit.py` 의 `collect_facts()` + pure 파서 + `test_doc_audit.py`.
2. 사본 추가 → `DOC_COPIES` 리스트 + `build_prompt()` 의 "자주 드리프트하는 값" 힌트.

---

## CI

`.github/workflows/doc-audit.yml`:
- **collector-test** job — 시크릿 불필요, 항상 실행(단위테스트 + `--collect-only` smoke).
- **audit** job — 주간 cron(월 09:00 KST) + 수동(`workflow_dispatch`). auth 시크릿 미설정이면
  실패 대신 notice 후 스킵. 리포트는 Step Summary + 아티팩트. 기본 advisory(드리프트가 있어도
  run 은 green).

## Phase 매핑

- **Phase 0** — `doc_audit.py`(collector+auditor) + `test_doc_audit.py` + `requirements.txt`.
- **Phase 1** — `doc-audit.sh`(로컬 wrapper, 구독 인증). 릴리스 전 수동 실행 권장. 릴리스 preflight
  (`preflight-release.sh`)는 **오프라인·결정론** 유지를 위해 결합하지 않는다 — 문서 감사는 별도 advisory.
- **Phase 2** — `doc-audit.yml`(주간 cron + 수동). 운영자가 `CLAUDE_CODE_OAUTH_TOKEN` secret 등록 시 활성.
