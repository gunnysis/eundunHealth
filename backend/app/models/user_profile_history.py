import uuid
from datetime import datetime

from sqlalchemy import DateTime, Float, Index, String, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class UserProfileHistory(Base):
    """프로필이 갱신될 때마다 한 줄 추가 — 진행 차트 데이터 원천."""

    __tablename__ = "user_profile_history"
    __table_args__ = (
        # 모든 read 가 `WHERE user_id ORDER BY recorded_at DESC LIMIT N`(진행 차트).
        # 복합 인덱스로 정렬 step 을 제거(Postgres 가 단일방향 DESC 는 backward scan 으로 커버하므로
        # recorded_at 에 DESC 수식어 불필요). leftmost prefix(user_id)가 단일 user_id 필터도
        # 커버하므로 기존 user_id 단일 인덱스는 제거(중복 회피).
        Index("ix_history_user_recorded", "user_id", "recorded_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[str] = mapped_column(String, nullable=False)
    height_cm: Mapped[float] = mapped_column(Float, nullable=False)
    weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    body_fat_pct: Mapped[float | None] = mapped_column(Float, nullable=True)
    muscle_mass_kg: Mapped[float | None] = mapped_column(Float, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
