package com.digitscore.app.i18n

import android.content.Context
import com.digitscore.app.R
import com.digitscore.app.engine.CoachAction

/** 할 일 한 줄 문구. 행동과 결과(P)를 항상 함께 말합니다. */
fun Context.coachActionText(action: CoachAction): String {
    val target = action.targetScore
    val targetText = when {
        target == null -> ""
        action.reachesDefense -> getString(R.string.coach_target_defense, target)
        action.reachesTier != null -> getString(R.string.coach_target_tier, localizedGrade(action.reachesTier), target)
        else -> getString(R.string.coach_target_score, target)
    }
    return when (action.kind) {
        CoachAction.Kind.REST_FOR -> getString(R.string.coach_rest_for, action.minutes ?: 0, targetText)
        CoachAction.Kind.WIND_DOWN_REST -> getString(R.string.coach_wind_down_rest, action.minutes ?: 0, targetText)
        CoachAction.Kind.DELAY_CHECKS_REST -> getString(R.string.coach_delay_checks_rest, action.minutes ?: 0, targetText)
        CoachAction.Kind.REST_FOR_GAIN -> getString(R.string.coach_rest_for_gain, action.minutes ?: 0)
        CoachAction.Kind.DELAY_CHECKS -> getString(R.string.coach_delay_checks)
        CoachAction.Kind.KEEP -> getString(R.string.coach_keep, action.currentScore)
        CoachAction.Kind.REST_GENERIC -> getString(R.string.coach_rest_generic)
    }
}
