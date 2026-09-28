package com.hydra.shell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StockhubDarkColorScheme = darkColorScheme(
    background = Background,
    onBackground = Foreground,
    surface = Card,
    onSurface = CardForeground,
    surfaceVariant = Muted,
    onSurfaceVariant = MutedForeground,
    primary = Primary,
    onPrimary = PrimaryForeground,
    secondary = Secondary,
    onSecondary = SecondaryForeground,
    error = Destructive,
    outline = Border,
    outlineVariant = Input
)

@Composable
fun HydraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StockhubDarkColorScheme,
        shapes = StockhubShapes,
        content = content
    )
}
