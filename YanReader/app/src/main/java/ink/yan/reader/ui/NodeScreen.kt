package ink.yan.reader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.yan.reader.data.LatencyLevel
import ink.yan.reader.data.NodeConfig
import ink.yan.reader.data.NodeLatency
import ink.yan.reader.data.level
import ink.yan.reader.vm.MainViewModel

/**
 * 数据源（节点）管理。
 *
 * 这是整个应用可用性的地基：节点是外置的、随时可能失效，
 * 所以必须让用户能自由增删、随时测速、一眼看出谁快谁挂。
 */
@Composable
fun NodeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                "数据源",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.testNodes() }, enabled = !ui.testing) {
                Icon(Icons.Filled.Speed, "一键测速")
            }
        }

        if (ui.testing) {
            Text(
                "正在测速…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // 添加表单
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .glass(cornerDelta = -2)
                .padding(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("地址，如 http://host:port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    vm.addNode(name.trim(), url.trim())
                    name = ""; url = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加节点")
            }
        }

        Spacer(Modifier.height(8.dp))

        // 节点列表
        if (ui.nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有数据源\n添加一个才能开始使用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.nodes, key = { it.id }) { node ->
                    NodeCard(
                        node = node,
                        latency = ui.latencies[node.id],
                        onToggle = { vm.toggleNode(node.id) },
                        onDelete = { vm.removeNode(node.id) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: NodeConfig,
    latency: NodeLatency?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val level = latency?.level() ?: LatencyLevel.DEAD
    val dotColor by animateColorAsState(
        targetValue = when (level) {
            LatencyLevel.FAST -> Color(0xFF4ADE80)
            LatencyLevel.MEDIUM -> Color(0xFFFACC15)
            LatencyLevel.SLOW -> Color(0xFFFB923C)
            LatencyLevel.DEAD -> Color(0xFF6B7280)
        },
        label = "dot",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(cornerDelta = -4)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态灯
        Box(
            Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor),
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                node.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (latency != null) {
                Text(
                    if (latency.ok) "${latency.millis} ms" else (latency.error ?: "不可达"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (latency.ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }

        Switch(checked = node.enabled, onCheckedChange = { onToggle() })

        if (!node.builtin) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
