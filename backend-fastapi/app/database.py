from fastapi import Request
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass


# UoW 패턴 — 세션 단위 트랜잭션
# 엔진/세션팩토리는 lifespan에서 1회 생성 → app.state에 저장
# get_db는 Request를 통해 app.state.session_factory 참조
async def get_db(request: Request):
    async with request.app.state.session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
