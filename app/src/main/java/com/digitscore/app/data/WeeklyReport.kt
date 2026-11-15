package com.digitscore.app.data

import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.model.DailyGoalDayRecord
import com.digitscore.app.model.GoalRewardState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class AppChange(
    val packageName: String,
    val appName: String,
    val diffMinutes: Long
)

data class WeeklyReportData(
    val weekStartDate: String,
    val weekEndDate: String,
    val hasData: Boolean,
    val recordedDaysCount: Int,

    // 1. 코어 지수
    val avgScore: Double?,
    val scoreDiffVsPriorWeek: Double?,
    val highestScoreDay: Pair<String, Int>?,
    val lowestScoreDay: Pair<String, Int>?,

    // 2. 오늘의 목표 달성 일수
    val hasGoalHistory: Boolean = true,
    val scoreGoalMetDays: Int = 0,
    val appGoalMetDays: Int = 0,
    val unlockGoalMetDays: Int = 0,
    val achievedDaysCount: Int = scoreGoalMetDays,
    val perfectDaysCount: Int = 0,
    val perfectStampsEarned: Int = 0,
    val freePassesEarned: Int = 0,

    // 3. 화면 시간 / 잠금 해제 / 짧은 확인
    val avgScreenTimeMinutes: Long,
    val screenTimeDiffMinutes: Long?,
    val avgUnlockCount: Int,
    val unlockDiffCount: Int?,
    val avgShortSessionCount: Int,
    val shortSessionDiffCount: Int?,

    // 4. 앱 사용 증감
    val topIncreasedApp: AppChange?,
    val topDecreasedApp: AppChange?,

    // 5. 한도 도달 선택 기록
    val more15MinCount: Int = 0,
    val stopForTodayCount: Int = 0,

    // 6. 지수 중심 요약: 직전 4주 평균 대비 차이, 지수를 움직인 이유(하루 평균 P)
    val scoreDiffVsPriorFourWeeks: Double? = null,
    val causeBreakdownPerDay: CoreIndexBreakdown? = null
)

object WeeklyReportCalculator {

