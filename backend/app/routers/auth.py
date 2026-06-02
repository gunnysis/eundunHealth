"""App Links + email confirmation fallback page.

이 라우터는 정적 콘텐츠만 서빙한다. DB/JWT/비즈니스 로직 0.
- GET /.well-known/assetlinks.json: Android App Links 검증용
- GET /auth/confirm: 앱 미설치 디바이스용 fallback HTML (안내 + Play Store 링크)
"""
from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["auth"])

_PACKAGE_NAME = "com.gunnys.eundunhealth"

# Task 2 에서 추출한 fingerprint
_SHA256_FINGERPRINTS = [
    # debug variant
    "E3:A4:A8:3A:76:49:F3:34:62:4F:B0:B2:E6:6D:CF:51:36:BF:EF:0F:D9:15:FC:E8:4B:05:06:47:98:49:E4:42",
    # release variant
    "20:96:86:D4:FC:1C:51:1D:64:09:FB:22:8D:FD:0C:DA:35:64:93:7F:24:1D:43:DA:CB:E7:02:41:6C:0F:0A:8D",
]

_CONFIRM_HTML = """<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>은둔헬스 이메일 인증 완료</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
           max-width: 480px; margin: 0 auto; padding: 32px 24px; line-height: 1.6;
           color: #1a1a1a; background: #f8f9fa; }
    h1 { font-size: 1.5rem; margin-bottom: 16px; }
    p { font-size: 1rem; margin: 12px 0; }
    a.button { display: inline-block; margin-top: 24px; padding: 12px 24px;
               background: #1976d2; color: white; text-decoration: none;
               border-radius: 8px; font-weight: 600; }
  </style>
</head>
<body>
  <h1>이메일 인증이 완료되었습니다</h1>
  <p>은둔헬스 앱이 설치된 휴대폰에서 메일 링크를 클릭하면 자동으로 로그인됩니다.</p>
  <p>앱이 없으신가요? 아래 버튼으로 설치 후 동일 이메일로 로그인하실 수 있습니다.</p>
  <a class="button" href="https://play.google.com/store/apps/details?id=com.gunnys.eundunhealth">
    Google Play 에서 은둔헬스 설치
  </a>
</body>
</html>
"""


@router.get("/.well-known/assetlinks.json", operation_id="getAssetlinks")
def assetlinks_json() -> list[dict[str, object]]:
    """Android App Links 검증용 assetlinks.json을 반환한다."""
    return [
        {
            "relation": ["delegate_permission/common.handle_all_urls"],
            "target": {
                "namespace": "android_app",
                "package_name": _PACKAGE_NAME,
                "sha256_cert_fingerprints": _SHA256_FINGERPRINTS,
            },
        }
    ]


@router.get("/auth/confirm", operation_id="getAuthConfirmFallback", response_class=HTMLResponse)
def confirm_fallback() -> str:
    """앱 미설치 디바이스용 이메일 인증 완료 fallback 페이지를 반환한다."""
    return _CONFIRM_HTML
