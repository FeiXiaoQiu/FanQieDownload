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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.feixiaoqiu.fanqiedl.ui.theme.CardWhite
import com.feixiaoqiu.fanqiedl.ui.theme.InputBg
import com.feixiaoqiu.fanqiedl.ui.theme.InputBorder
import com.feixiaoqiu.fanqiedl.ui.theme.Placeholder
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import com.feixiaoqiu.fanqiedl.ui.theme.Scrim
import com.feixiaoqiu.fanqiedl.ui.theme.TextPrimary
import com.feixiaoqiu.fanqiedl.ui.theme.TextSecondary
import com.feixiaoqiu.fanqiedl.viewmodel.MainUiState

@Composable
fun SearchScreen(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBook: (BookSummary) -> Unit,
    onRefreshHitokoto: () -> Unit,
) {
    val hasResults = state.books.isNotEmpty() || state.searching || state.searchError != null
    val corner = RoundedCornerShape(10.dp)

    Box(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        if (state.backgroundDisplayUrl.isNotBlank()) {
            AsyncImage(
                model = state.backgroundDisplayUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Scrim))

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
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
                            subtitle = "搜书 · 简介 · 下载 TXT",
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "输入书名搜索；留空将使用示例书名。",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
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
    Card(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = "一言",
                color = Color(0xFF888888),
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
            .background(Color(0xFFF0F0F0))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE0E0E0)),
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
