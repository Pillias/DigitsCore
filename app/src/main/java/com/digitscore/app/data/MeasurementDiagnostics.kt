package com.digitscore.app.data

/** 앱 자체 측정기의 비용과 이벤트 포착 상태를 사용자가 확인할 수 있는 진단값입니다. */
data class MeasurementDiagnostics(
    val isIncremental: Boolean = false,
    val lastQueryWindowMillis: Long = 0L,
    val lastQueryDurationMillis: Long = 0L,
    val lastCycleCpuMillis: Long = 0L,
    val queriedEventCount: Int = 0,
    val foregroundCoveragePercent: Int? = null,
    val todayUnlockCount: Int = 0,
    val rolling24HourUnlockCount: Int = 0,
    val cyclesToday: Int = 0,
    val totalQueryDurationTodayMillis: Long = 0L,
    val totalCpuTodayMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)
