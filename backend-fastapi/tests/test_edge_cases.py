"""권장사항으로 추가된 엣지 케이스 테스트 (커버리지 + 회귀 안전망)."""
import pytest

from tests.conftest import set_test_user

# === PATCH /weekly-plan/complete (가장 큰 커버리지 갭) ===

@pytest.mark.asyncio
async def test_complete_exercise_toggles_flag(client, sample_plan):
    """완료 PATCH 후 GET이 갱신된 dayPlans를 반환해야 한다."""
    plan = {
        "weekStart": "2026-05-25",
        "dayPlans": '[{"day":1,"exercises":[{"id":"x","completed":false}]}]',
    }
    await client.post("/weekly-plan", json=plan)

    resp = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2026-05-25", "dayIndex": 0, "exerciseIndex": 0, "completed": True},
    )
    assert resp.status_code == 200

    resp = await client.get("/weekly-plan", params={"week_start": "2026-05-25"})
    assert '"completed": true' in resp.json()["dayPlans"]


@pytest.mark.asyncio
async def test_complete_when_plan_missing_returns_404(client):
    resp = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2099-01-01", "dayIndex": 0, "exerciseIndex": 0, "completed": True},
    )
    assert resp.status_code == 404


# === Supabase Admin API 실패 / 멱등 경로 ===

@pytest.mark.asyncio
async def test_delete_account_supabase_500_raises_502(client, sample_profile, supabase_delete_mock):
    """Supabase 5xx → AccountService가 AppException(502)로 변환 → DB는 보존."""
    await client.put("/profile", json=sample_profile)

    with supabase_delete_mock(status_code=500):
        resp = await client.delete("/account")
        assert resp.status_code == 502
        body = resp.json()
        assert body["code"] == "AUTH_DELETE_FAILED"

    # 실패 시 프로필이 보존되어야 안전 (재시도 가능)
    resp = await client.get("/profile")
    assert resp.status_code == 200


@pytest.mark.asyncio
async def test_delete_account_supabase_404_is_idempotent(client, sample_profile, supabase_delete_mock):
    """Supabase 404 (이미 삭제된 사용자) → 200 + DB 데이터도 정리되어야 한다."""
    await client.put("/profile", json=sample_profile)

    with supabase_delete_mock(status_code=404):
        resp = await client.delete("/account")
        assert resp.status_code == 200

    resp = await client.get("/profile")
    assert resp.status_code == 404


# === Pydantic validation (heightCm < 50) ===

@pytest.mark.asyncio
async def test_profile_validation_rejects_out_of_range(client):
    resp = await client.put("/profile", json={"heightCm": 30.0, "weightKg": 70.0})
    assert resp.status_code == 422


# === FastAPI Query 제약 (size > 50) ===

@pytest.mark.asyncio
async def test_history_size_above_50_rejected(client):
    """Query(le=50) → size=51은 API 레벨에서 422."""
    resp = await client.get("/weekly-plan/history", params={"size": 51})
    assert resp.status_code == 422


# === user isolation (서로 다른 사용자의 데이터 분리) ===

@pytest.mark.asyncio
async def test_user_data_is_isolated(client, sample_profile):
    """사용자 A의 프로필이 사용자 B의 GET에 노출되어선 안 된다."""
    # A로 프로필 생성
    set_test_user("user-a")
    resp = await client.put("/profile", json=sample_profile)
    assert resp.status_code == 200
    assert (await client.get("/profile")).status_code == 200

    # B로 컨텍스트 전환 → 자신의 프로필은 없어야 함
    set_test_user("user-b")
    resp = await client.get("/profile")
    assert resp.status_code == 404

    # A로 돌아오면 여전히 있어야 함
    set_test_user("user-a")
    assert (await client.get("/profile")).status_code == 200


# === JWT 미존재 (HTTPBearer가 403 반환) ===

@pytest.mark.asyncio
async def test_missing_authorization_header_is_rejected(client_no_auth):
    """Authorization 헤더 없이 보호 엔드포인트 호출 시 401/403으로 거부.

    starlette 0.49+에서 HTTPBearer의 기본 거부 코드가 403 → 401로 바뀌었다(RFC 7235).
    """
    resp = await client_no_auth.get("/profile")
    assert resp.status_code in (401, 403)
