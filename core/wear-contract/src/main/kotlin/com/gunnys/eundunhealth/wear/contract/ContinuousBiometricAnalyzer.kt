package com.gunnys.eundunhealth.wear.contract

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 워치 착용 전 구간(`PASSIVE` + `EXERCISE` + `ASLEEP` + `UNKNOWN`) 1분 버킷 시계열을 입력받아
 * 5차원 생체·마음 상태 일일 요약(`BiometricDailySummaryRecord`)을 산출하는 순수 Kotlin 분석 엔진.
 *
 * 설계 근거: `docs/plans/logs/android.md` (2026-11-09 entry — Wear OS 6 상시 생체·마음 상태 5차원 분석 런타임)
 */
object ContinuousBiometricAnalyzer {
    private const val DEFAULT_RESTING_BPM = 70
    private const val MIN_RESTING_BUCKETS = 5
    private const val LIGHT_STEP_THRESHOLD_PER_MIN = 15
    private const val RAPID_SPIKE_DELTA_BPM = 18
    private const val RAPID_SPIKE_COOLDOWN_MINUTES = 3L
    private const val SUSTAINED_TENSION_MIN_MINUTES = 2
    private const val SUSTAINED_TENSION_MAX_GAP_MINUTES = 2L
    private const val POST_EXERCISE_COOLDOWN_MINUTES = 5L
    private const val OVERLOAD_MIN_CONSECUTIVE_MINUTES = 3
    private const val SLEEP_AWAKENING_DELTA_BPM = 15
    private const val HRR_LOOKAHEAD_MINUTES = 2L

    @Suppress("LongParameterList")
    fun analyzeDay(
        recordDate: String,
        capturedAtEpochMs: Long,
        buckets: List<MinuteBiometricBucket>,
        totalSteps: Int = 0,
        activeCaloriesKcal: Double = 0.0,
        hrvRmssdMs: Double? = null,
        sourceDevices: List<String> = emptyList(),
        zoneId: ZoneId = KST_ZONE,
    ): BiometricDailySummaryRecord {
        val dedupedSorted =
            buckets
                .groupBy { it.epochMinute }
                .map { (_, group) -> mergeSameMinute(group) }
                .sortedBy { it.epochMinute }

        if (dedupedSorted.isEmpty()) {
            return BiometricDailySummaryRecord(
                recordDate = recordDate,
                capturedAtEpochMs = capturedAtEpochMs,
                zoneId = zoneId.id,
                totalSteps = totalSteps.coerceAtLeast(0),
                activeCaloriesKcal = activeCaloriesKcal.coerceAtLeast(0.0),
                hrvRmssdMs = hrvRmssdMs,
                sourceDevices = sourceDevices.distinct(),
            )
        }

        val restingBpm = computeAdaptiveRestingBpm(dedupedSorted)
        val avgBpm = dedupedSorted.map { it.avgBpm }.average().roundToInt()
        val minBpm = dedupedSorted.minOf { it.minBpm }
        val maxBpm = dedupedSorted.maxOf { it.maxBpm }

        val zones = classifyZones(dedupedSorted, restingBpm)
        val rapidSpikeResult = detectRapidDeltaSpikes(dedupedSorted, restingBpm)
        val tensionResult = detectSustainedTensionEpisodes(dedupedSorted, restingBpm)
        val overloadCount = detectActivityOverloads(dedupedSorted, restingBpm)
        val avgRecoveryDropBpm = computePostExerciseRecoveryDrop(dedupedSorted)
        val sleepAwakenings = detectSleepAwakenings(dedupedSorted, restingBpm)
        val hourlyBuckets = buildHourlyBuckets(dedupedSorted, zoneId)
        val effectiveSteps = max(totalSteps, dedupedSorted.sumOf { it.stepsDelta })

        val recoveryScore =
            computeRecoveryScore(
                totalMinutes = dedupedSorted.size,
                zones = zones,
                rapidSpikeCount = rapidSpikeResult.spikeCount,
                tensionEpisodeCount = tensionResult.episodeCount,
                overloadCount = overloadCount,
                avgRecoveryDropBpm = avgRecoveryDropBpm,
                sleepAwakeningCount = sleepAwakenings,
            )

        return BiometricDailySummaryRecord(
            recordDate = recordDate,
            capturedAtEpochMs = capturedAtEpochMs,
            zoneId = zoneId.id,
            totalSteps = effectiveSteps,
            activeCaloriesKcal = activeCaloriesKcal.coerceAtLeast(0.0),
            restingBpm = restingBpm,
            avgBpm = avgBpm,
            minBpm = minBpm,
            maxBpm = maxBpm,
            hrvRmssdMs = hrvRmssdMs,
            totalMonitoredMinutes = dedupedSorted.size,
            relaxedZoneMinutes = zones.relaxedMinutes,
            normalZoneMinutes = zones.normalMinutes,
            elevatedTensionMinutes = zones.elevatedTensionMinutes,
            highLoadMinutes = zones.highLoadMinutes,
            tensionEpisodeCount = tensionResult.episodeCount,
            tensionMinutes = tensionResult.totalTensionMinutes,
            peakTensionBpm = tensionResult.peakTensionBpm,
            rapidDeltaSpikeCount = rapidSpikeResult.spikeCount,
            maxDeltaBpmPerMin = rapidSpikeResult.maxDeltaBpm,
            activityOverloadCount = overloadCount,
            avgRecoveryDropBpm = avgRecoveryDropBpm,
            sleepAwakeningCount = sleepAwakenings,
            recoveryScore = recoveryScore,
            hourlyBuckets = hourlyBuckets,
            sourceDevices = sourceDevices.distinct(),
        )
    }

