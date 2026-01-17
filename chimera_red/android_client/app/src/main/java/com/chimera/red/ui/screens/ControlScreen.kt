package com.chimera.red.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.ui.theme.Dimens

@Composable
fun ControlScreen(usbManager: UsbSerialManager) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    fun sendCmd(cmd: String) {
        usbManager.write(cmd)
        android.widget.Toast.makeText(context, "Sent: $cmd", android.widget.Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpacingMd),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        Text(
            text = "REMOTE CONTROL",
            style = MaterialTheme.typography.titleMedium,
            color = RetroGreen,
            modifier = Modifier.padding(vertical = Dimens.SpacingLg)
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        // D-Pad Area
        Box(
           modifier = Modifier.size(240.dp),
           contentAlignment = Alignment.Center
        ) {
            // Background Cross
            Box(Modifier.size(80.dp, 240.dp).background(RetroGreen.copy(alpha=0.1f)))
            Box(Modifier.size(240.dp, 80.dp).background(RetroGreen.copy(alpha=0.1f)))

            // UP
            ControlButton(
                modifier = Modifier.align(Alignment.TopCenter), 
                icon = Icons.Default.ArrowUpward,
                onClick = { sendCmd("INPUT_UP") }
            )
            
            // DOWN
            ControlButton(
                modifier = Modifier.align(Alignment.BottomCenter), 
                icon = Icons.Default.ArrowDownward,
                onClick = { sendCmd("INPUT_DOWN") }
            )
            
            // LEFT
            ControlButton(
                modifier = Modifier.align(Alignment.CenterStart), 
                icon = Icons.Default.ArrowBack, // Pointing Left
                onClick = { sendCmd("INPUT_LEFT") } // Assuming Left exists or maps to Back? main.cpp had LEFT
            )
            
            // RIGHT
            ControlButton(
                modifier = Modifier.align(Alignment.CenterEnd), 
                icon = Icons.Default.ArrowForward,
                onClick = { sendCmd("INPUT_RIGHT") }
            )
            
            // SELECT (Center)
            Button(
                onClick = { sendCmd("INPUT_SELECT") },
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen),
                contentPadding = PaddingValues(0.dp)
            ) {
                 Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { sendCmd("INPUT_BACK") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                modifier = Modifier.width(120.dp).height(50.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("BACK", color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            "Controls the on-device ESP32 Display",
            color = RetroGreen.copy(alpha=0.5f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun ControlButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(60.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = RetroGreen.copy(alpha = 0.2f),
            contentColor = RetroGreen
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
    }
}
