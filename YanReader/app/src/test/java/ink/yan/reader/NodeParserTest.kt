package ink.yan.reader

import ink.yan.reader.data.JsonVal
import ink.yan.reader.data.NodeParser
import ink.yan.reader.data.js
import ink.yan.reader.data.jsn
import ink.yan.reader.data.jsonArr
import ink.yan.reader.data.jsonObj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节点响应解析的单元测试。
 *
 * 这些结构不是凭空设计的，是从真实节点抓回来的响应里摘的，尤其 [realSearchShape]
 * 基本照抄 `/search?query=斗破苍穹` 的返回：结果藏在 `search_tabs` 下 `tab_type==3`
 * 的那个 tab 里，书名和作者又在每项的 `book_data[0]` 里，外层只有 `book_id`。
 * 这层嵌套正是之前解析拿不到数据的地方，必须固化成用例。
 */
class NodeParserTest {

    // ---------- 搜索 ----------

    @Test
    fun parseSearch_expandsBookDataAndInheritsOuterId() {
        val root = jsonObj(
            "code" to 0.jsn(),
            "search_tabs" to jsonArr(
                jsonObj("tab_type" to 1.jsn(), "title" to "综合".js(), "data" to JsonVal.Nil),
                jsonObj(
                    "tab_type" to 3.jsn(),
                    "title" to "书籍".js(),
                    "data" to jsonArr(
                        jsonObj(
                            "book_id" to "7677249185988496408".js(),
                            "search_result_id" to "7677249185988496408".js(),
                            "book_data" to jsonArr(
                                jsonObj(
                                    "book_name" to "斗破苍穹".js(),
                                    "author" to "用户3929462820183".js(),
                                    "abstract" to "三十年河东".js(),
                                )
                            )
                        )
                    )
                )
            )
        )

        val books = NodeParser.parseSearch(root)
        assertEquals(1, books.size)
        // 关键：id 在外层，书名在里层，两边要拼到同一条记录上
        assertEquals("7677249185988496408", books[0].id)
        assertEquals("斗破苍穹", books[0].title)
        assertEquals("用户3929462820183", books[0].author)
        assertEquals("三十年河东", books[0].abstract)
    }

    @Test
    fun parseSearch_prefersBookTabOverOthers() {
        val root = jsonObj(
            "search_tabs" to jsonArr(
                jsonObj(
                    "title" to "听书".js(),
                    "data" to jsonArr(
                        jsonObj(
                            "book_id" to "1111111111111111111".js(),
                            "book_name" to "不该被选中".js(),
                            "author" to "某人".js(),
                        )
                    )
                ),
                jsonObj(
                    "title" to "书籍".js(),
                    "data" to jsonArr(
                        jsonObj(
                            "book_id" to "2222222222222222222".js(),
                            "book_name" to "该被选中".js(),
                            "author" to "某人".js(),
                        )
                    )
                )
            )
        )
        val books = NodeParser.parseSearch(root)
        assertEquals(1, books.size)
        assertEquals("该被选中", books[0].title)
    }

    @Test
    fun parseSearch_fallsBackToTabTypeWhenTitleDiffers() {
        // 有的节点 tab 标题不是"书籍"，只能靠 tab_type==3 认
        val root = jsonObj(
            "search_tabs" to jsonArr(
                jsonObj(
                    "tab_type" to 3.jsn(),
                    "title" to "小说".js(),
                    "data" to jsonArr(
                        jsonObj(
                            "book_id" to "3333333333333333333".js(),
                            "book_name" to "靠类型选中".js(),
                            "author" to "某人".js(),
                        )
                    )
                )
            )
        )
        assertEquals("靠类型选中", NodeParser.parseSearch(root).single().title)
    }

    @Test
    fun parseSearch_filtersNonBookIds() {
        val root = jsonObj(
            "search_tabs" to jsonArr(
                jsonObj(
                    "title" to "书籍".js(),
                    "data" to jsonArr(
                        // 广告位：id 太短
                        jsonObj("book_id" to "12345".js(), "book_name" to "广告".js()),
                        // 非数字 id
                        jsonObj("book_id" to "not-a-number".js(), "book_name" to "怪东西".js()),
                        // 没 id
                        jsonObj("book_name" to "无 id".js(), "author" to "某人".js()),
                        jsonObj(
                            "book_id" to "7677249185988496408".js(),
                            "book_name" to "正经书".js(),
                            "author" to "某人".js(),
                        )
                    )
                )
            )
        )
        val books = NodeParser.parseSearch(root)
        assertEquals(1, books.size)
        assertEquals("正经书", books[0].title)
    }

