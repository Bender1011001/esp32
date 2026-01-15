package com.chimera.red.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    // Logic Analyzer State
    var isAnalyzerRunning by remember { mutableStateOf(false) }
    // Hoisted state
    val analyzerData = ChimeraRepository.analyzerData
    
    // Config State
    var currentFreq by remember { mutableStateOf("433.92") }
    
    val gson = remember { Gson() }

    // Data Processing
    LaunchedEffect(Unit) {
        usbManager.receivedData.collect { msg ->
            try {
                if (msg.startsWith("{")) {
                    val message = gson.fromJson(msg, SerialMessage::class.java)
                    
                    // Logic Analyzer Pulses
                    if (message.type == "analyzer_data" && message.pulses != null) {
                        ChimeraRepository.appendAnalyzerData(message.pulses)
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpacingMd)
    ) {
        // Header
        Text(
            text = "SUB-GHZ VISUALIZER",
            style = MaterialTheme.typography.titleMedium,
            color = RetroGreen,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = Dimens.SpacingMd)
        )

        // Visualizer Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(Dimens.BorderThin, RetroGreen, RectangleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val midY = height / 2
                
                // Draw Grid
                drawLine(
                    color = RetroGreen.copy(alpha = 0.2f),
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 1f
                )

                if (analyzerData.isEmpty() && !isAnalyzerRunning) {
                    // Placeholder Text is hard in Canvas without NativePaint, 
                    // relying on Overlay Composable below
                } else {
                    val path = Path()
                    var currentX = 0f
                    // Scale: 500 pulses fit in width
                    // Just a simple visualization strategy:
                    // Each pulse width is proportional to distinct duration
                    // OR simple uniform width per pulse if we just want to see logic levels? 
                    // Let's try proportional but capped.
                    
                    // Improved: Time-based scale.
                    // Total window time? Let's arbitrary scale factor.
                    // 1 pixel = 20 microseconds?
                    val timeScale = 0.05f 
                    
                    // We only draw what fits
                    val dataToDraw = analyzerData.takeLast(500)
                    
                    if (dataToDraw.isNotEmpty()) {
                        val startY = if (dataToDraw.first() > 0) midY - 50f else midY + 50f
                        path.moveTo(0f, startY)
                        
                        dataToDraw.forEach { duration ->
                            val isHigh = duration > 0
                            val pulseWidth = kotlin.math.abs(duration) * timeScale
                            val y = if (isHigh) midY - 50f else midY + 50f
                            
                            // Vertical Transition
                            path.lineTo(currentX, y) 
                            // Horizontal Duration
                            currentX += pulseWidth
                            path.lineTo(currentX, y)
                        }
                        
                        drawPath(
                            path = path,
                            color = RetroGreen,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
            
            // Status Overlay
            if (isAnalyzerRunning) {
                Text(
                    text = "CAPTURING...",
                    color = RetroGreen,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    fontFamily = FontFamily.Monospace
                )
            } else if (analyzerData.isEmpty()) {
                Text(
                    text = "READY TO CAPTURE",
                    color = RetroGreen.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Freq Tuner
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            text = freq, 
                            color = if (isSelected) Color.Black else RetroGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (isAnalyzerRunning) {
                        usbManager.write("ANALYZER_STOP")
                        isAnalyzerRunning = false
                    } else {
                        usbManager.write("ANALYZER_START")
                        isAnalyzerRunning = true
                        ChimeraRepository.clearAnalyzer() // Clear old data
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAnalyzerRunning) Color.Red else RetroGreen
                ),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(
                    text = if (isAnalyzerRunning) "STOP CAPTURE" else "START CAPTURE",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { 
                    usbManager.write("TX_REPLAY") 
                },
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen.copy(alpha = 0.3f)),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text("REPLAY RAW", color = RetroGreen)
            }
        }
    }
}
