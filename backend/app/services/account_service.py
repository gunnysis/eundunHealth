import logging

import httpx
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import Settings
from app.exceptions import AppException
from app.repositories.badge_repo import BadgeRepository
from app.repositories.goal_repo import GoalRepository
from app.repositories.profile_history_repo import ProfileHistoryRepository
from app.repositories.profile_repo import ProfileRepository
from app.repositories.weekly_plan_repo import WeeklyPlanRepository

logger = logging.getLogger(__name__)


class AccountService:
    """계정 삭제 흐름을 Supabase Auth + 앱 DB 두 단계로 조율한다."""

    def __init__(self, db: AsyncSession, settings: Settings):
        self.db = db
        self.settings = settings
        self.profile_repo = ProfileRepository(db)
        self.plan_repo = WeeklyPlanRepository(db)
        self.badge_repo = BadgeRepository(db)
        self.goal_repo = GoalRepository(db)
        self.history_repo = ProfileHistoryRepository(db)

    async def delete_account(self, user_id: str) -> None:
        """Supabase Auth 삭제 후 앱 DB 데이터를 순서대로 제거한다.

        Auth 먼저 삭제하면:
        - Auth 실패 시 DB 데이터 보존 → 사용자가 재로그인 가능 (안전)
        - Auth 성공 후 DB 실패 시 → 고아 DB 데이터만 남음 (무해, 배치 정리 가능)
        반대 순서(DB 먼저)는 Auth 실패 시 빈 프로필 상태가 되어 위험.
        """
        # Step 1: Supabase Admin API로 Auth 사용자 삭제 (실패 시 예외 → DB 보존)
        await self._delete_supabase_user(user_id)

        # Step 2: Auth 삭제 성공 후 앱 데이터 삭제.
        try:
            await self._purge_app_data(user_id)
        except Exception:
            # Auth 는 이미 삭제됨(Step 1) → DB 삭제 실패 시 고아 데이터가 남는다(UoW 롤백으로 부분삭제는
            # 없음). 정리 대상 user_id 를 로깅 → reap_orphaned_data() 가 후속 청소.
            logger.error("Account DB purge failed after auth deletion — orphaned user_id=%s", user_id)
            raise

    async def _purge_app_data(self, user_id: str) -> None:
        """user_id 를 가진 *모든* per-user 테이블의 데이터를 제거한다.

        새 per-user 테이블 추가 시 여기에 삭제를 추가할 것 — 누락은
        tests/test_account.py::test_delete_account_purges_all_user_data 가 자동 탐지(회귀 가드).
        delete_account(Step 2)와 reap_orphaned_data 가 공유한다(DRY).
        """
        await self.badge_repo.delete_all_by_user(user_id)
        await self.plan_repo.delete_all_by_user(user_id)
        await self.goal_repo.delete_all_by_user(user_id)
        await self.history_repo.delete_all_by_user(user_id)
        await self.profile_repo.delete_by_user_id(user_id)

    async def reap_orphaned_data(self) -> list[str]:
        """앱 DB 엔 있으나 Supabase Auth 엔 없는 user_id 의 데이터를 정리하는 안전망 reaper.

        delete_account 의 Step 2(DB purge)가 실패해 남은 고아, 혹은 다른 경로의 불일치를
        주기적으로 청소한다(Container Apps Job 등). **fail-safe**: Auth 존재 확인이 404(확정
        부재)일 때만 purge — 200/네트워크오류/비정상응답은 보존(불확실하면 절대 지우지 않음).

        **사용자 단위 트랜잭션**: orphan 마다 commit + 에러 격리 — 한 명의 purge 실패가 전체
        sweep 을 중단하거나 이미 정리한 사용자를 롤백하지 않게 한다(배치 잡이 세션을 소유하므로
        메서드가 직접 commit/rollback — 요청 UoW 와 다른 경계). 반환: 실제 purge 한 user_id 목록.
        """
        candidate_ids = await self.profile_repo.list_all_user_ids()
        reaped: list[str] = []
        for user_id in candidate_ids:
            try:
                if await self._user_exists_in_auth(user_id) is False:  # None(불확실)은 스킵
                    await self._purge_app_data(user_id)
                    await self.db.commit()
                    reaped.append(user_id)
                    logger.info("Reaped orphaned app data for user_id=%s", user_id)
            except Exception:
                await self.db.rollback()
                logger.exception("Reap 실패(건너뜀) user_id=%s", user_id)
        return reaped

    def _admin_user_url(self, user_id: str) -> str:
        return f"{self.settings.supabase_url}/auth/v1/admin/users/{user_id}"

    def _admin_headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self.settings.supabase_service_role_key}",
            "apikey": self.settings.supabase_service_role_key,
        }

    async def _user_exists_in_auth(self, user_id: str) -> bool | None:
        """Supabase Auth 의 user 존재 여부. 200=True, 404=False, 그 외/오류=None(불확실)."""
        try:
            async with httpx.AsyncClient() as client:
                resp = await client.get(
                    self._admin_user_url(user_id), headers=self._admin_headers(), timeout=10
                )
        except httpx.HTTPError as e:
            logger.warning("Auth 존재 확인 실패(보존) user_id=%s: %s", user_id, e)
            return None
        if resp.status_code == 200:
            return True
        if resp.status_code == 404:
            return False
        logger.warning("Auth 존재 확인 비정상 응답(보존) user_id=%s status=%s", user_id, resp.status_code)
        return None

    async def _delete_supabase_user(self, user_id: str) -> None:
        async with httpx.AsyncClient() as client:
            resp = await client.delete(
                self._admin_user_url(user_id), headers=self._admin_headers(), timeout=10
            )
            # 200=삭제 성공, 404=이미 없음 (멱등)
            if resp.status_code not in (200, 404):
                logger.error(f"Supabase user deletion failed: {resp.status_code} {resp.text}")
                raise AppException(502, "AUTH_DELETE_FAILED", "인증 서버 사용자 삭제에 실패했습니다")
