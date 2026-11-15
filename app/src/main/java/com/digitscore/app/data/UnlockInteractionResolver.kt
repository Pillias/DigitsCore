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
    /** 사용 기록 끝 시각과 진행 중 구간 끝의 오차 허용치 */
    const val SEGMENT_END_TOLERANCE_MILLIS = 1_000L
    @Deprecated("Use HABITUAL_CHECK_THRESHOLD_MILLIS", ReplaceWith("HABITUAL_CHECK_THRESHOLD_MILLIS"))
    const val QUICK_CONFIRMATION_THRESHOLD_MILLIS = HABITUAL_CHECK_THRESHOLD_MILLIS

    /**
     * 잠금 해제 직후 60초 안에 처음 전면에 온 앱(런처, DigitsCore 제외)을 판별합니다.
     * 또한 직전 60초 안에 해당 앱의 알림이 도착했는지 확인하여 '알림이 부른 잠금 해제' 여부를 함께 기록합니다.
     * @param unlockTimestamps 잠금 해제 시각 목록 (오름차순)
     * @param segments 사용 세그먼트 목록
     * @param ignoredPackages 런처 및 DigitsCore 패키지 목록
     * @param lastProcessedUnlockTimestamp 이전 처리 커서 (이 시각 초과의 잠금 해제만 처리)
     * @param notifications 당일 도착한 앱 알림 이벤트 목록
     * @param now 사용 기록을 읽은 끝 시각(서비스의 collectionEnd). 진행 중인 구간의 끝이 이 값과 같습니다.
     */
    fun resolveFirstAppsAfterUnlock(
        unlockTimestamps: List<Long>,
        segments: List<ForegroundUsageSegment>,
        ignoredPackages: Set<String>,
        lastProcessedUnlockTimestamp: Long = 0L,
        notifications: List<AppNotificationEvent> = emptyList(),
        now: Long = System.currentTimeMillis()
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
            val firstSegmentIndex = validSegments.indexOfFirst { segment ->
                (segment.startTimeMillis in unlockTime..maxAllowedTime) ||
                (segment.startTimeMillis <= unlockTime && segment.endTimeMillis > unlockTime)
            }

            if (firstSegmentIndex == -1) {
                // 아직 60초가 지나지 않았으면 다음 주기에 앱 진입을 확인할 수 있도록 커서를 멈춥니다.
                if (now < maxAllowedTime) {
                    break
                }
                continue
            }

            val firstSegment = validSegments[firstSegmentIndex]

            // 첫 앱 구간이 7초 이상이면 짧은 확인이 아니므로 바로 확정합니다.
            // 7초 미만이면 그 구간이 끝난 경우(다음 구간 시작 또는 데이터 끝보다 앞서 종료)에만 확정합니다.
            // [now]는 사용 기록을 읽은 끝 시각이며, 진행 중인 구간의 끝은 이 값과 같습니다.
            val hasNextSegment = (firstSegmentIndex < validSegments.lastIndex) ||
                segments.any { it.startTimeMillis >= firstSegment.endTimeMillis && it !== firstSegment }
            val isDurationSufficient = firstSegment.durationMillis >= HABITUAL_CHECK_THRESHOLD_MILLIS
            val isSessionEnded = hasNextSegment || firstSegment.endTimeMillis < now - SEGMENT_END_TOLERANCE_MILLIS

            if (!isDurationSufficient && !isSessionEnded) {
                // 진행 중인 7초 미만 구간: 다음 주기에 다시 판정하도록 이 잠금 해제에서 멈춥니다.
                break
            }

            val isQuick = firstSegment.durationMillis < HABITUAL_CHECK_THRESHOLD_MILLIS
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

        return results
    }
}
