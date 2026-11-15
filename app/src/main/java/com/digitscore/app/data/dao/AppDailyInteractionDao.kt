package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.digitscore.app.data.entity.AppDailyInteractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDailyInteractionDao {

    @Query("SELECT * FROM app_daily_interactions WHERE dateString = :dateString")
    suspend fun getByDate(dateString: String): List<AppDailyInteractionEntity>

    @Query("SELECT * FROM app_daily_interactions WHERE dateString = :dateString")
    fun observeByDate(dateString: String): Flow<List<AppDailyInteractionEntity>>

    @Query("SELECT * FROM app_daily_interactions WHERE dateString >= :startDateString AND dateString <= :endDateString ORDER BY dateString ASC")
    suspend fun getByDateRange(startDateString: String, endDateString: String): List<AppDailyInteractionEntity>

    @Query("SELECT * FROM app_daily_interactions WHERE dateString >= :startDateString AND dateString <= :endDateString ORDER BY dateString ASC")
    fun observeByDateRange(startDateString: String, endDateString: String): Flow<List<AppDailyInteractionEntity>>

    @Query("SELECT * FROM app_daily_interactions WHERE dateString = :dateString AND firstAfterUnlockCount > 0 ORDER BY firstAfterUnlockCount DESC LIMIT :limit")
    suspend fun getTopFirstAfterUnlock(dateString: String, limit: Int = 5): List<AppDailyInteractionEntity>

    @Query("SELECT * FROM app_daily_interactions WHERE dateString = :dateString AND notificationUnlockCount > 0 ORDER BY notificationUnlockCount DESC LIMIT :limit")
    suspend fun getTopNotificationUnlock(dateString: String, limit: Int = 5): List<AppDailyInteractionEntity>

    @Query("SELECT * FROM app_daily_interactions WHERE dateString = :dateString AND notificationCount > 0 ORDER BY notificationCount DESC LIMIT :limit")
    suspend fun getTopNotifications(dateString: String, limit: Int = 5): List<AppDailyInteractionEntity>

    @Query("SELECT * FROM app_daily_interactions ORDER BY dateString ASC, packageName ASC")
    suspend fun getAll(): List<AppDailyInteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(interactions: List<AppDailyInteractionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interaction: AppDailyInteractionEntity)

    @Query("DELETE FROM app_daily_interactions WHERE dateString = :dateString")
    suspend fun deleteDate(dateString: String): Int

    @Query("DELETE FROM app_daily_interactions WHERE dateString < :cutoffDateString")
    suspend fun deleteBefore(cutoffDateString: String): Int

    @Query("DELETE FROM app_daily_interactions")
    suspend fun deleteAll(): Int

    @Transaction
    suspend fun pruneBefore(cutoffDateString: String) {
        deleteBefore(cutoffDateString)
    }
}
