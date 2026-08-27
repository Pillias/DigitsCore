package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Query("SELECT * FROM daily_score_history ORDER BY dateString DESC")
    fun getAllScoreHistories(): Flow<List<DailyScoreHistoryEntity>>

    @Query("SELECT * FROM daily_score_history WHERE dateString = :dateString LIMIT 1")
    fun getScoreHistoryForDateFlow(dateString: String): Flow<DailyScoreHistoryEntity?>

    @Query("SELECT * FROM daily_score_history WHERE dateString = :dateString LIMIT 1")
    suspend fun getScoreHistoryForDate(dateString: String): DailyScoreHistoryEntity?

    @Query("SELECT * FROM daily_score_history ORDER BY dateString DESC LIMIT 7")
    fun getRecent7DaysHistories(): Flow<List<DailyScoreHistoryEntity>>

    @Query("SELECT * FROM daily_score_history ORDER BY dateString DESC LIMIT 30")
    fun getRecent30DaysHistories(): Flow<List<DailyScoreHistoryEntity>>

    @Query("SELECT * FROM daily_score_history ORDER BY dateString DESC LIMIT :limit")
    fun getRecentDaysHistories(limit: Int): Flow<List<DailyScoreHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateScoreHistory(history: DailyScoreHistoryEntity)
}
