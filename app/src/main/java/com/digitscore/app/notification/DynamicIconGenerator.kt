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

/**
 * 상태바 알림 아이콘 및 위젯용 고해상도 동적 아이콘을 생성합니다.
 * 24dp 상태바 극소형 캔버스에서도 선명하게 보이도록 여백을 최소화(크기 140% 극대화)하고,
 * 볼드 스트로크와 외곽 고대비 컨투어 림을 적용하여 밝은/어두운 배경 모두에 대응합니다.
 */
object DynamicIconGenerator {
    private const val RED = "#FF5A5F"
    private const val ORANGE = "#E67E22"
    private const val YELLOW = "#F1C40F"
    private const val GREEN = "#35D07F"

    fun createScoreBitmapIcon(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        // OS 축소 후에도 선명하도록 충분히 큰 고해상도(최소 128px) 원본으로 그립니다.
        val size = (64 * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tierColor = scoreTierColor(normalizedScore)

        when (style) {
            StatusIconStyle.BIG_NUMBER -> {
                // 옵션 2: 볼드 전원 링 + 센터 대형 고대비 숫자
                drawBigNumberFrame(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size / 2f,
                    centerY = size * 0.62f,
                    maxWidth = size * 0.68f,
                    scaleForDigits = floatArrayOf(0.48f, 0.44f, 0.36f)
                )
            }
            StatusIconStyle.NUMBER_FOCUS -> {
                // 숫자 분리형: 좌측 미니 볼드 전원 심볼 + 우측 대형 고대비 숫자
                drawSeparatedPowerSymbol(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size * 0.67f,
                    centerY = size * 0.52f,
                    maxWidth = size * 0.56f,
                    scaleForDigits = floatArrayOf(0.56f, 0.48f, 0.38f)
                )
            }
            StatusIconStyle.SCORE_TIER -> {
                // 전원 단계형: 볼드 풀사이즈 전원 심볼 전체에 점수 티어 단일 색상 적용 (숫자 겹침 제거, 크기 극대화)
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = false)
            }
            StatusIconStyle.SCORE_PROPORTION -> {
                // 옵션 1 (기본 권장): 볼드 풀사이즈 전원 심볼 (빨간 베이스 + 녹색 점수 아크 + 티어 스템)
                // 여백을 최소화하여 24dp 상태바 캔버스를 140% 꽉 채우고, 외곽 컨투어로 밝은/어두운 배경 동시 대응
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = true)
            }
        }

