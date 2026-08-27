package com.digitscore.app.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Statistics : Screen("statistics")
    data object AppSettings : Screen("app_settings")
    data object PresetSettings : Screen("preset_settings")
}
