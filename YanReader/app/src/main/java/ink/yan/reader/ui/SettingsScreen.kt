package ink.yan.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.yan.reader.data.ExportFormat
import ink.yan.reader.ui.settings.BackgroundSection
import ink.yan.reader.ui.settings.ChoiceRow
import ink.yan.reader.ui.settings.CollapsibleSection
import ink.yan.reader.ui.settings.HitokotoSection
import ink.yan.reader.ui.settings.LookSection
import ink.yan.reader.ui.settings.UpdateSection
import ink.yan.reader.vm.MainViewModel

/**
 * 设置页。
 *
 * 每个分区默认收起，只留一行摘要。摊平之后十几项堆在一起，
 * 找一项要滚很久；收起后靠摘要确认当前值，需要改再展开。
 * item 都带 key —— 折叠状态存在 item 内部，不设 key 会在滚动后串位。
 */
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "format") {
                CollapsibleSection(
                    title = "导出格式",
                    summary = "${ui.format.label} —— ${
                        if (ui.format == ExportFormat.EPUB) "支持目录与章节跳转"
                        else "兼容性最好"
                    }",
                ) {
                    ExportFormat.entries.forEach { f ->
                        ChoiceRow(
                            text = f.label,
                            sub = if (f == ExportFormat.EPUB) "标准格式，支持目录与章节跳转"
                            else "纯文本，兼容性最好",
                            selected = ui.format == f,
                            onClick = { vm.setFormat(f) },
                        )
                    }
                }
            }

            item(key = "look") { LookSection(vm) }
            item(key = "background") { BackgroundSection(vm) }
            item(key = "hitokoto") { HitokotoSection(vm) }
            item(key = "update") { UpdateSection(vm) }

            item(key = "about") {
                CollapsibleSection(title = "关于", summary = "砚 v${ui.currentVersion.ifBlank { "0.3.0" }}") {
                    Text(
                        "预置节点均为第三方公开接口，可用性不受本项目控制，"
                            + "可随时停用、删除或一键恢复。背景接口与一言接口同样可自由替换。"
                            + "请遵守相关平台条款与版权法规，仅限个人学习研究使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "包名 ink.yan.reader，独立签名。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
