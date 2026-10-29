package com.digitscore.app.data

import com.digitscore.app.data.release.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    private val md = """
        ## DigitsCore v0.9.40

        보상을 바꿨습니다. 점수 계산은 같습니다.

        - **보호권**: 기록을 지켜 줍니다.
        - **도룡뇽**: 자랍니다.

        ---

        ## DigitsCore v0.9.40

        Rewards changed.

        - **Shields**: keep your streak.
    """.trimIndent()

    @Test
    fun parsesKoreanAndEnglishSections() {
        val ko = ReleaseNotes.parse("0.9.40", md, english = false)
        assertEquals("DigitsCore v0.9.40", ko.title)
        assertEquals("보상을 바꿨습니다. 점수 계산은 같습니다.", ko.summary)
        assertEquals(2, ko.items.size)
        val en = ReleaseNotes.parse("0.9.40", md, english = true)
        assertEquals(listOf("**Shields**: keep your streak."), en.items)
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(ReleaseNotes.compare("0.9.40", "0.9.39") > 0)
        assertTrue(ReleaseNotes.compare("0.9.9", "0.9.10") < 0)
        assertTrue(ReleaseNotes.compare("1.0.0", "0.9.40a") > 0)
        assertEquals(0, ReleaseNotes.compare("0.9.40", "0.9.40b"))
        assertEquals("0.9.38", ReleaseNotes.versionOf("v0.9.38.md"))
        assertNull(ReleaseNotes.versionOf("notes.md"))
    }
}
