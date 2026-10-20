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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.digitscore.app.i18n.AppLocale
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
import androidx.compose.foundation.layout.IntrinsicSize
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
    val yesterdayGoalResult by viewModel.yesterdayGoalResult.collectAsState()
    val rewardState by viewModel.rewardState.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(60_000L)
        }
    }
    val rolling24hStart = remember(nowMillis / (5 * 60_000L)) { nowMillis - 24L * 3600_000L }
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
    var showEditGoalsFromTopBar by remember { mutableStateOf(false) }

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
                    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateToStatistics) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = if (isEn) "Statistics" else "상세 통계",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = { showEditGoalsFromTopBar = true }) {
                            Icon(
                                imageVector = Icons.Default.TrackChanges,
                                contentDescription = if (isEn) "Goals" else "3대 목표",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onNavigateToAppSettings) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = if (isEn) "App Usage & Grades" else "앱별 사용량 및 등급",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onNavigateToPresetSettings) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = if (isEn) "Settings" else "환경설정",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 코어 지수 게이지 + 최근 7일 스파크라인을 결합한 통합 히어로 카드
                item {
                    CoreIndexHeroCard(
                        score = currentScore,
                        grade = grade,
                        rollingScore = rollingScoreDetail,
                        samples = rollingSamples,
                        guidance = guidance,
                        onScoreDetailClick = { showScoreDetailModal = true },
                        onTrendDetailClick = onNavigateToStatistics
                    )
                }

                // 3. 주요 3단 통계 카드 (화면, 언락, 관리)
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

                // 4. 3대 실질 목표 관리 카드 (코어 지수, 앱 1개 제한, 잠금해제 횟수)
                item {
                    ThreeGoalsDashboardCard(
                        currentScore = currentScore,
                        guidance = guidance,
                        dailyGoal = dailyGoal,
                        unlockCount = unlockCount,
                        appsUsage = appsUsage,
                        rewardState = rewardState,
                        onUnlockClick = { showUnlockModal = true }
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



                // 4. 앱별 사용시간 요약 카드 (탭 시 앱별 사용량 및 등급 화면으로 이동)
                item {
                    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAppSettings() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEn) "App Usage" else "앱별 사용시간",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isEn) "All (${appsUsage.size}) ▸" else "전체 ${appsUsage.size}개 ▸",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { onNavigateToAppSettings() }
                                )
                            }

                            if (appsUsage.isEmpty()) {
                                Text(
                                    text = if (isEn) "No usage records yet." else "아직 사용 기록이 없습니다.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                val topApps = appsUsage.take(3)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    topApps.forEach { app ->
                                        val mins = (app.usageTimeMillis / 60_000L).coerceAtLeast(1L)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .clickable { selectedAppDetail = app }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = app.appName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = com.digitscore.app.i18n.formatReadableMinutes(mins, isEn, compact = true),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = appRatingColor(app.categoryType),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
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
    // 세부 항목 모달 / 다이얼로그들
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
            yesterdayGoalResult = yesterdayGoalResult,
            dailyGoal = requireNotNull(dailyGoal),
            rewardState = rewardState,
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

    if (showEditGoalsFromTopBar) {
        EditThreeGoalsDialog(
            currentGoal = dailyGoal ?: com.digitscore.app.model.DailyGoal(dateString = com.digitscore.app.data.DailyGoalStore.getLogicalDateString()),
            appsUsage = appsUsage,
            onDismiss = { showEditGoalsFromTopBar = false },
            onSave = { score, pkg, name, limit, unlocks ->
                com.digitscore.app.data.DailyGoalStore.setCustomGoal(context, score, pkg, name, limit, unlocks)
                com.digitscore.app.service.TrackerForegroundService.refreshNotification(context)
                showEditGoalsFromTopBar = false
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
    rewardState: com.digitscore.app.model.GoalRewardState = com.digitscore.app.model.GoalRewardState(),
    onUnlockClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showDetailDialog by remember { mutableStateOf(false) }
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

    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 헤더: 타이틀 + 상세 ▸
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = null,
                        tint = if (isAchieved && !isAppExceeded && !isUnlockExceeded) ScoreGreen else ScoreYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isEn) "Today's 3 Key Goals" else "오늘의 3대 실천 목표",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isEn) "Details ▸" else "상세 ▸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 한 줄로 된 3칸 가로 요약 그리드 (Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1칸 (코어 지수): Shield ${currentScore}/${targetScore}P + 달성 시 ✓ (미달 시 주황색 텍스트)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val tint = if (isAchieved) ScoreGreen else ScoreOrange
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${currentScore}/${targetScore}P" + if (isAchieved) " ✓" else "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 2칸 (집중 앱): PhoneAndroid ${targetAppName ?: (if (isEn) "App" else "앱")} ${appMins}/${appLimit}m + 달성 시 ✓ (초과 시 경고색 !)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val appLabel = targetAppName ?: (if (isEn) "App" else "앱")
                        val appStatus = if (isAppExceeded) " !" else if (appMins > 0) " ✓" else ""
                        val tint = if (isAppExceeded) ScoreRed else if (appRatio >= 0.8f) ScoreYellow else ScoreGreen
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        val goalText = com.digitscore.app.i18n.formatGoalMinutesSmallUnit(appMins, appLimit, appStatus)
                        com.digitscore.app.i18n.Text(
                            text = goalText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3칸 (잠금 해제): LockOpen ${unlockCount}/${unlockLimit}x + 달성 시 ✓
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val unlockStatus = if (isUnlockExceeded) " !" else " ✓"
                        val tint = if (isUnlockExceeded) ScoreRed else if (unlockRatio >= 0.8f) ScoreYellow else ScoreGreen
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${unlockCount}/${unlockLimit}x${unlockStatus}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    if (showDetailDialog) {
        ThreeGoalsDetailDialog(
            currentScore = currentScore,
            targetScore = targetScore,
            pointsNeeded = pointsNeeded,
            isAchieved = isAchieved,
            targetAppName = targetAppName,
            appLimit = appLimit,
            appMins = appMins,
            appRatio = appRatio,
            isAppExceeded = isAppExceeded,
            unlockCount = unlockCount,
            unlockLimit = unlockLimit,
            unlockRatio = unlockRatio,
            isUnlockExceeded = isUnlockExceeded,
            rewardState = rewardState,
            onDismiss = { showDetailDialog = false },
            onEditGoals = {
                showDetailDialog = false
                showEditDialog = true
            },
            onShowHelp = { showGoalHelp = true },
            onUnlockClick = {
                showDetailDialog = false
                onUnlockClick()
            }
        )
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
        val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
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
private fun ThreeGoalsDetailDialog(
    currentScore: Int,
    targetScore: Int,
    pointsNeeded: Int,
    isAchieved: Boolean,
    targetAppName: String?,
    appLimit: Int,
    appMins: Int,
    appRatio: Float,
    isAppExceeded: Boolean,
    unlockCount: Int,
    unlockLimit: Int,
    unlockRatio: Float,
    isUnlockExceeded: Boolean,
    rewardState: com.digitscore.app.model.GoalRewardState,
    onDismiss: () -> Unit,
    onEditGoals: () -> Unit,
    onShowHelp: () -> Unit,
    onUnlockClick: () -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = null,
                        tint = if (isAchieved && !isAppExceeded && !isUnlockExceeded) ScoreGreen else ScoreYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isEn) "Today's 3 Key Goals" else "오늘의 3대 실천 목표",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onShowHelp,
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
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 스트릭 & 퍼펙트 스탬프 현황 바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (rewardState.currentStreak > 0) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = ScoreOrange
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (rewardState.currentStreak > 0) (if (isEn) "${rewardState.currentStreak}-Day Streak" else "${rewardState.currentStreak}일 연속 방어 중")
                            else (if (isEn) "Start your streak today!" else "새로운 스트릭에 도전하세요!"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rewardState.currentStreak > 0) ScoreOrange else MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isEn) "Stamps ${rewardState.perfectStamps}/3" else "스탬프 ${rewardState.perfectStamps}/3",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 15분 자유권(Free Pass) 활성화 또는 사용 UI
                if (rewardState.isFreePassActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ScoreGreen.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = ScoreGreen
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEn) "Free Pass Active (${rewardState.remainingFreePassSeconds / 60}min left) — Zero Load"
                                else "15min 자유 시간 진행 중 (${rewardState.remainingFreePassSeconds / 60}min 남음) — 부하 면제",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScoreGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (rewardState.availableFreePasses > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEn) "15min Free Pass (${rewardState.availableFreePasses} available)"
                                    else "15min 자유권 (${rewardState.availableFreePasses}장 보유)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    com.digitscore.app.data.DailyGoalStore.activateFreePass(context)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(if (isEn) "Use" else "사용", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // --- 목표 1: 코어 지수 점수 ---
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                                if (pointsNeeded > 0) "${currentScore}P / ${targetScore}P (+${pointsNeeded}P)"
                                else "${currentScore}P / ${targetScore}P (Achieved ✓)"
                            } else {
                                if (pointsNeeded > 0) "${currentScore}P / 목표 ${targetScore}P (+${pointsNeeded}P 필요)"
                                else "${currentScore}P / 목표 ${targetScore}P (달성 ✓)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pointsNeeded > 0) ScoreYellow else ScoreGreen
                        )
                    }

                    val scoreProgress = (currentScore.toFloat() / 100f).coerceIn(0.02f, 1f)
                    val targetRatio = (targetScore.toFloat() / 100f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { scoreProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .align(Alignment.Center),
                            color = if (isAchieved) ScoreGreen else ScoreYellow,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            strokeCap = StrokeCap.Round
                        )
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                        ) {
                            val markerX = maxWidth * targetRatio
                            Box(
                                modifier = Modifier
                                    .offset(x = (markerX - 1.dp).coerceAtLeast(0.dp))
                                    .width(2.dp)
                                    .height(10.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }

                // --- 목표 2: 관리 앱 1개 시간 제한 ---
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val appTitle = if (isEn) "2. Managed App" else "2. 관리 앱"
                        val noneLabel = if (isEn) " (None)" else " (미지정)"
                        Text(
                            text = appTitle + if (targetAppName != null) " (${targetAppName})" else noneLabel,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (targetAppName != null) {
                                if (isAppExceeded) "${appMins} / ${appLimit}m (+${appMins - appLimit}m !)"
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
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (isAppExceeded) ScoreRed else if (appRatio >= 0.8f) ScoreYellow else ScoreGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round
                    )
                }

                // --- 목표 3: 잠금 해제 횟수 제한 ---
                Surface(
                    onClick = onUnlockClick,
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
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
                                    "${unlockCount} / ${unlockLimit}x (+${unlockCount - unlockLimit}x !)"
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
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (isUnlockExceeded) ScoreRed else if (unlockRatio >= 0.8f) ScoreYellow else ScoreGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onEditGoals,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isEn) "Edit Goals" else "목표 수정", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "닫기")
            }
        }
    )
}

