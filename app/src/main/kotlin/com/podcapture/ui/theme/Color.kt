package com.podcapture.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ============ Color Utility Functions ============

fun parseHexColor(hex: String): Color? {
    val cleanHex = hex.removePrefix("#").uppercase()
    if (cleanHex.length != 6) return null
    return try {
        val colorInt = cleanHex.toLong(16)
        Color(
            red = ((colorInt shr 16) and 0xFF) / 255f,
            green = ((colorInt shr 8) and 0xFF) / 255f,
            blue = (colorInt and 0xFF) / 255f
        )
    } catch (e: NumberFormatException) {
        null
    }
}

fun isValidHexColor(hex: String): Boolean {
    val cleanHex = hex.removePrefix("#").uppercase()
    if (cleanHex.length != 6) return false
    return cleanHex.all { it in '0'..'9' || it in 'A'..'F' }
}

fun Color.lighten(factor: Float): Color = Color(
    red = (red + (1f - red) * factor).coerceIn(0f, 1f),
    green = (green + (1f - green) * factor).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * factor).coerceIn(0f, 1f),
    alpha = alpha
)

fun Color.darken(factor: Float): Color = Color(
    red = (red * (1f - factor)).coerceIn(0f, 1f),
    green = (green * (1f - factor)).coerceIn(0f, 1f),
    blue = (blue * (1f - factor)).coerceIn(0f, 1f),
    alpha = alpha
)

fun Color.contrastingOnColor(): Color {
    return if (luminance() > 0.5f) {
        Color(0xFF13293D) // Dark text for light backgrounds
    } else {
        Color(0xFFE8F1F8) // Light text for dark backgrounds
    }
}

// ============ Core Brand Colors ============
// Deep navy background with ocean blue accents

val NavyDark = Color(0xFF13293D)           // Primary background
val NavyDeep = Color(0xFF0D1B2A)           // Deeper navy for contrast
val NavyMedium = Color(0xFF1B3A52)         // Elevated surfaces
val NavyLight = Color(0xFF234B67)          // Lighter navy accent

val OceanBlue = Color(0xFF3E92CC)          // Primary accent - bright blue
val OceanBlueMuted = Color(0xFF2A628F)     // Muted ocean blue
val OceanBlueDark = Color(0xFF1E4A6B)      // Dark ocean blue for containers

val SteelBlue = Color(0xFF2A628F)          // Secondary accent - medium blue
val SteelBlueMuted = Color(0xFF1F4A6D)     // Muted steel blue
val SteelBlueDark = Color(0xFF16354D)      // Dark steel blue

// ============ Functional Colors ============
val Coral = Color(0xFFFF8A7A)              // Tertiary - capture button, fun accent
val CoralMuted = Color(0xFFE07868)         // Muted coral
val CoralDark = Color(0xFFB85A4A)          // Dark coral

val Mint = Color(0xFF7EDEB8)               // Success states
val Amber = Color(0xFFFFB74D)              // Warnings, markers
val Rose = Color(0xFFFF6B8A)               // Error states

// ============ Surface Colors - Dark Theme ============
val SurfaceDark = Color(0xFF13293D)        // Main background
val SurfaceContainerDark = Color(0xFF0D1B2A)  // Lower surfaces
val SurfaceContainerHighDark = Color(0xFF1B3A52) // Cards, elevated
val SurfaceVariantDark = Color(0xFF234B67) // Variant surfaces

// ============ Surface Colors - Light Theme ============
val SurfaceLight = Color(0xFFF5F8FA)       // Main background
val SurfaceContainerLight = Color(0xFFFFFFFF)  // Cards
val SurfaceContainerHighLight = Color(0xFFE8F0F5) // Elevated
val SurfaceVariantLight = Color(0xFFDCE8F0) // Variant surfaces

// ============ Text Colors ============
val OnDarkSurface = Color(0xFFE8F1F8)      // Primary text on dark
val OnDarkSurfaceVariant = Color(0xFFA8C4D8) // Secondary text on dark
val OnLightSurface = Color(0xFF13293D)     // Primary text on light
val OnLightSurfaceVariant = Color(0xFF4A6B82) // Secondary text on light

// ============ Outline Colors ============
val OutlineDark = Color(0xFF4A6B82)        // Borders on dark
val OutlineVariantDark = Color(0xFF2A4A60) // Subtle borders on dark
val OutlineLight = Color(0xFFA8C4D8)       // Borders on light
val OutlineVariantLight = Color(0xFFC8DCE8) // Subtle borders on light

// ============ Waveform & Player Colors ============
val WaveformUnplayed = Color(0xFF4A6B82)   // Unplayed waveform bars
val WaveformPlayed = Color(0xFF3E92CC)     // Played waveform bars (ocean blue)
val PlayheadColor = Color(0xFFFF8A7A)      // Playhead indicator (coral)
val CaptureMarkerColor = Color(0xFFFFB74D) // Capture point markers (amber)