    /**
     * 특정 날짜 기준으로 지난주 (월~일) 날짜 범위를 반환합니다.
     * referenceDate가 월요일이라면 직전 주 월~일을 반환합니다.
     */
    fun getLastWeekRange(referenceDate: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
        val lastSunday = referenceDate.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY))
        val lastMonday = lastSunday.minusDays(6)
        return lastMonday to lastSunday
    }

    fun getPriorWeekRange(lastMonday: LocalDate): Pair<LocalDate, LocalDate> {
        return lastMonday.minusDays(7) to lastMonday.minusDays(1)
    }

    fun calculate(
        lastWeekRange: Pair<LocalDate, LocalDate>,
        histories: Map<String, DailyScoreHistoryEntity>,
        dailyAppUsage: List<DailyAppUsageEntity>,
        dailyGoalRecords: List<DailyGoalDayRecord> = emptyList(),
        rewardState: GoalRewardState = GoalRewardState(),
        more15MinCount: Int = 0,
        stopForTodayCount: Int = 0,
        /** 지난주(월 05:00 ~ 다음 월 05:00)의 시간별 감점·회복 */
        weekImpacts: List<com.digitscore.app.data.entity.ScoreImpactHourEntity> = emptyList(),
        isManaged: (String) -> Boolean = { false }
    ): WeeklyReportData {
        val (lastMon, lastSun) = lastWeekRange
        val (priorMon, priorSun) = getPriorWeekRange(lastMon)

        val lastWeekDates = generateSequence(lastMon) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastSun) }
            .map { it.toString() }
            .toList()

        val priorWeekDates = generateSequence(priorMon) { it.plusDays(1) }
            .takeWhile { !it.isAfter(priorSun) }
            .map { it.toString() }
            .toList()

        val lastWeekHistories = lastWeekDates.mapNotNull { histories[it] }
        val priorWeekHistories = priorWeekDates.mapNotNull { histories[it] }

        val hasData = lastWeekHistories.isNotEmpty()
        val recordedDays = lastWeekHistories.size

        // 1. 코어 지수 평균 및 증감
        val avgScore = if (lastWeekHistories.isNotEmpty()) {
            lastWeekHistories.map { it.finalScore }.average()
        } else null

        val priorAvgScore = if (priorWeekHistories.isNotEmpty()) {
            priorWeekHistories.map { it.finalScore }.average()
        } else null

        val scoreDiff = if (avgScore != null && priorAvgScore != null) {
            avgScore - priorAvgScore
        } else null

        // 직전 4주(28일) 평균 대비
        val priorFourWeekScores = (1..28).mapNotNull { histories[lastMon.minusDays(it.toLong()).toString()]?.finalScore }
        val scoreDiffFourWeeks = if (avgScore != null && priorFourWeekScores.isNotEmpty()) {
            avgScore - priorFourWeekScores.average()
        } else null

        val highestDay = lastWeekHistories.maxByOrNull { it.finalScore }?.let {
            it.dateString to it.finalScore
        }
        val lowestDay = lastWeekHistories.minByOrNull { it.finalScore }?.let {
            it.dateString to it.finalScore
        }

        // 2. 화면 시간 및 잠금 해제 일평균
        val avgScreen = if (recordedDays > 0) {
            lastWeekHistories.sumOf { it.totalScreenTimeMinutes } / recordedDays
        } else 0L

        val priorRecordedDays = priorWeekHistories.size
        val priorAvgScreen = if (priorRecordedDays > 0) {
            priorWeekHistories.sumOf { it.totalScreenTimeMinutes } / priorRecordedDays
        } else 0L

        val avgUnlock = if (recordedDays > 0) {
            lastWeekHistories.sumOf { it.unlockCount } / recordedDays
        } else 0

        val priorAvgUnlock = if (priorRecordedDays > 0) {
            priorWeekHistories.sumOf { it.unlockCount } / priorRecordedDays
        } else 0

        // 짧은 확인 일평균
        val lastWeekApps = dailyAppUsage.filter { it.dateString in lastWeekDates }
        val priorWeekApps = dailyAppUsage.filter { it.dateString in priorWeekDates }

        val lastWeekShortOpens = lastWeekApps.sumOf { it.shortSessionCount }
        val avgShortOpens = if (recordedDays > 0) lastWeekShortOpens / recordedDays else 0

        val priorWeekShortOpens = priorWeekApps.sumOf { it.shortSessionCount }
        val priorAvgShortOpens = if (priorRecordedDays > 0) priorWeekShortOpens / priorRecordedDays else 0

        val screenTimeDiff = if (priorRecordedDays > 0) avgScreen - priorAvgScreen else null
        val unlockDiff = if (priorRecordedDays > 0) avgUnlock - priorAvgUnlock else null
        val shortSessionDiff = if (priorRecordedDays > 0) avgShortOpens - priorAvgShortOpens else null

        // 앱별 사용량 증감: 기록된 날 기준 하루 평균으로 비교, 양쪽 모두 3일 이상 기록 시에만 노출
        val (topIncreased, topDecreased) = if (recordedDays >= 3 && priorRecordedDays >= 3) {
            val lastAppUsageByPkg = lastWeekApps.groupBy { it.packageName }.mapValues { (_, records) ->
                records.first().appName to (records.sumOf { it.usageMillis } / 60_000L)
            }
            val priorAppUsageByPkg = priorWeekApps.groupBy { it.packageName }.mapValues { (_, records) ->
                records.first().appName to (records.sumOf { it.usageMillis } / 60_000L)
            }
            val allPkgs = (lastAppUsageByPkg.keys + priorAppUsageByPkg.keys).distinct()
            val appDiffs = allPkgs.map { pkg ->
                val lastTotal = lastAppUsageByPkg[pkg]?.second ?: 0L
                val priorTotal = priorAppUsageByPkg[pkg]?.second ?: 0L
                val lastAvg = kotlin.math.round(lastTotal.toDouble() / recordedDays).toLong()
                val priorAvg = kotlin.math.round(priorTotal.toDouble() / priorRecordedDays).toLong()
                val appName = lastAppUsageByPkg[pkg]?.first ?: priorAppUsageByPkg[pkg]?.first ?: pkg
                AppChange(packageName = pkg, appName = appName, diffMinutes = lastAvg - priorAvg)
            }
            val inc = appDiffs.filter { it.diffMinutes > 0 }.maxByOrNull { it.diffMinutes }
            val dec = appDiffs.filter { it.diffMinutes < 0 }.minByOrNull { it.diffMinutes }
            inc to dec
        } else {
            null to null
        }

        val weekGoalRecords = dailyGoalRecords.filter { it.date in lastWeekDates }
        val hasGoalHistory = weekGoalRecords.isNotEmpty()
        val scoreGoalMetDays = weekGoalRecords.count { it.scoreMet }
        val appGoalMetDays = weekGoalRecords.count { it.appMet }
        val unlockGoalMetDays = weekGoalRecords.count { it.unlockMet }
        val perfectDays = weekGoalRecords.count { it.perfect }
        val stampsEarned = weekGoalRecords.sumOf { it.stampEarned }
        val passesEarned = weekGoalRecords.sumOf { it.passEarned }

        return WeeklyReportData(
            weekStartDate = lastMon.toString(),
            weekEndDate = lastSun.toString(),
            hasData = hasData,
            recordedDaysCount = recordedDays,
            avgScore = avgScore,
            scoreDiffVsPriorWeek = scoreDiff,
            highestScoreDay = highestDay,
            lowestScoreDay = lowestDay,
            hasGoalHistory = hasGoalHistory,
            scoreGoalMetDays = scoreGoalMetDays,
            appGoalMetDays = appGoalMetDays,
            unlockGoalMetDays = unlockGoalMetDays,
            achievedDaysCount = scoreGoalMetDays,
            perfectDaysCount = perfectDays,
            perfectStampsEarned = stampsEarned,
            freePassesEarned = passesEarned,
            avgScreenTimeMinutes = avgScreen,
            screenTimeDiffMinutes = screenTimeDiff,
            avgUnlockCount = avgUnlock,
            unlockDiffCount = unlockDiff,
            avgShortSessionCount = avgShortOpens,
            shortSessionDiffCount = shortSessionDiff,
            topIncreasedApp = topIncreased,
            topDecreasedApp = topDecreased,
            more15MinCount = more15MinCount,
            stopForTodayCount = stopForTodayCount,
            scoreDiffVsPriorFourWeeks = scoreDiffFourWeeks,
            causeBreakdownPerDay = if (weekImpacts.isNotEmpty() && recordedDays > 0) {
                CoreIndexBreakdown.from(weekImpacts, isManaged).let { b ->
                    val d = recordedDays.toDouble()
                    CoreIndexBreakdown(b.managedApps / d, b.otherApps / d, b.unlocks / d, b.rest / d,
                        b.perApp.map { it.first to it.second / d })
                }
            } else null
        )
    }
}
