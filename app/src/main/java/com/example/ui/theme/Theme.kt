package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SiaDarkColorScheme = darkColorScheme(
    primary = SiaCyan,
    onPrimary = SiaBgDark,
    primaryContainer = SiaViolet,
    onPrimaryContainer = SiaTextPrimary,
    secondary = SiaViolet,
    onSecondary = SiaTextPrimary,
    secondaryContainer = SiaSurfaceVariantDark,
    onSecondaryContainer = SiaTextPrimary,
    tertiary = SiaPink,
    onTertiary = SiaBgDark,
    background = SiaBgDark,
    onBackground = SiaTextPrimary,
    surface = SiaSurfaceDark,
    onSurface = SiaTextPrimary,
    surfaceVariant = SiaSurfaceVariantDark,
    onSurfaceVariant = SiaTextSecondary,
    outline = SiaBorder,
    error = SiaRed,
    onError = SiaTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // SIA defaults to futuristic dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = SiaDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
