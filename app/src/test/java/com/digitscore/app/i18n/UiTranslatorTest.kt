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

            val auditedUiTexts = listOf(
                "사용시간 상위 6개 앱",
                "상위 7개 앱",
                "전체 12개 보기 ❯",
                "OS 감지 알림 14건 · 언락 20회",
                "알림 14건과 언락 20회 비교 그래프",
                "최근 30일 중 12일 측정",
                "최근 30일 사용량은 이전 기간과 비슷합니다.",
                "최근 30일 평균이 이전 기간보다 12% 늘었습니다.",
                "최근 30일 평균이 이전 기간보다 8% 줄었습니다.",
                "최근 30일에 새 사용 기록이 생겼습니다.",
                "평균 사용이 가장 많은 요일은 월요일 · 42분입니다.",
                "🌙 심야 사용 18분",
                "24시간 앱 사용량 그래프",
                "24시간 언락 횟수 그래프",
                "관리 대상 앱별 사용시간 그래프",
                "방해 생산성 중립 앱 사용시간 비교 그래프",
                "🌙 심야(24시~05시) 감점 배수: 1.5배",
                "방어선: 70점",
                "백업 파일은 20MB 이하여야 합니다."
            )
            auditedUiTexts.forEach { source ->
                val english = UiTranslator.translate(source)
                assertFalse("Untranslated Korean remains in: $english", english.contains(Regex("[가-힣]")))
            }
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

    @Test
    fun englishModeDoesNotRewriteKoreanAppNames() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("당근", UiTranslator.translate("당근"))
            assertEquals("삼성 브라우저", UiTranslator.translate("삼성 브라우저"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
