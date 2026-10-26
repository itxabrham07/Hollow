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
    primary = ProEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = ProEmeraldContainer,
    onPrimaryContainer = ProEmeraldDark,

    secondary = ProIndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = ProIndigoContainer,
    onSecondaryContainer = Color(0xFF142742),

    tertiary = ProAmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = ProAmberContainer,
    onTertiaryContainer = Color(0xFF3E2200),

    background = ProBackgroundLight,
    onBackground = ProInkPrimary,
    surface = ProSurfaceLight,
    onSurface = ProInkPrimary,
    surfaceVariant = ProSurfaceLowLight,
    onSurfaceVariant = ProInkSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ProSurfaceLowLight,
    surfaceContainer = ProSurfaceContainerLight,
    surfaceContainerHigh = ProSurfaceHighLight,
    surfaceContainerHighest = Color(0xFFD1DDD8),
    outline = ProOutlineLight,
    outlineVariant = ProOutlineSubtleLight,

    error = ProCoralError,
    onError = Color.White,
    errorContainer = ProCoralContainer,
    onErrorContainer = Color(0xFF5C1010)
)

private val IsoVaultProDarkScheme = darkColorScheme(
    primary = ProEmeraldLight,
    onPrimary = Color(0xFF00382B),
    primaryContainer = ProEmeraldContainerDark,
    onPrimaryContainer = Color(0xFFBFF5E3),

    secondary = ProIndigoDark,
    onSecondary = Color(0xFF0E233E),
    secondaryContainer = ProIndigoContainerDark,
    onSecondaryContainer = Color(0xFFDCE5F7),

    tertiary = ProAmberDark,
    onTertiary = Color(0xFF452600),
    tertiaryContainer = ProAmberContainerDark,
    onTertiaryContainer = Color(0xFFFFDFBA),

    background = ProBackgroundDark,
    onBackground = Color(0xFFEAF2EE),
    surface = ProSurfaceDark,
    onSurface = Color(0xFFEAF2EE),
    surfaceVariant = ProSurfaceLowDark,
    onSurfaceVariant = Color(0xFFA5B8B1),
    surfaceContainerLowest = Color(0xFF090E0C),
    surfaceContainerLow = ProSurfaceLowDark,
    surfaceContainer = ProSurfaceContainerDark,
    surfaceContainerHigh = ProSurfaceHighDark,
    surfaceContainerHighest = Color(0xFF31423C),
    outline = ProOutlineDark,
    outlineVariant = ProOutlineSubtleDark,

    error = ProCoralDark,
    onError = Color(0xFF560A0A),
    errorContainer = ProCoralContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
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
