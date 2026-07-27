package com.feixiaoqiu.fanqiedl.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class JsonBackgroundResolver {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 请求 JSON 接口，从 data[].urlsList[].url 中随机返回一张图 URL；失败返回 null */
    suspend fun resolve(apiUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(apiUrl).get().build()
            val body = http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string().orEmpty()
            }
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext null
            val candidates = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val urls = item.optJSONArray("urlsList") ?: continue
                for (j in 0 until urls.length()) {
                    val urlObj = urls.optJSONObject(j) ?: continue
                    val url = urlObj.optString("url").trim()
                    if (url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                        candidates.add(url)
                    }
                }
            }
            if (candidates.isEmpty()) return@withContext null
            candidates.random()
        } catch (_: Exception) {
            null
        }
    }
}
