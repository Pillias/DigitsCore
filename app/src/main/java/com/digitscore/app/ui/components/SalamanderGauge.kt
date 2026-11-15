package com.digitscore.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.digitscore.app.R
import com.digitscore.app.model.ScoreTier
import com.digitscore.app.ui.theme.SalamanderColors
import kotlin.math.min

/**
 * 앱 아이콘과 같은 '빨간 링을 먹어 가는 도룡뇽' 지수 게이지.
 * 몸(초록)은 지수만큼 링을 덮고, 머리는 앱 아이콘 4종의 머리 그림을 그대로 잘라 쓴 것(drawable-nodpi/gauge_head_*)을
 * 링을 따라 돌려 몸 끝에 붙입니다. 좌표 단위는 아이콘 전경 이미지(432px)의 픽셀입니다.
 * 머리 그림을 다시 만들 때: scripts/art/gauge_heads.py
 */
private val C = Offset(216f, 218f)   // 링 중심
private const val RING_R = 85.5f          // 링 반지름
private const val W = 29f            // 링 두께
private const val BACK = 34f         // 머리 그림이 덮는 목 뒤 각도

class GaugeHeads(val images: Map<ScoreTier, ImageBitmap>)

private data class HeadSpec(val left: Float, val top: Float, val ringCx: Float, val ringCy: Float, val mouth: Float)

private val SPECS = mapOf(
    ScoreTier.STABLE to HeadSpec(104f, 190f, 217.1f, 219.2f, 190f),
    ScoreTier.FAIR to HeadSpec(123f, 248f, 217.1f, 217.9f, 150f),
    ScoreTier.CAUTION to HeadSpec(215f, 252f, 215.9f, 217.2f, 76f),
    ScoreTier.REST to HeadSpec(262f, 194f, 215.0f, 218.4f, 24f)
)

@Composable
fun rememberGaugeHeads(): GaugeHeads = GaugeHeads(
    mapOf(
        ScoreTier.STABLE to ImageBitmap.imageResource(R.drawable.gauge_head_stable),
        ScoreTier.FAIR to ImageBitmap.imageResource(R.drawable.gauge_head_fair),
        ScoreTier.CAUTION to ImageBitmap.imageResource(R.drawable.gauge_head_caution),
        ScoreTier.REST to ImageBitmap.imageResource(R.drawable.gauge_head_rest)
    )
)

private fun DrawScope.ringArc(from: Float, to: Float, color: Color, width: Float, r: Float = RING_R) {
    if (to - from <= 0.5f) return
    drawArc(
        color = color, startAngle = from, sweepAngle = to - from, useCenter = false,
        topLeft = Offset(C.x - r, C.y - r), size = Size(r * 2, r * 2),
        style = Stroke(width = width, cap = StrokeCap.Round)
    )
}

fun DrawScope.drawSalamanderGauge(score: Float, tier: ScoreTier, heads: GaugeHeads) {
    val tone = when (tier) {
        ScoreTier.STABLE -> SalamanderColors.Stable
        ScoreTier.FAIR -> SalamanderColors.Fair
        ScoreTier.CAUTION -> SalamanderColors.Caution
        ScoreTier.REST -> SalamanderColors.Rest
    }
    val spec = SPECS.getValue(tier)
    val u = min(size.width, size.height) / 236f
    withTransform({
        translate(size.width / 2f - C.x * u, size.height / 2f - (C.y - 8f) * u)
        scale(u, u, Offset.Zero)
    }) {
        val mouth = -60f + 300f * score.coerceIn(6f, 100f) / 100f
        val neck = mouth - BACK + 4f
        ringArc(-60f, 240f, SalamanderColors.Ring, W)                 // 남은 빨간 링
        ringArc(-60f, neck, tone.rim, W)                              // 몸
        ringArc(-60f, neck, tone.body, W - 7f, RING_R - 1.5f)
        if (neck > -40f) ringArc(-48f, neck - 2f, tone.stripe.copy(alpha = 0.8f), 4f, RING_R - 6.5f) // 등줄기 선
        // 머리: 아이콘의 머리 그림을 링 중심 기준으로 돌려 입이 지수 위치에 오게 함
        heads.images[tier]?.let { img ->
            rotate(mouth - spec.mouth, C) {
                drawImage(
                    image = img,
                    dstOffset = IntOffset((spec.left - spec.ringCx + C.x).toInt(), (spec.top - spec.ringCy + C.y).toInt()),
                    dstSize = IntSize(img.width, img.height),
                    filterQuality = FilterQuality.High
                )
            }
        }
        // 가운데 막대 (단계 색)
        drawRoundRect(tone.stem, Offset(C.x - 13f, 114f), Size(26f, 64f), CornerRadius(13f, 13f))
    }
}

/** 앱 밖(위젯·알림)에서 쓰는 도룡뇽 게이지 그림. 대시보드 게이지와 같은 그리기 코드를 씁니다. */
object SalamanderGaugeBitmap {
    @Volatile private var cached: GaugeHeads? = null

    private fun heads(context: android.content.Context): GaugeHeads = cached ?: run {
        val res = context.resources
        fun load(id: Int) = android.graphics.BitmapFactory.decodeResource(res, id,
            android.graphics.BitmapFactory.Options().apply { inScaled = false }).asImageBitmap()
        GaugeHeads(
            mapOf(
                ScoreTier.STABLE to load(R.drawable.gauge_head_stable),
                ScoreTier.FAIR to load(R.drawable.gauge_head_fair),
                ScoreTier.CAUTION to load(R.drawable.gauge_head_caution),
                ScoreTier.REST to load(R.drawable.gauge_head_rest)
            )
        ).also { cached = it }
    }

    fun create(context: android.content.Context, score: Int, sizePx: Int): android.graphics.Bitmap {
        val image = ImageBitmap(sizePx, sizePx)
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        scope.draw(
            androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(image), Size(sizePx.toFloat(), sizePx.toFloat())
        ) {
            drawSalamanderGauge(score.coerceIn(0, 100).toFloat(), ScoreTier.fromScore(score.coerceIn(1, 100)), heads(context))
        }
        return image.asAndroidBitmap()
    }
}
