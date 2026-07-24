package com.feixiaoqiu.fanqiedl.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HitokotoClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    fun fetch(url: String): String {
        val target = url.trim().ifEmpty { DefaultNodes.DEFAULT_HITOKOTO }
        val req = Request.Builder()
            .url(target)
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty().trim()
            if (body.isEmpty()) throw IllegalStateException("空响应")
            return formatBody(body)
        }
    }

    fun fetchOrFallback(url: String): String {
        return try {
            fetch(url)
        } catch (_: Exception) {
            FALLBACK.random()
        }
    }

    companion object {
        fun formatBody(body: String): String {
            val t = body.trim()
            if (t.startsWith("{")) {
                return try {
                    val o = JSONObject(t)
                    val text = first(
                        o.optString("hitokoto"),
                        o.optString("text"),
                        o.optString("content"),
                        o.optString("msg"),
                    )
                    if (text.isBlank()) t
                    else {
                        val from = first(o.optString("from"), o.optString("source"))
                        val who = first(o.optString("from_who"), o.optString("author"))
                        when {
                            from.isNotBlank() && who.isNotBlank() -> "$text——$from·$who"
                            from.isNotBlank() -> "$text——$from"
                            who.isNotBlank() -> "$text——$who"
                            else -> text
                        }
                    }
                } catch (_: Exception) {
                    t
                }
            }
            return t
        }

        private fun first(vararg values: String): String {
            for (v in values) if (v.isNotBlank()) return v
            return ""
        }

        val FALLBACK = listOf(
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
}
