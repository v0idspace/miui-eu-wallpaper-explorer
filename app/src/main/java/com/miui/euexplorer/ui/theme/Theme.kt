package com.miui.euexplorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val fullscreenCanvas: Color,
    val fullscreenOverlayScrim: Color,
    val fullscreenOverlayInk: Color,
    val fullscreenOverlayBorder: Color,
    val savedBadge: Color,
    val surfaceGradientEnd: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        fullscreenCanvas = FullscreenCanvasDark,
        fullscreenOverlayScrim = FullscreenScrimDark,
        fullscreenOverlayInk = FullscreenInkLight,
        fullscreenOverlayBorder = FullscreenHairline,
        savedBadge = SavedSuccessDark,
        surfaceGradientEnd = ObsidianGradientEnd
    )
}

private val DarkColors = darkColorScheme(
    primary = ObsidianAccent,
    onPrimary = ObsidianOnAccent,
    primaryContainer = ObsidianAccentContainer,
    onPrimaryContainer = ObsidianOnAccentContainer,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceContainerLowest = ObsidianSurfaceLowest,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    background = ObsidianBackground,
    onBackground = ObsidianOnSurface
)

private val LightColors = lightColorScheme(
    primary = PaperAccent,
    onPrimary = PaperOnAccent,
    primaryContainer = PaperAccentContainer,
    onPrimaryContainer = PaperOnAccentContainer,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    surfaceContainerLowest = PaperSurfaceLowest,
    surfaceContainerLow = PaperSurfaceLow,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerHigh = PaperSurfaceHigh,
    surfaceContainerHighest = PaperSurfaceHighest,
    onSurfaceVariant = PaperOnSurfaceVariant,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    background = PaperBackground,
    onBackground = PaperOnSurface
)

@Composable
fun EuExplorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = ExtendedColors(
        fullscreenCanvas = FullscreenCanvasDark,
        fullscreenOverlayScrim = FullscreenScrimDark,
        fullscreenOverlayInk = FullscreenInkLight,
        fullscreenOverlayBorder = FullscreenHairline,
        savedBadge = if (darkTheme) SavedSuccessDark else SavedSuccessLight,
        surfaceGradientEnd = if (darkTheme) ObsidianGradientEnd else PaperGradientEnd
    )

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ExplorerTypography,
            shapes = ExplorerShapes,
            content = content
        )
    }
}
