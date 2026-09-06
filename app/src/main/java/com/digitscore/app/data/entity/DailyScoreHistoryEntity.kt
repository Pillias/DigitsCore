package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일자별 레거시 점수 및 사용 통계 엔티티
 */
@Entity(tableName = "daily_score_history")
data class DailyScoreHistoryEntity(
    @PrimaryKey
    val dateString: String, // Format: YYYY-MM-DD
    val finalScore: Int,
    val totalScreenTimeMinutes: Long,
    val distractingTimeMinutes: Long,
    val productiveTimeMinutes: Long,
    val idleMinutes: Long,
    val unlockCount: Int,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
