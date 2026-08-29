package ink.yan.reader.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * org.json ↔ [JsonVal] 的转换。
 *
 * **本文件是全项目唯一 import org.json 的地方。** 把它隔离出来的意义：
 * 业务逻辑（BackgroundResolver / HitokotoParser）只认 [JsonVal]，
 * 于是它们能在 JVM 上直接跑单元测试，不必引入 Robolectric
 * 或为了单测额外挂一份 org.json 依赖。
 */
object JsonAdapter {

    fun parseObject(text: String): JsonVal.Obj? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching { from(org.json.JSONObject(trimmed)) }
            .getOrNull() as? JsonVal.Obj
    }

    fun parseAny(text: String): JsonVal? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when (trimmed.first()) {
                '{' -> from(JSONObject(trimmed))
                '[' -> from(JSONArray(trimmed))
                else -> JsonVal.Str(trimmed)
            }
        }.getOrNull()
    }

    private fun from(value: Any?): JsonVal {
        if (value == null || value === JSONObject.NULL) return JsonVal.Nil
        return when (value) {
            is JSONObject -> {
                val map = LinkedHashMap<String, JsonVal>(value.length())
                value.keys().forEach { k -> map[k] = from(value.opt(k)) }
                JsonVal.Obj(map)
            }

            is JSONArray -> {
                val list = ArrayList<JsonVal>(value.length())
                for (i in 0 until value.length()) list.add(from(value.opt(i)))
                JsonVal.Arr(list)
            }

            is String -> JsonVal.Str(value)
            is Boolean -> JsonVal.Num(if (value) 1.0 else 0.0)
            is Number -> JsonVal.Num(value.toDouble())
            else -> JsonVal.Nil
        }
    }
}
