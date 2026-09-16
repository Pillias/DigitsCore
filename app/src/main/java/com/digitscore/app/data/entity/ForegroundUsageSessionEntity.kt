package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 화면 ON·잠금 해제 상태에서 실제 최상단이었던 앱의 30일 상세 사용 구간입니다.
 * effectivePackageName/effectiveCategoryLevel은 PiP·분할 화면에서 동시에 보인 앱 중
 * 가장 높은 부하 앱과 등급이며, concurrentAppCount는 시간 중복 없이 병렬 표시가
 * 감지됐음을 남깁니다. sessionStartTimeMillis는 전면 조각과 별개인 실제 앱 진입 ID입니다.
 */
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
    val effectivePackageName: String = packageName,
    val effectiveCategoryLevel: Int = categoryLevel,
    val concurrentAppCount: Int = 1,
    val sessionStartTimeMillis: Long = startTimeMillis,
    val isLateNight: Boolean,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
