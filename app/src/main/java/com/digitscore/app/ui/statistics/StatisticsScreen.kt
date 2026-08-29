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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.model.PresetMode
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
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

    // 통계 화면 진입 시 과거 기록이 부족하면 30일치 자동 소급 분석 실행
    LaunchedEffect(Unit) {
        if (UsageStatsHelper.hasUsageStatsPermission(context)) {
            withContext(Dispatchers.IO) {
                val existing = db.scoreDao().getAllScoreHistories().firstOrNull() ?: emptyList()
                if (existing.size < 7) {
                    val appWeights = db.appDao().getAllAppWeights().firstOrNull() ?: emptyList()
                    val weightMap = appWeights.associateBy { it.packageName }
                    val currentPreset = PresetMode.fromId(userSettings?.selectedPresetModeId ?: "balanced")
                    val pastHistories = UsageStatsHelper.syncPastDaysUsageStats(
                        context = context,
                        appWeightMap = weightMap,
                        scoreRule = currentPreset.scoreRule,
                        days = 30
                    )
                    for (h in pastHistories) {
                        db.scoreDao().insertOrUpdateScoreHistory(h)
                    }
                }
            }
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
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
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
                                color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else Color.Gray
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
                                color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    )
                }
            }

            // 2. 점수 추세 차트 카드
            item {
                ScoreTrendChartCard(
                    histories = histories,
                    targetDefense = targetDefense
                )
            }

            // 3. 주요 메트릭 지표 요약
            item {
                AnalyticsSummaryCards(
                    histories = histories,
                    targetDefense = targetDefense
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ScoreTrendChartCard(
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
                    text = "일별 점수 추세",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
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
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "기록된 이전 히스토리가 없습니다.\n오늘부터 점수가 기록됩니다.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val barSpacing = width / (histories.size.coerceAtLeast(1) * 1.5f)
                    val barWidth = (width / histories.size) * 0.55f

                    // 1. 방어선 가이드 라인 그리기 (점선/실선)
                    val defenseY = height - (height * (targetDefense / 100f))
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(0f, defenseY),
                        end = Offset(width, defenseY),
                        strokeWidth = 2f
                    )

                    // 2. 바 차트 그리기
                    val totalBars = histories.size
                    val stepX = width / totalBars

                    histories.forEachIndexed { index, item ->
                        val barHeight = height * (item.finalScore / 100f)
                        val startX = (index * stepX) + (stepX - barWidth) / 2
                        val startY = height - barHeight

                        val barColor = when {
                            item.finalScore >= 80 -> ScoreGreen
                            item.finalScore >= 60 -> ScoreYellow
                            item.finalScore >= 40 -> ScoreOrange
                            else -> ScoreRed
                        }

                        // 바
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(startX, startY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )

                        // 텍스트 라벨 (일자 마지막 2자리 e.g., '27')
                        val dayText = if (item.dateString.length >= 10) item.dateString.substring(8) else "${index + 1}"
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 26f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText(dayText, startX + (barWidth / 2), height + 30f, paint)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
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
            color = Color.White
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
                value = "${avgScreenTime}분",
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
                Text(text = title, fontSize = 12.sp, color = Color.Gray)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