@Composable
private fun EditThreeGoalsDialog(
    currentGoal: com.digitscore.app.model.DailyGoal,
    appsUsage: List<AppUsage>,
    onDismiss: () -> Unit,
    onSave: (scoreTarget: Int, targetPkg: String?, targetAppName: String, appLimitMins: Int, unlockLimit: Int) -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
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
                                text = if (isEn) "Recent ${mins}min used → Target ${selectedAppLimit}min (${reduction}% reduction)"
                                       else "최근 ${mins}min 사용 → 목표 ${selectedAppLimit}min (${reduction}% 절감 제안)",
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
private fun CoreIndexHeroCard(
    score: Int,
    grade: ScoreGrade,
    rollingScore: RollingScoreDetail?,
    samples: List<CoreIndexSampleEntity>,
    guidance: CoreIndexGuidance?,
    onScoreDetailClick: () -> Unit,
    onTrendDetailClick: () -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val gradeText = context.localizedGrade(grade)
    var showChartHelp by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 상단 행 (좌측 게이지 컬럼 + 세로선 + 우측 스파크라인)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 좌측: 게이지 (고정 너비 102.dp, 클릭 시 점수 상세 모달)
                Column(
                    modifier = Modifier
                        .width(102.dp)
                        .clickable(onClick = onScoreDetailClick)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoreIndexGauge(
                        score = score,
                        grade = gradeText,
                        iconSize = 54.dp
                    )
                    val continuousMins = rollingScore?.continuousUsageMinutes ?: 0L
                    val flowText = if (continuousMins > 0L) {
                        if (isEn) "Continuous ${continuousMins}min" else "현재 연속 ${continuousMins}min"
                    } else when (rollingScore?.flow) {
                        ScoreFlow.RECOVERING -> if (isEn) "Resting" else "회복 중"
                        ScoreFlow.CALIBRATING -> if (isEn) "Calibrating" else "보정 중"
                        else -> if (isEn) "Steady" else "유지 중"
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = flowText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }

                // 세로 구분선
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .height(125.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // 우측: 스파크라인 추세 그래프 (Weight 1f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 미니 헤더
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isEn) "Core Trend" else "코어 지수 추세",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { showChartHelp = true },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.HelpOutline,
                                    contentDescription = if (isEn) "Help" else "도움말",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onTrendDetailClick)
                        ) {
                            Text(
                                text = if (isEn) "Stats >" else "상세 >",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // 스파크라인 Canvas
                    CoreIndexSparklineContent(
                        currentScore = score,
                        samples = samples,
                        isEn = isEn
                    )
                }
            }

            // 하단 구분선
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // 하단 1줄 가이드/상태 요약 바
            val adviceText = if (guidance != null) {
                guidanceRecommendationText(guidance)
            } else {
                if (isEn) "Your 24-hour core index is maintaining stability."
                else "최근 24시간 코어 지수가 안정적으로 유지되고 있습니다."
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScoreDetailClick)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = adviceText,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "▸",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }

    if (showChartHelp) {
        AlertDialog(
            onDismissRequest = { showChartHelp = false },
            title = {
                Text(
                    text = if (isEn) "Core Index Trend Guide" else "코어 지수 변화 차트 안내",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = if (isEn) {
                        "• Real-time core index score trend over the rolling 24h/7 days.\n" +
                        "• Vertical lines mark 6-hour boundaries and midnight dates.\n" +
                        "• High and low points show the exact score and time of occurrence."
                    } else {
                        "• 최근 코어 지수의 실시간 변화 흐름을 6시간 간격 및 자정 기준선과 함께 표시합니다.\n" +
                        "• 최고/최저점에 발생 시각(HH:mm)이 함께 기록되어 집중 및 피로 시간대를 쉽게 확인할 수 있습니다."
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
private fun CoreIndexSparklineContent(
    currentScore: Int,
    samples: List<CoreIndexSampleEntity>,
    isEn: Boolean
) {
    val tierColor = coreIndexTierColor(currentScore)
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    if (samples.size < 2) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (isEn) "Live tracking active" else "실시간 추적 진행 중",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEn) "Trend records every 5 minutes" else "5min마다 점수 추세가 기록됩니다",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    } else {
        val outlineColorInt = MaterialTheme.colorScheme.outline.toArgb()
        val onSurfaceColorInt = MaterialTheme.colorScheme.onSurface.toArgb()

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val viewportWidth = maxWidth
            val chartWidth = maxOf(viewportWidth, viewportWidth * 2.2f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(chartWidth)
                        .height(84.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val hPx = size.height
                        val topPadding = 6f
                        val bottomPadding = 16f
                        val chartH = hPx - topPadding - bottomPadding

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
                                strokeWidth = 1.0f.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }

                        if (40.0 in yMin..yMax) {
                            val y40 = yPos(40.0)
                            drawLine(
                                color = ScoreRed.copy(alpha = 0.45f),
                                start = Offset(0f, y40),
                                end = Offset(w, y40),
                                strokeWidth = 1.0f.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }

                        val now = System.currentTimeMillis()
                        val windowStartMillis = now - 24L * 3600_000L
                        val duration = (24L * 3600_000L).toDouble()
                        fun xPos(ts: Long): Float =
                            (((ts - windowStartMillis).toDouble() / duration).coerceIn(0.0, 1.0) * w).toFloat()

                        // X축 3시간 간격 및 자정 날짜 기준선 표시
                        val vertDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        val calAxis = Calendar.getInstance().apply {
                            timeInMillis = windowStartMillis
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            val hour = get(Calendar.HOUR_OF_DAY)
                            val rem = hour % 3
                            add(Calendar.HOUR_OF_DAY, if (rem == 0) 0 else (3 - rem))
                        }
                        val paintAxis = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = outlineColorInt
                            textSize = 8.sp.toPx()
                        }
                        val paintMidnight = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = onSurfaceColorInt
                            textSize = 8.5.sp.toPx()
                            isFakeBoldText = true
                        }
                        while (calAxis.timeInMillis <= now) {
                            val ts = calAxis.timeInMillis
                            val x = xPos(ts)
                            val h = calAxis.get(Calendar.HOUR_OF_DAY)
                            val isMidnight = (h == 0)

                            drawLine(
                                color = if (isMidnight) Color.Gray.copy(alpha = 0.35f) else Color.Gray.copy(alpha = 0.15f),
                                start = Offset(x, topPadding),
                                end = Offset(x, hPx - bottomPadding),
                                strokeWidth = if (isMidnight) 1.2f.dp.toPx() else 0.8f.dp.toPx(),
                                pathEffect = vertDash
                            )

                            if (isMidnight) {
                                val month = calAxis.get(Calendar.MONTH) + 1
                                val day = calAxis.get(Calendar.DAY_OF_MONTH)
                                val dateStr = "$month/$day"
                                drawContext.canvas.nativeCanvas.drawText(
                                    dateStr,
                                    (x - 10.dp.toPx()).coerceIn(2.dp.toPx(), w - 28.dp.toPx()),
                                    hPx - 3.dp.toPx(),
                                    paintMidnight
                                )
                            } else {
                                val hourStr = String.format(Locale.getDefault(), "%02d:00", h)
                                drawContext.canvas.nativeCanvas.drawText(
                                    hourStr,
                                    (x - 12.dp.toPx()).coerceIn(2.dp.toPx(), w - 28.dp.toPx()),
                                    hPx - 3.dp.toPx(),
                                    paintAxis
                                )
                            }
                            calAxis.add(Calendar.HOUR_OF_DAY, 3)
                        }

                        // 샘플 라인 및 영역 그리기
                        val path = Path()
                        val fillPath = Path()

                        samples.forEachIndexed { i, s ->
                            val x = xPos(s.timestampMillis)
                            val y = yPos(s.exactScore)

                            if (i == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, hPx - bottomPadding)
                                fillPath.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }

                        val lastSample = samples.last()
                        val lastX = xPos(lastSample.timestampMillis)
                        fillPath.lineTo(lastX, hPx - bottomPadding)
                        fillPath.close()

                        val areaBrush = Brush.verticalGradient(
                            colors = listOf(
                                ScoreGreen.copy(alpha = 0.22f),
                                ScoreYellow.copy(alpha = 0.10f),
                                ScoreRed.copy(alpha = 0.02f)
                            ),
                            startY = topPadding,
                            endY = hPx - bottomPadding
                        )
                        drawPath(path = fillPath, brush = areaBrush)

                        val lineBrush = Brush.verticalGradient(
                            colors = listOf(ScoreGreen, ScoreYellow, ScoreRed),
                            startY = topPadding,
                            endY = hPx - bottomPadding
                        )
                        drawPath(
                            path = path,
                            brush = lineBrush,
                            style = Stroke(width = 2.0f.dp.toPx(), cap = StrokeCap.Round)
                        )

                        val maxSample = samples.maxByOrNull { it.exactScore }
                        val minSample = samples.minByOrNull { it.exactScore }
                        val paintGreen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = ScoreGreen.toArgb(); textSize = 8.5.sp.toPx(); isFakeBoldText = true
                        }
                        val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = ScoreRed.toArgb(); textSize = 8.5.sp.toPx(); isFakeBoldText = true
                        }
                        val calPoint = Calendar.getInstance()
                        maxSample?.let { s ->
                            val mx = xPos(s.timestampMillis)
                            val my = yPos(s.exactScore)
                            calPoint.timeInMillis = s.timestampMillis
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", calPoint.get(Calendar.HOUR_OF_DAY), calPoint.get(Calendar.MINUTE))
                            drawCircle(ScoreGreen, 2.2.dp.toPx(), Offset(mx, my))
                            drawContext.canvas.nativeCanvas.drawText(
                                if (isEn) "High ${points(s.exactScore)}P ($timeStr)" else "최고 ${points(s.exactScore)}P ($timeStr)",
                                (mx - 38.dp.toPx()).coerceIn(4.dp.toPx(), w - 96.dp.toPx()),
                                (my - 4.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                paintGreen
                            )
                        }
                        minSample?.let { s ->
                            val mx = xPos(s.timestampMillis)
                            val my = yPos(s.exactScore)
                            calPoint.timeInMillis = s.timestampMillis
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", calPoint.get(Calendar.HOUR_OF_DAY), calPoint.get(Calendar.MINUTE))
                            drawCircle(ScoreRed, 2.2.dp.toPx(), Offset(mx, my))
                            drawContext.canvas.nativeCanvas.drawText(
                                if (isEn) "Low ${points(s.exactScore)}P ($timeStr)" else "최저 ${points(s.exactScore)}P ($timeStr)",
                                (mx - 38.dp.toPx()).coerceIn(4.dp.toPx(), w - 96.dp.toPx()),
                                (my + 11.dp.toPx()).coerceAtMost(hPx - bottomPadding - 2.dp.toPx()),
                                paintRed
                            )
                        }

                        val lastY = yPos(lastSample.exactScore)
                        val lastTierColor = coreIndexTierColor(lastSample.exactScore.roundToInt())
                        drawCircle(
                            color = lastTierColor.copy(alpha = 0.25f),
                            radius = 6.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                        drawCircle(
                            color = tierColor,
                            radius = 3.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidanceDetailDialog(
    guidance: CoreIndexGuidance,
    onDismiss: () -> Unit,
    onNavigateToStatistics: () -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEn) "Last 24 Hours Flow" else "최근 24시간 사용 흐름", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightCard(if (isEn) "Why Score Changed" else "지수가 움직인 이유") {
                    Text(guidanceCauseText(guidance), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isEn) {
                            if (guidance.scoreChange > 0) "+${guidance.scoreChange}P compared to previous record."
                            else if (guidance.scoreChange < 0) "-${-guidance.scoreChange}P compared to previous record."
                            else "Score is unchanged, but usage trends continue updating."
                        } else {
                            if (guidance.scoreChange > 0) "직전 기록보다 ${guidance.scoreChange}P 올랐습니다."
                            else if (guidance.scoreChange < 0) "직전 기록보다 ${-guidance.scoreChange}P 낮아졌습니다."
                            else "직전 기록과 같은 점수지만 사용 흐름은 계속 갱신됩니다."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                InsightCard(if (isEn) "Estimated Recovery" else "회복 예상") {
                    Text(
                        guidance.recoveryMinutes?.let {
                            if (isEn) "About ${it}min of screen break may reach ${guidance.recoveryTargetScore}P."
                            else "약 ${it}min 화면을 쉬면 ${guidance.recoveryTargetScore}P로 예상됩니다."
                        } ?: if (isEn) "Recovery depends on accumulated use and screen-off rest; a near-term target is not available."
                            else "회복 속도는 누적 사용과 화면 휴식에 따라 달라집니다. 가까운 회복 시점은 아직 예측하기 어렵습니다.",
                        fontSize = 12.sp
                    )
                }
                InsightCard(if (isEn) "Today's Summary (since 05:00)" else "오늘 요약 (05:00 기준)") {
                    Text(
                        if (isEn) "Screen ${formatMinutesToHoursAndMinutes(guidance.rollingUsageMinutes)} · Unlocks ${guidance.rollingUnlockCount}x"
                        else "화면 ${formatMinutesToHoursAndMinutes(guidance.rollingUsageMinutes)} · 잠금해제 ${guidance.rollingUnlockCount}x",
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
                                previous == null -> "Recent average is ${recent}P. Preparing comparison baseline."
                                else -> "7-day average ${recent}P · ${if (recent >= previous) "+${recent - previous}" else "${recent - previous}"}P vs prior 7 days"
                            }
                        } else {
                            when {
                                recent == null -> "기록이 쌓이면 자신의 지난 사용 흐름과 비교합니다."
                                previous == null -> "최근 기록 평균은 ${recent}P입니다. 이전 비교 기간을 준비하고 있습니다."
                                else -> "최근 7일 평균 ${recent}P · 이전 7일 대비 ${recent - previous}P"
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

@Composable
private fun guidanceCauseText(guidance: CoreIndexGuidance): String {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
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

@Composable
private fun guidanceRecommendationText(guidance: CoreIndexGuidance): String {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    return if (isEn) {
        when (guidance.recommendation) {
            CoreIndexRecommendation.KEEP_BALANCE -> "Keep up this healthy pace and mindfully plan your next check."
            CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK -> "Take a 10min break away from your screen now."
            CoreIndexRecommendation.TAKE_QUIET_BREAK -> "Silence notifications and take a screen-free rest."
            CoreIndexRecommendation.BATCH_PHONE_CHECKS -> "Try batching your next two phone checks into one."
            CoreIndexRecommendation.WIND_DOWN -> "Time to wind down late-night screen time and dim the brightness."
        }
    } else {
        when (guidance.recommendation) {
            CoreIndexRecommendation.KEEP_BALANCE -> "지금의 흐름을 유지하고 다음 확인을 의식적으로 선택해보세요."
            CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK -> "지금 한 번, 10min 동안 화면을 내려놓아 보세요."
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
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            val useWideLayout = maxWidth >= 560.dp
            if (useWideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoreIndexGauge(
                        score = score,
                        grade = gradeText,
                        modifier = Modifier.width(96.dp),
                        iconSize = 48.dp
                    )
                    ScoreGaugeSummary(
                        rollingScore = rollingScore,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoreIndexGauge(
                        score = score,
                        grade = gradeText,
                        modifier = Modifier.width(80.dp),
                        iconSize = 38.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(R.string.digitscore_score)} · ${stringResource(R.string.cumulative_index_label)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ScoreDetailAffordance()
                        }
                        HeroMetric(
                            label = stringResource(R.string.continuous_usage),
                            value = formatMinutesToHoursAndMinutes(rollingScore?.continuousUsageMinutes ?: 0L),
                            modifier = Modifier.fillMaxWidth()
                        )
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
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val screenMinutes = screenTimeMillis / 60_000L
    val distractingMinutes = distractingMillis / 60_000L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatisticCard(
            label = if (isEn) "Screen" else "화면",
            value = com.digitscore.app.i18n.formatMinutesSmallUnit(screenMinutes),
            icon = Icons.Default.PhoneAndroid,
            onClick = onScreenTimeClick,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        StatisticCard(
            label = if (isEn) "Unlocks" else "언락",
            value = "${unlockCount}x",
            icon = Icons.Default.LockOpen,
            onClick = onUnlockClick,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        StatisticCard(
            label = if (isEn) "Managed" else "관리",
            value = com.digitscore.app.i18n.formatMinutesSmallUnit(distractingMinutes),
            icon = Icons.Default.Warning,
            onClick = onDistractingClick,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun StatisticCard(
    label: String,
    value: Any,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                DetailChevron()
            }
            if (value is androidx.compose.ui.text.AnnotatedString) {
                com.digitscore.app.i18n.Text(
                    text = value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            } else {
                Text(
                    text = value.toString(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun AppUsageItemCard(appUsage: AppUsage, onClick: () -> Unit) {
    val categoryColor = appRatingColor(appUsage.categoryType)
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
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
                    text = "${appUsage.categoryType.displayName} · ${if (isEn) "Today" else "오늘"} ${appUsage.sessionCount}x",
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
    return "${safeMinutes}min"
}

// =========================================================================
// 상세 다이얼로그 컴포넌트들
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

    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = if (isEn) "Screen Time (Today since 05:00)" else "오늘 화면 사용 (05:00 기준)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                                text = "${app.categoryType.displayName} · ${if (isEn) "Today" else "오늘"} ${app.sessionCount}x",
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

    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
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

                    // 1. 습관적 순간 열람(7초 이내) vs 실제 앱 사용 분리 분석 카드
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
                                Text(if (isEn) "Threshold: ≤7s" else "기준: 7초 이내", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
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
                                text = if (isEn) "Detected habitual checks where the screen was turned off within 7 seconds without opening apps. Calibrates gradually to your response speed."
                                else "앱을 켜지 않고 7초 이내에 화면을 끈 습관적 확인을 감지했습니다. 개인 세션 반응 속도를 학습하여 점진적으로 보정합니다.",
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
                                    text = if (isEn) "Use your past record (${value.past14DaysLowestUnlock}x) as a benchmark to gradually reduce daily unlock goals."
                                    else "과거 최저 기록(${value.past14DaysLowestUnlock}x)을 바닥선으로 삼아 일일 목표를 점진적으로 낮춰보세요.",
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
                                    if (isEn) "Most active interval is ${rollingHourLabel(value.windowStartMillis, peak.index)} · ${peak.value}x unlocks."
                                    else "가장 잦은 구간은 ${rollingHourLabel(value.windowStartMillis, peak.index)} · ${peak.value}x입니다.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            value.averageIntervalMinutes?.let { interval ->
                                Text(
                                    if (isEn) "Unlocked once every ~${interval.coerceAtLeast(1)}min on average."
                                    else "평균 약 ${interval.coerceAtLeast(1)}min마다 한 번 열었습니다.",
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
                                    if (isEn) "OS Notifications: ${value.notificationCount} · Unlocks: ${unlockCount}x"
                                    else "OS 감지 알림 ${value.notificationCount}건 · 언락 ${unlockCount}x",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                CompactBarChart(
                                    values = listOf(value.notificationCount.toFloat(), unlockCount.toFloat()),
                                    barColor = MaterialTheme.colorScheme.primary,
                                    contentDescription = UiTranslator.translate(
                                        "알림 ${value.notificationCount}건과 언락 ${unlockCount}x 비교 그래프"
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    Text(if (isEn) "Notifications" else "알림", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(if (isEn) "Unlocks" else "언락", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                val comparison = when {
                                    value.notificationCount > 0 -> {
                                        val ratio = unlockCount.toFloat() / value.notificationCount
                                        if (isEn) "Unlocked ~${String.format(Locale.US, "%.1f", ratio)}x per notification."
                                        else "알림 1건당 약 ${String.format(Locale.US, "%.1f", ratio)}x 언락했습니다."
                                    }
                                    unlockCount > 0 -> if (isEn) "Unlocked ${unlockCount}x without any detected notifications."
                                        else "감지된 알림 없이도 ${unlockCount}x 언락했습니다."
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
                            text = if (isEn) "Today's unlock counts are factored smoothly into core score usage load."
                            else "오늘 언락 횟수는 코어 지수의 사용 부하에 완만하게 반영됩니다.",
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
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
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
                            text = if (isEn) "No managed apps used today. Great balance!" else "오늘 사용된 관리 대상 앱이 없습니다. 안정적인 사용 흐름입니다.",
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
                                    text = if (isEn) "Today ${app.sessionCount}x · Habitual ${app.shortSessionCount}x"
                                    else "오늘 ${app.sessionCount}x · 습관성 확인 ${app.shortSessionCount}x",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (app.lateNightUsageMinutes > 0) {
                                    Text(
                                        text = if (isEn) "Late night ${app.lateNightUsageMinutes}min" else "심야 사용 ${app.lateNightUsageMinutes}min",
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
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (isEn) "All Apps (Today since 05:00)" else "오늘 전체 앱 목록 (05:00 기준)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                                text = "${app.categoryType.displayName} · ${if (isEn) "Today" else "오늘"} ${app.sessionCount}x",
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
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
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
                                Text(text = if (isEn) "Today's Use (since 05:00)" else "오늘 사용 (05:00 기준)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
                                        text = "${appUsage.lateNightUsageMinutes}min",
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
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val busiest = insights.hourlyUsageMillis.withIndex()
        .filter { it.value >= 10_000L }
        .sortedByDescending { it.value }
        .take(3)
    InsightCard(if (isEn) "When was it used today?" else "오늘 언제 많이 사용했나요?") {
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
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val sessions = insights.sessionDurationsMillis
    val average = sessions.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
    val longest = sessions.maxOrNull() ?: 0L
    val shortOpens = sessions.count { it in 1 until com.digitscore.app.data.HABITUAL_CHECK_THRESHOLD_MILLIS }
    val assessment = if (isEn) {
        when {
            longest >= 30 * 60_000L -> "Sessions exceeding 30 min detected."
            longest >= 15 * 60_000L -> "Moderately long continuous usage detected."
            sessions.isNotEmpty() -> "Mostly split into brief sessions."
            else -> "No session records yet."
        }
    } else {
        when {
            longest >= 30 * 60_000L -> "한 번에 30min 이상 이어진 사용이 있습니다."
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
                Text(if (isEn) "Orange: ≥30min" else "30min 이상은 주황색", fontSize = 10.sp, color = ScoreOrange)
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
                if (isEn) "Opened ${sessions.size}x today, with ${shortOpens}x habitual checks."
                else "오늘 ${sessions.size}x 열었고, 그중 습관성 확인은 ${shortOpens}x입니다.",
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
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val days = insights.dailyUsage.takeLast(14)
    InsightCard(if (isEn) "How often was it opened by date?" else "날짜별로 몇 번 열었나요?") {
        val rollingSessions = insights.sessionDurationsMillis
        val rollingShortCount = rollingSessions.count { it in 1 until com.digitscore.app.data.HABITUAL_CHECK_THRESHOLD_MILLIS }
        Text(
            if (isEn) "Today: ${rollingSessions.size}x opens · ${rollingShortCount}x habitual checks"
            else "오늘 ${rollingSessions.size}x · 습관성 확인 ${rollingShortCount}x",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (rollingSessions.size >= 5) {
            val shortRatio = (rollingShortCount * 100f / rollingSessions.size).roundToInt()
            if (shortRatio >= 60) {
                Text(
                    if (isEn) "Habitual checks account for ${shortRatio}% of opens. Mindful habit check recommended."
                    else "습관성 확인이 전체 실행의 ${shortRatio}%입니다. 무의식적 실행 흐름인지 살펴보세요.",
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
                    contentDescription = UiTranslator.translate("최근 14일 앱 실행 횟수와 습관성 확인 그래프")
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
            Text(if (isEn) "Orange: Habitual" else "주황색 · 습관성 확인", fontSize = 10.sp, color = ScoreOrange)
        }
    }
}

@Composable
private fun AppTrendInsight(insights: AppUsageInsights) {
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
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
        minutes > 0 -> "${minutes}min"
        safeMillis > 0 -> "${(safeMillis / 1_000L).coerceAtLeast(1)}s"
        else -> "0min"
    }
}

@Composable
private fun appRatingColor(category: AppCategoryType): Color = when (category.canonical) {
    AppCategoryType.EXEMPT -> Color(0xFF29B6F6)
    AppCategoryType.NEUTRAL -> MaterialTheme.colorScheme.outline
    AppCategoryType.DISTRACTING -> ScoreRed
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun TopNavActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

