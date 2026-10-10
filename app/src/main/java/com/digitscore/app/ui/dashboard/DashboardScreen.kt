package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.digitscore.app.i18n.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.digitscore.app.data.entity.CoreIndexSampleEntity
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
import com.digitscore.app.engine.CoreIndexGuidance
import com.digitscore.app.engine.CoreIndexCause
import com.digitscore.app.engine.CoreIndexRecommendation
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.digitscore.app.ui.components.coreIndexTierColor
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.CoreIndexGauge
import com.digitscore.app.ui.components.InformationDetailDialog
import com.digitscore.app.ui.components.ResponsiveContent
import com.digitscore.app.ui.components.SectionHeading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableLongStateOf

private fun points(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToStatistics: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToPresetSettings: () -> Unit,
    forceShowMorningBriefing: Boolean = false,
    onMorningBriefingHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DigitsDatabase.getInstance(context) }
    val viewModel: DashboardViewModel = viewModel()
    val scoreDetail by viewModel.scoreDetail.collectAsState()
    val rollingScoreDetail by viewModel.rollingScoreDetail.collectAsState()
    val appsUsage by viewModel.appsUsage.collectAsState()
    val rollingUsageSummary by viewModel.rollingUsageSummary.collectAsState()
    val unlockCount by viewModel.unlockCount.collectAsState()
    val guidance by viewModel.coreIndexGuidance.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val yesterdaySummary by viewModel.yesterdaySummary.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(60_000L)
        }
    }
    val rolling24hStart = remember(nowMillis / (5 * 60_000L)) { nowMillis - 7L * 24 * 3600_000L }
    val rollingSamples by remember(rolling24hStart) {
        db.coreIndexSampleDao().observeSince(rolling24hStart)
    }.collectAsState(emptyList())

    // 모달 / 다이얼로그 상태 관리
    var showScoreDetailModal by remember { mutableStateOf(false) }
    var showScreenTimeModal by remember { mutableStateOf(false) }
    var showUnlockModal by remember { mutableStateOf(false) }
    var showDistractingModal by remember { mutableStateOf(false) }
    var selectedAppDetail by remember { mutableStateOf<AppUsage?>(null) }
    var showAllAppsModal by remember { mutableStateOf(false) }
    var showGuidanceModal by remember { mutableStateOf(false) }
    var showMorningDialog by remember { mutableStateOf(false) }

    // 외부 Intent(첫 언락 / 헤드업 탭)로부터 강제 호출 시 즉시 팝업 트리거
    LaunchedEffect(forceShowMorningBriefing) {
        if (forceShowMorningBriefing) {
            showMorningDialog = true
        }
    }

    // 아침 기상 시(오전 5시~11시59분) 아직 브리핑 팝업을 확인하지 않은 경우 다이얼로그 자동 표시 (새벽 0~4시 심야 미표시)
    LaunchedEffect(dailyGoal, yesterdaySummary) {
        val currentGoal = dailyGoal
        val summary = yesterdaySummary
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (currentGoal != null && summary != null && currentHour in 5..11) {
            val isCompleted = com.digitscore.app.data.DailyGoalStore.isBriefingCompleted(context)
            if (!isCompleted && !currentGoal.isDismissed) {
                showMorningDialog = true
            }
        }
    }

    // 팝업 즉각 반응성을 위해 dailyGoal 또는 yesterdaySummary가 비어있다면 즉시 로드
    LaunchedEffect(Unit) {
        if (dailyGoal == null || yesterdaySummary == null) {
            withContext(Dispatchers.IO) {
                val (ySummary, goal) = com.digitscore.app.data.DailyGoalStore.generateOrGetGoal(
                    context, db, com.digitscore.app.data.DailyGoalStore.getLogicalDateString()
                )
                com.digitscore.app.data.ScoreRepository.updateYesterdaySummary(ySummary)
                com.digitscore.app.data.ScoreRepository.updateDailyGoal(goal)
            }
        }
    }


    val currentScore = rollingScoreDetail?.finalScore ?: 75
    val grade = ScoreGrade.fromScore(currentScore)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
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
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = UiTranslator.translate("통계 리포트"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToAppSettings) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = UiTranslator.translate("앱별 균형 등급"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToPresetSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = UiTranslator.translate("설정"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        ResponsiveContent(modifier = Modifier.padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        screenTimeMillis = rollingUsageSummary.totalScreenTimeMillis,
                        unlockCount = unlockCount,
                        distractingMillis = rollingUsageSummary.managedTimeMillis,
                        onScreenTimeClick = { showScreenTimeModal = true },
                        onUnlockClick = { showUnlockModal = true },
                        onDistractingClick = { showDistractingModal = true }
                    )
                }

                // 2.5 3대 실질 목표 관리 카드 (코어 지수, 앱 1개 제한, 잠금해제 횟수)
                item {
                    ThreeGoalsDashboardCard(
                        currentScore = currentScore,
                        guidance = guidance,
                        dailyGoal = dailyGoal,
                        unlockCount = unlockCount,
                        appsUsage = appsUsage,
                        onUnlockClick = { showUnlockModal = true }
                    )
                }

                // 3. 최근 24시간 코어 지수 변화 스파크라인 카드 (클릭 시 전체 통계 화면 이동)
                item {
                    TodayCoreIndexSparklineCard(
                        currentScore = currentScore,
                        samples = rollingSamples,
                        onClick = onNavigateToStatistics
                    )
                }

                item {
                    HabitReviewCard(
                        score = currentScore,
                        apps = appsUsage,
                        dailyGoal = dailyGoal,
                        yesterdaySummary = yesterdaySummary
                    )
                }



                // 4. 실시간 앱 사용 헤더
                item {
                    val isEn = Locale.getDefault().language == "en"
                    SectionHeading(
                        title = if (isEn) "App Usage (Last 24 Hours)" else "최근 24시간 앱 사용 현황",
                        subtitle = if (isEn) "Hourly distribution, sessions, and recent trends for the last 24h." else "현재 시각 직전 24시간의 시간대·세션·최근 추세입니다.",
                        actionLabel = if (isEn) (if (appsUsage.size > 3) "All ${appsUsage.size}" else "View All") else (if (appsUsage.size > 3) "전체 ${appsUsage.size}개" else "전체 보기"),
                        onAction = { showAllAppsModal = true }
                    )
                }

                // 5. 상위 3개 앱 사용 목록
                if (appsUsage.isEmpty()) {
                    item {
                        val isEn = Locale.getDefault().language == "en"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isEn) "No app usage records collected yet." else "아직 집계된 앱 사용 기록이 없습니다.",
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(24.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(appsUsage.take(3)) { app ->
                        AppUsageItemCard(
                            appUsage = app,
                            onClick = { selectedAppDetail = app }
                        )
                    }
                    if (appsUsage.size > 3) {
                        item {
                            val isEn = Locale.getDefault().language == "en"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAllAppsModal = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "View all ${appsUsage.size} apps" else "나머지 ${appsUsage.size - 3}개 앱 모두 보기",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    DetailChevron(tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
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
            totalScreenMillis = rollingUsageSummary.totalScreenTimeMillis,
            managedMillis = rollingUsageSummary.managedTimeMillis,
            growthMillis = rollingUsageSummary.growthTimeMillis,
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
            distractingMillis = rollingUsageSummary.managedTimeMillis,
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

    if (showGuidanceModal && guidance != null) {
        GuidanceDetailDialog(
            guidance = requireNotNull(guidance),
            onDismiss = { showGuidanceModal = false },
            onNavigateToStatistics = {
                showGuidanceModal = false
                onNavigateToStatistics()
            }
        )
    }

    if (showMorningDialog && dailyGoal != null && yesterdaySummary != null) {
        MorningBriefingDialog(
            yesterdaySummary = requireNotNull(yesterdaySummary),
            dailyGoal = requireNotNull(dailyGoal),
            onAcceptGoals = { scoreTarget, targetPkg, targetAppName, appLimitMins, unlockLimit ->
                scope.launch {
                    com.digitscore.app.data.DailyGoalStore.setCustomGoal(
                        context, scoreTarget, targetPkg, targetAppName, appLimitMins, unlockLimit
                    )
                    com.digitscore.app.data.CumulativeScoreStore.confirmActivity(db, System.currentTimeMillis(), true)
                    TrackerForegroundService.refreshNotification(context)
                    com.digitscore.app.notification.ScoreNotificationManager.cancelMorningBriefingNotification(context)
                }
                showMorningDialog = false
                onMorningBriefingHandled()
            },
            onSkipGoal = {
                scope.launch {
                    com.digitscore.app.data.DailyGoalStore.markBriefingCompleted(context)
                    com.digitscore.app.data.CumulativeScoreStore.confirmActivity(db, System.currentTimeMillis(), true)
                    TrackerForegroundService.refreshNotification(context)
                    com.digitscore.app.notification.ScoreNotificationManager.cancelMorningBriefingNotification(context)
                }
                showMorningDialog = false
                onMorningBriefingHandled()
            },
            onDismiss = {
                scope.launch {
                    com.digitscore.app.data.DailyGoalStore.markBriefingCompleted(context)
                    com.digitscore.app.notification.ScoreNotificationManager.cancelMorningBriefingNotification(context)
                }
                showMorningDialog = false
                onMorningBriefingHandled()
            }
        )
    }
}

@Composable
private fun ThreeGoalsDashboardCard(
    currentScore: Int,
    guidance: CoreIndexGuidance?,
    dailyGoal: com.digitscore.app.model.DailyGoal?,
    unlockCount: Int,
    appsUsage: List<AppUsage> = emptyList(),
    onUnlockClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showGoalHelp by remember { mutableStateOf(false) }

    // 1. 코어 지수 목표 (하루 종일 고정된 절대 타겟 점수)
    val targetScore = dailyGoal?.scoreTarget ?: 70
    val pointsNeeded = (targetScore - currentScore).coerceAtLeast(0)
    val isAchieved = currentScore >= targetScore

    // 2. 관리 앱 1개 제한 목표
    val targetAppName = dailyGoal?.targetAppName?.ifBlank { null }
    val appLimit = dailyGoal?.appLimitMinutes ?: 30
    val appMins = dailyGoal?.currentAppUsageMinutes ?: 0
    val appRatio = if (appLimit > 0) (appMins.toFloat() / appLimit.toFloat()) else 0f
    val isAppExceeded = appMins > appLimit

    // 3. 잠금 해제 횟수 제한 목표
    val unlockLimit = dailyGoal?.unlockLimitTarget ?: 100
    val unlockRatio = (unlockCount.toFloat() / unlockLimit.toFloat())
    val isUnlockExceeded = unlockCount > unlockLimit

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        val isEn = Locale.getDefault().language == "en"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 헤더: 타이틀 + ? 도움말 아이콘 + 목표 수정 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = null,
                        tint = if (isAchieved) ScoreGreen else ScoreYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isEn) "Today's 3 Key Goals" else "오늘의 3대 실천 목표",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showGoalHelp = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = if (isEn) "Daily Goals Guide" else "실천 목표 안내",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { showEditDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isEn) "Set / Edit Goals" else "목표 설정/수정", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // --- 목표 1: 코어 지수 점수 (수평 게이지 바) ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "1. Core Index" else "1. 코어 지수",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEn) {
                            if (pointsNeeded > 0) "Current ${currentScore}P / Target ${targetScore}P (+${pointsNeeded}P needed)"
                            else "Current ${currentScore}P / Target ${targetScore}P (Achieved 🎉)"
                        } else {
                            if (pointsNeeded > 0) "현재 ${currentScore}P / 목표 ${targetScore}P (+${pointsNeeded}P 필요)"
                            else "현재 ${currentScore}P / 목표 ${targetScore}P (달성 🎉)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pointsNeeded > 0) ScoreYellow else ScoreGreen
                    )
                }

                val scoreProgress = (currentScore.toFloat() / targetScore.toFloat()).coerceIn(0.05f, 1f)

                LinearProgressIndicator(
                    progress = { scoreProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (isAchieved) ScoreGreen else ScoreYellow,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round
                )
            }

            // --- 목표 2: 관리 앱 1개 시간 제한 (수평 막대 그래프) ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val appTitle = if (isEn) "2. Managed App" else "2. 관리 앱"
                    val noneLabel = if (isEn) " (None)" else " (미지정)"
                    Text(
                        text = appTitle + if (targetAppName != null) " (${targetAppName})" else noneLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (targetAppName != null) {
                            if (isAppExceeded) "${appMins} / ${appLimit}m (+${appMins - appLimit}m ⚠️)"
                            else "${appMins} / ${appLimit}m (${(appRatio * 100).toInt()}%)"
                        } else {
                            if (isEn) "Select an app" else "앱 선택 필요"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (targetAppName == null) MaterialTheme.colorScheme.outline
                        else if (isAppExceeded) ScoreRed
                        else if (appRatio >= 0.8f) ScoreYellow
                        else ScoreGreen
                    )
                }

                LinearProgressIndicator(
                    progress = { if (targetAppName != null) appRatio.coerceIn(0.02f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (isAppExceeded) ScoreRed else if (appRatio >= 0.8f) ScoreYellow else ScoreGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round
                )
            }

            // --- 목표 3: 오픈 / 잠금 해제 횟수 제한 (클릭 시 언락 상세 분석 팝업) ---
            Surface(
                onClick = onUnlockClick,
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEn) "3. Daily Unlocks" else "3. 일일 언락",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            DetailChevron()
                        }
                        Text(
                            text = if (isUnlockExceeded) {
                                "${unlockCount} / ${unlockLimit}x (+${unlockCount - unlockLimit}x ⚠️)"
                            } else {
                                "${unlockCount} / ${unlockLimit}x (${(unlockRatio * 100).toInt()}%)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isUnlockExceeded) ScoreRed else if (unlockRatio >= 0.8f) ScoreYellow else ScoreGreen
                        )
                    }

                    LinearProgressIndicator(
                        progress = { unlockRatio.coerceIn(0.02f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (isUnlockExceeded) ScoreRed else if (unlockRatio >= 0.8f) ScoreYellow else ScoreGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditThreeGoalsDialog(
            currentGoal = dailyGoal ?: com.digitscore.app.model.DailyGoal(dateString = com.digitscore.app.data.DailyGoalStore.getLogicalDateString()),
            appsUsage = appsUsage,
            onDismiss = { showEditDialog = false },
            onSave = { score, pkg, name, limit, unlocks ->
                com.digitscore.app.data.DailyGoalStore.setCustomGoal(context, score, pkg, name, limit, unlocks)
                com.digitscore.app.service.TrackerForegroundService.refreshNotification(context)
                showEditDialog = false
            }
        )
    }

    if (showGoalHelp) {
        val isEn = Locale.getDefault().language == "en"
        AlertDialog(
            onDismissRequest = { showGoalHelp = false },
            title = { Text(if (isEn) "Today's 3 Key Goals Guide" else "오늘의 3대 실천 목표 안내", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    if (isEn) {
                        "• The Core Index reflects your rolling 24-hour continuous usage trend.\n" +
                        "• Daily unlock count and managed app limit reset to 0 every morning at 05:00.\n" +
                        "• Goals set in the morning stay active all day to guide your digital balance."
                    } else {
                        "• 코어 지수는 최근 24시간의 연속적인 사용 흐름을 유지합니다.\n" +
                        "• 일일 잠금 해제 횟수와 집중 관리 앱 사용 시간은 매일 아침 05:00에 0으로 리셋됩니다.\n" +
                        "• 아침에 설정한 목표는 하루 종일 유지되며 나의 디지털 밸런스를 돕습니다."
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showGoalHelp = false }) {
                    Text(if (isEn) "OK" else "확인")
                }
            }
        )
    }
}

@Composable
private fun EditThreeGoalsDialog(
    currentGoal: com.digitscore.app.model.DailyGoal,
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onSave: (scoreTarget: Int, targetPkg: String?, targetAppName: String, appLimitMins: Int, unlockLimit: Int) -> Unit
) {
    val isEn = Locale.getDefault().language == "en"
    var selectedScore by remember { mutableIntStateOf(currentGoal.scoreTarget) }
    var selectedPkg by remember { mutableStateOf(currentGoal.targetPackageName) }
    var selectedAppName by remember { mutableStateOf(currentGoal.targetAppName) }
    var selectedAppLimit by remember { mutableIntStateOf(currentGoal.appLimitMinutes) }
    var selectedUnlockLimit by remember { mutableIntStateOf(currentGoal.unlockLimitTarget) }

    val candidateApps = remember(appsUsage) {
        val list = appsUsage.filter { it.packageName != "com.digitscore.app" && it.usageTimeMillis > 60_000L }
            .take(6)
            .map { it.packageName to it.appName }
            .toMutableList()
        if (currentGoal.targetPackageName != null && list.none { it.first == currentGoal.targetPackageName }) {
            list.add(0, currentGoal.targetPackageName to currentGoal.targetAppName)
        }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEn) "Set Today's 3 Key Goals" else "오늘 3대 실천 목표 설정", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 코어 지수 방어선 목표
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEn) "1. Core Index Target" else "1. 코어 지수 목표", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${selectedScore} P", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                    Slider(
                        value = selectedScore.toFloat(),
                        onValueChange = { selectedScore = (Math.round(it / 5f) * 5).toInt() },
                        valueRange = 40f..80f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 2. 관리 앱 1개 선택 & 제한 시간
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEn) "2. Managed App" else "2. 집중 관리할 앱 1개", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (selectedPkg != null) {
                            Text("${selectedAppName} (${selectedAppLimit}${if (isEn) "m" else "분"})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                        } else {
                            Text(if (isEn) "None" else "선택 안 함", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    if (candidateApps.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedPkg == null,
                                onClick = {
                                    selectedPkg = null
                                    selectedAppName = ""
                                },
                                label = { Text(if (isEn) "None" else "선택 안 함", fontSize = 11.sp) }
                            )
                            candidateApps.forEach { (pkg, name) ->
                                val matchingApp = appsUsage.firstOrNull { it.packageName == pkg }
                                val mins = (matchingApp?.usageTimeMillis ?: 0L) / 60_000L
                                FilterChip(
                                    selected = selectedPkg == pkg,
                                    onClick = {
                                        selectedPkg = pkg
                                        selectedAppName = name
                                        if (mins >= 15) {
                                            selectedAppLimit = ((mins * 0.7f).toInt() / 5) * 5
                                        }
                                    },
                                    label = {
                                        val labelText = if (mins > 0) "$name (${mins}m)" else name
                                        Text(labelText, fontSize = 11.sp, maxLines = 1)
                                    }
                                )
                            }
                        }
                    }

                    if (selectedPkg != null) {
                        val matchingApp = appsUsage.firstOrNull { it.packageName == selectedPkg }
                        val mins = (matchingApp?.usageTimeMillis ?: 0L) / 60_000L
                        if (mins > 0) {
                            val reduction = ((mins - selectedAppLimit).toFloat() / mins.toFloat() * 100).toInt().coerceAtLeast(0)
                            Text(
                                text = if (isEn) "💡 Recent ${mins}m used → Target ${selectedAppLimit}m (${reduction}% reduction)"
                                       else "💡 최근 ${mins}분 사용 → 목표 ${selectedAppLimit}분 (${reduction}% 절감 제안)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Slider(
                            value = selectedAppLimit.toFloat(),
                            onValueChange = { selectedAppLimit = (Math.round(it / 5f) * 5).toInt() },
                            valueRange = 15f..180f,
                            steps = 32,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 3. 잠금 해제 횟수 제한
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEn) "3. Daily Unlock Limit" else "3. 일일 잠금 해제 조절", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${selectedUnlockLimit}x", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                    Slider(
                        value = selectedUnlockLimit.toFloat(),
                        onValueChange = { selectedUnlockLimit = (Math.round(it / 10f) * 10).toInt() },
                        valueRange = 40f..180f,
                        steps = 13,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedScore, selectedPkg, selectedAppName, selectedAppLimit, selectedUnlockLimit)
                }
            ) {
                Text(if (isEn) "Save Goals" else "목표 저장", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "취소")
            }
        }
    )
}

