package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자 설정 엔티티 (단일 레코드 관리 id=1)
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val selectedPresetModeId: String = "balanced",
    val minimumScoreDefenseLine: Int = 60, // 최저 점수 방어선
    val targetUnlockCount: Int = 25,       // 일일 목표 언락 횟수
    val isTrackingEnabled: Boolean = true,
    val isNotificationEnabled: Boolean = true
)
