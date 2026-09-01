import pytest
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.database import Base
from tests.conftest import graph_token_response


@pytest.mark.asyncio
async def test_delete_account(client, sample_profile, entra_delete_mock):
    # 프로필 생성 → 존재 확인
    await client.put("/profile", json=sample_profile)
    assert (await client.get("/profile")).status_code == 200

    # Graph 호출을 mock → 계정 삭제
    with entra_delete_mock(status_code=204):
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
    client, db_engine, sample_profile, sample_plan, entra_delete_mock
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

    with entra_delete_mock(status_code=204):
        assert (await client.delete("/account")).status_code == 200

    # 삭제 후 전 per-user 테이블 0건
    for model in models:
        assert await count(model) == 0, f"계정 삭제 후 사용자 데이터 잔존: {model.__tablename__}"


@pytest.mark.asyncio
async def test_reap_orphaned_data_purges_only_auth_missing_users(db_engine):
    """reap_orphaned_data 는 Entra 에 없는(404) user 의 데이터만 purge, 존재(200)는 보존.

    fail-safe 가드: orphan-user(404)만 청소되고 valid-user(200)는 그대로 남아야 한다.
    """
    from unittest.mock import AsyncMock, patch

    import httpx

    from app.config import Settings
    from app.repositories.profile_repo import ProfileRepository
    from app.services.account_service import AccountService

    settings = Settings(
        database_url="sqlite+aiosqlite:///:memory:",
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
            instance.post = AsyncMock(return_value=graph_token_response())
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


@pytest.mark.asyncio
async def test_reap_orphaned_data_preserves_on_uncertain_auth(db_engine):
    """fail-safe: Auth 존재 확인이 '확정 부재(404)' 가 아니면 절대 purge 하지 않는다.

    네트워크오류(httpx.HTTPError)·비정상응답(500) 둘 다 None(불확실)으로 처리되어
    데이터가 보존돼야 한다. 잘못된 삭제(불확실한데 지움) 회귀를 차단하는 안전 가드.
    """
    from unittest.mock import AsyncMock, patch

    import httpx

    from app.config import Settings
    from app.repositories.profile_repo import ProfileRepository
    from app.services.account_service import AccountService

    settings = Settings(
        database_url="sqlite+aiosqlite:///:memory:",
    )
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async with session_factory() as session:
        repo = ProfileRepository(session)
        await repo.upsert("neterr-user", {"height_cm": 175.0, "weight_kg": 70.0})
        await repo.upsert("status500-user", {"height_cm": 180.0, "weight_kg": 80.0})
        await session.commit()

    def fake_get(url: str, **_: object) -> httpx.Response:
        if "neterr-user" in url:
            raise httpx.ConnectError("boom")  # httpx.HTTPError → None(보존)
        return httpx.Response(500, request=httpx.Request("GET", url))  # 비정상 → None(보존)

    async with session_factory() as session:
        service = AccountService(session, settings)
        with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
            instance = AsyncMock()
            instance.get = AsyncMock(side_effect=fake_get)
            instance.post = AsyncMock(return_value=graph_token_response())
            instance.__aenter__ = AsyncMock(return_value=instance)
            instance.__aexit__ = AsyncMock(return_value=False)
            mock_cls.return_value = instance
            reaped = await service.reap_orphaned_data()
        await session.commit()

    assert reaped == []  # 불확실 → 아무도 purge 안 됨

    async with session_factory() as session:
        repo = ProfileRepository(session)
        assert await repo.get_by_user_id("neterr-user") is not None  # 네트워크오류 → 보존
        assert await repo.get_by_user_id("status500-user") is not None  # 비정상 응답 → 보존


@pytest.mark.asyncio
async def test_reap_orphaned_data_isolates_per_user_failure(db_engine):
    """한 orphan 의 purge 실패가 다른 orphan 청소를 막지 않는다(사용자 단위 트랜잭션).

    orphan-a 의 purge 가 실패해도 orphan-b 는 정상 purge·commit 되고, 실패한 a 는 롤백·보존.
    """
    from unittest.mock import AsyncMock, patch

    import httpx

    from app.config import Settings
    from app.repositories.profile_repo import ProfileRepository
    from app.services.account_service import AccountService

    settings = Settings(
        database_url="sqlite+aiosqlite:///:memory:",
    )
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async with session_factory() as session:
        repo = ProfileRepository(session)
        await repo.upsert("orphan-a", {"height_cm": 170.0, "weight_kg": 60.0})
        await repo.upsert("orphan-b", {"height_cm": 180.0, "weight_kg": 80.0})
        await session.commit()

    def fake_get(url: str, **_: object) -> httpx.Response:
        return httpx.Response(404, request=httpx.Request("GET", url))  # 둘 다 orphan(404)

    async with session_factory() as session:
        service = AccountService(session, settings)
        real_purge = service._purge_app_data

        async def flaky_purge(uid: str) -> None:
            if uid == "orphan-a":
                raise RuntimeError("purge boom")
            await real_purge(uid)

        with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
            instance = AsyncMock()
            instance.get = AsyncMock(side_effect=fake_get)
            instance.post = AsyncMock(return_value=graph_token_response())
            instance.__aenter__ = AsyncMock(return_value=instance)
            instance.__aexit__ = AsyncMock(return_value=False)
            mock_cls.return_value = instance
            with patch.object(service, "_purge_app_data", new=flaky_purge):
                reaped = await service.reap_orphaned_data()

    assert reaped == ["orphan-b"]  # a 실패해도 sweep 계속 → b 청소

    async with session_factory() as session:
        repo = ProfileRepository(session)
        assert await repo.get_by_user_id("orphan-a") is not None  # 실패 → 롤백·보존
        assert await repo.get_by_user_id("orphan-b") is None      # 성공 → purge·commit


def test_reaper_script_imports_resolve_when_run_directly():
    """`python scripts/reap_orphaned_accounts.py` 직접 실행이 import 단계에서 깨지지 않는다.

    self-locating(sys.path) 회귀 가드 — 과거 sys.path[0]=scripts/ 로 `import app` 가
    ModuleNotFoundError 나던 footgun 의 재발 방지. (Settings/DB 단계에서 실패하는 건 무관 —
    import 가 resolve 되는지만 본다.)
    """
    import pathlib
    import subprocess
    import sys

    backend_root = pathlib.Path(__file__).resolve().parent.parent
    result = subprocess.run(
        [sys.executable, "scripts/reap_orphaned_accounts.py"],
        cwd=backend_root,
        capture_output=True,
        text=True,
        timeout=60,
    )
    assert "No module named 'app'" not in result.stderr
    assert "ModuleNotFoundError" not in result.stderr


@pytest.mark.asyncio
async def test_delete_account_purges_soft_deleted_user(client, sample_profile):
    """Graph 사용자 삭제는 30일 소프트 삭제다 — deletedItems 파기까지 해야 방침과 일치한다.

    게시된 방침(docs/store/account-deletion.md)이 "즉시 영구 삭제" 를 약속하므로
    이 두 번째 호출이 빠지면 문구와 실제 동작이 어긋난다.
    """
    from unittest.mock import AsyncMock, patch

    import httpx

    await client.put("/profile", json=sample_profile)
    called: list[str] = []

    def fake_delete(url: str, **_: object) -> httpx.Response:
        called.append(url)
        return httpx.Response(204, request=httpx.Request("DELETE", url))

    with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
        instance = AsyncMock()
        instance.post = AsyncMock(return_value=graph_token_response())
        instance.delete = AsyncMock(side_effect=fake_delete)
        instance.__aenter__ = AsyncMock(return_value=instance)
        instance.__aexit__ = AsyncMock(return_value=False)
        mock_cls.return_value = instance
        assert (await client.delete("/account")).status_code == 200

    assert any("/users/" in u for u in called), called
    assert any("/directory/deletedItems/" in u for u in called), called


@pytest.mark.asyncio
async def test_delete_account_succeeds_even_if_purge_forbidden(client, sample_profile):
    """deletedItems 파기가 403(권한 누락)이어도 요청은 성공해야 한다.

    사용자 계정 자체는 이미 삭제됐으므로 실패시키면 사용자만 혼란스럽다. 30일 후
    자동 파기되며, 로그가 유일한 탐지 수단이다(User.DeleteRestore.All 권한 필요).
    """
    from unittest.mock import AsyncMock, patch

    import httpx

    await client.put("/profile", json=sample_profile)

    def fake_delete(url: str, **_: object) -> httpx.Response:
        status = 403 if "deletedItems" in url else 204
        return httpx.Response(status, request=httpx.Request("DELETE", url))

    with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
        instance = AsyncMock()
        instance.post = AsyncMock(return_value=graph_token_response())
        instance.delete = AsyncMock(side_effect=fake_delete)
        instance.__aenter__ = AsyncMock(return_value=instance)
        instance.__aexit__ = AsyncMock(return_value=False)
        mock_cls.return_value = instance
        assert (await client.delete("/account")).status_code == 200

    # 앱 데이터는 정상 삭제됐어야 한다
    assert (await client.get("/profile")).status_code == 404


@pytest.mark.asyncio
async def test_graph_token_is_reused_across_reaper_sweep(db_engine):
    """reaper 가 사용자마다 토큰을 재발급하지 않는다(인스턴스 수명 캐시)."""
    from unittest.mock import AsyncMock, patch

    import httpx

    from app.config import Settings
    from app.repositories.profile_repo import ProfileRepository
    from app.services.account_service import AccountService

    settings = Settings(database_url="sqlite+aiosqlite:///:memory:")
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async with session_factory() as session:
        repo = ProfileRepository(session)
        for uid in ("u1", "u2", "u3"):
            await repo.upsert(uid, {"height_cm": 175.0, "weight_kg": 70.0})
        await session.commit()

    def fake_get(url: str, **_: object) -> httpx.Response:
        return httpx.Response(200, request=httpx.Request("GET", url))  # 전원 존재 → purge 없음

    async with session_factory() as session:
        service = AccountService(session, settings)
        with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
            instance = AsyncMock()
            instance.get = AsyncMock(side_effect=fake_get)
            instance.post = AsyncMock(return_value=graph_token_response())
            instance.__aenter__ = AsyncMock(return_value=instance)
            instance.__aexit__ = AsyncMock(return_value=False)
            mock_cls.return_value = instance
            await service.reap_orphaned_data()

            assert instance.get.await_count == 3
            assert instance.post.await_count == 1  # 토큰은 1회만
            # 커넥션도 1회만 — 사용자마다 AsyncClient 를 새로 열면 TLS 핸드셰이크가
            # 사용자 수에 비례해 늘어난다(httpx 공식 권고: Client 재사용).
            assert mock_cls.call_count == 1


@pytest.mark.asyncio
async def test_delete_account_reuses_one_graph_connection(client, sample_profile):
    """계정 삭제의 Graph 호출 3건(토큰·사용자삭제·파기)이 클라이언트 하나를 공유한다."""
    from unittest.mock import AsyncMock, patch

    import httpx

    await client.put("/profile", json=sample_profile)

    with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
        instance = AsyncMock()
        instance.post = AsyncMock(return_value=graph_token_response())
        instance.delete = AsyncMock(
            side_effect=lambda url, **_: httpx.Response(204, request=httpx.Request("DELETE", url))
        )
        instance.__aenter__ = AsyncMock(return_value=instance)
        instance.__aexit__ = AsyncMock(return_value=False)
        mock_cls.return_value = instance
        assert (await client.delete("/account")).status_code == 200

    assert mock_cls.call_count == 1


@pytest.mark.asyncio
async def test_graph_token_failure_returns_502(client, sample_profile):
    """토큰 발급 실패(가장 흔한 원인: 클라이언트 시크릿 만료)는 502 로 표면화돼야 한다.

    조용히 500 이 되면 "계정 삭제만 안 되는" 상태를 원인 없이 만나게 된다.
    """
    from unittest.mock import AsyncMock, patch

    import httpx

    await client.put("/profile", json=sample_profile)

    with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
        instance = AsyncMock()
        instance.post = AsyncMock(
            return_value=httpx.Response(401, json={"error": "invalid_client"},
                                        request=httpx.Request("POST", "http://mock/token"))
        )
        instance.__aenter__ = AsyncMock(return_value=instance)
        instance.__aexit__ = AsyncMock(return_value=False)
        mock_cls.return_value = instance
        resp = await client.delete("/account")

    assert resp.status_code == 502
    assert resp.json()["code"] == "AUTH_TOKEN_FAILED"
    # Auth 삭제가 시작되지 않았으므로 앱 데이터는 보존돼야 한다(재로그인 가능).
    assert (await client.get("/profile")).status_code == 200


@pytest.mark.asyncio
async def test_db_purge_failure_after_auth_deletion_is_logged_as_orphan(client, sample_profile, caplog):
    """Auth 삭제 성공 후 DB purge 가 실패하면 고아 user_id 를 로깅해야 한다.

    reaper 가 후속 청소를 하지만, 어떤 사용자가 고아가 됐는지는 이 로그가 유일한 단서다.
    """
    import logging
    from unittest.mock import AsyncMock, patch

    import httpx

    from app.services.account_service import AccountService

    await client.put("/profile", json=sample_profile)

    with patch("app.services.account_service.httpx.AsyncClient") as mock_cls, patch.object(
        AccountService, "_purge_app_data", side_effect=RuntimeError("db down")
    ):
        instance = AsyncMock()
        instance.post = AsyncMock(return_value=graph_token_response())
        instance.delete = AsyncMock(
            side_effect=lambda url, **_: httpx.Response(204, request=httpx.Request("DELETE", url))
        )
        instance.__aenter__ = AsyncMock(return_value=instance)
        instance.__aexit__ = AsyncMock(return_value=False)
        mock_cls.return_value = instance

        # ServerErrorMiddleware 는 500 응답을 만든 뒤에도 예외를 재전파하고 ASGITransport 가
        # 그대로 올려보낸다. 사용자가 받는 응답(500 + 일반 메시지)은 test_main_handlers.py 가
        # 따로 고정하므로 여기서는 **고아 로그**만 검증한다.
        with caplog.at_level(logging.ERROR, logger="app.services.account_service"):
            with pytest.raises(RuntimeError, match="db down"):
                await client.delete("/account")

    assert "orphaned user_id" in " ".join(r.getMessage() for r in caplog.records)
