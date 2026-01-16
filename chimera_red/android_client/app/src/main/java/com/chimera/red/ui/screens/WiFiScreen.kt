package com.chimera.red.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chimera.red.ChimeraRepository
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.models.SerialMessage
import com.chimera.red.models.WifiNetwork
import com.chimera.red.ui.theme.Dimens
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

@Composable
fun WiFiScreen(usbManager: UsbSerialManager) {
    // Hoisted state from Singleton Repo
    val networks = ChimeraRepository.wifiNetworks
    var isScanning by remember { mutableStateOf(false) }
    // Watch for updates from central handler
    val lastUpdate = ChimeraRepository.lastWifiUpdate
    
    // Selection State for Details Dialog
    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }

    LaunchedEffect(lastUpdate) {
        if (isScanning && lastUpdate > 0) {
            isScanning = false
        }
    }

    // Detail Dialog
    if (selectedNetwork != null) {
        val net = selectedNetwork!!
        AlertDialog(
            onDismissRequest = { selectedNetwork = null },
            title = {
                Column {
                    Text(text = net.ssid.ifEmpty { "<HIDDEN>" }, style = MaterialTheme.typography.titleLarge, color = RetroGreen)
                    Text(text = net.bssid ?: "Unknown MAC", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Divider(color = RetroGreen.copy(alpha = 0.3f))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Signal Strength:", style = MaterialTheme.typography.bodyMedium)
                        Text("${net.rssi} dBm", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Channel:", style = MaterialTheme.typography.bodyMedium)
                        Text("${net.channel}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Encryption:", style = MaterialTheme.typography.bodyMedium)
                        Text("${net.encryption}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Divider(color = RetroGreen.copy(alpha = 0.3f))
                    
                    Text("Actions", style = MaterialTheme.typography.titleSmall, color = RetroGreen)
                    
                    // Action Grid
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { 
                                val target = net.bssid ?: net.ssid
                                usbManager.write("DEAUTH:$target") 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DEAUTH", color = Color.Red, fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = { 
                                usbManager.write("SNIFF_START:${net.channel}")
                                selectedNetwork = null // Close to show logs?
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RetroGreen.copy(alpha = 0.2f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SNIFF CH", color = RetroGreen, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNetwork = null }) {
                    Text("CLOSE", color = RetroGreen)
                }
            },
            containerColor = Color.Black,
            textContentColor = RetroGreen
        )
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
                text = "TARGET LIST (${networks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = RetroGreen
            )
            
            Button(
                onClick = { 
                    isScanning = true
                    ChimeraRepository.updateNetworks(emptyList()) 
                    usbManager.write("SCAN_WIFI") 
                },
                enabled = !isScanning,
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

        // Network List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)
        ) {
            items(networks) { network ->
                NetworkCard(network, onClick = { selectedNetwork = network })
            }
        }
    }
}

@Composable
fun NetworkCard(network: WifiNetwork, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingMd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = network.ssid.ifEmpty { "<HIDDEN>" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RetroGreen
                )
                Text(
                    text = "${network.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RetroGreen
                )
            }
            
            Spacer(modifier = Modifier.height(Dimens.SpacingXs))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CH: ${network.channel} | ${network.bssid ?: "MAC?"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RetroGreen.copy(alpha = 0.7f)
                )
                
                Icon(
                    imageVector = Icons.Default.Warning, 
                    contentDescription = "Details",
                    tint = RetroGreen.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
