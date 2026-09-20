package com.digitscore.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextButton
import com.digitscore.app.i18n.Text
import com.digitscore.app.i18n.UiTranslator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.CoreIndexHistoryRepair
import com.digitscore.app.data.UnlockInsights
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.CoreIndexSampleEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.InformationDetailDialog
import com.digitscore.app.ui.components.ResponsiveContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { DigitsDatabase.getInstance(context) }
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: 최근 24시간, 1: 최근 4주
    var selectedDetail by remember { mutableStateOf<StatisticsDetail?>(null) }
    var selectedDay by remember { mutableStateOf<DailyScoreHistoryEntity?>(null) }
    var rollingWindowEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val rollingWindowStartMillis = rollingWindowEndMillis - ROLLING_24_HOURS_MILLIS
    val fourWeekStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(FOUR_WEEK_DAYS - 1))
        }.timeInMillis
    }

    val allHistories by db.scoreDao().getAllScoreHistories().collectAsState(initial = emptyList())
    val rollingSamples by remember(rollingWindowStartMillis) {
        db.coreIndexSampleDao().observeSince(rollingWindowStartMillis)
    }.collectAsState(initial = emptyList())
    val fourWeekSamples by remember(fourWeekStartMillis) {
        db.coreIndexSampleDao().observeSince(fourWeekStartMillis)
    }.collectAsState(initial = emptyList())
    val rollingSessions by remember(rollingWindowStartMillis) {
        db.foregroundUsageSessionDao().observeSince(rollingWindowStartMillis)
    }.collectAsState(initial = emptyList())
    var rollingUnlockInsights by remember { mutableStateOf<UnlockInsights?>(null) }
    val selectedDaySamplesFlow = remember(selectedDay?.dateString) {
        selectedDay?.let { db.coreIndexSampleDao().observeForDate(it.dateString) }
            ?: flowOf(emptyList())
    }
    val selectedDaySamples by selectedDaySamplesFlow.collectAsState(initial = emptyList())

    // 누적 UsageStats 기반 소급은 정확한 전면 앱 시간을 보장하지 못하므로 사용하지 않습니다.
    // 데이터 없는 날을 100점으로 만든 구형 행만 정리하고, 실제 UsageEvents 측정 결과만 둡니다.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val rangeStart = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -30)
            }
            db.scoreDao().deleteLegacyEmptyBackfills(
                startDateString = dateFormat.format(rangeStart.time),
                endDateString = dateFormat.format(today.time)
            )
            CoreIndexHistoryRepair.repairLegacyRows(db)
        }
    }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 0) {
            while (isActive) {
                val windowEnd = System.currentTimeMillis()
                rollingWindowEndMillis = windowEnd
                rollingUnlockInsights = withContext(Dispatchers.IO) {
                    UsageStatsHelper.getRolling24HourUnlockInsights(context, windowEnd)
                }
                delay(60_000L)
            }
        }
    }

    // 장기 탭은 DB 행 개수 LIMIT가 아니라 오늘을 포함한 네 개의 달력 주 범위입니다.
    val histories = remember(allHistories) {
        historiesInCalendarRange(allHistories, FOUR_WEEK_DAYS)
    }
    val coreIndexHistories = remember(histories) {
        coreIndexHistories(histories)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사용 균형 통계 & 리포트", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = UiTranslator.translate("뒤로가기"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        ResponsiveContent(modifier = Modifier.padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            // 1. 분석 목적 선택 (최근 24시간 흐름 / 최근 4주 패턴)
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(
                                "시간별 · 24H",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Text(
                                "일별 · 4W",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("주별 · 4W") }
                    )
                }
            }

            if (selectedTabIndex == 0) {
                item {
                    RollingMarketChartCard(
                        samples = rollingSamples,
                        sessions = rollingSessions,
                        unlockInsights = rollingUnlockInsights,
                        windowStartMillis = rollingWindowStartMillis,
                        windowEndMillis = rollingWindowEndMillis,
                        onClick = {
                            val visible = rollingSamples.filter {
                                it.timestampMillis in rollingWindowStartMillis..rollingWindowEndMillis
                            }
                            selectedDetail = StatisticsDetail(
                                title = "최근 24시간 코어 지수",
                                value = visible.lastOrNull()?.let { "현재 ${it.score}점" } ?: "표본 준비 중",
                                description = if (visible.isEmpty()) {
                                    "이 기기에서 저장된 최근 24시간 코어 지수 표본이 아직 없습니다."
                                } else {
                                    "최저 ${visible.minOf { it.score }}점, 최고 ${visible.maxOf { it.score }}점이며 ${visible.size}개 구간을 표시합니다."
                                },
                                supportingText = "화면을 끄고 쉬는 동안 연속 사용 부하가 줄어 코어 지수가 회복됩니다. 차트는 다음 사용 시 계산된 회복값까지 흐름을 이어 표시합니다."
                            )
                        }
                    )
                }
                item {
                    RollingUsageSummaryCards(
                        sessions = rollingSessions,
                        unlockInsights = rollingUnlockInsights,
                        windowStartMillis = rollingWindowStartMillis,
                        windowEndMillis = rollingWindowEndMillis,
                        onDetailRequested = { selectedDetail = it }
                    )
                }
            } else if (selectedTabIndex == 2) {
                item {
                    WeeklyMarketSummary(histories, fourWeekSamples) { selectedDay = it }
                }
                item {
                    FourWeekPatternCard(histories = histories, onClick = { selectedDetail = it })
                }
            } else {
                item {
                    FourWeekMarketChartCard(
                        histories = histories,
                        samples = fourWeekSamples,
                        onDaySelected = { selectedDay = it },
                        onClick = {
                            val latest = coreIndexHistories.lastOrNull()?.finalScore
                            val high = coreIndexHistories.maxOfOrNull { it.finalScore }
                            val low = coreIndexHistories.minOfOrNull { it.finalScore }
                            val presetChanges = coreIndexPresetChanges(coreIndexHistories)
                            selectedDetail = StatisticsDetail(
                                title = "일별 코어 지수 추세",
                                value = latest?.let { "현재 ${it}점" } ?: "기록 준비 중",
                                description = if (latest == null) {
                                    "이 기간에 계산된 코어 지수가 아직 없습니다."
                                } else {
                                    "4주 차트에 표시된 코어 지수 범위는 최저 ${low}점에서 최고 ${high}점입니다."
                                },
                                supportingText = buildString {
                                    append("현재 지수는 최근 24시간 사용 흐름으로 계산하며, 4주 차트는 날짜별 변화를 보여줍니다.")
                                    if (presetChanges.isNotEmpty()) {
                                        append("\n프리셋 변경: ")
                                        append(
                                            presetChanges.joinToString(" · ") { change ->
                                                "${change.dateString} ${CoreIndexPreset.fromId(change.presetId).title}"
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }

                item {
                    UsageAndUnlockBarChartCard(
                        histories = histories,
                        onClick = {
                            val count = histories.size.coerceAtLeast(1)
                            val averageScreen = histories.sumOf { it.totalScreenTimeMinutes } / count
                            val averageUnlocks = histories.sumOf { it.unlockCount } / count
                            selectedDetail = StatisticsDetail(
                                title = "사용 시간과 언락",
                                value = "화면 평균 ${formatMinutesToHoursAndMinutes(averageScreen)} · 언락 평균 ${averageUnlocks}회",
                                description = "4주 차트에서 날짜별 화면시간, 관리 앱 시간과 언락 횟수를 함께 비교합니다.",
                                supportingText = "청록색은 전체 화면시간, 빨간색은 관리 앱 시간, 노란 점은 언락 횟수입니다."
                            )
                        }
                    )
                }

                item {
                    FourWeekPatternCard(
                        histories = histories,
                        onClick = { selectedDetail = it }
                    )
                }

                item {
                    AnalyticsSummaryCards(
                        usageHistories = histories,
                        coreIndexHistories = coreIndexHistories,
                        onDetailRequested = { selectedDetail = it }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        }
    }

    selectedDetail?.let { detail ->
        InformationDetailDialog(
            title = detail.title,
            value = detail.value,
            description = detail.description,
            supportingText = detail.supportingText,
            onDismiss = { selectedDetail = null }
        )
    }

    selectedDay?.let { history ->
        IntradayCoreIndexDialog(
            history = history,
            samples = selectedDaySamples,
            onPrevious = {
                selectedDay = coreIndexHistories.lastOrNull { it.dateString < history.dateString } ?: history
            },
            onNext = {
                selectedDay = coreIndexHistories.firstOrNull { it.dateString > history.dateString } ?: history
            },
            onDismiss = { selectedDay = null }
        )
    }
}

private const val ROLLING_24_HOURS_MILLIS = 24 * 60 * 60_000L
private const val FOUR_WEEK_DAYS = 28

internal data class RollingUsageSummary(
    val totalMillis: Long,
    val managedMillis: Long,
    val hourlyTotalMillis: List<Long>,
    val hourlyManagedMillis: List<Long>,
    val longestSessionMillis: Long,
    val longestSessionAppName: String?,
    val topAppMillis: Long,
    val topAppName: String?
)

private data class PrimarySessionKey(
    val packageName: String,
    val sessionStartTimeMillis: Long
)

internal fun summarizeRollingUsage(
    sessions: List<ForegroundUsageSessionEntity>,
    windowStartMillis: Long,
    windowEndMillis: Long
): RollingUsageSummary {
    val duration = (windowEndMillis - windowStartMillis).coerceAtLeast(1L)
    val bucketMillis = (duration / 24).coerceAtLeast(1L)
    val hourlyTotal = LongArray(24)
    val hourlyManaged = LongArray(24)
    val packageTotals = mutableMapOf<String, Long>()
    val appNames = mutableMapOf<String, String>()
    val primarySessionDurations = mutableMapOf<PrimarySessionKey, Long>()

    sessions.sortedBy { it.startTimeMillis }.forEach { session ->
        val start = maxOf(session.startTimeMillis, windowStartMillis)
        val end = minOf(session.endTimeMillis, windowEndMillis)
        if (end <= start) return@forEach

        val clippedDuration = end - start
        packageTotals[session.packageName] =
            (packageTotals[session.packageName] ?: 0L) + clippedDuration
        appNames[session.packageName] = session.appName
        val sessionKey = PrimarySessionKey(session.packageName, session.sessionStartTimeMillis)
        primarySessionDurations[sessionKey] =
            (primarySessionDurations[sessionKey] ?: 0L) + clippedDuration

        var cursor = start
        while (cursor < end) {
            val bucket = ((cursor - windowStartMillis) / bucketMillis)
                .toInt()
                .coerceIn(0, 23)
            val bucketEnd = minOf(
                end,
                windowStartMillis + (bucket + 1L) * bucketMillis
            )
            val interval = (bucketEnd - cursor).coerceAtLeast(0L)
            hourlyTotal[bucket] += interval
            if (session.effectiveCategoryLevel >= 3) hourlyManaged[bucket] += interval
            cursor = bucketEnd
        }
    }

    val topPackage = packageTotals.maxByOrNull { it.value }
    val longestSession = primarySessionDurations.maxByOrNull { it.value }
    return RollingUsageSummary(
        totalMillis = hourlyTotal.sum(),
        managedMillis = hourlyManaged.sum(),
        hourlyTotalMillis = hourlyTotal.toList(),
        hourlyManagedMillis = hourlyManaged.toList(),
        longestSessionMillis = longestSession?.value ?: 0L,
        longestSessionAppName = longestSession?.key?.packageName?.let(appNames::get),
        topAppMillis = topPackage?.value ?: 0L,
        topAppName = topPackage?.key?.let(appNames::get)
    )
}

@Composable
private fun MarketIndexHeader(
    title: String,
    current: Int?,
    change: Int?,
    changeLabel: String? = null,
    low: Int?,
    high: Int?,
    rangeLabel: String,
    onClick: () -> Unit
) {
    val changeColor = when {
        change == null || change == 0 -> MaterialTheme.colorScheme.outline
        change > 0 -> ScoreGreen
        else -> ScoreRed
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    current?.toString() ?: "—",
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (change != null) {
                    Column(modifier = Modifier.padding(start = 10.dp, bottom = 5.dp)) {
                        Text(
                            text = when {
                                change > 0 -> "▲ +$change"
                                change < 0 -> "▼ $change"
                                else -> "― 0"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = changeColor
                        )
                        changeLabel?.let {
                            Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            DetailChevron(tint = MaterialTheme.colorScheme.primary)
            Text(
                "$rangeLabel 최저 ${low ?: "—"} · 최고 ${high ?: "—"}",
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun topAppInWindow(
    sessions: List<ForegroundUsageSessionEntity>,
    startMillis: Long,
    endMillis: Long
): String? = sessions.asSequence()
    .mapNotNull { session ->
        val start = maxOf(session.startTimeMillis, startMillis)
        val end = minOf(session.endTimeMillis, endMillis)
        (end - start).takeIf { it > 0L }?.let { session.appName to it }
    }
    .groupBy({ it.first }, { it.second })
    .maxByOrNull { (_, values) -> values.sum() }
    ?.key

@Composable
private fun RollingMarketChartCard(
    samples: List<CoreIndexSampleEntity>,
    sessions: List<ForegroundUsageSessionEntity>,
    unlockInsights: UnlockInsights?,
    windowStartMillis: Long,
    windowEndMillis: Long,
    onClick: () -> Unit
) {
    val visible = remember(samples, windowStartMillis, windowEndMillis) {
        samples.filter { it.timestampMillis in windowStartMillis..windowEndMillis }
            .sortedBy { it.timestampMillis }
    }
    val summary = remember(sessions, windowStartMillis, windowEndMillis) {
        summarizeRollingUsage(sessions, windowStartMillis, windowEndMillis)
    }
    val axisColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    var selectedIndex by remember(visible) {
        mutableStateOf<Int?>(visible.lastIndex.takeIf { it >= 0 })
    }
    val selectedSample = selectedIndex?.let(visible::getOrNull)
    val current = visible.lastOrNull()?.score
    val change = if (visible.size >= 2) current?.minus(visible.first().score) else null
    val duration = (windowEndMillis - windowStartMillis).coerceAtLeast(1L)
    val selectedBucket = selectedSample?.let {
        (((it.timestampMillis - windowStartMillis) * 24) / duration).toInt().coerceIn(0, 23)
    }
    val selectedApp = remember(sessions, selectedBucket, windowStartMillis, duration) {
        selectedBucket?.let { bucket ->
            val bucketStart = windowStartMillis + duration * bucket / 24
            val bucketEnd = windowStartMillis + duration * (bucket + 1) / 24
            topAppInWindow(sessions, bucketStart, bucketEnd)
        }
    }
    val tooltipFormatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            MarketIndexHeader(
                title = "현재 코어 지수 · 24H",
                current = current,
                change = change,
                changeLabel = "24시간 내 첫 기록 대비",
                low = visible.minOfOrNull { it.score },
                high = visible.maxOfOrNull { it.score },
                rangeLabel = "24H 범위",
                onClick = onClick
            )
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    Text("최근 24시간 코어 지수 표본을 준비하고 있습니다.", color = axisColor)
                }
            } else {
                selectedSample?.let { sample ->
                    val bucket = selectedBucket ?: 0
                    val usage = summary.hourlyTotalMillis.getOrElse(bucket) { 0L }
                    val managed = summary.hourlyManagedMillis.getOrElse(bucket) { 0L }
                    val unlocks = unlockInsights?.hourlyUnlockCounts?.getOrElse(bucket) { 0 } ?: 0
                    Text(
                        buildString {
                            append(tooltipFormatter.format(java.util.Date(sample.timestampMillis)))
                            append(" · ${sample.score}점")
                            append("\n화면 ${formatMinutesToHoursAndMinutes(usage / 60_000L)}")
                            append(" · 관리 ${formatMinutesToHoursAndMinutes(managed / 60_000L)}")
                            append(" · 언락 ${unlocks}회")
                            selectedApp?.let { append(" · $it") }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surfaceColor.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val chartModifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .pointerInput(visible, windowStartMillis, windowEndMillis) {
                        detectTapGestures { position ->
                            val target = windowStartMillis +
                                (duration * (position.x / size.width).coerceIn(0f, 1f)).toLong()
                            selectedIndex = visible.indices.minByOrNull {
                                kotlin.math.abs(visible[it].timestampMillis - target)
                            }
                        }
                    }
                    .pointerInput(visible, windowStartMillis, windowEndMillis) {
                        fun select(x: Float) {
                            val target = windowStartMillis +
                                (duration * (x / size.width).coerceIn(0f, 1f)).toLong()
                            selectedIndex = visible.indices.minByOrNull {
                                kotlin.math.abs(visible[it].timestampMillis - target)
                            }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { select(it.x) },
                            onHorizontalDrag = { changeEvent, _ -> select(changeEvent.position.x) }
                        )
                    }
                Canvas(chartModifier) {
                    val scoreBottom = 174.dp.toPx()
                    val volumeTop = 190.dp.toPx()
                    val volumeBottom = size.height - 12.dp.toPx()
                    listOf(50, 70, 90).forEach { score ->
                        val y = scoreBottom - scoreBottom * score / 100f
                        drawLine(
                            color = axisColor.copy(alpha = 0.22f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    for (hour in 0..24 step 6) {
                        val x = size.width * hour / 24f
                        drawLine(
                            axisColor.copy(alpha = 0.10f),
                            Offset(x, 0f),
                            Offset(x, volumeBottom),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    val points = visible.map { sample ->
                        val ratio = ((sample.timestampMillis - windowStartMillis).toFloat() /
                            (windowEndMillis - windowStartMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        Offset(
                            size.width * ratio,
                            scoreBottom - scoreBottom * sample.score.coerceIn(0, 100) / 100f
                        )
                    }
                    if (points.size > 1) {
                        val fillPath = Path().apply {
                            moveTo(points.first().x, scoreBottom)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, scoreBottom)
                            close()
                        }
                        drawPath(
                            fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.18f), Color.Transparent),
                                startY = 0f,
                                endY = scoreBottom
                            )
                        )
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEachIndexed { index, point ->
                                if (visible[index].scoreModelVersion == visible[index + 1].scoreModelVersion)
                                    lineTo(point.x, point.y)
                                else moveTo(point.x, point.y)
                            }
                        }
                        drawPath(
                            linePath,
                            lineColor,
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    if (points.size == 1) {
                        drawCircle(lineColor, 3.dp.toPx(), points.first())
                    }

                    val maxUsage = (summary.hourlyTotalMillis.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
                    val maxUnlock = (unlockInsights?.hourlyUnlockCounts?.maxOrNull() ?: 0).coerceAtLeast(1)
                    val step = size.width / 24f
                    val barWidth = (step * 0.64f).coerceAtLeast(2.dp.toPx())
                    summary.hourlyTotalMillis.forEachIndexed { index, value ->
                        val x = index * step + (step - barWidth) / 2f
                        val availableHeight = volumeBottom - volumeTop
                        val totalHeight = availableHeight * value / maxUsage.toFloat()
                        val managedHeight = availableHeight * summary.hourlyManagedMillis[index] / maxUsage.toFloat()
                        drawRoundRect(
                            Color(0xFF26A69A).copy(alpha = 0.5f),
                            Offset(x, volumeBottom - totalHeight),
                            Size(barWidth, totalHeight),
                            CornerRadius(3f)
                        )
                        drawRoundRect(
                            ScoreRed.copy(alpha = 0.88f),
                            Offset(x, volumeBottom - managedHeight),
                            Size(barWidth, managedHeight),
                            CornerRadius(3f)
                        )
                        val unlock = unlockInsights?.hourlyUnlockCounts?.getOrElse(index) { 0 } ?: 0
                        if (unlock > 0) {
                            val y = volumeBottom - availableHeight * unlock / maxUnlock.toFloat()
                            drawCircle(ScoreYellow, 2.6.dp.toPx(), Offset(x + barWidth / 2f, y))
                        }
                    }
                    selectedIndex?.let { index ->
                        points.getOrNull(index)?.let { point ->
                            drawLine(
                                axisColor.copy(alpha = 0.7f),
                                Offset(point.x, 0f),
                                Offset(point.x, volumeBottom),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawCircle(surfaceColor, 6.dp.toPx(), point)
                            drawCircle(lineColor, 4.dp.toPx(), point)
                        }
                    }
                }
                RollingTimeAxis(windowStartMillis, windowEndMillis, axisColor)
                ChartLegendGrid(
                    listOf(
                        lineColor to "코어 지수",
                        Color(0xFF26A69A) to "화면",
                        ScoreRed to "관리",
                        ScoreYellow to "언락"
                    )
                )
            }
            Text(
                "차트를 누르거나 드래그해 시점별 기록을 확인하세요. 화면을 끄고 쉰 구간은 다음 회복값까지 선으로 이어지며 사용량은 0으로 표시됩니다.",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartLegendGrid(items: List<Pair<Color, String>>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { (color, label) ->
                    Box(Modifier.weight(1f)) { ChartLegend(color, label) }
                }
            }
        }
    }
}

@Composable
private fun RollingTimeAxis(startMillis: Long, endMillis: Long, color: Color) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatter.format(java.util.Date(startMillis)), fontSize = 11.sp, color = color)
        Text(
            formatter.format(java.util.Date(startMillis + ROLLING_24_HOURS_MILLIS / 2)),
            fontSize = 11.sp,
            color = color
        )
        Text(formatter.format(java.util.Date(endMillis)), fontSize = 11.sp, color = color)
    }
}

@Composable
private fun RollingUsageSummaryCards(
    sessions: List<ForegroundUsageSessionEntity>,
    unlockInsights: UnlockInsights?,
    windowStartMillis: Long,
    windowEndMillis: Long,
    onDetailRequested: (StatisticsDetail) -> Unit
) {
    val summary = remember(sessions, windowStartMillis, windowEndMillis) {
        summarizeRollingUsage(sessions, windowStartMillis, windowEndMillis)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("24시간 핵심 정보", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "가장 많이 사용",
                value = summary.topAppName ?: "—",
                icon = Icons.Default.PhoneAndroid,
                iconColor = MaterialTheme.colorScheme.primary,
                subtitle = formatMinutesToHoursAndMinutes(summary.topAppMillis / 60_000L),
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "가장 많이 사용한 앱",
                            summary.topAppName ?: "기록 없음",
                            "최근 24시간 ${formatMinutesToHoursAndMinutes(summary.topAppMillis / 60_000L)} 사용했습니다.",
                            "화면 ON·잠금 해제 상태에서 최상단이었던 시간만 포함합니다."
                        )
                    )
                }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "최장 연속 사용",
                value = formatMinutesToHoursAndMinutes(summary.longestSessionMillis / 60_000L),
                icon = Icons.Default.Star,
                iconColor = ScoreOrange,
                subtitle = summary.longestSessionAppName ?: "기록 없음",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "최장 연속 사용",
                            formatMinutesToHoursAndMinutes(summary.longestSessionMillis / 60_000L),
                            summary.longestSessionAppName?.let { "$it 앱의 가장 긴 전면 사용 세션입니다." }
                                ?: "최근 24시간에 저장된 사용 세션이 없습니다.",
                            "화면을 끄거나 다른 앱으로 전환하면 세션이 종료됩니다."
                        )
                    )
                }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "관리 앱 사용",
                value = formatMinutesToHoursAndMinutes(summary.managedMillis / 60_000L),
                icon = Icons.Default.CheckCircle,
                iconColor = ScoreRed,
                subtitle = "몰입 관리 앱",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "관리 앱 사용",
                            formatMinutesToHoursAndMinutes(summary.managedMillis / 60_000L),
                            "최근 24시간 중 몰입 관리 앱이 전면 또는 병렬 화면에 표시된 시간입니다.",
                            "앱 등급을 변경하면 이후 세션부터 새 등급으로 기록됩니다."
                        )
                    )
                }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "언락 간격",
                value = unlockInsights?.averageIntervalMinutes?.let { "${it}분" } ?: "—",
                icon = Icons.Default.LockOpen,
                iconColor = ScoreYellow,
                subtitle = "${unlockInsights?.unlockCount ?: 0}회 열음",
                onClick = {
                    val notifications = unlockInsights?.notificationCount ?: 0
                    onDetailRequested(
                        StatisticsDetail(
                            "24시간 언락 흐름",
                            "${unlockInsights?.unlockCount ?: 0}회",
                            "연속 언락 사이 평균 간격은 ${unlockInsights?.averageIntervalMinutes?.let { "${it}분" } ?: "계산 전"}입니다.",
                            if (unlockInsights?.notificationEventsSupported == true) {
                                "같은 기간 OS 감지 알림은 ${notifications}건입니다."
                            } else {
                                "이 기기에서는 알림 이벤트 수를 제공하지 않을 수 있습니다."
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun FourWeekPatternCard(
    histories: List<DailyScoreHistoryEntity>,
    onClick: (StatisticsDetail) -> Unit
) {
    val today = LocalDate.now()
    // Missing dates stay missing: the comparison is two exact calendar windows,
    // not the last fourteen rows that happened to be recorded.
    val recent = historiesInCalendarRange(histories, 7, today)
    val previous = historiesInCalendarRange(histories, 7, today.minusDays(7))
    val recentAverage = recent.takeIf { it.isNotEmpty() }
        ?.let { rows -> rows.sumOf { it.totalScreenTimeMinutes } / rows.size }
    val previousAverage = previous.takeIf { it.isNotEmpty() }
        ?.let { rows -> rows.sumOf { it.totalScreenTimeMinutes } / rows.size }
    val weekdayAverages = remember(histories) {
        histories.mapNotNull { row ->
            runCatching { LocalDate.parse(row.dateString).dayOfWeek.value }.getOrNull()
                ?.let { it to row.totalScreenTimeMinutes }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.average().toLong() }
    }
    val busiest = weekdayAverages.maxByOrNull { it.value }
    val weekdayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val weeklyMax = maxOf(recentAverage ?: 0L, previousAverage ?: 0L, 1L)
    val weekdayMax = (weekdayAverages.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val weeklyDelta = if (recentAverage != null && previousAverage != null) {
        recentAverage - previousAverage
    } else null

    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            val delta = if (recentAverage != null && previousAverage != null) recentAverage - previousAverage else null
            onClick(
                StatisticsDetail(
                    "4주 사용 패턴",
                    busiest?.let { "${weekdayLabels[it.key - 1]}요일 평균 ${formatMinutesToHoursAndMinutes(it.value)}" }
                        ?: "기록 준비 중",
                    delta?.let {
                        "최근 7일의 하루 평균 화면시간이 이전 7일보다 ${kotlin.math.abs(it)}분 ${if (it > 0) "늘었습니다" else if (it < 0) "줄었습니다" else "같습니다"}."
                    } ?: "두 개의 7일 구간에 사용 기록이 있으면 주간 변화를 비교합니다.",
                    "주간 막대와 요일 막대는 사용 기록이 있는 날의 화면시간 평균입니다. 기록이 없는 날을 0분으로 채우지 않습니다."
                )
            )
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("4주 사용 패턴", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "화면을 얼마나 오래 쓰는지 주간·요일별로 비교합니다.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
            Text("주간 하루 평균", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            WeeklyUsageBar(
                label = "이전 7일",
                value = previousAverage,
                maximum = weeklyMax,
                color = outlineColor.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(7.dp))
            WeeklyUsageBar(
                label = "최근 7일",
                value = recentAverage,
                maximum = weeklyMax,
                color = primaryColor
            )
            Text(
                weeklyDelta?.let { delta ->
                    when {
                        delta > 0 -> "이전 7일보다 하루 평균 ${formatMinutesToHoursAndMinutes(delta)} 증가"
                        delta < 0 -> "이전 7일보다 하루 평균 ${formatMinutesToHoursAndMinutes(-delta)} 감소"
                        else -> "이전 7일과 하루 평균 사용시간이 같습니다."
                    }
                } ?: "비교할 주간 사용 기록을 준비하고 있습니다.",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
                    .height(1.dp)
                    .background(outlineColor.copy(alpha = 0.18f))
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("요일별 하루 평균", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(
                    busiest?.let { "최다 ${weekdayLabels[it.key - 1]} · ${formatMinutesToHoursAndMinutes(it.value)}" }
                        ?: "기록 준비 중",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val step = size.width / 7f
                weekdayLabels.indices.forEach { index ->
                    val value = weekdayAverages[index + 1] ?: 0L
                    val height = size.height * value / weekdayMax.toFloat()
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.7f),
                        topLeft = Offset(index * step + step * 0.2f, size.height - height),
                        size = Size(step * 0.6f, height),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekdayLabels.forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
            }
            Text(
                "막대는 사용 기록이 있는 날의 화면시간 평균입니다.",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun WeeklyUsageBar(
    label: String,
    value: Long?,
    maximum: Long,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.width(54.dp), fontSize = 10.sp)
        Box(
            Modifier
                .weight(1f)
                .height(12.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(((value ?: 0L) / maximum.toFloat()).coerceIn(0f, 1f))
                    .height(12.dp)
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
        Text(
            value?.let(::formatMinutesToHoursAndMinutes) ?: "—",
            modifier = Modifier.width(62.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val CORE_INDEX_SCORE_MODEL_VERSION = 2

internal fun coreIndexHistories(
    histories: List<DailyScoreHistoryEntity>
): List<DailyScoreHistoryEntity> = histories.filter {
    it.scoreModelVersion >= CORE_INDEX_SCORE_MODEL_VERSION
}

internal data class CoreIndexPresetChange(
    val dateString: String,
    val presetId: String
)

internal fun coreIndexPresetChanges(
    histories: List<DailyScoreHistoryEntity>
): List<CoreIndexPresetChange> = histories.zipWithNext().mapNotNull { (previous, current) ->
    if (previous.coreIndexPresetId == current.coreIndexPresetId) null else {
        CoreIndexPresetChange(current.dateString, current.coreIndexPresetId)
    }
}

private data class StatisticsDetail(
    val title: String,
    val value: String,
    val description: String,
    val supportingText: String? = null
)

internal fun historiesInCalendarRange(
    histories: List<DailyScoreHistoryEntity>,
    days: Int,
    today: LocalDate = LocalDate.now()
): List<DailyScoreHistoryEntity> {
    val safeDays = days.coerceAtLeast(1)
    val firstDate = today.minusDays((safeDays - 1).toLong())
    return histories.asSequence()
        .mapNotNull { history ->
            val date = runCatching { LocalDate.parse(history.dateString) }.getOrNull()
                ?: return@mapNotNull null
            if (date.isBefore(firstDate) || date.isAfter(today)) null else date to history
        }
        .sortedBy { it.first }
        .map { it.second }
        .toList()
}

internal fun calendarDayOffset(
    dateString: String,
    days: Int,
    today: LocalDate = LocalDate.now()
): Int {
    val safeDays = days.coerceAtLeast(2)
    val firstDate = today.minusDays((safeDays - 1).toLong())
    val date = runCatching { LocalDate.parse(dateString) }.getOrDefault(firstDate)
    return java.time.temporal.ChronoUnit.DAYS.between(firstDate, date)
        .toInt()
        .coerceIn(0, safeDays - 1)
}

internal data class DailyCoreRange(
    val history: DailyScoreHistoryEntity,
    val startScore: Int,
    val lastScore: Int,
    val low: Int,
    val high: Int,
    val hasIntradaySamples: Boolean
)

internal fun buildDailyCoreRanges(
    histories: List<DailyScoreHistoryEntity>,
    samples: List<CoreIndexSampleEntity>
): List<DailyCoreRange> {
    val samplesByDate = samples.groupBy { it.dateString }
    return histories.sortedBy { it.dateString }.map { history ->
        val daySamples = samplesByDate[history.dateString].orEmpty().sortedBy { it.timestampMillis }
        if (daySamples.isEmpty()) {
            DailyCoreRange(
                history = history,
                startScore = history.finalScore,
                lastScore = history.finalScore,
                low = history.finalScore,
                high = history.finalScore,
                hasIntradaySamples = false
            )
        } else {
            DailyCoreRange(
                history = history,
                startScore = daySamples.first().score,
                lastScore = daySamples.last().score,
                low = daySamples.minOf { it.score },
                high = daySamples.maxOf { it.score },
                hasIntradaySamples = true
            )
        }
    }
}

internal fun sevenDayMovingAverages(ranges: List<DailyCoreRange>): List<Float?> = ranges.map { current ->
    val currentDate = runCatching { LocalDate.parse(current.history.dateString) }.getOrNull()
        ?: return@map null
    val startDate = currentDate.minusDays(6)
    ranges.mapNotNull { candidate ->
        val date = runCatching { LocalDate.parse(candidate.history.dateString) }.getOrNull()
            ?: return@mapNotNull null
        candidate.lastScore.takeIf { !date.isBefore(startDate) && !date.isAfter(currentDate) &&
            candidate.history.scoreModelVersion == current.history.scoreModelVersion &&
            candidate.history.coreIndexPresetId == current.history.coreIndexPresetId }
    }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

@Composable
private fun FourWeekMarketChartCard(
    histories: List<DailyScoreHistoryEntity>,
    samples: List<CoreIndexSampleEntity>,
    onDaySelected: (DailyScoreHistoryEntity) -> Unit,
    onClick: () -> Unit
) {
    val scoreHistories = remember(histories) { coreIndexHistories(histories) }
    val ranges = remember(scoreHistories, samples) { buildDailyCoreRanges(scoreHistories, samples) }
    val movingAverages = remember(ranges) { sevenDayMovingAverages(ranges) }
    val axisColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    var selectedIndex by remember(ranges) {
        mutableStateOf<Int?>(ranges.lastIndex.takeIf { it >= 0 })
    }
    val selected = selectedIndex?.let(ranges::getOrNull)
    val chartScroll = rememberScrollState()
    LaunchedEffect(chartScroll.maxValue) {
        if (chartScroll.maxValue > 0) chartScroll.scrollTo(chartScroll.maxValue)
    }
    val current = ranges.lastOrNull()?.lastScore
    val allScores = ranges.flatMap { listOf(it.low, it.high) }
    val today = LocalDate.now()
    val firstDate = today.minusDays((FOUR_WEEK_DAYS - 1).toLong())
    val lastDayOffset = (FOUR_WEEK_DAYS - 1).toLong()

    fun dayOffset(dateString: String): Long = calendarDayOffset(
        dateString = dateString,
        days = FOUR_WEEK_DAYS,
        today = today
    ).toLong()

    fun nearestIndex(x: Float, width: Int): Int? {
        if (ranges.isEmpty()) return null
        val targetOffset = lastDayOffset * (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        return ranges.indices.minByOrNull {
            kotlin.math.abs(dayOffset(ranges[it].history.dateString) - targetOffset)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            MarketIndexHeader(
                title = "현재 코어 지수 · 4W",
                current = current,
                change = null,
                low = allScores.minOrNull(),
                high = allScores.maxOrNull(),
                rangeLabel = "4주 범위",
                onClick = onClick
            )
            Spacer(Modifier.height(12.dp))
            if (histories.isEmpty() && ranges.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "코어 지수 기록을 준비하고 있습니다.\n사용 흐름을 측정하면 날짜별 기록이 쌓입니다.",
                        color = axisColor,
                        fontSize = 13.sp
                    )
                }
            } else {
                selected?.let { range ->
                    Text(
                        buildString {
                            append(range.history.dateString)
                            append(" · 시작 ${range.startScore} · 마지막 ${range.lastScore}")
                            append(" · 최저 ${range.low} · 최고 ${range.high}")
                            append("\n")
                            append(UiTranslator.translate("일별 마지막 지수 7일 평균"))
                            append(": ")
                            append(selectedIndex?.let { movingAverages.getOrNull(it) }?.let {
                                String.format(Locale.getDefault(), "%.1f", it)
                            } ?: "—")
                            append("\n화면 ${formatMinutesToHoursAndMinutes(range.history.totalScreenTimeMinutes)}")
                            append(" · 관리 ${formatMinutesToHoursAndMinutes(range.history.distractingTimeMinutes)}")
                            append(" · 언락 ${range.history.unlockCount}회")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surfaceColor.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Column(Modifier.horizontalScroll(chartScroll)) {
                Canvas(
                    Modifier
                        .width(1000.dp)
                        .height(270.dp)
                        .pointerInput(ranges) {
                            detectTapGestures { tap ->
                                nearestIndex(tap.x, size.width)?.let { index ->
                                    selectedIndex = index
                                    onDaySelected(ranges[index].history)
                                }
                            }
                        }
                ) {
                    val scoreBottom = 190.dp.toPx()
                    val volumeTop = 205.dp.toPx()
                    val volumeBottom = size.height - 10.dp.toPx()
                    val plotInset = 6.dp.toPx()
                    val plotWidth = (size.width - plotInset * 2f).coerceAtLeast(1f)
                    fun scoreY(score: Float): Float = scoreBottom - scoreBottom * score.coerceIn(0f, 100f) / 100f
                    fun xForDate(dateString: String): Float =
                        plotInset + plotWidth * dayOffset(dateString) / lastDayOffset.toFloat()

                    fun xFor(index: Int): Float = xForDate(ranges[index].history.dateString)

                    listOf(50, 70, 90).forEach { score ->
                        val y = scoreY(score.toFloat())
                        drawLine(axisColor.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(score.toString(), 2.dp.toPx(), y - 3.dp.toPx(),
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = axisColor.toArgb()
                                textSize = 10.sp.toPx()
                            })
                    }
                    listOf(0, 7, 14, 21, 27).forEach { day ->
                        val x = plotInset + plotWidth * day / lastDayOffset.toFloat()
                        drawLine(
                            axisColor.copy(alpha = 0.1f),
                            Offset(x, 0f),
                            Offset(x, volumeBottom),
                            1.dp.toPx()
                        )
                    }

                    val maxUsage = (histories.maxOfOrNull { it.totalScreenTimeMinutes } ?: 0L)
                        .coerceAtLeast(1L)
                    val candleWidth = (size.width / FOUR_WEEK_DAYS * 0.58f).coerceIn(3.dp.toPx(), 10.dp.toPx())
                    histories.forEach { history ->
                        val x = xForDate(history.dateString)
                        val totalHeight = (volumeBottom - volumeTop) * history.totalScreenTimeMinutes / maxUsage.toFloat()
                        val managedHeight = (volumeBottom - volumeTop) * history.distractingTimeMinutes / maxUsage.toFloat()
                        drawRoundRect(
                            Color(0xFF26A69A).copy(alpha = 0.45f),
                            Offset(x - candleWidth / 2f, volumeBottom - totalHeight),
                            Size(candleWidth, totalHeight),
                            CornerRadius(2f)
                        )
                        drawRoundRect(
                            ScoreRed.copy(alpha = 0.8f),
                            Offset(x - candleWidth / 2f, volumeBottom - managedHeight),
                            Size(candleWidth, managedHeight),
                            CornerRadius(2f)
                        )
                    }

                    ranges.forEachIndexed { index, range ->
                        val x = xFor(index)
                        if (range.hasIntradaySamples) {
                            val candleColor = if (range.lastScore >= range.startScore) ScoreGreen else ScoreRed
                            drawLine(
                                candleColor,
                                Offset(x, scoreY(range.high.toFloat())),
                                Offset(x, scoreY(range.low.toFloat())),
                                1.5.dp.toPx()
                            )
                            val top = minOf(scoreY(range.startScore.toFloat()), scoreY(range.lastScore.toFloat()))
                            val bodyHeight = kotlin.math.abs(scoreY(range.startScore.toFloat()) - scoreY(range.lastScore.toFloat()))
                                .coerceAtLeast(2.dp.toPx())
                            drawRoundRect(
                                candleColor,
                                Offset(x - candleWidth / 2f, top),
                                Size(candleWidth, bodyHeight),
                                CornerRadius(2f)
                            )
                        } else {
                            drawCircle(axisColor, 2.5.dp.toPx(), Offset(x, scoreY(range.lastScore.toFloat())))
                        }
                    }

                    val movingPath = Path()
                    var pathStarted = false
                    movingAverages.forEachIndexed { index, value ->
                        val prior = ranges.getOrNull(index - 1)?.history
                        if (prior != null && (prior.scoreModelVersion != ranges[index].history.scoreModelVersion ||
                            prior.coreIndexPresetId != ranges[index].history.coreIndexPresetId)) {
                            pathStarted = false
                            val x = xFor(index)
                            drawLine(axisColor, Offset(x, 0f), Offset(x, scoreBottom),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                        }
                        if (value != null) {
                            val x = xFor(index)
                            val y = scoreY(value)
                            if (!pathStarted) {
                                movingPath.moveTo(x, y)
                                pathStarted = true
                            } else {
                                movingPath.lineTo(x, y)
                            }
                        }
                    }
                    if (pathStarted) {
                        drawPath(movingPath, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    }

                    selectedIndex?.let { index ->
                        val x = xFor(index)
                        drawLine(
                            axisColor.copy(alpha = 0.75f),
                            Offset(x, 0f),
                            Offset(x, volumeBottom),
                            1.dp.toPx()
                        )
                        drawCircle(surfaceColor, 5.dp.toPx(), Offset(x, scoreY(ranges[index].lastScore.toFloat())))
                        drawCircle(lineColor, 3.dp.toPx(), Offset(x, scoreY(ranges[index].lastScore.toFloat())))
                    }
                }
                Row(Modifier.width(1000.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    (0L..27L).forEach { offset ->
                        val date = firstDate.plusDays(offset)
                        Text(
                            "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 10.sp,
                            color = axisColor
                        )
                    }
                }
                }
                ChartLegendGrid(
                    listOf(
                        ScoreGreen to "첫 기록 대비 상승",
                        ScoreRed to "첫 기록 대비 하락",
                        lineColor to "일별 마지막 지수 7일 평균",
                        Color(0xFF26A69A) to "화면"
                    )
                )
                Text(
                    "범위봉은 하루의 시작·마지막·최저·최고 코어 지수를, 아래 막대는 기록된 모든 날짜의 화면 사용을 표시합니다.",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "점선은 계산 방식 또는 프리셋 변경일입니다. 평균선은 같은 기준의 기록만 사용하며, 누락일은 제외합니다. 아래 빨간 막대는 관리 앱 사용시간입니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "좌우로 스크롤하고 날짜를 누르면 하루 중 상세 흐름을 확인할 수 있습니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun WeeklyMarketSummary(
    histories: List<DailyScoreHistoryEntity>,
    samples: List<CoreIndexSampleEntity>,
    onDaySelected: (DailyScoreHistoryEntity) -> Unit
) {
    val ranges = remember(histories, samples) { buildDailyCoreRanges(coreIndexHistories(histories), samples) }
    val weeks = ranges.groupBy {
        LocalDate.parse(it.history.dateString).with(java.time.DayOfWeek.MONDAY)
    }.toSortedMap()
    val lineColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("주별 흐름 · 월요일 기준", fontWeight = FontWeight.Bold)
        if (weeks.isEmpty()) Text("기록 준비 중")
        weeks.forEach { (monday, days) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("$monday – ${monday.plusDays(6)}", fontWeight = FontWeight.Bold)
                    Text("${days.first().startScore} → ${days.last().lastScore}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                        val path = Path()
                        days.forEachIndexed { index, day ->
                            val x = size.width * (LocalDate.parse(day.history.dateString).dayOfWeek.value - 1) / 6f
                            val y = size.height * (1f - day.lastScore / 100f)
                            if (index == 0 || java.time.temporal.ChronoUnit.DAYS.between(
                                    LocalDate.parse(days[index - 1].history.dateString), LocalDate.parse(day.history.dateString)) != 1L ||
                                days[index - 1].history.scoreModelVersion != day.history.scoreModelVersion ||
                                days[index - 1].history.coreIndexPresetId != day.history.coreIndexPresetId) path.moveTo(x, y)
                            else path.lineTo(x, y)
                            drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
                        }
                        drawPath(path, lineColor, style = Stroke(2.dp.toPx()))
                    }
                    Text("일별 마지막 지수 · 날짜를 눌러 상세 보기", fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        days.forEach { day ->
                            TextButton(onClick = { onDaySelected(day.history) }) {
                                Text(day.history.dateString.substring(5))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntradayCoreIndexDialog(
    history: DailyScoreHistoryEntity,
    samples: List<CoreIndexSampleEntity>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val axisColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val minimum = samples.minOfOrNull { it.score }
    val maximum = samples.maxOfOrNull { it.score }
    val latest = samples.lastOrNull()?.score

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${history.dateString} · 하루 코어 지수", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onPrevious) { Text("이전 기록") }
                    TextButton(onClick = onNext) { Text("다음 기록") }
                }
                if (samples.isEmpty()) {
                    Text(
                        "이 날짜에는 하루 중 변화 기록이 없습니다. 세부 변화는 화면을 사용하는 동안 5분 단위로 저장됩니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "최저 ${minimum}점 · 최고 ${maximum}점 · 마지막 ${latest}점",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Canvas(
                        modifier = Modifier.fillMaxWidth().height(210.dp)
                    ) {
                        val graphHeight = size.height - 24.dp.toPx()
                        listOf(40, 70, 100).forEach { score ->
                            val y = graphHeight - graphHeight * (score / 100f)
                            drawLine(
                                color = axisColor.copy(alpha = 0.25f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val points = samples.map { sample ->
                            val calendar = Calendar.getInstance().apply {
                                timeInMillis = sample.timestampMillis
                            }
                            val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                                calendar.get(Calendar.MINUTE)
                            Offset(
                                x = size.width * (minuteOfDay / 1_440f),
                                y = graphHeight - graphHeight * (sample.score.coerceIn(0, 100) / 100f)
                            )
                        }
                        if (points.size > 1) {
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEachIndexed { index, point ->
                                    if (samples[index].scoreModelVersion == samples[index + 1].scoreModelVersion)
                                        lineTo(point.x, point.y)
                                    else moveTo(point.x, point.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        points.forEach { point ->
                            drawCircle(lineColor, radius = 2.5.dp.toPx(), center = point)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("00:00", fontSize = 11.sp, color = axisColor)
                        Text("12:00", fontSize = 11.sp, color = axisColor)
                        Text("24:00", fontSize = 11.sp, color = axisColor)
                    }
                    Text(
                        "화면을 사용하는 동안 5분 단위의 최신 값을 저장합니다. 화면을 끄고 쉬면 지수가 회복되고, 다음 사용 시 계산된 값까지 선으로 이어집니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "프리셋: ${CoreIndexPreset.fromId(samples.last().presetId).title}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

/**
 * 📊 2. 일별 사용 시간 & 언락 횟수 복합 바 차트 (Bar Chart)
 */
@Composable
private fun UsageAndUnlockBarChartCard(
    histories: List<DailyScoreHistoryEntity>,
    onClick: () -> Unit
) {
    val axisTextColor = MaterialTheme.colorScheme.outline.toArgb()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "사용 시간 & 언락 횟수",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 범례 (Legend)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF26A69A), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "총 화면시간", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(ScoreRed, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "방해 앱", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(ScoreYellow, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "언락(회)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (histories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "기록된 사용량 데이터가 없습니다.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                }
            } else {
                val maxMinutes = max(180L, histories.maxOfOrNull { it.totalScreenTimeMinutes } ?: 180L).toFloat()

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val width = size.width
                    val height = size.height - 30f
                    val n = histories.size
                    val stepX = width / n
                    val barWidth = (stepX * 0.55f).coerceAtMost(24f)

                    histories.forEachIndexed { index, item ->
                        val startX = (index * stepX) + (stepX - barWidth) / 2

                        // 1. 총 화면 시간 바 (청록색)
                        val totalH = height * (item.totalScreenTimeMinutes / maxMinutes).coerceIn(0f, 1f)
                        val totalY = height - totalH
                        drawRoundRect(
                            color = Color(0xFF26A69A).copy(alpha = 0.5f),
                            topLeft = Offset(startX, totalY),
                            size = Size(barWidth, totalH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )

                        // 2. 방해 앱 시간 바 (빨간색 - 내부 중첩)
                        val distH = height * (item.distractingTimeMinutes / maxMinutes).coerceIn(0f, 1f)
                        val distY = height - distH
                        drawRoundRect(
                            color = ScoreRed.copy(alpha = 0.85f),
                            topLeft = Offset(startX, distY),
                            size = Size(barWidth, distH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )

                        // 3. 언락 횟수 포인트 (상단 노란 점 인디케이터)
                        val unlockRatio = (item.unlockCount / 60f).coerceIn(0.1f, 1f)
                        val unlockY = height - (height * unlockRatio)
                        drawCircle(
                            color = ScoreYellow,
                            radius = 3.dp.toPx(),
                            center = Offset(startX + barWidth / 2, unlockY)
                        )

                        // X축 날짜 라벨
                        val shouldShowLabel = if (n <= 7) true else (index % 5 == 0 || index == n - 1)
                        if (shouldShowLabel) {
                            val dateStr = item.dateString
                            val dayLabel = if (dateStr.length >= 10) dateStr.substring(8) else "${index + 1}"
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = axisTextColor
                                    textSize = 22f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawText(com.digitscore.app.i18n.UiTranslator.translate("${dayLabel}일"), startX + (barWidth / 2), height + 24f, paint)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AnalyticsSummaryCards(
    usageHistories: List<DailyScoreHistoryEntity>,
    coreIndexHistories: List<DailyScoreHistoryEntity>,
    onDetailRequested: (StatisticsDetail) -> Unit
) {
    val avgScreenTime = if (usageHistories.isNotEmpty()) {
        (usageHistories.sumOf { it.totalScreenTimeMinutes } / usageHistories.size.toFloat()).roundToInt()
    } else 0

    val avgUnlockCount = if (usageHistories.isNotEmpty()) {
        (usageHistories.sumOf { it.unlockCount } / usageHistories.size.toFloat()).roundToInt()
    } else 0
    val highestUsageDay = usageHistories.maxByOrNull { it.totalScreenTimeMinutes }
    val totalScreenTime = usageHistories.sumOf { it.totalScreenTimeMinutes }
    val totalManagedTime = usageHistories.sumOf { it.distractingTimeMinutes }
    val managedShare = if (totalScreenTime > 0L) {
        (totalManagedTime * 100f / totalScreenTime).roundToInt()
    } else 0
    val scoreRange = coreIndexHistories.takeIf { it.isNotEmpty() }?.let { rows ->
        rows.minOf { it.finalScore } to rows.maxOf { it.finalScore }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "4주 사용 요약",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "가장 많이 쓴 날",
                value = highestUsageDay?.let {
                    "${it.dateString.substringAfter('-').replace('-', '/')} · ${formatMinutesToHoursAndMinutes(it.totalScreenTimeMinutes)}"
                } ?: "—",
                icon = Icons.Default.Star,
                iconColor = MaterialTheme.colorScheme.primary,
                subtitle = "하루 사용 최고",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "가장 많이 쓴 날",
                            highestUsageDay?.let {
                                "${it.dateString} · ${formatMinutesToHoursAndMinutes(it.totalScreenTimeMinutes)}"
                            } ?: "기록 준비 중",
                            "최근 4주 기록에서 화면을 켜고 전면 앱을 가장 오래 사용한 날입니다.",
                            scoreRange?.let { "같은 기간 코어 지수 범위는 ${it.first}~${it.second}점입니다." }
                        )
                    )
                }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "관리 앱 비중",
                value = "${managedShare}%",
                icon = Icons.Default.CheckCircle,
                iconColor = ScoreRed,
                subtitle = "전체 화면시간 중",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "관리 앱 비중",
                            "${managedShare}%",
                            "전체 화면시간 ${formatMinutesToHoursAndMinutes(totalScreenTime)} 중 관리 앱을 ${formatMinutesToHoursAndMinutes(totalManagedTime)} 사용했습니다.",
                            "몰입 관리 앱이 전면 또는 병렬 화면에 표시된 사용시간 비율입니다."
                        )
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "일평균 화면 시간",
                value = formatMinutesToHoursAndMinutes(avgScreenTime.toLong()),
                icon = Icons.Default.PhoneAndroid,
                iconColor = ScoreYellow,
                subtitle = "하루 평균 사용량",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "일평균 화면 시간",
                            formatMinutesToHoursAndMinutes(avgScreenTime.toLong()),
                            "기록된 날짜의 전체 전면 앱 사용시간 평균입니다.",
                            "화면 OFF 백그라운드 재생은 포함하지 않습니다."
                        )
                    )
                }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "일평균 언락",
                value = "${avgUnlockCount}회",
                icon = Icons.Default.LockOpen,
                iconColor = ScoreOrange,
                subtitle = "하루 폰 켠 횟수",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "일평균 언락",
                            "${avgUnlockCount}회",
                            "기록된 날짜의 잠금 해제 횟수 평균입니다.",
                            "짧은 앱 사용은 시간과 별도로 앱별 실행 횟수에 반영됩니다."
                        )
                    )
                }
            )
        }
    }
}

private fun formatMinutesToHoursAndMinutes(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}시간 ${mins}분"
        hours > 0 -> "${hours}시간"
        else -> "${mins}분"
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = "$title $value",
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                DetailChevron()
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
