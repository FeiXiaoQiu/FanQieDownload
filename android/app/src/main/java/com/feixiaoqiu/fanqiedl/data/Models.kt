package com.feixiaoqiu.fanqiedl.data

data class NodeConfig(
    val id: String,
    val name: String = "",
    val baseUrl: String,
    val enabled: Boolean = true,
    val builtin: Boolean = false,
)

data class SearchPage(
    val books: List<BookSummary>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

/** 主界面背景：三选一 */
enum class BackgroundMode {
    DEFAULT,
    CUSTOM_API,
    CUSTOM_IMAGE,
    ;

    companion object {
        fun fromStorage(raw: String?): BackgroundMode {
            return entries.find { it.name == raw } ?: DEFAULT
        }
    }
}

data class BookSummary(
    val bookId: String,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val description: String = "",
    val meta: String = "",
)

data class BookInfo(
    val bookId: String,
    val title: String,
    val author: String,
    val abstract: String,
)

data class ChapterRef(
    val itemId: String,
    val title: String,
    val index: Int,
)

data class ChapterContent(
    val title: String,
    val text: String,
)

data class DownloadRequest(
    val bookId: String,
    val title: String,
    val startChapter: Int = 0,
    val endChapter: Int = 0,
    val resume: Boolean = true,
)

data class DownloadProgress(
    val current: Int,
    val total: Int,
    val message: String,
    val percent: Int,
)

data class DownloadResult(
    val status: String,
    val title: String,
    val filename: String,
    val displayPath: String,
    val uriString: String?,
    val done: Int,
    val total: Int,
    val errorCount: Int,
    val message: String,
)

class NoEnabledNodeException : Exception("请至少添加一个下载节点")
