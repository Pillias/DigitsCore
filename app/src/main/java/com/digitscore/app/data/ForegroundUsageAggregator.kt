package com.digitscore.app.data

/**
 * Android UsageEvents를 테스트 가능한 순수 데이터로 변환한 이벤트입니다.
 * 한 시점에는 하나의 foreground 앱만 시간을 소유하도록 집계합니다.
 */
internal data class ForegroundTimelineEvent(
    val timestampMillis: Long,
    val type: ForegroundTimelineEventType,
    val packageName: String? = null,
    val className: String? = null
)

internal enum class ForegroundTimelineEventType {
    APP_RESUMED,
    APP_PAUSED,
    APP_STOPPED,
    SCREEN_NON_INTERACTIVE,
    DEVICE_SHUTDOWN
}

internal data class ForegroundUsageResult(
    val usageMillisByPackage: Map<String, Long>,
    val lateNightUsageMillisByPackage: Map<String, Long>,
    val lastUsedMillisByPackage: Map<String, Long>,
    val hasForegroundEvidence: Boolean
)

internal object ForegroundUsageAggregator {
    fun aggregate(
        startTimeMillis: Long,
        endTimeMillis: Long,
        lateNightEndTimeMillis: Long,
        events: List<ForegroundTimelineEvent>
    ): ForegroundUsageResult {
        require(endTimeMillis >= startTimeMillis)

        val usage = mutableMapOf<String, Long>()
        val lateNightUsage = mutableMapOf<String, Long>()
        val lastUsed = mutableMapOf<String, Long>()
        var activePackage: String? = null
        var activeClass: String? = null
        var cursor = startTimeMillis
        var hasForegroundEvidence = false

        fun addActiveInterval(until: Long) {
            val pkg = activePackage ?: return
            val intervalEnd = until.coerceIn(startTimeMillis, endTimeMillis)
            val intervalStart = cursor.coerceIn(startTimeMillis, endTimeMillis)
            if (intervalEnd <= intervalStart) return

            usage[pkg] = (usage[pkg] ?: 0L) + (intervalEnd - intervalStart)
            val lateEnd = minOf(intervalEnd, lateNightEndTimeMillis)
            if (lateEnd > intervalStart) {
                lateNightUsage[pkg] = (lateNightUsage[pkg] ?: 0L) + (lateEnd - intervalStart)
            }
        }

        fun matchesActive(event: ForegroundTimelineEvent): Boolean {
            if (event.packageName != activePackage) return false
            val eventClass = event.className
            return eventClass.isNullOrBlank() || activeClass.isNullOrBlank() || eventClass == activeClass
        }

        for (event in events.sortedBy { it.timestampMillis }) {
            if (event.timestampMillis > endTimeMillis) break

            // 조회 시작 이전 이벤트로 자정 경계의 foreground 상태를 복원합니다.
            if (event.timestampMillis < startTimeMillis) {
                when (event.type) {
                    ForegroundTimelineEventType.APP_RESUMED -> {
                        activePackage = event.packageName
                        activeClass = event.className
                    }
                    ForegroundTimelineEventType.APP_PAUSED,
                    ForegroundTimelineEventType.APP_STOPPED -> if (matchesActive(event)) {
                        activePackage = null
                        activeClass = null
                    }
                    ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE,
                    ForegroundTimelineEventType.DEVICE_SHUTDOWN -> {
                        activePackage = null
                        activeClass = null
                    }
                }
                continue
            }

            addActiveInterval(event.timestampMillis)
            cursor = event.timestampMillis.coerceIn(startTimeMillis, endTimeMillis)

            when (event.type) {
                ForegroundTimelineEventType.APP_RESUMED -> {
                    val pkg = event.packageName
                    if (!pkg.isNullOrBlank()) {
                        activePackage = pkg
                        activeClass = event.className
                        lastUsed[pkg] = event.timestampMillis
                        hasForegroundEvidence = true
                    }
                }
                ForegroundTimelineEventType.APP_PAUSED,
                ForegroundTimelineEventType.APP_STOPPED -> if (matchesActive(event)) {
                    activePackage = null
                    activeClass = null
                }
                ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE,
                ForegroundTimelineEventType.DEVICE_SHUTDOWN -> {
                    activePackage = null
                    activeClass = null
                }
            }
        }

        addActiveInterval(endTimeMillis)
        return ForegroundUsageResult(
            usageMillisByPackage = usage,
            lateNightUsageMillisByPackage = lateNightUsage,
            lastUsedMillisByPackage = lastUsed,
            hasForegroundEvidence = hasForegroundEvidence || usage.isNotEmpty()
        )
    }
}
