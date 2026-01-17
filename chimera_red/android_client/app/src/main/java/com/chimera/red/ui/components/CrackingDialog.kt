package com.chimera.red.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chimera.red.RetroGreen
import com.chimera.red.utils.CrackingEngine
import com.chimera.red.crypto.VulkanCracker

/**
 * Chimera Red WPA2 Cracking Dialog
 * 
 * A professional, polished UI for displaying the cracking operation.
 * Shows real-time progress, speed metrics, and clear success/failure states.
 */
@Composable
fun CrackingDialog(ssid: String, onDismiss: () -> Unit) {
    // State
    var currentPassword by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(0) }
    var testedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf("INITIALIZING") }
    var result by remember { mutableStateOf<String?>(null) }
    var isDone by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    // Launch cracking operation
    LaunchedEffect(ssid) {
        result = CrackingEngine.runDictionaryAttack(ssid) { password, tested, total, hps ->
            currentPassword = password
            testedCount = tested
            totalCount = total
            progress = if (total > 0) tested.toFloat() / total else 0f
            speed = hps
            phase = "MIC VERIFY"
        }
        isDone = true
        hasError = result == null && totalCount == 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, RetroGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = RetroGreen,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        "CHIMERA CRACKER",
                        color = RetroGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                    Text(
                        "v2.0 • IEEE 802.11i",
                        color = RetroGreen.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Target Info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "TARGET",
                            color = RetroGreen.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            ssid,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp
                        )
                    }
                }

                // Main Content
                if (!isDone) {
                    // Progress State
                    CrackingProgress(
                        phase = phase,
                        currentPassword = currentPassword,
                        progress = progress,
                        testedCount = testedCount,
                        totalCount = totalCount,
                        speed = speed
                    )
                } else {
                    // Result State
                    CrackingResult(
                        password = result,
                        hasError = hasError
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = RetroGreen)
            ) {
                Text(
                    if (isDone) "CLOSE" else "CANCEL",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun CrackingProgress(
    phase: String,
    currentPassword: String,
    progress: Float,
    testedCount: Int,
    totalCount: Int,
    speed: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Engine Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "ENGINE",
                    color = RetroGreen.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                val vulkan = VulkanCracker.getInstance()
                val isGpu = vulkan.isGpuAvailable()
                Text(
                    if (isGpu) "VULKAN GPU" else "NATIVE CPU",
                    color = if (isGpu) Color(0xFF00FFFF) else RetroGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "PHASE",
                    color = RetroGreen.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    phase,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Divider(color = RetroGreen.copy(alpha = 0.2f))

        // Current Password Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            RetroGreen.copy(alpha = 0.1f),
                            RetroGreen.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    "TESTING",
                    color = RetroGreen.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    currentPassword.ifEmpty { "..." },
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Progress Bar
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = RetroGreen,
                trackColor = RetroGreen.copy(alpha = 0.2f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$testedCount / $totalCount",
                    color = RetroGreen.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    color = RetroGreen.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Speed Indicator
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SPEED",
                        color = RetroGreen.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "$speed H/s",
                        color = RetroGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp
                    )
                }
                Text(
                    "PBKDF2-HMAC-SHA1\n4096 iterations",
                    color = RetroGreen.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun CrackingResult(
    password: String?,
    hasError: Boolean
) {
    if (hasError) {
        // Error State
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "HANDSHAKE REQUIRED",
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Capture a valid 4-way handshake\nusing DEAUTH + SNIFF",
                    color = Color(0xFFFF6B6B).copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else if (password != null) {
        // Success State
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A1A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = RetroGreen,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "KEY FOUND!",
                    color = RetroGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp
                )
                
                Divider(color = RetroGreen.copy(alpha = 0.3f))
                
                Text(
                    password,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp
                )
                
                Text(
                    "MIC Verified ✓",
                    color = RetroGreen.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    } else {
        // Not Found State
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFF6B9DFF),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "PASSWORD NOT FOUND",
                    color = Color(0xFF6B9DFF),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Password not in wordlist.\nTry a larger dictionary.",
                    color = Color(0xFF6B9DFF).copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
