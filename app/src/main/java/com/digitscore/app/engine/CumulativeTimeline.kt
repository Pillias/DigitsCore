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
    val packageName: String, val opening: Boolean = false
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
        penaltyFreeWindows: List<LongRange> = emptyList()
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
                penaltyFreeWindows.flatMap { w -> listOf(w.first, w.last) }.filter { it in time..next })
                .distinct().sorted()
            for ((a, b) in boundaries.zipWithNext()) {
                while (openIndex < openings.size && openings[openIndex].openedAt <= a) {
                    val opening = openings[openIndex++]
                    val before = config.displayed(state.signal)
                    state = CumulativeScoreEngine.advance(state, CumulativeActivity.UNKNOWN, 0.0, 1, config)
                    onMovement(ScoreMovement(a, a, before, config.displayed(state.signal), opening.packageName, true))
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
                onMovement(ScoreMovement(a, b, before, config.displayed(state.signal), owner?.effectivePackageName ?: ""))
            }
            time = next
        }
        return CumulativeCheckpoint(time, state)
    }
}
