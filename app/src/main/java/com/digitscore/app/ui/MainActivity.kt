package com.digitscore.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.ui.dashboard.DashboardScreen
import com.digitscore.app.ui.navigation.Screen
import com.digitscore.app.ui.onboarding.OnboardingScreen
import com.digitscore.app.ui.settings.AppWeightSettingsScreen
import com.digitscore.app.ui.settings.PresetModeScreen
import com.digitscore.app.ui.statistics.StatisticsScreen
import com.digitscore.app.ui.theme.DigitsCoreTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasPermission = UsageStatsHelper.hasUsageStatsPermission(this)
        if (hasPermission) {
            lifecycleScope.launch(Dispatchers.IO) {
                val settings = DigitsDatabase.getInstance(applicationContext)
                    .settingsDao()
                    .getSettings()
                if (settings?.isTrackingEnabled == true) {
                    TrackerForegroundService.start(applicationContext)
                }
            }
        }

        val startDestination = if (hasPermission) {
            Screen.Dashboard.route
        } else {
            Screen.Onboarding.route
        }

        setContent {
            DigitsCoreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(Screen.Onboarding.route) {
                            OnboardingScreen(
                                onNavigateToDashboard = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val dao = DigitsDatabase.getInstance(applicationContext).settingsDao()
                                        val current = dao.getSettings() ?: UserSettingsEntity()
                                        dao.insertOrUpdateSettings(current.copy(isTrackingEnabled = true))
                                        TrackerForegroundService.start(applicationContext)
                                    }
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                onNavigateToStatistics = {
                                    navController.navigate(Screen.Statistics.route)
                                },
                                onNavigateToAppSettings = {
                                    navController.navigate(Screen.AppSettings.route)
                                },
                                onNavigateToPresetSettings = {
                                    navController.navigate(Screen.PresetSettings.route)
                                }
                            )
                        }

                        composable(Screen.Statistics.route) {
                            StatisticsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.AppSettings.route) {
                            AppWeightSettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.PresetSettings.route) {
                            PresetModeScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
