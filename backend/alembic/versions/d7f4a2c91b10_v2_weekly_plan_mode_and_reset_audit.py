"""v2 weekly plan mode, profile preference, rest_days array, and reset audit.

Revision ID: d7f4a2c91b10
Revises: a3869f575d6a
Create Date: 2026-10-27
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

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
    op.add_column(
        "user_profiles",
        sa.Column(
            "rest_days",
            postgresql.ARRAY(sa.Integer()),
            nullable=False,
            server_default=sa.text("'{7}'::integer[]"),
        ),
    )
    op.execute("UPDATE user_profiles SET rest_days = ARRAY[COALESCE(rest_day, 7)]")
    op.drop_column("user_profiles", "rest_day")
    op.create_check_constraint(
        "ck_user_profiles_rest_days_valid",
        "user_profiles",
        "cardinality(rest_days) >= 1 AND cardinality(rest_days) <= 6 "
        "AND array_position(rest_days, NULL) IS NULL "
        "AND rest_days <@ ARRAY[1, 2, 3, 4, 5, 6, 7]",
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
    op.add_column(
        "user_profiles",
        sa.Column("rest_day", sa.Integer(), nullable=False, server_default="7"),
    )
    op.execute("UPDATE user_profiles SET rest_day = COALESCE(rest_days[1], 7)")
    op.drop_constraint("ck_user_profiles_rest_days_valid", "user_profiles", type_="check")
    op.drop_column("user_profiles", "rest_days")
    op.drop_constraint("ck_user_profiles_default_workout_plan_mode", "user_profiles", type_="check")
    op.drop_column("user_profiles", "default_workout_plan_mode")
    op.drop_constraint("ck_weekly_plans_plan_mode", "weekly_plans", type_="check")
    op.drop_column("weekly_plans", "plan_mode")
