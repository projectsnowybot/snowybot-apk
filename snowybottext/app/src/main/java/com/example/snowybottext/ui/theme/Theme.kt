package com.example.snowybottext.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = OnDarkTextPrimary,
    secondary = CyanAccent,
    onSecondary = DarkBackground,
    tertiary = PurpleAccent,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = OnDarkTextPrimary,
    surface = DarkSurface,
    onSurface = OnDarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkTextSecondary,
    outline = DarkBorder,
    error = LossRed,
    onError = OnDarkTextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8AF8CB),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF006879),
    onSecondary = Color.White,
    background = Color(0xFFF5FAF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FAF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404944),
)

@Composable
fun SnowybottextTheme(
    darkTheme: Boolean = true, // Preserve the dashboard's dark financial console aesthetic
    dynamicColor: Boolean = true, // Use wallpaper-derived colors when requested by the system
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
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
