package com.digitscore.app.engine

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class RollingUsageSession(
    val packageName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val categoryLevel: Int,
    val isLateNight: Boolean = false
)

enum class ScoreFlow { CALIBRATING, USING, RECOVERING, STEADY }

data class RollingScoreDetail(
    val finalScore: Int,
    val exactScore: Double,
    val rollingLoad: Double,
    val acuteLoad: Double,
    val calibrationProgress: Float,
    val flow: ScoreFlow,
    val statusText: String,
    val recentUsageMinutes: Long,
    val continuousUsageMinutes: Long,
    val restMinutes: Long
)

/**
 * 자정에 초기화하지 않고 최근 24시간 사용 부하와 현재 연속 사용 부하를 함께 계산합니다.
 * 1단계 앱도 화면 피로의 아주 작은 기본 부하만 가지며, 좋은 앱 사용 자체에 가점은 없습니다.
 */
object RollingScoreCalculator {
    private const val WINDOW_MILLIS = 24 * 60 * 60 * 1_000L
    private const val SESSION_JOIN_GAP_MILLIS = 90_000L
    private const val ACTIVE_GRACE_MILLIS = 90_000L
    private const val CALIBRATION_USAGE_MILLIS = 60 * 60 * 1_000L

    private val extraLoadPerMinute = mapOf(
        1 to 0.0,
        2 to 0.008,
        3 to 0.020,
        4 to 0.055,
        5 to 0.080
    )

    fun calculate(
        sessions: List<RollingUsageSession>,
        nowMillis: Long,
        rollingUnlockCount: Int = 0,
        calibrationUsageMillis: Long? = null
    ): RollingScoreDetail {
        val windowStart = nowMillis - WINDOW_MILLIS
        val clipped = sessions.asSequence()
            .filter { it.endTimeMillis > windowStart && it.startTimeMillis < nowMillis }
            .map {
                it.copy(
                    startTimeMillis = max(it.startTimeMillis, windowStart),
                    endTimeMillis = minOf(it.endTimeMillis, nowMillis),
                    categoryLevel = it.categoryLevel.coerceIn(1, 5)
                )
            }
            .filter { it.endTimeMillis > it.startTimeMillis }
            .sortedBy { it.startTimeMillis }
            .toList()

        val merged = mergeContinuousSessions(clipped)
        val usageMillis = clipped.sumOf { it.endTimeMillis - it.startTimeMillis }
        var rollingLoad = clipped.sumOf { session ->
            val minutes = (session.endTimeMillis - session.startTimeMillis) / 60_000.0
            val perMinute = 0.040 + (extraLoadPerMinute[session.categoryLevel] ?: 0.020)
            minutes * perMinute * if (session.isLateNight && session.categoryLevel >= 4) 1.25 else 1.0
        }
        rollingLoad += max(0, rollingUnlockCount - 20) * 0.12

        val last = merged.lastOrNull()
        val isActive = last != null && nowMillis - last.endTimeMillis <= ACTIVE_GRACE_MILLIS
        val continuousMinutes = if (isActive) {
            ((last!!.endTimeMillis - last.startTimeMillis) / 60_000L).coerceAtLeast(0L)
        } else 0L
        val restMinutes = if (!isActive && last != null) {
            ((nowMillis - last.endTimeMillis) / 60_000L).coerceAtLeast(0L)
        } else 0L

        val lastLevel = last?.categoryLevel?.coerceIn(1, 5) ?: 3
        val acuteFactor = when (lastLevel) {
            5 -> 1.0
            4 -> 0.55
            3 -> 0.20
            else -> 0.0
        }
        val peakAcute = if (last != null) {
            val sessionMinutes = (last.endTimeMillis - last.startTimeMillis) / 60_000.0
            34.0 * (max(sessionMinutes - 30.0, 0.0) / 150.0).pow(1.3) * acuteFactor
        } else 0.0
        val acuteLoad = if (isActive) peakAcute else peakAcute * 0.5.pow(restMinutes / 35.0)

        val totalLoad = rollingLoad + acuteLoad
        val rawScore = 100.0 / (1.0 + (totalLoad / 45.0).pow(1.43))
        val effectiveCalibrationUsageMillis = calibrationUsageMillis ?: usageMillis
        val calibrationProgress = (effectiveCalibrationUsageMillis.toDouble() / CALIBRATION_USAGE_MILLIS)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val exactScore = (80.0 * (1.0 - calibrationProgress) + rawScore * calibrationProgress)
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
            calibrationProgress = calibrationProgress,
            flow = flow,
            statusText = statusText,
            recentUsageMinutes = usageMillis / 60_000L,
            continuousUsageMinutes = continuousMinutes,
            restMinutes = restMinutes
        )
    }

    private fun mergeContinuousSessions(sessions: List<RollingUsageSession>): List<RollingUsageSession> {
        val result = mutableListOf<RollingUsageSession>()
        sessions.forEach { session ->
            val previous = result.lastOrNull()
            if (previous != null && previous.packageName == session.packageName &&
                session.startTimeMillis - previous.endTimeMillis <= SESSION_JOIN_GAP_MILLIS
            ) {
                result[result.lastIndex] = previous.copy(
                    endTimeMillis = max(previous.endTimeMillis, session.endTimeMillis),
                    isLateNight = previous.isLateNight || session.isLateNight
                )
            } else {
                result += session
            }
        }
        return result
    }

    private fun formatLoad(load: Double): String = String.format(java.util.Locale.US, "%.1f", load)
}
