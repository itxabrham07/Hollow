package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IsoVaultDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFF9CF4FF),

    secondary = CyberEmerald,
    onSecondary = Color(0xFF003913),
    secondaryContainer = Color(0xFF00531D),
    onSecondaryContainer = Color(0xFFA6FFB8),

    tertiary = CyberAmber,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF623E00),
    onTertiaryContainer = Color(0xFFFFDDB3),

    background = DeepObsidian,
    onBackground = TextPrimary,
    surface = SurfaceObsidian,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    outlineVariant = Color(0xFF1E2D47),

    error = CyberRed,
    onError = Color.White
)

private val IsoVaultLightColorScheme = lightColorScheme(
    primary = Color(0xFF007A8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3F3FA),
    onPrimaryContainer = Color(0xFF001F25),

    secondary = Color(0xFF00833C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC0F8CF),
    onSecondaryContainer = Color(0xFF00210A),

    tertiary = Color(0xFF8A5800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B8),
    onTertiaryContainer = Color(0xFF2B1700),

    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF101826),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101826),
    surfaceVariant = Color(0xFFE2E9F4),
    onSurfaceVariant = Color(0xFF47566C),
    outline = Color(0xFFB5C4D8)
)

@Composable
fun IsoVaultTheme(
    darkTheme: Boolean = true, // Default to sleek cybersecurity dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IsoVaultDarkColorScheme else IsoVaultLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias for template
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = IsoVaultTheme(darkTheme = darkTheme, content = content)
