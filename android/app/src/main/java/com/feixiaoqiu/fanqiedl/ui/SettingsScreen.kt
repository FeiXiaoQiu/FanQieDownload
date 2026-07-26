package com.feixiaoqiu.fanqiedl.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.feixiaoqiu.fanqiedl.data.BackgroundMode
import com.feixiaoqiu.fanqiedl.data.BackgroundScale
import com.feixiaoqiu.fanqiedl.data.CustomBackground
import com.feixiaoqiu.fanqiedl.data.DefaultNodes
import com.feixiaoqiu.fanqiedl.data.DownloadSource
import com.feixiaoqiu.fanqiedl.data.NodeConfig
import com.feixiaoqiu.fanqiedl.data.ReleaseAsset
import com.feixiaoqiu.fanqiedl.ui.theme.Accent
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.GlassFill
import com.feixiaoqiu.fanqiedl.ui.theme.CardMuted
import com.feixiaoqiu.fanqiedl.ui.theme.GlassText
import com.feixiaoqiu.fanqiedl.ui.theme.GlassTextSecondary
import com.feixiaoqiu.fanqiedl.ui.theme.OnDark
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import com.feixiaoqiu.fanqiedl.ui.theme.Scrim
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState
import com.feixiaoqiu.fanqiedl.viewmodel.NodeProbeInfo
import com.feixiaoqiu.fanqiedl.viewmodel.NodeProbePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File

