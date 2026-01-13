package com.chimera.red.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.chimera.red.ui.theme.Dimens

@Composable
fun TerminalConsole(logs: List<String>) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(Dimens.SpacingSm)
    ) {
        items(logs.reversed()) { log ->
            Text(
                text = "[${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}] $log",
                color = if (log.contains("error", true)) Color.Red else Color(0xFF00FF41),
                fontFamily = FontFamily.Monospace,
                fontSize = Dimens.TextBody
            )
            Divider(color = Color.DarkGray, thickness = Dimens.BorderHairline)
        }
    }
}
