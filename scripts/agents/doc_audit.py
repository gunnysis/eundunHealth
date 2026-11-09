#!/usr/bin/env python3
"""문서 일관성 감사 도구 — canonical 코드/인프라 값 ↔ 문서 서술 일관성 점검 (결정론적 린터).

이 프로젝트는 버전·스키마·CORS·API 엔드포인트 수·파이썬 런타임 같은 불변식(SSoT)이
코드와 여러 문서(CLAUDE.md / README / operations-snapshot / TRD / PRD / CHANGELOG)에 서술된다.

본 도구는 외부 AI 의존성이나 시크릿 없이 Python 표준 라이브러리(stdlib)만으로 100% 결정론적으로
동작하며, 0.05초 만에 핵심 문서들의 불변식과 현재형 서술을 정밀 대조합니다.

Used by:
  - scripts/agents/doc-audit.sh           (로컬 wrapper)
  - .github/workflows/doc-audit.yml       (CI 워크플로우: PR / Push / 주간 스케줄)
  - scripts/agents/test_doc_audit.py      (파서 및 린터 단위 테스트)

모드:
  (default)        결정론적 사실 수집 + 6대 문서 감사 → 구조화 텍스트 리포트 (advisory=exit 0).
  --collect-only   결정론적 사실만 JSON 출력 (CI 디버깅용).
  --json           전체 결과(facts + findings)를 JSON 출력.
  --strict         드리프트 발견 시 exit 2 (CI 하드 게이트용).
"""
from __future__ import annotations

import argparse
import ast
import contextlib
import json
import re
import sys
from pathlib import Path
from typing import Any


def _force_utf8_stdio() -> None:
    """stdout/stderr 를 UTF-8 로 재구성한다."""
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


def parse_wear_version(text: str) -> str | None:
    """version.properties 텍스트에서 wearVersionCode 를 추출한다."""
    wear_code = re.search(r"^wearVersionCode=(.+)$", text, re.MULTILINE)
    return wear_code.group(1).strip() if wear_code else None


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
    """(revision, down_revision) 목록에서 head 를 계산한다."""
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


_ROUTER_PREFIX_RE = re.compile(
    r'APIRouter\([^)]*?\bprefix\s*=\s*["\']([^"\']+)["\']', re.DOTALL
)
_ROUTE_RE = re.compile(
    r'@router\.(get|post|put|patch|delete)\(\s*["\']([^"\']*)["\']', re.IGNORECASE
)


def count_routes_in_text(text: str) -> list[dict[str, str]]:
    """라우터 파일 텍스트에서 (method, path) 목록을 추출한다 (APIRouter prefix 반영)."""
    prefix_match = _ROUTER_PREFIX_RE.search(text)
    prefix = prefix_match.group(1).rstrip("/") if prefix_match else ""
    routes: list[dict[str, str]] = []
    for m in _ROUTE_RE.finditer(text):
        raw_path = m.group(2)
        full_path = f"{prefix}{raw_path}" if prefix else raw_path
        routes.append({"method": m.group(1).lower(), "path": full_path})
    return routes


_DOCKER_PY_RE = re.compile(r"^FROM\s+python:(\d+\.\d+)", re.MULTILINE)
_RUFF_TARGET_RE = re.compile(r"""^target-version\s*=\s*["']py(\d)(\d+)["']""", re.MULTILINE)
_MYPY_PY_RE = re.compile(r"""^python_version\s*=\s*["'](\d+\.\d+)["']""", re.MULTILINE)


def parse_python_runtime(dockerfile_text: str, pyproject_text: str) -> dict[str, Any]:
    """백엔드 Python 런타임을 3개 출처에서 읽고 서로 일치하는지 확인한다."""
    docker = m.group(1) if (m := _DOCKER_PY_RE.search(dockerfile_text)) else None
    ruff = f"{m.group(1)}.{m.group(2)}" if (m := _RUFF_TARGET_RE.search(pyproject_text)) else None
    mypy = m.group(1) if (m := _MYPY_PY_RE.search(pyproject_text)) else None
    found = [v for v in (docker, ruff, mypy) if v]
    return {
        "dockerfile": docker,
        "ruff_target_version": ruff,
        "mypy_python_version": mypy,
        "mismatch": len(set(found)) > 1,
    }


