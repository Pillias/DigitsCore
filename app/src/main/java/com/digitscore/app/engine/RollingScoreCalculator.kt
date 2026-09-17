package com.digitscore.app.engine

import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.model.AppCategoryType
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
    private const val SESSION_JOIN_GAP_MILLIS = 90_000L
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
        preset: CoreIndexPreset = CoreIndexPreset.BALANCED
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

        val merged = mergeContinuousSessions(clipped)
        val usageMillis = clipped.sumOf { it.endTimeMillis - it.startTimeMillis }
        var rollingLoad = clipped.sumOf { session ->
            val minutes = (session.endTimeMillis - session.startTimeMillis) / 60_000.0
            val perMinute = 0.040 * preset.baseLoadMultiplier +
                (extraLoadPerMinute[session.categoryLevel] ?: 0.020) * preset.categoryLoadMultiplier
            minutes * perMinute *
                if (session.isLateNight && session.categoryLevel >= 3) preset.lateNightMultiplier else 1.0
        }
        rollingLoad += max(0, rollingUnlockCount - preset.unlockThreshold) * preset.unlockLoadPerExcess

        val last = merged.lastOrNull()
        val isActive = last != null && nowMillis - last.endTimeMillis <= ACTIVE_GRACE_MILLIS
        val continuousMinutes = if (isActive) {
            ((last!!.endTimeMillis - last.startTimeMillis) / 60_000L).coerceAtLeast(0L)
        } else 0L
        val restMinutes = if (!isActive && last != null) {
            ((nowMillis - last.endTimeMillis) / 60_000L).coerceAtLeast(0L)
        } else 0L

        val lateNightCarryoverLoad = merged.asSequence()
            .filter { it.isLateNight && AppCategoryType.normalizeLevel(it.categoryLevel) >= 3 }
            .sumOf { peakAcuteLoad(it, preset) * preset.lateNightCarryoverRatio }
        rollingLoad += lateNightCarryoverLoad

        val peakAcute = last?.let { peakAcuteLoad(it, preset) } ?: 0.0
        val lastHasLateNightCarryover = last?.let {
            it.isLateNight && AppCategoryType.normalizeLevel(it.categoryLevel) >= 3
        } == true
        val lastCarryoverRatio = if (lastHasLateNightCarryover) {
            preset.lateNightCarryoverRatio
        } else 0.0
        // 심야 연속 사용의 일부는 최근 24시간에 남는 이월 부하로 분리합니다.
        // 사용 중 총 급성 부하는 이전과 같지만, 잠을 잔다고 전부 사라지지는 않습니다.
        val recoverableAcute = peakAcute * (1.0 - lastCarryoverRatio)
        val acuteLoad = if (isActive) recoverableAcute else {
            recoverableAcute * 0.5.pow(restMinutes / preset.recoveryHalfLifeMinutes)
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
        preset: CoreIndexPreset
    ): Double {
        val level = AppCategoryType.normalizeLevel(session.categoryLevel)
        val acuteFactor = when (level) {
            3 -> 1.0
            2 -> 0.45
            else -> 0.10
        }
        val sessionMinutes = (session.endTimeMillis - session.startTimeMillis) / 60_000.0
        return 34.0 *
            (max(sessionMinutes - preset.continuousLoadStartMinutes, 0.0) / 150.0).pow(1.3) *
            acuteFactor * preset.acuteLoadMultiplier
    }

    private fun mergeContinuousSessions(sessions: List<RollingUsageSession>): List<RollingUsageSession> {
        val result = mutableListOf<RollingUsageSession>()
        sessions.forEach { session ->
            val previous = result.lastOrNull()
            if (previous != null &&
                session.startTimeMillis - previous.endTimeMillis <= SESSION_JOIN_GAP_MILLIS
            ) {
                result[result.lastIndex] = previous.copy(
                    packageName = session.packageName,
                    endTimeMillis = max(previous.endTimeMillis, session.endTimeMillis),
                    categoryLevel = session.categoryLevel,
                    effectivePackageName = session.effectivePackageName,
                    isLateNight = session.isLateNight
                )
            } else {
                result += session
            }
        }
        return result
    }

    private fun formatLoad(load: Double): String = String.format(java.util.Locale.US, "%.1f", load)
}
