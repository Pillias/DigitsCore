package com.digitscore.app.data

data class AppNotificationEvent(
    val packageName: String,
    val timestampMillis: Long
)

data class UnlockInteractionResult(
    val packageName: String,
    val isQuick: Boolean,
    val unlockTimestamp: Long,
    val appOpenTimestamp: Long,
    val isNotificationTriggered: Boolean = false
)

object UnlockInteractionResolver {

    const val MAX_DELAY_AFTER_UNLOCK_MILLIS = 60_000L
    const val QUICK_CONFIRMATION_THRESHOLD_MILLIS = 60_000L

    /**
     * 잠금 해제 직후 60초 안에 처음 전면에 온 앱(런처, DigitsCore 제외)을 판별합니다.
     * 또한 직전 60초 안에 해당 앱의 알림이 도착했는지 확인하여 '알림이 부른 잠금 해제' 여부를 함께 기록합니다.
     * @param unlockTimestamps 잠금 해제 시각 목록 (오름차순)
     * @param segments 사용 세그먼트 목록
     * @param ignoredPackages 런처 및 DigitsCore 패키지 목록
     * @param lastProcessedUnlockTimestamp 이전 처리 커서 (이 시각 초과의 잠금 해제만 처리)
     * @param notifications 당일 도착한 앱 알림 이벤트 목록
     */
    fun resolveFirstAppsAfterUnlock(
        unlockTimestamps: List<Long>,
        segments: List<ForegroundUsageSegment>,
        ignoredPackages: Set<String>,
        lastProcessedUnlockTimestamp: Long = 0L,
        notifications: List<AppNotificationEvent> = emptyList()
    ): List<UnlockInteractionResult> {
        val pendingUnlocks = unlockTimestamps.filter { it > lastProcessedUnlockTimestamp }.sorted()
        if (pendingUnlocks.isEmpty() || segments.isEmpty()) return emptyList()

        val results = mutableListOf<UnlockInteractionResult>()
        val validSegments = segments
            .filter { it.packageName !in ignoredPackages && it.durationMillis > 0 }
            .sortedBy { it.startTimeMillis }

        for (unlockTime in pendingUnlocks) {
            val maxAllowedTime = unlockTime + MAX_DELAY_AFTER_UNLOCK_MILLIS

            // 잠금 해제 시점에 이미 활성화되어 있거나, 해제 후 60초 이내에 처음 포그라운드로 온 세그먼트
            val firstSegment = validSegments.firstOrNull { segment ->
                (segment.startTimeMillis in unlockTime..maxAllowedTime) ||
                (segment.startTimeMillis <= unlockTime && segment.endTimeMillis > unlockTime)
            }

            if (firstSegment != null) {
                val isQuick = firstSegment.durationMillis < QUICK_CONFIRMATION_THRESHOLD_MILLIS
                // 잠금 해제 직전 60초 안에 이 앱의 알림이 도착했는지 검사
                val isNotificationTriggered = notifications.any { notif ->
                    notif.packageName == firstSegment.packageName &&
                    notif.timestampMillis in (unlockTime - MAX_DELAY_AFTER_UNLOCK_MILLIS)..unlockTime
                }

                results += UnlockInteractionResult(
                    packageName = firstSegment.packageName,
                    isQuick = isQuick,
                    unlockTimestamp = unlockTime,
                    appOpenTimestamp = maxOf(unlockTime, firstSegment.startTimeMillis),
                    isNotificationTriggered = isNotificationTriggered
                )
            }
        }

        return results
    }
}
