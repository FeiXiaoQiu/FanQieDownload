package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feixiaoqiu.fanqiedl.R
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    val fadeIn by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "splashFade",
    )
    val infinite = rememberInfiniteTransition(label = "splashMotion")
    val pulse by infinite.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )
    val floatY by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatY",
    )
    val shimmer by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )

    LaunchedEffect(Unit) {
        delay(3000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0C10), BgBlack, Color(0xFF161821)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.55f),
        ) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val r = size.minDimension * 0.18f * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Primary.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 2.2f,
                ),
                radius = r * 2.2f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Primary.copy(alpha = 0.55f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f * density, cap = StrokeCap.Round),
            )
            val rad = Math.toRadians(orbit.toDouble())
            val ox = cx + cos(rad).toFloat() * r * 1.25f
            val oy = cy + sin(rad).toFloat() * r * 1.25f
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 5f * density, center = Offset(ox, oy))
            drawCircle(color = Primary.copy(alpha = 0.9f), radius = 3f * density, center = Offset(ox, oy))
            // soft ink strokes
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(cx - r * 1.8f, cy + r * 1.6f),
                end = Offset(cx + r * 1.8f, cy + r * 1.6f),
                strokeWidth = 1.2f * density,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(fadeIn)
                .offset(y = floatY.dp)
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    scaleX = 0.97f + (pulse - 0.86f) * 0.15f
                    scaleY = 0.97f + (pulse - 0.86f) * 0.15f
                },
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                color = Color.White.copy(alpha = 0.55f + shimmer * 0.35f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                letterSpacing = 1.2.sp,
            )
            Spacer(modifier = Modifier.height(36.dp))
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 2.dp)
                    .background(Primary.copy(alpha = 0.4f + shimmer * 0.5f)),
            )
        }
    }
}
