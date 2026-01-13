package com.chimera.red.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chimera.red.RetroGreen
import com.chimera.red.DarkGreen
import com.chimera.red.ui.theme.Dimens

@Composable
fun NFCScreen(onSend: (String) -> Unit, lastNfcUid: String, memoryDump: String?) {
    var isEmulating by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(Dimens.SpacingMd), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ROBOT MAFIA LOOT", 
            color = RetroGreen, 
            fontSize = Dimens.TextDisplay, 
            fontFamily = FontFamily.Monospace, 
            fontWeight = FontWeight.Bold
        )
        Text(
            "Clamps would be proud.", 
            fontSize = Dimens.TextCaption, 
            color = RetroGreen.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        // Card Visualizer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.CardHeightMd)
                .border(Dimens.BorderStandard, RetroGreen, RoundedCornerShape(Dimens.CornerMd))
                .background(DarkGreen.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(Dimens.SpacingMd)) {
                Text("MIFARE CLASSIC 1K", fontWeight = FontWeight.Bold, color = RetroGreen)
                Spacer(Modifier.height(Dimens.SpacingSm))
                Text("UID: $lastNfcUid", color = Color.White, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(Dimens.SpacingSm))
                Divider(color = RetroGreen)
                Spacer(Modifier.height(Dimens.SpacingSm))
                Text(
                    memoryDump ?: "[MEMORY EMPTY]", 
                    fontSize = Dimens.TextCaption, 
                    color = RetroGreen, 
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingLg))
        
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { 
                    onSend("NFC_SCAN") 
                },
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
            ) {
                Text("STEAL (READ)", color = Color.Black)
            }
            
            Button(
                onClick = { 
                    onSend("NFC_EMULATE") 
                    isEmulating = !isEmulating
                },
                colors = ButtonDefaults.buttonColors(containerColor = if(isEmulating) Color.Red else Color.Gray)
            ) {
                Text(if(isEmulating) "STOP SPOOF" else "SPOOF (EMULATE)", color = Color.White)
            }
        }
    }
}
