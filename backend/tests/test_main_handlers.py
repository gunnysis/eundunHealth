"""전역 예외 핸들러 — 예기치 못한 예외가 사용자에게 새지 않는지 고정한다."""
import httpx
import pytest
from fastapi import APIRouter

from app.main import app


@pytest.mark.asyncio
async def test_unhandled_exception_returns_generic_500():
    """내부 오류는 코드/메시지만 노출한다 — 예외 문구가 그대로 나가면 내부 구조가 샌다.

    `raise_app_exceptions=False` 를 쓰는 이유: starlette 의 ServerErrorMiddleware 는
    핸들러가 응답을 만든 **뒤에도 예외를 다시 던진다**(서버 로그·Sentry 를 위해). 기본
    ASGITransport 는 그걸 그대로 올려보내므로, 실제 클라이언트가 받는 응답을 보려면
    전송 계층에서 재전파를 꺼야 한다.
    """
    router = APIRouter()

    @router.get("/__boom__")
    async def _boom() -> None:
        raise RuntimeError("secret internal detail")

    app.include_router(router)
    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    try:
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as ac:
            resp = await ac.get("/__boom__")
    finally:
        app.router.routes = [r for r in app.router.routes if getattr(r, "path", None) != "/__boom__"]

    assert resp.status_code == 500
    assert resp.json() == {"code": "INTERNAL_ERROR", "message": "서버 내부 오류"}
    assert "secret internal detail" not in resp.text
