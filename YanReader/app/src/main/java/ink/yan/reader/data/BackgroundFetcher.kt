package ink.yan.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 背景图地址解析的网络层。
 *
 * [BackgroundKind.DIRECT] 型不在这里下载图片，只把地址原样返回，
 * 由 Coil 去加载并跟随 302 —— 自己下再喂给 Coil 等于多读一遍内存。
 */
class BackgroundFetcher(
    private val http: OkHttpClient = defaultHttp(),
) {

    /**
     * 解析出可直接交给 Coil 的图片地址；失败返回 null，由上层决定是否兜底。
     *
     * @param bust 是否附加时间戳参数。同一个地址不 bust 的话 Coil 命中缓存，
     *             用户点「换一张」会毫无反应 —— 这是个很容易漏的坑。
     */
    suspend fun resolveUrl(source: BackgroundSource, bust: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val url = source.url.trim()
            when (source.kind) {
                BackgroundKind.DIRECT -> if (bust) cacheBust(url) else url

                BackgroundKind.JSON -> {
                    val body = runCatching {
                        val req = Request.Builder().url(url).get().build()
                        http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@withContext null
                            resp.body?.string().orEmpty()
                        }
                    }.getOrNull() ?: return@withContext null

                    val root = JsonAdapter.parseAny(body) ?: return@withContext null
                    val picked = BackgroundResolver.resolve(root, source.jsonPath, url)
                        ?: return@withContext null
                    if (bust) cacheBust(picked) else picked
                }
            }
        }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        /**
         * 加个一次性查询参数绕开缓存。
         * 注意要判断已有 query：直接拼 `?_t=` 到带参地址上会破坏原参数。
         */
        fun cacheBust(url: String): String {
            if (url.isBlank()) return url
            val sep = if (url.contains("?")) "&" else "?"
            return "$url${sep}_t=${System.currentTimeMillis()}"
        }
    }
}
