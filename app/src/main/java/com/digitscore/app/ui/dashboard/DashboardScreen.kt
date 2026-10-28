package com.digitscore.app.ui.dashboard

import com.digitscore.app.ui.theme.tile
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.ScoreRedText
import com.digitscore.app.ui.theme.ScoreOrangeText
import com.digitscore.app.ui.theme.ScoreYellowText
import com.digitscore.app.ui.theme.ScoreGreenText
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.style.TextAlign
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.data.entity.CoreIndexSampleEntity
import com.digitscore.app.data.ScreenTimeReconciliationCalculator
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
import com.digitscore.app.i18n.coachActionText
import com.digitscore.app.i18n.UiTranslator
import com.digitscore.app.data.AppUsageInsights
import com.digitscore.app.data.UnlockInsights
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.model.ScoreTier
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
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.ui.text.AnnotatedString
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

    // 외부 Intent(첫 잠금 해제 / 헤드업 탭)로부터 강제 호출 시 즉시 팝업 트리거
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
        // 설치 첫날처럼 어제 기록이 없으면 빈 브리핑을 띄우지 않습니다 (목표는 자동 지정).
        if (currentGoal != null && currentHour in 5..11 &&
            com.digitscore.app.data.DailyGoalStore.hasYesterdayBaseline(summary)
        ) {
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


    // 서비스의 첫 계산 전에는 기본 75점 대신 마지막으로 저장된 점수를 보여줍니다.
    val lastSavedScore = remember { com.digitscore.app.widget.WidgetSnapshotStore.read(context)?.score }
    val currentScore = rollingScoreDetail?.finalScore ?: lastSavedScore
        ?: com.digitscore.app.engine.CumulativeScoreConfig.CURRENT.initialScore.toInt()
    val grade = ScoreTier.fromScore(currentScore)

    // 쉬는 동안 오른 P: 화면을 다시 켠 뒤 첫 진입에 한 번만 보여 줍니다.
    val restGain = remember(rollingSamples, currentScore) {
        com.digitscore.app.data.RestGainNotice.latest(
            rollingSamples, rollingScoreDetail?.exactScore ?: currentScore.toDouble(), System.currentTimeMillis()
        )
    }
    val restGainPrefs = remember { context.getSharedPreferences("dashboard_notices", android.content.Context.MODE_PRIVATE) }
    val restGainPoints = remember(restGain?.restStartMillis) {
        restGain?.takeIf { restGainPrefs.getLong("rest_gain_shown_start", 0L) != it.restStartMillis }?.points
    }
    LaunchedEffect(restGain?.restStartMillis) {
        restGain?.let { restGainPrefs.edit().putLong("rest_gain_shown_start", it.restStartMillis).apply() }
    }

    // 평소 이 시간 비교: 최근 14일 같은 시각 지수 중간값 (표본 7일 미만이면 null)
    val usualDiff by produceState<Int?>(initialValue = null, currentScore) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                com.digitscore.app.data.UsualScoreComparison.difference(
                    db.coreIndexSampleDao().getSince(now - 15 * 24 * 3_600_000L),
                    rollingScoreDetail?.exactScore ?: currentScore.toDouble(),
                    now
                )
            }.getOrNull()
        }
    }

    // 최근 24시간 지수 변화의 원인별 분해 (엔진이 남긴 시간별 감점·회복을 더함, 비례 배분 없음)
    val indexBreakdown by produceState(
        initialValue = com.digitscore.app.data.CoreIndexBreakdown.EMPTY,
        currentScore, unlockCount, appsUsage
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val end = System.currentTimeMillis()
                val startHour = com.digitscore.app.data.statisticHour(end) - 23 * com.digitscore.app.data.STAT_HOUR
                val managed = appsUsage
                    .filter { it.categoryType.canonical == com.digitscore.app.model.AppCategoryType.DISTRACTING }
                    .map { it.packageName }.toSet()
                com.digitscore.app.data.CoreIndexBreakdown.from(
                    db.statisticsDao().impactsBetween(startHour, end + com.digitscore.app.data.STAT_HOUR)
                ) { it in managed }
            }.getOrDefault(com.digitscore.app.data.CoreIndexBreakdown.EMPTY)
        }
    }

    val timeRules = remember { com.digitscore.app.data.TimeRuleStore.getRules(context) }
    var currentTime by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            currentTime = java.time.LocalDateTime.now()
        }
    }
    val activeTimeRule = remember(timeRules, currentTime.minute, currentTime.hour, currentTime.dayOfWeek) {
        com.digitscore.app.engine.TimeRuleEngine.getActiveRule(timeRules, currentTime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = DigitsTextSize.headline
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
                        com.digitscore.app.ui.components.LabeledTopAction(
                            icon = Icons.AutoMirrored.Rounded.TrendingUp,
                            label = if (isEn) "Stats" else "통계",
                            contentDescription = if (isEn) "Statistics" else "상세 통계",
                            onClick = onNavigateToStatistics
                        )
                        com.digitscore.app.ui.components.LabeledTopAction(
                            icon = Icons.Rounded.Flag,
                            label = if (isEn) "Goals" else "목표",
                            contentDescription = if (isEn) "Today's goals" else "오늘의 목표",
                            onClick = { showEditGoalsFromTopBar = true }
                        )
                        com.digitscore.app.ui.components.LabeledTopAction(
                            icon = Icons.Rounded.Apps,
                            label = if (isEn) "Apps" else "앱",
                            contentDescription = if (isEn) "App usage & grades" else "앱별 사용량 및 등급",
                            onClick = onNavigateToAppSettings
                        )
                        com.digitscore.app.ui.components.LabeledTopAction(
                            icon = Icons.Rounded.Tune,
                            label = if (isEn) "Settings" else "설정",
                            contentDescription = if (isEn) "Settings" else "환경설정",
                            onClick = onNavigateToPresetSettings
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 0. 활성 시간대 규칙 안내 줄
                if (activeTimeRule != null) {
                    item {
                        val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = DigitsShape.xs,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.HourglassBottom,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${activeTimeRule.displayName(isEn)} · ${if (isEn) "Lighting" else "신호등"} ${activeTimeRule.lightingMinutes}min",
                                        fontSize = DigitsTextSize.caption,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. 코어 지수 게이지 + 최근 7일 스파크라인을 결합한 통합 히어로 카드
                item {
                    CoreIndexHeroCard(
                        score = currentScore,
                        grade = grade,
                        rollingScore = rollingScoreDetail,
                        samples = rollingSamples,
                        guidance = guidance,
                        usualDiff = usualDiff,
                        restGainPoints = restGainPoints,
                        onScoreDetailClick = { showScoreDetailModal = true },
                        onTrendDetailClick = onNavigateToStatistics
                    )
                }

                // 3. 요약 3칸: 최근 24시간 지수를 움직인 원인 (앱 사용 · 잠금 해제 · 쉼, ±P)
                item {
                    ScoreStatsRow(
                        breakdown = indexBreakdown,
                        longestRest = remember(rollingSamples) {
                            val now = System.currentTimeMillis()
                            // 오늘(05:00 이후) 기준: 밤잠이 '가장 긴 쉼'을 차지하지 않도록 합니다.
                            com.digitscore.app.data.LongestRest.find(rollingSamples, com.digitscore.app.data.LongestRest.todayStartMillis(now), now)
                        },
                        screenTimeMillis = rollingUsageSummary.totalScreenTimeMillis,
                        unlockCount = unlockCount,
                        distractingMillis = rollingUsageSummary.managedTimeMillis,
                        onScreenTimeClick = { showScreenTimeModal = true },
                        onUnlockClick = { showUnlockModal = true },
                        onDistractingClick = { showDistractingModal = true },
                        onRestClick = { showScoreDetailModal = true }
                    )
                }

                // 4. 3대 실질 목표 관리 카드 (코어 지수, 앱 1개 제한, 잠금 해제 횟수)
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
                    TopAppsGradeCheckCard(onOpenAppSettings = { onNavigateToAppSettings() })
                }

                item {
                    HabitReviewCard(
                        score = currentScore,
                        apps = appsUsage,
                        dailyGoal = dailyGoal,
                        yesterdaySummary = yesterdaySummary
                    )
                }



                // 4. 앱별 사용 시간 요약 카드 (탭 시 앱별 사용량 및 등급 화면으로 이동)
                item {
                    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAppSettings() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = DigitsShape.md
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
                                    text = if (isEn) "App Usage" else "앱별 사용 시간",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                com.digitscore.app.ui.components.MoreLink(
                                    text = if (isEn) "All (${appsUsage.size})" else "전체 ${appsUsage.size}개",
                                    onClick = { onNavigateToAppSettings() }
                                )
                            }

                            if (appsUsage.isEmpty()) {
                                Text(
                                    text = if (isEn) "No usage records yet." else "아직 사용 기록이 없습니다.",
                                    fontSize = DigitsTextSize.label,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                // 이름이 없는 시스템 구성요소(패키지명만 있는 앱)는 요약에서 뺍니다.
                                // 지수에 준 영향(−P)이 큰 앱부터 보여 줍니다(분해 기록이 없으면 사용 시간 순).
                                val impactByPkg = indexBreakdown.perApp.toMap()
                                val topApps = appsUsage.filter { it.appName.isNotBlank() && it.appName != it.packageName }
                                    .sortedBy { impactByPkg[it.packageName] ?: 0.0 }
                                    .take(3)
                                // 한 앱 = 한 줄: 이름(줄어듦) · 사용 시간 · 지수 효과(고정 폭)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    topApps.forEach { app ->
                                        val mins = (app.usageTimeMillis / 60_000L).coerceAtLeast(1L)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.tile, DigitsShape.sm)
                                                .clickable { selectedAppDetail = app }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = app.appName,
                                                fontSize = DigitsTextSize.label,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            com.digitscore.app.i18n.Text(
                                                text = com.digitscore.app.i18n.formatMinutesSmallUnit(mins),
                                                fontSize = DigitsTextSize.label,
                                                fontWeight = FontWeight.Bold,
                                                color = appRatingColor(app.categoryType),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            val impact = impactByPkg[app.packageName]
                                            Text(
                                                text = (if (isEn) "Index " else "지수 ") + formatPoints(impact ?: 0.0),
                                                fontSize = DigitsTextSize.caption,
                                                fontWeight = FontWeight.Bold,
                                                color = impact?.let { pointsColor(it) } ?: MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                softWrap = false,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.widthIn(min = 64.dp)
                                            )
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

    // 3. 잠금 해제 횟수 세부 통계 다이얼로그
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

    // 4. 관리 앱 세부 분석 다이얼로그
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
            onGradeChanged = { newGrade ->
                scope.launch(Dispatchers.IO) {
                    // 등급(DB)과 공부 앱 목록을 함께 갱신하고, 추적 중일 때만 재계산합니다.
                    com.digitscore.app.data.AppGradeStore.apply(context, app.packageName, app.appName, newGrade)
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
                    com.digitscore.app.data.CumulativeScoreStore.confirmWake(db, System.currentTimeMillis())
                    TrackerForegroundService.refreshNotification(context)
                    com.digitscore.app.notification.ScoreNotificationManager.cancelMorningBriefingNotification(context)
                }
                showMorningDialog = false
                onMorningBriefingHandled()
            },
            onSkipGoal = {
                scope.launch {
                    com.digitscore.app.data.DailyGoalStore.markBriefingCompleted(context)
                    com.digitscore.app.data.CumulativeScoreStore.confirmWake(db, System.currentTimeMillis())
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
            currentScore = currentScore,
            todayUnlocks = unlockCount,
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
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    var showDetailDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showGoalHelp by remember { mutableStateOf(false) }
    var goalDetail by remember { mutableStateOf<GoalKind?>(null) }

    // 1. 지수 방어선 (하루 종일 고정된 절대 타겟 점수)
    val targetScore = dailyGoal?.scoreTarget ?: 70
    val pointsNeeded = (targetScore - currentScore).coerceAtLeast(0)
    val isAchieved = currentScore >= targetScore

    // 2. 관리 앱 제한 목표 (앱 1개 또는 관리 앱 전체)
    val isAllManaged = dailyGoal?.isAllManagedApps == true
    val targetAppName = if (isAllManaged) (if (isEn) "All managed apps" else "관리 앱 전체") else dailyGoal?.targetAppName?.ifBlank { null }
    val appLimit = dailyGoal?.appLimitMinutes ?: 30
    val appMins = dailyGoal?.currentAppUsageMinutes ?: 0
    val appRatio = if (appLimit > 0) (appMins.toFloat() / appLimit.toFloat()) else 0f
    val isAppExceeded = appMins > appLimit

    // 3. 잠금 해제 횟수 제한 목표
    val unlockLimit = dailyGoal?.unlockLimitTarget ?: 100
    val unlockRatio = (unlockCount.toFloat() / unlockLimit.toFloat())
    val isUnlockExceeded = unlockCount > unlockLimit

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = DigitsShape.lg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 헤더: 타이틀 + 상세 (MoreLink)
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
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = if (isAchieved && !isAppExceeded && !isUnlockExceeded) ScoreGreenText else ScoreYellowText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isEn) "Today's goals" else "오늘의 목표",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { showGoalHelp = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = if (isEn) "About today's goals" else "오늘의 목표 안내",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    com.digitscore.app.ui.components.MoreLink(text = if (isEn) "Details" else "상세")
                }
            }

            // 한 줄로 된 3칸 가로 요약 그리드 (Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1칸 (코어 지수): Shield ${currentScore}/${targetScore}P + 달성 시 체크 아이콘 (미달 시 주황색 텍스트)
                Surface(
                    onClick = { goalDetail = GoalKind.SCORE },
                    shape = DigitsShape.sm,
                    color = MaterialTheme.colorScheme.tile,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val tint = if (isAchieved) ScoreGreenText else ScoreOrangeText
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${currentScore}/${targetScore}P",
                            fontSize = DigitsTextSize.caption,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            maxLines = 1,
                            softWrap = false
                        )
                        com.digitscore.app.ui.components.GoalStatusMark(achieved = isAchieved, exceeded = false, tint = tint)
                    }
                }

                // 2칸 (관리할 앱): PhoneAndroid ${targetAppName ?: (if (isEn) "App" else "앱")} ${appMins}/${appLimit}m + 달성 시 체크·초과 시 느낌표 아이콘
                Surface(
                    onClick = { goalDetail = GoalKind.APP },
                    shape = DigitsShape.sm,
                    color = MaterialTheme.colorScheme.tile,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val appLabel = targetAppName ?: (if (isEn) "App" else "앱")
                        val tint = if (isAppExceeded) ScoreRedText else if (appRatio >= 0.8f) ScoreYellowText else ScoreGreenText
                        Icon(
                            imageVector = Icons.Rounded.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        val goalText = com.digitscore.app.i18n.formatGoalMinutesSmallUnit(appMins, appLimit)
                        com.digitscore.app.i18n.Text(
                            text = goalText,
                            fontSize = DigitsTextSize.caption,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            maxLines = 1,
                            softWrap = false
                        )
                        com.digitscore.app.ui.components.GoalStatusMark(achieved = !isAppExceeded && appMins > 0, exceeded = isAppExceeded, tint = tint)
                    }
                }

                // 3칸 (잠금 해제): LockOpen ${unlockCount}/${unlockLimit}회 + 달성 시 체크 아이콘
                Surface(
                    onClick = { goalDetail = GoalKind.UNLOCK },
                    shape = DigitsShape.sm,
                    color = MaterialTheme.colorScheme.tile,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val tint = if (isUnlockExceeded) ScoreRedText else if (unlockRatio >= 0.8f) ScoreYellowText else ScoreGreenText
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${unlockCount}/${unlockLimit}${com.digitscore.app.i18n.countUnit()}",
                            fontSize = DigitsTextSize.caption,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            maxLines = 1,
                            softWrap = false
                        )
                        com.digitscore.app.ui.components.GoalStatusMark(achieved = !isUnlockExceeded, exceeded = isUnlockExceeded, tint = tint)
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
            onGoalClick = { goalDetail = it }
        )
    }

    goalDetail?.let { kind ->
        GoalItemDetailDialog(
            kind = kind,
            currentScore = currentScore,
            targetScore = targetScore,
            appName = targetAppName,
            appPackage = dailyGoal?.targetPackageName?.ifBlank { null },
            appMins = appMins,
            appLimit = appLimit,
            unlockCount = unlockCount,
            unlockLimit = unlockLimit,
            onDismiss = { goalDetail = null },
            onOpenUnlockAnalysis = {
                goalDetail = null
                showDetailDialog = false
                onUnlockClick()
            }
        )
    }

    if (showEditDialog) {
        EditThreeGoalsDialog(
            currentGoal = dailyGoal ?: com.digitscore.app.model.DailyGoal(dateString = com.digitscore.app.data.DailyGoalStore.getLogicalDateString()),
            appsUsage = appsUsage,
            currentScore = currentScore,
            todayUnlocks = unlockCount,
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
            title = { Text(if (isEn) "About today's goals" else "오늘의 목표 안내", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.title) },
            text = {
                Text(
                    if (isEn) {
                        "Three goals for one day (05:00 to 05:00 the next morning).\n\n" +
                        "1. Score floor: keep the day's last Core Index at or above the floor.\n" +
                        "2. App limit: keep one chosen app within its time limit.\n" +
                        "3. Unlock limit: keep the number of unlocks within the limit.\n\n" +
                        "• Usage time and unlocks start from 0 at 05:00. The Core Index is not reset.\n" +
                        "• Meeting all three earns a stamp; 3 stamps give a streak shield (max 2). A shield is used automatically to keep your streak on a missed day.\n" +
                        "• Your salamander grows at 7, 14 and 30 streak days and shrinks one stage when the streak breaks.\n" +
                        "• Tap each goal to see its criterion, tips and the last 7 days."
                    } else {
                        "하루(05:00부터 다음 날 05:00까지) 동안 지킬 목표 3가지입니다.\n\n" +
                        "1. 지수 방어선: 하루 마지막 코어 지수를 방어선 이상으로 유지합니다.\n" +
                        "2. 관리할 앱 한도: 고른 앱 1개의 사용 시간을 한도 안으로 유지합니다.\n" +
                        "3. 잠금 해제 한도: 잠금 해제 횟수를 한도 안으로 유지합니다.\n\n" +
                        "• 사용 시간과 잠금 해제 횟수는 05:00에 0부터 다시 셉니다. 코어 지수는 초기화되지 않습니다.\n" +
                        "• 3가지를 모두 지키면 스탬프 1개, 스탬프 3개면 보호권 1장(최대 2장)을 받습니다. 연속 기록이 끊기는 날 보호권이 자동으로 쓰여 기록을 지켜 줍니다.\n" +
                        "• 도룡뇽은 연속 7·14·30일에 한 단계씩 자라고, 보호권 없이 기록이 끊기면 한 단계 작아집니다.\n" +
                        "• 각 목표를 누르면 기준, 달성 방법, 최근 7일 기록을 볼 수 있습니다."
                    },
                    fontSize = DigitsTextSize.body,
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
    onGoalClick: (GoalKind) -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    // 자유권 사용 직후 서비스 갱신(최대 1분)을 기다리지 않고 즉시 반영하고, 남은 시간을 매초 갱신합니다.
    var liveReward by remember(rewardState) { mutableStateOf(rewardState) }
    var freePassNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(liveReward.activeFreePassUntil) {
        while (liveReward.activeFreePassUntil > System.currentTimeMillis()) {
            freePassNow = System.currentTimeMillis()
            delay(1_000L)
        }
        freePassNow = System.currentTimeMillis()
    }
    val freePassActive = liveReward.activeFreePassUntil > freePassNow
    val freePassRemainingMinutes = ((liveReward.activeFreePassUntil - freePassNow).coerceAtLeast(0L) + 59_999L) / 60_000L

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
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = if (isAchieved && !isAppExceeded && !isUnlockExceeded) ScoreGreenText else ScoreYellowText,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isEn) "Today's goals" else "오늘의 목표",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                // 연속 달성 & 퍼펙트 스탬프 현황 바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tile, DigitsShape.sm)
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
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = ScoreOrangeText
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (rewardState.currentStreak > 0) (if (isEn) "${rewardState.currentStreak}-Day Streak" else "${rewardState.currentStreak}일 연속 방어 중")
                            else (if (isEn) "Start your streak today!" else "오늘부터 연속 달성을 시작해 보세요."),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = DigitsTextSize.caption,
                            fontWeight = FontWeight.Bold,
                            color = if (rewardState.currentStreak > 0) ScoreOrangeText else MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isEn) "Stamps ${rewardState.perfectStamps}/3" else "스탬프 ${rewardState.perfectStamps}/3",
                            fontSize = DigitsTextSize.caption,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 15분 자유권(Free Pass) 활성화 또는 사용 UI
                if (freePassActive) {
                    Surface(
                        shape = DigitsShape.sm,
                        color = ScoreGreen.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ConfirmationNumber,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = ScoreGreenText
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEn) "Free Pass Active (${freePassRemainingMinutes}min left) — Zero Load"
                                else "15min 자유권 진행 중 (${freePassRemainingMinutes}min 남음) — 감점 없음",
                                fontSize = DigitsTextSize.caption,
                                fontWeight = FontWeight.Bold,
                                color = ScoreGreenText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                val personalBest = remember(liveReward) { com.digitscore.app.data.DailyGoalStore.getPersonalBest(context) }
                StreakRewardRow(reward = liveReward, best = personalBest, isEn = isEn)

                // --- 목표 1: 코어 지수 점수 ---
                Column(
                    modifier = Modifier.clip(DigitsShape.sm).clickable { onGoalClick(GoalKind.SCORE) },
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
                                text = if (isEn) "1. Score floor" else "1. 지수 방어선",
                                fontSize = DigitsTextSize.label,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            DetailChevron()
                        }
                        Text(
                            text = if (isEn) {
                                if (pointsNeeded > 0) "${currentScore}P / ${targetScore}P (+${pointsNeeded}P)"
                                else "${currentScore}P / ${targetScore}P (achieved)"
                            } else {
                                if (pointsNeeded > 0) "${currentScore}P / 목표 ${targetScore}P (+${pointsNeeded}P 필요)"
                                else "${currentScore}P / 목표 ${targetScore}P (달성)"
                            },
                            fontSize = DigitsTextSize.label,
                            fontWeight = FontWeight.Medium,
                            color = if (pointsNeeded > 0) ScoreYellowText else ScoreGreenText
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
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), DigitsShape.bar)
                            )
                        }
                    }
                }

                // --- 목표 2: 관리 앱 1개 시간 제한 ---
                Column(
                    modifier = Modifier.clip(DigitsShape.sm).clickable { onGoalClick(GoalKind.APP) },
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val appTitle = if (isEn) "2. App limit" else "2. 관리할 앱 한도"
                        val noneLabel = if (isEn) " (None)" else " (미지정)"
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = appTitle + if (targetAppName != null) " (${targetAppName})" else noneLabel,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = DigitsTextSize.label,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            DetailChevron()
                        }
                        Text(
                            text = if (targetAppName != null) {
                                if (isAppExceeded) "${appMins} / ${appLimit}min (+${appMins - appLimit}min)"
                                else "${appMins} / ${appLimit}min (${(appRatio * 100).toInt()}%)"
                            } else {
                                if (isEn) "Select an app" else "앱 선택 필요"
                            },
                            fontSize = DigitsTextSize.label,
                            fontWeight = FontWeight.Medium,
                            color = if (targetAppName == null) MaterialTheme.colorScheme.outline
                            else if (isAppExceeded) ScoreRedText
                            else if (appRatio >= 0.8f) ScoreYellowText
                            else ScoreGreenText
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
                    onClick = { onGoalClick(GoalKind.UNLOCK) },
                    shape = DigitsShape.sm,
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
                                    text = if (isEn) "3. Unlock limit" else "3. 잠금 해제 한도",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                DetailChevron()
                            }
                            Text(
                                text = if (isUnlockExceeded) {
                                    "${unlockCount} / ${unlockLimit}${com.digitscore.app.i18n.countUnit()} (+${unlockCount - unlockLimit}${com.digitscore.app.i18n.countUnit()})"
                                } else {
                                    "${unlockCount} / ${unlockLimit}${com.digitscore.app.i18n.countUnit()} (${(unlockRatio * 100).toInt()}%)"
                                },
                                fontSize = DigitsTextSize.label,
                                fontWeight = FontWeight.Medium,
                                color = if (isUnlockExceeded) ScoreRedText else if (unlockRatio >= 0.8f) ScoreYellowText else ScoreGreenText
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
                shape = DigitsShape.sm
            ) {
                Text(if (isEn) "Edit Goals" else "목표 수정", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.label)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "닫기")
            }
        }
    )
}

