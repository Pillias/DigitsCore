package com.digitscore.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.digitscore.app.R
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.engine.ScoreFlow
import com.digitscore.app.engine.ScoreGrade
import com.digitscore.app.engine.CoreIndexRecommendation
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.localizedGrade
import com.digitscore.app.notification.DynamicIconGenerator
import com.digitscore.app.notification.StatusIconStyle
import com.digitscore.app.ui.MainActivity

class ScoreWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(50.dp, 50.dp),    // 1x1 SmallWidget
            DpSize(110.dp, 110.dp),  // 2x2 Square2x2Widget (표준 정사각형)
            DpSize(185.dp, 70.dp),   // 3x1 / 4x1 WideBarWidget (가로 바)
            DpSize(240.dp, 135.dp)   // 4x2 LargeWidget (대형 종합)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance 작업은 서비스와 다른 시점에 실행될 수 있으므로 영구 snapshot을 우선합니다.
        // 이전 버전에서 snapshot이 아직 없는 경우에만 프로세스 Repository로 호환합니다.
        val persistedSnapshot = WidgetSnapshotStore.read(context)
        val scoreDetail = ScoreRepository.currentScoreDetail.value
        val rollingScoreDetail = ScoreRepository.rollingScoreDetail.value
        val settings = DigitsDatabase.getInstance(context).settingsDao().getSettings()
        val strings = AppLocale.stringsContext(context)
        val isEn = AppLocale.isEnglish(context)
        val unlockCount = persistedSnapshot?.unlockCount
            ?: ScoreRepository.currentUnlockCount.value
        val score = persistedSnapshot?.score
            ?: rollingScoreDetail?.finalScore
            ?: 75
        val grade = ScoreGrade.fromScore(score)
        val screenMinutes = persistedSnapshot?.screenMinutes
            ?: scoreDetail?.totalScreenTimeMinutes
            ?: 0L
        val managedMinutes = persistedSnapshot?.managedMinutes
            ?: scoreDetail?.distractingTimeMinutes
            ?: 0L
        val backgroundStyle = WidgetBackgroundStyle.fromId(settings?.widgetBackgroundStyleId)
        val palette = WidgetPalette.forStyle(backgroundStyle, score)
        val isDarkSurface = backgroundStyle != WidgetBackgroundStyle.WHITE
        val gaugeBitmap = DynamicIconGenerator.createPurePowerGaugeBitmap(
            context, score, 64
        )
        val screenTime = formatMinutes(strings, screenMinutes)
        val managedTime = formatMinutes(strings, managedMinutes)
        val localizedGrade = strings.localizedGrade(grade)
        val stateText = strings.getString(
            when (persistedSnapshot?.flow ?: rollingScoreDetail?.flow) {
                ScoreFlow.USING -> R.string.widget_state_using
                ScoreFlow.RECOVERING -> R.string.widget_state_recovering
                ScoreFlow.STEADY -> R.string.widget_state_steady
                ScoreFlow.CALIBRATING, null -> R.string.widget_state_calibrating
            }
        )
        val keyMessage = widgetKeyMessage(
            strings = strings,
            recommendation = persistedSnapshot?.recommendation,
            recoveryMinutes = persistedSnapshot?.recoveryMinutes,
            fallback = stateText
        )
        val continuousMinutes = persistedSnapshot?.continuousUsageMinutes
            ?: rollingScoreDetail?.continuousUsageMinutes
            ?: 0L
        val scoreTarget = persistedSnapshot?.scoreTarget ?: 75
        val targetAppName = persistedSnapshot?.targetAppName ?: ""
        val targetAppUsageMinutes = persistedSnapshot?.targetAppUsageMinutes ?: 0
        val targetAppLimitMinutes = persistedSnapshot?.targetAppLimitMinutes ?: 30
        val unlockLimitTarget = persistedSnapshot?.unlockLimitTarget ?: 60
        val recentScoresCsv = persistedSnapshot?.recentScoresCsv ?: ""
        val recentScores = recentScoresCsv.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }

        val sparkline2x2 = DynamicIconGenerator.createWidgetSparklineBitmap(
            scores = recentScores,
            currentScore = score,
            isDarkSurface = isDarkSurface,
            widthPx = 280,
            heightPx = 90,
            showMinMaxLabels = false
        )
        val sparklineWide = DynamicIconGenerator.createWidgetSparklineBitmap(
            scores = recentScores,
            currentScore = score,
            isDarkSurface = isDarkSurface,
            widthPx = 180,
            heightPx = 70,
            showMinMaxLabels = false
        )
        val sparklineLarge = DynamicIconGenerator.createWidgetSparklineBitmap(
            scores = recentScores,
            currentScore = score,
            isDarkSurface = isDarkSurface,
            widthPx = 280,
            heightPx = 100,
            showMinMaxLabels = true
        )

        val accessibility = strings.getString(
            R.string.widget_accessibility, score, localizedGrade, screenTime, unlockCount
        )

        provideContent {
            val currentSize = LocalSize.current
            WidgetSurface(backgroundStyle, palette) {
                when {
                    // 1. 4x2 대형 종합 위젯 (가로 185dp 이상 & 세로 125dp 이상)
                    currentSize.width >= 185.dp && currentSize.height >= 125.dp -> LargeWidget(
                        gaugeBitmap = gaugeBitmap,
                        sparklineBitmap = sparklineLarge,
                        score = score,
                        grade = localizedGrade,
                        screenTime = screenTime,
                        unlockCount = unlockCount,
                        managedTime = managedTime,
                        continuousMinutes = continuousMinutes,
                        scoreTarget = scoreTarget,
                        targetAppName = targetAppName,
                        targetAppUsageMinutes = targetAppUsageMinutes,
                        targetAppLimitMinutes = targetAppLimitMinutes,
                        unlockLimitTarget = unlockLimitTarget,
                        keyMessage = keyMessage,
                        accessibility = accessibility,
                        palette = palette,
                        strings = strings,
                        isEn = isEn,
                        widgetWidth = currentSize.width,
                        isDarkSurface = isDarkSurface
                    )
                    // 2. 3x1 / 4x1 가로 슬림 바 위젯 (가로 185dp 이상 & 세로 125dp 미만)
                    currentSize.width >= 185.dp -> WideBarWidget(
                        gaugeBitmap = gaugeBitmap,
                        sparklineBitmap = sparklineWide,
                        score = score,
                        grade = localizedGrade,
                        continuousMinutes = continuousMinutes,
                        scoreTarget = scoreTarget,
                        targetAppName = targetAppName,
                        targetAppUsageMinutes = targetAppUsageMinutes,
                        targetAppLimitMinutes = targetAppLimitMinutes,
                        screenMinutes = screenMinutes,
                        unlockCount = unlockCount,
                        unlockLimitTarget = unlockLimitTarget,
                        accessibility = accessibility,
                        palette = palette,
                        isEn = isEn
                    )
                    // 3. 2x2 정사각형 전용 위젯 (가로 100dp 이상 & 세로 95dp 이상)
                    currentSize.width >= 100.dp && currentSize.height >= 95.dp -> Square2x2Widget(
                        gaugeBitmap = gaugeBitmap,
                        sparklineBitmap = sparkline2x2,
                        score = score,
                        grade = localizedGrade,
                        continuousMinutes = continuousMinutes,
                        targetAppName = targetAppName,
                        targetAppUsageMinutes = targetAppUsageMinutes,
                        targetAppLimitMinutes = targetAppLimitMinutes,
                        screenMinutes = screenMinutes,
                        unlockCount = unlockCount,
                        unlockLimitTarget = unlockLimitTarget,
                        stateText = stateText,
                        accessibility = accessibility,
                        palette = palette,
                        isEn = isEn
                    )
                    // 4. 1x1 초소형 위젯 (그 외)
                    else -> SmallWidget(
                        gaugeBitmap = gaugeBitmap,
                        score = score,
                        grade = localizedGrade,
                        continuousMinutes = continuousMinutes,
                        accessibility = accessibility,
                        palette = palette
                    )
                }
            }
        }
    }
}

