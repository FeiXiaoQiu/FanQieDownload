package ink.yan.reader.data

/**
 * 预置数据源节点。
 *
 * 首次启动、节点被删空、或存储内容损坏时，都会回落到这份清单 ——
 * 否则用户打开应用面对一个空列表，还得自己去找地址才能开始用。
 *
 * 这些是第三方公益接口，可用性不归本项目管。实测时有 5 个可达、
 * 1 个超时，所以列表里保留全部 6 条：测速会把不可达的沉底，
 * 少留一条就少一次重试机会。
 */
object NodePresets {

    /** 每次返回新列表，避免调用方拿到共享引用后互相污染。 */
    fun builtin(): List<NodeConfig> = listOf(
        NodeConfig("builtin-1", "节点1", "http://110.42.57.146:4018", builtin = true),
        NodeConfig("builtin-2", "节点2", "http://81.70.223.143:6897", builtin = true),
        NodeConfig("builtin-3", "节点3", "http://110.42.63.158:5888", builtin = true),
        NodeConfig("builtin-4", "节点4", "http://59.110.160.171:5007", builtin = true),
        NodeConfig("builtin-5", "节点5", "http://43.143.149.30:8008", builtin = true),
        NodeConfig("builtin-6", "节点6", "http://45.116.77.104:8008", builtin = true),
    )
}
