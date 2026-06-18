"""App Links + email confirmation fallback page.

이 라우터는 정적 콘텐츠만 서빙한다. DB/JWT/비즈니스 로직 0.
- GET /.well-known/assetlinks.json: Android App Links 검증용
- GET /auth/confirm: 앱 미설치 디바이스용 fallback HTML (안내 + Play Store 링크)
"""

from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["auth"])

_PACKAGE_NAME = "com.gunnys.eundunhealth"

# App Links 자산 링크 지문 — 설치된 앱의 *서명 인증서* SHA-256 과 일치해야 딥링크 검증(autoVerify) 성공.
# 셋 다 등록 → 로컬(debug/release APK)·Play 배포본 모든 설치 경로에서 딥링크가 검증된다.
#
# ⚠️ 출시 CRITICAL: AAB 는 **Play App Signing** 으로 Google 이 재서명한다(AAB 는 opt-out 불가).
# 따라서 Play 로 배포(내부 테스트 트랙 포함)된 설치본의 인증서 = **Play App Signing 키**이지 업로드 키가 아니다.
# 이 키가 빠지면 Play 배포 빌드에서만 딥링크/자동로그인이 깨진다(로컬 APK 는 정상이라 테스트로 못 잡음).
#   Play 키 값 위치(2024+ 개편 경로): Play Console(앱 선택) → Play로 보호(Protected with Play)
#     → Play 스토어 배포(Play Store distribution) → Play 앱 서명으로 이동 → "앱 서명 키 인증서" SHA-256.
#     (키는 첫 AAB 업로드 시 자동 생성. 로컬 keytool 은 업로드 키만 나와 무용.)
#   값 변경 후 재배포만 하면 prod assetlinks 반영(앱 재업로드 불필요 — assetlinks 는 서버 측).
# 절차/체크리스트: docs/ops/play-store-release.md §4.1.
_SHA256_FINGERPRINTS = [
    # debug 키 — 로컬 adb 디버그 빌드 검증용
    "E3:A4:A8:3A:76:49:F3:34:62:4F:B0:B2:E6:6D:CF:51:36:BF:EF:0F:D9:15:FC:E8:4B:05:06:47:98:49:E4:42",
    # 업로드 키(.key/eundunhealth_upload_key) — 로컬 adb install 한 release APK 검증용 (signingReport release)
    "20:96:86:D4:FC:1C:51:1D:64:09:FB:22:8D:FD:0C:DA:35:64:93:7F:24:1D:43:DA:CB:E7:02:41:6C:0F:0A:8D",
    # Play App Signing 키 (Google 재서명) — Play 배포본(내부테스트 포함) 검증용. ★출시 필수.
    "92:D5:0C:45:45:3B:BA:04:19:67:DD:F2:84:6D:A9:D7:0B:7B:80:9A:F6:BA:F2:A2:87:E1:62:FE:D2:D5:DA:48",
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


# include_in_schema=False — 브라우저용 HTML fallback 이라 JSON API 계약(openapi.json)에서 제외.
# 포함 시 앱이 호출하지 않는 죽은 Android 클라이언트 메서드를 생성한다. 라우트는 그대로 동작(브라우저 직접 접근).
@router.get("/auth/confirm", include_in_schema=False, response_class=HTMLResponse)
def confirm_fallback() -> str:
    """앱 미설치 디바이스용 이메일 인증 완료 fallback 페이지를 반환한다."""
    return _CONFIRM_HTML
