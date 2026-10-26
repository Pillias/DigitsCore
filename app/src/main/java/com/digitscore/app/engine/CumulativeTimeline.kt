package com.digitscore.app.engine

/** A closed, immutable scoring interval. Overlapping visible apps are collapsed by max tier. */
data class CumulativeUse(
    val start: Long,
    val end: Long,
    val managed: Boolean,
    val openingId: String,
    val openedAt: Long = start,
    val packageName: String = "",
    val effectivePackageName: String = packageName,
    val exempt: Boolean = false,
    val studyExempt: Boolean = false,
    val unlimitedExempt: Boolean = false
)
data class CumulativeCheckpoint(val timestamp: Long, val state: CumulativeScoreState)
data class ScoreMovement(
    val start: Long, val end: Long, val before: Double, val after: Double,
    val packageName: String, val opening: Boolean = false,
    /** 이 구간에 적용된 활동 (잠금 해제·앱 실행 순간 이동은 null). 지수 변화 분해에 씁니다. */
    val activity: CumulativeActivity? = null,
    /** 잠금 해제 간격 점수(v3)로 생긴 순간 이동이면 true. */
    val unlock: Boolean = false
)

/**
 * Deterministic minute clock, independent of poll frequency. Process only settled minutes;
 * the caller supplies at least two minutes of look-ahead for the one-minute brief-check rule.
 * Consecutive short opens separated by <=60s form one usage burst (not unlimited free checks).
 * Openings remain individually chargeable, while repeated fragments of one opening are not.
 * Exempt uses (e.g. Navigation, Phone, Utility, Study) do not trigger usage bursts or fake rest.
 * [penaltyFreeWindows] (15분 자유권 등) 안의 앱 사용은 감점·가속 없이 시간 제한 없는 면제로 처리하며,
 * 휴식 회복도 만들지 않습니다.
 */
object CumulativeTimeline {
    const val MINUTE = 60_000L

