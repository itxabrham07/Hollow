package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val IsoVaultProLightScheme = lightColorScheme(
    primary = ProEmeraldPrimary,          // Onyx #111827
    onPrimary = Color.White,
    primaryContainer = ProEmeraldContainer,  // Light Gray #F1F5F9
    onPrimaryContainer = ProEmeraldDark,

    secondary = ProIndigoSecondary,       // Green #22C55E
    onSecondary = Color.White,
    secondaryContainer = ProIndigoContainer,
    onSecondaryContainer = Color(0xFF14532D),

    tertiary = ProAmberTertiary,          // Slate #64748B
    onTertiary = Color.White,
    tertiaryContainer = ProAmberContainer,
    onTertiaryContainer = ProInkPrimary,

    background = ProBackgroundLight,      // #F8FAFC
    onBackground = ProInkPrimary,
    surface = ProSurfaceLight,            // White
    onSurface = ProInkPrimary,
    surfaceVariant = ProSurfaceLowLight,  // #F1F5F9
    onSurfaceVariant = ProInkTertiary,    // #64748B
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ProSurfaceLowLight,
    surfaceContainer = ProSurfaceContainerLight,
    surfaceContainerHigh = ProSurfaceHighLight,
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = ProOutlineLight,
    outlineVariant = ProOutlineSubtleLight,

    error = ProCoralError,
    onError = Color.White,
    errorContainer = ProCoralContainer,
    onErrorContainer = Color(0xFF5C1010)
)

private val IsoVaultProDarkScheme = darkColorScheme(
    primary = ProEmeraldLight,            // Slate #334155
    onPrimary = Color.White,
    primaryContainer = ProEmeraldContainerDark,  // #1E293B
    onPrimaryContainer = Color(0xFFF1F5F9),

    secondary = ProIndigoDark,            // Bright Green #4ADE80
    onSecondary = Color(0xFF052E16),
    secondaryContainer = ProIndigoContainerDark,
    onSecondaryContainer = Color(0xFFBBF7D0),

    tertiary = ProAmberDark,              // Light Slate #94A3B8
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = ProAmberContainerDark,
    onTertiaryContainer = Color(0xFFE2E8F0),

    background = ProBackgroundDark,       // #0B0F1A
    onBackground = Color(0xFFF8FAFC),
    surface = ProSurfaceDark,             // #111827
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = ProSurfaceLowDark,   // #1E293B
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF060912),
    surfaceContainerLow = ProSurfaceLowDark,
    surfaceContainer = ProSurfaceContainerDark,
    surfaceContainerHigh = ProSurfaceHighDark,
    surfaceContainerHighest = Color(0xFF2D3A52),
    outline = ProOutlineDark,
    outlineVariant = ProOutlineSubtleDark,

    error = ProCoralDark,
    onError = Color(0xFF4C0519),
    errorContainer = ProCoralContainerDark,
    onErrorContainer = Color(0xFFFFE4E6)
)

val IsoVaultProShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun IsoVaultTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> IsoVaultProDarkScheme
        else -> IsoVaultProLightScheme
    }

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
        shapes = IsoVaultProShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = IsoVaultTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