_JWT_ALGS_RE = re.compile(r"algorithms\s*=\s*\[([^\]]*)\]")


def parse_jwt_algorithms(dependencies_text: str) -> list[str]:
    """JWT 검증 알고리즘 리터럴을 추출한다."""
    m = _JWT_ALGS_RE.search(dependencies_text)
    if not m:
        return []
    return re.findall(r"""["']([^"']+)["']""", m.group(1))


def _parametrize_factor(decorator: ast.expr) -> int:
    """`@pytest.mark.parametrize` 케이스 수를 계산한다."""
    if not isinstance(decorator, ast.Call):
        return 1
    func = decorator.func
    if not (isinstance(func, ast.Attribute) and func.attr == "parametrize"):
        return 1
    if len(decorator.args) < 2:
        return 1
    argvalues = decorator.args[1]
    if isinstance(argvalues, (ast.List, ast.Tuple)) and argvalues.elts:
        return len(argvalues.elts)
    return 1


def count_test_functions(texts: list[str]) -> int:
    """test 파일 텍스트들에서 pytest 케이스 수를 정적 분석으로 센다."""
    total = 0
    for text in texts:
        try:
            tree = ast.parse(text)
        except SyntaxError:
            total += len(re.findall(r"^\s*(?:async\s+)?def test_\w+", text, re.MULTILINE))
            continue
        for node in ast.walk(tree):
            if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            if not node.name.startswith("test_"):
                continue
            cases = 1
            for dec in node.decorator_list:
                cases *= _parametrize_factor(dec)
            total += cases
    return total


def extract_json_block(text: str) -> Any | None:
    """텍스트에서 마지막 ```json ... ``` 블록을 파싱한다."""
    blocks = re.findall(r"```json\s*(.+?)```", text, re.DOTALL)
    for block in reversed(blocks):
        try:
            return json.loads(block)
        except json.JSONDecodeError:
            continue
    return None


# ---------------------------------------------------------------------------
# 2) Collector — canonical 소스를 읽어 ground-truth dict 를 만든다.
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
    """canonical 소스를 읽어 ground-truth 사실 dict 를 반환한다."""
    ver_props_text = _read(repo_root / "version.properties")
    app_ver = parse_app_version(ver_props_text)
    wear_ver = parse_wear_version(ver_props_text)
    api_ver = parse_backend_api_version(_read(repo_root / "backend" / "app" / "__init__.py"))

    versions_dir = repo_root / "backend" / "alembic" / "versions"
    py_files = sorted(versions_dir.glob("*.py")) if versions_dir.is_dir() else []
    if py_files:
        pairs = [parse_revision_pair(_read(p)) for p in py_files]
        alembic = compute_alembic_head(pairs)
    else:
        canonical_sql = repo_root / "backend" / "sql" / "02_schema_create_all.sql"
        alembic = compute_alembic_head([("02_schema_create_all.sql", None)] if canonical_sql.is_file() else [])

    cors = parse_cors_default(_read(repo_root / "backend" / "app" / "config.py"))

    routers_dir = repo_root / "backend" / "app" / "routers"
    routes: list[dict[str, str]] = []
    for p in sorted(routers_dir.glob("*.py")):
        routes.extend(count_routes_in_text(_read(p)))

    py_runtime = parse_python_runtime(
        _read(repo_root / "backend" / "Dockerfile"),
        _read(repo_root / "backend" / "pyproject.toml"),
    )
    jwt_algs = parse_jwt_algorithms(_read(repo_root / "backend" / "app" / "dependencies.py"))

    tests_dir = repo_root / "backend" / "tests"
    test_files = sorted(tests_dir.rglob("test_*.py")) if tests_dir.is_dir() else []
    test_count = count_test_functions([_read(p) for p in test_files])

    android_test_Annotation_re = re.compile(r"^\s*@Test\b", re.MULTILINE)
    android_test_counts: dict[str, int] = {}
    for mod_key, mod_path in (
        ("app", repo_root / "app" / "src" / "test"),
        ("wear", repo_root / "wear" / "src" / "test"),
        ("core_wear_contract", repo_root / "core" / "wear-contract" / "src" / "test"),
    ):
        kt_files = sorted(mod_path.rglob("*.kt")) if mod_path.is_dir() else []
        android_test_counts[mod_key] = sum(len(android_test_Annotation_re.findall(_read(p))) for p in kt_files)

    return {
        "app_version": app_ver,
        "wear_version_code": wear_ver,
        "backend_api_version": api_ver,
        "alembic": alembic,
        "cors_origins_default": cors,
        "python_runtime": py_runtime,
        "jwt_algorithms": jwt_algs,
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
        "android_tests": {
            "total": sum(android_test_counts.values()),
            "by_module": android_test_counts,
        },
    }


