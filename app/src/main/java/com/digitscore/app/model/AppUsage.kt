package com.digitscore.app.model

/**
 * 앱 사용 통계 모델
 * @property packageName 패키지명
 * @property appName 앱 이름 (레이블)
 * @property usageTimeMillis 사용 시간(밀리초)
 * @property categoryType 앱 분류
 * @property lastTimeUsedMillis 마지막 사용 시점
 */
data class AppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    val categoryType: AppCategoryType = AppCategoryType.NEUTRAL,
    val lastTimeUsedMillis: Long = 0L
) {
    val usageTimeMinutes: Long
        get() = usageTimeMillis / 1000 / 60
}