/** 목표 설정 다이얼로그에 쓰는 최근 7일(오늘 제외) 기록 */
private data class GoalHistory(val finalScores: List<Int> = emptyList(), val unlocks: List<Int> = emptyList(), val loaded: Boolean = false)

@Composable
private fun EditThreeGoalsDialog(
    currentGoal: com.digitscore.app.model.DailyGoal,
    appsUsage: List<AppUsage>,
    currentScore: Int,
    todayUnlocks: Int,
    onDismiss: () -> Unit,
    onSave: (scoreTarget: Int, targetPkg: String?, targetAppName: String, appLimitMins: Int, unlockLimit: Int) -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    var selectedPkg by remember { mutableStateOf(currentGoal.targetPackageName) }
    var selectedAppName by remember { mutableStateOf(currentGoal.targetAppName) }

    // 최근 7일(오늘 제외) 마감 점수·잠금 해제 횟수
    val history by androidx.compose.runtime.produceState(GoalHistory()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val today = com.digitscore.app.data.DailyGoalStore.getLogicalDateString()
            val rows = com.digitscore.app.data.DigitsDatabase.getInstance(context).scoreDao()
                .getRecentCoreIndexHistories(10).filter { it.dateString < today }.take(7)
            GoalHistory(rows.map { it.finalScore }, rows.map { it.unlockCount }, loaded = true)
        }
    }
    // 고른 앱 또는 관리 앱 전체의 최근 7일(오늘 제외) 하루 사용 시간
    val appHistory by androidx.compose.runtime.produceState<List<Int>?>(null, selectedPkg) {
        val pkg = selectedPkg
        value = if (pkg == null) emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val today = java.time.LocalDate.parse(com.digitscore.app.data.DailyGoalStore.getLogicalDateString())
            val db = com.digitscore.app.data.DigitsDatabase.getInstance(context)
            if (pkg == com.digitscore.app.model.DailyGoal.PACKAGE_ALL_MANAGED) {
                val allRows = db.dailyAppUsageDao().getAll()
                    .filter { it.dateString >= today.minusDays(7).toString() && it.dateString < today.toString() && it.categoryLevel >= 3 }
                allRows.groupBy { it.dateString }.values.map { dayList ->
                    dayList.sumOf { (it.usageMillis / 60_000L).toInt() }
                }
            } else {
                db.dailyAppUsageDao()
                    .getForPackageSince(pkg, today.minusDays(7).toString())
                    .filter { it.dateString < today.toString() }
                    .map { (it.usageMillis / 60_000L).toInt() }
            }
        }
    }
    val todayAppMinutes = if (selectedPkg == com.digitscore.app.model.DailyGoal.PACKAGE_ALL_MANAGED) {
        (appsUsage.filter { it.categoryType.canonical == com.digitscore.app.model.AppCategoryType.DISTRACTING }.sumOf { it.usageTimeMillis } / 60_000L).toInt()
    } else {
        ((appsUsage.firstOrNull { it.packageName == selectedPkg }?.usageTimeMillis ?: 0L) / 60_000L).toInt()
    }

    val scoreSpec = remember(history, currentScore) { com.digitscore.app.model.GoalRanges.score(currentScore, history.finalScores) }
    val unlockSpec = remember(history, todayUnlocks) { com.digitscore.app.model.GoalRanges.unlockLimit(todayUnlocks, history.unlocks) }
    val appSpec = remember(appHistory, todayAppMinutes) { com.digitscore.app.model.GoalRanges.appLimit(todayAppMinutes, appHistory ?: emptyList()) }

    var selectedScore by remember { mutableIntStateOf(scoreSpec.initial(currentGoal.scoreTarget)) }
    var selectedUnlockLimit by remember { mutableIntStateOf(unlockSpec.initial(currentGoal.unlockLimitTarget)) }
    var selectedAppLimit by remember { mutableIntStateOf(appSpec.initial(currentGoal.appLimitMinutes)) }
    var scoreTouched by remember { mutableStateOf(false) }
    var unlockTouched by remember { mutableStateOf(false) }
    var appTouched by remember { mutableStateOf(false) }
    // 기록을 읽고 범위가 정해지면 아직 손대지 않은 값만 새 범위에 맞춥니다.
    LaunchedEffect(scoreSpec) { if (!scoreTouched) selectedScore = scoreSpec.initial(currentGoal.scoreTarget) }
    LaunchedEffect(unlockSpec) { if (!unlockTouched) selectedUnlockLimit = unlockSpec.initial(currentGoal.unlockLimitTarget) }
    LaunchedEffect(appSpec, appHistory != null) {
        if (!appTouched && appHistory != null) {
            selectedAppLimit = if (selectedPkg == currentGoal.targetPackageName) appSpec.initial(currentGoal.appLimitMinutes) else appSpec.suggested
        }
    }

    val candidateApps = remember(appsUsage) {
        val list = appsUsage.filter { it.packageName !in UsageStatsHelper.SELF_PACKAGES && it.usageTimeMillis > 60_000L }
            .take(6)
            .map { it.packageName to it.appName }
            .toMutableList()
        if (currentGoal.targetPackageName != null && list.none { it.first == currentGoal.targetPackageName }) {
            list.add(0, currentGoal.targetPackageName to currentGoal.targetAppName)
        }
        list
    }

    var isSeparateWeekdayWeekend by remember {
        mutableStateOf(com.digitscore.app.data.DailyGoalStore.isSeparateWeekdayWeekend(context))
    }
    val isTodayWeekend = remember { com.digitscore.app.data.DailyGoalStore.isWeekend() }
    var editingWeekend by remember { mutableStateOf(isTodayWeekend) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEn) "Set today's goals" else "오늘의 목표 설정", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 평일·주말 따로 스위치
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Separate weekday / weekend" else "평일·주말 따로",
                            fontWeight = FontWeight.Bold,
                            fontSize = DigitsTextSize.body
                        )
                        Text(
                            text = if (isEn) "Set different goals for weekdays and weekends" else "평일과 주말의 목표를 구분하여 설정합니다.",
                            fontSize = DigitsTextSize.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSeparateWeekdayWeekend,
                        onCheckedChange = { checked ->
                            isSeparateWeekdayWeekend = checked
                            com.digitscore.app.data.DailyGoalStore.setSeparateWeekdayWeekend(context, checked)
                        }
                    )
                }

                // 스위치를 켜고 꺼도 아래 내용이 움직이지 않도록 평일·주말 칩 자리는 항상 둡니다(꺼지면 비활성).
                run {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            enabled = isSeparateWeekdayWeekend,
                            selected = isSeparateWeekdayWeekend && !editingWeekend,
                            onClick = {
                                if (editingWeekend) {
                                    com.digitscore.app.data.DailyGoalStore.saveWeekendGoalTargets(
                                        context,
                                        com.digitscore.app.model.CustomGoalTargets(selectedScore, selectedPkg, selectedAppName, selectedAppLimit, selectedUnlockLimit)
                                    )
                                    editingWeekend = false
                                    val wd = com.digitscore.app.data.DailyGoalStore.getWeekdayGoalTargets(context)
                                    if (wd != null) {
                                        selectedScore = wd.scoreTarget
                                        selectedPkg = wd.targetPackageName
                                        selectedAppName = wd.targetAppName
                                        selectedAppLimit = wd.appLimitMinutes
                                        selectedUnlockLimit = wd.unlockLimitTarget
                                    }
                                }
                            },
                            label = { Text(if (isEn) "Weekday (Mon-Fri)" else "평일 (월~금)") }
                        )
                        FilterChip(
                            enabled = isSeparateWeekdayWeekend,
                            selected = isSeparateWeekdayWeekend && editingWeekend,
                            onClick = {
                                if (!editingWeekend) {
                                    com.digitscore.app.data.DailyGoalStore.saveWeekdayGoalTargets(
                                        context,
                                        com.digitscore.app.model.CustomGoalTargets(selectedScore, selectedPkg, selectedAppName, selectedAppLimit, selectedUnlockLimit)
                                    )
                                    editingWeekend = true
                                    val we = com.digitscore.app.data.DailyGoalStore.getWeekendGoalTargets(context)
                                    if (we != null) {
                                        selectedScore = we.scoreTarget
                                        selectedPkg = we.targetPackageName
                                        selectedAppName = we.targetAppName
                                        selectedAppLimit = we.appLimitMinutes
                                        selectedUnlockLimit = we.unlockLimitTarget
                                    }
                                }
                            },
                            label = { Text(if (isEn) "Weekend (Sat-Sun)" else "주말 (토~일)") }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 1. 지수 방어선 목표
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEn) "1. Score floor" else "1. 지수 방어선", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                        Text("${selectedScore}P", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = DigitsTextSize.bodyLarge)
                    }
                    GoalRangeSlider(
                        value = selectedScore,
                        spec = scoreSpec,
                        unit = "P",
                        todayLabel = if (isEn) "Now" else "지금",
                        averageLabel = if (isEn) "7-day end" else "7일 마감",
                        onChange = { scoreTouched = true; selectedScore = it }
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
                        Text(if (isEn) "2. App limit" else "2. 관리할 앱 한도", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                        if (selectedPkg != null) {
                            com.digitscore.app.i18n.Text(
                                com.digitscore.app.i18n.formatMinutesSmallUnit(selectedAppLimit.toLong()),
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = DigitsTextSize.bodyLarge
                            )
                        } else {
                            Text(if (isEn) "None" else "선택 안 함", fontSize = DigitsTextSize.label, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // 범위 선택: 앱 1개 vs 관리 앱 전체
                    val isAllManaged = selectedPkg == com.digitscore.app.model.DailyGoal.PACKAGE_ALL_MANAGED
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = !isAllManaged,
                            onClick = {
                                if (isAllManaged) {
                                    appTouched = false
                                    selectedPkg = candidateApps.firstOrNull()?.first
                                    selectedAppName = candidateApps.firstOrNull()?.second ?: ""
                                }
                            },
                            label = { Text(if (isEn) "Single app" else "앱 1개", fontSize = DigitsTextSize.caption) }
                        )
                        FilterChip(
                            selected = isAllManaged,
                            onClick = {
                                if (!isAllManaged) {
                                    appTouched = false
                                    selectedPkg = com.digitscore.app.model.DailyGoal.PACKAGE_ALL_MANAGED
                                    selectedAppName = if (isEn) "All managed apps" else "관리 앱 전체"
                                }
                            },
                            label = { Text(if (isEn) "All managed apps" else "관리 앱 전체", fontSize = DigitsTextSize.caption) }
                        )
                    }

                    if (!isAllManaged && candidateApps.isNotEmpty()) {
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
                                label = { Text(if (isEn) "None" else "선택 안 함", fontSize = DigitsTextSize.caption) }
                            )
                            candidateApps.forEach { (pkg, name) ->
                                val mins = (appsUsage.firstOrNull { it.packageName == pkg }?.usageTimeMillis ?: 0L) / 60_000L
                                FilterChip(
                                    selected = selectedPkg == pkg,
                                    onClick = {
                                        if (selectedPkg != pkg) appTouched = false
                                        selectedPkg = pkg
                                        selectedAppName = name
                                    },
                                    label = {
                                        val labelText = if (mins > 0) "$name · ${mins}min" else name
                                        Text(labelText, fontSize = DigitsTextSize.caption, maxLines = 1)
                                    }
                                )
                            }
                        }
                    }

                    if (selectedPkg != null) {
                        GoalRangeSlider(
                            value = selectedAppLimit,
                            spec = appSpec,
                            unit = "min",
                            todayLabel = if (isEn) "Today" else "오늘",
                            averageLabel = if (isEn) "7-day avg" else "7일 평균",
                            onChange = { appTouched = true; selectedAppLimit = it }
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
                        Text(if (isEn) "3. Unlock limit" else "3. 잠금 해제 한도", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                        Text("${selectedUnlockLimit}${com.digitscore.app.i18n.countUnit()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = DigitsTextSize.bodyLarge)
                    }
                    GoalRangeSlider(
                        value = selectedUnlockLimit,
                        spec = unlockSpec,
                        unit = "x",
                        todayLabel = if (isEn) "Today" else "오늘",
                        averageLabel = if (isEn) "7-day avg" else "7일 평균",
                        onChange = { unlockTouched = true; selectedUnlockLimit = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSeparateWeekdayWeekend) {
                        val targets = com.digitscore.app.model.CustomGoalTargets(selectedScore, selectedPkg, selectedAppName, selectedAppLimit, selectedUnlockLimit)
                        if (editingWeekend) {
                            com.digitscore.app.data.DailyGoalStore.saveWeekendGoalTargets(context, targets)
                        } else {
                            com.digitscore.app.data.DailyGoalStore.saveWeekdayGoalTargets(context, targets)
                        }
                        if (editingWeekend == isTodayWeekend) {
                            onSave(selectedScore, selectedPkg, selectedAppName, selectedAppLimit, selectedUnlockLimit)
                        } else {
                            onDismiss()
                        }
                    } else {
                        onSave(selectedScore, selectedPkg, selectedAppName, selectedAppLimit, selectedUnlockLimit)
                    }
                }
            ) {
                Text(if (isEn) "Save goals" else "목표 저장", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "취소")
            }
        }
    )
}

