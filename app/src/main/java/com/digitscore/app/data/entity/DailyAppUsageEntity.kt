package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo

/**
 * OS 원본 이벤트가 사라진 뒤에도 장기 추세를 볼 수 있도록 보관하는 앱별 일일 집계입니다.
 * 원본 이벤트나 화면 내용은 저장하지 않습니다.
 */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["dateString", "packageName"],
    indices = [Index("packageName"), Index("dateString")]
)
data class DailyAppUsageEntity(
    val dateString: String,
    val packageName: String,
    val appName: String,
    val usageMillis: Long,
    val sessionCount: Int,
    @ColumnInfo(defaultValue = "0")
    val shortSessionCount: Int = 0,
    val longestSessionMillis: Long,
    val lateNightUsageMillis: Long,
    val categoryLevel: Int,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

/** 앱을 사용하지 않은 날과 측정하지 않은 날을 구분하기 위한 일별 측정 표식입니다. */
@Entity(tableName = "daily_usage_coverage")
data class DailyUsageCoverageEntity(
    @androidx.room.PrimaryKey
    val dateString: String,
    val isComplete: Boolean,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
