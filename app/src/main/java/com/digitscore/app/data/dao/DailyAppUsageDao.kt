package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity

@Dao
interface DailyAppUsageDao {
    @Query(
        """SELECT * FROM daily_app_usage
           WHERE packageName = :packageName AND dateString >= :startDateString
           ORDER BY dateString ASC"""
    )
    suspend fun getForPackageSince(
        packageName: String,
        startDateString: String
    ): List<DailyAppUsageEntity>

    @Query("SELECT * FROM daily_app_usage ORDER BY dateString ASC, packageName ASC")
    suspend fun getAll(): List<DailyAppUsageEntity>

    @Query(
        """SELECT * FROM daily_usage_coverage
           WHERE dateString >= :startDateString
           ORDER BY dateString ASC"""
    )
    suspend fun getCoverageSince(startDateString: String): List<DailyUsageCoverageEntity>

    @Query("SELECT * FROM daily_usage_coverage ORDER BY dateString ASC")
    suspend fun getAllCoverage(): List<DailyUsageCoverageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<DailyAppUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoverage(coverage: DailyUsageCoverageEntity)

    @Query("DELETE FROM daily_app_usage WHERE dateString = :dateString")
    suspend fun deleteDate(dateString: String)

    @Query("UPDATE daily_usage_coverage SET isComplete = 1 WHERE dateString < :todayDateString")
    suspend fun markPastDaysComplete(todayDateString: String)

    @Query("DELETE FROM daily_app_usage WHERE dateString < :cutoffDateString")
    suspend fun deleteUsageBefore(cutoffDateString: String)

    @Query("DELETE FROM daily_usage_coverage WHERE dateString < :cutoffDateString")
    suspend fun deleteCoverageBefore(cutoffDateString: String)

    @Transaction
    suspend fun replaceDay(
        dateString: String,
        records: List<DailyAppUsageEntity>,
        coverage: DailyUsageCoverageEntity
    ) {
        deleteDate(dateString)
        if (records.isNotEmpty()) insertAll(records)
        insertCoverage(coverage)
    }

    @Transaction
    suspend fun pruneBefore(cutoffDateString: String) {
        deleteUsageBefore(cutoffDateString)
        deleteCoverageBefore(cutoffDateString)
    }
}
