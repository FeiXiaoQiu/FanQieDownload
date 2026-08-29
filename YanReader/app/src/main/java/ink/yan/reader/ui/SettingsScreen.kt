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
import ink.yan.reader.ui.settings.HitokotoSection
import ink.yan.reader.ui.settings.LookSection
import ink.yan.reader.ui.settings.SettingsSection
import ink.yan.reader.vm.MainViewModel

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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsSection("导出格式") {
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

            item { LookSection(vm) }
            item { BackgroundSection(vm) }
            item { HitokotoSection(vm) }

            item {
                Column {
                    Text(
                        "关于",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.glass(cornerDelta = -4).padding(14.dp)) {
                        Text(
                            "砚 YanReader v0.2.0",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "本项目不内置任何内容源。所有数据源、背景接口与一言接口"
                                + "均由用户自行配置，请遵守相关平台条款与版权法规，"
                                + "仅限个人学习研究使用。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
