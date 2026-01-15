package com.chimera.red.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.ui.theme.Dimens

@Composable
fun SettingsScreen(usbManager: UsbSerialManager) {
    val context = LocalContext.current
    var keepScreenOn by remember { mutableStateOf(false) }

    // Effect to toggle screen wake lock
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            // Optional cleanup if leaving screen, but usually we want this setting to persist?
            // For now, let's keep it tied to this screen or Global state. 
            // Ideally this should be a stored preference.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpacingMd)
    ) {
        Text(
            text = "SYSTEM CONFIGURATION",
            style = MaterialTheme.typography.titleMedium,
            color = RetroGreen,
            modifier = Modifier.padding(bottom = Dimens.SpacingLg)
        )

        // Keep Screen On
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpacingMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Keep Screen On",
                        fontWeight = FontWeight.Bold,
                        color = RetroGreen
                    )
                    Text(
                        text = "Prevent device from sleeping during scans",
                        style = MaterialTheme.typography.bodySmall,
                        color = RetroGreen.copy(alpha=0.7f)
                    )
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = RetroGreen,
                        uncheckedThumbColor = RetroGreen,
                        uncheckedTrackColor = Color.Black,
                        uncheckedBorderColor = RetroGreen
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // App Info
        Card(
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
             modifier = Modifier.fillMaxWidth()
        ) {
             Column(
                 modifier = Modifier.padding(Dimens.SpacingMd)
             ) {
                 Text(
                     text = "ABOUT",
                     fontWeight = FontWeight.Bold,
                     color = RetroGreen
                 )
                 Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                 Text("Chimera Red", color = RetroGreen)
                 Text("Version: 1.0.0-alpha", color = RetroGreen.copy(alpha=0.7f))
                 Text("Build: Debug / S24 Ultra Optimized", color = RetroGreen.copy(alpha=0.5f))
             }
        }
    }
}
