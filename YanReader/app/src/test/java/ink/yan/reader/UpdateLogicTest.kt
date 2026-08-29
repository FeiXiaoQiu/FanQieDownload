package ink.yan.reader

import ink.yan.reader.data.DownloadPresets
import ink.yan.reader.data.DownloadSource
import ink.yan.reader.data.ReleaseAsset
import ink.yan.reader.data.ReleaseParser
import ink.yan.reader.data.buildDownloadCandidates
import ink.yan.reader.data.compareVersion
import ink.yan.reader.data.formatSize
import ink.yan.reader.data.jsonArr
import ink.yan.reader.data.jsonObj
import ink.yan.reader.data.js
import ink.yan.reader.data.jsn
import ink.yan.reader.data.normalizeVersion
import ink.yan.reader.data.orderDownloadSources
import ink.yan.reader.data.pickApkAsset
import ink.yan.reader.data.resolveSourceUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载源与更新解析的纯逻辑测试。
 * 这两块刻意不碰 OkHttp 与 org.json，所以能在 JVM 上直接跑。
 */
class UpdateLogicTest {

    private val assetUrl = "https://github.com/o/r/releases/download/v/yanreader-0.3.0.apk"

    // ── 模板替换 ──

    @Test
    fun `直连模板替换后就是原地址`() {
        assertEquals(assetUrl, resolveSourceUrl("{url}", assetUrl))
    }

    @Test
    fun `镜像模板把原地址接到前缀后面`() {
        assertEquals(
            "https://gh.xmly.dev/$assetUrl",
            resolveSourceUrl("https://gh.xmly.dev/{url}", assetUrl),
        )
    }

    @Test
    fun `模板前后有空白也会被清掉`() {
        assertEquals(assetUrl, resolveSourceUrl("  {url}  ", assetUrl))
    }

    // ── 排序与候选 ──

    @Test
    fun `直连排在镜像后面`() {
        val ordered = orderDownloadSources(DownloadPresets.all)
        assertEquals("{url}", ordered.last().urlTemplate)
        assertEquals(DownloadPresets.DIRECT.id, ordered.last().id)
    }

    @Test
    fun `自定义的镜像也会排在直连前面`() {
        val mine = DownloadSource("mine", "我的镜像", "https://my.example.com/{url}")
        val ordered = orderDownloadSources(listOf(DownloadPresets.DIRECT, mine))
        assertEquals("mine", ordered.first().id)
    }

    @Test
    fun `候选列表按排序后的顺序生成`() {
        val got = buildDownloadCandidates(DownloadPresets.all, assetUrl)
        assertEquals(3, got.size)
        assertTrue(got.first().second.startsWith("https://gh."))
        assertEquals(assetUrl, got.last().second)
    }

    @Test
    fun `空的源列表生成不出候选`() {
        assertTrue(buildDownloadCandidates(emptyList(), assetUrl).isEmpty())
    }

    // ── 版本号 ──

    @Test
    fun `tag 前缀会被剥掉`() {
        assertEquals("0.3.0", normalizeVersion("yanreader-v0.3.0"))
        assertEquals("1.2", normalizeVersion("v1.2"))
        assertEquals("0.9.0", normalizeVersion("0.9.0"))
    }

    @Test
    fun `没有数字时版本号为空串而不是 0`() {
        assertEquals("", normalizeVersion("yanreader-v"))
        assertEquals("", normalizeVersion(""))
    }

    @Test
    fun `0_10 比 0_9 新`() {
        // 字符串比较会得出相反结论，这正是不能用 String.compareTo 的原因
        assertTrue(compareVersion("0.10.0", "0.9.0") > 0)
        assertTrue(compareVersion("0.9.0", "0.10.0") < 0)
    }

    @Test
    fun `缺位的段按 0 补齐`() {
        assertEquals(0, compareVersion("1.2", "1.2.0"))
        assertTrue(compareVersion("1.2.1", "1.2") > 0)
    }

