package com.feixiaoqiu.fanqiedl.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feixiaoqiu.fanqiedl.data.AppContainer
import com.feixiaoqiu.fanqiedl.data.BackgroundMode
import com.feixiaoqiu.fanqiedl.data.BookInfo
import com.feixiaoqiu.fanqiedl.data.BookSummary
import com.feixiaoqiu.fanqiedl.data.ChapterContent
import com.feixiaoqiu.fanqiedl.data.ChapterRef
import com.feixiaoqiu.fanqiedl.data.DefaultNodes
import com.feixiaoqiu.fanqiedl.data.DownloadProgress
import com.feixiaoqiu.fanqiedl.data.DownloadRequest
import com.feixiaoqiu.fanqiedl.data.DownloadResult
import com.feixiaoqiu.fanqiedl.data.NoEnabledNodeException
import com.feixiaoqiu.fanqiedl.data.NodeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NodeProbePhase {
    Idle,
    Probing,
    Ok,
    Fail,
    Timeout,
}

data class NodeProbeInfo(
    val phase: NodeProbePhase = NodeProbePhase.Idle,
    val latencyMs: Long? = null,
    val error: String? = null,
)

data class MainUiState(
    val query: String = "",
    val searching: Boolean = false,
    val loadingMore: Boolean = false,
    val books: List<BookSummary> = emptyList(),
    val searchError: String? = null,
    val searchHasMore: Boolean = false,
    val searchNextOffset: Int = 0,
    val lastSearchQuery: String = "",
    val hitokoto: String = "",
    val selected: BookSummary? = null,
    val detail: BookInfo? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val showDownloadOptions: Boolean = false,
    val startChapter: String = "",
    val endChapter: String = "",
    val resume: Boolean = true,
    val downloading: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val downloadResult: DownloadResult? = null,
    val snackbar: String? = null,
    val nodes: List<NodeConfig> = emptyList(),
    val hitokotoUrl: String = DefaultNodes.DEFAULT_HITOKOTO,
    val probeMessage: String? = null,
    val nodeProbes: Map<String, NodeProbeInfo> = emptyMap(),
    val probingAll: Boolean = false,
    val backgroundMode: BackgroundMode = BackgroundMode.DEFAULT,
    val backgroundApiUrl: String = "",
    val backgroundImageUrl: String = "",
    val backgroundDisplayUrl: String = DefaultNodes.DEFAULT_BACKGROUND_API,
    val reading: Boolean = false,
    val readerTitle: String = "",
    val readerChapters: List<ChapterRef> = emptyList(),
    val readerIndex: Int = 0,
    val readerContent: ChapterContent? = null,
    val readerLoading: Boolean = false,
    val readerError: String? = null,
    val showCatalog: Boolean = false,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val updateChecking: Boolean = false,
    val updateMessage: String? = null,
    val latestReleaseUrl: String? = null,
    val latestVersionTag: String? = null,
    val updateAvailable: Boolean = false,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(
        MainUiState(
            appVersionName = container.appVersionName,
            appVersionCode = container.appVersionCode,
        ),
    )
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private var downloadJob: Job? = null
    private var hitokotoJob: Job? = null
    private var readerJob: Job? = null
    private var updateJob: Job? = null
    private var probeAllJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.nodesFlow.collect { nodes ->
                _ui.update { s ->
                    val keep = s.nodeProbes.filterKeys { id -> nodes.any { it.id == id } }
                    s.copy(nodes = nodes, nodeProbes = keep)
                }
            }
        }
        viewModelScope.launch {
            container.settings.hitokotoUrlFlow.collect { url ->
                _ui.update { it.copy(hitokotoUrl = url) }
            }
        }
        viewModelScope.launch {
            combine(
                container.settings.backgroundModeFlow,
                container.settings.backgroundApiUrlFlow,
                container.settings.backgroundImageUrlFlow,
            ) { mode, api, image -> Triple(mode, api, image) }.collect { (mode, api, image) ->
                _ui.update {
                    it.copy(
                        backgroundMode = mode,
                        backgroundApiUrl = api,
                        backgroundImageUrl = image,
                        backgroundDisplayUrl = resolveBackgroundUrl(mode, api, image, bust = true),
                    )
                }
            }
        }
        startHitokotoLoop()
        // 进入设置可手动检查；冷启动静默检查一次
        checkForUpdate(silent = true)
    }

    private fun resolveBackgroundUrl(
        mode: BackgroundMode,
        apiUrl: String,
        imageUrl: String,
        bust: Boolean,
    ): String {
        return when (mode) {
            BackgroundMode.DEFAULT -> {
                val base = DefaultNodes.DEFAULT_BACKGROUND_API
                if (bust) cacheBust(base) else base
            }
            BackgroundMode.CUSTOM_API -> {
                val base = apiUrl.trim().ifEmpty { DefaultNodes.DEFAULT_BACKGROUND_API }
                if (bust) cacheBust(base) else base
            }
            BackgroundMode.CUSTOM_IMAGE -> {
                val local = container.backgroundImages.localPathOrEmpty()
                when {
                    local.isNotBlank() -> local
                    imageUrl.isNotBlank() -> imageUrl.trim()
                    else -> ""
                }
            }
        }
    }

    /** Coil 可直接加载的背景 model（File / URL 字符串） */
    fun backgroundModel(pathOrUrl: String): Any? {
        return container.backgroundImages.displayModel(pathOrUrl)
    }

    private fun cacheBust(url: String): String {
        val sep = if (url.contains("?")) "&" else "?"
        return url + sep + "_t=" + System.currentTimeMillis()
    }

    private fun startHitokotoLoop() {
        hitokotoJob?.cancel()
        hitokotoJob = viewModelScope.launch {
            while (isActive) {
                val url = _ui.value.hitokotoUrl
                val text = withContext(Dispatchers.IO) {
                    container.hitokoto.fetchOrFallback(url)
                }
                _ui.update { it.copy(hitokoto = text) }
                delay(HITOKOTO_INTERVAL_MS)
            }
        }
    }

    fun onQueryChange(q: String) {
        _ui.update { it.copy(query = q) }
    }

    fun consumeSnackbar() {
        _ui.update { it.copy(snackbar = null) }
    }

    fun refreshHitokoto() {
        viewModelScope.launch {
            val url = _ui.value.hitokotoUrl
            val text = withContext(Dispatchers.IO) {
                container.hitokoto.fetchOrFallback(url)
            }
            _ui.update { it.copy(hitokoto = text) }
        }
    }

    fun search(exampleIfEmpty: Boolean = true) {
        viewModelScope.launch {
            var q = _ui.value.query.trim()
            if (q.isEmpty() && exampleIfEmpty) {
                q = "抽象职校生存手册"
                _ui.update { it.copy(query = q) }
            }
            if (q.isEmpty()) return@launch
            _ui.update {
                it.copy(
                    searching = true,
                    searchError = null,
                    books = emptyList(),
                    searchHasMore = false,
                    searchNextOffset = 0,
                    lastSearchQuery = q,
                )
            }
            try {
                val page = withContext(Dispatchers.IO) { container.client.search(q, 0) }
                _ui.update {
                    it.copy(
                        searching = false,
                        books = page.books,
                        searchNextOffset = page.nextOffset,
                        searchHasMore = page.hasMore,
                        searchError = if (page.books.isEmpty()) "未找到相关书籍" else null,
                    )
                }
            } catch (e: NoEnabledNodeException) {
                _ui.update {
                    it.copy(searching = false, searchError = e.message, snackbar = e.message)
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        searching = false,
                        searchError = "搜索失败：${e.message ?: e}",
                        books = emptyList(),
                    )
                }
            }
        }
    }

    fun loadMoreSearch() {
        val s = _ui.value
        if (s.searching || s.loadingMore || !s.searchHasMore || s.lastSearchQuery.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            try {
                val page = withContext(Dispatchers.IO) {
                    container.client.search(s.lastSearchQuery, s.searchNextOffset)
                }
                val merged = (s.books + page.books).distinctBy { it.bookId }
                _ui.update {
                    it.copy(
                        loadingMore = false,
                        books = merged,
                        searchNextOffset = page.nextOffset,
                        searchHasMore = page.hasMore && page.books.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loadingMore = false,
                        snackbar = "加载更多失败：${e.message ?: e}",
                    )
                }
            }
        }
    }

    fun openDetail(book: BookSummary) {
        _ui.update {
            it.copy(
                selected = book,
                detail = null,
                detailLoading = true,
                detailError = null,
            )
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { container.client.info(book.bookId) }
                _ui.update { it.copy(detailLoading = false, detail = info) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        detailLoading = false,
                        detail = BookInfo(book.bookId, book.title, book.author, book.description),
                        detailError = e.message,
                    )
                }
            }
        }
    }

    fun closeDetail() {
        _ui.update {
            it.copy(
                selected = null,
                detail = null,
                detailError = null,
                showDownloadOptions = false,
            )
        }
    }

    fun openDownloadOptions() {
        _ui.update { it.copy(showDownloadOptions = true, startChapter = "", endChapter = "", resume = true) }
    }

    fun closeDownloadOptions() {
        _ui.update { it.copy(showDownloadOptions = false) }
    }

    fun setStartChapter(v: String) = _ui.update { it.copy(startChapter = v.filter { c -> c.isDigit() }) }
    fun setEndChapter(v: String) = _ui.update { it.copy(endChapter = v.filter { c -> c.isDigit() }) }
    fun setResume(v: Boolean) = _ui.update { it.copy(resume = v) }

    fun openReader() {
        val book = _ui.value.selected ?: return
        val title = _ui.value.detail?.title ?: book.title
        readerJob?.cancel()
        _ui.update {
            it.copy(
                reading = true,
                readerTitle = title,
                readerChapters = emptyList(),
                readerIndex = 0,
                readerContent = null,
                readerLoading = true,
                readerError = null,
                showCatalog = false,
            )
        }
        readerJob = viewModelScope.launch {
            try {
                val chapters = withContext(Dispatchers.IO) { container.client.catalog(book.bookId) }
                if (chapters.isEmpty()) {
                    _ui.update {
                        it.copy(readerLoading = false, readerError = "目录为空")
                    }
                    return@launch
                }
                _ui.update { it.copy(readerChapters = chapters, readerIndex = 0) }
                loadChapterAt(0)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(readerLoading = false, readerError = e.message ?: "打开阅读失败")
                }
            }
        }
    }

    fun closeReader() {
        readerJob?.cancel()
        readerJob = null
        _ui.update {
            it.copy(
                reading = false,
                readerContent = null,
                readerChapters = emptyList(),
                readerError = null,
                readerLoading = false,
                showCatalog = false,
            )
        }
    }

    fun toggleCatalog(show: Boolean) {
        _ui.update { it.copy(showCatalog = show) }
    }

    fun goChapter(index: Int) {
        val chapters = _ui.value.readerChapters
        if (index !in chapters.indices) return
        loadChapterAt(index)
    }

    fun prevChapter() {
        val i = _ui.value.readerIndex
        if (i > 0) loadChapterAt(i - 1)
    }

    fun nextChapter() {
        val i = _ui.value.readerIndex
        val max = _ui.value.readerChapters.lastIndex
        if (i < max) loadChapterAt(i + 1)
    }

    private fun loadChapterAt(index: Int) {
        val chapters = _ui.value.readerChapters
        if (index !in chapters.indices) return
        val ch = chapters[index]
        readerJob?.cancel()
        _ui.update {
            it.copy(
                readerIndex = index,
                readerLoading = true,
                readerError = null,
                showCatalog = false,
            )
        }
        readerJob = viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { container.client.content(ch.itemId) }
                val title = content.title.ifBlank { ch.title }
                _ui.update {
                    it.copy(
                        readerLoading = false,
                        readerContent = content.copy(title = title),
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        readerLoading = false,
                        readerContent = null,
                        readerError = e.message ?: "正文加载失败",
                    )
                }
            }
        }
    }

    fun startDownload() {
        val book = _ui.value.selected ?: return
        val start = _ui.value.startChapter.toIntOrNull() ?: 0
        val end = _ui.value.endChapter.toIntOrNull() ?: 0
        downloadJob?.cancel()
        _ui.update {
            it.copy(
                downloading = true,
                downloadProgress = DownloadProgress(0, 0, "准备中…", 0),
                downloadResult = null,
                showDownloadOptions = false,
            )
        }
        downloadJob = viewModelScope.launch {
            try {
                container.downloads.download(
                    DownloadRequest(
                        bookId = book.bookId,
                        title = book.title,
                        startChapter = start,
                        endChapter = end,
                        resume = _ui.value.resume,
                    )
                ).collect { event ->
                    when (event) {
                        is DownloadProgress -> _ui.update { it.copy(downloadProgress = event) }
                        is DownloadResult -> _ui.update {
                            it.copy(
                                downloading = false,
                                downloadProgress = null,
                                downloadResult = event,
                                snackbar = event.message,
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _ui.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = null,
                        snackbar = "已取消下载",
                    )
                }
            } catch (e: NoEnabledNodeException) {
                _ui.update {
                    it.copy(downloading = false, downloadProgress = null, snackbar = e.message)
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = null,
                        snackbar = "下载失败：${e.message ?: e}",
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun dismissDownloadResult() {
        _ui.update { it.copy(downloadResult = null) }
    }

    fun setHitokotoUrl(url: String) {
        _ui.update { it.copy(hitokotoUrl = url) }
    }

    fun setBackgroundMode(mode: BackgroundMode) {
        _ui.update { it.copy(backgroundMode = mode) }
    }

    fun setBackgroundApiUrl(url: String) {
        _ui.update { it.copy(backgroundApiUrl = url) }
    }

    fun setBackgroundImageUrl(url: String) {
        _ui.update { it.copy(backgroundImageUrl = url) }
    }

    fun importLocalBackground(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    container.backgroundImages.saveFromUri(uri)
                }
                container.settings.setBackground(
                    mode = BackgroundMode.CUSTOM_IMAGE,
                    apiUrl = _ui.value.backgroundApiUrl,
                    imageUrl = path,
                )
                _ui.update {
                    it.copy(
                        backgroundMode = BackgroundMode.CUSTOM_IMAGE,
                        backgroundImageUrl = path,
                        backgroundDisplayUrl = path,
                        snackbar = "本地背景已保存",
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(snackbar = "导入失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    fun clearLocalBackground() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.backgroundImages.clear() }
            container.settings.setBackground(
                mode = BackgroundMode.DEFAULT,
                apiUrl = _ui.value.backgroundApiUrl,
                imageUrl = "",
            )
            _ui.update {
                it.copy(
                    backgroundMode = BackgroundMode.DEFAULT,
                    backgroundImageUrl = "",
                    backgroundDisplayUrl = resolveBackgroundUrl(
                        BackgroundMode.DEFAULT,
                        it.backgroundApiUrl,
                        "",
                        bust = true,
                    ),
                    snackbar = "已清除本地背景，恢复默认图床",
                )
            }
        }
    }

    fun saveBackground() {
        viewModelScope.launch {
            val s = _ui.value
            when (s.backgroundMode) {
                BackgroundMode.CUSTOM_API -> {
                    if (s.backgroundApiUrl.isNotBlank() && !DefaultNodes.isValidHttpUrl(s.backgroundApiUrl)) {
                        _ui.update { it.copy(snackbar = "请输入有效的图床 API 地址") }
                        return@launch
                    }
                }
                BackgroundMode.CUSTOM_IMAGE -> {
                    val hasLocal = container.backgroundImages.hasLocal() ||
                        s.backgroundImageUrl.isNotBlank()
                    if (!hasLocal) {
                        _ui.update { it.copy(snackbar = "请先从相册选择一张图片") }
                        return@launch
                    }
                }
                BackgroundMode.DEFAULT -> Unit
            }
            val imagePath = when (s.backgroundMode) {
                BackgroundMode.CUSTOM_IMAGE ->
                    container.backgroundImages.localPathOrEmpty().ifBlank { s.backgroundImageUrl }
                else -> s.backgroundImageUrl
            }
            container.settings.setBackground(
                mode = s.backgroundMode,
                apiUrl = s.backgroundApiUrl,
                imageUrl = imagePath,
            )
            _ui.update {
                it.copy(
                    backgroundImageUrl = imagePath,
                    backgroundDisplayUrl = resolveBackgroundUrl(
                        s.backgroundMode,
                        s.backgroundApiUrl,
                        imagePath,
                        bust = true,
                    ),
                    snackbar = "背景已保存",
                )
            }
        }
    }

    fun refreshBackground() {
        val s = _ui.value
        if (s.backgroundMode == BackgroundMode.CUSTOM_IMAGE) {
            _ui.update { it.copy(snackbar = "当前为本地图片，无需刷新") }
            return
        }
        _ui.update {
            it.copy(
                backgroundDisplayUrl = resolveBackgroundUrl(
                    s.backgroundMode,
                    s.backgroundApiUrl,
                    s.backgroundImageUrl,
                    bust = true,
                ),
            )
        }
    }

    fun saveHitokotoUrl() {
        viewModelScope.launch {
            container.settings.setHitokotoUrl(_ui.value.hitokotoUrl)
            refreshHitokoto()
            _ui.update { it.copy(snackbar = "一言地址已保存") }
        }
    }

    fun testHitokoto() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    container.hitokoto.fetch(_ui.value.hitokotoUrl)
                } catch (e: Exception) {
                    "失败：${e.message}"
                }
            }
            _ui.update { it.copy(hitokoto = text, snackbar = "一言测试完成") }
        }
    }

    fun addNode(baseUrl: String) {
        viewModelScope.launch {
            if (!DefaultNodes.isValidHttpUrl(baseUrl)) {
                _ui.update { it.copy(snackbar = "请输入有效的 http(s) 地址") }
                return@launch
            }
            container.settings.addNode("", baseUrl)
            _ui.update { it.copy(snackbar = "节点已添加") }
        }
    }

    fun removeNode(id: String) {
        viewModelScope.launch {
            val next = _ui.value.nodes.filterNot { it.id == id }
            if (next.isEmpty()) {
                _ui.update { it.copy(snackbar = "至少保留一个节点，或点「恢复默认」") }
                return@launch
            }
            container.settings.removeNode(id)
        }
    }

    fun updateNodeUrl(id: String, baseUrl: String) {
        viewModelScope.launch {
            if (!DefaultNodes.isValidHttpUrl(baseUrl)) {
                _ui.update { it.copy(snackbar = "请输入有效的 http(s) 地址") }
                return@launch
            }
            val n = _ui.value.nodes.find { it.id == id } ?: return@launch
            container.settings.updateNode(n.copy(baseUrl = baseUrl, enabled = true))
            _ui.update { it.copy(snackbar = "节点已更新") }
        }
    }

    fun restoreNodes() {
        viewModelScope.launch {
            container.settings.restoreDefaultNodes()
            _ui.update { it.copy(snackbar = "已恢复默认节点") }
        }
    }

    fun probeNode(baseUrl: String) {
        viewModelScope.launch {
            val node = _ui.value.nodes.find {
                DefaultNodes.normalizeBaseUrl(it.baseUrl) == DefaultNodes.normalizeBaseUrl(baseUrl)
            }
            if (node != null) {
                setProbe(node.id, NodeProbeInfo(phase = NodeProbePhase.Probing))
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { container.client.probe(baseUrl, timeoutMs = PROBE_TIMEOUT_MS) }
            }
            if (node != null) {
                applyProbeResult(node.id, result)
            }
            val msg = result.fold(
                onSuccess = { ms -> "可用 · ${formatLatency(ms)}" },
                onFailure = { e ->
                    if (isTimeout(e)) "超时（>${PROBE_TIMEOUT_MS / 1000}s）"
                    else "失败：${e.message}"
                },
            )
            _ui.update { it.copy(probeMessage = msg, snackbar = msg) }
        }
    }

    fun probeAllNodes() {
        if (probeAllJob?.isActive == true) return
        val list = _ui.value.nodes
        if (list.isEmpty()) {
            _ui.update { it.copy(snackbar = "没有可测活的节点") }
            return
        }
        probeAllJob = viewModelScope.launch {
            _ui.update { it.copy(probingAll = true, probeMessage = "一键测活中…") }
            list.forEach { node ->
                setProbe(node.id, NodeProbeInfo(phase = NodeProbePhase.Probing))
            }
            // 串行测活，避免同时打满节点
            for (node in list) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { container.client.probe(node.baseUrl, timeoutMs = PROBE_TIMEOUT_MS) }
                }
                applyProbeResult(node.id, result)
            }
            _ui.update {
                it.copy(
                    probingAll = false,
                    probeMessage = "测活完成",
                    snackbar = "节点测活完成",
                )
            }
        }
    }

    private fun setProbe(id: String, info: NodeProbeInfo) {
        _ui.update { s ->
            s.copy(nodeProbes = s.nodeProbes + (id to info))
        }
    }

    private fun applyProbeResult(id: String, result: Result<Long>) {
        result.fold(
            onSuccess = { ms ->
                setProbe(id, NodeProbeInfo(phase = NodeProbePhase.Ok, latencyMs = ms))
            },
            onFailure = { e ->
                if (isTimeout(e)) {
                    setProbe(id, NodeProbeInfo(phase = NodeProbePhase.Timeout, error = "超时"))
                } else {
                    setProbe(
                        id,
                        NodeProbeInfo(
                            phase = NodeProbePhase.Fail,
                            error = e.message?.take(40) ?: "失败",
                        ),
                    )
                }
            },
        )
    }

    private fun isTimeout(e: Throwable): Boolean {
        val msg = (e.message ?: "").lowercase()
        return e is java.net.SocketTimeoutException ||
            e is java.io.InterruptedIOException ||
            msg.contains("timeout") ||
            msg.contains("timed out")
    }

    private fun formatLatency(ms: Long): String {
        return when {
            ms <= 500L -> "${ms}ms"
            ms < 1000L -> String.format("%.2fs", ms / 1000.0)
            else -> String.format("%.1fs", ms / 1000.0)
        }
    }

    fun checkForUpdate(silent: Boolean = false) {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            if (!silent) {
                _ui.update { it.copy(updateChecking = true, updateMessage = "正在检查更新…") }
            }
            val result = withContext(Dispatchers.IO) {
                container.updateChecker.checkLatest()
            }
            result.fold(
                onSuccess = { info ->
                    val current = normalizeVersion(_ui.value.appVersionName)
                    val latest = normalizeVersion(info.tagName)
                    val newer = compareVersion(latest, current) > 0
                    val msg = if (newer) {
                        "发现新版本 ${info.tagName}"
                    } else {
                        "已是最新版本（${_ui.value.appVersionName}）"
                    }
                    _ui.update {
                        it.copy(
                            updateChecking = false,
                            updateAvailable = newer,
                            latestVersionTag = info.tagName,
                            latestReleaseUrl = info.htmlUrl,
                            updateMessage = msg,
                            snackbar = if (silent && !newer) null else msg,
                        )
                    }
                },
                onFailure = { e ->
                    val msg = "检查更新失败：${e.message ?: "网络错误"}"
                    _ui.update {
                        it.copy(
                            updateChecking = false,
                            updateMessage = if (silent) it.updateMessage else msg,
                            snackbar = if (silent) null else msg,
                        )
                    }
                },
            )
        }
    }

    private fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
    }

    /** a > b => 1; equal => 0; a < b => -1 */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }

    companion object {
        private const val HITOKOTO_INTERVAL_MS = 30_000L
        const val PROBE_TIMEOUT_MS = 40_000L
        const val WEB_HOME_URL = "https://fanqie-dl.feixiaoqiu.top/"
    }
}
