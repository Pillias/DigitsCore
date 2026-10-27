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
    val recommendation: CoreIndexRecommendation = CoreIndexRecommendation.KEEP_BALANCE,
    /** 안내를 만든 시점의 표시 지수. 할 일 한 줄(`CoachActionPlanner`)이 씁니다. */
    val currentScore: Int = 0,
    /** 오늘의 지수 방어선 (목표가 없으면 null) */
    val defenseLine: Int? = null,
    /** 1시간 안에 닿는 목표가 없을 때 30min 쉬면 오르는 P */
    val shortRestGain: Double? = null
)

/** 숫자만 보여주지 않고 현재 원인, 예상 회복, 개인 과거 비교를 한 가지 행동으로 연결합니다. */
object CoreIndexCoach {
    /** 최근 1시간 잠금 해제가 이 횟수 이상이면 잦은 확인으로 안내합니다(기준 시간당 5회의 2배). */
    const val FREQUENT_UNLOCKS_PER_HOUR = 10

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
        cumulativeState: CumulativeScoreState? = null,
        defenseLine: Int? = null
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

        // 잠금 해제 간격 점수(v3)와 같은 기준: 최근 1시간에 기준(시간당 5회)의 두 배 이상 풀면 잦은 확인으로 봅니다.
        val recentHourUnlocks = rollingUnlockTimestamps.count { it > nowMillis - 60 * 60_000L && it <= nowMillis }
        val frequentUnlocks = recentHourUnlocks >= FREQUENT_UNLOCKS_PER_HOUR
        val cause = when {
            detail.flow == ScoreFlow.CALIBRATING -> CoreIndexCause.CALIBRATING
            detail.continuousUsageMinutes >= scoringConfig.continuousLoadStartMinutes -> CoreIndexCause.CONTINUOUS_USE
            leading != null && leadingLevel >= 3 && leading.value >= 10 * 60_000L -> CoreIndexCause.MANAGED_APP_USE
            frequentUnlocks -> CoreIndexCause.FREQUENT_UNLOCKS
            detail.flow == ScoreFlow.RECOVERING -> CoreIndexCause.RECOVERING
            else -> CoreIndexCause.STEADY
        }
        val recommendation = when {
            detail.flow == ScoreFlow.CALIBRATING -> CoreIndexRecommendation.KEEP_BALANCE
            lateNightManaged -> CoreIndexRecommendation.WIND_DOWN
            detail.continuousUsageMinutes >= scoringConfig.continuousLoadStartMinutes -> CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK
            detail.finalScore <= 60 -> CoreIndexRecommendation.TAKE_QUIET_BREAK
            frequentUnlocks -> CoreIndexRecommendation.BATCH_PHONE_CHECKS
            else -> CoreIndexRecommendation.KEEP_BALANCE
        }
        // 누적 모델: 1시간 안에 닿는 가까운 목표만 안내합니다(방어선 → 단계 경계 → +3/+2/+1P).
        val chosen = cumulativeState?.let { state ->
            CoachActionPlanner.chooseRecovery(detail.finalScore, defenseLine) { t ->
                com.digitscore.app.data.CumulativeScoreStore.awakeRecoveryMinutes(state, t, CoachActionPlanner.MAX_RECOVERY_MINUTES)
            }
        }
        val shortRestGain = if (cumulativeState != null && chosen == null) {
            com.digitscore.app.data.CumulativeScoreStore.awakeRecoveryGain(cumulativeState, CoachActionPlanner.SHORT_REST_MINUTES)
        } else null
        val target = chosen?.first ?: CoachActionPlanner.recoveryTarget(detail.finalScore)
        val recovery = if (cumulativeState != null) {
            chosen?.second
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
            recommendation = recommendation,
            currentScore = detail.finalScore,
            defenseLine = defenseLine,
            shortRestGain = shortRestGain
        )
    }
}
