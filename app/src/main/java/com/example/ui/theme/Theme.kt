package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val IsoVaultDarkScheme = darkColorScheme(
    primary = AccentSkyBright,
    onPrimary = Color(0xFF07131D),
    primaryContainer = AccentSkySoft,
    onPrimaryContainer = TextWhite,

    secondary = AccentSky,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = TextWhite,

    tertiary = AccentMint,
    onTertiary = Color.White,
    tertiaryContainer = AccentMintSoft,
    onTertiaryContainer = TextWhite,

    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = TextMuted,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,

    error = AccentCoral,
    onError = Color.White
)

private val IsoVaultLightScheme = lightColorScheme(
    primary = AccentSky,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),

    secondary = AccentSky,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,

    tertiary = AccentMint,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),

    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,

    error = AccentCoral,
    onError = Color.White
)

@Composable
fun IsoVaultTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Dynamic color intentionally OFF to preserve our custom minimal palette
    val colorScheme = if (darkTheme) IsoVaultDarkScheme else IsoVaultLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    IsoVaultTheme(darkTheme = darkTheme, content = content)
}
