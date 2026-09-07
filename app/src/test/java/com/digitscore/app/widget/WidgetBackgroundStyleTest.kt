package com.digitscore.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBackgroundStyleTest {
    @Test
    fun unknownOrMissingStyleUsesDarkBackground() {
        assertEquals(WidgetBackgroundStyle.DARK, WidgetBackgroundStyle.fromId(null))
        assertEquals(WidgetBackgroundStyle.DARK, WidgetBackgroundStyle.fromId("unknown"))
    }

    @Test
    fun knownStylesAreRestored() {
        assertEquals(WidgetBackgroundStyle.WHITE, WidgetBackgroundStyle.fromId("white"))
        assertEquals(WidgetBackgroundStyle.TRANSPARENT, WidgetBackgroundStyle.fromId("transparent"))
    }
}
