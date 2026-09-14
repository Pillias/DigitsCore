package com.digitscore.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF001D33),
    primaryContainer = Color(0xFF0D3A5A),
    onPrimaryContainer = Color(0xFFD6ECFF),
    secondary = ScoreGreen,
    onSecondary = Color(0xFF00391B),
    secondaryContainer = Color(0xFF0B4A2B),
    onSecondaryContainer = Color(0xFFB5F6CF),
    tertiary = ScoreYellow,
    onTertiary = Color(0xFF3B2F00),
    tertiaryContainer = Color(0xFF544600),
    onTertiaryContainer = Color(0xFFFFF1A6),
    background = DarkBackground,
    onBackground = Color(0xFFF0F6FC),
    surface = DarkSurface,
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = DarkCard,
    surfaceContainer = DarkSurface,
    surfaceContainerLow = Color(0xFF11161D),
    surfaceContainerHigh = DarkCard,
    surfaceContainerHighest = Color(0xFF292F37),
    onSurfaceVariant = Color(0xFFC9D1D9),
    outline = Color(0xFF8B949E),
    outlineVariant = Color(0xFF30363D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = ScoreGreen,
    tertiary = ScoreYellow,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceMuted,
    surfaceContainerHighest = Color(0xFFE7EAF0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = Color(0xFF4F5762),
    outline = LightOutline,
    outlineVariant = Color(0xFFD9DEE5)
)

private val DigitsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DigitsCoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DigitsShapes,
        content = content
    )
}
