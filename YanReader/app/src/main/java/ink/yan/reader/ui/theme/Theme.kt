package ink.yan.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 砚 YanReader 主题。
 *
 * 配色刻意压低饱和度：液态玻璃的质感依赖背景透出，
 * 背景一花，玻璃就糊了，所以这里用大面积低饱和深色 + 少量点亮色。
 */

// 墨色基调（暗色）
private val InkBlack = Color(0xFF0B0D10)
private val InkSurface = Color(0xFF14171C)
private val InkSurfaceHi = Color(0xFF1C2027)
private val InkBorder = Color(0xFF2A2F38)

// 砚青 —— 主色，取砚台的青灰
private val YanCyan = Color(0xFF6FD3C7)
private val YanCyanDim = Color(0xFF3E8C84)

// 朱砂 —— 强调色，用于危险操作与下载中状态
private val ZhuSha = Color(0xFFE4796B)

private val DarkColors = darkColorScheme(
    primary = YanCyan,
    onPrimary = Color(0xFF06322E),
    primaryContainer = YanCyanDim,
    secondary = Color(0xFFB8C4D0),
    tertiary = ZhuSha,
    background = InkBlack,
    surface = InkSurface,
    surfaceVariant = InkSurfaceHi,
    outline = InkBorder,
    onBackground = Color(0xFFE6EAEF),
    onSurface = Color(0xFFE6EAEF),
    onSurfaceVariant = Color(0xFF9AA4B1),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F7B72),
    onPrimary = Color.White,
    secondary = Color(0xFF4A5560),
    tertiary = Color(0xFFB8503F),
    background = Color(0xFFF4F6F8),
    surface = Color.White,
    onBackground = Color(0xFF1A1D21),
    onSurface = Color(0xFF1A1D21),
)

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = YanTypography,
        content = content,
    )
}