/**
 * 범위가 지금 값·최근 기록에 맞춰지는 목표 슬라이더.
 * 아래 줄에 범위 양 끝과 기준점(오늘·최근 평균)을 슬라이더 위치에 맞춰 표시합니다.
 */
@Composable
private fun GoalRangeSlider(
    value: Int,
    spec: com.digitscore.app.model.GoalSliderSpec,
    unit: String,
    todayLabel: String,
    averageLabel: String,
    onChange: (Int) -> Unit
) {
    Column {
        Slider(
            value = value.coerceIn(spec.min, spec.max).toFloat(),
            onValueChange = { onChange(spec.snap(it)) },
            valueRange = spec.min.toFloat()..spec.max.toFloat(),
            // 눈금 점은 그리지 않고(칸이 많으면 점이 빽빽해짐) 값만 단위에 맞춰 끊습니다.
            steps = 0,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${spec.min}$unit", fontSize = DigitsTextSize.micro, color = MaterialTheme.colorScheme.outline)
            val refs = listOfNotNull(
                spec.today?.let { "$todayLabel $it$unit" },
                spec.recentAverage?.let { "$averageLabel $it$unit" }
            ).joinToString(" · ")
            if (refs.isNotEmpty()) {
                Text(refs, fontSize = DigitsTextSize.micro, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
            Text("${spec.max}$unit", fontSize = DigitsTextSize.micro, color = MaterialTheme.colorScheme.outline)
        }
    }
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
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("지금의 한 가지 제안", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(guidanceRecommendationText(guidance), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(guidanceCauseText(guidance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DetailChevron(tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CoreIndexHeroCard(
    score: Int,
    grade: ScoreTier,
    rollingScore: RollingScoreDetail?,
    samples: List<CoreIndexSampleEntity>,
    guidance: CoreIndexGuidance?,
    usualDiff: Int? = null,
    restGainPoints: Int? = null,
    onScoreDetailClick: () -> Unit,
    onTrendDetailClick: () -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val gradeText = context.localizedGrade(grade)
    var showChartHelp by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DigitsShape.lg,
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
                    // 단계가 오른 뒤 처음 보는 순간에 한 번만 링을 채웁니다 (R-3)
                    val tierPrefs = remember { context.getSharedPreferences("dashboard_notices", android.content.Context.MODE_PRIVATE) }
                    val celebrate = remember(grade) {
                        val last = tierPrefs.getInt("last_seen_tier", -1)
                        tierPrefs.edit().putInt("last_seen_tier", grade.ordinal).apply()
                        last != -1 && grade.ordinal < last
                    }
                    CoreIndexGauge(
                        score = score,
                        grade = gradeText,
                        iconSize = 54.dp,
                        celebrate = celebrate
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
                        shape = DigitsShape.xs,
                        color = MaterialTheme.colorScheme.tile.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = flowText,
                            fontSize = DigitsTextSize.tiny,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                    // 평소와 비교 한 줄 (쉬고 돌아온 직후에는 '쉬는 동안 +nP'를 한 번 보여 줌)
                    val compareText: String? = when {
                        restGainPoints != null -> if (isEn) "+${restGainPoints}P while resting" else "쉬는 동안 +${restGainPoints}P"
                        usualDiff == null -> null
                        usualDiff > 0 -> if (isEn) "+${usualDiff}P vs usual" else "평소보다 +${usualDiff}P"
                        usualDiff < 0 -> if (isEn) "${usualDiff}P vs usual" else "평소보다 ${usualDiff}P"
                        else -> if (isEn) "Same as usual" else "평소와 같음"
                    }
                    compareText?.let {
                        val positive = restGainPoints != null || (usualDiff ?: 0) > 0
                        val negative = restGainPoints == null && (usualDiff ?: 0) < 0
                        Text(
                            text = it,
                            fontSize = DigitsTextSize.tiny,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                positive -> ScoreGreenText
                                negative -> ScoreRedText
                                else -> MaterialTheme.colorScheme.outline
                            },
                            maxLines = 1,
                            softWrap = false
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
                                fontSize = DigitsTextSize.body,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { showChartHelp = true },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HelpOutline,
                                    contentDescription = if (isEn) "About the Core Index" else "코어 지수 안내",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onTrendDetailClick)
                        ) {
                            com.digitscore.app.ui.components.MoreLink(text = if (isEn) "Details" else "상세")
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
                if (isEn) "Your Core Index is holding steady."
                else "코어 지수가 안정적으로 유지되고 있습니다."
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
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = adviceText,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = DigitsTextSize.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 문장을 끝까지 보여 주도록 두 줄까지 허용합니다.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                com.digitscore.app.ui.components.DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showChartHelp) {
        AlertDialog(
            onDismissRequest = { showChartHelp = false },
            title = {
                Text(
                    text = if (isEn) "About the Core Index" else "코어 지수 안내",
                    fontWeight = FontWeight.Bold,
                    fontSize = DigitsTextSize.title
                )
            },
            text = {
                Text(
                    text = if (isEn) {
                        "What is the Core Index?\n" +
                        "A score from 1 to 99 that shows your phone-use balance right now. It starts at 75, goes down while you use the phone and goes up while the screen is off. It is not reset at midnight or 05:00; it keeps going from where it was.\n" +
                        "• Managed apps lower it faster, and faster still after 30min in a row. Study and Tool apps do not lower it within their limits.\n" +
                        "• Levels and colors: Stable 80+ (green), Fair 60–79 (yellow), Caution 40–59 (orange), Rest below 40 (red).\n\n" +
                        "Reading the chart\n" +
                        "• The line shows the last 24 hours. Thin vertical lines mark every 3 hours; the 05:00 line starts a new day.\n" +
                        "• The highest and lowest points show the score and the time (HH:mm)."
                    } else {
                        "코어 지수란?\n" +
                        "지금의 스마트폰 사용 균형을 1~99로 나타낸 점수입니다. 75에서 시작해 화면을 쓰는 동안 내려가고, 화면을 끄고 쉬는 동안 올라갑니다. 자정이나 05:00에 초기화되지 않고 이전 값에서 계속 이어집니다.\n" +
                        "• 관리 앱은 더 빨리 내려가고, 30min 넘게 이어 쓰면 더 빨라집니다. 권장·필수 앱은 정해진 시간 안에서는 내려가지 않습니다.\n" +
                        "• 지수 단계와 색: 안정 80 이상(초록), 보통 60~79(노랑), 주의 40~59(주황), 휴식 필요 40 미만(빨강).\n\n" +
                        "차트 읽기\n" +
                        "• 최근 24시간의 변화를 선으로 보여 줍니다. 가는 세로선은 3시간 간격, 05:00 선은 하루가 바뀌는 시점입니다.\n" +
                        "• 가장 높은 점과 낮은 점에 점수와 시각(HH:mm)을 표시합니다."
                    },
                    fontSize = DigitsTextSize.body,
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
            shape = DigitsShape.sm,
            color = MaterialTheme.colorScheme.tile.copy(alpha = 0.5f),
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
                    imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (isEn) "Live tracking active" else "실시간 추적 진행 중",
                        fontSize = DigitsTextSize.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEn) "Trend records every 5 minutes" else "5min마다 점수 추세가 기록됩니다",
                        fontSize = DigitsTextSize.tiny,
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
                    val highLabelArgb = ScoreGreenText.toArgb()
                    val lowLabelArgb = ScoreRedText.toArgb()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val hPx = size.height
                        // 위: 최고 라벨 자리, 아래: 시간축 글자 자리 (글자 크기 설정에 맞춰 늘어남)
                        val topPadding = DigitsTextSize.chart.toPx() + 6.dp.toPx()
                        val bottomPadding = DigitsTextSize.chart.toPx() + 6.dp.toPx()
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

                        // X축 3시간 간격 및 05:00 논리적 하루 기준선 표시
                        val vertDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        val calAxis = Calendar.getInstance().apply {
                            timeInMillis = windowStartMillis
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            val hour = get(Calendar.HOUR_OF_DAY)
                            val rem = Math.floorMod(hour - 5, 3)
                            add(Calendar.HOUR_OF_DAY, if (rem == 0) 0 else (3 - rem))
                        }
                        val paintAxis = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = outlineColorInt
                            textSize = DigitsTextSize.chart.toPx()
                        }
                        val paintDayBoundary = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = onSurfaceColorInt
                            textSize = DigitsTextSize.chart.toPx()
                            isFakeBoldText = true
                        }
                        while (calAxis.timeInMillis <= now) {
                            val ts = calAxis.timeInMillis
                            val x = xPos(ts)
                            val h = calAxis.get(Calendar.HOUR_OF_DAY)
                            val isLogicalDayStart = (h == 5)

                            drawLine(
                                color = if (isLogicalDayStart) Color.Gray.copy(alpha = 0.35f) else Color.Gray.copy(alpha = 0.15f),
                                start = Offset(x, topPadding),
                                end = Offset(x, hPx - bottomPadding),
                                strokeWidth = if (isLogicalDayStart) 1.2f.dp.toPx() else 0.8f.dp.toPx(),
                                pathEffect = vertDash
                            )

                            if (isLogicalDayStart) {
                                val month = calAxis.get(Calendar.MONTH) + 1
                                val day = calAxis.get(Calendar.DAY_OF_MONTH)
                                val dateStr = "$month/$day"
                                drawContext.canvas.nativeCanvas.drawText(
                                    dateStr,
                                    (x - paintDayBoundary.measureText(dateStr) / 2f).coerceIn(0f, w - paintDayBoundary.measureText(dateStr)),
                                    hPx - 3.dp.toPx(),
                                    paintDayBoundary
                                )
                            } else {
                                val hourStr = String.format(Locale.getDefault(), "%02d:00", h)
                                drawContext.canvas.nativeCanvas.drawText(
                                    hourStr,
                                    (x - paintAxis.measureText(hourStr) / 2f).coerceIn(0f, w - paintAxis.measureText(hourStr)),
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
                            color = highLabelArgb; textSize = DigitsTextSize.chart.toPx(); isFakeBoldText = true
                        }
                        val paintRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = lowLabelArgb; textSize = DigitsTextSize.chart.toPx(); isFakeBoldText = true
                        }
                        // 라벨은 실제 글자 폭을 재서 차트 안쪽에 둡니다 (양 끝 잘림 방지).
                        fun labelX(centerX: Float, text: String, paint: android.graphics.Paint): Float {
                            val tw = paint.measureText(text)
                            return (centerX - tw / 2f).coerceIn(2.dp.toPx(), (w - tw - 2.dp.toPx()).coerceAtLeast(2.dp.toPx()))
                        }
                        val calPoint = Calendar.getInstance()
                        maxSample?.let { s ->
                            val mx = xPos(s.timestampMillis)
                            val my = yPos(s.exactScore)
                            calPoint.timeInMillis = s.timestampMillis
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", calPoint.get(Calendar.HOUR_OF_DAY), calPoint.get(Calendar.MINUTE))
                            drawCircle(ScoreGreen, 2.2.dp.toPx(), Offset(mx, my))
                            val highText = if (isEn) "High ${points(s.exactScore)}P ($timeStr)" else "최고 ${points(s.exactScore)}P ($timeStr)"
                            drawContext.canvas.nativeCanvas.drawText(
                                highText,
                                labelX(mx, highText, paintGreen),
                                (my - 4.dp.toPx()).coerceAtLeast(paintGreen.textSize),
                                paintGreen
                            )
                        }
                        minSample?.let { s ->
                            val mx = xPos(s.timestampMillis)
                            val my = yPos(s.exactScore)
                            calPoint.timeInMillis = s.timestampMillis
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", calPoint.get(Calendar.HOUR_OF_DAY), calPoint.get(Calendar.MINUTE))
                            drawCircle(ScoreRed, 2.2.dp.toPx(), Offset(mx, my))
                            val lowText = if (isEn) "Low ${points(s.exactScore)}P ($timeStr)" else "최저 ${points(s.exactScore)}P ($timeStr)"
                            drawContext.canvas.nativeCanvas.drawText(
                                lowText,
                                labelX(mx, lowText, paintRed),
                                (my + paintRed.textSize + 3.dp.toPx()).coerceAtMost(hPx - bottomPadding - 2.dp.toPx()),
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
                    Text(guidanceCauseText(guidance), fontSize = DigitsTextSize.body, fontWeight = FontWeight.Medium)
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
                        fontSize = DigitsTextSize.label,
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
                        fontSize = DigitsTextSize.label
                    )
                }
                InsightCard(if (isEn) "Today's Summary (since 05:00)" else "오늘 요약 (05:00 기준)") {
                    Text(
                        if (isEn) "Screen ${formatMinutesToHoursAndMinutes(guidance.rollingUsageMinutes)} · Unlocks ${guidance.rollingUnlockCount}"
                        else "화면 ${formatMinutesToHoursAndMinutes(guidance.rollingUsageMinutes)} · 잠금 해제 ${guidance.rollingUnlockCount}회",
                        fontSize = DigitsTextSize.label,
                        fontWeight = FontWeight.Medium
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
                        fontSize = DigitsTextSize.label
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
    // 할 일 한 줄: 행동과 결과(P)를 항상 함께 말합니다.
    return context.coachActionText(
        com.digitscore.app.engine.CoachActionPlanner.plan(
            recommendation = guidance.recommendation,
            recoveryMinutes = guidance.recoveryMinutes,
            recoveryTargetScore = guidance.recoveryTargetScore,
            currentScore = guidance.currentScore,
            defenseLine = guidance.defenseLine,
            shortRestGain = guidance.shortRestGain
        )
    )
}

@Composable
private fun ScoreGaugeCard(
    score: Int,
    grade: ScoreTier,
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
    breakdown: com.digitscore.app.data.CoreIndexBreakdown,
    longestRest: com.digitscore.app.data.LongestRest.Rest?,
    screenTimeMillis: Long,
    unlockCount: Int,
    distractingMillis: Long,
    onScreenTimeClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onDistractingClick: () -> Unit,
    onRestClick: () -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val screenMinutes = screenTimeMillis / 60_000L
    val distractingMinutes = distractingMillis / 60_000L
    val hasBreakdown = breakdown != com.digitscore.app.data.CoreIndexBreakdown.EMPTY
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (hasBreakdown) {
            // 큰 글자는 실제 사용량(시간·횟수), 아래 줄은 그만큼이 지수에 준 효과(±P)
            // 관리 앱(가장 큰 감점 원인) · 잠금 해제 · 가장 긴 쉼(길게 쉴수록 크게 오름)
            fun effect(value: Double) = (if (isEn) "Index " else "지수 ") + formatPoints(value)
            StatisticCard(
                label = if (isEn) "Managed" else "관리 앱",
                value = com.digitscore.app.i18n.formatMinutesSmallUnit(distractingMinutes),
                caption = effect(breakdown.managedApps),
                captionColor = pointsColor(breakdown.managedApps),
                icon = Icons.Rounded.HourglassBottom,
                onClick = onDistractingClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            StatisticCard(
                label = if (isEn) "Unlocks" else "잠금 해제",
                value = "${unlockCount}${com.digitscore.app.i18n.countUnit()}",
                caption = effect(breakdown.unlocks),
                captionColor = pointsColor(breakdown.unlocks),
                icon = Icons.Rounded.LockOpen,
                onClick = onUnlockClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            StatisticCard(
                label = if (isEn) "Longest rest" else "가장 긴 쉼",
                // 가장 길게 쉰 한 번과 그동안 오른 지수 (24시간 전체 회복이 아님)
                value = com.digitscore.app.i18n.formatMinutesSmallUnit(longestRest?.minutes ?: 0L),
                caption = effect(longestRest?.gain?.coerceAtLeast(0.0) ?: 0.0),
                captionColor = pointsColor(longestRest?.gain?.coerceAtLeast(0.0) ?: 0.0),
                icon = Icons.Rounded.Bedtime,
                onClick = onRestClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        } else {
            // 분해 기록이 쌓이기 전에는 원값을 보여 줍니다.
            StatisticCard(
                label = if (isEn) "Screen" else "화면",
                value = com.digitscore.app.i18n.formatMinutesSmallUnit(screenMinutes),
                icon = Icons.Rounded.PhoneAndroid,
                onClick = onScreenTimeClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            StatisticCard(
                label = if (isEn) "Unlocks" else "잠금 해제",
                value = "${unlockCount}${com.digitscore.app.i18n.countUnit()}",
                icon = Icons.Rounded.LockOpen,
                onClick = onUnlockClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            StatisticCard(
                label = if (isEn) "Managed" else "관리",
                value = com.digitscore.app.i18n.formatMinutesSmallUnit(distractingMinutes),
                icon = Icons.Rounded.HourglassBottom,
                onClick = onDistractingClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

/** 지수 영향 표기: 1P 미만은 소수 한 자리, 그 이상은 정수. 예) -6P, +0.4P, 0P */
internal fun formatPoints(value: Double): String {
    val abs = kotlin.math.abs(value)
    if (abs < 0.05) return "0P"
    val body = if (abs < 1.0) String.format(java.util.Locale.US, "%.1f", abs) else kotlin.math.round(abs).toInt().toString()
    return (if (value > 0) "+" else "-") + body + "P"
}

@Composable
private fun pointsColor(value: Double): androidx.compose.ui.graphics.Color = when {
    value <= -0.05 -> ScoreRedText
    value >= 0.05 -> ScoreGreenText
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun StatisticCard(
    label: String,
    value: Any,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    caption: String? = null,
    captionColor: androidx.compose.ui.graphics.Color? = null
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = DigitsShape.md
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
                        fontSize = DigitsTextSize.label,
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
                    fontSize = DigitsTextSize.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            } else {
                Text(
                    text = value.toString(),
                    fontSize = DigitsTextSize.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
            caption?.let {
                Text(
                    text = it,
                    fontSize = DigitsTextSize.caption,
                    fontWeight = if (captionColor != null) FontWeight.Bold else null,
                    color = captionColor ?: MaterialTheme.colorScheme.outline,
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
        shape = DigitsShape.md
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appUsage.appName, fontSize = DigitsTextSize.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${appUsage.categoryType.displayName} · ${if (isEn) "Today" else "오늘"} ${appUsage.sessionCount}${com.digitscore.app.i18n.countUnit()}",
                    fontSize = DigitsTextSize.caption,
                    color = categoryColor
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatInsightDuration(appUsage.usageTimeMillis), fontSize = DigitsTextSize.bodyLarge, fontWeight = FontWeight.Bold)
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
    val config = com.digitscore.app.engine.CumulativeScoreConfig.CURRENT
    fun mins(v: Long) = com.digitscore.app.i18n.formatMinutesSmallUnit(v)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.score_detail_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.score_points, detail?.finalScore ?: 80), fontSize = DigitsTextSize.heroLarge, fontWeight = FontWeight.Bold)
                Text(rollingStatusText(detail))
                BreakdownRow(
                    stringResource(R.string.recent_usage),
                    mins(detail?.recentUsageMinutes ?: 0L),
                    stringResource(R.string.recent_usage_desc)
                )
                BreakdownRow(
                    stringResource(R.string.acute_load),
                    mins((detail?.acuteLoad ?: 0.0).roundToLong()),
                    stringResource(
                        R.string.acute_load_desc,
                        config.accelerationStartMinutes.roundToInt(),
                        config.accelerationStepMinutes.roundToInt(),
                        (config.managedAcceleration * 100).roundToInt(),
                        String.format(Locale.US, "%.1f", config.maxManagedMultiplier),
                        config.momentumRestDecayRate.roundToInt()
                    )
                )
                BreakdownRow(
                    stringResource(R.string.rolling_load),
                    AnnotatedString(recoverySlowdownText(detail?.rollingLoad ?: 0.0)),
                    stringResource(
                        R.string.rolling_load_desc,
                        config.accelerationStartMinutes.roundToInt(),
                        config.burdenHalfLifeAwakeHours.roundToInt()
                    )
                )
                if ((detail?.lateNightCarryoverLoad ?: 0.0) >= 0.05) {
                    BreakdownRow(
                        stringResource(R.string.late_night_carryover_load),
                        AnnotatedString(String.format("%.1f", detail?.lateNightCarryoverLoad ?: 0.0))
                    )
                }
                if ((detail?.sleepRestMinutes ?: 0L) > 0L) {
                    BreakdownRow(stringResource(R.string.sleep_rest_time), mins(detail?.sleepRestMinutes ?: 0L))
                    if ((detail?.postWakeRestMinutes ?: 0L) > 0L) {
                        BreakdownRow(stringResource(R.string.post_wake_rest_time), mins(detail?.postWakeRestMinutes ?: 0L))
                    }
                    BreakdownRow(stringResource(R.string.effective_recovery_time), mins(detail?.effectiveRecoveryMinutes ?: 0L))
                }
                Text(
                    stringResource(R.string.score_method_note),
                    fontSize = DigitsTextSize.label,
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

/** 회복 부담을 "회복 속도가 몇 % 느려지는지"로 표시합니다. 엔진: 회복량 / (1 + 부담 / 나눔값). */
internal fun recoverySlowdownPercent(burden: Double): Int {
    val divisor = com.digitscore.app.engine.CumulativeScoreConfig.CURRENT.burdenRecoveryDivisor
    if (burden <= 0.0) return 0
    return ((1.0 - 1.0 / (1.0 + burden / divisor)) * 100.0).roundToInt()
}

@Composable
private fun recoverySlowdownText(burden: Double): String {
    val pct = recoverySlowdownPercent(burden)
    return if (pct <= 0) stringResource(R.string.recovery_burden_none) else stringResource(R.string.recovery_burden_value, pct)
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
            recoverySlowdownText(detail.rollingLoad)
        )
        ScoreFlow.RECOVERING -> stringResource(R.string.score_recovering, detail.restMinutes)
        ScoreFlow.STEADY -> stringResource(
            R.string.score_steady,
            recoverySlowdownText(detail.rollingLoad)
        )
    }
}

@Composable
private fun BreakdownRow(title: String, value: AnnotatedString, description: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, DigitsShape.sm)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = DigitsTextSize.body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text(
                text = value,
                fontSize = DigitsTextSize.body,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
        }
        if (description != null) {
            Text(text = description, fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    val reconciliation by produceState(
        initialValue = ScreenTimeReconciliationCalculator.calculate(totalScreenMillis, totalScreenMillis, 0L, 0L, 0L),
        key1 = totalScreenMillis
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ScreenTimeReconciliationCalculator.queryReconciliation(context, totalScreenMillis)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = if (isEn) "Screen Time (Today since 05:00)" else "오늘 화면 사용 (05:00 기준)", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge)
                Text(
                    text = if (isEn) "Total ${formatInsightDuration(totalScreenMillis)}" else "총 ${formatInsightDuration(totalScreenMillis)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = DigitsTextSize.headlineLarge,
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
                        shape = DigitsShape.md
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = if (isEn) "Time Breakdown by Category" else "카테고리별 시간 분배", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            CompactBarChart(
                                values = listOf(distractingMillis.toFloat(), (productiveMillis + neutralMillis).toFloat()),
                                barColor = MaterialTheme.colorScheme.primary,
                                contentDescription = UiTranslator.translate("관리 및 일반 앱 사용 시간 비교 그래프")
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Text(if (isEn) "Managed" else "관리", fontSize = DigitsTextSize.tiny, color = ScoreRedText)
                                Text(if (isEn) "General" else "일반", fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.outline)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (isEn) "Managed apps" else "관리 앱", fontSize = DigitsTextSize.label)
                                Text(text = formatInsightDuration(distractingMillis), fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = ScoreRedText)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (isEn) "General apps" else "일반 앱", fontSize = DigitsTextSize.label)
                                Text(text = formatInsightDuration(neutralMillis + productiveMillis), fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    // 디지털 웰빙과 숫자가 다른 이유
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = DigitsShape.md
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isEn) "Why this differs from Digital Wellbeing" else "디지털 웰빙과 숫자가 다른 이유",
                                fontSize = DigitsTextSize.label,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = if (isEn)
                                    "Digital Wellbeing counts from midnight and includes launcher time. DigitsCore counts from 05:00 and excludes launcher."
                                else
                                    "디지털 웰빙은 자정부터 세고 홈 화면 시간도 포함합니다. DigitsCore는 05:00부터 세고 홈 화면은 빼서 숫자가 다를 수 있습니다.",
                                fontSize = DigitsTextSize.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = if (reconciliation.isLateNightRange) {
                                        if (isEn) "DigitsCore (since yesterday 05:00)" else "DigitsCore (어제 05:00 기준)"
                                    } else {
                                        if (isEn) "DigitsCore (since 05:00)" else "DigitsCore (05:00 기준)"
                                    },
                                    fontSize = DigitsTextSize.label
                                )
                                Text(text = com.digitscore.app.i18n.formatMinutesSmallUnit(reconciliation.digitsCoreMinutes.toLong()), fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (isEn) "OS estimate (since midnight)" else "OS 기준 추정치 (자정부터)", fontSize = DigitsTextSize.label)
                                Text(text = com.digitscore.app.i18n.formatMinutesSmallUnit(reconciliation.osEstimatedMinutes.toLong()), fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold)
                            }
                            if (reconciliation.isLateNightRange && reconciliation.yesterdayUsageMinutes > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "Yesterday 05:00-midnight usage" else "어제 05:00~자정 사용분", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                    Text(text = com.digitscore.app.i18n.formatMinutesSmallUnit(reconciliation.yesterdayUsageMinutes.toLong()), fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (!reconciliation.isLateNightRange && reconciliation.midnightToDawnMinutes > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "00:00-05:00 usage" else "00:00~05:00 사용분", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                    Text(text = com.digitscore.app.i18n.formatMinutesSmallUnit(reconciliation.midnightToDawnMinutes.toLong()), fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (reconciliation.launcherMinutes > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "Home screen (launcher)" else "홈 화면(런처) 머문 시간", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                    Text(text = com.digitscore.app.i18n.formatMinutesSmallUnit(reconciliation.launcherMinutes.toLong()), fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (reconciliation.splitScreenDeduplicatedMinutes > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "Split screen deduplication" else "분할 화면·PiP 중복 제거분", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                    Text(text = com.digitscore.app.i18n.formatMinutesSmallUnit(reconciliation.splitScreenDeduplicatedMinutes.toLong()), fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (reconciliation.otherMinutes != 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isEn) "Other difference" else "기타 차이", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                    Text(text = com.digitscore.app.i18n.formatSignedMinutesSmallUnit(reconciliation.otherMinutes.toLong()), fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = if (isEn) "App Usage (Tap to configure)" else "앱별 사용 시간 (탭하여 설정 변경)",
                        fontSize = DigitsTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(appsUsage) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectApp(app) }
                            .background(MaterialTheme.colorScheme.surfaceVariant, DigitsShape.sm)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.appName, fontWeight = FontWeight.Medium, fontSize = DigitsTextSize.body)
                            Text(
                                text = "${app.categoryType.displayName} · ${if (isEn) "Today" else "오늘"} ${app.sessionCount}${com.digitscore.app.i18n.countUnit()}",
                                fontSize = DigitsTextSize.caption,
                                color = appRatingColor(app.categoryType)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatInsightDuration(app.usageTimeMillis),
                                fontWeight = FontWeight.Bold,
                                fontSize = DigitsTextSize.body
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
 * 3. 잠금 해제 횟수 세부 통계 다이얼로그
 */
@Composable
fun UnlockDetailDialog(
    unlockCount: Int,
    onDismiss: () -> Unit,
    onNavigateToPresetSettings: () -> Unit
) {
    val context = LocalContext.current
    var insights by remember { mutableStateOf<UnlockInsights?>(null) }
    var topFirstApps by remember { mutableStateOf<List<Pair<String, com.digitscore.app.data.entity.AppDailyInteractionEntity>>>(emptyList()) }
    var topNotificationUnlocks by remember { mutableStateOf<List<Pair<String, com.digitscore.app.data.entity.AppDailyInteractionEntity>>>(emptyList()) }
    var topNotifications by remember { mutableStateOf<List<Pair<String, com.digitscore.app.data.entity.AppDailyInteractionEntity>>>(emptyList()) }
    LaunchedEffect(unlockCount) {
        insights = withContext(Dispatchers.IO) {
            UsageStatsHelper.getRolling24HourUnlockInsights(context)
        }
        val (firsts, notifUnlocks, notifs) = withContext(Dispatchers.IO) {
            val db = com.digitscore.app.data.DigitsDatabase.getInstance(context)
            val todayDate = com.digitscore.app.data.DailyGoalStore.getLogicalDateString()
            val pm = context.packageManager

            fun mapLabels(list: List<com.digitscore.app.data.entity.AppDailyInteractionEntity>) = list.map { entity ->
                val label = runCatching {
                    val appInfo = pm.getApplicationInfo(entity.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(entity.packageName)
                label to entity
            }

            Triple(
                mapLabels(db.appDailyInteractionDao().getTopFirstAfterUnlock(todayDate, 5)),
                mapLabels(db.appDailyInteractionDao().getTopNotificationUnlock(todayDate, 5)),
                mapLabels(db.appDailyInteractionDao().getTopNotifications(todayDate, 5))
            )
        }
        topFirstApps = firsts
        topNotificationUnlocks = notifUnlocks
        topNotifications = notifs
    }

    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = if (isEn) "Unlock In-Depth Analysis" else "오늘의 잠금 해제 정밀 분석", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge)
                Text(
                    text = if (isEn) "${unlockCount} unlocks today (since 05:00)" else "오늘 05:00 이후 총 ${unlockCount}회",
                    fontWeight = FontWeight.Bold,
                    fontSize = DigitsTextSize.headlineLarge,
                    color = ScoreYellowText
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
                        shape = DigitsShape.md
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isEn) "Quick Glances vs App Usage" else "짧은 확인 vs 실제 앱 사용", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Text(if (isEn) "Threshold: ≤7s" else "기준: 7초 이내", fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.primary)
                            }

                            // 수평 분할 막대 그래프
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(DigitsShape.sm)
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
                                        if (isEn) "Quick Glance: ${glanceCount} (${(glanceRatio * 100).toInt()}%)"
                                        else "짧은 확인: ${glanceCount}회 (${(glanceRatio * 100).toInt()}%)",
                                        fontSize = DigitsTextSize.caption,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(ScoreGreen, CircleShape))
                                    Text(
                                        if (isEn) "App Usage: ${appUnlocks}"
                                        else "앱 사용: ${appUnlocks}회",
                                        fontSize = DigitsTextSize.caption,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Text(
                                text = if (isEn) "Detected quick checks where the screen was turned off within 7 seconds without opening apps. Calibrates gradually to your response speed."
                                else "앱을 켜지 않고 7초 이내에 화면을 끈 짧은 확인을 감지했습니다. 개인 세션 반응 속도를 학습하여 점진적으로 보정합니다.",
                                fontSize = DigitsTextSize.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. 나의 14일 최저치 / 평균 기준 카드
                    if (value.past14DaysLowestUnlock > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = DigitsShape.md
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isEn) "My Past Trend Baseline" else "나의 과거 트렌드 기준", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isEn) "14d Low: ${value.past14DaysLowestUnlock}" else "14일 최저: ${value.past14DaysLowestUnlock}회", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Medium, color = ScoreGreenText)
                                    Text(if (isEn) "Daily Avg: ${value.past14DaysAverageUnlock}" else "일평균: ${value.past14DaysAverageUnlock}회", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isEn) "Today: ${unlockCount}" else "오늘: ${unlockCount}회", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = ScoreYellowText)
                                }
                                Text(
                                    text = if (isEn) "Use your lowest recent count (${value.past14DaysLowestUnlock} unlocks) as a benchmark to gradually reduce daily unlock goals."
                                    else "과거 최저 기록(${value.past14DaysLowestUnlock}회)을 바닥선으로 삼아 일일 목표를 점진적으로 낮춰보세요.",
                                    fontSize = DigitsTextSize.caption,
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
                        shape = DigitsShape.md
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(if (isEn) "Hourly Unlocks" else "시간대별 잠금 해제", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            if (busiest.isEmpty()) {
                                Text(if (isEn) "No unlock records available yet for hourly analysis." else "아직 시간대 분석에 필요한 잠금 해제 기록이 없습니다.", fontSize = DigitsTextSize.body)
                            } else {
                                CompactBarChart(
                                    values = value.hourlyUnlockCounts.map { it.toFloat() },
                                    barColor = ScoreYellow,
                                    contentDescription = UiTranslator.translate("24시간 잠금 해제 횟수 그래프")
                                )
                                RollingHourlyAxisLabels(value.windowStartMillis, value.windowEndMillis)
                                val peak = busiest.first()
                                Text(
                                    if (isEn) "Most active interval is ${rollingHourLabel(value.windowStartMillis, peak.index)} · ${peak.value} unlocks."
                                    else "가장 잦은 구간은 ${rollingHourLabel(value.windowStartMillis, peak.index)} · ${peak.value}회입니다.",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            value.averageIntervalMinutes?.let { interval ->
                                Text(
                                    if (isEn) "Unlocked once every ~${interval.coerceAtLeast(1)}min on average."
                                    else "평균 약 ${interval.coerceAtLeast(1)}min마다 한 번 열었습니다.",
                                    fontSize = DigitsTextSize.label,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = DigitsShape.md
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isEn) "Notifications vs Unlocks" else "알림과 잠금 해제 비교", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            if (!value.notificationEventsSupported) {
                                Text(if (isEn) "This Android version does not support notification event comparison." else "이 Android 버전은 알림 이벤트 비교를 제공하지 않습니다.", fontSize = DigitsTextSize.body)
                            } else {
                                Text(
                                    if (isEn) "OS Notifications: ${value.notificationCount} · Unlocks: ${unlockCount}"
                                    else "받은 알림 ${value.notificationCount}건 · 잠금 해제 ${unlockCount}회",
                                    fontSize = DigitsTextSize.body,
                                    fontWeight = FontWeight.Medium
                                )
                                CompactBarChart(
                                    values = listOf(value.notificationCount.toFloat(), unlockCount.toFloat()),
                                    barColor = MaterialTheme.colorScheme.primary,
                                    contentDescription = UiTranslator.translate(
                                        "알림 ${value.notificationCount}건과 잠금 해제 ${unlockCount}회 비교 그래프"
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    Text(if (isEn) "Notifications" else "알림", fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.outline)
                                    Text(if (isEn) "Unlocks" else "잠금 해제", fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.outline)
                                }
                                val comparison = when {
                                    value.notificationCount > 0 -> {
                                        val ratio = unlockCount.toFloat() / value.notificationCount
                                        if (isEn) "Unlocked ~${String.format(Locale.US, "%.1f", ratio)} times per notification."
                                        else "알림 1건당 약 ${String.format(Locale.US, "%.1f", ratio)}회 잠금 해제했습니다."
                                    }
                                    unlockCount > 0 -> if (isEn) "Unlocked ${unlockCount} times without any received notifications."
                                        else "받은 알림 없이도 ${unlockCount}회 잠금 해제했습니다."
                                    else -> if (isEn) "No comparative records available yet." else "아직 비교할 기록이 없습니다."
                                }
                                Text(comparison, fontSize = DigitsTextSize.label, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    if (isEn) "Simple comparison of OS events; does not imply notifications directly caused unlocks."
                                    else "OS 이벤트의 단순 비교이며 알림이 잠금 해제의 직접 원인이라는 뜻은 아닙니다.",
                                    fontSize = DigitsTextSize.tiny,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // 잠금 해제 후 처음 연 앱 상위 5개
                    if (topFirstApps.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = DigitsShape.md
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isEn) "First App Opened After Unlock" else "잠금 해제 후 처음 연 앱",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = if (isEn) "Top 5 apps opened within 60s of unlocking."
                                    else "잠금 해제 직후 60초 안에 처음 실행한 상위 5개 앱입니다.",
                                    fontSize = DigitsTextSize.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                topFirstApps.forEach { (appName, item) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(appName, fontSize = DigitsTextSize.body, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = if (isEn) "Quick glance: ${item.firstAfterUnlockQuickCount}"
                                                else "짧은 확인: ${item.firstAfterUnlockQuickCount}회",
                                                fontSize = DigitsTextSize.caption,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Text(
                                            text = if (isEn) "${item.firstAfterUnlockCount}${com.digitscore.app.i18n.countUnit()}" else "${item.firstAfterUnlockCount}회",
                                            fontSize = DigitsTextSize.body,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 알림이 부른 잠금 해제 상위 5개
                    if (topNotificationUnlocks.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = DigitsShape.md
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isEn) "Notification-Triggered Unlocks" else "알림이 부른 잠금 해제",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = if (isEn) "Apps opened within 60s of receiving a notification."
                                    else "알림을 받은 뒤 60초 안에 잠금을 풀고 실행한 상위 5개 앱입니다.",
                                    fontSize = DigitsTextSize.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val top = topNotificationUnlocks.first()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, DigitsShape.sm)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isEn) "Turning off ${top.first}'s notifications could reduce ~${top.second.notificationUnlockCount} unlocks per day."
                                        else "${top.first} 알림을 끄면 하루 약 ${top.second.notificationUnlockCount}회 잠금 해제를 줄일 수 있습니다.",
                                        fontSize = DigitsTextSize.caption,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, top.second.packageName)
                                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            runCatching { context.startActivity(intent) }
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(if (isEn) "Notification settings" else "알림 설정 열기", fontSize = DigitsTextSize.caption)
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                topNotificationUnlocks.forEach { (appName, item) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(appName, modifier = Modifier.weight(1f), fontSize = DigitsTextSize.body, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (isEn) "${item.notificationUnlockCount}${com.digitscore.app.i18n.countUnit()}" else "${item.notificationUnlockCount}회",
                                            fontSize = DigitsTextSize.body,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 알림을 많이 보낸 앱 상위 5개
                    if (topNotifications.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = DigitsShape.md
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isEn) "Top Notification Apps" else "알림을 많이 보낸 앱",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = if (isEn) "Apps that sent the most notifications today."
                                    else "오늘 알림을 가장 많이 보낸 상위 5개 앱입니다.",
                                    fontSize = DigitsTextSize.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                topNotifications.forEach { (appName, item) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(appName, modifier = Modifier.weight(1f), fontSize = DigitsTextSize.body, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (isEn) "${item.notificationCount}${com.digitscore.app.i18n.countUnit()}" else "${item.notificationCount}회",
                                            fontSize = DigitsTextSize.body,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = DigitsShape.md
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = if (isEn) "Unlock Management Guide" else "잠금 해제 관리 가이드", fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = if (isEn) "Reducing subconscious phone wakeups significantly sharpens daily focus."
                            else "스마트폰을 무의식적으로 켜는 습관을 줄이면 집중력을 대폭 향상시킬 수 있습니다.",
                            fontSize = DigitsTextSize.body,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEn) "Today's unlock counts are factored smoothly into core score usage load."
                            else "오늘 잠금 해제 횟수는 코어 지수의 사용 부하에 완만하게 반영됩니다.",
                            fontSize = DigitsTextSize.label,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToPresetSettings) {
                Text(if (isEn) "Set Unlock Target" else "목표 잠금 해제 횟수 설정")
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
 * 4. 관리 앱 세부 분석 다이얼로그
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
                Text(text = if (isEn) "Managed Apps Detailed Analysis" else "관리 앱 상세 분석", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge)
                Text(
                    text = if (isEn) "Total ${formatInsightDuration(distractingMillis)}" else "총 ${formatInsightDuration(distractingMillis)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = DigitsTextSize.titleLarge,
                    color = ScoreRedText
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
                        fontSize = DigitsTextSize.label,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (appsUsage.isNotEmpty()) {
                    item {
                        CompactBarChart(
                            values = appsUsage.take(8).map { it.usageTimeMillis / 60_000f },
                            barColor = ScoreRed,
                            contentDescription = UiTranslator.translate("관리 대상 앱별 사용 시간 그래프")
                        )
                        Text(
                            text = if (isEn) "Top ${minOf(8, appsUsage.size)} apps by usage" else "사용 시간 상위 ${minOf(8, appsUsage.size)}개 앱",
                            fontSize = DigitsTextSize.tiny,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (appsUsage.isEmpty()) {
                    item {
                        Text(
                            text = if (isEn) "No managed apps used today. Great balance!" else "오늘 사용된 관리 대상 앱이 없습니다. 안정적인 사용 흐름입니다.",
                            fontSize = DigitsTextSize.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = ScoreGreenText,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(appsUsage) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app) }
                                .background(MaterialTheme.colorScheme.surfaceVariant, DigitsShape.sm)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = app.appName, fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                                Text(
                                    text = if (isEn) "Today ${app.sessionCount} · Quick ${app.shortSessionCount}"
                                    else "오늘 ${app.sessionCount}회 · 짧은 확인 ${app.shortSessionCount}회",
                                    fontSize = DigitsTextSize.tiny,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (app.lateNightUsageMinutes > 0) {
                                    Text(
                                        text = if (isEn) "Late night ${app.lateNightUsageMinutes}min" else "심야 사용 ${app.lateNightUsageMinutes}min",
                                        fontSize = DigitsTextSize.caption,
                                        color = ScoreOrangeText
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatInsightDuration(app.usageTimeMillis),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DigitsTextSize.body,
                                    color = ScoreRedText
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
            Text(text = if (isEn) "All Apps (Today since 05:00)" else "오늘 전체 앱 목록 (05:00 기준)", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge)
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
                            .background(MaterialTheme.colorScheme.surfaceVariant, DigitsShape.sm)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.appName, fontWeight = FontWeight.Medium, fontSize = DigitsTextSize.body)
                            Text(
                                text = "${app.categoryType.displayName} · ${if (isEn) "Today" else "오늘"} ${app.sessionCount}${com.digitscore.app.i18n.countUnit()}",
                                fontSize = DigitsTextSize.caption,
                                color = appRatingColor(app.categoryType)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatInsightDuration(app.usageTimeMillis),
                                fontWeight = FontWeight.Bold,
                                fontSize = DigitsTextSize.body
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
    onGradeChanged: (com.digitscore.app.model.AppGrade) -> Unit
) {
    val context = LocalContext.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    // 앱 목록 화면과 같은 4단계(권장/필수/일반/관리) 등급을 사용합니다.
    var selectedGrade by remember {
        mutableStateOf(
            com.digitscore.app.model.AppGrade.from(
                appUsage.categoryType,
                com.digitscore.app.model.AppCategoryPolicy.isStudyOrReadingPackage(appUsage.packageName, context)
            )
        )
    }
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
                    Text(text = appUsage.appName, fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.titleLarge)
                    Text(text = appUsage.packageName, fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                }
                Box {
                    TextButton(onClick = { categoryMenuExpanded = true }) {
                        Text(
                            selectedGrade.displayName,
                            fontSize = DigitsTextSize.caption,
                            color = appGradeColor(selectedGrade)
                        )
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = if (isEn) "Change category" else "균형 등급 변경",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        com.digitscore.app.model.AppGrade.entries.forEach { grade ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            grade.displayName,
                                            fontWeight = FontWeight.Bold,
                                            color = appGradeColor(grade)
                                        )
                                        Text(grade.description, fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                onClick = {
                                    selectedGrade = grade
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
                        shape = DigitsShape.md
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = if (isEn) "Today's Use (since 05:00)" else "오늘 사용 (05:00 기준)", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = formatInsightDuration(appUsage.usageTimeMillis),
                                    fontSize = DigitsTextSize.title,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (appUsage.lateNightUsageMinutes > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = if (isEn) "Late Night (00~05h)" else "심야(00~05시)", fontSize = DigitsTextSize.caption, color = ScoreOrangeText)
                                    Text(
                                        text = "${appUsage.lateNightUsageMinutes}min",
                                        fontSize = DigitsTextSize.title,
                                        fontWeight = FontWeight.Bold,
                                        color = ScoreOrangeText
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
                        fontSize = DigitsTextSize.tiny,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGradeChanged(selectedGrade) }
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
            Text(if (isEn) "No hourly usage records available yet." else "아직 분석할 시간대 기록이 없습니다.", fontSize = DigitsTextSize.label)
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
                fontSize = DigitsTextSize.label,
                fontWeight = FontWeight.Medium
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
                Text(if (isEn) "Recent sessions" else "최근 세션", fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.outline)
                Text(if (isEn) "Orange: ≥30min" else "30min 이상은 주황색", fontSize = DigitsTextSize.tiny, color = ScoreOrangeText)
            }
        }
        Text(
            if (isEn) "${sessions.size} sessions · avg ${formatInsightDuration(average)} · max ${formatInsightDuration(longest)}"
            else "${sessions.size}회 · 평균 ${formatInsightDuration(average)} · 최장 ${formatInsightDuration(longest)}",
            fontSize = DigitsTextSize.label,
            fontWeight = FontWeight.Medium
        )
        if (sessions.isNotEmpty()) {
            Text(
                if (isEn) "Opened ${sessions.size} times today, including ${shortOpens} quick checks."
                else "오늘 ${sessions.size}회 열었고, 그중 짧은 확인은 ${shortOpens}회입니다.",
                fontSize = DigitsTextSize.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            assessment,
            fontSize = DigitsTextSize.label,
            color = if (longest >= 30 * 60_000L) ScoreOrangeText else MaterialTheme.colorScheme.primary
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
            if (isEn) "Today: ${rollingSessions.size} opens · ${rollingShortCount} quick checks"
            else "오늘 ${rollingSessions.size}회 · 짧은 확인 ${rollingShortCount}회",
            fontSize = DigitsTextSize.label,
            fontWeight = FontWeight.Medium
        )
        if (rollingSessions.size >= 5) {
            val shortRatio = (rollingShortCount * 100f / rollingSessions.size).roundToInt()
            if (shortRatio >= 60) {
                Text(
                    if (isEn) "Quick checks account for ${shortRatio}% of opens. Mindful habit check recommended."
                    else "짧은 확인이 전체 실행의 ${shortRatio}%입니다. 무의식적 실행 흐름인지 살펴보세요.",
                    fontSize = DigitsTextSize.caption,
                    color = ScoreOrangeText
                )
            }
        }
        if (days.isEmpty()) {
            Text(if (isEn) "No app open records yet." else "아직 앱 실행 기록이 없습니다.", fontSize = DigitsTextSize.label)
            return@InsightCard
        }
        val maximum = (days.maxOfOrNull { it.sessionCount } ?: 0).coerceAtLeast(1)
        val totalColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .semantics {
                    contentDescription = UiTranslator.translate("최근 14일 앱 실행 횟수와 짧은 확인 그래프")
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
            Text(if (isEn) "Total opens" else "전체 실행", fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.primary)
            Text(if (isEn) "Orange: Quick checks" else "주황색 · 짧은 확인", fontSize = DigitsTextSize.tiny, color = ScoreOrangeText)
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
        if (get(Calendar.HOUR_OF_DAY) < 5) {
            add(Calendar.DAY_OF_YEAR, -1)
        }
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
            fontSize = DigitsTextSize.tiny,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isEn) "Measured on ${periodUsage.size} of last ${selectedDays} days" else "최근 ${selectedDays}일 중 ${periodUsage.size}일 측정",
                fontSize = DigitsTextSize.caption,
                color = MaterialTheme.colorScheme.outline
            )
            Box {
                TextButton(onClick = { periodMenuExpanded = true }) {
                    Text(periods.first { it.first == selectedDays }.second, fontSize = DigitsTextSize.caption)
                    Icon(
                        Icons.Rounded.ArrowDropDown,
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
        Text(trendText, fontSize = DigitsTextSize.label, color = MaterialTheme.colorScheme.primary)
        CompactBarChart(
            values = weekdayAverages,
            barColor = MaterialTheme.colorScheme.primary,
            contentDescription = UiTranslator.translate("요일별 평균 앱 사용량 그래프")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdayLabels.forEach { label ->
                Text(label, fontSize = DigitsTextSize.micro, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (busiestWeekday != null && weekdayAverages[busiestWeekday] > 0f) {
            Text(
                if (isEn) "Peak average usage on ${weekdayLabels[busiestWeekday]} · " +
                    formatInsightDuration((weekdayAverages[busiestWeekday] * 60_000).toLong()) + "."
                else "평균 사용이 가장 많은 요일은 ${weekdayLabels[busiestWeekday]}요일 · " +
                    formatInsightDuration((weekdayAverages[busiestWeekday] * 60_000).toLong()) + "입니다.",
                fontSize = DigitsTextSize.caption,
                fontWeight = FontWeight.Medium
            )
        }
        val today = insights.dailyUsage.lastOrNull { it.isToday }
        if (today != null) {
            Text(if (isEn) "Today ${formatInsightDuration(today.usageMillis)}" else "오늘 ${formatInsightDuration(today.usageMillis)}", fontSize = DigitsTextSize.caption, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InsightCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = DigitsShape.md
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontSize = DigitsTextSize.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
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
            Text(label, fontSize = DigitsTextSize.micro, color = MaterialTheme.colorScheme.outline)
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

/** 앱 목록 화면의 4단계 세그먼트 버튼과 같은 색을 씁니다. */
@Composable
internal fun appGradeColor(grade: com.digitscore.app.model.AppGrade): Color =
    com.digitscore.app.ui.theme.gradeAccentColor(grade)

@Composable
private fun appRatingColor(category: AppCategoryType): Color = when (category.canonical) {
    AppCategoryType.EXEMPT -> com.digitscore.app.ui.theme.gradeAccentColor(com.digitscore.app.model.AppGrade.TOOL)
    AppCategoryType.NEUTRAL -> MaterialTheme.colorScheme.outline
    AppCategoryType.DISTRACTING -> com.digitscore.app.ui.theme.gradeAccentColor(com.digitscore.app.model.AppGrade.LIMIT)
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

