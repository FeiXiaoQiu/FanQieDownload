package com.feixiaoqiu.lookapp.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.feixiaoqiu.lookapp.data.Resolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private val BgBlack = Color(0xFF0A0A0A)
private val Surface = Color(0xFF151515)
private val Primary = Color(0xFFC08860)
private val TextMuted = Color(0xFF999999)
private val TextDim = Color(0xFF666666)
private val Placeholder = Color(0xFF1E1E1E)
private val ErrorRed = Color(0xFFFF5252)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InspectorScreen(
    resolver: Resolver,
    onSaveBytes: (String, ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var urls by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreenIndex by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    var saveDone by remember { mutableIntStateOf(0) }
    var saveTotal by remember { mutableIntStateOf(0) }
    var saveProgress by remember { mutableFloatStateOf(0f) }

    val http = resolver.http

    fun doParse() {
        if (url.isBlank() || loading) return
        loading = true; error = null
        scope.launch {
            urls = resolver.resolve(url)
            loading = false
            if (urls.isEmpty()) error = "未解析到图片"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("观察", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("v2607271940", color = TextDim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (urls.isNotEmpty() && !saving) {
                    TextButton(onClick = {
                        saving = true
                        saveDone = 0
                        saveTotal = urls.size
                        saveProgress = 0f
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val batch = urls.chunked(4)
                                for (chunk in batch) {
                                    chunk.map { u ->
                                        async {
                                            try {
                                                val req = Request.Builder().url(u).get().build()
                                                val bytes = http.newCall(req).execute().use { resp ->
                                                    if (!resp.isSuccessful) return@async
                                                    resp.body?.bytes()
                                                }
                                                if (bytes != null) {
                                                    onSaveBytes("img_${System.currentTimeMillis()}_${u.hashCode()}.jpg", bytes)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }.awaitAll()
                                    saveDone += chunk.size
                                    saveProgress = saveDone.toFloat() / saveTotal
                                }
                            }
                            saving = false
                            Toast.makeText(context, "已保存 $saveDone/$saveTotal 张到 Download/Look", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.SaveAlt, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("全部保存", color = Primary, fontSize = 13.sp)
                    }
                }
            }

            if (saving) {
                LinearProgressIndicator(
                    progress = { saveProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Primary,
                    trackColor = Color(0xFF333333),
                )
                Text(
                    "保存中 $saveDone/$saveTotal",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // 输入行
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入 JSON 图源地址", color = Color(0xFF555555), fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Primary,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions { doParse() },
                    trailingIcon = {
                        if (url.isNotBlank()) {
                            IconButton(onClick = { url = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, "清空", tint = TextDim)
                            }
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        clipboard.getText()?.let { url = it.toString(); error = null }
                    },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(Icons.Default.ContentPaste, "粘贴", tint = TextMuted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { doParse() },
                    enabled = url.isNotBlank() && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, "解析", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("解析", color = Color.White, fontSize = 13.sp)
                }
            }

            if (error != null) {
                Text(
                    error!!,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (urls.isNotEmpty()) {
                Text(
                    "共 ${urls.size} 张",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            } else if (urls.isEmpty() && !loading && error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF333333), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("输入 JSON 图源链接，点击解析", color = Color(0xFF444444), fontSize = 14.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(urls, key = { _, imgUrl -> imgUrl }) { index, imgUrl ->
                        val req = remember(imgUrl) {
                            ImageRequest.Builder(context)
                                .data(imgUrl)
                                .crossfade(false)
                                .size(320)
                                .memoryCacheKey(imgUrl)
                                .build()
                        }
                        SubcomposeAsyncImage(
                            model = req,
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(6.dp))
                                .combinedClickable(
                                    onClick = { fullscreenIndex = index },
                                    onLongClick = {
                                        scope.launch {
                                            try {
                                                val r = Request.Builder().url(imgUrl).get().build()
                                                val bytes = http.newCall(r).execute().use { it.body?.bytes() }
                                                if (bytes != null) {
                                                    onSaveBytes("img_${System.currentTimeMillis()}_${index}.jpg", bytes)
                                                    withContext(Dispatchers.Main) { Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() }
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    },
                                ),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(Modifier.fillMaxSize().background(Placeholder), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = TextDim, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            },
                            error = {
                                Box(Modifier.fillMaxSize().background(Placeholder), contentAlignment = Alignment.Center) {
                                    Text("失败", color = TextDim, fontSize = 10.sp)
                                }
                            },
                        )
                    }
                }
            }
        }

        // 全屏查看
        AnimatedVisibility(visible = fullscreenIndex >= 0, enter = fadeIn(), exit = fadeOut()) {
            if (fullscreenIndex in urls.indices) {
                FullscreenView(
                    imageUrl = urls[fullscreenIndex],
                    index = fullscreenIndex,
                    total = urls.size,
                    onClose = { fullscreenIndex = -1 },
                    onSave = {
                        scope.launch {
                            try {
                                val r = Request.Builder().url(urls[fullscreenIndex]).get().build()
                                val bytes = http.newCall(r).execute().use { it.body?.bytes() }
                                if (bytes != null) {
                                    onSaveBytes("img_${System.currentTimeMillis()}_${fullscreenIndex}.jpg", bytes)
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() }
                                }
                            } catch (_: Exception) {}
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FullscreenView(
    imageUrl: String,
    index: Int,
    total: Int,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .clickable { onClose() },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(false).build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            contentScale = ContentScale.Fit,
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Text("${index + 1} / $total", color = TextMuted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave) { Icon(Icons.Default.SaveAlt, "保存", tint = Primary) }
        }
    }
}
