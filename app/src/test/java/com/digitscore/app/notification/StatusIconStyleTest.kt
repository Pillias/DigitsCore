package com.digitscore.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusIconStyleTest {
    @Test
    fun `unknown and missing style safely use large number style`() {
        assertEquals(StatusIconStyle.BIG_NUMBER, StatusIconStyle.fromId(null))
        assertEquals(StatusIconStyle.BIG_NUMBER, StatusIconStyle.fromId("unknown"))
    }

    @Test
    fun `saved large number style is restored`() {
        assertEquals(StatusIconStyle.BIG_NUMBER, StatusIconStyle.fromId("big_number"))
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
