package com.digitscore.app.ui.theme

import android.content.Context

enum class AppThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun usesDarkColors(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromId(id: String?): AppThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

object AppTheme {
    private const val PREFS = "app_theme"
    private const val KEY_MODE = "mode"

    fun currentMode(context: Context): AppThemeMode = AppThemeMode.fromId(
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, AppThemeMode.SYSTEM.id)
    )

    fun saveMode(context: Context, mode: AppThemeMode) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.id)
            .apply()
    }
}
