package ink.yan.reader

import ink.yan.reader.data.BookInfo
import ink.yan.reader.data.Chapter
import ink.yan.reader.data.ChapterContent
import ink.yan.reader.data.DownloadEngine
import ink.yan.reader.data.DownloadRequest
import ink.yan.reader.data.ExportFormat
import ink.yan.reader.data.NodeCodec
import ink.yan.reader.data.NodeConfig
import ink.yan.reader.data.NodePresets
import ink.yan.reader.data.NodeTester
import ink.yan.reader.data.export.EpubWriter
import ink.yan.reader.data.export.TxtWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 纯逻辑单元测试 —— 不依赖 Robolectric，直接在 JVM 上跑。
 *
 * 覆盖三块最容易出错、也最难以肉眼发现的地方：
 *   1. 节点序列化（分隔符冲突 / 转义 / 脏数据容错）
 *   2. 并发下载（顺序、限流、缓存丢写）
 *   3. EPUB 结构（mimetype 首条目、XML 转义）
 */
class CoreLogicTest {

    // ---------- 节点序列化 ----------

    @Test
    fun codec_roundTripPreservesAllFields() {
        val nodes = listOf(
            NodeConfig("u1", "本机", "http://127.0.0.1:8080"),
            NodeConfig("u2", "家里NAS", "https://nas.example.com:9443", enabled = false),
            NodeConfig("u3", "内置源", "http://built.in", builtin = true),
        )
        val back = NodeCodec.decode(NodeCodec.encode(nodes))
        assertEquals(nodes, back)
        assertEquals(listOf("u1", "u2", "u3"), back.map { it.id })
    }

    @Test
    fun codec_survivesHostileCharacters() {
        // 名称里塞进：换行、字段分隔符本身、制表符、emoji、URL 元字符
        val evil = NodeConfig(
            id = "u\n1\u001Fx\ty",
            name = "坏\t名\u001F字\n第二行 🎉 \"引号\" +加号&等=%",
            baseUrl = "http://ex ample.com:80/a b?c=1&d=2",
        )
        val back = NodeCodec.decode(NodeCodec.encode(listOf(evil)))
        assertEquals("分隔符/换行不应撑坏记录", 1, back.size)
        assertEquals(evil, back.first())
    }

    @Test
    fun codec_emptyAndMalformedAreSafe() {
        assertTrue(NodeCodec.encode(emptyList()).isEmpty())
        assertTrue(NodeCodec.decode("").isEmpty())
        assertTrue(NodeCodec.decode("\n\n  \n").isEmpty())

        val broken = NodeCodec.encode(listOf(NodeConfig("ok", "好", "http://a.b"))) +
                "只有三段\u001Fxx\u001Fyy\n" +                       // 字段数不足
                "\u001F\u001Fhttp://noid\u001Ftrue\u001Ffalse\n" +   // id 为空
                "idnourl\u001F名\u001F\u001Ftrue\u001Ffalse\n" +     // baseUrl 为空
                "notABool\u001F名\u001Fhttp://x.y\u001Fyes\u001Fno\n" // 布尔值非法
        val salvaged = NodeCodec.decode(broken)
        assertEquals("脏行应被跳过且不抛异常", 1, salvaged.size)
        assertEquals("ok", salvaged.first().id)
    }

    // ---------- 并发下载 ----------

