package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Matte Dark Palette (from reference mockup)
val DarkBackground = Color(0xFF111215)
val DarkSurface = Color(0xFF17181E)
val DarkSurfaceCard = Color(0xFF20222A)
val DarkSurfaceElevated = Color(0xFF282A34)
val DarkBorder = Color(0xFF2C2F3A)
val DarkBorderSubtle = Color(0xFF23252E)

// Keypad Specific Colors (mockup leftmost screen)
val KeypadButtonBg = Color(0xFF212328)
val KeypadWhiteCircle = Color(0xFFFFFFFF)
val KeypadDarkIcon = Color(0xFF121316)

// Accents (mockup center-right screens)
val AccentSky = Color(0xFF52B5F8)          // Vibrant sky blue (+ button, checkmark button, active dot)
val AccentSkySoft = Color(0xFF1E3A52)
val AccentMint = Color(0xFF4ADE80)         // Success green badge
val AccentMintSoft = Color(0xFF143823)
val AccentAmber = Color(0xFFFBBF24)        // Warm standby/warning
val AccentAmberSoft = Color(0xFF382D12)
val AccentCoral = Color(0xFFF87171)        // Error / blocked red
val AccentCoralSoft = Color(0xFF3B1A1A)
val AccentLavender = Color(0xFFA78BFA)     // Purple accent
val AccentLavenderSoft = Color(0xFF281E45)
val AccentPink = Color(0xFFF472B6)

// Typography Colors
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF9A9DA8)
val TextSubtle = Color(0xFF656875)

// Compatibility aliases mapped to the new matte dark palette
val ForestPrimary = AccentSky
val ForestDark = AccentSkySoft
val SageSoft = DarkSurfaceCard
val SageTint = DarkSurfaceElevated

val ClaySecondary = AccentCoral
val ClaySoft = AccentCoralSoft

val HoneyTertiary = AccentAmber
val HoneySoft = AccentAmberSoft

val CoralError = AccentCoral
val CoralSoft = AccentCoralSoft

val LavenderAccent = AccentLavender
val LavenderSoft = AccentLavenderSoft

// Daylight Surfaces mapped to Dark Mode
val WarmLinen = DarkBackground
val WarmIvory = DarkSurfaceCard
val WarmSand = DarkSurfaceElevated
val WarmStone = DarkBorder

// Typography Mappings
val InkPrimary = TextWhite
val InkSecondary = TextMuted
val InkTertiary = TextSubtle

val CyberCyan = AccentSky
val CyberCyanDark = AccentSkySoft
val CyberEmerald = AccentMint
val CyberEmeraldDark = AccentMintSoft
val CyberAmber = AccentAmber
val CyberRed = AccentCoral
val CyberViolet = AccentLavender

val DeepObsidian = DarkBackground
val SurfaceObsidian = DarkSurfaceCard
val SurfaceElevated = DarkSurfaceElevated
val SurfaceCard = DarkSurfaceCard
val SurfaceBorder = DarkBorder

val TextPrimary = TextWhite
val TextSecondary = TextMuted
val TextTertiary = TextSubtle

val TerminalBlack = Color(0xFF14151B)
val TerminalGreen = AccentMint
