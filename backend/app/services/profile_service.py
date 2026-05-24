from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import NotFoundException
from app.repositories.profile_history_repo import ProfileHistoryRepository
from app.repositories.profile_repo import ProfileRepository
from app.schemas.goal import ProfileHistoryEntry
from app.schemas.profile import UserProfileRequest, UserProfileResponse


class ProfileService:
    def __init__(self, db: AsyncSession):
        self.repo = ProfileRepository(db)
        self.history_repo = ProfileHistoryRepository(db)

    async def get_profile(self, user_id: str) -> UserProfileResponse:
        profile = await self.repo.get_by_user_id(user_id)
        if not profile:
            raise NotFoundException("프로필이 존재하지 않습니다")
        return UserProfileResponse.model_validate(profile)

    async def upsert_profile(self, user_id: str, req: UserProfileRequest) -> None:
        # 1) 프로필 upsert
        data = req.model_dump(exclude_unset=True)
        await self.repo.upsert(user_id, data)
        # 2) 명시적 히스토리 기록 — 사이드 이펙트 분리(spec §M.2)
        # exclude_unset=True로 필드가 빠질 수 있으므로 모델 default 값을 보장한다.
        await self.history_repo.record(
            user_id=user_id,
            height_cm=req.height_cm,
            weight_kg=req.weight_kg,
            body_fat_pct=req.body_fat_pct,
            muscle_mass_kg=req.muscle_mass_kg,
        )

    async def get_history(self, user_id: str, limit: int = 50) -> list[ProfileHistoryEntry]:
        entries = await self.history_repo.list_recent(user_id, limit)
        # 시간 오름차순(차트용)
        entries.reverse()
        return [
            ProfileHistoryEntry(
                height_cm=e.height_cm,
                weight_kg=e.weight_kg,
                body_fat_pct=e.body_fat_pct,
                muscle_mass_kg=e.muscle_mass_kg,
                recorded_at=str(e.recorded_at),
            )
            for e in entries
        ]
