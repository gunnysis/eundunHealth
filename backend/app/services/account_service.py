import logging

import httpx
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import Settings
from app.exceptions import AppException
from app.repositories.badge_repo import BadgeRepository
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

    async def delete_account(self, user_id: str) -> None:
        """Supabase Auth 삭제 후 앱 DB 데이터를 순서대로 제거한다.

        Auth 먼저 삭제하면:
        - Auth 실패 시 DB 데이터 보존 → 사용자가 재로그인 가능 (안전)
        - Auth 성공 후 DB 실패 시 → 고아 DB 데이터만 남음 (무해, 배치 정리 가능)
        반대 순서(DB 먼저)는 Auth 실패 시 빈 프로필 상태가 되어 위험.
        """
        # Step 1: Supabase Admin API로 Auth 사용자 삭제 (실패 시 예외 → DB 보존)
        await self._delete_supabase_user(user_id)

        # Step 2: Auth 삭제 성공 후 앱 데이터 삭제
        await self.badge_repo.delete_all_by_user(user_id)
        await self.plan_repo.delete_all_by_user(user_id)
        await self.profile_repo.delete_by_user_id(user_id)

    async def _delete_supabase_user(self, user_id: str) -> None:
        async with httpx.AsyncClient() as client:
            resp = await client.delete(
                f"{self.settings.supabase_url}/auth/v1/admin/users/{user_id}",
                headers={
                    "Authorization": f"Bearer {self.settings.supabase_service_role_key}",
                    "apikey": self.settings.supabase_service_role_key,
                },
                timeout=10,
            )
            # 200=삭제 성공, 404=이미 없음 (멱등)
            if resp.status_code not in (200, 404):
                logger.error(f"Supabase user deletion failed: {resp.status_code} {resp.text}")
                raise AppException(502, "AUTH_DELETE_FAILED", "인증 서버 사용자 삭제에 실패했습니다")
