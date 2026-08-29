package ink.yan.reader.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃（Liquid Glass）材质。
 *
 * 说明一下做法的边界：Apple 的 Liquid Glass 依赖 Metal 层面的实时折射与
 * 镜面高光，Compose 拿不到那层能力。这里用「多层渐变叠绘」在 2D 上逼近质感：
 *
 *   1. 填充层  —— 顶亮底暗的垂直渐变，模拟玻璃本体的厚度不均
 *   2. 描边层  —— 顶亮底暗的渐变描边，模拟边缘折射
 *   3. 高光层  —— 顶部一道窄亮弧，模拟光源在玻璃上沿的反射
 *   4. 底部反光 —— 极淡的一条底边亮线，避免玻璃「沉」进背景
 *
 * 全程只用 drawWithCache，不创建额外 Layer，也不会触发
 * BackdropFilter 那种逐帧重采样 —— 中低端机上滚动 60fps 没问题。
 *
 * 如果需要「真实背景模糊」，见本文件末尾的说明，但请谨慎使用。
 */
@Stable
object LiquidGlassTokens {
    val FillTop = Color.White
    val FillBottom = Color.White
    val BorderTop = Color.White
    val BorderBottom = Color.White
    val Highlight = Color.White
}

fun Modifier.liquidGlass(
    corner: Dp = 22.dp,
    fillAlpha: Float = 0.10f,
    borderAlpha: Float = 0.34f,
    highlightAlpha: Float = 0.55f,
    elevationShadow: Boolean = true,
): Modifier = this.drawWithCache {
    val r = CornerRadius(corner.toPx())

    val fillBrush = Brush.verticalGradient(
        listOf(
            LiquidGlassTokens.FillTop.copy(alpha = fillAlpha),
            LiquidGlassTokens.FillBottom.copy(alpha = fillAlpha * 0.55f),
        )
    )
    val borderBrush = Brush.verticalGradient(
        listOf(
            LiquidGlassTokens.BorderTop.copy(alpha = borderAlpha),
            LiquidGlassTokens.BorderBottom.copy(alpha = borderAlpha * 0.28f),
        )
    )

    onDrawWithContent {
        // 0) 极淡的投影，让玻璃与背景分离
        if (elevationShadow) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.10f),
                cornerRadius = r,
                topLeft = Offset(0f, 2.dp.toPx()),
                size = Size(size.width, size.height),
            )
        }

        // 1) 玻璃本体
        drawRoundRect(
            brush = fillBrush,
            cornerRadius = r,
            size = Size(size.width, size.height),
        )

        // 2) 渐变描边（Stroke 模式下 Brush 依然生效）
        drawRoundRect(
            brush = borderBrush,
            cornerRadius = r,
            size = Size(size.width, size.height),
            style = Stroke(width = 1.2.dp.toPx()),
        )

        // 3) 顶部高光弧：只画上半部分，两端渐隐
        val hlH = 1.5.dp.toPx()
        val inset = corner.toPx() * 0.35f
        drawPath(
            path = Path().apply {
                moveTo(inset, hlH)
                lineTo(size.width - inset, hlH)
            },
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    LiquidGlassTokens.Highlight.copy(alpha = highlightAlpha),
                    Color.Transparent,
                )
            ),
            style = Stroke(
                width = hlH,
                pathEffect = PathEffect.cornerPathEffect(r.x.coerceAtLeast(1f)),
            ),
        )

        // 4) 底部反光
        drawPath(
            path = Path().apply {
                val y = size.height - 1.dp.toPx()
                moveTo(inset * 2, y)
                lineTo(size.width - inset * 2, y)
            },
            color = Color.White.copy(alpha = 0.10f),
            style = Stroke(width = 1.dp.toPx()),
        )

        drawContent()
    }
}

/**
 * 关于「真实背景模糊」：
 *
 * Compose 没有系统级的 backdrop blur 原语。真要做出 iOS 那种背景折射感，
 * 需要自己截屏背景再模糊（成本高、易掉帧），或者等官方支持。
 * 因此本项目统一使用 [liquidGlass] 的渐变叠绘方案。
 *
 * 如果你的目标机型集中在 Android 12+，可以按需叠加：
 *     Modifier.blur(radius, BlurredEdgeTreatment(RoundedCornerShape(corner)))
 * 但请注意 blur 会逐帧 GPU 重采样，滚动列表里大量使用会明显掉帧，
 * 只建议用在静态浮层（弹窗、详情页头部）上。
 */

/** 玻璃按钮。按下时略微加深填充，配合 spring 动画形成「按压回弹」的液态感。 */
object LiquidGlassDefaults {
    val PressedFillBoost = 0.06f
    val CornerLarge = 26.dp
    val CornerMedium = 18.dp
    val ContentPadding = 14.dp
}

fun Modifier.glassPressed(isPressed: Boolean, corner: Dp = 22.dp): Modifier =
    this.liquidGlass(
        corner = corner,
        fillAlpha = if (isPressed) 0.10f + LiquidGlassDefaults.PressedFillBoost else 0.10f,
    )
