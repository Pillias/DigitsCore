package com.digitscore.app.model

/**
 * 코어 지수 4단계. 색 구간(80/60/40)과 같은 경계를 쓰는 유일한 단계 기준입니다.
 * 화면·위젯·알림·런처 아이콘이 모두 이 단계를 씁니다(이전 S~F 6등급은 화면에서 쓰지 않음).
 */
enum class ScoreTier(val minScore: Int) {
    STABLE(80),
    FAIR(60),
    CAUTION(40),
    REST(1);

    /** 한 단계 위의 시작 점수. 가장 높은 단계이면 null. */
    val nextTierMinScore: Int?
        get() = when (this) {
            STABLE -> null
            FAIR -> STABLE.minScore
            CAUTION -> FAIR.minScore
            REST -> CAUTION.minScore
        }

    companion object {
        fun fromScore(score: Int): ScoreTier = when {
            score >= 80 -> STABLE
            score >= 60 -> FAIR
            score >= 40 -> CAUTION
            else -> REST
        }
    }
}
