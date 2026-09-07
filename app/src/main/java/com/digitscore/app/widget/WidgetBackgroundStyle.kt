package com.digitscore.app.widget

enum class WidgetBackgroundStyle(val id: String) {
    DARK("dark"),
    WHITE("white"),
    TRANSPARENT("transparent");

    companion object {
        fun fromId(id: String?): WidgetBackgroundStyle =
            entries.firstOrNull { it.id == id } ?: DARK
    }
}