# ---------------------------------------------------------------------------
# 3) Deterministic Linter Rules (100% 결정론적 문서 감사 엔진)
# ---------------------------------------------------------------------------

DOC_COPIES: list[str] = [
    "CLAUDE.md",
    "README.md",
    "docs/ops/operations-snapshot.md",
    "docs/TRD.md",
    "docs/PRD.md",
    "docs/SPEC.md",
    "docs/conventions/versioning.md",
    "docs/CHANGELOG.md",
]


def audit_app_version_in_text(file_rel: str, text: str, expected_name: str, expected_code: str) -> list[dict[str, Any]]:
    """문서 텍스트에서 '현재 상태' 버전 서술을 검사하여 드리프트를 반환한다."""
    findings: list[dict[str, Any]] = []

    # 1. README.md 배지 및 헤더
    if file_rel == "README.md":
        name_match = re.search(r"!\[versionName\]\(https://img\.shields\.io/badge/versionName-([^)-]+)-blue\)", text)
        if name_match and name_match.group(1) != expected_name:
            findings.append({
                "file": file_rel,
                "locator": "README 배지 (versionName)",
                "doc_says": name_match.group(1),
                "ground_truth": expected_name,
                "kind": "version",
                "severity": "high",
                "note": "README 상단 versionName 배지가 version.properties 와 불일치합니다.",
            })
        code_match = re.search(r"!\[versionCode\]\(https://img\.shields\.io/badge/versionCode-(\d+)-blue\)", text)
        if code_match and code_match.group(1) != expected_code:
            findings.append({
                "file": file_rel,
                "locator": "README 배지 (versionCode)",
                "doc_says": code_match.group(1),
                "ground_truth": expected_code,
                "kind": "version",
                "severity": "high",
                "note": "README 상단 versionCode 배지가 version.properties 와 불일치합니다.",
            })
        stage_match = re.search(r"-\s*\*\*현재 단계\*\*\s*—\s*\*\*v([^\s(]+)\s*\(versionCode\s*(\d+)", text)
        if stage_match:
            doc_n, doc_c = stage_match.group(1), stage_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "소개 > 현재 단계 서술",
                    "doc_says": f"v{doc_n} (versionCode {doc_c})",
                    "ground_truth": f"v{expected_name} (versionCode {expected_code})",
                    "kind": "version",
                    "severity": "high",
                    "note": "README '현재 단계' 본문 서술이 version.properties 와 불일치합니다.",
                })

    # 2. docs/ops/operations-snapshot.md
    elif file_rel == "docs/ops/operations-snapshot.md":
        # §1 클라이언트 표: | versionName / versionCode | **`0.7.3` / `44`
        table_match = re.search(r"\|\s*versionName\s*/\s*versionCode\s*\|\s*\*\*`([^`]+)`\s*/\s*`([^`]+)`", text)
        if table_match:
            doc_n, doc_c = table_match.group(1), table_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "§1 클라이언트 표 (versionName / versionCode)",
                    "doc_says": f"{doc_n}/{doc_c}",
                    "ground_truth": f"{expected_name}/{expected_code}",
                    "kind": "version",
                    "severity": "high",
                    "note": "operations-snapshot §1 클라이언트 표의 현재 버전이 SSoT 와 불일치합니다.",
                })

    # 3. CLAUDE.md
    elif file_rel == "CLAUDE.md":
        # Current state: versionName `0.7.2` (versionCode `43`, ...)
        cur_match = re.search(r"\*\*Current state\*\*:\s*versionName\s*`([^`]+)`\s*\(versionCode\s*`([^`]+)`", text)
        if cur_match:
            doc_n, doc_c = cur_match.group(1), cur_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "Current state 서술",
                    "doc_says": f"versionName {doc_n} (versionCode {doc_c})",
                    "ground_truth": f"versionName {expected_name} (versionCode {expected_code})",
                    "kind": "version",
                    "severity": "high",
                    "note": "CLAUDE.md 상단 Current state 서술이 version.properties 와 불일치합니다.",
                })
        # App version: 저장소 및 Play 운영 versionName **`0.7.2`**, versionCode **`43`**
        app_match = re.search(r"\bApp version\b.*?:.*?versionName\s*\*\*`([^`]+)`\*\*,\s*versionCode\s*\*\*`([^`]+)`\*\*", text)
        if app_match:
            doc_n, doc_c = app_match.group(1), app_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "Key Technical Details > App version 서술",
                    "doc_says": f"{doc_n}/{doc_c}",
                    "ground_truth": f"{expected_name}/{expected_code}",
                    "kind": "version",
                    "severity": "high",
                    "note": "CLAUDE.md 기술 세부사항의 App version 이 version.properties 와 불일치합니다.",
                })

    # 4. docs/TRD.md
    elif file_rel == "docs/TRD.md":
        # > **현재 상태:** Google Play 프로덕션 및 저장소 최신 릴리스 **v0.7.2/43 ...
        status_match = re.search(r">\s*\*\*현재 상태:\*\*.*?\*\*v([^\/]+)\/(\d+)", text)
        if status_match:
            doc_n, doc_c = status_match.group(1), status_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "현재 상태 헤더 서술",
                    "doc_says": f"v{doc_n}/{doc_c}",
                    "ground_truth": f"v{expected_name}/{expected_code}",
                    "kind": "version",
                    "severity": "high",
                    "note": "TRD 상단 현재 상태의 앱 릴리스 버전이 version.properties 와 불일치합니다.",
                })
        # VERSION_NAME=0.7.2, VERSION_CODE=43 (또는 `VERSION_NAME=0.7.2`, `VERSION_CODE=43`)
        ssot_match = re.search(r"VERSION_NAME=([^\s,`]+)[`,\s]+VERSION_CODE=([^\s\)`]+)", text)
        if ssot_match:
            doc_n, doc_c = ssot_match.group(1), ssot_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "버전 관리 SSoT 서술",
                    "doc_says": f"VERSION_NAME={doc_n}, VERSION_CODE={doc_c}",
                    "ground_truth": f"VERSION_NAME={expected_name}, VERSION_CODE={expected_code}",
                    "kind": "version",
                    "severity": "high",
                    "note": "TRD 버전 관리 SSoT 예시 서술이 version.properties 와 불일치합니다.",
                })

    # 5. docs/PRD.md
    elif file_rel == "docs/PRD.md":
        # **제품 버전:** v0.7.3 (versionCode 44...)
        prd_match = re.search(r"\*\*제품 버전:\*\*\s*v([^\s]+)\s*\(versionCode\s*(\d+)", text)
        if prd_match:
            doc_n, doc_c = prd_match.group(1), prd_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "제품 버전 헤더",
                    "doc_says": f"v{doc_n} (versionCode {doc_c})",
                    "ground_truth": f"v{expected_name} (versionCode {expected_code})",
                    "kind": "version",
                    "severity": "high",
                    "note": "PRD 상단 제품 버전 서술이 version.properties 와 불일치합니다.",
                })

    # 6. docs/SPEC.md
    elif file_rel == "docs/SPEC.md":
        spec_match = re.search(r"모바일\s*`:app`\(`versionCode=(\d+)`", text)
        if spec_match and spec_match.group(1) != expected_code:
            findings.append({
                "file": file_rel,
                "locator": "상단 Wear OS 6 & 3모듈 아키텍처 (:app versionCode)",
                "doc_says": f"versionCode={spec_match.group(1)}",
                "ground_truth": f"versionCode={expected_code}",
                "kind": "version",
                "severity": "high",
                "note": "SPEC.md 상단 모바일 :app versionCode 서술이 version.properties 와 불일치합니다.",
            })

    # 7. docs/conventions/versioning.md
    elif file_rel == "docs/conventions/versioning.md":
        conv_match = re.search(
            r"\|\s*\*\*Android 모바일 앱 \(`:app`\)\*\*\s*\|[^|]*현행\s*`([^`]+)`\s*/\s*`(\d+)`",
            text,
        )
        if conv_match:
            doc_n, doc_c = conv_match.group(1), conv_match.group(2)
            if doc_n != expected_name or doc_c != expected_code:
                findings.append({
                    "file": file_rel,
                    "locator": "§1 독립 버전 체계 표 (:app)",
                    "doc_says": f"{doc_n}/{doc_c}",
                    "ground_truth": f"{expected_name}/{expected_code}",
                    "kind": "version",
                    "severity": "high",
                    "note": "versioning.md §1 표의 현행 버전이 version.properties 와 불일치합니다.",
                })

    return findings


