package com.chimera.red.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chimera.red.R
// import com.chimera.red.ui.theme.ChimeraRedTheme

@Composable
fun ControllerScreen(
    sendCommand: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Remote Controller", style = MaterialTheme.typography.headlineMedium, color = Color.Green)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // D-Pad
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Up
            ControllerButton(
                text = "UP",
                modifier = Modifier.align(Alignment.TopCenter),
                onClick = { sendCommand("INPUT_UP") }
            )
            // Down
            ControllerButton(
                text = "DOWN",
                modifier = Modifier.align(Alignment.BottomCenter),
                onClick = { sendCommand("INPUT_DOWN") }
            )
            // Left
            ControllerButton(
                text = "LEFT",
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = { sendCommand("INPUT_LEFT") }
            )
            // Right
            ControllerButton(
                text = "RIGHT",
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = { sendCommand("INPUT_RIGHT") }
            )
            
            // Center Select
             ControllerButton(
                text = "OK",
                modifier = Modifier.align(Alignment.Center),
                color = Color.Red,
                onClick = { sendCommand("INPUT_SELECT") }
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ControllerButton(
                text = "BACK",
                modifier = Modifier,
                color = Color.Gray,
                onClick = { sendCommand("INPUT_BACK") }
            )
        }
    }
}

@Composable
fun ControllerButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Green,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall)
    }
}
