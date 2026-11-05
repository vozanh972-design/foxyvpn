package com.example.foxy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FoxyColors {
    val Violet = Color(0xFF2A1086)
    val Violet2 = Color(0xFF4A22C9)
    val VioletDeep = Color(0xFF150647)
    val Glow = Color(0xFF8264FF)
    val Sun = Color(0xFFFFD23F)
    val Mint = Color(0xFF5DF2A5)
    val Sheet = Color(0xFFF8F6FF)
    val Ink = Color(0xFF1A0F45)
    val Muted = Color(0xFF7A70A8)
    val Line = Color(0xFFE4DEF8)
    val PingChip = Color(0xFFECE6FF)
    val Glass = Color(0x24FFFFFF)
    val DotOff = Color(0xFFFF8A7A)
    val DotOn = Color(0xFF7DFFB3)
}

@Composable
fun FoxyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FoxyColors.Violet2,
            onPrimary = Color.White,
            background = FoxyColors.Sheet,
            surface = FoxyColors.Sheet,
        ),
        content = content,
    )
}
