package com.chimera.red.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimera.red.ChimeraRepository
import com.chimera.red.UsbSerialManager
import com.chimera.red.models.SerialMessage
import com.chimera.red.models.BleDevice
import com.chimera.red.ui.theme.ChimeraColors
import com.chimera.red.ui.theme.Dimens
import com.chimera.red.ui.components.*
import com.google.gson.Gson
import kotlinx.coroutines.delay

@Composable
fun BleScreen(usbManager: UsbSerialManager) {
    val devices = ChimeraRepository.bleDevices
    var isScanning by remember { mutableStateOf(false) }
    var isSpamming by remember { mutableStateOf(false) }
    var activeSpamType by remember { mutableStateOf<String?>(null) }
    var showAttackPanel by remember { mutableStateOf(false) }
    
    val gson = remember { Gson() }

    // Data Processing
    LaunchedEffect(Unit) {
        usbManager.receivedData.collect { msg ->
            try {
                if (msg.startsWith("{")) {
                    val message = gson.fromJson(msg, SerialMessage::class.java)
                    if (message.devices != null) {
                        ChimeraRepository.updateBleDevices(message.devices)
                        isScanning = false
                    }
                    if (message.msg?.contains("Spam burst complete") == true) {
                        isSpamming = false
                        activeSpamType = null
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    // Auto-disable scanning after timeout
    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(10000)
            isScanning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChimeraColors.Background)
            .padding(Dimens.SpacingMd)
    ) {
        // ====================================================================
        // HEADER
        // ====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BLE SCANNER",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ChimeraColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${devices.size} devices discovered",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChimeraColors.TextSecondary
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)) {
                // Attack Toggle
                ChimeraGhostButton(
                    text = if (showAttackPanel) "HIDE" else "ATTACK",
                    onClick = { showAttackPanel = !showAttackPanel },
                    accentColor = ChimeraColors.Secondary,
                    icon = if (showAttackPanel) Icons.Default.KeyboardArrowUp else Icons.Default.Warning
                )
                
                // Scan Button
                ChimeraPrimaryButton(
                    text = if (isScanning) "SCANNING" else "SCAN",
                    onClick = {
                        isScanning = true
                        ChimeraRepository.updateBleDevices(emptyList())
                        usbManager.write("SCAN_BLE")
                    },
                    isLoading = isScanning,
                    icon = Icons.Default.Refresh
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // ====================================================================
        // ATTACK PANEL (Collapsible)
        // ====================================================================
        AnimatedVisibility(
            visible = showAttackPanel,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            AttackPanel(
                isSpamming = isSpamming,
                activeSpamType = activeSpamType,
                onSpamClick = { type ->
                    isSpamming = true
                    activeSpamType = type
                    usbManager.write("BLE_SPAM:$type")
                },
                modifier = Modifier.padding(bottom = Dimens.SpacingMd)
            )
        }

        // ====================================================================
        // DEVICE LIST
        // ====================================================================
        if (devices.isEmpty() && !isScanning) {
            // Empty State
            EmptyState(
                onScanClick = {
                    isScanning = true
                    usbManager.write("SCAN_BLE")
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)
            ) {
                itemsIndexed(
                    items = devices.sortedByDescending { it.rssi ?: -100 },
                    key = { _, device -> device.address ?: device.hashCode() }
                ) { index, device ->
                    BleDeviceCard(
                        device = device,
                        index = index
                    )
                }
            }
        }
    }
}

// ============================================================================
// ATTACK PANEL
// ============================================================================

@Composable
private fun AttackPanel(
    isSpamming: Boolean,
    activeSpamType: String?,
    onSpamClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ChimeraCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = ChimeraColors.Secondary
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMd)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ChimeraColors.SecondaryMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Attack",
                            tint = ChimeraColors.Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "BENDER'S CURSE",
                            style = MaterialTheme.typography.titleSmall,
                            color = ChimeraColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "BLE Advertisement Spam",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChimeraColors.TextSecondary
                        )
                    }
                }
                
                if (isSpamming) {
                    ChimeraBadge(
                        text = "ACTIVE",
                        color = ChimeraColors.Secondary
                    )
                }
            }
            
            // Description
            Text(
                text = "Flood the area with fake BLE advertisements to confuse nearby devices and disrupt pairing.",
                style = MaterialTheme.typography.bodySmall,
                color = ChimeraColors.TextSecondary
            )
            
            ChimeraDivider()
            
            // Attack Buttons Grid
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)
                ) {
                    SpamButton(
                        label = "BENDER",
                        icon = Icons.Default.Android,
                        isActive = activeSpamType == "BENDER",
                        isBusy = isSpamming && activeSpamType != "BENDER",
                        onClick = { onSpamClick("BENDER") },
                        modifier = Modifier.weight(1f)
                    )
                    SpamButton(
                        label = "SAMSUNG",
                        icon = Icons.Default.PhoneAndroid,
                        isActive = activeSpamType == "SAMSUNG",
                        isBusy = isSpamming && activeSpamType != "SAMSUNG",
                        onClick = { onSpamClick("SAMSUNG") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)
                ) {
                    SpamButton(
                        label = "GOOGLE",
                        icon = Icons.Default.Devices,
                        isActive = activeSpamType == "GOOGLE",
                        isBusy = isSpamming && activeSpamType != "GOOGLE",
                        onClick = { onSpamClick("GOOGLE") },
                        modifier = Modifier.weight(1f)
                    )
                    SpamButton(
                        label = "APPLE",
                        icon = Icons.Default.Smartphone,
                        isActive = activeSpamType == "APPLE",
                        isBusy = isSpamming && activeSpamType != "APPLE",
                        onClick = { onSpamClick("APPLE") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpamButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val backgroundColor = when {
        isActive -> ChimeraColors.Secondary.copy(alpha = pulseAlpha)
        isBusy -> ChimeraColors.Surface2
        else -> ChimeraColors.SecondaryMuted
    }
    
    val contentColor = when {
        isActive -> ChimeraColors.Secondary
        isBusy -> ChimeraColors.TextDisabled
        else -> ChimeraColors.Secondary
    }
    
    Button(
        onClick = onClick,
        enabled = !isBusy,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = ChimeraColors.Surface2,
            disabledContentColor = ChimeraColors.TextDisabled
        ),
        border = if (isActive) BorderStroke(1.dp, ChimeraColors.Secondary) else null,
        shape = RoundedCornerShape(Dimens.CornerRadius)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================================
// DEVICE CARD
// ============================================================================

@Composable
private fun BleDeviceCard(
    device: BleDevice,
    index: Int
) {
    val signalColor = ChimeraColors.getSignalColor(device.rssi ?: -100)
    
    // Staggered animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 }
    ) {
        ChimeraCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = signalColor
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Device Icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Dimens.CornerRadiusSm))
                            .background(ChimeraColors.PrimaryMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "BLE Device",
                            tint = ChimeraColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            text = device.name ?: "Unknown Device",
                            style = MaterialTheme.typography.titleSmall,
                            color = ChimeraColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = device.address ?: "00:00:00:00:00:00",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChimeraColors.TextSecondary
                        )
                    }
                }
                
                // Signal Info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignalStrengthIndicator(rssi = device.rssi ?: -100)
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${device.rssi ?: 0}",
                            style = MaterialTheme.typography.titleSmall,
                            color = signalColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChimeraColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// EMPTY STATE
// ============================================================================

@Composable
private fun EmptyState(onScanClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMd)
        ) {
            // Animated icon
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                )
            )
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChimeraColors.PrimaryMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothSearching,
                    contentDescription = "Scan",
                    tint = ChimeraColors.Primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Text(
                text = "No Devices Found",
                style = MaterialTheme.typography.titleLarge,
                color = ChimeraColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Start scanning to discover nearby BLE devices",
                style = MaterialTheme.typography.bodyMedium,
                color = ChimeraColors.TextSecondary
            )
            
            Spacer(Modifier.height(Dimens.SpacingSm))
            
            ChimeraPrimaryButton(
                text = "START SCAN",
                onClick = onScanClick,
                icon = Icons.Default.Refresh
            )
        }
    }
}
