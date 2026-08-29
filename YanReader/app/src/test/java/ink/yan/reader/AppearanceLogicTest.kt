package ink.yan.reader

import ink.yan.reader.data.Appearance
import ink.yan.reader.data.BackgroundFetcher
import ink.yan.reader.data.BackgroundResolver
import ink.yan.reader.data.BackgroundSource
import ink.yan.reader.data.CornerStyle
import ink.yan.reader.data.GlassStrength
import ink.yan.reader.data.HitokotoParser
import ink.yan.reader.data.HitokotoPresets
import ink.yan.reader.data.JsonVal
import ink.yan.reader.data.StylePreset
import ink.yan.reader.data.jsonArr
import ink.yan.reader.data.jsonObj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外观 / 背景 / 一言的纯逻辑测试。
 * 与 CoreLogicTest 一样不依赖 Robolectric —— 数据层刻意避开了 android.* 与 org.json。
 */
class AppearanceLogicTest {

    // ── 背景：路径求值 ──

    @Test
    fun `必应的相对路径会被补全成绝对地址`() {
        // 实测响应结构：images 是数组，url 以 / 开头
        val root = jsonObj(
            "images" to jsonArr(
                jsonObj(
                    "url" to JsonVal.Str("/th?id=OHR.Test_1920x1080.jpg&rf=x.jpg&pid=hp"),
                    "urlbase" to JsonVal.Str("/th?id=OHR.Test"),
                )
            )
        )
        val got = BackgroundResolver.extract(
            root, "images.url",
            "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1",
        )
        assertEquals(
            "https://cn.bing.com/th?id=OHR.Test_1920x1080.jpg&rf=x.jpg&pid=hp",
            got,
        )
    }

    @Test
    fun `二级数组格式 data_urlsList_url 可取到值`() {
        val root = jsonObj(
            "data" to jsonArr(
                jsonObj(
                    "urlsList" to jsonArr(
                        jsonObj("url" to JsonVal.Str("https://img.example.com/a.webp")),
                        jsonObj("url" to JsonVal.Str("https://img.example.com/b.webp")),
                    )
                )
            )
        )
        val got = BackgroundResolver.extract(root, "data.urlsList.url", "https://api.example.com")
        // 两个候选都合法，取到任意一个都算通过
        assertTrue(got == "https://img.example.com/a.webp" || got == "https://img.example.com/b.webp")
    }

    @Test
    fun `嵌套对象的普通路径可取到值`() {
        val root = jsonObj("data" to jsonObj("url" to JsonVal.Str("https://x.com/p.png")))
        assertEquals(
            "https://x.com/p.png",
            BackgroundResolver.extract(root, "data.url", "https://x.com"),
        )
    }

    @Test
    fun `方括号下标写法也认`() {
        val root = jsonObj(
            "data" to jsonArr(
                jsonObj("url" to JsonVal.Str("https://x.com/first.png")),
                jsonObj("url" to JsonVal.Str("https://x.com/second.png")),
            )
        )
        assertEquals(
            "https://x.com/first.png",
            BackgroundResolver.extract(root, "data[0].url", "https://x.com"),
        )
    }

    @Test
    fun `路径取不到时自动探测兜底`() {
        val root = jsonObj(
            "code" to JsonVal.Str("200"),
            "pic" to JsonVal.Str("https://cdn.example.com/rand.jpg"),
        )
        // 路径写错，理应回退到全树探测
        assertEquals(
            "https://cdn.example.com/rand.jpg",
            BackgroundResolver.resolve(root, "not.exist.path", "https://api.example.com"),
        )
    }

    @Test
    fun `非图片地址会被过滤掉`() {
        val root = jsonObj(
            "link" to JsonVal.Str("https://example.com/page.html"),
            "img" to JsonVal.Str("https://example.com/real.png"),
        )
        val probed = BackgroundResolver.probe(root, "https://example.com")
        assertEquals(listOf("https://example.com/real.png"), probed)
    }

    @Test
    fun `没有 origin 时相对路径直接放弃而不是瞎猜`() {
        val root = jsonObj("url" to JsonVal.Str("/relative/pic.png"))
        val probed = BackgroundResolver.probe(root, "not-a-url")
        assertTrue("相对路径无法补全时应被丢弃", probed.isEmpty())
    }

    @Test
    fun `协议相对地址会补上 scheme`() {
        assertEquals(
            "https://cdn.example.com/a.png",
            BackgroundResolver.absolutize("//cdn.example.com/a.png", "https://api.example.com/x"),
        )
    }

    // ── 背景：缓存穿透 ──

