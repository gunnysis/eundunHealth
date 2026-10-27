from typing import Literal

from fastapi import APIRouter, Depends, Query

from app.config import Settings, get_settings
from app.schemas.app_compatibility import AppCompatibilityResponse

router = APIRouter(tags=["app-compatibility"])


@router.get("/app-compatibility", response_model=AppCompatibilityResponse, operation_id="getAppCompatibility")
async def get_app_compatibility(
    platform: Literal["android"] = Query(),
    version_code: int = Query(ge=1, alias="versionCode"),
    settings: Settings = Depends(get_settings),
) -> AppCompatibilityResponse:
    """인증 전 Android 앱의 최소 지원 버전과 업데이트 위치를 반환한다."""
    minimum = settings.minimum_supported_android_version_code
    return AppCompatibilityResponse(
        platform=platform,
        minimum_supported_version_code=minimum,
        update_required=version_code < minimum,
        play_package_uri=settings.android_play_package_uri,
    )
