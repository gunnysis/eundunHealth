package com.gunnys.eundunhealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gunnys.eundunhealth.data.local.entity.SmileSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmileSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(entity: SmileSessionEntity): Long

    @Query("SELECT * FROM smile_sessions WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getById(id: String, userId: String): SmileSessionEntity?

    @Query("SELECT * FROM smile_sessions WHERE userId = :userId ORDER BY occurredAtUtc DESC")
    fun observeSessionsByUser(userId: String): Flow<List<SmileSessionEntity>>

    @Query("SELECT * FROM smile_sessions WHERE userId = :userId ORDER BY occurredAtUtc DESC")
    suspend fun getSessionsByUser(userId: String): List<SmileSessionEntity>

    @Query(
        """
        SELECT * FROM smile_sessions
        WHERE userId = :userId AND syncStatus = 'PENDING'
        ORDER BY occurredAtUtc ASC
        LIMIT :limit
        """,
    )
    suspend fun getOldestPendingByUser(userId: String, limit: Int = 10): List<SmileSessionEntity>

    @Query(
        """
        UPDATE smile_sessions
        SET syncStatus = :status, syncedAtUtc = :syncedAtUtc, lastSyncErrorCode = :errorCode
        WHERE id = :id AND userId = :userId
        """,
    )
    suspend fun updateSyncStatus(
        id: String,
        userId: String,
        status: String,
        syncedAtUtc: Long?,
        errorCode: Int?,
    )

    @Query(
        """
        UPDATE smile_sessions
        SET syncStatus = 'PENDING', lastSyncErrorCode = NULL
        WHERE userId = :userId AND syncStatus = 'BLOCKED'
        """,
    )
    suspend fun resetBlockedToPending(userId: String): Int

    @Query("DELETE FROM smile_sessions WHERE userId = :userId")
    suspend fun deleteAllByUser(userId: String)

    @Query("DELETE FROM smile_sessions")
    suspend fun clearAll()
}
