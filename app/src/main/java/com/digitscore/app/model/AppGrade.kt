package com.digitscore.app.model

import com.digitscore.app.engine.CumulativeScoreConfig

/**
 * 사용자가 고르는 4단계 앱 등급입니다. 앱 목록과 앱 상세 화면이 같은 기준을 씁니다.
 * 점수 계산은 [category]와 공부 앱 여부([isStudy])로 이루어집니다.
 */
enum class AppGrade(
    val category: AppCategoryType,
    val isStudy: Boolean,
    private val koreanName: String,
    private val englishName: String
) {
    FOCUS(AppCategoryType.EXEMPT, true, "권장", "Study"),
    TOOL(AppCategoryType.EXEMPT, false, "필수", "Tool"),
    DAILY(AppCategoryType.NEUTRAL, false, "일반", "Daily"),
    LIMIT(AppCategoryType.DISTRACTING, false, "관리", "Limit");

    val displayName: String
        get() = if (com.digitscore.app.i18n.AppLocale.isCurrentEnglish) englishName else koreanName

    /** 등급 설명. 면제 등급은 감점 없는 연속 사용 한도를 함께 안내합니다. */
    val description: String
        get() {
            val en = com.digitscore.app.i18n.AppLocale.isCurrentEnglish
            val config = CumulativeScoreConfig.CURRENT
            val study = config.studyExemptLimitMinutes.toInt()
            val utility = config.utilityExemptLimitMinutes.toInt()
            val accel = config.accelerationStartMinutes.toInt()
            return when (this) {
                FOCUS -> if (en) "Study·reading · No deduction for the first ${study}min in a row, then standard load"
                    else "공부·독서 · 연속 ${study}min까지 감점 없음, 이후 일반 감점"
                TOOL -> if (en) "Essential tools · No deduction for the first ${utility}min in a row, then standard load (navigation·calls: unlimited)"
                    else "필수 도구 · 연속 ${utility}min까지 감점 없음, 이후 일반 감점 (내비·통화는 제한 없음)"
                DAILY -> if (en) "General use · Standard usage load" else "일반 사용 · 기본 사용 부하"
                LIMIT -> if (en) "Managed · Deduction accelerates after ${accel}min in a row" else "관리 · 연속 ${accel}min 이후 감점 가속"
            }
        }

    companion object {
        fun from(category: AppCategoryType, isStudy: Boolean): AppGrade = when (category.canonical) {
            AppCategoryType.EXEMPT -> if (isStudy) FOCUS else TOOL
            AppCategoryType.DISTRACTING -> LIMIT
            else -> DAILY
        }
    }
}
