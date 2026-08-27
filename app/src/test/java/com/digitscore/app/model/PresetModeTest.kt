package com.digitscore.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetModeTest {

    @Test
    fun testPresetMode_allModesDefinedProperly() {
        val modes = PresetMode.entries
        assertEquals(5, modes.size)

        modes.forEach { mode ->
            assertNotNull(mode.id)
            assertNotNull(mode.title)
            assertNotNull(mode.description)
            assertTrue("초기 점수는 100점이어야 합니다", mode.scoreRule.initialScore == 100f)
            assertTrue("상한선은 100점이어야 합니다", mode.scoreRule.maxScoreBoundary == 100f)
            assertTrue("하한선은 0점이어야 합니다", mode.scoreRule.minScoreBoundary == 0f)
        }
    }

    @Test
    fun testPresetMode_fromId() {
        assertEquals(PresetMode.STUDY, PresetMode.fromId("study"))
        assertEquals(PresetMode.EYE_HEALTH, PresetMode.fromId("eye_health"))
        assertEquals(PresetMode.WORKER, PresetMode.fromId("worker"))
        assertEquals(PresetMode.KIDS, PresetMode.fromId("kids"))
        assertEquals(PresetMode.BALANCED, PresetMode.fromId("balanced"))
        assertEquals(PresetMode.BALANCED, PresetMode.fromId("invalid_id_default"))
    }
}
