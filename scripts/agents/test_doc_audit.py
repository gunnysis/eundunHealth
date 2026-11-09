"""doc_audit 의 collector·파서 및 결정론적 린터 단위 테스트.

실행: python -m pytest scripts/agents/test_doc_audit.py -v
"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from doc_audit import (  # noqa: E402  (sys.path 조작 후 import)
    audit_api_routes_count,
    audit_app_version_in_text,
    audit_backend_api_version_in_text,
    audit_documents,
    audit_python_runtime,
    audit_stale_tech_in_text,
    audit_wear_version_in_text,
    collect_facts,
    compute_alembic_head,
    count_routes_in_text,
    count_test_functions,
    extract_json_block,
    parse_app_version,
    parse_backend_api_version,
    parse_cors_default,
    parse_jwt_algorithms,
    parse_python_runtime,
    parse_revision_pair,
    parse_wear_version,
    render_report,
)

REPO_ROOT = Path(__file__).resolve().parents[2]


class TestParseAppVersion:
    def test_extracts_name_and_code(self):
        text = "# comment\nversionName=0.1.15\nversionCode=29\n"
        assert parse_app_version(text) == {"versionName": "0.1.15", "versionCode": "29"}

    def test_missing_returns_none(self):
        assert parse_app_version("nothing here") == {"versionName": None, "versionCode": None}


class TestParseBackendApiVersion:
    def test_double_quotes(self):
        assert parse_backend_api_version('__version__ = "1.0.0"') == "1.0.0"

    def test_single_quotes(self):
        assert parse_backend_api_version("__version__ = '2.3.4'") == "2.3.4"

    def test_missing(self):
        assert parse_backend_api_version("x = 1") is None


class TestParseRevisionPair:
    def test_typed_form(self):
        text = 'revision: str = "c849579de6c4"\ndown_revision: Union[str, None] = "fa3915deab2f"\n'
        assert parse_revision_pair(text) == ("c849579de6c4", "fa3915deab2f")

    def test_none_down_revision(self):
        text = 'revision: str = "131baaa7b80b"\ndown_revision = None\n'
        assert parse_revision_pair(text) == ("131baaa7b80b", None)


class TestComputeAlembicHead:
    def test_linear_single_head(self):
        result = compute_alembic_head([("a", None), ("b", "a"), ("c", "b")])
        assert result["head"] == "c"
        assert result["revision_count"] == 3

    def test_branch_yields_no_single_head(self):
        result = compute_alembic_head([("a", None), ("b", "a"), ("c", "a")])
        assert result["head"] is None
        assert set(result["heads"]) == {"b", "c"}


class TestParseCorsDefault:
    def test_empty_list(self):
        assert parse_cors_default("    cors_origins: list[str] = []\n") == "[]"

    def test_wildcard(self):
        assert parse_cors_default('cors_origins: list[str] = ["*"]') == '["*"]'


class TestCountRoutes:
    def test_counts_methods_and_paths(self):
        text = '@router.get("/profile")\n@router.put("/profile")\n@router.post("/badges/{key}")\n'
        routes = count_routes_in_text(text)
        assert {"method": "get", "path": "/profile"} in routes
        assert len(routes) == 3

    def test_prepends_router_prefix(self):
        text = (
            'router = APIRouter(prefix="/meals", tags=["meals"])\n'
            '@router.post("/generate")\n'
            '@router.get("/current")\n'
        )
        routes = count_routes_in_text(text)
        assert routes == [
            {"method": "post", "path": "/meals/generate"},
            {"method": "get", "path": "/meals/current"},
        ]


class TestCountTestFunctions:
    def test_counts_sync_and_async(self):
        texts = ["def test_a(): pass\nasync def test_b(): pass\n", "def test_c(): pass\n"]
        assert count_test_functions(texts) == 3

    def test_ignores_non_test_defs(self):
        assert count_test_functions(["def helper(): pass\n"]) == 0

    def test_expands_parametrize_literal_list(self):
        text = (
            "import pytest\n"
            '@pytest.mark.parametrize("f", ["a.md", "b.md"])\n'
            "def test_x(f): pass\n"
        )
        assert count_test_functions([text]) == 2

    def test_expands_stacked_parametrize_as_product(self):
        text = (
            "import pytest\n"
            '@pytest.mark.parametrize("a", [1, 2])\n'
            '@pytest.mark.parametrize("b", [3, 4, 5])\n'
            "def test_x(a, b): pass\n"
        )
        assert count_test_functions([text]) == 6

    def test_non_literal_parametrize_counts_once(self):
        text = (
            "import pytest\n"
            "CASES = [1, 2, 3]\n"
            '@pytest.mark.parametrize("a", CASES)\n'
            "def test_x(a): pass\n"
        )
        assert count_test_functions([text]) == 1

    def test_counts_methods_inside_class(self):
        text = "class TestFoo:\n    def test_a(self): pass\n    def test_b(self): pass\n"
        assert count_test_functions([text]) == 2

    def test_unparseable_text_falls_back_to_regex(self):
        assert count_test_functions(["def test_a(: pass\n"]) == 1


class TestExtractJsonBlock:
    def test_extracts_last_valid_block(self):
        text = '```json\n{"a": 1}\n```\nblah\n```json\n{"b": 2}\n```'
        assert extract_json_block(text) == {"b": 2}

    def test_none_when_absent(self):
        assert extract_json_block("no block here") is None

    def test_skips_malformed_to_valid(self):
        text = '```json\n{nope}\n```\n```json\n{"ok": true}\n```'
        assert extract_json_block(text) == {"ok": True}


class TestCollectFactsIntegration:
    @pytest.fixture(scope="class")
    @classmethod
    def facts(cls):
        return collect_facts(REPO_ROOT)

    def test_has_all_keys(self, facts):
        for key in (
            "app_version", "backend_api_version", "alembic",
            "cors_origins_default", "api_routes", "backend_tests",
            "python_runtime", "jwt_algorithms",
        ):
            assert key in facts

    def test_app_version_well_formed(self, facts):
        assert facts["app_version"]["versionName"]
        assert facts["app_version"]["versionCode"].isdigit()

    def test_alembic_is_single_head(self, facts):
        assert facts["alembic"]["head"] is not None
        assert len(facts["alembic"]["heads"]) == 1

    def test_routes_collected(self, facts):
        assert facts["api_routes"]["total"] >= 14
        assert facts["api_routes"]["by_method"].get("get", 0) >= 1

    def test_test_count_positive_int(self, facts):
        assert isinstance(facts["backend_tests"]["count"], int)
        assert facts["backend_tests"]["count"] > 0

    def test_python_runtime_sources_agree(self, facts):
        rt = facts["python_runtime"]
        assert rt["dockerfile"], "Dockerfile 의 FROM python:X.Y 를 못 읽었다"
        assert rt["mismatch"] is False, f"런타임 출처 불일치: {rt}"

    def test_jwt_algorithms_collected(self, facts):
        algs = facts["jwt_algorithms"]
        assert isinstance(algs, list) and algs, "algorithms=[...] 리터럴을 못 읽었다"


DOCKERFILE_314 = """\
FROM python:3.14-slim
RUN apt-get update
"""

PYPROJECT_314 = """\
target-version = "py314"
python_version = "3.14"
"""


class TestParsePythonRuntime:
    def test_reads_all_three_sources(self):
        assert parse_python_runtime(DOCKERFILE_314, PYPROJECT_314) == {
            "dockerfile": "3.14",
            "ruff_target_version": "3.14",
            "mypy_python_version": "3.14",
            "mismatch": False,
        }

    def test_flags_mismatch(self):
        rt = parse_python_runtime("FROM python:3.12-slim\n", PYPROJECT_314)
        assert rt["mismatch"] is True

    def test_missing_sources_are_none_not_mismatch(self):
        assert parse_python_runtime("FROM alpine:3\n", "") == {
            "dockerfile": None,
            "ruff_target_version": None,
            "mypy_python_version": None,
            "mismatch": False,
        }


class TestParseJwtAlgorithms:
    def test_single_algorithm(self):
        assert parse_jwt_algorithms('        algorithms=["RS256"],\n') == ["RS256"]

    def test_multiple_algorithms(self):
        assert parse_jwt_algorithms("algorithms=['RS256', 'ES256']") == ["RS256", "ES256"]

    def test_absent_returns_empty(self):
        assert parse_jwt_algorithms("no algorithms here") == []


# ---------------------------------------------------------------------------
# 신규 결정론적 린터(Deterministic Linter) 단위 테스트
# ---------------------------------------------------------------------------


class TestAuditAppVersionInText:
    def test_readme_matches_clean(self):
        text = (
            "![versionName](https://img.shields.io/badge/versionName-0.7.3-blue)\n"
            "![versionCode](https://img.shields.io/badge/versionCode-44-blue)\n"
        )
        findings = audit_app_version_in_text("README.md", text, "0.7.3", "44")
        assert len(findings) == 0

    def test_readme_drift_detected(self):
        text = (
            "![versionName](https://img.shields.io/badge/versionName-0.7.2-blue)\n"
            "![versionCode](https://img.shields.io/badge/versionCode-43-blue)\n"
        )
        findings = audit_app_version_in_text("README.md", text, "0.7.3", "44")
        assert len(findings) == 2
        assert findings[0]["kind"] == "version"
        assert findings[0]["severity"] == "high"

    def test_claude_md_drift_detected(self):
        text = (
            "**Current state**: versionName `0.7.2` (versionCode `43`, ...)\n"
            "- **App version**: 저장소 및 Play 운영 versionName **`0.7.2`**, versionCode **`43`** — SSoT\n"
        )
        findings = audit_app_version_in_text("CLAUDE.md", text, "0.7.3", "44")
        assert len(findings) == 2
        assert "Current state" in findings[0]["locator"]
        assert "App version" in findings[1]["locator"]

    def test_operations_snapshot_drift_detected(self):
        text = "| versionName / versionCode | **`0.7.2` / `43` (태그 ...)** |"
        findings = audit_app_version_in_text("docs/ops/operations-snapshot.md", text, "0.7.3", "44")
        assert len(findings) == 1
        assert "0.7.2/43" in findings[0]["doc_says"]

    def test_trd_drift_detected(self):
        text = (
            "> **현재 상태:** Google Play 프로덕션 및 저장소 최신 릴리스 **v0.7.2/43 (`completed` 100%)**\n"
            "`VERSION_NAME=0.7.2`, `VERSION_CODE=43`\n"
        )
        findings = audit_app_version_in_text("docs/TRD.md", text, "0.7.3", "44")
        assert len(findings) == 2
        assert findings[0]["doc_says"] == "v0.7.2/43"
        assert findings[1]["doc_says"] == "VERSION_NAME=0.7.2, VERSION_CODE=43"

    def test_readme_stage_narrative_drift_detected(self):
        text = "- **현재 단계** — **v0.7.3 (versionCode 44, `wearVersionCode=10001`)**\n"
        findings = audit_app_version_in_text("README.md", text, "0.8.0", "45")
        assert len(findings) == 1
        assert findings[0]["locator"] == "소개 > 현재 단계 서술"

    def test_spec_and_versioning_drift_detected(self):
        spec_text = "모바일 `:app`(`versionCode=44`, `EundunDatabase` v4)"
        assert len(audit_app_version_in_text("docs/SPEC.md", spec_text, "0.8.0", "45")) == 1

        conv_text = "| **Android 모바일 앱 (`:app`)** | 루트 `version.properties` (`versionName`+`versionCode`, 현행 `0.7.3` / `44`) |"
        assert len(audit_app_version_in_text("docs/conventions/versioning.md", conv_text, "0.8.0", "45")) == 1


class TestAuditBackendApiVersionInText:
    def test_clean_match(self):
        text = "API version `1.0.0` — backend/app/__init__.py"
        assert len(audit_backend_api_version_in_text("CLAUDE.md", text, "1.0.0")) == 0

    def test_drift_detected(self):
        text = "API version `2.0.0` — backend/app/__init__.py"
        findings = audit_backend_api_version_in_text("CLAUDE.md", text, "1.0.0")
        assert len(findings) == 1
        assert findings[0]["kind"] == "api-version"
        assert findings[0]["doc_says"] == "2.0.0"


class TestAuditPythonRuntimeMismatch:
    def test_mismatch_generates_finding(self):
        facts = {"python_runtime": {"mismatch": True, "dockerfile": "3.14", "ruff_target_version": "3.12"}}
        findings = audit_python_runtime(facts)
        assert len(findings) == 1
        assert findings[0]["kind"] == "python-runtime"
        assert findings[0]["severity"] == "high"

    def test_match_clean(self):
        facts = {"python_runtime": {"mismatch": False, "dockerfile": "3.14", "ruff_target_version": "3.14"}}
        assert len(audit_python_runtime(facts)) == 0


class TestAuditApiRoutesCount:
    def test_route_count_matches(self):
        text = "API Endpoints (총 27 = JWT 필요 22 + 공개 5)"
        assert len(audit_api_routes_count("CLAUDE.md", text, 27)) == 0

    def test_route_count_mismatch(self):
        text = "API Endpoints (총 25 = JWT 필요 20 + 공개 5)"
        findings = audit_api_routes_count("CLAUDE.md", text, 27)
        assert len(findings) == 1
        assert findings[0]["kind"] == "endpoint-count"


class TestAuditWearVersionInText:
    def test_parse_wear_version(self):
        assert parse_wear_version("versionName=0.7.3\nversionCode=44\nwearVersionCode=10001\n") == "10001"
        assert parse_wear_version("versionName=0.7.3\n") is None

    def test_wear_version_clean(self):
        text = "Wear OS 앱 (minSdk=36, wearVersionCode=10001)"
        assert len(audit_wear_version_in_text("README.md", text, "10001")) == 0

    def test_wear_version_drift_detected(self):
        text = "Wear OS 앱 (minSdk=36, wearVersionCode=9999)"
        findings = audit_wear_version_in_text("README.md", text, "10001")
        assert len(findings) == 1
        assert findings[0]["kind"] == "wear-version"

    def test_wear_version_preserves_append_only_history(self):
        assert len(audit_wear_version_in_text("docs/CHANGELOG.md", "wearVersionCode=10001", "10002")) == 0
        snap_text = "현재 wearVersionCode=10002\n## 13. 변경 이력\n| 2026-11-06 | wearVersionCode=10001 |"
        assert len(audit_wear_version_in_text("docs/ops/operations-snapshot.md", snap_text, "10002")) == 0
        readme_text = "현재 wearVersionCode=10002\n### 마일스톤 진행\n- [x] Foundation (wearVersionCode=10001)"
        assert len(audit_wear_version_in_text("README.md", readme_text, "10002")) == 0

    def test_wear_version_versioning_md_table_checked(self):
        conv_text = "| **Wear OS 워치 앱 (`:wear`)** | 루트 `version.properties` (`versionName`+`wearVersionCode`, 현행 `0.8.0` / `10001`) |"
        findings = audit_wear_version_in_text("docs/conventions/versioning.md", conv_text, "10002")
        assert len(findings) == 1
        assert findings[0]["locator"] == "§1 독립 버전 체계 표 (:wear)"


class TestRenderReport:
    def test_renders_clean_report(self):
        result = {"clean": True, "summary": "정상", "findings": []}
        output = render_report(result)
        assert "✓ 드리프트 없음" in output
        assert "Deterministic Linter" in output

    def test_renders_findings_report(self):
        result = {
            "clean": False,
            "summary": "1건 감지",
            "findings": [{
                "file": "CLAUDE.md",
                "locator": "Current state",
                "doc_says": "0.7.2",
                "ground_truth": "0.7.3",
                "kind": "version",
                "severity": "high",
                "note": "불일치",
            }],
        }
        output = render_report(result)
        assert "드리프트 1건:" in output
        assert "[1] (HIGH · version) CLAUDE.md" in output
