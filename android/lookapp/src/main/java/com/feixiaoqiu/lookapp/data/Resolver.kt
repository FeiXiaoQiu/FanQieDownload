package com.feixiaoqiu.lookapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Resolver {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolve(url: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().build()
            val body = http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string().orEmpty()
            }
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val candidates = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val urls = item.optJSONArray("urlsList") ?: continue
                for (j in 0 until urls.length()) {
                    val urlObj = urls.optJSONObject(j) ?: continue
                    val u = urlObj.optString("url").trim()
                    if (u.isNotBlank() &&
                        (u.startsWith("http://") || u.startsWith("https://"))
                    ) {
                        candidates.add(u)
                    }
                }
            }
            candidates
        } catch (_: Exception) {
            emptyList()
        }
    }
}
