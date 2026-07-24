package com.feixiaoqiu.fanqiedl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgBlack = Color(0xFF0A0A0A)
val CardWhite = Color(0xFFF7F7F7)
val TextPrimary = Color(0xFF111111)
val TextSecondary = Color(0xFF666666)
val Accent = Color(0xFF222222)
val Scrim = Color(0x99000000)

private val Scheme = darkColorScheme(
    primary = CardWhite,
    onPrimary = TextPrimary,
    secondary = TextSecondary,
    background = BgBlack,
    surface = CardWhite,
    onSurface = TextPrimary,
    onBackground = CardWhite,
)

@Composable
fun FanqieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content,
    )
}
