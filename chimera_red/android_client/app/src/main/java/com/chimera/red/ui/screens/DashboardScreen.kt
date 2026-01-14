package com.chimera.red.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chimera.red.R
import com.chimera.red.RetroGreen
import com.chimera.red.DarkGreen
import com.chimera.red.UsbSerialManager
import com.chimera.red.ui.components.TerminalView
import com.chimera.red.ui.theme.Dimens

@Composable
fun DashboardScreen(serialManager: UsbSerialManager, logs: List<String>) {
    val status by serialManager.connectionState.collectAsState(initial = "Disconnected")
    val batteryLevel = 0.75f // Placeholder

    Column(Modifier.fillMaxSize().padding(Dimens.SpacingMd)) {
        // Status Monitor
        Box(
            Modifier
                .fillMaxWidth()
                .border(Dimens.BorderStandard, RetroGreen, RectangleShape)
                .background(DarkGreen.copy(alpha = 0.3f))
                .padding(Dimens.SpacingMd)
        ) {
            Column {
                Text(
                    "SYSTEM STATUS", 
                    fontWeight = FontWeight.Bold, 
                    color = RetroGreen, 
                    fontFamily = FontFamily.Monospace
                )
                Divider(
                    color = RetroGreen, 
                    modifier = Modifier.padding(vertical = Dimens.SpacingSm)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("USB LINK:", fontSize = Dimens.TextCaption, color = RetroGreen)
                        Text(
                            status.uppercase(), 
                            color = if(status.contains("Connected")) RetroGreen else Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (status != "Connected") {
                            androidx.compose.material3.Button(
                                onClick = { serialManager.connect() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = RetroGreen),
                                shape = RectangleShape,
                                modifier = Modifier.height(30.dp).padding(top = 4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("FORCE CONNECT", fontSize = 10.sp, color = Color.Black)
                            }
                        }
                    }
                    // Beer Battery Meter
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Image(
                            painter = painterResource(id = R.drawable.beer_battery),
                            contentDescription = "Fuel",
                            modifier = Modifier.size(Dimens.IconSizeMd),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(RetroGreen, BlendMode.SrcAtop)
                        )
                        Text(
                            "${(batteryLevel * 100).toInt()}% ALCOHOL", 
                            fontSize = Dimens.TextCaption, 
                            color = RetroGreen, 
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingMd))
        
        Text(
            "RECENT SCHEMES", 
            color = RetroGreen, 
            fontFamily = FontFamily.Monospace, 
            fontWeight = FontWeight.Bold
        )
        // Terminal output as "Schemes"
        TerminalView(logs, serialManager)
    }
}
