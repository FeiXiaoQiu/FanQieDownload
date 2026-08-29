package ink.yan.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * 节点测速。
 *
 * 刻意使用 java.net.HttpURLConnection 而非 OkHttp —— 测速只需要最轻量的连通性
 * 探测，用 JDK 原生实现可以让这段逻辑脱离 Android 独立编译与测试。
 */
object NodeTester {

    private const val PROBE_PATH = "/search?query=probe"

    suspend fun test(node: NodeConfig, timeoutMs: Int = 5000): NodeLatency =
        withContext(Dispatchers.IO) {
            if (node.baseUrl.isBlank()) {
                return@withContext NodeLatency(node.id, false, error = "地址为空")
            }
            val r = withTimeoutOrNull(timeoutMs.toLong() + 500L) {
                probe(node.baseUrl, timeoutMs)
            }
            // probe 不关心身份，这里统一回填 nodeId
            r?.copy(nodeId = node.id)
                ?: NodeLatency(node.id, false, error = "超时")
        }

    /** 并发探测全部节点，返回顺序与入参一致。 */
    suspend fun testAll(
        nodes: List<NodeConfig>,
        timeoutMs: Int = 5000,
    ): List<NodeLatency> = coroutineScope {
        nodes.map { node ->
            async(Dispatchers.IO) { test(node, timeoutMs) }
        }.map { it.await() }
    }

    /** 按延迟升序排序，不可达的沉底。用于 UI 展示与自动择优。 */
    fun rank(results: List<NodeLatency>): List<NodeLatency> =
        results.sortedWith(
            compareBy(
                { if (it.ok) 0 else 1 },
                { it.millis ?: Long.MAX_VALUE },
            )
        )

    /** 挑一个可用节点；全挂则返回 null。 */
    fun pickBest(results: List<NodeLatency>): String? =
        rank(results).firstOrNull { it.ok }?.nodeId

    private fun probe(baseUrl: String, timeoutMs: Int): NodeLatency {
        var conn: HttpURLConnection? = null
        return try {
            // 用 URI 中转以避免 java.net.URL 构造函数在新 JDK 上的弃用警告
            val url = java.net.URI(normalize(baseUrl) + PROBE_PATH).toURL()
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "YanReader/0.1")

            val t0 = System.nanoTime()
            val code = conn.responseCode
            val costMs = (System.nanoTime() - t0) / 1_000_000L

            // 只要服务端有响应就算可达；4xx 表示节点在跑但探测参数不被接受
            if (code in 200..499) {
                NodeLatency("", true, costMs)
            } else {
                NodeLatency("", false, costMs, "HTTP $code")
            }
        } catch (e: Exception) {
            NodeLatency("", false, error = e.javaClass.simpleName + ": " + shortMsg(e))
        } finally {
            conn?.disconnect()
        }
    }

    fun normalize(raw: String): String {
        var u = raw.trim()
        while (u.endsWith("/")) u = u.dropLast(1)
        return u
    }

    fun isValidHttpUrl(raw: String): Boolean {
        val u = normalize(raw)
        return u.startsWith("http://") || u.startsWith("https://")
    }

    private fun shortMsg(e: Exception): String =
        (e.message ?: "无详情").take(60)
}

/**
 * 节点仓库：负责增删改查与持久化顺序。
 * 持久化交给 DataStore（Android 侧），这里只维护内存态与纯逻辑。
 */
class NodeRepository(
    initial: List<NodeConfig> = emptyList(),
) {
    private val _nodes = initial.toMutableList()

    val nodes: List<NodeConfig> get() = _nodes.toList()

    fun enabledNodes(): List<NodeConfig> = _nodes.filter { it.enabled }

    fun add(node: NodeConfig): Boolean {
        if (_nodes.any { it.baseUrl == node.baseUrl }) return false
        _nodes.add(node)
        return true
    }

    fun update(node: NodeConfig) {
        val i = _nodes.indexOfFirst { it.id == node.id }
        if (i >= 0) _nodes[i] = node
    }

    /** 删除节点。返回 false 表示节点不存在或是内置节点（拒绝删除）。 */
    fun remove(id: String): Boolean {
        val i = _nodes.indexOfFirst { it.id == id }
        if (i < 0 || _nodes[i].builtin) return false
        _nodes.removeAt(i)
        return true
    }

    fun find(id: String): NodeConfig? = _nodes.firstOrNull { it.id == id }

    /** 移动排序，用于「长按拖动」换位。 */
    fun move(from: Int, to: Int) {
        if (from !in _nodes.indices || to !in _nodes.indices) return
        val item = _nodes.removeAt(from)
        _nodes.add(to, item)
    }

    fun replaceAll(list: List<NodeConfig>) {
        _nodes.clear()
        _nodes.addAll(list)
    }
}
