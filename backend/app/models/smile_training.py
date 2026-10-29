from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Index, Integer, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class SmileTrainingSession(Base):
    """스마일 트레이닝 v2 완료 세션 요약.

    카메라 프레임·얼굴 좌표·원시 확률 시계열은 온디바이스에서만 소비되고 서버에 저장하지 않는다.
    id는 클라이언트가 생성한 UUID(멱등 키)이며 occurred_at은 클라이언트 완료 시각이다.
    """

    __tablename__ = "smile_training_sessions"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(String(128), nullable=False)
    duration_sec: Mapped[int] = mapped_column(Integer, nullable=False)
    completed_reps: Mapped[int] = mapped_column(Integer, nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        CheckConstraint("length(trim(user_id)) >= 1", name="user_id_nonempty"),
        CheckConstraint("duration_sec >= 0 AND duration_sec <= 1800", name="duration_sec_range"),
        CheckConstraint("completed_reps >= 1 AND completed_reps <= 5", name="completed_reps_range"),
        Index("ix_smile_training_sessions_user_occurred", "user_id", occurred_at.desc()),
    )
