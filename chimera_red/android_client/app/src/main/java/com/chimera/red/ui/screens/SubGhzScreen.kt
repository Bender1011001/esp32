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
                    if (message.msg?.contains("Replay Complete") == true) {
                         // replay finished
                    }
                    // Hacky check for record done? Currently firmware doesn't say "Record Done", it likely just captures once or waits?
                    // Actually handleRxRecord resets buffer and waits. Ideally it should send "Captured" when signal received.
                    // For now we assume "Recording..." means listening.
                }
            } catch (e: Exception) { }
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
               Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height / 2
                    drawLine(RetroGreen.copy(0.2f), Offset(0f, midY), Offset(size.width, midY))
                    
                    val timeScale = 0.05f 
                    val dataToDraw = analyzerData.takeLast(500)
                    var currentX = 0f
                    
                    if (dataToDraw.isNotEmpty()) {
                         val path = Path()
                         val startY = if (dataToDraw.first() > 0) midY - 50f else midY + 50f
                         path.moveTo(0f, startY)
                         dataToDraw.forEach { duration ->
                            val isHigh = duration > 0
                            val pulseWidth = kotlin.math.abs(duration) * timeScale
                            val y = if (isHigh) midY - 50f else midY + 50f
                            path.lineTo(currentX, y) 
                            currentX += pulseWidth
                            path.lineTo(currentX, y)
                        }
                        drawPath(path, RetroGreen, style = Stroke(2.dp.toPx()))
                    }
               }
               
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
            // == ATTACKS TAB ==
            Column(Modifier.weight(1f).padding(Dimens.SpacingMd)) {
                 Text("GATE OPENER BRUTE FORCE", fontWeight = FontWeight.Bold, color = RetroGreen)
                 Spacer(Modifier.height(8.dp))
                 Text("Cycles common 12-bit fixed codes (CAME, Nice, PT2262). Warning: This may jam local frequencies.", 
                      style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                 
                 Spacer(Modifier.height(Dimens.SpacingXl))
                 
                 var isBruting by remember { mutableStateOf(false) }
                 
                 Box(Modifier.fillMaxWidth().height(100.dp).border(1.dp, RetroGreen), contentAlignment = Alignment.Center) {
                     if (isBruting) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(color = RetroGreen)
                             Spacer(Modifier.height(8.dp))
                             Text("TRANSMITTING...", color = RetroGreen)
                         }
                     } else {
                         Text("READY", color = RetroGreen)
                     }
                 }
                 
                 Spacer(Modifier.height(Dimens.SpacingXl))
                 
                 Button(
                    onClick = {
                        isBruting = !isBruting
                        if (isBruting) {
                            usbManager.write("SUBGHZ_BRUTE")
                        } else {
                             // Stop? Firmware doesn't explicitly support stop yet without serial check loop
                             isBruting = false
                             // We can try sending any char to break the serial loop if implemented
                             usbManager.write("STOP") 
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isBruting) Color.Red else RetroGreen)
                ) {
                    Text(if (isBruting) "STOP ATTACK" else "START 12-BIT BRUTE FORCE", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}
