package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitscore.app.data.WeeklyReportCalculator
import com.digitscore.app.data.WeeklyReportData
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.DailyGoalStore
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.formatMinutesSmallUnit
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.ScoreGreenText
import com.digitscore.app.ui.theme.ScoreRedText
import com.digitscore.app.ui.theme.ScoreYellowText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale

@Composable
fun WeeklyReportDialog(
    onDismiss: () -> Unit,
    /** 상세 통계로 이동 (null이면 버튼을 숨김) */
    onOpenStatistics: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    var reportData by remember { mutableStateOf<WeeklyReportData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        reportData = withContext(Dispatchers.IO) {
            val db = DigitsDatabase.getInstance(context)
            val todayDate = DailyGoalStore.getLogicalDateString()
            val localDate = runCatching { LocalDate.parse(todayDate) }.getOrDefault(LocalDate.now())
            val range = WeeklyReportCalculator.getLastWeekRange(localDate)
            // 직전 4주 평균 비교를 위해 지난주 + 4주(약 35일)를 읽습니다.
            val histories = db.scoreDao().getRecentCoreIndexHistories(42).associateBy { it.dateString }
            val zone = java.time.ZoneId.systemDefault()
            fun dayStart(d: LocalDate) = d.atTime(5, 0).atZone(zone).toInstant().toEpochMilli()
            val weekImpacts = runCatching {
                db.statisticsDao().impactsBetween(dayStart(range.first), dayStart(range.second.plusDays(1)))
            }.getOrDefault(emptyList())
            val managedPkgs = runCatching {
                db.appDao().getAllAppWeights().first()
                    .filter { it.categoryType.canonical == com.digitscore.app.model.AppCategoryType.DISTRACTING }
                    .map { it.packageName }.toSet()
            }.getOrDefault(emptySet())
            val (priorMon, _) = WeeklyReportCalculator.getPriorWeekRange(range.first)
            val dailyApps = db.dailyAppUsageDao().getRange(priorMon.toString(), range.second.toString())
            val rewardState = DailyGoalStore.getRewardState(context)
            val dailyGoalRecords = DailyGoalStore.getDailyGoalDayRecords(context)
            val (more15, stopToday) = com.digitscore.app.data.GoalLimitChoiceStore.getCountsForRange(
                context,
                range.first,
                range.second
            )

            WeeklyReportCalculator.calculate(
                lastWeekRange = range,
                histories = histories,
                dailyAppUsage = dailyApps,
                dailyGoalRecords = dailyGoalRecords,
                rewardState = rewardState,
                more15MinCount = more15,
                stopForTodayCount = stopToday,
                weekImpacts = weekImpacts,
                isManaged = { it in managedPkgs }
            )
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEn) "Weekly Report" else "주간 리포트",
                    fontWeight = FontWeight.Bold,
                    fontSize = DigitsTextSize.titleLarge
                )
                reportData?.let { report ->
                    Text(
                        text = "${report.weekStartDate} ~ ${report.weekEndDate}",
                        fontSize = DigitsTextSize.caption,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                val report = reportData
                if (report == null || !report.hasData) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEn) "No recorded usage data found for the past week." else "지난주에 기록된 사용 데이터가 없습니다.",
                            fontSize = DigitsTextSize.body,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. 코어 지수 요약
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = DigitsShape.md
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isEn) "Core Index" else "코어 지수",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "Daily Final Avg" else "하루 마감 평균",
                                        fontSize = DigitsTextSize.body
                                    )
                                    val avgScoreStr = report.avgScore?.let { String.format(Locale.US, "%.1fP", it) } ?: "--"
                                    Text(
                                        text = avgScoreStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = DigitsTextSize.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // 비교는 하나만: 직전 4주 평균 대비(없으면 그 전 주 대비)
                                val fourWeek = report.scoreDiffVsPriorFourWeeks
                                (fourWeek ?: report.scoreDiffVsPriorWeek)?.let { diff ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (fourWeek != null) {
                                                if (isEn) "vs prior 4 weeks" else "직전 4주 평균 대비"
                                            } else {
                                                if (isEn) "Change vs Prior Week" else "지난주 대비 변화"
                                            },
                                            fontSize = DigitsTextSize.caption,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        val prefix = if (diff >= 0) "+" else ""
                                        val diffColor = if (diff >= 0) ScoreGreenText else ScoreRedText
                                        Text(
                                            text = "$prefix${String.format(Locale.US, "%.1fP", diff)}",
                                            fontSize = DigitsTextSize.caption,
                                            fontWeight = FontWeight.Bold,
                                            color = diffColor
                                        )
                                    }
                                }
                                if (report.highestScoreDay != null && report.lowestScoreDay != null) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Highest" else "최고점", fontSize = DigitsTextSize.caption)
                                        Text(text = "${report.highestScoreDay.first} (${report.highestScoreDay.second}P)", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Medium)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Lowest" else "최저점", fontSize = DigitsTextSize.caption)
                                        Text(text = "${report.lowestScoreDay.first} (${report.lowestScoreDay.second}P)", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // 1-1. 지수를 움직인 이유 (하루 평균 P)
                        report.causeBreakdownPerDay?.let { cause ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = DigitsShape.md
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (isEn) "What Moved the Index (daily avg)" else "지수를 움직인 이유 (하루 평균)",
                                        fontSize = DigitsTextSize.label,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    listOf(
                                        (if (isEn) "App use" else "앱 사용") to cause.appUse,
                                        (if (isEn) "Unlocks" else "잠금 해제") to cause.unlocks,
                                        (if (isEn) "Rest" else "쉼") to cause.rest
                                    ).forEach { (name, value) ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = name, fontSize = DigitsTextSize.caption)
                                            val sign = if (value > 0.05) "+" else ""
                                            Text(
                                                text = "$sign${String.format(Locale.US, "%.1fP", value)}",
                                                fontSize = DigitsTextSize.caption,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    value <= -0.05 -> ScoreRedText
                                                    value >= 0.05 -> ScoreGreenText
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. 오늘의 목표 달성
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = DigitsShape.md
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isEn) "Daily Goals" else "오늘의 목표",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (!report.hasGoalHistory) {
                                    Text(
                                        text = if (isEn) "No record" else "기록 없음",
                                        fontSize = DigitsTextSize.body,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Defense Line Met" else "지수 방어선 달성", fontSize = DigitsTextSize.body)
                                        Text(text = if (isEn) "${report.achievedDaysCount} / ${report.recordedDaysCount} days" else "${report.achievedDaysCount} / ${report.recordedDaysCount}일", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "All Goals Kept" else "모두 지킨 날", fontSize = DigitsTextSize.body)
                                        Text(text = if (isEn) "${report.perfectDaysCount} days" else "${report.perfectDaysCount}일", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Stamps / Shields" else "받은 스탬프 / 보호권", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            text = if (isEn) "${report.perfectStampsEarned} stamps · ${report.freePassesEarned} shields" else "${report.perfectStampsEarned}개 · ${report.freePassesEarned}장",
                                            fontSize = DigitsTextSize.caption,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        // 3. 화면 시간 / 잠금 해제 / 짧은 확인
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = DigitsShape.md
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isEn) "Screen Time & Unlock Activity" else "화면 시간과 잠금 해제",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "Daily Screen Time" else "하루 화면 시간", fontSize = DigitsTextSize.body)
                                    Text(text = formatMinutesSmallUnit(report.avgScreenTimeMinutes), fontSize = DigitsTextSize.body, fontWeight = FontWeight.Bold)
                                }
                                report.screenTimeDiffMinutes?.let { diff ->
                                    val prefix = if (diff >= 0) "+" else ""
                                    val diffColor = if (diff <= 0) ScoreGreenText else ScoreRedText
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Change vs Prior Week" else "지난주 대비 변화", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                        Text(text = "$prefix${diff}min", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold, color = diffColor)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "Daily Unlocks" else "하루 잠금 해제", fontSize = DigitsTextSize.body)
                                    Text(text = "${report.avgUnlockCount}${com.digitscore.app.i18n.countUnit()}", fontSize = DigitsTextSize.body, fontWeight = FontWeight.Bold)
                                }
                                report.unlockDiffCount?.let { diff ->
                                    val prefix = if (diff >= 0) "+" else ""
                                    val diffColor = if (diff <= 0) ScoreGreenText else ScoreRedText
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Change vs Prior Week" else "지난주 대비 변화", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                        Text(text = "$prefix${diff}${com.digitscore.app.i18n.countUnit()}", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold, color = diffColor)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "Daily Quick Glances" else "하루 짧은 확인", fontSize = DigitsTextSize.body)
                                    Text(text = "${report.avgShortSessionCount}${com.digitscore.app.i18n.countUnit()}", fontSize = DigitsTextSize.body, fontWeight = FontWeight.Bold)
                                }
                                report.shortSessionDiffCount?.let { diff ->
                                    val prefix = if (diff >= 0) "+" else ""
                                    val diffColor = if (diff <= 0) ScoreGreenText else ScoreRedText
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Change vs Prior Week" else "지난주 대비 변화", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                        Text(text = "$prefix${diff}${com.digitscore.app.i18n.countUnit()}", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold, color = diffColor)
                                    }
                                }
                            }
                        }

                        // 4. 앱 사용 증감
                        if (report.topIncreasedApp != null || report.topDecreasedApp != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = DigitsShape.md
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (isEn) "Top Changed Apps" else "앱 사용 변화",
                                        fontSize = DigitsTextSize.label,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    report.topIncreasedApp?.let { app ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = if (isEn) "Most Increased: ${app.appName}" else "가장 많이 늘어난 앱: ${app.appName}", fontSize = DigitsTextSize.caption)
                                            Text(text = "+${app.diffMinutes}min", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold, color = ScoreRedText)
                                        }
                                    }
                                    report.topDecreasedApp?.let { app ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = if (isEn) "Most Decreased: ${app.appName}" else "가장 많이 줄어든 앱: ${app.appName}", fontSize = DigitsTextSize.caption)
                                            Text(text = "${app.diffMinutes}min", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold, color = ScoreGreenText)
                                        }
                                    }
                                }
                            }
                        }

                        // 5. 한도 도달 선택 기록
                        if (report.more15MinCount > 0 || report.stopForTodayCount > 0) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = DigitsShape.md
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (isEn) "Limit Decision Choices" else "한도 도달 선택 기록",
                                        fontSize = DigitsTextSize.label,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "15min More" else "15min 더", fontSize = DigitsTextSize.caption)
                                        Text(text = "${report.more15MinCount}${com.digitscore.app.i18n.countUnit()}", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (isEn) "Done for Today" else "오늘은 여기까지", fontSize = DigitsTextSize.caption)
                                        Text(text = "${report.stopForTodayCount}${com.digitscore.app.i18n.countUnit()}", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "닫기")
            }
        },
        dismissButton = onOpenStatistics?.let { open ->
            {
                TextButton(onClick = { onDismiss(); open() }) {
                    Text(if (isEn) "View statistics" else "상세 통계 보기")
                }
            }
        }
    )
}
