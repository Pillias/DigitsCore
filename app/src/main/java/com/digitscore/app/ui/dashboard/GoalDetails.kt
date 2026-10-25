package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digitscore.app.data.DailyGoalStore
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.Text
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreGreenText
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRedText
import com.digitscore.app.ui.theme.ScoreYellowText
import com.digitscore.app.ui.theme.tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 오늘의 목표 3개 중 하나. */
internal enum class GoalKind { SCORE, APP, UNLOCK }

/** 최근 하루의 기록: 값이 없으면(기록 없는 날) null. */
internal data class GoalTrendDay(val date: LocalDate, val value: Int?, val achieved: Boolean?)

/** 지난 7일(오늘 제외) 목표 기록. 기준은 현재 목표값입니다. */
internal fun goalTrend(
    kind: GoalKind,
    today: LocalDate,
    target: Int,
    scoreByDate: Map<String, Int>,
    unlocksByDate: Map<String, Int>,
    appMinutesByDate: Map<String, Int>
): List<GoalTrendDay> = (7 downTo 1).map { back ->
    val date = today.minusDays(back.toLong())
    val key = date.toString()
    val value = when (kind) {
        GoalKind.SCORE -> scoreByDate[key]
        GoalKind.UNLOCK -> unlocksByDate[key]
        // 그날 기록이 있는데 앱 사용 행이 없으면 0분으로 봅니다.
        GoalKind.APP -> appMinutesByDate[key] ?: if (scoreByDate.containsKey(key)) 0 else null
    }
    val achieved = value?.let { if (kind == GoalKind.SCORE) it >= target else it <= target }
    GoalTrendDay(date, value, achieved)
}

