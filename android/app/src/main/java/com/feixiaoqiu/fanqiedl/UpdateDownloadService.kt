package com.feixiaoqiu.fanqiedl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "update.apk"
        createNotificationChannel()
        startForeground(NOTIF_DOWNLOAD_ID, buildProgressNotification(0f, false, "准备下载…"))
        UpdateDownloadState.start()

        downloadJob = scope.launch {
            try {
                val cacheDir = File(cacheDir, "update")
                cacheDir.mkdirs()
                val outFile = File(cacheDir, fileName)
                if (outFile.exists()) outFile.delete()

                val client = OkHttpClient.Builder()
                    .callTimeout(5, TimeUnit.MINUTES)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    updateError("HTTP ${resp.code}")
                    return@launch
                }
                val body = resp.body
                if (body == null) {
                    resp.close()
                    updateError("空响应")
                    return@launch
                }
                val total = body.contentLength()

                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var bytesRead = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            bytesRead += read
                            val pct = if (total > 0) (bytesRead.toFloat() / total).coerceIn(0f, 1f) else 0f
                            val msg = if (total > 0) {
                                "下载中 ${(pct * 100).toInt()}%"
                            } else {
                                "下载中 ${bytesRead / 1024}KB"
                            }
                            updateProgress(pct, msg)
                        }
                    }
                }
                resp.close()

                val uri = FileProvider.getUriForFile(
                    this@UpdateDownloadService,
                    "${packageName}.fileprovider",
                    outFile,
                )
                showCompleted(uri)
                UpdateDownloadState.complete()
            } catch (e: Exception) {
                updateError(e.message ?: "未知错误")
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        UpdateDownloadState.reset()
        super.onDestroy()
    }

    private fun updateProgress(pct: Float, message: String) {
        UpdateDownloadState.progress(pct, message)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_DOWNLOAD_ID, buildProgressNotification(pct, false, message))
    }

    private fun updateError(msg: String) {
        UpdateDownloadState.error(msg)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_DOWNLOAD_ID, buildProgressNotification(0f, true, "下载失败：$msg"))
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun showCompleted(uri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(installIntent)

        val openPi = PendingIntent.getActivity(
            this, 0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("下载完成")
            .setContentText("点击安装新版本")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_COMPLETE_ID, notif)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun buildProgressNotification(
        progress: Float,
        indeterminate: Boolean,
        text: String,
    ): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("观隅 更新下载")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "更新下载",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "更新下载进度通知"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "update_download"
        const val NOTIF_DOWNLOAD_ID = 1001
        const val NOTIF_COMPLETE_ID = 1002
        const val EXTRA_URL = "download_url"
        const val EXTRA_FILE_NAME = "file_name"

        fun start(context: Context, url: String, fileName: String) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
