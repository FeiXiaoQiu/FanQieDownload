package ink.yan.reader.vm

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.yan.reader.data.Appearance
import ink.yan.reader.data.BackgroundFetcher
import ink.yan.reader.data.BackgroundKind
import ink.yan.reader.data.BackgroundPresets
import ink.yan.reader.data.BackgroundScale
import ink.yan.reader.data.BackgroundSource
import ink.yan.reader.data.BookInfo
import ink.yan.reader.data.Chapter
import ink.yan.reader.data.ChapterContent
import ink.yan.reader.data.CornerStyle
import ink.yan.reader.data.DownloadEngine
import ink.yan.reader.data.DownloadRequest
import ink.yan.reader.data.DownloadState
import ink.yan.reader.data.ExportFormat
import ink.yan.reader.data.GlassStrength
import ink.yan.reader.data.HitokotoClient
import ink.yan.reader.data.HitokotoPresets
import ink.yan.reader.data.HitokotoSource
import ink.yan.reader.data.NodeConfig
import ink.yan.reader.data.NodeLatency
import ink.yan.reader.data.NodeRepository
import ink.yan.reader.data.NodeTester
import ink.yan.reader.data.StylePreset
import ink.yan.reader.data.export.EpubWriter
import ink.yan.reader.data.export.TxtWriter
import ink.yan.reader.store.AppearanceStore
import ink.yan.reader.store.BackgroundPrefs
import ink.yan.reader.store.HitokotoPrefs
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

    // —— 外观 / 背景 / 一言 ——
    val appearance: Appearance = Appearance(),
    val background: BackgroundPrefs = BackgroundPrefs(),
    val hitokoto: HitokotoPrefs = HitokotoPrefs(),
    /** 已解析出的图片地址，交给 Coil 加载；null 表示还没有可用背景 */
    val backgroundUrl: String? = null,
    val hitokotoText: String = "",
    val bgRefreshing: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = NodeStore(app.applicationContext)
    private val lookStore = AppearanceStore(app.applicationContext)
    private val hitokotoClient = HitokotoClient()
    private val backgroundFetcher = BackgroundFetcher()
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

        // 外观设置同样只读一次快照，理由比节点更充分：
        // 滑杆是高频写入，若持续 collect，落盘回灌的旧值会把用户正在拖的
        // 数值拽回去，表现为「滑杆自己往回跳」。
        viewModelScope.launch {
            val look = runCatching { lookStore.appearance.first() }.getOrDefault(Appearance())
            val bg = runCatching { lookStore.background.first() }.getOrDefault(BackgroundPrefs())
            val hk = runCatching { lookStore.hitokoto.first() }.getOrDefault(HitokotoPrefs())
            val fmt = runCatching { lookStore.format.first() }.getOrDefault(ExportFormat.EPUB)
            _ui.update { it.copy(appearance = look, background = bg, hitokoto = hk, format = fmt) }
            if (hk.enabled) refreshHitokoto()
            refreshBackground(bust = false)
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

    fun setFormat(f: ExportFormat) {
        _ui.update { it.copy(format = f) }
        viewModelScope.launch { runCatching { lookStore.saveFormat(f) } }
    }

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

    // ---------- 外观 ----------

    fun setPreset(p: StylePreset) {
        val next = _ui.value.appearance.withPreset(p)
        _ui.update { it.copy(appearance = next) }
        persistLook(next)
    }

    fun setGlassStrength(s: GlassStrength) = updateLook { it.copy(strength = s) }

    fun setCornerStyle(c: CornerStyle) = updateLook { it.copy(corner = c) }

    fun resetTweaks() = updateLook { it.resetTweaks() }

    private fun updateLook(transform: (Appearance) -> Appearance) {
        val next = transform(_ui.value.appearance)
        _ui.update { it.copy(appearance = next) }
        persistLook(next)
    }

    // ---------- 背景 ----------

    /**
     * 解析当前背景源的图片地址。
     *
     * @param bust 是否绕过缓存。用户点「换一张」必须为 true，否则 Coil 命中
     *             旧缓存、界面毫无反应 —— 这是最容易当成「功能没做」的坑。
     */
    fun refreshBackground(bust: Boolean = true) {
        viewModelScope.launch {
            val prefs = _ui.value.background
            val src = prefs.sources.find { it.id == prefs.sourceId }
            if (src == null) {
                _ui.update {
                    it.copy(
                        backgroundUrl = null,
                        bgRefreshing = false,
                        message = "背景源已不存在，回退纯色",
                    )
                }
                return@launch
            }
            _ui.update { it.copy(bgRefreshing = true) }
            val url = backgroundFetcher.resolveUrl(src, bust)
            _ui.update {
                it.copy(
                    backgroundUrl = url,
                    bgRefreshing = false,
                    message = if (url == null) "背景解析失败，沿用底色" else null,
                )
            }
        }
    }

    fun selectBackgroundSource(id: String) {
        val next = _ui.value.background.copy(sourceId = id, localPath = "")
        _ui.update { it.copy(background = next) }
        persistBg(next)
        refreshBackground(bust = false)
    }

    /** @param commit 滑杆松手时才落盘，拖动过程中只改内存 */
    fun setBackgroundScale(s: BackgroundScale) {
        val next = _ui.value.background.copy(scale = s)
        _ui.update { it.copy(background = next) }
        persistBg(next)
    }

    fun setBackgroundBlur(v: Float, commit: Boolean = false) = tweakBg(commit) {
        it.copy(blurDp = v.coerceIn(0f, 40f))
    }

    fun setBackgroundScrim(v: Float, commit: Boolean = false) = tweakBg(commit) {
        it.copy(scrimAlpha = v.coerceIn(0f, 0.9f))
    }

    private fun tweakBg(commit: Boolean, transform: (BackgroundPrefs) -> BackgroundPrefs) {
        val next = transform(_ui.value.background)
        _ui.update { it.copy(background = next) }
        if (commit) persistBg(next)
    }

    fun setShowMature(on: Boolean) {
        val cur = _ui.value.background
        val next = if (!on && BackgroundPresets.byId(cur.sourceId)?.mature == true) {
            // 关掉开关时若正选中成熟源，必须一并切走，否则会停在不可见的选择上
            cur.copy(showMature = false, sourceId = BackgroundPresets.ALCY.id)
        } else {
            cur.copy(showMature = on)
        }
        _ui.update { it.copy(background = next) }
        persistBg(next)
        refreshBackground(bust = false)
    }

    fun useLocalBackground(path: String) {
        val next = _ui.value.background.copy(localPath = path)
        _ui.update { it.copy(background = next) }
        persistBg(next)
    }

    fun clearLocalBackground() {
        val next = _ui.value.background.copy(localPath = "")
        _ui.update { it.copy(background = next) }
        persistBg(next)
        refreshBackground(bust = false)
    }

    /**
     * 从相册选的图要复制到私有目录再用。
     *
     * 不能图省事直接存 content:// URI：那是一次性授权，进程重启后就没权限了，
     * 背景会静默变回纯色，而且很难排查。
     */
    fun importLocalBackground(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = runCatching {
                val app = getApplication<Application>()
                val dir = File(app.filesDir, "backgrounds").apply { mkdirs() }
                val dest = File(dir, "custom_bg.img")
                val input = app.contentResolver.openInputStream(uri)
                    ?: error("无法读取所选图片")
                input.use { src -> dest.outputStream().use { dst -> src.copyTo(dst) } }
                require(dest.isFile && dest.length() > 0L) { "图片保存失败" }
                dest.absolutePath
            }.getOrElse { e ->
                _ui.update { it.copy(message = "背景导入失败：${e.message}") }
                return@launch
            }
            useLocalBackground(path)
        }
    }

    fun addCustomBackground(name: String, url: String, kind: BackgroundKind, jsonPath: String) {
        val trimmed = url.trim()
        if (!NodeTester.isValidHttpUrl(trimmed)) {
            _ui.update { it.copy(message = "地址无效，需以 http:// 或 https:// 开头") }
            return
        }
        val src = BackgroundSource(
            id = "cbg-" + System.currentTimeMillis().toString(36),
            name = name.ifBlank { "自定义接口" },
            url = trimmed,
            kind = kind,
            jsonPath = jsonPath.trim(),
        )
        val next = _ui.value.background.copy(
            customSources = _ui.value.background.customSources + src,
            sourceId = src.id,
            localPath = "",
        )
        _ui.update { it.copy(background = next) }
        persistBg(next)
        refreshBackground(bust = false)
    }

    fun removeCustomBackground(id: String) {
        val cur = _ui.value.background
        val next = cur.copy(
            customSources = cur.customSources.filterNot { it.id == id },
            sourceId = if (cur.sourceId == id) BackgroundPresets.ALCY.id else cur.sourceId,
        )
        _ui.update { it.copy(background = next) }
        persistBg(next)
        if (cur.sourceId == id) refreshBackground(bust = false)
    }

    // ---------- 一言 ----------

    fun refreshHitokoto() {
        val prefs = _ui.value.hitokoto
        val src = prefs.sources.find { it.id == prefs.sourceId }
        viewModelScope.launch {
            val text = hitokotoClient.fetchOrFallback(src?.url.orEmpty())
            _ui.update { it.copy(hitokotoText = text) }
        }
    }

    fun selectHitokotoSource(id: String) {
        val next = _ui.value.hitokoto.copy(sourceId = id)
        _ui.update { it.copy(hitokoto = next) }
        persistHk(next)
        refreshHitokoto()
    }

    fun setHitokotoEnabled(on: Boolean) {
        val next = _ui.value.hitokoto.copy(enabled = on)
        _ui.update { it.copy(hitokoto = next) }
        persistHk(next)
        if (on) refreshHitokoto()
    }

    fun addCustomHitokoto(name: String, url: String) {
        val trimmed = url.trim()
        if (!NodeTester.isValidHttpUrl(trimmed)) {
            _ui.update { it.copy(message = "地址无效，需以 http:// 或 https:// 开头") }
            return
        }
        val src = HitokotoSource(
            id = "chk-" + System.currentTimeMillis().toString(36),
            name = name.ifBlank { "自定义接口" },
            url = trimmed,
        )
        val next = _ui.value.hitokoto.copy(
            customSources = _ui.value.hitokoto.customSources + src,
            sourceId = src.id,
        )
        _ui.update { it.copy(hitokoto = next) }
        persistHk(next)
        refreshHitokoto()
    }

    fun removeCustomHitokoto(id: String) {
        val cur = _ui.value.hitokoto
        val next = cur.copy(
            customSources = cur.customSources.filterNot { it.id == id },
            sourceId = if (cur.sourceId == id) HitokotoPresets.MIXED.id else cur.sourceId,
        )
        _ui.update { it.copy(hitokoto = next) }
        persistHk(next)
        if (cur.sourceId == id) refreshHitokoto()
    }

    // ---------- 落盘 ----------

    private fun persistLook(a: Appearance) {
        viewModelScope.launch { runCatching { lookStore.saveLook(a) } }
    }

    private fun persistBg(p: BackgroundPrefs) {
        viewModelScope.launch { runCatching { lookStore.saveBackground(p) } }
    }

    private fun persistHk(p: HitokotoPrefs) {
        viewModelScope.launch { runCatching { lookStore.saveHitokoto(p) } }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}
