package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feixiaoqiu.fanqiedl.data.BackgroundMode
import com.feixiaoqiu.fanqiedl.data.DefaultNodes
import com.feixiaoqiu.fanqiedl.data.NodeConfig
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.theme.CardWhite
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState

@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
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
    onBgImageChange: (String) -> Unit,
    onSaveBackground: () -> Unit,
    onRefreshBackground: () -> Unit,
) {
    var newUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(16.dp),
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
                        OutlinedTextField(
                            value = state.backgroundApiUrl,
                            onValueChange = onBgApiChange,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            singleLine = true,
                            label = { Text("API 地址") },
                            placeholder = { Text(DefaultNodes.DEFAULT_BACKGROUND_API) },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    BgOption(
                        selected = state.backgroundMode == BackgroundMode.CUSTOM_IMAGE,
                        title = "自定义背景",
                        subtitle = "固定图片 URL",
                        onClick = { onBgModeChange(BackgroundMode.CUSTOM_IMAGE) },
                    )
                    if (state.backgroundMode == BackgroundMode.CUSTOM_IMAGE) {
                        OutlinedTextField(
                            value = state.backgroundImageUrl,
                            onValueChange = onBgImageChange,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            singleLine = true,
                            label = { Text("图片 URL") },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveBackground, colors = primaryBtn()) { Text("保存") }
                    if (state.backgroundMode != BackgroundMode.CUSTOM_IMAGE) {
                        TextButton(onClick = onRefreshBackground) { Text("换一张") }
                    }
                }
            }

            SectionCard(title = "一言") {
                OutlinedTextField(
                    value = state.hitokotoUrl,
                    onValueChange = onHitokotoUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("URL") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveHitokoto, colors = primaryBtn()) { Text("保存") }
                    TextButton(onClick = onTestHitokoto) { Text("测试") }
                }
            }

            SectionCard(title = "下载节点") {
                state.nodes.forEach { node ->
                    NodeRow(
                        node = node,
                        onToggle = { onToggle(node.id, it) },
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
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("http(s)://…") },
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
                    TextButton(onClick = onRestore) { Text("恢复默认") }
                }
            }
        }
    }
}

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
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        modifier = modifier.fillMaxWidth(),
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
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onProbe: () -> Unit,
    onSave: (String) -> Unit,
) {
    var url by remember(node.id, node.baseUrl) { mutableStateOf(node.baseUrl) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("URL") },
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Switch(checked = node.enabled, onCheckedChange = onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSave(url) }) { Text("保存") }
                TextButton(onClick = onProbe) { Text("测活") }
                if (!node.builtin) {
                    TextButton(onClick = onRemove) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun primaryBtn() = ButtonDefaults.buttonColors(
    containerColor = Primary,
    contentColor = Color.White,
)
