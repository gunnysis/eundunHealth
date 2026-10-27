import datetime

from sqlalchemy import DateTime, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class DataResetAudit(Base):
    """비가역 v2 user-data reset의 중복 실행 방지 및 비민감 실행 증적."""

    __tablename__ = "data_reset_audits"

    reset_id: Mapped[str] = mapped_column(String(128), primary_key=True)
    image_sha: Mapped[str] = mapped_column(String(64), nullable=False)
    execution_id: Mapped[str] = mapped_column(String(128), nullable=False)
    row_counts_json: Mapped[str] = mapped_column(Text, nullable=False)
    completed_at: Mapped[datetime.datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
