package ink.yan.reader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ink.yan.reader.data.BackgroundKind
import ink.yan.reader.data.BackgroundScale
import ink.yan.reader.data.BackgroundSource
import ink.yan.reader.data.BackgroundPresets
import ink.yan.reader.vm.MainViewModel

@Composable
fun BackgroundSection(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val prefs = ui.background
    var showAdd by remember { mutableStateOf(false) }

    // 从相册选图。选中后由 ViewModel 复制到私有目录，
    // 不能直接存 URI —— 授权在进程重启后就失效了。
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.importLocalBackground(uri) }

    SettingsSection("背景图源") {
        // 本地图片：一旦选了就优先于任何网络源
        ChoiceRow(
            text = "本地图片",
            sub = if (prefs.localPath.isBlank()) "从相册选一张" else "已选，点击重新选择",
            selected = prefs.localPath.isNotBlank(),
            onClick = { picker.launch("image/*") },
        )
        if (prefs.localPath.isNotBlank()) {
            TextButton(
                onClick = vm::clearLocalBackground,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("取消本地图片，改用接口") }
        }

        prefs.sources.forEach { src ->
            ChoiceRow(
                text = src.name,
                sub = describe(src),
                selected = prefs.sourceId == src.id && prefs.localPath.isBlank(),
                onClick = { vm.selectBackgroundSource(src.id) },
            )
        }

        TextButton(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ 添加自定义接口") }

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
                    selected = prefs.sourceId == src.id && prefs.localPath.isBlank(),
                    onClick = { vm.selectBackgroundSource(src.id) },
                )
                TextButton(
                    onClick = { vm.removeCustomBackground(src.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("删除「${src.name}」") }
            }
        }
    }

    SettingsSection("背景显示") {
        TextButton(
            onClick = { vm.refreshBackground(bust = true) },
            enabled = !ui.bgRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (ui.bgRefreshing) "正在换…" else "换一张") }

        BackgroundScale.entries.forEach { s ->
            ChoiceRow(
                text = s.label,
                sub = s.hint,
                selected = prefs.scale == s,
                onClick = { vm.setBackgroundScale(s) },
            )
        }

        SliderRow(
            label = "模糊",
            value = prefs.blurDp,
            range = 0f..40f,
            onValue = { vm.setBackgroundBlur(it) },
            onCommit = { vm.setBackgroundBlur(prefs.blurDp, commit = true) },
            format = { "${it.toInt()} dp" },
        )

        SliderRow(
            label = "压暗",
            value = prefs.scrimAlpha,
            range = 0f..0.9f,
            onValue = { vm.setBackgroundScrim(it) },
            onCommit = { vm.setBackgroundScrim(prefs.scrimAlpha, commit = true) },
            format = { "${(it * 100).toInt()}%" },
        )

        SwitchRow(
            text = "显示 R18 图源",
            sub = "默认关闭。开启后会多出一个成人向图源，请自行判断场合",
            checked = prefs.showMature,
            onCheckedChange = vm::setShowMature,
        )
    }

    if (showAdd) {
        AddBackgroundDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, url, kind, path ->
                showAdd = false
                vm.addCustomBackground(name, url, kind, path)
            },
        )
    }
}

private fun describe(src: BackgroundSource): String = when {
    src.kind == BackgroundKind.DIRECT -> src.url
    src.jsonPath.isBlank() -> "${src.url}（自动探测图片地址）"
    else -> "${src.url}（取 ${src.jsonPath}）"
}

@Composable
private fun AddBackgroundDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, kind: BackgroundKind, jsonPath: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(BackgroundKind.DIRECT) }
    var path by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加背景接口") },
        text = {
            androidx.compose.foundation.layout.Column {
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
                    "接口返回什么？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                BackgroundKind.entries.forEach { k ->
                    ChoiceRow(text = k.label, sub = k.hint, selected = kind == k, onClick = { kind = k })
                }
                if (kind == BackgroundKind.JSON) {
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("图片地址路径（留空自动探测）") },
                        placeholder = { Text("images.url") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url, kind, path) },
                enabled = url.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
