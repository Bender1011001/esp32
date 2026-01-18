package com.chimera.red.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chimera.red.ChimeraRepository
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.models.SerialMessage
import com.chimera.red.ui.theme.Dimens
import com.chimera.red.ui.components.SpectrumCanvas
import com.google.gson.Gson
import kotlinx.coroutines.launch

@Composable
fun SubGhzScreen(usbManager: UsbSerialManager) {
    var selectedTab by remember { mutableStateOf(0) }
    var currentFreq by remember { mutableStateOf("433.92") }
    
    // Analyzer State
    var isAnalyzerRunning by remember { mutableStateOf(false) }
    val analyzerData = ChimeraRepository.analyzerData
    
    // Recorder State
    var isRecording by remember { mutableStateOf(false) }
    var hasRecording by remember { mutableStateOf(false) }
    
    val gson = remember { Gson() }

    LaunchedEffect(Unit) {
        usbManager.receivedData.collect { msg ->
            try {
                if (msg.startsWith("{")) {
                    val message = gson.fromJson(msg, SerialMessage::class.java)
                    if (message.type == "analyzer_data" && message.pulses != null) {
                        ChimeraRepository.appendAnalyzerData(message.pulses)
                    }
                    if (message.msg?.contains("Replay Complete") == true) {
                       // Done replaying logic if needed
                    }
                    if (message.msg?.contains("Recording...") == true) {
                        isRecording = true
                    }
                    if (message.msg?.contains("Buffer Empty") == true) {
                        hasRecording = false
                    }
                }
            } catch (e: Exception) { }
        }
    }
    
    // Listen for High-Speed COBS Spectrum Data
    LaunchedEffect(Unit) {
        usbManager.spectrumData.collect { bytes ->
            // Convert signed bytes to unsigned ints (0-255)
            val ints = bytes.map { it.toInt() and 0xFF }
            ChimeraRepository.appendAnalyzerData(ints)
        }
    }

    Column(Modifier.fillMaxSize().padding(Dimens.SpacingMd)) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = RetroGreen,
            divider = { Divider(color = RetroGreen) }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("VISUALIZER", modifier = Modifier.padding(16.dp), fontFamily = FontFamily.Monospace)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("RECORDER", modifier = Modifier.padding(16.dp), fontFamily = FontFamily.Monospace)
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        // Frequency Tuner (Global)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("FREQ:", color = RetroGreen, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            listOf("315", "433.92", "868").forEach { freq ->
                val isSelected = currentFreq == freq
                Button(
                    onClick = { 
                        currentFreq = freq 
                        usbManager.write("SET_FREQ:$freq")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) RetroGreen else Color.Transparent
                    ),
                    modifier = Modifier.padding(end = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(freq, color = if (isSelected) Color.Black else RetroGreen, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(Dimens.SpacingMd))

        if (selectedTab == 0) {
            // == VISUALIZER TAB ==
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(Dimens.BorderThin, RetroGreen, RectangleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
               // Render using High-Performance SurfaceView
               SpectrumCanvas(
                   spectrumData = analyzerData,
                   modifier = Modifier.fillMaxSize()
               )
               
               if (isAnalyzerRunning) {
                   Text("CAPTURING...", color = RetroGreen, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
               }
            }
            
            Spacer(Modifier.height(Dimens.SpacingMd))
            
            Button(
                onClick = {
                    if (isAnalyzerRunning) {
                        usbManager.write("ANALYZER_STOP")
                        isAnalyzerRunning = false
                    } else {
                        usbManager.write("ANALYZER_START")
                        isAnalyzerRunning = true
                        ChimeraRepository.clearAnalyzer()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isAnalyzerRunning) Color.Red else RetroGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAnalyzerRunning) "STOP VISUALIZER" else "START VISUALIZER", color = Color.Black)
            }
            
        } else if (selectedTab == 1) {
            // == RECORDER TAB ==
            Column(Modifier.weight(1f).padding(Dimens.SpacingMd)) {
                 Text("SIGNAL RECORDER", fontWeight = FontWeight.Bold, color = RetroGreen)
                 Spacer(Modifier.height(8.dp))
                 Text("Record and replay Sub-GHz signals from remotes, key fobs, and other RF devices.", 
                      style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                 
                 Spacer(Modifier.height(Dimens.SpacingXl))
                 
                 // Recording Status Box
                 Box(Modifier.fillMaxWidth().height(100.dp).border(1.dp, RetroGreen), contentAlignment = Alignment.Center) {
                     if (isRecording) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(color = Color.Red)
                             Spacer(Modifier.height(8.dp))
                             Text("RECORDING...", color = Color.Red)
                         }
                     } else if (hasRecording) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Text("✓ SIGNAL CAPTURED", color = RetroGreen, fontWeight = FontWeight.Bold)
                             Spacer(Modifier.height(4.dp))
                             Text("Ready to replay", color = Color.Gray, fontSize = 12.sp)
                         }
                     } else {
                         Text("NO RECORDING", color = Color.Gray)
                     }
                 }
                 
                 Spacer(Modifier.height(Dimens.SpacingXl))
                 
                 // Record / Replay Buttons
                 Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMd)
                 ) {
                     Button(
                        onClick = {
                            if (isRecording) {
                                // Stop recording - firmware handles this internally
                                isRecording = false
                                hasRecording = true
                            } else {
                                usbManager.write("RX_RECORD")
                                isRecording = true
                                hasRecording = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else RetroGreen)
                    ) {
                        Text(if (isRecording) "STOP REC" else "RECORD", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    
                    Button(
                        onClick = {
                            if (hasRecording) {
                                usbManager.write("TX_REPLAY")
                            }
                        },
                        enabled = hasRecording && !isRecording,
                        modifier = Modifier.weight(1f).height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasRecording) RetroGreen.copy(alpha = 0.8f) else Color.Gray,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    ) {
                        Text("REPLAY", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (hasRecording) Color.Black else Color.Gray)
                    }
                 }
            }
        }
    }
}
