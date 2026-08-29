package ink.yan.reader.data

/**
 * 一言响应解析。
 *
 * 各家的字段名完全没有共识，所以统一按「优先级列表」逐个试：
 * 正文 hitokoto/text/content/msg…，出处 from/source…，作者 from_who/author…。
 * 据此定下的解析规则，补了三处容错：
 *
 *   1. 句子可能被包在 `data` 里（很多自建接口这么干）
 *   2. 字段名做大小写不敏感兜底
 *   3. 目标字段一个都找不到时，退而求其次挑一个「像句子」的字符串，
 *      而不是把整坨原始 JSON 显示给用户
 *
 * 纯 Kotlin，不碰网络也不碰 org.json，可直接单测。
 */
object HitokotoParser {

    private val TEXT_KEYS = listOf("hitokoto", "text", "content", "msg", "word", "sentence", "saying")
    private val FROM_KEYS = listOf("from", "source", "origin")
    private val WHO_KEYS = listOf("from_who", "author", "by", "creator")

    /** 从 JSON 树里拼一句话；拼不出来返回 null。 */
    fun fromJson(root: JsonVal): String? {
        var obj = root as? JsonVal.Obj ?: return null
        // 句子可能在 data 里，最多再往下钻一层
        (obj.fields["data"] as? JsonVal.Obj)?.let { obj = it }

        val text = field(obj, TEXT_KEYS)
        if (text.isNullOrBlank()) return looseSentence(root)

        val from = field(obj, FROM_KEYS)
        val who = field(obj, WHO_KEYS)

        return buildString {
            append(text.trim())
            val tail = listOfNotNull(
                from?.trim()?.takeIf { it.isNotBlank() },
                who?.trim()?.takeIf { it.isNotBlank() },
            )
            if (tail.isNotEmpty()) append("——").append(tail.joinToString("·"))
        }
    }

    /**
     * 原始响应 → 一句话。
     * @param json 已解析好的 JSON 树；不是 JSON 时传 null，此时按纯文本处理。
     */
    fun fromBody(body: String, json: JsonVal?): String {
        val raw = body.trim()
        if (raw.isEmpty()) return ""
        if (json != null) fromJson(json)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return raw
    }

    private fun field(obj: JsonVal.Obj, names: List<String>): String? {
        for (n in names) {
            obj.fields[n]?.asText()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        for ((k, v) in obj.fields) {
            if (names.any { it.equals(k, ignoreCase = true) }) {
                v.asText()?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    private fun JsonVal.asText(): String? = (this as? JsonVal.Str)?.value?.trim()

    /**
     * 兜底：遍历所有字符串，挑第一个「像句子」的。
     * 判定条件刻意保守 —— 太宽松会把 URL、时间戳之类也当成句子。
     */
    private fun looseSentence(root: JsonVal): String? {
        val pool = mutableListOf<String>()
        walk(root, pool)
        return pool.firstOrNull {
            it.length in 8..300 &&
                !it.contains("http", ignoreCase = true) &&
                !it.contains('{') && !it.contains('}') &&
                !it.contains('<')
        }
    }

    private fun walk(node: JsonVal, out: MutableList<String>) {
        when (node) {
            is JsonVal.Str -> out.add(node.value.trim())
            is JsonVal.Obj -> node.fields.values.forEach { walk(it, out) }
            is JsonVal.Arr -> node.items.forEach { walk(it, out) }
            is JsonVal.Num -> Unit
            JsonVal.Nil -> Unit
        }
    }
}
