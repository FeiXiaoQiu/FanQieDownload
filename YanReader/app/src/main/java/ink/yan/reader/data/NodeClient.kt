package ink.yan.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * 一次搜索的结果页。
 *
 * [nextOffset] 用于翻页；[hasMore] 为 false 时 UI 不该再显示「加载更多」。
 */
data class SearchPage(
    val books: List<BookInfo>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

/**
 * 节点协议客户端。
 *
 * 四个接口与节点一一对应：`/search`、`/info`、`/catalog`、`/content`。
 *
 * 多节点回退是核心：这些是第三方公益接口，随时可能挂掉或被限流，
 * 逐个试到第一个可用的为止，而不是绑死某一个。失败时抛出的是**最后一个**
 * 节点的异常 —— 只抛第一个会让人误判成整体不可用。
 */
class NodeClient(
    private val http: OkHttpClient = defaultHttp(),
) {

    suspend fun search(bases: List<String>, query: String, offset: Int = 0): SearchPage =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext SearchPage(emptyList(), 0, false)

            val path = "/search?query=" + URLEncoder.encode(q, "UTF-8") +
                "&page=${max(0, offset / 10)}&offset=$offset"
            val data = requestJson(bases, path, 20_000) {
                NodeParser.parseSearch(it).isNotEmpty() || it.hasKey("search_tabs") || offset > 0
            }

            val books = NodeParser.parseSearch(data)
            val next = when {
                data.hasKey("next_offset") -> data.int("next_offset", offset + books.size)
                data.obj("data")?.hasKey("offset") == true ->
                    data.obj("data")!!.int("offset", offset + books.size)
                else -> offset + books.size
            }
            val more = when {
                data.flag("has_more") != null -> data.flag("has_more")!!
                data.obj("data")?.flag("has_more") != null -> data.obj("data")!!.flag("has_more")!!
                else -> books.size >= 7
            }
            // 一本都没搜到时不要给「还有更多」，否则用户会一直翻空页
            SearchPage(books, next, more && books.isNotEmpty())
        }

    suspend fun info(bases: List<String>, bookId: String): BookInfo =
        withContext(Dispatchers.IO) {
            val data = requestJson(bases, "/info?book_id=$bookId", 15_000) {
                val d = it.obj("data") ?: it
                d.hasKey("book_name") || d.hasKey("title") || d.hasKey("book_id")
            }
            val d = data.obj("data") ?: data
            BookInfo(
                id = bookId,
                title = d.text("book_name", "title").ifBlank { "小说$bookId" },
                author = d.text("author", "author_name").ifBlank { "未知" },
                abstract = d.text("book_abstract_v2", "book_abstract", "abstract"),
            )
        }

    suspend fun catalog(bases: List<String>, bookId: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val data = requestJson(bases, "/catalog?book_id=$bookId", 20_000) {
                NodeParser.extractCatalog(it).isNotEmpty()
            }
            NodeParser.extractCatalog(data)
        }

    suspend fun content(bases: List<String>, itemId: String): ChapterContent =
        withContext(Dispatchers.IO) {
            val data = requestJson(bases, "/content?item_id=$itemId", 20_000) {
                NodeParser.extractContent(it, itemId) != null
            }
            NodeParser.extractContent(data, itemId)
                ?: throw IllegalStateException("正文为空（可能被限流）")
        }

    /**
     * 逐个尝试节点，返回第一个「响应里有有效数据」的结果。
     *
     * 判据交给调用方（[ok]）是必要的：HTTP 200 不代表有数据，
     * 被限流的节点常常返回 200 加一个空壳 JSON。
     */
    private fun requestJson(
        bases: List<String>,
        path: String,
        timeoutMs: Long,
        ok: (JsonVal.Obj) -> Boolean,
    ): JsonVal.Obj {
        val nodes = bases.map { it.trimEnd('/') }.filter { it.isNotBlank() }
        if (nodes.isEmpty()) throw IllegalStateException("没有可用的数据源节点")

        var last: Exception? = null
        for (base in nodes) {
            try {
                val text = httpGet(base + path, timeoutMs)
                val root = JsonAdapter.parseObject(text)
                    ?: throw IllegalStateException("响应不是合法 JSON")
                if (!ok(root)) {
                    last = IllegalStateException("节点无有效数据：$base")
                    continue
                }
                return root
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("全部节点都失败了")
    }

    private fun httpGet(url: String, timeoutMs: Long): String {
        val client = clientFor(timeoutMs)
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            // 以 < 开头说明被网关拦到了 HTML 错误页，不是接口响应
            if (body.isBlank() || body.startsWith("<")) throw IllegalStateException("非 JSON 响应")
            return body
        }
    }

    /**
     * 按超时缓存客户端。
     *
     * 之前每个请求都 `newBuilder().build()` 一次，翻章是高频操作，
     * 反复建客户端等于反复建线程池。共享的 [http] 已经带连接池，
     * 派生出来的客户端会共用它，所以按超时档位各留一个就够。
     */
    private fun clientFor(timeoutMs: Long): OkHttpClient =
        clients.getOrPut(timeoutMs) {
            http.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(max(5_000, timeoutMs / 2), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
        }

    private val clients = ConcurrentHashMap<Long, OkHttpClient>()

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .build()
    }
}