def audit_backend_api_version_in_text(file_rel: str, text: str, expected_api_ver: str) -> list[dict[str, Any]]:
    """문서에서 백엔드 API 버전(1.0.0) 서술을 검사한다."""
    findings: list[dict[str, Any]] = []
    # API version `1.0.0`
    for m in re.finditer(r"API version\s*`([^`]+)`", text):
        found = m.group(1)
        if found != expected_api_ver:
            findings.append({
                "file": file_rel,
                "locator": m.group(0),
                "doc_says": found,
                "ground_truth": expected_api_ver,
                "kind": "api-version",
                "severity": "high",
                "note": "백엔드 API 버전 서술이 app/__init__.py:__version__ 과 불일치합니다.",
            })
    return findings


def audit_python_runtime(facts: dict[str, Any]) -> list[dict[str, Any]]:
    """파이썬 런타임 설정 간의 불일치를 검사한다."""
    findings: list[dict[str, Any]] = []
    py_info = facts.get("python_runtime", {})
    if py_info.get("mismatch"):
        findings.append({
            "file": "backend/Dockerfile & pyproject.toml",
            "locator": "python_runtime",
            "doc_says": f"Dockerfile={py_info.get('dockerfile')}, ruff={py_info.get('ruff_target_version')}, mypy={py_info.get('mypy_python_version')}",
            "ground_truth": "3개 파일의 Python 버전이 일치해야 함",
            "kind": "python-runtime",
            "severity": "high",
            "note": "백엔드 Python 런타임 설정 출처(Dockerfile / ruff / mypy) 간 버전이 어긋나 있습니다.",
        })
    return findings


