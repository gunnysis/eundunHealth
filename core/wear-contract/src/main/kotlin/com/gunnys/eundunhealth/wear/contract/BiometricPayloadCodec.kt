package com.gunnys.eundunhealth.wear.contract

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 외부 라이브러리 의존성 없이 순수 JDK 17 API만으로 `BiometricSyncBatchEnvelope` 및
 * `BiometricDailySummaryRecord`를 직렬화·GZIP 압축·SHA-256 무결성 검증하는 코덱.
 */
object BiometricPayloadCodec {
    private const val RECORD_DELIMITER = "\n---RECORD---\n"
    private const val HOURLY_ITEM_DELIMITER = ";"
    private const val DEVICE_ITEM_DELIMITER = "|"

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun encodeBatchGzip(
        sentAtEpochMs: Long,
        sourceNodeId: String,
        deviceModel: String,
        summaries: List<BiometricDailySummaryRecord>,
        schemaVersion: Int = WearContractConstants.BIOMETRIC_SCHEMA_VERSION,
    ): ByteArray {
        val bodyText = summaries.joinToString(RECORD_DELIMITER) { encodeSummaryRecord(it) }
        val payloadSha256 = computeSha256Hex(bodyText.toByteArray(StandardCharsets.UTF_8))
        val header =
            buildString {
                append("schemaVersion=").append(schemaVersion).append('\n')
                append("sentAtEpochMs=").append(sentAtEpochMs).append('\n')
                append("sourceNodeId=").append(sanitizeSingleLine(sourceNodeId)).append('\n')
                append("deviceModel=").append(sanitizeSingleLine(deviceModel)).append('\n')
                append("payloadSha256=").append(payloadSha256).append('\n')
                append("===BODY===\n")
                append(bodyText)
            }
        val rawBytes = header.toByteArray(StandardCharsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(rawBytes)
        }
        return bos.toByteArray()
    }

    fun decodeBatchGzip(compressedBytes: ByteArray): BiometricSyncBatchEnvelope {
        val decompressed =
            GZIPInputStream(ByteArrayInputStream(compressedBytes)).use { gzip ->
                gzip.readBytes().toString(StandardCharsets.UTF_8)
            }
        val marker = "\n===BODY===\n"
        val splitIdx = decompressed.indexOf(marker)
        require(splitIdx >= 0) { "Malformed biometric batch envelope: missing BODY marker" }

        val headerMap = parseKeyValueLines(decompressed.substring(0, splitIdx))
        val bodyText = decompressed.substring(splitIdx + marker.length)

        val schemaVersion = headerMap.intOrDefault("schemaVersion", WearContractConstants.BIOMETRIC_SCHEMA_VERSION)
        val sentAtEpochMs =
            requireNotNull(headerMap["sentAtEpochMs"]?.toLongOrNull()) {
                "Missing sentAtEpochMs in envelope"
            }
        val sourceNodeId = headerMap["sourceNodeId"].orEmpty()
        val deviceModel = headerMap["deviceModel"].orEmpty()
        val expectedSha256 =
            requireNotNull(headerMap["payloadSha256"]) {
                "Missing payloadSha256 in envelope"
            }

        val actualSha256 = computeSha256Hex(bodyText.toByteArray(StandardCharsets.UTF_8))
        require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
            "Biometric batch SHA-256 mismatch: expected=$expectedSha256 actual=$actualSha256"
        }

        val summaries =
            if (bodyText.isBlank()) {
                emptyList()
            } else {
                bodyText.split(RECORD_DELIMITER).map { decodeSummaryRecord(it) }
            }

