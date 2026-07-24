package com.feixiaoqiu.fanqiedl.data

import android.content.Context
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = AppSettings(appContext)
    val decoder = CharsetDecoder(appContext)
    val hitokoto = HitokotoClient()

    val client = FanqieNodeClient(
        settings = settings,
        decoder = decoder,
        basesProvider = {
            val nodes = settings.nodesFlow.first()
            val enabled = nodes.filter { it.enabled }.map { DefaultNodes.normalizeBaseUrl(it.baseUrl) }
            if (enabled.isEmpty()) return@FanqieNodeClient emptyList()
            val last = settings.lastGoodBaseFlow.first()
            if (last != null && last in enabled) {
                listOf(last) + enabled.filter { it != last }
            } else {
                enabled
            }
        },
    )

    val downloads = DownloadRepository(appContext, client)
}
