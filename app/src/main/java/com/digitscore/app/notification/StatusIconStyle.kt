package com.digitscore.app.notification

enum class StatusIconStyle(val id: String) {
    SCORE_PROPORTION("score_proportion"),
    SCORE_TIER("score_tier");

    companion object {
        fun fromId(id: String?): StatusIconStyle =
            entries.firstOrNull { it.id == id } ?: SCORE_TIER
    }
}
