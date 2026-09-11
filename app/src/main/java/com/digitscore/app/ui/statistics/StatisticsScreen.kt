package com.digitscore.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
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
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: 최근 24시간, 1: 최근 30일
    var selectedDetail by remember { mutableStateOf<StatisticsDetail?>(null) }
    var selectedDay by remember { mutableStateOf<DailyScoreHistoryEntity?>(null) }
    val rollingWindowEndMillis = remember { System.currentTimeMillis() }
    val rollingWindowStartMillis = rollingWindowEndMillis - ROLLING_24_HOURS_MILLIS

    val allHistories by db.scoreDao().getAllScoreHistories().collectAsState(initial = emptyList())
    val rollingSamples by remember(rollingWindowStartMillis) {
        db.coreIndexSampleDao().observeSince(rollingWindowStartMillis)
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

    // 누적 UsageStats 기반 30일 소급은 정확한 전면 앱 시간을 보장하지 못하므로 중단합니다.
    // 과거 버전이 데이터 없는 날을 100점으로 만든 행만 정리하고, 이후 기록은 실시간
    // UsageEvents 측정 결과가 매일 쌓이도록 둡니다.
    LaunchedEffect(Unit) {
        val unlockInsights = withContext(Dispatchers.IO) {
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
            UsageStatsHelper.getRolling24HourUnlockInsights(context)
        }
        rollingUnlockInsights = unlockInsights
    }

    // 장기 탭은 DB 행 개수 LIMIT가 아니라 오늘을 포함한 실제 30일 달력 범위입니다.
    val histories = remember(allHistories) {
        historiesInCalendarRange(allHistories, 30)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. 분석 목적 선택 (직전 24시간 흐름 / 최근 30일 패턴)
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
                                "최근 24시간",
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
                                "최근 30일",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    )
                }
            }

            if (selectedTabIndex == 0) {
                item {
                    RollingCoreIndexCard(
                        samples = rollingSamples,
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
                                supportingText = "자정에 초기화하지 않고 조회 시점 직전 24시간만 표시합니다. 화면이 꺼진 구간은 표본을 만들지 않습니다."
                            )
                        }
                    )
                }
                item {
                    RollingUsageAndUnlockCard(
                        sessions = rollingSessions,
                        unlockInsights = rollingUnlockInsights,
                        windowStartMillis = rollingWindowStartMillis,
                        windowEndMillis = rollingWindowEndMillis,
                        onDetailRequested = { selectedDetail = it }
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
            } else {
                if (histories.size < 30) {
                    item {
                        Card(
                            modifier = Modifier.clickable {
                                selectedDetail = StatisticsDetail(
                                    title = "30일 기록 범위",
                                    value = "${histories.size}일 기록",
                                    description = "DigitsCore가 직접 측정해 저장한 날짜만 표시합니다.",
                                    supportingText = "상세 세션은 30일, 날짜별 집계는 365일 보관합니다. 기록이 없는 날짜를 0분이나 100점으로 채우지 않습니다."
                                )
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildString {
                                        append("최근 30일 중 DigitsCore가 실제 저장한 ${histories.size}일을 표시합니다.")
                                        histories.firstOrNull()?.let { first ->
                                            append(" 기록 범위: ${first.dateString}")
                                            histories.lastOrNull()?.let { last -> append(" ~ ${last.dateString}") }
                                            append(".")
                                        }
                                        append(" 장기 일별 저장 기능이 적용된 날부터 하루씩 누적됩니다.")
                                    },
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                DetailChevron()
                            }
                        }
                    }
                }

                item {
                    ScoreTrendLineChartCard(
                        histories = coreIndexHistories,
                        onDaySelected = { selectedDay = it },
                        onClick = {
                            val average = if (coreIndexHistories.isEmpty()) null else
                                (coreIndexHistories.sumOf { it.finalScore } / coreIndexHistories.size.toFloat()).roundToInt()
                            val high = coreIndexHistories.maxOfOrNull { it.finalScore }
                            val low = coreIndexHistories.minOfOrNull { it.finalScore }
                            val presetChanges = coreIndexPresetChanges(coreIndexHistories)
                            val reconstructedCount = coreIndexHistories.count { it.scoreModelVersion == 3 }
                            selectedDetail = StatisticsDetail(
                                title = "일별 코어 지수 추세",
                                value = average?.let { "평균 ${it}점" } ?: "기록 준비 중",
                                description = if (average == null) {
                                    "업데이트 후 측정된 코어 지수가 아직 없습니다."
                                } else {
                                    "기간 중 최고 ${high}점, 최저 ${low}점입니다."
                                },
                                supportingText = buildString {
                                    append("최근 7일의 변화는 30일 흐름 안에서 함께 비교합니다.")
                                    if (presetChanges.isNotEmpty()) {
                                        append("\n프리셋 변경: ")
                                        append(
                                            presetChanges.joinToString(" · ") { change ->
                                                "${change.dateString} ${CoreIndexPreset.fromId(change.presetId).title}"
                                            }
                                        )
                                    }
                                    if (reconstructedCount > 0) {
                                        append("\n${reconstructedCount}일은 기존 상세 세션으로 복원한 코어 지수입니다.")
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
                            selectedDetail = StatisticsDetail(
                                title = "사용 시간과 언락",
                                value = "${histories.size}일 기록",
                                description = "하루 평균 화면 ${histories.sumOf { it.totalScreenTimeMinutes } / count}분, 관리 앱 ${histories.sumOf { it.distractingTimeMinutes } / count}분, 언락 ${histories.sumOf { it.unlockCount } / count}회입니다.",
                                supportingText = "청록색은 전체 화면시간, 빨간색은 관리 앱 시간, 노란 점은 언락 횟수입니다."
                            )
                        }
                    )
                }

                item {
                    ThirtyDayPatternCard(
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
            onDismiss = { selectedDay = null }
        )
    }
}

private const val ROLLING_24_HOURS_MILLIS = 24 * 60 * 60_000L

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
    var longestMillis = 0L
    var longestApp: String? = null

    sessions.forEach { session ->
        val start = maxOf(session.startTimeMillis, windowStartMillis)
        val end = minOf(session.endTimeMillis, windowEndMillis)
        if (end <= start) return@forEach

        val clippedDuration = end - start
        packageTotals[session.packageName] =
            (packageTotals[session.packageName] ?: 0L) + clippedDuration
        appNames[session.packageName] = session.appName
        if (clippedDuration > longestMillis) {
            longestMillis = clippedDuration
            longestApp = session.appName
        }

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
            if (session.categoryLevel >= 4) hourlyManaged[bucket] += interval
            cursor = bucketEnd
        }
    }

    val topPackage = packageTotals.maxByOrNull { it.value }
    return RollingUsageSummary(
        totalMillis = hourlyTotal.sum(),
        managedMillis = hourlyManaged.sum(),
        hourlyTotalMillis = hourlyTotal.toList(),
        hourlyManagedMillis = hourlyManaged.toList(),
        longestSessionMillis = longestMillis,
        longestSessionAppName = longestApp,
        topAppMillis = topPackage?.value ?: 0L,
        topAppName = topPackage?.key?.let(appNames::get)
    )
}

@Composable
private fun RollingCoreIndexCard(
    samples: List<CoreIndexSampleEntity>,
    windowStartMillis: Long,
    windowEndMillis: Long,
    onClick: () -> Unit
) {
    val visible = remember(samples, windowStartMillis, windowEndMillis) {
        samples.filter { it.timestampMillis in windowStartMillis..windowEndMillis }
    }
    val axisColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("최근 24시간 코어 지수", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            if (visible.isNotEmpty()) {
                Text(
                    "현재 ${visible.last().score}점 · 최저 ${visible.minOf { it.score }} · 최고 ${visible.maxOf { it.score }}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("최근 24시간 코어 지수 표본을 준비하고 있습니다.", color = axisColor)
                }
            } else {
                Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                    val graphHeight = size.height - 20.dp.toPx()
                    listOf(40, 70, 100).forEach { score ->
                        val y = graphHeight - graphHeight * score / 100f
                        drawLine(
                            color = axisColor.copy(alpha = 0.22f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    val points = visible.map { sample ->
                        val ratio = ((sample.timestampMillis - windowStartMillis).toFloat() /
                            (windowEndMillis - windowStartMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        Offset(
                            size.width * ratio,
                            graphHeight - graphHeight * sample.score.coerceIn(0, 100) / 100f
                        )
                    }
                    if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    points.forEach { drawCircle(lineColor, 2.5.dp.toPx(), it) }
                }
                RollingTimeAxis(windowStartMillis, windowEndMillis, axisColor)
            }
            Text(
                "자정이 아니라 조회 시점 직전 24시간 기준입니다.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun RollingUsageAndUnlockCard(
    sessions: List<ForegroundUsageSessionEntity>,
    unlockInsights: UnlockInsights?,
    windowStartMillis: Long,
    windowEndMillis: Long,
    onDetailRequested: (StatisticsDetail) -> Unit
) {
    val summary = remember(sessions, windowStartMillis, windowEndMillis) {
        summarizeRollingUsage(sessions, windowStartMillis, windowEndMillis)
    }
    val axisColor = MaterialTheme.colorScheme.outline
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            onDetailRequested(
                StatisticsDetail(
                    "24시간 사용과 언락",
                    "화면 ${formatMinutesToHoursAndMinutes(summary.totalMillis / 60_000L)} · 언락 ${unlockInsights?.unlockCount ?: 0}회",
                    "현재 시각 직전 24시간의 전면 앱 사용과 잠금 해제 흐름입니다.",
                    "청록색은 전체 전면 사용, 빨간색은 4·5단계 앱, 노란 점은 언락 횟수입니다."
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
                Text("24시간 사용 흐름", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChartLegend(Color(0xFF26A69A), "화면")
                ChartLegend(ScoreRed, "관리 앱")
                ChartLegend(ScoreYellow, "언락")
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val graphHeight = size.height - 16.dp.toPx()
                val maxUsage = (summary.hourlyTotalMillis.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
                val maxUnlock = (unlockInsights?.hourlyUnlockCounts?.maxOrNull() ?: 0).coerceAtLeast(1)
                val step = size.width / 24f
                val width = (step * 0.62f).coerceAtLeast(2.dp.toPx())
                summary.hourlyTotalMillis.forEachIndexed { index, value ->
                    val x = index * step + (step - width) / 2f
                    val totalHeight = graphHeight * value / maxUsage.toFloat()
                    drawRoundRect(
                        Color(0xFF26A69A).copy(alpha = 0.55f),
                        Offset(x, graphHeight - totalHeight),
                        Size(width, totalHeight),
                        CornerRadius(3f, 3f)
                    )
                    val managedHeight = graphHeight * summary.hourlyManagedMillis[index] / maxUsage.toFloat()
                    drawRoundRect(
                        ScoreRed.copy(alpha = 0.86f),
                        Offset(x, graphHeight - managedHeight),
                        Size(width, managedHeight),
                        CornerRadius(3f, 3f)
                    )
                    val unlock = unlockInsights?.hourlyUnlockCounts?.getOrNull(index) ?: 0
                    if (unlock > 0) {
                        val y = graphHeight - graphHeight * unlock / maxUnlock.toFloat()
                        drawCircle(ScoreYellow, 2.7.dp.toPx(), Offset(x + width / 2f, y))
                    }
                }
                drawLine(axisColor.copy(alpha = 0.35f), Offset(0f, graphHeight), Offset(size.width, graphHeight))
            }
            RollingTimeAxis(windowStartMillis, windowEndMillis, axisColor)
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
                subtitle = "4·5단계 앱",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "관리 앱 사용",
                            formatMinutesToHoursAndMinutes(summary.managedMillis / 60_000L),
                            "최근 24시간 중 균형 등급 4·5단계 앱을 전면에서 사용한 시간입니다.",
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
private fun ThirtyDayPatternCard(
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

    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            val delta = if (recentAverage != null && previousAverage != null) recentAverage - previousAverage else null
            onClick(
                StatisticsDetail(
                    "30일 패턴",
                    busiest?.let { "${weekdayLabels[it.key - 1]}요일 평균 ${formatMinutesToHoursAndMinutes(it.value)}" }
                        ?: "기록 준비 중",
                    delta?.let {
                        "최근 7일 하루 평균은 이전 7일보다 ${kotlin.math.abs(it)}분 ${if (it > 0) "늘었고" else if (it < 0) "줄었고" else "같고"}, 요일별 평균도 함께 비교합니다."
                    } ?: "두 개의 7일 구간이 쌓이면 단기 변화를 비교합니다.",
                    "7일은 별도 탭이 아니라 30일 장기 흐름을 해석하는 이동 구간으로 사용합니다."
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
                Text("30일 패턴", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "최근 7일과 이전 7일 · 요일별 평균",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            val maxAverage = maxOf(
                recentAverage ?: 0L,
                previousAverage ?: 0L,
                weekdayAverages.values.maxOrNull() ?: 0L,
                1L
            )
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val topHeight = 38.dp.toPx()
                listOf(previousAverage ?: 0L, recentAverage ?: 0L).forEachIndexed { index, value ->
                    val y = index * 24.dp.toPx()
                    drawRoundRect(
                        color = if (index == 0) outlineColor.copy(alpha = 0.45f) else primaryColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width * value / maxAverage.toFloat(), 12.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
                val weekdayTop = topHeight + 18.dp.toPx()
                val step = size.width / 7f
                weekdayLabels.indices.forEach { index ->
                    val value = weekdayAverages[index + 1] ?: 0L
                    val height = 44.dp.toPx() * value / maxAverage.toFloat()
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.7f),
                        topLeft = Offset(index * step + step * 0.2f, weekdayTop + 44.dp.toPx() - height),
                        size = Size(step * 0.6f, height),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekdayLabels.forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
            }
            Text(
                "이전 7일 ${previousAverage?.let(::formatMinutesToHoursAndMinutes) ?: "—"} · 최근 7일 ${recentAverage?.let(::formatMinutesToHoursAndMinutes) ?: "—"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

/**
 * 📈 1. 일별 코어 지수 추세 꺾은선 그래프 (Line Chart)
 */
@Composable
private fun ScoreTrendLineChartCard(
    histories: List<DailyScoreHistoryEntity>,
    onDaySelected: (DailyScoreHistoryEntity) -> Unit,
    onClick: () -> Unit
) {
    val axisTextColor = MaterialTheme.colorScheme.outline.toArgb()
    val scoreTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val pointRingColor = MaterialTheme.colorScheme.surface.toArgb()
    val presetChangeRingColor = MaterialTheme.colorScheme.primary
    val reconstructedRingColor = MaterialTheme.colorScheme.outline
    val hasPresetChanges = coreIndexPresetChanges(histories).isNotEmpty()
    val hasReconstructedDays = histories.any { it.scoreModelVersion == 3 }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "일별 코어 지수 추세 (0~100)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (histories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "코어 지수 기록을 준비하고 있습니다.\n업데이트 후 하루씩 누적됩니다.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .pointerInput(histories) {
                            detectTapGestures { tap ->
                                val selectedIndex = if (histories.size == 1) {
                                    0
                                } else {
                                    ((tap.x / size.width) * (histories.size - 1))
                                        .roundToInt()
                                        .coerceIn(0, histories.lastIndex)
                                }
                                onDaySelected(histories[selectedIndex])
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height - 35f // X축 라벨용 여백

                    // 1. 꺾은선 좌표 계산
                    val n = histories.size
                    val stepX = if (n > 1) width / (n - 1) else width / 2
                    val points = histories.mapIndexed { index, item ->
                        val x = if (n > 1) index * stepX else width / 2
                        val y = height - (height * (item.finalScore.coerceIn(0, 100) / 100f))
                        Offset(x, y)
                    }

                    // 2. 하단 그라데이션 채우기 (Fill Path)
                    if (points.isNotEmpty()) {
                        val fillPath = Path().apply {
                            moveTo(points.first().x, height)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, height)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ScoreGreen.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )
                    }

                    // 3. 메인 꺾은선 그리기 (Stroke Path)
                    if (points.size > 1) {
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = ScoreGreen,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 4. 각 포인트 원형 점 및 라벨
                    points.forEachIndexed { index, pt ->
                        val score = histories[index].finalScore
                        val isPresetChange = index > 0 &&
                            histories[index - 1].coreIndexPresetId != histories[index].coreIndexPresetId
                        val isReconstructed = histories[index].scoreModelVersion == 3
                        val dotColor = when {
                            score >= 80 -> ScoreGreen
                            score >= 60 -> ScoreYellow
                            score >= 40 -> ScoreOrange
                            else -> ScoreRed
                        }

                        // 외부 글로우 링
                        drawCircle(
                            color = when {
                                isPresetChange -> presetChangeRingColor
                                isReconstructed -> reconstructedRingColor
                                else -> Color(pointRingColor)
                            },
                            radius = when {
                                isPresetChange -> 8.dp.toPx()
                                isReconstructed -> 7.dp.toPx()
                                else -> 6.dp.toPx()
                            },
                            center = pt
                        )
                        // 내부 점
                        drawCircle(
                            color = dotColor,
                            radius = 4.dp.toPx(),
                            center = pt
                        )

                        // 텍스트 라벨 (7일일 때는 매일, 30일일 때는 5일 간격 또는 시작/끝)
                        val shouldShowLabel = if (n <= 7) true else (index % 5 == 0 || index == n - 1)
                        if (shouldShowLabel) {
                            val dateStr = histories[index].dateString
                            val dayLabel = if (dateStr.length >= 10) dateStr.substring(8) else "${index + 1}"
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = axisTextColor
                                    textSize = 24f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawText(com.digitscore.app.i18n.UiTranslator.translate("${dayLabel}일"), pt.x, height + 28f, paint)

                                // 7일 뷰에서는 포인트 위에 점수도 작게 표시
                                if (n <= 7) {
                                    val scorePaint = android.graphics.Paint().apply {
                                        color = scoreTextColor
                                        textSize = 22f
                                        isFakeBoldText = true
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }
                                    val textY = (pt.y - 12f).coerceAtLeast(20f)
                                    drawText("$score", pt.x, textY, scorePaint)
                                }
                            }
                        }
                    }
                }
                if (hasPresetChanges) {
                    Text(
                        text = "큰 테두리는 프리셋이 바뀐 날입니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (hasReconstructedDays) {
                    Text(
                        text = "옅은 테두리는 기존 상세 세션으로 복원한 날짜입니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "그래프의 날짜를 누르면 하루 중 변화를 볼 수 있습니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun IntradayCoreIndexDialog(
    history: DailyScoreHistoryEntity,
    samples: List<CoreIndexSampleEntity>,
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
                if (samples.isEmpty()) {
                    Text(
                        "이 날짜의 하루 중 변화 표본은 없습니다. 5분 단위 기록은 이번 버전부터 최대 30일간 보관됩니다.",
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
                                points.drop(1).forEach { lineTo(it.x, it.y) }
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
                        "화면이 켜진 동안 같은 5분 구간의 최신 계산값을 저장합니다. 화면을 끈 동안에는 기록하지 않고 다음 사용 시 회복된 값으로 이어집니다.",
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
    val avgScore = if (coreIndexHistories.isNotEmpty()) {
        (coreIndexHistories.sumOf { it.finalScore } / coreIndexHistories.size.toFloat()).roundToInt()
    } else null

    val avgScreenTime = if (usageHistories.isNotEmpty()) {
        (usageHistories.sumOf { it.totalScreenTimeMinutes } / usageHistories.size.toFloat()).roundToInt()
    } else 0

    val avgUnlockCount = if (usageHistories.isNotEmpty()) {
        (usageHistories.sumOf { it.unlockCount } / usageHistories.size.toFloat()).roundToInt()
    } else 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "사용 균형 분석",
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
                title = "평균 코어 지수",
                value = avgScore?.let { "${it}점" } ?: "—",
                icon = Icons.Default.Star,
                iconColor = MaterialTheme.colorScheme.primary,
                subtitle = "기간 내 평균",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "평균 코어 지수",
                            avgScore?.let { "${it}점" } ?: "기록 준비 중",
                            "선택한 기간에 저장된 최근 24시간 코어 지수의 산술 평균입니다.",
                            "기존 일일 초기화 점수와 기록이 없는 날짜는 평균에 포함하지 않습니다."
                        )
                    )
                }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "코어 지수 기록",
                value = "${coreIndexHistories.size}일",
                icon = Icons.Default.CheckCircle,
                iconColor = ScoreGreen,
                subtitle = "새 방식 측정일",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "코어 지수 기록",
                            "${coreIndexHistories.size}일",
                            "최근 24시간 방식으로 저장된 코어 지수 기록 수입니다.",
                            "업데이트 전 기록은 사용시간과 언락 통계에는 유지되지만 점수 통계에는 섞지 않습니다."
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
                            "짧은 앱 사용은 시간과 별도로 언락 횟수에 반영됩니다."
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
