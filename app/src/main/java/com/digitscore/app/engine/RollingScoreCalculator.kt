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
    val restMinutes: Long,
    val sleepRestMinutes: Long = 0L,
    val postWakeRestMinutes: Long = 0L,
    val effectiveRecoveryMinutes: Long = 0L,
    val estimatedWakeTimeMillis: Long? = null
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

    private data class ContinuousUsageBlock(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val activeDurationMillis: Long,
        val categoryDurationMillis: Map<Int, Long>,
        val lateNightTier1Millis: Long,
        val lateNightTier2Millis: Long
    ) {
        val maxCategoryLevel: Int
            get() = categoryDurationMillis.filterValues { it > 0L }.keys.maxOrNull() ?: 2
        val lateNightMillis: Long
            get() = lateNightTier1Millis + lateNightTier2Millis
    }

    internal data class RestRecoveryBreakdown(
        val elapsedMinutes: Long,
        val sleepMinutes: Long,
        val postWakeMinutes: Long,
        val effectiveMinutes: Double,
        val estimatedWakeTimeMillis: Long?,
        val isSleepRest: Boolean
    )

    private val extraLoadPerMinute = mapOf(
        1 to 0.0,
        2 to 0.020,
        3 to 0.080
    )

    fun calculate(
        sessions: List<RollingUsageSession>,
        nowMillis: Long,
        rollingUnlockCount: Int = 0,
        rollingUnlockTimestamps: List<Long> = emptyList(),
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

        val blocks = buildContinuousBlocks(clipped, config)
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

        val last = blocks.lastOrNull()
        val isActive = last != null && nowMillis - last.endTimeMillis <= ACTIVE_GRACE_MILLIS
        val continuousMinutes = if (isActive) {
            (last!!.activeDurationMillis / 60_000L).coerceAtLeast(0L)
        } else 0L
        val restMinutes = if (!isActive && last != null) {
            ((nowMillis - last.endTimeMillis) / 60_000L).coerceAtLeast(0L)
        } else 0L

        val blockLoads = blocks.mapIndexed { index, block ->
            val peak = peakAcuteLoad(block, preset, config.continuousLoadStartMinutes)
            val carryoverRatio = carryoverRatio(block, config)
            val carryover = peak * carryoverRatio
            val recoverable = peak - carryover
            val effectiveRecovery = effectiveRecoveryAfterBlock(
                blockIndex = index,
                blocks = blocks,
                nowMillis = nowMillis,
                unlockTimestamps = rollingUnlockTimestamps,
                config = config
            )
            val acute = recoverable * 0.5.pow(effectiveRecovery / config.recoveryHalfLifeMinutes)
            carryover to acute
        }
        val lateNightCarryoverLoad = blockLoads.sumOf { it.first }
        rollingLoad += lateNightCarryoverLoad
        val acuteLoad = blockLoads.sumOf { it.second }
        val currentRest = if (!isActive && last != null) {
            analyzeRestGap(last.endTimeMillis, nowMillis, rollingUnlockTimestamps, config)
        } else null

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
            restMinutes = restMinutes,
            sleepRestMinutes = currentRest?.sleepMinutes ?: 0L,
            postWakeRestMinutes = currentRest?.postWakeMinutes ?: 0L,
            effectiveRecoveryMinutes = currentRest?.effectiveMinutes?.roundToInt()?.toLong() ?: 0L,
            estimatedWakeTimeMillis = currentRest?.estimatedWakeTimeMillis
        )
    }

    /**
     * 세션의 시작/종료 시각을 바탕으로 심야 1단계(23~01시) / 2단계(01~05시) 배율을 산출합니다.
     */
    internal fun getLateNightMultiplier(
        session: RollingUsageSession,
        config: CoreIndexScoringConfig
    ): Double {
        val duration = (session.endTimeMillis - session.startTimeMillis).coerceAtLeast(0L)
        if (duration == 0L) return 1.0
        val late = lateNightDurations(session.startTimeMillis, session.endTimeMillis, config)
        val normalMillis = (duration - late.first - late.second).coerceAtLeast(0L)
        return (
            normalMillis +
                late.first * config.lateNightTier1Multiplier +
                late.second * config.lateNightTier2Multiplier
            ) / duration.toDouble()
    }

    /**
     * 연속 휴식 시간이 수면 구간인지 감지합니다.
     * 취침/새벽 시간대(21시~06시)에 화면이 꺼져 기준 시간(기본 150분) 이상 지속된 경우 수면으로 판단합니다.
     */
    internal fun isSleepRest(
        lastSession: RollingUsageSession?,
        restMinutes: Long,
        config: CoreIndexScoringConfig
    ): Boolean {
        if (!config.isSleepFreezeEnabled || lastSession == null) return false
        if (restMinutes < config.sleepDetectionThresholdMinutes) return false
        val endHour = getHourOfDay(lastSession.endTimeMillis)
        return endHour >= 21 || endHour < config.wakeWindowStartHour
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
        block: ContinuousUsageBlock,
        preset: CoreIndexPreset,
        startMinutes: Double = preset.continuousLoadStartMinutes
    ): Double {
        val weightedAcuteFactor = block.categoryDurationMillis.entries.sumOf { (level, duration) ->
            val factor = when (AppCategoryType.normalizeLevel(level)) {
                3 -> 1.0
                2 -> 0.45
                else -> 0.10
            }
            duration * factor
        } / block.activeDurationMillis.coerceAtLeast(1L).toDouble()
        val activeMinutes = block.activeDurationMillis / 60_000.0
        return 34.0 *
            (max(activeMinutes - startMinutes, 0.0) / 150.0).pow(1.3) *
            weightedAcuteFactor * preset.acuteLoadMultiplier
    }

    private fun carryoverRatio(
        block: ContinuousUsageBlock,
        config: CoreIndexScoringConfig
    ): Double {
        val activeMinutes = block.activeDurationMillis / 60_000.0
        if (block.maxCategoryLevel < 3 ||
            activeMinutes < config.chronicCarryoverThresholdMinutes ||
            block.lateNightMillis <= 0L
        ) return 0.0

        val lateFraction = (block.lateNightMillis / block.activeDurationMillis.toDouble())
            .coerceIn(0.0, 1.0)
        val severity = if (block.lateNightTier2Millis > 0L) 1.5 else 1.0
        return (config.chronicCarryoverRatio * severity * lateFraction).coerceIn(0.0, 0.40)
    }

    private fun effectiveRecoveryAfterBlock(
        blockIndex: Int,
        blocks: List<ContinuousUsageBlock>,
        nowMillis: Long,
        unlockTimestamps: List<Long>,
        config: CoreIndexScoringConfig
    ): Double {
        var cursor = blocks[blockIndex].endTimeMillis
        var effectiveMinutes = 0.0
        for (index in (blockIndex + 1) until blocks.size) {
            val next = blocks[index]
            if (next.startTimeMillis > cursor) {
                effectiveMinutes += analyzeRestGap(cursor, next.startTimeMillis, unlockTimestamps, config)
                    .effectiveMinutes
            }
            cursor = max(cursor, next.endTimeMillis)
        }
        if (nowMillis > cursor) {
            effectiveMinutes += analyzeRestGap(cursor, nowMillis, unlockTimestamps, config)
                .effectiveMinutes
        }
        return effectiveMinutes
    }

    internal fun analyzeRestGap(
        startMillis: Long,
        endMillis: Long,
        unlockTimestamps: List<Long>,
        config: CoreIndexScoringConfig
    ): RestRecoveryBreakdown {
        val elapsedMinutes = ((endMillis - startMillis).coerceAtLeast(0L) / 60_000L)
        val startHour = getHourOfDay(startMillis)
        val sleepRest = config.isSleepFreezeEnabled &&
            elapsedMinutes >= config.sleepDetectionThresholdMinutes &&
            (startHour >= 21 || startHour < config.wakeWindowStartHour)
        if (!sleepRest) {
            return RestRecoveryBreakdown(
                elapsedMinutes = elapsedMinutes,
                sleepMinutes = 0L,
                postWakeMinutes = 0L,
                effectiveMinutes = elapsedMinutes.toDouble(),
                estimatedWakeTimeMillis = null,
                isSleepRest = false
            )
        }

        val earliestWake = startMillis + config.sleepDetectionThresholdMinutes * 60_000L
        val wakeTime = unlockTimestamps.asSequence()
            .filter { it in earliestWake..endMillis }
            .sorted()
            .firstOrNull { timestamp ->
                getHourOfDay(timestamp) in config.wakeWindowStartHour until config.wakeWindowEndHour
            }
        val sleepEnd = wakeTime ?: endMillis
        val sleepMinutes = ((sleepEnd - startMillis).coerceAtLeast(0L) / 60_000L)
        val postWakeMinutes = wakeTime?.let {
            ((endMillis - it).coerceAtLeast(0L) / 60_000L)
        } ?: 0L
        val interruptions = unlockTimestamps.count { timestamp ->
            timestamp > startMillis && timestamp < sleepEnd && timestamp != wakeTime
        }
        val continuityFactor = 0.85.pow(interruptions.toDouble()).coerceAtLeast(0.40)
        val eligibleSleepMinutes = (sleepMinutes - config.sleepRecoveryDelayMinutes).coerceAtLeast(0L)
        val effectiveSleepMinutes = (eligibleSleepMinutes *
            sleepTimingEfficiency(startMillis) * continuityFactor)
            .coerceAtMost(config.sleepRecoveryMaxEquivalentMinutes.toDouble())
        return RestRecoveryBreakdown(
            elapsedMinutes = elapsedMinutes,
            sleepMinutes = sleepMinutes,
            postWakeMinutes = postWakeMinutes,
            effectiveMinutes = effectiveSleepMinutes + postWakeMinutes,
            estimatedWakeTimeMillis = wakeTime,
            isSleepRest = true
        )
    }

    private fun sleepTimingEfficiency(startMillis: Long): Double {
        val calendar = Calendar.getInstance().apply { timeInMillis = startMillis }
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return when {
            minuteOfDay >= 21 * 60 || minuteOfDay < 30 -> 1.0
            minuteOfDay < 90 -> 0.60
            minuteOfDay < 150 -> 0.25
            minuteOfDay < 240 -> 0.08
            else -> 0.03
        }
    }

    private fun buildContinuousBlocks(
        sessions: List<RollingUsageSession>,
        config: CoreIndexScoringConfig
    ): List<ContinuousUsageBlock> {
        val result = mutableListOf<ContinuousUsageBlock>()
        sessions.forEach { session ->
            val duration = (session.endTimeMillis - session.startTimeMillis).coerceAtLeast(0L)
            if (duration == 0L) return@forEach
            val level = AppCategoryType.normalizeLevel(session.categoryLevel)
            val late = lateNightDurations(session.startTimeMillis, session.endTimeMillis, config)
            val previous = result.lastOrNull()
            if (previous != null &&
                session.startTimeMillis - previous.endTimeMillis <= config.sessionJoinGapMillis
            ) {
                result[result.lastIndex] = previous.copy(
                    endTimeMillis = max(previous.endTimeMillis, session.endTimeMillis),
                    activeDurationMillis = previous.activeDurationMillis + duration,
                    categoryDurationMillis = previous.categoryDurationMillis.toMutableMap().apply {
                        this[level] = (this[level] ?: 0L) + duration
                    },
                    lateNightTier1Millis = previous.lateNightTier1Millis + late.first,
                    lateNightTier2Millis = previous.lateNightTier2Millis + late.second
                )
            } else {
                result += ContinuousUsageBlock(
                    startTimeMillis = session.startTimeMillis,
                    endTimeMillis = session.endTimeMillis,
                    activeDurationMillis = duration,
                    categoryDurationMillis = mapOf(level to duration),
                    lateNightTier1Millis = late.first,
                    lateNightTier2Millis = late.second
                )
            }
        }
        return result
    }

    private fun lateNightDurations(
        startMillis: Long,
        endMillis: Long,
        config: CoreIndexScoringConfig
    ): Pair<Long, Long> {
        var cursor = startMillis
        var tier1Millis = 0L
        var tier2Millis = 0L
        while (cursor < endMillis) {
            val next = minOf(endMillis, ((cursor / 60_000L) + 1L) * 60_000L)
            val duration = next - cursor
            val hour = getHourOfDay(cursor)
            when {
                isHourInRange(hour, config.lateNightTier2StartHour, config.lateNightTier2EndHour) ->
                    tier2Millis += duration
                isHourInRange(hour, config.lateNightTier1StartHour, config.lateNightTier1EndHour) ->
                    tier1Millis += duration
            }
            cursor = next
        }
        return tier1Millis to tier2Millis
    }

    private fun isHourInRange(hour: Int, startHour: Int, endHour: Int): Boolean =
        if (startHour <= endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }

    private fun formatLoad(load: Double): String = String.format(java.util.Locale.US, "%.1f", load)
}