    @Test
    fun parseSearch_skipsBlankEntriesAndDedupes() {
        val root = jsonObj(
            "search_tabs" to jsonArr(
                jsonObj(
                    "title" to "书籍".js(),
                    "data" to jsonArr(
                        // 书名作者都空的是空壳，不是书
                        jsonObj("book_id" to "4444444444444444444".js()),
                        jsonObj(
                            "book_id" to "5555555555555555555".js(),
                            "book_name" to "重复的书".js(),
                        ),
                        jsonObj(
                            "book_id" to "5555555555555555555".js(),
                            "book_name" to "重复的书".js(),
                        )
                    )
                )
            )
        )
        val books = NodeParser.parseSearch(root)
        assertEquals(1, books.size)
        assertEquals("重复的书", books[0].title)
        // 作者缺失时给占位，不能留空串让 UI 显示一条空白
        assertEquals("未知", books[0].author)
    }

    @Test
    fun parseSearch_walksWholeTreeWhenNoTabs() {
        // 老版本节点直接把结果塞在 data 里，没有 search_tabs
        val root = jsonObj(
            "data" to jsonArr(
                jsonObj(
                    "book_id" to "6666666666666666666".js(),
                    "book_name" to "没有 tab 的书".js(),
                    "author" to "某人".js(),
                )
            )
        )
        assertEquals("没有 tab 的书", NodeParser.parseSearch(root).single().title)
    }

    @Test
    fun parseSearch_returnsEmptyOnGarbage() {
        // 乱七八糟的输入要安静地返回空，不能抛异常把整次搜索打挂
        assertTrue(NodeParser.parseSearch(JsonVal.Str("not json at all")).isEmpty())
        assertTrue(NodeParser.parseSearch(jsonObj("unrelated" to "x".js())).isEmpty())
        assertTrue(NodeParser.parseSearch(JsonVal.Nil).isEmpty())
    }

    // ---------- 目录 ----------

    @Test
    fun extractCatalog_readsItemDataList() {
        val root = jsonObj(
            "code" to 0.jsn(),
            "data" to jsonObj(
                "item_data_list" to jsonArr(
                    jsonObj("item_id" to "7672698507827891224".js(), "title" to "第一章".js()),
                    jsonObj("item_id" to "7672698558851599384".js(), "title" to "第六章".js()),
                )
            )
        )
        val chapters = NodeParser.extractCatalog(root)
        assertEquals(2, chapters.size)
        assertEquals("7672698507827891224", chapters[0].itemId)
        assertEquals("第一章", chapters[0].title)
        // index 必须是连续的，下载与翻章都拿它当下标
        assertEquals(0, chapters[0].index)
        assertEquals(1, chapters[1].index)
    }

    @Test
    fun extractCatalog_skipsBlankItemIdAndNumbersTitle() {
        val root = jsonObj(
            "data" to jsonObj(
                "item_data_list" to jsonArr(
                    jsonObj("item_id" to "".js(), "title" to "坏条目".js()),
                    jsonObj("item_id" to "7777777777777777777".js()),
                )
            )
        )
        val chapters = NodeParser.extractCatalog(root)
        assertEquals(1, chapters.size)
        // 缺标题时按序号兜底，不能留空
        assertEquals("第 1 章", chapters[0].title)
    }

    @Test
    fun extractCatalog_handlesEmptyPayload() {
        // 真实存在的情况：书搜得到，但数据源没有它的章节
        val root = jsonObj(
            "code" to 0.jsn(),
            "data" to jsonObj("item_data_list" to jsonArr())
        )
        assertTrue(NodeParser.extractCatalog(root).isEmpty())
    }

    @Test
    fun extractCatalog_acceptsCamelCaseField() {
        val root = jsonObj(
            "data" to jsonObj(
                "itemDataList" to jsonArr(
                    jsonObj("itemId" to "8888888888888888888".js(), "title" to "某章".js())
                )
            )
        )
        assertEquals("8888888888888888888", NodeParser.extractCatalog(root).single().itemId)
    }

    // ---------- 正文 ----------

    @Test
    fun decodeBody_stripsHtmlTags() {
        // 这就是节点真实的返回形态：正文包裹在 HTML 里。
        // 走 decodeBody 而不是 extractContent，是因为后者还有一道最短长度判据，
        // 而这里要单独验证"标签有没有剥干净"。
        val html = """<html><head></head><body><header></header>""" +
            """<article><p idx="0"><span>焚火溺命，</span></p>""" +
            """<p idx="1"><span>这一刻，他终于明白了。</span></p></article></body></html>"""
        val text = NodeParser.decodeBody(html)
        assertTrue("不该残留标签，实际是：$text", !text.contains("<p"))
        assertTrue("不该残留标签，实际是：$text", !text.contains("<span>"))
        assertTrue("不该残留标签，实际是：$text", !text.contains("article"))
        assertEquals("焚火溺命，\n这一刻，他终于明白了。", text)
    }

