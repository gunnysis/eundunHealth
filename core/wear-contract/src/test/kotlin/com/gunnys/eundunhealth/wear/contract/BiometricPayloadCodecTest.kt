package com.gunnys.eundunhealth.wear.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BiometricPayloadCodecTest {

    @Test
    fun `encodeBatchGzip and decodeBatchGzip preserve all 5-dimensional summary fields`() {
        val sampleSummary =
            BiometricDailySummaryRecord(
                recordDate = "2026-11-09",
                capturedAtEpochMs = 1_762_680_000_000L,
                zoneId = "Asia/Seoul",
                totalSteps = 8420,
                activeCaloriesKcal = 412.5,
                restingBpm = 61,
                avgBpm = 74,
                minBpm = 54,
                maxBpm = 162,
                hrvRmssdMs = 38.4,
                totalMonitoredMinutes = 720,
                relaxedZoneMinutes = 410,
                normalZoneMinutes = 230,
                elevatedTensionMinutes = 45,
                highLoadMinutes = 35,
                tensionEpisodeCount = 3,
                tensionMinutes = 28,
                peakTensionBpm = 114,
                rapidDeltaSpikeCount = 2,
                maxDeltaBpmPerMin = 29,
                activityOverloadCount = 1,
                avgRecoveryDropBpm = 24,
                sleepAwakeningCount = 1,
                recoveryScore = 76,
                hourlyBuckets =
                listOf(
                    HourlyBiometricBucket(
                        hour = 9,
                        avgBpm = 72,
                        maxBpm = 108,
                        dominantContext = WearActivityContext.PASSIVE,
                        monitoredMinutes = 58,
                    ),
                    HourlyBiometricBucket(
                        hour = 14,
                        avgBpm = 132,
                        maxBpm = 162,
                        dominantContext = WearActivityContext.EXERCISE,
                        monitoredMinutes = 45,
                    ),
                ),
                sourceDevices = listOf("Xiaomi Watch 5 (M2501W1)"),
            )

        val compressed =
            BiometricPayloadCodec.encodeBatchGzip(
                sentAtEpochMs = 1_762_680_123_000L,
                sourceNodeId = "node-xiaomi-watch-5",
                deviceModel = "M2501W1",
                summaries = listOf(sampleSummary),
            )

        val decoded = BiometricPayloadCodec.decodeBatchGzip(compressed)
        assertEquals(WearContractConstants.BIOMETRIC_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1_762_680_123_000L, decoded.sentAtEpochMs)
        assertEquals("node-xiaomi-watch-5", decoded.sourceNodeId)
        assertEquals("M2501W1", decoded.deviceModel)
        assertEquals(1, decoded.summaries.size)
        assertEquals(sampleSummary, decoded.summaries.first())

        val hourlyJson = BiometricPayloadCodec.encodeHourlyBucketsJson(sampleSummary.hourlyBuckets)
        assertTrue(hourlyJson.contains("\"hour\":9"))
        assertTrue(hourlyJson.contains("\"dominantContext\":\"EXERCISE\""))

        val devicesJson = BiometricPayloadCodec.encodeSourceDevicesJson(sampleSummary.sourceDevices)
        assertEquals("[\"Xiaomi Watch 5 (M2501W1)\"]", devicesJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeBatchGzip rejects tampered body when SHA-256 does not match`() {
        val summary =
            BiometricDailySummaryRecord(
                recordDate = "2026-11-09",
                capturedAtEpochMs = 1_762_680_000_000L,
                totalSteps = 1000,
            )
        val compressed =
            BiometricPayloadCodec.encodeBatchGzip(
                sentAtEpochMs = 1_762_680_000_000L,
                sourceNodeId = "watch-node",
                deviceModel = "M2501W1",
                summaries = listOf(summary),
            )
        val rawText =
            GZIPInputStream(ByteArrayInputStream(compressed)).use {
                it.readBytes().toString(StandardCharsets.UTF_8)
            }
        val tamperedText = rawText.replace("totalSteps=1000", "totalSteps=9999")
        val tamperedCompressed =
            ByteArrayOutputStream().also { bos ->
                GZIPOutputStream(bos).use { gzip ->
                    gzip.write(tamperedText.toByteArray(StandardCharsets.UTF_8))
                }
            }.toByteArray()

        BiometricPayloadCodec.decodeBatchGzip(tamperedCompressed)
    }
}
