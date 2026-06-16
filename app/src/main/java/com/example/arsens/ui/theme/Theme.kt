package com.example.arsens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ArsensColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = OnAccent,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = AccentTeal,
    onSecondary = OnAccent,
    secondaryContainer = AppSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentBlueDark,
    onTertiary = OnAccent,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    outlineVariant = Outline,
    error = AppError,
    onError = OnError,
)

@Composable
fun ARSensTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArsensColorScheme,
        typography = Typography,
        content = content
    )
}
