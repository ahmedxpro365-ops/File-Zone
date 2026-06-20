package com.filezone.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FA7FF),          // Vibrant tech active blue
    secondary = Color(0xFF8E9EB8),        // Cool slate steel secondary
    tertiary = Color(0xFF6DE0B2),         // Energy emerald storage accent
    background = Color(0xFF0F172A),       // Deep slate-900 background
    surface = Color(0xFF1E293B),          // Clean slate-800 card surfaces
    onPrimary = Color(0xFF003063),
    onSecondary = Color(0xFF263244),
    onTertiary = Color(0xFF003823),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFF1F5F9),
    primaryContainer = Color(0xFF00448A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF005FAF),          // Rich professional blue
    secondary = Color(0xFF4B5A6F),        // Balanced slate grey
    tertiary = Color(0xFF00875A),         // Strong emerald green accent
    background = Color(0xFFF8FAFC),       // Clean light gray base
    surface = Color(0xFFFFFFFF),          // Crisp white container surfaces
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A40),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun FileZoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Changed from true to false for consistent branding
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
