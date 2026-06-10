"""앱(API) 버전 SSoT 검증 — __version__ 이 OpenAPI info.version 으로 노출되는지."""

from app import __version__
from app.main import app


def test_version_is_independent_semver() -> None:
    # 백엔드는 prod 운영 중 → semver 1.0.0 에서 시작. 앱(version.properties)과 독립.
    assert __version__ == "1.0.0"


def test_openapi_info_version_matches_dunder() -> None:
    assert app.openapi()["info"]["version"] == __version__
