package ink.yan.reader.data

/**
 * 节点响应的解析。
 *
 * 面向 [JsonVal] 而不是 org.json，理由同 BackgroundResolver：
 * 这段逻辑要在 JVM 上跑单元测试，而 org.json 在单测里是 stub。
 *
 * 为什么要写得这么容错：这些节点是第三方公益接口，各版本返回结构并不一致
 * ——同一个 `/search` 就有「书籍 tab 下挂 data 数组」「结果直接塞在 data 里」
 * 「book_data 再套一层」三种形态，字段命名也混用下划线与驼峰。硬认某一种
 * 结构，换个节点或节点升级就全废。这里的策略是「先按已知路径取，取不到
 * 再全树探测」，与背景解析一致。
 */
object NodeParser {

    /** 书籍 id 的形态约束：纯数字且够长。用来把广告、听书等杂质滤掉。 */
    private val BOOK_ID = Regex("\\d{10,}")

    fun parseSearch(root: JsonVal): List<BookInfo> {
        val books = ArrayList<BookInfo>(16)
        val seen = HashSet<String>()

        fun push(node: JsonVal.Obj, fallbackId: String = "") {
            // book_data 是「一本书带多个版本」的包法，展开后继承外层 id
            val nested = node.arr("book_data")
            if (nested.isNotEmpty()) {
                val outerId = node.text("book_id", "search_result_id", "bookId")
                nested.forEach { child ->
                    if (child is JsonVal.Obj) push(child, outerId)
                }
                return
            }

            val id = node.text("book_id", "search_result_id", "bookId")
                .ifBlank { fallbackId }
            if (!BOOK_ID.matches(id) || id in seen) return

            val title = node.text("book_name", "title")
            val author = node.text("author", "author_name")
            // 标题和作者都空的是广告位，不是书
            if (title.isBlank() && author.isBlank()) return

            seen.add(id)
            books += BookInfo(
                id = id,
                title = title.ifBlank { "未命名" },
                author = author.ifBlank { "未知" },
                cover = normalizeCover(node.text("thumb_uri", "audio_thumb_uri", "thumb_url", "cover_url")),
                abstract = node.text("abstract", "book_abstract", "description"),
            )
        }

        // 主流形态：结果按 tab 分类，书籍在 title=="书籍" 或 tab_type==3 的那个里
        val tabs = root.objOrEmpty().arr("search_tabs")
        if (tabs.isNotEmpty()) {
            val tab = tabs.filterIsInstance<JsonVal.Obj>()
                .firstOrNull { it.text("title") == "书籍" || it.num("tab_type") == 3.0 }
                ?: tabs.filterIsInstance<JsonVal.Obj>().firstOrNull()
            tab?.arr("data")
                .orEmpty()
                .filterIsInstance<JsonVal.Obj>()
                .forEach { push(it) }
            if (books.isNotEmpty()) return books
        }

        // 兜底：全树找像书的对象
        walk(root, 0) { push(it) }
        return books
    }

    fun extractCatalog(root: JsonVal): List<Chapter> {
        val top = root.objOrEmpty()
        val d = top.obj("data") ?: top
        val arr = d.arr("item_data_list")
            .ifEmpty { d.arr("itemDataList") }
            .ifEmpty { d.arr("chapter_list") }
            .ifEmpty { d.arr("chapters") }

        val out = ArrayList<Chapter>(arr.size)
        arr.filterIsInstance<JsonVal.Obj>().forEach { ch ->
            val itemId = ch.text("item_id", "itemId", "id")
            if (itemId.isBlank()) return@forEach
            out += Chapter(
                itemId = itemId,
                // 序号按已收录的条数算，不是按原始数组下标：跳过了脏条目之后
                // 两者会错开，拿下标当章号会让章号出现空洞。
                title = ch.text("title", "chapter_title").ifBlank { "第 ${out.size + 1} 章" },
                index = out.size,
            )
        }
        return out
    }