    private fun mergeSameMinute(group: List<MinuteBiometricBucket>): MinuteBiometricBucket {
        if (group.size == 1) return group.first()
        val first = group.first()
        val avg = group.map { it.avgBpm }.average().roundToInt()
        val maxVal = max(avg, group.maxOf { it.maxBpm })
        val minVal = min(avg, group.minOf { it.minBpm })
        val dominantContext =
            group
                .groupingBy { it.activityContext }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: first.activityContext
        return MinuteBiometricBucket(
            epochMinute = first.epochMinute,
            avgBpm = avg,
            maxBpm = maxVal,
            minBpm = minVal,
            stepsDelta = group.maxOf { it.stepsDelta },
            activityContext = dominantContext,
        )
    }

    /**
     * ① 개인 적응형 기저 심박선(Adaptive Baseline) 산출:
     * 수면(`ASLEEP`) 및 정지 휴식(`PASSIVE`, stepsDelta <= 5) 버킷이 5개 이상이면 하위 20백분위수(P20)를,
     * 부족하면 비운동 버킷의 하위 15백분위수(P15)를 개인 `restingBpm`으로 산출한다.
     */
    internal fun computeAdaptiveRestingBpm(buckets: List<MinuteBiometricBucket>): Int {
        val calmSamples =
            buckets
                .filter {
                    it.activityContext == WearActivityContext.ASLEEP ||
                        (it.activityContext == WearActivityContext.PASSIVE && it.stepsDelta <= 5)
                }.map { it.avgBpm }
                .sorted()

        if (calmSamples.size >= MIN_RESTING_BUCKETS) {
            val idx = ((calmSamples.size - 1) * 0.20).roundToInt().coerceIn(0, calmSamples.lastIndex)
            return calmSamples[idx].coerceIn(40, 110)
        }

        val nonExerciseSamples =
            buckets
                .filter { it.activityContext != WearActivityContext.EXERCISE }
                .map { it.avgBpm }
                .sorted()

        if (nonExerciseSamples.isNotEmpty()) {
            val idx = ((nonExerciseSamples.size - 1) * 0.15).roundToInt().coerceIn(0, nonExerciseSamples.lastIndex)
            return nonExerciseSamples[idx].coerceIn(40, 110)
        }

        return DEFAULT_RESTING_BPM
    }

    private data class ZoneDistribution(
        val relaxedMinutes: Int,
        val normalMinutes: Int,
        val elevatedTensionMinutes: Int,
        val highLoadMinutes: Int,
    )

