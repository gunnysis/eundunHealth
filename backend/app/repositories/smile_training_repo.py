from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import and_, delete, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.smile_training import SmileTrainingSession


class SmileTrainingRepository:
    """스마일 트레이닝 세션(SmileTrainingSession)의 DB 접근 레이어."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_id(self, session_id: uuid.UUID) -> SmileTrainingSession | None:
        """UUID로 세션 1건 조회."""
        result = await self.db.execute(select(SmileTrainingSession).where(SmileTrainingSession.id == session_id))
        return result.scalar_one_or_none()

    async def create(
        self,
        *,
        session_id: uuid.UUID,
        user_id: str,
        duration_sec: int,
        completed_reps: int,
        occurred_at: datetime,
    ) -> SmileTrainingSession:
        """신규 스마일 트레이닝 v2 세션 삽입 후 flush."""
        entity = SmileTrainingSession(
            id=session_id,
            user_id=user_id,
            duration_sec=duration_sec,
            completed_reps=completed_reps,
            occurred_at=occurred_at,
        )
        self.db.add(entity)
        await self.db.flush()
        return entity

    async def list_by_user_cursor(
        self,
        user_id: str,
        limit: int,
        cursor_occurred_at: datetime | None = None,
        cursor_id: uuid.UUID | None = None,
    ) -> list[SmileTrainingSession]:
        """인증 사용자의 세션을 `occurred_at DESC, id DESC` 커서 기준으로 조회한다.

        next_cursor 판정을 위해 호출부에서 `limit + 1` 로 조회한다.
        """
        stmt = select(SmileTrainingSession).where(SmileTrainingSession.user_id == user_id)
        if cursor_occurred_at is not None and cursor_id is not None:
            stmt = stmt.where(
                or_(
                    SmileTrainingSession.occurred_at < cursor_occurred_at,
                    and_(
                        SmileTrainingSession.occurred_at == cursor_occurred_at,
                        SmileTrainingSession.id < cursor_id,
                    ),
                )
            )
        stmt = stmt.order_by(SmileTrainingSession.occurred_at.desc(), SmileTrainingSession.id.desc()).limit(limit)
        result = await self.db.execute(stmt)
        return list(result.scalars().all())

    async def list_all_occurred_at_by_user(self, user_id: str) -> list[datetime]:
        """사용자의 전체 세션 occurred_at 목록을 반환 (KST 날짜 기반 연속일 계산용)."""
        result = await self.db.execute(
            select(SmileTrainingSession.occurred_at)
            .where(SmileTrainingSession.user_id == user_id)
            .order_by(SmileTrainingSession.occurred_at.asc())
        )
        return list(result.scalars().all())

    async def get_summary_counts(self, user_id: str) -> tuple[int, int]:
        """사용자의 총 세션 수와 총 수행 시간(초) 합계를 1회 쿼리로 반환."""
        result = await self.db.execute(
            select(
                func.count(SmileTrainingSession.id),
                func.coalesce(func.sum(SmileTrainingSession.duration_sec), 0),
            ).where(SmileTrainingSession.user_id == user_id)
        )
        row = result.one()
        return int(row[0]), int(row[1])

    async def delete_all_by_user(self, user_id: str) -> int:
        """사용자의 모든 스마일 트레이닝 세션을 삭제하고 삭제된 행 수를 반환한다."""
        result = await self.db.execute(delete(SmileTrainingSession).where(SmileTrainingSession.user_id == user_id))
        return int(result.rowcount or 0)  # type: ignore[attr-defined]
