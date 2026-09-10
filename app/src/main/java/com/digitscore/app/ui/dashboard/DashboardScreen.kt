package com.digitscore.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.digitscore.app.i18n.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.R
import com.digitscore.app.i18n.localizedGrade
import com.digitscore.app.i18n.UiTranslator
import com.digitscore.app.data.AppUsageInsights
import com.digitscore.app.data.UnlockInsights
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.engine.ScoreGrade
import com.digitscore.app.engine.RollingScoreDetail
import com.digitscore.app.engine.ScoreFlow
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.SectionHeading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

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
    val rollingScoreDetail by viewModel.rollingScoreDetail.collectAsState()
    val appsUsage by viewModel.appsUsage.collectAsState()
    val unlockCount by viewModel.unlockCount.collectAsState()

    // 모달 / 다이얼로그 상태 관리
    var showScoreDetailModal by remember { mutableStateOf(false) }
    var showScreenTimeModal by remember { mutableStateOf(false) }
    var showUnlockModal by remember { mutableStateOf(false) }
    var showDistractingModal by remember { mutableStateOf(false) }
    var selectedAppDetail by remember { mutableStateOf<AppUsage?>(null) }
    var showAllAppsModal by remember { mutableStateOf(false) }

    val currentScore = rollingScoreDetail?.finalScore ?: 80
    val grade = ScoreGrade.fromScore(currentScore)

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
                            contentDescription = UiTranslator.translate("통계 리포트"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToAppSettings) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = UiTranslator.translate("앱 가중치 설정"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToPresetSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = UiTranslator.translate("모드 설정"),
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
                    rollingScore = rollingScoreDetail,
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
                SectionHeading(
                    title = "오늘의 앱 사용 현황",
                    subtitle = "앱을 누르면 시간대·세션·최근 추세를 볼 수 있습니다.",
                    actionLabel = if (appsUsage.size > 5) "전체 ${appsUsage.size}개" else "전체 보기",
                    onAction = { showAllAppsModal = true }
                )
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
    if (showScoreDetailModal) {
        RollingScoreDetailDialog(
            rollingScore = rollingScoreDetail,
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
            appsUsage = appsUsage.filter { it.categoryType.isPenalty },
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
                                categoryType = newCategory,
                                isUserModified = true
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
    rollingScore: RollingScoreDetail?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val gradeText = context.localizedGrade(grade)
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
            .semantics { contentDescription = context.getString(R.string.score_accessibility, score, gradeText) },
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
            Text(
                text = "${stringResource(R.string.digitscore_score)} · ${stringResource(R.string.rolling_24_hours)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
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
                    Text(text = gradeText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = rollingStatusText(rollingScore),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = stringResource(R.string.tap_score_details), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
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
        StatisticCard("관리", formatMinutesToHoursAndMinutes(distractingMinutes), Icons.Default.Warning, onDistractingClick, Modifier.weight(1f))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                DetailChevron()
            }
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AppUsageItemCard(appUsage: AppUsage, onClick: () -> Unit) {
    val categoryColor = appRatingColor(appUsage.categoryType)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatMinutesToHoursAndMinutes(appUsage.usageTimeMinutes), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                DetailChevron()
            }
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

@Composable
private fun RollingScoreDetailDialog(
    rollingScore: RollingScoreDetail?,
    onDismiss: () -> Unit,
    onNavigateToStatistics: () -> Unit
) {
    val detail = rollingScore
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.score_detail_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.score_points, detail?.finalScore ?: 80), fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(rollingStatusText(detail))
                BreakdownRow(stringResource(R.string.recent_usage), stringResource(R.string.format_minutes, detail?.recentUsageMinutes ?: 0))
                BreakdownRow(stringResource(R.string.rolling_load), String.format("%.1f", detail?.rollingLoad ?: 0.0))
                BreakdownRow(stringResource(R.string.acute_load), String.format("%.1f", detail?.acuteLoad ?: 0.0))
                Text(
                    stringResource(R.string.score_method_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToStatistics) { Text(stringResource(R.string.view_statistics)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun rollingStatusText(detail: RollingScoreDetail?): String {
    if (detail == null) return stringResource(R.string.score_calibrating, 0)
    return when (detail.flow) {
        ScoreFlow.CALIBRATING -> stringResource(
            R.string.score_calibrating,
            (detail.calibrationProgress * 60).roundToInt()
        )
        ScoreFlow.USING -> stringResource(
            R.string.score_using,
            detail.continuousUsageMinutes,
            String.format(Locale.US, "%.1f", detail.rollingLoad + detail.acuteLoad)
        )
        ScoreFlow.RECOVERING -> stringResource(R.string.score_recovering, detail.restMinutes)
        ScoreFlow.STEADY -> stringResource(
            R.string.score_steady,
            String.format(Locale.US, "%.1f", detail.rollingLoad + detail.acuteLoad)
        )
    }
}

@Composable
private fun BreakdownRow(title: String, value: String) {
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
            color = MaterialTheme.colorScheme.primary
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
    val distractingMins = appsUsage.filter { it.categoryType.isPenalty }.sumOf { it.usageTimeMinutes }
    val productiveMins = appsUsage.filter { it.categoryType.isBonus }.sumOf { it.usageTimeMinutes }
    val neutralMins = (totalScreenMinutes - distractingMins - productiveMins).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "오늘의 화면 사용 시간", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                            CompactBarChart(
                                values = listOf(distractingMins.toFloat(), productiveMins.toFloat(), neutralMins.toFloat()),
                                barColor = MaterialTheme.colorScheme.primary,
                                contentDescription = UiTranslator.translate("방해 생산성 중립 앱 사용시간 비교 그래프")
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Text("관리", fontSize = 10.sp, color = ScoreRed)
                                Text("성장", fontSize = 10.sp, color = ScoreGreen)
                                Text("균형", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "관리 앱", fontSize = 12.sp)
                                Text(text = formatMinutesToHoursAndMinutes(distractingMins), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ScoreRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "성장 앱", fontSize = 12.sp)
                                Text(text = formatMinutesToHoursAndMinutes(productiveMins), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ScoreGreen)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "균형/기타", fontSize = 12.sp)
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
                            Text(
                                text = app.categoryType.displayName,
                                fontSize = 11.sp,
                                color = appRatingColor(app.categoryType)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatMinutesToHoursAndMinutes(app.usageTimeMinutes),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            DetailChevron()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToStatistics) {
                Text("주간/월간 추세 보기")
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
    onDismiss: () -> Unit,
    onNavigateToPresetSettings: () -> Unit
) {
    val context = LocalContext.current
    var insights by remember { mutableStateOf<UnlockInsights?>(null) }
    LaunchedEffect(unlockCount) {
        insights = withContext(Dispatchers.IO) {
            UsageStatsHelper.getTodayUnlockInsights(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "오늘의 잠금 해제(언락) 통계", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "총 ${unlockCount}회 잠금 해제",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = ScoreYellow
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (insights == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    val value = requireNotNull(insights)
                    val busiest = value.hourlyUnlockCounts.withIndex()
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .take(3)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("시간대별 언락", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            if (busiest.isEmpty()) {
                                Text("아직 시간대 분석에 필요한 언락 기록이 없습니다.", fontSize = 13.sp)
                            } else {
                                CompactBarChart(
                                    values = value.hourlyUnlockCounts.map { it.toFloat() },
                                    barColor = ScoreYellow,
                                    contentDescription = UiTranslator.translate("24시간 언락 횟수 그래프")
                                )
                                HourlyAxisLabels()
                                val peak = busiest.first()
                                Text(
                                    "가장 잦은 시간은 ${hourLabel(peak.index)} · ${peak.value}회입니다.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            value.averageIntervalMinutes?.let { interval ->
                                Text("평균 약 ${interval.coerceAtLeast(1)}분마다 한 번 열었습니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("알림과 언락 비교", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            if (!value.notificationEventsSupported) {
                                Text("이 Android 버전은 알림 이벤트 비교를 제공하지 않습니다.", fontSize = 13.sp)
                            } else {
                                Text("OS 감지 알림 ${value.notificationCount}건 · 언락 ${unlockCount}회", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                CompactBarChart(
                                    values = listOf(value.notificationCount.toFloat(), unlockCount.toFloat()),
                                    barColor = MaterialTheme.colorScheme.primary,
                                    contentDescription = UiTranslator.translate(
                                        "알림 ${value.notificationCount}건과 언락 ${unlockCount}회 비교 그래프"
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    Text("알림", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("언락", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                val comparison = when {
                                    value.notificationCount > 0 -> {
                                        val ratio = unlockCount.toFloat() / value.notificationCount
                                        "알림 1건당 약 ${String.format(Locale.US, "%.1f", ratio)}회 언락했습니다."
                                    }
                                    unlockCount > 0 -> "감지된 알림 없이도 ${unlockCount}회 언락했습니다."
                                    else -> "아직 비교할 기록이 없습니다."
                                }
                                Text(comparison, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text("OS 이벤트의 단순 비교이며 알림이 언락의 직접 원인이라는 뜻은 아닙니다.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "언락 관리 가이드", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "스마트폰을 무의식적으로 켜는 습관을 줄이면 집중력을 대폭 향상시킬 수 있습니다.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "최근 24시간 언락 횟수는 코어 지수의 사용 부하에 완만하게 반영됩니다.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToPresetSettings) {
                Text("목표 언락 횟수 설정")
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
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onSelectApp: (AppUsage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "관리 앱 상세 분석", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "총 ${formatMinutesToHoursAndMinutes(distractingMinutes)}",
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
                        text = "관리 대상 앱 목록입니다. 앱을 눌러 등급을 변경할 수 있습니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (appsUsage.isNotEmpty()) {
                    item {
                        CompactBarChart(
                            values = appsUsage.take(8).map { it.usageTimeMinutes.toFloat() },
                            barColor = ScoreRed,
                            contentDescription = UiTranslator.translate("관리 대상 앱별 사용시간 그래프")
                        )
                        Text(
                            "사용시간 상위 ${minOf(8, appsUsage.size)}개 앱",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (appsUsage.isEmpty()) {
                    item {
                        Text(
                            text = "오늘 사용된 관리 대상 앱이 없습니다. 안정적인 사용 흐름입니다.",
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
                                        text = "심야 사용 ${app.lateNightUsageMinutes}분",
                                        fontSize = 11.sp,
                                        color = ScoreOrange
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatMinutesToHoursAndMinutes(app.usageTimeMinutes),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ScoreRed
                                )
                                DetailChevron()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToAppSettings) {
                Text("앱 등급 목록 관리")
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
            Text(text = "오늘의 전체 앱 사용 목록", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                                color = appRatingColor(app.categoryType)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatMinutesToHoursAndMinutes(app.usageTimeMinutes),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            DetailChevron()
                        }
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
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(appUsage.categoryType) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var insights by remember { mutableStateOf<AppUsageInsights?>(null) }

    LaunchedEffect(appUsage.packageName, appUsage.usageTimeMillis) {
        insights = withContext(Dispatchers.IO) {
            UsageStatsHelper.getAppUsageInsights(context, appUsage.packageName)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = appUsage.appName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = appUsage.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
                Box {
                    TextButton(onClick = { categoryMenuExpanded = true }) {
                        Text(
                            "${selectedCategory.level} · ${selectedCategory.displayName}",
                            fontSize = 11.sp,
                            color = appRatingColor(selectedCategory)
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = UiTranslator.translate("균형 등급 변경"),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        AppCategoryType.orderedEntries.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "${category.level} · ${category.displayName}",
                                            fontWeight = FontWeight.Bold,
                                            color = appRatingColor(category)
                                        )
                                        Text(category.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                onClick = {
                                    selectedCategory = category
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                }

                if (insights == null) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    val value = requireNotNull(insights)
                    item { AppTimeOfDayInsight(value) }
                    item { AppSessionInsight(value) }
                    item { AppTrendInsight(value) }
                }

                item {
                    Text(
                        "등급은 우측 상단 드롭다운에서 변경할 수 있습니다.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
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
private fun AppTimeOfDayInsight(insights: AppUsageInsights) {
    val busiest = insights.hourlyUsageMillis.withIndex()
        .filter { it.value >= 10_000L }
        .sortedByDescending { it.value }
        .take(3)
    InsightCard("언제 많이 사용했나요?") {
        if (busiest.isEmpty()) {
            Text("아직 분석할 시간대 기록이 없습니다.", fontSize = 12.sp)
        } else {
            CompactBarChart(
                values = insights.hourlyUsageMillis.map { it / 60_000f },
                barColor = MaterialTheme.colorScheme.primary,
                contentDescription = UiTranslator.translate("24시간 앱 사용량 그래프")
            )
            HourlyAxisLabels()
            val peak = busiest.first()
            Text(
                "가장 많이 사용한 시간은 ${hourLabel(peak.index)} · ${formatInsightDuration(peak.value)}입니다.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AppSessionInsight(insights: AppUsageInsights) {
    val sessions = insights.sessionDurationsMillis
    val average = sessions.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
    val longest = sessions.maxOrNull() ?: 0L
    val assessment = when {
        longest >= 30 * 60_000L -> "한 번에 30분 이상 이어진 사용이 있습니다."
        longest >= 15 * 60_000L -> "한 번에 다소 길게 사용한 구간이 있습니다."
        sessions.isNotEmpty() -> "대체로 짧게 나누어 사용했습니다."
        else -> "아직 세션 기록이 없습니다."
    }
    InsightCard("한 번에 너무 길게 사용했나요?") {
        if (sessions.isNotEmpty()) {
            CompactBarChart(
                values = sessions.takeLast(10).map { it / 60_000f },
                barColor = MaterialTheme.colorScheme.primary,
                warningThreshold = 30f,
                contentDescription = UiTranslator.translate("최근 앱 사용 세션 길이 그래프")
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("최근 세션", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                Text("30분 이상은 주황색", fontSize = 10.sp, color = ScoreOrange)
            }
        }
        Text(
            "${sessions.size}회 · 평균 ${formatInsightDuration(average)} · 최장 ${formatInsightDuration(longest)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            assessment,
            fontSize = 12.sp,
            color = if (longest >= 30 * 60_000L) ScoreOrange else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AppTrendInsight(insights: AppUsageInsights) {
    val periods = listOf(
        7 to "최근 7일",
        28 to "최근 4주",
        84 to "최근 12주",
        182 to "최근 6개월",
        365 to "최근 1년"
    )
    var selectedDays by remember { mutableStateOf(84) }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    val periodStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -(selectedDays - 1))
    }.timeInMillis
    val periodUsage = insights.dailyUsage.filter { it.dayStartMillis >= periodStart }
    val completed = periodUsage.filterNot { it.isToday }
    val comparisonWindow = minOf(28, completed.size / 2)
    val recent = if (comparisonWindow > 0) completed.takeLast(comparisonWindow).map { it.usageMillis } else emptyList()
    val previous = if (comparisonWindow > 0) {
        completed.dropLast(comparisonWindow).takeLast(comparisonWindow).map { it.usageMillis }
    } else {
        emptyList()
    }
    val recentAverage = recent.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val previousAverage = previous.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val trendText = when {
        previous.isEmpty() -> "기록이 더 쌓이면 같은 길이의 이전 기간과 비교할 수 있습니다."
        previousAverage == 0.0 && recentAverage > 0.0 -> "최근 ${comparisonWindow}일에 새 사용 기록이 생겼습니다."
        previousAverage == 0.0 -> "최근 사용량 변화가 없습니다."
        else -> {
            val percent = ((recentAverage - previousAverage) / previousAverage * 100).roundToInt()
            when {
                percent >= 10 -> "최근 ${comparisonWindow}일 평균이 이전 기간보다 ${percent}% 늘었습니다."
                percent <= -10 -> "최근 ${comparisonWindow}일 평균이 이전 기간보다 ${-percent}% 줄었습니다."
                else -> "최근 ${comparisonWindow}일 사용량은 이전 기간과 비슷합니다."
            }
        }
    }

    val weekdayTotals = LongArray(7)
    val weekdayCounts = IntArray(7)
    periodUsage.forEach { day ->
        val calendar = Calendar.getInstance().apply { timeInMillis = day.dayStartMillis }
        val mondayBasedIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        weekdayTotals[mondayBasedIndex] += day.usageMillis
        weekdayCounts[mondayBasedIndex]++
    }
    val weekdayAverages = weekdayTotals.mapIndexed { index, total ->
        if (weekdayCounts[index] == 0) 0f else total / weekdayCounts[index] / 60_000f
    }
    val weekdayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    val busiestWeekday = weekdayAverages.indices.maxByOrNull { weekdayAverages[it] }

    InsightCard("장기 사용 추세") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "최근 ${selectedDays}일 중 ${periodUsage.size}일 측정",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Box {
                TextButton(onClick = { periodMenuExpanded = true }) {
                    Text(periods.first { it.first == selectedDays }.second, fontSize = 11.sp)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = UiTranslator.translate("추세 기간 선택"),
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = periodMenuExpanded,
                    onDismissRequest = { periodMenuExpanded = false }
                ) {
                    periods.forEach { (days, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedDays = days
                                periodMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        Text(trendText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        CompactBarChart(
            values = weekdayAverages,
            barColor = MaterialTheme.colorScheme.primary,
            contentDescription = UiTranslator.translate("요일별 평균 앱 사용량 그래프")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdayLabels.forEach { label ->
                Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (busiestWeekday != null && weekdayAverages[busiestWeekday] > 0f) {
            Text(
                "평균 사용이 가장 많은 요일은 ${weekdayLabels[busiestWeekday]}요일 · " +
                    formatInsightDuration((weekdayAverages[busiestWeekday] * 60_000).toLong()) + "입니다.",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        val today = insights.dailyUsage.lastOrNull()
        if (today != null) {
            Text("오늘 ${formatInsightDuration(today.usageMillis)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InsightCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            content()
        }
    }
}

@Composable
private fun CompactBarChart(
    values: List<Float>,
    barColor: Color,
    contentDescription: String,
    warningThreshold: Float? = null
) {
    val safeValues = values.map { it.coerceAtLeast(0f) }
    val maximum = (safeValues.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (safeValues.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (safeValues.size - 1)) / safeValues.size)
            .coerceAtLeast(1f)
        drawLine(
            color = Color.Gray.copy(alpha = 0.25f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
        safeValues.forEachIndexed { index, value ->
            val barHeight = if (value <= 0f) 1.dp.toPx() else (value / maximum) * size.height
            val color = if (warningThreshold != null && value >= warningThreshold) {
                ScoreOrange
            } else {
                barColor
            }
            drawRect(
                color = color.copy(alpha = if (value <= 0f) 0.15f else 0.85f),
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
private fun HourlyAxisLabels() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("0시", "6시", "12시", "18시", "24시").forEach { label ->
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun hourLabel(hour: Int): String = "%02d:00–%02d:00".format(hour, (hour + 1) % 24)

private fun formatInsightDuration(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val minutes = safeMillis / 60_000L
    return when {
        minutes >= 60 -> "${minutes / 60}시간 ${minutes % 60}분"
        minutes > 0 -> "${minutes}분"
        safeMillis > 0 -> "${(safeMillis / 1_000L).coerceAtLeast(1)}초"
        else -> "0분"
    }
}

@Composable
private fun appRatingColor(category: AppCategoryType): Color = when (category.level) {
    1 -> ScoreGreen
    2 -> Color(0xFF38A6A5)
    3 -> MaterialTheme.colorScheme.outline
    4 -> ScoreOrange
    else -> ScoreRed
}
