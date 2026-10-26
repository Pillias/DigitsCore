package com.digitscore.app.i18n

import android.content.Context
import com.digitscore.app.R
import com.digitscore.app.model.ScoreTier

/** 지수 단계 이름 (안정·보통·주의·휴식 필요). */
fun Context.localizedGrade(tier: ScoreTier): String = getString(
    when (tier) {
        ScoreTier.STABLE -> R.string.tier_stable
        ScoreTier.FAIR -> R.string.tier_fair
        ScoreTier.CAUTION -> R.string.tier_caution
        ScoreTier.REST -> R.string.tier_rest
    }
)

/** 지수 단계별 한 줄 설명. */
fun Context.localizedGradeDescription(tier: ScoreTier): String = getString(
    when (tier) {
        ScoreTier.STABLE -> R.string.tier_stable_description
        ScoreTier.FAIR -> R.string.tier_fair_description
        ScoreTier.CAUTION -> R.string.tier_caution_description
        ScoreTier.REST -> R.string.tier_rest_description
    }
)
