package com.chimera.red.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import com.chimera.red.UsbSerialManager
import com.chimera.red.RetroGreen
import com.chimera.red.ui.theme.Dimens

@Composable
fun TerminalView(logs: List<String>, serialManager: UsbSerialManager) {
    val listState = rememberLazyListState()
    
    // Auto scroll
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(Dimens.SpacingMd)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(Dimens.BorderThin, RetroGreen.copy(alpha = 0.3f), RectangleShape)
                .background(Color.Black)
                .padding(Dimens.SpacingSm)
        ) {
            items(logs) { log ->
                Text(
                    text = "> $log", 
                    color = RetroGreen, 
                    fontFamily = FontFamily.Monospace, 
                    fontSize = Dimens.TextBody
                )
            }
        }
        
        var cmd by remember { mutableStateOf("") }
        Row(Modifier.padding(top = Dimens.SpacingSm)) {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RetroGreen,
                    unfocusedBorderColor = RetroGreen.copy(alpha = 0.5f),
                    focusedTextColor = RetroGreen, 
                    unfocusedTextColor = RetroGreen
                ),
                singleLine = true
            )
            Spacer(Modifier.width(Dimens.SpacingSm))
            Button(
                onClick = { 
                    serialManager.send(cmd)
                    cmd = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = RetroGreen),
                shape = RectangleShape
            ) {
                Text("SEND")
            }
        }
    }
}
