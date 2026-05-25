from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.goal import GoalRequest, GoalResponse
from app.services.goal_service import GoalService

router = APIRouter(tags=["goals"])


@router.get("/goals", response_model=list[GoalResponse], operation_id="getGoals")
async def list_goals(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[GoalResponse]:
    return await GoalService(db).list_goals(user_id)


@router.put("/goals", response_model=GoalResponse, operation_id="upsertGoal")
async def upsert_goal(
    req: GoalRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> GoalResponse:
    return await GoalService(db).upsert_goal(user_id, req)