        return bitmap
    }

    /**
     * 옵션 1 (기본 권장) / 전원 단계형:
     * 캔버스 꽉 찬 볼드 풀사이즈 전원 심볼을 렌더링합니다.
     * 여백 0% 최소화로 24dp 상태바에서 압도적인 크기를 가지며,
     * 외곽 반투명 섀도우 컨투어 림으로 흰색/검은색 배경 모두에서 선명합니다.
     */
    private fun drawFullPowerSymbol(
        canvas: Canvas,
        size: Int,
        score: Int,
        tierColor: Int,
        isProportionMode: Boolean
    ) {
        val cx = size / 2f
        val cy = size * 0.51f
        val strokeWidth = size * 0.14f
        val r = size * 0.38f
        val ringBounds = RectF(cx - r, cy - r, cx + r, cy + r)

        val arcStart = -50f
        val fullSweep = 280f
        val stemTop = size * 0.10f
        val stemBottom = cy - r * 0.05f

        // 1. 외곽 고대비 반투명 섀도우 컨투어 (라이트/다크 배경 동시 대비 확보)
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth * 1.25f
            color = Color.argb(90, 0, 0, 0)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, contourPaint)
        canvas.drawLine(cx, stemTop, cx, stemBottom, contourPaint)

        // 2. 원호 렌더링
        if (isProportionMode) {
            // 빨간 바탕 원호
            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = Color.parseColor(RED)
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep, false, redPaint)

            // 점수 비율 녹색 호
            if (score > 0) {
                val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    this.strokeWidth = strokeWidth
                    color = Color.parseColor(GREEN)
                }
                canvas.drawArc(ringBounds, arcStart, fullSweep * (score / 100f), false, greenPaint)
            }
        } else {
            // 티어 단일 색상 원호
            val tierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = tierColor
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep, false, tierPaint)
        }

        // 3. 상단 스템 (티어 색상)
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = tierColor
        }
        canvas.drawLine(cx, stemTop, cx, stemBottom, stemPaint)
    }

    /**
     * 옵션 2: 볼드 전원 링 프레임
     * 상단 스템을 컴팩트하게 축소하여 원 내부 공간을 확보하고,
     * 센터 숫자가 스템과 겹치지 않도록 구성합니다.
     */
    private fun drawBigNumberFrame(
        canvas: Canvas,
        size: Int,
        score: Int,
        tierColor: Int
    ) {
        val cx = size / 2f
        val cy = size * 0.52f
        val strokeWidth = size * 0.105f
        val r = size * 0.36f
        val ringBounds = RectF(cx - r, cy - r, cx + r, cy + r)

        val arcStart = -50f
        val fullSweep = 280f
        val stemTop = size * 0.06f
        val stemBottom = size * 0.22f

        // 고대비 컨투어 림
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth * 1.35f
            color = Color.argb(80, 0, 0, 0)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, contourPaint)
        canvas.drawLine(cx, stemTop, cx, stemBottom, contourPaint)

        // 빨간 바탕 원호
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = Color.parseColor(RED)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, redPaint)

        // 녹색 점수 호
        if (score > 0) {
            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = Color.parseColor(GREEN)
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep * (score / 100f), false, greenPaint)
        }

        // 컴팩트 스템
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = tierColor
        }
        canvas.drawLine(cx, stemTop, cx, stemBottom, stemPaint)
    }

    /**
     * 숫자 분리형: 좌측 미니 전원 심볼
     */
    private fun drawSeparatedPowerSymbol(
        canvas: Canvas,
        size: Int,
        score: Int,
        tierColor: Int
    ) {
        val cx = size * 0.23f
        val cy = size * 0.50f
        val strokeWidth = size * 0.085f
        val r = size * 0.19f
        val ringBounds = RectF(cx - r, cy - r, cx + r, cy + r)

        val arcStart = -50f
        val fullSweep = 280f
        val stemTop = cy - r * 1.15f
        val stemBottom = cy - r * 0.05f

        // 고대비 컨투어
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth * 1.35f
            color = Color.argb(80, 0, 0, 0)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, contourPaint)
        canvas.drawLine(cx, stemTop, cx, stemBottom, contourPaint)

        // 빨간 바탕
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = Color.parseColor(RED)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, redPaint)

        // 녹색 점수 호
        if (score > 0) {
            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = Color.parseColor(GREEN)
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep * (score / 100f), false, greenPaint)
        }

        // 스템
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = tierColor
        }
        canvas.drawLine(cx, stemTop, cx, stemBottom, stemPaint)
    }

    /**
     * 밝고 어두운 상태바 배경 모두에서 숫자가 선명하도록 굵은 다크 외곽선과 화이트 채움을 함께 그립니다.
     */
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

        // 굵은 다크 아웃라인 (흰 배경 및 밝은 상태바에서도 또렷한 경계선 형성)
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = size * 0.08f
        textPaint.strokeJoin = Paint.Join.ROUND
        textPaint.color = Color.argb(220, 15, 15, 15)
        canvas.drawText(text, centerX, baseline, textPaint)

        // 화이트 채움
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

    /**
     * 위젯 전용: 숫자 텍스트가 겹치지 않는 순수 전원 버튼(Power Symbol) 게이지 비트맵을 생성합니다.
     * 상태바의 풀사이즈 전원 심볼(옵션 1)과 100% 동일한 비례와 색상을 공유합니다.
     */
    fun createPurePowerGaugeBitmap(
        context: Context,
        score: Int,
        sizeDp: Int = 64
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        val size = (sizeDp * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tierColor = scoreTierColor(normalizedScore)

        drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = true)
        return bitmap
    }

    /**
     * 알림창 대형 아이콘(Large Icon)을 생성합니다.
     * Samsung OneUI 및 Android 12+의 알림창 좌측 원형 슬롯에 표시되며,
     * 고유 브랜드 다크 원형 배경(#101820) 위에 실시간 점수(%) 게이지 또는 숫자를 렌더링합니다.
     */
    fun createScoreLargeIcon(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        val size = (64 * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 브랜드 다크 원형 배경 (#101820)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#101820")
        }
        val cx = size / 2f
        val cy = size / 2f
        canvas.drawCircle(cx, cy, size / 2f, bgPaint)

        val tierColor = scoreTierColor(normalizedScore)
        when (style) {
            StatusIconStyle.BIG_NUMBER -> {
                drawBigNumberFrame(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size / 2f,
                    centerY = size * 0.62f,
                    maxWidth = size * 0.68f,
                    scaleForDigits = floatArrayOf(0.48f, 0.44f, 0.36f)
                )
            }
            StatusIconStyle.NUMBER_FOCUS -> {
                drawSeparatedPowerSymbol(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size * 0.67f,
                    centerY = size * 0.52f,
                    maxWidth = size * 0.56f,
                    scaleForDigits = floatArrayOf(0.56f, 0.48f, 0.38f)
                )
            }
            StatusIconStyle.SCORE_TIER -> {
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = false)
            }
            StatusIconStyle.SCORE_PROPORTION -> {
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = true)
            }
        }

        return bitmap
    }
}
