package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 일자별 코어 지수와 사용 통계 엔티티. */
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
    /** 1: 기존 일일 점수, 2: 최근 24시간 코어 지수 */
    val scoreModelVersion: Int = 1,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
