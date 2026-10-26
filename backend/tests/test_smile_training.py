from __future__ import annotations

import datetime
import uuid

import pytest
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.config import Settings
from app.models.smile_training import SmileTrainingSession
from app.services.account_service import AccountService
from app.services.smile_training_service import SmileTrainingService
from tests.conftest import graph_token_response, set_test_user


def _now_iso(offset_minutes: int = 0) -> str:
    dt = datetime.datetime.now(datetime.UTC) + datetime.timedelta(minutes=offset_minutes)
    return dt.isoformat()


def _sample_payload(
    session_id: str | None = None,
    routine: str = "GENTLE",
    difficulty: str = "STANDARD",
    duration_sec: int = 12,
    completed_reps: int = 3,
    reflection_emoji: int | None = 4,
    occurred_at: str | None = None,
) -> dict[str, object]:
    return {
        "id": session_id or str(uuid.uuid4()),
        "routine": routine,
        "difficulty": difficulty,
        "durationSec": duration_sec,
        "completedReps": completed_reps,
        "reflectionEmoji": reflection_emoji,
        "occurredAt": occurred_at or _now_iso(),
    }


@pytest.mark.asyncio
async def test_record_smile_session_creates_and_is_idempotent(client):
    payload = _sample_payload(session_id="22222222-2222-4222-8222-222222222222")

    resp1 = await client.post("/smile-training/sessions", json=payload)
    assert resp1.status_code == 201
    body1 = resp1.json()
    assert body1["id"] == payload["id"]
    assert body1["routine"] == "GENTLE"
    assert body1["difficulty"] == "STANDARD"
    assert body1["durationSec"] == 12
    assert body1["completedReps"] == 3
    assert body1["reflectionEmoji"] == 4

    # 동일 user + 동일 UUID + 동일 payload 재전송 → 200 OK (멱등)
    resp2 = await client.post("/smile-training/sessions", json=payload)
    assert resp2.status_code == 200
    assert resp2.json()["id"] == body1["id"]

    # 목록에 중복 생성되지 않고 1건만 존재하는지 확인
    list_resp = await client.get("/smile-training/sessions")
    assert list_resp.status_code == 200
    assert len(list_resp.json()["items"]) == 1


@pytest.mark.asyncio
async def test_record_smile_session_conflict_on_different_payload_or_user(client):
    sid = "a3333333-b333-4333-8333-c33333333333"
    base = _sample_payload(session_id=sid, completed_reps=2)
    assert (await client.post("/smile-training/sessions", json=base)).status_code == 201

    # 동일 user가 같은 ID에 다른 payload 전송 → 409 Conflict
    mutated = dict(base, completedReps=4)
    conflict_resp = await client.post("/smile-training/sessions", json=mutated)
    assert conflict_resp.status_code == 409
    assert conflict_resp.json()["code"] == "CONFLICT"

    # 다른 user가 같은 ID로 전송 → 409 Conflict (500 노출 금지)
    set_test_user("other-user-id")
    other_user_resp = await client.post("/smile-training/sessions", json=base)
    assert other_user_resp.status_code == 409


@pytest.mark.asyncio
async def test_record_smile_session_validation_guards(client):
    # 1) timezone 없는 occurredAt 거부 (422)
    naive_payload = _sample_payload(occurred_at="2026-10-26T12:00:00")
    assert (await client.post("/smile-training/sessions", json=naive_payload)).status_code == 422

    # 2) 미래 10분 시각 거부 (422)
    future_payload = _sample_payload(occurred_at=_now_iso(offset_minutes=10))
    assert (await client.post("/smile-training/sessions", json=future_payload)).status_code == 422

    # 3) 과거 100일 전 시각 거부 (422)
    old_dt = (datetime.datetime.now(datetime.UTC) - datetime.timedelta(days=100)).isoformat()
    old_payload = _sample_payload(occurred_at=old_dt)
    assert (await client.post("/smile-training/sessions", json=old_payload)).status_code == 422

    # 4) completedReps = 0 거부 (422)
    zero_rep_payload = _sample_payload(completed_reps=0)
    assert (await client.post("/smile-training/sessions", json=zero_rep_payload)).status_code == 422

    # 5) reflectionEmoji 범위 밖(6) 거부 (422)
    bad_emoji_payload = _sample_payload(reflection_emoji=6)
    assert (await client.post("/smile-training/sessions", json=bad_emoji_payload)).status_code == 422


@pytest.mark.asyncio
async def test_list_smile_sessions_cursor_pagination_and_isolation(client):
    base_time = datetime.datetime.now(datetime.UTC) - datetime.timedelta(hours=1)
    ids = [
        "a0000000-b000-4000-8000-c00000000001",
        "a0000000-b000-4000-8000-c00000000002",
        "a0000000-b000-4000-8000-c00000000003",
    ]
    # 동일한 occurred_at을 가진 2건 + 더 최신 1건을 넣어 tie-breaker(id DESC)까지 검증
    await client.post(
        "/smile-training/sessions",
        json=_sample_payload(session_id=ids[0], occurred_at=base_time.isoformat()),
    )
    await client.post(
        "/smile-training/sessions",
        json=_sample_payload(session_id=ids[1], occurred_at=base_time.isoformat()),
    )
    await client.post(
        "/smile-training/sessions",
        json=_sample_payload(
            session_id=ids[2],
            occurred_at=(base_time + datetime.timedelta(minutes=5)).isoformat(),
        ),
    )

    # 타 사용자 세션 추가 (격리 검증)
    set_test_user("other-user")
    await client.post("/smile-training/sessions", json=_sample_payload())
    set_test_user("test-user-id")

    page1 = await client.get("/smile-training/sessions?limit=2")
    assert page1.status_code == 200
    data1 = page1.json()
    assert [item["id"] for item in data1["items"]] == [ids[2], ids[1]]
    assert data1["nextCursor"] is not None

    page2 = await client.get(f"/smile-training/sessions?limit=2&cursor={data1['nextCursor']}")
    assert page2.status_code == 200
    data2 = page2.json()
    assert [item["id"] for item in data2["items"]] == [ids[0]]
    assert data2["nextCursor"] is None

    # 잘못된 cursor 요청 시 400
    bad_cursor = await client.get("/smile-training/sessions?cursor=not-a-valid-cursor")
    assert bad_cursor.status_code == 400


