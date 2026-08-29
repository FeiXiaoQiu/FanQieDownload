package ink.yan.reader.data

/**
 * 背景图源。
 *
 * 关键设计：[kind] 区分两种接口形态，[jsonPath] 用来从 JSON 里取图。
 * 常见做法是硬编码某一家接口的 JSON 结构，换个接口就废了 ——
 * 而「动态选择 + 自定义」正是要支持任意接口，所以这里改成
 * 「显式路径 + 递归探测兜底」两层策略。
 */

enum class BackgroundKind(val label: String, val hint: String) {
    /** 接口直接吐图片字节，交给 Coil 加载即可（可能会 302 跳真实图） */
    DIRECT("直链出图", "接口直接返回图片，例如 t.alcy.cc/ycy"),

    /** 接口返回 JSON，需要从里面把图片 URL 挖出来 */
    JSON("JSON 包裹", "接口返回 JSON，需指定或自动探测图片地址"),
    ;

    companion object {
        fun fromStorage(raw: String?): BackgroundKind =
            entries.find { it.name == raw } ?: DIRECT
    }
}

data class BackgroundSource(
    val id: String,
    val name: String,
    val url: String,
    val kind: BackgroundKind = BackgroundKind.DIRECT,
    /**
     * JSON 取值路径，点分，如 `images.url`、`data.urlsList.url`。
     * 留空则走自动探测（遍历整棵 JSON 找像图片 URL 的字符串）。
     */
    val jsonPath: String = "",
    val builtin: Boolean = false,
    /** 内容可能不适合公开场合，需要用户在设置里显式开启才出现在列表里 */
    val mature: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "背景源 id 不能为空" }
        require(url.isNotBlank()) { "背景源地址不能为空" }
    }
}

/**
 * 预置背景源。
 *
 * 其中 R18 那条标记为 [mature]，默认不出现在可选列表里，
 * 需要用户自己在设置里打开开关。
 */
object BackgroundPresets {

    val BING = BackgroundSource(
        id = "bg-bing",
        name = "必应每日一图",
        url = "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1",
        kind = BackgroundKind.JSON,
        // 必应返回的是相对路径 "/th?id=OHR..."，解析器会自动补 origin
        jsonPath = "images.url",
        builtin = true,
    )

    val ALCY = BackgroundSource(
        id = "bg-alcy",
        name = "随机二次元",
        url = "https://t.alcy.cc/ycy",
        kind = BackgroundKind.DIRECT,
        builtin = true,
    )

    val EARTH = BackgroundSource(
        id = "bg-earth",
        name = "随机风景",
        url = "https://picsum.photos/1080/1920",
        kind = BackgroundKind.DIRECT,
        builtin = true,
    )

    val R18 = BackgroundSource(
        id = "bg-r18",
        name = "R18（需手动开启）",
        url = "https://acg.yaohud.cn/dm/r18.php?type=json",
        kind = BackgroundKind.JSON,
        jsonPath = "data.url",
        builtin = true,
        mature = true,
    )

    /** 对外展示的列表：默认不含成熟内容 */
    fun visible(showMature: Boolean): List<BackgroundSource> =
        all.filter { !it.mature || showMature }

    val all: List<BackgroundSource> get() = listOf(BING, ALCY, EARTH, R18)

    fun byId(id: String): BackgroundSource? = all.find { it.id == id }
}
