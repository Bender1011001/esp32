package com.chimera.red.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.chimera.red.ChimeraRepository
import com.chimera.red.RetroGreen
import com.chimera.red.data.CaptureEntity
import com.chimera.red.ui.theme.Dimens
import com.chimera.red.utils.PcapUtils
import com.chimera.red.utils.CrackingEngine
import androidx.compose.material.icons.filled.Terminal
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LootScreen() {
    val captures = ChimeraRepository.captures
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Cracking State
    var selectedCapture by remember { mutableStateOf<CaptureEntity?>(null) }
    var showCrackDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpacingMd)
    ) {
        // ... (Header text)
        Text(
            text = "LOOT GALLERY",
            style = MaterialTheme.typography.titleMedium,
            color = RetroGreen,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = Dimens.SpacingMd)
        )

        if (captures.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO CAPTURES YET", color = RetroGreen.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)) {
                items(captures) { capture ->
                    CaptureCard(
                        capture = capture, 
                        onDelete = { ChimeraRepository.deleteCapture(capture.id) },
                        onExport = {
                            val fileName = "capture_${capture.ssid ?: "unknown"}_${capture.timestamp}.pcap"
                            val file = File(context.cacheDir, fileName)
                            PcapUtils.saveAsPcap(file, capture.data)
                            
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export PCAP"))
                        },
                        onCrack = {
                            selectedCapture = it
                            showCrackDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showCrackDialog && selectedCapture != null) {
        CrackingDialog(
            ssid = selectedCapture?.ssid ?: "Unknown",
            onDismiss = { showCrackDialog = false }
        )
    }
}

@Composable
fun CaptureCard(capture: CaptureEntity, onDelete: () -> Unit, onExport: () -> Unit, onCrack: (CaptureEntity) -> Unit) {
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = capture.type.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = RetroGreen.copy(alpha = 0.7f)
                )
                Text(
                    text = capture.ssid ?: capture.bssid ?: "Unknown Source",
                    fontWeight = FontWeight.Bold,
                    color = RetroGreen
                )
                Text(
                    text = df.format(Date(capture.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (capture.channel != null) {
                    Text(
                        text = "Channel: ${capture.channel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RetroGreen.copy(alpha = 0.6f)
                    )
                }
            }
            
            if (capture.type == "WIFI_HANDSHAKE") {
                IconButton(onClick = { onCrack(capture) }) {
                    Icon(Icons.Default.Terminal, contentDescription = "Crack", tint = RetroGreen)
                }
            }
            
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Share, contentDescription = "Export", tint = RetroGreen)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun CrackingDialog(ssid: String, onDismiss: () -> Unit) {
    var candidate by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    var isDone by remember { mutableStateOf(false) }

    LaunchedEffect(ssid) {
        result = CrackingEngine.runDictionaryAttack(ssid) { current, currentIdx, total, hps ->
            candidate = current
            progress = currentIdx.toFloat() / total
            speed = hps
        }
        isDone = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        title = { Text("CHIMERA CRACKER v1.0", color = RetroGreen) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("TARGET: $ssid", color = RetroGreen.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                
                if (!isDone) {
                    Text("DERIVING PMK...", color = RetroGreen)
                    Text("PASS: $candidate", color = Color.White, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progress, 
                        modifier = Modifier.fillMaxWidth(),
                        color = RetroGreen
                    )
                    Text("SPEED: $speed H/S (CPU)", color = RetroGreen, fontSize = 10.sp)
                } else {
                    if (result != null) {
                        Text("KEY FOUND!", color = RetroGreen, fontWeight = FontWeight.Bold)
                        Text("RESULT: $result", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("(Valid PMK Derived)", color = RetroGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                    } else {
                        Text("PASSWORD NOT IN WORDLIST", color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isDone) "CLOSE" else "CANCEL", color = RetroGreen)
            }
        }
    )
}
