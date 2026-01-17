package com.chimera.red.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.chimera.red.ChimeraRepository
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.ui.theme.Dimens
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext

@Composable
fun TerminalScreen(usbManager: UsbSerialManager) {
    var inputText by remember { mutableStateOf("") }
    // Hoisted state
    val logMessages = ChimeraRepository.terminalLogs
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Subscribe to incoming data - AUTO SCROLL when logs change
    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            listState.animateScrollToItem(0) // reversed list
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpacingMd)
    ) {
        // Header Actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("SERIAL OUTPUT", color = RetroGreen, fontFamily = FontFamily.Monospace)
            
            Row {
                Button(
                    onClick = { ChimeraRepository.clearLogs() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.3f)),
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("CLEAR", fontSize = 10.sp, color = RetroGreen)
                }
                
                Spacer(Modifier.width(8.dp))
                
                Button(
                    onClick = { 
                        val allText = logMessages.joinToString("\n") { 
                           "[${Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalTime()}] ${it.message}" 
                        }
                        clipboardManager.setText(AnnotatedString(allText))
                        android.widget.Toast.makeText(context, "Logs Copied!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RetroGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("COPY", fontSize = 10.sp, color = RetroGreen)
                }
            }
        }

        // Output Area
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.SpacingSm),
                reverseLayout = true // Classic Terminal style, bottom-up
            ) {
                items(logMessages.reversed()) { msg ->
                    val timeStr = Instant.ofEpochMilli(msg.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    Text(
                        text = "[$timeStr] ${msg.message}",
                        color = RetroGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        // Input Area
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    color = RetroGreen,
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RetroGreen,
                    unfocusedBorderColor = RetroGreen.copy(alpha = 0.5f),
                    cursorColor = RetroGreen
                ),
                placeholder = { Text("CMD...", color = RetroGreen.copy(alpha = 0.3f)) },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.width(Dimens.SpacingSm))
            
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        usbManager.write(inputText) // Calls the alias in UsbSerialManager
                        ChimeraRepository.addLog("> $inputText")
                        inputText = ""
                        // Scroll to bottom
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
            ) {
                Text("SEND", color = Color.Black)
            }
        }
    }
    
    // Auto-scroll on new message logic would go here
}
