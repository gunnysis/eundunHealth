import pytest


@pytest.mark.asyncio
async def test_create_and_get_plan(client, sample_plan):
    resp = await client.post("/weekly-plan", json=sample_plan)
    assert resp.status_code == 200

    resp = await client.get("/weekly-plan", params={"weekStart": sample_plan["weekStart"]})
    assert resp.status_code == 200
    assert resp.json()["weekStart"] == sample_plan["weekStart"]


@pytest.mark.asyncio
async def test_get_plan_not_found(client):
    resp = await client.get("/weekly-plan", params={"weekStart": "2099-01-01"})
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_history_empty(client):
    resp = await client.get("/weekly-plan/history")
    assert resp.status_code == 200
    # envelope 응답 — Android HistoryViewModel이 totalCount를 페이지 인디케이터로 사용한다
    assert resp.json() == {"plans": [], "totalCount": 0, "page": 0, "size": 10}


@pytest.mark.asyncio
async def test_history_with_data(client, sample_plan):
    await client.post("/weekly-plan", json=sample_plan)
    await client.post(
        "/weekly-plan",
        json={"weekStart": "2026-06-01", "dayPlans": sample_plan["dayPlans"]},
    )

    resp = await client.get("/weekly-plan/history")
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["plans"]) == 2
    assert body["totalCount"] == 2
    assert body["page"] == 0
    assert body["size"] == 10


@pytest.mark.asyncio
async def test_history_second_page_boundary(client, sample_plan):
    """12개 생성 → page0=10, page1=2, totalCount=12 (페이지네이션 경계 + COUNT 정확성).

    window function 으로 COUNT 를 접은 뒤에도 모든 페이지에서 totalCount 가 동일하게
    유지되는지 가드한다(Android HistoryViewModel 의 hasMore 계산 입력).
    """
    import datetime

    base = datetime.date(2026, 1, 5)  # 월요일
    for i in range(12):
        week_start = (base + datetime.timedelta(weeks=i)).isoformat()
        await client.post(
            "/weekly-plan",
            json={"weekStart": week_start, "dayPlans": sample_plan["dayPlans"]},
        )

    page0 = await client.get("/weekly-plan/history", params={"page": 0, "size": 10})
    page1 = await client.get("/weekly-plan/history", params={"page": 1, "size": 10})

    assert len(page0.json()["plans"]) == 10
    assert page0.json()["totalCount"] == 12

    body1 = page1.json()
    assert len(body1["plans"]) == 2
    assert body1["totalCount"] == 12
    assert body1["page"] == 1


@pytest.mark.asyncio
async def test_creating_plan_for_same_week_overwrites(client):
    """같은 주에 다시 생성하면 덮어쓴다(upsert).

    앱의 자가치유가 이 동작에 의존한다 — 운동 0개짜리 '껍데기' 계획이 저장됐을 때
    GetOrCreateWeeklyPlanUseCase 가 재생성으로 덮어써 그 주 내내 고착되는 것을 막는다
    (룰 12 / 2026-06-15 빈 운동계획 회귀). 새 행이 쌓이면 자가치유가 무력화된다.
    """
    first = await client.post(
        "/weekly-plan", json={"weekStart": "2026-05-04", "dayPlans": '[{"marker": "old"}]'}
    )
    assert first.status_code in (200, 201)

    second = await client.post(
        "/weekly-plan", json={"weekStart": "2026-05-04", "dayPlans": '[{"marker": "new"}]'}
    )
    assert second.status_code in (200, 201)
    assert second.json()["id"] == first.json()["id"]  # 새 행이 아니라 같은 행

    got = await client.get("/weekly-plan", params={"weekStart": "2026-05-04"})
    assert "new" in got.json()["dayPlans"]
