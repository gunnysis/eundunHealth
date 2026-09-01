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


@pytest.mark.asyncio
async def test_validation_error_with_non_serializable_ctx_still_returns_422():
    """422 가 500 으로 뒤집히지 않는지 고정한다.

    pydantic 의 `errors()` 는 `ctx` 에 예외 객체나 `Decimal`·`date` 같은 값을 담을 수 있다
    (커스텀 validator 가 `ValueError` 를 던지는 흔한 경우). 이것을 `jsonable_encoder` 없이
    `JSONResponse` 에 넘기면 **핸들러가 끝난 뒤 응답 렌더링 단계**에서 직렬화가 터져
    422 가 500 이 된다 — 핸들러 밖이라 원인 추적도 어렵다. FastAPI 자체 핸들러가
    `jsonable_encoder` 를 쓰는 이유이며(`fastapi/exception_handlers.py`), 우리도 맞춘다.

    현재 스키마(`Field(ge/le)` 제약뿐)로는 재현되지 않으므로 커스텀 validator 를 가진
    임시 라우트로 그 조건을 만든다.
    """
    from decimal import Decimal

    from pydantic import BaseModel, field_validator

    class _Body(BaseModel):
        amount: Decimal

        @field_validator("amount")
        @classmethod
        def _reject(cls, v: Decimal) -> Decimal:
            # ctx 에 Decimal 이 들어가는 형태 — 직렬화 불가 값의 대표
            raise ValueError(f"허용되지 않는 값: {v}")

    router = APIRouter()

    @router.post("/__validate__")
    async def _validate(body: _Body) -> dict[str, str]:
        return {"ok": "yes"}

    app.include_router(router)
    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    try:
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as ac:
            resp = await ac.post("/__validate__", json={"amount": "1.5"})
    finally:
        app.router.routes = [
            r for r in app.router.routes if getattr(r, "path", None) != "/__validate__"
        ]

    assert resp.status_code == 422, f"500 으로 뒤집혔다: {resp.status_code} {resp.text[:200]}"
    assert "detail" in resp.json()
