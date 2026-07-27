package com.feixiaoqiu.lookapp

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.feixiaoqiu.lookapp.data.Resolver
import com.feixiaoqiu.lookapp.ui.InspectorScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private val resolver = Resolver()
    val saveDir by lazy {
        File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Look").also { dir ->
            if (!dir.exists()) dir.mkdirs()
            File(dir, ".nomedia").createNewFile()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InspectorScreen(
                resolver = resolver,
                onSaveBytes = { filename, bytes ->
                    // 写入文件
                    saveDir.mkdirs()
                    File(saveDir, ".nomedia").createNewFile()
                    val file = File(saveDir, filename)
                    file.writeBytes(bytes)
                    // 同时注册 MediaStore 供文件管理器看到（.nomedia 阻止媒体扫描）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, filename)
                            put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Look")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        uri?.let {
                            contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                            values.clear()
                            values.put(MediaStore.Downloads.IS_PENDING, 0)
                            contentResolver.update(it, values, null, null)
                        }
                    }
                },
            )
        }
    }
}
