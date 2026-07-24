package com.feixiaoqiu.fanqiedl.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.feixiaoqiu.fanqiedl.BuildConfig
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = AppSettings(appContext)
    val decoder = CharsetDecoder(appContext)
    val hitokoto = HitokotoClient()
    val updateChecker = UpdateChecker()

    val appVersionName: String = run {
        val fromPm = try {
            val pm = appContext.packageManager
            val pkg = appContext.packageName
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionName
            }
        } catch (_: Exception) {
            null
        }
        (fromPm ?: BuildConfig.VERSION_NAME).orEmpty()
    }

    val appVersionCode: Int = run {
        try {
            val pm = appContext.packageManager
            val pkg = appContext.packageName
            if (Build.VERSION.SDK_INT >= 28) {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).longVersionCode.toInt()
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionCode
            }
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    val client = FanqieNodeClient(
        settings = settings,
        decoder = decoder,
        basesProvider = {
            // 列表里有的节点一律启用；不想用只能删
            val all = settings.nodesFlow.first()
                .map { DefaultNodes.normalizeBaseUrl(it.baseUrl) }
                .filter { it.isNotBlank() }
                .distinct()
            if (all.isEmpty()) return@FanqieNodeClient emptyList()
            val last = settings.lastGoodBaseFlow.first()
            if (last != null && last in all) {
                listOf(last) + all.filter { it != last }
            } else {
                all
            }
        },
    )

    val downloads = DownloadRepository(appContext, client)
}
