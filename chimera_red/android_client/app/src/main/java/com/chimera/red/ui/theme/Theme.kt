package com.chimera.red.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Chimera Red Theme
 * 
 * A dark, cyberpunk-inspired theme with proper Material3 integration.
 * Uses the ChimeraColors palette for a cohesive visual language.
 */

private val ChimeraDarkColorScheme = darkColorScheme(
    // Primary colors - Cyan accent
    primary = ChimeraColors.Primary,
    onPrimary = ChimeraColors.TextInverse,
    primaryContainer = ChimeraColors.PrimaryMuted,
    onPrimaryContainer = ChimeraColors.Primary,
    
    // Secondary colors - Magenta accent
    secondary = ChimeraColors.Secondary,
    onSecondary = ChimeraColors.TextInverse,
    secondaryContainer = ChimeraColors.SecondaryMuted,
    onSecondaryContainer = ChimeraColors.Secondary,
    
    // Tertiary colors - Amber accent
    tertiary = ChimeraColors.Tertiary,
    onTertiary = ChimeraColors.TextInverse,
    tertiaryContainer = ChimeraColors.TertiaryMuted,
    onTertiaryContainer = ChimeraColors.Tertiary,
    
    // Error colors
    error = ChimeraColors.Error,
    onError = ChimeraColors.TextInverse,
    errorContainer = ChimeraColors.ErrorMuted,
    onErrorContainer = ChimeraColors.Error,
    
    // Background & Surface
    background = ChimeraColors.Background,
    onBackground = ChimeraColors.TextPrimary,
    surface = ChimeraColors.Surface0,
    onSurface = ChimeraColors.TextPrimary,
    surfaceVariant = ChimeraColors.Surface1,
    onSurfaceVariant = ChimeraColors.TextSecondary,
    
    // Outlines
    outline = ChimeraColors.SurfaceBorder,
    outlineVariant = ChimeraColors.Surface2,
    
    // Inverse (for snackbars, etc.)
    inverseSurface = ChimeraColors.TextPrimary,
    inverseOnSurface = ChimeraColors.Background,
    inversePrimary = ChimeraColors.PrimaryDark,
    
    // Scrim for modals
    scrim = ChimeraColors.Background.copy(alpha = 0.8f)
)

@Composable
fun ChimeraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = ChimeraDarkColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
