package ink.yan.reader.vm

import android.app.Application
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.yan.reader.data.Appearance
import ink.yan.reader.data.ApkFetcher
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
import ink.yan.reader.data.DownloadSource
import ink.yan.reader.data.DownloadState
import ink.yan.reader.data.ExportFormat
import ink.yan.reader.data.formatSize
import ink.yan.reader.data.GlassStrength
import ink.yan.reader.data.HitokotoClient
import ink.yan.reader.data.HitokotoPresets
import ink.yan.reader.data.HitokotoSource
import ink.yan.reader.data.NodeConfig
import ink.yan.reader.data.NodeClient
import ink.yan.reader.data.NodeLatency
import ink.yan.reader.data.NodePresets
import ink.yan.reader.data.NodeRepository
import ink.yan.reader.data.NodeTester
import ink.yan.reader.data.ReleaseInfo
import ink.yan.reader.data.buildDownloadCandidates
import ink.yan.reader.data.compareVersion
import ink.yan.reader.data.formatSize
import ink.yan.reader.data.pickApkAsset
import ink.yan.reader.data.resolveSourceUrl
import ink.yan.reader.data.StylePreset
import ink.yan.reader.data.export.EpubWriter
import ink.yan.reader.data.export.TxtWriter
import ink.yan.reader.store.AppearanceStore
import ink.yan.reader.store.BackgroundPrefs
import ink.yan.reader.store.HitokotoPrefs
import ink.yan.reader.store.NodeStore
import ink.yan.reader.store.UpdatePrefs
import ink.yan.reader.store.UpdateStore
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
    /**
     * 搜索的负面反馈，包括"没搜到"和"请求失败"。
     *
     * 单独开一个字段而不是复用 [message]：搜索结果为空时列表区本来就是空的，
     * 提示必须出现在那个位置才看得到；而 [message] 是全局提示，各处都在写，
     * 混在一起会出现"节点已添加"顶掉"搜索失败"的情况。
     */
    val searchError: String? = null,
    val selected: BookInfo? = null,
    val chapters: List<Chapter> = emptyList(),
    /** 正在拉目录。目录动辄几千章，不转圈的话点了像没反应 */
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
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

    // —— 应用更新 ——
    /** 安装包里读到的版本名，读不到时为空串 */
    val currentVersion: String = "",
    val updatePrefs: UpdatePrefs = UpdatePrefs(),
    /** 查到的最新版；为 null 表示还没查过或没查到 */
    val updateInfo: ReleaseInfo? = null,
    val updateChecking: Boolean = false,
    val updateMessage: String? = null,
    /** 下载进度 0..1；null 表示没在下载 */
    val apkProgress: Float? = null,
    val apkMessage: String? = null,
    /** 已下好待安装的文件，UI 拉起安装后调 consumeApk() 清空 */
    val apkFile: File? = null,

    // —— 在线阅读 ——
    /** 当前阅读的章节正文；null 表示没在阅读 */
    val reading: ChapterContent? = null,
    /** 在 chapters 里的下标，用于上一章 / 下一章 */
    val readingIndex: Int = -1,
    val readingLoading: Boolean = false,
    val readingError: String? = null,
) {
    /**
     * 查到的版本确实比当前新。
     *
     * 当前版本读不到时是空串，比较结果恒为「有更新」——
     * 宁可多提示一次，也不要因为取不到版本号而永远不提示。
     */
    val hasUpdate: Boolean
        get() = updateInfo?.let { compareVersion(it.version, currentVersion) > 0 } ?: false
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = NodeStore(app.applicationContext)
    private val lookStore = AppearanceStore(app.applicationContext)
    private val updateStore = UpdateStore(app.applicationContext)
    private val hitokotoClient = HitokotoClient()
    private val backgroundFetcher = BackgroundFetcher()
    private val apkFetcher = ApkFetcher()
    private val nodeClient = NodeClient()
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
    private var updateJob: Job? = null

    init {
        // 只读一次快照，而不是 collect 整条 flow：
        // 若持续 collect，落盘回灌的旧快照会覆盖掉用户两次点击之间的内存改动，
        // 快速连点「添加」会丢节点。这里没有外部写入方，一次性读取更安全。
        viewModelScope.launch {
            val saved = runCatching { store.nodes.first() }.getOrDefault(NodePresets.builtin())
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

        // 下载源同样只读快照：开关是点击式操作，被回灌覆盖会表现为「点了没反应」
        viewModelScope.launch {
            val up = runCatching { updateStore.prefs.first() }.getOrDefault(UpdatePrefs())
            _ui.update { it.copy(updatePrefs = up, currentVersion = currentVersionName()) }
        }
    }

    /** 读安装包版本名。失败返回空串 —— 那样比较结果恒为「有更新」，比静默不提示安全。 */
    private fun currentVersionName(): String = runCatching {
        val ctx = getApplication<Application>()
        val pm = ctx.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, 0)
        }
        info.versionName.orEmpty()
    }.getOrDefault("")

    /** 内存态变更后落盘。失败只提示，不回滚内存 —— 节点丢了可以重加，卡住界面不行。 */
    private fun persist() {
        viewModelScope.launch {
            runCatching { store.save(repo.nodes) }
                .onFailure { _ui.update { s -> s.copy(message = "节点保存失败：${it.message}") } }
        }
    }

    /**
     * 恢复预置节点。
     *
     * 直接替换而不是追加：追加会留下重复条目，用户得自己分辨哪条是旧的。
     * 这是设置里的「重置」语义，不是「补充」。
     */
    fun restoreDefaultNodes() {
        repo.replaceAll(NodePresets.builtin())
        _ui.update { it.copy(nodes = repo.nodes, latencies = emptyMap(), message = "已恢复预置节点") }
        persist()
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

    /**
     * 全部启用的节点地址，按当前顺序。
     *
     * 交给 [NodeClient] 逐个回退：公益节点随时会挂或被限流，
     * 绑死某一个等于把可用性押在别人身上。
     */
    private fun enabledBases(): List<String> =
        repo.enabledNodes().map { it.baseUrl.trimEnd('/') }

    fun search() {
        val q = _ui.value.query.trim()
        if (q.isBlank()) return
        val bases = enabledBases()
        if (bases.isEmpty()) {
            _ui.update { it.copy(searchError = "还没有启用任何数据源节点") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(searching = true, searchError = null) }
            runCatching { nodeClient.search(bases, q) }
                .onSuccess { page ->
                    _ui.update {
                        it.copy(
                            searching = false,
                            results = page.books,
                            searchError = if (page.books.isEmpty()) {
                                "没有找到「$q」相关的书"
                            } else null,
                        )
                    }
                }
                .onFailure { e ->
                    // 多节点全挂时 e 是最后一个节点的异常，带上它至少能判断是
                    // 超时还是被拒；吞掉就只剩"点了没反应"。
                    _ui.update { it.copy(searching = false, searchError = "搜索失败：${e.message}") }
                }
        }
    }

    /** 选中一本书并拉它的目录。目录为空视为失败 —— 空目录没法下载也没法读。 */
    fun selectBook(book: BookInfo) {
        val bases = enabledBases()
        if (bases.isEmpty()) {
            _ui.update { it.copy(message = "没有启用的数据源节点") }
            return
        }
        viewModelScope.launch {
            _ui.update {
                it.copy(selected = book, chapters = emptyList(), catalogLoading = true, catalogError = null)
            }
            runCatching { nodeClient.catalog(bases, book.id) }
                .onSuccess { list ->
                    _ui.update {
                        it.copy(
                            chapters = list,
                            catalogLoading = false,
                            // 空目录多半不是网络问题，而是这本在数据源里就没有章节
                            // （聚合条目、已下架都会这样），提示要指向"换一本"而不是"重试"
                            catalogError = if (list.isEmpty()) {
                                "数据源没有返回这本书的章节，换一个搜索结果试试"
                            } else null,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(catalogLoading = false, catalogError = "目录加载失败：${e.message}")
                    }
                }
        }
    }

    fun clearSelected() = _ui.update {
        it.copy(selected = null, chapters = emptyList(), catalogError = null)
    }

    // —— 在线阅读 ——

    fun openChapter(index: Int) {
        val ch = _ui.value.chapters.getOrNull(index) ?: return
        val bases = enabledBases()
        viewModelScope.launch {
            _ui.update { it.copy(readingIndex = index, readingLoading = true, readingError = null, reading = null) }
            runCatching { nodeClient.content(bases, ch.itemId) }
                .onSuccess { c ->
                    _ui.update { it.copy(reading = c, readingLoading = false) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(readingLoading = false, readingError = "正文加载失败：${e.message}") }
                }
        }
    }

    /** 上一章。已在第一章时返回 false，UI 用它决定按钮是否可点。 */
    fun prevChapter(): Boolean {
        val i = _ui.value.readingIndex
        if (i <= 0) return false
        openChapter(i - 1)
        return true
    }

    fun nextChapter(): Boolean {
        val i = _ui.value.readingIndex
        if (i < 0 || i >= _ui.value.chapters.lastIndex) return false
        openChapter(i + 1)
        return true
    }

    fun closeReader() = _ui.update {
        it.copy(reading = null, readingIndex = -1, readingError = null, readingLoading = false)
    }

    private suspend fun fetchChapter(ch: Chapter): ChapterContent =
        nodeClient.content(enabledBases(), ch.itemId)

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

    // ---------- 应用更新 ----------

    /**
     * 检查更新。
     *
     * @param silent 静默模式：只在确实有新版本时才更新状态，用于启动时自动检查。
     */
    fun checkUpdate(silent: Boolean = false) {
        if (_ui.value.updateChecking) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    updateChecking = true,
                    updateMessage = if (silent) null else "正在检查更新…",
                )
            }
            apkFetcher.latest().fold(
                onSuccess = { info ->
                    _ui.update { s ->
                        when {
                            info == null ->
                                s.copy(updateChecking = false, updateMessage = "暂时没有可用的发布版本")

                            compareVersion(info.version, s.currentVersion) > 0 ->
                                s.copy(updateChecking = false, updateInfo = info, updateMessage = null)

                            else -> s.copy(
                                updateChecking = false,
                                updateInfo = info,
                                updateMessage = "已是最新版本（${s.currentVersion}）",
                            )
                        }
                    }
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _ui.update {
                        it.copy(updateChecking = false, updateMessage = "检查更新失败：${e.message}")
                    }
                },
            )
        }
    }

    /**
     * 下载更新包。
     *
     * 逐个探测候选源，取第一个可达的。探测用 HEAD，代价远小于先下一个再说。
     * 全部不可达才报错 —— 镜像站抽风是常态，多试几个比直接失败有用。
     */
    fun downloadUpdate() {
        val info = _ui.value.updateInfo ?: return
        val asset = pickApkAsset(info.assets)
        if (asset == null) {
            _ui.update { it.copy(updateMessage = "这个版本没有可安装的 APK") }
            return
        }
        if (_ui.value.apkProgress != null) return

        val candidates = buildDownloadCandidates(_ui.value.updatePrefs.activeSources, asset.url)
        if (candidates.isEmpty()) {
            _ui.update { it.copy(updateMessage = "没有启用的下载源") }
            return
        }

        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _ui.update { it.copy(apkProgress = 0f, apkMessage = "正在选择下载源…") }

            val dest = File(getApplication<Application>().cacheDir, "update/${asset.name}")
            // 下过且大小吻合就复用，不重复耗用户流量
            if (dest.isFile && (asset.size <= 0 || dest.length() == asset.size)) {
                _ui.update { it.copy(apkProgress = null, apkMessage = null, apkFile = dest) }
                return@launch
            }

            var picked: Pair<DownloadSource, String>? = null
            for ((src, url) in candidates) {
                _ui.update { it.copy(apkMessage = "正在探测 ${src.name}…") }
                if (apkFetcher.probe(url)) {
                    picked = src to url
                    break
                }
            }
            val (src, url) = picked ?: run {
                _ui.update {
                    it.copy(
                        apkProgress = null,
                        apkMessage = null,
                        updateMessage = "所有下载源都不可达，可稍后重试或手动前往发布页",
                    )
                }
                return@launch
            }

            _ui.update { it.copy(apkMessage = "正在从 ${src.name} 下载…") }
            // 进度回调是每 64KB 一次，直接回写 UI 会刷新到卡死，按整百分比节流
            var lastPct = -1
            apkFetcher.download(url, dest) { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                if (pct != lastPct) {
                    lastPct = pct
                    _ui.update {
                        it.copy(
                            apkProgress = pct / 100f,
                            apkMessage = "正在从 ${src.name} 下载 ${formatSize(done)}",
                        )
                    }
                }
            }.fold(
                onSuccess = { file ->
                    _ui.update { it.copy(apkProgress = null, apkMessage = null, apkFile = file) }
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _ui.update {
                        it.copy(
                            apkProgress = null,
                            apkMessage = null,
                            updateMessage = "从 ${src.name} 下载失败：${e.message}",
                        )
                    }
                },
            )
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        _ui.update { it.copy(apkProgress = null, apkMessage = null) }
    }

    /** 安装意图已拉起，清空待安装文件，避免下次进入设置又弹一次。 */
    fun consumeApk() = _ui.update { it.copy(apkFile = null) }

    fun clearUpdateMessage() = _ui.update { it.copy(updateMessage = null) }

    /** 预置源只能停用；自定义源直接删。 */
    fun setDownloadSourceEnabled(src: DownloadSource, enabled: Boolean) {
        if (!src.builtin) return
        val cur = _ui.value.updatePrefs
        val next = cur.copy(
            disabledIds = if (enabled) cur.disabledIds - src.id else cur.disabledIds + src.id
        )
        _ui.update { it.copy(updatePrefs = next) }
        persistUpdate(next)
    }

    fun addCustomDownloadSource(name: String, template: String) {
        val tpl = template.trim()
        if (!tpl.contains("{url}")) {
            _ui.update { it.copy(message = "模板必须包含 {url} 占位符") }
            return
        }
        // 拿一个假地址试替换，比对着模板字符串做正则直观得多
        val sample = resolveSourceUrl(tpl, "https://example.com/a.apk")
        if (!NodeTester.isValidHttpUrl(sample)) {
            _ui.update { it.copy(message = "模板替换后不是合法地址：$sample") }
            return
        }
        val cur = _ui.value.updatePrefs
        val src = DownloadSource(
            id = "cds-" + System.currentTimeMillis().toString(36),
            name = name.ifBlank { "自定义镜像" },
            urlTemplate = tpl,
        )
        val next = cur.copy(customSources = cur.customSources + src)
        _ui.update { it.copy(updatePrefs = next) }
        persistUpdate(next)
    }

    fun removeCustomDownloadSource(id: String) {
        val cur = _ui.value.updatePrefs
        if (cur.customSources.none { it.id == id }) return
        val next = cur.copy(customSources = cur.customSources.filterNot { it.id == id })
        _ui.update { it.copy(updatePrefs = next) }
        persistUpdate(next)
    }

    private fun persistUpdate(p: UpdatePrefs) {
        viewModelScope.launch { runCatching { updateStore.save(p) } }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}
