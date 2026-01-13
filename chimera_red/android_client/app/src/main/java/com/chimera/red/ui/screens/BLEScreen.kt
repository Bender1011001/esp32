package com.chimera.red.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import com.chimera.red.RetroGreen
import com.chimera.red.UsbSerialManager
import com.chimera.red.ui.components.TerminalView
import com.chimera.red.ui.theme.Dimens

@Composable
fun BLEScreen(serialManager: UsbSerialManager, logs: List<String>) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.SpacingMd), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SHINY METAL (BLE)", 
            color = RetroGreen, 
            fontSize = Dimens.TextDisplay, 
            fontFamily = FontFamily.Monospace
        )
        Text(
            "Antiquing in progress...", 
            fontSize = Dimens.TextCaption, 
            color = RetroGreen.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(Dimens.SpacingXl))
        
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { serialManager.send("SCAN_BLE") },
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen),
                shape = RectangleShape
            ) {
                Text("SCAN", color = Color.Black)
            }
            
            Button(
                onClick = { serialManager.send("BLE_SPAM") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RectangleShape
            ) {
                Text("HYPNO-BLAST", color = Color.White)
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingXl))
        
        Text(
            "ZOIDBERG'S DUMPSTER:", 
            color = RetroGreen.copy(alpha = 0.7f), 
            fontSize = Dimens.TextCaption
        )
        TerminalView(logs.filter { it.contains("ble", true) || it.contains("spam", true) }, serialManager)
    }
}
