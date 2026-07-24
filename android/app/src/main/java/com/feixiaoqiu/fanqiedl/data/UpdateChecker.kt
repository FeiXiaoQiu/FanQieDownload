package com.feixiaoqiu.fanqiedl.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val name: String = "",
)

class UpdateChecker(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    fun checkLatest(
        apiUrl: String = GITHUB_LATEST_API,
    ): Result<ReleaseInfo> {
        return try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Guanyu-Android")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${resp.code}"))
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return Result.failure(IllegalStateException("空响应"))
                }
                val o = JSONObject(body)
                val tag = o.optString("tag_name").ifBlank {
                    return Result.failure(IllegalStateException("无 tag_name"))
                }
                val html = o.optString("html_url").ifBlank {
                    "https://github.com/FeiXiaoQiu/FanQieDownload/releases/latest"
                }
                Result.success(
                    ReleaseInfo(
                        tagName = tag,
                        htmlUrl = html,
                        name = o.optString("name"),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val GITHUB_LATEST_API =
            "https://api.github.com/repos/FeiXiaoQiu/FanQieDownload/releases/latest"
        const val REPO_URL = "https://github.com/FeiXiaoQiu/FanQieDownload"
    }
}
