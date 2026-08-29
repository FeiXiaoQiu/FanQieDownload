package ink.yan.reader.vm

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.yan.reader.data.BookInfo
import ink.yan.reader.data.Chapter
import ink.yan.reader.data.ChapterContent
import ink.yan.reader.data.DownloadEngine
import ink.yan.reader.data.DownloadRequest
import ink.yan.reader.data.DownloadState
import ink.yan.reader.data.ExportFormat
import ink.yan.reader.data.NodeConfig
import ink.yan.reader.data.NodeLatency
import ink.yan.reader.data.NodeRepository
import ink.yan.reader.data.NodeTester
import ink.yan.reader.data.export.EpubWriter
import ink.yan.reader.data.export.TxtWriter
import ink.yan.reader.store.NodeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class UiState(
    val nodes: List<NodeConfig> = emptyList(),
    val latencies: Map<String, NodeLatency> = emptyMap(),
    val testing: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<BookInfo> = emptyList(),
    val selected: BookInfo? = null,
    val chapters: List<Chapter> = emptyList(),
    val download: DownloadState? = null,
    val format: ExportFormat = ExportFormat.EPUB,
    val concurrency: Int = 5,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = NodeStore(app.applicationContext)
    private val repo = NodeRepository()
    private val engine = DownloadEngine(
        fetch = { ch -> fetchChapter(ch) },
        loadCache = { bookId, itemId -> loadCache(bookId, itemId) },
        saveCache = { bookId, itemId, c -> saveCache(bookId, itemId, c) },
        concurrency = 5,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var downloadJob: Job? = null

    init {
        // 只读一次快照，而不是 collect 整条 flow：
        // 若持续 collect，落盘回灌的旧快照会覆盖掉用户两次点击之间的内存改动，
        // 快速连点「添加」会丢节点。这里没有外部写入方，一次性读取更安全。
        viewModelScope.launch {
            val saved = runCatching { store.nodes.first() }.getOrDefault(emptyList())
            repo.replaceAll(saved)
            _ui.update { it.copy(nodes = repo.nodes) }
        }
    }

    /** 内存态变更后落盘。失败只提示，不回滚内存 —— 节点丢了可以重加，卡住界面不行。 */
    private fun persist() {
        viewModelScope.launch {
            runCatching { store.save(repo.nodes) }
                .onFailure { _ui.update { s -> s.copy(message = "节点保存失败：${it.message}") } }
        }
    }

    fun addNode(name: String, url: String) {
        if (!NodeTester.isValidHttpUrl(url)) {
            _ui.update { it.copy(message = "地址无效，需以 http:// 或 https:// 开头") }
            return
        }
        val node = NodeConfig(
            id = "u${System.currentTimeMillis()}",
            name = name.ifBlank { NodeTester.normalize(url) },
            baseUrl = NodeTester.normalize(url),
        )
        if (repo.add(node)) {
            _ui.update { it.copy(nodes = repo.nodes, message = "已添加") }
            persist()
        } else {
            _ui.update { it.copy(message = "该地址已存在") }
        }
    }

    fun removeNode(id: String) {
        if (repo.remove(id)) {
            _ui.update { it.copy(nodes = repo.nodes) }
            persist()
        } else {
            _ui.update { it.copy(message = "该节点不可删除") }
        }
    }

    fun toggleNode(id: String) {
        repo.find(id)?.let { repo.update(it.copy(enabled = !it.enabled)) }
        _ui.update { it.copy(nodes = repo.nodes) }
        persist()
    }

    fun testNodes() {
        viewModelScope.launch {
            _ui.update { it.copy(testing = true) }
            val list = repo.enabledNodes()
            val res = NodeTester.testAll(list)
            _ui.update {
                it.copy(
                    testing = false,
                    latencies = res.associateBy { r -> r.nodeId },
                    message = buildString {
                        val ok = res.count { r -> r.ok }
                        append("测速完成：$ok/${res.size} 可用")
                        val best = NodeTester.pickBest(res)?.let { id -> list.firstOrNull { n -> n.id == id } }
                        if (best != null) append("，最快 ${best.name}")
                    }
                )
            }
        }
    }

    // ---------- 搜索 / 抓取 ----------

    fun onQueryChange(q: String) = _ui.update { it.copy(query = q) }

    fun search() {
        val q = _ui.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(searching = true) }
            runCatching {
                // TODO: 对接真实数据源；这里保留占位，避免无节点时崩溃
                emptyList<BookInfo>()
            }.onSuccess { list ->
                _ui.update { it.copy(searching = false, results = list) }
            }.onFailure { e ->
                _ui.update { it.copy(searching = false, message = "搜索失败：${e.message}") }
            }
        }
    }

    private suspend fun fetchChapter(ch: Chapter): ChapterContent =
        withContext(Dispatchers.IO) {
            // TODO: 对接真实数据源
            ChapterContent(ch.title, "")
        }

    /**
     * 缓存文件格式：第一行是章节标题，其余是正文。
     * 拆成多目录避免单目录下几千个 inode。
     */
    private suspend fun loadCache(bookId: String, itemId: String): ChapterContent? =
        withContext(Dispatchers.IO) {
            val f = File(getApplication<Application>().filesDir, "cache/$bookId/$itemId.txt")
            if (!f.isFile) return@withContext null
            val text = f.readText()
            if (text.isBlank()) return@withContext null
            val nl = text.indexOf('\n')
            if (nl < 0) return@withContext ChapterContent(text, "")
            ChapterContent(text.substring(0, nl), text.substring(nl + 1))
        }

    private suspend fun saveCache(bookId: String, itemId: String, c: ChapterContent) =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(getApplication<Application>().filesDir, "cache/$bookId")
                    .apply { mkdirs() }
                File(dir, "$itemId.txt").writeText("${c.title}\n${c.text}")
            }
        }

    // ---------- 下载与导出 ----------

    fun setFormat(f: ExportFormat) = _ui.update { it.copy(format = f) }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _ui.update { it.copy(download = null) }
    }

    fun startDownload(req: DownloadRequest) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val chapters = _ui.value.chapters.ifEmpty { return@launch }
            val picked = slice(chapters, req.startChapter, req.endChapter)
            try {
                val outcome = engine.run(req, picked) { done, total, msg ->
                    _ui.update {
                        it.copy(
                            download = DownloadState.Progress(
                                done, total, msg,
                                max(0, (done * 95 / total)),
                            )
                        )
                    }
                }
                val meta = _ui.value.selected ?: BookInfo(req.bookId, req.title)
                val (name, _) = writeExport(meta, outcome.chapters, req.format)
                _ui.update {
                    it.copy(
                        download = DownloadState.Done(
                            filename = name,
                            done = outcome.chapters.size,
                            total = picked.size,
                            errorCount = outcome.errorCount,
                            format = req.format,
                        )
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(download = DownloadState.Failed(e.message ?: "未知错误")) }
            }
        }
    }

    private suspend fun writeExport(
        meta: BookInfo,
        chapters: List<ChapterContent>,
        format: ExportFormat,
    ): Pair<String, Uri?> = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${safeName(meta.title)}-${meta.id}-$stamp.${format.ext}"
        val ctx = getApplication<Application>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, format.mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/YanReader")
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建文件")
            resolver.openOutputStream(uri)?.use { os ->
                when (format) {
                    ExportFormat.TXT -> TxtWriter.write(os, meta, chapters)
                    ExportFormat.EPUB -> EpubWriter.write(
                        os, meta.title, meta.author, meta.id,
                        chapters.map { it.title to it.text }
                    )
                }
            } ?: throw IllegalStateException("无法写入文件")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            name to uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "YanReader"
            ).apply { mkdirs() }
            val f = File(dir, name)
            f.outputStream().use { os ->
                when (format) {
                    ExportFormat.TXT -> TxtWriter.write(os, meta, chapters)
                    ExportFormat.EPUB -> EpubWriter.write(
                        os, meta.title, meta.author, meta.id,
                        chapters.map { it.title to it.text }
                    )
                }
            }
            name to Uri.fromFile(f)
        }
    }

    private fun slice(list: List<Chapter>, start: Int, end: Int): List<Chapter> {
        if (list.isEmpty()) return emptyList()
        val s = start.coerceIn(0, list.lastIndex)
        val e = if (end == Int.MAX_VALUE) list.lastIndex else end.coerceIn(s, list.lastIndex)
        return list.subList(s, e + 1)
    }

    private fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "未命名" }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}