@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onRestore: () -> Unit,
    onProbeAll: () -> Unit,
    onHitokotoUrlChange: (String) -> Unit,
    onSaveHitokoto: () -> Unit,
    onTestHitokoto: () -> Unit,
    onBgModeChange: (BackgroundMode) -> Unit,
    onBgApiChange: (String) -> Unit,
    onSaveBackground: () -> Unit,
    onRefreshBackground: () -> Unit,
    onPickLocalBackground: (Uri) -> Unit,
    onClearLocalBackground: () -> Unit,
    onAddCustomBg: (String, String) -> Unit = { _, _ -> },
    onRemoveCustomBg: (String) -> Unit = {},
    onUpdateCustomBg: (String, String, String) -> Unit = { _, _, _ -> },
    onSelectCustomBg: (String) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onOpenRepo: () -> Unit = {},
    r18Accepted: Boolean = false,
    onAcceptR18: () -> Unit = {},
    onAddDownloadSource: (String, String) -> Unit = { _, _ -> },
    onRemoveDownloadSource: (String) -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onBackgroundScaleChange: (BackgroundScale) -> Unit = {},
    onBackgroundBlurChange: (Float) -> Unit = {},
    onResetMirrors: () -> Unit = {},
    onToggleAutoUpdateCheck: () -> Unit = {},
) {
    var newUrl by remember { mutableStateOf("") }
    var showR18Dialog by remember { mutableStateOf(false) }
    var showAddDownloadSource by remember { mutableStateOf(false) }
    var showHiddenFeatureQuery by remember { mutableStateOf(false) }
    var showHiddenAlreadyEnabled by remember { mutableStateOf(false) }
    var toastTick by remember { mutableIntStateOf(0) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) cropUri = uri
    }

    // 裁剪对话框
    if (cropUri != null) {
        ImageCropDialog(
            imageUri = cropUri!!,
            onConfirm = { croppedUri ->
                cropUri = null
                onPickLocalBackground(croppedUri)
            },
            onCancel = { cropUri = null },
        )
    }

    // 进入设置时自动测速
    LaunchedEffect(Unit) {
        onProbeAll()
    }

    val bgModel: Any? = remember(state.backgroundDisplayUrl) {
        val p = state.backgroundDisplayUrl
        when {
            p.isBlank() -> null
            p.startsWith("http://") || p.startsWith("https://") ||
                p.startsWith("file://") || p.startsWith("content://") -> p
            else -> {
                val f = java.io.File(p)
                if (f.isFile) Uri.fromFile(f) else null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        if (bgModel != null) {
            if (state.backgroundScale == BackgroundScale.FIT) {
                AsyncImage(
                    model = bgModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(
                            if (state.backgroundMode == BackgroundMode.CUSTOM_IMAGE)
                                state.backgroundBlur.dp else 24.dp
                        ),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
                AsyncImage(
                    model = bgModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                )
            } else {
                AsyncImage(
                    model = bgModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Scrim))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text("设置", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(title = "背景") {
                    // 第1行：缩放（同时作用于接口和本地）
                    Text("缩放：", color = GlassTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BackgroundScale.entries.forEach { scale ->
                            Row(
                                modifier = Modifier
                                    .selectable(
                                        selected = state.backgroundScale == scale,
                                        onClick = { onBackgroundScaleChange(scale) },
                                        role = Role.RadioButton,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.backgroundScale == scale,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Primary),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(scale.label, color = GlassText, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // 接口背景区域
                    Column(Modifier.selectableGroup()) {
                        BgOption(
                            selected = state.backgroundMode == BackgroundMode.DEFAULT,
                            title = "栗次元图床",
                            subtitle = DefaultNodes.DEFAULT_BACKGROUND_API,
                            onClick = { onBgModeChange(BackgroundMode.DEFAULT) },
                        )
                        if (state.r18HiddenEnabled) {
                        BgOption(
                            selected = state.backgroundMode == BackgroundMode.R18,
                            title = "妖狐R18（慎用）",
                            subtitle = DefaultNodes.R18_BACKGROUND_API,
                            onClick = {
                                if (state.r18Accepted) {
                                    onBgModeChange(BackgroundMode.R18)
                                } else {
                                    showR18Dialog = true
                                }
                            },
                        )
                        }
                        state.customBackgrounds.forEach { cbg ->
                            CustomBgRow(
                                cbg = cbg,
                                selected = state.backgroundMode == BackgroundMode.CUSTOM_API &&
                                    state.selectedCustomBgId == cbg.id,
                                onSelect = { onSelectCustomBg(cbg.id) },
                                onUpdate = { name, url -> onUpdateCustomBg(cbg.id, name, url) },
                                onRemove = { onRemoveCustomBg(cbg.id) },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = false,
                                    onClick = { onAddCustomBg("", "") },
                                    role = Role.Button,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("＋ 新增接口", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (state.backgroundMode != BackgroundMode.CUSTOM_IMAGE) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRefreshBackground) {
                                Text("换一张", color = Primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // 本地图片区域
                    BgOption(
                        selected = state.backgroundMode == BackgroundMode.CUSTOM_IMAGE,
                        title = "本地图片",
                        subtitle = "从相册选择，自动裁剪适配",
                        onClick = { onBgModeChange(BackgroundMode.CUSTOM_IMAGE) },
                    )
                    if (state.backgroundMode == BackgroundMode.CUSTOM_IMAGE) {
                        val hasLocal = state.backgroundImageUrl.isNotBlank() ||
                            state.backgroundDisplayUrl.isNotBlank()
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (hasLocal) "已选择本地图片" else "尚未选择图片",
                            color = GlassTextSecondary,
                            fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pickImage.launch("image/*") },
                                colors = primaryBtn(),
                            ) { Text("选择图片") }
                            if (hasLocal) {
                                TextButton(onClick = onClearLocalBackground) {
                                    Text("清除", color = Primary)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("模糊：", color = GlassTextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = state.backgroundBlur.coerceIn(0f, 48f),
                                onValueChange = onBackgroundBlurChange,
                                valueRange = 0f..48f,
                                steps = 47,
                                colors = SliderDefaults.colors(
                                    thumbColor = Primary,
                                    activeTrackColor = Primary,
                                    inactiveTrackColor = CardMuted,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${state.backgroundBlur.toInt()}dp",
                                color = GlassTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(36.dp),
                            )
                        }
                    }
                }

                SectionCard(title = "一言") {
                    Field(
                        value = state.hitokotoUrl,
                        onValueChange = onHitokotoUrlChange,
                        label = "URL",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSaveHitokoto, colors = primaryBtn()) { Text("保存") }
                        TextButton(onClick = onTestHitokoto) { Text("测试", color = Primary) }
                    }
                }

                SectionCard(title = "下载节点") {
                    Text(
                        "尽量填写番茄小说相关 API 节点；其他小说软件的接口不保证可用。",
                        color = GlassTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "如何找 API：打开必应 www.bing.com，搜索「番茄API状态」。若某条结果的网页标题与「番茄API状态」六字完全一致，复制该页 URL，在下方直接添加即可。",
                        color = GlassTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    state.nodes.forEachIndexed { idx, node ->
                        NodeRow(
                            node = node,
                            probeInfo = state.nodeProbes[node.id],
                            onUpdate = { url -> onUpdate(node.id, url) },
                            onRemove = { onRemove(node.id) },
                        )
                            if (idx < state.nodes.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp)
                                        .height(0.5.dp)
                                        .background(Color.White.copy(alpha = 0.08f)),
                                )
                            }
                        }
                    }
                    if (state.probeMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(state.probeMessage, color = GlassTextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onProbeAll,
                            enabled = !state.probingAll,
                            colors = primaryBtn(),
                        ) {
                            if (state.probingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (state.probingAll) "测速中…" else "一键测速")
                        }
                        TextButton(onClick = onRestore) { Text("恢复默认", color = Primary) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Field(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = "http(s)://…",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onAdd(newUrl)
                            newUrl = ""
                        },
                        colors = primaryBtn(),
                    ) { Text("添加") }
                }

                val context = LocalContext.current
                val pm = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager }
                var isIgnoring by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
                val batteryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                }
                if (!isIgnoring) {
                    SectionCard(title = "省电设置") {
                        Text(
                            "建议关闭电池优化以免下载中断",
                            color = GlassTextSecondary,
                            fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                batteryLauncher.launch(intent)
                            },
                        ) {
                            Text(
                                "关闭电池优化",
                                color = Primary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                SectionCard(title = "关于") {
                    Text("软件AI制作，作者不会编程", color = GlassText, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "当前版本：${state.appVersionName.ifBlank { "—" }}",
                        color = GlassText,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime > 1000L) versionTapCount = 0
                            lastTapTime = now
                            versionTapCount++
                            if (versionTapCount >= 10) {
                                if (state.r18HiddenEnabled) {
                                    showHiddenAlreadyEnabled = true
                                    toastTick++
                                } else {
                                    showHiddenFeatureQuery = true
                                }
                            }
                        },
                    )
                    if (state.updateMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(state.updateMessage, color = GlassTextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCheckUpdate,
                            enabled = !state.updateChecking,
                            colors = primaryBtn(),
                        ) {
                            Text(if (state.updateChecking) "检查中…" else "检查更新")
                        }
                        TextButton(onClick = onOpenRepo) {
                            Text("打开仓库", color = Primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启动时自动检查更新", color = GlassText, fontSize = 13.sp)
                            Text("打开应用时静默检测新版本", color = GlassTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = state.autoUpdateCheck,
                            onCheckedChange = { onToggleAutoUpdateCheck() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.4f)),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("下载源（下载时自动测速选最快）", color = GlassTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    state.downloadSources.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                source.name,
                                color = GlassText,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (!source.builtin) {
                                TextButton(
                                    onClick = { onRemoveDownloadSource(source.id) },
                                ) {
                                    Text("删除", color = Primary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showAddDownloadSource = true
                        }) {
                            Text("+ 添加自定义源", color = Primary, fontSize = 12.sp)
                        }
                        TextButton(onClick = onResetMirrors) {
                            Text("恢复默认镜像", color = Primary, fontSize = 12.sp)
                        }
                    }
                    if (showAddDownloadSource) {
                        Spacer(Modifier.height(4.dp))
                        var newSrcName by remember { mutableStateOf("") }
                        var newSrcTmpl by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = newSrcName,
                            onValueChange = { newSrcName = it },
                            label = { Text("名称", color = GlassTextSecondary) },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newSrcTmpl,
                            onValueChange = { newSrcTmpl = it },
                            label = { Text("URL 模板（用 {url} 占位）", color = GlassTextSecondary) },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                if (newSrcTmpl.isNotBlank()) {
                                    onAddDownloadSource(newSrcName, newSrcTmpl)
                                    newSrcName = ""
                                    newSrcTmpl = ""
                                    showAddDownloadSource = false
                                }
                            }) { Text("保存", color = Primary, fontSize = 13.sp) }
                            TextButton(onClick = {
                                showAddDownloadSource = false
                            }) { Text("取消", color = TextSecondary, fontSize = 13.sp) }
                        }
                    }

                    if (state.updateAvailable && state.releaseAssets.isNotEmpty()) {
                        val asset = state.matchingAsset ?: state.releaseAssets.firstOrNull() ?: return@SectionCard
                        val assetNote = if (asset.abi.isEmpty()) "已匹配 universal" else "已匹配 ${asset.abi}"
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "新版 ${state.latestVersionTag.orEmpty()} / ${asset.name}",
                            color = GlassTextSecondary,
                            fontSize = 12.sp,
                        )
                        Text(assetNote, color = GlassTextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))

                        if (state.updateDownloadMessage != null) {
                            Text(
                                state.updateDownloadMessage,
                                color = if (state.updateDownloading) Accent else GlassTextSecondary,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (state.updateDownloading) {
                            LinearProgressIndicator(
                                progress = { state.updateDownloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Primary,
                                trackColor = CardMuted,
                            )
                            Spacer(Modifier.height(6.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onDownloadUpdate,
                                enabled = !state.updateDownloading,
                                colors = primaryBtn(),
                            ) {
                                Text(if (state.updateDownloading) "下载中…" else "下载新版本")
                            }
                        }
                    }
                }
            }
        }

        if (showHiddenFeatureQuery) {
            AlertDialog(
                onDismissRequest = { /* 必须点按钮关闭 */ },
                title = { Text("隐藏功能", color = Color.White, fontSize = 18.sp) },
                text = { Text("是否启用隐藏功能？", color = Color(0xFFBBBBBB), fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        showHiddenFeatureQuery = false
                        showR18Dialog = true
                    }) {
                        Text("是", color = Primary, fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHiddenFeatureQuery = false }) {
                        Text("否", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                    }
                },
                containerColor = Color(0xFF1E1E2E),
            )
        }

        if (showHiddenAlreadyEnabled) {
            LaunchedEffect(toastTick) {
                delay(1500)
                showHiddenAlreadyEnabled = false
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC1E1E2E))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text(
                        "你已经开启功能了啊，笨蛋~",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (showR18Dialog) {
            R18DisclaimerDialog(
                onAccept = {
                    showR18Dialog = false
                    onAcceptR18()
                    onBgModeChange(BackgroundMode.R18)
                },
                onDecline = { showR18Dialog = false },
            )
        }
    }
}

@Composable
private fun R18DisclaimerDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var countdown by remember { mutableIntStateOf(30) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text("免责声明与注意事项", color = Color.White, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                Text(
                    "本图源所展示的图片内容可能包含成人向（R18）素材，请确认您已年满 18 周岁并符合当地法律法规。",
                    color = Color(0xFFBBBBBB),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DisclaimerItem("年龄限制", "仅供 18 岁以上用户使用。如您未满 18 周岁，请立即关闭此图源。")
                    Spacer(Modifier.height(8.dp))
                    DisclaimerItem("内容性质", "图片由第三方接口随机提供，开发者不对图片具体内容做任何审查或干预。")
                    Spacer(Modifier.height(8.dp))
                    DisclaimerItem("合规使用", "请遵守您所在地区的法律法规。因使用本图源产生的任何后果由用户自行承担。")
                    Spacer(Modifier.height(8.dp))
                    DisclaimerItem("隐私保护", "应用仅在获取背景图片时向第三方接口发起一次请求，不会上传您的任何个人信息。")
                    Spacer(Modifier.height(8.dp))
                    DisclaimerItem("免责声明", "开发者不对第三方接口的可用性、内容准确性或安全性做任何保证。如遇不适内容，请切换至其他图源。")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                enabled = countdown <= 0,
            ) {
                Text(
                    if (countdown > 0) "同意（${countdown}s）" else "同意",
                    color = if (countdown > 0) Color(0xFF777777) else Primary,
                    fontSize = 14.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("不同意", color = Color(0xFFBBBBBB), fontSize = 14.sp)
            }
        },
        containerColor = Color(0xFF1E1E2E),
    )
}

@Composable
private fun DisclaimerItem(title: String, body: String) {
    Text(
        title,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        body,
        color = Color(0xFFBBBBBB),
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label, color = GlassTextSecondary) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = GlassTextSecondary.copy(alpha = 0.6f)) }
        } else null,
        colors = fieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(color = GlassText, fontSize = 14.sp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GlassText,
    unfocusedTextColor = GlassText,
    focusedBorderColor = Primary,
    unfocusedBorderColor = GlassTextSecondary.copy(alpha = 0.35f),
    cursorColor = Primary,
    focusedLabelColor = Primary,
    unfocusedLabelColor = GlassTextSecondary,
    focusedContainerColor = Color(0x18FFFFFF),
    unfocusedContainerColor = Color(0x18FFFFFF),
)

@Composable
private fun BgOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Primary,
                unselectedColor = TextSecondary,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = GlassText, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = GlassTextSecondary, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        corner = 16.dp,
        fill = GlassFill,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = GlassText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun NodeRow(
    node: NodeConfig,
    probeInfo: NodeProbeInfo?,
    onUpdate: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var url by remember(node.id, node.baseUrl) { mutableStateOf(node.baseUrl) }
    var editing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidthDp = 56.dp
    val actionWidthPx = with(density) { actionWidthDp.toPx() * 2 }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
    ) {
        // 左滑操作层
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(actionWidthDp)
                    .fillMaxHeight()
                    .background(Accent)
                    .clickable {
                        editing = true
                        scope.launch { offsetX.animateTo(0f) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("编辑", color = Color.White, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .width(actionWidthDp)
                    .fillMaxHeight()
                    .background(Primary)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Text("删除", color = Color.White, fontSize = 13.sp)
            }
        }

        // 前景行（暗底 + 玻璃上层）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -actionWidthPx / 2) {
                                    offsetX.animateTo(-actionWidthPx)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount)
                                        .coerceIn(-actionWidthPx, 0f),
                                )
                            }
                        },
                    )
                },
        ) {
            // 暗底遮罩：挡住背后的左滑按钮，避免透出
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF12121A)),
            )
            // 玻璃上层
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                    .background(GlassFill),
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            ) {
                ProbeDot(probeInfo)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = url,
                    color = GlassText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                ProbeLabel(probeInfo)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "左滑操作",
                    tint = TextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(12.dp)
                        .offset { IntOffset(0, 0) },
                )
            }
            if (editing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                ) {
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = TextStyle(color = GlassText, fontSize = 13.sp),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onUpdate(url)
                        editing = false
                    }) { Text("保存", color = Primary, fontSize = 13.sp) }
                    TextButton(onClick = { editing = false }) {
                        Text("取消", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ProbeDot(info: NodeProbeInfo?) {
    val color = probeLatencyColor(info)
    Icon(
        Icons.Default.Circle,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(8.dp),
    )
}

@Composable
private fun ProbeLabel(info: NodeProbeInfo?) {
    if (info == null) return
    val text = when (info.phase) {
        NodeProbePhase.Idle -> return
        NodeProbePhase.Probing -> "测活中…"
        NodeProbePhase.Ok -> formatLatencyLabel(info.latencyMs ?: return)
        NodeProbePhase.Timeout -> "超时"
        NodeProbePhase.Fail -> info.error ?: "失败"
    }
    val color = probeLatencyColor(info)
    Text(text, color = color, fontSize = 12.sp)
}

private fun formatLatencyLabel(ms: Long): String {
    return "${ms}ms"
}

private fun probeLatencyColor(info: NodeProbeInfo?): Color {
    if (info == null) return TextSecondary
    return when (info.phase) {
        NodeProbePhase.Idle -> TextSecondary
        NodeProbePhase.Probing -> Color(0xFF9E9E9E)
        NodeProbePhase.Ok -> {
            val ms = info.latencyMs ?: return TextSecondary
            when {
                ms <= 100 -> Color(0xFF2E7D32)
                ms <= 200 -> Color(0xFF66BB6A)
                ms <= 500 -> Color(0xFFFDD835)
                ms <= 1000 -> Color(0xFFFF9800)
                else -> Color(0xFFFF5722)
            }
        }
        NodeProbePhase.Timeout -> Color(0xFFD32F2F)
        NodeProbePhase.Fail -> Color(0xFFD32F2F)
    }
}

@Composable
private fun CustomBgRow(
    cbg: CustomBackground,
    selected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    var editUrl by remember(cbg.id, cbg.url) { mutableStateOf(cbg.url) }
    var editName by remember(cbg.id, cbg.name) { mutableStateOf(cbg.name) }
    var editing by remember { mutableStateOf(false) }

    BgOption(
        selected = selected,
        title = editName.ifBlank { "自定义接口" },
        subtitle = editUrl,
        onClick = {
            if (selected) editing = !editing
            else onSelect()
        },
    )
    if (editing) {
        Field(value = editName, onValueChange = { editName = it }, label = "名称")
        Spacer(Modifier.height(4.dp))
        Field(value = editUrl, onValueChange = { editUrl = it }, label = "API 地址")
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                onUpdate(editName, editUrl)
                editing = false
            }) { Text("保存", color = Primary, fontSize = 13.sp) }
            TextButton(onClick = {
                onRemove()
                editing = false
            }) { Text("删除", color = Primary, fontSize = 13.sp) }
            TextButton(onClick = { editing = false }) {
                Text("取消", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun primaryBtn() = ButtonDefaults.buttonColors(
    containerColor = Primary,
    contentColor = Color.White,
)