    /**
     * ② 착용 전 구간 4-Zone 연속 시간 분포:
     * 모든 착용 분(`totalMonitoredMinutes`)을 누락 없이 4개 구간으로 상호배타 분류한다.
     */
    private fun classifyZones(buckets: List<MinuteBiometricBucket>, restingBpm: Int): ZoneDistribution {
        val relaxedCeiling = restingBpm + 10
        val tensionFloor = max(restingBpm + 20, 95)
        val highLoadFloor = max(restingBpm + 45, 130)

        var relaxed = 0
        var normal = 0
        var elevated = 0
        var highLoad = 0

        for (bucket in buckets) {
            when {
                bucket.activityContext == WearActivityContext.EXERCISE || bucket.avgBpm >= highLoadFloor -> {
                    highLoad++
                }
                bucket.avgBpm >= tensionFloor -> {
                    elevated++
                }
                bucket.avgBpm < relaxedCeiling -> {
                    relaxed++
                }
                else -> {
                    normal++
                }
            }
        }
        return ZoneDistribution(relaxed, normal, elevated, highLoad)
    }

    private data class RapidSpikeResult(val spikeCount: Int, val maxDeltaBpm: Int?)

    /**
     * ③ 상태 무관 급격한 심박 급상승(Rapid Delta Spike) 포착:
     * `PASSIVE`뿐 아니라 보행·이동·미분류(`UNKNOWN`) 상태에서도 직전 1~3분 내 버킷 평균 대비
     * `+18 BPM` 이상 급등하고 피크 심박이 `max(restingBpm + 22, 100)` 이상인 경우 포착한다.
     * 단, 이미 고강도 운동(`EXERCISE` 및 고걸음 수)이 진행 중인 연속 구간의 정상 상승은 제외한다.
     */
    private fun detectRapidDeltaSpikes(buckets: List<MinuteBiometricBucket>, restingBpm: Int): RapidSpikeResult {
        val spikeFloorBpm = max(restingBpm + 22, 100)
        var spikeCount = 0
        var maxDelta: Int? = null
        var lastSpikeEpochMinute = Long.MIN_VALUE / 2

        for (i in buckets.indices) {
            val current = buckets[i]
            val window =
                buckets
                    .subList(max(0, i - 3), i)
                    .filter { current.epochMinute - it.epochMinute in 1L..3L }

            if (window.isEmpty()) continue

            val baselineAvg = window.map { it.avgBpm }.average().roundToInt()
            val delta = current.maxBpm - baselineAvg
            if (delta > 0) {
                maxDelta = max(maxDelta ?: 0, delta)
            }

            val isSustainedHeavyExercise =
                current.activityContext == WearActivityContext.EXERCISE &&
                    window.all { it.activityContext == WearActivityContext.EXERCISE } &&
                    current.stepsDelta > 60

            val isCandidateSpike =
                !isSustainedHeavyExercise &&
                    delta >= RAPID_SPIKE_DELTA_BPM &&
                    current.maxBpm >= spikeFloorBpm
            val isCooldownElapsed = current.epochMinute - lastSpikeEpochMinute >= RAPID_SPIKE_COOLDOWN_MINUTES

            if (isCandidateSpike && isCooldownElapsed) {
                spikeCount++
                lastSpikeEpochMinute = current.epochMinute
            }
        }
        return RapidSpikeResult(spikeCount, maxDelta)
    }

    private data class SustainedTensionResult(
        val episodeCount: Int,
        val totalTensionMinutes: Int,
        val peakTensionBpm: Int?,
    )

