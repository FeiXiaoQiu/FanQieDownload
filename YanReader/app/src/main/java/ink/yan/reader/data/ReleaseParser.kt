package ink.yan.reader.data

/**
 * GitHub `/releases` 数组响应的解析。
 *
 * 面向 [JsonVal] 而不是 org.json，理由同 HitokotoParser：
 * 这段逻辑要能在 JVM 上跑单元测试，而 org.json 在单测里是 stub。
 *
 * 只认标准字段名。GitHub 的响应结构很稳定，不像第三方接口那样各家自定义，
 * 所以这里不做全树探测——探测是给不可控输入兜底的，对可控输入只会掩盖错误。
 */
object ReleaseParser {

    fun parseReleases(root: JsonVal): List<ReleaseInfo> {
        val arr = root as? JsonVal.Arr ?: return emptyList()
        return arr.items.mapNotNull { parseRelease(it) }
    }

    fun parseRelease(node: JsonVal): ReleaseInfo? {
        val o = node as? JsonVal.Obj ?: return null
        val tag = o.str("tag_name") ?: return null
        return ReleaseInfo(
            tagName = tag,
            title = o.str("name").orEmpty().ifBlank { tag },
            notes = o.str("body").orEmpty(),
            htmlUrl = o.str("html_url")?.ifBlank { null } ?: UpdateConfig.PAGE,
            assets = (o.fields["assets"] as? JsonVal.Arr)
                ?.items
                ?.mapNotNull { parseAsset(it) }
                .orEmpty(),
            draft = o.flag("draft"),
            prerelease = o.flag("prerelease"),
        )
    }

    private fun parseAsset(node: JsonVal): ReleaseAsset? {
        val o = node as? JsonVal.Obj ?: return null
        val url = o.str("browser_download_url") ?: return null
        val name = o.str("name").orEmpty().ifBlank { url.substringAfterLast('/') }
        return ReleaseAsset(name = name, url = url, size = o.num("size"))
    }

    /**
     * 从一次响应里挑出本应用的最新正式版。
     *
     * 仓库里还发布着别的项目，必须按 tag 前缀筛掉；
     * 草稿与预发布一并排除，避免把测试包推给用户。
     * 按版本号取最大而不是信任数组顺序 —— 数组顺序会被置顶 release 打乱。
     */
    fun latestRelease(root: JsonVal): ReleaseInfo? =
        parseReleases(root)
            .filter { it.tagName.startsWith(UpdateConfig.TAG_PREFIX) }
            .filter { !it.draft && !it.prerelease }
            .maxWithOrNull { a, b -> compareVersion(a.version, b.version) }

    // —— 读取辅助 ——

    private fun JsonVal.Obj.str(key: String): String? =
        (fields[key] as? JsonVal.Str)?.value

    private fun JsonVal.Obj.num(key: String): Long =
        (fields[key] as? JsonVal.Num)?.value?.toLong() ?: 0L

    private fun JsonVal.Obj.flag(key: String): Boolean =
        (fields[key] as? JsonVal.Num)?.value?.let { it != 0.0 } ?: false
}