    fun advance(
        checkpoint: CumulativeCheckpoint,
        throughMillis: Long,
        uses: List<CumulativeUse>,
        restActivity: (Long) -> CumulativeActivity,
        config: CumulativeScoreConfig = CumulativeScoreConfig.CURRENT,
        onMovement: (ScoreMovement) -> Unit = {},
        penaltyFreeWindows: List<LongRange> = emptyList(),
        /** 실제 잠금 해제 시각(중복 합친 값). [unlockLedger]가 있고 v3 설정일 때만 점수에 반영합니다. */
        unlocks: List<Long> = emptyList(),
        unlockLedger: UnlockPacingLedger? = null,
        onUnlockLedger: (UnlockPacingLedger) -> Unit = {},
        /** 잠금 해제 하루 상한을 세는 논리적 날짜(05:00 기준) */
        dayKeyOf: (Long) -> String = { "" }
    ): CumulativeCheckpoint {
        val end = throughMillis - Math.floorMod(throughMillis, MINUTE)
        if (end <= checkpoint.timestamp) return checkpoint
        val sorted = uses.filter { it.end > it.start }.sortedBy { it.start }
        val nonExemptSorted = sorted.filter { !it.exempt }
        val groups = mutableListOf<MutableList<CumulativeUse>>()
        var groupEnd = Long.MIN_VALUE
        for (use in sorted) {
            if (groups.isEmpty() || use.start > groupEnd + MINUTE) groups.add(mutableListOf())
            groups.last().add(use)
            groupEnd = maxOf(groupEnd, use.end)
        }
        val longOpenIds = sorted.groupBy { it.openingId }.filterValues { parts ->
            var lastEnd = Long.MIN_VALUE
            var duration = 0L
            for (use in parts) {
                duration += (use.end - maxOf(use.start, lastEnd)).coerceAtLeast(0L)
                lastEnd = maxOf(lastEnd, use.end)
            }
            duration > MINUTE
        }.keys
        // Brief bursts lose their exemption only going forward, never by reclassifying
        // an already-settled short check when more opens arrive several minutes later.
        val substantial = groups.flatMap { group ->
            var lastEnd = Long.MIN_VALUE
            var duration = 0L
            group.filter { use ->
                duration += (use.end - maxOf(use.start, lastEnd)).coerceAtLeast(0L)
                lastEnd = maxOf(lastEnd, use.end)
                use.openingId in longOpenIds || duration > MINUTE
            }
        }
        // Only non-exempt openings incur opening loss
        val openings = nonExemptSorted.groupBy { it.openingId }.values.map { group -> group.minBy { it.openedAt } }
            .sortedBy { it.openedAt }
        var time = checkpoint.timestamp
        var state = checkpoint.state
        var useIndex = 0
        var openIndex = 0
        while (openIndex < openings.size && openings[openIndex].openedAt < time) openIndex++
        var ledger = unlockLedger
        val unlockTimes = if (ledger != null && config.unlockPacingEnabled) {
            unlocks.filter { it >= checkpoint.timestamp && it < end }.distinct().sorted()
        } else emptyList()
        var unlockIndex = 0
        fun applyUnlock(at: Long) {
            val current = ledger ?: return
            // 잠금 해제 순간에 이어지는 앱 사용이 있으면(자동 꺼짐 직후 등) 화면이 꺼져 있던 것이 아닙니다.
            if (sorted.any { it.start < at - UnlockPacingPolicy.MIN_GAP_MILLIS && it.end > at + UnlockPacingPolicy.MIN_GAP_MILLIS }) return
            val lastUseEnd = maxOf(current.lastUseEndAt, sorted.filter { it.end <= at }.maxOfOrNull { it.end } ?: 0L)
            ledger = current.copy(lastUseEndAt = lastUseEnd)
            if (lastUseEnd <= 0L) return // 처음 정산: 직전 사용을 모르면 뉴트럴
            if (current.lastSleepAt > lastUseEnd) return // 수면 뒤 첫 잠금 해제는 뉴트럴
            val firstApp = sorted.firstOrNull { it.start >= at - 2_000L && it.start <= at + UnlockPacingPolicy.FIRST_APP_WINDOW_MILLIS }
            if (firstApp?.unlimitedExempt == true) return // 통화·내비게이션은 뉴트럴
            val raw = UnlockPacingPolicy.rawDelta(at - lastUseEnd, state.signal, config)
            if (raw == 0.0) return
            val (applied, nextLedger) = UnlockPacingPolicy.capped(raw, ledger!!, dayKeyOf(at), config)
            ledger = nextLedger
            if (applied == 0.0) return
            val before = config.displayed(state.signal)
            state = state.copy(signal = (state.signal + applied).coerceIn(1.0, 99.0))
            onMovement(ScoreMovement(at, at, before, config.displayed(state.signal), "", unlock = true))
        }
        while (time < end) {
            val next = minOf(end, time + MINUTE)
            while (useIndex < substantial.size && substantial[useIndex].end <= time) useIndex++
            val active = buildList {
                var index = useIndex
                while (index < substantial.size && substantial[index].start < next) {
                    if (substantial[index].end > time) add(substantial[index])
                    index++
                }
            }
            val boundaries = (listOf(time, next) + active.flatMap {
                listOf(it.start.coerceIn(time, next), it.end.coerceIn(time, next))
            } + openings.subList(openIndex, openings.size).takeWhile { it.openedAt < next }.map { it.openedAt } +
                unlockTimes.subList(unlockIndex, unlockTimes.size).takeWhile { it < next } +
                penaltyFreeWindows.flatMap { w -> listOf(w.first, w.last) }.filter { it in time..next })
                .distinct().sorted()
            for ((a, b) in boundaries.zipWithNext()) {
                while (openIndex < openings.size && openings[openIndex].openedAt <= a) {
                    val opening = openings[openIndex++]
                    val before = config.displayed(state.signal)
                    state = CumulativeScoreEngine.advance(state, CumulativeActivity.UNKNOWN, 0.0, 1, config)
                    onMovement(ScoreMovement(a, a, before, config.displayed(state.signal), opening.packageName, true))
                }
                while (unlockIndex < unlockTimes.size && unlockTimes[unlockIndex] <= a) {
                    applyUnlock(unlockTimes[unlockIndex++])
                }
                val visible = active.filter { it.start < b && it.end > a }
                val nonExemptVisible = visible.filter { !it.exempt }
                val isShortGap = if (substantial.isNotEmpty()) {
                    val prevUse = substantial.lastOrNull { it.end <= a }
                    val nextUse = substantial.firstOrNull { it.start >= b }
                    prevUse != null && nextUse != null && (nextUse.start - prevUse.end) <= 3 * MINUTE
                } else false

                val penaltyFree = penaltyFreeWindows.any { a >= it.first && a < it.last }
                val activity = when {
                    penaltyFree && visible.isNotEmpty() -> CumulativeActivity.EXEMPT_UNLIMITED
                    nonExemptVisible.any { it.managed } -> CumulativeActivity.MANAGED_USE
                    nonExemptVisible.isNotEmpty() -> CumulativeActivity.NORMAL_USE
                    visible.any { it.exempt && it.studyExempt } -> CumulativeActivity.EXEMPT_STUDY
                    visible.any { it.exempt && !it.unlimitedExempt } -> CumulativeActivity.EXEMPT_UTILITY
                    visible.any { it.exempt } -> CumulativeActivity.EXEMPT_UNLIMITED
                    isShortGap -> CumulativeActivity.UNKNOWN
                    else -> restActivity(a)
                }
                val before = config.displayed(state.signal)
                state = CumulativeScoreEngine.advance(state, activity, (b - a) / 60_000.0, 0, config)
                // One visible owner gets the duration cost, even in PiP/split screen.
                val owner = nonExemptVisible.firstOrNull { it.managed } ?: nonExemptVisible.firstOrNull() ?: visible.firstOrNull()
                onMovement(ScoreMovement(a, b, before, config.displayed(state.signal), owner?.effectivePackageName ?: "",
                    activity = activity))
                if (activity == CumulativeActivity.SLEEP) ledger = ledger?.copy(lastSleepAt = b)
            }
            time = next
        }
        ledger?.let { finalLedger ->
            val lastEnd = sorted.filter { it.end <= time }.maxOfOrNull { it.end } ?: 0L
            onUnlockLedger(finalLedger.copy(lastUseEndAt = maxOf(finalLedger.lastUseEndAt, lastEnd)))
        }
        return CumulativeCheckpoint(time, state)
    }
}
