package ink.yan.reader.data

/**
 * 应用更新的仓库坐标。
 *
 * 版本列表取 releases 数组而不是 `/releases/latest`：
 * 这个仓库同时发布别的项目，latest 拿到的可能是别人的包，
 * 只能拉列表再按 tag 前缀筛。
 */
object UpdateConfig {
    const val OWNER = "FeiXiaoQiu"
    const val REPO = "FanQieDownload"
    const val TAG_PREFIX = "yanreader-v"
    const val API = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"
    const val PAGE = "https://github.com/$OWNER/$REPO/releases"
}

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
)

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
    /** 草稿与预发布都不推给用户 */
    val draft: Boolean = false,
    val prerelease: Boolean = false,
) {
    /** tag 形如 `yanreader-v0.3.0`，剥掉前缀后即是比较用的版本号 */
    val version: String get() = normalizeVersion(tagName)
}

/**
 * 剥掉前导的非数字字符，只留下版本号主体。
 *
 * `yanreader-v0.3.0` / `v1.2` / `0.3.0-beta1` 都能处理。
 * 取不到数字时返回空串——调用方必须按「无版本」处理，不能当成 0。
 */
fun normalizeVersion(raw: String): String {
    val s = raw.trim()
    val start = s.indexOfFirst { it.isDigit() }
    if (start < 0) return ""
    return s.substring(start)
        .takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' }
        .trimEnd('.', '-', '+')
}

/**
 * 语义化版本比较：逐段比数字，缺位补 0，非数字段按 0 计。
 *
 * 返回正数表示 [a] 更新。用数值比较而非字符串比较，
 * 否则 `0.10.0` 会被判成比 `0.9.0` 旧。
 */
fun compareVersion(a: String, b: String): Int {
    val pa = normalizeVersion(a).split('.')
    val pb = normalizeVersion(b).split('.')
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val x = pa.getOrNull(i)?.numericPrefix() ?: 0L
        val y = pb.getOrNull(i)?.numericPrefix() ?: 0L
        if (x != y) return if (x > y) 1 else -1
    }
    return 0
}

/** 取分段的前导数字；`0-beta1` 得 0，`10` 得 10，没有数字得 0。 */
private fun String.numericPrefix(): Long {
    val digits = takeWhile { it.isDigit() }
    return digits.toLongOrNull() ?: 0L
}

/** 从资产列表里挑 APK：优先名字带 `yanreader` 的，其次任意 apk。 */
fun pickApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
    assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.name.contains("yanreader", ignoreCase = true) }
        ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
