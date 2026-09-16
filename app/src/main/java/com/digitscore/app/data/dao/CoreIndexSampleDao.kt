package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digitscore.app.data.entity.CoreIndexSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoreIndexSampleDao {
    @Query("SELECT * FROM core_index_samples WHERE timestampMillis >= :startMillis ORDER BY timestampMillis ASC")
    fun observeSince(startMillis: Long): Flow<List<CoreIndexSampleEntity>>

    @Query("SELECT * FROM core_index_samples WHERE dateString = :dateString ORDER BY timestampMillis ASC")
    fun observeForDate(dateString: String): Flow<List<CoreIndexSampleEntity>>

    @Query("SELECT * FROM core_index_samples ORDER BY timestampMillis ASC")
    suspend fun getAll(): List<CoreIndexSampleEntity>

    @Query("SELECT * FROM core_index_samples WHERE timestampMillis < :beforeMillis ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatestBefore(beforeMillis: Long): CoreIndexSampleEntity?

    @Query(
        """SELECT * FROM core_index_samples
           WHERE timestampMillis BETWEEN :rangeStartMillis AND :rangeEndMillis
           ORDER BY ABS(timestampMillis - :targetMillis) ASC
           LIMIT 1"""
    )
    suspend fun getClosestTo(
        targetMillis: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): CoreIndexSampleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(sample: CoreIndexSampleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<CoreIndexSampleEntity>)

    @Query("DELETE FROM core_index_samples WHERE timestampMillis < :cutoffMillis")
    suspend fun pruneBefore(cutoffMillis: Long)

    @Query("DELETE FROM core_index_samples")
    suspend fun deleteAll()
}
