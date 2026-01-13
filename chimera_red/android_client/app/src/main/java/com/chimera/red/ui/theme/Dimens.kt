package com.chimera.red.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Centralized dimension constants for Chimera Red UI.
 * Ensures consistent spacing and sizing across different screen sizes.
 * 
 * This object follows Material Design spacing conventions while
 * providing semantic names for UI-specific use cases.
 */
object Dimens {
    // ========================================================================
    // SPACING - General purpose padding and margins
    // ========================================================================
    
    /** Extra small spacing (4dp) - tight internal padding */
    val SpacingXs: Dp = 4.dp
    
    /** Small spacing (8dp) - compact element separation */
    val SpacingSm: Dp = 8.dp
    
    /** Medium spacing (16dp) - standard screen padding */
    val SpacingMd: Dp = 16.dp
    
    /** Large spacing (24dp) - section separation */
    val SpacingLg: Dp = 24.dp
    
    /** Extra large spacing (32dp) - major section breaks */
    val SpacingXl: Dp = 32.dp
    
    // ========================================================================
    // COMPONENT SIZES - Specific UI element dimensions
    // ========================================================================
    
    /** Icon size - small (24dp) */
    val IconSizeSm: Dp = 24.dp
    
    /** Icon size - medium (40dp) - battery icon, status icons */
    val IconSizeMd: Dp = 40.dp
    
    /** Icon size - large (56dp) - feature icons */
    val IconSizeLg: Dp = 56.dp
    
    /** Button size - large square buttons (100dp) - replay controls */
    val ButtonSizeLg: Dp = 100.dp
    
    /** Card height - standard content card (200dp) */
    val CardHeightMd: Dp = 200.dp
    
    /** Terminal minimum height (300dp) */
    val TerminalMinHeight: Dp = 300.dp
    
    // ========================================================================
    // BORDERS & DIVIDERS
    // ========================================================================
    
    /** Hairline border (0.5dp) */
    val BorderHairline: Dp = 0.5.dp
    
    /** Thin border (1dp) */
    val BorderThin: Dp = 1.dp
    
    /** Standard border (2dp) - focus indicators, cards */
    val BorderStandard: Dp = 2.dp
    
    // ========================================================================
    // CORNER RADIUS
    // ========================================================================
    
    /** Small corner radius (8dp) */
    val CornerSm: Dp = 8.dp
    
    /** Medium corner radius (16dp) - cards */
    val CornerMd: Dp = 16.dp
    
    /** Large corner radius (24dp) */
    val CornerLg: Dp = 24.dp
    
    // ========================================================================
    // TYPOGRAPHY - Font sizes (consider moving to Typography.kt if complex)
    // ========================================================================
    
    /** Caption text size (10sp) */
    val TextCaption: TextUnit = 10.sp
    
    /** Body text size (12sp) - terminal output */
    val TextBody: TextUnit = 12.sp
    
    /** Subtitle text size (16sp) */
    val TextSubtitle: TextUnit = 16.sp
    
    /** Title text size (20sp) */
    val TextTitle: TextUnit = 20.sp
    
    /** Display text size (24sp) - screen headers */
    val TextDisplay: TextUnit = 24.sp
}
