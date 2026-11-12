package com.example.foxy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

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

object VeilColors {
    val Background = Color(0xFFF5F8F8)
    val HeaderTint = Color(0xFFD3F1EA)
    val Ink = Color(0xFF1E2128)
    val Muted = Color(0xFF6B6F78)
    val Accent = Color(0xFF4CC9B0)
    val AccentText = Color(0xFF0E7466)
    val AccentTint = Color(0xFFD9F3EC)
    val StatDownload = Color(0xFFDDF4EE)
    val StatUpload = Color(0xFFF0EFEA)
    val NavBar = Color(0xFF1C2A2D)
    val NavInactive = Color(0xFFB9BDC6)
    val Border = Color(0xFFE6E4DF)
    val SignalOn = Color(0xFF5E9B6E)
    val SignalOff = Color(0xFFE0D2C5)
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

@Composable
fun VeilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VeilColors.Accent,
            onPrimary = VeilColors.Ink,
            background = VeilColors.Background,
            onBackground = VeilColors.Ink,
            surface = Color.White,
            onSurface = VeilColors.Ink,
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 21.sp),
        ),
        content = content,
    )
}
