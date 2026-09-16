package com.digitscore.app.model

/** 화면 흐름이 짧은 시간에 급격히 무거워졌는지 판단하는 1단계 무음 알림 기준입니다. */
data class RapidUsageAlertConfig(
    val windowMinutes: Int,
    val scoreDrop: Int,
    val usageMinutes: Int,
    val continuousMinutes: Int,
    val cooldownMinutes: Int
) {
    fun sanitized(): RapidUsageAlertConfig {
        val safeWindow = windowMinutes.coerceIn(15, 60)
        return copy(
            windowMinutes = safeWindow,
            scoreDrop = scoreDrop.coerceIn(2, 12),
            usageMinutes = usageMinutes.coerceIn(10, safeWindow),
            continuousMinutes = continuousMinutes.coerceIn(15, 90),
            cooldownMinutes = cooldownMinutes.coerceIn(30, 360)
        )
    }
}

val CoreIndexPreset.defaultRapidUsageAlertConfig: RapidUsageAlertConfig
    get() = when (this) {
        CoreIndexPreset.BALANCED -> RapidUsageAlertConfig(
            windowMinutes = 30,
            scoreDrop = 5,
            usageMinutes = 24,
            continuousMinutes = 35,
            cooldownMinutes = 90
        )
        CoreIndexPreset.FOCUS -> RapidUsageAlertConfig(
            windowMinutes = 30,
            scoreDrop = 4,
            usageMinutes = 20,
            continuousMinutes = 30,
            cooldownMinutes = 60
        )
        CoreIndexPreset.SCREEN_REST -> RapidUsageAlertConfig(
            windowMinutes = 30,
            scoreDrop = 4,
            usageMinutes = 22,
            continuousMinutes = 25,
            cooldownMinutes = 60
        )
        CoreIndexPreset.NIGHT_BALANCE -> RapidUsageAlertConfig(
            windowMinutes = 30,
            scoreDrop = 4,
            usageMinutes = 20,
            continuousMinutes = 30,
            cooldownMinutes = 60
        )
        CoreIndexPreset.FAMILY -> RapidUsageAlertConfig(
            windowMinutes = 30,
            scoreDrop = 3,
            usageMinutes = 18,
            continuousMinutes = 25,
            cooldownMinutes = 45
        )
    }
