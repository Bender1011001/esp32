package com.chimera.red.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Chimera Red Design System - Color Palette
 * 
 * A sophisticated cyberpunk-inspired color system with:
 * - Primary accent (Cyan/Teal) for main actions
 * - Secondary accent (Magenta/Pink) for active/danger states  
 * - Tertiary (Amber) for warnings and highlights
 * - Semantic colors for status indicators
 * - Surface hierarchy for depth
 */
object ChimeraColors {
    
    // ========================================================================
    // PRIMARY - Cyan/Teal family (Main UI accent, safe actions)
    // ========================================================================
    val Primary = Color(0xFF00F0FF)        // Electric Cyan - primary actions
    val PrimaryDark = Color(0xFF00A8B5)    // Darker cyan - pressed states
    val PrimaryMuted = Color(0xFF00F0FF).copy(alpha = 0.15f)  // Backgrounds
    val PrimaryGlow = Color(0xFF00F0FF).copy(alpha = 0.4f)    // Glow effects
    
    // ========================================================================
    // SECONDARY - Magenta/Pink family (Active attacks, danger)
    // ========================================================================
    val Secondary = Color(0xFFFF0080)      // Hot Pink - active attacks
    val SecondaryDark = Color(0xFFCC0066)  // Darker pink - pressed
    val SecondaryMuted = Color(0xFFFF0080).copy(alpha = 0.15f)
    val SecondaryGlow = Color(0xFFFF0080).copy(alpha = 0.4f)
    
    // ========================================================================
    // TERTIARY - Amber/Gold family (Warnings, highlights, special)
    // ========================================================================
    val Tertiary = Color(0xFFFFB000)       // Amber - warnings, highlights
    val TertiaryDark = Color(0xFFCC8C00)   // Darker amber
    val TertiaryMuted = Color(0xFFFFB000).copy(alpha = 0.15f)
    
    // ========================================================================
    // SEMANTIC - Status indicators
    // ========================================================================
    val Success = Color(0xFF00FF88)        // Bright green - success
    val SuccessMuted = Color(0xFF00FF88).copy(alpha = 0.15f)
    
    val Warning = Color(0xFFFFB000)        // Amber - warning (same as tertiary)
    val WarningMuted = Color(0xFFFFB000).copy(alpha = 0.15f)
    
    val Error = Color(0xFFFF3366)          // Coral red - errors
    val ErrorMuted = Color(0xFFFF3366).copy(alpha = 0.15f)
    
    val Info = Color(0xFF00F0FF)           // Cyan - info (same as primary)
    
    // ========================================================================
    // SURFACE - Background hierarchy (darkest to lightest)
    // ========================================================================
    val Background = Color(0xFF0A0A0F)     // Near black - main background
    val Surface0 = Color(0xFF12121A)       // Slightly lifted - cards base
    val Surface1 = Color(0xFF1A1A24)       // Elevated - interactive cards
    val Surface2 = Color(0xFF24242E)       // Highest - dialogs, popovers
    val SurfaceBorder = Color(0xFF2A2A38)  // Card borders
    
    // ========================================================================
    // TEXT - Typography colors
    // ========================================================================
    val TextPrimary = Color(0xFFE8E8F0)    // Off-white - primary text
    val TextSecondary = Color(0xFF9090A8)  // Muted - secondary info
    val TextDisabled = Color(0xFF505068)   // Disabled text
    val TextInverse = Color(0xFF0A0A0F)    // Dark text on light backgrounds
    
    // ========================================================================
    // GRADIENTS - For premium feel
    // ========================================================================
    val GradientPrimary = Brush.linearGradient(
        colors = listOf(Primary, Color(0xFF0080FF))
    )
    
    val GradientSecondary = Brush.linearGradient(
        colors = listOf(Secondary, Color(0xFFFF4080))
    )
    
    val GradientDanger = Brush.linearGradient(
        colors = listOf(Color(0xFFFF0040), Color(0xFFFF0080))
    )
    
    val GradientSurface = Brush.verticalGradient(
        colors = listOf(Surface1, Surface0)
    )
    
    // ========================================================================
    // SIGNAL STRENGTH - For RSSI visualization
    // ========================================================================
    val SignalExcellent = Color(0xFF00FF88)  // -30 to -50 dBm
    val SignalGood = Color(0xFF88FF00)       // -50 to -60 dBm
    val SignalFair = Color(0xFFFFB000)       // -60 to -70 dBm
    val SignalWeak = Color(0xFFFF6600)       // -70 to -80 dBm
    val SignalPoor = Color(0xFFFF3366)       // < -80 dBm
    
    /**
     * Get signal color based on RSSI value
     */
    fun getSignalColor(rssi: Int): Color {
        return when {
            rssi >= -50 -> SignalExcellent
            rssi >= -60 -> SignalGood
            rssi >= -70 -> SignalFair
            rssi >= -80 -> SignalWeak
            else -> SignalPoor
        }
    }
}

// Legacy compatibility alias
val RetroGreen = ChimeraColors.Primary
