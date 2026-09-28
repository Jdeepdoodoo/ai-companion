import os

theme_dir = "/home/ubuntu/ai-companion/ai-companion-android/app/src/main/java/com/hydra/shell/ui/theme"
os.makedirs(theme_dir, exist_ok=True)

with open(f"{theme_dir}/Color.kt", "w") as f:
    f.write("""package com.hydra.shell.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0D0D0D)
val Foreground = Color(0xFFFFFFFF)
val Card = Color(0xFF141414)
val CardForeground = Color(0xFFFFFFFF)
val Primary = Color(0xFF00D4AA)
val PrimaryForeground = Color(0xFF000000)
val Secondary = Color(0xFF1A1A1A)
val SecondaryForeground = Color(0xFFFFFFFF)
val Muted = Color(0xFF1A1A1A)
val MutedForeground = Color(0xFFAAAAAA)
val Destructive = Color(0xFFFF4757)
val Border = Color(0xFF2A2A2A)
val Input = Color(0xFF2A2A2A)
""")

with open(f"{theme_dir}/Shape.kt", "w") as f:
    f.write("""package com.hydra.shell.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val StockhubShapes = Shapes(
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraSmall = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)
""")

with open(f"{theme_dir}/Theme.kt", "w") as f:
    f.write("""package com.hydra.shell.ui.theme

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
""")