@Composable
private fun GuidanceSummaryCard(guidance: CoreIndexGuidance, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("지금의 한 가지 제안", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(guidanceRecommendationText(guidance), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(guidanceCauseText(guidance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DetailChevron(tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TodayCoreIndexSparklineCard(
    currentScore: Int,
    samples: List<CoreIndexSampleEntity>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val zone = remember { java.time.ZoneId.systemDefault() }

    var showChartHelp by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isEn = Locale.getDefault().language == "en"
                        Text(
                            text = if (isEn) "Core Index Trend (Last 7 Days)" else "최근 7일 코어 지수 변화",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showChartHelp = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = if (isEn) "Chart Guide" else "차트 안내",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    val isEn = Locale.getDefault().language == "en"
                    val nowMillis = System.currentTimeMillis()
                    val target24hAgo = nowMillis - 24 * 3600_000L
                    val sample24hAgo = samples.minByOrNull { kotlin.math.abs(it.timestampMillis - target24hAgo) }?.score ?: currentScore
                    val diff = currentScore - sample24hAgo
                    val diffStr = if (isEn) {
                        if (diff > 0) "+${diff} pts" else if (diff < 0) "${diff} pts" else "0 pts"
                    } else {
                        if (diff > 0) "+${diff}점" else if (diff < 0) "${diff}점" else "0점"
                    }
                    val minScore = samples.minOfOrNull { it.score } ?: currentScore
                    val maxScore = samples.maxOfOrNull { it.score } ?: currentScore
                    Text(
                        text = if (isEn) "24h ago: ${sample24hAgo} pts → Now: ${currentScore} pts (${diffStr}) · High: ${maxScore} pts"
                        else "24시간 전 ${sample24hAgo}점 → 현재 ${currentScore}점 (${diffStr}) · 최고 ${maxScore}점",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val headerTierColor = coreIndexTierColor(currentScore)
                    val grade = ScoreGrade.fromScore(currentScore)
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(headerTierColor, CircleShape)
                        )
                        val zoneDesc = if (isEn) {
                            when {
                                currentScore >= 80 -> "Stable"
                                currentScore >= 60 -> "Moderate"
                                currentScore >= 40 -> "Caution"
                                else -> "Critical"
                            }
                        } else {
                            when {
                                currentScore >= 80 -> "안정 구간"
                                currentScore >= 60 -> "보통 구간"
                                currentScore >= 40 -> "주의 구간"
                                else -> "하위 위험 구간"
                            }
                        }
                        Text(
                            text = if (isEn) "Now: ${currentScore} pts · ${grade.gradeText} ($zoneDesc)"
                            else "현재 ${currentScore}점 · ${grade.gradeText} ($zoneDesc)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = headerTierColor
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isEn = Locale.getDefault().language == "en"
                    Text(
                        text = if (isEn) "Full Stats" else "상세 통계",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DetailChevron(tint = MaterialTheme.colorScheme.primary)
                }
            }

            val tierColor = coreIndexTierColor(currentScore)
            val textMeasurer = rememberTextMeasurer()
            val scrollState = rememberScrollState()
            var initialScroll by remember(samples.size) { mutableStateOf(false) }
            LaunchedEffect(scrollState.maxValue) {
                if (!initialScroll && scrollState.maxValue > 0) {
                    scrollState.scrollTo(scrollState.maxValue)
                    initialScroll = true
                }
            }

            val timelineDays = 7
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val viewportWidth = maxWidth
                val chartWidth = maxOf(viewportWidth, viewportWidth * timelineDays)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .width(chartWidth)
                            .height(116.dp)
                            .semantics {
                                contentDescription = "최근 코어 지수 변화 차트, 현재 ${currentScore}점"
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val topPadding = 12f
                            val bottomPadding = 12f
                            val chartH = h - topPadding - bottomPadding

                            val allScores = samples.map { it.exactScore } + listOf(currentScore.toDouble())
                            val rawMin = allScores.minOrNull() ?: currentScore.toDouble()
                            val rawMax = allScores.maxOrNull() ?: currentScore.toDouble()
                            val scoreRange = rawMax - rawMin

                            val targetSpan = maxOf(16.0, scoreRange + 6.0)
                            val centerScore = (rawMin + rawMax) / 2.0
                            var yMin = (centerScore - targetSpan / 2.0).coerceIn(0.0, 100.0 - targetSpan)
                            var yMax = (yMin + targetSpan).coerceAtMost(100.0)
                            if (yMax - yMin < targetSpan) {
                                yMin = (yMax - targetSpan).coerceAtLeast(0.0)
                            }

                            fun yPos(score: Double): Float =
                                topPadding + chartH * (1f - ((score - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f))

                            fun drawZone(bottomScore: Double, topScore: Double, color: Color) {
                                val boundedTop = topScore.coerceAtMost(yMax)
                                val boundedBottom = bottomScore.coerceAtLeast(yMin)
                                if (boundedBottom < boundedTop) {
                                    val yTop = yPos(boundedTop)
                                    val yBottom = yPos(boundedBottom)
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(0f, yTop),
                                        size = Size(w, yBottom - yTop)
                                    )
                                }
                            }

                            drawZone(80.0, 100.0, ScoreGreen.copy(alpha = 0.07f))
                            drawZone(60.0, 80.0, ScoreYellow.copy(alpha = 0.04f))
                            drawZone(40.0, 60.0, ScoreOrange.copy(alpha = 0.05f))
                            drawZone(0.0, 40.0, ScoreRed.copy(alpha = 0.08f))

                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                            if (80.0 in yMin..yMax) {
                                val y80 = yPos(80.0)
                                drawLine(
                                    color = ScoreGreen.copy(alpha = 0.45f),
                                    start = Offset(0f, y80),
                                    end = Offset(w, y80),
                                    strokeWidth = 1.2f.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "80 안정",
                                    topLeft = Offset(w - 44.dp.toPx(), y80 - 13.sp.toPx()),
                                    style = TextStyle(
                                        color = ScoreGreen.copy(alpha = 0.85f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            if (60.0 in yMin..yMax) {
                                val y60 = yPos(60.0)
                                drawLine(
                                    color = ScoreYellow.copy(alpha = 0.25f),
                                    start = Offset(0f, y60),
                                    end = Offset(w, y60),
                                    strokeWidth = 0.8f.dp.toPx()
                                )
                            }

                            if (40.0 in yMin..yMax) {
                                val y40 = yPos(40.0)
                                drawLine(
                                    color = ScoreRed.copy(alpha = 0.45f),
                                    start = Offset(0f, y40),
                                    end = Offset(w, y40),
                                    strokeWidth = 1.2f.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "40 위험",
                                    topLeft = Offset(w - 44.dp.toPx(), y40 - 13.sp.toPx()),
                                    style = TextStyle(
                                        color = ScoreRed.copy(alpha = 0.85f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            val now = System.currentTimeMillis()
                            val windowStartMillis = now - timelineDays * 24 * 3600_000L
                            val duration = (timelineDays * 24 * 3600_000L).toDouble()
                            fun xPos(ts: Long): Float =
                                (((ts - windowStartMillis).toDouble() / duration).coerceIn(0.0, 1.0) * w).toFloat()

                            if (samples.size >= 2) {
                                val path = Path()
                                val fillPath = Path()

                                samples.forEachIndexed { i, s ->
                                    val x = xPos(s.timestampMillis)
                                    val y = yPos(s.exactScore)

                                    if (i == 0) {
                                        path.moveTo(x, y)
                                        fillPath.moveTo(x, h - bottomPadding)
                                        fillPath.lineTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                        fillPath.lineTo(x, y)
                                    }
                                }

                                val lastSample = samples.last()
                                val lastX = xPos(lastSample.timestampMillis)
                                fillPath.lineTo(lastX, h - bottomPadding)
                                fillPath.close()

                                val areaBrush = Brush.verticalGradient(
                                    colors = listOf(
                                        ScoreGreen.copy(alpha = 0.22f),
                                        ScoreYellow.copy(alpha = 0.10f),
                                        ScoreRed.copy(alpha = 0.02f)
                                    ),
                                    startY = topPadding,
                                    endY = h - bottomPadding
                                )
                                drawPath(path = fillPath, brush = areaBrush)

                                val lineBrush = Brush.verticalGradient(
                                    colors = listOf(ScoreGreen, ScoreYellow, ScoreRed),
                                    startY = topPadding,
                                    endY = h - bottomPadding
                                )
                                drawPath(
                                    path = path,
                                    brush = lineBrush,
                                    style = Stroke(width = 2.6f.dp.toPx(), cap = StrokeCap.Round)
                                )

                                val maxSample = samples.maxByOrNull { it.exactScore }
                                val minSample = samples.minByOrNull { it.exactScore }
                                val paintGreen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = ScoreGreen.toArgb(); textSize = 9.sp.toPx(); isFakeBoldText = true
                                }
                                val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = ScoreRed.toArgb(); textSize = 9.sp.toPx(); isFakeBoldText = true
                                }
                                val isEn = Locale.getDefault().language == "en"
                                maxSample?.let { s ->
                                    val mx = xPos(s.timestampMillis)
                                    val my = yPos(s.exactScore)
                                    drawCircle(ScoreGreen, 2.5.dp.toPx(), Offset(mx, my))
                                    drawContext.canvas.nativeCanvas.drawText(
                                        if (isEn) "High ${points(s.exactScore)}" else "최고 ${points(s.exactScore)}",
                                        (mx - 20.dp.toPx()).coerceIn(4.dp.toPx(), w - 50.dp.toPx()),
                                        my - 6.dp.toPx(),
                                        paintGreen
                                    )
                                }
                                minSample?.let { s ->
                                    val mx = xPos(s.timestampMillis)
                                    val my = yPos(s.exactScore)
                                    drawCircle(ScoreRed, 2.5.dp.toPx(), Offset(mx, my))
                                    drawContext.canvas.nativeCanvas.drawText(
                                        if (isEn) "Low ${points(s.exactScore)}" else "최저 ${points(s.exactScore)}",
                                        (mx - 20.dp.toPx()).coerceIn(4.dp.toPx(), w - 50.dp.toPx()),
                                        my + 14.dp.toPx(),
                                        paintRed
                                    )
                                }

                                val lastY = yPos(lastSample.exactScore)
                                val lastTierColor = coreIndexTierColor(lastSample.exactScore.roundToInt())
                                drawCircle(
                                    color = lastTierColor.copy(alpha = 0.25f),
                                    radius = 7.dp.toPx(),
                                    center = Offset(lastX, lastY)
                                )
                                drawCircle(
                                    color = tierColor,
                                    radius = 3.5f.dp.toPx(),
                                    center = Offset(lastX, lastY)
                                )
                            } else {
                                val y = yPos(currentScore.toDouble())
                                drawLine(
                                    color = tierColor,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }

                    // 시간 눈금 라벨 (7일 기준)
                    val nowInstant = remember(samples) { Instant.now() }
                    val dayFormatter = remember { DateTimeFormatter.ofPattern("M/d") }
                    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

                    Row(
                        modifier = Modifier
                            .width(chartWidth)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val isEn = Locale.getDefault().language == "en"
                        (6 downTo 1).forEach { daysAgo ->
                            val t = nowInstant.minusSeconds(daysAgo * 86400L).atZone(zone)
                            val isYesterday = daysAgo == 1
                            val text = if (isYesterday) (if (isEn) "Yest (${t.format(timeFormatter)})" else "어제 (${t.format(timeFormatter)})") else "${t.format(dayFormatter)} ${t.format(timeFormatter)}"
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isYesterday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                fontWeight = if (isYesterday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Text(
                            text = if (isEn) "Now (${nowInstant.atZone(zone).format(timeFormatter)})" else "현재 (${nowInstant.atZone(zone).format(timeFormatter)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showChartHelp) {
        val isEn = Locale.getDefault().language == "en"
        AlertDialog(
            onDismissRequest = { showChartHelp = false },
            title = { Text(if (isEn) "Core Index Trend Guide" else "코어 지수 변화 차트 안내", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    if (isEn) {
                        "• Real-time core index score trend over the last 7 days.\n" +
                        "• Scroll horizontally to inspect score changes by time on past dates."
                    } else {
                        "• 최근 7일 동안의 실시간 코어 지수 변화 흐름입니다.\n" +
                        "• 좌우로 스크롤하여 지난 날짜의 시간대별 점수 변화를 상세히 살펴볼 수 있습니다."
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showChartHelp = false }) {
                    Text(if (isEn) "OK" else "확인")
                }
            }
        )
    }
}

@Composable
private fun GuidanceDetailDialog(
    guidance: CoreIndexGuidance,
    onDismiss: () -> Unit,
    onNavigateToStatistics: () -> Unit
) {
    val isEn = Locale.getDefault().language == "en"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEn) "Last 24 Hours Flow" else "최근 24시간 사용 흐름", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightCard(if (isEn) "Why Score Changed" else "지수가 움직인 이유") {
                    Text(guidanceCauseText(guidance), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isEn) {
                            if (guidance.scoreChange > 0) "+${guidance.scoreChange} pts compared to previous record."
                            else if (guidance.scoreChange < 0) "-${-guidance.scoreChange} pts compared to previous record."
                            else "Score is unchanged, but usage trends continue updating."
                        } else {
                            if (guidance.scoreChange > 0) "직전 기록보다 ${guidance.scoreChange}점 올랐습니다."
                            else if (guidance.scoreChange < 0) "직전 기록보다 ${-guidance.scoreChange}점 낮아졌습니다."
                            else "직전 기록과 같은 점수지만 사용 흐름은 계속 갱신됩니다."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                InsightCard(if (isEn) "Estimated Recovery" else "회복 예상") {
                    Text(
                        guidance.recoveryMinutes?.let {
                            if (isEn) "About $it minutes of awake rest may reach ${guidance.recoveryTargetScore}. Sleep is excluded."
                            else "깨어 있는 상태로 약 ${it}분 쉬면 ${guidance.recoveryTargetScore}점으로 예상됩니다. 수면은 제외한 추정입니다."
                        } ?: if (isEn) "Recovery depends on accumulated use and awake rest; a near-term target is not available."
                            else "회복 속도는 누적 사용과 활동 중 휴식에 따라 달라집니다. 가까운 회복 시점은 아직 예측하기 어렵습니다.",
                        fontSize = 12.sp
                    )
                }
                InsightCard(if (isEn) "Last 24 Hours Summary" else "최근 24시간 요약") {
                    Text(
                        if (isEn) "Screen ${formatMinutesToHoursAndMinutes(guidance.rollingUsageMinutes)} · ${guidance.rollingOpenCount}x · ≤1m ${guidance.shortOpenCount}x"
                        else "화면 ${formatMinutesToHoursAndMinutes(guidance.rollingUsageMinutes)} · 앱 ${guidance.rollingOpenCount}x · 1분 미만 ${guidance.shortOpenCount}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                InsightCard(if (isEn) "My Recent Baseline" else "나의 최근 기준") {
                    val recent = guidance.recentSevenDayAverage
                    val previous = guidance.previousSevenDayAverage
                    Text(
                        if (isEn) {
                            when {
                                recent == null -> "Will compare with your past trends once enough data accumulates."
                                previous == null -> "Recent average is $recent pts. Preparing comparison baseline."
                                else -> "7-day average $recent pts · ${if (recent >= previous) "+${recent - previous}" else "${recent - previous}"} pts vs prior 7 days"
                            }
                        } else {
                            when {
                                recent == null -> "기록이 쌓이면 자신의 지난 사용 흐름과 비교합니다."
                                previous == null -> "최근 기록 평균은 ${recent}점입니다. 이전 비교 기간을 준비하고 있습니다."
                                else -> "최근 7일 평균 ${recent}점 · 이전 7일 대비 ${recent - previous}점"
                            }
                        },
                        fontSize = 12.sp
                    )
                }
                Text(guidanceRecommendationText(guidance), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = onNavigateToStatistics) { Text(if (isEn) "View in Stats" else "통계에서 확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isEn) "Close" else "닫기") } }
    )
}

private fun guidanceCauseText(guidance: CoreIndexGuidance): String {
    val isEn = Locale.getDefault().language == "en"
    return if (isEn) {
        when (guidance.cause) {
            CoreIndexCause.CALIBRATING -> "Calibrating and learning usage patterns."
            CoreIndexCause.CONTINUOUS_USE -> "Continuous use is the main cause of index change."
            CoreIndexCause.MANAGED_APP_USE -> "${guidance.leadingAppName ?: "Managed app"} usage is the primary load factor."
            CoreIndexCause.FREQUENT_UNLOCKS -> "Frequent screen unlocks have contributed to recent load."
            CoreIndexCause.RECOVERING -> "Recovering steadily after putting down the device."
            CoreIndexCause.STEADY -> "Recent usage patterns are well-balanced."
        }
    } else {
        when (guidance.cause) {
            CoreIndexCause.CALIBRATING -> "사용 흐름을 학습하고 있습니다."
            CoreIndexCause.CONTINUOUS_USE -> "연속 사용이 현재 지수 변화의 가장 큰 원인입니다."
            CoreIndexCause.MANAGED_APP_USE -> "${guidance.leadingAppName ?: "관리 앱"} 사용이 최근 부하의 가장 큰 원인입니다."
            CoreIndexCause.FREQUENT_UNLOCKS -> "잦은 화면 확인이 최근 부하에 반영됐습니다."
            CoreIndexCause.RECOVERING -> "화면을 내려놓은 뒤 회복이 진행 중입니다."
            CoreIndexCause.STEADY -> "최근 사용 흐름은 안정적입니다."
        }
    }
}

private fun guidanceRecommendationText(guidance: CoreIndexGuidance): String {
    val isEn = Locale.getDefault().language == "en"
    return if (isEn) {
        when (guidance.recommendation) {
            CoreIndexRecommendation.KEEP_BALANCE -> "Keep up this healthy pace and mindfully plan your next check."
            CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK -> "Take a 10-minute break away from your screen now."
            CoreIndexRecommendation.TAKE_QUIET_BREAK -> "Silence notifications and take a screen-free rest."
            CoreIndexRecommendation.BATCH_PHONE_CHECKS -> "Try batching your next two phone checks into one."
            CoreIndexRecommendation.WIND_DOWN -> "Time to wind down late-night screen time and dim the brightness."
        }
    } else {
        when (guidance.recommendation) {
            CoreIndexRecommendation.KEEP_BALANCE -> "지금의 흐름을 유지하고 다음 확인을 의식적으로 선택해보세요."
            CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK -> "지금 한 번, 10분 동안 화면을 내려놓아 보세요."
            CoreIndexRecommendation.TAKE_QUIET_BREAK -> "알림을 잠시 두고 화면 없는 휴식을 시작해보세요."
            CoreIndexRecommendation.BATCH_PHONE_CHECKS -> "다음 확인 두 번을 한 번으로 묶어보세요."
            CoreIndexRecommendation.WIND_DOWN -> "심야 사용을 마치고 화면 밝기를 내려놓을 시간입니다."
        }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = context.getString(R.string.score_accessibility, score, gradeText) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val useWideLayout = maxWidth >= 560.dp
            if (useWideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoreIndexGauge(
                        score = score,
                        grade = gradeText,
                        modifier = Modifier.width(110.dp),
                        iconSize = 64.dp
                    )
                    ScoreGaugeSummary(
                        rollingScore = rollingScore,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoreIndexGauge(
                        score = score,
                        grade = gradeText,
                        modifier = Modifier.width(88.dp),
                        iconSize = 48.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.digitscore_score)} · ${stringResource(R.string.cumulative_index_label)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HeroMetric(
                            label = stringResource(R.string.continuous_usage),
                            value = formatMinutesToHoursAndMinutes(rollingScore?.continuousUsageMinutes ?: 0L),
                            modifier = Modifier.fillMaxWidth()
                        )
                        ScoreDetailAffordance()
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreGaugeSummary(
    rollingScore: RollingScoreDetail?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${stringResource(R.string.digitscore_score)} · ${stringResource(R.string.cumulative_index_label)}",
            style = MaterialTheme.typography.titleMedium
        )
        HeroMetric(
            label = stringResource(R.string.continuous_usage),
            value = formatMinutesToHoursAndMinutes(rollingScore?.continuousUsageMinutes ?: 0L),
            modifier = Modifier.fillMaxWidth()
        )
        ScoreDetailAffordance()
    }
}

@Composable
private fun ScoreStatusChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ScoreDetailAffordance() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.tap_score_details),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        DetailChevron()
    }
}

@Composable
private fun ScoreStatsRow(
    screenTimeMillis: Long,
    unlockCount: Int,
    distractingMillis: Long,
    onScreenTimeClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onDistractingClick: () -> Unit
) {
    val isEn = Locale.getDefault().language == "en"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatisticCard(if (isEn) "Screen" else "화면", formatInsightDuration(screenTimeMillis), Icons.Default.PhoneAndroid, onScreenTimeClick, Modifier.weight(1f))
        StatisticCard(if (isEn) "Unlocks" else "언락", "${unlockCount}x", Icons.Default.LockOpen, onUnlockClick, Modifier.weight(1f))
        StatisticCard(if (isEn) "Managed" else "관리", formatInsightDuration(distractingMillis), Icons.Default.Warning, onDistractingClick, Modifier.weight(1f))
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
    val isEn = Locale.getDefault().language == "en"
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
                Text(
                    text = "${appUsage.categoryType.displayName} · ${if (isEn) "24h" else "24시간"} ${appUsage.sessionCount}x",
                    fontSize = 11.sp,
                    color = categoryColor
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatInsightDuration(appUsage.usageTimeMillis), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${remainingMinutes}m"
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
                if ((detail?.lateNightCarryoverLoad ?: 0.0) >= 0.05) {
                    BreakdownRow(
                        stringResource(R.string.late_night_carryover_load),
                        String.format("%.1f", detail?.lateNightCarryoverLoad ?: 0.0)
                    )
                }
                BreakdownRow(stringResource(R.string.acute_load), String.format("%.1f", detail?.acuteLoad ?: 0.0))
                if ((detail?.sleepRestMinutes ?: 0L) > 0L) {
                    BreakdownRow(
                        stringResource(R.string.sleep_rest_time),
                        stringResource(R.string.format_minutes, detail?.sleepRestMinutes ?: 0L)
                    )
                    if ((detail?.postWakeRestMinutes ?: 0L) > 0L) {
                        BreakdownRow(
                            stringResource(R.string.post_wake_rest_time),
                            stringResource(R.string.format_minutes, detail?.postWakeRestMinutes ?: 0L)
                        )
                    }
                    BreakdownRow(
                        stringResource(R.string.effective_recovery_time),
                        stringResource(R.string.format_minutes, detail?.effectiveRecoveryMinutes ?: 0L)
                    )
                }
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
            String.format(Locale.US, "%.1f", detail.rollingLoad)
        )
        ScoreFlow.RECOVERING -> stringResource(R.string.score_recovering, detail.restMinutes)
        ScoreFlow.STEADY -> stringResource(
            R.string.score_steady,
            String.format(Locale.US, "%.1f", detail.rollingLoad)
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
    totalScreenMillis: Long,
    managedMillis: Long,
    growthMillis: Long,
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onSelectApp: (AppUsage) -> Unit
) {
    val distractingMillis = managedMillis.coerceAtLeast(0L)
    val productiveMillis = growthMillis.coerceAtLeast(0L)
    val neutralMillis = (totalScreenMillis - distractingMillis - productiveMillis).coerceAtLeast(0L)

    val isEn = Locale.getDefault().language == "en"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = if (isEn) "Screen Time (Last 24 Hours)" else "최근 24시간 화면 사용", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = if (isEn) "Total ${formatInsightDuration(totalScreenMillis)}" else "총 ${formatInsightDuration(totalScreenMillis)}",
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
                            Text(text = if (isEn) "Time Breakdown by Category" else "카테고리별 시간 분배", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            CompactBarChart(
                                values = listOf(distractingMillis.toFloat(), (productiveMillis + neutralMillis).toFloat()),
                                barColor = MaterialTheme.colorScheme.primary,
                                contentDescription = UiTranslator.translate("관리 및 일반 앱 사용시간 비교 그래프")
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Text(if (isEn) "Managed" else "관리", fontSize = 10.sp, color = ScoreRed)
                                Text(if (isEn) "General" else "일반", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (isEn) "Managed apps" else "관리 앱", fontSize = 12.sp)
                                Text(text = formatInsightDuration(distractingMillis), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ScoreRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (isEn) "General apps" else "일반 앱", fontSize = 12.sp)
                                Text(text = formatInsightDuration(neutralMillis + productiveMillis), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = if (isEn) "App Usage (Tap to configure)" else "앱별 사용 시간 (탭하여 설정 변경)",
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
                                text = "${app.categoryType.displayName} · ${if (isEn) "24h" else "24시간"} ${app.sessionCount}x",
                                fontSize = 11.sp,
                                color = appRatingColor(app.categoryType)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatInsightDuration(app.usageTimeMillis),
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
                Text(if (isEn) "View Weekly/Monthly Trends" else "주간/월간 추세 보기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "닫기")
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
            UsageStatsHelper.getRolling24HourUnlockInsights(context)
        }
    }

    val isEn = Locale.getDefault().language == "en"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = if (isEn) "Unlock In-Depth Analysis" else "오늘의 잠금 해제 정밀 분석", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = if (isEn) "Total ${unlockCount}x today (since 05:00)" else "오늘 05:00 이후 총 ${unlockCount}x",
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
                    val totalUnlocks = unlockCount.coerceAtLeast(1)
                    val glanceCount = value.glanceUnlockCount
                    val glanceRatio = (glanceCount.toFloat() / totalUnlocks.toFloat()).coerceIn(0f, 1f)
                    val appUnlocks = (totalUnlocks - glanceCount).coerceAtLeast(0)

                    // 1. 습관적 순간 열람(10초 이내) vs 실제 앱 사용 분리 분석 카드
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isEn) "Quick Glances vs App Usage" else "습관적 열람 vs 실제 앱 사용", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Text(if (isEn) "Threshold: ≤10s" else "기준: 10초 이내", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            // 수평 분할 막대 그래프
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                            ) {
                                if (glanceRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(glanceRatio.coerceAtLeast(0.05f))
                                            .fillMaxHeight()
                                            .background(ScoreYellow)
                                    )
                                }
                                if (1f - glanceRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight((1f - glanceRatio).coerceAtLeast(0.05f))
                                            .fillMaxHeight()
                                            .background(ScoreGreen)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(ScoreYellow, CircleShape))
                                    Text(
                                        if (isEn) "Quick Glance: ${glanceCount}x (${(glanceRatio * 100).toInt()}%)"
                                        else "순간 확인: ${glanceCount}x (${(glanceRatio * 100).toInt()}%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(ScoreGreen, CircleShape))
                                    Text(
                                        if (isEn) "App Usage: ${appUnlocks}x"
                                        else "앱 사용: ${appUnlocks}x",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Text(
                                text = if (isEn) "💡 Detected habitual checks where the screen was turned off within 10s without launching apps. Calibrates gradually to your response speed."
                                else "💡 앱을 켜지 않고 10초 이내에 화면을 끈 습관적 확인을 감지했습니다. 개인 세션 반응 속도를 학습하여 점진적으로 보정합니다.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. 나의 14일 최저치 / 평균 기준 카드
                    if (value.past14DaysLowestUnlock > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isEn) "My Past Trend Baseline" else "나의 과거 트렌드 기준", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isEn) "14d Low: ${value.past14DaysLowestUnlock}x" else "14일 최저: ${value.past14DaysLowestUnlock}x", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ScoreGreen)
                                    Text(if (isEn) "Daily Avg: ${value.past14DaysAverageUnlock}x" else "일평균: ${value.past14DaysAverageUnlock}x", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isEn) "Today: ${unlockCount}x" else "오늘: ${unlockCount}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ScoreYellow)
                                }
                                Text(
                                    text = if (isEn) "💡 Use your past record (${value.past14DaysLowestUnlock}x) as a benchmark to gradually reduce daily unlock goals."
                                    else "💡 과거 최저 기록(${value.past14DaysLowestUnlock}x)을 바닥선으로 삼아 일일 목표를 점진적으로 낮춰보세요.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val busiest = value.hourlyUnlockCounts.withIndex()
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .take(3)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(if (isEn) "Hourly Unlocks" else "시간대별 언락", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            if (busiest.isEmpty()) {
                                Text(if (isEn) "No unlock records available yet for hourly analysis." else "아직 시간대 분석에 필요한 언락 기록이 없습니다.", fontSize = 13.sp)
                            } else {
                                CompactBarChart(
                                    values = value.hourlyUnlockCounts.map { it.toFloat() },
                                    barColor = ScoreYellow,
                                    contentDescription = UiTranslator.translate("24시간 언락 횟수 그래프")
                                )
                                RollingHourlyAxisLabels(value.windowStartMillis, value.windowEndMillis)
                                val peak = busiest.first()
                                Text(
                                    if (isEn) "Most active interval is ${rollingHourLabel(value.windowStartMillis, peak.index)} · ${peak.value} unlocks."
                                    else "가장 잦은 구간은 ${rollingHourLabel(value.windowStartMillis, peak.index)} · ${peak.value}회입니다.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            value.averageIntervalMinutes?.let { interval ->
                                Text(
                                    if (isEn) "Unlocked once every ~${interval.coerceAtLeast(1)} minutes on average."
                                    else "평균 약 ${interval.coerceAtLeast(1)}분마다 한 번 열었습니다.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isEn) "Notifications vs Unlocks" else "알림과 언락 비교", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            if (!value.notificationEventsSupported) {
                                Text(if (isEn) "This Android version does not support notification event comparison." else "이 Android 버전은 알림 이벤트 비교를 제공하지 않습니다.", fontSize = 13.sp)
                            } else {
                                Text(
                                    if (isEn) "OS Notifications: ${value.notificationCount} · Unlocks: $unlockCount"
                                    else "OS 감지 알림 ${value.notificationCount}건 · 언락 ${unlockCount}회",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                CompactBarChart(
                                    values = listOf(value.notificationCount.toFloat(), unlockCount.toFloat()),
                                    barColor = MaterialTheme.colorScheme.primary,
                                    contentDescription = UiTranslator.translate(
                                        "알림 ${value.notificationCount}건과 언락 ${unlockCount}회 비교 그래프"
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    Text(if (isEn) "Notifications" else "알림", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(if (isEn) "Unlocks" else "언락", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                val comparison = when {
                                    value.notificationCount > 0 -> {
                                        val ratio = unlockCount.toFloat() / value.notificationCount
                                        if (isEn) "Unlocked ~${String.format(Locale.US, "%.1f", ratio)} times per notification."
                                        else "알림 1건당 약 ${String.format(Locale.US, "%.1f", ratio)}회 언락했습니다."
                                    }
                                    unlockCount > 0 -> if (isEn) "Unlocked $unlockCount times without any detected notifications."
                                        else "감지된 알림 없이도 ${unlockCount}회 언락했습니다."
                                    else -> if (isEn) "No comparative records available yet." else "아직 비교할 기록이 없습니다."
                                }
                                Text(comparison, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    if (isEn) "Simple comparison of OS events; does not imply notifications directly caused unlocks."
                                    else "OS 이벤트의 단순 비교이며 알림이 언락의 직접 원인이라는 뜻은 아닙니다.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = if (isEn) "Unlock Management Guide" else "언락 관리 가이드", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = if (isEn) "Reducing subconscious phone wakeups significantly sharpens daily focus."
                            else "스마트폰을 무의식적으로 켜는 습관을 줄이면 집중력을 대폭 향상시킬 수 있습니다.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEn) "Rolling 24-hour unlock counts are factored smoothly into core score usage load."
                            else "최근 24시간 언락 횟수는 코어 지수의 사용 부하에 완만하게 반영됩니다.",
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
                Text(if (isEn) "Set Unlock Target" else "목표 언락 횟수 설정")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "닫기")
            }
        }
    )
}

/**
 * 4. 방해 앱 세부 분석 다이얼로그
 */
@Composable
fun DistractingDetailDialog(
    distractingMillis: Long,
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onSelectApp: (AppUsage) -> Unit
) {
    val isEn = Locale.getDefault().language == "en"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = if (isEn) "Managed Apps Detailed Analysis" else "관리 앱 상세 분석", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = if (isEn) "Total ${formatInsightDuration(distractingMillis)}" else "총 ${formatInsightDuration(distractingMillis)}",
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
                        text = if (isEn) "List of managed apps. Tap an app to adjust its category." else "관리 대상 앱 목록입니다. 앱을 눌러 등급을 변경할 수 있습니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (appsUsage.isNotEmpty()) {
                    item {
                        CompactBarChart(
                            values = appsUsage.take(8).map { it.usageTimeMillis / 60_000f },
                            barColor = ScoreRed,
                            contentDescription = UiTranslator.translate("관리 대상 앱별 사용시간 그래프")
                        )
                        Text(
                            text = if (isEn) "Top ${minOf(8, appsUsage.size)} apps by usage" else "사용시간 상위 ${minOf(8, appsUsage.size)}개 앱",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (appsUsage.isEmpty()) {
                    item {
                        Text(
                            text = if (isEn) "No managed apps used in the last 24 hours. Great balance!" else "최근 24시간 사용된 관리 대상 앱이 없습니다. 안정적인 사용 흐름입니다.",
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
                                Text(
                                    text = if (isEn) "24h ${app.sessionCount}x · ≤1m ${app.shortSessionCount}x"
                                    else "24시간 ${app.sessionCount}x · 1분 미만 ${app.shortSessionCount}x",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (app.lateNightUsageMinutes > 0) {
                                    Text(
                                        text = if (isEn) "Late night ${app.lateNightUsageMinutes}m" else "심야 사용 ${app.lateNightUsageMinutes}m",
                                        fontSize = 11.sp,
                                        color = ScoreOrange
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatInsightDuration(app.usageTimeMillis),
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
                Text(if (isEn) "Manage App Categories" else "앱 등급 목록 관리")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "닫기")
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
    val isEn = Locale.getDefault().language == "en"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (isEn) "All Apps (Last 24 Hours)" else "최근 24시간 전체 앱 목록", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                                text = "${app.categoryType.displayName} · ${if (isEn) "24h" else "24시간"} ${app.sessionCount}x",
                                fontSize = 11.sp,
                                color = appRatingColor(app.categoryType)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatInsightDuration(app.usageTimeMillis),
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
                Text(if (isEn) "Close" else "닫기")
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
    val isEn = Locale.getDefault().language == "en"
    var selectedCategory by remember { mutableStateOf(appUsage.categoryType.canonical) }
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
                            selectedCategory.displayName,
                            fontSize = 11.sp,
                            color = appRatingColor(selectedCategory)
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = if (isEn) "Change category" else "균형 등급 변경",
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
                                            category.displayName,
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
                                Text(text = if (isEn) "Last 24 Hours Use" else "최근 24시간 사용", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = formatInsightDuration(appUsage.usageTimeMillis),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (appUsage.lateNightUsageMinutes > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = if (isEn) "Late Night (00~05h)" else "심야(00~05시)", fontSize = 11.sp, color = ScoreOrange)
                                    Text(
                                        text = if (isEn) "${appUsage.lateNightUsageMinutes}m" else "${appUsage.lateNightUsageMinutes}분",
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
                    item { AppOpenTrendInsight(value) }
                    item { AppTrendInsight(value) }
                }

                item {
                    Text(
                        if (isEn) "You can change the category from the menu at the top right."
                        else "등급은 우측 상단 드롭다운에서 변경할 수 있습니다.",
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
                Text(if (isEn) "Apply & Save" else "적용 및 저장", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "취소")
            }
        }
    )
}

@Composable
private fun AppTimeOfDayInsight(insights: AppUsageInsights) {
    val isEn = Locale.getDefault().language == "en"
    val busiest = insights.hourlyUsageMillis.withIndex()
        .filter { it.value >= 10_000L }
        .sortedByDescending { it.value }
        .take(3)
    InsightCard(if (isEn) "When was it used most (Last 24h)?" else "최근 24시간 언제 많이 사용했나요?") {
        if (busiest.isEmpty()) {
            Text(if (isEn) "No hourly usage records available yet." else "아직 분석할 시간대 기록이 없습니다.", fontSize = 12.sp)
        } else {
            CompactBarChart(
                values = insights.hourlyUsageMillis.map { it / 60_000f },
                barColor = MaterialTheme.colorScheme.primary,
                contentDescription = UiTranslator.translate("24시간 앱 사용량 그래프")
            )
            RollingHourlyAxisLabels(insights.windowStartMillis, insights.windowEndMillis)
            val peak = busiest.first()
            Text(
                if (isEn) "Peak interval was ${rollingHourLabel(insights.windowStartMillis, peak.index)} · ${formatInsightDuration(peak.value)}."
                else "가장 많이 사용한 구간은 ${rollingHourLabel(insights.windowStartMillis, peak.index)} · ${formatInsightDuration(peak.value)}입니다.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AppSessionInsight(insights: AppUsageInsights) {
    val isEn = Locale.getDefault().language == "en"
    val sessions = insights.sessionDurationsMillis
    val average = sessions.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
    val longest = sessions.maxOrNull() ?: 0L
    val shortOpens = sessions.count { it in 1 until 60_000L }
    val assessment = if (isEn) {
        when {
            longest >= 30 * 60_000L -> "Sessions exceeding 30 min detected."
            longest >= 15 * 60_000L -> "Moderately long continuous usage detected."
            sessions.isNotEmpty() -> "Mostly split into brief sessions."
            else -> "No session records yet."
        }
    } else {
        when {
            longest >= 30 * 60_000L -> "한 번에 30분 이상 이어진 사용이 있습니다."
            longest >= 15 * 60_000L -> "한 번에 다소 길게 사용한 구간이 있습니다."
            sessions.isNotEmpty() -> "대체로 짧게 나누어 사용했습니다."
            else -> "아직 세션 기록이 없습니다."
        }
    }
    InsightCard(if (isEn) "Were any sessions overly long?" else "한 번에 너무 길게 사용했나요?") {
        if (sessions.isNotEmpty()) {
            CompactBarChart(
                values = sessions.takeLast(10).map { it / 60_000f },
                barColor = MaterialTheme.colorScheme.primary,
                warningThreshold = 30f,
                contentDescription = UiTranslator.translate("최근 앱 사용 세션 길이 그래프")
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isEn) "Recent sessions" else "최근 세션", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                Text(if (isEn) "Orange: ≥30 min" else "30분 이상은 주황색", fontSize = 10.sp, color = ScoreOrange)
            }
        }
        Text(
            if (isEn) "${sessions.size}x · avg ${formatInsightDuration(average)} · max ${formatInsightDuration(longest)}"
            else "${sessions.size}x · 평균 ${formatInsightDuration(average)} · 최장 ${formatInsightDuration(longest)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (sessions.isNotEmpty()) {
            Text(
                if (isEn) "Opened ${sessions.size} times in 24h, with ${shortOpens} under 1 min."
                else "최근 24시간 ${sessions.size}회 열었고, 그중 1분 미만은 ${shortOpens}회입니다.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            assessment,
            fontSize = 12.sp,
            color = if (longest >= 30 * 60_000L) ScoreOrange else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AppOpenTrendInsight(insights: AppUsageInsights) {
    val isEn = Locale.getDefault().language == "en"
    val days = insights.dailyUsage.takeLast(14)
    InsightCard(if (isEn) "How often was it opened by date?" else "날짜별로 몇 번 열었나요?") {
        val rollingSessions = insights.sessionDurationsMillis
        val rollingShortCount = rollingSessions.count { it in 1 until 60_000L }
        Text(
            if (isEn) "Last 24h: ${rollingSessions.size} opens · ${rollingShortCount} under 1 min"
            else "최근 24시간 ${rollingSessions.size}회 · 1분 미만 ${rollingShortCount}회",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (rollingSessions.size >= 5) {
            val shortRatio = (rollingShortCount * 100f / rollingSessions.size).roundToInt()
            if (shortRatio >= 60) {
                Text(
                    if (isEn) "Brief checks account for ${shortRatio}% of opens. Mindful habit check recommended."
                    else "짧은 확인이 전체 실행의 ${shortRatio}%입니다. 습관적으로 여는 흐름인지 살펴보세요.",
                    fontSize = 11.sp,
                    color = ScoreOrange
                )
            }
        }
        if (days.isEmpty()) {
            Text(if (isEn) "No app open records yet." else "아직 앱 실행 기록이 없습니다.", fontSize = 12.sp)
            return@InsightCard
        }
        val maximum = (days.maxOfOrNull { it.sessionCount } ?: 0).coerceAtLeast(1)
        val totalColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .semantics {
                    contentDescription = UiTranslator.translate("최근 14일 앱 실행 횟수와 1분 미만 실행 그래프")
                }
        ) {
            val slot = size.width / days.size.coerceAtLeast(1)
            val totalWidth = (slot * 0.62f).coerceAtLeast(2.dp.toPx())
            val shortWidth = totalWidth * 0.48f
            days.forEachIndexed { index, day ->
                val totalHeight = size.height * (day.sessionCount.toFloat() / maximum)
                val shortHeight = size.height * (day.shortSessionCount.toFloat() / maximum)
                val centerX = slot * index + slot / 2f
                drawRoundRect(
                    color = totalColor,
                    topLeft = Offset(centerX - totalWidth / 2f, size.height - totalHeight),
                    size = Size(totalWidth, totalHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                if (shortHeight > 0f) {
                    drawRoundRect(
                        color = ScoreOrange,
                        topLeft = Offset(centerX - shortWidth / 2f, size.height - shortHeight),
                        size = Size(shortWidth, shortHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isEn) "Total opens" else "전체 실행", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            Text(if (isEn) "Orange: <1 min" else "주황색 · 1분 미만", fontSize = 10.sp, color = ScoreOrange)
        }
    }
}

@Composable
private fun AppTrendInsight(insights: AppUsageInsights) {
    val isEn = Locale.getDefault().language == "en"
    val periods = listOf(
        7 to (if (isEn) "Last 7 days" else "최근 7일"),
        28 to (if (isEn) "Last 4 weeks" else "최근 4주"),
        84 to (if (isEn) "Last 12 weeks" else "최근 12주"),
        182 to (if (isEn) "Last 6 months" else "최근 6개월"),
        365 to (if (isEn) "Last 1 year" else "최근 1년")
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
    val trendText = if (isEn) {
        when {
            previous.isEmpty() -> "Will compare with prior period of same length once more data accumulates."
            previousAverage == 0.0 && recentAverage > 0.0 -> "New usage detected in the last ${comparisonWindow} days."
            previousAverage == 0.0 -> "No recent change in usage volume."
            else -> {
                val percent = ((recentAverage - previousAverage) / previousAverage * 100).roundToInt()
                when {
                    percent >= 10 -> "Last ${comparisonWindow}d avg increased by ${percent}% vs prior period."
                    percent <= -10 -> "Last ${comparisonWindow}d avg decreased by ${-percent}% vs prior period."
                    else -> "Last ${comparisonWindow}d usage is similar to prior period."
                }
            }
        }
    } else {
        when {
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
    val weekdayLabels = if (isEn) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("월", "화", "수", "목", "금", "토", "일")
    val busiestWeekday = weekdayAverages.indices.maxByOrNull { weekdayAverages[it] }

    InsightCard(if (isEn) "Long-Term Usage Trend" else "장기 사용 추세") {
        Text(
            if (isEn) "Aggregated by calendar dates to allow day-of-week comparisons." else "이 장기 그래프만 요일 비교를 위해 달력 날짜 단위로 집계합니다.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isEn) "Measured on ${periodUsage.size} of last ${selectedDays} days" else "최근 ${selectedDays}일 중 ${periodUsage.size}일 측정",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Box {
                TextButton(onClick = { periodMenuExpanded = true }) {
                    Text(periods.first { it.first == selectedDays }.second, fontSize = 11.sp)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = if (isEn) "Select trend period" else UiTranslator.translate("추세 기간 선택"),
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
                if (isEn) "Peak average usage on ${weekdayLabels[busiestWeekday]} · " +
                    formatInsightDuration((weekdayAverages[busiestWeekday] * 60_000).toLong()) + "."
                else "평균 사용이 가장 많은 요일은 ${weekdayLabels[busiestWeekday]}요일 · " +
                    formatInsightDuration((weekdayAverages[busiestWeekday] * 60_000).toLong()) + "입니다.",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        val today = insights.dailyUsage.lastOrNull { it.isToday }
        if (today != null) {
            Text(if (isEn) "Today ${formatInsightDuration(today.usageMillis)}" else "오늘 ${formatInsightDuration(today.usageMillis)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
            color = baselineColor,
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
private fun RollingHourlyAxisLabels(windowStartMillis: Long, windowEndMillis: Long) {
    val hourFormat = remember { java.text.SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val edgeFormat = remember { java.text.SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val labels = listOf(0, 12, 24).map { offset ->
        val timestamp = if (offset == 24) {
            windowEndMillis
        } else {
            windowStartMillis + offset * 60 * 60_000L
        }
        (if (offset == 0 || offset == 24) edgeFormat else hourFormat).format(Date(timestamp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { label ->
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun rollingHourLabel(windowStartMillis: Long, bucket: Int): String {
    val format = java.text.SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val start = windowStartMillis + bucket.coerceIn(0, 23) * 60 * 60_000L
    return "${format.format(Date(start))}–${format.format(Date(start + 60 * 60_000L))}"
}

private fun formatInsightDuration(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val minutes = safeMillis / 60_000L
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        safeMillis > 0 -> "${(safeMillis / 1_000L).coerceAtLeast(1)}s"
        else -> "0m"
    }
}

@Composable
private fun appRatingColor(category: AppCategoryType): Color = when (category.canonical) {
    AppCategoryType.EXEMPT -> Color(0xFF29B6F6)
    AppCategoryType.NEUTRAL -> MaterialTheme.colorScheme.outline
    AppCategoryType.DISTRACTING -> ScoreRed
    else -> MaterialTheme.colorScheme.outline
}
