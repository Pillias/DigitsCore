package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.Index

/** 화면 ON·잠금 해제 상태에서 실제 최상단이었던 앱의 30일 상세 사용 구간입니다. */
@Entity(
    tableName = "foreground_usage_sessions",
    primaryKeys = ["packageName", "startTimeMillis"],
    indices = [Index("dateString"), Index("endTimeMillis")]
)
data class ForegroundUsageSessionEntity(
    val packageName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val dateString: String,
    val appName: String,
    val categoryLevel: Int,
    val isLateNight: Boolean,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
