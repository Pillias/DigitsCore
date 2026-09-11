package com.digitscore.app.notification

enum class StatusIconStyle(val id: String) {
    BIG_NUMBER("big_number"),
    SCORE_TIER("score_tier"),
    SCORE_PROPORTION("score_proportion"),
    NUMBER_FOCUS("number_focus");

    companion object {
        fun fromId(id: String?): StatusIconStyle =
            entries.firstOrNull { it.id == id } ?: BIG_NUMBER
    }
}
