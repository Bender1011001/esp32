package com.chimera.red

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Planet Express Theme Colors - Futurama Aesthetic
val PipelineBlack = Color(0xFF000B00) // Very dark green-black
val RetroGreen = Color(0xFF4BA383)   // Planet Express Ship Green
val DarkGreen = Color(0xFF1B3B2F)    // Complementary dark green

// Bender OS Colors
val BenderGray = Color(0xFFAAAAAA)
val ShinyMetal = Brush.linearGradient(
    colors = listOf(Color(0xFFE0E0E0), Color(0xFF888888), Color(0xFFE0E0E0)),
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(100f, 100f)
)
val BeerYellow = Color(0xFFFFD700)
val PlanetTeal = Color(0xFF00AA9E)
val CigarRed = Color(0xFFFF4500)

val BenderQuotes = listOf(
    "Bite my shiny metal app!",
    "I'm 40% radio hacker!",
    "Shut up baby, I know it!",
    "Kill all humans! (Except Fry)",
    "Hooray! I'm useful!",
    "Good news, everyone!",
    "I'll build my own network! With blackjack! And hookers!"
)

@Composable
fun ChimeraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RetroGreen,
            background = PipelineBlack,
            surface = PipelineBlack,
            onPrimary = PipelineBlack,
            onBackground = RetroGreen,
            onSurface = RetroGreen
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, color = RetroGreen),
            bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, color = RetroGreen),
            titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = RetroGreen)
        ),
        content = content
    )
}
