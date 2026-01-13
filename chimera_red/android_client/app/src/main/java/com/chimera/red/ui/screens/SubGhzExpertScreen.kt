package com.chimera.red.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chimera.red.RetroGreen
import com.chimera.red.ui.theme.Dimens

@Composable
fun SubGhzExpertScreen(onSend: (String) -> Unit, isRecorded: Boolean) {
    var freq by remember { mutableStateOf("433.92") }
    
    Column(
        Modifier.padding(Dimens.SpacingMd), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "HYPNOTOAD TRANSCEIVER", 
            color = RetroGreen, 
            fontWeight = FontWeight.Bold, 
            fontSize = Dimens.TextTitle, 
            fontFamily = FontFamily.Monospace
        )
        Text(
            "ALL GLORY TO THE HYPNOTOAD", 
            fontSize = Dimens.TextCaption, 
            color = RetroGreen.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        // Frequency Tuner
        OutlinedTextField(
            value = freq,
            onValueChange = { freq = it },
            label = { Text("Frequency (MHz)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RetroGreen,
                unfocusedBorderColor = RetroGreen.copy(alpha = 0.5f),
                focusedTextColor = RetroGreen, 
                unfocusedTextColor = RetroGreen
            )
        )
        Spacer(Modifier.height(Dimens.SpacingSm))
        Row {
            Button(
                onClick = { onSend("SET_FREQ:$freq") }, 
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
            ) { 
                Text("Tune", color = Color.Black) 
            }
            Spacer(Modifier.width(Dimens.SpacingSm))
            Button(
                onClick = { onSend("INIT_CC1101") }, 
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen)
            ) { 
                Text("Reset", color = Color.Black) 
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingXl))
        Divider(color = RetroGreen, thickness = Dimens.BorderStandard)
        Spacer(Modifier.height(Dimens.SpacingXl))
        
        // Replay Controls
        Text("SIGNAL REPLAY", color = RetroGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { 
                    onSend("RX_RECORD") 
                },
                colors = ButtonDefaults.buttonColors(containerColor = if(isRecorded) RetroGreen else Color.Gray),
                modifier = Modifier.size(Dimens.ButtonSizeLg)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_btn_speak_now), 
                        contentDescription = null, 
                        tint = Color.White
                    )
                    Text(
                        if(isRecorded) "SAVED!" else "RECORD", 
                        fontSize = Dimens.TextCaption, 
                        color = Color.White
                    )
                }
            }
            
            // Replay Button
            Button(
                onClick = { 
                    if(isRecorded) onSend("TX_REPLAY") 
                },
                colors = ButtonDefaults.buttonColors(containerColor = if(isRecorded) Color.Red else Color.DarkGray),
                modifier = Modifier.size(Dimens.ButtonSizeLg)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_media_play), 
                        contentDescription = null, 
                        tint = Color.White
                    )
                    Text("REPLAY", fontSize = Dimens.TextCaption, color = Color.White)
                }
            }
        }
    }
}
