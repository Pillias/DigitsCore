package com.digitscore.app.notification

import android.content.res.Configuration
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicIconGeneratorTest {
    @Test
    fun `core number uses black in light mode`() {
        assertEquals(
            Color.BLACK,
            DynamicIconGenerator.statusIconContrastColor(Configuration.UI_MODE_NIGHT_NO)
        )
    }

    @Test
    fun `core number uses white in dark mode`() {
        assertEquals(
            Color.WHITE,
            DynamicIconGenerator.statusIconContrastColor(Configuration.UI_MODE_NIGHT_YES)
        )
    }

    @Test
    fun `undefined mode safely defaults to black`() {
        assertEquals(
            Color.BLACK,
            DynamicIconGenerator.statusIconContrastColor(Configuration.UI_MODE_NIGHT_UNDEFINED)
        )
    }
}
