package com.digitscore.app.data

/**
 * UsageEvents를 테스트 가능한 순수 이벤트로 변환한 모델입니다.
 * 화면 시간은 최근 상호작용한 package 하나가 소유하고, 점수 부하는 동시에 표시된
 * 앱(PiP·분할 화면 포함) 중 가장 높은 3단계 등급을 따릅니다.
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
    APP_INTERACTION,
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
    val observableUnlockedMillis: Long,
    val hasForegroundEvidence: Boolean,
    val endingState: ForegroundTrackerState
)

/** Samsung multi-resume/PiP 이벤트에서 STOPPED 전까지 화면에 남아 있다고 본 Activity입니다. */
data class ForegroundActivityState(
    val packageName: String,
    val className: String? = null,
    val instanceId: Int? = null,
    val isResumed: Boolean = true,
    val lastActivatedMillis: Long = 0L,
    val sessionStartTimeMillis: Long = lastActivatedMillis
)

/** 증분 UsageEvents 조회 사이에 이어지는 화면·잠금·전면 앱 상태입니다. */
data class ForegroundTrackerState(
    val activePackage: String? = null,
    val activeClass: String? = null,
    val activeInstanceId: Int? = null,
    val screenInteractive: Boolean = true,
    val keyguardHidden: Boolean = true,
    val visibleActivities: List<ForegroundActivityState> = emptyList()
)

data class ForegroundUsageSegment(
    val packageName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val effectivePackageName: String = packageName,
    val effectiveCategoryLevel: Int = 2,
    val concurrentAppCount: Int = 1,
    /** 실제 앱 진입을 만든 ACTIVITY_RESUMED 세션의 시작 시각입니다. */
    val sessionStartTimeMillis: Long = startTimeMillis
) {
    val durationMillis: Long get() = (endTimeMillis - startTimeMillis).coerceAtLeast(0L)
}

/**
 * 화면이 상호작용 가능하고 잠금이 해제된 동안 가장 최근에 상호작용한 앱 하나만
 * 시간을 소유합니다. 다중 RESUMED 또는 PAUSED 후 STOPPED 되지 않은 앱은 화면에
 * 함께 보이는 후보로 유지하여, 해당 구간의 점수 등급만 후보 중 가장 높은 값으로
 * 계산합니다. 두 앱의 시간을 더하지 않으므로 실제 경과시간을 초과하지 않습니다.
 */
