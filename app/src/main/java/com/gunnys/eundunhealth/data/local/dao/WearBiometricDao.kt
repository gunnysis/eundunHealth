package com.gunnys.eundunhealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gunnys.eundunhealth.data.local.entity.WearBiometricSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WearBiometricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WearBiometricSummaryEntity>)

    @Query(
        """
        SELECT * FROM wear_biometric_daily_summaries
        WHERE userId = :userId
        ORDER BY recordDate DESC
        LIMIT :limit
        """,
    )
    fun observeRecentByUser(userId: String, limit: Int): Flow<List<WearBiometricSummaryEntity>>

    @Query(
        """
        SELECT * FROM wear_biometric_daily_summaries
        WHERE userId = :userId AND syncStatus = 'PENDING'
        ORDER BY recordDate ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingByUser(userId: String, limit: Int): List<WearBiometricSummaryEntity>

    @Query(
        """
        UPDATE wear_biometric_daily_summaries
        SET syncStatus = :status, syncedAtUtc = :syncedAtUtc
        WHERE userId = :userId AND recordDate IN (:recordDates)
        """,
    )
    suspend fun updateSyncStatus(
        userId: String,
        recordDates: List<String>,
        status: String,
        syncedAtUtc: Long?,
    )

    @Query("SELECT COUNT(*) FROM wear_biometric_daily_summaries WHERE userId = :userId")
    suspend fun countByUser(userId: String): Int

    @Query("DELETE FROM wear_biometric_daily_summaries WHERE userId = :userId")
    suspend fun deleteAllByUser(userId: String)

    @Query("DELETE FROM wear_biometric_daily_summaries")
    suspend fun clearAll()
}
