from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import Settings, get_settings
from app.database import get_db
from app.dependencies import get_current_user_id
from app.services.account_service import AccountService

router = APIRouter(tags=["account"])


@router.delete("/account", operation_id="deleteAccount")
async def delete_account(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> dict[str, str]:
    await AccountService(db, settings).delete_account(user_id)
    return {"status": "ok"}
