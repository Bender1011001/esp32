package com.chimera.red.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.models.SerialMessage
import com.chimera.red.ui.theme.Dimens
import com.google.gson.Gson

@Composable
fun NFCScreen(usbManager: UsbSerialManager) {
    var nfcData by remember { mutableStateOf("NO TAG DATA") }
    var status by remember { mutableStateOf("READY") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val gson = remember { Gson() }

    LaunchedEffect(Unit) {
        usbManager.receivedData.collect { msg ->
            try {
                if (msg.startsWith("{")) {
                     val message = gson.fromJson(msg, SerialMessage::class.java)
                     if (!message.data.isNullOrEmpty()) {
                         nfcData = message.data
                         status = "TAG READ SUCCESS"
                         isProcessing = false
                     }
                     // Update status if provided
                     if (!message.msg.isNullOrEmpty()) {
                         status = message.msg.uppercase()
                         if (message.msg.contains("Emulating")) {
                             isProcessing = true
                         }
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
            .padding(Dimens.SpacingMd),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = "NFC",
            tint = RetroGreen,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // Status
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            color = RetroGreen,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        // Data Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, RetroGreen, RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpacingMd),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LAST TAG DATA (HEX)",
                    color = RetroGreen.copy(alpha=0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                Text(
                    text = nfcData,
                    color = RetroGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMd)
        ) {
            Button(
                onClick = {
                    status = "SCANNING..."
                    isProcessing = true
                    nfcData = "..."
                    usbManager.write("NFC_SCAN")
                },
                modifier = Modifier.weight(1f).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = Color.Black)
                    Text("READ TAG", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            
            Button(
                onClick = {
                     status = "EMULATING..."
                     isProcessing = true
                     usbManager.write("NFC_EMULATE")
                },
                modifier = Modifier.weight(1f).height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RetroGreen.copy(alpha = 0.3f),
                    contentColor = RetroGreen
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Nfc, contentDescription = null)
                    Text("EMULATE", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        if (isProcessing) {
            Spacer(modifier = Modifier.height(Dimens.SpacingMd))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = RetroGreen,
                trackColor = RetroGreen.copy(alpha = 0.2f)
            )
        }
    }
}