    /**
     * ④ 휴식/일상 중 지속 긴장 에피소드(Sustained Mind-Tension):
     * 비운동 상태(`PASSIVE` 또는 저걸음 `UNKNOWN`)에서 심박이 `max(restingBpm + 20, 95)` 이상으로
     * 2분 이상 지속되는 에피소드를 집계한다 (버킷 간 최대 2분 갭 병합).
     * 단, 운동(`EXERCISE`) 종료 직후 5분 이내의 정상적인 심박 쿨다운(HRR) 구간은 긴장 에피소드 오탐에서 제외한다.
     */
    private fun detectSustainedTensionEpisodes(
        buckets: List<MinuteBiometricBucket>,
        restingBpm: Int,
    ): SustainedTensionResult {
        val tensionFloor = max(restingBpm + 20, 95)
        val exerciseMinutes =
            buckets
                .filter { it.activityContext == WearActivityContext.EXERCISE }
                .map { it.epochMinute }

        val candidateBuckets =
            buckets.filter { bucket ->
                val isLowMovementNonExercise =
                    bucket.activityContext == WearActivityContext.PASSIVE ||
                        (
                            bucket.activityContext == WearActivityContext.UNKNOWN &&
                                bucket.stepsDelta <= LIGHT_STEP_THRESHOLD_PER_MIN
                            )
                val isPostExerciseCooldown =
                    exerciseMinutes.any { exMin ->
                        bucket.epochMinute - exMin in 1L..POST_EXERCISE_COOLDOWN_MINUTES
                    }
                isLowMovementNonExercise && !isPostExerciseCooldown && bucket.avgBpm >= tensionFloor
            }

        if (candidateBuckets.isEmpty()) {
            return SustainedTensionResult(0, 0, null)
        }

        val episodes = mutableListOf<MutableList<MinuteBiometricBucket>>()
        for (bucket in candidateBuckets) {
            val currentEpisode = episodes.lastOrNull()
            if (
                currentEpisode == null ||
                bucket.epochMinute - currentEpisode.last().epochMinute > SUSTAINED_TENSION_MAX_GAP_MINUTES
            ) {
                episodes.add(mutableListOf(bucket))
            } else {
                currentEpisode.add(bucket)
            }
        }

        val qualifiedEpisodes = episodes.filter { it.size >= SUSTAINED_TENSION_MIN_MINUTES }
        val totalMinutes = qualifiedEpisodes.sumOf { it.size }
        val peakBpm = qualifiedEpisodes.flatten().maxOfOrNull { it.maxBpm }

        return SustainedTensionResult(
            episodeCount = qualifiedEpisodes.size,
            totalTensionMinutes = totalMinutes,
            peakTensionBpm = peakBpm,
        )
    }

    /**
     * ⑤-A 활동/운동 중 심박 과부하(`activityOverloadCount`):
     * `EXERCISE` 상태에서 `avgBpm >= max(restingBpm + 55, 150)`이 연속 3분 이상 이어진 에피소드 수.
     */
    private fun detectActivityOverloads(buckets: List<MinuteBiometricBucket>, restingBpm: Int): Int {
        val overloadFloor = max(restingBpm + 55, 150)
        var overloadEpisodes = 0
        var streak = 0
        var prevMinute = Long.MIN_VALUE / 2

        for (bucket in buckets) {
            val isOverload =
                bucket.activityContext == WearActivityContext.EXERCISE && bucket.avgBpm >= overloadFloor
            if (isOverload && (streak == 0 || bucket.epochMinute - prevMinute <= 2L)) {
                streak++
                if (streak == OVERLOAD_MIN_CONSECUTIVE_MINUTES) {
                    overloadEpisodes++
                }
            } else if (isOverload) {
                streak = 1
            } else {
                streak = 0
            }
            prevMinute = bucket.epochMinute
        }
        return overloadEpisodes
    }

    /**
     * ⑤-B 활동 종료 직후 심박 회복량(`avgRecoveryDropBpm`, HRR):
     * `EXERCISE` 구간 종료 후 비운동(`PASSIVE` 또는 `UNKNOWN`) 전환 시점 피크 심박에서
     * 1~2분 뒤 심박이 얼마나 감소했는지 평균 회복 폭(BPM)을 계산한다.
     */
    private fun computePostExerciseRecoveryDrop(buckets: List<MinuteBiometricBucket>): Int? {
        val drops = mutableListOf<Int>()
        for (i in 0 until buckets.lastIndex) {
            val current = buckets[i]
            val next = buckets[i + 1]
            val isTransitionFromExercise =
                current.activityContext == WearActivityContext.EXERCISE &&
                    (next.activityContext == WearActivityContext.PASSIVE || next.activityContext == WearActivityContext.UNKNOWN)
            if (!isTransitionFromExercise) continue

            val recoveryTarget =
                buckets
                    .subList(i + 1, min(buckets.size, i + 5))
                    .filter { it.epochMinute - current.epochMinute in 1L..HRR_LOOKAHEAD_MINUTES }
                    .minByOrNull { it.avgBpm }

            if (recoveryTarget != null) {
                drops.add(current.avgBpm - recoveryTarget.avgBpm)
            }
        }
        return if (drops.isNotEmpty()) drops.average().roundToInt() else null
    }

