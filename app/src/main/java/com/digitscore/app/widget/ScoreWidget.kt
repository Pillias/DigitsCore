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
        setOf(DpSize(50.dp, 50.dp), DpSize(110.dp, 50.dp), DpSize(110.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance 작업은 서비스와 다른 시점에 실행될 수 있으므로 영구 snapshot을 우선합니다.
        // 이전 버전에서 snapshot이 아직 없는 경우에만 프로세스 Repository로 호환합니다.
        val persistedSnapshot = WidgetSnapshotStore.read(context)
        val scoreDetail = ScoreRepository.currentScoreDetail.value
        val rollingScoreDetail = ScoreRepository.rollingScoreDetail.value
        val settings = DigitsDatabase.getInstance(context).settingsDao().getSettings()
        val strings = AppLocale.stringsContext(context)
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
        val accessibility = strings.getString(
            R.string.widget_accessibility, score, localizedGrade, screenTime, unlockCount
        )

        provideContent {
            val currentSize = LocalSize.current
            WidgetSurface(backgroundStyle, palette) {
                when {
                    currentSize.width >= 105.dp && currentSize.height >= 105.dp -> LargeWidget(
                        gaugeBitmap, score, localizedGrade, screenTime, unlockCount, managedTime,
                        continuousMinutes, keyMessage, accessibility, palette, strings,
                        currentSize.width
                    )
                    currentSize.width >= 100.dp || currentSize.height >= 100.dp -> MediumWidget(
                        gaugeBitmap, score, localizedGrade, screenTime, unlockCount, accessibility, palette,
                        strings, vertical = currentSize.height > currentSize.width
                    )
                    else -> SmallWidget(gaugeBitmap, score, localizedGrade, accessibility, palette)
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
                Color.White, Color(0xFF17191D), Color(0xFF5F6368), Color(0xFFF3F0FA),
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
    accessibility: String,
    palette: WidgetPalette
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Gauge(gaugeBitmap, accessibility, 30)
        Spacer(GlanceModifier.height(1.dp))
        Text(
            text = score.toString(),
            style = widgetTextStyle(palette.accent, 16, FontWeight.Bold),
            maxLines = 1
        )
        Text(
            text = grade,
            style = widgetTextStyle(palette.primary, 9, FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun MediumWidget(
    gaugeBitmap: android.graphics.Bitmap,
    score: Int,
    grade: String,
    screenTime: String,
    unlockCount: Int,
    accessibility: String,
    palette: WidgetPalette,
    strings: Context,
    vertical: Boolean
) {
    if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Gauge(gaugeBitmap, accessibility, 34)
            Spacer(GlanceModifier.height(1.dp))
            Text(
                text = "${score}점",
                style = widgetTextStyle(palette.accent, 14, FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = grade,
                style = widgetTextStyle(palette.primary, 10, FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = screenTime,
                style = widgetTextStyle(palette.secondary, 9),
                maxLines = 1
            )
        }
    } else {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Gauge(gaugeBitmap, accessibility, 32)
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text = "${score}점",
                    style = widgetTextStyle(palette.accent, 11, FontWeight.Bold),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = grade,
                    style = widgetTextStyle(palette.accent, 11, FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text = "📱 $screenTime",
                    style = widgetTextStyle(palette.primary, 10, FontWeight.Medium),
                    maxLines = 1
                )
                Text(
                    text = "🔓 ${strings.getString(R.string.unlock_count_short, unlockCount)}",
                    style = widgetTextStyle(palette.secondary, 9),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LargeWidget(
    gaugeBitmap: android.graphics.Bitmap,
    score: Int,
    grade: String,
    screenTime: String,
    unlockCount: Int,
    managedTime: String,
    continuousMinutes: Long,
    keyMessage: String,
    accessibility: String,
    palette: WidgetPalette,
    strings: Context,
    widgetWidth: Dp
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Gauge(gaugeBitmap, accessibility, 46)
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(
                text = strings.getString(R.string.widget_index_24h),
                style = widgetTextStyle(palette.secondary, 10, FontWeight.Medium),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${score}점",
                    style = widgetTextStyle(palette.accent, 18, FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = grade,
                    style = widgetTextStyle(palette.primary, 12, FontWeight.Bold),
                    maxLines = 1
                )
            }
            Text(
                text = if (continuousMinutes > 0L) {
                    strings.getString(R.string.widget_continuous, continuousMinutes)
                } else {
                    strings.getString(R.string.widget_score_points, score)
                },
                style = widgetTextStyle(palette.secondary, 9),
                maxLines = 1
            )
        }
    }
    Spacer(GlanceModifier.height(6.dp))
    val metricWidth = ((widgetWidth - 20.dp) / 3).coerceAtLeast(28.dp)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        StatMetric(strings.getString(R.string.widget_screen), screenTime, palette, metricWidth)
        StatMetric(
            strings.getString(R.string.widget_unlocks),
            strings.getString(R.string.unlock_count_short, unlockCount),
            palette,
            metricWidth
        )
        StatMetric(strings.getString(R.string.widget_managed), managedTime, palette, metricWidth)
    }
    Spacer(GlanceModifier.height(6.dp))
    Text(
        text = keyMessage,
        modifier = GlanceModifier.fillMaxWidth().background(palette.chip).cornerRadius(9.dp).padding(7.dp),
        style = widgetTextStyle(palette.primary, 10, FontWeight.Medium),
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
private fun StatMetric(label: String, value: String, palette: WidgetPalette, width: Dp) {
    Column(
        modifier = GlanceModifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = widgetTextStyle(palette.secondary, 8),
            maxLines = 1
        )
        Text(
            value,
            style = widgetTextStyle(palette.primary, 10, FontWeight.Bold, TextAlign.Center),
            maxLines = 1
        )
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

private fun lightWidgetAccent(score: Int): Color = when {
    score >= 80 -> Color(0xFF14804A)
    score >= 60 -> Color(0xFFA86400)
    score >= 40 -> Color(0xFFC94F00)
    else -> Color(0xFFC62828)
}

private fun darkWidgetAccent(score: Int): Color = when {
    score >= 80 -> Color(0xFF49E291)
    score >= 60 -> Color(0xFFFFD54F)
    score >= 40 -> Color(0xFFFFA05A)
    else -> Color(0xFFFF6B62)
}

private fun formatMinutes(context: Context, totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> context.getString(R.string.format_hours_minutes, hours, minutes)
        hours > 0 -> context.getString(R.string.format_hours, hours)
        else -> context.getString(R.string.format_minutes, minutes)
    }
}
