package com.digitscore.app.notification

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

/** 점수 색은 ScorePalette의 실제 ARGB 값과 비교합니다 (JVM 테스트에서는 Color.parseColor가 동작하지 않음). */
class DynamicIconGeneratorTest {
    @Test
    fun `core number uses high contrast tier colors in light mode`() {
        assertEquals(
            0xFF087A46.toInt(),
            DynamicIconGenerator.statusIconScoreColor(82, Configuration.UI_MODE_NIGHT_NO)
        )
        assertEquals(
            0xFF9A6200.toInt(),
            DynamicIconGenerator.statusIconScoreColor(72, Configuration.UI_MODE_NIGHT_NO)
        )
        assertEquals(
            0xFFC34A00.toInt(),
            DynamicIconGenerator.statusIconScoreColor(55, Configuration.UI_MODE_NIGHT_NO)
        )
        assertEquals(
            0xFFC62828.toInt(),
            DynamicIconGenerator.statusIconScoreColor(30, Configuration.UI_MODE_NIGHT_NO)
        )
    }

    @Test
    fun `core number uses brighter tier colors in dark mode`() {
        assertEquals(
            0xFFFFA05A.toInt(),
            DynamicIconGenerator.statusIconScoreColor(55, Configuration.UI_MODE_NIGHT_YES)
        )
    }

    @Test
    fun `undefined mode safely uses light background palette`() {
        assertEquals(
            0xFF087A46.toInt(),
            DynamicIconGenerator.statusIconScoreColor(90, Configuration.UI_MODE_NIGHT_UNDEFINED)
        )
    }
}
