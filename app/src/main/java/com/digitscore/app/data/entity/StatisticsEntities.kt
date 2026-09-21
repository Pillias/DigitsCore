package com.digitscore.app.data.entity

import androidx.room.Entity

/** Empty packageName is the whole-device row. Null interaction counts mean unobserved. */
@Entity(tableName = "usage_hourly", primaryKeys = ["hour", "packageName"])
data class UsageHourEntity(
    val hour: Long, val packageName: String, val appName: String,
    val usageMillis: Long = 0, val managedMillis: Long = 0,
    val opens: Int = 0, val shortOpens: Int = 0,
    val unlocks: Int? = null, val notifications: Int? = null
)

@Entity(tableName = "score_hourly", primaryKeys = ["hour", "model"])
data class ScoreHourEntity(
    val hour: Long, val model: Int, val firstAt: Long, val lastAt: Long,
    val first: Double, val last: Double, val low: Double, val high: Double
)

/** Recorded alongside the cumulative cursor, never reconstructed from usage time. */
@Entity(tableName = "score_impact_hourly", primaryKeys = ["hour", "packageName"])
data class ScoreImpactHourEntity(
    val hour: Long, val packageName: String,
    val loss: Double = 0.0, val recovery: Double = 0.0,
    val observedMillis: Long = 0, val firstAt: Long, val lastAt: Long
)

@Entity(tableName = "statistics_state", primaryKeys = ["id"])
data class StatisticsStateEntity(
    val id: Int = 1, val processedUntil: Long, val interactionCoverageStart: Long
)
