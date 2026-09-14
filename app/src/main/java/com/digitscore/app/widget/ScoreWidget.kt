package com.digitscore.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.digitscore.app.R
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.engine.ScoreFlow
import com.digitscore.app.engine.ScoreGrade
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
            ?: 80
        val grade = ScoreGrade.fromScore(score)
        val screenMinutes = persistedSnapshot?.screenMinutes
            ?: scoreDetail?.totalScreenTimeMinutes
            ?: 0L
        val managedMinutes = persistedSnapshot?.managedMinutes
            ?: scoreDetail?.distractingTimeMinutes
            ?: 0L
        val backgroundStyle = WidgetBackgroundStyle.fromId(settings?.widgetBackgroundStyleId)
        val palette = WidgetPalette.forStyle(backgroundStyle)
        val scoreBitmap = DynamicIconGenerator.createScoreBitmapIcon(
            context, score, StatusIconStyle.SCORE_PROPORTION
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
        val accessibility = strings.getString(
            R.string.widget_accessibility, score, localizedGrade, screenTime, unlockCount
        )

        provideContent {
            val currentSize = LocalSize.current
            WidgetSurface(backgroundStyle, palette) {
                when {
                    currentSize.width >= 105.dp && currentSize.height >= 105.dp -> LargeWidget(
                        scoreBitmap, score, localizedGrade, screenTime, unlockCount, managedTime,
                        stateText, accessibility, palette, strings
                    )
                    currentSize.width >= 100.dp || currentSize.height >= 100.dp -> MediumWidget(
                        scoreBitmap, localizedGrade, screenTime, accessibility, palette,
                        vertical = currentSize.height > currentSize.width
                    )
                    else -> SmallWidget(scoreBitmap, accessibility)
                }
            }
        }
    }
}

private data class WidgetPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val chip: Color
) {
    companion object {
        fun forStyle(style: WidgetBackgroundStyle): WidgetPalette = when (style) {
            WidgetBackgroundStyle.DARK -> WidgetPalette(
                Color(0xFF1A1E24), Color.White, Color(0xFFD1D5DB), Color(0xFF282D35)
            )
            WidgetBackgroundStyle.WHITE -> WidgetPalette(
                Color.White, Color(0xFF17191D), Color(0xFF5F6368), Color(0xFFF1F3F4)
            )
            WidgetBackgroundStyle.TRANSPARENT -> WidgetPalette(
                Color.Transparent, Color.White, Color.White, Color(0x99000000)
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
private fun SmallWidget(scoreBitmap: android.graphics.Bitmap, accessibility: String) {
    Image(
        provider = ImageProvider(scoreBitmap),
        contentDescription = accessibility,
        modifier = GlanceModifier.size(48.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun MediumWidget(
    scoreBitmap: android.graphics.Bitmap,
    grade: String,
    screenTime: String,
    accessibility: String,
    palette: WidgetPalette,
    vertical: Boolean
) {
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Gauge(scoreBitmap, accessibility, 48)
            Text(grade, style = widgetTextStyle(palette.primary, 11, FontWeight.Bold), maxLines = 1)
            Text(screenTime, style = widgetTextStyle(palette.secondary, 10), maxLines = 1)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Gauge(scoreBitmap, accessibility, 48)
            Spacer(GlanceModifier.width(6.dp))
            Column {
                Text(grade, style = widgetTextStyle(palette.primary, 11, FontWeight.Bold), maxLines = 1)
                Text(screenTime, style = widgetTextStyle(palette.secondary, 10), maxLines = 1)
            }
        }
    }
}

@Composable
private fun LargeWidget(
    scoreBitmap: android.graphics.Bitmap,
    score: Int,
    grade: String,
    screenTime: String,
    unlockCount: Int,
    managedTime: String,
    stateText: String,
    accessibility: String,
    palette: WidgetPalette,
    strings: Context
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Gauge(scoreBitmap, accessibility, 62)
        Spacer(GlanceModifier.width(6.dp))
        Column {
            Text(strings.getString(R.string.widget_index), style = widgetTextStyle(palette.secondary, 10), maxLines = 1)
            Text("$score · $grade", style = widgetTextStyle(palette.primary, 12, FontWeight.Bold), maxLines = 1)
        }
    }
    Spacer(GlanceModifier.height(5.dp))
    StatLine(strings.getString(R.string.widget_screen), screenTime, palette)
    StatLine(
        strings.getString(R.string.widget_unlocks),
        strings.getString(R.string.unlock_count_short, unlockCount),
        palette
    )
    StatLine(strings.getString(R.string.widget_managed), managedTime, palette)
    Spacer(GlanceModifier.height(4.dp))
    Text(
        stateText,
        modifier = GlanceModifier.fillMaxWidth().background(palette.chip).cornerRadius(7.dp).padding(5.dp),
        style = widgetTextStyle(palette.primary, 9),
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
private fun StatLine(label: String, value: String, palette: WidgetPalette) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text("$label  ", style = widgetTextStyle(palette.secondary, 9), maxLines = 1)
        Text(value, style = widgetTextStyle(palette.primary, 10, FontWeight.Bold), maxLines = 1)
    }
}

private fun widgetTextStyle(
    color: Color,
    fontSize: Int,
    fontWeight: FontWeight? = null
) = TextStyle(color = ColorProvider(color), fontSize = fontSize.sp, fontWeight = fontWeight)

private fun formatMinutes(context: Context, totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> context.getString(R.string.format_hours_minutes, hours, minutes)
        hours > 0 -> context.getString(R.string.format_hours, hours)
        else -> context.getString(R.string.format_minutes, minutes)
    }
}