@Composable
internal fun GoalItemDetailDialog(
    kind: GoalKind,
    currentScore: Int,
    targetScore: Int,
    appName: String?,
    appPackage: String?,
    appMins: Int,
    appLimit: Int,
    unlockCount: Int,
    unlockLimit: Int,
    onDismiss: () -> Unit,
    onOpenUnlockAnalysis: () -> Unit
) {
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    val target = when (kind) {
        GoalKind.SCORE -> targetScore
        GoalKind.APP -> appLimit
        GoalKind.UNLOCK -> unlockLimit
    }
    val trend by produceState(initialValue = emptyList<GoalTrendDay>(), kind, target, appPackage) {
        value = withContext(Dispatchers.IO) {
            val db = DigitsDatabase.getInstance(context)
            val today = runCatching { LocalDate.parse(DailyGoalStore.getLogicalDateString()) }.getOrElse { LocalDate.now() }
            val histories = db.scoreDao().getRecentCoreIndexHistories(10)
            val scores = histories.associate { it.dateString to it.finalScore }
            val unlocks = histories.associate { it.dateString to it.unlockCount }
            val appMinutes = if (kind == GoalKind.APP && appPackage != null) {
                db.dailyAppUsageDao().getForPackageSince(appPackage, today.minusDays(7).toString())
                    .associate { it.dateString to (it.usageMillis / 60_000L).toInt() }
            } else emptyMap()
            goalTrend(kind, today, target, scores, unlocks, appMinutes)
        }
    }

    val title = when (kind) {
        GoalKind.SCORE -> if (isEn) "Score floor" else "지수 방어선"
        GoalKind.APP -> if (isEn) "App limit${appName?.let { " · $it" } ?: ""}" else "관리할 앱 한도${appName?.let { " · $it" } ?: ""}"
        GoalKind.UNLOCK -> if (isEn) "Unlock limit" else "잠금 해제 한도"
    }
    val status = when (kind) {
        GoalKind.SCORE -> if (currentScore >= targetScore) {
            if (isEn) "Now ${currentScore}P · ${currentScore - targetScore}P above the floor (${targetScore}P)"
            else "지금 ${currentScore}P · 방어선(${targetScore}P)보다 ${currentScore - targetScore}P 높음"
        } else {
            if (isEn) "Now ${currentScore}P · ${targetScore - currentScore}P below the floor (${targetScore}P)"
            else "지금 ${currentScore}P · 방어선(${targetScore}P)까지 ${targetScore - currentScore}P 필요"
        }
        GoalKind.APP -> if (appName == null) {
            if (isEn) "No app selected" else "아직 앱을 고르지 않았습니다"
        } else if (appMins <= appLimit) {
            if (isEn) "${appMins}min used · ${appLimit - appMins}min left" else "${appMins}min 사용 · ${appLimit - appMins}min 남음"
        } else {
            if (isEn) "${appMins}min used · ${appMins - appLimit}min over" else "${appMins}min 사용 · ${appMins - appLimit}min 초과"
        }
        GoalKind.UNLOCK -> if (unlockCount <= unlockLimit) {
            if (isEn) "$unlockCount unlocks · ${unlockLimit - unlockCount} left" else "${unlockCount}회 · ${unlockLimit - unlockCount}회 남음"
        } else {
            if (isEn) "$unlockCount unlocks · ${unlockCount - unlockLimit} over" else "${unlockCount}회 · ${unlockCount - unlockLimit}회 초과"
        }
    }
    val achievedNow = when (kind) {
        GoalKind.SCORE -> currentScore >= targetScore
        GoalKind.APP -> appName != null && appMins <= appLimit
        GoalKind.UNLOCK -> unlockCount <= unlockLimit
    }
    val criterion = when (kind) {
        GoalKind.SCORE -> if (isEn)
            "Met when the day's last Core Index (just before 05:00 the next morning) is at or above ${targetScore}P. A dip during the day is fine if you recover by the end."
        else "하루의 마지막 코어 지수(다음 날 05:00 직전 값)가 ${targetScore}P 이상이면 달성입니다. 하루 중 잠시 내려가도 마지막에 회복하면 달성으로 봅니다."
        GoalKind.APP -> if (isEn)
            "Met when ${appName ?: "the chosen app"} is used ${appLimit}min or less today (from 05:00). Other apps do not count toward this goal."
        else "${appName ?: "고른 앱"}의 오늘(05:00부터) 사용 시간이 ${appLimit}min 이하이면 달성입니다. 다른 앱 사용은 이 목표에 들어가지 않습니다."
        GoalKind.UNLOCK -> if (isEn)
            "Met when you unlock the screen ${unlockLimit} times or fewer today (from 05:00). Turning the screen on without unlocking is not counted."
        else "오늘(05:00부터) 화면 잠금을 푼 횟수가 ${unlockLimit}회 이하이면 달성입니다. 잠금을 풀지 않고 화면만 켠 경우는 세지 않습니다."
    }
    val howTo = when (kind) {
        GoalKind.SCORE -> if (isEn) listOf(
            "Stop managed apps before 30min in a row to avoid faster deductions.",
            "Screen-off breaks restore the score; an evening break helps the last score most.",
            "Study and tool apps do not lower the score within their limits."
        ) else listOf(
            "관리 앱은 연속 30min이 되기 전에 끊어 쓰면 감점이 빨라지지 않습니다.",
            "화면을 끄고 쉬면 점수가 회복됩니다. 저녁 휴식이 마지막 점수에 가장 크게 반영됩니다.",
            "권장·필수 앱은 정해진 시간 안에서는 점수를 낮추지 않습니다."
        )
        GoalKind.APP -> if (isEn) listOf(
            "Decide how long you will use it before opening the app.",
            "You get a notification at 80% of the limit.",
            "Moving the app off the first home screen reduces habitual opens."
        ) else listOf(
            "앱을 열기 전에 쓸 시간을 먼저 정해 보세요.",
            "한도의 80%에 도달하면 알림으로 알려 줍니다.",
            "앱을 홈 화면 첫 페이지에서 옮기면 무심코 여는 횟수가 줄어듭니다."
        )
        GoalKind.UNLOCK -> if (isEn) listOf(
            "If you have many quick checks (closed within 7s), check notifications in batches.",
            "You get a notification at 80% of the limit.",
            "See which hours have the most unlocks in the unlock analysis."
        ) else listOf(
            "7초 안에 끄는 짧은 확인이 많다면 알림을 모아서 확인해 보세요.",
            "한도의 80%에 도달하면 알림으로 알려 줍니다.",
            "잠금 해제 분석에서 횟수가 많은 시간대를 확인할 수 있습니다."
        )
    }
    val unit = when (kind) {
        GoalKind.SCORE -> "P"
        GoalKind.APP -> "min"
        GoalKind.UNLOCK -> if (isEn) "" else "회"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    status,
                    fontSize = DigitsTextSize.body,
                    fontWeight = FontWeight.Bold,
                    color = if (achievedNow) ScoreGreenText else ScoreYellowText
                )
                GoalSection(if (isEn) "Criterion" else "기준") {
                    Text(criterion, fontSize = DigitsTextSize.body, color = MaterialTheme.colorScheme.onSurface)
                }
                GoalSection(if (isEn) "How to achieve" else "달성 방법") {
                    howTo.forEach { line ->
                        Text("• $line", fontSize = DigitsTextSize.body, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                GoalSection(if (isEn) "Last 7 days" else "최근 7일") {
                    val recorded = trend.filter { it.achieved != null }
                    val met = recorded.count { it.achieved == true }
                    Text(
                        if (recorded.isEmpty()) (if (isEn) "No records yet." else "아직 기록이 없습니다.")
                        else if (isEn) "Met on $met of ${recorded.size} recorded days (current goal ${target}${unit})"
                        else "기록 ${recorded.size}일 중 ${met}일 달성 (현재 목표 ${target}${unit} 기준)",
                        fontSize = DigitsTextSize.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (recorded.isNotEmpty()) {
                        GoalTrendChart(trend, target, higherIsBetter = kind == GoalKind.SCORE)
                    }
                }
            }
        },
        confirmButton = {
            if (kind == GoalKind.UNLOCK) {
                TextButton(onClick = onOpenUnlockAnalysis) { Text(if (isEn) "Unlock analysis" else "잠금 해제 분석") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isEn) "Close" else "닫기") } }
    )
}