private data class WidgetPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val chip: Color,
    val accent: Color
) {
    companion object {
        fun forStyle(style: WidgetBackgroundStyle, score: Int): WidgetPalette = when (style) {
            WidgetBackgroundStyle.DARK -> WidgetPalette(
                Color(0xFF1A1E24), Color.White, Color(0xFFD1D5DB), Color(0xFF282D35),
                darkWidgetAccent(score)
            )
            WidgetBackgroundStyle.WHITE -> WidgetPalette(
                Color.White, Color(0xFF17191D), Color(0xFF5F6368), Color(0xFFF0F2F5),
                lightWidgetAccent(score)
            )
            WidgetBackgroundStyle.TRANSPARENT -> WidgetPalette(
                Color.Transparent, Color.White, Color.White, Color(0xB3000000),
                darkWidgetAccent(score)
            )
        }
    }
}

@Composable
private fun WidgetSurface(
    backgroundStyle: WidgetBackgroundStyle,
    palette: WidgetPalette,
    content: @Composable () -> Unit
) {
    var modifier = GlanceModifier
        .fillMaxSize()
        .background(palette.background)
    if (backgroundStyle != WidgetBackgroundStyle.TRANSPARENT) {
        modifier = modifier.cornerRadius(16.dp)
    }
    modifier = modifier.padding(8.dp).clickable(actionStartActivity<MainActivity>())
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

@Composable
private fun SmallWidget(
    gaugeBitmap: android.graphics.Bitmap,
    score: Int,
    grade: String,
    continuousMinutes: Long,
    accessibility: String,
    palette: WidgetPalette
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Gauge(gaugeBitmap, accessibility, 28)
        Spacer(GlanceModifier.height(1.dp))
        Text(
            text = "${score}P",
            style = widgetTextStyle(palette.accent, 14, FontWeight.Bold),
            maxLines = 1
        )
        Text(
            text = if (continuousMinutes > 0L) "${continuousMinutes}min" else grade,
            style = widgetTextStyle(palette.secondary, 9, FontWeight.Medium),
            maxLines = 1
        )
    }
}

