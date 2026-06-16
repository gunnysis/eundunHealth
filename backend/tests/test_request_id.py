"""요청 상관관계용 X-Request-ID 미들웨어 테스트.

다중 replica(min/max 1/3) 환경에서 한 요청의 로그를 추적할 핸들을 제공한다.
"""
import pytest


@pytest.mark.asyncio
async def test_response_has_generated_request_id_header(client):
    """클라이언트가 X-Request-ID 를 보내지 않으면 서버가 생성해 응답 헤더로 돌려준다."""
    resp = await client.get("/health")
    rid = resp.headers.get("X-Request-ID")
    assert rid
    assert len(rid) >= 8


@pytest.mark.asyncio
async def test_request_id_is_echoed_when_provided(client):
    """클라이언트가 X-Request-ID 를 보내면 그대로 echo 한다(분산 추적 연계)."""
    resp = await client.get("/health", headers={"X-Request-ID": "trace-abc-123"})
    assert resp.headers.get("X-Request-ID") == "trace-abc-123"
