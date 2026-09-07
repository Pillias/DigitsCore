package com.digitscore.app.notification

enum class StatusIconStyle(val id: String) {
    SCORE_PROPORTION("score_proportion"),
    SCORE_TIER("score_tier"),
    NUMBER_FOCUS("number_focus");

    companion object {
        fun fromId(id: String?): StatusIconStyle =
            entries.firstOrNull { it.id == id } ?: SCORE_TIER
    }
}
