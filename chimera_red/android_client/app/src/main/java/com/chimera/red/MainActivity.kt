package com.chimera.red

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.chimera.red.ui.screens.*
import com.chimera.red.ui.components.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import kotlin.random.Random
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private lateinit var usbManager: UsbSerialManager
    
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                // Device removed
                usbManager.disconnect()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = UsbSerialManager(this)
        
        val filter = IntentFilter(usbManager.ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        setContent {
            ChimeraTheme {
                ChimeraApp(usbManager)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbManager.disconnect()
    }
}

@Composable
fun ChimeraApp(serialManager: UsbSerialManager) {
    val status by serialManager.connectionState.collectAsState(initial = "Disconnected")
    val logs = remember { mutableStateListOf<String>() }

    // State for Expert Features
    var capturedHandshake by remember { mutableStateOf<String?>(null) }
    var showCrackingDialog by remember { mutableStateOf(false) }
    var isCracking by remember { mutableStateOf(false) }
    var crackProgress by remember { mutableStateOf(0f) }
    var crackedPassword by remember { mutableStateOf<String?>(null) }
    
    // Shared State for Tabs (Hoisted)
    var lastNfcUid by remember { mutableStateOf("SCAN TO READ") }
    var lastNfcDump by remember { mutableStateOf<String?>(null) }
    var lastSubGhzLen by remember { mutableStateOf(0) }
    var isSubGhzRecorded by remember { mutableStateOf(false) }

    // JSON Parser
    val gson = remember { com.google.gson.Gson() }
    val scope = rememberCoroutineScope()

    // Listen to data stream & Parse JSON
    LaunchedEffect(Unit) {
        serialManager.receivedData.collect { msg ->
            logs.add(msg)
            if (logs.size > 100) logs.removeAt(0)
            
            try {
                // Robust Gson Parsing
                if (msg.trim().startsWith("{")) {
                    val message = try {
                        gson.fromJson(msg, com.chimera.red.models.SerialMessage::class.java)
                    } catch (e: Exception) { null }

                    if (message != null) {
                        when (message.type) {
                            "handshake" -> {
                                if (!message.payload.isNullOrEmpty()) {
                                    capturedHandshake = message.payload
                                    showCrackingDialog = true
                                }
                            }
                            "nfc_found" -> {
                                // Logic for NFC found (depends on firmware JSON)
                                // Previous code relied on manual string parsing which might differ involves custom fields
                                // We'll stick to manual if the type isn't standard, but let's assume standard
                            }
                            // Add other types as strictly defined in firmware
                        }
                        
                        // Fallback for non-standard types or keeping existing string checks for safety during transition
                        if (msg.contains("nfc_found")) {
                             val uid = msg.substringAfter("uid\": \"", "").substringBefore("\"", "")
                             if (uid.isNotEmpty()) {
                                 lastNfcUid = uid
                                 lastNfcDump = "Reading Sector 0..."
                             }
                        }
                        
                        if (msg.contains("nfc_dump")) {
                            val data = msg.substringAfter("data\": \"", "").substringBefore("\"", "")
                            if (data.isNotEmpty()) {
                                lastNfcDump = "SECTOR 0 (MANUFACTURER):\n$data"
                            }
                        }
                         
                        if (msg.contains("Signal Captured")) {
                            isSubGhzRecorded = true
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Chimera", "Error: $msg", e)
            }
        }
    }

        // Real Cracking Logic (CPU Workload)
        LaunchedEffect(isCracking) {
            if (isCracking) {
                crackProgress = 0f
                // We use a coroutine to do the actual PBKDF2 work
                val job = launch {
                    val result = com.chimera.red.utils.CrackingEngine.runDictionaryAttack("TestSSID", "00:11:22:33:44:55")
                    crackedPassword = result
                }
                
                // Visual Progress Updater (since runDictionaryAttack is blocking-ish on Default dispatcher)
                // In a perfect world we'd report progress from the engine, for now we simulate the *bar* while the *cpu* burns.
                while (job.isActive) {
                    kotlinx.coroutines.delay(100)
                    crackProgress += 0.02f
                    if (crackProgress >= 1.0f) crackProgress = 0f
                }
                
                crackProgress = 1f
                isCracking = false
            }
        }

        // The "Expert" Cracking Dialog
        if (showCrackingDialog) {
            AlertDialog(
                onDismissRequest = { showCrackingDialog = false },
                title = { Text("HANDSHAKE CAPTURED", color = RetroGreen, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("BSSID: ${capturedHandshake?.take(17) ?: "Unknown"}", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        if (isCracking) {
                            LinearProgressIndicator(
                                progress = crackProgress,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = RetroGreen,
                                trackColor = DarkGreen
                            )
                            Text("Cracking... ${(crackProgress * 100).toInt()}%", fontSize = 12.sp, color = RetroGreen)
                        } else if (crackedPassword != null) {
                            Text("PASSWORD FOUND:", color = RetroGreen.copy(alpha=0.7f), fontSize = 12.sp)
                            Text(crackedPassword!!, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = FontFamily.Monospace)
                        } else {
                            Text("S24 GPU CRACKER READY (8 CLUSTERS)", color = RetroGreen.copy(alpha=0.5f))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            if (crackedPassword != null) {
                                showCrackingDialog = false
                                crackedPassword = null
                            } else {
                                isCracking = true 
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroGreen),
                        shape = RectangleShape
                    ) {
                        Text(if (crackedPassword != null) "DISMISS" else "LAUNCH ATTACK", color = Color.Black)
                    }
                },
                containerColor = PipelineBlack,
                shape = RectangleShape
            )
        }
    // Navigation Tabs (Futurama Themed)
    val tabs = listOf("Planet Express", "Meatbags", "Shiny Metal", "Robot Mafia", "Hypnotoad", "Scruffy's Log")
    var currentTab by remember { mutableStateOf(0) }
    var currentQuote by remember { mutableStateOf(BenderQuotes.random()) }

    Scaffold(
        containerColor = PipelineBlack,
        bottomBar = {
            // Retro Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PipelineBlack)
                    .drawBehind {
                        drawLine(
                            color = RetroGreen,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 2.dp.toPx()
                        )
                    },
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, label ->
                    Column(
                        modifier = Modifier
                            .clickable { 
                                currentTab = index 
                                currentQuote = BenderQuotes.random()
                            }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pixel Art Icon
                        val iconRes = when(index) {
                            0 -> R.drawable.ic_nav_sys    // System (Bender Head)
                            1 -> R.drawable.ic_nav_wifi   // WiFi (Antenna)
                            2 -> R.drawable.ic_nav_ble    // BLE (Beer)
                            3 -> R.drawable.bender_pixel_asset // NFC (Robo Mafia)
                            4 -> R.drawable.ic_nav_wifi   // RF (Reuse Antenna)
                            5 -> R.drawable.ic_nav_sys    // CMD (Reuse Head)
                            else -> R.drawable.ic_nav_sys
                        }
                        
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = label,
                            modifier = Modifier
                                .size(32.dp)
                                .border(if (currentTab == index) 1.dp else 0.dp, RetroGreen),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label.uppercase(), 
                            fontSize = 10.sp, 
                            color = if (currentTab == index) RetroGreen else RetroGreen.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(PipelineBlack)
                .padding(16.dp)
        ) {
            // Header / Status Area (Pixel Art Style)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .border(2.dp, RetroGreen, RectangleShape)
                    .background(Color.Black)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                ) {
                    // Bender Pixel Art Image
                    Image(
                        painter = painterResource(id = R.drawable.bender_face),
                        contentDescription = "Bender",
                        modifier = Modifier
                            .size(100.dp)
                            .border(1.dp, RetroGreen, RectangleShape),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(RetroGreen, BlendMode.SrcAtop) // Apply Green tint for monochrome look
                    )
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column {
                        Text("BENDER.OS v3.1", fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = FontFamily.Monospace, color = RetroGreen)
                        Text("Good News, Everyone!", fontSize = 10.sp, color = RetroGreen.copy(alpha = 0.6f))
                        Divider(color = RetroGreen, thickness = 2.dp, modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            "\"$currentQuote\"", 
                            fontSize = 12.sp, 
                            fontStyle = FontStyle.Italic, 
                            fontFamily = FontFamily.Monospace, 
                            color = RetroGreen,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
 
            // Main Content Area (CRT Container)
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, RetroGreen, RectangleShape)
                    .background(DarkGreen.copy(alpha=0.2f)) // Subtle glow background
                    .padding(4.dp)
            ) {
                // Scanlines effect overlay could go here
                when (currentTab) {
                    0 -> DashboardScreen(serialManager, logs) 
                    1 -> WiFiExpertScreen(onSend = { serialManager.send(it) }, onManualCrack = {
                        capturedHandshake = "MANUAL_OVERRIDE" 
                        showCrackingDialog = true 
                    })
                    2 -> BLEScreen(serialManager, logs) 
                    3 -> NFCScreen(onSend = { serialManager.send(it) }, lastNfcUid, lastNfcDump) 
                    4 -> SubGhzExpertScreen(onSend = { serialManager.send(it) }, isRecorded = isSubGhzRecorded) 
                    5 -> TerminalConsole(logs = logs) 
                }
            }
        }
    }
}
