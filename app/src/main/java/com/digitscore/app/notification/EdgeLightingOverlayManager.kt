package com.digitscore.app.notification

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.Locale

object EdgeLightingOverlayManager {

    /** InputManager의 기본 최대 가림 불투명도(0.8). 이 값 이하여야 아래 앱으로 터치가 전달됩니다. */
    private const val MAX_PASS_THROUGH_WINDOW_ALPHA = 0.8f

    private const val PREFS_NAME = "edge_lighting_prefs"
    private const val KEY_ENABLED = "edge_lighting_enabled"
    private const val KEY_MANAGED_MINUTES = "edge_lighting_managed_minutes"
    private const val KEY_STUDY_MINUTES = "edge_lighting_study_minutes"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeOverlayView: View? = null
    private var activeAnimator: ValueAnimator? = null
    private var watchdogRunnable: Runnable? = null

    /**
     * 외부에서 언제든(화면 OFF, 홈 화면 이동, 서비스 종료 시) 즉시 오버레이를 닫습니다.
     */
    fun dismiss(context: Context, reason: String = "external") {
        if (activeOverlayView != null) android.util.Log.d("EdgeLighting", "dismiss: $reason")
        mainHandler.post {
            try {
                val appCtx = context.applicationContext
                val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                dismissCurrent(wm)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 화면 신호등 알림 활성화 여부를 확인합니다.
     * 기본값은 true입니다.
     */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    /**
     * 화면 신호등 알림 활성화 설정을 저장합니다.
     * 만약 false로 설정되면 현재 표시 중인 오버레이를 즉시 닫습니다.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            dismiss(context)
        }
    }

    /**
     * 관리 앱 (빨간불) 경고 발동 시간(분)을 가져옵니다. 기본값 30분.
     */
    fun getManagedTriggerMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MANAGED_MINUTES, 30).coerceIn(1, 180)
    }

    /**
     * 관리 앱 (빨간불) 경고 발동 시간(분)을 저장합니다.
     */
    fun setManagedTriggerMinutes(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MANAGED_MINUTES, minutes.coerceIn(1, 180)).apply()
    }

