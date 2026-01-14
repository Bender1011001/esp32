package com.chimera.red.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.chimera.red.RetroGreen
import com.chimera.red.ui.theme.Dimens

@Composable
fun WiFiExpertScreen(onSend: (String) -> Unit, onManualCrack: () -> Unit) {
    var channel by remember { mutableStateOf("1") }
    var targetBssid by remember { mutableStateOf("") }

    Column(Modifier.padding(Dimens.SpacingMd)) {
        Text(
            "MEATBAG DETECTOR (WIFI)", 
            color = RetroGreen, 
            fontWeight = FontWeight.Bold, 
            fontSize = Dimens.TextTitle
        )
        Text(
            "Fry's search history: still loading...", 
            fontSize = Dimens.TextCaption, 
            color = RetroGreen.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = channel,
                onValueChange = { channel = it },
                label = { Text("Channel") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RetroGreen,
                    unfocusedBorderColor = RetroGreen.copy(alpha = 0.5f),
                    focusedTextColor = RetroGreen, 
                    unfocusedTextColor = RetroGreen
                )
            )
            Spacer(Modifier.width(Dimens.SpacingSm))
            Button(
                onClick = { onSend("SNIFF_START:$channel") }, 
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
            ) { 
                Text("SNIFF", color = Color.Black) 
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        OutlinedTextField(
            value = targetBssid,
            onValueChange = { targetBssid = it },
            label = { Text("Target BSSID (AA:BB:CC...)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RetroGreen,
                unfocusedBorderColor = RetroGreen.copy(alpha = 0.5f),
                focusedTextColor = RetroGreen, 
                unfocusedTextColor = RetroGreen
            )
        )
        
        Spacer(Modifier.height(Dimens.SpacingSm))
        
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onSend("DEAUTH:$targetBssid") }, 
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.weight(1f)
            ) { 
                Text("DEATH BY SNU SNU", color = Color.White) 
            }
            Spacer(Modifier.width(Dimens.SpacingSm))
            Button(
                onClick = { onSend("SNIFF_STOP") }, 
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.weight(1f)
            ) { 
                Text("STOP", color = Color.White) 
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingMd))
        Spacer(Modifier.height(Dimens.SpacingMd))
        Button(
            onClick = { onSend("CMD_SPECTRUM") }, 
            colors = ButtonDefaults.buttonColors(containerColor = RetroGreen),
            modifier = Modifier.fillMaxWidth()
        ) { 
            Text("FULL SPECTRUM SCAN", color = Color.Black) 
        }

        Spacer(Modifier.height(Dimens.SpacingMd))
        Button(
             onClick = { onManualCrack() },
             colors = ButtonDefaults.buttonColors(containerColor = RetroGreen.copy(alpha=0.8f)),
             modifier = Modifier.fillMaxWidth()
        ) {
             Text("OPEN BENDER'S LOCKER (CRACK TOOL)", color = Color.Black)
        }
    }
}
