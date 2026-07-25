package com.feixiaoqiu.fanqiedl.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    onConfirm: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    // 屏幕比例（竖屏约为 9:16 到 9:19.5）
    val cropAspect = 9f / 16f

    var viewSize by remember { mutableStateOf(IntSize(1, 1)) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 加载原图
    val bitmap = remember(imageUri) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap == null) {
        onCancel()
        return
    }

    // 计算裁剪框尺寸
    val cropWidth: Float
    val cropHeight: Float
    if (viewSize.width > 0 && viewSize.height > 0) {
        if (viewSize.width.toFloat() / viewSize.height > cropAspect) {
            cropHeight = viewSize.height.toFloat()
            cropWidth = cropHeight * cropAspect
        } else {
            cropWidth = viewSize.width.toFloat()
            cropHeight = cropWidth / cropAspect
        }
    } else {
        cropWidth = 1f
        cropHeight = 1f
    }

    val cropLeft = (viewSize.width - cropWidth) / 2f
    val cropTop = (viewSize.height - cropHeight) / 2f

    // 稳定的初始比例
    val initScale = if (bitmap.width > 0 && cropWidth > 1f) {
        cropWidth / bitmap.width.toFloat()
    } else 1f

    var initialized by remember { mutableStateOf(false) }
    if (!initialized && cropWidth > 1f && bitmap.width > 0) {
        initialized = true
        scale = initScale
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
    ) {
        // 可拖拽缩放的图片
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(
                            initScale * 0.5f,
                            max(initScale * 3f, 2f),
                        )
                        scale = newScale
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
        ) {
            val imgW = bitmap.width * scale
            val imgH = bitmap.height * scale

            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale / initScale,
                        scaleY = scale / initScale,
                        translationX = (offsetX) / initScale,
                        translationY = (offsetY) / initScale,
                    ),
            )
        }

        // 裁剪框遮罩
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 半透明遮罩
            val maskColor = Color.Black.copy(alpha = 0.6f)
            // 上方
            if (cropTop > 0) {
                drawRect(maskColor, topLeft = Offset.Zero, size = Size(size.width, cropTop))
            }
            // 下方
            val bottomStart = cropTop + cropHeight
            if (bottomStart < size.height) {
                drawRect(
                    maskColor,
                    topLeft = Offset(0f, bottomStart),
                    size = Size(size.width, size.height - bottomStart),
                )
            }
            // 左方
            if (cropLeft > 0) {
                drawRect(
                    maskColor,
                    topLeft = Offset(0f, cropTop),
                    size = Size(cropLeft, cropHeight),
                )
            }
            // 右方
            val rightStart = cropLeft + cropWidth
            if (rightStart < size.width) {
                drawRect(
                    maskColor,
                    topLeft = Offset(rightStart, cropTop),
                    size = Size(size.width - rightStart, cropHeight),
                )
            }
            // 裁剪框边框
            drawRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropWidth, cropHeight),
                style = Stroke(width = 2.dp.toPx()),
            )
            // 四角
            val cornerLen = 24.dp.toPx()
            val corners = listOf(
                Offset(cropLeft, cropTop), // 左上
                Offset(cropLeft + cropWidth, cropTop), // 右上
                Offset(cropLeft, cropTop + cropHeight), // 左下
                Offset(cropLeft + cropWidth, cropTop + cropHeight), // 右下
            )
            for (c in corners) {
                val hDir = if (c.x == cropLeft) 1f else -1f
                val vDir = if (c.y == cropTop) 1f else -1f
                drawLine(Color.White, c, Offset(c.x + hDir * cornerLen, c.y), strokeWidth = 3.dp.toPx())
                drawLine(Color.White, c, Offset(c.x, c.y + vDir * cornerLen), strokeWidth = 3.dp.toPx())
            }
        }

        // 底部按钮
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onCancel) {
                Text("取消", color = Color.White, fontSize = 16.sp)
            }
            TextButton(onClick = {
                // 裁剪
                val cropRect = Rect(
                    offset = Offset(-offsetX / scale + cropLeft / scale,
                        -offsetY / scale + cropTop / scale),
                    size = Size(cropWidth / scale, cropHeight / scale),
                )
                val left = cropRect.left.toInt().coerceIn(0, bitmap.width - 1)
                val top = cropRect.top.toInt().coerceIn(0, bitmap.height - 1)
                val w = cropRect.width.toInt().coerceIn(1, bitmap.width - left)
                val h = cropRect.height.toInt().coerceIn(1, bitmap.height - top)

                val cropped = try {
                    Bitmap.createBitmap(bitmap, left, top, w, h)
                } catch (_: Exception) {
                    bitmap
                }
                // 保存到缓存目录
                val file = File(context.cacheDir, "bg_crop_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                onConfirm(Uri.fromFile(file))
            }) {
                Text("确定", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 顶部提示
        Text(
            "拖动缩放图片，调整裁剪区域",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )
    }
}