internal object ForegroundUsageAggregator {
    fun aggregate(
        startTimeMillis: Long,
        endTimeMillis: Long,
        lateNightEndTimeMillis: Long,
        events: List<ForegroundTimelineEvent>,
        initialState: ForegroundTrackerState = ForegroundTrackerState(),
        categoryLevelResolver: (String) -> Int = { 2 }
    ): ForegroundUsageResult {
        require(endTimeMillis >= startTimeMillis)

        val usage = mutableMapOf<String, Long>()
        val lateNightUsage = mutableMapOf<String, Long>()
        val lastUsed = mutableMapOf<String, Long>()
        val segments = mutableListOf<ForegroundUsageSegment>()

        var activePackage: String? = initialState.activePackage
        var activeClass: String? = initialState.activeClass
        var activeInstanceId: Int? = initialState.activeInstanceId
        val visibleActivities = initialState.visibleActivities.toMutableList().apply {
            if (isEmpty() && !initialState.activePackage.isNullOrBlank()) {
                add(
                    ForegroundActivityState(
                        packageName = initialState.activePackage,
                        className = initialState.activeClass,
                        instanceId = initialState.activeInstanceId,
                        lastActivatedMillis = startTimeMillis,
                        sessionStartTimeMillis = startTimeMillis
                    )
                )
            }
        }
        var screenInteractive = initialState.screenInteractive
        var keyguardHidden = initialState.keyguardHidden
        var cursor = startTimeMillis
        var unlockCount = 0
        var hasForegroundEvidence = false
        var observableUnlockedMillis = 0L

        fun canAssignTime(): Boolean =
            screenInteractive && keyguardHidden && !activePackage.isNullOrBlank()

        fun matchesActivity(
            activity: ForegroundActivityState,
            event: ForegroundTimelineEvent
        ): Boolean {
            if (event.packageName != activity.packageName) return false
            val eventInstanceId = event.instanceId?.takeIf { it != 0 }
            val currentInstanceId = activity.instanceId?.takeIf { it != 0 }
            if (eventInstanceId != null && currentInstanceId != null) {
                return eventInstanceId == currentInstanceId
            }
            val eventClass = event.className
            return eventClass.isNullOrBlank() || activity.className.isNullOrBlank() ||
                eventClass == activity.className
        }

        fun selectFallbackPrimary() {
            val fallback = visibleActivities
                .filter { it.isResumed }
                .maxByOrNull { it.lastActivatedMillis }
            activePackage = fallback?.packageName
            activeClass = fallback?.className
            activeInstanceId = fallback?.instanceId
        }

        fun addActiveInterval(untilMillis: Long) {
            val intervalStart = cursor.coerceIn(startTimeMillis, endTimeMillis)
            val intervalEnd = untilMillis.coerceIn(startTimeMillis, endTimeMillis)
            if (intervalEnd <= intervalStart) return
            if (screenInteractive && keyguardHidden) {
                observableUnlockedMillis += intervalEnd - intervalStart
            }
            if (!canAssignTime()) return

            val packageName = activePackage ?: return
            val duration = intervalEnd - intervalStart
            usage[packageName] = (usage[packageName] ?: 0L) + duration

            val visiblePackages = visibleActivities
                .map { it.packageName }
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf(packageName) }
            val effectiveApp = visiblePackages
                .map { visiblePackage ->
                    visiblePackage to categoryLevelResolver(visiblePackage).coerceIn(1, 3)
                }
                .maxWithOrNull(
                    compareBy<Pair<String, Int>> { it.second }
                        .thenBy { if (it.first == packageName) 1 else 0 }
                )
                ?: (packageName to categoryLevelResolver(packageName).coerceIn(1, 3))
            val effectivePackageName = effectiveApp.first
            val effectiveCategoryLevel = effectiveApp.second
            val concurrentAppCount = visiblePackages.size.coerceAtLeast(1)
            val sessionStartTimeMillis = visibleActivities
                .filter { it.packageName == packageName }
                .maxByOrNull { it.lastActivatedMillis }
                ?.sessionStartTimeMillis
                ?: intervalStart

            val previous = segments.lastOrNull()
            if (previous?.packageName == packageName &&
                previous.effectivePackageName == effectivePackageName &&
                previous.effectiveCategoryLevel == effectiveCategoryLevel &&
                previous.concurrentAppCount == concurrentAppCount &&
                previous.sessionStartTimeMillis == sessionStartTimeMillis &&
                previous.endTimeMillis == intervalStart
            ) {
                segments[segments.lastIndex] = previous.copy(endTimeMillis = intervalEnd)
            } else {
                segments += ForegroundUsageSegment(
                    packageName = packageName,
                    startTimeMillis = intervalStart,
                    endTimeMillis = intervalEnd,
                    effectivePackageName = effectivePackageName,
                    effectiveCategoryLevel = effectiveCategoryLevel,
                    concurrentAppCount = concurrentAppCount,
                    sessionStartTimeMillis = sessionStartTimeMillis
                )
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
                        val existingIndex = visibleActivities.indexOfFirst { matchesActivity(it, event) }
                        val existingPackageSessionStart = visibleActivities
                            .filter { it.packageName == packageName }
                            .maxByOrNull { it.lastActivatedMillis }
                            ?.sessionStartTimeMillis
                        val resumed = ForegroundActivityState(
                            packageName = packageName,
                            className = event.className,
                            instanceId = event.instanceId,
                            isResumed = true,
                            lastActivatedMillis = event.timestampMillis,
                            // 같은 앱 내부 Activity 전환과 PiP 복귀는 새 실행이 아닙니다.
                            sessionStartTimeMillis = existingPackageSessionStart ?: event.timestampMillis
                        )
                        if (existingIndex >= 0) visibleActivities[existingIndex] = resumed
                        else visibleActivities += resumed
                        activePackage = packageName
                        activeClass = event.className
                        activeInstanceId = event.instanceId
                        lastUsed[packageName] = event.timestampMillis
                        hasForegroundEvidence = true
                    }
                }

