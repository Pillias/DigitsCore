package com.digitscore.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.engine.ScoreGrade
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToStatistics: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToPresetSettings: () -> Unit
) {
    val context = LocalContext.current
    val scoreDetail by TrackerForegroundService.currentScoreDetail.collectAsState()
    val appsUsage by TrackerForegroundService.currentAppsUsage.collectAsState()
    val unlockCount by TrackerForegroundService.currentUnlockCount.collectAsState()
    val isRunning by TrackerForegroundService.isServiceRunning.collectAsState()

    LaunchedEffect(Unit) {
        if (!isRunning) {
            TrackerForegroundService.start(context)
        }
    }

    val currentScore = scoreDetail?.finalScore ?: 100
    val grade = scoreDetail?.grade ?: ScoreGrade.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DigitsCore",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = onNavigateToStatistics) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "통계 리포트",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onNavigateToAppSettings) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = "앱 가중치 설정",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onNavigateToPresetSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "모드 설정",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. 원형 점수 인디케이터
            item {
                ScoreGaugeCard(score = currentScore, grade = grade)
            }

            // 2. 주요 3단 통계 카드
            item {
                ScoreStatsRow(
                    screenTimeMinutes = scoreDetail?.totalScreenTimeMinutes ?: 0L,
                    unlockCount = unlockCount,
                    distractingMinutes = scoreDetail?.distractingTimeMinutes ?: 0L
                )
            }

            // 3. 실시간 앱 사용 헤더
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "오늘의 앱 사용 현황",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "상위 ${appsUsage.take(5).size}개 앱",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // 4. 앱 사용 목록
            if (appsUsage.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "아직 집계된 앱 사용 기록이 없습니다.",
                            color = Color.Gray,
                            modifier = Modifier.padding(24.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(appsUsage.take(5)) { app ->
                    AppUsageItemCard(appUsage = app)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ScoreGaugeCard(score: Int, grade: ScoreGrade) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "ScoreAnimation"
    )

    val scoreColor = when {
        score >= 80 -> ScoreGreen
        score >= 60 -> ScoreYellow
        score >= 40 -> ScoreOrange
        else -> ScoreRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 18.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // 배경 트랙
                    drawArc(
                        color = Color(0xFF2C333D),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // 활성 게이지
                    val sweep = (animatedScore / 100f) * 270f
                    drawArc(
                        color = scoreColor,
                        startAngle = 135f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${animatedScore.toInt()}",
                        fontSize = 76.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 76.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "GRADE ${grade.gradeText.take(1)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = grade.description,
                fontSize = 13.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ScoreStatsRow(
    screenTimeMinutes: Long,
    unlockCount: Int,
    distractingMinutes: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatMiniCard(
            modifier = Modifier.weight(1f),
            title = "화면 시간",
            value = "${screenTimeMinutes}분",
            icon = Icons.Default.PhoneAndroid,
            iconColor = MaterialTheme.colorScheme.primary
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            title = "언락 횟수",
            value = "${unlockCount}회",
            icon = Icons.Default.LockOpen,
            iconColor = ScoreYellow
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            title = "방해 앱",
            value = "${distractingMinutes}분",
            icon = Icons.Default.Warning,
            iconColor = ScoreRed
        )
    }
}

@Composable
fun StatMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = title, fontSize = 11.sp, color = Color.Gray)
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AppUsageItemCard(appUsage: AppUsage) {
    val tagColor = when (appUsage.categoryType) {
        AppCategoryType.PRODUCTIVE -> ScoreGreen
        AppCategoryType.NEUTRAL -> Color.Gray
        AppCategoryType.DISTRACTING -> ScoreRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appUsage.appName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = appUsage.categoryType.displayName,
                    fontSize = 11.sp,
                    color = tagColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${appUsage.usageTimeMinutes}분",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}
