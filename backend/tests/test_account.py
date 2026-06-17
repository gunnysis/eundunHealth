import pytest
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.database import Base


@pytest.mark.asyncio
async def test_delete_account(client, sample_profile, supabase_delete_mock):
    # 프로필 생성 → 존재 확인
    await client.put("/profile", json=sample_profile)
    assert (await client.get("/profile")).status_code == 200

    # Supabase Admin API 호출을 mock → 계정 삭제
    with supabase_delete_mock(status_code=200):
        resp = await client.delete("/account")
        assert resp.status_code == 200

    # 프로필이 삭제되었는지 확인
    resp = await client.get("/profile")
    assert resp.status_code == 404


def _user_id_models() -> list:
    """user_id 컬럼을 가진 전체 SQLAlchemy 모델을 동적으로 수집한다.

    계정 삭제 완전성의 '정답 집합'. 향후 user_id 를 가진 새 모델이 추가되면 자동 포함되어,
    account_service 가 해당 테이블을 지우지 않으면 아래 회귀 가드 테스트가 실패한다.
    """
    return [m.class_ for m in Base.registry.mappers if "user_id" in m.columns.keys()]


@pytest.mark.asyncio
async def test_delete_account_purges_all_user_data(
    client, db_engine, sample_profile, sample_plan, supabase_delete_mock
):
    """계정 삭제는 user_id 를 가진 *모든* 테이블에서 사용자 데이터를 제거해야 한다 (회귀 가드).

    과거 goals(v0.3)·user_profile_history(v0.3) 가 account_service 누락으로 계정 삭제 후에도
    영구 잔존했다(민감 건강 이력 미삭제). 이 테스트는 per-user 테이블 전체를 seed → 삭제 →
    0건 검증해 재발을 차단한다.
    """
    user_id = "test-user-id"  # conftest override_get_current_user_id 와 일치

    # 모든 per-user 테이블에 1행 이상 seed (실제 API 경로로)
    await client.put("/profile", json=sample_profile)  # user_profiles + user_profile_history
    await client.post("/weekly-plan", json=sample_plan)  # weekly_plans
    await client.post("/badges/first_workout")  # badges
    await client.put("/goals", json={"goalType": "weight", "targetValue": 65.0})  # goals

    models = _user_id_models()
    assert len(models) >= 5, f"per-user 모델 수 예상 미달: {[m.__tablename__ for m in models]}"

    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async def count(model: type) -> int:
        async with session_factory() as session:
            return await session.scalar(
                select(func.count()).select_from(model).where(model.user_id == user_id)
            )

    # seed 가 실제로 들어갔는지 먼저 확인 — false-green(0→0 통과) 방지
    for model in models:
        assert await count(model) > 0, f"seed 누락: {model.__tablename__}"

    with supabase_delete_mock(status_code=200):
        assert (await client.delete("/account")).status_code == 200

    # 삭제 후 전 per-user 테이블 0건
    for model in models:
        assert await count(model) == 0, f"계정 삭제 후 사용자 데이터 잔존: {model.__tablename__}"


@pytest.mark.asyncio
async def test_reap_orphaned_data_purges_only_auth_missing_users(db_engine):
    """reap_orphaned_data 는 Supabase Auth 에 없는(404) user 의 데이터만 purge, 존재(200)는 보존.

    fail-safe 가드: orphan-user(404)만 청소되고 valid-user(200)는 그대로 남아야 한다.
    """
    from unittest.mock import AsyncMock, patch

    import httpx

    from app.config import Settings
    from app.repositories.profile_repo import ProfileRepository
    from app.services.account_service import AccountService

    settings = Settings(
        database_url="sqlite+aiosqlite:///:memory:",
        supabase_url="https://test.supabase.co",
        supabase_service_role_key="k",
    )
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async with session_factory() as session:
        repo = ProfileRepository(session)
        await repo.upsert("orphan-user", {"height_cm": 175.0, "weight_kg": 70.0})
        await repo.upsert("valid-user", {"height_cm": 180.0, "weight_kg": 80.0})
        await session.commit()

    def fake_get(url: str, **_: object) -> httpx.Response:
        status = 404 if "orphan-user" in url else 200
        return httpx.Response(status, request=httpx.Request("GET", url))

    async with session_factory() as session:
        service = AccountService(session, settings)
        with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
            instance = AsyncMock()
            instance.get = AsyncMock(side_effect=fake_get)
            instance.__aenter__ = AsyncMock(return_value=instance)
            instance.__aexit__ = AsyncMock(return_value=False)
            mock_cls.return_value = instance
            reaped = await service.reap_orphaned_data()
        await session.commit()

    assert reaped == ["orphan-user"]

    async with session_factory() as session:
        repo = ProfileRepository(session)
        assert await repo.get_by_user_id("orphan-user") is None  # 고아 → purge
        assert await repo.get_by_user_id("valid-user") is not None  # 존재 → 보존