@Composable
private fun GoalSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** 7일 막대 + 목표선. 달성일은 초록, 미달성일은 주황, 기록 없는 날은 빈칸. */
@Composable
private fun GoalTrendChart(days: List<GoalTrendDay>, target: Int, higherIsBetter: Boolean) {
    val outline = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.tile
    val maxValue = maxOf(target, days.maxOfOrNull { it.value ?: 0 } ?: 0).coerceAtLeast(1)
    // 점수는 0~99 전체보다 목표 주변을 보여 줍니다.
    val minValue = if (higherIsBetter) (minOf(target, days.minOfOrNull { it.value ?: target } ?: target) - 10).coerceAtLeast(0) else 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.55f
            fun y(v: Int): Float = size.height * (1f - (v - minValue).toFloat() / (maxValue - minValue).coerceAtLeast(1))
            days.forEachIndexed { i, d ->
                val x = slot * i + (slot - barW) / 2f
                // 기록 없는 날은 칸을 비우고, 값이 0인 날도 보이도록 최소 높이를 둡니다.
                if (d.value != null) drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(3.dp.toPx()))
                d.value?.let { v ->
                    val top = y(v).coerceIn(0f, size.height - 3.dp.toPx())
                    drawRoundRect(
                        if (d.achieved == true) ScoreGreen else ScoreOrange,
                        Offset(x, top), Size(barW, size.height - top), CornerRadius(3.dp.toPx())
                    )
                }
            }
            val ty = y(target)
            drawLine(outline, Offset(0f, ty), Offset(size.width, ty), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(
                    "${d.date.monthValue}/${d.date.dayOfMonth}",
                    modifier = Modifier.weight(1f).padding(top = 2.dp),
                    fontSize = DigitsTextSize.micro,
                    color = if (d.achieved == false) ScoreRedText else outline,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
