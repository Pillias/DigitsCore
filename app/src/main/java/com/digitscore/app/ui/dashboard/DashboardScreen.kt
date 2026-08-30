package com.digitscore.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.engine.ScoreGrade
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToStatistics: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToPresetSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DigitsDatabase.getInstance(context) }
    val viewModel: DashboardViewModel = viewModel()
    val scoreDetail by viewModel.scoreDetail.collectAsState()
    val appsUsage by viewModel.appsUsage.collectAsState()
    val unlockCount by viewModel.unlockCount.collectAsState()
    val isRunning by viewModel.isServiceRunning.collectAsState()

    // 모달 / 다이얼로그 상태 관리
    var showScoreDetailModal by remember { mutableStateOf(false) }
    var showScreenTimeModal by remember { mutableStateOf(false) }
    var showUnlockModal by remember { mutableStateOf(false) }
    var showDistractingModal by remember { mutableStateOf(false) }
    var selectedAppDetail by remember { mutableStateOf<AppUsage?>(null) }
    var showAllAppsModal by remember { mutableStateOf(false) }

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
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = onNavigateToStatistics) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "통계 리포트",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToAppSettings) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = "앱 가중치 설정",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToPresetSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "모드 설정",
                            tint = MaterialTheme.colorScheme.onBackground
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
            // 1. 원형 점수 인디케이터 (클릭 시 점수 산출 상세 내역 팝업)
            item {
                ScoreGaugeCard(
                    score = currentScore,
                    grade = grade,
                    yesterdayPenalty = scoreDetail?.yesterdayPenalty ?: 0f,
                    onClick = { showScoreDetailModal = true }
                )
            }

            // 2. 주요 3단 통계 카드 (각 카드 클릭 시 해당 세부 항목 팝업)
            item {
                ScoreStatsRow(
                    screenTimeMinutes = scoreDetail?.totalScreenTimeMinutes ?: 0L,
                    unlockCount = unlockCount,
                    distractingMinutes = scoreDetail?.distractingTimeMinutes ?: 0L,
                    onScreenTimeClick = { showScreenTimeModal = true },
                    onUnlockClick = { showUnlockModal = true },
                    onDistractingClick = { showDistractingModal = true }
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
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (appsUsage.size > 5) "전체 ${appsUsage.size}개 보기 ❯" else "상위 ${appsUsage.size}개 앱",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { showAllAppsModal = true }
                            .padding(4.dp)
                    )
                }
            }

            // 4. 앱 사용 목록 (각 앱 클릭 시 개별 앱 통계 & 카테고리 변경 팝업)
            if (appsUsage.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "아직 집계된 앱 사용 기록이 없습니다.",
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(24.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(appsUsage.take(5)) { app ->
                    AppUsageItemCard(
                        appUsage = app,
                        onClick = { selectedAppDetail = app }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ==========================================
    // 🔍 세부 항목 모달 / 다이얼로그들
    // ==========================================

    // 1. 점수 산출 상세 내역 다이얼로그
    if (showScoreDetailModal && scoreDetail != null) {
        ScoreDetailDialog(
            scoreDetail = scoreDetail!!,
            onDismiss = { showScoreDetailModal = false },
            onNavigateToStatistics = {
                showScoreDetailModal = false
                onNavigateToStatistics()
            }
        )
    }

    // 2. 화면 사용 시간 세부 통계 다이얼로그
    if (showScreenTimeModal) {
        ScreenTimeDetailDialog(
            totalScreenMinutes = scoreDetail?.totalScreenTimeMinutes ?: 0L,
            appsUsage = appsUsage,
            onDismiss = { showScreenTimeModal = false },
            onNavigateToStatistics = {
                showScreenTimeModal = false
                onNavigateToStatistics()
            },
            onSelectApp = { app ->
                showScreenTimeModal = false
                selectedAppDetail = app
            }
        )
    }

    // 3. 언락 횟수 세부 통계 다이얼로그
    if (showUnlockModal) {
        UnlockDetailDialog(
            unlockCount = unlockCount,
            unlockPenalty = scoreDetail?.unlockPenalty ?: 0f,
            onDismiss = { showUnlockModal = false },
            onNavigateToPresetSettings = {
                showUnlockModal = false
                onNavigateToPresetSettings()
            }
        )
    }

    // 4. 방해 앱 세부 분석 다이얼로그
    if (showDistractingModal) {
        DistractingDetailDialog(
            distractingMinutes = scoreDetail?.distractingTimeMinutes ?: 0L,
            distractingPenalty = scoreDetail?.distractingPenalty ?: 0f,
            lateNightPenalty = scoreDetail?.lateNightPenalty ?: 0f,
            appsUsage = appsUsage.filter { it.categoryType == AppCategoryType.DISTRACTING },
            onDismiss = { showDistractingModal = false },
            onNavigateToAppSettings = {
                showDistractingModal = false
                onNavigateToAppSettings()
            },
            onSelectApp = { app ->
                showDistractingModal = false
                selectedAppDetail = app
            }
        )
    }

    // 5. 오늘 사용된 전체 앱 목록 다이얼로그
    if (showAllAppsModal) {
        AllAppsUsageDialog(
            appsUsage = appsUsage,
            onDismiss = { showAllAppsModal = false },
            onSelectApp = { app ->
                showAllAppsModal = false
                selectedAppDetail = app
            }
        )
    }

    // 6. 개별 앱 상세 & 카테고리 즉시 변경 다이얼로그
    selectedAppDetail?.let { app ->
        AppDetailDialog(
            appUsage = app,
            onDismiss = { selectedAppDetail = null },
            onCategoryChanged = { newCategory ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.appDao().insertOrUpdateAppWeight(
                            AppWeightEntity(
                                packageName = app.packageName,
                                appName = app.appName,
                                categoryType = newCategory
                            )
                        )
                    }
                    // 서비스 재계산 트리거
                    TrackerForegroundService.start(context)
                }
                selectedAppDetail = null
            }
        )
    }
}

@Composable
private fun ScoreGaugeCard(
    score: Int,
    grade: ScoreGrade,
    yesterdayPenalty: Float,
    onClick: () -> Unit
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 700),
        label = "scoreGauge"
    )
    val scoreColor = when {
        score >= 80 -> ScoreGreen
        score >= 60 -> ScoreYellow
        score >= 40 -> ScoreOrange
        else -> ScoreRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "오늘의 디톡스 점수 ${score}점, ${grade.gradeText}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.2f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = scoreColor,
                        startAngle = 135f,
                        sweepAngle = 270f * (animatedScore / 100f),
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = score.toString(), fontSize = 54.sp, fontWeight = FontWeight.Black, color = scoreColor)
                    Text(text = grade.gradeText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (yesterdayPenalty > 0f) {
                Text(
                    text = "전날 디톡스 부채 -${String.format("%.1f", yesterdayPenalty)}점 반영",
                    fontSize = 12.sp,
                    color = ScoreOrange
                )
            }
            Text(text = "탭하여 점수 계산 내역 보기", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ScoreStatsRow(
    screenTimeMinutes: Long,
    unlockCount: Int,
    distractingMinutes: Long,
    onScreenTimeClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onDistractingClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatisticCard("화면", formatMinutesToHoursAndMinutes(screenTimeMinutes), Icons.Default.PhoneAndroid, onScreenTimeClick, Modifier.weight(1f))
        StatisticCard("언락", "${unlockCount}회", Icons.Default.LockOpen, onUnlockClick, Modifier.weight(1f))
        StatisticCard("방해", formatMinutesToHoursAndMinutes(distractingMinutes), Icons.Default.Warning, onDistractingClick, Modifier.weight(1f))
    }
}

@Composable
private fun StatisticCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AppUsageItemCard(appUsage: AppUsage, onClick: () -> Unit) {
    val categoryColor = when (appUsage.categoryType) {
        AppCategoryType.PRODUCTIVE -> ScoreGreen
        AppCategoryType.DISTRACTING -> ScoreRed
        AppCategoryType.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appUsage.appName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text = appUsage.categoryType.displayName, fontSize = 11.sp, color = categoryColor)
            }
            Text(text = formatMinutesToHoursAndMinutes(appUsage.usageTimeMinutes), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatMinutesToHoursAndMinutes(minutes: Long): String {
    val safeMinutes = minutes.coerceAtLeast(0L)
    val hours = safeMinutes / 60
    val remainingMinutes = safeMinutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}시간 ${remainingMinutes}분"
        hours > 0 -> "${hours}시간"
        else -> "${remainingMinutes}분"
    }
}

