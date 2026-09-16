package com.digitscore.app.notification

import android.content.res.Configuration
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicIconGeneratorTest {
    @Test
    fun `core number uses high contrast tier colors in light mode`() {
        assertEquals(
            Color.parseColor("#087A46"),
            DynamicIconGenerator.statusIconScoreColor(82, Configuration.UI_MODE_NIGHT_NO)
        )
        assertEquals(
            Color.parseColor("#9A6200"),
            DynamicIconGenerator.statusIconScoreColor(72, Configuration.UI_MODE_NIGHT_NO)
        )
        assertEquals(
            Color.parseColor("#C34A00"),
            DynamicIconGenerator.statusIconScoreColor(55, Configuration.UI_MODE_NIGHT_NO)
        )
        assertEquals(
            Color.parseColor("#C62828"),
            DynamicIconGenerator.statusIconScoreColor(30, Configuration.UI_MODE_NIGHT_NO)
        )
    }

    @Test
    fun `core number uses brighter tier colors in dark mode`() {
        assertEquals(
            Color.parseColor("#FFA05A"),
            DynamicIconGenerator.statusIconScoreColor(55, Configuration.UI_MODE_NIGHT_YES)
        )
    }

    @Test
    fun `undefined mode safely uses light background palette`() {
        assertEquals(
            Color.parseColor("#087A46"),
            DynamicIconGenerator.statusIconScoreColor(90, Configuration.UI_MODE_NIGHT_UNDEFINED)
        )
    }
}
