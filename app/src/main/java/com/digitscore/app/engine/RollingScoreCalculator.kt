package com.digitscore.app.engine

import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.model.CoreIndexScoringConfig
import com.digitscore.app.model.defaultScoringConfig
import com.digitscore.app.model.AppCategoryType
import java.util.Calendar
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class RollingUsageSession(
    val packageName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val categoryLevel: Int,
    val effectivePackageName: String = packageName,
    val sessionStartTimeMillis: Long = startTimeMillis,
    val isLateNight: Boolean = false
)

enum class ScoreFlow { CALIBRATING, USING, RECOVERING, STEADY }

data class RollingScoreDetail(
    val finalScore: Int,
    val exactScore: Double,
    val rollingLoad: Double,
    val acuteLoad: Double,
    val lateNightCarryoverLoad: Double,
    val calibrationProgress: Float,
    val flow: ScoreFlow,
    val statusText: String,
    val recentUsageMinutes: Long,
    val continuousUsageMinutes: Long,
    val restMinutes: Long
)

/**
 * 자정에 초기화하지 않고 최근 24시간 사용 부하와 현재 연속 사용 부하를 함께 계산합니다.
 * 성장 앱도 화면 피로의 작은 기본 부하만 가지며, 좋은 앱 사용 자체에 가점은 없습니다.
 * PiP·분할 화면 구간은 시간 합산 없이 동시에 보인 앱 중 가장 높은 부하 등급을 사용합니다.
 */
object RollingScoreCalculator {
    private const val WINDOW_MILLIS = 24 * 60 * 60 * 1_000L
    private const val ACTIVE_GRACE_MILLIS = 90_000L
    private const val CALIBRATION_USAGE_MILLIS = 60 * 60 * 1_000L
    private const val RESPONSIVE_SCORE_MIN = 50.0
    private const val RESPONSIVE_SCORE_MAX = 90.0
    private const val MID_RANGE_RESPONSE_STRENGTH = 0.8

    private val extraLoadPerMinute = mapOf(
        1 to 0.0,
        2 to 0.020,
        3 to 0.080
    )

