#!/usr/bin/env python3
"""문서 드리프트 감사 에이전트 — canonical 코드/인프라 값 ↔ 문서 서술 일관성 점검.

이 프로젝트는 버전·alembic head·CORS·테스트 수 같은 값이 코드(SSoT)와 여러 문서
(CLAUDE.md / README / operations-snapshot / TRD / PRD / CHANGELOG)에 중복 서술된다.
`bump-version.sh` 의 blind replace(INC-27)·5-릴리스 누적 드리프트가 반복 통증이었다.

본 스크립트는 2단계로 동작한다:
  1) collector (결정론적, SDK 불필요): canonical 소스를 파싱해 ground-truth 사실을 수집.
  2) auditor (Claude Agent SDK): 1)의 사실 + 문서 사본을 읽고, 서술이 사실과 모순되거나
     명백히 stale 한 지점을 구조화 리포트로 보고한다. **문서를 직접 수정하지 않는다**
     (read-only — INC-27 교훈: blind 수정 금지, 사람이 검토 후 수정).

왜 LLM 인가: 단일 값 = 단일 값 비교는 bash/CI 가 이미 한다(openapi drift·secretref·
collectAsState). LLM 의 비교우위는 "이 서술이 *현재 사실*과 맞는가 / 과거 이력 entry 인가"
같은 의미 판단 — regex 로는 위험(INC-27)하거나 불가능한 영역뿐이다.

Used by:
  - scripts/agents/doc-audit.sh           (로컬 wrapper, 구독 인증)
  - .github/workflows/doc-audit.yml       (주간 cron + manual, CLAUDE_CODE_OAUTH_TOKEN)
  - scripts/agents/test_doc_audit.py      (collector 단위 테스트)

모드:
  --collect-only   결정론적 사실만 JSON 출력 (SDK·인증 불필요). CI 단위테스트·디버깅용.
  (default)        collector → SDK auditor → 사람용 리포트.
  --json           전체 결과를 기계용 JSON 으로 출력.
  --strict         드리프트 발견 시 exit 2 (CI 하드 게이트용; 기본은 advisory=exit 0).

설계 근거: docs/plans/logs/process-infra.md (2026-06-16 Agent SDK 적용 검토 entry).
"""
from __future__ import annotations

import argparse
import asyncio
import contextlib
import json
import re
import sys
from pathlib import Path
from typing import Any


def _force_utf8_stdio() -> None:
    """stdout/stderr 를 UTF-8 로 재구성한다.

    dev 호스트(한국어 Windows)의 콘솔 코드페이지가 cp949 라, 한국어·em-dash(—)가 섞인
    JSON/리포트를 print 하면 UnicodeEncodeError 로 죽는다. CI(Linux)는 기본 UTF-8 이라 무관.
    redirect 등으로 reconfigure 가 없는 스트림은 조용히 건너뛴다.
    """
    for stream in (sys.stdout, sys.stderr):
        with contextlib.suppress(AttributeError):
            stream.reconfigure(encoding="utf-8")  # type: ignore[union-attr]

# ---------------------------------------------------------------------------
# 1) Pure parsers — 파일 IO 없이 문자열만 받는다 (단위 테스트 대상).
# ---------------------------------------------------------------------------


def parse_app_version(text: str) -> dict[str, str | None]:
    """version.properties 텍스트에서 versionName/versionCode 를 추출한다."""
    name = re.search(r"^versionName=(.+)$", text, re.MULTILINE)
    code = re.search(r"^versionCode=(.+)$", text, re.MULTILINE)
    return {
        "versionName": name.group(1).strip() if name else None,
        "versionCode": code.group(1).strip() if code else None,
    }


def parse_backend_api_version(text: str) -> str | None:
    """backend/app/__init__.py 텍스트에서 __version__ 리터럴을 추출한다."""
    m = re.search(r'^__version__\s*=\s*["\']([^"\']+)["\']', text, re.MULTILINE)
    return m.group(1) if m else None


def parse_revision_pair(text: str) -> tuple[str | None, str | None]:
    """alembic version 파일 텍스트에서 (revision, down_revision) 을 추출한다."""
    rev = re.search(r'^revision:[^=\n]*=\s*["\']([^"\']+)["\']', text, re.MULTILINE)
    down = re.search(r'^down_revision:[^=\n]*=\s*["\']([^"\']+)["\']', text, re.MULTILINE)
    return (rev.group(1) if rev else None, down.group(1) if down else None)