    /**
     * 공부/독서 앱 (초록불) 경고 발동 시간(분)을 가져옵니다. 기본값 50분.
     */
    fun getStudyTriggerMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_STUDY_MINUTES, 50).coerceIn(1, 180)
    }

    /**
     * 공부/독서 앱 (초록불) 경고 발동 시간(분)을 저장합니다.
     */
    fun setStudyTriggerMinutes(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_STUDY_MINUTES, minutes.coerceIn(1, 180)).apply()
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    /**
     * 화면 최외곽(0px 물리 베젤)에서 안쪽으로 은은하게 번지는 화면 신호등 간접조명(Ambient Glow)을 표시합니다.
     *
     * @param context Context
     * @param isStudyGreen true: 공부 도달 (에메랄드 초록불 #00E676), false: 관리앱 도달 (경고 빨간불 #FF5252)
     * @param isPreview true: 설정 화면 미리보기 (설정 ON/OFF 여부 무관하게 권한만 있으면 즉시 표시), false: 백그라운드 자동 발동
     * @param labelText 우측 상단 모서리에 표시할 캡슐 배지 안내 문구 (null 시 기본 문구 사용)
     */
    fun showEdgeWarning(
        context: Context,
        isStudyGreen: Boolean,
        isPreview: Boolean = false,
        labelText: String? = null
    ) {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) {
            return
        }
        if (!isPreview && !isEnabled(appContext)) {
            return
        }

        mainHandler.post {
            try {
                val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

                android.util.Log.d("EdgeLighting", "show: green=$isStudyGreen preview=$isPreview replacing=${activeOverlayView != null}")
                // 기존 오버레이가 있다면 안전하게 제거
                dismissCurrent(windowManager)

                val density = appContext.resources.displayMetrics.density
                val baseColor = if (isStudyGreen) {
                    com.digitscore.app.ui.theme.ScorePalette.FILL[0].toInt() // 초록불 (점수 색표와 같은 초록)
                } else {
                    com.digitscore.app.ui.theme.ScorePalette.FILL[3].toInt() // 빨간불 (점수 색표와 같은 빨강)
                }

                val glowDepth = 38f * density
                val cornerRadius = 38f * density
                val rimStrokeWidth = 28f * density
                val rimBlurRadius = 22f * density

                val badgeText = if (!labelText.isNullOrBlank()) {
                    labelText
                } else {
                    val isEn = Locale.getDefault().language == "en"
                    if (isStudyGreen) {
                        if (isEn) "● Study limit reached · Rest eyes" else "● 공부·독서 한도 도달 · 눈 휴식 권장"
                    } else {
                        if (isEn) "● Managed app limit · Overuse alert" else "● 연속 사용 한도 · 과몰입 주의"
                    }
                }

                val overlayView = object : View(appContext) {
                    private val createdAtElapsed = android.os.SystemClock.elapsedRealtime()
                    private var pulseAlpha = 0f
                    private val rectF = RectF()

                    init {
                        setLayerType(LAYER_TYPE_SOFTWARE, null)
                    }

                    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                    }

                    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = rimStrokeWidth
                        color = baseColor
                        maskFilter = BlurMaskFilter(rimBlurRadius, BlurMaskFilter.Blur.NORMAL)
                    }

                    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 11f * density
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                    }

                    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1f * density
                    }

                    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
                        super.onConfigurationChanged(newConfig)
                        android.util.Log.d("EdgeLighting", "overlay config changed: ${newConfig?.orientation} size=${width}x${height}")
                    }

                    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                        super.onSizeChanged(w, h, oldw, oldh)
                        android.util.Log.d("EdgeLighting", "overlay size ${oldw}x${oldh} -> ${w}x${h}")
                    }

                    fun setPulse(alpha: Float) {
                        pulseAlpha = alpha
                        invalidate()
                    }

                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        if (pulseAlpha <= 0.01f || (android.os.SystemClock.elapsedRealtime() - createdAtElapsed) > 7_500L) return

                        val w = width.toFloat()
                        val h = height.toFloat()
                        if (w <= 0f || h <= 0f) return

                        // Layer A: 4방향 최외곽(0px 베젤) -> 안쪽 그라디언트 (Ambient Glow Band)
                        val alpha1 = (pulseAlpha * 0.75f * 255).toInt().coerceIn(0, 255)
                        val alpha2 = (pulseAlpha * 0.28f * 255).toInt().coerceIn(0, 255)
                        val gradientColors = intArrayOf(
                            colorWithAlpha(baseColor, alpha1),
                            colorWithAlpha(baseColor, alpha2),
                            Color.TRANSPARENT
                        )
                        val gradientPositions = floatArrayOf(0f, 0.38f, 1f)

                        // 상단
                        glowPaint.shader = android.graphics.LinearGradient(
                            0f, 0f, 0f, glowDepth,
                            gradientColors, gradientPositions,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        canvas.drawRect(0f, 0f, w, glowDepth, glowPaint)

                        // 하단
                        glowPaint.shader = android.graphics.LinearGradient(
                            0f, h, 0f, h - glowDepth,
                            gradientColors, gradientPositions,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        canvas.drawRect(0f, h - glowDepth, w, h, glowPaint)

                        // 좌측
                        glowPaint.shader = android.graphics.LinearGradient(
                            0f, 0f, glowDepth, 0f,
                            gradientColors, gradientPositions,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        canvas.drawRect(0f, 0f, glowDepth, h, glowPaint)

                        // 우측
                        glowPaint.shader = android.graphics.LinearGradient(
                            w, 0f, w - glowDepth, 0f,
                            gradientColors, gradientPositions,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        canvas.drawRect(w - glowDepth, 0f, w, h, glowPaint)

                        // Layer B: 코너 라운드 소프트 블러 림 (Corner Round Soft Blur Rim)
                        rimPaint.alpha = (pulseAlpha * 0.80f * 255).toInt().coerceIn(0, 255)
                        rectF.set(0f, 0f, w, h)
                        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, rimPaint)

                        // Layer C: 우측 상단 모서리 은은한 미니 캡슐 배지 (Corner Pill Indicator)
                        val rightMargin = 18f * density
                        val topMargin = 42f * density
                        val paddingH = 10f * density
                        val paddingV = 5f * density
                        val pillCorner = 14f * density

                        val textWidth = pillTextPaint.measureText(badgeText)
                        val fontMetrics = pillTextPaint.fontMetrics
                        val textHeight = fontMetrics.descent - fontMetrics.ascent

                        val pillWidth = textWidth + paddingH * 2
                        val pillHeight = textHeight + paddingV * 2

                        val pillRight = w - rightMargin
                        val pillLeft = pillRight - pillWidth
                        val pillTop = topMargin
                        val pillBottom = pillTop + pillHeight

                        pillBgPaint.color = Color.argb(
                            (pulseAlpha * 0.78f * 255).toInt().coerceIn(0, 220),
                            18, 20, 26
                        )
                        val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)
                        canvas.drawRoundRect(pillRect, pillCorner, pillCorner, pillBgPaint)

                        pillBorderPaint.color = colorWithAlpha(
                            baseColor,
                            (pulseAlpha * 0.75f * 255).toInt().coerceIn(0, 255)
                        )
                        canvas.drawRoundRect(pillRect, pillCorner, pillCorner, pillBorderPaint)

                        pillTextPaint.color = Color.argb(
                            (pulseAlpha * 0.95f * 255).toInt().coerceIn(0, 255),
                            245, 247, 250
                        )
                        val textX = pillLeft + paddingH
                        val textY = pillTop + paddingV - fontMetrics.ascent
                        canvas.drawText(badgeText, textX, textY, pillTextPaint)
                    }
                }

                // 크기를 고정 픽셀로 주면 표시 중에 가로·세로가 바뀔 때 창이 이전 방향 크기로 남아
                // 테두리 빛이 화면 가운데에 걸리거나 한쪽이 잘립니다. MATCH_PARENT로 두어 회전 시 다시 배치되게 합니다.
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    x = 0
                    y = 0
                    // Android 12+는 다른 앱의 불투명 오버레이를 통과하는 터치를 차단합니다(untrusted touch).
                    // 창 불투명도를 시스템 허용 상한(기본 0.8) 이하로 두어 경고 중에도 아래 앱을 조작할 수 있게 합니다.
                    alpha = MAX_PASS_THROUGH_WINDOW_ALPHA
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // 상태바·내비게이션 바 영역까지 덮어 화면 가장자리에서 빛이 시작되게 합니다.
                        fitInsetsTypes = 0
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                windowManager.addView(overlayView, layoutParams)
                activeOverlayView = overlayView

                // 7초(7000ms) 동안 3회 부드럽게 숨쉬듯 밝아졌다 어두워지는 애니메이션 (repeatCount = 5, REVERSE)
                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1166L // 1166ms * 6 = ~7000ms (3 full breaths)
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = 5
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        overlayView.setPulse(anim.animatedValue as Float)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            android.util.Log.d("EdgeLighting", "animation end")
                            dismissCurrent(windowManager)
                        }
                    })
                }

                activeAnimator = animator
                val watchdog = Runnable {
                    android.util.Log.d("EdgeLighting", "watchdog")
                    dismissCurrent(windowManager)
                }
                watchdogRunnable = watchdog
                mainHandler.postDelayed(watchdog, 7_300L)

                animator.start()
            } catch (_: Exception) {
                // WindowManager addView 오류 발생 시 안전하게 무시
            }
        }
    }

    private fun dismissCurrent(windowManager: WindowManager?) {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
        try {
            activeAnimator?.let { anim ->
                anim.removeAllUpdateListeners()
                anim.removeAllListeners()
                anim.cancel()
            }
        } catch (_: Exception) {
        } finally {
            activeAnimator = null
        }
        activeOverlayView?.let { view ->
            try {
                view.visibility = View.GONE
                view.invalidate()
                if (windowManager != null && view.isAttachedToWindow) {
                    try {
                        windowManager.removeViewImmediate(view)
                    } catch (_: Exception) {
                        windowManager.removeView(view)
                    }
                } else if (windowManager != null) {
                    windowManager.removeView(view)
                }
            } catch (_: Exception) {
            }
        }
        activeOverlayView = null
    }
}
