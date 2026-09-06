package com.digitscore.app.i18n

import android.content.Context
import com.digitscore.app.R
import com.digitscore.app.engine.ScoreGrade

fun Context.localizedGrade(grade: ScoreGrade): String = getString(
    when (grade) {
        ScoreGrade.S -> R.string.grade_s
        ScoreGrade.A -> R.string.grade_a
        ScoreGrade.B -> R.string.grade_b
        ScoreGrade.C -> R.string.grade_c
        ScoreGrade.D -> R.string.grade_d
        ScoreGrade.F -> R.string.grade_f
    }
)

fun Context.localizedGradeDescription(grade: ScoreGrade): String = getString(
    when (grade) {
        ScoreGrade.S -> R.string.grade_s_description
        ScoreGrade.A -> R.string.grade_a_description
        ScoreGrade.B -> R.string.grade_b_description
        ScoreGrade.C -> R.string.grade_c_description
        ScoreGrade.D -> R.string.grade_d_description
        ScoreGrade.F -> R.string.grade_f_description
    }
)
