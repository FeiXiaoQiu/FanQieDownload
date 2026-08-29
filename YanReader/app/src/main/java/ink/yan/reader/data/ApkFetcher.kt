package ink.yan.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 更新包的网络层：查版本、探测源、流式下载。
 *
 * 下载用流式写盘而不是先把 byte[] 读进内存再写：
 * APK 通常几 MB 到几十 MB，一次性读入在低内存机器上很容易 OOM。
 */
class ApkFetcher(
    private val http: OkHttpClient = defaultHttp(),
) {

    /**
     * 拉取最新版本。
     *
     * 返回 null 有两种含义，靠 Result 区分：
     * 成功但没有本应用的发布（正常，比如仓库刚建），或网络/解析失败。
     */
    suspend fun latest(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(UpdateConfig.API)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val root = JsonAdapter.parseAny(body) ?: error("响应不是合法 JSON")
                ReleaseParser.latestRelease(root)
            }
        }
    }

    /**
     * 探测一个候选地址是否可用。
     *
     * 用 HEAD 而不是 GET：只想知道通不通，没必要先把整个包拉下来。
     * 少数静态托管不支持 HEAD，返回 405 时按「可用」处理，
     * 让真正的下载去兜底判断 —— 保守起见宁可多试，不要误杀。
     */
    suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
                resp.code in 200..399 || resp.code == 405
            }
        }.getOrDefault(false)
    }

    /** 流式下载。写出前先落 .part 临时文件，完成后再改名，避免半成品被当成完整包。 */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            val part = File(dest.parentFile, "${dest.name}.part")
            part.delete()

            http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("响应体为空")
                val total = body.contentLength()
                var written = 0L
                body.byteStream().use { input ->
                    part.outputStream().buffered().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            onProgress(written, total)
                        }
                    }
                }
                if (total > 0 && written != total) {
                    part.delete()
                    error("下载不完整：$written / $total 字节")
                }
            }

            dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            dest
        }.onFailure {
            File(dest.parentFile, "${dest.name}.part").delete()
        }
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
