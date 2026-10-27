from fastapi import Depends, Header

from app.config import Settings, get_settings
from app.exceptions import ClientUpdateRequiredException


async def require_supported_android_client(
    version_code: int | None = Header(default=None, alias="Eundun-Client-Version-Code"),
    settings: Settings = Depends(get_settings),
) -> None:
    """v2 변경 endpoint만 최소 Android version policy를 적용한다."""
    if version_code is None or version_code < settings.minimum_supported_android_version_code:
        raise ClientUpdateRequiredException(settings.minimum_supported_android_version_code)