def compute_alembic_head(pairs: list[tuple[str | None, str | None]]) -> dict[str, Any]:
    """(revision, down_revision) 목록에서 head 를 계산한다.

    head = 다른 어떤 파일의 down_revision 으로도 참조되지 않는 revision.
    선형 이력이면 정확히 1개. 0개/2개+ 이면 분기·오류 신호로 그대로 노출한다.
    """
    revisions = [r for r, _ in pairs if r]
    downs = {d for _, d in pairs if d}
    heads = [r for r in revisions if r not in downs]
    return {
        "head": heads[0] if len(heads) == 1 else None,
        "heads": heads,
        "revision_count": len(revisions),
    }


def parse_cors_default(config_text: str) -> str | None:
    """config.py 텍스트에서 cors_origins 필드의 기본값 리터럴(원문)을 추출한다."""
    m = re.search(r"cors_origins\s*:\s*list\[str\]\s*=\s*(\[[^\]]*\]|\S+)", config_text)
    return m.group(1).strip() if m else None


_ROUTE_RE = re.compile(
    r'@router\.(get|post|put|patch|delete)\(\s*["\']([^"\']*)["\']', re.IGNORECASE
)


def count_routes_in_text(text: str) -> list[dict[str, str]]:
    """라우터 파일 텍스트에서 (method, path) 목록을 추출한다."""
    return [
        {"method": m.group(1).lower(), "path": m.group(2)}
        for m in _ROUTE_RE.finditer(text)
    ]


def count_test_functions(texts: list[str]) -> int:
    """test 파일 텍스트들에서 `def test_` 개수를 합산한다(pytest pass 수의 정적 근사)."""
    return sum(
        len(re.findall(r"^\s*(?:async\s+)?def test_\w+", t, re.MULTILINE)) for t in texts
    )


def extract_json_block(text: str) -> Any | None:
    """텍스트에서 마지막 ```json ... ``` 블록을 파싱해 반환한다(없으면 None)."""
    blocks = re.findall(r"```json\s*(.+?)```", text, re.DOTALL)
    for block in reversed(blocks):
        try:
            return json.loads(block)
        except json.JSONDecodeError:
            continue
    return None


# ---------------------------------------------------------------------------
# 2) Collector — canonical 소스를 읽어 ground-truth dict 를 만든다 (SDK 불필요).
# ---------------------------------------------------------------------------


def _read(path: Path) -> str:
    """파일을 UTF-8 로 읽되 없으면 빈 문자열을 반환한다."""
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def _by_method(routes: list[dict[str, str]]) -> dict[str, int]:
    """라우트 목록을 HTTP method 별 개수로 집계한다."""
    out: dict[str, int] = {}
    for route in routes:
        out[route["method"]] = out.get(route["method"], 0) + 1
    return out


def collect_facts(repo_root: Path) -> dict[str, Any]:
    """canonical 소스를 읽어 ground-truth 사실 dict 를 반환한다(결정론적)."""
    app_ver = parse_app_version(_read(repo_root / "version.properties"))
    api_ver = parse_backend_api_version(_read(repo_root / "backend" / "app" / "__init__.py"))

    versions_dir = repo_root / "backend" / "alembic" / "versions"
    pairs = [parse_revision_pair(_read(p)) for p in sorted(versions_dir.glob("*.py"))]
    alembic = compute_alembic_head(pairs)

    cors = parse_cors_default(_read(repo_root / "backend" / "app" / "config.py"))

    routers_dir = repo_root / "backend" / "app" / "routers"
    routes: list[dict[str, str]] = []
    for p in sorted(routers_dir.glob("*.py")):
        routes.extend(count_routes_in_text(_read(p)))

    tests_dir = repo_root / "backend" / "tests"
    test_files = sorted(tests_dir.rglob("test_*.py")) if tests_dir.is_dir() else []
    test_count = count_test_functions([_read(p) for p in test_files])

    return {
        "app_version": app_ver,
        "backend_api_version": api_ver,
        "alembic": alembic,
        "cors_origins_default": cors,
        "api_routes": {
            "total": len(routes),
            "by_method": _by_method(routes),
            "routes": routes,
        },
        "backend_tests": {
            "count": test_count,
            "method": "static `def test_` count (pytest pass 수 근사 — parametrize 제외)",
            "files": len(test_files),
        },
    }


# ---------------------------------------------------------------------------
# 3) Auditor — collector 사실 + 문서 사본을 SDK 에이전트로 의미 대조한다.
# ---------------------------------------------------------------------------

# 사실(SSoT)이 서술되는 문서 사본들. 새 사본 추가 시 여기에 더한다.
DOC_COPIES: list[str] = [
    "CLAUDE.md",
    "README.md",
    "docs/ops/operations-snapshot.md",
    "docs/TRD.md",
    "docs/PRD.md",
    "docs/CHANGELOG.md",
]

