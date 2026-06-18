"""법적 고지 공개 페이지 — Play Store 등록용 개인정보/계정삭제 URL.

- GET /privacy: 개인정보 처리방침
- GET /account-deletion: 계정 및 데이터 삭제 안내

SSoT 는 docs/store/*.md, scripts/sync-legal-docs.sh 로 app/legal/*.md 동기화(빌드 컨텍스트).
인증 불필요 공개 라우트(Play 심사봇·미설치 사용자도 접근). 내용은 우리 소유 .md 만
렌더하므로(사용자 입력 아님) 인젝션 표면 없음.
"""

from pathlib import Path

import markdown
from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["legal"])

_LEGAL_DIR = Path(__file__).resolve().parent.parent / "legal"

# 모바일 친화 최소 템플릿 — Play 심사 + 단말 브라우저 가독성(UTF-8, viewport, 읽기 폭 제한).
_PAGE_TEMPLATE = """<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="all">
<title>{title}</title>
<style>
  :root {{ color-scheme: light dark; }}
  body {{ font-family: -apple-system, "Segoe UI", Roboto, "Noto Sans KR", sans-serif;
         line-height: 1.7; max-width: 760px; margin: 0 auto; padding: 24px 18px;
         color: #1a1a1a; background: #fff; }}
  @media (prefers-color-scheme: dark) {{ body {{ color: #e6e6e6; background: #16181c; }} }}
  h1 {{ font-size: 1.5rem; }} h2 {{ font-size: 1.2rem; margin-top: 1.8em; }}
  table {{ border-collapse: collapse; width: 100%; }}
  th, td {{ border: 1px solid #8884; padding: 6px 10px; text-align: left; }}
  hr {{ border: none; border-top: 1px solid #8884; margin: 1.6em 0; }}
  code {{ background: #8881; padding: 0 4px; border-radius: 3px; }}
  a {{ color: #2563eb; }}
</style>
</head>
<body>
{body}
</body>
</html>
"""


def _render(filename: str, title: str) -> str:
    md_text = (_LEGAL_DIR / filename).read_text(encoding="utf-8")
    body = markdown.markdown(md_text, extensions=["tables", "sane_lists"])
    return _PAGE_TEMPLATE.format(title=title, body=body)


# 정적 콘텐츠 — import 시 1회 렌더해 캐시(요청마다 재파싱 불필요).
_PRIVACY_HTML = _render("privacy-policy.md", "개인정보 처리방침 — 은둔헬스")
_ACCOUNT_DELETION_HTML = _render("account-deletion.md", "계정 및 데이터 삭제 — 은둔헬스")


@router.get("/privacy", operation_id="getPrivacyPolicy", response_class=HTMLResponse)
def privacy_policy() -> HTMLResponse:
    """개인정보 처리방침(Play Store 개인정보 URL 등록 대상)."""
    return HTMLResponse(content=_PRIVACY_HTML)


@router.get("/account-deletion", operation_id="getAccountDeletion", response_class=HTMLResponse)
def account_deletion() -> HTMLResponse:
    """계정 및 데이터 삭제 안내(Play Store 계정 삭제 요청 URL 등록 대상)."""
    return HTMLResponse(content=_ACCOUNT_DELETION_HTML)
