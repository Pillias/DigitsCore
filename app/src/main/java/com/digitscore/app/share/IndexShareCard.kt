package com.digitscore.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.digitscore.app.R
import com.digitscore.app.model.StreakRewards
import com.digitscore.app.ui.theme.ScorePalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 지금 코어 지수와 도룡뇽을 담은 공유 카드 (v0.9.44).
 * 인터넷 권한 없이 이미지 한 장을 만들어 Android 공유 시트로 넘깁니다.
 * 앱 이름·사용 시간·잠금 해제 수는 담지 않습니다(지수 하나 원칙과 사생활 보호).
 */
object IndexShareCard {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.digitscore.app"
    private const val SIZE = 1080
    private const val SHARE_DIR = "share"

    data class Content(
        val score: Int,
        val tierName: String,
        val salamanderStage: Int,
        val streakDays: Int,
        val isEn: Boolean,
        val dateText: String
    )

    /** 공유 시트에 함께 넘기는 글. 링크를 눌러 앱을 받을 수 있게 합니다. */
    fun shareText(score: Int, tierName: String, isEn: Boolean): String =
        if (isEn) "My Core Index right now: ${score}P ($tierName). It doesn't lock any app, just shows the balance of phone use and rest.\n$PLAY_URL"
        else "지금 내 코어 지수 ${score}P ($tierName). 앱을 잠그지 않고 스마트폰 사용과 휴식의 균형을 보여 줘요.\n$PLAY_URL"

    fun streakLine(stage: Int, streakDays: Int, isEn: Boolean): String {
        val s = stage.coerceIn(1, StreakRewards.MAX_STAGE)
        return when {
            streakDays <= 0 -> if (isEn) "Salamander stage $s" else "도룡뇽 ${s}단계"
            isEn -> "Salamander stage $s · $streakDays-day streak"
            else -> "도룡뇽 ${s}단계 · 연속 ${streakDays}일"
        }
    }

    fun render(context: Context, c: Content): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)
        val fill = ScorePalette.fillArgb(c.score)
        val accent = ScorePalette.textArgb(c.score, onDark = true)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        fun text(size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; this.typeface = typeface; textAlign = Paint.Align.CENTER
        }
        val cx = SIZE / 2f

        // 상단: 앱 이름과 기준
        canvas.drawText("DigitsCore", cx, 132f, text(56f, TEXT_MAIN, bold))
        val sub = if (c.isEn) "Core Index · last 24 hours · ${c.dateText}" else "코어 지수 · 최근 24시간 · ${c.dateText}"
        canvas.drawText(sub, cx, 188f, text(32f, TEXT_SUB))

        // 가운데: 지수 링 (1~99)
        val radius = 250f
        val cy = 500f
        val ring = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 36f; strokeCap = Paint.Cap.ROUND }
        canvas.drawArc(ring, 0f, 360f, false, stroke.apply { color = TRACK })
        canvas.drawArc(ring, -90f, 360f * c.score.coerceIn(1, 99) / 99f, false, stroke.apply { color = fill })
        canvas.drawText(c.score.toString(), cx, cy + 70f, text(210f, TEXT_MAIN, bold))
        canvas.drawText(c.tierName, cx, cy + 150f, text(52f, accent, bold))

        // 아래: 도룡뇽과 연속 기록
        val stageRes = when (c.salamanderStage.coerceIn(1, StreakRewards.MAX_STAGE)) {
            1 -> R.drawable.ic_salamander_stage1
            2 -> R.drawable.ic_salamander_stage2
            3 -> R.drawable.ic_salamander_stage3
            else -> R.drawable.ic_salamander_stage4
        }
        val line = streakLine(c.salamanderStage, c.streakDays, c.isEn)
        val linePaint = text(40f, TEXT_MAIN, bold).apply { textAlign = Paint.Align.LEFT }
        val icon = 110
        val gap = 24f
        val rowWidth = icon + gap + linePaint.measureText(line)
        val left = cx - rowWidth / 2f
        val rowTop = 820
        ContextCompat.getDrawable(context, stageRes)?.let { d ->
            d.setBounds(left.toInt(), rowTop, left.toInt() + icon, rowTop + icon)
            d.draw(canvas)
        }
        canvas.drawText(line, left + icon + gap, rowTop + icon / 2f + 14f, linePaint)

        // 맨 아래: 한 줄 소개
        val footer = if (c.isEn) "Never locks your apps · Get it on Google Play" else "앱을 잠그지 않는 코어 지수 · Google Play"
        canvas.drawText(footer, cx, 1010f, text(30f, TEXT_SUB))
        return bmp
    }

    /** 카드를 캐시에 저장하고 공유 시트를 엽니다. 이전 카드는 지웁니다. */
    fun share(context: Context, c: Content) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "DigitsCore_${c.score}.png")
        val bmp = render(context, c)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText(c.score, c.tierName, c.isEn))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, if (c.isEn) "Share Core Index" else "코어 지수 공유").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun today(isEn: Boolean): String =
        SimpleDateFormat(if (isEn) "MMM d, yyyy" else "yyyy.M.d", if (isEn) Locale.ENGLISH else Locale.KOREAN).format(Date())

    private const val BG = 0xFF151F2E.toInt()
    private const val TRACK = 0xFF26344A.toInt()
    private const val TEXT_MAIN = 0xFFFFFFFF.toInt()
    private const val TEXT_SUB = 0xFF9AA8BC.toInt()
}
