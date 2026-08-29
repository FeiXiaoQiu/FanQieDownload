package ink.yan.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 一言网络层。只负责取回文本，解析交给 [HitokotoParser]。
 */
class HitokotoClient(
    private val http: OkHttpClient = defaultHttp(),
) {

    /** @throws IllegalStateException 网络或 HTTP 层面失败 */
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val target = url.trim().ifBlank { HitokotoPresets.MIXED.url }
        val req = Request.Builder()
            .url(target)
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val json = JsonAdapter.parseAny(body)
            HitokotoParser.fromBody(body, json)
        }
    }

    /** 永不抛异常：失败就用内置文案兜底，一言是装饰，不该拖累主流程。 */
    suspend fun fetchOrFallback(url: String): String = try {
        fetch(url).ifBlank { HitokotoPresets.FALLBACK.random() }
    } catch (_: Exception) {
        HitokotoPresets.FALLBACK.random()
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
