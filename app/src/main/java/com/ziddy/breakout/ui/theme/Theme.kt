package com.ziddy.breakout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.ziddy.breakout.model.Severity

object AppColors {
    val Background = Color(0xFF0A0C10)
    val Console = Color(0xFF07090D)
    val Surface = Color(0xFF12151C)
    val SurfaceHigh = Color(0xFF1A1F29)
    val Outline = Color(0xFF2A3140)
    val TextPrimary = Color(0xFFE9ECF2)
    val TextSecondary = Color(0xFF8A93A6)
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF5449)
    val Green = Color(0xFF3DDC84)
    val Blue = Color(0xFF4DA3FF)
    val Grey = Color(0xFF98A2B3)

    fun forSeverity(severity: Severity): Color = when (severity) {
        Severity.CRITICAL -> Red
        Severity.HIGH -> Amber
        Severity.MEDIUM -> Blue
        Severity.LOW -> Grey
    }
}

val Mono: FontFamily = FontFamily.Monospace

private val DarkScheme = darkColorScheme(
    primary = AppColors.Amber,
    onPrimary = Color(0xFF201403),
    primaryContainer = Color(0xFF3A2C05),
    onPrimaryContainer = Color(0xFFFFD469),
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHigh,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Outline,
    error = AppColors.Red,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
