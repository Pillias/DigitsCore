package com.digitscore.app.model

/**
 * 보상: 연속 기록 보호권 + 도룡뇽 성장 (사용자 결정 2026-10-28, 15min 자유권 대체).
 * - 오늘의 목표를 모두 지킨 날 스탬프 1개, 3개면 보호권 1장(최대 2장).
 * - 연속 기록이 끊기는 날 보호권이 있으면 1장을 자동으로 써서 기록을 잇습니다(그날은 +1 되지 않음).
 * - 도룡뇽은 연속 기록 7·14·30일에 한 단계씩 자라고(1~4단계), 보호권 없이 기록이 끊기면 한 단계 내려갑니다.
 * 점수(코어 지수) 계산에는 영향이 없습니다.
 */
object StreakRewards {
    const val STAMPS_PER_SHIELD = 3
    const val MAX_SHIELDS = 2
    const val MAX_STAGE = 4
    private val STAGE_STARTS = listOf(0, 7, 14, 30) // 1~4단계가 시작되는 연속 일수

    fun stageForStreak(streak: Int): Int = STAGE_STARTS.indexOfLast { streak >= it } + 1

    /** 다음 단계까지 남은 일수 (최고 단계면 null) */
    fun daysToNextStage(streak: Int, stage: Int): Int? =
        STAGE_STARTS.getOrNull(stage)?.let { (it - streak).coerceAtLeast(1) }

    data class Settlement(
        val state: GoalRewardState,
        val stampEarned: Int,
        val shieldEarned: Int,
        val shieldUsed: Boolean,
        val stageChange: Int
    )

    fun settle(state: GoalRewardState, maintained: Boolean, perfect: Boolean, date: String): Settlement {
        var streak = state.currentStreak
        var shields = state.availableFreePasses
        var stage = state.salamanderStage.coerceIn(1, MAX_STAGE)
        var shieldUsed = false
        if (maintained) {
            streak += 1
        } else if (shields > 0 && streak > 0) {
            shields -= 1
            shieldUsed = true
        } else {
            streak = 0
            stage = (stage - 1).coerceAtLeast(1)
        }
        if (maintained || shieldUsed) stage = maxOf(stage, stageForStreak(streak))
        var stamps = state.perfectStamps
        var shieldEarned = 0
        var stampEarned = 0
        if (perfect) {
            stampEarned = 1
            stamps += 1
            if (stamps >= STAMPS_PER_SHIELD) {
                stamps = 0
                val before = shields
                shields = minOf(shields + 1, MAX_SHIELDS)
                shieldEarned = shields - before
            }
        }
        val next = state.copy(
            currentStreak = streak,
            perfectStamps = stamps,
            availableFreePasses = shields,
            salamanderStage = stage,
            bestStreak = maxOf(state.bestStreak, streak),
            lastProcessedDate = date
        )
        return Settlement(next, stampEarned, shieldEarned, shieldUsed, stage - state.salamanderStage.coerceIn(1, MAX_STAGE))
    }
}

/**
 * 개인 기록: 관리 앱 사용이 가장 적었던 날, 잠금 해제가 가장 적었던 날, 방어선 최장 연속.
 * 화면 사용이 30min 미만인 날(기록이 빠졌거나 폰을 거의 안 쓴 날)과 지난 기록 불러오기로 채운 날은 제외합니다.
 */
data class PersonalBest(
    val minManagedMinutes: Long? = null,
    val minManagedDate: String? = null,
    val minUnlocks: Int? = null,
    val minUnlocksDate: String? = null
) {
    enum class Kind { MANAGED, UNLOCKS }

    companion object {
        const val MIN_SCREEN_MINUTES = 30L

        fun update(
            current: PersonalBest, date: String, screenMinutes: Long, managedMinutes: Long, unlocks: Int, imported: Boolean
        ): Pair<PersonalBest, Set<Kind>> {
            if (imported || screenMinutes < MIN_SCREEN_MINUTES) return current to emptySet()
            var next = current
            val broken = mutableSetOf<Kind>()
            if (current.minManagedMinutes == null || managedMinutes < current.minManagedMinutes) {
                next = next.copy(minManagedMinutes = managedMinutes, minManagedDate = date); broken += Kind.MANAGED
            }
            if (unlocks > 0 && (current.minUnlocks == null || unlocks < current.minUnlocks)) {
                next = next.copy(minUnlocks = unlocks, minUnlocksDate = date); broken += Kind.UNLOCKS
            }
            return next to broken
        }
    }
}
