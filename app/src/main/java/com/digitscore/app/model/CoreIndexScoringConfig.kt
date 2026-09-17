package com.digitscore.app.model

/**
 * 최근 24시간 코어 지수 계산 엔진에 적용되는 세부 설정 규칙입니다.
 * 프리셋의 권장 기본값을 사용하거나 사용자가 설정 화면에서 수동으로 조정할 수 있습니다.
 */
data class CoreIndexScoringConfig(
    val continuousLoadStartMinutes: Double = 30.0,
    val sessionJoinGapMillis: Long = 300_000L,
    val lateNightTier1StartHour: Int = 23,
    val lateNightTier1EndHour: Int = 1,
    val lateNightTier1Multiplier: Double = 1.4,
    val lateNightTier2StartHour: Int = 1,
    val lateNightTier2EndHour: Int = 5,
    val lateNightTier2Multiplier: Double = 2.0,
    val isSleepFreezeEnabled: Boolean = true,
    val sleepDetectionThresholdMinutes: Long = 150L,
    val sleepRecoveryDelayMinutes: Long = 120L,
    val sleepRecoveryMaxEquivalentMinutes: Long = 90L,
    val wakeWindowStartHour: Int = 5,
    val wakeWindowEndHour: Int = 11,
    val unlockThreshold: Int = 20,
    val recoveryHalfLifeMinutes: Double = 90.0,
    val chronicCarryoverThresholdMinutes: Double = 60.0,
    val chronicCarryoverRatio: Double = 0.25
) {
    fun sanitized(): CoreIndexScoringConfig = copy(
        continuousLoadStartMinutes = continuousLoadStartMinutes.coerceIn(15.0, 90.0),
        sessionJoinGapMillis = sessionJoinGapMillis.coerceIn(30_000L, 300_000L),
        lateNightTier1StartHour = lateNightTier1StartHour.coerceIn(20, 23),
        lateNightTier1EndHour = lateNightTier1EndHour.coerceIn(0, 2),
        lateNightTier1Multiplier = lateNightTier1Multiplier.coerceIn(1.0, 3.0),
        lateNightTier2StartHour = lateNightTier2StartHour.coerceIn(0, 2),
        lateNightTier2EndHour = lateNightTier2EndHour.coerceIn(4, 7),
        lateNightTier2Multiplier = lateNightTier2Multiplier.coerceIn(1.2, 4.0),
        sleepDetectionThresholdMinutes = sleepDetectionThresholdMinutes.coerceIn(90L, 300L),
        sleepRecoveryDelayMinutes = sleepRecoveryDelayMinutes.coerceIn(60L, 240L),
        sleepRecoveryMaxEquivalentMinutes = sleepRecoveryMaxEquivalentMinutes.coerceIn(30L, 180L),
        wakeWindowStartHour = wakeWindowStartHour.coerceIn(3, 9),
        wakeWindowEndHour = wakeWindowEndHour.coerceIn(9, 13),
        unlockThreshold = unlockThreshold.coerceIn(5, 100),
        recoveryHalfLifeMinutes = recoveryHalfLifeMinutes.coerceIn(45.0, 240.0),
        chronicCarryoverThresholdMinutes = chronicCarryoverThresholdMinutes.coerceIn(30.0, 120.0),
        chronicCarryoverRatio = chronicCarryoverRatio.coerceIn(0.1, 0.5)
    )
}
