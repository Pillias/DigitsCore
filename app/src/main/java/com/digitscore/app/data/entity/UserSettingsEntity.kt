package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digitscore.app.model.ScoreRule

/**
 * 사용자 설정 엔티티 (단일 레코드 관리 id=1)
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val selectedPresetModeId: String = "balanced",
    val selectedCoreIndexPresetId: String = "balanced",
    val minimumScoreDefenseLine: Int = 60, // 최저 점수 방어선
    val targetUnlockCount: Int = 30,       // 일일 목표 언락 횟수
    val distractingWeightPerMinute: Float = 0.6f, // 방해 앱 1분당 감점치
    val productiveBonusPerMinute: Float = 0.2f,  // 생산성 앱 1분당 가산치
    val idleBonusPer10Minutes: Float = 0.25f,     // 화면 미사용 10분당 회복 점수
    val maxIdleBonus: Float = 15.0f,              // 일일 화면 미사용 보너스 최대 상한선
    val maxProductiveBonus: Float = 15.0f,        // 일일 생산성 보너스 최대 상한선
    val unlockPenaltyPerCount: Float = 0.3f,      // 언락 1회 초과당 감점치
    val lateNightMultiplier: Float = 1.5f,        // 심야 시간(24시~05시) 감점 배수
    val isLogAccelerationEnabled: Boolean = true, // 장시간 사용 로그 가속 적용 여부
    val logAccelerationThresholdMinutes: Float = 60f,
    val logAccelerationScaleMinutes: Float = 120f,
    val isYesterdayPenaltyEnabled: Boolean = true, // 이전 사용량 이월 적용 여부
    val yesterdayPenaltyTriggerScore: Int = 60,
    val yesterdayPenaltyRate: Float = 0.2f,
    val maxYesterdayPenalty: Float = 10f,
    val isTrackingEnabled: Boolean = false,
    val isNotificationEnabled: Boolean = true,
    val appHistoryRetentionDays: Int = 365,
    val hideSensitiveNotificationOnLockScreen: Boolean = true,
    val statusIconStyleId: String = "score_tier",
    val widgetBackgroundStyleId: String = "dark"
)

fun UserSettingsEntity.applyTo(
    baseRule: ScoreRule,
    yesterdayPenalty: Float = 0f
): ScoreRule = baseRule.copy(
    distractingWeightPerMinute = distractingWeightPerMinute,
    productiveBonusPerMinute = productiveBonusPerMinute,
    idleBonusPer10Minutes = idleBonusPer10Minutes,
    maxIdleBonus = maxIdleBonus,
    maxProductiveBonus = maxProductiveBonus,
    unlockPenaltyThreshold = targetUnlockCount,
    unlockPenaltyPerCount = unlockPenaltyPerCount,
    lateNightMultiplier = lateNightMultiplier,
    isLogAccelerationEnabled = isLogAccelerationEnabled,
    logAccelerationThresholdMinutes = logAccelerationThresholdMinutes,
    logAccelerationScaleMinutes = logAccelerationScaleMinutes,
    isYesterdayPenaltyEnabled = isYesterdayPenaltyEnabled,
    yesterdayPenaltyTriggerScore = yesterdayPenaltyTriggerScore,
    yesterdayPenaltyRate = yesterdayPenaltyRate,
    maxYesterdayPenalty = maxYesterdayPenalty,
    yesterdayPenalty = yesterdayPenalty
)