                ForegroundTimelineEventType.APP_INTERACTION -> {
                    val packageName = event.packageName
                    if (!packageName.isNullOrBlank()) {
                        val candidates = visibleActivities.withIndex()
                            .filter { it.value.packageName == packageName }
                        val existingIndex = candidates.maxByOrNull {
                            it.value.lastActivatedMillis
                        }?.index
                        if (existingIndex != null) {
                            val previous = visibleActivities[existingIndex]
                            visibleActivities[existingIndex] = previous.copy(
                                lastActivatedMillis = event.timestampMillis
                            )
                            activeClass = previous.className
                            activeInstanceId = previous.instanceId
                            activePackage = packageName
                            lastUsed[packageName] = event.timestampMillis
                            hasForegroundEvidence = true
                        }
                        // USER_INTERACTION은 앱 실행 이벤트가 아닙니다. 화면에 보이는 앱의
                        // 분할 화면/PiP 초점 판별에만 쓰며, 알 수 없는 package는 무시합니다.
                    }
                }

                ForegroundTimelineEventType.APP_PAUSED -> {
                    val index = visibleActivities.indexOfFirst { matchesActivity(it, event) }
                    if (index >= 0) {
                        visibleActivities[index] = visibleActivities[index].copy(isResumed = false)
                    }
                    if (matchesActive(event)) selectFallbackPrimary()
                }

                ForegroundTimelineEventType.APP_STOPPED -> {
                    visibleActivities.removeAll { matchesActivity(it, event) }
                    if (matchesActive(event)) selectFallbackPrimary()
                }

                ForegroundTimelineEventType.SCREEN_INTERACTIVE -> {
                    screenInteractive = true
                }

                ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE -> {
                    screenInteractive = false
                }

                ForegroundTimelineEventType.KEYGUARD_SHOWN -> {
                    keyguardHidden = false
                }

                ForegroundTimelineEventType.KEYGUARD_HIDDEN -> {
                    val wasLocked = !keyguardHidden
                    keyguardHidden = true
                    if (wasLocked) {
                        activePackage?.let { unlockedPackage ->
                            visibleActivities.indices.forEach { index ->
                                val activity = visibleActivities[index]
                                if (activity.packageName == unlockedPackage) {
                                    visibleActivities[index] = activity.copy(
                                        lastActivatedMillis = event.timestampMillis,
                                        sessionStartTimeMillis = event.timestampMillis
                                    )
                                }
                            }
                        }
                    }
                    if (countUnlock) unlockCount++
                }

                ForegroundTimelineEventType.DEVICE_STARTUP,
                ForegroundTimelineEventType.DEVICE_SHUTDOWN -> {
                    screenInteractive = false
                    keyguardHidden = false
                    activePackage = null
                    activeClass = null
                    activeInstanceId = null
                    visibleActivities.clear()
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
            unlockCount = unlockCount,
            assignedUsageMillis = assignedUsageMillis,
            observableUnlockedMillis = observableUnlockedMillis.coerceAtMost(endTimeMillis - startTimeMillis),
            hasForegroundEvidence = hasForegroundEvidence || usage.isNotEmpty(),
            endingState = ForegroundTrackerState(
                activePackage = activePackage,
                activeClass = activeClass,
                activeInstanceId = activeInstanceId,
                screenInteractive = screenInteractive,
                keyguardHidden = keyguardHidden,
                visibleActivities = visibleActivities.toList()
            )
        )
    }
}
