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
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
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

    val limit = if (selectedTabIndex == 0) 7 else 30
    val rawHistories by db.scoreDao().getRecentDaysHistories(limit).collectAsState(initial = emptyList())
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

    // 날짜 오름차순(과거->최신)으로 정렬하여 차트에 표시
    val histories = remember(rawHistories) {
        rawHistories.reversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("디톡스 통계 & 리포트", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
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
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "기기에서 확인 가능한 ${histories.size}일의 기록을 표시하고 있어요. " +
                                "오래된 사용 기록은 기기 정책에 따라 제공되지 않을 수 있습니다.",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        benchmark = selectedBenchmark
                    )
                }
            }

            // 2. 일별 점수 추세 (꺾은선 그래프)
            item {
                ScoreTrendLineChartCard(
                    histories = histories,
                    targetDefense = targetDefense
                )
            }

            // 3. 일별 사용 시간 & 언락 횟수 (바 차트)
            item {
                UsageAndUnlockBarChartCard(
                    histories = histories
                )
            }

            // 4. 주요 메트릭 지표 요약
            item {
                AnalyticsSummaryCards(
                    histories = histories,
                    targetDefense = targetDefense
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BenchmarkComparisonCard(
    histories: List<DailyScoreHistoryEntity>,
    benchmark: ScoringBenchmark
) {
    val count = histories.size.coerceAtLeast(1)
    val averageScore = histories.sumOf { it.finalScore } / count
    val averageScreen = histories.sumOf { it.totalScreenTimeMinutes } / count
    val averageDistracting = histories.sumOf { it.distractingTimeMinutes } / count
    val averageUnlocks = histories.sumOf { it.unlockCount } / count

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "📊 ${benchmark.title} 비교",
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
fun ScoreTrendLineChartCard(
    histories: List<DailyScoreHistoryEntity>,
    targetDefense: Int
) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📈 일별 점수 추세 (0~100점)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "방어선: ${targetDefense}점",
                    fontSize = 12.sp,
                    color = ScoreOrange,
                    fontWeight = FontWeight.SemiBold
                )
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
                            color = Color(0xFF1E2630),
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
                                    color = android.graphics.Color.LTGRAY
                                    textSize = 24f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawText("${dayLabel}일", pt.x, height + 28f, paint)

                                // 7일 뷰에서는 포인트 위에 점수도 작게 표시
                                if (n <= 7) {
                                    val scorePaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
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
fun UsageAndUnlockBarChartCard(
    histories: List<DailyScoreHistoryEntity>
) {
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
            Text(
                text = "📊 사용 시간 & 언락 횟수",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

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
                                    color = android.graphics.Color.GRAY
                                    textSize = 22f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawText("${dayLabel}일", startX + (barWidth / 2), height + 24f, paint)
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
fun AnalyticsSummaryCards(
    histories: List<DailyScoreHistoryEntity>,
    targetDefense: Int
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
            text = "디톡스 성과 분석",
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
                subtitle = "기간 내 평균"
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "목표 달성률",
                value = "${successRate}%",
                icon = Icons.Default.CheckCircle,
                iconColor = ScoreGreen,
                subtitle = "${successDays}/${totalCount}일 방어 성공"
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
                subtitle = "하루 평균 사용량"
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "일평균 언락",
                value = "${avgUnlockCount}회",
                icon = Icons.Default.LockOpen,
                iconColor = ScoreOrange,
                subtitle = "하루 폰 켠 횟수"
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
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    subtitle: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Icon(
                    imageVector = icon,
                    contentDescription = "$title $value",
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
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