    /**
     * ⑤-C 수면 중 야간 각성/심박 스파이크(`sleepAwakeningCount`):
     * `ASLEEP` 상태에서 수면 기저선 대비 `+15 BPM` 이상 치솟은 횟수(최소 3분 쿨다운).
     */
    private fun detectSleepAwakenings(buckets: List<MinuteBiometricBucket>, restingBpm: Int): Int {
        val sleepBuckets = buckets.filter { it.activityContext == WearActivityContext.ASLEEP }
        if (sleepBuckets.isEmpty()) return 0

        val sleepSorted = sleepBuckets.map { it.avgBpm }.sorted()
        val sleepBaseline = min(restingBpm, sleepSorted[sleepSorted.size / 4])
        val awakeningThreshold = max(sleepBaseline + SLEEP_AWAKENING_DELTA_BPM, restingBpm + 12)

        var awakenings = 0
        var lastAwakeningMinute = Long.MIN_VALUE / 2
        for (bucket in sleepBuckets) {
            if (
                bucket.maxBpm >= awakeningThreshold &&
                bucket.epochMinute - lastAwakeningMinute >= RAPID_SPIKE_COOLDOWN_MINUTES
            ) {
                awakenings++
                lastAwakeningMinute = bucket.epochMinute
            }
        }
        return awakenings
    }

    /**
     * ⑤-D 전 구간 종합 회복 점수(`recoveryScore` 0..100):
     * 이완 비율(+), 지속 긴장 시간(-), 급격한 스파이크(-), 수면 각성(-), 활동 후 HRR 회복력(+)을 종합 평가.
     */
    @Suppress("LongParameterList")
    private fun computeRecoveryScore(
        totalMinutes: Int,
        zones: ZoneDistribution,
        rapidSpikeCount: Int,
        tensionEpisodeCount: Int,
        overloadCount: Int,
        avgRecoveryDropBpm: Int?,
        sleepAwakeningCount: Int,
    ): Int {
        if (totalMinutes <= 0) return 75
        val relaxedRatio = zones.relaxedMinutes.toDouble() / totalMinutes.toDouble()
        val tensionRatio = zones.elevatedTensionMinutes.toDouble() / totalMinutes.toDouble()

        var score = 78.0
        score += (relaxedRatio * 18.0).coerceAtMost(16.0)
        score -= (tensionRatio * 42.0).coerceAtMost(28.0)
        score -= (rapidSpikeCount * 4.5).coerceAtMost(22.0)
        score -= (tensionEpisodeCount * 3.5).coerceAtMost(18.0)
        score -= (overloadCount * 3.0).coerceAtMost(12.0)
        score -= (sleepAwakeningCount * 3.0).coerceAtMost(15.0)

        if (avgRecoveryDropBpm != null) {
            score +=
                when {
                    avgRecoveryDropBpm >= 20 -> 6.0
                    avgRecoveryDropBpm >= 12 -> 2.0
                    avgRecoveryDropBpm < 6 -> -6.0
                    else -> -2.0
                }
        }

        return score.roundToInt().coerceIn(0, 100)
    }

    private fun buildHourlyBuckets(
        buckets: List<MinuteBiometricBucket>,
        zoneId: ZoneId,
    ): List<HourlyBiometricBucket> = buckets
        .groupBy { bucket ->
            Instant.ofEpochSecond(bucket.epochMinute * 60L).atZone(zoneId).hour
        }.toSortedMap()
        .map { (hour, hourGroup) ->
            val dominantContext =
                hourGroup
                    .groupingBy { it.activityContext }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key ?: WearActivityContext.UNKNOWN
            HourlyBiometricBucket(
                hour = hour,
                avgBpm = hourGroup.map { it.avgBpm }.average().roundToInt(),
                maxBpm = hourGroup.maxOf { it.maxBpm },
                dominantContext = dominantContext,
                monitoredMinutes = hourGroup.size.coerceAtMost(60),
            )
        }
}
