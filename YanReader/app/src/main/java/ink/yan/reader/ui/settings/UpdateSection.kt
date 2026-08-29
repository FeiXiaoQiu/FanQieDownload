package ink.yan.reader.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ink.yan.reader.data.DownloadPresets
import ink.yan.reader.data.DownloadSource
import ink.yan.reader.data.formatSize
import ink.yan.reader.data.pickApkAsset
import ink.yan.reader.data.resolveSourceUrl
import ink.yan.reader.vm.MainViewModel

private const val SAMPLE_URL = "https://github.com/a/b/releases/download/v/yanreader.apk"

/** 应用更新：检查版本、选择镜像下载、拉起安装。 */
@Composable
fun UpdateSection(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val prefs = ui.updatePrefs
    val info = ui.updateInfo
    val ctx = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }

    val summary = when {
        ui.apkProgress != null -> "正在下载…"
        info != null && ui.hasUpdate -> "发现新版本 ${info.version}"
        else -> "当前 ${ui.currentVersion.ifBlank { "未知版本" }}"
    }

    CollapsibleSection(title = "应用更新", summary = summary) {
        Text(
            "当前版本 ${ui.currentVersion.ifBlank { "未知" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        ActionRow(
            text = if (ui.updateChecking) "正在检查…" else "检查更新",
            enabled = !ui.updateChecking,
            onClick = { vm.checkUpdate() },
        )

        if (!ui.updateMessage.isNullOrBlank()) {
            Text(
                ui.updateMessage!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        if (info != null) {
            val asset = remember(info) { pickApkAsset(info.assets) }
            Text(
                "最新 ${info.version}${asset?.size?.let { " · ${formatSize(it)}" } ?: ""}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (info.notes.isNotBlank()) {
                Text(
                    info.notes.trim().lineSequence().take(12).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            if (ui.apkProgress != null) {
                LinearProgressIndicator(
                    progress = { ui.apkProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                if (!ui.apkMessage.isNullOrBlank()) {
                    Text(
                        ui.apkMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ActionRow(text = "取消下载", onClick = vm::cancelUpdate)
            } else if (ui.hasUpdate) {
                ActionRow(
                    text = if (asset == null) "这个版本没有 APK" else "下载并安装",
                    enabled = asset != null,
                    onClick = vm::downloadUpdate,
                )
            }

            ActionRow(text = "前往发布页", onClick = {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            })
        }

        // —— 下载源 ——

        Text(
            "下载源",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )

        prefs.customSources.forEach { src ->
            SourceRow(
                name = src.name,
                sub = previewOf(src),
                checked = true,
                removable = true,
                onDelete = { vm.removeCustomDownloadSource(src.id) },
            )
        }

        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        SourceRow(
            name = "GitHub 原链接",
            sub = "直连，最稳但可能慢",
            checked = prefs.isEnabled(DownloadPresets.DIRECT),
            onToggle = { vm.setDownloadSourceEnabled(DownloadPresets.DIRECT, it) },
        )
        SourceRow(
            name = "gh.xmly.dev 镜像",
            sub = "国内通常更快",
            checked = prefs.isEnabled(DownloadPresets.MIRROR_XMLY),
            onToggle = { vm.setDownloadSourceEnabled(DownloadPresets.MIRROR_XMLY, it) },
        )
        SourceRow(
            name = "gh-proxy.com 镜像",
            sub = "备用镜像",
            checked = prefs.isEnabled(DownloadPresets.MIRROR_GH_PROXY),
            onToggle = { vm.setDownloadSourceEnabled(DownloadPresets.MIRROR_GH_PROXY, it) },
        )

        ActionRow(text = "+ 添加自定义镜像", onClick = { showAdd = true })

        Text(
            "下载时按「镜像优先，直连垫底」依次探测，取第一个可达的源。"
                + "镜像站寿命普遍不长，哪天失效了直接在这关掉或换成自己的。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }

    if (showAdd) {
        AddMirrorDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, tpl ->
                showAdd = false
                vm.addCustomDownloadSource(name, tpl)
            },
        )
    }

    // 下好后拉起安装。未知来源权限没开就先引导去设置，
    // 否则 startActivity 会被系统直接拦掉，用户只看到「点了没反应」。
    LaunchedEffect(ui.apkFile) {
        val file = ui.apkFile ?: return@LaunchedEffect
        vm.consumeApk()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return@LaunchedEffect
        }

        runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun previewOf(src: DownloadSource): String =
    resolveSourceUrl(src.urlTemplate, SAMPLE_URL)

/** 整行文字按钮，用于「检查更新」这类不常点的动作。 */
@Composable
private fun ActionRow(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

/** 下载源行：开关 + 可选删除。 */
@Composable
private fun SourceRow(
    name: String,
    sub: String,
    checked: Boolean,
    removable: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (onToggle != null) {
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onToggle)
        }
        if (removable && onDelete != null) {
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun AddMirrorDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, template: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var tpl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加镜像") },
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
                    value = tpl,
                    onValueChange = { tpl = it },
                    label = { Text("地址模板") },
                    placeholder = { Text("https://gh.example.com/{url}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "{url} 会被替换成发布页上的原始下载地址，模板其余部分原样保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, tpl) },
                enabled = tpl.contains("{url}"),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
