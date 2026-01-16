package com.chimera.red.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.models.SerialMessage
import com.chimera.red.ui.theme.Dimens
import com.google.gson.Gson
import kotlinx.coroutines.flow.collect

import androidx.compose.ui.unit.sp // Added import

@Composable
fun CSIScreen(usbManager: UsbSerialManager) {
    var isRunning by remember { mutableStateOf(false) }
    var csiHistory by remember { mutableStateOf(listOf<List<Int>>()) } // List of CSI packets (each 16-64 ints)
    val maxHistory = 50
    
    val gson = remember { Gson() } // Re-added gson
    
    var useFilter by remember { mutableStateOf(true) }
    var lastPacket: List<Int>? = null // Logic to store previous packet for EMA

    // Motion metric
    var motionMetric by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        usbManager.receivedData.collect { msg ->
            try {
                if (msg.startsWith("{")) {
                    val message = gson.fromJson(msg, SerialMessage::class.java)
                    if (message.type == "csi" && message.csiData != null) {
                        var newData: List<Int> = message.csiData
                        
                        // Apply Filter (EMA)
                        if (useFilter) {
                            val alpha = 0.3f
                            val prev = lastPacket ?: newData
                            newData = newData.mapIndexed { index, value -> 
                                val oldVal = if (index < prev.size) prev[index] else value
                                (value * alpha + oldVal * (1 - alpha)).toInt()
                            }
                        }
                        
                        lastPacket = newData
                        csiHistory = (csiHistory + listOf(newData)).takeLast(maxHistory)
                        
                        // Calculate Motion Metric (Variance from previous)
                        if (csiHistory.size > 2) {
                             val lastSum = newData.sum()
                             val prevSum = csiHistory[csiHistory.size - 2].sum()
                             motionMetric = kotlin.math.abs(lastSum - prevSum).toFloat()
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize().padding(Dimens.SpacingMd)) {
        Row(
             modifier = Modifier.fillMaxWidth(),
             horizontalArrangement = Arrangement.SpaceBetween,
             verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("CSI RADAR (PRO)", style = MaterialTheme.typography.titleLarge, color = RetroGreen)
                Text("WiFi Channel State Information - Motion Detection", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
            
            // Filter Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FILTER", color = RetroGreen, fontSize = 12.sp)
                Switch(
                    checked = useFilter,
                    onCheckedChange = { useFilter = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RetroGreen,
                        checkedTrackColor = RetroGreen.copy(alpha=0.5f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        // Waterfall / Heatmap Visualization
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(Dimens.BorderThin, RetroGreen)
                .background(Color.Black)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                if (csiHistory.isNotEmpty()) {
                    val rowHeight = canvasHeight / maxHistory
                    
                    csiHistory.forEachIndexed { i, packet ->
                        // Draw latest at bottom
                        val y = canvasHeight - ((csiHistory.size - i) * rowHeight)
                        val cellWidth = canvasWidth / packet.size
                        
                        packet.forEachIndexed { j, amplitude ->
                            // Color mapping: low amp = black/blue, high = green/red
                            val normalized = (amplitude / 100f).coerceIn(0f, 1f)
                            val alpha = normalized
                            
                            drawRect(
                                color = RetroGreen.copy(alpha = alpha),
                                topLeft = Offset(j * cellWidth, y),
                                size = Size(cellWidth, rowHeight)
                            )
                        }
                    }
                }
            }
            
            // Motion Indicator (Simple variance check)
            if (isRunning && motionMetric > 50) { // arbitrary threshold
                 Text("MOVEMENT DETECTED", color = Color.Red, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        Button(
            onClick = {
                isRunning = !isRunning
                if (isRunning) {
                    usbManager.write("CSI_START")
                } else {
                    usbManager.write("CSI_STOP")
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else RetroGreen)
        ) {
            Text(if (isRunning) "STOP RADAR" else "START RADAR", color = Color.Black)
        }
    }
}
