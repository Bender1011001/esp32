package com.chimera.red.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chimera.red.ChimeraRepository
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.models.SerialMessage
import com.chimera.red.models.BleDevice
import com.chimera.red.ui.theme.Dimens
import com.google.gson.Gson

@Composable
fun BleScreen(usbManager: UsbSerialManager) {
    var devices = ChimeraRepository.bleDevices
    var isScanning by remember { mutableStateOf(false) }
    var isSpamming by remember { mutableStateOf(false) }
    
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
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpacingMd)
    ) {
        // Header / Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BLE DEVICES (${devices.size})",
                style = MaterialTheme.typography.titleMedium,
                color = RetroGreen
            )
            
            Button(
                onClick = { 
                    isScanning = true
                    ChimeraRepository.updateBleDevices(emptyList()) 
                    usbManager.write("SCAN_BLE") 
                },
                enabled = !isScanning && !isSpamming,
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("SCANNING", color = Color.Black)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("SCAN", color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))
        
        // Active Attack Console ("Bender's Curse")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Dimens.SpacingMd)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Alert", tint = Color.Yellow)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "BENDER'S CURSE (Active)",
                        fontWeight = FontWeight.Bold,
                        color = RetroGreen
                    )
                }
                Spacer(Modifier.height(Dimens.SpacingSm))
                Text(
                    "Broadcast spam advertisements to disrupt or annoy nearby devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RetroGreen.copy(alpha=0.7f)
                )
                
                Spacer(Modifier.height(Dimens.SpacingMd))
                
                // Attack Buttons Grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                   SpamButton("BENDER", isSpamming) { 
                       isSpamming = true
                       usbManager.write("BLE_SPAM:BENDER")
                   }
                   SpamButton("SAMSUNG", isSpamming) { 
                       isSpamming = true
                       usbManager.write("BLE_SPAM:SAMSUNG")
                   }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                   SpamButton("GOOGLE", isSpamming) { 
                       isSpamming = true
                       usbManager.write("BLE_SPAM:GOOGLE")
                   }
                   SpamButton("APPLE", isSpamming) { 
                       isSpamming = true
                       usbManager.write("BLE_SPAM:APPLE")
                   }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // Device List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)
        ) {
            items(devices) { device ->
                BleDeviceCard(device)
            }
        }
    }
}

@Composable
fun BleDeviceCard(device: BleDevice) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RetroGreen
                )
                Text(
                    text = device.address ?: "00:00:00:00:00:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = RetroGreen.copy(alpha = 0.7f)
                )
            }
            
            Text(
                text = "${device.rssi ?: 0} dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = RetroGreen
            )
        }
    }
}

@Composable
fun SpamButton(label: String, isBusy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isBusy,
        modifier = Modifier.width(140.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RetroGreen.copy(alpha = 0.2f),
            contentColor = RetroGreen
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