    @Test
    fun engine_downloadsAllInOrder() = runBlocking {
        val chapters = (0 until 40).map { Chapter("i$it", "第${it + 1}章 标题", it) }
        var netHits = 0
        val cache = HashMap<String, ChapterContent>()

        val engine = DownloadEngine(
            fetch = { netHits++; ChapterContent(it.title, "正文-${it.itemId}") },
            loadCache = { b, i -> cache["$b/$i"] },
            // 故意用普通 HashMap：引擎内部必须自己保证并发写安全
            saveCache = { b, i, c -> cache["$b/$i"] = c },
            concurrency = 5,
        )
        val req = DownloadRequest("book1", "测试书", 0, Int.MAX_VALUE, ExportFormat.EPUB, resume = true)

        val out = engine.run(req, chapters) { _, _, _ -> }
        assertEquals(40, out.chapters.size)
        assertEquals(0, out.errorCount)
        assertEquals(40, netHits)
        assertEquals("并发写缓存不应丢条", 40, cache.size)
        assertEquals(chapters.map { it.title }, out.chapters.map { it.title })

        val before = netHits
        val out2 = engine.run(req, chapters) { _, _, _ -> }
        assertEquals(40, out2.chapters.size)
        assertEquals("第二轮应全部命中缓存", before, netHits)
    }

    // ---------- 导出 ----------

    @Test
    fun export_txtContainsEveryChapter() {
        val chapters = (1..3).map { ChapterContent("第${it}章 <>&\"'", "正文 $it\n换行") }
        val os = ByteArrayOutputStream()
        TxtWriter.write(os, BookInfo("b1", "<测试&书名>", "某作者"), chapters)
        val t = os.toString("UTF-8")
        assertTrue(t.contains("测试&书名"))
        (1..3).forEach { assertTrue(t.contains("第${it}章")) }
    }

    @Test
    fun export_epubIsWellFormedAndEscaped() {
        val chapters = (1..3).map { "第${it}章 <>&\"'" to "正文 $it" }
        val os = ByteArrayOutputStream()
        EpubWriter.write(os, "<测试&书名>", "某作者", "b1", chapters)
        val bytes = os.toByteArray()

        val names = ArrayList<String>()
        val xmls = LinkedHashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var first = true
            while (true) {
                val e = zis.nextEntry ?: break
                names.add(e.name)
                if (first) {
                    // EPUB 硬性要求：mimetype 必须是第一个条目且未压缩
                    assertEquals("mimetype", e.name)
                    assertEquals("application/epub+zip", zis.readBytes().toString(Charsets.US_ASCII))
                    first = false
                } else if (e.name.endsWith(".xml") || e.name.endsWith(".opf") || e.name.endsWith(".xhtml")) {
                    xmls[e.name] = zis.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        assertTrue(names.any { it.endsWith("container.xml") })
        assertTrue(names.any { it.endsWith(".opf") })
        assertTrue(names.count { it.endsWith(".xhtml") } >= 3)

        val factory = DocumentBuilderFactory.newInstance()
        xmls.forEach { (n, s) ->
            try {
                factory.newDocumentBuilder().parse(s.byteInputStream())
            } catch (e: Exception) {
                throw AssertionError("$n 不是良构 XML（转义缺失？）：${e.message}")
            }
        }
        assertTrue("标题中的 & 必须转义", xmls.values.any { it.contains("测试&amp;书名") })
    }

    // ---------- 预置节点 ----------

    @Test
    fun presets_areSixValidNodesWithUniqueIds() {
        val nodes = NodePresets.builtin()
        assertEquals(6, nodes.size)
        assertEquals(6, nodes.map { it.id }.toSet().size)
        nodes.forEach { n ->
            assertTrue("${n.baseUrl} 必须是 http(s) 地址", NodeTester.isValidHttpUrl(n.baseUrl))
            assertTrue("预置节点必须标记为内置", n.builtin)
            assertTrue("预置节点必须默认启用", n.enabled)
        }
    }

    @Test
    fun presets_returnFreshListEachCall() {
        // 共享引用会让「恢复默认」之后的改动污染下一次调用
        val a = NodePresets.builtin()
        val b = NodePresets.builtin()
        assertTrue(a !== b)
        assertEquals(a, b)
    }

    @Test
    fun presets_surviveCodecRoundTrip() {
        // 存进 DataStore 再读出来必须一模一样，否则每次启动都会当成「已损坏」而重置
        val src = NodePresets.builtin()
        assertEquals(src, NodeCodec.decode(NodeCodec.encode(src)))
    }
}
