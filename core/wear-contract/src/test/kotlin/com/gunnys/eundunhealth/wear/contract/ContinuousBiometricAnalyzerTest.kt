package com.gunnys.eundunhealth.wear.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousBiometricAnalyzerTest {

    @Test
    fun `analyzeDay handles empty bucket list gracefully with zeroed zones`() {
        val summary =
            ContinuousBiometricAnalyzer.analyzeDay(
                recordDate = "2026-11-09",
                capturedAtEpochMs = 1_762_650_000_000L,
                buckets = emptyList(),
                totalSteps = 1200,
                activeCaloriesKcal = 85.5,
                sourceDevices = listOf("Xiaomi Watch 5", "Xiaomi Watch 5"),
            )

        assertEquals("2026-11-09", summary.recordDate)
        assertEquals(0, summary.totalMonitoredMinutes)
        assertEquals(0, summary.zoneMinutesSum)
        assertEquals(1200, summary.totalSteps)
        assertEquals(listOf("Xiaomi Watch 5"), summary.sourceDevices)
    }

    @Test
    fun `analyzeDay computes 5-dimensional metrics across ASLEEP PASSIVE UNKNOWN and EXERCISE states`() {
        val baseMinute = 29_377_500L // 2025/2026 epoch minute baseline
        val buckets = mutableListOf<MinuteBiometricBucket>()

        // 1) ASLEEP (10 minutes): 8 minutes calm at 58 BPM + 2 sleep awakenings at 78 BPM & 80 BPM
        for (m in 0L until 10L) {
            val isAwakening = (m == 3L || m == 8L)
            buckets.add(
                MinuteBiometricBucket(
                    epochMinute = baseMinute + m,
                    avgBpm = if (isAwakening) 74 else 58,
                    maxBpm = if (isAwakening) 80 else 60,
                    minBpm = 56,
                    stepsDelta = 0,
                    activityContext = WearActivityContext.ASLEEP,
                ),
            )
        }

        // 2) PASSIVE calm morning (10 minutes): 64 BPM
        for (m in 10L until 20L) {
            buckets.add(
                MinuteBiometricBucket(
                    epochMinute = baseMinute + m,
                    avgBpm = 64,
                    maxBpm = 66,
                    minBpm = 62,
                    stepsDelta = 2,
                    activityContext = WearActivityContext.PASSIVE,
                ),
            )
        }

        // 3) UNKNOWN light movement / outing with sudden Rapid Delta Spike (+36 BPM jump to 112 BPM)
        //    followed by 3 minutes of sustained mind-tension at 98..104 BPM
        buckets.add(
            MinuteBiometricBucket(
                epochMinute = baseMinute + 20L,
                avgBpm = 98,
                maxBpm = 112,
                minBpm = 82,
                stepsDelta = 12,
                activityContext = WearActivityContext.UNKNOWN,
            ),
        )
        buckets.add(
            MinuteBiometricBucket(
                epochMinute = baseMinute + 21L,
                avgBpm = 100,
                maxBpm = 105,
                minBpm = 95,
                stepsDelta = 8,
                activityContext = WearActivityContext.PASSIVE,
            ),
        )
        buckets.add(
            MinuteBiometricBucket(
                epochMinute = baseMinute + 22L,
                avgBpm = 96,
                maxBpm = 101,
                minBpm = 92,
                stepsDelta = 0,
                activityContext = WearActivityContext.PASSIVE,
            ),
        )

        // 4) EXERCISE overload (4 minutes at 158 BPM) -> transition to PASSIVE recovery (drops to 122 BPM in 2 min)
        for (m in 30L until 34L) {
            buckets.add(
                MinuteBiometricBucket(
                    epochMinute = baseMinute + m,
                    avgBpm = 158,
                    maxBpm = 164,
                    minBpm = 150,
                    stepsDelta = 110,
                    activityContext = WearActivityContext.EXERCISE,
                ),
            )
        }
        buckets.add(
            MinuteBiometricBucket(
                epochMinute = baseMinute + 34L,
                avgBpm = 135,
                maxBpm = 145,
                minBpm = 128,
                stepsDelta = 10,
                activityContext = WearActivityContext.PASSIVE,
            ),
        )
        buckets.add(
            MinuteBiometricBucket(
                epochMinute = baseMinute + 35L,
                avgBpm = 122,
                maxBpm = 128,
                minBpm = 118,
                stepsDelta = 0,
                activityContext = WearActivityContext.PASSIVE,
            ),
        )

        val summary =
            ContinuousBiometricAnalyzer.analyzeDay(
                recordDate = "2026-11-09",
                capturedAtEpochMs = 1_762_680_000_000L,
                buckets = buckets,
                totalSteps = 4500,
                activeCaloriesKcal = 310.0,
                hrvRmssdMs = 42.5,
                sourceDevices = listOf("Xiaomi Watch 5 (M2501W1)"),
            )

        // Verify 100% zone partition invariant across all monitored minutes
        assertEquals(29, summary.totalMonitoredMinutes)
        assertEquals(summary.totalMonitoredMinutes, summary.zoneMinutesSum)

        // Adaptive resting BPM should reflect calm sleep/passive P20 (~58..60 BPM)
        assertNotNull(summary.restingBpm)
        assertTrue("Expected restingBpm in 55..65, got ${summary.restingBpm}", summary.restingBpm!! in 55..65)

        // Rapid delta spike in UNKNOWN state must be captured!
        assertTrue("Expected at least 1 rapid delta spike", summary.rapidDeltaSpikeCount >= 1)
        assertNotNull(summary.maxDeltaBpmPerMin)
        assertTrue(summary.maxDeltaBpmPerMin!! >= 18)

        // Sustained tension episode (minutes 20..22) must be captured
        assertEquals(1, summary.tensionEpisodeCount)
        assertEquals(3, summary.tensionMinutes)
        assertEquals(112, summary.peakTensionBpm)

        // Exercise overload (4 consecutive minutes >= 150 BPM) & Post-exercise HRR drop (158 -> 122 = 36 BPM)
        assertEquals(1, summary.activityOverloadCount)
        assertEquals(36, summary.avgRecoveryDropBpm)

        // Nocturnal sleep awakenings (minutes 3 and 8)
        assertEquals(2, summary.sleepAwakeningCount)

        // Recovery score in valid 0..100 bounds
        assertNotNull(summary.recoveryScore)
        assertTrue(summary.recoveryScore!! in 0..100)
        assertTrue(summary.hourlyBuckets.isNotEmpty())
    }
}