    fun calculate(
        sessions: List<RollingUsageSession>,
        nowMillis: Long,
        rollingUnlockCount: Int = 0,
        calibrationUsageMillis: Long? = null,
        preset: CoreIndexPreset = CoreIndexPreset.BALANCED,
        config: CoreIndexScoringConfig = preset.defaultScoringConfig
    ): RollingScoreDetail {
        val windowStart = nowMillis - WINDOW_MILLIS
        val clipped = sessions.asSequence()
            .filter { it.endTimeMillis > windowStart && it.startTimeMillis < nowMillis }
            .map {
                it.copy(
                    startTimeMillis = max(it.startTimeMillis, windowStart),
                    endTimeMillis = minOf(it.endTimeMillis, nowMillis),
                    categoryLevel = AppCategoryType.normalizeLevel(it.categoryLevel),
                    effectivePackageName = it.effectivePackageName
                )
            }
            .filter { it.endTimeMillis > it.startTimeMillis }
            .sortedBy { it.startTimeMillis }
            .toList()

        val merged = mergeContinuousSessions(clipped, config.sessionJoinGapMillis)
        val usageMillis = clipped.sumOf { it.endTimeMillis - it.startTimeMillis }
        var rollingLoad = clipped.sumOf { session ->
            val minutes = (session.endTimeMillis - session.startTimeMillis) / 60_000.0
            val perMinute = 0.040 * preset.baseLoadMultiplier +
                (extraLoadPerMinute[session.categoryLevel] ?: 0.020) * preset.categoryLoadMultiplier
            val lateMultiplier = if (session.categoryLevel >= 2) {
                getLateNightMultiplier(session, config)
            } else 1.0
            minutes * perMinute * lateMultiplier
        }
        rollingLoad += max(0, rollingUnlockCount - config.unlockThreshold) * preset.unlockLoadPerExcess

        val last = merged.lastOrNull()
        val isActive = last != null && nowMillis - last.endTimeMillis <= ACTIVE_GRACE_MILLIS
        val continuousMinutes = if (isActive) {
            ((last!!.endTimeMillis - last.startTimeMillis) / 60_000L).coerceAtLeast(0L)
        } else 0L
        val restMinutes = if (!isActive && last != null) {
            ((nowMillis - last.endTimeMillis) / 60_000L).coerceAtLeast(0L)
        } else 0L

        val lateNightCarryoverLoad = merged.asSequence()
            .filter { AppCategoryType.normalizeLevel(it.categoryLevel) >= 3 }
            .sumOf { session ->
                val peak = peakAcuteLoad(session, preset, config.continuousLoadStartMinutes)
                val lateMult = getLateNightMultiplier(session, config)
                val durationMin = (session.endTimeMillis - session.startTimeMillis) / 60_000.0
                when {
                    lateMult >= config.lateNightTier2Multiplier -> peak * (config.chronicCarryoverRatio * 1.5).coerceAtMost(0.40)
                    lateMult > 1.0 -> peak * config.chronicCarryoverRatio
                    durationMin >= config.chronicCarryoverThresholdMinutes -> peak * config.chronicCarryoverRatio
                    else -> 0.0
                }
            }
        rollingLoad += lateNightCarryoverLoad

        val peakAcute = last?.let { peakAcuteLoad(it, preset, config.continuousLoadStartMinutes) } ?: 0.0
        val lastIsImmersion = last != null && AppCategoryType.normalizeLevel(last.categoryLevel) >= 3
        val lastLateMult = last?.let { getLateNightMultiplier(it, config) } ?: 1.0
        val lastDurationMin = last?.let { (it.endTimeMillis - it.startTimeMillis) / 60_000.0 } ?: 0.0
        val lastCarryoverRatio = when {
            !lastIsImmersion -> 0.0
            lastLateMult >= config.lateNightTier2Multiplier -> (config.chronicCarryoverRatio * 1.5).coerceAtMost(0.40)
            lastLateMult > 1.0 -> config.chronicCarryoverRatio
            lastDurationMin >= config.chronicCarryoverThresholdMinutes -> config.chronicCarryoverRatio
            else -> 0.0
        }
        // 심야 연속 사용 및 60분 이상 고강도 세션의 일부는 최근 24시간에 남는 이월 부하로 분리합니다.
        // 사용 중 총 급성 부하는 이전과 같지만, 잠을 잔다고 전부 사라지지는 않습니다.
        val recoverableAcute = peakAcute * (1.0 - lastCarryoverRatio)
        val acuteLoad = if (isActive || last == null) recoverableAcute else {
            val halfLife = if (isSleepRest(last.endTimeMillis, restMinutes, config)) {
                config.sleepRecoveryHalfLifeMinutes
            } else {
                config.recoveryHalfLifeMinutes
            }
            recoverableAcute * 0.5.pow(restMinutes / halfLife)
        }

        val totalLoad = rollingLoad + acuteLoad
        val rawScore = 100.0 / (1.0 + (totalLoad / 45.0).pow(1.43))
        val responsiveScore = enhanceMidRangeResponse(rawScore)
        val effectiveCalibrationUsageMillis = calibrationUsageMillis ?: usageMillis
        val calibrationProgress = (effectiveCalibrationUsageMillis.toDouble() / CALIBRATION_USAGE_MILLIS)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val exactScore = (80.0 * (1.0 - calibrationProgress) + responsiveScore * calibrationProgress)
            .coerceIn(1.0, 100.0)
        val finalScore = exactScore.roundToInt().coerceIn(1, 100)

        val flow = when {
            calibrationProgress < 1f -> ScoreFlow.CALIBRATING
            isActive -> ScoreFlow.USING
            acuteLoad >= 0.25 -> ScoreFlow.RECOVERING
            else -> ScoreFlow.STEADY
        }
        val statusText = when (flow) {
            ScoreFlow.CALIBRATING -> "보정 중 · 전면 사용 ${effectiveCalibrationUsageMillis / 60_000L}/60분 반영"
            ScoreFlow.USING -> "현재 ${continuousMinutes}분 연속 사용 · 부하 ${formatLoad(totalLoad)}"
            ScoreFlow.RECOVERING -> "회복 중 · 화면을 내려놓은 지 ${restMinutes}분"
            ScoreFlow.STEADY -> "최근 24시간 흐름 안정 · 부하 ${formatLoad(totalLoad)}"
        }

        return RollingScoreDetail(
            finalScore = finalScore,
            exactScore = exactScore,
            rollingLoad = rollingLoad,
            acuteLoad = acuteLoad,
            lateNightCarryoverLoad = lateNightCarryoverLoad,
            calibrationProgress = calibrationProgress,
            flow = flow,
            statusText = statusText,
            recentUsageMinutes = usageMillis / 60_000L,
            continuousUsageMinutes = continuousMinutes,
            restMinutes = restMinutes
        )
    }

