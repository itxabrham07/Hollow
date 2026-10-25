package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val IsoVaultDarkScheme = darkColorScheme(
    primary = AccentSky,
    onPrimary = DarkBackground,
    primaryContainer = AccentSkySoft,
    onPrimaryContainer = Color.White,

    secondary = AccentMint,
    onSecondary = DarkBackground,
    secondaryContainer = AccentMintSoft,
    onSecondaryContainer = Color.White,

    tertiary = AccentAmber,
    onTertiary = DarkBackground,
    tertiaryContainer = AccentAmberSoft,
    onTertiaryContainer = Color.White,

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

@Composable
fun IsoVaultTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = IsoVaultDarkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
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
    IsoVaultTheme(darkTheme = true, content = content)
}
