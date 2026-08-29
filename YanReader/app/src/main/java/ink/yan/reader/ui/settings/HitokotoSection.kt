package ink.yan.reader.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.yan.reader.vm.MainViewModel

/** 一言：开关、源选择、自定义接口。 */
@Composable
fun HitokotoSection(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val prefs = ui.hitokoto
    var showAdd by remember { mutableStateOf(false) }

    CollapsibleSection(
        title = "首页一言",
        summary = if (prefs.enabled) {
            prefs.sources.find { it.id == prefs.sourceId }?.name ?: "未选择"
        } else {
            "已关闭"
        },
    ) {
        SwitchRow(
            text = "首页显示一言",
            sub = "关掉后首页只留搜索框",
            checked = prefs.enabled,
            onCheckedChange = vm::setHitokotoEnabled,
        )

        if (prefs.enabled) {
            prefs.sources.forEach { src ->
                ChoiceRow(
                    text = src.name,
                    sub = src.url,
                    selected = prefs.sourceId == src.id,
                    onClick = { vm.selectHitokotoSource(src.id) },
                )
            }

            if (prefs.customSources.isNotEmpty()) {
                Text(
                    "自定义接口",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                prefs.customSources.forEach { src ->
                    ChoiceRow(
                        text = src.name,
                        sub = src.url,
                        selected = prefs.sourceId == src.id,
                        onClick = { vm.selectHitokotoSource(src.id) },
                    )
                    TextButton(
                        onClick = { vm.removeCustomHitokoto(src.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("删除「${src.name}」") }
                }
            }

            TextButton(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ 添加自定义接口") }

            // 当前这句话，方便判断接口通不通
            if (ui.hitokotoText.isNotBlank()) {
                Text(
                    ui.hitokotoText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            TextButton(
                onClick = vm::refreshHitokoto,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("换一句") }
        }
    }

    if (showAdd) {
        AddHitokotoDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, url ->
                showAdd = false
                vm.addCustomHitokoto(name, url)
            },
        )
    }
}

@Composable
private fun AddHitokotoDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加一言接口") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("接口地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "返回的 JSON 会自动按 hitokoto / text / content 等常见字段名取值，"
                        + "纯文本响应也会原样显示。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url) },
                enabled = url.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
