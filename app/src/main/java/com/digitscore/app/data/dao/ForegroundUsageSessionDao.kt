package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForegroundUsageSessionDao {
    @Query("SELECT * FROM foreground_usage_sessions WHERE endTimeMillis > :start AND startTimeMillis < :end ORDER BY startTimeMillis")
    fun observeBetween(start: Long, end: Long): Flow<List<ForegroundUsageSessionEntity>>

    @Query("SELECT * FROM foreground_usage_sessions WHERE endTimeMillis > :startMillis ORDER BY startTimeMillis ASC")
    fun observeSince(startMillis: Long): Flow<List<ForegroundUsageSessionEntity>>

    @Query("SELECT * FROM foreground_usage_sessions WHERE endTimeMillis >= :startMillis ORDER BY startTimeMillis ASC")
    suspend fun getSince(startMillis: Long): List<ForegroundUsageSessionEntity>

    @Query(
        """SELECT * FROM foreground_usage_sessions
           WHERE endTimeMillis > :startMillis AND startTimeMillis < :endMillis
           ORDER BY startTimeMillis ASC"""
    )
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<ForegroundUsageSessionEntity>

    @Query("SELECT * FROM foreground_usage_sessions ORDER BY startTimeMillis ASC")
    suspend fun getAll(): List<ForegroundUsageSessionEntity>

    @Query("SELECT COALESCE(SUM(endTimeMillis - startTimeMillis), 0) FROM foreground_usage_sessions")
    suspend fun getTotalRecordedUsageMillis(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ForegroundUsageSessionEntity>)

    @Query("DELETE FROM foreground_usage_sessions WHERE dateString = :dateString")
    suspend fun deleteDate(dateString: String)

    @Query("DELETE FROM foreground_usage_sessions WHERE endTimeMillis < :cutoffMillis")
    suspend fun pruneBefore(cutoffMillis: Long)

    @Query("DELETE FROM foreground_usage_sessions")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceDay(dateString: String, records: List<ForegroundUsageSessionEntity>) {
        deleteDate(dateString)
        if (records.isNotEmpty()) insertAll(records)
    }
}
