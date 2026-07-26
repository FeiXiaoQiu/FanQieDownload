package com.feixiaoqiu.fanqiedl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val BgBlack = Color(0xFF0A0A0A)
val CardWhite = Color(0xFFFFFFFF)
val CardMuted = Color(0xFFF2F3F5)
val TextPrimary = Color(0xFF111111)
val TextSecondary = Color(0xFF555555)
val Accent = Color(0xFF3A3A48)
val Scrim = Color(0x99000000)
val Primary = Color(0xFFC08860)
val PrimaryDark = Color(0xFFA07050)
val InputBg = Color(0x18FFFFFF)
val InputBorder = Color(0x33FFFFFF)
val Placeholder = Color(0x66FFFFFF)
val OnDark = Color(0xFFFFFFFF)
val GlassText = Color(0xFFFFFFFF)
val GlassTextSecondary = Color(0xB3FFFFFF)

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
    // 禁用系统字体缩放：简易模式、大字体下仍按设计尺寸渲染，避免换行/溢出
    val density = LocalDensity.current
    val fixedDensity = if (density.fontScale != 1.0f)
        Density(density = density.density, fontScale = 1.0f)
    else density
    MaterialTheme(
        colorScheme = Scheme,
        content = {
            CompositionLocalProvider(LocalDensity provides fixedDensity) {
                content()
            }
        },
    )
}
