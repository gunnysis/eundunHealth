package com.gunnys.eundunhealth.wear.contract

/**
 * 워치 착용 중 AndroidX Health Services(`UserActivityInfo`) 및 걸음 수 변화량으로부터 정규화된 활동 맥락.
 * 특정 상태(`PASSIVE`)에만 국한하지 않고 착용 전 구간을 연속 분석하기 위해 4개 상태를 모두 보존한다.
 */
enum class WearActivityContext {
    PASSIVE,
    EXERCISE,
    ASLEEP,
    UNKNOWN,
    ;

    companion object {
        fun fromWireName(raw: String?): WearActivityContext = entries.firstOrNull {
            it.name.equals(raw?.trim(), ignoreCase = true)
        } ?: UNKNOWN
    }
}

/**
 * 1분 단위로 정규화된 온디바이스 생체·활동 시계열 버킷.
 * 워치 로컬 SQLite에는 최근 7일만 보관되고 외부로 원시 버킷이 직접 전송되지 않는다.
 */
data class MinuteBiometricBucket(
    val epochMinute: Long,
    val avgBpm: Int,
    val maxBpm: Int = avgBpm,
    val minBpm: Int = avgBpm,
    val stepsDelta: Int = 0,
    val activityContext: WearActivityContext = WearActivityContext.UNKNOWN,
) {
    init {
        require(epochMinute >= 0L) { "epochMinute must be non-negative" }
        require(avgBpm in 25..260) { "avgBpm out of physiological range: $avgBpm" }
        require(maxBpm in avgBpm..260) { "maxBpm must be >= avgBpm and <= 260: $maxBpm" }
        require(minBpm in 25..avgBpm) { "minBpm must be >= 25 and <= avgBpm: $minBpm" }
        require(stepsDelta >= 0) { "stepsDelta must be non-negative" }
    }
}

/**
 * KST 기준 시간대별(0~23시) 심박 및 주요 착용 맥락 요약 버킷.
 */
data class HourlyBiometricBucket(
    val hour: Int,
    val avgBpm: Int,
    val maxBpm: Int,
    val dominantContext: WearActivityContext,
    val monitoredMinutes: Int,
) {
    init {
        require(hour in 0..23) { "hour must be in 0..23" }
        require(monitoredMinutes in 0..60) { "monitoredMinutes must be in 0..60" }
    }
}

/**
 * 워치 착용 전 구간(`PASSIVE` + `EXERCISE` + `ASLEEP` + `UNKNOWN`)을 5차원으로 연속 분석한 일일 요약 레코드.
 * 워치(`:wear`) → 모바일(`:app`) → 운영 Azure(`public.biometric_daily_summaries`) 간 단일 계약 모델.
 */
@Suppress("LongParameterList")
data class BiometricDailySummaryRecord(
    val recordDate: String,
    val capturedAtEpochMs: Long,
    val zoneId: String = KST_ZONE.id,
    val totalSteps: Int = 0,
    val activeCaloriesKcal: Double = 0.0,
    val restingBpm: Int? = null,
    val avgBpm: Int? = null,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val hrvRmssdMs: Double? = null,
    val totalMonitoredMinutes: Int = 0,
    val relaxedZoneMinutes: Int = 0,
    val normalZoneMinutes: Int = 0,
    val elevatedTensionMinutes: Int = 0,
    val highLoadMinutes: Int = 0,
    val tensionEpisodeCount: Int = 0,
    val tensionMinutes: Int = 0,
    val peakTensionBpm: Int? = null,
    val rapidDeltaSpikeCount: Int = 0,
    val maxDeltaBpmPerMin: Int? = null,
    val activityOverloadCount: Int = 0,
    val avgRecoveryDropBpm: Int? = null,
    val sleepAwakeningCount: Int = 0,
    val recoveryScore: Int? = null,
    val hourlyBuckets: List<HourlyBiometricBucket> = emptyList(),
    val sourceDevices: List<String> = emptyList(),
) {
    val zoneMinutesSum: Int
        get() = relaxedZoneMinutes + normalZoneMinutes + elevatedTensionMinutes + highLoadMinutes
}

/**
 * Wearable Data Layer(`ChannelClient` / `MessageClient`)로 전송되는 GZIP 압축 전 배치 봉투(Envelope).
 */
data class BiometricSyncBatchEnvelope(
    val schemaVersion: Int = WearContractConstants.BIOMETRIC_SCHEMA_VERSION,
    val sentAtEpochMs: Long,
    val sourceNodeId: String,
    val deviceModel: String,
    val summaries: List<BiometricDailySummaryRecord>,
    val payloadSha256: String,
)
