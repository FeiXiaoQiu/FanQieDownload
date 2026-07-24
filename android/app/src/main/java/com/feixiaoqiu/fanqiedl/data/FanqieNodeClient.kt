package com.feixiaoqiu.fanqiedl.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.max

class FanqieNodeClient(
    private val settings: AppSettings,
    private val decoder: CharsetDecoder,
    private val basesProvider: suspend () -> List<String>,
    private val http: OkHttpClient = defaultClient(),
) {
    suspend fun search(query: String, offset: Int = 0): List<BookSummary> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val path = "/search?query=" + URLEncoder.encode(q, "UTF-8") + "&page=0&offset=$offset"
        val data = requestJson(path, 20_000) {
            parseSearch(it).isNotEmpty() || it.has("search_tabs")
        }
        return parseSearch(data)
    }

    suspend fun info(bookId: String): BookInfo {
        val data = requestJson("/info?book_id=$bookId", 15_000) {
            val d = it.optJSONObject("data") ?: it
            d.has("book_name") || d.has("title") || d.has("book_id")
        }
        val d = data.optJSONObject("data") ?: data
        return BookInfo(
            bookId = bookId,
            title = d.optString("book_name").ifBlank { d.optString("title").ifBlank { "小说$bookId" } },
            author = d.optString("author").ifBlank { d.optString("author_name").ifBlank { "未知" } },
            abstract = firstNonBlank(
                d.optString("book_abstract_v2"),
                d.optString("book_abstract"),
                d.optString("abstract"),
            ),
        )
    }

    suspend fun catalog(bookId: String): List<ChapterRef> {
        val data = requestJson("/catalog?book_id=$bookId", 20_000) {
            extractCatalog(it).isNotEmpty()
        }
        return extractCatalog(data).mapIndexed { idx, ch ->
            ChapterRef(
                itemId = firstNonBlank(ch.optString("item_id"), ch.optString("itemId"), ch.optString("id")),
                title = ch.optString("title"),
                index = idx,
            )
        }.filter { it.itemId.isNotBlank() }
    }

    suspend fun content(itemId: String): ChapterContent {
        val data = requestJson("/content?item_id=$itemId", 20_000) {
            extractContentRaw(it, itemId) != null
        }
        val got = extractContentRaw(data, itemId) ?: throw IllegalStateException("正文为空")
        val text = decoder.decodeBody(got.first)
        if (text.length < 30) throw IllegalStateException("正文过短")
        return ChapterContent(title = got.second, text = text)
    }

    /** @return latency ms */
    fun probe(baseUrl: String): Long {
        val base = DefaultNodes.normalizeBaseUrl(baseUrl)
        val t0 = System.currentTimeMillis()
        val url = "$base/content?item_id=${DefaultNodes.PROBE_ITEM}"
        val text = httpGet(url, 12_000)
        val data = JSONObject(text)
        val raw = extractContentRaw(data, DefaultNodes.PROBE_ITEM)
            ?: throw IllegalStateException("探活无正文")
        if (raw.first.length < 30) throw IllegalStateException("探活正文过短")
        return System.currentTimeMillis() - t0
    }

    private suspend fun requestJson(
        path: String,
        timeoutMs: Long,
        ok: (JSONObject) -> Boolean,
    ): JSONObject {
        val nodes = basesProvider()
        if (nodes.isEmpty()) throw NoEnabledNodeException()
        var last: Exception? = null
        for (base in nodes) {
            try {
                val text = httpGet(base + path, timeoutMs)
                val data = JSONObject(text)
                if (!ok(data)) {
                    last = IllegalStateException("节点无有效数据: $base")
                    continue
                }
                settings.setLastGoodBase(base)
                return data
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("全部节点失败")
    }

    private fun httpGet(url: String, timeoutMs: Long): String {
        val client = http.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(max(5_000, timeoutMs / 2), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (body.isBlank() || body.startsWith("<")) throw IllegalStateException("非 JSON 响应")
            return body
        }
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .build()

        fun parseSearch(data: JSONObject): List<BookSummary> {
            val books = mutableListOf<BookSummary>()
            val seen = mutableSetOf<String>()
            fun push(it: JSONObject?) {
                if (it == null) return
                val bookData = it.optJSONArray("book_data")
                if (bookData != null && bookData.length() > 0) {
                    for (i in 0 until bookData.length()) {
                        val bd = bookData.optJSONObject(i) ?: continue
                        val merged = JSONObject(bd.toString())
                        if (!merged.has("book_id")) {
                            merged.put(
                                "book_id",
                                firstNonBlank(
                                    bd.optString("book_id"),
                                    it.optString("book_id"),
                                    it.optString("search_result_id"),
                                )
                            )
                        }
                        push(merged)
                    }
                    return
                }
                val bookId = firstNonBlank(
                    it.optString("book_id"),
                    it.optString("search_result_id"),
                    it.optString("bookId"),
                )
                if (!bookId.matches(Regex("\\d{10,}")) || bookId in seen) return
                val title = firstNonBlank(it.optString("book_name"), it.optString("title"))
                if (title.isBlank() && it.optString("author").isBlank()) return
                seen.add(bookId)
                val cover = normalizeCover(
                    firstNonBlank(
                        it.optString("thumb_uri"),
                        it.optString("audio_thumb_uri"),
                        it.optString("thumb_url"),
                        it.optString("cover_url"),
                    )
                )
                val author = it.optString("author")
                val category = it.optString("category")
                val score = it.optString("score")
                val meta = listOf(author, category, score).filter { s -> s.isNotBlank() }.joinToString(" · ")
                books.add(
                    BookSummary(
                        bookId = bookId,
                        title = title,
                        author = author,
                        coverUrl = cover,
                        description = it.optString("abstract"),
                        meta = meta,
                    )
                )
            }

            val tabs = data.optJSONArray("search_tabs")
            if (tabs != null) {
                var bookTab: JSONObject? = null
                for (i in 0 until tabs.length()) {
                    val t = tabs.optJSONObject(i) ?: continue
                    if (t.optString("title") == "书籍" || t.optInt("tab_type") == 3) {
                        bookTab = t
                        break
                    }
                }
                if (bookTab == null && tabs.length() > 0) bookTab = tabs.optJSONObject(0)
                val tabData = bookTab?.optJSONArray("data")
                if (tabData != null) {
                    for (i in 0 until tabData.length()) push(tabData.optJSONObject(i))
                    if (books.isNotEmpty()) return books
                }
            }
            walk(data, 0) { push(it) }
            return books
        }

        private fun walk(node: Any?, depth: Int, push: (JSONObject) -> Unit) {
            if (node == null || depth > 8) return
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), depth + 1, push)
                is JSONObject -> {
                    if (node.has("book_data") || node.has("book_name") || node.has("book_id")) push(node)
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1, push)
                }
            }
        }

        fun extractCatalog(data: JSONObject): List<JSONObject> {
            val d = data.optJSONObject("data") ?: data
            val arr = d.optJSONArray("item_data_list")
                ?: d.optJSONArray("itemDataList")
                ?: d.optJSONArray("chapter_list")
                ?: JSONArray()
            return buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(it) }
                }
            }
        }

        fun extractContentRaw(data: JSONObject, itemId: String): Pair<String, String>? {
            val code = data.opt("code")
            if (code != null && code != 0 && code != "0") return null
            var payload: Any? = data.opt("data") ?: JSONObject()
            if (payload is JSONObject && payload.has(itemId)) {
                payload = payload.opt(itemId)
            }
            var raw = ""
            var title = ""
            when (payload) {
                is JSONObject -> {
                    raw = firstNonBlank(payload.optString("content"), payload.optString("text"))
                    title = firstNonBlank(payload.optString("title"), payload.optString("chapter_title"))
                }
                is String -> raw = payload
            }
            if (raw.length < 30) return null
            return raw to title
        }

        fun normalizeCover(raw: String): String {
            var u = raw.trim()
            if (u.isEmpty()) return ""
            if (u.contains("bytecdn.cn") && u.contains("novel-pic/")) {
                val m = Regex("novel-pic/([^~?/]+)").find(u)
                if (m != null) {
                    u = "https://p3-novel.byteimg.com/img/novel-pic/${m.groupValues[1]}~tplv-tt-cs0:120:160.image"
                }
            }
            return u
        }

        fun firstNonBlank(vararg values: String): String {
            for (v in values) if (v.isNotBlank()) return v
            return ""
        }

        fun sliceChapters(chapters: List<ChapterRef>, startChapter: Int, endChapter: Int): List<ChapterRef> {
            if (chapters.isEmpty()) return emptyList()
            val start = max(0, if (startChapter > 0) startChapter - 1 else 0)
            val endExclusive = if (endChapter > 0) endChapter.coerceAtMost(chapters.size) else chapters.size
            if (start >= endExclusive) return emptyList()
            return chapters.subList(start, endExclusive)
        }

        fun safeFileName(name: String): String {
            val s = name.replace(Regex("[\\\\/:*?\"<>|]+"), "_").trim().take(80)
            return s.ifBlank { "novel" }
        }
    }
}
