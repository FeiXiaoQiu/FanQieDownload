package ink.yan.reader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class YanApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel(
            CHANNEL_DOWNLOAD,
            "下载进度",
            NotificationManager.IMPORTANCE_LOW,   // 低打扰：进度类通知不该响铃
        ).apply {
            description = "显示书籍下载进度"
            setSound(null, null)
            mgr.createNotificationChannel(this)
        }

        NotificationChannel(
            CHANNEL_DONE,
            "下载完成",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "下载完成或失败时提醒"
            mgr.createNotificationChannel(this)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOAD = "yan_download"
        const val CHANNEL_DONE = "yan_done"
    }
}
