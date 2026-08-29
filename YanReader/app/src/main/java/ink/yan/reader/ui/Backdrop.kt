package ink.yan.reader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import ink.yan.reader.data.BackgroundScale
import ink.yan.reader.data.StylePreset
import ink.yan.reader.store.BackgroundPrefs
import java.io.File

/**
 * 全屏背景。
 *
 * 「完整显示」模式下没有直接套 ContentScale.Fit —— 那样两侧会露出底色，
 * 在有背景图时非常扎眼。这里用同一张图叠两层：底层 Crop 铺满并模糊当填充，
 * 上层 Fit 保持完整比例，效果比两侧留白好得多。
 *
 * 模糊只作用在这张静止的背景上，不会随列表滚动重绘，
 * 所以不用担心 Modifier.blur 的逐帧开销。
 */
@Composable
fun YanBackdrop(
    prefs: BackgroundPrefs,
    preset: StylePreset,
    resolvedUrl: String?,
    modifier: Modifier = Modifier,
) {
    val model: Any? = when {
        prefs.localPath.isNotBlank() -> File(prefs.localPath).takeIf { it.isFile }
        !resolvedUrl.isNullOrBlank() -> resolvedUrl
        else -> null
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (model != null) {
            when (prefs.scale) {
                BackgroundScale.FIT -> FitLayer(model, prefs.blurDp)
                BackgroundScale.CROP -> CropLayer(model, prefs.blurDp)
            }
        }
        // 遮罩：保证玻璃上的文字始终读得清。浓度由用户调。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(preset.ink).copy(alpha = prefs.scrimAlpha.coerceIn(0f, 0.9f))),
        )
    }
}

@Composable
private fun FitLayer(model: Any, blurDp: Float) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        success = { state ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 底层：铺满 + 模糊，充当留白处的延续
                Image(
                    painter = state.painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurDp.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
                // 上层：完整比例
                Image(
                    painter = state.painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                )
            }
        },
        // 加载中与失败都不画，剩下底色兜底，比显示一个错误图标干净
        error = {},
        loading = {},
    )
}

@Composable
private fun CropLayer(model: Any, blurDp: Float) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .blur(blurDp.dp),
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        error = {},
        loading = {},
    )
}
