package com.feixiaoqiu.fanqiedl.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class R18BackgroundResolver {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val MAX_RETRIES = 8
        private const val RETRY_DELAY_MS = 300L
    }

    /** 请求 R18 JSON，检查分辨率，只返回竖图 URL；最多重试 8 次 */
    suspend fun resolve(): String = with(kotlinx.coroutines.Dispatchers.IO) {
        kotlinx.coroutines.withContext(this) {
            for (i in 0 until MAX_RETRIES) {
                try {
                    val req = Request.Builder()
                        .url("${DefaultNodes.R18_BACKGROUND_API}?type=json")
                        .header("User-Agent", MOBILE_UA)
                        .get()
                        .build()
                    val body = http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                        resp.body?.string().orEmpty()
                    }
                    val json = JSONObject(body)
                    val data = json.getJSONObject("data")
                    val resolution = data.getString("resolution") // "1628×2186"
                    val parts = resolution.split("×")
                    val w = parts[0].toIntOrNull() ?: break
                    val h = parts[1].toIntOrNull() ?: break
                    if (w < h) { // 竖图
                        val url = data.optString("url", "").ifBlank {
                            "${DefaultNodes.R18_BACKGROUND_API}?img=${data.getString("acgurl").substringAfter("img=")}"
                        }
                        return@withContext url
                    }
                } catch (_: Exception) {
                    // 重试
                }
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
            // 所有重试失败，回退到直接出图 URL
            return@withContext DefaultNodes.R18_BACKGROUND_API
        }
    }
}