@pytest.mark.asyncio
async def test_smile_statistics_kst_boundary_and_streak(db_engine):
    """UTC 시각이 달라도 KST(UTC+9) 날짜 기준으로 연속일과 당일 완료를 정확히 판정한다."""
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)
    user_id = "kst-user"

    # KST 2026-10-24 23:30 (= UTC 2026-10-24 14:30)
    # KST 2026-10-25 00:30 (= UTC 2026-10-24 15:30) -> UTC로는 같은 날(10/24)이지만 KST로는 이틀 연속!
    # KST 2026-10-26 08:00 (= UTC 2026-10-25 23:00) -> 3일 연속 달성
    kst_moments_utc = [
        datetime.datetime(2026, 10, 24, 14, 30, tzinfo=datetime.UTC),
        datetime.datetime(2026, 10, 24, 15, 30, tzinfo=datetime.UTC),
        datetime.datetime(2026, 10, 25, 23, 0, tzinfo=datetime.UTC),
    ]

    async with session_factory() as session:
        for idx, ts in enumerate(kst_moments_utc, start=1):
            session.add(
                SmileTrainingSession(
                    id=uuid.uuid4(),
                    user_id=user_id,
                    routine="GENTLE",
                    difficulty="STANDARD",
                    duration_sec=10 * idx,
                    completed_reps=2,
                    reflection_emoji=5,
                    occurred_at=ts,
                )
            )
        await session.commit()

    async with session_factory() as session:
        service = SmileTrainingService(session)
        # 기준 시각: KST 2026-10-26 12:00 (UTC 2026-10-26 03:00)
        stats_today = await service.get_statistics(
            user_id=user_id,
            now_utc=datetime.datetime(2026, 10, 26, 3, 0, tzinfo=datetime.UTC),
        )
        assert stats_today.total_sessions == 3
        assert stats_today.total_duration_sec == 60
        assert stats_today.completed_today is True
        assert stats_today.current_streak_days == 3
        assert stats_today.longest_streak_days == 3

        # 다음날(KST 2026-10-27) 수행 전이어도 어제(10/26) 완료했으므로 current_streak=3 유지, completed_today=False
        stats_next_day = await service.get_statistics(
            user_id=user_id,
            now_utc=datetime.datetime(2026, 10, 27, 1, 0, tzinfo=datetime.UTC),
        )
        assert stats_next_day.completed_today is False
        assert stats_next_day.current_streak_days == 3

        # 이틀 뒤(KST 2026-10-28)에는 current_streak=0 으로 초기화, longest_streak=3 유지
        stats_broken = await service.get_statistics(
            user_id=user_id,
            now_utc=datetime.datetime(2026, 10, 28, 1, 0, tzinfo=datetime.UTC),
        )
        assert stats_broken.current_streak_days == 0
        assert stats_broken.longest_streak_days == 3


@pytest.mark.asyncio
async def test_delete_all_smile_sessions_isolates_user(client):
    await client.post("/smile-training/sessions", json=_sample_payload())
    await client.post("/smile-training/sessions", json=_sample_payload())

    set_test_user("other-user")
    await client.post("/smile-training/sessions", json=_sample_payload())
    set_test_user("test-user-id")

    del_resp = await client.delete("/smile-training/sessions")
    assert del_resp.status_code == 200
    assert del_resp.json()["deletedCount"] == 2

    # 내 기록은 0건
    my_stats = await client.get("/smile-training/statistics")
    assert my_stats.json()["totalSessions"] == 0

    # 다른 사용자 기록은 그대로 보존
    set_test_user("other-user")
    other_stats = await client.get("/smile-training/statistics")
    assert other_stats.json()["totalSessions"] == 1


@pytest.mark.asyncio
async def test_reaper_discovers_session_only_orphan_user(db_engine):
    """프로필 없이 smile_training_sessions 에만 행이 남은 고아 user_id 도 reaper 후보에 포함돼 정리된다."""
    from unittest.mock import AsyncMock, patch

    import httpx

    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)
    async with session_factory() as session:
        session.add(
            SmileTrainingSession(
                id=uuid.uuid4(),
                user_id="smile-only-orphan",
                routine="GENTLE",
                difficulty="STANDARD",
                duration_sec=15,
                completed_reps=1,
                reflection_emoji=None,
                occurred_at=datetime.datetime.now(datetime.UTC),
            )
        )
        await session.commit()

    settings = Settings(database_url="sqlite+aiosqlite:///:memory:")
    async with session_factory() as session:
        service = AccountService(session, settings)
        with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
            instance = AsyncMock()
            instance.get = AsyncMock(
                side_effect=lambda url, **_: httpx.Response(404, request=httpx.Request("GET", url))
            )
            instance.post = AsyncMock(return_value=graph_token_response())
            instance.__aenter__ = AsyncMock(return_value=instance)
            instance.__aexit__ = AsyncMock(return_value=False)
            mock_cls.return_value = instance
            reaped = await service.reap_orphaned_data()

    assert "smile-only-orphan" in reaped