@Composable
private fun Square2x2Widget(
    gaugeBitmap: android.graphics.Bitmap,
    sparklineBitmap: android.graphics.Bitmap,
    score: Int,
    grade: String,
    continuousMinutes: Long,
    targetAppName: String,
    targetAppUsageMinutes: Int,
    targetAppLimitMinutes: Int,
    screenMinutes: Long,
    unlockCount: Int,
    unlockLimitTarget: Int,
    stateText: String,
    accessibility: String,
    palette: WidgetPalette,
    isEn: Boolean
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 상단 헤더
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Gauge(gaugeBitmap, accessibility, 36)
            Spacer(GlanceModifier.width(7.dp))
            Column(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${score}P",
                        style = widgetTextStyle(palette.accent, 17, FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        text = grade,
                        style = widgetTextStyle(palette.primary, 11, FontWeight.Bold),
                        maxLines = 1
                    )
                }
                Spacer(GlanceModifier.height(1.dp))
                val subText = if (continuousMinutes > 0L) {
                    if (isEn) "${continuousMinutes}min continuous" else "연속 ${continuousMinutes}min"
                } else {
                    stateText
                }
                Text(
                    text = subText,
                    style = widgetTextStyle(palette.secondary, 9, FontWeight.Normal),
                    maxLines = 1
                )
            }
        }

        Spacer(GlanceModifier.height(3.dp))

        // 중단 (24시간 미니 추세선)
        Image(
            provider = ImageProvider(sparklineBitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(36.dp),
            contentScale = ContentScale.FillBounds
        )

        Spacer(GlanceModifier.height(3.dp))

        // 하단 풋터 (오늘의 목표 달성 현황 캡슐)
        val goalSummaryText = if (targetAppName.isNotBlank()) {
            val appStatus = if (targetAppUsageMinutes > targetAppLimitMinutes) (if (isEn) "Over" else "초과") else (if (isEn) "Met" else "달성")
            "$targetAppName ${targetAppUsageMinutes}/${targetAppLimitMinutes}min ($appStatus)"
        } else {
            if (isEn) {
                "Screen ${screenMinutes}min · ${unlockCount}/${unlockLimitTarget}x"
            } else {
                "화면 ${screenMinutes}min · 잠금 ${unlockCount}/${unlockLimitTarget}x"
            }
        }
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(palette.chip)
                .cornerRadius(7.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = goalSummaryText,
                style = widgetTextStyle(palette.primary, 9, FontWeight.Medium, TextAlign.Center),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WideBarWidget(
    gaugeBitmap: android.graphics.Bitmap,
    sparklineBitmap: android.graphics.Bitmap,
    score: Int,
    grade: String,
    continuousMinutes: Long,
    scoreTarget: Int,
    targetAppName: String,
    targetAppUsageMinutes: Int,
    targetAppLimitMinutes: Int,
    screenMinutes: Long,
    unlockCount: Int,
    unlockLimitTarget: Int,
    accessibility: String,
    palette: WidgetPalette,
    isEn: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 좌측: 게이지 + 점수 + 등급
        Gauge(gaugeBitmap, accessibility, 34)
        Spacer(GlanceModifier.width(6.dp))
        Column(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${score}P",
                    style = widgetTextStyle(palette.accent, 15, FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = grade,
                    style = widgetTextStyle(palette.primary, 10, FontWeight.Bold),
                    maxLines = 1
                )
            }
            Text(
                text = if (continuousMinutes > 0L) "${continuousMinutes}min" else if (isEn) "Target ${scoreTarget}P" else "목표 ${scoreTarget}P",
                style = widgetTextStyle(palette.secondary, 8, FontWeight.Normal),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.width(8.dp))

        // 중앙: 미니 스파크라인
        Image(
            provider = ImageProvider(sparklineBitmap),
            contentDescription = null,
            modifier = GlanceModifier.width(75.dp).height(28.dp),
            contentScale = ContentScale.FillBounds
        )

        Spacer(GlanceModifier.width(8.dp))

        // 우측: 오늘의 목표 요약 1줄 캡슐
        val appGoalPart = if (targetAppName.isNotBlank()) {
            "$targetAppName ${targetAppUsageMinutes}/${targetAppLimitMinutes}min"
        } else {
            "${screenMinutes}min"
        }
        val isScoreAchieved = score >= scoreTarget
        val scoreTag = if (isScoreAchieved) (if (isEn) "Met" else "달성") else (if (isEn) "Below" else "미달")
        val goalText = "${score}/${scoreTarget}P($scoreTag) · $appGoalPart · ${unlockCount}/${unlockLimitTarget}x"
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(palette.chip)
                .cornerRadius(6.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = goalText,
                style = widgetTextStyle(palette.primary, 9, FontWeight.Medium, TextAlign.Center),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LargeWidget(
    gaugeBitmap: android.graphics.Bitmap,
    sparklineBitmap: android.graphics.Bitmap,
    score: Int,
    grade: String,
    screenTime: String,
    unlockCount: Int,
    managedTime: String,
    continuousMinutes: Long,
    scoreTarget: Int,
    targetAppName: String,
    targetAppUsageMinutes: Int,
    targetAppLimitMinutes: Int,
    unlockLimitTarget: Int,
    keyMessage: String,
    accessibility: String,
    palette: WidgetPalette,
    strings: Context,
    isEn: Boolean,
    widgetWidth: Dp,
    isDarkSurface: Boolean = true
) {
    // 상단: 지수 정보 + 24시간 미니 스파크라인 나란히
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Gauge(gaugeBitmap, accessibility, 38)
        Spacer(GlanceModifier.width(7.dp))
        Column(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.getString(R.string.widget_index_24h),
                style = widgetTextStyle(palette.secondary, 9, FontWeight.Medium),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${score}P",
                    style = widgetTextStyle(palette.accent, 17, FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    text = grade,
                    style = widgetTextStyle(palette.primary, 11, FontWeight.Bold),
                    maxLines = 1
                )
            }
            Text(
                text = if (continuousMinutes > 0L) {
                    strings.getString(R.string.widget_continuous, continuousMinutes)
                } else {
                    if (isEn) "Target ${scoreTarget}P" else "목표 ${scoreTarget}P"
                },
                style = widgetTextStyle(palette.secondary, 8),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.width(10.dp))

        // 스파크라인 (우측)
        Image(
            provider = ImageProvider(sparklineBitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(36.dp),
            contentScale = ContentScale.FillBounds
        )
    }

    Spacer(GlanceModifier.height(5.dp))

    // 중단: 오늘의 목표 3분할 캡슐 통계 (달성 표시 포함)
    val metricWidth = ((widgetWidth - 28.dp) / 3).coerceAtLeast(28.dp)
    val appMetricLabel = if (targetAppName.isNotBlank()) targetAppName else strings.getString(R.string.widget_managed)
    val appMetricVal = if (targetAppName.isNotBlank()) "${targetAppUsageMinutes}/${targetAppLimitMinutes}min" else managedTime

    val isScoreAchieved = score >= scoreTarget
    val isAppExceeded = if (targetAppName.isNotBlank()) targetAppUsageMinutes > targetAppLimitMinutes else false
    val isUnlockExceeded = unlockCount > unlockLimitTarget

    val greenColor = Color(com.digitscore.app.ui.theme.ScorePalette.textArgb(100, onDark = isDarkSurface))
    val redColor = Color(com.digitscore.app.ui.theme.ScorePalette.textArgb(0, onDark = isDarkSurface))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatMetric(
            label = if (isEn) "Defense" else "지수 방어",
            value = "${score}/${scoreTarget}P",
            statusText = if (isScoreAchieved) (if (isEn) "Met" else "달성") else (if (isEn) "Below" else "미달"),
            statusColor = if (isScoreAchieved) greenColor else redColor,
            palette = palette,
            width = metricWidth
        )
        StatMetric(
            label = appMetricLabel,
            value = appMetricVal,
            statusText = if (targetAppName.isNotBlank()) {
                if (isAppExceeded) (if (isEn) "Over" else "초과") else (if (isEn) "Met" else "달성")
            } else null,
            statusColor = if (isAppExceeded) redColor else greenColor,
            palette = palette,
            width = metricWidth
        )
        StatMetric(
            label = strings.getString(R.string.widget_unlocks),
            value = "${unlockCount}/${unlockLimitTarget}x",
            statusText = if (isUnlockExceeded) (if (isEn) "Over" else "초과") else (if (isEn) "Met" else "달성"),
            statusColor = if (isUnlockExceeded) redColor else greenColor,
            palette = palette,
            width = metricWidth
        )
    }

    Spacer(GlanceModifier.height(5.dp))

    // 하단: 실시간 코칭 바
    Text(
        text = keyMessage,
        modifier = GlanceModifier.fillMaxWidth().background(palette.chip).cornerRadius(8.dp).padding(6.dp),
        style = widgetTextStyle(palette.primary, 9, FontWeight.Medium),
        maxLines = 2
    )
}

@Composable
private fun Gauge(bitmap: android.graphics.Bitmap, accessibility: String, size: Int) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = accessibility,
        modifier = GlanceModifier.size(size.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun StatMetric(
    label: String,
    value: String,
    statusText: String? = null,
    statusColor: Color? = null,
    palette: WidgetPalette,
    width: Dp
) {
    Column(
        modifier = GlanceModifier
            .width(width)
            .background(palette.chip)
            .cornerRadius(6.dp)
            .padding(vertical = 3.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = widgetTextStyle(palette.secondary, 8),
            maxLines = 1
        )
        Text(
            value,
            style = widgetTextStyle(palette.primary, 9, FontWeight.Bold, TextAlign.Center),
            maxLines = 1
        )
        if (statusText != null) {
            Text(
                statusText,
                style = widgetTextStyle(statusColor ?: palette.secondary, 8, FontWeight.Bold, TextAlign.Center),
                maxLines = 1
            )
        }
    }
}

private fun widgetTextStyle(
    color: Color,
    fontSize: Int,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null
) = TextStyle(
    color = ColorProvider(color),
    fontSize = fontSize.sp,
    fontWeight = fontWeight,
    textAlign = textAlign
)

private fun widgetKeyMessage(
    strings: Context,
    recommendation: CoreIndexRecommendation?,
    recoveryMinutes: Int?,
    fallback: String
): String = when (recommendation) {
    CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK ->
        strings.getString(R.string.widget_action_ten_minute_break)
    CoreIndexRecommendation.TAKE_QUIET_BREAK -> recoveryMinutes?.let {
        strings.getString(R.string.widget_action_recovery, it)
    } ?: strings.getString(R.string.widget_action_quiet_break)
    CoreIndexRecommendation.BATCH_PHONE_CHECKS ->
        strings.getString(R.string.widget_action_batch_checks)
    CoreIndexRecommendation.WIND_DOWN -> strings.getString(R.string.widget_action_wind_down)
    CoreIndexRecommendation.KEEP_BALANCE -> strings.getString(R.string.widget_action_keep_balance)
    null -> fallback
}

private fun lightWidgetAccent(score: Int): Color =
    Color(com.digitscore.app.ui.theme.ScorePalette.textArgb(score, onDark = false))

private fun darkWidgetAccent(score: Int): Color =
    Color(com.digitscore.app.ui.theme.ScorePalette.textArgb(score, onDark = true))

private fun formatMinutes(context: Context, totalMinutes: Long): String {
    return "${totalMinutes.coerceAtLeast(0L)}min"
}