// =========================================================================
// 📱 상세 다이얼로그 컴포넌트들
// =========================================================================

/**
 * 1. 점수 산출 상세 내역 다이얼로그
 */
@Composable
fun ScoreDetailDialog(
    scoreDetail: ScoreDetail,
    onDismiss: () -> Unit,
    onNavigateToStatistics: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🏆 점수 산출 상세 내역",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "${scoreDetail.finalScore}점",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = if (scoreDetail.finalScore >= 80) ScoreGreen else if (scoreDetail.finalScore >= 60) ScoreYellow else ScoreRed
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "오늘 하루의 행동에 따라 계산된 디톡스 점수 내역입니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                item {
                    BreakdownRow(title = "기본 시작 점수", value = "100.0점", isBonus = true)
                }

                if (scoreDetail.yesterdayPenalty > 0f) {
                    item {
                        BreakdownRow(
                            title = "전날 과사용 페널티 (디톡스 부채)",
                            value = "-${String.format("%.1f", scoreDetail.yesterdayPenalty)}점",
                            isBonus = false
                        )
                    }
                }

                item {
                    BreakdownRow(
                        title = "방해 앱 사용 감점 (${scoreDetail.distractingTimeMinutes}분)",
                        value = "-${String.format("%.1f", scoreDetail.distractingPenalty)}점",
                        isBonus = false
                    )
                }

                if (scoreDetail.lateNightPenalty > 0f) {
                    item {
                        BreakdownRow(
                            title = "심야(00~05시) 추가 가속 감점",
                            value = "-${String.format("%.1f", scoreDetail.lateNightPenalty)}점",
                            isBonus = false
                        )
                    }
                }

                if (scoreDetail.unlockPenalty > 0f) {
                    item {
                        BreakdownRow(
                            title = "언락 목표 초과 감점",
                            value = "-${String.format("%.1f", scoreDetail.unlockPenalty)}점",
                            isBonus = false
                        )
                    }
                }

                item {
                    BreakdownRow(
                        title = "생산성 앱 보너스 (${scoreDetail.productiveTimeMinutes}분)",
                        value = "+${String.format("%.1f", scoreDetail.productiveBonus)}점",
                        isBonus = true
                    )
                }

                item {
                    BreakdownRow(
                        title = "화면 휴식(Idle) 회복 보너스",
                        value = "+${String.format("%.1f", scoreDetail.idleBonus)}점",
                        isBonus = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToStatistics) {
                Text("📊 전체 통계 리포트 보기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun BreakdownRow(title: String, value: String, isBonus: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isBonus) ScoreGreen else ScoreRed
        )
    }
}

