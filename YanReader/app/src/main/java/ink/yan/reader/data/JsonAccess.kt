package ink.yan.reader.data

/**
 * [JsonVal] 的读取辅助。
 *
 * 集中在这里是因为 NodeParser 与 NodeClient 都要用，而两边各自写一份
 * 必然会在字段名回退这类细节上长出分歧。
 *
 * 全部返回可空或默认值的意义：第三方接口的字段缺失是常态，
 * 缺一个字段就让整次解析失败，等于把节点的兼容性问题转嫁给用户。
 */

fun JsonVal.Obj.hasKey(key: String): Boolean = key in fields

fun JsonVal.Obj.field(key: String): JsonVal? = fields[key]

fun JsonVal.Obj.obj(key: String): JsonVal.Obj? = fields[key] as? JsonVal.Obj

fun JsonVal.Obj.arr(key: String): List<JsonVal> =
    (fields[key] as? JsonVal.Arr)?.items.orEmpty()

/** 依次尝试多个字段名，取第一个非空值 —— 节点间命名不统一，只能都试一遍。 */
fun JsonVal.Obj.text(vararg keys: String): String {
    for (k in keys) {
        val v = (fields[k] as? JsonVal.Str)?.value
        if (!v.isNullOrBlank()) return v
    }
    return ""
}

fun JsonVal.Obj.num(key: String): Double? = (fields[key] as? JsonVal.Num)?.value

fun JsonVal.Obj.int(key: String, fallback: Int): Int = num(key)?.toInt() ?: fallback

/** 布尔在 JSON 里是 true/false，但有些接口用 0/1，统一按数字判断。 */
fun JsonVal.Obj.flag(key: String): Boolean? = num(key)?.let { it != 0.0 }

/** 不是对象时退化成空对象，让调用链不必层层判空。 */
fun JsonVal.objOrEmpty(): JsonVal.Obj = this as? JsonVal.Obj ?: JsonVal.Obj(emptyMap())
