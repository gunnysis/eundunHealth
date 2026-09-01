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


@pytest.mark.asyncio
async def test_request_id_with_newline_is_rejected(client):
    """개행이 든 값은 채택하지 않는다 — 로그 위조(CWE-117) 차단.

    이 값은 로그 포맷 `[%(request_id)s]` 에 그대로 들어가므로, 개행을 허용하면
    클라이언트가 로그에 **가짜 줄**을 삽입할 수 있다.
    """
    forged = "abc\nWARNING [x] app: fabricated log line"
    resp = await client.get("/health", headers={"X-Request-ID": forged})

    rid = resp.headers["X-Request-ID"]
    assert "\n" not in rid
    assert rid != forged


@pytest.mark.asyncio
async def test_overlong_request_id_is_rejected(client):
    """길이 상한이 없으면 로그 볼륨 증폭에 쓰일 수 있다."""
    resp = await client.get("/health", headers={"X-Request-ID": "a" * 4096})

    rid = resp.headers["X-Request-ID"]
    assert len(rid) <= 64
    assert rid != "a" * 4096


@pytest.mark.asyncio
async def test_request_id_allows_common_trace_id_shapes(client):
    """정상적인 추적 ID(영숫자·하이픈·언더스코어)는 그대로 통과해야 한다."""
    for rid in ("trace-abc-123", "0af7651916cd43dd8448eb211c80319c", "req_42"):
        resp = await client.get("/health", headers={"X-Request-ID": rid})
        assert resp.headers["X-Request-ID"] == rid
