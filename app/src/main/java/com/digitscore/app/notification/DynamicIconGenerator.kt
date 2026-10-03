package com.digitscore.app.notification

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

/** 상태바에 점수와 전원 버튼 형상을 함께 렌더링합니다. */
object DynamicIconGenerator {
    private const val RED = "#E74C3C"
    private const val ORANGE = "#E67E22"
    private const val YELLOW = "#F1C40F"
    private const val GREEN = "#2ECC71"

    fun createScoreBitmapIcon(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        // OS 축소 후에도 숫자와 둥근 끝이 선명하도록 충분히 큰 원본으로 그립니다.
        val size = (64 * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tierColor = scoreTierColor(normalizedScore)

        if (style == StatusIconStyle.BIG_NUMBER) {
            // 숫자 크기는 유지하면서 점수 구간색 하나로 숫자와 전원 실루엣을 묶습니다.
            // 밝은·어두운 시스템 모드별로 명도를 달리해 배경 대비를 확보합니다.
            val statusColor = statusIconScoreColor(context, normalizedScore)
            drawCoreNumberFrame(canvas, size, statusColor)
            drawScoreText(
                canvas = canvas,
                size = size,
                score = normalizedScore,
                centerX = size / 2f,
                centerY = size * 0.59f,
                maxWidth = size * 0.86f,
                scaleForDigits = floatArrayOf(0.90f, 0.82f, 0.58f),
                solidColor = statusColor
            )
            return bitmap
        }

        if (style == StatusIconStyle.NUMBER_FOCUS) {
            drawSeparatedPowerSymbol(canvas, size, tierColor)
            drawScoreText(
                canvas = canvas,
                size = size,
                score = normalizedScore,
                centerX = size * 0.69f,
                centerY = size * 0.52f,
                maxWidth = size * 0.60f,
                scaleForDigits = floatArrayOf(0.76f, 0.64f, 0.46f)
            )
            return bitmap
        }

        val strokeWidth = size * 0.085f
        val ringBounds = RectF(
            strokeWidth * 0.72f,
            strokeWidth * 0.72f,
            size - strokeWidth * 0.72f,
            size - strokeWidth * 0.72f
        )
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
        }

        // 위쪽 중앙을 기준으로 대칭인 76도 간격을 둔 전원 버튼 원호입니다.
        val arcStart = -52f
        val fullSweep = 284f
        when (style) {
            StatusIconStyle.SCORE_PROPORTION -> {
                ringPaint.color = Color.parseColor(RED)
                canvas.drawArc(ringBounds, arcStart, fullSweep, false, ringPaint)
                if (normalizedScore > 0) {
                    ringPaint.color = Color.parseColor(GREEN)
                    canvas.drawArc(
                        ringBounds,
                        arcStart,
                        fullSweep * normalizedScore / 100f,
                        false,
                        ringPaint
                    )
                }
            }
            StatusIconStyle.SCORE_TIER -> {
                ringPaint.color = tierColor
                canvas.drawArc(ringBounds, arcStart, fullSweep, false, ringPaint)
            }
            StatusIconStyle.BIG_NUMBER -> Unit // 위의 코어 숫자 전용 경로에서 반환됩니다.
            StatusIconStyle.NUMBER_FOCUS -> Unit // 위의 분리형 전용 경로에서 반환됩니다.
        }

        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tierColor
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
        }
        canvas.drawLine(size / 2f, size * 0.075f, size / 2f, size * 0.285f, stemPaint)

        drawScoreText(
            canvas = canvas,
            size = size,
            score = normalizedScore,
            centerX = size / 2f,
            centerY = size * 0.61f,
            maxWidth = size * 0.78f,
            scaleForDigits = floatArrayOf(0.68f, 0.58f, 0.43f)
        )
        return bitmap
    }

    private fun drawCoreNumberFrame(canvas: Canvas, size: Int, color: Int) {
        val strokeWidth = size * 0.047f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
        }

        // 위쪽을 넓게 비운 U자형 원호라 큰 숫자의 폭을 거의 침범하지 않습니다.
        // 상태바가 색을 단색으로 치환해도 전원 막대와 대칭 원호의 외곽은 남습니다.
        val bounds = RectF(
            size * 0.03f,
            size * 0.12f,
            size * 0.97f,
            size * 0.98f
        )
        canvas.drawArc(bounds, -38f, 256f, false, paint)
        canvas.drawLine(
            size * 0.5f,
            size * 0.02f,
            size * 0.5f,
            size * 0.17f,
            paint
        )
    }

    private fun drawSeparatedPowerSymbol(canvas: Canvas, size: Int, color: Int) {
        val strokeWidth = size * 0.065f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
        }
        val bounds = RectF(size * 0.045f, size * 0.31f, size * 0.35f, size * 0.615f)
        canvas.drawArc(bounds, -48f, 276f, false, paint)
        canvas.drawLine(size * 0.1975f, size * 0.25f, size * 0.1975f, size * 0.405f, paint)
    }

    private fun drawScoreText(
        canvas: Canvas,
        size: Int,
        score: Int,
        centerX: Float,
        centerY: Float,
        maxWidth: Float,
        scaleForDigits: FloatArray,
        @ColorInt solidColor: Int? = null
    ) {
        val text = score.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * scaleForDigits[(text.length - 1).coerceIn(0, 2)]
        }
        val measuredWidth = textPaint.measureText(text)
        if (measuredWidth > maxWidth) {
            textPaint.textSize *= maxWidth / measuredWidth
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val baseline = centerY - bounds.exactCenterY()

        if (solidColor != null) {
            textPaint.style = Paint.Style.FILL
            textPaint.color = solidColor
            canvas.drawText(text, centerX, baseline, textPaint)
            return
        }

        // 밝고 어두운 상태바 모두에서 숫자의 경계를 잃지 않도록 외곽선을 먼저 그립니다.
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = size * 0.035f
        textPaint.color = Color.argb(220, 20, 20, 20)
        canvas.drawText(text, centerX, baseline, textPaint)
        textPaint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        canvas.drawText(text, centerX, baseline, textPaint)
    }

    @ColorInt
    internal fun scoreTierColor(score: Int): Int = Color.parseColor(
        when {
            score >= 80 -> GREEN
            score >= 60 -> YELLOW
            score >= 40 -> ORANGE
            else -> RED
        }
    )

    @ColorInt
    internal fun statusIconScoreColor(context: Context, score: Int): Int =
        statusIconScoreColor(score, context.resources.configuration.uiMode)

    @ColorInt
    internal fun statusIconScoreColor(score: Int, uiMode: Int): Int {
        val darkMode = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return Color.parseColor(
            when {
                darkMode && score >= 80 -> "#49E291"
                darkMode && score >= 60 -> "#FFD54F"
                darkMode && score >= 40 -> "#FFA05A"
                darkMode -> "#FF6B62"
                score >= 80 -> "#087A46"
                score >= 60 -> "#9A6200"
                score >= 40 -> "#C34A00"
                else -> "#C62828"
            }
        )
    }

    fun createScoreIconCompat(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
    ): IconCompat = IconCompat.createWithBitmap(createScoreBitmapIcon(context, score, style))
}
