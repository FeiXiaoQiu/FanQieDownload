package ink.yan.reader.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 节点列表的序列化编解码。
 *
 * 为什么不用 JSON / kotlinx.serialization：
 * 这段逻辑要能在纯 JVM 上编译测试（不引插件、不拉依赖），而节点只有 5 个标量字段，
 * 一行一个记录、字段用单元分隔符 US(0x1F) 切开就够了。字段值全部过一遍
 * URL 编解码，所以名称里带换行、制表符、emoji、中文都不会把格式撑坏。
 *
 * 格式：
 *   <id>US<name>US<baseUrl>US<enabled>US<builtin>\n
 *   每行一条，空行与字段数不对的行静默跳过（旧版本兼容 / 手工编辑容错）
 */
object NodeCodec {

    private const val SEP = "\u001F"
    private const val CHARSET = "UTF-8"

    fun encode(list: List<NodeConfig>): String = buildString {
        for (n in list) {
            append(enc(n.id)).append(SEP)
                .append(enc(n.name)).append(SEP)
                .append(enc(n.baseUrl)).append(SEP)
                .append(n.enabled).append(SEP)
                .append(n.builtin)
            append('\n')
        }
    }

    fun decode(raw: String): List<NodeConfig> {
        if (raw.isBlank()) return emptyList()
        val out = ArrayList<NodeConfig>(8)
        for (line in raw.lineSequence()) {
            if (line.isBlank()) continue
            val p = line.split(SEP)
            if (p.size != 5) continue
            val id = dec(p[0])
            val baseUrl = dec(p[2])
            // id / baseUrl 是 NodeConfig 的硬性约束，缺失的行直接丢弃
            if (id.isBlank() || baseUrl.isBlank()) continue
            val name = dec(p[1]).ifBlank { baseUrl }
            val node = try {
                NodeConfig(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    enabled = p[3].toBooleanStrict(),
                    builtin = p[4].toBooleanStrict(),
                )
            } catch (e: IllegalArgumentException) {
                continue
            }
            out += node
        }
        return out
    }

    private fun enc(s: String): String = URLEncoder.encode(s, CHARSET)

    private fun dec(s: String): String = URLDecoder.decode(s, CHARSET)
}
