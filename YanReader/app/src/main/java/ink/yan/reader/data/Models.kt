package ink.yan.reader.data

/**
 * 纯 Kotlin 数据模型 —— 不依赖任何 android.* 类型，
 * 因此可以在 JVM 上单独编译与单元测试。
 */

/** 一个数据源节点。baseUrl 形如 "http://host:port"，末尾不带斜杠。 */
data class NodeConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val builtin: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "节点 id 不能为空" }
    }
}

/** 节点测速结果。millis 为 null 表示不可达。 */
data class NodeLatency(
    val nodeId: String,
    val ok: Boolean,
    val millis: Long? = null,
    val error: String? = null,
)

/** 延迟分级，用于 UI 分色。 */
enum class LatencyLevel { FAST, MEDIUM, SLOW, DEAD }

fun NodeLatency.level(): LatencyLevel = when {
    !ok || millis == null -> LatencyLevel.DEAD
    millis < 400 -> LatencyLevel.FAST
    millis < 1200 -> LatencyLevel.MEDIUM
    else -> LatencyLevel.SLOW
}

data class BookInfo(
    val id: String,
    val title: String,
    val author: String = "未知",
    val cover: String = "",
    val abstract: String = "",
    val chapterCount: Int = 0,
    /** 来自哪个节点，便于出问题定位 */
    val fromNode: String = "",
)

data class Chapter(
    val itemId: String,
    val title: String,
    val index: Int,
)

data class ChapterContent(
    val title: String,
    val text: String,
) {
    val isEmptyContent: Boolean get() = text.isBlank()
}

enum class ExportFormat(
    val ext: String,
    val mime: String,
    val label: String,
) {
    TXT("txt", "text/plain", "TXT"),
    EPUB("epub", "application/epub+zip", "EPUB"),
}

/** 下载请求。start/end 为闭区间章节下标，越界会被 clamp。 */
data class DownloadRequest(
    val bookId: String,
    val title: String,
    val startChapter: Int = 0,
    val endChapter: Int = Int.MAX_VALUE,
    val format: ExportFormat = ExportFormat.EPUB,
    val resume: Boolean = true,
)

sealed interface DownloadState {
    data class Progress(
        val done: Int,
        val total: Int,
        val message: String,
        val percent: Int,
    ) : DownloadState

    data class Done(
        val filename: String,
        val done: Int,
        val total: Int,
        val errorCount: Int,
        val format: ExportFormat,
    ) : DownloadState

    data class Failed(val reason: String) : DownloadState
}
