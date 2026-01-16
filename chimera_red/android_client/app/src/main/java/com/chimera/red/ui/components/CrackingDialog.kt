package com.chimera.red.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chimera.red.RetroGreen
import com.chimera.red.utils.CrackingEngine

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
