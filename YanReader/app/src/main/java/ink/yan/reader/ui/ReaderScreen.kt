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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ink.yan.reader.vm.MainViewModel

/**
 * 在线阅读。
 *
 * 正文用 verticalScroll 而不是 LazyColumn：一章是一次性拿到的完整文本，
 * 按段落拆分再复用反而会破坏选中复制这类原生行为，
 * 而几千字量级全量组合完全扛得住。
 */
@Composable
fun ReaderScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val reading = ui.reading
    val total = ui.chapters.size
    val scroll = rememberScrollState()
    // 翻章后回到顶部。滚动状态是跨重组保留的，不主动归零的话，
    // 从一章末尾切到下一章会直接落在人家结尾处。
    LaunchedEffect(ui.readingIndex) { scroll.scrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    reading?.title ?: ui.chapters.getOrNull(ui.readingIndex)?.title ?: "阅读",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ui.readingIndex >= 0 && total > 0) {
                    Text(
                        "${ui.readingIndex + 1} / $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                ui.readingLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                !ui.readingError.isNullOrBlank() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            ui.readingError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "重试",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.noRippleClick { vm.openChapter(ui.readingIndex) },
                        )
                    }
                }

                reading == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有正文", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        reading.text,
                        // 默认行高对整屏中文偏挤，读久了累眼；这里放松到 1.75 倍字号
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
                        ),
                    )
                    // 底部留白，让最后一行不要贴着翻章按钮
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        // 翻章。两端各占一半，比两个小按钮好按。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageButton(
                text = "上一章",
                enabled = ui.readingIndex > 0,
                modifier = Modifier.weight(1f),
                onClick = { vm.prevChapter() },
            )
            PageButton(
                text = "下一章",
                enabled = ui.readingIndex >= 0 && ui.readingIndex < total - 1,
                modifier = Modifier.weight(1f),
                onClick = { vm.nextChapter() },
            )
        }
    }
}

@Composable
private fun PageButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .glass(cornerDelta = -4)
            .then(if (enabled) Modifier.noRippleClick(onClick) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = tint, style = MaterialTheme.typography.titleMedium)
    }
}
