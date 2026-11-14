package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D233A),
    primaryContainer = NavyDark,
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = SafetyAmberLight,
    onSecondary = Color(0xFF452200),
    secondaryContainer = SafetyAmberDark,
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF80CBC4),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkTextMuted,
    outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6F5),
    onPrimaryContainer = NavyDark,
    secondary = SafetyAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECD6),
    onSecondaryContainer = SafetyAmberDark,
    tertiary = BlueprintTeal,
    background = SurfaceLight,
    surface = CardLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = TextDark,
    onSurface = TextDark,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set false to prioritize the distinct engineering theme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