    @Test
    fun decodeBody_joinsParagraphsWithNewline() {
        assertEquals("第一段\n第二段", NodeParser.decodeBody("<article><p>第一段</p><p>第二段</p></article>"))
    }

    @Test
    fun decodeBody_convertsBrAndEntities() {
        val html = "第一句<br/>第二句&amp;第三句&nbsp;结尾&lt;标签&gt;"
        // &amp; 要最后解，否则 &amp;lt; 会被解成 < 而不是字面的 &lt;
        assertEquals("第一句\n第二句&第三句 结尾<标签>", NodeParser.decodeBody(html))
    }

    @Test
    fun decodeBody_dropsBlankLinesAndTrims() {
        assertEquals("甲\n乙", NodeParser.decodeBody("<p>  甲  </p><p></p><p>乙</p>"))
    }

    @Test
    fun decodeBody_decodesNumericEntities() {
        assertEquals("中文", NodeParser.decodeBody("&#20013;&#x6587;"))
    }

    @Test
    fun extractContent_endToEndOnRealShapedPayload() {
        val html = """<html><head></head><body><article>""" +
            """<p idx="0"><span>焚火溺命，这一段足够长，用来跨过最短正文的判据。</span></p>""" +
            """<p idx="1"><span>这一刻，他终于明白了自己要走的路。</span></p>""" +
            """</article></body></html>"""
        val root = jsonObj(
            "code" to 0.jsn(),
            "data" to jsonObj("content" to html.js(), "title" to "第一章".js())
        )
        val content = NodeParser.extractContent(root, "any")
        assertNotNull(content)
        assertTrue(content!!.text.contains("焚火溺命"))
        assertTrue(!content.text.contains("<"))
        assertEquals("第一章", content.title)
    }

    @Test
    fun extractContent_rejectsNonZeroCode() {
        val root = jsonObj(
            "code" to 1.jsn(),
            "data" to jsonObj("content" to ("字".repeat(100)).js())
        )
        assertNull(NodeParser.extractContent(root, "x"))
    }

    @Test
    fun extractContent_rejectsTagsOnlyPayload() {
        // HTML 原文很长但剥完没有内容 —— 判据必须在剥离之后
        val html = "<article>" + "<p></p>".repeat(50) + "</article>"
        val root = jsonObj("data" to jsonObj("content" to html.js()))
        assertNull(NodeParser.extractContent(root, "x"))
    }

    @Test
    fun extractContent_readsNestedItemIdObject() {
        val root = jsonObj(
            "data" to jsonObj(
                "7672698507827891224" to jsonObj(
                    "content" to "<article><p>${"内".repeat(60)}</p></article>".js(),
                    "title" to "嵌套正文".js(),
                )
            )
        )
        val content = NodeParser.extractContent(root, "7672698507827891224")
        assertNotNull(content)
        assertEquals("嵌套正文", content!!.title)
    }

    @Test
    fun extractContent_defaultsTitleWhenMissing() {
        val root = jsonObj("data" to jsonObj("content" to ("字".repeat(60)).js()))
        assertEquals("正文", NodeParser.extractContent(root, "x")!!.title)
    }

    // ---------- 反混淆 ----------

    @Test
    fun decodeBody_restoresPrivateUseCodepoints() {
        // 表 0 起点 58344，第 0 项映射为 'D'
        val obfuscated = buildString {
            appendCodePoint(58344)
            appendCodePoint(58345)
            append("正常的字")
        }
        val out = NodeParser.decodeBody(obfuscated)
        assertTrue("私有区字符应被还原，实际是：$out", out.none { it.code in 58344..58716 })
        assertTrue(out.contains("正常的字"))
    }

    @Test
    fun decodeBody_leavesPlainTextAlone() {
        val plain = "这是一段没有任何混淆的正文，不该被动到。"
        assertEquals(plain, NodeParser.decodeBody(plain))
    }

    @Test
    fun decodeBody_handlesBlank() {
        assertEquals("", NodeParser.decodeBody(""))
        assertEquals("", NodeParser.decodeBody("   "))
    }

    // ---------- 封面 ----------

    @Test
    fun normalizeCover_convertsBytecdnToThumbnail() {
        val raw = "https://p6-tt.bytecdn.cn/novel-pic/" +
            "p2o13e0b3f5a87a148b4db61d754aeafce5~tplv-shrink:640:0.image"
        val out = NodeParser.normalizeCover(raw)
        assertEquals(
            "https://p3-novel.byteimg.com/img/novel-pic/" +
                "p2o13e0b3f5a87a148b4db61d754aeafce5~tplv-tt-cs0:120:160.image",
            out
        )
    }

    @Test
    fun normalizeCover_passesThroughUnknownAndBlank() {
        assertEquals("", NodeParser.normalizeCover(""))
        assertEquals("https://example.com/a.jpg", NodeParser.normalizeCover("https://example.com/a.jpg"))
    }
}
