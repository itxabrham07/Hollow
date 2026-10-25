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

// Electric Blue Accent (clean modern contrast)
val ElectricBlue = Color(0xFF0066FF)
val AccentSky = ElectricBlue
val AccentSkyBright = Color(0xFF388BFF)
val AccentSkySoft = Color(0xFF0D254C)
val AccentMint = Color(0xFF4ADE80)         // Success green badge
val AccentMintSoft = Color(0xFF143823)

// Light Theme Palette
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceCard = Color(0xFFF1F5F9)
val LightSurfaceElevated = Color(0xFFE2E8F0)
val LightBorder = Color(0xFFCBD5E1)
val LightBorderSubtle = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)
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