def audit_api_routes_count(file_rel: str, text: str, expected_total: int) -> list[dict[str, Any]]:
    """CLAUDE.md, README.md, PRD.md, TRD.md, SPEC.md 의 엔드포인트 총 개수 서술을 검사한다."""
    findings: list[dict[str, Any]] = []
    if file_rel == "CLAUDE.md":
        m = re.search(r"API Endpoints \(총 (\d+) =", text)
        if m and int(m.group(1)) != expected_total:
            findings.append({
                "file": file_rel,
                "locator": m.group(0),
                "doc_says": f"총 {m.group(1)}개",
                "ground_truth": f"총 {expected_total}개",
                "kind": "endpoint-count",
                "severity": "med",
                "note": f"CLAUDE.md 엔드포인트 총수 서술({m.group(1)}개)이 실제 라우터 합계({expected_total}개)와 불일치합니다.",
            })
    elif file_rel == "README.md":
        m = re.search(r"### API 엔드포인트 \((\d+)\s*개", text)
        if m and int(m.group(1)) != expected_total:
            findings.append({
                "file": file_rel,
                "locator": m.group(0),
                "doc_says": f"총 {m.group(1)}개",
                "ground_truth": f"총 {expected_total}개",
                "kind": "endpoint-count",
                "severity": "med",
                "note": f"README.md API 엔드포인트 총수 서술({m.group(1)}개)이 실제 라우터 합계({expected_total}개)와 불일치합니다.",
            })
    elif file_rel in {"docs/PRD.md", "docs/TRD.md", "docs/SPEC.md"}:
        m = re.search(r"총\s*(\d+)\s*개\s*엔드포인트", text)
        if m and int(m.group(1)) != expected_total:
            findings.append({
                "file": file_rel,
                "locator": m.group(0),
                "doc_says": f"총 {m.group(1)}개",
                "ground_truth": f"총 {expected_total}개",
                "kind": "endpoint-count",
                "severity": "med",
                "note": f"{file_rel} API 엔드포인트 총수 서술({m.group(1)}개)이 실제 라우터 합계({expected_total}개)와 불일치합니다.",
            })
    return findings


