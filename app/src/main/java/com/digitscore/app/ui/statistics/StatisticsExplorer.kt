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
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreOrange
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
    return if (min >= 60) label("${min / 60}시간 ${min % 60}분", "${min / 60}h ${min % 60}m")
    else if (min > 0) label("${min}분", "${min}m") else label("${ms / 1000}초", "${ms / 1000}s")
}
private fun points(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onNavigateBack: () -> Unit, database: DigitsDatabase? = null) {
    val context = LocalContext.current
    val db = remember(database) { database ?: DigitsDatabase.getInstance(context) }
    val dao = remember { db.statisticsDao() }
    val zone = ZoneId.systemDefault()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var period by rememberSaveable { mutableIntStateOf(1) }
    var offsetDays by rememberSaveable { mutableIntStateOf(0) }
    var hourCandles by rememberSaveable { mutableStateOf(false) }
    var detailed by rememberSaveable { mutableStateOf(true) }
    var showUnlocks by rememberSaveable { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<Long?>(null) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedApp by remember { mutableStateOf<ExplorerApp?>(null) }
    var ranking by rememberSaveable { mutableIntStateOf(0) }
    var allApps by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showHelp by remember { mutableStateOf(false) }
    BackHandler(enabled = selectedDay != null && selectedApp == null) { selectedDay = null; selectedTime = null }
    val liveScore by ScoreRepository.rollingScoreDetail.collectAsState()
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }

    val isDay = period == 1 || selectedDay != null
    val end = selectedDay?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.coerceAtMost(now)
        ?: (now - offsetDays * 24L * STAT_HOUR)
    val start = selectedDay?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        ?: if (period == 1) end - 24 * STAT_HOUR else Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
            .minusDays((period - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    val daily = !isDay && !hourCandles
    val queryStart = statisticHour(start)
    val queryEnd = statisticHour(end) + STAT_HOUR
    val usage by remember(queryStart, queryEnd) { dao.observeUsage(queryStart, queryEnd) }.collectAsState(emptyList())
    val hourlyScores by remember(queryStart, queryEnd) { dao.observeScores(queryStart, queryEnd) }.collectAsState(emptyList())
    val impacts by remember(queryStart, queryEnd) { dao.observeImpacts(queryStart, queryEnd) }.collectAsState(emptyList())
    val samples by remember(queryStart, queryEnd, isDay) {
        if (isDay) db.coreIndexSampleDao().observeBetween(queryStart, queryEnd) else flowOf(emptyList())
    }.collectAsState(emptyList())
    val sessions by remember(queryStart, queryEnd, isDay) {
        if (isDay) db.foregroundUsageSessionDao().observeBetween(queryStart, queryEnd + 120_000) else flowOf(emptyList())
    }.collectAsState(emptyList())
    val dailyApps by remember(start, end) {
        db.dailyAppUsageDao().observeRange(dateLabel(start, "yyyy-MM-dd"), dateLabel(end - 1, "yyyy-MM-dd"))
    }.collectAsState(emptyList())
    val histories by db.scoreDao().getAllScoreHistories().collectAsState(emptyList())
    val rawAvailable = isDay && start >= now - 30L * 24 * STAT_HOUR
    val exactInteractions = isDay && start >= now - 24L * STAT_HOUR
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
    val apps = remember(visibleUsage, visibleImpacts, dailyApps, isDay) {
        explorerApps(visibleUsage, visibleImpacts, dailyApps, !isDay)
    }
    val selected = selectedTime?.let { t -> buckets.firstOrNull { t >= it.start && t < it.end } }
    val selectedSample = if (isDay && selectedTime != null && selected != null)
        visibleSamples.filter { it.timestampMillis >= selected.start && it.timestampMillis < selected.end }
            .minByOrNull { abs(it.timestampMillis - selectedTime!!) } else null
    val readings = if (isDay && visibleSamples.isNotEmpty()) visibleSamples.map {
        ScoreHourEntity(statisticHour(it.timestampMillis), it.scoreModelVersion, it.timestampMillis, it.timestampMillis,
            it.exactScore, it.exactScore, it.exactScore, it.exactScore)
    } else buckets.mapNotNull { it.score }
    val latest = readings.lastOrNull()
    val currentModel = latest?.model ?: 5
    val comparable = readings.filter { it.model == currentModel }
    val displayed = if (selected != null) selectedSample?.exactScore ?: selected.score?.last
        else if (offsetDays == 0 && selectedDay == null && currentModel == 5) liveScore?.exactScore ?: latest?.last else latest?.last
    val change = if (selected == null && comparable.isNotEmpty() && displayed != null)
        displayed - comparable.first().first else null
    val coverage = visibleImpacts.filter { it.packageName.isEmpty() }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (selectedDay != null) selectedDay.toString() else label("사용 통계", "Usage statistics"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { if (selectedDay != null) { selectedDay = null; selectedTime = null } else onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, label("뒤로", "Back"))
            } }, actions = { TextButton(onClick = { showHelp = true }) { Text(label("읽는 법", "Guide")) } })
    }) { padding ->
        ResponsiveContent(Modifier.padding(padding)) {
            LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (selected != null) label("선택한 기록", "Selected reading") else label("코어 지수", "Core Index"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(displayed?.roundToInt()?.toString() ?: "—", modifier = Modifier.testTag("statistics-score"), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                            change?.let { Text("${if (it >= 0) "+" else ""}${points(it)}", color = if (it >= 0) ScoreGreen else ScoreRed,
                                fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                        }
                        Text(if (selectedSample != null) dateLabel(selectedSample.timestampMillis)
                            else if (selected != null) "${dateLabel(selected.start)} – ${dateLabel(selected.end)}"
                            else change?.let { label("${dateLabel(comparable.first().firstAt)} 첫 기록 대비", "Since first reading at ${dateLabel(comparable.first().firstAt)}") }
                                ?: label("기록이 쌓이면 변화가 표시됩니다", "Change appears as readings accumulate"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if ((selectedSample?.scoreModelVersion ?: selected?.score?.model ?: currentModel) != 5)
                            Text(label("이전 점수 방식의 기록", "Previous scoring model"), color = MaterialTheme.colorScheme.outline)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { detailed = !detailed }) { Text(if (detailed) label("✓ 자세한 차트", "✓ Detailed chart") else label("자세한 차트", "Detailed chart")) }
                        Spacer(Modifier.weight(1f))
                        if (!isDay && period <= 28) TextButton(onClick = { hourCandles = !hourCandles; selectedTime = null; zoom = 1f }) {
                            Text(if (hourCandles) label("시간봉 ▾", "Hourly ▾") else label("일봉 ▾", "Daily ▾"))
                        }
                        TextButton(onClick = { zoom = if (zoom >= 4f) 1f else zoom * 2f }) { Text("${zoom.toInt()}×") }
                    }
                    ExplorerChart(buckets, if (isDay) visibleSamples else emptyList(), isDay, detailed, showUnlocks,
                        start, end, selectedTime, zoom, { zoom = it.coerceIn(1f, 4f) }, { selectedTime = it })
                    if (detailed) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(MaterialTheme.colorScheme.primary, label("일반 사용", "General"))
                        Spacer(Modifier.width(12.dp)); Legend(ScoreRed, label("관리 사용", "Managed"))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showUnlocks = !showUnlocks }) { Text(if (showUnlocks) label("잠금 해제 ▾", "Unlocks ▾") else label("앱 오픈 ▾", "App opens ▾")) }
                    }
                    if (selected != null) {
                        SelectionReadout(selected, showUnlocks)
                        Row(Modifier.padding(horizontal = 14.dp)) {
                            TextButton(onClick = { selectedTime = null }) { Text(label("선택 해제", "Clear")) }
                            if (!isDay) TextButton(onClick = {
                                selectedDay = Instant.ofEpochMilli(selected.start).atZone(zone).toLocalDate()
                                selectedTime = null; zoom = 1f
                            }) { Text(label("이날 자세히 보기 ›", "Explore this day ›")) }
                        }
                    } else Text(label("좌우로 이동 · 길게 눌러 시점 선택 · 두 손가락으로 확대", "Scroll sideways · hold to inspect · pinch to zoom"),
                        Modifier.padding(horizontal = 22.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    // Accessible alternatives to chart-only gestures.
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (selectedDay != null) selectedDay = selectedDay!!.minusDays(1)
                            else offsetDays = (offsetDays + if (isDay) 1 else 7).coerceAtMost((365 - period).coerceAtLeast(0))
                            selectedTime = null
                        }, enabled = start > now - 364L * 24 * STAT_HOUR) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, label("이전 기간", "Previous period")) }
                        Text("${dateLabel(start, "yy/MM/dd")} – ${dateLabel(end, "MM/dd HH:mm")}", Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { offsetDays = 0; selectedDay = null; selectedTime = null }) { Text(label("현재로", "Latest")) }
                        IconButton(onClick = {
                            if (selectedDay != null) selectedDay = selectedDay!!.plusDays(1)
                            else offsetDays = (offsetDays - if (isDay) 1 else 7).coerceAtLeast(0)
                            selectedTime = null
                        }, enabled = end < now - 60_000) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, label("다음 기간", "Next period")) }
                    }
                }
                if (selectedDay == null) item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to label("24시간", "24h"), 7 to label("1주", "1w"), 28 to label("4주", "4w"),
                            90 to label("3개월", "3m"), 365 to label("1년", "1y")).forEach { (days, text) ->
                            FilterChip(selected = period == days, onClick = { period = days; offsetDays = 0; selectedTime = null; hourCandles = false; zoom = 1f; allApps = false }, label = { Text(text) })
                        }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(label("기간 요약", "Period summary"), fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val countsKnown = apps.isNotEmpty() || buckets.any { it.opens != null }
                            SmallMetric(label("화면", "Screen"), buckets.mapNotNull { it.usage }.takeIf { it.isNotEmpty() }?.sum()?.let(::minutes) ?: "—")
                            SmallMetric(label("앱 오픈", "App opens"), if (countsKnown) apps.sumOf { it.opens }.toString() else "—")
                            SmallMetric(label("1분 이하", "≤1 min"), if (countsKnown) apps.sumOf { it.shortOpens }.toString() else "—")
                        }
                        val top = apps.maxByOrNull { it.usage }
                        Text(if (top != null && top.usage > 0) label("${top.name}을 가장 오래 사용했습니다 · ${minutes(top.usage)}", "Most time spent in ${top.name} · ${minutes(top.usage)}")
                            else label("이 구간의 사용 기록이 아직 없습니다", "No usage recorded for this interval"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (coverage.isNotEmpty()) Text(label("기록된 감점 −${points(coverage.sumOf { it.loss })} · 회복 +${points(coverage.sumOf { it.recovery })}",
                            "Recorded loss −${points(coverage.sumOf { it.loss })} · recovery +${points(coverage.sumOf { it.recovery })}"), fontSize = 12.sp)
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label("앱별 요약", "Apps at a glance"), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(label("사용시간", "Usage"), label("점수 영향", "Score impact"), label("오픈 횟수", "Opens")).forEachIndexed { index, text ->
                                FilterChip(ranking == index, { ranking = index; allApps = false }, label = { Text(text) })
                            }
                        }
                        if (ranking == 1) Text(if (coverage.isEmpty()) label("감점 기여도 기록이 아직 없습니다. 과거 사용시간으로 추정하지 않습니다.", "No recorded attribution yet. Past usage is not converted into estimated loss.")
                            else label("실제 감점 합계 · ${dateLabel(coverage.minOf { it.firstAt })}부터 기록 · 회복은 별도", "Actual recorded loss · since ${dateLabel(coverage.minOf { it.firstAt })} · recovery shown separately"),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, confirmButton = { TextButton(onClick = { showHelp = false }) { Text(label("닫기", "Close")) } },
        title = { Text(label("차트 읽는 법", "Reading the chart")) }, text = {
            Text(label("선은 기록된 점수를 연결합니다. 일주일 이상은 캔들 몸통으로 첫 점수와 마지막 점수, 꼬리로 기록된 최고·최저를 표시합니다.\n\n녹색은 상승, 빨간색은 하락, 회색은 이전 점수 방식입니다. 사용시간과 실행 횟수는 같은 시간축이며 주황색은 전체 오픈 중 1분 이하 실행입니다.\n\n수면 중에는 회복하지 않습니다. 빈 구간은 관측 기록이 없으며 점수 선이 연결되어도 중간 상태를 측정했다는 뜻은 아닙니다.\n\n30일 이후에는 시간별 집계를 보여줍니다. 시간별·일별 기록은 1년 보관합니다.",
                "Lines connect recorded readings. For a week or longer, candle bodies show first and last scores; wicks show recorded highs and lows.\n\nGreen means a rise, red a fall, gray a previous scoring model. Usage and opens share the time axis. Orange is the subset of opens lasting at most one minute.\n\nSleep pauses recovery. Blank intervals have no observations; a connecting line does not imply that the interval was measured.\n\nAfter 30 days, hourly aggregates replace fine detail. Hourly and daily history lasts one year."))
        })
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
                Text(if (mode == 1) label("실제 감점 기여", "Recorded score loss") else label("${app.opens}회 오픈 · 1분 이하 ${app.shortOpens}회", "${app.opens} opens · ${app.shortOpens} ≤1 min"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(when (mode) { 1 -> "−${points(app.loss ?: 0.0)}"; 2 -> label("${app.opens}회", "${app.opens}"); else -> minutes(app.usage) }, color = color, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
            Box(Modifier.fillMaxWidth((appValue(app, mode) / maximum.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color, CircleShape))
        }
    }
}

@Composable private fun SelectionReadout(bucket: ExplorerBucket, unlocks: Boolean) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${dateLabel(bucket.start)} – ${dateLabel(bucket.end, "HH:mm")}", fontWeight = FontWeight.SemiBold)
        bucket.score?.let { Text(label("시작 ${points(it.first)} → 마지막 ${points(it.last)} · 최저 ${points(it.low)} / 최고 ${points(it.high)}",
            "Open ${points(it.first)} → close ${points(it.last)} · low ${points(it.low)} / high ${points(it.high)}"), fontSize = 12.sp) }
        Text(label("화면 ${bucket.usage?.let(::minutes) ?: "—"} · 관리 ${minutes(bucket.managed)}", "Screen ${bucket.usage?.let(::minutes) ?: "—"} · managed ${minutes(bucket.managed)}"), fontSize = 12.sp)
        Text(label("앱 오픈 ${bucket.opens?.toString() ?: "—"}회 · 1분 이하 ${bucket.shortOpens}회 · 잠금 해제 ${bucket.unlocks?.toString() ?: "—"}회",
            "${bucket.opens?.toString() ?: "—"} opens · ${bucket.shortOpens} ≤1 min · ${bucket.unlocks?.toString() ?: "—"} unlocks"), fontSize = 12.sp)
    }
}

