package dev.tvtuner.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Colour tokens ────────────────────────────────────────────────────────────

private val TvBlue = Color(0xFF1E88E5)
private val TvBlueContainer = Color(0xFF0D47A1)
private val TvBlueDark = Color(0xFF90CAF9)

private val TvBackground = Color(0xFF0A0A0F)
private val TvSurface = Color(0xFF14141C)
private val TvSurfaceVariant = Color(0xFF1E1E28)
private val TvOutline = Color(0xFF3A3A4A)

private val DarkColorScheme = darkColorScheme(
    primary = TvBlue,
    onPrimary = Color.White,
    primaryContainer = TvBlueContainer,
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFF78909C),
    onSecondary = Color.White,
    background = TvBackground,
    onBackground = Color(0xFFE8E8EE),
    surface = TvSurface,
    onSurface = Color(0xFFE8E8EE),
    surfaceVariant = TvSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B0BE),
    outline = TvOutline,
    error = Color(0xFFEF5350),
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1A1A20),
    surface = Color.White,
    onSurface = Color(0xFF1A1A20),
)

// ── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun TvTunerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TvTunerTypography,
        content = content,
    )
}
