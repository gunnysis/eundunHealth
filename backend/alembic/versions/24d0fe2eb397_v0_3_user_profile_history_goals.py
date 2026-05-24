"""v0.3 user_profile_history + goals

Revision ID: 24d0fe2eb397
Revises: 131baaa7b80b
Create Date: 2026-05-24 21:38:04.306679

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '24d0fe2eb397'
down_revision: Union[str, None] = '131baaa7b80b'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'goals',
        sa.Column('id', sa.UUID(), nullable=False),
        sa.Column('user_id', sa.String(), nullable=False),
        sa.Column('goal_type', sa.String(), nullable=False),
        sa.Column('target_value', sa.Float(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True),
                  server_default=sa.text('(CURRENT_TIMESTAMP)'), nullable=False),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('user_id', 'goal_type'),
    )
    op.create_table(
        'user_profile_history',
        sa.Column('id', sa.UUID(), nullable=False),
        sa.Column('user_id', sa.String(), nullable=False),
        sa.Column('height_cm', sa.Float(), nullable=False),
        sa.Column('weight_kg', sa.Float(), nullable=False),
        sa.Column('body_fat_pct', sa.Float(), nullable=True),
        sa.Column('muscle_mass_kg', sa.Float(), nullable=True),
        sa.Column('recorded_at', sa.DateTime(timezone=True),
                  server_default=sa.text('(CURRENT_TIMESTAMP)'), nullable=False),
        sa.PrimaryKeyConstraint('id'),
    )
    op.create_index(
        op.f('ix_user_profile_history_user_id'),
        'user_profile_history',
        ['user_id'],
        unique=False,
    )
    # NOTE: autogenerate가 badges/user_profiles/weekly_plans의 id 컬럼에 대해
    # NUMERIC → UUID alter_column을 끼워넣었으나, 이는 SQLite test_alembic.db에서만
    # 발생하는 false positive다. 프로덕션 PostgreSQL은 이미 native UUID 타입이므로
    # 해당 라인들을 제거했다. (실행되면 'cannot cast type numeric to uuid' 발생 위험)


def downgrade() -> None:
    op.drop_index(op.f('ix_user_profile_history_user_id'), table_name='user_profile_history')
    op.drop_table('user_profile_history')
    op.drop_table('goals')
