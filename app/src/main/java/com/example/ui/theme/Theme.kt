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

private val IsoVaultWarmLightScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = Color.White,
    primaryContainer = SageSoft,
    onPrimaryContainer = ForestDark,

    secondary = ClaySecondary,
    onSecondary = Color.White,
    secondaryContainer = ClaySoft,
    onSecondaryContainer = Color(0xFF4A1D10),

    tertiary = HoneyTertiary,
    onTertiary = Color.White,
    tertiaryContainer = HoneySoft,
    onTertiaryContainer = Color(0xFF3E2700),

    background = WarmLinen,
    onBackground = InkPrimary,
    surface = WarmIvory,
    onSurface = InkPrimary,
    surfaceVariant = WarmSand,
    onSurfaceVariant = InkSecondary,
    outline = WarmStone,
    outlineVariant = Color(0xFFEDE7DC),

    error = CoralError,
    onError = Color.White
)

private val IsoVaultWarmDarkScheme = darkColorScheme(
    primary = Color(0xFF8CD4B5),
    onPrimary = Color(0xFF0B3324),
    primaryContainer = Color(0xFF1B4B39),
    onPrimaryContainer = Color(0xFFDDF0E6),

    secondary = Color(0xFFE89980),
    onSecondary = Color(0xFF4A1D10),
    secondaryContainer = Color(0xFF692E1D),
    onSecondaryContainer = Color(0xFFFAE7E1),

    tertiary = Color(0xFFE9BA67),
    onTertiary = Color(0xFF3E2700),
    tertiaryContainer = Color(0xFF593A00),
    onTertiaryContainer = Color(0xFFFDF3DF),

    background = Color(0xFF181614),
    onBackground = Color(0xFFF5F1EB),
    surface = Color(0xFF221F1C),
    onSurface = Color(0xFFF5F1EB),
    surfaceVariant = Color(0xFF2D2925),
    onSurfaceVariant = Color(0xFFBDB5AC),
    outline = Color(0xFF3E3934),
    outlineVariant = Color(0xFF2D2925),

    error = Color(0xFFF28B82),
    onError = Color(0xFF601410)
)

@Composable
fun IsoVaultTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IsoVaultWarmDarkScheme else IsoVaultWarmLightScheme
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
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = IsoVaultTheme(darkTheme = darkTheme, content = content)
