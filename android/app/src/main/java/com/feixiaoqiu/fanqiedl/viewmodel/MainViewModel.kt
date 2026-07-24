package com.feixiaoqiu.fanqiedl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feixiaoqiu.fanqiedl.data.AppContainer
import com.feixiaoqiu.fanqiedl.data.BookInfo
import com.feixiaoqiu.fanqiedl.data.BookSummary
import com.feixiaoqiu.fanqiedl.data.DefaultNodes
import com.feixiaoqiu.fanqiedl.data.DownloadProgress
import com.feixiaoqiu.fanqiedl.data.DownloadRequest
import com.feixiaoqiu.fanqiedl.data.DownloadResult
import com.feixiaoqiu.fanqiedl.data.NoEnabledNodeException
import com.feixiaoqiu.fanqiedl.data.NodeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val query: String = "",
    val searching: Boolean = false,
    val books: List<BookSummary> = emptyList(),
    val searchError: String? = null,
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
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.nodesFlow.collect { nodes ->
                _ui.update { it.copy(nodes = nodes) }
            }
        }
        viewModelScope.launch {
            container.settings.hitokotoUrlFlow.collect { url ->
                _ui.update { it.copy(hitokotoUrl = url) }
            }
        }
        refreshHitokoto()
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
            _ui.update { it.copy(searching = true, searchError = null) }
            try {
                val books = withContext(Dispatchers.IO) { container.client.search(q) }
                _ui.update {
                    it.copy(
                        searching = false,
                        books = books,
                        searchError = if (books.isEmpty()) "未找到相关书籍" else null,
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

    fun toggleNode(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val nodes = _ui.value.nodes.map { if (it.id == id) it.copy(enabled = enabled) else it }
            if (enabled.not() && nodes.none { it.enabled }) {
                _ui.update { it.copy(snackbar = "请至少启用一个番茄节点") }
                return@launch
            }
            container.settings.setNodes(nodes)
        }
    }

    fun addNode(name: String, baseUrl: String) {
        viewModelScope.launch {
            if (!DefaultNodes.isValidHttpUrl(baseUrl)) {
                _ui.update { it.copy(snackbar = "请输入有效的 http(s) 地址") }
                return@launch
            }
            container.settings.addNode(name, baseUrl)
            _ui.update { it.copy(snackbar = "节点已添加") }
        }
    }

    fun removeNode(id: String) {
        viewModelScope.launch {
            val target = _ui.value.nodes.find { it.id == id } ?: return@launch
            if (target.builtin) {
                _ui.update { it.copy(snackbar = "内置节点不可删除，可禁用") }
                return@launch
            }
            val next = _ui.value.nodes.filterNot { it.id == id }
            if (next.none { it.enabled }) {
                _ui.update { it.copy(snackbar = "请至少启用一个番茄节点") }
                return@launch
            }
            container.settings.removeNode(id)
        }
    }

    fun updateNodeUrl(id: String, name: String, baseUrl: String) {
        viewModelScope.launch {
            if (!DefaultNodes.isValidHttpUrl(baseUrl)) {
                _ui.update { it.copy(snackbar = "请输入有效的 http(s) 地址") }
                return@launch
            }
            val n = _ui.value.nodes.find { it.id == id } ?: return@launch
            container.settings.updateNode(n.copy(name = name.ifBlank { n.name }, baseUrl = baseUrl))
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
            _ui.update { it.copy(probeMessage = "测活中…") }
            val msg = withContext(Dispatchers.IO) {
                try {
                    val ms = container.client.probe(baseUrl)
                    "可用 · ${ms}ms"
                } catch (e: Exception) {
                    "失败：${e.message}"
                }
            }
            _ui.update { it.copy(probeMessage = msg, snackbar = msg) }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
