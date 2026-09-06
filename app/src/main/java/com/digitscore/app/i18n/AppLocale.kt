package com.digitscore.app.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY_LANGUAGE = "language"
    const val KOREAN = "ko"
    const val ENGLISH = "en"

    fun currentLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, if (Locale.getDefault().language == KOREAN) KOREAN else ENGLISH)
            .takeIf { it == KOREAN || it == ENGLISH }
            ?: ENGLISH

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(currentLanguage(context))
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    fun setLanguage(activity: Activity, language: String) {
        val safeLanguage = if (language == ENGLISH) ENGLISH else KOREAN
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, safeLanguage)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags(safeLanguage)
        }
        activity.recreate()
    }

    /** Service·widget처럼 Activity 밖에서 생성하는 문구에도 선택 언어를 적용합니다. */
    fun stringsContext(context: Context): Context = wrap(context.applicationContext)
}
