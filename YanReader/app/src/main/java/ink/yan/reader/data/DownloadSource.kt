package ink.yan.reader.data

/**
 * 更新包下载地址模板。
 *
 * [urlTemplate] 里的 `{url}` 会被替换成 GitHub Release 附件的原始地址。
 * 模板化而不是写死镜像域名，是为了让用户能自己加源：
 * 镜像站寿命普遍不长，写死在代码里等于把可用性押在第三方身上。
 */
data class DownloadSource(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val builtin: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "下载源 id 不能为空" }
        require(urlTemplate.contains("{url}")) { "模板必须包含 {url} 占位符" }
    }
}

object DownloadPresets {

    val DIRECT = DownloadSource(
        id = "direct",
        name = "GitHub 原链接",
        urlTemplate = "{url}",
        builtin = true,
    )

    val MIRROR_XMLY = DownloadSource(
        id = "mirror",
        name = "gh.xmly.dev 镜像",
        urlTemplate = "https://gh.xmly.dev/{url}",
        builtin = true,
    )

    val MIRROR_GH_PROXY = DownloadSource(
        id = "mirror-gh-proxy",
        name = "gh-proxy.com 镜像",
        urlTemplate = "https://gh-proxy.com/{url}",
        builtin = true,
    )

    val all: List<DownloadSource> get() = listOf(DIRECT, MIRROR_XMLY, MIRROR_GH_PROXY)

    fun byId(id: String): DownloadSource? = all.find { it.id == id }
}

/** 把占位符换成真实地址。模板里没有占位符时原样返回。 */
fun resolveSourceUrl(template: String, assetUrl: String): String =
    template.replace("{url}", assetUrl).trim()

/**
 * 尝试顺序：镜像在前，直连垫底。
 *
 * 判据是「模板是否就是占位符本身」而不是硬编码某个镜像域名 ——
 * 这样用户自己加的镜像也能自动排在直连前面。
 */
fun orderDownloadSources(sources: List<DownloadSource>): List<DownloadSource> =
    sources.sortedBy { src -> if (src.urlTemplate.trim() == "{url}") 1 else 0 }

/** 生成候选地址列表，过滤掉替换后为空的坏模板。 */
fun buildDownloadCandidates(
    sources: List<DownloadSource>,
    assetUrl: String,
): List<Pair<DownloadSource, String>> =
    orderDownloadSources(sources)
        .map { src -> src to resolveSourceUrl(src.urlTemplate, assetUrl) }
        .filter { (_, url) -> url.isNotBlank() }
