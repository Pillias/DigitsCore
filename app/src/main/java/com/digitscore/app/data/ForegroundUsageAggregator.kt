package com.digitscore.app.data

/**
 * UsageEvents를 테스트 가능한 순수 이벤트로 변환한 모델입니다.
 * 한 시점에는 오직 하나의 package만 화면 사용시간을 소유합니다.
 */
internal data class ForegroundTimelineEvent(
    val timestampMillis: Long,
    val type: ForegroundTimelineEventType,
    val packageName: String? = null,
    val className: String? = null,
    val instanceId: Int? = null
)

internal enum class ForegroundTimelineEventType {
    APP_RESUMED,
    APP_PAUSED,
    APP_STOPPED,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    KEYGUARD_SHOWN,
    KEYGUARD_HIDDEN,
    DEVICE_STARTUP,
    DEVICE_SHUTDOWN
}

internal data class ForegroundUsageResult(
    val usageMillisByPackage: Map<String, Long>,
    val lateNightUsageMillisByPackage: Map<String, Long>,
    val lastUsedMillisByPackage: Map<String, Long>,
    val segments: List<ForegroundUsageSegment>,
    val unlockCount: Int,
    val assignedUsageMillis: Long,
    val hasForegroundEvidence: Boolean
)

internal data class ForegroundUsageSegment(
    val packageName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long
) {
    val durationMillis: Long get() = (endTimeMillis - startTimeMillis).coerceAtLeast(0L)
}

/**
 * 화면이 상호작용 가능하고 잠금이 해제된 동안 가장 최근에 RESUMED 된 앱 하나만
 * 시간을 소유하도록 계산합니다. OS의 누적 UsageStats 값은 이 계산에 사용하지 않습니다.
 */
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
        val segments = mutableListOf<ForegroundUsageSegment>()

        var activePackage: String? = null
        var activeClass: String? = null
        var activeInstanceId: Int? = null
        var screenInteractive = true
        var keyguardHidden = true
        var cursor = startTimeMillis
        var unlockCount = 0
        var screenInteractiveCount = 0
        var hasKeyguardEvents = false
        var hasForegroundEvidence = false

        fun canAssignTime(): Boolean =
            screenInteractive && keyguardHidden && !activePackage.isNullOrBlank()

        fun addActiveInterval(untilMillis: Long) {
            val intervalStart = cursor.coerceIn(startTimeMillis, endTimeMillis)
            val intervalEnd = untilMillis.coerceIn(startTimeMillis, endTimeMillis)
            if (intervalEnd <= intervalStart || !canAssignTime()) return

            val packageName = activePackage ?: return
            val duration = intervalEnd - intervalStart
            usage[packageName] = (usage[packageName] ?: 0L) + duration

            val previous = segments.lastOrNull()
            if (previous?.packageName == packageName && previous.endTimeMillis == intervalStart) {
                segments[segments.lastIndex] = previous.copy(endTimeMillis = intervalEnd)
            } else {
                segments += ForegroundUsageSegment(packageName, intervalStart, intervalEnd)
            }

            val lateStart = maxOf(intervalStart, startTimeMillis)
            val lateEnd = minOf(intervalEnd, lateNightEndTimeMillis)
            if (lateEnd > lateStart) {
                lateNightUsage[packageName] =
                    (lateNightUsage[packageName] ?: 0L) + (lateEnd - lateStart)
            }
        }

        fun matchesActive(event: ForegroundTimelineEvent): Boolean {
            if (event.packageName != activePackage) return false
            val eventInstanceId = event.instanceId?.takeIf { it != 0 }
            val currentInstanceId = activeInstanceId?.takeIf { it != 0 }
            if (eventInstanceId != null && currentInstanceId != null) {
                return eventInstanceId == currentInstanceId
            }
            val eventClass = event.className
            return eventClass.isNullOrBlank() || activeClass.isNullOrBlank() || eventClass == activeClass
        }

        fun applyEvent(event: ForegroundTimelineEvent, countUnlock: Boolean) {
            when (event.type) {
                ForegroundTimelineEventType.APP_RESUMED -> {
                    val packageName = event.packageName
                    if (!packageName.isNullOrBlank()) {
                        activePackage = packageName
                        activeClass = event.className
                        activeInstanceId = event.instanceId
                        lastUsed[packageName] = event.timestampMillis
                        hasForegroundEvidence = true
                    }
                }

                ForegroundTimelineEventType.APP_PAUSED,
                ForegroundTimelineEventType.APP_STOPPED -> if (matchesActive(event)) {
                    activePackage = null
                    activeClass = null
                    activeInstanceId = null
                }

                ForegroundTimelineEventType.SCREEN_INTERACTIVE -> {
                    screenInteractive = true
                    if (countUnlock) screenInteractiveCount++
                }

                ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE -> {
                    screenInteractive = false
                }

                ForegroundTimelineEventType.KEYGUARD_SHOWN -> {
                    if (countUnlock) hasKeyguardEvents = true
                    keyguardHidden = false
                }

                ForegroundTimelineEventType.KEYGUARD_HIDDEN -> {
                    if (countUnlock) hasKeyguardEvents = true
                    keyguardHidden = true
                    if (countUnlock) unlockCount++
                }

                ForegroundTimelineEventType.DEVICE_STARTUP,
                ForegroundTimelineEventType.DEVICE_SHUTDOWN -> {
                    screenInteractive = false
                    keyguardHidden = false
                    activePackage = null
                    activeClass = null
                    activeInstanceId = null
                }
            }
        }

        events.sortedBy { it.timestampMillis }.forEach { event ->
            if (event.timestampMillis > endTimeMillis) return@forEach

            // 조회 시작 전 이벤트는 자정 시점의 화면·잠금·전면 앱 상태만 복원합니다.
            if (event.timestampMillis < startTimeMillis) {
                applyEvent(event, countUnlock = false)
                return@forEach
            }

            addActiveInterval(event.timestampMillis)
            cursor = event.timestampMillis.coerceIn(startTimeMillis, endTimeMillis)
            applyEvent(event, countUnlock = true)
        }

        addActiveInterval(endTimeMillis)
        val assignedUsageMillis = usage.values.sum().coerceAtMost(endTimeMillis - startTimeMillis)

        return ForegroundUsageResult(
            usageMillisByPackage = usage,
            lateNightUsageMillisByPackage = lateNightUsage,
            lastUsedMillisByPackage = lastUsed,
            segments = segments,
            unlockCount = if (hasKeyguardEvents) unlockCount else screenInteractiveCount,
            assignedUsageMillis = assignedUsageMillis,
            hasForegroundEvidence = hasForegroundEvidence || usage.isNotEmpty()
        )
    }
}
