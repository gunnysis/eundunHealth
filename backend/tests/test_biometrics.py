from __future__ import annotations

import pytest


@pytest.mark.asyncio
async def test_sync_get_and_delete_biometric_summaries(client):
    """POST /v2/biometrics/sync 멱등 업서트, GET /v2/biometrics/daily 조회, DELETE 단독 삭제 검증."""
    payload = {
        "summaries": [
            {
                "recordDate": "2026-11-08",
                "capturedAtEpochMs": 1762593600000,
                "totalSteps": 4800,
                "activeCaloriesKcal": 230.0,
                "restingBpm": 60,
                "avgBpm": 72,
                "minBpm": 54,
                "maxBpm": 138,
                "totalMonitoredMinutes": 600,
                "relaxedZoneMinutes": 340,
                "normalZoneMinutes": 200,
                "elevatedTensionMinutes": 30,
                "highLoadMinutes": 30,
                "tensionEpisodeCount": 1,
                "tensionMinutes": 14,
                "peakTensionBpm": 105,
                "rapidDeltaSpikeCount": 1,
                "maxDeltaBpmPerMin": 20,
                "activityOverloadCount": 0,
                "avgRecoveryDropBpm": 22,
                "sleepAwakeningCount": 1,
                "recoveryScore": 85,
                "watchModel": "Xiaomi Watch 5 (M2501W1)",
            },
            {
                "recordDate": "2026-11-09",
                "capturedAtEpochMs": 1762680000000,
                "totalSteps": 6200,
                "activeCaloriesKcal": 310.5,
                "restingBpm": 61,
                "avgBpm": 75,
                "minBpm": 55,
                "maxBpm": 152,
                "totalMonitoredMinutes": 720,
                "relaxedZoneMinutes": 410,
                "normalZoneMinutes": 230,
                "elevatedTensionMinutes": 40,
                "highLoadMinutes": 40,
                "tensionEpisodeCount": 2,
                "tensionMinutes": 22,
                "peakTensionBpm": 112,
                "rapidDeltaSpikeCount": 2,
                "maxDeltaBpmPerMin": 26,
                "activityOverloadCount": 1,
                "avgRecoveryDropBpm": 24,
                "sleepAwakeningCount": 1,
                "recoveryScore": 79,
                "watchModel": "Xiaomi Watch 5 (M2501W1)",
            },
        ]
    }

    sync_resp = await client.post("/v2/biometrics/sync", json=payload)
    assert sync_resp.status_code == 200
    sync_body = sync_resp.json()
    assert sync_body["upsertedCount"] == 2
    assert len(sync_body["items"]) == 2

    # 동일 날짜(2026-11-09) 업데이트 재전송 시 행이 복제되지 않고 멱등 갱신되는지 확인
    updated_payload = {
        "summaries": [
            {
                **payload["summaries"][1],
                "totalSteps": 8500,
                "recoveryScore": 88,
                "capturedAtEpochMs": 1762683600000,
            }
        ]
    }
    resync_resp = await client.post("/v2/biometrics/sync", json=updated_payload)
    assert resync_resp.status_code == 200
    assert resync_resp.json()["upsertedCount"] == 1

    list_resp = await client.get("/v2/biometrics/daily?days=14")
    assert list_resp.status_code == 200
    items = list_resp.json()["items"]
    assert len(items) == 2
    assert items[0]["recordDate"] == "2026-11-09"
    assert items[0]["totalSteps"] == 8500
    assert items[0]["recoveryScore"] == 88
    assert items[1]["recordDate"] == "2026-11-08"

    # 단독 삭제 API 검증
    del_resp = await client.delete("/v2/biometrics/daily")
    assert del_resp.status_code == 200
    assert del_resp.json() == {"status": "deleted", "deletedCount": 2}

    empty_resp = await client.get("/v2/biometrics/daily")
    assert empty_resp.status_code == 200
    assert empty_resp.json()["items"] == []


@pytest.mark.asyncio
async def test_biometric_sync_rejects_out_of_range_values(client):
    """Pydantic 경계 검증: recoveryScore > 100 또는 빈 배치 요청 시 422 반환."""
    empty_resp = await client.post("/v2/biometrics/sync", json={"summaries": []})
    assert empty_resp.status_code == 422

    bad_score_resp = await client.post(
        "/v2/biometrics/sync",
        json={
            "summaries": [
                {
                    "recordDate": "2026-11-09",
                    "capturedAtEpochMs": 1762680000000,
                    "recoveryScore": 105,
                }
            ]
        },
    )
    assert bad_score_resp.status_code == 422
