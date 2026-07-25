package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onToggleCatalog: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    onRetry: () -> Unit = {},
) {
    val chapters = state.readerChapters
    val idx = state.readerIndex
    val content = state.readerContent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F0E6)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgBlack)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.readerTitle,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val chTitle = content?.title
                        ?: chapters.getOrNull(idx)?.title
                        ?: ""
                    if (chTitle.isNotBlank()) {
                        Text(
                            chTitle,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { onToggleCatalog(true) }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                when {
                    state.readerLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Primary,
                        )
                    }
                    state.readerError != null && content == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(state.readerError, color = TextSecondary, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            ) {
                                Text("重试", color = Color.White)
                            }
                        }
                    }
                    content != null -> {
                        val body = remember(content.text) { indentParagraphs(content.text) }
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                content.title.ifBlank { "第 ${idx + 1} 章" },
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                body,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                lineHeight = 28.sp,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrev, enabled = idx > 0) {
                    Text("上一章", color = if (idx > 0) Primary else TextSecondary, fontSize = 14.sp)
                }
                Text(
                    if (chapters.isEmpty()) "—" else "第 ${idx + 1} / ${chapters.size} 章",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                TextButton(onClick = onNext, enabled = idx < chapters.lastIndex) {
                    Text(
                        "下一章",
                        color = if (idx < chapters.lastIndex) Primary else TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (state.showCatalog) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            LaunchedEffect(state.showCatalog) {
                if (idx in chapters.indices) {
                    listState.scrollToItem(idx)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable { onToggleCatalog(false) },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize(0.82f)
                    .background(Color.White)
                    .padding(12.dp)
                    .clickable(enabled = false) {},
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "目录",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Text(
                        "${chapters.size} 章",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(chapters, key = { _, ch -> ch.itemId }) { i, ch ->
                        val selected = i == idx
                        val bgColor by animateColorAsState(
                            if (selected) Primary.copy(alpha = 0.08f) else Color.Transparent,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgColor)
                                .clickable { onJump(i) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${i + 1}",
                                color = if (selected) Primary else TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.width(36.dp),
                            )
                            Text(
                                text = ch.title.ifBlank { "第 ${i + 1} 章" },
                                color = if (selected) TextPrimary else TextPrimary.copy(alpha = 0.75f),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 段落首行缩进两个汉字宽（全角空格） */
private fun indentParagraphs(raw: String): String {
    if (raw.isBlank()) return raw
    return raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .joinToString("\n") { line ->
            val t = line.trimEnd()
            if (t.isBlank()) t
            else if (t.startsWith("　　") || t.startsWith("  ")) t
            else "　　$t"
        }
}