    @Test
    fun `带 tag 前缀也能正确比较`() {
        assertTrue(compareVersion("yanreader-v0.3.0", "yanreader-v0.2.0") > 0)
    }

    // ── 资产挑选 ──

    @Test
    fun `优先挑名字里带应用名的 apk`() {
        val got = pickApkAsset(
            listOf(
                ReleaseAsset("other-1.0.apk", "https://x/other", 1),
                ReleaseAsset("yanreader-0.3.0.apk", "https://x/yan", 2),
            )
        )
        assertEquals("yanreader-0.3.0.apk", got?.name)
    }

    @Test
    fun `没有同名包时退回任意一个 apk`() {
        val got = pickApkAsset(
            listOf(
                ReleaseAsset("notes.txt", "https://x/t", 1),
                ReleaseAsset("app-release.apk", "https://x/a", 2),
            )
        )
        assertEquals("app-release.apk", got?.name)
    }

    @Test
    fun `没有 apk 时返回 null`() {
        assertNull(pickApkAsset(listOf(ReleaseAsset("a.txt", "https://x/a", 1))))
    }

    // ── 发布解析 ──

    private fun release(
        tag: String,
        apkName: String = "yanreader.apk",
        size: Long = 1024L,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = jsonObj(
        "tag_name" to tag.js(),
        "name" to "砚 $tag".js(),
        "body" to "更新说明".js(),
        "html_url" to "https://github.com/o/r/releases/tag/$tag".js(),
        "draft" to (if (draft) 1 else 0).jsn(),
        "prerelease" to (if (prerelease) 1 else 0).jsn(),
        "assets" to jsonArr(
            jsonObj(
                "name" to apkName.js(),
                "browser_download_url" to "https://github.com/o/r/download/$tag/$apkName".js(),
                "size" to size.jsn(),
            )
        ),
    )

    @Test
    fun `按 tag 前缀筛掉别的项目的发布`() {
        val root = jsonArr(
            release("v1.9.0"),
            release("yanreader-v0.2.0"),
        )
        assertEquals("yanreader-v0.2.0", ReleaseParser.latestRelease(root)?.tagName)
    }

    @Test
    fun `取版本号最大的那个而不是数组第一个`() {
        // 置顶 release 会打乱数组顺序，只认顺序会推错版本
        val root = jsonArr(
            release("yanreader-v0.2.0"),
            release("yanreader-v0.3.0"),
            release("yanreader-v0.1.0"),
        )
        assertEquals("0.3.0", ReleaseParser.latestRelease(root)?.version)
    }

    @Test
    fun `草稿与预发布不会被推给用户`() {
        val root = jsonArr(
            release("yanreader-v0.4.0", draft = true),
            release("yanreader-v0.5.0", prerelease = true),
            release("yanreader-v0.3.0"),
        )
        assertEquals("0.3.0", ReleaseParser.latestRelease(root)?.version)
    }

    @Test
    fun `资产的名字与大小会被解析出来`() {
        val info = ReleaseParser.latestRelease(jsonArr(release("yanreader-v0.3.0", "yanreader-0.3.0.apk", 2048)))
        assertEquals(1, info?.assets?.size)
        assertEquals("yanreader-0.3.0.apk", info?.assets?.first()?.name)
        assertEquals(2048L, info?.assets?.first()?.size)
    }

    @Test
    fun `响应不是数组时返回 null 而不是崩`() {
        assertNull(ReleaseParser.latestRelease(jsonObj("message" to "Not Found".js())))
    }

    @Test
    fun `数组里的坏条目被跳过而不是让整次解析失败`() {
        val root = jsonArr(
            jsonObj("name" to "缺 tag_name".js()),
            release("yanreader-v0.3.0"),
        )
        assertEquals("0.3.0", ReleaseParser.latestRelease(root)?.version)
    }

    @Test
    fun `文件大小格式化`() {
        assertEquals("2.0 KB", formatSize(2048))
        assertEquals("1.5 MB", formatSize(1_572_864))
        assertEquals("", formatSize(0))
    }
}