_SYSTEM_PROMPT = """\
너는 이 저장소 전용의 **읽기 전용 문서 일관성 감사관**이다. 절대 파일을 수정하지 않는다.

임무: 주어진 ground-truth 사실(코드/인프라에서 결정론적으로 수집된 현재 값)과 지정된 문서
사본들의 *서술*을 대조해, 서술이 현재 사실과 **모순**되거나 **명백히 stale** 한 지점만 보고한다.

반드시 지킬 원칙(정밀도 우선 — 거짓 양성은 신뢰를 깎는다):
- **현재 값 서술 vs 과거 이력 entry 를 구분하라.** CHANGELOG 의 과거 버전 항목, "직전 v0.1.x",
  "이전:" 같은 *역사적* 서술은 옛 값을 담는 게 정상이다 → 드리프트 아님. (INC-27 의 핵심 교훈:
  blind 치환이 과거 이력까지 오염시켰다. 너는 그 반대로, 이력은 건드리지 마라.)
- "현재 상태/운영 기준/SSoT/배지/스냅샷 작성 기준" 처럼 **현 시점 사실을 주장**하는 서술만 대상.
- 확신이 없으면 보고하지 말고 note 에 사유를 남겨라. 추정 금지(룰 9).
- 너는 Read/Grep/Glob 만 쓸 수 있다. 각 문서 사본을 직접 읽고 근거(인용/라인 힌트)를 들어라.

출력: 사람이 읽을 분석을 먼저 쓰고, **마지막에 단 하나의 ```json 블록**으로 아래 스키마를 채워라.
findings 가 없으면 clean=true, findings=[] 로.
"""


def build_prompt(facts: dict[str, Any], doc_copies: list[str]) -> str:
    """auditor 에이전트에 줄 사용자 프롬프트(사실 + 대상 문서 + JSON 스키마)를 만든다."""
    facts_json = json.dumps(facts, ensure_ascii=False, indent=2)
    doc_list = "\n".join(f"  - {d}" for d in doc_copies)
    return f"""\
아래는 코드/인프라에서 결정론적으로 수집한 **ground-truth 사실(현재 값)** 이다:

```json
{facts_json}
```

다음 문서 사본들을 각각 Read 로 읽고, 위 사실과 대조해 드리프트를 찾아라:
{doc_list}

참고 — 자주 드리프트하는 값과 의미:
- app_version (versionName/versionCode): README 배지·operations-snapshot §1·PRD·CLAUDE.md 의
  *현재* 서술이 일치해야 한다. CHANGELOG/이력 entry 는 제외.
- backend_api_version: 앱 버전과 **독립**(현재 1.0.0). 혼동 서술 주의.
- alembic.head: CLAUDE.md·operations-snapshot 의 "Alembic head" 서술과 일치해야 한다.
- cors_origins_default: 현재 `[]`(차단). 과거 `["*"]` 잔재 서술이 있으면 stale(보안 민감).
- api_routes.total: CLAUDE.md 의 엔드포인트 개수 주석(예: "14개")과 의미상 정합한지. 공개 라우트
  (/health, /health/ready, /.well-known/assetlinks.json, /auth/confirm)는 별도 카운트일 수 있으니
  단순 숫자 차이가 곧 드리프트는 아니다 — 서술의 기준을 보고 판단하라.
- backend_tests.count: README/CLAUDE.md/TRD 의 테스트 수 서술과 비교(정적 근사이므로 ±소폭은 note).
- 그 외: "Ktor"(레거시, 현재 FastAPI) 같은 명백한 stale 기술 서술도 stale-tech 로 보고.

출력 JSON 스키마(마지막 ```json 블록 하나):
{{
  "clean": <bool>,
  "summary": "<1~2문장 요약>",
  "findings": [
    {{
      "file": "<상대경로>",
      "locator": "<섹션/라인 힌트 또는 인용 스니펫>",
      "doc_says": "<문서의 서술>",
      "ground_truth": "<수집된 현재 값>",
      "kind": "version|api-version|alembic|cors|endpoint-count|test-count|stale-tech|other",
      "severity": "high|med|low",
      "note": "<드리프트인 이유 + 과거 이력이 아닌 근거>"
    }}
  ]
}}
"""


