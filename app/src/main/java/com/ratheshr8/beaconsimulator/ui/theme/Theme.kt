package com.ratheshr8.beaconsimulator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = OceanDeep,
    onPrimary = Color.White,
    primaryContainer = OceanLight.copy(alpha = 0.25f),
    onPrimaryContainer = OceanDeep,
    secondary = SignalTeal,
    onSecondary = Color.White,
    tertiary = SignalAmber,
    background = Mist,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    onSurfaceVariant = MutedInk,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = OceanLight,
    onPrimary = Ink,
    primaryContainer = OceanDeep,
    onPrimaryContainer = Color.White,
    secondary = SignalTeal,
    onSecondary = Color.White,
    tertiary = SignalAmber,
    background = Color(0xFF0A1620),
    onBackground = Color(0xFFE8F1F6),
    surface = Color(0xFF122433),
    onSurface = Color(0xFFE8F1F6),
    onSurfaceVariant = Color(0xFFA8B8C2),
    error = Danger,
)

@Composable
fun BeaconSimulatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
