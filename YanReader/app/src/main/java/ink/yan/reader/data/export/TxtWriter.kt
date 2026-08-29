package ink.yan.reader.data.export

import ink.yan.reader.data.BookInfo
import ink.yan.reader.data.ChapterContent
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * TXT 导出。
 *
 * 同样采用流式写出：边遍历章节边写，不把整本书拼成一个 String。
 * 一部 3000 章的小说正文动辄十几 MB，一次性 join 在低端机上很容易 OOM。
 */
object TxtWriter {

    fun write(
        out: OutputStream,
        meta: BookInfo,
        chapters: List<ChapterContent>,
        separator: String = "-".repeat(30),
    ) {
        val w = OutputStreamWriter(out, Charsets.UTF_8)
        try {
            w.write(buildHeader(meta, chapters.size))
            chapters.forEach { ch ->
                w.write(ch.title)
                w.write("\n\n")
                w.write(ch.text)
                w.write("\n\n")
                w.write(separator)
                w.write("\n\n")
            }
            w.flush()
        } finally {
            // 只 flush 不 close：OutputStream 的生命周期由调用方（Uri / FileOutputStream）掌管
        }
    }

    private fun buildHeader(meta: BookInfo, count: Int): String = buildString {
        appendLine(meta.title)
        appendLine("作者：${meta.author}")
        appendLine("书籍ID：${meta.id}")
        appendLine("章节数：$count")
        appendLine()
        if (meta.abstract.isNotBlank()) {
            appendLine("简介：")
            appendLine(meta.abstract.take(500))
            appendLine()
        }
        appendLine("=".repeat(40))
        appendLine()
    }
}
