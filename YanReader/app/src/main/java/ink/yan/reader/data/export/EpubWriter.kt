package ink.yan.reader.data.export

import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 极简 EPUB 3.0 写出器。
 *
 * 不引入任何第三方依赖 —— java.util.zip 是 JDK 自带的。
 * 采用流式写出：章节逐个写入 OutputStream，不把整本书攒在内存里，
 * 因此可以直接对接 MediaStore 的 openOutputStream()。
 *
 * 产出结构：
 *   mimetype                 (必须第一个，且 STORED 不压缩)
 *   META-INF/container.xml
 *   OEBPS/content.opf
 *   OEBPS/nav.xhtml
 *   OEBPS/chap_1.xhtml ... chap_N.xhtml
 */
object EpubWriter {

    private const val MIMETYPE = "application/epub+zip"

    private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    /**
     * @param chapters 章节列表，Pair(章节标题, 正文)。正文以 \n 分段。
     */
    fun write(
        out: OutputStream,
        title: String,
        author: String,
        bookId: String,
        chapters: List<Pair<String, String>>,
    ) {
        val zos = ZipOutputStream(out.buffered())
        try {
            writeMimetype(zos)

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(CONTAINER_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val entries = mutableListOf<String>()
            chapters.forEachIndexed { i, (chTitle, text) ->
                val name = "OEBPS/chap_${i + 1}.xhtml"
                zos.putNextEntry(ZipEntry(name))
                zos.write(chapterXhtml(chTitle, text).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                entries.add(name)
            }

            zos.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zos.write(
                navXhtml(title, chapters.map { it.first }).toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                contentOpf(title, author, bookId, chapters.map { it.first }, entries)
                    .toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        } finally {
            zos.close()
        }
    }

    /**
     * EPUB 规范硬要求：
     *   1. mimetype 必须是 ZIP 的第一个条目
     *   2. 必须 STORED（不压缩），内容恰好是 "application/epub+zip"
     *   3. STORED 模式下 Java 强制要求手工填 size / compressedSize / crc
     *      否则抛 ZipException: STORED entry missing size, compressed size, or crc-32
     * 少任何一条，多数阅读器（尤其 Apple Books、静读天下）会拒绝打开。
     */
    private fun writeMimetype(zos: ZipOutputStream) {
        val data = MIMETYPE.toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            val crc = CRC32().also { it.update(data) }
            setCrc(crc.value)
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun chapterXhtml(title: String, text: String): String {
        // 纯文本按空行切段，逐段包 <p>。XHTML 要求标签严格闭合，不能用 <br> 裸标签。
        val body = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "    <p>${it.escapeXml()}</p>" }
            .ifBlank { "    <p>（本章无内容）</p>" }

        return """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN">
<head>
  <meta charset="UTF-8"/>
  <title>${title.escapeXml()}</title>
</head>
<body>
  <h2>${title.escapeXml()}</h2>
$body
</body>
</html>"""
    }

    private fun navXhtml(title: String, chapterTitles: List<String>): String {
        val items = chapterTitles.mapIndexed { i, t ->
            """      <li><a href="chap_${i + 1}.xhtml">${t.escapeXml()}</a></li>"""
        }.joinToString("\n")

        return """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="UTF-8"/><title>${title.escapeXml()}</title></head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>目录</h1>
    <ol>
$items
    </ol>
  </nav>
</body>
</html>"""
    }

    private fun contentOpf(
        title: String,
        author: String,
        bookId: String,
        chapterTitles: List<String>,
        entries: List<String>,
    ): String {
        val manifest = buildString {
            appendLine("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            entries.forEachIndexed { i, name ->
                val href = name.removePrefix("OEBPS/")
                appendLine("""    <item id="c${i + 1}" href="$href" media-type="application/xhtml+xml"/>""")
            }
        }
        // spine 决定阅读顺序，这里是自然顺序
        val spine = chapterTitles.indices.joinToString("\n") {
            """    <itemref idref="c${it + 1}"/>"""
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">urn:yanreader:$bookId</dc:identifier>
    <dc:title>${title.escapeXml()}</dc:title>
    <dc:creator>${author.escapeXml()}</dc:creator>
    <dc:language>zh-CN</dc:language>
    <meta property="dcterms:modified">${isoNow()}</meta>
  </metadata>
  <manifest>
$manifest  </manifest>
  <spine>
$spine
  </spine>
</package>"""
    }

    private fun isoNow(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    /** XHTML 只允许 5 个预定义实体，其余必须转义成数字实体。 */
    private fun String.escapeXml(): String = buildString(length) {
        for (ch in this@escapeXml) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(ch)
        }
    }
}
