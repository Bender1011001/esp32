package com.chimera.red.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.chimera.red.UsbSerialManager
import com.chimera.red.ui.theme.Dimens

@Composable
fun DashboardScreen(usbManager: UsbSerialManager) {
    val connectionState by usbManager.connectionState.collectAsState(initial = "Disconnected")
    
    Column(Modifier.padding(Dimens.SpacingMd)) {
        // Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Dimens.SpacingMd)) {
                Text(
                    text = "System Status", 
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Dimens.SpacingSm))
                Text("Connection: $connectionState")
            }
        }
    }
}
