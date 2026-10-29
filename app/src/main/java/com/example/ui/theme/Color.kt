package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Hollow Onyx Palette (matches OnboardingAuthScreen) ────────────────────────
val ProEmeraldPrimary     = Color(0xFF111827)   // Dark Onyx — primary actions
val ProEmeraldDark        = Color(0xFF0F172A)   // Deeper Onyx — container/pressed
val ProEmeraldLight       = Color(0xFF334155)   // Slate — dark-mode primary
val ProEmeraldContainer   = Color(0xFFF1F5F9)   // Light Gray — primary container
val ProEmeraldContainerDark = Color(0xFF1E293B) // Dark Slate — dark container

val ProIndigoSecondary    = Color(0xFF22C55E)   // Emerald Green — active/success accent
val ProIndigoContainer    = Color(0xFFDCFCE7)   // Soft Green container light
val ProIndigoDark         = Color(0xFF4ADE80)   // Bright Green dark-mode
val ProIndigoContainerDark = Color(0xFF14532D)  // Deep Green dark container

val ProAmberTertiary      = Color(0xFF64748B)   // Slate — tertiary/muted
val ProAmberContainer     = Color(0xFFF8FAFC)   // Near-white container
val ProAmberDark          = Color(0xFF94A3B8)   // Light Slate dark-mode
val ProAmberContainerDark = Color(0xFF1E293B)   // Dark container

val ProCoralError         = Color(0xFFEF4444)   // Red error
val ProCoralContainer     = Color(0xFFFFE4E6)
val ProCoralDark          = Color(0xFFFCA5A5)
val ProCoralContainerDark = Color(0xFF4C0519)

// ── Accent chips & status indicators ─────────────────────────────────────────
val M3IndigoAccent   = Color(0xFF111827)   // Onyx (replaces indigo)
val M3IndigoSoft     = Color(0xFFF1F5F9)   // Light Gray
val M3TealAccent     = Color(0xFF22C55E)   // Green
val M3TealSoft       = Color(0xFFDCFCE7)
val M3VioletAccent   = Color(0xFF475569)   // Slate
val M3VioletSoft     = Color(0xFFF1F5F9)
val M3RoseAccent     = Color(0xFFEF4444)   // Red (error only)
val M3RoseSoft       = Color(0xFFFFE4E6)
val M3AmberAccent    = Color(0xFFF59E0B)   // Amber (install/pending)
val M3AmberSoft      = Color(0xFFFEF3C7)
val M3SkyAccent      = Color(0xFF475569)   // Slate (replaces sky blue)
val M3SkySoft        = Color(0xFFF1F5F9)

val ProVioletAccent      = M3VioletAccent
val ProVioletContainer   = M3VioletSoft
val ProCyanAccent        = M3TealAccent

// ── Light surface tokens ───────────────────────────────────────────────────────
val ProBackgroundLight         = Color(0xFFF8FAFC)
val ProSurfaceLight            = Color(0xFFFFFFFF)
val ProSurfaceLowLight         = Color(0xFFF1F5F9)
val ProSurfaceContainerLight   = Color(0xFFE2E8F0)
val ProSurfaceHighLight        = Color(0xFFCBD5E1)
val ProOutlineLight            = Color(0xFFE2E8F0)
val ProOutlineSubtleLight      = Color(0xFFF1F5F9)

// ── Dark surface tokens ───────────────────────────────────────────────────────
val ProBackgroundDark          = Color(0xFF0B0F1A)
val ProSurfaceDark             = Color(0xFF111827)
val ProSurfaceLowDark          = Color(0xFF1E293B)
val ProSurfaceContainerDark    = Color(0xFF253045)
val ProSurfaceHighDark         = Color(0xFF2D3A52)
val ProOutlineDark             = Color(0xFF334155)
val ProOutlineSubtleDark       = Color(0xFF1E293B)

// ── Typography ────────────────────────────────────────────────────────────────
val ProInkPrimary   = Color(0xFF111827)
val ProInkSecondary = Color(0xFF374151)
val ProInkTertiary  = Color(0xFF64748B)

// ── Legacy aliases ────────────────────────────────────────────────────────────
val ForestPrimary     = ProEmeraldPrimary
val ForestDark        = ProEmeraldDark
val SageSoft          = ProEmeraldContainer
val SageTint          = M3IndigoSoft

val ClaySecondary     = M3RoseAccent
val ClaySoft          = ProCoralContainer

val HoneyTertiary     = M3AmberAccent
val HoneySoft         = M3AmberSoft

val CoralError        = ProCoralError
val CoralSoft         = ProCoralContainer

val LavenderAccent    = M3VioletAccent
val LavenderSoft      = ProVioletContainer

val WarmLinen         = ProBackgroundLight
val WarmIvory         = ProSurfaceLight
val WarmSand          = ProSurfaceLowLight
val WarmStone         = ProOutlineLight

val InkPrimary        = ProInkPrimary
val InkSecondary      = ProInkSecondary
val InkTertiary       = ProInkTertiary

val CyberCyan         = M3TealAccent
val CyberCyanDark     = ProEmeraldDark
val CyberEmerald      = M3TealAccent
val CyberEmeraldDark  = ProEmeraldDark
val CyberAmber        = M3AmberAccent
val CyberRed          = ProCoralError
val CyberViolet       = M3VioletAccent

val DeepObsidian      = ProBackgroundLight
val SurfaceObsidian   = ProSurfaceLight
val SurfaceElevated   = ProSurfaceLowLight
val SurfaceCard       = ProSurfaceLight
val SurfaceBorder     = ProOutlineLight

val TextPrimary       = ProInkPrimary
val TextSecondary     = ProInkSecondary
val TextTertiary      = ProInkTertiary

val TerminalBlack     = Color(0xFF111827)
val TerminalGreen     = M3TealAccent
