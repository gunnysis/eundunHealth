"""v2 weekly plan mode, profile preference, and reset audit.

Revision ID: d7f4a2c91b10
Revises: a3869f575d6a
Create Date: 2026-10-27
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "d7f4a2c91b10"
down_revision: str | None = "a3869f575d6a"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "weekly_plans",
        sa.Column("plan_mode", sa.String(length=4), server_default="gym", nullable=False),
    )
    op.create_check_constraint(
        "ck_weekly_plans_plan_mode",
        "weekly_plans",
        "plan_mode IN ('gym', 'home')",
    )
    op.add_column(
        "user_profiles",
        sa.Column("default_workout_plan_mode", sa.String(length=4), nullable=True),
    )
    op.create_check_constraint(
        "ck_user_profiles_default_workout_plan_mode",
        "user_profiles",
        "default_workout_plan_mode IS NULL OR default_workout_plan_mode IN ('gym', 'home')",
    )
    op.create_table(
        "data_reset_audits",
        sa.Column("reset_id", sa.String(length=128), nullable=False),
        sa.Column("image_sha", sa.String(length=64), nullable=False),
        sa.Column("execution_id", sa.String(length=128), nullable=False),
        sa.Column("row_counts_json", sa.Text(), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.PrimaryKeyConstraint("reset_id"),
    )


def downgrade() -> None:
    op.drop_table("data_reset_audits")
    op.drop_constraint("ck_user_profiles_default_workout_plan_mode", "user_profiles", type_="check")
    op.drop_column("user_profiles", "default_workout_plan_mode")
    op.drop_constraint("ck_weekly_plans_plan_mode", "weekly_plans", type_="check")
    op.drop_column("weekly_plans", "plan_mode")
