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

    LaunchedEffect(lastUpdate) {
        if (isScanning && lastUpdate > 0) {
            isScanning = false
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
                text = "TARGET LIST (${networks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = RetroGreen
            )
            
            Button(
                onClick = { 
                    isScanning = true
                    // Keep old results or clear? Clearing is safer for "new scan"
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
                NetworkCard(network, onDeauth = {
                     // Prefer BSSID if available for precision
                     val target = network.bssid ?: network.ssid
                     usbManager.write("DEAUTH:$target")
                })
            }
        }
    }
}

@Composable
fun NetworkCard(network: WifiNetwork, onDeauth: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
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
                    text = "CH: ${network.channel} | ENC: ${network.encryption}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RetroGreen.copy(alpha = 0.7f)
                )
                
                Button(
                    onClick = onDeauth,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("DEAUTH", fontSize = 10.sp, color = Color.Red)
                }
            }
        }
    }
}
