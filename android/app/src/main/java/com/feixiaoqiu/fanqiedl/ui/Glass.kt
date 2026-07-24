package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GlassFill = Color(0xE6FFFFFF)
val GlassFillSoft = Color(0xCCFFFFFF)
val GlassBorder = Color(0x66FFFFFF)
val GlassHighlight = Color(0x33FFFFFF)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    fill: Color = GlassFill,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(fill, fill.copy(alpha = fill.alpha * 0.92f)),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.55f), GlassBorder),
                ),
                shape = shape,
            ),
        content = content,
    )
}
