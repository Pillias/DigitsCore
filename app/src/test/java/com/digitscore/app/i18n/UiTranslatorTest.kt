package com.digitscore.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class UiTranslatorTest {
    @Test
    fun englishModeTranslatesStaticAndDynamicDashboardText() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Today's App Usage", UiTranslator.translate("오늘의 앱 사용 현황"))
            val translated = UiTranslator.translate("최근 7일 평균 42분")
            assertFalse(translated.contains(Regex("[가-힣]")))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun koreanModeKeepsOriginalText() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.KOREAN)
            assertEquals("코어 지수", UiTranslator.translate("코어 지수"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
