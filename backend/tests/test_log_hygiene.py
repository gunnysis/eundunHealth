"""검증 실패 로그에 사용자 입력값이 남지 않는지 고정한다.

건강 데이터를 다루는 앱이라 `PUT /profile` 의 422 는 키·몸무게·체지방·근육량을 동반한다.
Pydantic 공식 문서의 `ErrorDetails` 표에 따르면 `errors()` 의 각 항목은 `input`
("The input provided for validation")을 포함하므로, 그대로 로깅하면 값이 Log Analytics 로
흘러간다. 진단에 필요한 것은 **어느 필드가 어떤 규칙을 어겼는지**이지 값 자체가 아니다.

출처: https://pydantic.dev/docs/validation/latest/errors/errors/
"""
import logging

import pytest

SECRET_HEIGHT = "13579.2468"
SECRET_WEIGHT = "97531.8642"


@pytest.mark.asyncio
async def test_validation_log_omits_submitted_values(client, caplog):
    """422 로그에 제출한 값이 문자열로 남으면 안 된다."""
    with caplog.at_level(logging.WARNING, logger="app.main"):
        resp = await client.put(
            "/profile",
            json={"heightCm": SECRET_HEIGHT, "weightKg": SECRET_WEIGHT, "restDay": "월요일"},
        )
    assert resp.status_code == 422

    logged = "\n".join(r.getMessage() for r in caplog.records)
    assert SECRET_HEIGHT not in logged
    assert SECRET_WEIGHT not in logged
    assert "월요일" not in logged


@pytest.mark.asyncio
async def test_validation_log_keeps_field_and_rule(client, caplog):
    """값은 지우되 진단 정보(위치·규칙)는 남아야 한다 — 안 남기면 422 디버깅이 불가능해진다."""
    with caplog.at_level(logging.WARNING, logger="app.main"):
        resp = await client.put("/profile", json={"heightCm": SECRET_HEIGHT})
    assert resp.status_code == 422

    logged = "\n".join(r.getMessage() for r in caplog.records)
    assert "heightCm" in logged or "weightKg" in logged
    assert "path=/profile" in logged


@pytest.mark.asyncio
async def test_validation_response_body_is_unchanged(client):
    """응답 body 는 축소하지 않는다 — 받는 주체가 자기 자신이고, 바꾸면 API 호환성이 깨진다."""
    resp = await client.put("/profile", json={"heightCm": "abc"})
    assert resp.status_code == 422
    assert "detail" in resp.json()
