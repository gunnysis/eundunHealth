"""doc_audit 의 collector·파서 단위 테스트.

run_audit(Claude Agent SDK 경로)는 제외 — 결정론적 collector/파서 로직만 검증한다.
실행: python -m pytest scripts/agents/test_doc_audit.py -q
"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from doc_audit import (  # noqa: E402  (sys.path 조작 후 import)
    collect_facts,
    compute_alembic_head,
    count_routes_in_text,
    count_test_functions,
    extract_json_block,
    parse_app_version,
    parse_backend_api_version,
    parse_cors_default,
    parse_revision_pair,
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
        # b·c 가 둘 다 a 를 가리키면 head 후보 2개 → head=None(분기 신호)
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


class TestCountTestFunctions:
    def test_counts_sync_and_async(self):
        texts = ["def test_a(): pass\nasync def test_b(): pass\n", "def test_c(): pass\n"]
        assert count_test_functions(texts) == 3

    def test_ignores_non_test_defs(self):
        assert count_test_functions(["def helper(): pass\n"]) == 0


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
    """실제 repo 통합 — 구조·타입과 *안정* 불변식만 핀(현재 버전값은 변동하므로 핀하지 않음)."""

    @pytest.fixture(scope="class")
    def facts(self):
        return collect_facts(REPO_ROOT)

    def test_has_all_keys(self, facts):
        for key in (
            "app_version", "backend_api_version", "alembic",
            "cors_origins_default", "api_routes", "backend_tests",
        ):
            assert key in facts

    def test_app_version_well_formed(self, facts):
        assert facts["app_version"]["versionName"]
        assert facts["app_version"]["versionCode"].isdigit()

    def test_alembic_is_single_head(self, facts):
        # 선형 이력 불변식: head 1개
        assert facts["alembic"]["head"] is not None
        assert len(facts["alembic"]["heads"]) == 1

    def test_routes_collected(self, facts):
        assert facts["api_routes"]["total"] >= 14
        assert facts["api_routes"]["by_method"].get("get", 0) >= 1

    def test_test_count_positive_int(self, facts):
        assert isinstance(facts["backend_tests"]["count"], int)
        assert facts["backend_tests"]["count"] > 0