def audit_stale_tech_in_text(file_rel: str, text: str) -> list[dict[str, Any]]:
    """현재형 서술 구역에 폐기된 기술명(Supabase, Alembic 등)이 존재하는지 검사한다."""
    findings: list[dict[str, Any]] = []

    # CLAUDE.md 기술 스택 및 현행 아키텍처 구역 검사
    if file_rel == "CLAUDE.md":
        head_lines = "\n".join(text.splitlines()[:30])
        if "Supabase Auth" in head_lines and "전환" not in head_lines:
            findings.append({
                "file": file_rel,
                "locator": "상단 프로젝트 개요",
                "doc_says": "Supabase Auth",
                "ground_truth": "Microsoft Entra External ID (Supabase 폐기 완료)",
                "kind": "stale-tech",
                "severity": "high",
                "note": "프로젝트 개요 상단에 폐기된 Supabase Auth 가 현재형으로 남아 있습니다.",
            })

    return findings


def audit_wear_version_in_text(file_rel: str, text: str, expected_wear_code: str) -> list[dict[str, Any]]:
    """문서 텍스트(현재 상태 구역)에서 wearVersionCode 표기가 SSoT(version.properties)와 일치하는지 검사한다.

    append-only 이력 구역(docs/CHANGELOG.md, operations-snapshot.md §13, README.md '## 구현 현황')은
    과거 릴리스 기록 보존을 위해 스캔 대상에서 제외한다(INC-2026-06-16-27 원칙).
    """
    findings: list[dict[str, Any]] = []
    if not expected_wear_code or file_rel == "docs/CHANGELOG.md":
        return findings

    scan_text = text
    if file_rel == "docs/ops/operations-snapshot.md":
        scan_text = text.split("## 13.", 1)[0]
    elif file_rel == "README.md":
        scan_text = text.split("### 마일스톤 진행", 1)[0].split("## 구현 현황", 1)[0]

    for m in re.finditer(r"wearVersionCode\s*=\s*`?(\d+)`?", scan_text):
        doc_wc = m.group(1)
        if doc_wc != expected_wear_code:
            findings.append({
                "file": file_rel,
                "locator": "wearVersionCode 서술",
                "doc_says": f"wearVersionCode={doc_wc}",
                "ground_truth": f"wearVersionCode={expected_wear_code}",
                "kind": "wear-version",
                "severity": "high",
                "note": "문서 내 wearVersionCode 표기가 version.properties 와 불일치합니다.",
            })

    if file_rel == "docs/conventions/versioning.md":
        wear_row_match = re.search(
            r"\|\s*\*\*Wear OS 워치 앱 \(`:wear`\)\*\*\s*\|[^|]*현행\s*`([^`]+)`\s*/\s*`(\d+)`",
            scan_text,
        )
        if wear_row_match and wear_row_match.group(2) != expected_wear_code:
            findings.append({
                "file": file_rel,
                "locator": "§1 독립 버전 체계 표 (:wear)",
                "doc_says": f"wearVersionCode={wear_row_match.group(2)}",
                "ground_truth": f"wearVersionCode={expected_wear_code}",
                "kind": "wear-version",
                "severity": "high",
                "note": "versioning.md §1 표의 :wear 현행 wearVersionCode 가 version.properties 와 불일치합니다.",
            })

    return findings