/**
 * 2. 화면 사용 시간 세부 통계 다이얼로그
 */
@Composable
fun ScreenTimeDetailDialog(
    totalScreenMinutes: Long,
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onSelectApp: (AppUsage) -> Unit
) {
    val distractingMins = appsUsage.filter { it.categoryType == AppCategoryType.DISTRACTING }.sumOf { it.usageTimeMinutes }
    val productiveMins = appsUsage.filter { it.categoryType == AppCategoryType.PRODUCTIVE }.sumOf { it.usageTimeMinutes }
    val neutralMins = (totalScreenMinutes - distractingMins - productiveMins).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "📱 오늘의 화면 사용 시간", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "총 ${formatMinutesToHoursAndMinutes(totalScreenMinutes)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // 카테고리별 요약 바
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "카테고리별 시간 분배", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "🔴 방해 앱", fontSize = 12.sp)
                                Text(text = formatMinutesToHoursAndMinutes(distractingMins), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ScoreRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "🟢 생산성 앱", fontSize = 12.sp)
                                Text(text = formatMinutesToHoursAndMinutes(productiveMins), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ScoreGreen)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "⚪ 중립/기타", fontSize = 12.sp)
                                Text(text = formatMinutesToHoursAndMinutes(neutralMins), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "앱별 사용 시간 (탭하여 설정 변경)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(appsUsage) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectApp(app) }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.appName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(text = app.categoryType.displayName, fontSize = 11.sp, color = when(app.categoryType) {
                                AppCategoryType.PRODUCTIVE -> ScoreGreen
                                AppCategoryType.DISTRACTING -> ScoreRed
                                AppCategoryType.NEUTRAL -> MaterialTheme.colorScheme.outline
                            })
                        }
                        Text(
                            text = formatMinutesToHoursAndMinutes(app.usageTimeMinutes),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToStatistics) {
                Text("📈 주간/월간 추세 보기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

/**
 * 3. 언락 횟수 세부 통계 다이얼로그
 */
@Composable
fun UnlockDetailDialog(
    unlockCount: Int,
    unlockPenalty: Float,
    onDismiss: () -> Unit,
    onNavigateToPresetSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "🔓 오늘의 잠금 해제(언락) 통계", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "총 ${unlockCount}회 잠금 해제",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = ScoreYellow
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "💡 언락 관리 가이드", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "스마트폰을 무의식적으로 켜는 습관을 줄이면 집중력을 대폭 향상시킬 수 있습니다.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (unlockPenalty > 0f) {
                            Text(
                                text = "⚠️ 일일 기준치를 초과하여 -${String.format("%.1f", unlockPenalty)}점 감점 적용 중입니다.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScoreOrange
                            )
                        } else {
                            Text(
                                text = "✅ 현재 기준치 이내로 안전하게 유지하고 있습니다.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScoreGreen
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToPresetSettings) {
                Text("⚙️ 목표 언락 횟수 설정")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

/**
 * 4. 방해 앱 세부 분석 다이얼로그
 */
@Composable
fun DistractingDetailDialog(
    distractingMinutes: Long,
    distractingPenalty: Float,
    lateNightPenalty: Float,
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onSelectApp: (AppUsage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "🚨 방해 앱 집중 분석", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "총 ${formatMinutesToHoursAndMinutes(distractingMinutes)} (-${String.format("%.1f", distractingPenalty + lateNightPenalty)}점)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = ScoreRed
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "지정된 방해 앱 목록입니다. 앱을 탭하여 카테고리를 변경할 수 있습니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (appsUsage.isEmpty()) {
                    item {
                        Text(
                            text = "오늘 사용된 방해 앱이 없습니다! 🎉 완벽한 디톡스입니다.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScoreGreen,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(appsUsage) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app) }
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = app.appName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (app.lateNightUsageMinutes > 0) {
                                    Text(
                                        text = "🌙 심야 사용 ${app.lateNightUsageMinutes}분",
                                        fontSize = 11.sp,
                                        color = ScoreOrange
                                    )
                                }
                            }
                            Text(
                                text = formatMinutesToHoursAndMinutes(app.usageTimeMinutes),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ScoreRed
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToAppSettings) {
                Text("🏷️ 앱 분류 목록 관리")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

/**
 * 5. 전체 앱 사용 현황 목록 다이얼로그
 */
@Composable
fun AllAppsUsageDialog(
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onSelectApp: (AppUsage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "📱 오늘의 전체 앱 사용 목록", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appsUsage) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectApp(app) }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.appName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = app.categoryType.displayName,
                                fontSize = 11.sp,
                                color = when (app.categoryType) {
                                    AppCategoryType.PRODUCTIVE -> ScoreGreen
                                    AppCategoryType.DISTRACTING -> ScoreRed
                                    AppCategoryType.NEUTRAL -> MaterialTheme.colorScheme.outline
                                }
                            )
                        }
                        Text(
                            text = formatMinutesToHoursAndMinutes(app.usageTimeMinutes),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

/**
 * 6. 개별 앱 상세 정보 & 카테고리 즉시 변경 다이얼로그
 */
@Composable
fun AppDetailDialog(
    appUsage: AppUsage,
    onDismiss: () -> Unit,
    onCategoryChanged: (AppCategoryType) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(appUsage.categoryType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = appUsage.appName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = appUsage.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 사용 시간 요약
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "오늘 총 사용 시간", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = formatMinutesToHoursAndMinutes(appUsage.usageTimeMinutes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (appUsage.lateNightUsageMinutes > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "심야(00~05시)", fontSize = 11.sp, color = ScoreOrange)
                                Text(
                                    text = "${appUsage.lateNightUsageMinutes}분",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScoreOrange
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "디톡스 카테고리 분류 변경",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // 카테고리 라디오 버튼 그룹
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryRadioOption(
                        title = "🔴 방해 앱 (감점 대상)",
                        description = "SNS, 동영상, 게임 등 사용을 줄여야 하는 앱",
                        selected = selectedCategory == AppCategoryType.DISTRACTING,
                        color = ScoreRed,
                        onClick = { selectedCategory = AppCategoryType.DISTRACTING }
                    )
                    CategoryRadioOption(
                        title = "🟢 생산성 앱 (보너스 가산)",
                        description = "공부, 업무, 독서 등 권장되는 앱",
                        selected = selectedCategory == AppCategoryType.PRODUCTIVE,
                        color = ScoreGreen,
                        onClick = { selectedCategory = AppCategoryType.PRODUCTIVE }
                    )
                    CategoryRadioOption(
                        title = "⚪ 중립 앱 (점수 영향 없음)",
                        description = "통화, 지도, 금융 등 일상 필수 유틸리티 앱",
                        selected = selectedCategory == AppCategoryType.NEUTRAL,
                        color = MaterialTheme.colorScheme.outline,
                        onClick = { selectedCategory = AppCategoryType.NEUTRAL }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCategoryChanged(selectedCategory) }
            ) {
                Text("적용 및 저장", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun CategoryRadioOption(
    title: String,
    description: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = if (selected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) color else MaterialTheme.colorScheme.onSurface)
            Text(text = description, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}
