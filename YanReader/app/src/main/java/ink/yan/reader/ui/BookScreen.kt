package ink.yan.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.yan.reader.data.DownloadRequest
import ink.yan.reader.data.ExportFormat
import ink.yan.reader.vm.MainViewModel

/**
 * 书籍详情：目录 + 下载 + 点章节在线看。
 *
 * 目录可能几千章，所以走 LazyColumn 而不是一次性渲染 ——
 * 长列表全量组合会在进页面时卡住好几秒。
 */
@Composable
fun BookScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onRead: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val book = ui.selected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    book?.title ?: "书籍",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book != null) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when {
            ui.catalogLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text("正在加载目录…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            !ui.catalogError.isNullOrBlank() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        ui.catalogError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 空目录多半换一本才有解，请求失败则是重试有意义。
                    // 两种都给重试入口，不细分了 —— 用户点一下就知道有没有用。
                    Text(
                        "重试",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.noRippleClick {
                            ui.selected?.let(vm::selectBook)
                        },
                    )
                }
            }

            ui.chapters.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> {
                // 下载整本。放在列表顶部而不是悬浮，避免遮住章节。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "共 ${ui.chapters.size} 章",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            vm.startDownload(
                                DownloadRequest(
                                    bookId = book?.id.orEmpty(),
                                    title = book?.title.orEmpty(),
                                    format = ui.format,
                                )
                            )
                        },
                    ) { Text("下载 ${ui.format.label}") }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(ui.chapters, key = { _, c -> c.itemId }) { idx, ch ->
                        Text(
                            ch.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glass(cornerDelta = -4)
                                // 点击要挂在 padding 之前：挂在之后的话可点区域
                                // 只剩 padding 内那一小块，玻璃边缘的留白点不到。
                                .noRippleClick {
                                    vm.openChapter(idx)
                                    onRead()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
