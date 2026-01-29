package com.podcapture.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class ThemeColors(
    val backgroundColor: Color,
    val accent1Color: Color,
    val accent2Color: Color
) {
    companion object {
        val Default = ThemeColors(
            backgroundColor = Color(0xFF13293D),
            accent1Color = Color(0xFF2A628F),
            accent2Color = Color(0xFF3E92CC)
        )
    }
}

private fun generateDarkColorScheme(colors: ThemeColors): ColorScheme {
    val background = colors.backgroundColor
    val accent1 = colors.accent1Color
    val accent2 = colors.accent2Color
    val onSurface = background.contrastingOnColor()
    val onSurfaceVariant = onSurface.copy(alpha = 0.7f)

    return darkColorScheme(
        // Primary - accent2 (brighter, for buttons/highlights)
        primary = accent2,
        onPrimary = background.darken(0.3f),
        primaryContainer = accent2.darken(0.4f),
        onPrimaryContainer = accent2,

        // Secondary - accent1 (muted)
        secondary = accent1,
        onSecondary = background.darken(0.3f),
        secondaryContainer = accent1.darken(0.4f),
        onSecondaryContainer = accent2,

        // Tertiary - Use primary accent (accent2) for capture actions and playhead
        tertiary = accent2,
        onTertiary = background.darken(0.3f),
        tertiaryContainer = accent2.darken(0.2f),
        onTertiaryContainer = background.darken(0.3f),

        // Background & Surface
        background = background,
        onBackground = onSurface,
        surface = background,
        onSurface = onSurface,
        surfaceVariant = background.lighten(0.1f),
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = background.darken(0.3f),
        surfaceContainerLow = background.darken(0.3f),
        surfaceContainer = background,
        surfaceContainerHigh = background.lighten(0.1f),
        surfaceContainerHighest = background.lighten(0.15f),

        // Outline
        outline = accent1.lighten(0.2f),
        outlineVariant = accent1.darken(0.2f),

        // Inverse (for snackbars, tooltips)
        inverseSurface = SurfaceLight,
        inverseOnSurface = OnLightSurface,
        inversePrimary = accent1,

        // Error
        error = Rose,
        onError = background.darken(0.3f),
        errorContainer = Color(0xFF5C2030),
        onErrorContainer = Rose,

        // Scrim
        scrim = Color(0xFF000000)
    )
}

private fun generateLightColorScheme(colors: ThemeColors): ColorScheme {
    val background = colors.backgroundColor
    val accent1 = colors.accent1Color
    val accent2 = colors.accent2Color

    return lightColorScheme(
        // Primary - accent1 (darker for light theme)
        primary = accent1,
        onPrimary = Color.White,
        primaryContainer = accent2,
        onPrimaryContainer = background.darken(0.3f),

        // Secondary - accent2
        secondary = accent2.darken(0.2f),
        onSecondary = Color.White,
        secondaryContainer = accent2,
        onSecondaryContainer = background.darken(0.3f),

        // Tertiary - Use primary accent (accent2) for capture actions and playhead
        tertiary = accent2.darken(0.2f),
        onTertiary = Color.White,
        tertiaryContainer = accent2,
        onTertiaryContainer = Color.White,

        // Background & Surface
        background = SurfaceLight,
        onBackground = OnLightSurface,
        surface = SurfaceLight,
        onSurface = OnLightSurface,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnLightSurfaceVariant,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceVariantLight,

        // Outline
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,

        // Inverse (for snackbars, tooltips)
        inverseSurface = background,
        inverseOnSurface = OnDarkSurface,
        inversePrimary = accent2,

        // Error
        error = Rose,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF5C1A1A),

        // Scrim
        scrim = Color(0xFF000000)
    )
}

private val DarkColorScheme = darkColorScheme(
    // Primary - Ocean Blue accent
    primary = OceanBlue,
    onPrimary = NavyDeep,
    primaryContainer = OceanBlueDark,
    onPrimaryContainer = OceanBlue,

    // Secondary - Steel Blue accent
    secondary = SteelBlue,
    onSecondary = NavyDeep,
    secondaryContainer = SteelBlueDark,
    onSecondaryContainer = OceanBlue,

    // Tertiary - Use Ocean Blue (primary accent) for capture actions and playhead
    tertiary = OceanBlue,
    onTertiary = NavyDeep,
    tertiaryContainer = OceanBlueMuted,
    onTertiaryContainer = NavyDeep,

    // Background & Surface
    background = NavyDark,
    onBackground = OnDarkSurface,
    surface = NavyDark,
    onSurface = OnDarkSurface,
    surfaceVariant = NavyMedium,
    onSurfaceVariant = OnDarkSurfaceVariant,
    surfaceContainerLowest = NavyDeep,
    surfaceContainerLow = NavyDeep,
    surfaceContainer = NavyDark,
    surfaceContainerHigh = NavyMedium,
    surfaceContainerHighest = NavyLight,

    // Outline
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,

    // Inverse (for snackbars, tooltips)
    inverseSurface = SurfaceLight,
    inverseOnSurface = OnLightSurface,
    inversePrimary = SteelBlue,

    // Error
    error = Rose,
    onError = NavyDeep,
    errorContainer = Color(0xFF5C2030),
    onErrorContainer = Rose,

    // Scrim
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    // Primary - Steel Blue (darker for light theme)
    primary = SteelBlue,
    onPrimary = Color.White,
    primaryContainer = OceanBlue,
    onPrimaryContainer = NavyDeep,

    // Secondary - Ocean Blue
    secondary = OceanBlueMuted,
    onSecondary = Color.White,
    secondaryContainer = OceanBlue,
    onSecondaryContainer = NavyDeep,

    // Tertiary - Use Ocean Blue (primary accent) for capture actions and playhead
    tertiary = OceanBlueMuted,
    onTertiary = Color.White,
    tertiaryContainer = OceanBlue,
    onTertiaryContainer = Color.White,

    // Background & Surface
    background = SurfaceLight,
    onBackground = OnLightSurface,
    surface = SurfaceLight,
    onSurface = OnLightSurface,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnLightSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceVariantLight,

    // Outline
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,

    // Inverse (for snackbars, tooltips)
    inverseSurface = NavyDark,
    inverseOnSurface = OnDarkSurface,
    inversePrimary = OceanBlue,

    // Error
    error = Rose,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5C1A1A),

    // Scrim
    scrim = Color(0xFF000000)
)

@Composable
fun PodCaptureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to use our custom colors
    themeColors: ThemeColors = ThemeColors.Default,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        generateDarkColorScheme(themeColors)
    } else {
        generateLightColorScheme(themeColors)
    }

    // Set status bar and navigation bar colors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