async def run_audit(
    repo_root: Path,
    facts: dict[str, Any],
    doc_copies: list[str],
    model: str | None = None,
    on_event: Any = None,
) -> dict[str, Any]:
    """SDK 에이전트를 read-only 로 실행하고 구조화 결과 dict 를 반환한다.

    반환: {"report": <agent JSON or None>, "raw": <최종 텍스트>, "cost_usd": float,
           "session_id": str|None, "subtype": str}
    """
    try:
        from claude_agent_sdk import (  # noqa: PLC0415  (지연 import — collector 는 SDK 불필요)
            AssistantMessage,
            ClaudeAgentOptions,
            ResultMessage,
            query,
        )
    except ImportError as exc:  # pragma: no cover - 설치 안내 경로
        raise SystemExit(
            "claude-agent-sdk 가 설치되지 않았습니다.\n"
            "  pip install -r scripts/agents/requirements.txt\n"
            "사실만 보려면 --collect-only 로 실행하세요(SDK 불필요)."
        ) from exc

    options = ClaudeAgentOptions(
        cwd=str(repo_root),
        allowed_tools=["Read", "Grep", "Glob"],  # 읽기 전용 — 수정 도구 없음
        permission_mode="dontAsk",  # allowed 외 전부 거부 → 파일 변경 물리적으로 불가
        setting_sources=[],  # CLAUDE.md 는 '감사 대상'으로 Read 한다; dev 룰 주입은 불필요
        system_prompt=_SYSTEM_PROMPT,
        max_turns=40,
        model=model,
    )

    raw_parts: list[str] = []
    cost = 0.0
    session_id: str | None = None
    subtype = "unknown"

    async for message in query(prompt=build_prompt(facts, doc_copies), options=options):
        if isinstance(message, AssistantMessage):
            for block in message.content:
                text = getattr(block, "text", None)
                if text:
                    raw_parts.append(text)
                    if on_event:
                        on_event(text)
        elif isinstance(message, ResultMessage):
            cost = float(getattr(message, "total_cost_usd", 0.0) or 0.0)
            session_id = getattr(message, "session_id", None)
            subtype = getattr(message, "subtype", "unknown")

    raw = "\n".join(raw_parts)
    return {
        "report": extract_json_block(raw),
        "raw": raw,
        "cost_usd": cost,
        "session_id": session_id,
        "subtype": subtype,
    }


# ---------------------------------------------------------------------------
# 4) CLI / 리포트 렌더링
# ---------------------------------------------------------------------------

_SEV_ORDER = {"high": 0, "med": 1, "low": 2}


def render_report(result: dict[str, Any]) -> str:
    """auditor 결과를 사람용 텍스트 리포트로 렌더링한다."""
    report = result.get("report")
    if report is None:
        return (
            "⚠ 에이전트가 구조화 JSON 을 반환하지 않았습니다. 원문:\n\n"
            + result.get("raw", "")
        )
    findings = sorted(
        report.get("findings", []),
        key=lambda f: _SEV_ORDER.get(f.get("severity", "low"), 9),
    )
    lines = [f"요약: {report.get('summary', '(없음)')}", ""]
    if not findings:
        lines.append("✓ 드리프트 없음 — 문서 서술이 현재 사실과 일치합니다.")
    else:
        lines.append(f"드리프트 {len(findings)}건:")
        for i, f in enumerate(findings, 1):
            lines += [
                "",
                f"  [{i}] ({f.get('severity', '?').upper()} · {f.get('kind', '?')}) {f.get('file', '?')}",
                f"      위치   : {f.get('locator', '')}",
                f"      문서   : {f.get('doc_says', '')}",
                f"      현재값 : {f.get('ground_truth', '')}",
                f"      근거   : {f.get('note', '')}",
            ]
    cost = result.get("cost_usd") or 0.0
    lines += ["", f"(추정 비용: ${cost:.4f} · 결과: {result.get('subtype')})"]
    return "\n".join(lines)


def _default_repo_root() -> Path:
    """스크립트 위치(scripts/agents/) 기준 저장소 루트를 추정한다."""
    return Path(__file__).resolve().parents[2]


def main(argv: list[str] | None = None) -> int:
    """CLI 진입점. exit code 를 반환한다."""
    _force_utf8_stdio()
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo-root", type=Path, default=_default_repo_root())
    parser.add_argument("--collect-only", action="store_true", help="결정론적 사실만 JSON 출력(SDK 불필요)")
    parser.add_argument("--json", action="store_true", help="전체 결과를 기계용 JSON 으로 출력")
    parser.add_argument("--strict", action="store_true", help="드리프트 발견 시 exit 2")
    parser.add_argument("--model", default=None, help="모델 override(미지정 시 SDK 기본)")
    parser.add_argument("--quiet", action="store_true", help="에이전트 진행 텍스트 숨김")
    args = parser.parse_args(argv)

    repo_root: Path = args.repo_root.resolve()
    facts = collect_facts(repo_root)

    if args.collect_only:
        print(json.dumps(facts, ensure_ascii=False, indent=2))
        return 0

    def _progress(text: str) -> None:
        if not args.quiet:
            print(text, file=sys.stderr)

    result = asyncio.run(run_audit(repo_root, facts, DOC_COPIES, model=args.model, on_event=_progress))

    if args.json:
        print(json.dumps({"facts": facts, **result}, ensure_ascii=False, indent=2))
    else:
        print(render_report(result))

    report = result.get("report") or {}
    has_drift = not report.get("clean", True) or bool(report.get("findings"))
    if args.strict and has_drift:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
