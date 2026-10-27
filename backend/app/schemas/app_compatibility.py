from app.schemas.base import CamelSchema


class AppCompatibilityResponse(CamelSchema):
    platform: str
    minimum_supported_version_code: int
    update_required: bool
    play_package_uri: str
