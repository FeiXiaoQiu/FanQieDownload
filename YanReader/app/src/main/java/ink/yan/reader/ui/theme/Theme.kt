package ink.yan.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ink.yan.reader.data.Appearance
import ink.yan.reader.data.StylePreset
import ink.yan.reader.ui.GlassStyle
import ink.yan.reader.ui.LocalGlassStyle

/**
 * 砚 YanReader 主题。
 *
 * 配色刻意压低饱和度：液态玻璃的质感依赖背景透出，
 * 背景一花，玻璃就糊了，所以这里用大面积低饱和底色 + 少量点亮色。
 *
 * 整套颜色来自 [Appearance.preset]，不再硬编码 —— 换风格包时配色与玻璃
 * 参数必须一起变，否则会出现「换成浅色但玻璃本色还是白的」这种割裂。
 */

private val YanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)

@Composable
fun YanTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val p = appearance.preset
    MaterialTheme(
        colorScheme = if (p.lightContent) lightOf(p) else darkOf(p),
        typography = YanTypography,
    ) {
        // 玻璃参数随配色一起下发，子组件用 Modifier.glass() 即自动跟随
        CompositionLocalProvider(
            LocalGlassStyle provides GlassStyle.from(appearance),
            content = content,
        )
    }
}

private fun darkOf(p: StylePreset) = darkColorScheme(
    primary = Color(p.accent),
    onPrimary = Color(p.onAccent),
    primaryContainer = Color(p.accentDim),
    secondary = Color(p.onInkMuted),
    tertiary = Color(p.accent),
    background = Color(p.ink),
    surface = Color(p.surface),
    surfaceVariant = Color(p.surfaceHi),
    outline = Color(p.border),
    onBackground = Color(p.onInk),
    onSurface = Color(p.onInk),
    onSurfaceVariant = Color(p.onInkMuted),
)

private fun lightOf(p: StylePreset) = lightColorScheme(
    primary = Color(p.accent),
    onPrimary = Color(p.onAccent),
    primaryContainer = Color(p.accentDim),
    secondary = Color(p.onInkMuted),
    tertiary = Color(p.accent),
    background = Color(p.ink),
    surface = Color(p.surface),
    surfaceVariant = Color(p.surfaceHi),
    outline = Color(p.border),
    onBackground = Color(p.onInk),
    onSurface = Color(p.onInk),
    onSurfaceVariant = Color(p.onInkMuted),
)
