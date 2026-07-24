package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feixiaoqiu.fanqiedl.data.NodeConfig
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.theme.CardWhite
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState

@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onRestore: () -> Unit,
    onProbe: (String) -> Unit,
    onHitokotoUrlChange: (String) -> Unit,
    onSaveHitokoto: () -> Unit,
    onTestHitokoto: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text("设置", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        SectionCard(title = "一言 API") {
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
            Text("默认 https://v1.hitokoto.cn/", color = TextSecondary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        SectionCard(title = "番茄节点 API") {
            Text(
                "协议：/search /info /catalog /content（与 Web 节点一致）",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.nodes.forEach { node ->
                NodeRow(
                    node = node,
                    onToggle = { onToggle(node.id, it) },
                    onRemove = { onRemove(node.id) },
                    onProbe = { onProbe(node.baseUrl) },
                    onSave = { name, url -> onUpdate(node.id, name, url) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (state.probeMessage != null) {
                Text(state.probeMessage, color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称（可选）") },
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("基址 http(s)://…") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAdd(newName, newUrl)
                        newName = ""
                        newUrl = ""
                    },
                    colors = primaryBtn(),
                ) { Text("添加节点") }
                TextButton(onClick = onRestore) { Text("恢复默认") }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        modifier = Modifier.fillMaxWidth(),
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
    onSave: (String, String) -> Unit,
) {
    var name by remember(node.id, node.name) { mutableStateOf(node.name) }
    var url by remember(node.id, node.baseUrl) { mutableStateOf(node.baseUrl) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (node.builtin) "${node.name}（内置）" else node.name, fontWeight = FontWeight.Medium)
                    Text(node.baseUrl, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
                }
                Switch(checked = node.enabled, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("URL") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSave(name, url) }) { Text("保存") }
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
    containerColor = TextPrimary,
    contentColor = Color.White,
)