    /**
     * 取章节正文。
     *
     * 节点给的是一段 HTML（`<article><p idx="0"><span>…</span></p>`），不是纯文本，
     * 必须先剥标签再交给 UI；直接用会把标签当正文显示出来。
     *
     * @return null 表示这次响应里没有可用正文（可能是失败响应，也可能被限流）
     */
    fun extractContent(root: JsonVal, itemId: String): ChapterContent? {
        val top = root.objOrEmpty()
        // code 非 0 是失败响应；有些节点不返回 code，那就只看正文在不在
        val code = top.num("code")
        if (code != null && code != 0.0) return null

        var payload: JsonVal = top.obj("data") ?: JsonVal.Obj(emptyMap())
        if (payload is JsonVal.Obj && itemId in payload.fields) {
            payload = payload.fields[itemId] ?: payload
        }

        val (raw, title) = when (payload) {
            is JsonVal.Obj -> payload.text("content", "text") to payload.text("title", "chapter_title")
            is JsonVal.Str -> payload.value to ""
            else -> "" to ""
        }
        val text = decodeBody(raw)
        // 太短的通常是错误提示而不是正文。判据放在还原之后：HTML 原文很长不代表
        // 有内容，一屏标签剥完可能什么都不剩。
        if (text.length < 30) return null
        return ChapterContent(title = title.ifBlank { "正文" }, text = text)
    }

    /**
     * 原始正文 → 可读文本：先还原被混淆的私有区码位，再剥 HTML 标签与实体。
     *
     * 顺序是先还原后剥标签。反混淆只按码位走、不认识标签，反过来做结果一样，
     * 但先还原能让剥标签时看到的已是正常汉字，便于判断段落边界。
     */
    fun decodeBody(raw: String): String {
        if (raw.isBlank()) return ""
        return htmlToText(CharsetTable.decode(raw))
    }

    /**
     * HTML → 纯文本。
     *
     * 只处理正文会用到的那点标签：换行类的转成 `\n`，其余整对抹掉，再解实体。
     * 没有上完整 HTML 解析器——节点返回的标签集合很固定，用正则够且省事。
     */
    private fun htmlToText(html: String): String {
        var s = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        s = s
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { m ->
                // 越界的码位换成空，避免把无效数字编成乱码字符
                m.groupValues[1].toIntOrNull()?.takeIf { it in 0..0x10FFFF }?.toChar()?.toString() ?: ""
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.takeIf { it in 0..0x10FFFF }?.toChar()?.toString() ?: ""
            }
        // 逐行去空白并丢掉空行：正文里夹的空段不显示出来更干净
        return s.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /**
     * 封面地址归一化。
     *
     * 节点返回的是 bytecdn 上的原始图，尺寸巨大且需要带特定参数才能取到，
     * 直接拿去加载会拉回几 MB 一张的图。换成字节公开的缩略图域名。
     */
    fun normalizeCover(raw: String): String {
        val u = raw.trim()
        if (u.isEmpty()) return ""
        if (u.contains("bytecdn.cn") && u.contains("novel-pic/")) {
            val m = Regex("novel-pic/([^~?/]+)").find(u)
            if (m != null) {
                return "https://p3-novel.byteimg.com/img/novel-pic/${m.groupValues[1]}~tplv-tt-cs0:120:160.image"
            }
        }
        return u
    }

    private fun walk(node: JsonVal, depth: Int, push: (JsonVal.Obj) -> Unit) {
        if (depth > 8) return
        when (node) {
            is JsonVal.Arr -> node.items.forEach { walk(it, depth + 1, push) }
            is JsonVal.Obj -> {
                val f = node.fields
                if ("book_data" in f || "book_name" in f || "book_id" in f) push(node)
                f.values.forEach { walk(it, depth + 1, push) }
            }
            else -> Unit
        }
    }
}