def audit_documents(repo_root: Path, facts: dict[str, Any], doc_copies: list[str]) -> dict[str, Any]:
    """모든 규칙을 실행하여 구조화된 감사 리포트 dict 를 반환한다."""
    app_ver = facts.get("app_version", {})
    expected_name = app_ver.get("versionName") or ""
    expected_code = app_ver.get("versionCode") or ""
    expected_wear_code = facts.get("wear_version_code") or ""
    expected_api = facts.get("backend_api_version") or ""
    expected_routes = facts.get("api_routes", {}).get("total", 0)

    all_findings: list[dict[str, Any]] = []

    # 1. 런타임 자체 불일치 검사
    all_findings.extend(audit_python_runtime(facts))

    # 2. 문서별 텍스트 대조
    for doc_rel in doc_copies:
        path = repo_root / doc_rel
        if not path.is_file():
            continue
        text = _read(path)
        all_findings.extend(audit_app_version_in_text(doc_rel, text, expected_name, expected_code))
        all_findings.extend(audit_wear_version_in_text(doc_rel, text, expected_wear_code))
        all_findings.extend(audit_backend_api_version_in_text(doc_rel, text, expected_api))
        all_findings.extend(audit_api_routes_count(doc_rel, text, expected_routes))
        all_findings.extend(audit_stale_tech_in_text(doc_rel, text))

    clean = len(all_findings) == 0
    summary = (
        "✓ 모든 문서 서술이 코드베이스의 ground-truth 사실(SSoT)과 100% 일치합니다."
        if clean
        else f"⚠ {len(all_findings)}건의 문서 불일치(드리프트)가 감지되었습니다."
    )

    return {
        "clean": clean,
        "summary": summary,
        "findings": all_findings,
    }


# ---------------------------------------------------------------------------
# 4) CLI / 리포트 렌더링
# ---------------------------------------------------------------------------

_SEV_ORDER = {"high": 0, "med": 1, "low": 2}


def render_report(result: dict[str, Any]) -> str:
    """결정론적 감사 결과를 사람이 읽을 수 있는 텍스트 리포트로 렌더링한다."""
    findings = sorted(
        result.get("findings", []),
        key=lambda f: _SEV_ORDER.get(f.get("severity", "low"), 9),
    )
    lines = [
        "============================================================",
        "  📄 문서 일관성 감사 리포트 (Deterministic Document Audit)",
        "============================================================",
        f"요약: {result.get('summary', '(없음)')}",
        "",
    ]
    if not findings:
        lines.append("✓ 드리프트 없음 — 6대 핵심 문서의 불변식이 최신 SSoT 와 일치합니다.")
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
    lines.append("")
    lines.append("(엔진: Deterministic Linter · 외부 AI 의존성 없음 · 비용: $0.0000)")
    return "\n".join(lines)


def _default_repo_root() -> Path:
    """저장소 루트를 추정한다."""
    return Path(__file__).resolve().parents[2]


def main(argv: list[str] | None = None) -> int:
    """CLI 진입점. exit code 를 반환한다."""
    _force_utf8_stdio()
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo-root", type=Path, default=_default_repo_root())
    parser.add_argument("--collect-only", action="store_true", help="결정론적 사실만 JSON 출력")
    parser.add_argument("--json", action="store_true", help="전체 결과를 기계용 JSON 으로 출력")
    parser.add_argument("--strict", action="store_true", help="드리프트 발견 시 exit 2 (CI 게이트용)")
    args = parser.parse_args(argv)

    repo_root: Path = args.repo_root.resolve()
    facts = collect_facts(repo_root)

    if args.collect_only:
        print(json.dumps(facts, ensure_ascii=False, indent=2))
        return 0

    audit_result = audit_documents(repo_root, facts, DOC_COPIES)

    if args.json:
        print(json.dumps({"facts": facts, **audit_result}, ensure_ascii=False, indent=2))
    else:
        print(render_report(audit_result))

    has_drift = not audit_result.get("clean", True)
    if args.strict and has_drift:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
