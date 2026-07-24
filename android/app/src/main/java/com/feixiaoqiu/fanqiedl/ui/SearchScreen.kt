package com.feixiaoqiu.fanqiedl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.feixiaoqiu.fanqiedl.R
import com.feixiaoqiu.fanqiedl.data.BookSummary
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.theme.InputBg
import com.feixiaoqiu.fanqiedl.ui.theme.InputBorder
import com.feixiaoqiu.fanqiedl.ui.theme.Placeholder
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import com.feixiaoqiu.fanqiedl.ui.theme.Scrim
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState
import java.io.File

@Composable
fun SearchScreen(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenBook: (BookSummary) -> Unit,
    onRefreshHitokoto: () -> Unit,
) {
    val hasResults = state.books.isNotEmpty() || state.searching || state.searchError != null
    val corner = RoundedCornerShape(10.dp)
    val bgModel: Any? = remember(state.backgroundDisplayUrl) {
        val p = state.backgroundDisplayUrl
        when {
            p.isBlank() -> null
            p.startsWith("http://") || p.startsWith("https://") ||
                p.startsWith("file://") || p.startsWith("content://") -> p
            else -> {
                val f = File(p)
                if (f.isFile) f else null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        if (bgModel != null) {
            AsyncImage(
                model = bgModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Scrim))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenWeb) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("网页端", color = Color.White, fontSize = 13.sp)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
                }
            }

            if (hasResults) {
                SearchHeader(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.disclaimer),
                    compact = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SearchBar(
                    query = state.query,
                    searching = state.searching,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    corner = corner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    corner = 20.dp,
                    fill = GlassFillSoft,
                ) {
                    when {
                        state.searching && state.books.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Primary)
                            }
                        }
                        state.searchError != null && state.books.isEmpty() -> {
                            Text(
                                state.searchError,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.books, key = { it.bookId }) { book ->
                                    BookRow(book = book, onClick = { onOpenBook(book) })
                                }
                                if (state.searchHasMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (state.loadingMore) {
                                                CircularProgressIndicator(
                                                    color = Primary,
                                                    modifier = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Text(
                                                    "加载更多",
                                                    color = Primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier
                                                        .clickable(onClick = onLoadMore)
                                                        .padding(8.dp),
                                                )
                                            }
                                        }
                                    }
                                } else if (state.books.isNotEmpty()) {
                                    item {
                                        Text(
                                            "已全部加载（${state.books.size}）",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SearchHeader(
                            title = stringResource(R.string.app_name),
                            subtitle = stringResource(R.string.app_tagline),
                            compact = false,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        SearchBar(
                            query = state.query,
                            searching = state.searching,
                            onQueryChange = onQueryChange,
                            onSearch = onSearch,
                            corner = corner,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            HitokotoBar(
                text = state.hitokoto,
                onClick = onRefreshHitokoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SearchHeader(title: String, subtitle: String, compact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = if (compact) 18.sp else 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = if (compact) TextAlign.Start else TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = if (compact) 11.sp else 13.sp,
            textAlign = if (compact) TextAlign.Start else TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    corner: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(corner)
                .background(InputBg)
                .border(1.dp, InputBorder, corner)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    "书名 / ID / 分享链接",
                    color = Placeholder,
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = onSearch,
            enabled = !searching,
            shape = corner,
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = Color.White,
                disabledContainerColor = Primary.copy(alpha = 0.7f),
                disabledContentColor = Color.White,
            ),
            modifier = Modifier
                .height(48.dp)
                .width(84.dp),
        ) {
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text("搜索", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun HitokotoBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        corner = 14.dp,
        fill = GlassFill,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = "一言",
                color = Color(0xFF666666),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text.ifBlank { "加载中…" },
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun BookRow(book: BookSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x33FFFFFF)),
        ) {
            if (book.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 2)
            if (book.meta.isNotBlank()) {
                Text(book.meta, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            if (book.description.isNotBlank()) {
                Text(
                    book.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