        return BiometricSyncBatchEnvelope(
            schemaVersion = schemaVersion,
            sentAtEpochMs = sentAtEpochMs,
            sourceNodeId = sourceNodeId,
            deviceModel = deviceModel,
            summaries = summaries,
            payloadSha256 = expectedSha256,
        )
    }

    fun encodeSummaryRecord(record: BiometricDailySummaryRecord): String {
        val hourlyEncoded =
            record.hourlyBuckets.joinToString(HOURLY_ITEM_DELIMITER) {
                "${it.hour}:${it.avgBpm}:${it.maxBpm}:${it.dominantContext.name}:${it.monitoredMinutes}"
            }
        val devicesEncoded =
            record.sourceDevices.joinToString(DEVICE_ITEM_DELIMITER) { sanitizeToken(it) }

        return buildString {
            append("recordDate=").append(sanitizeSingleLine(record.recordDate)).append('\n')
            append("capturedAtEpochMs=").append(record.capturedAtEpochMs).append('\n')
            append("zoneId=").append(sanitizeSingleLine(record.zoneId)).append('\n')
            append("totalSteps=").append(record.totalSteps).append('\n')
            append("activeCaloriesKcal=").append(record.activeCaloriesKcal).append('\n')
            append("restingBpm=").append(record.restingBpm ?: "").append('\n')
            append("avgBpm=").append(record.avgBpm ?: "").append('\n')
            append("minBpm=").append(record.minBpm ?: "").append('\n')
            append("maxBpm=").append(record.maxBpm ?: "").append('\n')
            append("hrvRmssdMs=").append(record.hrvRmssdMs ?: "").append('\n')
            append("totalMonitoredMinutes=").append(record.totalMonitoredMinutes).append('\n')
            append("relaxedZoneMinutes=").append(record.relaxedZoneMinutes).append('\n')
            append("normalZoneMinutes=").append(record.normalZoneMinutes).append('\n')
            append("elevatedTensionMinutes=").append(record.elevatedTensionMinutes).append('\n')
            append("highLoadMinutes=").append(record.highLoadMinutes).append('\n')
            append("tensionEpisodeCount=").append(record.tensionEpisodeCount).append('\n')
            append("tensionMinutes=").append(record.tensionMinutes).append('\n')
            append("peakTensionBpm=").append(record.peakTensionBpm ?: "").append('\n')
            append("rapidDeltaSpikeCount=").append(record.rapidDeltaSpikeCount).append('\n')
            append("maxDeltaBpmPerMin=").append(record.maxDeltaBpmPerMin ?: "").append('\n')
            append("activityOverloadCount=").append(record.activityOverloadCount).append('\n')
            append("avgRecoveryDropBpm=").append(record.avgRecoveryDropBpm ?: "").append('\n')
            append("sleepAwakeningCount=").append(record.sleepAwakeningCount).append('\n')
            append("recoveryScore=").append(record.recoveryScore ?: "").append('\n')
            append("hourlyBuckets=").append(hourlyEncoded).append('\n')
            append("sourceDevices=").append(devicesEncoded)
        }
    }

    fun decodeSummaryRecord(serialized: String): BiometricDailySummaryRecord {
        val map = parseKeyValueLines(serialized)
        val recordDate =
            requireNotNull(map["recordDate"]?.takeIf { it.isNotBlank() }) {
                "Missing recordDate in summary record"
            }
        val capturedAtEpochMs =
            requireNotNull(map["capturedAtEpochMs"]?.toLongOrNull()) {
                "Missing capturedAtEpochMs in summary record"
            }

        return BiometricDailySummaryRecord(
            recordDate = recordDate,
            capturedAtEpochMs = capturedAtEpochMs,
            zoneId = map.nonBlankOrDefault("zoneId", KST_ZONE.id),
            totalSteps = map.intOrDefault("totalSteps", 0),
            activeCaloriesKcal = map.doubleOrDefault("activeCaloriesKcal", 0.0),
            restingBpm = map.intOrNull("restingBpm"),
            avgBpm = map.intOrNull("avgBpm"),
            minBpm = map.intOrNull("minBpm"),
            maxBpm = map.intOrNull("maxBpm"),
            hrvRmssdMs = map.doubleOrNull("hrvRmssdMs"),
            totalMonitoredMinutes = map.intOrDefault("totalMonitoredMinutes", 0),
            relaxedZoneMinutes = map.intOrDefault("relaxedZoneMinutes", 0),
            normalZoneMinutes = map.intOrDefault("normalZoneMinutes", 0),
            elevatedTensionMinutes = map.intOrDefault("elevatedTensionMinutes", 0),
            highLoadMinutes = map.intOrDefault("highLoadMinutes", 0),
            tensionEpisodeCount = map.intOrDefault("tensionEpisodeCount", 0),
            tensionMinutes = map.intOrDefault("tensionMinutes", 0),
            peakTensionBpm = map.intOrNull("peakTensionBpm"),
            rapidDeltaSpikeCount = map.intOrDefault("rapidDeltaSpikeCount", 0),
            maxDeltaBpmPerMin = map.intOrNull("maxDeltaBpmPerMin"),
            activityOverloadCount = map.intOrDefault("activityOverloadCount", 0),
            avgRecoveryDropBpm = map.intOrNull("avgRecoveryDropBpm"),
            sleepAwakeningCount = map.intOrDefault("sleepAwakeningCount", 0),
            recoveryScore = map.intOrNull("recoveryScore"),
            hourlyBuckets = decodeHourlyBuckets(map["hourlyBuckets"]),
            sourceDevices = decodeSourceDevices(map["sourceDevices"]),
        )
    }

    /**
     * 백엔드(`biometric_daily_summaries`) 및 모바일 Room 저장용 JSON 배열 문자열 변환 헬퍼.
     */
    fun encodeHourlyBucketsJson(buckets: List<HourlyBiometricBucket>): String {
        if (buckets.isEmpty()) return "[]"
        return buckets.joinToString(prefix = "[", postfix = "]", separator = ",") { bucket ->
            val ctx = bucket.dominantContext.name
            buildString {
                append("{\"hour\":").append(bucket.hour)
                append(",\"avgBpm\":").append(bucket.avgBpm)
                append(",\"maxBpm\":").append(bucket.maxBpm)
                append(",\"dominantContext\":\"").append(ctx).append('"')
                append(",\"monitoredMinutes\":").append(bucket.monitoredMinutes).append('}')
            }
        }
    }

    fun encodeSourceDevicesJson(devices: List<String>): String {
        if (devices.isEmpty()) return "[]"
        return devices.distinct().joinToString(prefix = "[", postfix = "]", separator = ",") {
            "\"${escapeJsonString(it)}\""
        }
    }

    private fun parseKeyValueLines(raw: String): Map<String, String> = raw
        .lines()
        .mapNotNull { line ->
            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) null else line.substring(0, eqIdx) to line.substring(eqIdx + 1)
        }.toMap()

    private fun decodeHourlyBuckets(raw: String?): List<HourlyBiometricBucket> = raw
        ?.takeIf { it.isNotBlank() }
        ?.split(HOURLY_ITEM_DELIMITER)
        ?.mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size < 5) {
                null
            } else {
                HourlyBiometricBucket(
                    hour = parts[0].toInt(),
                    avgBpm = parts[1].toInt(),
                    maxBpm = parts[2].toInt(),
                    dominantContext = WearActivityContext.fromWireName(parts[3]),
                    monitoredMinutes = parts[4].toInt(),
                )
            }
        } ?: emptyList()

    private fun decodeSourceDevices(raw: String?): List<String> = raw
        ?.takeIf { it.isNotBlank() }
        ?.split(DEVICE_ITEM_DELIMITER)
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    private fun Map<String, String>.intOrNull(key: String): Int? = this[key]?.toIntOrNull()

    private fun Map<String, String>.intOrDefault(key: String, default: Int): Int = this[key]?.toIntOrNull() ?: default

    private fun Map<String, String>.doubleOrNull(key: String): Double? = this[key]?.toDoubleOrNull()

    private fun Map<String, String>.doubleOrDefault(key: String, default: Double): Double = this[key]?.toDoubleOrNull() ?: default

    private fun Map<String, String>.nonBlankOrDefault(key: String, fallback: String): String {
        val v = this[key]
        return if (v.isNullOrBlank()) fallback else v
    }

    private fun sanitizeSingleLine(value: String): String = value.replace('\r', ' ').replace('\n', ' ').trim()

    private fun sanitizeToken(value: String): String {
        val clean = sanitizeSingleLine(value)
        return clean.replace(DEVICE_ITEM_DELIMITER, "_").replace(RECORD_DELIMITER, "_")
    }

    private fun escapeJsonString(value: String): String = sanitizeSingleLine(value).replace("\\", "\\\\").replace("\"", "\\\"")
}
