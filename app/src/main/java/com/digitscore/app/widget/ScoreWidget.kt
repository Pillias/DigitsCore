package com.digitscore.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll as glanceUpdateAll
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
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.coachActionText
import com.digitscore.app.i18n.localizedGrade
import com.digitscore.app.model.ScoreTier
import com.digitscore.app.ui.MainActivity
import com.digitscore.app.ui.components.SalamanderGaugeBitmap

/**
 * 홈 화면 위젯 4종 (2026-10-28 정리). 한 위젯을 늘리고 줄이는 대신 크기별로 따로 둡니다.
 * - 1x1: 점수만
 * - 2x1: 도룡뇽 게이지 · 점수 · 단계
 * - 2x2: 게이지 · 점수 · 단계 + 할 일 한 줄
 * - 4x2: 게이지 · 점수 · 단계 + 오늘의 목표 3줄 + 할 일 한 줄
 * 글자는 크게, 핵심만 둡니다. 게이지는 앱 안 게이지와 같은 도룡뇽 그림입니다.
 */
enum class WidgetKind { SCORE_1X1, BAR_2X1, SQUARE_2X2, LARGE_4X2 }

abstract class DigitsScoreWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context, withGauge = kind != WidgetKind.SCORE_1X1)
        provideContent {
            WidgetSurface(data.style, data.palette, compact = kind == WidgetKind.SCORE_1X1 || kind == WidgetKind.BAR_2X1) {
                when (kind) {
                    WidgetKind.SCORE_1X1 -> ScoreOnly(data)
                    WidgetKind.BAR_2X1 -> Bar2x1(data)
                    WidgetKind.SQUARE_2X2 -> Square2x2(data)
                    WidgetKind.LARGE_4X2 -> Large4x2(data)
                }
            }
        }
    }
}

/** 기존에 홈 화면에 놓인 위젯(ScoreWidgetReceiver)은 2x2로 이어집니다. */
class ScoreWidget : DigitsScoreWidget(WidgetKind.SQUARE_2X2)
class ScoreWidgetSmall : DigitsScoreWidget(WidgetKind.SCORE_1X1)
class ScoreWidgetBar : DigitsScoreWidget(WidgetKind.BAR_2X1)
class ScoreWidgetLarge : DigitsScoreWidget(WidgetKind.LARGE_4X2)

class ScoreWidgetSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ScoreWidgetSmall() }
class ScoreWidgetBarReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ScoreWidgetBar() }
class ScoreWidgetLargeReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ScoreWidgetLarge() }

object DigitsWidgets {
    /** 위젯 4종을 모두 새로 그립니다. 한 종류가 실패해도 나머지는 그립니다. */
    suspend fun updateAll(context: Context) {
        listOf(ScoreWidget(), ScoreWidgetSmall(), ScoreWidgetBar(), ScoreWidgetLarge()).forEach { widget ->
            runCatching { widget.glanceUpdateAll(context) }
        }
    }
}

private class WidgetData(
    val score: Int,
    val grade: String,
    val gauge: android.graphics.Bitmap?,
    val action: String,
    val scoreTarget: Int,
    val appLabel: String,
    val appValue: String,
    val appOver: Boolean?,
    val unlockCount: Int,
    val unlockLimit: Int,
    val accessibility: String,
    val style: WidgetBackgroundStyle,
    val palette: WidgetPalette,
    val isEn: Boolean
) {
    companion object {
        suspend fun load(context: Context, withGauge: Boolean): WidgetData {
            val snap = WidgetSnapshotStore.read(context)
            val rolling = ScoreRepository.rollingScoreDetail.value
            val detail = ScoreRepository.currentScoreDetail.value
            val settings = DigitsDatabase.getInstance(context).settingsDao().getSettings()
            val strings = AppLocale.stringsContext(context)
            val isEn = AppLocale.isEnglish(context)
            val score = snap?.score ?: rolling?.finalScore ?: 75
            val grade = strings.localizedGrade(ScoreTier.fromScore(score))
            val unlockCount = snap?.unlockCount ?: ScoreRepository.currentUnlockCount.value
            val screenMinutes = snap?.screenMinutes ?: detail?.totalScreenTimeMinutes ?: 0L
            val managedMinutes = snap?.managedMinutes ?: detail?.distractingTimeMinutes ?: 0L
            val style = WidgetBackgroundStyle.fromId(settings?.widgetBackgroundStyleId)
            val stateText = strings.getString(
                when (snap?.flow ?: rolling?.flow) {
                    ScoreFlow.USING -> R.string.widget_state_using
                    ScoreFlow.RECOVERING -> R.string.widget_state_recovering
                    ScoreFlow.STEADY -> R.string.widget_state_steady
                    ScoreFlow.CALIBRATING, null -> R.string.widget_state_calibrating
                }
            )
            val action = snap?.let {
                strings.coachActionText(
                    com.digitscore.app.engine.CoachActionPlanner.plan(
                        recommendation = it.recommendation,
                        recoveryMinutes = it.recoveryMinutes,
                        recoveryTargetScore = it.recoveryTargetScore,
                        currentScore = score,
                        defenseLine = it.scoreTarget
                    )
                )
            } ?: stateText
            val targetApp = snap?.targetAppName.orEmpty()
            val appLabel = if (targetApp.isNotBlank()) targetApp else strings.getString(R.string.widget_managed)
            val appValue = if (targetApp.isNotBlank()) "${snap?.targetAppUsageMinutes ?: 0}/${snap?.targetAppLimitMinutes ?: 30}min"
                else "${managedMinutes}min"
            val appOver = if (targetApp.isNotBlank()) (snap?.targetAppUsageMinutes ?: 0) > (snap?.targetAppLimitMinutes ?: 30) else null
            return WidgetData(
                score = score,
                grade = grade,
                gauge = if (withGauge) SalamanderGaugeBitmap.create(context, score, 192) else null,
                action = action,
                scoreTarget = snap?.scoreTarget ?: 75,
                appLabel = appLabel,
                appValue = appValue,
                appOver = appOver,
                unlockCount = unlockCount,
                unlockLimit = snap?.unlockLimitTarget ?: 60,
                accessibility = strings.getString(R.string.widget_accessibility, score, grade, "${screenMinutes}min", unlockCount),
                style = style,
                palette = WidgetPalette.forStyle(style, score),
                isEn = isEn
            )
        }
    }
}

