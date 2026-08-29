package ink.yan.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.yan.reader.data.DownloadState
import ink.yan.reader.vm.MainViewModel

@Composable
fun SearchScreen(
    vm: MainViewModel,
    onNodes: () -> Unit,
    onSettings: () -> Unit,
) {
    val ui by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "砚",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNodes) {
                Icon(Icons.Filled.Hub, "数据源")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, "设置")
            }
        }

        // 搜索框（玻璃容器）
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .liquidGlass(corner = 20.dp)
                .padding(14.dp),
        ) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::onQueryChange,
                label = { Text("书名 / 作者") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = vm::search,
                enabled = !ui.searching && ui.nodes.any { it.enabled },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("搜索")
                }
            }
            if (ui.nodes.none { it.enabled }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "还没有启用任何数据源，先去「数据源」页添加一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // 下载进度
        ui.download?.let { state ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                when (state) {
                    is DownloadState.Progress -> Column(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlass(corner = 18.dp)
                            .padding(14.dp),
                    ) {
                        Text(state.message, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text("${state.percent}%", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "取消",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { vm.cancelDownload() },
                            )
                        }
                    }

                    is DownloadState.Done -> Column(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlass(corner = 18.dp)
                            .padding(14.dp),
                    ) {
                        Text(
                            "已保存：${state.filename}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "共 ${state.total} 章，失败 ${state.errorCount} 章（${state.format.label}）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "关闭",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.clearMessage() },
                        )
                    }

                    is DownloadState.Failed -> Column(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlass(corner = 18.dp)
                            .padding(14.dp),
                    ) {
                        Text(
                            "下载失败：${state.reason}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "关闭",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.clearMessage() },
                        )
                    }
                }
            }
        }

        // 结果列表
        if (ui.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (ui.searching) "搜索中…" else "输入关键词开始搜索",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.results, key = { it.id + it.fromNode }) { book ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlass(corner = 18.dp)
                            .padding(14.dp),
                    ) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row {
                            Text(
                                book.author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (book.chapterCount > 0) {
                                Text(
                                    "${book.chapterCount} 章",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