    @Test
    fun `cacheBust 能正确区分有无 query`() {
        val a = BackgroundFetcher.cacheBust("https://x.com/pic")
        assertTrue(a.startsWith("https://x.com/pic?_t="))

        val b = BackgroundFetcher.cacheBust("https://x.com/api?format=js&idx=0")
        assertTrue(b.startsWith("https://x.com/api?format=js&idx=0&_t="))
    }

    // ── 一言解析 ──

    @Test
    fun `标准一言字段拼出 正文——出处·作者`() {
        val root = jsonObj(
            "hitokoto" to JsonVal.Str("三更灯火五更鸡，正是男儿读书时。"),
            "from" to JsonVal.Str("劝学诗"),
            "from_who" to JsonVal.Str("颜真卿"),
        )
        assertEquals(
            "三更灯火五更鸡，正是男儿读书时。——劝学诗·颜真卿",
            HitokotoParser.fromJson(root),
        )
    }

    @Test
    fun `备选字段名也能识别`() {
        val root = jsonObj(
            "text" to JsonVal.Str("行到水穷处，坐看云起时。"),
            "source" to JsonVal.Str("终南别业"),
            "author" to JsonVal.Str("王维"),
        )
        assertEquals("行到水穷处，坐看云起时。——终南别业·王维", HitokotoParser.fromJson(root))
    }

    @Test
    fun `句子包在 data 里也能取到`() {
        val root = jsonObj(
            "code" to JsonVal.Str("0"),
            "data" to jsonObj("content" to JsonVal.Str("自建接口常见这种包法")),
        )
        assertEquals("自建接口常见这种包法", HitokotoParser.fromJson(root))
    }

    @Test
    fun `只有正文时不会多出破折号`() {
        val root = jsonObj("hitokoto" to JsonVal.Str("孤句"))
        assertEquals("孤句", HitokotoParser.fromJson(root))
    }

    @Test
    fun `纯文本响应原样返回`() {
        assertEquals("今天也要开心。", HitokotoParser.fromBody("今天也要开心。", null))
    }

    @Test
    fun `JSON 里没有已知字段时退而求其次挑一句像话的`() {
        val root = jsonObj(
            "id" to JsonVal.Str("12"),
            "wise_words" to JsonVal.Str("这是一句来自奇怪字段的名人名言"),
        )
        assertEquals("这是一句来自奇怪字段的名人名言", HitokotoParser.fromJson(root))
    }

    @Test
    fun `兜底文案非空且数量足够`() {
        assertTrue(HitokotoPresets.FALLBACK.size >= 5)
        assertTrue(HitokotoPresets.FALLBACK.all { it.isNotBlank() })
    }

    // ── 外观 ──

    @Test
    fun `未微调时各项跟随预设`() {
        val a = Appearance(preset = StylePreset.DAI_LAN)
        assertEquals(StylePreset.DAI_LAN.glassFill, a.resolvedFill, 0.0001f)
        assertEquals(StylePreset.DAI_LAN.cornerDp, a.resolvedCorner)
        assertEquals(false, a.tweaked)
    }

    @Test
    fun `微调覆盖预设值`() {
        val a = Appearance(
            preset = StylePreset.YAN_QING,
            strength = GlassStrength.NONG,
            corner = CornerStyle.JIAO_NANG,
        )
        assertTrue("浓郁档应比标准更不透明", a.resolvedFill > StylePreset.YAN_QING.glassFill)
        assertEquals(StylePreset.YAN_QING.cornerDp + 10, a.resolvedCorner)
        assertTrue(a.tweaked)
    }

    @Test
    fun `切换预设会清空微调`() {
        val a = Appearance(preset = StylePreset.YAN_QING, strength = GlassStrength.NONG)
            .withPreset(StylePreset.MO_BAI)
        assertEquals(StylePreset.MO_BAI, a.preset)
        assertEquals(false, a.tweaked)
        assertEquals(StylePreset.MO_BAI.glassFill, a.resolvedFill, 0.0001f)
    }

    @Test
    fun `极端参数会被夹在合理区间`() {
        val a = Appearance(
            preset = StylePreset.MO_BAI,
            strength = GlassStrength.NONG,
            corner = CornerStyle.JIAO_NANG,
        )
        assertTrue("填充透明度不得超过 0.95", a.resolvedFill <= 0.95f)
        assertTrue("圆角不得超过 32dp", a.resolvedCorner <= 32)
    }

    @Test
    fun `背景源构造会拒绝空 id 与空地址`() {
        assertNull(runCatching { BackgroundSource("", "x", "https://a.b") }.getOrNull())
        assertNull(runCatching { BackgroundSource("id", "x", "") }.getOrNull())
    }
}
