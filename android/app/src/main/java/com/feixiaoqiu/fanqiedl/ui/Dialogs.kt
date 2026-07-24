package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState

@Composable
fun BookDetailDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val book = state.selected ?: return
    val info = state.detail
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(info?.title ?: book.title, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val author = info?.author ?: book.author
                if (author.isNotBlank()) {
                    Text("作者：$author", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier = Modifier.height(8.dp))
                }
                if (state.detailLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    val abstract = info?.abstract ?: book.description
                    if (abstract.isBlank()) {
                        Text("暂无简介", color = TextSecondary)
                    } else {
                        abstract.split(Regex("\n+")).forEach { para ->
                            if (para.isNotBlank()) {
                                Text(
                                    text = para.trim(),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    style = TextStyle(
                                        textIndent = TextIndent(firstLine = 28.sp)
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                    if (state.detailError != null) {
                        Text("详情：${state.detailError}", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = Color.White),
            ) { Text("下载") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
fun DownloadOptionsDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onResumeChange: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载选项") },
        text = {
            Column {
                Text("起始/结束章留空或 0 表示整本", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.startChapter,
                    onValueChange = onStartChange,
                    label = { Text("起始章") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.endChapter,
                    onValueChange = onEndChange,
                    label = { Text("结束章") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.resume, onCheckedChange = onResumeChange)
                    Text("断点续传")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = Color.White),
            ) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun DownloadProgressDialog(
    state: MainUiState,
    onCancel: () -> Unit,
) {
    val p = state.downloadProgress ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("下载中") },
        text = {
            Column {
                Text(p.message)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (p.percent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (p.total > 0) {
                    Text("${p.current}/${p.total}", color = TextSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}

@Composable
fun DownloadResultDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
) {
    val r = state.downloadResult ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (r.status == "done") "完成" else "结果") },
        text = {
            Column {
                Text(r.message)
                Spacer(modifier = Modifier.height(6.dp))
                Text(r.displayPath, color = TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("好的") }
        },
    )
}
