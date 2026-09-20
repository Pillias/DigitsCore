package com.digitscore.app.engine

import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.model.CoreIndexScoringConfig
import com.digitscore.app.model.defaultScoringConfig
import kotlin.math.roundToInt

enum class CoreIndexCause {
    CALIBRATING,
    CONTINUOUS_USE,
    MANAGED_APP_USE,
    FREQUENT_UNLOCKS,
    RECOVERING,
    STEADY
}

enum class CoreIndexRecommendation {
    KEEP_BALANCE,
    TAKE_TEN_MINUTE_BREAK,
    TAKE_QUIET_BREAK,
    BATCH_PHONE_CHECKS,
    WIND_DOWN
}

data class CoreIndexGuidance(
    val scoreChange: Int,
    val cause: CoreIndexCause,
    val leadingAppName: String? = null,
    val leadingAppMinutes: Long = 0L,
    val continuousUsageMinutes: Long = 0L,
    val recoveryTargetScore: Int? = null,
    val recoveryMinutes: Int? = null,
    val rollingUsageMinutes: Long = 0L,
    val rollingOpenCount: Int = 0,
    val shortOpenCount: Int = 0,
    val rollingUnlockCount: Int = 0,
    val recentSevenDayAverage: Int? = null,
    val previousSevenDayAverage: Int? = null,
    val recommendation: CoreIndexRecommendation = CoreIndexRecommendation.KEEP_BALANCE
)

/** 숫자만 보여주지 않고 현재 원인, 예상 회복, 개인 과거 비교를 한 가지 행동으로 연결합니다. */
object CoreIndexCoach {
    fun create(
        detail: RollingScoreDetail,
        previousScore: Int?,
        sessions: List<RollingUsageSession>,
        apps: List<AppUsage>,
        rollingUnlockTimestamps: List<Long>,
        histories: List<DailyScoreHistoryEntity>,
        nowMillis: Long,
        preset: CoreIndexPreset,
        scoringConfig: CoreIndexScoringConfig = preset.defaultScoringConfig,
        cumulativeState: CumulativeScoreState? = null
    ): CoreIndexGuidance {
        val appNames = apps.associate { it.packageName to it.appName }
        val recentStart = nowMillis - 2 * 60 * 60_000L
        val recentByPackage = sessions
            .filter { it.endTimeMillis > recentStart }
            .groupBy { it.effectivePackageName }
            .mapValues { (_, values) ->
                values.sumOf { (it.endTimeMillis - maxOf(it.startTimeMillis, recentStart)).coerceAtLeast(0L) }
            }
        val leading = recentByPackage.maxByOrNull { it.value }
        val leadingLevel = sessions.lastOrNull { it.effectivePackageName == leading?.key }?.categoryLevel ?: 2
        val hour = RollingScoreCalculator.getHourOfDay(nowMillis)
        val lateNightManaged = (hour >= scoringConfig.lateNightTier1StartHour ||
            hour < scoringConfig.lateNightTier2EndHour) && sessions.any {
            it.endTimeMillis > nowMillis - 30 * 60_000L && it.categoryLevel >= 3 &&
                RollingScoreCalculator.getLateNightMultiplier(it, scoringConfig) > 1.0
        }

        val cause = when {
            detail.flow == ScoreFlow.CALIBRATING -> CoreIndexCause.CALIBRATING
            detail.continuousUsageMinutes >= scoringConfig.continuousLoadStartMinutes -> CoreIndexCause.CONTINUOUS_USE
            leading != null && leadingLevel >= 3 && leading.value >= 10 * 60_000L -> CoreIndexCause.MANAGED_APP_USE
            rollingUnlockTimestamps.size > scoringConfig.unlockThreshold -> CoreIndexCause.FREQUENT_UNLOCKS
            detail.flow == ScoreFlow.RECOVERING -> CoreIndexCause.RECOVERING
            else -> CoreIndexCause.STEADY
        }
        val recommendation = when {
            detail.flow == ScoreFlow.CALIBRATING -> CoreIndexRecommendation.KEEP_BALANCE
            lateNightManaged -> CoreIndexRecommendation.WIND_DOWN
            detail.continuousUsageMinutes >= scoringConfig.continuousLoadStartMinutes -> CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK
            detail.finalScore <= 60 -> CoreIndexRecommendation.TAKE_QUIET_BREAK
            rollingUnlockTimestamps.size > scoringConfig.unlockThreshold -> CoreIndexRecommendation.BATCH_PHONE_CHECKS
            else -> CoreIndexRecommendation.KEEP_BALANCE
        }
        val target = (detail.finalScore + 3).coerceAtMost(90)
        val recovery = if (cumulativeState != null) {
            if (target > detail.finalScore) com.digitscore.app.data.CumulativeScoreStore.awakeRecoveryMinutes(cumulativeState, target)
            else null
        } else if (target > detail.finalScore && detail.flow != ScoreFlow.CALIBRATING) {
            (5..180 step 5).firstOrNull { minutes ->
                val future = nowMillis + minutes * 60_000L
                val futureUnlocks = rollingUnlockTimestamps.count { it >= future - 24 * 60 * 60_000L }
                RollingScoreCalculator.calculate(
                    sessions = sessions,
                    nowMillis = future,
                    rollingUnlockCount = futureUnlocks,
                    rollingUnlockTimestamps = rollingUnlockTimestamps,
                    calibrationUsageMillis = 60 * 60_000L,
                    preset = preset,
                    config = scoringConfig
                ).finalScore >= target
            }
        } else null

        val latestModel = histories.maxByOrNull { it.dateString }?.scoreModelVersion
        val validDays = histories.filter {
            it.scoreModelVersion >= 2 && it.scoreModelVersion == latestModel && it.coreIndexPresetId == preset.id
        }.sortedBy { it.dateString }
        val recent = validDays.takeLast(7).map { it.finalScore }
        val previous = validDays.dropLast(recent.size).takeLast(7).map { it.finalScore }
        return CoreIndexGuidance(
            scoreChange = detail.finalScore - (previousScore ?: detail.finalScore),
            cause = cause,
            leadingAppName = leading?.key?.let { appNames[it] ?: it },
            leadingAppMinutes = (leading?.value ?: 0L) / 60_000L,
            continuousUsageMinutes = detail.continuousUsageMinutes,
            recoveryTargetScore = target.takeIf { recovery != null },
            recoveryMinutes = recovery,
            rollingUsageMinutes = apps.sumOf { it.usageTimeMillis } / 60_000L,
            rollingOpenCount = apps.sumOf { it.sessionCount },
            shortOpenCount = apps.sumOf { it.shortSessionCount },
            rollingUnlockCount = rollingUnlockTimestamps.size,
            recentSevenDayAverage = recent.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            previousSevenDayAverage = previous.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            recommendation = recommendation
        )
    }
}
