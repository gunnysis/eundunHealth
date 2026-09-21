"""add_gender_to_user_profile

Revision ID: 0dda67f1638a
Revises: 718783053fcd
Create Date: 2026-09-21 01:22:08.755869

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '0dda67f1638a'
down_revision: Union[str, None] = '718783053fcd'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "user_profiles",
        sa.Column("gender", sa.String(length=20), server_default="unspecified", nullable=False),
    )


def downgrade() -> None:
    op.drop_column("user_profiles", "gender")
