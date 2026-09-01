"""법적 고지 공개 페이지(개인정보/계정삭제) — 라우트 + SSoT 드리프트 가드."""
from pathlib import Path

import pytest

# backend/tests/test_legal.py → parents[1]=backend, parents[2]=repo root
_BACKEND_LEGAL = Path(__file__).resolve().parents[1] / "app" / "legal"
_DOCS_STORE = Path(__file__).resolve().parents[2] / "docs" / "store"


@pytest.mark.asyncio
async def test_privacy_route_returns_html(client):
    resp = await client.get("/privacy")
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/html")
    assert "max-age" in resp.headers.get("cache-control", "")  # 정적 콘텐츠 캐시
    assert "개인정보 처리방침" in resp.text
    assert "<html" in resp.text  # 단순 md 가 아니라 완성된 HTML 페이지


@pytest.mark.asyncio
async def test_account_deletion_route_returns_html(client):
    resp = await client.get("/account-deletion")
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/html")
    assert "max-age" in resp.headers.get("cache-control", "")
    assert "계정" in resp.text and "삭제" in resp.text
    assert "<html" in resp.text


def test_html_routes_excluded_from_openapi_schema():
    """HTML 브라우저 라우트는 openapi.json(= Android 생성기 입력)에서 제외돼야 한다.

    포함되면 앱이 절대 호출 않는 타입 없는 죽은 클라이언트 메서드(LegalApi 등)를 생성한다.
    회귀 가드: 누군가 include_in_schema=False 를 빼면 실패. /health 등 JSON 라우트는 유지.
    """
    from app.main import app

    paths = app.openapi()["paths"]
    for p in ("/privacy", "/account-deletion"):
        assert p not in paths, f"{p} 는 HTML 라우트 — openapi 스키마에서 제외돼야 함"
    assert "/health" in paths  # JSON 라우트는 문서화 유지


@pytest.mark.asyncio
async def test_legal_routes_are_public(client_no_auth):
    """Play 심사봇·미설치 사용자도 접근 가능해야 함 — 인증 불필요 공개 라우트."""
    assert (await client_no_auth.get("/privacy")).status_code == 200
    assert (await client_no_auth.get("/account-deletion")).status_code == 200


@pytest.mark.parametrize("filename", ["privacy-policy.md", "account-deletion.md"])
def test_legal_docs_in_sync_with_ssot(filename):
    """드리프트 가드: backend/app/legal/*.md 가 SSoT docs/store/*.md 와 동일해야 한다.

    백엔드 빌드 컨텍스트(backend/)는 repo 루트 docs/store/ 에 접근 불가라 동기화 사본을 둔다.
    docs/store 를 편집하고 `bash scripts/sync-legal-docs.sh` 를 안 돌리면 이 테스트가 실패해
    Play 등록 페이지가 stale 로 배포되는 것을 차단한다(openapi.json drift 가드와 동일 패턴).
    """
    src = (_DOCS_STORE / filename).read_text(encoding="utf-8")
    dst = (_BACKEND_LEGAL / filename).read_text(encoding="utf-8")
    assert src == dst, (
        f"{filename} 가 SSoT 와 어긋남 — `bash scripts/sync-legal-docs.sh` 실행 후 커밋하세요."
    )
