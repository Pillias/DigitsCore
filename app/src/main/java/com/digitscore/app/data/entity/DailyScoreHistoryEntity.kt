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
    /** 1: 기존 일일 점수, 2: 실시간 코어 지수, 3: 상세 세션에서 복원한 코어 지수 */
    val scoreModelVersion: Int = 1,
    /** 해당 기록을 계산할 때 사용한 코어 지수 프리셋 ID */
    val coreIndexPresetId: String = "balanced",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
