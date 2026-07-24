package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feixiaoqiu.fanqiedl.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 开屏：墨迹晕开 + 笔触轨迹 + 标题逐字落墨，约 3 秒。
 * 与常见「转圈 + 脉冲 logo」区分，偏水墨质感。
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    val ink = remember { Animatable(0f) }
    val stroke = remember { Animatable(0f) }
    val titleReveal = remember { Animatable(0f) }
    val taglineReveal = remember { Animatable(0f) }
    val fadeOut = remember { Animatable(1f) }

    val infinite = rememberInfiniteTransition(label = "mist")
    val mist by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mistPhase",
    )
    val breath by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val seeds = remember {
        List(28) {
            floatArrayOf(
                Random.nextFloat(),
                Random.nextFloat(),
                0.4f + Random.nextFloat() * 0.6f,
                Random.nextFloat() * 360f,
            )
        }
    }

    LaunchedEffect(Unit) {
        launch {
            ink.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            delay(280)
            stroke.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        }
        launch {
            delay(520)
            titleReveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(980)
            taglineReveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        delay(2600)
        fadeOut.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        onFinished()
    }

    val title = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.app_tagline)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fadeOut.value)
            .graphicsLayer {
                scaleX = breath
                scaleY = breath
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // rice-paper base
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF14151C), Color(0xFF0B0C10), Color(0xFF1A1520)),
                ),
            )
            // soft vermilion corner wash
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55FF4757), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.22f),
                    radius = size.minDimension * 0.55f * (0.55f + ink.value * 0.45f),
                ),
                center = Offset(size.width * 0.18f, size.height * 0.22f),
                radius = size.minDimension * 0.55f * (0.55f + ink.value * 0.45f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x334A5DFF), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.78f),
                    radius = size.minDimension * 0.5f,
                ),
                center = Offset(size.width * 0.82f, size.height * 0.78f),
                radius = size.minDimension * 0.5f,
            )

            val cx = size.width * 0.5f
            val cy = size.height * 0.38f
            val maxR = size.minDimension * 0.34f
            // multi-layer ink blot
            for (i in 0 until 5) {
                val t = (ink.value - i * 0.08f).coerceIn(0f, 1f)
                if (t <= 0f) continue
                val rr = maxR * (0.35f + t * (0.55f + i * 0.08f))
                drawCircle(
                    color = Color.White.copy(alpha = (0.08f - i * 0.012f) * t),
                    radius = rr,
                    center = Offset(cx + sin(i * 0.7f) * 12f, cy + cos(i * 0.5f) * 10f),
                )
            }

            // brush stroke path (horizontal seal-like curve under title area)
            val path = Path().apply {
                val y = size.height * 0.52f
                val left = size.width * 0.18f
                val right = size.width * 0.82f
                val mid = (left + right) / 2f
                moveTo(left, y)
                cubicTo(
                    left + (right - left) * 0.25f, y - 36f * stroke.value,
                    mid, y + 42f * stroke.value,
                    right, y - 8f,
                )
            }
            drawPath(
                path = path,
                color = Color(0xFFFF4757).copy(alpha = 0.55f * stroke.value),
                style = Stroke(
                    width = (3.2f + 4f * stroke.value) * density,
                    cap = StrokeCap.Round,
                ),
            )
            // secondary dry-brush echo
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.12f * stroke.value),
                style = Stroke(width = 1.2f * density, cap = StrokeCap.Round),
            )

            // floating ink flecks / paper fiber
            seeds.forEachIndexed { idx, s ->
                val px = s[0] * size.width
                val baseY = s[1] * size.height
                val speed = s[2]
                val rot = s[3]
                val phase = (mist + idx * 0.037f) % 1f
                val py = (baseY + phase * 80f * speed) % (size.height + 40f) - 20f
                val a = 0.06f + 0.1f * sin((phase + idx) * PI * 2).toFloat().let { (it + 1f) / 2f }
                rotate(degrees = rot + phase * 40f, pivot = Offset(px, py)) {
                    drawCircle(
                        color = Color.White.copy(alpha = a * ink.value),
                        radius = (1.2f + s[2] * 2.4f) * density,
                        center = Offset(px, py),
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            // staggered title characters
            Box {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.12f),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 14.sp,
                )
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.15f + 0.85f * titleReveal.value),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 14.sp,
                    modifier = Modifier.graphicsLayer {
                        translationY = (1f - titleReveal.value) * 28f
                        // slight ink "press"
                        scaleX = 0.92f + 0.08f * titleReveal.value
                        scaleY = 0.92f + 0.08f * titleReveal.value
                    },
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = tagline,
                color = Color.White.copy(alpha = 0.15f + 0.7f * taglineReveal.value),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - taglineReveal.value) * 16f
                },
            )
        }
    }
}
