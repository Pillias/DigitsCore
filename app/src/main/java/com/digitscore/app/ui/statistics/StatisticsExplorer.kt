package com.digitscore.app.ui.statistics

import com.digitscore.app.ui.theme.ScoreRedText
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.ScoreYellowText
import com.digitscore.app.ui.theme.ScoreGreenText
import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.digitscore.app.data.*
import com.digitscore.app.data.entity.*
import com.digitscore.app.ui.components.ResponsiveContent
import com.digitscore.app.ui.components.coreIndexTierColor
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.digitscore.app.i18n.AppLocale
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun label(ko: String, en: String) = if (AppLocale.isCurrentEnglish) en else ko
private fun dateLabel(time: Long, pattern: String = "MM/dd HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
private fun minutes(ms: Long): String = "${ms / 60_000}min"
private fun points(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

private fun weekdayName(index: Int, isEn: Boolean): String {
    val ko = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")
    val en = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val idx = index.coerceIn(0, 6)
    return if (isEn) en[idx] else ko[idx]
}

internal fun calculateMovingAverages(buckets: List<ExplorerBucket>, period: Int): List<Double?> {
    return buckets.indices.map { i ->
        val window = buckets.subList(maxOf(0, i - period + 1), i + 1).mapNotNull { it.score?.last }
        if (window.isNotEmpty()) window.average() else null
    }
}
private fun formatBucketAxisLabel(start: Long, isHourly: Boolean, isDay: Boolean, isRolling: Boolean = false, isWeekday: Boolean = false): String {
    val zone = ZoneId.systemDefault()
    val isKo = !AppLocale.isCurrentEnglish
    if (isWeekday) {
        val koList = listOf("월", "화", "수", "목", "금", "토", "일")
        val enList = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val idx = start.toInt().coerceIn(0, 6)
        return if (isKo) koList[idx] else enList[idx]
    }
    val time = Instant.ofEpochMilli(start).atZone(zone)
    return when {
        isRolling -> {
            if (time.hour == 5) {
                if (isKo) "${time.monthValue}/${time.dayOfMonth}" else time.format(DateTimeFormatter.ofPattern("M/d"))
            } else {
                time.format(DateTimeFormatter.ofPattern("HH:mm"))
            }
        }
        isDay -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
        isHourly -> if (isKo) {
            "${time.monthValue}/${time.dayOfMonth} ${time.hour}시"
        } else {
            time.format(DateTimeFormatter.ofPattern("M/d ha"))
        }
        else -> time.format(DateTimeFormatter.ofPattern("MM/dd"))
    }
}

enum class ExplorerViewMode {
    ROLLING_24H,
    PERIOD,
    WEEKDAY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onNavigateBack: () -> Unit, database: DigitsDatabase? = null) {
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    val db = remember(database) { database ?: DigitsDatabase.getInstance(context) }
    val dao = remember { db.statisticsDao() }
    val zone = ZoneId.systemDefault()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var viewMode by rememberSaveable { mutableStateOf(ExplorerViewMode.ROLLING_24H) }
    var hourlyView by rememberSaveable { mutableStateOf(false) }
    var detailed by rememberSaveable { mutableStateOf(true) }
    var showUnlocks by rememberSaveable { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<Long?>(null) }
    var selectedApp by remember { mutableStateOf<ExplorerApp?>(null) }
    var ranking by rememberSaveable { mutableIntStateOf(0) }
    var allApps by remember(viewMode, selectedTime) { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showHelp by remember { mutableStateOf(false) }
    val chartScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val handleBack: () -> Unit = {
        when {
            selectedApp != null -> selectedApp = null
            selectedTime != null -> selectedTime = null
            viewMode != ExplorerViewMode.ROLLING_24H -> {
                viewMode = ExplorerViewMode.ROLLING_24H
            }
            else -> onNavigateBack()
        }
    }

    BackHandler(onBack = handleBack)
    val liveScore by ScoreRepository.rollingScoreDetail.collectAsState()
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }

    val isRolling = viewMode == ExplorerViewMode.ROLLING_24H
    val isPeriod = viewMode == ExplorerViewMode.PERIOD
    val isWeekday = viewMode == ExplorerViewMode.WEEKDAY

    val (start, end) = when (viewMode) {
        ExplorerViewMode.ROLLING_24H -> {
            val rollingEnd = now
            val rollingStart = now - 30L * 24 * STAT_HOUR
            rollingStart to rollingEnd
        }
        ExplorerViewMode.PERIOD, ExplorerViewMode.WEEKDAY -> {
            val periodEnd = now
            val periodStart = logicalDayStartMillis(logicalDate(periodEnd, zone).minusDays(27L), zone)
            periodStart to periodEnd
        }
    }
    val daily = (isPeriod || isWeekday) && !hourlyView
    val queryStart = statisticHour(start)
    val queryEnd = statisticHour(end) + STAT_HOUR
    val needIntraday = isRolling
    val usage by remember(queryStart, queryEnd) { dao.observeUsage(queryStart, queryEnd) }.collectAsState(emptyList())
    val hourlyScores by remember(queryStart, queryEnd) { dao.observeScores(queryStart, queryEnd) }.collectAsState(emptyList())
    val impacts by remember(queryStart, queryEnd) { dao.observeImpacts(queryStart, queryEnd) }.collectAsState(emptyList())
    val samples by remember(queryStart, queryEnd, needIntraday) {
        if (needIntraday) db.coreIndexSampleDao().observeBetween(queryStart, queryEnd) else flowOf(emptyList())
    }.collectAsState(emptyList())
    val sessions by remember(queryStart, queryEnd, needIntraday) {
        if (needIntraday) db.foregroundUsageSessionDao().observeBetween(queryStart, queryEnd + 120_000) else flowOf(emptyList())
    }.collectAsState(emptyList())
    val dailyApps by remember(start, end) {
        db.dailyAppUsageDao().observeRange(dateLabel(start, "yyyy-MM-dd"), dateLabel(end - 1, "yyyy-MM-dd"))
    }.collectAsState(emptyList())
    val dailyInteractions by remember(start, end) {
        db.appDailyInteractionDao().observeByDateRange(dateLabel(start, "yyyy-MM-dd"), dateLabel(end, "yyyy-MM-dd"))
    }.collectAsState(emptyList())
    val histories by db.scoreDao().getAllScoreHistories().collectAsState(emptyList())
    val rawAvailable = needIntraday && start >= now - 30L * 24 * STAT_HOUR
    val exactInteractions = needIntraday
    val events by remember(start, end, exactInteractions) {
        if (exactInteractions) {
            val eventQueryStart = maxOf(start - 15_000, now - 30L * 24 * STAT_HOUR)
            db.deviceInteractionEventDao().observeBetween(eventQueryStart, end)
        } else flowOf(emptyList())
    }.collectAsState(emptyList())
    val visibleUsage = remember(usage, sessions, events, start, end, rawAvailable) {
        if (!rawAvailable) usage else {
            val raw = aggregateUsageHours(sessions, start, end, now - 120_000)
            val interactions = usage.filter { it.packageName.isEmpty() }.associateBy { it.hour }
            val combined = raw.associateBy { it.hour to it.packageName }.toMutableMap()
            val unlockCounts = UsageStatsHelper.resolvedUnlockTimestamps(events).filter { it >= start && it < end }
                .groupingBy { statisticHour(it) }.eachCount()
            val habitualCounts = UsageStatsHelper.resolvedHabitualUnlockTimestamps(events, sessions).filter { it >= start && it < end }
                .groupingBy { statisticHour(it) }.eachCount()
            val notificationCounts = events.filter { it.timestampMillis >= start && it.eventType == DeviceInteractionEventEntity.NOTIFICATION_INTERRUPTION }
                .groupingBy { statisticHour(it.timestampMillis) }.eachCount()
            interactions.forEach { (hour, row) ->
                val key = hour to ""
                val unlockVal = if (unlockCounts.containsKey(hour)) unlockCounts[hour] ?: 0 else row.unlocks
                val habitualVal = if (habitualCounts.containsKey(hour)) habitualCounts[hour] ?: 0 else row.shortOpens
                val notifVal = if (notificationCounts.containsKey(hour)) notificationCounts[hour] ?: 0 else row.notifications
                combined[key] = (combined[key] ?: UsageHourEntity(hour, "", "")).copy(
                    unlocks = unlockVal,
                    shortOpens = habitualVal,
                    notifications = notifVal
                )
            }
            (unlockCounts.keys + habitualCounts.keys + notificationCounts.keys).forEach { hour ->
                val key = hour to ""
                combined[key] = (combined[key] ?: UsageHourEntity(hour, "", "")).copy(
                    unlocks = unlockCounts[hour] ?: combined[key]?.unlocks ?: 0,
                    shortOpens = habitualCounts[hour] ?: combined[key]?.shortOpens ?: 0,
                    notifications = notificationCounts[hour] ?: combined[key]?.notifications ?: 0
                )
            }
            combined.values.toList()
        }
    }
    val visibleSamples = remember(samples, start, end) { samples.filter { it.timestampMillis in start..end } }
    // Partial-hour attribution is not prorated: its observed coverage is shown explicitly.
    val visibleImpacts = remember(impacts, start, end) { impacts.filter { it.firstAt >= start && it.lastAt < end } }
    val rawBuckets = remember(start, end, daily, visibleUsage, hourlyScores, visibleImpacts, histories) {
        explorerBuckets(start, end, daily, visibleUsage, hourlyScores, visibleImpacts, histories)
    }
    val buckets = remember(rawBuckets, isWeekday, zone) {
        if (isWeekday) explorerWeekdayBuckets(rawBuckets, zone) else rawBuckets
    }
    val selected = selectedTime?.let { t ->
        buckets.firstOrNull { if (isWeekday) it.start == t else (t >= it.start && t < it.end) }
    }
    val selectedDayOfWeek = if (isWeekday && selected != null) DayOfWeek.of(selected.start.toInt() + 1) else null
    val filteredUsageForApps = remember(visibleUsage, isRolling, now, isWeekday, selectedDayOfWeek, zone) {
        when {
            isRolling -> visibleUsage.filter { it.hour >= statisticHour(now - 24 * STAT_HOUR) }
            isWeekday && selectedDayOfWeek != null -> visibleUsage.filter {
                logicalDate(it.hour, zone).dayOfWeek == selectedDayOfWeek
            }
            else -> visibleUsage
        }
    }
    val filteredImpactsForApps = remember(visibleImpacts, isRolling, now, isWeekday, selectedDayOfWeek, zone) {
        when {
            isRolling -> visibleImpacts.filter { it.firstAt >= now - 24 * STAT_HOUR }
            isWeekday && selectedDayOfWeek != null -> visibleImpacts.filter {
                logicalDate(it.firstAt, zone).dayOfWeek == selectedDayOfWeek
            }
            else -> visibleImpacts
        }
    }
    val filteredDailyApps = remember(dailyApps, isWeekday, selectedDayOfWeek) {
        if (isWeekday && selectedDayOfWeek != null) {
            dailyApps.filter { runCatching { LocalDate.parse(it.dateString).dayOfWeek == selectedDayOfWeek }.getOrDefault(false) }
        } else dailyApps
    }
    val apps = remember(filteredUsageForApps, filteredImpactsForApps, filteredDailyApps, dailyInteractions, isRolling, isPeriod, isWeekday) {
        explorerApps(filteredUsageForApps, filteredImpactsForApps, filteredDailyApps, isPeriod || isWeekday, dailyInteractions)
    }
    val selectedSample = if (isRolling && selectedTime != null && selected != null)
        visibleSamples.filter { it.timestampMillis >= selected.start && it.timestampMillis < selected.end }
            .minByOrNull { abs(it.timestampMillis - selectedTime!!) } else null
    val readings = if (isRolling && visibleSamples.isNotEmpty()) visibleSamples.map {
        ScoreHourEntity(statisticHour(it.timestampMillis), it.scoreModelVersion, it.timestampMillis, it.timestampMillis,
            it.exactScore, it.exactScore, it.exactScore, it.exactScore)
    } else buckets.mapNotNull { it.score }
    val latest = readings.lastOrNull()
    val currentModel = latest?.model ?: com.digitscore.app.engine.ScoreModel.CURRENT_STORED
    val comparable = readings.filter { it.model == currentModel }
    val displayed = if (selected != null) selectedSample?.exactScore ?: selected.score?.last
        else if (isRolling && currentModel == com.digitscore.app.engine.ScoreModel.CURRENT_STORED) liveScore?.exactScore ?: latest?.last else latest?.last
    val change = if (selected == null && comparable.isNotEmpty() && displayed != null) {
        val baseScore = if (isRolling) {
            val target24hAgo = now - 24 * STAT_HOUR
            visibleSamples.minByOrNull { abs(it.timestampMillis - target24hAgo) }?.exactScore
                ?: visibleSamples.firstOrNull()?.exactScore ?: comparable.first().first
        } else comparable.first().first
        displayed - baseScore
    } else null
    val coverage = visibleImpacts.filter { it.packageName.isEmpty() }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    label("사용 통계 & 분석", "Usage Analytics"),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, label("뒤로", "Back"))
                }
            }
        )
    }) { padding ->
        ResponsiveContent(Modifier.padding(padding)) {
            LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    // 고정 3줄 상단 HUD (모든 탭에서 동일 높이 및 구성 유지하여 덜컹거림 원천 차단)
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 1줄: 날짜/시간 타이틀 & 점수
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            val titleStr = when {
                                selected != null -> {
                                    when {
                                        isWeekday -> weekdayName(selected.start.toInt(), isEn)
                                        isPeriod -> dateLabel(selected.start, "yy.MM.dd (E)")
                                        else -> "${dateLabel(selected.start, "MM.dd HH:mm")} ~ ${dateLabel(selected.end, "HH:mm")}"
                                    }
                                }
                                isWeekday -> label("4주간 요일별 평균", "4-Week Weekday Average")
                                isPeriod -> "${dateLabel(start, "yy.MM.dd")} ~ ${dateLabel(end, "yy.MM.dd")} (4주)"
                                else -> label("코어 지수 · 실시간", "Core Index · Live")
                            }
                            Text(
                                text = titleStr,
                                fontSize = DigitsTextSize.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                            )

                            val isImportedDay = selected != null && selected.score == null && (selected.usage ?: 0L) > 0L
                            val targetScore = if (selected != null) {
                                selectedSample?.exactScore ?: selected.score?.last ?: 80.0
                            } else if (isRolling) {
                                liveScore?.exactScore ?: latest?.last ?: 80.0
                            } else {
                                buckets.lastOrNull { it.score != null }?.score?.last ?: 80.0
                            }

                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isImportedDay) {
                                    Text(
                                        text = "—",
                                        fontSize = DigitsTextSize.hero,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.testTag("statistics-score")
                                    )
                                } else {
                                    Text(
                                        text = points(targetScore),
                                        fontSize = DigitsTextSize.hero,
                                        fontWeight = FontWeight.Bold,
                                        color = com.digitscore.app.ui.theme.scoreTextColor(targetScore.roundToInt()),
                                        modifier = Modifier.testTag("statistics-score")
                                    )
                                }
                                if (selected != null && isPeriod && !isImportedDay) {
                                    val openS = selected.score?.first ?: targetScore
                                    val diffVal = targetScore - openS
                                    val isUp = diffVal >= 0
                                    Text(
                                        text = "${if (diffVal >= 0) "+" else ""}${points(diffVal)}",
                                        color = if (isUp) ScoreGreenText else ScoreRedText,
                                        fontSize = DigitsTextSize.emphasis,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                } else if (change != null) {
                                    val isUp = change >= 0
                                    Text(
                                        text = "${if (isUp) "+" else ""}${points(change)}",
                                        color = if (isUp) ScoreGreenText else ScoreRedText,
                                        fontSize = DigitsTextSize.emphasis,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                            }
                        }

                        // 2줄: 인라인 요약 바 (선택 시 해당 항목 세부, 미선택 시 탭별 요약)
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), DigitsShape.sm)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected != null) {
                                val s = selected.score
                                val isImportedDay = remember(selected.start) {
                                    val dateStr = logicalDate(selected.start, zone).toString()
                                    com.digitscore.app.data.UsageHistoryImporter.isImportedDate(context, dateStr)
                                }
                                val scoreInfo = if (isImportedDay) {
                                    label("불러온 기록", "Imported history")
                                } else if (s != null) {
                                    if (isRolling || s.low == s.high) label("점수 ${points(s.last)}P", "Score ${points(s.last)}P")
                                    else label("최저 ${points(s.low)} ~ 최고 ${points(s.high)}", "Low ${points(s.low)} ~ High ${points(s.high)}")
                                } else if ((selected.usage ?: 0L) > 0L) {
                                    label("기록 없음", "No score record")
                                } else label("점수 ${points(displayed ?: 80.0)}P", "Score ${points(displayed ?: 80.0)}P")
                                val usageInfo = label("화면 ${minutes(selected.usage ?: 0L)} (관리 ${minutes(selected.managed)})", "Screen ${minutes(selected.usage ?: 0L)} (${minutes(selected.managed)})")
                                val unlockInfo = label("잠금 ${selected.unlocks ?: 0}x (${selected.shortOpens}x)", "Unlocks ${selected.unlocks ?: 0}x (${selected.shortOpens}x)")
                                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
 androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "$scoreInfo · $usageInfo",
                                        fontSize = DigitsTextSize.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Text(
                                        text = unlockInfo,
                                        fontSize = DigitsTextSize.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else {
                                when (viewMode) {
                                    ExplorerViewMode.ROLLING_24H -> {
                                        val windowSamples = visibleSamples.filter { it.timestampMillis >= now - 24 * STAT_HOUR }
                                        val highScore = windowSamples.maxOfOrNull { it.exactScore } ?: (displayed ?: 80.0)
                                        val lowScore = windowSamples.minOfOrNull { it.exactScore } ?: (displayed ?: 80.0)
                                        val rBuckets = buckets.filter { it.start >= now - 24 * STAT_HOUR }
                                        val totalU = rBuckets.sumOf { it.usage ?: 0L }
                                        val totalO = rBuckets.sumOf { it.unlocks ?: 0 }
                                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
 androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(label("24시간 최고 ", "24h High ") + points(highScore), fontSize = DigitsTextSize.caption, color = ScoreGreenText, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                                Text(label("최저 ", "Low ") + points(lowScore), fontSize = DigitsTextSize.caption, color = ScoreRedText, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                            }
                                            Text(
                                                text = label("화면 ${minutes(totalU)} · 잠금 ${totalO}x", "Screen ${minutes(totalU)} · ${totalO}x"),
                                                fontSize = DigitsTextSize.caption,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    ExplorerViewMode.PERIOD -> {
                                        val highScore = buckets.maxOfOrNull { it.score?.high ?: 0.0 } ?: 80.0
                                        val lowScore = buckets.minOfOrNull { it.score?.low ?: 100.0 } ?: 80.0
                                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
 androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(label("4주 최고 ", "4W High ") + points(highScore), fontSize = DigitsTextSize.caption, color = ScoreGreenText, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                                Text(label("최저 ", "Low ") + points(lowScore), fontSize = DigitsTextSize.caption, color = ScoreRedText, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                            }
                                            val activePeriodDays = recordedDayCount(buckets)
                                            val avgUsage = if (buckets.isNotEmpty()) buckets.sumOf { it.usage ?: 0L } / activePeriodDays else 0L
                                            val avgUnlocks = if (buckets.isNotEmpty()) (buckets.sumOf { it.unlocks ?: 0 }.toFloat() / activePeriodDays).roundToInt() else 0
                                            Text(
                                                text = label("일 평균 ${minutes(avgUsage)} · ${avgUnlocks}x", "Daily ${minutes(avgUsage)} · ${avgUnlocks}x"),
                                                fontSize = DigitsTextSize.caption,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    ExplorerViewMode.WEEKDAY -> {
                                        val validBuckets = buckets.filter { (it.score?.last ?: 0.0) > 0.0 }
                                        val bestB = validBuckets.maxByOrNull { it.score?.last ?: 0.0 }
                                        val worstB = validBuckets.minByOrNull { it.score?.last ?: 100.0 }
                                        val bestName = bestB?.let { weekdayName(it.start.toInt(), isEn) } ?: "—"
                                        val worstName = worstB?.let { weekdayName(it.start.toInt(), isEn) } ?: "—"
                                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
 androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(label("최고 ", "Best ") + "$bestName (${points(bestB?.score?.last ?: 0.0)})", fontSize = DigitsTextSize.caption, color = ScoreGreenText, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                                Text(label("주의 ", "Watch ") + "$worstName (${points(worstB?.score?.last ?: 0.0)})", fontSize = DigitsTextSize.caption, color = ScoreRedText, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                            }
                                            val avgUsage = if (validBuckets.isNotEmpty()) validBuckets.sumOf { it.usage ?: 0L } / validBuckets.size else 0L
                                            Text(
                                                text = label("요일 평균 ${minutes(avgUsage)}", "Avg ${minutes(avgUsage)}"),
                                                fontSize = DigitsTextSize.caption,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3줄: 고정 범례 (점·막대 색으로 계열 구분, 글자는 읽기 쉬운 본문 색. 공간이 부족하면 줄바꿈)
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val legendText = MaterialTheme.colorScheme.onSurfaceVariant
                            @Composable
                            fun LegendItem(marker: @Composable () -> Unit, text: String) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    marker()
                                    Text(text, fontSize = DigitsTextSize.tiny, color = legendText, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                }
                            }
                            LegendItem({ Box(Modifier.size(7.dp).background(ScoreYellow, CircleShape)) }, label("코어 지수", "Core Index"))
                            LegendItem({ Box(Modifier.size(7.dp).background(com.digitscore.app.ui.theme.ChartScreen, DigitsShape.bar)) }, label("화면 시간", "Screen time"))
                            LegendItem({ Box(Modifier.size(7.dp).background(com.digitscore.app.ui.theme.ChartManaged, DigitsShape.bar)) }, label("관리 앱", "Managed apps"))
                            LegendItem({ Box(Modifier.size(7.dp).background(com.digitscore.app.ui.theme.ChartUnlock, CircleShape)) }, label("잠금 해제", "Unlocks"))
                            LegendItem({ Box(Modifier.width(9.dp).height(2.dp).background(MaterialTheme.colorScheme.outline)) }, label("기준 75P", "Baseline 75P"))
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = viewMode == ExplorerViewMode.ROLLING_24H,
                            onClick = {
                                viewMode = ExplorerViewMode.ROLLING_24H
                                selectedTime = null
                                zoom = 1f
                            },
                            label = { Text(label("시간별 (30일)", "Hourly (30D)")) }
                        )
                        FilterChip(
                            selected = viewMode == ExplorerViewMode.PERIOD,
                            onClick = {
                                viewMode = ExplorerViewMode.PERIOD
                                selectedTime = null
                                zoom = 1f
                            },
                            label = { Text(label("일별 (4주)", "Daily (4W)")) }
                        )
                        FilterChip(
                            selected = viewMode == ExplorerViewMode.WEEKDAY,
                            onClick = {
                                viewMode = ExplorerViewMode.WEEKDAY
                                selectedTime = null
                                zoom = 1f
                            },
                            label = { Text(label("요일별", "By Weekday")) }
                        )
                    }
                }
                item {
                    ExplorerChart(
                        buckets = buckets,
                        samples = if (isRolling) visibleSamples else emptyList(),
                        line = isRolling,
                        detailed = detailed,
                        unlocks = showUnlocks,
                        start = start,
                        end = end,
                        selection = selectedTime,
                        zoom = zoom,
                        isWeekday = isWeekday,
                        scrollState = chartScrollState,
                        onZoom = { zoom = it.coerceIn(0.5f, 4f) },
                        onSelect = { selectedTime = it }
                    )
                    // 서브바: 기간 날짜 라벨 및 선택 해제 / 최신으로 가기 액션
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val periodLabel = when (viewMode) {
                            ExplorerViewMode.ROLLING_24H -> "${dateLabel(start, "MM/dd")} ~ ${dateLabel(end, "MM/dd HH:mm")}"
                            ExplorerViewMode.PERIOD -> "${dateLabel(start, "yy/MM/dd")} – ${dateLabel(end, "yy/MM/dd")}"
                            ExplorerViewMode.WEEKDAY -> label("최근 4주 요일별 집계", "4-Week Weekday Totals")
                        }
                        Text(
                            text = periodLabel,
                            fontSize = DigitsTextSize.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectedTime != null) {
                                TextButton(
                                    onClick = { selectedTime = null },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(label("선택 해제", "Clear"), fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isRolling) {
                                TextButton(
                                    onClick = {
                                        scope.launch { chartScrollState.animateScrollTo(chartScrollState.maxValue) }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(label("최신으로", "Latest"), fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item {
                    val rolling24hStart = now - 24 * STAT_HOUR
                    val summaryBuckets = if (isRolling) buckets.filter { it.start >= rolling24hStart }
                        else buckets
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (viewMode) {
                                ExplorerViewMode.ROLLING_24H -> label("최근 24시간 패턴 분석", "24-Hour Usage Pattern")
                                ExplorerViewMode.PERIOD -> recordedDayCount(summaryBuckets).let { days ->
                                    label("기간 요약 (기록 ${days}일 기준 일 평균)", "Period summary (daily avg of $days recorded days)")
                                }
                                ExplorerViewMode.WEEKDAY -> label("요일별 요약 (최근 4주 평균)", "Weekday summary (4W Avg)")
                            },
                            fontWeight = FontWeight.Bold
                        )
                        val totalUnlocks = summaryBuckets.sumOf { it.unlocks ?: 0 }
                        val habitualUnlocks = summaryBuckets.sumOf { it.shortOpens }
                        val habitualPercent = if (totalUnlocks > 0) (habitualUnlocks * 100) / totalUnlocks else 0
                        val unlockKnown = summaryBuckets.any { it.unlocks != null } || totalUnlocks > 0
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isRolling) {
                                val peakBucket = summaryBuckets.maxByOrNull { it.usage ?: 0L }
                                val peakHourStr = peakBucket?.let { dateLabel(it.start, "H:00") } ?: "—"
                                val peakUsageMins = ((peakBucket?.usage ?: 0L) / 60_000L).toInt()
                                SmallMetric(
                                    title = label("24시간 화면", "24h Screen"),
                                    value = summaryBuckets.mapNotNull { it.usage }.takeIf { it.isNotEmpty() }?.sum()?.let(::minutes) ?: "—",
                                    subtext = if (peakUsageMins > 0) label("피크 ${peakHourStr} (${peakUsageMins}min)", "Peak ${peakHourStr} (${peakUsageMins}min)") else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                SmallMetric(
                                    title = label("총 잠금 해제", "Total Unlocks"),
                                    value = if (unlockKnown) "${totalUnlocks}x" else "—",
                                    subtext = if (unlockKnown && totalUnlocks > 0) label("시간당 ${(totalUnlocks / 24f).roundToInt()}x", "${(totalUnlocks / 24f).roundToInt()}/hr") else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                SmallMetric(
                                    title = label("짧은 확인", "Quick checks"),
                                    value = if (unlockKnown) "${habitualUnlocks}x" else "—",
                                    subtext = if (unlockKnown && totalUnlocks > 0) label("7초내 종료 ${habitualPercent}%", "≤7s close ${habitualPercent}%") else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            } else if (isPeriod) {
                                val daysCount = recordedDayCount(summaryBuckets)
                                val totalUsage = summaryBuckets.mapNotNull { it.usage }.sum()
                                val dailyAvgUsage = totalUsage / daysCount
                                val dailyAvgUnlocks = (totalUnlocks.toFloat() / daysCount).roundToInt()
                                val dailyAvgHabitual = (habitualUnlocks.toFloat() / daysCount).roundToInt()

                                SmallMetric(
                                    title = label("일 평균 화면", "Daily Screen"),
                                    value = if (summaryBuckets.any { it.usage != null }) minutes(dailyAvgUsage) else "—",
                                    subtext = if (totalUsage > 0) label("총 ${minutes(totalUsage)}", "Total ${minutes(totalUsage)}") else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                SmallMetric(
                                    title = label("일 평균 잠금 해제", "Daily Unlocks"),
                                    value = if (unlockKnown) "${dailyAvgUnlocks}x" else "—",
                                    subtext = if (unlockKnown) label("4주 총 ${totalUnlocks}x", "Total ${totalUnlocks}x") else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                SmallMetric(
                                    title = label("일 평균 짧은 확인", "Daily Quick checks"),
                                    value = if (unlockKnown) "${dailyAvgHabitual}x" else "—",
                                    subtext = if (unlockKnown && totalUnlocks > 0) label("7초내 종료 ${habitualPercent}%", "≤7s close ${habitualPercent}%") else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            } else {
                                val validBuckets = summaryBuckets.filter { (it.score?.last ?: 0.0) > 0.0 }
                                val bestB = validBuckets.maxByOrNull { it.score?.last ?: 0.0 }
                                val worstB = validBuckets.minByOrNull { it.score?.last ?: 100.0 }
                                val bestName = bestB?.let { weekdayName(it.start.toInt(), isEn) } ?: "—"
                                val worstName = worstB?.let { weekdayName(it.start.toInt(), isEn) } ?: "—"
                                val weekdayAvgScore = summaryBuckets.filter { it.start in 0L..4L && (it.score?.last ?: 0.0) > 0 }
                                    .mapNotNull { it.score?.last }.takeIf { it.isNotEmpty() }?.average() ?: 80.0
                                val weekendAvgScore = summaryBuckets.filter { it.start in 5L..6L && (it.score?.last ?: 0.0) > 0 }
                                    .mapNotNull { it.score?.last }.takeIf { it.isNotEmpty() }?.average() ?: 80.0
                                val diff = weekendAvgScore - weekdayAvgScore

                                SmallMetric(
                                    title = label("최고 요일", "Best Day"),
                                    value = bestName,
                                    subtext = "${points(bestB?.score?.last ?: 80.0)}P",
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                SmallMetric(
                                    title = label("주의 요일", "Watch Day"),
                                    value = worstName,
                                    subtext = "${points(worstB?.score?.last ?: 80.0)}P",
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                SmallMetric(
                                    title = label("주말 vs 주중", "Weekend vs Wkday"),
                                    value = "${if (diff >= 0) "+" else ""}${points(diff)}P",
                                    subtext = label("주말 ${points(weekendAvgScore)}P", "Wknd ${points(weekendAvgScore)}P"),
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                        val top = apps.maxByOrNull { it.usage }
                        if (top != null && top.usage > 0) {
                            val topText = if (isPeriod || isWeekday) {
                                val count = if (isWeekday) 4 else summaryBuckets.size.coerceAtLeast(1)
                                val topAvgMins = top.usage / count
                                label("최다 사용: ${top.name} · ${if (isWeekday) "요일" else "일"} 평균 ${minutes(topAvgMins)} (총 ${minutes(top.usage)})",
                                      "Top: ${top.name} · Avg ${minutes(topAvgMins)} (Total ${minutes(top.usage)})")
                            } else {
                                label("최다 사용: ${top.name} · ${minutes(top.usage)}", "Top: ${top.name} · ${minutes(top.usage)}")
                            }
                            Text(topText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DigitsTextSize.label)
                        }
                    }
                }
                item {
                    var showWeeklyReport by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clickable { showWeeklyReport = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = DigitsShape.md
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label("지난주 주간 리포트", "Past Weekly Report"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DigitsTextSize.body
                                )
                                Text(
                                    text = label("코어 지수 평균과 목표 달성 돌아보기", "Review past week's Core Index & goals"),
                                    fontSize = DigitsTextSize.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
                        }
                    }
                    if (showWeeklyReport) {
                        com.digitscore.app.ui.dashboard.WeeklyReportDialog(
                            onDismiss = { showWeeklyReport = false }
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val appSectionTitle = when {
                            isWeekday && selected != null -> label("${weekdayName(selected.start.toInt(), isEn)} 주요 앱", "${weekdayName(selected.start.toInt(), isEn)} Top Apps")
                            isPeriod && selected != null -> label("${dateLabel(selected.start, "M/d")} 주요 앱", "${dateLabel(selected.start, "M/d")} Top Apps")
                            else -> label("앱별 요약", "Apps")
                        }
                        Text(appSectionTitle, fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                label("사용 시간", "Usage"),
                                label("점수 영향", "Impact"),
                                label("실행", "Opens"),
                                label("해제 후 첫 실행", "First Opened"),
                                label("알림 잠금", "Notif Unlocks")
                            ).forEachIndexed { index, text ->
                                FilterChip(ranking == index, { ranking = index; allApps = false }, label = { Text(text) })
                            }
                        }
                    }
                }
                val ordered = when (ranking) {
                    1 -> apps.filter { it.loss != null && it.loss > 0 }.sortedByDescending { it.loss }
                    2 -> apps.filter { it.opens > 0 }.sortedByDescending { it.opens }
                    3 -> apps.filter { it.firstAfterUnlockCount > 0 }.sortedByDescending { it.firstAfterUnlockCount }
                    4 -> apps.filter { it.notificationUnlockCount > 0 }.sortedByDescending { it.notificationUnlockCount }
                    else -> apps.filter { it.usage > 0 }.sortedByDescending { it.usage }
                }
                val maxValue = ordered.firstOrNull()?.let { appValue(it, ranking) } ?: 1.0
                val periodDaysCount = if (isWeekday) 4 else if (isPeriod) recordedDayCount(buckets.takeLast(28)) else 1
                (if (allApps) ordered else ordered.take(3)).forEach { app -> item(key = "app:${app.pkg}") {
                    AppRankingRow(app, ranking, maxValue, daysCount = periodDaysCount, isPeriod = isPeriod || isWeekday) { selectedApp = app }
                } }
                if (ordered.size > 3) item {
                    TextButton(onClick = { allApps = !allApps }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (!allApps) label("나머지 ${ordered.size - 3}개 앱 더보기", "Show ${ordered.size - 3} more apps") else label("앱 목록 접기", "Collapse apps"))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    selectedApp?.let { app -> AppPeriodDialog(app, if (isWeekday) rawBuckets else buckets, visibleUsage, start, end, daily) { selectedApp = null } }
}

@Composable private fun SmallMetric(
    title: String,
    value: String,
    subtext: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f), DigitsShape.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (subtext != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtext, fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.outline, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(color, CircleShape)); Spacer(Modifier.width(4.dp)); Text(text, fontSize = DigitsTextSize.caption) }
}
private fun appValue(app: ExplorerApp, mode: Int) = when (mode) {
    1 -> app.loss ?: 0.0
    2 -> app.opens.toDouble()
    3 -> app.firstAfterUnlockCount.toDouble()
    4 -> app.notificationUnlockCount.toDouble()
    else -> app.usage.toDouble()
}

@Composable private fun AppRankingRow(
    app: ExplorerApp,
    mode: Int,
    maximum: Double,
    daysCount: Int = 1,
    isPeriod: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (mode == 1) ScoreRed else MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.pkg) { value = withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(app.pkg).toBitmap(72, 72).asImageBitmap() }.getOrNull()
    } }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Image(icon!!, null, Modifier.size(36.dp))
            else Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Text(app.name.take(1)) }
            Column(Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Medium)
                val subtitle = when {
                    mode == 1 -> if (isPeriod) label("일 평균 −${points((app.loss ?: 0.0) / daysCount)} (총 −${points(app.loss ?: 0.0)})", "Daily −${points((app.loss ?: 0.0) / daysCount)}")
                                 else label("실제 감점 기여", "Recorded score loss")
                    mode == 3 -> if (isPeriod) label("해제 후 첫 실행 총 ${app.firstAfterUnlockCount}회", "Total ${app.firstAfterUnlockCount}x first after unlock")
                                 else label("잠금 해제 후 처음 연 앱", "First app opened after unlock")
                    mode == 4 -> if (isPeriod) label("알림이 부른 잠금 총 ${app.notificationUnlockCount}회", "Total ${app.notificationUnlockCount}x notif unlocks")
                                 else label("알림이 부른 잠금 해제", "Notification-triggered unlock")
                    isPeriod -> label(
                        "일 ${(app.opens.toFloat() / daysCount).roundToInt()}x · 짧은 확인 ${(app.shortOpens.toFloat() / daysCount).roundToInt()}x (총 ${app.opens}x)",
                        "Daily ${(app.opens.toFloat() / daysCount).roundToInt()}x · Brief ${(app.shortOpens.toFloat() / daysCount).roundToInt()}x (Total ${app.opens}x)"
                    )
                    else -> label(
                        "${app.opens}x · 짧은 확인 ${app.shortOpens}x",
                        "${app.opens}x · Brief ${app.shortOpens}x"
                    )
                }
                Text(subtitle, fontSize = DigitsTextSize.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val valueText = when (mode) {
                1 -> if (isPeriod) "일 −${points((app.loss ?: 0.0) / daysCount)}" else "−${points(app.loss ?: 0.0)}"
                2 -> if (isPeriod) "일 ${(app.opens.toFloat() / daysCount).roundToInt()}x" else "${app.opens}x"
                3 -> if (isPeriod) "일 ${(app.firstAfterUnlockCount.toFloat() / daysCount).roundToInt()}x" else "${app.firstAfterUnlockCount}x"
                4 -> if (isPeriod) "일 ${(app.notificationUnlockCount.toFloat() / daysCount).roundToInt()}x" else "${app.notificationUnlockCount}x"
                else -> if (isPeriod) "일 ${minutes(app.usage / daysCount)}" else minutes(app.usage)
            }
            Text(valueText, color = color, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
            Box(Modifier.fillMaxWidth((appValue(app, mode) / maximum.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color, CircleShape))
        }
    }
}

@Composable
private fun ExplorerChart(
    buckets: List<ExplorerBucket>, samples: List<CoreIndexSampleEntity>, line: Boolean,
    detailed: Boolean, unlocks: Boolean, start: Long, end: Long, selection: Long?,
    zoom: Float,
    isWeekday: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
    onZoom: (Float) -> Unit, onSelect: (Long) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    // 차트 위 최고·최저 글자색 (테마별 대비 확보)
    val highTextArgb = ScoreGreenText.toArgb()
    val lowTextArgb = ScoreRedText.toArgb()
    val foreground = MaterialTheme.colorScheme.onSurface
    val isHourly = buckets.firstOrNull()?.let { it.end - it.start <= STAT_HOUR } ?: false
    val values = buckets.mapNotNull { it.score }.flatMap { listOf(it.low, it.high) } + samples.map { it.exactScore }
    val rawMin = values.minOrNull() ?: 60.0
    val rawMax = values.maxOrNull() ?: 80.0
    val span = maxOf(16.0, rawMax - rawMin + 8.0)
    val low = ((rawMin + rawMax - span) / 2).coerceIn(0.0, (100.0 - span).coerceAtLeast(0.0))
    val high = (low + span).coerceAtMost(100.0)
    val currentZoom by rememberUpdatedState(zoom)
    var initialScroll by remember(start / STAT_HOUR, line, buckets.size) { mutableStateOf(false) }
    LaunchedEffect(scrollState.maxValue) { if (!initialScroll && scrollState.maxValue > 0) { scrollState.scrollTo(scrollState.maxValue); initialScroll = true } }
    val selected = selection?.let { t ->
        buckets.firstOrNull { if (isWeekday) it.start == t else (t >= it.start && t < it.end) }
    }
    val chartDescription = selected?.let { label("${dateLabel(it.start)} 점수 ${it.score?.last?.roundToInt() ?: "—"}", "${dateLabel(it.start)}, score ${it.score?.last?.roundToInt() ?: "—"}") }
        ?: label("코어 지수 차트", "Core Index Chart")

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val viewportWidth = maxWidth - 44.dp
        val width = if (isWeekday) {
            viewportWidth
        } else if (line) {
            maxOf(viewportWidth, (viewportWidth / 24f) * buckets.size * zoom)
        } else if (buckets.isEmpty()) {
            viewportWidth
        } else if (isHourly) {
            maxOf(viewportWidth, (viewportWidth / 36f) * buckets.size * zoom)
        } else {
            viewportWidth
        }
        val height = 310.dp

        Row {
            Column(Modifier.weight(1f).horizontalScroll(scrollState, enabled = (line || isHourly) && !isWeekday)) {
                Canvas(
                    Modifier.width(width).height(height).semantics { contentDescription = chartDescription }
                        .pointerInput(start, end, width, isWeekday, buckets.size) {
                            detectTapGestures {
                                if (isWeekday) {
                                    val slotW = size.width / 7f
                                    val idx = (it.x / slotW).toInt().coerceIn(0, 6)
                                    onSelect(idx.toLong())
                                } else {
                                    onSelect(start + ((end - start) * (it.x / size.width).coerceIn(0f, 0.999999f)).toLong())
                                }
                            }
                        }
                        .pointerInput(start, end, width, isWeekday, buckets.size) {
                            detectDragGesturesAfterLongPress(onDragStart = {
                                if (isWeekday) {
                                    val slotW = size.width / 7f
                                    val idx = (it.x / slotW).toInt().coerceIn(0, 6)
                                    onSelect(idx.toLong())
                                } else {
                                    onSelect(start + ((end - start) * (it.x / size.width).coerceIn(0f, 0.999999f)).toLong())
                                }
                            }) { change, _ ->
                                if (isWeekday) {
                                    val slotW = size.width / 7f
                                    val idx = (change.position.x / slotW).toInt().coerceIn(0, 6)
                                    onSelect(idx.toLong())
                                } else {
                                    onSelect(start + ((end - start) * (change.position.x / size.width).coerceIn(0f, 0.999999f)).toLong())
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2 && !isWeekday) {
                                        onZoom(currentZoom * event.calculateZoom())
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    val graphTop = 24.dp.toPx()
                    val graphBottom = 200.dp.toPx()
                    fun x(time: Long) = size.width * ((time - start).toDouble() / (end - start).coerceAtLeast(1)).toFloat()
                    fun y(score: Double) = graphBottom - (graphBottom - graphTop) * ((score - low) / (high - low).coerceAtLeast(1.0)).toFloat()

                    // 가로 그리드 선
                    listOf(low, (low + high) / 2, high).forEach {
                        drawLine(muted.copy(alpha = 0.12f), Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx())
                    }

                    // 75P 목표 기준선 (점선)
                    if (75.0 in low..high) {
                        val y75 = y(75.0)
                        drawLine(
                            color = muted.copy(alpha = 0.35f),
                            start = Offset(0f, y75),
                            end = Offset(size.width, y75),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                        )
                    }

                    // 05:00 논리적 하루 시작 세로 구분선 (실시간 타임라인 모드)
                    if (line) {
                        val zoneId = ZoneId.systemDefault()
                        buckets.forEach { b ->
                            val bTime = Instant.ofEpochMilli(b.start).atZone(zoneId)
                            if (bTime.hour == LOGICAL_DAY_START_HOUR) {
                                val mx = x(b.start)
                                drawLine(
                                    color = muted.copy(alpha = 0.22f),
                                    start = Offset(mx, graphTop),
                                    end = Offset(mx, 280.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }

                    if (isWeekday) {
                        // 요일별(월~일 7개 슬롯) 캔들 및 코어 지수선 차트
                        val slotW = size.width / 7f
                        val candleW = maxOf(4.dp.toPx(), slotW * 0.45f)

                        // 1. 캔들 그리기
                        buckets.forEachIndexed { i, b ->
                            b.score?.let { s ->
                                val mid = i * slotW + slotW / 2f
                                val yOpen = y(s.first)
                                val yClose = y(s.last)
                                val yHigh = y(s.high)
                                val yLow = y(s.low)
                                val isUp = s.last >= s.first
                                val candleColor = if (isUp) ScoreGreen else ScoreRed

                                drawLine(candleColor, Offset(mid, yHigh), Offset(mid, yLow), 1.3.dp.toPx())
                                val bodyTop = minOf(yOpen, yClose)
                                val bodyH = maxOf(2.dp.toPx(), abs(yClose - yOpen))
                                drawRect(candleColor, Offset(mid - candleW / 2f, bodyTop), Size(candleW, bodyH))
                            }
                        }

                        // 2. 요일별 코어 지수선 (노란색)
                        val coreIndexPath = Path()
                        var coreIndexStarted = false
                        buckets.forEachIndexed { i, b ->
                            b.score?.let { s ->
                                val mid = i * slotW + slotW / 2f
                                val my = y(s.last)
                                if (!coreIndexStarted) {
                                    coreIndexPath.moveTo(mid, my)
                                    coreIndexStarted = true
                                } else {
                                    coreIndexPath.lineTo(mid, my)
                                }
                            }
                        }
                        if (coreIndexStarted) {
                            drawPath(coreIndexPath, ScoreYellow, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                            buckets.forEachIndexed { i, b ->
                                b.score?.let { s ->
                                    val mid = i * slotW + slotW / 2f
                                    val my = y(s.last)
                                    drawCircle(ScoreYellow, 2.5.dp.toPx(), Offset(mid, my))
                                }
                            }
                        }

                        // 최고 / 최저 요일 라벨
                        val validScores = buckets.filter { (it.score?.last ?: 0.0) > 0.0 }
                        val maxB = validScores.maxByOrNull { it.score?.last ?: 0.0 }
                        val minB = validScores.minByOrNull { it.score?.last ?: 100.0 }
                        val paintGreen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = highTextArgb; textSize = DigitsTextSize.caption.toPx(); isFakeBoldText = true
                        }
                        val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = lowTextArgb; textSize = DigitsTextSize.caption.toPx(); isFakeBoldText = true
                        }

                        maxB?.score?.let { s ->
                            val idx = buckets.indexOf(maxB)
                            val mid = idx * slotW + slotW / 2f
                            val text = label("최고 ${points(s.last)}", "High ${points(s.last)}")
                            drawContext.canvas.nativeCanvas.drawText(text, chartLabelX(mid, text, paintGreen, size.width, 4.dp.toPx()), y(s.high) - 8.dp.toPx(), paintGreen)
                        }
                        minB?.score?.let { s ->
                            val idx = buckets.indexOf(minB)
                            val mid = idx * slotW + slotW / 2f
                            val text = label("최저 ${points(s.last)}", "Low ${points(s.last)}")
                            drawContext.canvas.nativeCanvas.drawText(text, chartLabelX(mid, text, paintRed, size.width, 4.dp.toPx()), y(s.low) + 16.dp.toPx(), paintRed)
                        }
                    } else if (line) {
                        // 1. 1일 24시간 실시간 라인 차트 (토스 첨부2 스타일)
                        val readings = if (samples.isNotEmpty()) samples.map { Triple(it.timestampMillis, it.exactScore, it.scoreModelVersion) }
                            else buckets.mapNotNull { it.score?.let { s -> Triple(s.lastAt, s.last, s.model) } }

                        if (readings.isNotEmpty()) {
                            val lineBrush = Brush.verticalGradient(
                                colors = listOf(ScoreGreen, ScoreYellow, ScoreRed),
                                startY = graphTop,
                                endY = graphBottom
                            )

                            readings.zipWithNext().forEach { (a, b) ->
                                drawLine(
                                    brush = lineBrush,
                                    start = Offset(x(a.first), y(a.second)),
                                    end = Offset(x(b.first), y(b.second)),
                                    strokeWidth = 2.4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            val maxReading = readings.maxByOrNull { it.second }
                            val minReading = readings.minByOrNull { it.second }

                            val paintGreen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = highTextArgb
                                textSize = DigitsTextSize.caption.toPx()
                                isFakeBoldText = true
                            }
                            val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = lowTextArgb
                                textSize = DigitsTextSize.caption.toPx()
                                isFakeBoldText = true
                            }

                            maxReading?.let {
                                val mx = x(it.first)
                                val my = y(it.second)
                                drawCircle(ScoreGreen, 3.5.dp.toPx(), Offset(mx, my))
                                val highText = label("최고 ${points(it.second)}", "High ${points(it.second)}")
                                drawContext.canvas.nativeCanvas.drawText(highText, chartLabelX(mx, highText, paintGreen, size.width, 4.dp.toPx()), my - 8.dp.toPx(), paintGreen)
                            }
                            minReading?.let {
                                val mx = x(it.first)
                                val my = y(it.second)
                                drawCircle(ScoreRed, 3.5.dp.toPx(), Offset(mx, my))
                                val lowText = label("최저 ${points(it.second)}", "Low ${points(it.second)}")
                                drawContext.canvas.nativeCanvas.drawText(lowText, chartLabelX(mx, lowText, paintRed, size.width, 4.dp.toPx()), my + 16.dp.toPx(), paintRed)
                            }
                        }
                    } else if (isHourly) {
                        val hourlyReadings = buckets.mapNotNull { b -> b.score?.let { s -> Triple((b.start + b.end) / 2, s.last, s.model) } }
                        val lineBrush = Brush.verticalGradient(listOf(ScoreGreen, ScoreYellow, ScoreRed), startY = graphTop, endY = graphBottom)
                        hourlyReadings.zipWithNext().forEach { (a, b) ->
                            drawLine(brush = lineBrush, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 2.4.dp.toPx(), StrokeCap.Round)
                        }
                    } else {
                        // 2. 4주 28일 일봉 캔들스틱 차트 (토스 첨부1 스타일)
                        val slotW = size.width / buckets.size.coerceAtLeast(1)
                        val candleW = maxOf(3.dp.toPx(), slotW * 0.55f)

                        buckets.forEachIndexed { i, b ->
                            b.score?.let { s ->
                                val mid = i * slotW + slotW / 2f
                                val yOpen = y(s.first)
                                val yClose = y(s.last)
                                val yHigh = y(s.high)
                                val yLow = y(s.low)
                                val isUp = s.last >= s.first
                                val candleColor = if (isUp) ScoreGreen else ScoreRed

                                drawLine(candleColor, Offset(mid, yHigh), Offset(mid, yLow), 1.3.dp.toPx())
                                val bodyTop = minOf(yOpen, yClose)
                                val bodyH = maxOf(2.dp.toPx(), abs(yClose - yOpen))
                                drawRect(candleColor, Offset(mid - candleW / 2f, bodyTop), Size(candleW, bodyH))
                            }
                        }

                        val coreIndexPath = Path()
                        var coreIndexStarted = false
                        buckets.forEachIndexed { i, b ->
                            b.score?.let { s ->
                                val mid = i * slotW + slotW / 2f
                                val my = y(s.last)
                                if (!coreIndexStarted) {
                                    coreIndexPath.moveTo(mid, my)
                                    coreIndexStarted = true
                                } else {
                                    coreIndexPath.lineTo(mid, my)
                                }
                            }
                        }
                        if (coreIndexStarted) {
                            drawPath(coreIndexPath, ScoreYellow, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                        }

                        val maxBucket = buckets.maxByOrNull { it.score?.high ?: 0.0 }
                        val minBucket = buckets.minByOrNull { it.score?.low ?: 100.0 }

                        val paintGreen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = highTextArgb; textSize = DigitsTextSize.caption.toPx(); isFakeBoldText = true
                        }
                        val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = lowTextArgb; textSize = DigitsTextSize.caption.toPx(); isFakeBoldText = true
                        }

                        maxBucket?.score?.let { s ->
                            val idx = buckets.indexOf(maxBucket)
                            val mid = idx * slotW + slotW / 2f
                            val text = label("최고 ${points(s.high)} (${dateLabel(maxBucket.start, "MM.dd")})", "High ${points(s.high)} (${dateLabel(maxBucket.start, "MM.dd")})")
                            drawContext.canvas.nativeCanvas.drawText(text, chartLabelX(mid, text, paintGreen, size.width, 4.dp.toPx()), y(s.high) - 8.dp.toPx(), paintGreen)
                        }
                        minBucket?.score?.let { s ->
                            val idx = buckets.indexOf(minBucket)
                            val mid = idx * slotW + slotW / 2f
                            val text = label("최저 ${points(s.low)} (${dateLabel(minBucket.start, "MM.dd")})", "Low ${points(s.low)} (${dateLabel(minBucket.start, "MM.dd")})")
                            drawContext.canvas.nativeCanvas.drawText(text, chartLabelX(mid, text, paintRed, size.width, 4.dp.toPx()), y(s.low) + 16.dp.toPx(), paintRed)
                        }
                    }

                    // 하단 볼륨: 화면 사용 시간 막대 (일반 시간 + 관리 시간 구분)
                    val maxUsage = buckets.maxOfOrNull { it.usage ?: 0L }?.coerceAtLeast(60_000L) ?: 60_000L
                    val slotW = size.width / (if (isWeekday) 7f else buckets.size.coerceAtLeast(1).toFloat())
                    val barW = maxOf(2.dp.toPx(), slotW * 0.55f)
                    val volumeBottom = 280.dp.toPx()
                    val maxBarH = 46.dp.toPx()

                    buckets.forEachIndexed { i, b ->
                        val mid = i * slotW + slotW / 2f
                        val totalUsage = b.usage ?: 0L
                        val managedUsage = b.managed.coerceAtLeast(0L).coerceAtMost(totalUsage)
                        val generalUsage = (totalUsage - managedUsage).coerceAtLeast(0L)

                        val totalH = maxBarH * totalUsage / maxUsage.toFloat()
                        val managedH = maxBarH * managedUsage / maxUsage.toFloat()
                        val generalH = (totalH - managedH).coerceAtLeast(0f)

                        // 일반 화면 시간 (청록색)
                        if (generalH > 0f) {
                            drawRect(
                                color = com.digitscore.app.ui.theme.ChartScreen.copy(alpha = 0.75f),
                                topLeft = Offset(mid - barW / 2f, volumeBottom - generalH),
                                size = Size(barW, generalH)
                            )
                        }
                        // 관리 시간 (빨간색 - 일반 시간 위에 스택)
                        if (managedH > 0f) {
                            drawRect(
                                color = ScoreRed.copy(alpha = 0.85f),
                                topLeft = Offset(mid - barW / 2f, volumeBottom - totalH),
                                size = Size(barW, managedH)
                            )
                        }
                    }

                    // 4주 모드: 화면 사용 시간 4주 평균 기준선 (청록색 점선)
                    val validUsageBuckets = buckets.filter { (it.usage ?: 0L) > 0 }
                    val avgUsage = if (validUsageBuckets.isNotEmpty()) buckets.mapNotNull { it.usage }.sum() / recordedDayCount(buckets) else 0L
                    if (!line && !isHourly && !isWeekday && avgUsage > 0L) {
                        val avgUsageH = maxBarH * avgUsage / maxUsage.toFloat()
                        val avgUsageY = volumeBottom - avgUsageH
                        drawLine(
                            color = com.digitscore.app.ui.theme.ChartScreen.copy(alpha = 0.9f),
                            start = Offset(0f, avgUsageY),
                            end = Offset(size.width, avgUsageY),
                            strokeWidth = 1.3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                        )
                        val avgUsagePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.parseColor("#26A69A")
                            textSize = DigitsTextSize.micro.toPx()
                        }
                        drawContext.canvas.nativeCanvas.drawText("4주 평균 ${minutes(avgUsage)}", 4.dp.toPx(), avgUsageY - 3.dp.toPx(), avgUsagePaint)
                    }

                    // 일별 잠금 해제 횟수 꺾은선 + 점 그래프 (하늘색/Cyan)
                    val maxOpens = buckets.maxOfOrNull { it.unlocks ?: 0 }?.coerceAtLeast(1) ?: 1
                    val openLineBottom = volumeBottom
                    val openLineTop = volumeBottom - 44.dp.toPx()
                    val opensColor = com.digitscore.app.ui.theme.ChartUnlock

                    val opensPath = Path()
                    var opensStarted = false
                    val openPoints = mutableListOf<Offset>()

                    buckets.forEachIndexed { i, b ->
                        val opens = b.unlocks ?: 0
                        if (opens > 0 || b.usage != null) {
                            val mid = i * slotW + slotW / 2f
                            val openH = (openLineBottom - openLineTop) * opens / maxOpens.toFloat()
                            val oy = openLineBottom - openH
                            val pt = Offset(mid, oy)
                            openPoints.add(pt)
                            if (!opensStarted) {
                                opensPath.moveTo(mid, oy)
                                opensStarted = true
                            } else {
                                opensPath.lineTo(mid, oy)
                            }
                        }
                    }

                    if (opensStarted) {
                        drawPath(opensPath, opensColor.copy(alpha = 0.75f), style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
                        openPoints.forEach { pt ->
                            drawCircle(opensColor, 1.8.dp.toPx(), pt)
                        }
                    }

                    // 4주 모드: 잠금 해제 횟수 4주 평균 기준선 (하늘색 점선)
                    val avgOpens = if (buckets.isNotEmpty()) buckets.mapNotNull { it.unlocks }.sum().toFloat() / recordedDayCount(buckets) else 0f
                    if (!line && !isHourly && !isWeekday && avgOpens > 0f) {
                        val avgOpensH = (openLineBottom - openLineTop) * avgOpens / maxOpens.toFloat()
                        val avgOpensY = openLineBottom - avgOpensH
                        drawLine(
                            color = opensColor.copy(alpha = 0.9f),
                            start = Offset(0f, avgOpensY),
                            end = Offset(size.width, avgOpensY),
                            strokeWidth = 1.3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                        )
                        val avgOpensPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.parseColor("#38BDF8")
                            textSize = DigitsTextSize.micro.toPx()
                        }
                        val avgOpensLabel = label("4주 평균 ${avgOpens.roundToInt()}x", "4W Avg ${avgOpens.roundToInt()}x")
                        drawContext.canvas.nativeCanvas.drawText(avgOpensLabel, (size.width - 65.dp.toPx()).coerceAtLeast(4.dp.toPx()), avgOpensY - 3.dp.toPx(), avgOpensPaint)
                    }

                    // 선택 십자선
                    selection?.let { time ->
                        val lineX = if (isWeekday) {
                            val idx = time.toInt().coerceIn(0, 6)
                            idx * slotW + slotW / 2f
                        } else {
                            x(time)
                        }
                        drawLine(muted.copy(alpha = 0.6f), Offset(lineX, 0f), Offset(lineX, volumeBottom), 1.2.dp.toPx())
                    }

                    // X축 날짜/요일 라벨
                    val axisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = DigitsTextSize.tiny.toPx() }
                    if (isWeekday) {
                        buckets.forEachIndexed { index, b ->
                            val mid = index * slotW + slotW / 2f
                            val axisLabel = formatBucketAxisLabel(b.start, isHourly = false, isDay = false, isRolling = false, isWeekday = true)
                            drawContext.canvas.nativeCanvas.drawText(axisLabel, chartLabelX(mid, axisLabel, axisPaint, size.width, 0f), size.height - 4.dp.toPx(), axisPaint)
                        }
                    } else {
                        val labelStep = if (line) 6 else 4
                        buckets.forEachIndexed { index, b ->
                            if (index % labelStep == 0) {
                                val axisLabel = formatBucketAxisLabel(b.start, isHourly, line, isRolling = line)
                                drawContext.canvas.nativeCanvas.drawText(axisLabel, chartLabelX(x(b.start), axisLabel, axisPaint, size.width, 0f), size.height - 4.dp.toPx(), axisPaint)
                            }
                        }
                    }
                }
            }

            // 우측 Y축 눈금 및 현재가 배지 (토스 첨부1 스타일)
            Canvas(Modifier.width(44.dp).height(height)) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = DigitsTextSize.tiny.toPx() }
                val graphTop = 24.dp.toPx()
                val graphBottom = 200.dp.toPx()
                fun y(score: Double) = graphBottom - (graphBottom - graphTop) * ((score - low) / (high - low).coerceAtLeast(1.0)).toFloat()

                // Y축 눈금 (상, 중, 하)
                listOf(high, (low + high) / 2, low).forEach { valScore ->
                    drawContext.canvas.nativeCanvas.drawText(valScore.roundToInt().toString(), 6.dp.toPx(), y(valScore) + 4.dp.toPx(), paint)
                }

                // 75P 목표 기준 라벨
                if (75.0 in low..high) {
                    val y75 = y(75.0)
                    val targetPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = muted.toArgb()
                        textSize = DigitsTextSize.micro.toPx()
                        isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText("75P", 6.dp.toPx(), y75 + 3.dp.toPx(), targetPaint)
                }

                // 토스 첨부1 우측 현재 가격 박스 배지
                val lastScore = buckets.lastOrNull()?.score?.last
                if (lastScore != null) {
                    val lastY = y(lastScore)
                    val isUp = lastScore >= (buckets.lastOrNull()?.score?.first ?: lastScore)
                    val badgeColor = if (isUp) ScoreGreen else ScoreRed
                    drawRoundRect(
                        color = badgeColor,
                        topLeft = Offset(2.dp.toPx(), lastY - 8.dp.toPx()),
                        size = Size(38.dp.toPx(), 16.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                    val badgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textSize = DigitsTextSize.tiny.toPx()
                        isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(points(lastScore), 6.dp.toPx(), lastY + 3.5.dp.toPx(), badgePaint)
                }

                // 볼륨 및 오픈 라벨
                val volPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = DigitsTextSize.micro.toPx() }
                val openPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = com.digitscore.app.ui.theme.ChartUnlock.toArgb(); textSize = DigitsTextSize.micro.toPx() }
                drawContext.canvas.nativeCanvas.drawText(label("잠금 해제", "Unlocks"), 4.dp.toPx(), 238.dp.toPx(), openPaint)
                drawContext.canvas.nativeCanvas.drawText(label("화면", "Screen"), 4.dp.toPx(), 274.dp.toPx(), volPaint)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppPeriodDialog(app: ExplorerApp, buckets: List<ExplorerBucket>, hours: List<UsageHourEntity>, start: Long, end: Long, daily: Boolean, onDismiss: () -> Unit) {
    val appRows = hours.filter { it.packageName == app.pkg }.map { it.copy(packageName = "") }
    val series = remember(appRows, start, end, daily) { explorerBuckets(start, end, daily, appRows, emptyList(), emptyList()) }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(modifier = Modifier.testTag("app-detail"), topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, label("뒤로", "Back")) } }) }) { padding ->
            ResponsiveContent(Modifier.padding(padding)) {
                LazyColumn(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    item { Text("${dateLabel(start)} – ${dateLabel(end)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SmallMetric(label("사용 시간", "Usage"), minutes(app.usage)); SmallMetric(label("실행", "Opens"), "${app.opens}x"); SmallMetric(label("짧은 확인", "Brief"), "${app.shortOpens}x")
                    } }
                    item { Text(label("기록된 감점", "Recorded loss"), fontWeight = FontWeight.Bold)
                        Text(app.loss?.let { "−${points(it)}" } ?: "—", fontSize = DigitsTextSize.displayLarge, color = ScoreRedText, fontWeight = FontWeight.Bold)
                    }
                    item { AppMetricBars(label("사용 시간 추세", "Usage trend"), series, false) }
                    item { AppMetricBars(label("실행 횟수 추세", "Open frequency"), series, true) }
                }
            }
        }
    }
}

@Composable private fun AppMetricBars(title: String, series: List<ExplorerBucket>, opens: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    var selected by remember(series) { mutableStateOf<ExplorerBucket?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        selected?.let { Text("${dateLabel(it.start)} · ${if (opens) "${it.opens ?: 0}" else minutes(it.usage ?: 0)}", fontSize = DigitsTextSize.label) }
        Canvas(Modifier.fillMaxWidth().height(140.dp).pointerInput(series) {
            detectTapGestures { tap -> selected = series.getOrNull((tap.x / size.width * series.size).toInt().coerceIn(0, series.lastIndex.coerceAtLeast(0))) }
        }) {
            val maximum = series.maxOfOrNull { if (opens) (it.opens ?: 0).toDouble() else (it.usage ?: 0).toDouble() }?.coerceAtLeast(1.0) ?: 1.0
            val step = size.width / series.size.coerceAtLeast(1)
            series.forEachIndexed { i, b ->
                val value = if (opens) (b.opens ?: 0).toDouble() else (b.usage ?: 0).toDouble()
                val h = (size.height * value / maximum).toFloat()
                drawRect(primary.copy(alpha = 0.6f), Offset(i * step + step * 0.15f, size.height - h), Size(step * 0.7f, h))
                if (opens) { val shortH = (size.height * b.shortOpens / maximum).toFloat()
                    drawRect(ScoreOrange, Offset(i * step + step * 0.15f, size.height - shortH), Size(step * 0.7f, shortH)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(series.firstOrNull()?.let { dateLabel(it.start) } ?: "—", fontSize = DigitsTextSize.caption)
            Text(series.lastOrNull()?.let { dateLabel(it.end) } ?: "—", fontSize = DigitsTextSize.caption)
        }
    }
}

/** 차트 라벨을 실제 글자 폭 기준으로 가운데 정렬하고 차트 안쪽에 둡니다 (양 끝 잘림 방지). */
private fun chartLabelX(centerX: Float, text: String, paint: android.graphics.Paint, width: Float, margin: Float): Float {
    val tw = paint.measureText(text)
    return (centerX - tw / 2f).coerceIn(margin, (width - tw - margin).coerceAtLeast(margin))
}
