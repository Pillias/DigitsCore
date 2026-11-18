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
 * 홈 화면 위젯 (2026-11-14: 크기별 4종 → 하나로 통합). 위젯 하나를 놓고 늘리거나 줄이면 크기에 맞는 내용이 나옵니다.
 * - 1칸: 도룡뇽 게이지 · 점수
 * - 가로 2칸: 도룡뇽 게이지 · 점수 · 단계
 * - 2x2: 게이지 · 점수 · 단계 + 할 일 한 줄
 * - 4x2 이상: 게이지 · 점수 · 단계 + 오늘의 목표 3줄 + 할 일 한 줄
 * 글자는 크게, 핵심만 둡니다. 게이지는 앱 안 게이지와 같은 도룡뇽 그림입니다.
 */
enum class WidgetKind { SCORE_1X1, TALL_1X2, BAR_2X1, SQUARE_2X2, LARGE_4X2 }

internal object WidgetSizes {
    val SMALL = DpSize(40.dp, 40.dp)
    val TALL = DpSize(40.dp, 110.dp)
    val BAR = DpSize(110.dp, 40.dp)
    val SQUARE = DpSize(110.dp, 110.dp)
    val LARGE = DpSize(250.dp, 110.dp)
    val ALL = setOf(SMALL, TALL, BAR, SQUARE, LARGE)

    /** 지금 크기에 맞는 내용. 런처마다 칸 크기가 달라 경계는 넉넉히 둡니다. */
    fun kindFor(size: DpSize): WidgetKind = when {
        size.width >= LARGE.width && size.height >= LARGE.height -> WidgetKind.LARGE_4X2
        size.width >= SQUARE.width && size.height >= SQUARE.height -> WidgetKind.SQUARE_2X2
        size.width >= BAR.width -> WidgetKind.BAR_2X1
        size.height >= TALL.height -> WidgetKind.TALL_1X2
        else -> WidgetKind.SCORE_1X1
    }
}

open class DigitsScoreWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            val kind = WidgetSizes.kindFor(LocalSize.current)
            val compact = kind == WidgetKind.SCORE_1X1 || kind == WidgetKind.TALL_1X2 || kind == WidgetKind.BAR_2X1
            WidgetSurface(data.style, data.palette, compact = compact) {
                when (kind) {
                    WidgetKind.SCORE_1X1 -> ScoreOnly(data)
                    WidgetKind.TALL_1X2 -> Tall1x2(data)
                    WidgetKind.BAR_2X1 -> Bar2x1(data)
                    WidgetKind.SQUARE_2X2 -> Square2x2(data)
                    WidgetKind.LARGE_4X2 -> Large4x2(data)
                }
            }
        }
    }
}

/** 위젯 선택 화면에 나오는 단 하나의 위젯. */
class ScoreWidget : DigitsScoreWidget()

// 이전 버전에서 크기별로 놓아 둔 위젯이 사라지지 않게 남겨 둡니다(선택 화면에서는 숨김). 내용은 같고 크기 조절도 됩니다.
class ScoreWidgetSmall : DigitsScoreWidget()
class ScoreWidgetBar : DigitsScoreWidget()
class ScoreWidgetLarge : DigitsScoreWidget()

class ScoreWidgetSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ScoreWidgetSmall() }
class ScoreWidgetBarReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ScoreWidgetBar() }
class ScoreWidgetLargeReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ScoreWidgetLarge() }

object DigitsWidgets {
    /** 놓여 있는 위젯을 모두 새로 그립니다. 한 종류가 실패해도 나머지는 그립니다. */
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
        suspend fun load(context: Context): WidgetData {
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
                gauge = SalamanderGaugeBitmap.create(context, score, 192),
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
    modifier = modifier.padding(if (compact) 4.dp else 12.dp).clickable(actionStartActivity<MainActivity>())
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

/**
 * 1칸: 도룡뇽 아이콘과 점수. Responsive 모드의 LocalSize는 실제 칸이 아니라 경계값(40dp)이라
 * 크기를 고정합니다. 좁은 런처 칸(약 56dp)에서도 잘리지 않는 크기입니다.
 */
@Composable
private fun ScoreOnly(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Gauge(data, 32)
        Text("${data.score}", style = style(data.palette.accent, 20, FontWeight.Bold, TextAlign.Center), maxLines = 1)
    }
}

/** 세로 1x2: 게이지 아래 점수 */
@Composable
private fun Tall1x2(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Gauge(data, 36)
        Spacer(GlanceModifier.height(4.dp))
        Text("${data.score}", style = style(data.palette.accent, 22, FontWeight.Bold, TextAlign.Center), maxLines = 1)
        Text(data.grade, style = style(data.palette.primary, 10, FontWeight.Medium, TextAlign.Center), maxLines = 1)
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
