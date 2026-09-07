package com.digitscore.app.notification

import android.content.Context
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
        style: StatusIconStyle = StatusIconStyle.SCORE_TIER
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        // OS 축소 후에도 숫자와 둥근 끝이 선명하도록 충분히 큰 원본으로 그립니다.
        val size = (64 * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tierColor = scoreTierColor(normalizedScore)

        val strokeWidth = size * 0.105f
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
        }

        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tierColor
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
        }
        canvas.drawLine(size / 2f, size * 0.075f, size / 2f, size * 0.285f, stemPaint)

        drawScoreText(canvas, size, normalizedScore)
        return bitmap
    }

    private fun drawScoreText(canvas: Canvas, size: Int, score: Int) {
        val text = score.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * when (text.length) {
                1 -> 0.52f
                2 -> 0.47f
                else -> 0.36f
            }
        }
        val maxWidth = size * 0.70f
        val measuredWidth = textPaint.measureText(text)
        if (measuredWidth > maxWidth) {
            textPaint.textSize *= maxWidth / measuredWidth
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val centerY = size * 0.61f
        canvas.drawText(text, size / 2f, centerY - bounds.exactCenterY(), textPaint)
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

    fun createScoreIconCompat(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_TIER
    ): IconCompat = IconCompat.createWithBitmap(createScoreBitmapIcon(context, score, style))
}
