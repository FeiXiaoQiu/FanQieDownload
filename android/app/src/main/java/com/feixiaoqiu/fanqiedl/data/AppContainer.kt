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