    /**
     * 세션의 시작/종료 시각을 바탕으로 심야 1단계(23~01시) / 2단계(01~05시) 배율을 산출합니다.
     */
    internal fun getLateNightMultiplier(
        session: RollingUsageSession,
        config: CoreIndexScoringConfig
    ): Double {
        val midMillis = (session.startTimeMillis + session.endTimeMillis) / 2
        val hour = getHourOfDay(midMillis)

        // Tier 2: 01:00 ~ 05:00
        val inTier2 = if (config.lateNightTier2StartHour <= config.lateNightTier2EndHour) {
            hour in config.lateNightTier2StartHour until config.lateNightTier2EndHour
        } else {
            hour >= config.lateNightTier2StartHour || hour < config.lateNightTier2EndHour
        }
        if (inTier2) return config.lateNightTier2Multiplier

        // Tier 1: 23:00 ~ 01:00
        val inTier1 = if (config.lateNightTier1StartHour <= config.lateNightTier1EndHour) {
            hour in config.lateNightTier1StartHour until config.lateNightTier1EndHour
        } else {
            hour >= config.lateNightTier1StartHour || hour < config.lateNightTier1EndHour
        }
        if (inTier1) return config.lateNightTier1Multiplier

        if (session.isLateNight) return config.lateNightTier1Multiplier
        return 1.0
    }

    /**
     * 연속 휴식 시간이 수면 구간인지 감지합니다.
     * 취침/새벽 시간대(21시~06시)에 화면이 꺼져 기준 시간(기본 150분) 이상 지속된 경우 수면으로 판단합니다.
     */
    internal fun isSleepRest(
        lastSessionEndTimeMillis: Long,
        restMinutes: Long,
        config: CoreIndexScoringConfig
    ): Boolean {
        if (!config.isSleepFreezeEnabled) return false
        if (restMinutes < config.sleepDetectionThresholdMinutes) return false
        val endHour = getHourOfDay(lastSessionEndTimeMillis)
        return endHour >= 21 || endHour < 6
    }

    internal fun getHourOfDay(epochMillis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * 50·70·90점은 그대로 두고 사용자가 가장 자주 보는 중앙 구간의 변화만 확대합니다.
     * 끝점 부근은 완만하게 이어져 0~40점과 90~100점이 갑자기 흔해지지 않습니다.
     */
    internal fun enhanceMidRangeResponse(score: Double): Double {
        if (score <= RESPONSIVE_SCORE_MIN || score >= RESPONSIVE_SCORE_MAX) return score

        val range = RESPONSIVE_SCORE_MAX - RESPONSIVE_SCORE_MIN
        val normalized = (score - RESPONSIVE_SCORE_MIN) / range
        val responseOffset = MID_RANGE_RESPONSE_STRENGTH *
            normalized * (1.0 - normalized) * (2.0 * normalized - 1.0)
        return RESPONSIVE_SCORE_MIN + range * (normalized + responseOffset)
    }

    private fun peakAcuteLoad(
        session: RollingUsageSession,
        preset: CoreIndexPreset,
        startMinutes: Double = preset.continuousLoadStartMinutes
    ): Double {
        val level = AppCategoryType.normalizeLevel(session.categoryLevel)
        val acuteFactor = when (level) {
            3 -> 1.0
            2 -> 0.45
            else -> 0.10
        }
        val sessionMinutes = (session.endTimeMillis - session.startTimeMillis) / 60_000.0
        return 34.0 *
            (max(sessionMinutes - startMinutes, 0.0) / 150.0).pow(1.3) *
            acuteFactor * preset.acuteLoadMultiplier
    }

    private fun mergeContinuousSessions(
        sessions: List<RollingUsageSession>,
        joinGapMillis: Long = 90_000L
    ): List<RollingUsageSession> {
        val result = mutableListOf<RollingUsageSession>()
        sessions.forEach { session ->
            val previous = result.lastOrNull()
            if (previous != null &&
                session.startTimeMillis - previous.endTimeMillis <= joinGapMillis
            ) {
                result[result.lastIndex] = previous.copy(
                    packageName = session.packageName,
                    endTimeMillis = max(previous.endTimeMillis, session.endTimeMillis),
                    categoryLevel = session.categoryLevel,
                    effectivePackageName = session.effectivePackageName,
                    isLateNight = session.isLateNight || previous.isLateNight
                )
            } else {
                result += session
            }
        }
        return result
    }

    private fun formatLoad(load: Double): String = String.format(java.util.Locale.US, "%.1f", load)
}
