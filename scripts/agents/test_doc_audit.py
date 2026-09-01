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
    parse_jwt_algorithms,
    parse_python_runtime,
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

    def test_expands_parametrize_literal_list(self):
        """parametrize 는 def 1개를 케이스 N개로 확장한다 — pytest 실측과 맞추려면 세야 한다.

        이 off-by-one 때문에 수집기가 정확한 문서를 드리프트로 오탐했다(95 vs 96).
        """
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
        """리터럴이 아니면 정적으로 셀 수 없다 — 과대계상하지 않고 1로 둔다."""
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
        """문법 오류가 있어도 수집기가 죽으면 안 된다(감사 전체가 멈춘다)."""
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
    """실제 repo 통합 — 구조·타입과 *안정* 불변식만 핀(현재 버전값은 변동하므로 핀하지 않음)."""

    # pytest 9 부터 class-scoped fixture 를 인스턴스 메서드로 두면 deprecation 경고가 난다
    # (fixture 는 클래스당 1회 도는데 테스트마다 새 인스턴스라 self 에 넣은 값이 안 보인다).
    # 공식 권고대로 classmethod 로 선언한다.
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
        # 선형 이력 불변식: head 1개
        assert facts["alembic"]["head"] is not None
        assert len(facts["alembic"]["heads"]) == 1

    def test_routes_collected(self, facts):
        assert facts["api_routes"]["total"] >= 14
        assert facts["api_routes"]["by_method"].get("get", 0) >= 1

    def test_test_count_positive_int(self, facts):
        assert isinstance(facts["backend_tests"]["count"], int)
        assert facts["backend_tests"]["count"] > 0

    def test_python_runtime_sources_agree(self, facts):
        """Dockerfile · ruff · mypy 세 출처가 같은 런타임을 가리켜야 한다.

        2026-09-02 감사에서 문서 9곳이 "Python 3.12" 로 남았는데도 이 수집기가 못 잡았다.
        항목이 없었기 때문이다. 이제는 값을 내보내고 출처 간 불일치도 함께 판정한다.
        """
        rt = facts["python_runtime"]
        assert rt["dockerfile"], "Dockerfile 의 FROM python:X.Y 를 못 읽었다"
        assert rt["mismatch"] is False, f"런타임 출처 불일치: {rt}"

    def test_jwt_algorithms_collected(self, facts):
        """IdP 를 바꾸면 반드시 바뀌는 값 — 문서의 ES256 잔존을 잡기 위한 근거."""
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
        """Dockerfile 만 뒤처진 형태 — 런타임 번프에서 실제로 잘 나는 어긋남이다."""
        rt = parse_python_runtime("FROM python:3.12-slim\n", PYPROJECT_314)
        assert rt["mismatch"] is True

    def test_missing_sources_are_none_not_mismatch(self):
        """읽지 못한 것과 어긋난 것은 다르다 — 없는 값으로 거짓 양성을 만들지 않는다."""
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
