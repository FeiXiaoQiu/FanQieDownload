package ink.yan.reader.data

/**
 * 一言（一句话）数据源。
 *
 * 与其它接口不同，一言的响应格式各家差异极大，所以解析侧一律走
 * 「按优先级试多个字段名」的宽松策略（见 HitokotoClient），
 * 这里只管地址和展示名。
 */
data class HitokotoSource(
    val id: String,
    val name: String,
    val url: String,
    val builtin: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "一言源 id 不能为空" }
        require(url.isNotBlank()) { "一言源地址不能为空" }
    }
}

object HitokotoPresets {

    val MIXED = HitokotoSource(
        id = "hk-mixed", name = "一言·综合", url = "https://v1.hitokoto.cn/", builtin = true,
    )
    val POETRY = HitokotoSource(
        id = "hk-poetry", name = "一言·诗词", url = "https://v1.hitokoto.cn/?c=i", builtin = true,
    )
    val LITERATURE = HitokotoSource(
        id = "hk-literature", name = "一言·文学", url = "https://v1.hitokoto.cn/?c=d", builtin = true,
    )
    val PHILOSOPHY = HitokotoSource(
        id = "hk-philosophy", name = "一言·哲学", url = "https://v1.hitokoto.cn/?c=k", builtin = true,
    )
    val JOKE = HitokotoSource(
        id = "hk-joke", name = "一言·抖机灵", url = "https://v1.hitokoto.cn/?c=l", builtin = true,
    )

    val all: List<HitokotoSource>
        get() = listOf(MIXED, POETRY, LITERATURE, PHILOSOPHY, JOKE)

    fun byId(id: String): HitokotoSource? = all.find { it.id == id }

    /** 网络不可用时的兜底文案。 */
    val FALLBACK: List<String> = listOf(
        "无论你去哪里，你总是在那里。——村上春树",
        "人生如逆旅，我亦是行人。——苏轼",
        "凡是过往，皆为序章。——莎士比亚",
        "我们都在阴沟里，但仍有人仰望星空。——王尔德",
        "当你凝视深渊时，深渊也在凝视你。——尼采",
        "现在，就是最好的开始。",
        "愿你出走半生，归来仍是少年。",
        "路漫漫其修远兮，吾将上下而求索。——屈原",
        "心有猛虎，细嗅蔷薇。——萨松",
        "上善若水，水善利万物而不争。——老子",
    )
}
