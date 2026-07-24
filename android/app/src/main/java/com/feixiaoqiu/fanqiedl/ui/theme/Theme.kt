package com.feixiaoqiu.fanqiedl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgBlack = Color(0xFF0A0A0A)
val CardWhite = Color(0xFFFFFFFF)
val CardMuted = Color(0xFFF2F3F5)
val TextPrimary = Color(0xFF111111)
val TextSecondary = Color(0xFF555555)
val Accent = Color(0xFF222222)
val Scrim = Color(0x99000000)
val Primary = Color(0xFFFF4757)
val PrimaryDark = Color(0xFFFF3838)
val InputBg = Color(0xFFF7F8FA)
val InputBorder = Color(0xFFE0E0E0)
val Placeholder = Color(0xFF888888)
val OnDark = Color(0xFFFFFFFF)

/** 浅色 scheme：白卡片/对话框内文字为深色，避免白底白字 */
private val Scheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = TextSecondary,
    onSecondary = Color.White,
    background = BgBlack,
    onBackground = OnDark,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = CardMuted,
    onSurfaceVariant = TextSecondary,
    outline = InputBorder,
)

@Composable
fun FanqieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content,
    )
}
