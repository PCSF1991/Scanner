package br.com.ccgeo.obd.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFF2A900)
val Teal = Color(0xFF2EC4B6)
val Danger = Color(0xFFFF5A5F)
val Ok = Color(0xFF4CD964)
val Muted = Color(0xFF8A97A3)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1300),
    primaryContainer = Color(0xFF3A2C00),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Teal,
    onSecondary = Color(0xFF00201D),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFE6EBEF),
    surface = Color(0xFF0F1418),
    onSurface = Color(0xFFE6EBEF),
    surfaceVariant = Color(0xFF1C252C),
    onSurfaceVariant = Color(0xFFB4C0CA),
    surfaceContainer = Color(0xFF161E24),
    surfaceContainerHigh = Color(0xFF1C252C),
    surfaceContainerHighest = Color(0xFF243039),
    outline = Color(0xFF3A4752),
    error = Danger,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
