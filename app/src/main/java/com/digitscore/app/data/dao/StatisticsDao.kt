package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digitscore.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StatisticsDao {
    @Query("SELECT * FROM usage_hourly WHERE hour >= :start AND hour < :end ORDER BY hour")
    fun observeUsage(start: Long, end: Long): Flow<List<UsageHourEntity>>
    @Query("SELECT * FROM score_hourly WHERE hour >= :start AND hour < :end ORDER BY hour, firstAt")
    fun observeScores(start: Long, end: Long): Flow<List<ScoreHourEntity>>
    @Query("SELECT * FROM score_impact_hourly WHERE hour >= :start AND hour < :end ORDER BY hour")
    fun observeImpacts(start: Long, end: Long): Flow<List<ScoreImpactHourEntity>>
    @Query("SELECT * FROM usage_hourly ORDER BY hour")
    suspend fun allUsage(): List<UsageHourEntity>
    @Query("SELECT * FROM usage_hourly WHERE hour >= :start AND packageName = ''")
    suspend fun deviceUsageSince(start: Long): List<UsageHourEntity>
    @Query("SELECT * FROM score_hourly ORDER BY hour")
    suspend fun allScores(): List<ScoreHourEntity>
    @Query("SELECT * FROM score_impact_hourly ORDER BY hour")
    suspend fun allImpacts(): List<ScoreImpactHourEntity>
    @Query("SELECT * FROM score_hourly WHERE hour = :hour AND model = :model")
    suspend fun score(hour: Long, model: Int): ScoreHourEntity?
    @Query("SELECT * FROM score_impact_hourly WHERE hour = :hour AND packageName = :pkg")
    suspend fun impact(hour: Long, pkg: String): ScoreImpactHourEntity?
    @Query("SELECT * FROM statistics_state WHERE id = 1")
    suspend fun state(): StatisticsStateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putUsage(rows: List<UsageHourEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putScores(rows: List<ScoreHourEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putImpacts(rows: List<ScoreImpactHourEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreUsage(rows: List<UsageHourEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreScores(rows: List<ScoreHourEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreImpacts(rows: List<ScoreImpactHourEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(row: StatisticsStateEntity)
    @Query("DELETE FROM usage_hourly WHERE hour >= :start AND hour < :end")
    suspend fun deleteUsageRange(start: Long, end: Long)
    @Query("DELETE FROM usage_hourly WHERE hour < :before")
    suspend fun pruneUsage(before: Long)
    @Query("DELETE FROM score_hourly WHERE hour < :before")
    suspend fun pruneScores(before: Long)
    @Query("DELETE FROM score_impact_hourly WHERE hour < :before")
    suspend fun pruneImpacts(before: Long)
    @Query("DELETE FROM usage_hourly") suspend fun deleteUsage()
    @Query("DELETE FROM score_hourly") suspend fun deleteScores()
    @Query("DELETE FROM score_impact_hourly") suspend fun deleteImpacts()
    @Query("DELETE FROM statistics_state") suspend fun deleteState()
}
