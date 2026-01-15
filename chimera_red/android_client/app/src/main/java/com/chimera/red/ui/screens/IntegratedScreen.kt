package com.chimera.red.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimera.red.UsbSerialManager
import com.chimera.red.ChimeraRepository
import com.chimera.red.RetroGreen
import com.chimera.red.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun IntegratedScreen(usbManager: UsbSerialManager) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // Scenario States
    var isRunningRecon by remember { mutableStateOf(false) }
    var reconProgress by remember { mutableStateOf(0f) }
    var reconStatus by remember { mutableStateOf("IDLE") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpacingMd)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "INTEGRATED SCENARIOS",
            style = MaterialTheme.typography.titleMedium,
            color = RetroGreen,
            modifier = Modifier.padding(bottom = Dimens.SpacingLg)
        )

        // Scenario 1: Full Recon
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Dimens.SpacingMd)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Full Reconnaissance", fontWeight = FontWeight.Bold, color = RetroGreen)
                        Text(
                            "Sequential Scan: WiFi -> BLE -> NFC", 
                            style = MaterialTheme.typography.bodySmall,
                            color = RetroGreen.copy(alpha = 0.7f)
                        )
                    }
                    Icon(Icons.Default.Security, contentDescription = null, tint = RetroGreen)
                }

                Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                
                if (isRunningRecon) {
                    LinearProgressIndicator(
                        progress = reconProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = RetroGreen,
                        trackColor = Color.Black
                    )
                    Text(
                        text = reconStatus, 
                        style = MaterialTheme.typography.labelSmall,
                        color = RetroGreen,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Button(
                        onClick = {
                            if (!isRunningRecon) {
                                isRunningRecon = true
                                scope.launch {
                                    // Step 1: WiFi
                                    reconStatus = "Step 1/3: WiFi Scan..."
                                    reconProgress = 0.1f
                                    val startWifi = System.currentTimeMillis()
                                    usbManager.write("SCAN_WIFI")
                                    
                                    try {
                                        withTimeout(15000) {
                                            while (ChimeraRepository.lastWifiUpdate < startWifi) {
                                                delay(250)
                                            }
                                        }
                                        reconStatus = "WiFi Complete. Starting BLE..."
                                    } catch(e: Exception) {
                                        reconStatus = "WiFi Timeout (Continuing)..."
                                    }
                                    
                                    // Step 2: BLE
                                    reconStatus = "Step 2/3: BLE Scan..."
                                    reconProgress = 0.4f
                                    val startBle = System.currentTimeMillis()
                                    usbManager.write("SCAN_BLE")
                                    
                                    try {
                                        withTimeout(15000) {
                                            while (ChimeraRepository.lastBleUpdate < startBle) {
                                                delay(250)
                                            }
                                        }
                                        reconStatus = "BLE Complete. Checks..."
                                    } catch(e: Exception) {
                                        reconStatus = "BLE Timeout (Continuing)..."
                                    }
                                    
                                    // Step 3: NFC (Instant check usually, or wait for tag? Assuming instant poll)
                                    // NFC doesn't have a "Scan complete" usually unless we poll.
                                    // We'll keep fixed delay for NFC as it might not return data if empty.
                                    reconStatus = "Step 3/3: NFC Check..."
                                    reconProgress = 0.7f
                                    usbManager.write("NFC_SCAN")
                                    delay(2000) 
                                    
                                    // Step 4: System Info
                                    reconStatus = "Step 4/4: System Health..."
                                    reconProgress = 0.9f
                                    usbManager.write("GET_INFO")
                                    
                                    delay(1000)
                                    reconStatus = "COMPLETE - Check Tabs"
                                    reconProgress = 1.0f
                                    delay(2000)
                                    isRunningRecon = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("RUN SCENARIO", color = Color.Black)
                    }
                }
            }
        }
    }
}
