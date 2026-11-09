package com.gunnys.eundunhealth.wear.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import com.gunnys.eundunhealth.wear.contract.BiometricDailySummaryRecord
import com.gunnys.eundunhealth.wear.contract.BiometricPayloadCodec
import com.gunnys.eundunhealth.wear.contract.ContinuousBiometricAnalyzer
import com.gunnys.eundunhealth.wear.contract.KST_ZONE
import com.gunnys.eundunhealth.wear.contract.MinuteBiometricBucket
import com.gunnys.eundunhealth.wear.contract.WearActivityContext
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 워치 온디바이스 SQLite 저장소.
 * - `minute_biometric_buckets`: 1분 단위 전 구간(`PASSIVE`, `EXERCISE`, `ASLEEP`, `UNKNOWN`) 시계열 버킷 (7일 보관 후 자동 파기)
 * - `daily_biometric_summaries`: `ContinuousBiometricAnalyzer`가 산출한 5차원 일일 요약 레코드 (30일 보관 및 폰 전송 상태 추적)
 * - `runtime_state`: 마지막 감지된 `WearActivityContext` 및 당일 누적 걸음 수/활동 칼로리 메타데이터
 */
class WearBiometricDatabase private constructor(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS minute_biometric_buckets (
                epoch_minute INTEGER PRIMARY KEY,
                record_date TEXT NOT NULL,
                avg_bpm INTEGER NOT NULL,
                max_bpm INTEGER NOT NULL,
                min_bpm INTEGER NOT NULL,
                steps_delta INTEGER NOT NULL DEFAULT 0,
                activity_context TEXT NOT NULL DEFAULT 'UNKNOWN'
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS ix_minute_buckets_date ON minute_biometric_buckets(record_date)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_biometric_summaries (
                record_date TEXT PRIMARY KEY,
                serialized_record TEXT NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL,
                synced_at_epoch_ms INTEGER NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_activity_totals (
                record_date TEXT PRIMARY KEY,
                total_steps INTEGER NOT NULL DEFAULT 0,
                active_calories_kcal REAL NOT NULL DEFAULT 0.0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS runtime_meta (
                meta_key TEXT PRIMARY KEY,
                meta_value TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    @Synchronized
    fun updateCurrentActivityContext(context: WearActivityContext, timestampEpochMs: Long = System.currentTimeMillis()) {
        writableDatabase.beginTransaction()
        try {
            upsertMeta(writableDatabase, KEY_CURRENT_ACTIVITY_CONTEXT, context.name)
            upsertMeta(writableDatabase, KEY_LAST_ACTIVITY_EPOCH_MS, timestampEpochMs.toString())
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun getCurrentActivityContext(): WearActivityContext {
        val raw = queryMeta(readableDatabase, KEY_CURRENT_ACTIVITY_CONTEXT)
        return WearActivityContext.fromWireName(raw)
    }

    @Synchronized
    fun recordHeartRateSample(
        bpm: Int,
        sampleEpochMs: Long = System.currentTimeMillis(),
        overrideContext: WearActivityContext? = null,
    ): BiometricDailySummaryRecord? = recordHeartRateSamplesBatch(
        samples = listOf(bpm to sampleEpochMs),
        overrideContext = overrideContext,
    )

    @Synchronized
    fun recordHeartRateSamplesBatch(
        samples: List<Pair<Int, Long>>,
        overrideContext: WearActivityContext? = null,
    ): BiometricDailySummaryRecord? {
        if (samples.isEmpty()) return null
        val activeContext = overrideContext ?: getCurrentActivityContext()
        val affectedDates = linkedSetOf<String>()
        var latestBpm = 0
        var latestEpochMs = 0L

        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((rawBpm, sampleEpochMs) in samples) {
                val clampedBpm = rawBpm.coerceIn(30, 220)
                val recordDate = Instant.ofEpochMilli(sampleEpochMs).atZone(KST_ZONE).toLocalDate().toString()
                affectedDates.add(recordDate)
                if (sampleEpochMs >= latestEpochMs) {
                    latestEpochMs = sampleEpochMs
                    latestBpm = clampedBpm
                }
                upsertMinuteHeartRateSample(
                    db = db,
                    epochMinute = sampleEpochMs / 60_000L,
                    recordDate = recordDate,
                    clampedBpm = clampedBpm,
                    activeContext = activeContext,
                )
            }

            upsertMeta(db, KEY_LAST_HEART_RATE_BPM, latestBpm.toString())
            upsertMeta(db, KEY_LAST_SAMPLE_EPOCH_MS, latestEpochMs.toString())
            pruneExpiredRows(db, latestEpochMs)

            var latestSummary: BiometricDailySummaryRecord? = null
            for (date in affectedDates) {
                latestSummary = recomputeAndPersistDailySummary(db, date, latestEpochMs)
            }
            db.setTransactionSuccessful()
            return latestSummary
        } finally {
            db.endTransaction()
        }
    }

    private fun upsertMinuteHeartRateSample(
        db: SQLiteDatabase,
        epochMinute: Long,
        recordDate: String,
        clampedBpm: Int,
        activeContext: WearActivityContext,
    ) {
        val existing = queryMinuteBucket(db, epochMinute)
        if (existing == null) {
            val cv =
                ContentValues().apply {
                    put("epoch_minute", epochMinute)
                    put("record_date", recordDate)
                    put("avg_bpm", clampedBpm)
                    put("max_bpm", clampedBpm)
                    put("min_bpm", clampedBpm)
                    put("steps_delta", 0)
                    put("activity_context", activeContext.name)
                }
            db.insertWithOnConflict("minute_biometric_buckets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            return
        }
        val mergedAvg = ((existing.avgBpm + clampedBpm) / 2.0).roundToInt().coerceIn(30, 220)
        val mergedMax = max(existing.maxBpm, clampedBpm)
        val mergedMin = min(existing.minBpm, clampedBpm)
        val resolvedContext =
            if (activeContext != WearActivityContext.UNKNOWN) activeContext else existing.activityContext
        val cv =
            ContentValues().apply {
                put("avg_bpm", mergedAvg)
                put("max_bpm", max(mergedAvg, mergedMax))
                put("min_bpm", min(mergedAvg, mergedMin))
                put("activity_context", resolvedContext.name)
            }
        db.update(
            "minute_biometric_buckets",
            cv,
            "epoch_minute = ?",
            arrayOf(epochMinute.toString()),
        )
    }

    @Synchronized
    fun updateDailyActivityTotals(
        totalSteps: Int?,
        activeCaloriesKcal: Double?,
        sampleEpochMs: Long = System.currentTimeMillis(),
    ): BiometricDailySummaryRecord {
        val recordDate = Instant.ofEpochMilli(sampleEpochMs).atZone(KST_ZONE).toLocalDate().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val (prevSteps, prevCalories) = queryDailyTotals(db, recordDate)
            val newSteps = if (totalSteps != null) max(prevSteps, totalSteps.coerceAtLeast(0)) else prevSteps
            val newCalories =
                if (activeCaloriesKcal != null) max(prevCalories, activeCaloriesKcal.coerceAtLeast(0.0)) else prevCalories

            val rawDelta = newSteps - prevSteps
            val stepsDelta =
                if (prevSteps == 0 && rawDelta > 300) {
                    // 앱 최초 기동 시 당일 누적 걸음 수(예: 5211보)가 한 번에 들어오는 경우 단일 분 버킷 스파이크 방지
                    0
                } else {
                    rawDelta.coerceIn(0, 300)
                }
            if (stepsDelta > 0) {
                addStepsDeltaToMinuteBucket(db, recordDate, sampleEpochMs / 60_000L, stepsDelta)
            }

            val cv =
                ContentValues().apply {
                    put("record_date", recordDate)
                    put("total_steps", newSteps)
                    put("active_calories_kcal", newCalories)
                }
            db.insertWithOnConflict("daily_activity_totals", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            val summary = recomputeAndPersistDailySummary(db, recordDate, sampleEpochMs)
            db.setTransactionSuccessful()
            return summary
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getTodaySummary(nowEpochMs: Long = System.currentTimeMillis()): BiometricDailySummaryRecord {
        val todayDate = Instant.ofEpochMilli(nowEpochMs).atZone(KST_ZONE).toLocalDate().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val summary = recomputeAndPersistDailySummary(db, todayDate, nowEpochMs)
            db.setTransactionSuccessful()
            return summary
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getUnsyncedOrRecentSummaries(limitDays: Int = 14): List<BiometricDailySummaryRecord> {
        val out = mutableListOf<BiometricDailySummaryRecord>()
        readableDatabase
            .rawQuery(
                """
                SELECT serialized_record
                FROM daily_biometric_summaries
                ORDER BY record_date DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(limitDays.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(0)
                    runCatching { BiometricPayloadCodec.decodeSummaryRecord(raw) }
                        .getOrNull()
                        ?.let { out.add(it) }
                }
            }
        return out
    }

    @Synchronized
    fun markSummariesSynced(recordDates: Collection<String>, syncedAtEpochMs: Long = System.currentTimeMillis()) {
        if (recordDates.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (date in recordDates) {
                val cv = ContentValues().apply { put("synced_at_epoch_ms", syncedAtEpochMs) }
                db.update("daily_biometric_summaries", cv, "record_date = ?", arrayOf(date))
            }
            upsertMeta(db, KEY_LAST_SYNC_EPOCH_MS, syncedAtEpochMs.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getLastRecordedHeartRateBpm(): Int? = queryMeta(readableDatabase, KEY_LAST_HEART_RATE_BPM)?.toIntOrNull()

    @Synchronized
    fun getLastSyncEpochMs(): Long? = queryMeta(readableDatabase, KEY_LAST_SYNC_EPOCH_MS)?.toLongOrNull()

    private fun addStepsDeltaToMinuteBucket(
        db: SQLiteDatabase,
        recordDate: String,
        epochMinute: Long,
        stepsDelta: Int,
    ) {
        val targetBucket =
            queryMinuteBucket(db, epochMinute)
                ?: queryRecentMinuteBucketForDate(db, recordDate, epochMinute - 5L, epochMinute)
                ?: return
        val cv =
            ContentValues().apply {
                put("steps_delta", targetBucket.stepsDelta + stepsDelta)
            }
        db.update(
            "minute_biometric_buckets",
            cv,
            "epoch_minute = ?",
            arrayOf(targetBucket.epochMinute.toString()),
        )
    }

    private fun queryRecentMinuteBucketForDate(
        db: SQLiteDatabase,
        recordDate: String,
        minEpochMinute: Long,
        maxEpochMinute: Long,
    ): MinuteBiometricBucket? {
        db
            .rawQuery(
                """
                SELECT epoch_minute, avg_bpm, max_bpm, min_bpm, steps_delta, activity_context
                FROM minute_biometric_buckets
                WHERE record_date = ? AND epoch_minute BETWEEN ? AND ?
                ORDER BY epoch_minute DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(recordDate, minEpochMinute.toString(), maxEpochMinute.toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return MinuteBiometricBucket(
                    epochMinute = cursor.getLong(0),
                    avgBpm = cursor.getInt(1),
                    maxBpm = cursor.getInt(2),
                    minBpm = cursor.getInt(3),
                    stepsDelta = cursor.getInt(4),
                    activityContext = WearActivityContext.fromWireName(cursor.getString(5)),
                )
            }
    }

    private fun recomputeAndPersistDailySummary(
        db: SQLiteDatabase,
        recordDate: String,
        capturedAtEpochMs: Long,
    ): BiometricDailySummaryRecord {
        val buckets = queryBucketsForDate(db, recordDate)
        val (totalSteps, activeCalories) = queryDailyTotals(db, recordDate)
        val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Wear OS Watch" }

        val summary =
            ContinuousBiometricAnalyzer.analyzeDay(
                recordDate = recordDate,
                capturedAtEpochMs = capturedAtEpochMs,
                buckets = buckets,
                totalSteps = totalSteps,
                activeCaloriesKcal = activeCalories,
                sourceDevices = listOf(deviceLabel),
                zoneId = KST_ZONE,
            )

        val serialized = BiometricPayloadCodec.encodeSummaryRecord(summary)
        val cv =
            ContentValues().apply {
                put("record_date", recordDate)
                put("serialized_record", serialized)
                put("updated_at_epoch_ms", capturedAtEpochMs)
                putNull("synced_at_epoch_ms")
            }
        db.insertWithOnConflict("daily_biometric_summaries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return summary
    }

    private fun queryMinuteBucket(db: SQLiteDatabase, epochMinute: Long): MinuteBiometricBucket? {
        db
            .rawQuery(
                """
                SELECT epoch_minute, avg_bpm, max_bpm, min_bpm, steps_delta, activity_context
                FROM minute_biometric_buckets
                WHERE epoch_minute = ?
                """.trimIndent(),
                arrayOf(epochMinute.toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return MinuteBiometricBucket(
                    epochMinute = cursor.getLong(0),
                    avgBpm = cursor.getInt(1),
                    maxBpm = cursor.getInt(2),
                    minBpm = cursor.getInt(3),
                    stepsDelta = cursor.getInt(4),
                    activityContext = WearActivityContext.fromWireName(cursor.getString(5)),
                )
            }
    }

    private fun queryBucketsForDate(db: SQLiteDatabase, recordDate: String): List<MinuteBiometricBucket> {
        val list = mutableListOf<MinuteBiometricBucket>()
        db
            .rawQuery(
                """
                SELECT epoch_minute, avg_bpm, max_bpm, min_bpm, steps_delta, activity_context
                FROM minute_biometric_buckets
                WHERE record_date = ?
                ORDER BY epoch_minute ASC
                """.trimIndent(),
                arrayOf(recordDate),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val avg = cursor.getInt(1).coerceIn(25, 260)
                    val maxVal = max(avg, cursor.getInt(2).coerceIn(25, 260))
                    val minVal = min(avg, cursor.getInt(3).coerceIn(25, 260))
                    list.add(
                        MinuteBiometricBucket(
                            epochMinute = cursor.getLong(0),
                            avgBpm = avg,
                            maxBpm = maxVal,
                            minBpm = minVal,
                            stepsDelta = cursor.getInt(4).coerceAtLeast(0),
                            activityContext = WearActivityContext.fromWireName(cursor.getString(5)),
                        ),
                    )
                }
            }
        return list
    }

    private fun queryDailyTotals(db: SQLiteDatabase, recordDate: String): Pair<Int, Double> {
        db
            .rawQuery(
                "SELECT total_steps, active_calories_kcal FROM daily_activity_totals WHERE record_date = ?",
                arrayOf(recordDate),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return 0 to 0.0
                return cursor.getInt(0) to cursor.getDouble(1)
            }
    }

    private fun pruneExpiredRows(db: SQLiteDatabase, nowEpochMs: Long) {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(KST_ZONE).toLocalDate()
        val rawCutoffDate = today.minusDays(WearContractConstants.RAW_BUCKET_RETENTION_DAYS).toString()
        val summaryCutoffDate = today.minusDays(WearContractConstants.DAILY_SUMMARY_RETENTION_DAYS).toString()

        db.delete("minute_biometric_buckets", "record_date < ?", arrayOf(rawCutoffDate))
        db.delete("daily_biometric_summaries", "record_date < ?", arrayOf(summaryCutoffDate))
        db.delete("daily_activity_totals", "record_date < ?", arrayOf(summaryCutoffDate))
    }

    private fun upsertMeta(db: SQLiteDatabase, key: String, value: String) {
        val cv =
            ContentValues().apply {
                put("meta_key", key)
                put("meta_value", value)
            }
        db.insertWithOnConflict("runtime_meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun queryMeta(db: SQLiteDatabase, key: String): String? {
        db
            .rawQuery("SELECT meta_value FROM runtime_meta WHERE meta_key = ?", arrayOf(key))
            .use { cursor ->
                return if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    companion object {
        private const val DB_NAME = "wear_biometrics.db"
        private const val DB_VERSION = 1

        private const val KEY_CURRENT_ACTIVITY_CONTEXT = "current_activity_context"
        private const val KEY_LAST_ACTIVITY_EPOCH_MS = "last_activity_epoch_ms"
        private const val KEY_LAST_HEART_RATE_BPM = "last_heart_rate_bpm"
        private const val KEY_LAST_SAMPLE_EPOCH_MS = "last_sample_epoch_ms"
        private const val KEY_LAST_SYNC_EPOCH_MS = "last_sync_epoch_ms"

        @Volatile
        private var instance: WearBiometricDatabase? = null

        fun getInstance(context: Context): WearBiometricDatabase = instance ?: synchronized(this) {
            instance ?: WearBiometricDatabase(context).also { instance = it }
        }

        internal fun todayDateString(): String = LocalDate.now(KST_ZONE).toString()
    }
}
