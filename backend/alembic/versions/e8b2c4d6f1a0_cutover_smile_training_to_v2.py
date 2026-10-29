"""cutover smile_training_sessions to v2 single spec

Revision ID: e8b2c4d6f1a0
Revises: d7f4a2c91b10
Create Date: 2026-10-29 10:15:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "e8b2c4d6f1a0"
down_revision: str | Sequence[str] | None = "d7f4a2c91b10"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # 1. Cutover Reset: 기존 v1 스마일 트레이닝 기록 전체 초기화
    op.execute("DELETE FROM smile_training_sessions")

    # 2. reflection_emoji 체크 제약조건 제거 후 레거시 3개 컬럼 드롭
    op.drop_constraint(
        "ck_smile_sessions_reflection_emoji_range",
        "smile_training_sessions",
        type_="check",
    )
    op.drop_column("smile_training_sessions", "reflection_emoji")
    op.drop_column("smile_training_sessions", "difficulty")
    op.drop_column("smile_training_sessions", "routine")


def downgrade() -> None:
    op.add_column(
        "smile_training_sessions",
        sa.Column("routine", sa.String(length=50), server_default="SMILE_AND_FRONT", nullable=False),
    )
    op.add_column(
        "smile_training_sessions",
        sa.Column("difficulty", sa.String(length=20), server_default="STANDARD", nullable=False),
    )
    op.add_column(
        "smile_training_sessions",
        sa.Column("reflection_emoji", sa.SmallInteger(), nullable=True),
    )
    op.create_check_constraint(
        "ck_smile_sessions_reflection_emoji_range",
        "smile_training_sessions",
        "reflection_emoji IS NULL OR (reflection_emoji >= 1 AND reflection_emoji <= 5)",
    )
