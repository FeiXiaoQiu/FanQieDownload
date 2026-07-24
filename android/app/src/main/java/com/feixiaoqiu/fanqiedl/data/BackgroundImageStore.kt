package com.feixiaoqiu.fanqiedl.data

import android.content.Context
import android.net.Uri
import java.io.File

/** 将用户选择的图片复制到应用私有目录，供自定义背景使用 */
class BackgroundImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir: File = File(appContext.filesDir, "backgrounds").also { it.mkdirs() }

    fun localFile(): File = File(dir, LOCAL_NAME)

    fun hasLocal(): Boolean = localFile().isFile && localFile().length() > 0L

    fun localPathOrEmpty(): String {
        val f = localFile()
        return if (f.isFile && f.length() > 0L) f.absolutePath else ""
    }

    fun displayModel(path: String): Any? {
        val p = path.trim()
        if (p.isEmpty()) return null
        if (p.startsWith("http://") || p.startsWith("https://") || p.startsWith("file://") || p.startsWith("content://")) {
            return p
        }
        val f = File(p)
        return if (f.isFile) f else null
    }

    fun saveFromUri(uri: Uri): String {
        val dest = localFile()
        val tmp = File(dir, "tmp_${System.currentTimeMillis()}.img")
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选图片" }
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        require(dest.isFile && dest.length() > 0L) { "保存背景失败" }
        return dest.absolutePath
    }

    fun clear() {
        val f = localFile()
        if (f.exists()) f.delete()
    }

    companion object {
        const val LOCAL_NAME = "custom_bg.jpg"
    }
}
