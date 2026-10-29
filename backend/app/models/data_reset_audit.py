from __future__ import annotations

import datetime

from sqlalchemy import CheckConstraint, DateTime, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class DataResetAudit(Base):
    """하드 리셋(Drop & Recreate) 및 컷오버 실행 증적 감사 원장."""

    __tablename__ = "data_reset_audits"

    reset_id: Mapped[str] = mapped_column(String(128), primary_key=True)
    image_sha: Mapped[str] = mapped_column(String(64), nullable=False)
    execution_id: Mapped[str] = mapped_column(String(128), nullable=False)
    row_counts_json: Mapped[str] = mapped_column(Text, nullable=False)
    completed_at: Mapped[datetime.datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        CheckConstraint("length(trim(reset_id)) >= 1", name="reset_id_nonempty"),
        CheckConstraint("length(trim(image_sha)) >= 1", name="image_sha_nonempty"),
        CheckConstraint("length(trim(execution_id)) >= 1", name="execution_id_nonempty"),
        CheckConstraint("length(trim(row_counts_json)) >= 2", name="row_counts_json_nonempty"),
    )
