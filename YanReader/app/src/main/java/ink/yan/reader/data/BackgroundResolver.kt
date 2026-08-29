package ink.yan.reader.data

/**
 * 从背景接口响应里挖出图片地址。
 *
 * 之所以要写这一层：背景接口的返回形态没有统一标准，实测下来至少三种
 *
 *   · 直链出图   t.alcy.cc/ycy   —— 302 直接跳 webp，压根没有 JSON
 *   · JSON+绝对路径  acg.yaohud.cn —— {"data":{"url":"https://..."}}
 *   · JSON+相对路径  必应每日一图 —— {"images":[{"url":"/th?id=OHR..."}]}
 *
 * 第三种最坑：相对路径不补全就是个无效地址。常见实现是硬编码
 * `data[].urlsList[].url` 一家的结构，换接口就失效；这里改成
 * 「显式路径优先 + 全树探测兜底 + 相对路径补全」，任意接口都能吃。
 *
 * 全部纯 Kotlin，不依赖 OkHttp / org.json / android.*，可直接单测。
 */
object BackgroundResolver {

    /**
     * 图片扩展名判定。刻意不要求出现在结尾 —— 必应的地址是
     * `/th?id=OHR.xxx_1920x1080.jpg&rf=yyy.jpg&pid=hp`，
     * `.jpg` 后面还跟着别的参数，只认结尾会漏掉。
     */
    private val IMG_EXT = Regex(
        """\.(jpe?g|png|webp|gif|bmp|avif)(\?|&|#|$)""",
        RegexOption.IGNORE_CASE,
    )

    /** 已是绝对地址则返回原样；否则尝试补全；无法补全返回空串。 */
    fun absolutize(raw: String, baseUrl: String): String {
        val u = raw.trim()
        if (u.isEmpty()) return ""
        if (u.startsWith("http://", true) || u.startsWith("https://", true)) return u
        if (u.startsWith("//")) {
            val scheme = baseUrl.substringBefore("://", "https")
            return "$scheme:$u"
        }
        if (u.startsWith("/")) {
            val origin = originOf(baseUrl) ?: return ""
            return origin + u
        }
        // 不以 / 开头的相对路径无法确定基准目录，宁可放弃也不要猜
        return ""
    }

    /** 取 `https://host` 这一段。baseUrl 不合法时返回 null。 */
    fun originOf(baseUrl: String): String? {
        val i = baseUrl.indexOf("://")
        if (i <= 0) return null
        val rest = baseUrl.substring(i + 3)
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isBlank()) return null
        return baseUrl.substring(0, i) + "://" + host
    }

    fun looksLikeImage(raw: String): Boolean {
        val u = raw.trim()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return false
        return IMG_EXT.containsMatchIn(u)
    }

    /**
     * 按显式路径取值。
     *
     * 路径语法是点分字段，遇到数组会自动展开：
     *   `images.url`        取 images 数组里每个元素的 url
     *   `data.urlsList.url` 二级数组（历史格式，兼容）
     *   `data.url`          普通嵌套对象
     *   `data[0].url`       也认，方括号可省略
     */
    fun extract(root: JsonVal, path: String, baseUrl: String): String? {
        val segs = parsePath(path)
        if (segs.isEmpty()) return null
        val found = mutableListOf<String>()
        collect(root, segs, found)
        return found
            .map { absolutize(it, baseUrl) }
            .filter { looksLikeImage(it) }
            .distinct()
            .randomOrNull()
    }

    /** 不靠路径，遍历整棵树收集所有像图片的绝对地址。 */
    fun probe(root: JsonVal, baseUrl: String): List<String> {
        val all = mutableListOf<String>()
        walk(root, all)
        return all
            .map { absolutize(it, baseUrl) }
            .filter { looksLikeImage(it) }
            .distinct()
    }

    /**
     * 完整解析：先按路径取，取不到再退回全树探测。
     *
     * 显式路径失效时静默回退是有意的 —— 接口改版是常态，
     * 与其弹「解析失败」不如先试试能不能蒙对，实在没有再让上层走兜底。
     */
    fun resolve(root: JsonVal, path: String, baseUrl: String): String? =
        if (path.isBlank()) {
            probe(root, baseUrl).randomOrNull()
        } else {
            extract(root, path, baseUrl) ?: probe(root, baseUrl).randomOrNull()
        }

    internal fun parsePath(path: String): List<String> =
        path.split('.', '[', ']', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun collect(node: JsonVal, segs: List<String>, out: MutableList<String>) {
        if (segs.isEmpty()) return
        val head = segs.first()
        val tail = segs.drop(1)
        when (node) {
            is JsonVal.Obj -> {
                val child = node.fields[head] ?: return
                if (tail.isEmpty()) emit(child, out) else collect(child, tail, out)
            }

            is JsonVal.Arr -> {
                val index = head.toIntOrNull()
                if (index != null) {
                    val child = node.items.getOrNull(index) ?: return
                    if (tail.isEmpty()) emit(child, out) else collect(child, tail, out)
                } else {
                    // head 还没被消耗，对数组元素沿用整段路径
                    node.items.forEach { collect(it, segs, out) }
                }
            }

            else -> Unit
        }
    }

    private fun emit(node: JsonVal, out: MutableList<String>) {
        when (node) {
            is JsonVal.Str -> out.add(node.value)
            is JsonVal.Arr -> node.items.forEach { emit(it, out) }
            else -> Unit
        }
    }

    private fun walk(node: JsonVal, out: MutableList<String>) {
        when (node) {
            is JsonVal.Str -> out.add(node.value)
            is JsonVal.Obj -> node.fields.values.forEach { walk(it, out) }
            is JsonVal.Arr -> node.items.forEach { walk(it, out) }
            is JsonVal.Num -> Unit
            JsonVal.Nil -> Unit
        }
    }
}
