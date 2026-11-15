package com.digitscore.app.engine

import com.digitscore.app.model.ScoreTier

/**
 * 지수 바로 아래 '할 일 한 줄'의 재료. 문구는 항상 행동과 그 결과(P)를 함께 말합니다.
 * 예) "화면 끄고 15min 쉬면 78P", "20min 쉬면 안정(80P)".
 */
data class CoachAction(
    val kind: Kind,
    val minutes: Int? = null,
    val targetScore: Int? = null,
    /** 목표 점수가 다음 지수 단계의 시작점이면 그 단계(문구에 단계 이름을 붙임). */
    val reachesTier: ScoreTier? = null,
    val currentScore: Int,
    /** 목표 점수가 오늘의 지수 방어선이면 true (문구에 "방어선"을 붙임) */
    val reachesDefense: Boolean = false,
    /** 가까운 목표가 없을 때: 짧게 쉬면 오르는 P (소수) */
    val gainPoints: Double? = null
) {
    enum class Kind {
        /** 화면 끄고 n분 쉬면 목표 P */
        REST_FOR,
        /** 화면 끄고 n분 쉬면 +x.xP (1시간 안에 닿는 정수 목표가 없을 때) */
        REST_FOR_GAIN,
        /** 지금 마무리하고 n분 쉬면 목표 P (심야) */
        WIND_DOWN_REST,
        /** 확인을 미루고 n분 쉬면 목표 P (잦은 잠금 해제) */
        DELAY_CHECKS_REST,
        /** 12분 넘게 내려놓으면 잠금 해제가 +P (회복 예측이 없을 때) */
        DELAY_CHECKS,
        /** 지금 흐름이면 현재 P 유지 */
        KEEP,
        /** 회복 예측이 없을 때의 일반 안내 */
        REST_GENERIC
    }
}

object CoachActionPlanner {
    /** 다음 단계 경계가 이 점수 이내로 가까우면 그 경계를 회복 목표로 삼습니다. */
    const val TIER_TARGET_REACH_POINTS = 5
    const val DEFAULT_TARGET_STEP = 3
    const val MAX_TARGET_SCORE = 90
    /** 안내하는 휴식 시간의 상한. 이보다 오래 걸리는 목표는 제시하지 않습니다(의욕이 꺾이지 않게). */
    const val MAX_RECOVERY_MINUTES = 60
    /** 가까운 목표가 없을 때 보여 주는 짧은 휴식 */
    const val SHORT_REST_MINUTES = 30

    /**
     * 1시간 안에 닿는 가장 의미 있는 목표를 고릅니다.
     * 순서: 오늘의 지수 방어선 → 다음 단계 경계 → +3P → +2P → +1P. 닿는 것이 없으면 null.
     * [minutesTo]는 화면을 끄고 그 점수에 닿는 데 걸리는 분(없으면 null).
     */
    fun chooseRecovery(currentScore: Int, defenseLine: Int?, minutesTo: (Int) -> Int?): Pair<Int, Int>? {
        val candidates = buildList {
            defenseLine?.let { add(it) }
            ScoreTier.fromScore(currentScore).nextTierMinScore?.let { add(it) }
            add(currentScore + 3); add(currentScore + 2); add(currentScore + 1)
        }.distinct().filter { it in (currentScore + 1)..99 }
        for (target in candidates) {
            val minutes = minutesTo(target) ?: continue
            if (minutes in 1..MAX_RECOVERY_MINUTES) return target to minutes
        }
        return null
    }

    /** 회복 목표 점수: 다음 단계 경계가 5P 이내면 그 경계, 아니면 지금 + 3P(최대 90P). */
    fun recoveryTarget(currentScore: Int): Int {
        val boundary = ScoreTier.fromScore(currentScore).nextTierMinScore
        return if (boundary != null && boundary - currentScore in 1..TIER_TARGET_REACH_POINTS) boundary
        else (currentScore + DEFAULT_TARGET_STEP).coerceAtMost(MAX_TARGET_SCORE)
    }

    fun plan(
        recommendation: CoreIndexRecommendation?,
        recoveryMinutes: Int?,
        recoveryTargetScore: Int?,
        currentScore: Int,
        defenseLine: Int? = null,
        shortRestGain: Double? = null
    ): CoachAction {
        if (recoveryMinutes != null && recoveryTargetScore != null && recoveryTargetScore > currentScore) {
            val tier = ScoreTier.fromScore(recoveryTargetScore)
            val reaches = tier.takeIf {
                recoveryTargetScore == it.minScore && it != ScoreTier.fromScore(currentScore)
            }
            val kind = when (recommendation) {
                CoreIndexRecommendation.WIND_DOWN -> CoachAction.Kind.WIND_DOWN_REST
                CoreIndexRecommendation.BATCH_PHONE_CHECKS -> CoachAction.Kind.DELAY_CHECKS_REST
                else -> CoachAction.Kind.REST_FOR
            }
            return CoachAction(kind, recoveryMinutes, recoveryTargetScore, reaches, currentScore,
                reachesDefense = defenseLine != null && recoveryTargetScore == defenseLine)
        }
        if (shortRestGain != null && shortRestGain >= 0.1 && recommendation != CoreIndexRecommendation.KEEP_BALANCE) {
            return CoachAction(CoachAction.Kind.REST_FOR_GAIN, minutes = SHORT_REST_MINUTES,
                currentScore = currentScore, gainPoints = shortRestGain)
        }
        val kind = when (recommendation) {
            CoreIndexRecommendation.KEEP_BALANCE, null -> CoachAction.Kind.KEEP
            CoreIndexRecommendation.BATCH_PHONE_CHECKS -> CoachAction.Kind.DELAY_CHECKS
            else -> CoachAction.Kind.REST_GENERIC
        }
        return CoachAction(kind = kind, currentScore = currentScore)
    }
}
