package com.feixiaoqiu.fanqiedl.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

class DownloadRepository(
    private val context: Context,
    private val client: FanqieNodeClient,
) {
    private val cacheDir: File
        get() = File(context.filesDir, "chapter_cache").also { it.mkdirs() }

    fun download(req: DownloadRequest): Flow<Any> = flow {
        emit(DownloadProgress(0, 0, "获取书籍信息…", 2))
        val meta = try {
            client.info(req.bookId)
        } catch (_: Exception) {
            BookInfo(req.bookId, req.title.ifBlank { "小说${req.bookId}" }, "未知", "")
        }
        coroutineContext.ensureActive()

        emit(DownloadProgress(0, 0, "获取目录…", 5))
        val all = client.catalog(req.bookId)
        val chapters = FanqieNodeClient.sliceChapters(all, req.startChapter, req.endChapter)
        if (chapters.isEmpty()) throw IllegalStateException("章节列表为空")
        val total = chapters.size
        val results = arrayOfNulls<Pair<String, String>>(total)
        var done = 0
        var cached = 0
        var errors = 0
        var consecutiveFail = 0

        chapters.forEachIndexed { idx, ch ->
            coroutineContext.ensureActive()
            var title = ch.title.ifBlank { "第${idx + 1}章" }
            try {
                if (req.resume) {
                    val c = loadCache(req.bookId, ch.itemId)
                    if (c != null) {
                        results[idx] = (c.first to c.second)
                        cached++
                        consecutiveFail = 0
                        done++
                        emit(
                            DownloadProgress(
                                done, total,
                                "下载中 $done/$total（缓存 $cached）",
                                5 + (done * 90 / total)
                            )
                        )
                        return@forEachIndexed
                    }
                }
                val got = client.content(ch.itemId)
                if (got.title.isNotBlank()) title = got.title
                results[idx] = title to got.text
                if (req.resume) saveCache(req.bookId, ch.itemId, title, got.text)
                consecutiveFail = 0
            } catch (e: Exception) {
                if (!coroutineContext.isActive) throw e
                results[idx] = title to "【本章获取失败: ${e.message}】"
                errors++
                consecutiveFail++
                if (consecutiveFail >= 10) {
                    throw IllegalStateException("连续失败过多，已中止（$done/$total）")
                }
            }
            done++
            emit(
                DownloadProgress(
                    done, total,
                    "下载中 $done/$total" + if (cached > 0) "（缓存 $cached）" else "",
                    5 + (done * 90 / total)
                )
            )
        }

        coroutineContext.ensureActive()
        emit(DownloadProgress(total, total, "写入文件…", 96))

        val lines = mutableListOf(
            meta.title,
            "作者：${meta.author}",
            "书籍ID：${req.bookId}",
            "章节数：$total",
            "模式：Android 本地",
        )
        if (meta.abstract.isNotBlank()) {
            lines.add("简介：" + meta.abstract.take(500))
        }
        lines.add("")
        lines.add("=".repeat(40))
        lines.add("")
        results.forEach { item ->
            if (item == null) return@forEach
            lines.add(item.first)
            lines.add("")
            lines.add(item.second)
            lines.add("")
            lines.add("-".repeat(30))
            lines.add("")
        }
        val content = lines.joinToString("\n")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = FanqieNodeClient.safeFileName(meta.title) + "-${req.bookId}-$stamp.txt"
        val saved = writeToDownloads(filename, content)

        emit(
            DownloadResult(
                status = "done",
                title = meta.title,
                filename = filename,
                displayPath = saved.displayPath,
                uriString = saved.uri?.toString(),
                done = total,
                total = total,
                errorCount = errors,
                message = buildString {
                    append("下载完成")
                    if (cached > 0) append("（缓存 $cached 章）")
                    if (errors > 0) append("，失败 $errors 章")
                },
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun loadCache(bookId: String, itemId: String): Pair<String, String>? {
        val f = File(cacheDir, "${bookId}_$itemId.json")
        if (!f.isFile) return null
        return try {
            val o = JSONObject(f.readText())
            val text = o.optString("text")
            if (text.length > 20) o.optString("title") to text else null
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCache(bookId: String, itemId: String, title: String, text: String) {
        try {
            val f = File(cacheDir, "${bookId}_$itemId.json")
            f.writeText(
                JSONObject()
                    .put("title", title)
                    .put("text", text)
                    .put("t", System.currentTimeMillis())
                    .toString()
            )
        } catch (_: Exception) {
            /* quota */
        }
    }

    data class SavedFile(val displayPath: String, val uri: Uri?)

    private fun writeToDownloads(filename: String, content: String): SavedFile {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FanQieDownload")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建下载文件")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法写入下载文件")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return SavedFile("Download/FanQieDownload/$filename", uri)
        }

        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outDir = File(dir, "FanQieDownload").also { it.mkdirs() }
        val file = File(outDir, filename)
        FileOutputStream(file).use { it.write(bytes) }
        return SavedFile(file.absolutePath, Uri.fromFile(file))
    }
}
