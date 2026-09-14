package com.digitscore.app.ui

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.digitscore.app.i18n.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.data.security.DatabaseEncryptionManager
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.R
import androidx.compose.ui.res.stringResource
import com.digitscore.app.ui.dashboard.DashboardScreen
import com.digitscore.app.ui.navigation.Screen
import com.digitscore.app.ui.onboarding.OnboardingScreen
import com.digitscore.app.ui.settings.AppWeightSettingsScreen
import com.digitscore.app.ui.settings.PresetModeScreen
import com.digitscore.app.ui.statistics.StatisticsScreen
import com.digitscore.app.ui.theme.AppTheme
import com.digitscore.app.ui.theme.AppThemeMode
import com.digitscore.app.ui.theme.DigitsCoreTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasPermission = UsageStatsHelper.hasUsageStatsPermission(this)
        val startDestination = if (hasPermission) {
            Screen.Dashboard.route
        } else {
            Screen.Onboarding.route
        }

        setContent {
            var themeMode by remember {
                mutableStateOf(AppTheme.currentMode(this@MainActivity))
            }
            val systemIsDark = isSystemInDarkTheme()
            DigitsCoreTheme(darkTheme = themeMode.usesDarkColors(systemIsDark)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var retryAttempt by remember { mutableIntStateOf(0) }
                    val startupState by produceState<DatabaseStartupState>(
                        initialValue = DatabaseStartupState.Initializing,
                        key1 = retryAttempt
                    ) {
                        value = initializeDatabase(hasPermission)
                    }

                    when (val state = startupState) {
                        DatabaseStartupState.Initializing -> DatabaseInitializingScreen()
                        is DatabaseStartupState.Error -> DatabaseStartupErrorScreen(
                            message = state.message,
                            onRetry = { retryAttempt++ }
                        )
                        DatabaseStartupState.Ready -> {
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
                                        },
                                        onThemeModeChanged = { selectedMode: AppThemeMode ->
                                            AppTheme.saveMode(applicationContext, selectedMode)
                                            themeMode = selectedMode
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun initializeDatabase(hasPermission: Boolean): DatabaseStartupState =
        withContext(Dispatchers.IO) {
            openDatabase(hasPermission, allowPlaintextRestore = true)
        }

    private suspend fun openDatabase(
        hasPermission: Boolean,
        allowPlaintextRestore: Boolean
    ): DatabaseStartupState {
        return try {
            val database = DigitsDatabase.getInstance(applicationContext)
            // Room은 build()만으로 파일을 열지 않으므로 여기서 암호와 스키마까지 검증합니다.
            database.openHelper.writableDatabase
            DigitsDatabase.finalizeSuccessfulOpen(applicationContext)
            if (hasPermission) {
                val settings = database.settingsDao().getSettings()
                if (settings?.isTrackingEnabled == true) {
                    TrackerForegroundService.start(applicationContext)
                }
            }
            DatabaseStartupState.Ready
        } catch (error: Exception) {
            recoverOrShowDatabaseError(error, hasPermission, allowPlaintextRestore)
        } catch (error: LinkageError) {
            recoverOrShowDatabaseError(error, hasPermission, allowPlaintextRestore)
        }
    }

    private suspend fun recoverOrShowDatabaseError(
        error: Throwable,
        hasPermission: Boolean,
        allowPlaintextRestore: Boolean
    ): DatabaseStartupState {
        if (allowPlaintextRestore &&
            DigitsDatabase.restorePlaintextAfterOpenFailure(applicationContext, error)
        ) {
            return openDatabase(hasPermission, allowPlaintextRestore = false)
        }
        DatabaseEncryptionManager.markUnavailable(error)
        val message = if (error is LinkageError) {
            getString(R.string.database_security_component_error)
        } else {
            error.message ?: getString(R.string.database_open_error)
        }
        return DatabaseStartupState.Error(message)
    }
}

private sealed interface DatabaseStartupState {
    data object Initializing : DatabaseStartupState
    data object Ready : DatabaseStartupState
    data class Error(val message: String) : DatabaseStartupState
}

@androidx.compose.runtime.Composable
private fun DatabaseInitializingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.database_preparing),
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@androidx.compose.runtime.Composable
private fun DatabaseStartupErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.database_open_failed_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.database_preserved_message, message),
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