private data class WidgetPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val chip: Color,
    val accent: Color,
    val good: Color,
    val bad: Color
) {
    companion object {
        fun forStyle(style: WidgetBackgroundStyle, score: Int): WidgetPalette {
            val onDark = style != WidgetBackgroundStyle.WHITE
            val accent = Color(com.digitscore.app.ui.theme.ScorePalette.textArgb(score, onDark = onDark))
            val good = Color(com.digitscore.app.ui.theme.ScorePalette.textArgb(100, onDark = onDark))
            val bad = Color(com.digitscore.app.ui.theme.ScorePalette.textArgb(0, onDark = onDark))
            return when (style) {
                WidgetBackgroundStyle.DARK -> WidgetPalette(Color(0xFF1A1E24), Color.White, Color(0xFFD1D5DB), Color(0xFF282D35), accent, good, bad)
                WidgetBackgroundStyle.WHITE -> WidgetPalette(Color.White, Color(0xFF17191D), Color(0xFF5F6368), Color(0xFFF0F2F5), accent, good, bad)
                WidgetBackgroundStyle.TRANSPARENT -> WidgetPalette(Color.Transparent, Color.White, Color.White, Color(0xB3000000), accent, good, bad)
            }
        }
    }
}

@Composable
private fun WidgetSurface(style: WidgetBackgroundStyle, palette: WidgetPalette, compact: Boolean, content: @Composable () -> Unit) {
    var modifier = GlanceModifier.fillMaxSize().background(palette.background)
    if (style != WidgetBackgroundStyle.TRANSPARENT) modifier = modifier.cornerRadius(18.dp)
    modifier = modifier.padding(if (compact) 6.dp else 12.dp).clickable(actionStartActivity<MainActivity>())
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
private fun Gauge(data: WidgetData, size: Int) {
    data.gauge?.let {
        Image(ImageProvider(it), contentDescription = data.accessibility, modifier = GlanceModifier.size(size.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun ScoreAndGrade(data: WidgetData, scoreSp: Int, gradeSp: Int) {
    Column(verticalAlignment = Alignment.CenterVertically) {
        Text("${data.score}P", style = style(data.palette.accent, scoreSp, FontWeight.Bold), maxLines = 1)
        Text(data.grade, style = style(data.palette.primary, gradeSp, FontWeight.Bold), maxLines = 1)
    }
}

/** 1x1: 점수만 크게 */
@Composable
private fun ScoreOnly(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${data.score}", style = style(data.palette.accent, 30, FontWeight.Bold, TextAlign.Center), maxLines = 1)
    }
}

/** 2x1: 게이지 · 점수 · 단계 */
@Composable
private fun Bar2x1(data: WidgetData) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Gauge(data, 44)
        Spacer(GlanceModifier.width(8.dp))
        ScoreAndGrade(data, 24, 13)
    }
}

/** 2x2: 게이지 · 점수 · 단계 + 할 일 한 줄 */
@Composable
private fun Square2x2(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Gauge(data, 48)
            Spacer(GlanceModifier.width(6.dp))
            ScoreAndGrade(data, 26, 14)
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(data.action, style = style(data.palette.secondary, 12, FontWeight.Medium, TextAlign.Center), maxLines = 2)
    }
}

/** 4x2: 게이지 · 점수 · 단계 + 오늘의 목표 3줄 + 할 일 한 줄 */
@Composable
private fun Large4x2(data: WidgetData) {
    val p = data.palette
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Gauge(data, 60)
            Spacer(GlanceModifier.width(8.dp))
            ScoreAndGrade(data, 32, 14)
            Spacer(GlanceModifier.width(14.dp))
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                GoalLine(if (data.isEn) "Defense" else "방어선", "${data.score}/${data.scoreTarget}P", data.score >= data.scoreTarget, p)
                GoalLine(data.appLabel, data.appValue, data.appOver?.not(), p)
                GoalLine(if (data.isEn) "Unlocks" else "잠금 해제",
                    "${data.unlockCount}/${data.unlockLimit}${com.digitscore.app.i18n.countUnit()}", data.unlockCount <= data.unlockLimit, p)
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            data.action,
            modifier = GlanceModifier.fillMaxWidth().background(p.chip).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            style = style(p.primary, 12, FontWeight.Medium),
            maxLines = 1
        )
    }
}

@Composable
private fun GoalLine(label: String, value: String, met: Boolean?, p: WidgetPalette) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = GlanceModifier.defaultWeight(), style = style(p.secondary, 12), maxLines = 1)
        Text(value, style = style(when (met) { true -> p.good; false -> p.bad; null -> p.primary }, 13, FontWeight.Bold), maxLines = 1)
    }
}

private fun style(color: Color, sp: Int, weight: FontWeight? = null, align: TextAlign? = null) =
    TextStyle(color = ColorProvider(color), fontSize = sp.sp, fontWeight = weight, textAlign = align)
