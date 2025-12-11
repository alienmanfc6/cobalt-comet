package com.alienmantech.cobaltcomet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CometTail,
    onPrimary = DeepSpace,
    primaryContainer = DarkNebula,
    onPrimaryContainer = Starlight,
    secondary = NebulaPurple,
    onSecondary = Starlight,
    secondaryContainer = DarkNebula,
    onSecondaryContainer = Starlight,
    tertiary = MeteorOrange,
    onTertiary = DeepSpace,
    background = CosmicSurface,
    onBackground = Starlight,
    surface = CosmicSurface,
    onSurface = Starlight,
    surfaceVariant = DeepCobalt,
    onSurfaceVariant = Starlight,
    outline = CometTail
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltBlue,
    onPrimary = Starlight,
    primaryContainer = CometTail,
    onPrimaryContainer = DeepSpace,
    secondary = NebulaPurple,
    onSecondary = Starlight,
    secondaryContainer = NebulaPurple,
    onSecondaryContainer = Starlight,
    tertiary = MeteorOrange,
    onTertiary = DeepSpace,
    background = Starlight,
    onBackground = DeepSpace,
    surface = Starlight,
    onSurface = DeepSpace,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = DeepCobalt,
    outline = DeepCobalt
)

@Composable
fun CobaltCometTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}