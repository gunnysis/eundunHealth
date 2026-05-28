"""add rest_day to user_profiles

Revision ID: fa3915deab2f
Revises: 24d0fe2eb397
Create Date: 2026-05-28 15:04:16.550342

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'fa3915deab2f'
down_revision: Union[str, None] = '24d0fe2eb397'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # PG 11+ fast path: ADD COLUMN ... DEFAULT <const> 는 table rewrite 없음.
    # (PostgreSQL 16 공식: "the default is evaluated at the time of the statement
    #  and the result stored in the table's metadata ... no rewrite required").
    # if_not_exists=True: dev DB 가 이미 적용된 경우 멱등 (Alembic 1.16+).
    op.add_column(
        "user_profiles",
        sa.Column("rest_day", sa.Integer(), nullable=False, server_default="7"),
        if_not_exists=True,
    )


def downgrade() -> None:
    op.drop_column("user_profiles", "rest_day", if_exists=True)
