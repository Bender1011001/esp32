package com.chimera.red

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.chimera.red.ui.navigation.ChimeraNavGraph
import kotlinx.coroutines.launch
import com.chimera.red.ui.theme.ChimeraTheme
import com.chimera.red.ui.components.ChimeraBottomBar

class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbSerialManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (usbManager.ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                             // Permission granted, retry connection
                             usbManager.tryConnect() 
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Retain existing USB Logic
        usbManager = UsbSerialManager(this)
        
        // GLOBAL DATA COLLECTION
        lifecycleScope.launch {
            SerialDataHandler.collect(usbManager.receivedData)
        }
        
        // Register USB Receiver
        val filter = IntentFilter(usbManager.ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Initial Connect Attempt
        usbManager.tryConnect()
        
        setContent {
            ChimeraTheme {
                val navController = rememberNavController()
                
                Scaffold(
                    bottomBar = { ChimeraBottomBar(navController) }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        ChimeraNavGraph(navController, usbManager)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbManager.disconnect()
    }
}
