package com.feixiaoqiu.fanqiedl.data

object DefaultNodes {
    val PROBE_ITEM = "7580458932431225368"
    val DEFAULT_HITOKOTO = "https://v1.hitokoto.cn/"
    val DEFAULT_BACKGROUND_API = "https://t.alcy.cc/ycy"
    val R18_BACKGROUND_API = "https://acg.yaohud.cn/dm/r18.php"

    val DOWNLOAD_SOURCES = listOf(
        DownloadSource("direct", "原链接", "{url}", builtin = true),
        DownloadSource("mirror", "镜像源 (gh.xmly.dev)", "https://gh.xmly.dev/{url}", builtin = true),
    )

    fun builtin(): List<NodeConfig> = listOf(
        NodeConfig("builtin-1", "节点1", "http://110.42.57.146:4018", enabled = true, builtin = true),
        NodeConfig("builtin-2", "节点2", "http://81.70.223.143:6897", enabled = true, builtin = true),
        NodeConfig("builtin-3", "节点3", "http://110.42.63.158:5888", enabled = true, builtin = true),
        NodeConfig("builtin-4", "节点4", "http://59.110.160.171:5007", enabled = true, builtin = true),
        NodeConfig("builtin-5", "节点5", "http://43.143.149.30:8008", enabled = true, builtin = true),
        NodeConfig("builtin-6", "节点6", "http://45.116.77.104:8008", enabled = true, builtin = true),
    )

    fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim()
        while (u.endsWith("/")) u = u.dropLast(1)
        return u
    }

    fun isValidHttpUrl(raw: String): Boolean {
        val u = normalizeBaseUrl(raw)
        return u.startsWith("http://") || u.startsWith("https://")
    }
}
