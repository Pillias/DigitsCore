package com.digitscore.app.engine

/** A closed, immutable scoring interval. Overlapping visible apps are collapsed by max tier. */
data class CumulativeUse(val start: Long, val end: Long, val managed: Boolean, val openingId: String, val openedAt: Long = start)
data class CumulativeCheckpoint(val timestamp: Long, val state: CumulativeScoreState)

/**
 * Deterministic minute clock, independent of poll frequency. Process only settled minutes;
 * the caller supplies at least two minutes of look-ahead for the one-minute brief-check rule.
 * Consecutive short opens separated by <=60s form one usage burst (not unlimited free checks).
 * Openings remain individually chargeable, while repeated fragments of one opening are not.
 */
object CumulativeTimeline {
    const val MINUTE = 60_000L

    fun advance(
        checkpoint: CumulativeCheckpoint,
        throughMillis: Long,
        uses: List<CumulativeUse>,
        restActivity: (Long) -> CumulativeActivity,
        config: CumulativeScoreConfig = CumulativeScoreConfig.CURRENT
    ): CumulativeCheckpoint {
        val end = throughMillis - Math.floorMod(throughMillis, MINUTE)
        if (end <= checkpoint.timestamp) return checkpoint
        val sorted = uses.filter { it.end > it.start }.sortedBy { it.start }
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
        val openings = sorted.groupBy { it.openingId }.values.map { group -> group.minOf { it.openedAt } }
            .sorted()
        var time = checkpoint.timestamp
        var state = checkpoint.state
        var useIndex = 0
        var openIndex = 0
        while (openIndex < openings.size && openings[openIndex] < time) openIndex++
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
            } + openings.subList(openIndex, openings.size).takeWhile { it < next })
                .distinct().sorted()
            for ((a, b) in boundaries.zipWithNext()) {
                var count = 0
                while (openIndex < openings.size && openings[openIndex] <= a) { count++; openIndex++ }
                val visible = active.filter { it.start < b && it.end > a }
                val activity = when {
                    visible.any { it.managed } -> CumulativeActivity.MANAGED_USE
                    visible.isNotEmpty() -> CumulativeActivity.NORMAL_USE
                    else -> restActivity(a)
                }
                state = CumulativeScoreEngine.advance(state, activity, (b - a) / 60_000.0, count, config)
            }
            time = next
        }
        return CumulativeCheckpoint(time, state)
    }
}
