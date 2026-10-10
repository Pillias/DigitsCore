package com.digitscore.app.ui.statistics

import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun label(ko: String, en: String) = if (Locale.getDefault().language == "en") en else ko
private fun dateLabel(time: Long, pattern: String = "MM/dd HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
private fun minutes(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60}h ${min % 60}m"
    else if (min > 0) "${min}m" else "${(ms / 1000).coerceAtLeast(1)}s"
}
private fun points(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

internal fun calculateMovingAverages(buckets: List<ExplorerBucket>, period: Int): List<Double?> {
    return buckets.indices.map { i ->
        val window = buckets.subList(maxOf(0, i - period + 1), i + 1).mapNotNull { it.score?.last }
        if (window.isNotEmpty()) window.average() else null
    }
}
private fun formatBucketAxisLabel(start: Long, isHourly: Boolean, isDay: Boolean, isRolling: Boolean = false): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(start).atZone(zone)
    val isKo = Locale.getDefault().language != "en"
    return when {
        isRolling -> {
            if (time.hour == 0) {
                if (isKo) "${time.monthValue}/${time.dayOfMonth} 0시" else time.format(DateTimeFormatter.ofPattern("M/d 00:00"))
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
    DAY,
    PERIOD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onNavigateBack: () -> Unit, database: DigitsDatabase? = null) {
    val context = LocalContext.current
    val db = remember(database) { database ?: DigitsDatabase.getInstance(context) }
    val dao = remember { db.statisticsDao() }
    val zone = ZoneId.systemDefault()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var viewMode by rememberSaveable { mutableStateOf(ExplorerViewMode.ROLLING_24H) }
    var hourlyView by rememberSaveable { mutableStateOf(false) }
    var detailed by rememberSaveable { mutableStateOf(true) }
    var showUnlocks by rememberSaveable { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<Long?>(null) }
    var selectedDay by remember { mutableStateOf(LocalDate.now(zone)) }
    var selectedApp by remember { mutableStateOf<ExplorerApp?>(null) }
    var ranking by rememberSaveable { mutableIntStateOf(0) }
    var allApps by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showHelp by remember { mutableStateOf(false) }

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
    val isDay = viewMode == ExplorerViewMode.DAY
    val isPeriod = viewMode == ExplorerViewMode.PERIOD

    val (start, end) = when (viewMode) {
        ExplorerViewMode.ROLLING_24H -> {
            val rollingEnd = now
            val rollingStart = now - 30L * 24 * STAT_HOUR // 30일간의 연속 실시간 타임라인 (보관된 1개월 전 구간 횡스크롤)
            rollingStart to rollingEnd
        }
        ExplorerViewMode.DAY -> {
            val dayStart = selectedDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = selectedDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().coerceAtMost(now)
            dayStart to dayEnd
        }
        ExplorerViewMode.PERIOD -> {
            val periodEnd = now
            val periodStart = Instant.ofEpochMilli(periodEnd).atZone(zone).toLocalDate()
                .minusDays(27L).atStartOfDay(zone).toInstant().toEpochMilli()
            periodStart to periodEnd
        }
    }
    val daily = isPeriod && !hourlyView
    val queryStart = statisticHour(start)
    val queryEnd = statisticHour(end) + STAT_HOUR
    val needIntraday = isRolling || isDay
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
    val histories by db.scoreDao().getAllScoreHistories().collectAsState(emptyList())
    val rawAvailable = needIntraday && start >= now - 30L * 24 * STAT_HOUR
    val exactInteractions = needIntraday && start >= now - 24L * STAT_HOUR
    val events by remember(start, end, exactInteractions) {
        if (exactInteractions) db.deviceInteractionEventDao().observeBetween(start - 15_000, end) else flowOf(emptyList())
    }.collectAsState(emptyList())
    val visibleUsage = remember(usage, sessions, events, start, end, rawAvailable) {
        if (!rawAvailable) usage else {
            val raw = aggregateUsageHours(sessions, start, end, now - 120_000)
            val interactions = usage.filter { it.packageName.isEmpty() }.associateBy { it.hour }
            val combined = raw.associateBy { it.hour to it.packageName }.toMutableMap()
            val unlockCounts = UsageStatsHelper.resolvedUnlockTimestamps(events).filter { it >= start && it < end }
                .groupingBy { statisticHour(it) }.eachCount()
            val notificationCounts = events.filter { it.timestampMillis >= start && it.eventType == DeviceInteractionEventEntity.NOTIFICATION_INTERRUPTION }
                .groupingBy { statisticHour(it.timestampMillis) }.eachCount()
            interactions.forEach { (hour, row) ->
                val key = hour to ""
                combined[key] = (combined[key] ?: UsageHourEntity(hour, "", "")).copy(
                    unlocks = if (exactInteractions) unlockCounts[hour] ?: 0 else row.unlocks,
                    notifications = if (exactInteractions) notificationCounts[hour] ?: 0 else row.notifications)
            }
            if (exactInteractions) (unlockCounts.keys + notificationCounts.keys).forEach { hour ->
                val key = hour to ""
                combined[key] = (combined[key] ?: UsageHourEntity(hour, "", "")).copy(
                    unlocks = unlockCounts[hour] ?: 0, notifications = notificationCounts[hour] ?: 0)
            }
            combined.values.toList()
        }
    }
    val visibleSamples = remember(samples, start, end) { samples.filter { it.timestampMillis in start..end } }
    // Partial-hour attribution is not prorated: its observed coverage is shown explicitly.
    val visibleImpacts = remember(impacts, start, end) { impacts.filter { it.firstAt >= start && it.lastAt < end } }
    val buckets = remember(start, end, daily, visibleUsage, hourlyScores, visibleImpacts, histories) {
        explorerBuckets(start, end, daily, visibleUsage, hourlyScores, visibleImpacts, histories)
    }
    val rollingUsageForApps = remember(visibleUsage, isRolling, now) {
        if (isRolling) visibleUsage.filter { it.hour >= statisticHour(now - 24 * STAT_HOUR) }
        else visibleUsage
    }
    val rollingImpactsForApps = remember(visibleImpacts, isRolling, now) {
        if (isRolling) visibleImpacts.filter { it.firstAt >= now - 24 * STAT_HOUR }
        else visibleImpacts
    }
    val apps = remember(rollingUsageForApps, rollingImpactsForApps, dailyApps, isDay, isRolling) {
        explorerApps(rollingUsageForApps, rollingImpactsForApps, dailyApps, !isDay && !isRolling)
    }
    val selected = selectedTime?.let { t -> buckets.firstOrNull { t >= it.start && t < it.end } }
    val selectedSample = if ((isDay || isRolling) && selectedTime != null && selected != null)
        visibleSamples.filter { it.timestampMillis >= selected.start && it.timestampMillis < selected.end }
            .minByOrNull { abs(it.timestampMillis - selectedTime!!) } else null
    val readings = if ((isDay || isRolling) && visibleSamples.isNotEmpty()) visibleSamples.map {
        ScoreHourEntity(statisticHour(it.timestampMillis), it.scoreModelVersion, it.timestampMillis, it.timestampMillis,
            it.exactScore, it.exactScore, it.exactScore, it.exactScore)
    } else buckets.mapNotNull { it.score }
    val latest = readings.lastOrNull()
    val currentModel = latest?.model ?: 5
    val comparable = readings.filter { it.model == currentModel }
    val isToday = isDay && selectedDay == LocalDate.now(zone)
    val displayed = if (selected != null) selectedSample?.exactScore ?: selected.score?.last
        else if ((isRolling || isToday) && currentModel == 5) liveScore?.exactScore ?: latest?.last else latest?.last
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
                    when (viewMode) {
                        ExplorerViewMode.ROLLING_24H -> label("최근 24시간 통계", "24-Hour Statistics")
                        ExplorerViewMode.DAY -> selectedDay.toString()
                        ExplorerViewMode.PERIOD -> label("최근 4주 통계", "4-Week Statistics")
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, label("뒤로", "Back"))
                }
            }
        )
    }) { padding ->
        ResponsiveContent(Modifier.padding(padding)) {
            LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    if (isPeriod) {
                        // 4주 일봉 모드 상단 HUD
                        val targetBucket = selected ?: buckets.lastOrNull()
                        val openScore = targetBucket?.score?.first ?: 80.0
                        val closeScore = targetBucket?.score?.last ?: 80.0
                        val highScore = targetBucket?.score?.high ?: maxOf(openScore, closeScore)
                        val lowScore = targetBucket?.score?.low ?: minOf(openScore, closeScore)
                        val diff = closeScore - openScore
                        val isUp = closeScore >= openScore

                        Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text(targetBucket?.let { dateLabel(it.start, "yy.MM.dd (E)") } ?: "—", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(points(closeScore), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("${if (diff >= 0) "+" else ""}${points(diff)}", color = if (isUp) ScoreGreen else ScoreRed, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                                }
                            }
                            // 시작 / 최고 / 마지막 / 최저 4분할 그리드
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(label("시작 ", "Open ") + points(openScore), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(label("최고 ", "High ") + points(highScore), fontSize = 11.sp, color = ScoreGreen, fontWeight = FontWeight.SemiBold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(label("마지막 ", "Close ") + points(closeScore), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(label("최저 ", "Low ") + points(lowScore), fontSize = 11.sp, color = ScoreRed, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            // 범례 및 사용 현황 요약
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).background(ScoreYellow, CircleShape))
                                        Text(label("코어지수", "Index"), fontSize = 11.sp, color = ScoreYellow, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).background(Color(0xFF26A69A), RoundedCornerShape(1.dp)))
                                        Text(label("일반", "General"), fontSize = 11.sp, color = Color(0xFF26A69A), fontWeight = FontWeight.Medium)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).background(ScoreRed, RoundedCornerShape(1.dp)))
                                        Text(label("몰입관리", "Managed"), fontSize = 11.sp, color = ScoreRed, fontWeight = FontWeight.Medium)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).background(Color(0xFF38BDF8), CircleShape))
                                        Text(label("오픈", "Opens"), fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Medium)
                                    }
                                }
                                targetBucket?.let { b ->
                                    val uStr = minutes(b.usage ?: 0L)
                                    val mStr = minutes(b.managed)
                                    val oStr = "${b.opens ?: 0}x"
                                    Text(
                                        text = "$uStr ($mStr) · $oStr",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // 24시간 실시간 모드 상단 HUD (토스 첨부2 스타일)
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label("코어 지수", "Core Index"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val scoreDouble = displayed ?: 80.0
                                val scoreTierColor = coreIndexTierColor(scoreDouble.roundToInt())
                                Text(points(scoreDouble), color = scoreTierColor, modifier = Modifier.testTag("statistics-score"), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            val windowSamples = if (isRolling) visibleSamples.filter { it.timestampMillis >= now - 24 * STAT_HOUR } else visibleSamples
                            val highScore = windowSamples.maxOfOrNull { it.exactScore } ?: (displayed ?: 80.0)
                            val lowScore = windowSamples.minOfOrNull { it.exactScore } ?: (displayed ?: 80.0)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label("24시간 최고 ", "24h High ") + points(highScore) + "P",
                                    color = ScoreGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    label("최저 ", "Low ") + points(lowScore) + "P",
                                    color = ScoreRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                            label = { Text(label("실시간 타임라인 (30일)", "Timeline (30D)")) }
                        )
                        FilterChip(
                            selected = viewMode == ExplorerViewMode.PERIOD,
                            onClick = {
                                viewMode = ExplorerViewMode.PERIOD
                                selectedTime = null
                                zoom = 1f
                            },
                            label = { Text(label("4주 전체", "4 Weeks")) }
                        )
                        FilterChip(
                            selected = viewMode == ExplorerViewMode.DAY,
                            onClick = {
                                viewMode = ExplorerViewMode.DAY
                                selectedDay = LocalDate.now(zone)
                                selectedTime = null
                                zoom = 1f
                            },
                            label = { Text(label("날짜별", "By Day")) }
                        )
                    }
                }
                item {
                    ExplorerChart(
                        buckets = buckets,
                        samples = if (isDay || isRolling) visibleSamples else emptyList(),
                        line = isDay || isRolling,
                        detailed = detailed,
                        unlocks = showUnlocks,
                        start = start,
                        end = end,
                        selection = selectedTime,
                        zoom = zoom,
                        onZoom = { zoom = it.coerceIn(0.5f, 4f) },
                        onSelect = { selectedTime = it }
                    )
                    if (selected != null) {
                        SelectionReadout(selected, showUnlocks)
                        Row(Modifier.padding(horizontal = 14.dp)) {
                            TextButton(onClick = { selectedTime = null }) { Text(label("선택 해제", "Clear")) }
                            if (viewMode != ExplorerViewMode.DAY) TextButton(onClick = {
                                selectedDay = Instant.ofEpochMilli(selected.start).atZone(zone).toLocalDate()
                                viewMode = ExplorerViewMode.DAY
                                selectedTime = null; zoom = 1f
                            }) { Text(label("이날 자세히 보기 ›", "Explore this day ›")) }
                        }
                    }
                    // 간결한 기간 날짜 표시 및 오늘/4주 전환 버튼
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (viewMode) {
                            ExplorerViewMode.ROLLING_24H -> {
                                Text(
                                    text = label(
                                        "실시간 타임라인 (${dateLabel(start, "MM/dd")} ~ ${dateLabel(end, "MM/dd HH:mm")})",
                                        "Timeline (${dateLabel(start, "MM/dd")} ~ ${dateLabel(end, "MM/dd HH:mm")})"
                                    ),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = {
                                    viewMode = ExplorerViewMode.DAY
                                    selectedDay = LocalDate.now(zone)
                                    selectedTime = null
                                }) {
                                    Text(label("날짜별 보기 ›", "By Day ›"))
                                }
                            }
                            ExplorerViewMode.DAY -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            selectedDay = selectedDay.minusDays(1)
                                            selectedTime = null
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 날")
                                    }
                                    val isTodaySelected = selectedDay == LocalDate.now(zone)
                                    Text(
                                        text = if (isTodaySelected) "${dateLabel(start, "yyyy년 MM월 dd일")} (오늘)"
                                               else dateLabel(start, "yyyy년 MM월 dd일"),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = {
                                            val next = selectedDay.plusDays(1)
                                            if (!next.isAfter(LocalDate.now(zone))) {
                                                selectedDay = next
                                                selectedTime = null
                                            }
                                        },
                                        enabled = selectedDay.isBefore(LocalDate.now(zone)),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 날")
                                    }
                                }
                                TextButton(onClick = {
                                    viewMode = ExplorerViewMode.ROLLING_24H
                                    selectedTime = null
                                }) {
                                    Text(label("최근 24시간 ↺", "24h View ↺"))
                                }
                            }
                            ExplorerViewMode.PERIOD -> {
                                Text(
                                    "${dateLabel(start, "yy/MM/dd")} – ${dateLabel(end, "yy/MM/dd")} (4주 전체)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = {
                                    viewMode = ExplorerViewMode.ROLLING_24H
                                    selectedTime = null
                                }) {
                                    Text(label("최근 24시간 ›", "24h View ›"))
                                }
                            }
                        }
                    }
                }
                item {
                    val rolling24hStart = now - 24 * STAT_HOUR
                    val summaryBuckets = if (isRolling) buckets.filter { it.start >= rolling24hStart }
                        else if (isDay) buckets
                        else buckets.takeLast(28)
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (viewMode) {
                                ExplorerViewMode.ROLLING_24H -> label("최근 24시간 패턴 분석", "24-Hour Usage Pattern")
                                ExplorerViewMode.DAY -> label("하루 요약", "Day summary")
                                ExplorerViewMode.PERIOD -> label("기간 요약 (최근 4주)", "Period summary (Last 4 weeks)")
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val countsKnown = apps.isNotEmpty() || summaryBuckets.any { it.opens != null }
                            if (isRolling) {
                                val peakBucket = summaryBuckets.maxByOrNull { it.usage ?: 0L }
                                val peakHourStr = peakBucket?.let { dateLabel(it.start, "H:00") } ?: "—"
                                val peakUsageMins = ((peakBucket?.usage ?: 0L) / 60_000L).toInt()
                                SmallMetric(label("24시간 화면", "Screen"), summaryBuckets.mapNotNull { it.usage }.takeIf { it.isNotEmpty() }?.sum()?.let(::minutes) ?: "—")
                                SmallMetric(label("최대 사용 시간", "Peak Hour"), if (peakUsageMins > 0) "${peakHourStr} (${peakUsageMins}분)" else "—")
                                SmallMetric(label("1분 이하 찰나", "≤1 min"), if (countsKnown) apps.sumOf { it.shortOpens }.toString() + "회" else "—")
                            } else {
                                SmallMetric(label("화면", "Screen"), summaryBuckets.mapNotNull { it.usage }.takeIf { it.isNotEmpty() }?.sum()?.let(::minutes) ?: "—")
                                SmallMetric(label("앱 실행", "App opens"), if (countsKnown) apps.sumOf { it.opens }.toString() else "—")
                                SmallMetric(label("1분 이하", "≤1 min"), if (countsKnown) apps.sumOf { it.shortOpens }.toString() else "—")
                            }
                        }
                        val top = apps.maxByOrNull { it.usage }
                        if (top != null && top.usage > 0) {
                            Text(label("최다 사용: ${top.name} · ${minutes(top.usage)}", "Top: ${top.name} · ${minutes(top.usage)}"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label("앱별 요약", "Apps"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(label("사용시간", "Usage"), label("점수 영향", "Impact"), label("실행", "Opens")).forEachIndexed { index, text ->
                                FilterChip(ranking == index, { ranking = index; allApps = false }, label = { Text(text) })
                            }
                        }
                    }
                }
                val ordered = when (ranking) {
                    1 -> apps.filter { it.loss != null && it.loss > 0 }.sortedByDescending { it.loss }
                    2 -> apps.filter { it.opens > 0 }.sortedByDescending { it.opens }
                    else -> apps.filter { it.usage > 0 }.sortedByDescending { it.usage }
                }
                val maxValue = ordered.firstOrNull()?.let { appValue(it, ranking) } ?: 1.0
                (if (allApps) ordered else ordered.take(3)).forEach { app -> item(key = "app:${app.pkg}") {
                    AppRankingRow(app, ranking, maxValue) { selectedApp = app }
                } }
                if (ordered.size > 3 && !allApps) item {
                    TextButton(onClick = { allApps = true }, modifier = Modifier.fillMaxWidth()) { Text(label("전체 ${ordered.size}개 앱 보기", "See all ${ordered.size} apps")) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    selectedApp?.let { app -> AppPeriodDialog(app, buckets, visibleUsage, start, end, daily) { selectedApp = null } }
}

@Composable private fun SmallMetric(title: String, value: String) {
    Column { Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
}
@Composable private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(color, CircleShape)); Spacer(Modifier.width(4.dp)); Text(text, fontSize = 11.sp) }
}
private fun appValue(app: ExplorerApp, mode: Int) = when (mode) { 1 -> app.loss ?: 0.0; 2 -> app.opens.toDouble(); else -> app.usage.toDouble() }

@Composable private fun AppRankingRow(app: ExplorerApp, mode: Int, maximum: Double, onClick: () -> Unit) {
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
                Text(app.name, fontWeight = FontWeight.SemiBold)
                Text(if (mode == 1) label("실제 감점 기여", "Recorded score loss") else "${app.opens}x · ≤1m ${app.shortOpens}x",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(when (mode) { 1 -> "−${points(app.loss ?: 0.0)}"; 2 -> "${app.opens}x"; else -> minutes(app.usage) }, color = color, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
            Box(Modifier.fillMaxWidth((appValue(app, mode) / maximum.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color, CircleShape))
        }
    }
}

@Composable private fun SelectionReadout(bucket: ExplorerBucket, unlocks: Boolean) {
    val isHourly = bucket.end - bucket.start <= STAT_HOUR
    val isSingleDay = bucket.end - bucket.start <= 24L * STAT_HOUR && !isHourly
    val titleText = when {
        isHourly -> "${dateLabel(bucket.start, "yyyy년 M월 d일 H:00")} ~ ${dateLabel(bucket.end, "H:00")}"
        isSingleDay -> dateLabel(bucket.start, "yyyy년 M월 d일")
        else -> "${dateLabel(bucket.start, "M/d")} – ${dateLabel(bucket.end, "M/d")}"
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titleText, fontWeight = FontWeight.SemiBold)
        bucket.score?.let {
            val scoreText = if (it.low == it.high || isHourly) {
                label("코어 지수 ${points(it.last)}P", "Core Index ${points(it.last)}P")
            } else {
                label("코어 지수 ${points(it.last)}P · 당일 최저 ${points(it.low)} ~ 최고 ${points(it.high)}",
                    "Core Index ${points(it.last)}P · Low ${points(it.low)} ~ High ${points(it.high)}")
            }
            Text(scoreText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Text(label("화면시간 ${bucket.usage?.let(::minutes) ?: "—"} · 관리 앱 ${minutes(bucket.managed)}",
            "Screen time ${bucket.usage?.let(::minutes) ?: "—"} · Managed ${minutes(bucket.managed)}"), fontSize = 12.sp)
        Text(label("앱 오픈 ${bucket.opens?.toString() ?: "—"}x (1분 이하 ${bucket.shortOpens}x) · 언락 ${bucket.unlocks?.toString() ?: "—"}x",
            "App opens ${bucket.opens?.toString() ?: "—"}x (≤1 min: ${bucket.shortOpens}x) · Unlocks ${bucket.unlocks?.toString() ?: "—"}x"), fontSize = 12.sp)
    }
}

@Composable
private fun ExplorerChart(
    buckets: List<ExplorerBucket>, samples: List<CoreIndexSampleEntity>, line: Boolean,
    detailed: Boolean, unlocks: Boolean, start: Long, end: Long, selection: Long?,
    zoom: Float,
    onZoom: (Float) -> Unit, onSelect: (Long) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    val foreground = MaterialTheme.colorScheme.onSurface
    val isHourly = buckets.firstOrNull()?.let { it.end - it.start <= STAT_HOUR } ?: false
    val values = buckets.mapNotNull { it.score }.flatMap { listOf(it.low, it.high) } + samples.map { it.exactScore }
    val rawMin = values.minOrNull() ?: 60.0
    val rawMax = values.maxOrNull() ?: 80.0
    val span = maxOf(16.0, rawMax - rawMin + 8.0)
    val low = ((rawMin + rawMax - span) / 2).coerceIn(0.0, (100.0 - span).coerceAtLeast(0.0))
    val high = (low + span).coerceAtMost(100.0)
    val scroll = rememberScrollState()
    val currentZoom by rememberUpdatedState(zoom)
    var initialScroll by remember(start / STAT_HOUR, line, buckets.size) { mutableStateOf(false) }
    LaunchedEffect(scroll.maxValue) { if (!initialScroll && scroll.maxValue > 0) { scroll.scrollTo(scroll.maxValue); initialScroll = true } }
    val selected = selection?.let { t -> buckets.firstOrNull { t >= it.start && t < it.end } }
    val chartDescription = selected?.let { label("${dateLabel(it.start)} 점수 ${it.score?.last?.roundToInt() ?: "—"}", "${dateLabel(it.start)}, score ${it.score?.last?.roundToInt() ?: "—"}") }
        ?: label("코어 지수 차트", "Core Index Chart")

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val viewportWidth = maxWidth - 44.dp
        val width = if (line) {
            maxOf(viewportWidth, (viewportWidth / 24f) * buckets.size * zoom)
        } else if (buckets.isEmpty()) {
            viewportWidth
        } else if (isHourly) {
            maxOf(viewportWidth, (viewportWidth / 36f) * buckets.size * zoom)
        } else {
            viewportWidth // 4주는 스크롤 없이 한 화면에 핏(Fit)
        }
        val height = 310.dp

        Row {
            Column(Modifier.weight(1f).horizontalScroll(scroll, enabled = line || isHourly)) {
                Canvas(
                    Modifier.width(width).height(height).semantics { contentDescription = chartDescription }
                        .pointerInput(start, end, width) {
                            detectTapGestures { onSelect(start + ((end - start) * (it.x / size.width).coerceIn(0f, 0.999999f)).toLong()) }
                        }
                        .pointerInput(start, end, width) {
                            detectDragGesturesAfterLongPress(onDragStart = { onSelect(start + ((end - start) * (it.x / size.width).coerceIn(0f, 0.999999f)).toLong()) }) { change, _ ->
                                onSelect(start + ((end - start) * (change.position.x / size.width).coerceIn(0f, 0.999999f)).toLong())
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
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

                    if (line) {
                        // 1. 1일 24시간 실시간 라인 차트 (토스 첨부2 스타일)
                        val readings = if (samples.isNotEmpty()) samples.map { Triple(it.timestampMillis, it.exactScore, it.scoreModelVersion) }
                            else buckets.mapNotNull { it.score?.let { s -> Triple(s.lastAt, s.last, s.model) } }

                        if (readings.isNotEmpty()) {
                            // 선 자체에 녹/노/빨 색상 투입 (Y축 높이 점수 기반 다색 그라데이션)
                            val lineBrush = Brush.verticalGradient(
                                colors = listOf(ScoreGreen, ScoreYellow, ScoreRed),
                                startY = graphTop,
                                endY = graphBottom
                            )

                            // 선 그리기
                            readings.zipWithNext().forEach { (a, b) ->
                                drawLine(
                                    brush = lineBrush,
                                    start = Offset(x(a.first), y(a.second)),
                                    end = Offset(x(b.first), y(b.second)),
                                    strokeWidth = 2.4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            // 토스 첨부2 스타일 최고/최저 점 라벨
                            val maxReading = readings.maxByOrNull { it.second }
                            val minReading = readings.minByOrNull { it.second }

                            val paintGreen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = ScoreGreen.toArgb()
                                textSize = 11.sp.toPx()
                                isFakeBoldText = true
                            }
                            val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = ScoreRed.toArgb()
                                textSize = 11.sp.toPx()
                                isFakeBoldText = true
                            }

                            maxReading?.let {
                                val mx = x(it.first)
                                val my = y(it.second)
                                drawCircle(ScoreGreen, 3.5.dp.toPx(), Offset(mx, my))
                                drawContext.canvas.nativeCanvas.drawText(label("최고 ${points(it.second)}", "High ${points(it.second)}"), (mx - 22.dp.toPx()).coerceAtLeast(4.dp.toPx()), my - 8.dp.toPx(), paintGreen)
                            }
                            minReading?.let {
                                val mx = x(it.first)
                                val my = y(it.second)
                                drawCircle(ScoreRed, 3.5.dp.toPx(), Offset(mx, my))
                                drawContext.canvas.nativeCanvas.drawText(label("최저 ${points(it.second)}", "Low ${points(it.second)}"), (mx - 22.dp.toPx()).coerceAtLeast(4.dp.toPx()), my + 16.dp.toPx(), paintRed)
                            }
                        }
                    } else if (isHourly) {
                        // 시간별 보기
                        val hourlyReadings = buckets.mapNotNull { b -> b.score?.let { s -> Triple((b.start + b.end) / 2, s.last, s.model) } }
                        val lineBrush = Brush.verticalGradient(listOf(ScoreGreen, ScoreYellow, ScoreRed), startY = graphTop, endY = graphBottom)
                        hourlyReadings.zipWithNext().forEach { (a, b) ->
                            drawLine(brush = lineBrush, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 2.4.dp.toPx(), StrokeCap.Round)
                        }
                    } else {
                        // 2. 4주 28일 일봉 캔들스틱 차트 (토스 첨부1 스타일)
                        val slotW = size.width / buckets.size.coerceAtLeast(1)
                        val candleW = maxOf(3.dp.toPx(), slotW * 0.55f)

                        // 캔들 그리기
                        buckets.forEachIndexed { i, b ->
                            b.score?.let { s ->
                                val mid = i * slotW + slotW / 2f
                                val yOpen = y(s.first)
                                val yClose = y(s.last)
                                val yHigh = y(s.high)
                                val yLow = y(s.low)
                                val isUp = s.last >= s.first
                                val candleColor = if (isUp) ScoreGreen else ScoreRed // 상승 녹색, 하락 빨강

                                // 꼬리선 (Wick)
                                drawLine(candleColor, Offset(mid, yHigh), Offset(mid, yLow), 1.3.dp.toPx())

                                // 몸통 (Body)
                                val bodyTop = minOf(yOpen, yClose)
                                val bodyH = maxOf(2.dp.toPx(), abs(yClose - yOpen))
                                drawRect(candleColor, Offset(mid - candleW / 2f, bodyTop), Size(candleW, bodyH))
                            }
                        }

                        // 일별 코어 지수선 (노란색 - 당일 종가들을 연결하여 4주 연속 흐름 표시)
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

                        // 토스 첨부1 스타일 최고/최저 화살표 라벨
                        val maxBucket = buckets.maxByOrNull { it.score?.high ?: 0.0 }
                        val minBucket = buckets.minByOrNull { it.score?.low ?: 100.0 }

                        val paintGreen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = ScoreGreen.toArgb(); textSize = 11.sp.toPx(); isFakeBoldText = true
                        }
                        val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = ScoreRed.toArgb(); textSize = 11.sp.toPx(); isFakeBoldText = true
                        }

                        maxBucket?.score?.let { s ->
                            val idx = buckets.indexOf(maxBucket)
                            val mid = idx * slotW + slotW / 2f
                            val text = "↓ ${points(s.high)} (${dateLabel(maxBucket.start, "MM.dd")})"
                            drawContext.canvas.nativeCanvas.drawText(text, (mid - 32.dp.toPx()).coerceIn(4.dp.toPx(), size.width - 90.dp.toPx()), y(s.high) - 8.dp.toPx(), paintGreen)
                        }
                        minBucket?.score?.let { s ->
                            val idx = buckets.indexOf(minBucket)
                            val mid = idx * slotW + slotW / 2f
                            val text = "↑ ${points(s.low)} (${dateLabel(minBucket.start, "MM.dd")})"
                            drawContext.canvas.nativeCanvas.drawText(text, (mid - 32.dp.toPx()).coerceIn(4.dp.toPx(), size.width - 90.dp.toPx()), y(s.low) + 16.dp.toPx(), paintRed)
                        }
                    }

                    // 하단 볼륨: 화면 사용 시간 막대 (일반 시간 + 몰입 관리 시간 구분)
                    val maxUsage = buckets.maxOfOrNull { it.usage ?: 0L }?.coerceAtLeast(60_000L) ?: 60_000L
                    val slotW = size.width / buckets.size.coerceAtLeast(1)
                    val barW = maxOf(2.dp.toPx(), slotW * 0.58f)
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

                        // 일반 화면시간 (청록색)
                        if (generalH > 0f) {
                            drawRect(
                                color = Color(0xFF26A69A).copy(alpha = 0.75f),
                                topLeft = Offset(mid - barW / 2f, volumeBottom - generalH),
                                size = Size(barW, generalH)
                            )
                        }
                        // 몰입 관리 시간 (빨간색 - 일반 시간 위에 스택)
                        if (managedH > 0f) {
                            drawRect(
                                color = ScoreRed.copy(alpha = 0.85f),
                                topLeft = Offset(mid - barW / 2f, volumeBottom - totalH),
                                size = Size(barW, managedH)
                            )
                        }
                    }

                    // 일별 앱 오픈 횟수 꺾은선 + 점 그래프 (하늘색/Cyan)
                    val maxOpens = buckets.maxOfOrNull { it.opens ?: 0 }?.coerceAtLeast(1) ?: 1
                    val openLineBottom = volumeBottom
                    val openLineTop = volumeBottom - 44.dp.toPx()
                    val opensColor = Color(0xFF38BDF8)

                    val opensPath = Path()
                    var opensStarted = false
                    val openPoints = mutableListOf<Offset>()

                    buckets.forEachIndexed { i, b ->
                        val opens = b.opens ?: 0
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

                    // 선택 십자선
                    selection?.let { time ->
                        drawLine(muted.copy(alpha = 0.6f), Offset(x(time), 0f), Offset(x(time), volumeBottom), 1.2.dp.toPx())
                    }

                    // X축 날짜 라벨 (6시간 간격: 00:00, 06:00, 12:00, 18:00 표시, 00시는 날짜 병기)
                    val axisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = 10.sp.toPx() }
                    val labelStep = if (line) 6 else 4
                    buckets.forEachIndexed { index, b ->
                        if (index % labelStep == 0) {
                            val axisLabel = formatBucketAxisLabel(b.start, isHourly, line, isRolling = line)
                            drawContext.canvas.nativeCanvas.drawText(axisLabel, x(b.start).coerceAtMost(size.width - 40.dp.toPx()), size.height - 4.dp.toPx(), axisPaint)
                        }
                    }
                }
            }

            // 우측 Y축 눈금 및 현재가 배지 (토스 첨부1 스타일)
            Canvas(Modifier.width(44.dp).height(height)) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = 10.sp.toPx() }
                val graphTop = 24.dp.toPx()
                val graphBottom = 200.dp.toPx()
                fun y(score: Double) = graphBottom - (graphBottom - graphTop) * ((score - low) / (high - low).coerceAtLeast(1.0)).toFloat()

                // Y축 눈금 (상, 중, 하)
                listOf(high, (low + high) / 2, low).forEach { valScore ->
                    drawContext.canvas.nativeCanvas.drawText(valScore.roundToInt().toString(), 6.dp.toPx(), y(valScore) + 4.dp.toPx(), paint)
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
                        textSize = 10.sp.toPx()
                        isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(points(lastScore), 6.dp.toPx(), lastY + 3.5.dp.toPx(), badgePaint)
                }

                // 볼륨 및 오픈 라벨
                val volPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = 9.sp.toPx() }
                val openPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color(0xFF38BDF8).toArgb(); textSize = 9.sp.toPx() }
                drawContext.canvas.nativeCanvas.drawText(label("오픈", "Opens"), 4.dp.toPx(), 238.dp.toPx(), openPaint)
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
        Scaffold(modifier = Modifier.testTag("app-detail"), topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, label("뒤로", "Back")) } }) }) { padding ->
            ResponsiveContent(Modifier.padding(padding)) {
                LazyColumn(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    item { Text("${dateLabel(start)} – ${dateLabel(end)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SmallMetric(label("사용시간", "Usage"), minutes(app.usage)); SmallMetric(label("실행", "Opens"), "${app.opens}x"); SmallMetric(label("1분 이하", "≤1 min"), "${app.shortOpens}x")
                    } }
                    item { Text(label("기록된 감점", "Recorded loss"), fontWeight = FontWeight.Bold)
                        Text(app.loss?.let { "−${points(it)}" } ?: "—", fontSize = 28.sp, color = ScoreRed, fontWeight = FontWeight.Bold)
                    }
                    item { AppMetricBars(label("사용시간 추세", "Usage trend"), series, false) }
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
        selected?.let { Text("${dateLabel(it.start)} · ${if (opens) "${it.opens ?: 0}" else minutes(it.usage ?: 0)}", fontSize = 12.sp) }
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
            Text(series.firstOrNull()?.let { dateLabel(it.start) } ?: "—", fontSize = 11.sp)
            Text(series.lastOrNull()?.let { dateLabel(it.end) } ?: "—", fontSize = 11.sp)
        }
    }
}
