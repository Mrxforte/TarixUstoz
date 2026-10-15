package com.tarixustoz.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Premium educational palette
private val PrimaryBlue = Color(0xFF1B4F72)
private val PrimaryBlueLight = Color(0xFF2980B9)
private val AccentTeal = Color(0xFF16A085)
private val AccentViolet = Color(0xFF6C5CE7)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EAF8),
    onPrimaryContainer = Color(0xFF0E2F44),
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5E3),
    onSecondaryContainer = Color(0xFF0E6655),
    tertiary = AccentViolet,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1A202C),
    surface = Color.White,
    onSurface = Color(0xFF1A202C),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626),
    onError = Color.White,
    outline = Color(0xFFCBD5E1)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5DADE2),
    onPrimary = Color(0xFF0E2F44),
    primaryContainer = Color(0xFF1B4F72),
    onPrimaryContainer = Color(0xFFD6EAF8),
    secondary = Color(0xFF48C9B0),
    onSecondary = Color(0xFF0E6655),
    secondaryContainer = Color(0xFF0E6655),
    onSecondaryContainer = Color(0xFFD5F5E3),
    tertiary = Color(0xFFA29BFE),
    onTertiary = Color(0xFF2D1B69),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
    onError = Color(0xFF7F1D1D),
    outline = Color(0xFF475569)
)

@Composable
fun TarixUstozTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
