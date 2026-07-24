package com.feixiaoqiu.fanqiedl.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.feixiaoqiu.fanqiedl.data.BackgroundMode
import com.feixiaoqiu.fanqiedl.data.DefaultNodes
import com.feixiaoqiu.fanqiedl.data.NodeConfig
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.theme.CardMuted
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import com.feixiaoqiu.fanqiedl.ui.theme.Scrim
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState
import java.io.File

@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onRestore: () -> Unit,
    onProbe: (String) -> Unit,
    onHitokotoUrlChange: (String) -> Unit,
    onSaveHitokoto: () -> Unit,
    onTestHitokoto: () -> Unit,
    onBgModeChange: (BackgroundMode) -> Unit,
    onBgApiChange: (String) -> Unit,
    onSaveBackground: () -> Unit,
    onRefreshBackground: () -> Unit,
    onPickLocalBackground: (Uri) -> Unit,
    onClearLocalBackground: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    onOpenRepo: () -> Unit = {},
    onOpenLatestRelease: () -> Unit = {},
) {
    var newUrl by remember { mutableStateOf("") }
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) onPickLocalBackground(uri)
    }

    val bgModel: Any? = remember(state.backgroundDisplayUrl) {
        val p = state.backgroundDisplayUrl
        when {
            p.isBlank() -> null
            p.startsWith("http://") || p.startsWith("https://") ||
                p.startsWith("file://") || p.startsWith("content://") -> p
            else -> {
                val f = File(p)
                if (f.isFile) f else null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        if (bgModel != null) {
            AsyncImage(
                model = bgModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Scrim))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Column(Modifier.selectableGroup()) {
                        BgOption(
                            selected = state.backgroundMode == BackgroundMode.DEFAULT,
                            title = "默认图床",
                            subtitle = DefaultNodes.DEFAULT_BACKGROUND_API,
                            onClick = { onBgModeChange(BackgroundMode.DEFAULT) },
                        )
                        BgOption(
                            selected = state.backgroundMode == BackgroundMode.CUSTOM_API,
                            title = "自定义图床 API",
                            subtitle = "随机图接口，每次进入会刷新",
                            onClick = { onBgModeChange(BackgroundMode.CUSTOM_API) },
                        )
                        if (state.backgroundMode == BackgroundMode.CUSTOM_API) {
                            Field(
                                value = state.backgroundApiUrl,
                                onValueChange = onBgApiChange,
                                label = "API 地址",
                                placeholder = DefaultNodes.DEFAULT_BACKGROUND_API,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        BgOption(
                            selected = state.backgroundMode == BackgroundMode.CUSTOM_IMAGE,
                            title = "本地图片",
                            subtitle = "从相册选择，保存在应用内",
                            onClick = { onBgModeChange(BackgroundMode.CUSTOM_IMAGE) },
                        )
                        if (state.backgroundMode == BackgroundMode.CUSTOM_IMAGE) {
                            val hasLocal = state.backgroundImageUrl.isNotBlank() ||
                                state.backgroundDisplayUrl.isNotBlank()
                            Text(
                                if (hasLocal) "已选择本地图片" else "尚未选择图片",
                                color = TextSecondary,
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
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSaveBackground, colors = primaryBtn()) { Text("保存") }
                        if (state.backgroundMode != BackgroundMode.CUSTOM_IMAGE) {
                            TextButton(onClick = onRefreshBackground) {
                                Text("换一张", color = Primary)
                            }
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
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "如何找 API：打开必应 www.bing.com，搜索「番茄API状态」。若某条结果的网页标题与「番茄API状态」六字完全一致，复制该页 URL，在下方直接添加即可。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    state.nodes.forEach { node ->
                        NodeRow(
                            node = node,
                            onRemove = { onRemove(node.id) },
                            onProbe = { onProbe(node.baseUrl) },
                            onSave = { url -> onUpdate(node.id, url) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (state.probeMessage != null) {
                        Text(state.probeMessage, color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Field(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = "http(s)://…",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onAdd(newUrl)
                                newUrl = ""
                            },
                            colors = primaryBtn(),
                        ) { Text("添加") }
                        TextButton(onClick = onRestore) { Text("恢复默认", color = Primary) }
                    }
                }

                SectionCard(title = "关于") {
                    Text("作者：非小酋", color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "当前版本：${state.appVersionName.ifBlank { "—" }}",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                    if (state.updateMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(state.updateMessage, color = TextSecondary, fontSize = 12.sp)
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
                    if (state.updateAvailable && state.latestReleaseUrl != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onOpenLatestRelease) {
                            Text("前往最新 Release", color = Primary)
                        }
                    }
                }
            }
        }
    }
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
        label = { Text(label, color = TextSecondary) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = TextSecondary.copy(alpha = 0.6f)) }
        } else null,
        colors = fieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = Primary,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.35f),
    cursorColor = Primary,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextSecondary,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
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
            Text(title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
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
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun NodeRow(
    node: NodeConfig,
    onRemove: () -> Unit,
    onProbe: () -> Unit,
    onSave: (String) -> Unit,
) {
    var url by remember(node.id, node.baseUrl) { mutableStateOf(node.baseUrl) }
    Card(
        colors = CardDefaults.cardColors(containerColor = CardMuted),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Field(value = url, onValueChange = { url = it }, label = "URL")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSave(url) }) { Text("保存", color = Primary) }
                TextButton(onClick = onProbe) { Text("测活", color = TextPrimary) }
                TextButton(onClick = onRemove) { Text("删除", color = Primary) }
            }
        }
    }
}

@Composable
private fun primaryBtn() = ButtonDefaults.buttonColors(
    containerColor = Primary,
    contentColor = Color.White,
)
