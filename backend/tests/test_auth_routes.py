"""App Links + email confirmation fallback page tests."""
import pytest


@pytest.mark.asyncio
async def test_assetlinks_json_returns_valid_structure(client):
    response = await client.get("/.well-known/assetlinks.json")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/json")
    body = response.json()
    assert isinstance(body, list)
    assert len(body) >= 1
    statement = body[0]
    assert statement["relation"] == ["delegate_permission/common.handle_all_urls"]
    target = statement["target"]
    assert target["namespace"] == "android_app"
    assert target["package_name"] == "com.gunnys.eundunhealth"
    assert isinstance(target["sha256_cert_fingerprints"], list)
    assert len(target["sha256_cert_fingerprints"]) >= 1


@pytest.mark.asyncio
async def test_assetlinks_json_includes_debug_and_release_sha256(client):
    response = await client.get("/.well-known/assetlinks.json")
    fingerprints = response.json()[0]["target"]["sha256_cert_fingerprints"]
    assert len(fingerprints) >= 2, "debug + release SHA256 둘 다 필요"


@pytest.mark.asyncio
async def test_confirm_html_returns_html_page_with_play_store_link(client):
    response = await client.get("/auth/confirm")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/html")
    html = response.text
    assert "이메일" in html
    assert "은둔헬스" in html
    assert "play.google.com/store/apps/details" in html
    assert "com.gunnys.eundunhealth" in html
