package com.digitscore.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusIconStyleTest {
    @Test
    fun `unknown and missing style safely use tier style`() {
        assertEquals(StatusIconStyle.SCORE_TIER, StatusIconStyle.fromId(null))
        assertEquals(StatusIconStyle.SCORE_TIER, StatusIconStyle.fromId("unknown"))
    }

    @Test
    fun `saved proportion style is restored`() {
        assertEquals(
            StatusIconStyle.SCORE_PROPORTION,
            StatusIconStyle.fromId("score_proportion")
        )
    }

    @Test
    fun `saved number focus style is restored`() {
        assertEquals(StatusIconStyle.NUMBER_FOCUS, StatusIconStyle.fromId("number_focus"))
    }
}
