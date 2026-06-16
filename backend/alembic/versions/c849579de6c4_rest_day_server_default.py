"""rest_day server_default consistency across environments

Revision ID: c849579de6c4
Revises: fa3915deab2f
Create Date: 2026-06-16 09:00:00.000000

환경 분기 수정(감사 B6): 초기 마이그레이션(131baaa7b80b)이 rest_day 를 server_default 없이
생성하므로, fresh DB 는 fa3915deab2f 의 `if_not_exists=True` add_column 이 no-op 이 되어
DB 레벨 기본값이 없다 → rest_day 를 누락한 raw insert 가 실패(ORM 은 default=7 로 가려짐).
prod 는 이미 server_default '7' 을 가지므로 SET DEFAULT 는 무해(idempotent). 모든 환경을
server_default='7' 로 통일하고, 모델 컬럼에도 server_default 를 부여해 autogen drift 를 제거한다.
"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "c849579de6c4"
down_revision: Union[str, None] = "fa3915deab2f"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.alter_column(
        "user_profiles",
        "rest_day",
        existing_type=sa.Integer(),
        existing_nullable=False,
        server_default="7",
    )


def downgrade() -> None:
    op.alter_column(
        "user_profiles",
        "rest_day",
        existing_type=sa.Integer(),
        existing_nullable=False,
        server_default=None,
    )
