package ink.yan.reader.data

/**
 * 极简 JSON 值模型。
 *
 * 为什么不用 org.json（JSONObject / JSONArray）直接写解析：
 * 那两个类在 Android 上是平台内置，JVM 单元测试里却是
 * android.jar 的 stub，一调用就抛 "Method ... not mocked"。
 * 为了背景解析这种纯逻辑能写单元测试，这里只保留三种真正用得上的
 * 节点类型，解析算法全部面向它编写；与 org.json 的互转见 JsonAdapter.kt，
 * 那一个文件是唯一 import org.json 的地方。
 */
sealed interface JsonVal {

    data class Str(val value: String) : JsonVal

    data class Obj(val fields: Map<String, JsonVal>) : JsonVal

    data class Arr(val items: List<JsonVal>) : JsonVal

    /** null、数字、布尔一律折叠成它 —— 解析图片地址时用不到具体值 */
    data object Nil : JsonVal
}

// —— 便于书写与测试的构造糖 ——

fun jsonObj(vararg pairs: Pair<String, JsonVal>): JsonVal.Obj =
    JsonVal.Obj(mapOf(*pairs))

fun jsonArr(vararg items: JsonVal): JsonVal.Arr =
    JsonVal.Arr(items.toList())

fun String.js(): JsonVal.Str = JsonVal.Str(this)
