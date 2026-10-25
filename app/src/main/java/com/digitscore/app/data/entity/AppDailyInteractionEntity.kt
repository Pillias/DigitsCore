package com.digitscore.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 잠금 해제 후 처음 연 앱 및 알림이 유발한 잠금 해제 등
 * 앱별 일일 인터랙션 집계 테이블 (30일 보관).
 */
@Entity(
    tableName = "app_daily_interactions",
    primaryKeys = ["dateString", "packageName"],
    indices = [Index("packageName"), Index("dateString")]
)
data class AppDailyInteractionEntity(
    val dateString: String,
    val packageName: String,
    @ColumnInfo(defaultValue = "0")
    val firstAfterUnlockCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val firstAfterUnlockQuickCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val notificationCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val notificationUnlockCount: Int = 0,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