@Composable
private fun ExplorerChart(
    buckets: List<ExplorerBucket>, samples: List<CoreIndexSampleEntity>, line: Boolean,
    detailed: Boolean, unlocks: Boolean, start: Long, end: Long, selection: Long?,
    zoom: Float, onZoom: (Float) -> Unit, onSelect: (Long) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    val foreground = MaterialTheme.colorScheme.onSurface
    val values = buckets.mapNotNull { it.score }.flatMap { listOf(it.low, it.high) } + samples.map { it.exactScore }
    val minValue = values.minOrNull() ?: 60.0
    val maxValue = values.maxOrNull() ?: 80.0
    val span = maxOf(20.0, maxValue - minValue + 10.0)
    val low = ((minValue + maxValue - span) / 2).coerceIn(0.0, (100.0 - span).coerceAtLeast(0.0))
    val high = (low + span).coerceAtMost(100.0)
    val scroll = rememberScrollState()
    val currentZoom by rememberUpdatedState(zoom)
    var initialScroll by remember(start / STAT_HOUR, line, buckets.size) { mutableStateOf(false) }
    LaunchedEffect(scroll.maxValue) { if (!initialScroll && scroll.maxValue > 0) { scroll.scrollTo(scroll.maxValue); initialScroll = true } }
    val selected = selection?.let { t -> buckets.firstOrNull { t >= it.start && t < it.end } }
    val chartDescription = selected?.let { label("${dateLabel(it.start)} 점수 ${it.score?.last?.roundToInt() ?: "—"}, 오픈 ${it.opens ?: 0}회", "${dateLabel(it.start)}, score ${it.score?.last?.roundToInt() ?: "—"}, ${it.opens ?: 0} opens") }
        ?: label("코어 지수와 사용 기록 차트. 아래 이전·다음 기록 버튼으로도 탐색할 수 있습니다.", "Core Index and usage chart. Previous and next reading buttons are available below.")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val width = maxOf(maxWidth - 36.dp, if (line) 0.dp else (buckets.size * 14).dp) * zoom
        val height = if (detailed) 360.dp else 230.dp
        Row {
            Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                Canvas(Modifier.width(width).height(height).semantics { contentDescription = chartDescription }
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
                    }) {
                    val graphTop = 10.dp.toPx(); val graphBottom = 190.dp.toPx()
                    fun x(time: Long) = size.width * ((time - start).toDouble() / (end - start).coerceAtLeast(1)).toFloat()
                    fun y(score: Double) = graphBottom - (graphBottom - graphTop) * ((score - low) / (high - low).coerceAtLeast(1.0)).toFloat()
                    listOf(low, (low + high) / 2, high).forEach {
                        drawLine(muted.copy(alpha = 0.16f), Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx())
                    }
                    if (line) {
                        val readings = if (samples.isNotEmpty()) samples.map { Triple(it.timestampMillis, it.exactScore, it.scoreModelVersion) }
                            else buckets.mapNotNull { it.score?.let { s -> Triple(s.lastAt, s.last, s.model) } }
                        readings.zipWithNext().forEach { (a, b) ->
                            if (a.third == b.third) drawLine(if (b.third == 5) primary else muted,
                                Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 2.5.dp.toPx(), StrokeCap.Round)
                        }
                        readings.lastOrNull()?.let { drawCircle(primary.copy(alpha = 0.12f), 10.dp.toPx(), Offset(x(it.first), y(it.second))); drawCircle(primary, 4.dp.toPx(), Offset(x(it.first), y(it.second))) }
                    } else buckets.forEach { bucket -> bucket.score?.let { s ->
                        val mid = x((bucket.start + bucket.end) / 2)
                        val bodyWidth = ((x(bucket.end) - x(bucket.start)) * 0.6f).coerceIn(2.dp.toPx(), 18.dp.toPx())
                        val color = if (s.model != 5) muted else if (s.last >= s.first) ScoreGreen else ScoreRed
                        if (s.firstAt == s.lastAt) drawCircle(color, 2.5.dp.toPx(), Offset(mid, y(s.last))) else {
                            drawLine(color, Offset(mid, y(s.high)), Offset(mid, y(s.low)), 1.3.dp.toPx())
                            drawRect(color, Offset(mid - bodyWidth / 2, minOf(y(s.first), y(s.last))), Size(bodyWidth, abs(y(s.first) - y(s.last)).coerceAtLeast(1.5.dp.toPx())))
                        }
                    } }
                    if (detailed) {
                        val maxUsage = buckets.maxOfOrNull { it.usage ?: 0 }?.coerceAtLeast(60_000) ?: 60_000
                        val maxCount = buckets.maxOfOrNull { if (unlocks) it.unlocks ?: 0 else it.opens ?: 0 }?.coerceAtLeast(1) ?: 1
                        buckets.forEach { b ->
                            val left = x(b.start); val right = x(b.end); val barWidth = (right - left) * 0.65f
                            val mid = (left + right) / 2
                            val usageHeight = 48.dp.toPx() * (b.usage ?: 0) / maxUsage.toFloat()
                            val managedHeight = 48.dp.toPx() * b.managed / maxUsage.toFloat()
                            drawRect(primary.copy(alpha = 0.42f), Offset(mid - barWidth / 2, 258.dp.toPx() - usageHeight), Size(barWidth, usageHeight))
                            drawRect(ScoreRed, Offset(mid - barWidth / 2, 258.dp.toPx() - managedHeight), Size(barWidth, managedHeight))
                            val count = if (unlocks) b.unlocks ?: 0 else b.opens ?: 0
                            val countHeight = 48.dp.toPx() * count / maxCount.toFloat()
                            drawRect(primary.copy(alpha = 0.5f), Offset(mid - barWidth / 2, 326.dp.toPx() - countHeight), Size(barWidth, countHeight))
                            if (!unlocks) {
                                val shortHeight = 48.dp.toPx() * b.shortOpens / maxCount.toFloat()
                                drawRect(ScoreOrange, Offset(mid - barWidth / 2, 326.dp.toPx() - shortHeight), Size(barWidth, shortHeight))
                            }
                        }
                    }
                    selection?.let { time -> drawLine(muted, Offset(x(time), 0f), Offset(x(time), size.height - 20.dp.toPx()), 1.dp.toPx()) }
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = foreground.toArgb(); textSize = 10.sp.toPx() }
                    // Keep dates readable in the visible viewport, even while scrolling
                    // hundreds of hourly candles. Eight labels for the entire canvas
                    // would leave most scrolled windows without any time labels.
                    val labelStep = maxOf(1, kotlin.math.ceil(56.dp.toPx() / (size.width / buckets.size.coerceAtLeast(1))).toInt())
                    buckets.forEachIndexed { index, b -> if (index % labelStep == 0) {
                        drawContext.canvas.nativeCanvas.drawText(dateLabel(b.start, if (line) "HH:mm" else if (b.end - b.start <= STAT_HOUR) "dd HH'h'" else "MM/dd"),
                            x(b.start).coerceAtMost(size.width - 35.dp.toPx()), size.height - 4.dp.toPx(), paint)
                    } }
                }
            }
            Canvas(Modifier.width(36.dp).height(height)) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = 10.sp.toPx() }
                listOf(high to 16f, (low + high) / 2 to 105f, low to 190f).forEach { (value, dp) ->
                    drawContext.canvas.nativeCanvas.drawText(value.roundToInt().toString(), 4.dp.toPx(), dp.dp.toPx(), paint)
                }
                if (detailed) {
                    val maxMinutes = ((buckets.maxOfOrNull { it.usage ?: 0 } ?: 0) / 60_000).coerceAtLeast(1)
                    val maxCount = (buckets.maxOfOrNull { if (unlocks) it.unlocks ?: 0 else it.opens ?: 0 } ?: 0).coerceAtLeast(1)
                    drawContext.canvas.nativeCanvas.drawText(label("${maxMinutes}분", "${maxMinutes}m"), 2.dp.toPx(), 216.dp.toPx(), paint)
                    drawContext.canvas.nativeCanvas.drawText("0", 4.dp.toPx(), 258.dp.toPx(), paint)
                    drawContext.canvas.nativeCanvas.drawText(maxCount.toString(), 4.dp.toPx(), 284.dp.toPx(), paint)
                    drawContext.canvas.nativeCanvas.drawText("0", 4.dp.toPx(), 326.dp.toPx(), paint)
                }
            }
        }
    }
    if (detailed && !unlocks) Text(label("주황색: 전체 오픈 중 1분 이하 실행", "Orange: opens lasting ≤1 min, included in the total"),
        Modifier.padding(horizontal = 22.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val index = selected?.let { buckets.indexOf(it) } ?: buckets.lastIndex
        TextButton(onClick = { buckets.getOrNull((index - 1).coerceAtLeast(0))?.let { onSelect(it.start) } }, enabled = index > 0) { Text(label("이전 기록", "Previous reading")) }
        TextButton(onClick = { buckets.getOrNull((index + 1).coerceAtMost(buckets.lastIndex))?.let { onSelect(it.start) } }, enabled = index < buckets.lastIndex) { Text(label("다음 기록", "Next reading")) }
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
                        SmallMetric(label("사용시간", "Usage"), minutes(app.usage)); SmallMetric(label("오픈", "Opens"), app.opens.toString()); SmallMetric(label("1분 이하", "≤1 min"), app.shortOpens.toString())
                    } }
                    item { Text(label("기록된 감점 기여", "Recorded score loss"), fontWeight = FontWeight.Bold)
                        Text(app.loss?.let { "−${points(it)}" } ?: "—", fontSize = 30.sp, color = ScoreRed)
                        Text(app.impactFrom?.let { label("${dateLabel(it)}부터 기록된 실제 감점입니다. 휴식 회복은 이 앱에 배정하지 않습니다.", "Actual loss recorded since ${dateLabel(it)}. Rest recovery is not assigned to this app.") }
                            ?: label("이 구간의 감점 기여도 기록이 없습니다.", "No attribution recorded for this interval."), fontSize = 12.sp) }
                    item { AppMetricBars(label("사용시간 추세", "Usage trend"), series, false) }
                    item { AppMetricBars(label("오픈 횟수 추세", "Open frequency"), series, true) }
                    item { Text(label("시간별 기록이 없는 과거 날짜는 추세 막대를 비워 둡니다. 상단 합계에는 보관된 일별 기록이 포함될 수 있습니다.",
                        "Days without hourly history remain blank in these charts. Totals may include retained daily records."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
