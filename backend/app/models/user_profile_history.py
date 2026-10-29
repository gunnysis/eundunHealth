from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Float, ForeignKey, Index, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class UserProfileHistory(Base):
    """프로필이 갱신될 때마다 한 줄 추가되는 시계열 신체 계측 이력."""

    __tablename__ = "user_profile_history"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(
        String(128),
        ForeignKey("user_profiles.user_id", ondelete="CASCADE"),
        nullable=False,
    )
    height_cm: Mapped[float] = mapped_column(Float, nullable=False)
    weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    body_fat_pct: Mapped[float | None] = mapped_column(Float, nullable=True)
    muscle_mass_kg: Mapped[float | None] = mapped_column(Float, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        CheckConstraint("height_cm >= 50.0 AND height_cm <= 300.0", name="height_cm_range"),
        CheckConstraint("weight_kg >= 10.0 AND weight_kg <= 500.0", name="weight_kg_range"),
        CheckConstraint(
            "body_fat_pct IS NULL OR (body_fat_pct >= 1.0 AND body_fat_pct <= 70.0)",
            name="body_fat_pct_range",
        ),
        CheckConstraint(
            "muscle_mass_kg IS NULL OR (muscle_mass_kg >= 1.0 AND muscle_mass_kg <= 200.0)",
            name="muscle_mass_kg_range",
        ),
        Index("ix_history_user_recorded", "user_id", "recorded_at"),
    )
