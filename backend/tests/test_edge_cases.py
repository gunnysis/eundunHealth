"""권장사항으로 추가된 엣지 케이스 테스트 (커버리지 + 회귀 안전망)."""
import json

import pytest

from tests.conftest import set_test_user

_ONE_DAY = '[{"isRestDay": false, "isCompleted": false, "exercises": []}]'


# === 완료 토글 "수동 우선" (manualOverride) ===

@pytest.mark.asyncio
async def test_manual_toggle_sets_manually_set_flag(client):
    """사용자 수동 토글(manual 기본 True)은 manuallySet=True 를 남겨 HC 자동완료가 못 덮게 한다."""
    await client.post("/weekly-plan", json={"weekStart": "2026-05-25", "dayPlans": _ONE_DAY})

    resp = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2026-05-25", "date": "2026-05-25", "completed": True},
    )
    assert resp.status_code == 200

    days = json.loads((await client.get("/weekly-plan", params={"weekStart": "2026-05-25"})).json()["dayPlans"])
    assert days[0]["manuallySet"] is True


@pytest.mark.asyncio
async def test_auto_complete_does_not_set_manually_set_flag(client):
    """HC 자동완료(manual=False)는 manuallySet 을 남기지 않는다 — 이후 수동 해제가 가능해야 함."""
    await client.post("/weekly-plan", json={"weekStart": "2026-05-25", "dayPlans": _ONE_DAY})

    resp = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2026-05-25", "date": "2026-05-25", "completed": True, "manual": False},
    )
    assert resp.status_code == 200

    days = json.loads((await client.get("/weekly-plan", params={"weekStart": "2026-05-25"})).json()["dayPlans"])
    assert days[0].get("manuallySet") is not True

# === 입력 검증: 잘못된 클라이언트 입력은 500 이 아니라 400 ===

@pytest.mark.asyncio
async def test_complete_day_offset_beyond_stored_days_returns_400(client):
    """저장된 day_plans 길이를 넘는 day 를 PATCH → IndexError(500) 아닌 400."""
    await client.post(
        "/weekly-plan",
        json={
            "weekStart": "2026-05-25",
            "dayPlans": '[{"isRestDay": false, "isCompleted": false, "exercises": []}]',
        },
    )
    resp = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2026-05-25", "date": "2026-05-29", "completed": True},
    )
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_complete_date_outside_7day_window_returns_400(client):
    """weekStart 기준 7일 범위를 벗어난 date(이전/이후 모두) → 500 아닌 400.

    test_complete_day_offset_beyond_stored_days(범위 내·저장일 초과)와 다른 분기 —
    여기선 day_offset 자체가 [0,7) 밖이라 weekly_plan_service 의 범위 가드(0<=offset<7)에 걸린다.
    """
    await client.post("/weekly-plan", json={"weekStart": "2026-05-25", "dayPlans": _ONE_DAY})

    # 이후로 7일 초과 (offset 16)
    after = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2026-05-25", "date": "2026-06-10", "completed": True},
    )
    assert after.status_code == 400

    # weekStart 이전 (offset 음수)
    before = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2026-05-25", "date": "2026-05-24", "completed": True},
    )
    assert before.status_code == 400


@pytest.mark.asyncio
async def test_complete_bad_date_returns_400(client):
    resp = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2026-05-25", "date": "not-a-date", "completed": True},
    )
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_get_plan_bad_weekstart_returns_400(client):
    resp = await client.get("/weekly-plan", params={"weekStart": "2026-13-99"})
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_create_plan_invalid_dayplans_returns_400(client):
    resp = await client.post("/weekly-plan", json={"weekStart": "2026-05-25", "dayPlans": "not json"})
    assert resp.status_code == 400

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
        json={"weekStart": "2026-05-25", "date": "2026-05-25", "completed": True},
    )
    assert resp.status_code == 200

    resp = await client.get("/weekly-plan", params={"weekStart": "2026-05-25"})
    assert '"completed": true' in resp.json()["dayPlans"]


@pytest.mark.asyncio
async def test_complete_when_plan_missing_returns_404(client):
    resp = await client.patch(
        "/weekly-plan/complete",
        json={"weekStart": "2099-01-01", "date": "2099-01-01", "completed": True},
    )
    assert resp.status_code == 404


# === Supabase Admin API 실패 / 멱등 경로 ===

@pytest.mark.asyncio
async def test_delete_account_graph_500_raises_502(client, sample_profile, entra_delete_mock):
    """Supabase 5xx → AccountService가 AppException(502)로 변환 → DB는 보존."""
    await client.put("/profile", json=sample_profile)

    with entra_delete_mock(status_code=500):
        resp = await client.delete("/account")
        assert resp.status_code == 502
        body = resp.json()
        assert body["code"] == "AUTH_DELETE_FAILED"

    # 실패 시 프로필이 보존되어야 안전 (재시도 가능)
    resp = await client.get("/profile")
    assert resp.status_code == 200


@pytest.mark.asyncio
async def test_delete_account_graph_404_is_idempotent(client, sample_profile, entra_delete_mock):
    """Supabase 404 (이미 삭제된 사용자) → 200 + DB 데이터도 정리되어야 한다."""
    await client.put("/profile", json=sample_profile)

    with entra_delete_mock(status_code=404):
        resp = await client.delete("/account")
        assert resp.status_code == 200

    resp = await client.get("/profile")
    assert resp.status_code == 404


# === Pydantic validation — 프로필 경계값 ===

@pytest.mark.asyncio
async def test_profile_validation_rejects_out_of_range(client):
    """heightCm < 50 → 422 (기존 회귀가드)."""
    resp = await client.put("/profile", json={"heightCm": 30.0, "weightKg": 70.0})
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_profile_weight_above_maximum_returns_422(client):
    """weightKg > 500 → 422 (Field le=500)."""
    resp = await client.put("/profile", json={"heightCm": 170.0, "weightKg": 999.0})
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_profile_height_above_maximum_returns_422(client):
    """heightCm > 300 → 422 (Field le=300)."""
    resp = await client.put("/profile", json={"heightCm": 400.0, "weightKg": 65.0})
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


# === CORS — 임의 origin 을 허용하지 않음 (네이티브 앱은 CORS 비적용) ===

@pytest.mark.asyncio
async def test_cors_does_not_allow_arbitrary_origin(client):
    """CORS_ORIGINS 기본 차단([]) → 임의 웹 origin 에 Access-Control-Allow-Origin 을 echo 하지 않는다.

    탈취 토큰의 임의 웹사이트 JS 악용 표면 축소. 회귀가드: 다시 와일드카드(["*"])로 풀리면 실패.
    """
    resp = await client.get("/health", headers={"Origin": "https://evil.example"})
    acao = resp.headers.get("access-control-allow-origin")
    assert acao != "*"
    assert acao != "https://evil.example"
