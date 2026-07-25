package com.feixiaoqiu.fanqiedl.data

import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long = 0,
    val abi: String = "",
)

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val name: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
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
                val assets = mutableListOf<ReleaseAsset>()
                val arr = o.optJSONArray("assets")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val ao = arr.getJSONObject(i)
                        val assetName = ao.optString("name")
                        val assetUrl = ao.optString("browser_download_url")
                        val assetSize = ao.optLong("size", 0)
                        if (assetName.endsWith(".apk") && assetUrl.isNotBlank()) {
                            assets.add(
                                ReleaseAsset(
                                    name = assetName,
                                    downloadUrl = assetUrl,
                                    size = assetSize,
                                    abi = detectAbi(assetName),
                                )
                            )
                        }
                    }
                }
                Result.success(
                    ReleaseInfo(
                        tagName = tag,
                        htmlUrl = html,
                        name = o.optString("name"),
                        assets = assets,
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

        fun detectAbi(filename: String): String {
            val known = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")
            return known.firstOrNull { filename.contains(it) } ?: ""
        }

        fun pickAssetForDevice(assets: List<ReleaseAsset>): ReleaseAsset? {
            if (assets.isEmpty()) return null
            val preferred = Build.SUPPORTED_ABIS.flatMap { deviceAbi ->
                val abi = when {
                    deviceAbi.startsWith("arm64") -> "arm64-v8a"
                    deviceAbi.startsWith("armeabi") -> "armeabi-v7a"
                    deviceAbi.startsWith("x86_64") -> "x86_64"
                    deviceAbi.startsWith("x86") -> "x86"
                    else -> ""
                }
                assets.filter { it.abi == abi }
            }
            return assets.firstOrNull { it.abi.isEmpty() }
                ?: preferred.firstOrNull()
                ?: assets.firstOrNull()
        }
    }
}
