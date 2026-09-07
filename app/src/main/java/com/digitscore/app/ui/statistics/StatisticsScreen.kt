package com.digitscore.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.engine.ScoringBenchmark
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.InformationDetailDialog
import kotlinx.coroutines.Dispatchers
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
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: 7일, 1: 30일
    var selectedDetail by remember { mutableStateOf<StatisticsDetail?>(null) }

    val allHistories by db.scoreDao().getAllScoreHistories().collectAsState(initial = emptyList())
    val userSettings by db.settingsDao().getSettingsFlow().collectAsState(initial = null)

    val targetDefense = userSettings?.minimumScoreDefenseLine ?: 60

    // 누적 UsageStats 기반 30일 소급은 정확한 전면 앱 시간을 보장하지 못하므로 중단합니다.
    // 과거 버전이 데이터 없는 날을 100점으로 만든 행만 정리하고, 이후 기록은 실시간
    // UsageEvents 측정 결과가 매일 쌓이도록 둡니다.
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
        }
    }

    // DB 행 개수 LIMIT가 아니라 오늘을 포함한 실제 달력 범위로 구분합니다.
    // 이렇게 해야 기록이 빈 날이 있어도 7일/30일 탭의 의미가 바뀌지 않습니다.
    val requestedDays = if (selectedTabIndex == 0) 7 else 30
    val histories = remember(allHistories, requestedDays) {
        historiesInCalendarRange(allHistories, requestedDays)
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
            // 1. 기간 선택 탭 (7일 / 30일)
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
                                "최근 7일",
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

            if (selectedTabIndex == 1 && histories.size < 30) {
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

            val selectedBenchmark = ScoringBenchmark.forPreset(
                userSettings?.selectedPresetModeId ?: "balanced"
            )
            if (selectedBenchmark != null) {
                item {
                    BenchmarkComparisonCard(
                        histories = histories,
                        benchmark = selectedBenchmark,
                        onClick = {
                            selectedDetail = StatisticsDetail(
                                title = "${selectedBenchmark.title} 비교",
                                value = "${histories.size}일 기록",
                                description = "선택한 생활 유형의 참고 기준과 실제 기간 평균을 비교합니다.",
                                supportingText = "${selectedBenchmark.sourceLabel}\n조사 평균은 건강 진단 기준이 아닙니다."
                            )
                        }
                    )
                }
            }

            // 2. 일별 점수 추세 (꺾은선 그래프)
            item {
                ScoreTrendLineChartCard(
                    histories = histories,
                    targetDefense = targetDefense,
                    onClick = {
                        val average = if (histories.isEmpty()) 0 else
                            (histories.sumOf { it.finalScore } / histories.size.toFloat()).roundToInt()
                        val high = histories.maxOfOrNull { it.finalScore } ?: 0
                        val low = histories.minOfOrNull { it.finalScore } ?: 0
                        selectedDetail = StatisticsDetail(
                            title = "일별 점수 추세",
                            value = "평균 ${average}점",
                            description = "기간 중 최고 ${high}점, 최저 ${low}점입니다.",
                            supportingText = "점선은 설정한 기준선 ${targetDefense}점을 나타냅니다."
                        )
                    }
                )
            }

            // 3. 일별 사용 시간 & 언락 횟수 (바 차트)
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

            // 4. 주요 메트릭 지표 요약
            item {
                AnalyticsSummaryCards(
                    histories = histories,
                    targetDefense = targetDefense,
                    onDetailRequested = { selectedDetail = it }
                )
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

@Composable
private fun BenchmarkComparisonCard(
    histories: List<DailyScoreHistoryEntity>,
    benchmark: ScoringBenchmark,
    onClick: () -> Unit
) {
    val count = histories.size.coerceAtLeast(1)
    val averageScore = histories.sumOf { it.finalScore } / count
    val averageScreen = histories.sumOf { it.totalScreenTimeMinutes } / count
    val averageDistracting = histories.sumOf { it.distractingTimeMinutes } / count
    val averageUnlocks = histories.sumOf { it.unlockCount } / count

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "${benchmark.title} 비교",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (histories.isEmpty()) {
                Text(
                    text = "기록이 쌓이면 선택한 기준과 실제 평균을 비교합니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Text(
                    text = "실제 평균 ${averageScore}점 · 화면 ${averageScreen}분 · " +
                        "방해 ${averageDistracting}분 · 언락 ${averageUnlocks}회",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "보정 기준 ${benchmark.targetScore}점 · 화면 ${benchmark.totalScreenMinutes}분 · " +
                        "방해 ${benchmark.distractingMinutes}분 · 언락 ${benchmark.unlockCount}회",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = benchmark.sourceLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "조사 평균은 건강 권고가 아니며, 앱 분류와 생활 맥락에 따라 직접 조정해야 합니다.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 📈 1. 일별 점수 추세 꺾은선 그래프 (Line Chart)
 */
@Composable
private fun ScoreTrendLineChartCard(
    histories: List<DailyScoreHistoryEntity>,
    targetDefense: Int,
    onClick: () -> Unit
) {
    val axisTextColor = MaterialTheme.colorScheme.outline.toArgb()
    val scoreTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val pointRingColor = MaterialTheme.colorScheme.surface.toArgb()

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
                    text = "일별 점수 추세 (0~100점)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "방어선: ${targetDefense}점",
                    fontSize = 12.sp,
                    color = ScoreOrange,
                    fontWeight = FontWeight.SemiBold
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
                        text = "기록된 이전 히스토리가 없습니다.\n오늘부터 점수가 기록됩니다.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                ) {
                    val width = size.width
                    val height = size.height - 35f // X축 라벨용 여백

                    // 1. 방어선 가이드 라인 (점선)
                    val defenseY = height - (height * (targetDefense / 100f))
                    drawLine(
                        color = Color(0xFFE65100).copy(alpha = 0.6f),
                        start = Offset(0f, defenseY),
                        end = Offset(width, defenseY),
                        strokeWidth = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )

                    // 2. 꺾은선 좌표 계산
                    val n = histories.size
                    val stepX = if (n > 1) width / (n - 1) else width / 2
                    val points = histories.mapIndexed { index, item ->
                        val x = if (n > 1) index * stepX else width / 2
                        val y = height - (height * (item.finalScore.coerceIn(0, 100) / 100f))
                        Offset(x, y)
                    }

                    // 3. 하단 그라데이션 채우기 (Fill Path)
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

                    // 4. 메인 꺾은선 그리기 (Stroke Path)
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

                    // 5. 각 포인트 원형 점 및 라벨
                    points.forEachIndexed { index, pt ->
                        val score = histories[index].finalScore
                        val dotColor = when {
                            score >= 80 -> ScoreGreen
                            score >= 60 -> ScoreYellow
                            score >= 40 -> ScoreOrange
                            else -> ScoreRed
                        }

                        // 외부 글로우 링
                        drawCircle(
                            color = Color(pointRingColor),
                            radius = 6.dp.toPx(),
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
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
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
    histories: List<DailyScoreHistoryEntity>,
    targetDefense: Int,
    onDetailRequested: (StatisticsDetail) -> Unit
) {
    val totalCount = histories.size.coerceAtLeast(1)
    val avgScore = if (histories.isNotEmpty()) {
        (histories.sumOf { it.finalScore } / histories.size.toFloat()).roundToInt()
    } else 0

    val successDays = histories.count { it.finalScore >= targetDefense }
    val successRate = if (histories.isNotEmpty()) {
        ((successDays.toFloat() / histories.size) * 100).roundToInt()
    } else 100

    val avgScreenTime = if (histories.isNotEmpty()) {
        (histories.sumOf { it.totalScreenTimeMinutes } / histories.size.toFloat()).roundToInt()
    } else 0

    val avgUnlockCount = if (histories.isNotEmpty()) {
        (histories.sumOf { it.unlockCount } / histories.size.toFloat()).roundToInt()
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
                title = "평균 점수",
                value = "${avgScore}점",
                icon = Icons.Default.Star,
                iconColor = MaterialTheme.colorScheme.primary,
                subtitle = "기간 내 평균",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "평균 점수",
                            "${avgScore}점",
                            "선택한 기간에 저장된 일별 점수의 산술 평균입니다.",
                            "기록이 없는 날짜는 평균에 포함하지 않습니다."
                        )
                    )
                }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "목표 달성률",
                value = "${successRate}%",
                icon = Icons.Default.CheckCircle,
                iconColor = ScoreGreen,
                subtitle = "${successDays}/${totalCount}일 기준 달성",
                onClick = {
                    onDetailRequested(
                        StatisticsDetail(
                            "목표 달성률",
                            "${successRate}%",
                            "기록된 ${totalCount}일 중 ${successDays}일이 기준선 ${targetDefense}점 이상이었습니다.",
                            "기준선은 설정에서 변경할 수 있습니다."
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
