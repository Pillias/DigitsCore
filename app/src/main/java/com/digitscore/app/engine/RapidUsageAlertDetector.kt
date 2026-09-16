package com.digitscore.app.engine

import com.digitscore.app.model.RapidUsageAlertConfig

enum class RapidUsageAlertReason {
    SCORE_DROP,
    HIGH_USAGE,
    CONTINUOUS_USE
}

data class RapidUsageObservation(
    val currentScore: Int,
    val baselineScore: Int?,
    val windowUsageMinutes: Int,
    val continuousUsageMinutes: Int
)

data class RapidUsageAlert(
    val reason: RapidUsageAlertReason,
    val scoreDrop: Int,
    val windowUsageMinutes: Int,
    val continuousUsageMinutes: Int
)

object RapidUsageAlertDetector {
    fun evaluate(
        observation: RapidUsageObservation,
        config: RapidUsageAlertConfig
    ): RapidUsageAlert? {
        val safe = config.sanitized()
        val drop = observation.baselineScore
            ?.minus(observation.currentScore)
            ?.coerceAtLeast(0)
            ?: 0
        val reason = when {
            drop >= safe.scoreDrop -> RapidUsageAlertReason.SCORE_DROP
            observation.continuousUsageMinutes >= safe.continuousMinutes ->
                RapidUsageAlertReason.CONTINUOUS_USE
            observation.windowUsageMinutes >= safe.usageMinutes ->
                RapidUsageAlertReason.HIGH_USAGE
            else -> return null
        }
        return RapidUsageAlert(
            reason = reason,
            scoreDrop = drop,
            windowUsageMinutes = observation.windowUsageMinutes.coerceAtLeast(0),
            continuousUsageMinutes = observation.continuousUsageMinutes.coerceAtLeast(0)
        )
    }
}
