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

    @Query("DELETE FROM daily_score_history")
    suspend fun deleteAllScoreHistories()

    /**
     * 구형 소급 로직이 OS 데이터가 없는 날을 100점/미사용 1,440분으로 만든 행만 제거합니다.
     * 실제 사용시간 또는 잠금 해제 기록이 있는 행은 대상이 아닙니다.
     */
    @Query(
        """DELETE FROM daily_score_history
           WHERE dateString >= :startDateString
             AND dateString < :endDateString
             AND finalScore = 100
             AND totalScreenTimeMinutes = 0
             AND distractingTimeMinutes = 0
             AND productiveTimeMinutes = 0
             AND idleMinutes = 1440
             AND unlockCount = 0"""
    )
    suspend fun deleteLegacyEmptyBackfills(startDateString: String, endDateString: String)
}
