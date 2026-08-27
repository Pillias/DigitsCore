package com.digitscore.app.model

/**
 * 앱의 성격 분류 (생산성/학습, 중립, 방해/중독/SNS 등)
 */
enum class AppCategoryType(val displayName: String, val defaultWeight: Float) {
    PRODUCTIVE("생산성 / 학습", 1.5f),   // 점수 보너스 (가산 요인)
    NEUTRAL("일반 / 중립", 0.0f),         // 점수에 미치는 영향 없음
    DISTRACTING("SNS / 오락 / 중독", -2.0f); // 점수 감점 (차감 요인)
}
