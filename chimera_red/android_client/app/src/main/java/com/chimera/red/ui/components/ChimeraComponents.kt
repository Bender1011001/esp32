package com.chimera.red.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chimera.red.ui.theme.ChimeraColors
import com.chimera.red.ui.theme.Dimens

/**
 * Chimera Design System - Reusable Components
 * 
 * A collection of styled, animated components that form the
 * visual language of Chimera Red.
 */

// ============================================================================
// CARD COMPONENTS
// ============================================================================

/**
 * Standard Chimera Card with glow border on interaction
 */
@Composable
fun ChimeraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accentColor: Color = ChimeraColors.Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) accentColor else ChimeraColors.SurfaceBorder,
        animationSpec = tween(150)
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    val shape = RoundedCornerShape(Dimens.CornerRadius)
    
    Surface(
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(color = accentColor.copy(alpha = 0.3f)),
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        color = ChimeraColors.Surface1,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.SpacingMd),
            content = content
        )
    }
}

/**
 * Feature Card with icon and glow effect
 */
@Composable
fun ChimeraFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = ChimeraColors.Primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    ChimeraCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon with glow background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Dimens.CornerRadiusSm))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = ChimeraColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChimeraColors.TextSecondary
                    )
                }
            }
            
            // Optional badge
            badge?.let {
                ChimeraBadge(
                    text = it,
                    color = accentColor
                )
            }
        }
    }
}

/**
 * Stats Card for dashboard displays
 */
@Composable
fun ChimeraStatsCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color = ChimeraColors.Primary,
    modifier: Modifier = Modifier,
    trend: String? = null,
    trendUp: Boolean = true
) {
    ChimeraCard(
        modifier = modifier,
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = ChimeraColors.TextSecondary
                )
                Spacer(Modifier.height(Dimens.SpacingXs))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ChimeraColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                trend?.let {
                    Spacer(Modifier.height(Dimens.SpacingXs))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (trendUp) ChimeraColors.Success else ChimeraColors.Error
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Dimens.CornerRadiusSm))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================================
// BUTTON COMPONENTS
// ============================================================================

/**
 * Primary action button with glow effect
 */
@Composable
fun ChimeraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .scale(scale),
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = ChimeraColors.Primary,
            contentColor = ChimeraColors.TextInverse,
            disabledContainerColor = ChimeraColors.Primary.copy(alpha = 0.3f),
            disabledContentColor = ChimeraColors.TextDisabled
        ),
        shape = RoundedCornerShape(Dimens.CornerRadius)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ChimeraColors.TextInverse,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(Dimens.SpacingSm))
        } else {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Dimens.SpacingSm))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Danger/Attack button with pulsing glow
 */
@Composable
fun ChimeraDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    icon: ImageVector? = null
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    val backgroundColor = if (isActive) {
        ChimeraColors.Secondary.copy(alpha = glowAlpha)
    } else {
        ChimeraColors.SecondaryMuted
    }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = ChimeraColors.Secondary,
            disabledContainerColor = ChimeraColors.Surface2,
            disabledContentColor = ChimeraColors.TextDisabled
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) ChimeraColors.Secondary else ChimeraColors.Secondary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(Dimens.CornerRadius)
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Dimens.SpacingSm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Ghost button (outline style)
 */
@Composable
fun ChimeraGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = ChimeraColors.Primary,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accentColor,
            disabledContentColor = ChimeraColors.TextDisabled
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) accentColor.copy(alpha = 0.5f) else ChimeraColors.SurfaceBorder
        ),
        shape = RoundedCornerShape(Dimens.CornerRadius)
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Dimens.SpacingSm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================================
// BADGE COMPONENTS
// ============================================================================

/**
 * Small badge/chip component
 */
@Composable
fun ChimeraBadge(
    text: String,
    color: Color = ChimeraColors.Primary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Status indicator badge
 */
@Composable
fun ChimeraStatusBadge(
    status: DeviceStatus,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (status) {
        DeviceStatus.CONNECTED -> ChimeraColors.Success to "CONNECTED"
        DeviceStatus.DISCONNECTED -> ChimeraColors.Error to "OFFLINE"
        DeviceStatus.SCANNING -> ChimeraColors.Primary to "SCANNING"
        DeviceStatus.ATTACKING -> ChimeraColors.Secondary to "ACTIVE"
        DeviceStatus.IDLE -> ChimeraColors.TextSecondary to "IDLE"
    }
    
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    color.copy(
                        alpha = if (status == DeviceStatus.SCANNING || status == DeviceStatus.ATTACKING) alpha else 1f
                    )
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class DeviceStatus {
    CONNECTED, DISCONNECTED, SCANNING, ATTACKING, IDLE
}

// ============================================================================
// SIGNAL STRENGTH INDICATOR
// ============================================================================

/**
 * Signal strength bar visualization
 */
@Composable
fun SignalStrengthIndicator(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val color = ChimeraColors.getSignalColor(rssi)
    val bars = when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        rssi >= -80 -> 1
        else -> 0
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            val height = (8 + (index * 4)).dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (index < bars) color else ChimeraColors.Surface2
                    )
            )
        }
    }
}

// ============================================================================
// SECTION HEADER
// ============================================================================

/**
 * Section header with optional action button
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ChimeraColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChimeraColors.TextSecondary
                )
            }
        }
        action?.invoke()
    }
}

// ============================================================================
// DIVIDERS
// ============================================================================

/**
 * Styled horizontal divider
 */
@Composable
fun ChimeraDivider(
    modifier: Modifier = Modifier,
    color: Color = ChimeraColors.SurfaceBorder
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}
