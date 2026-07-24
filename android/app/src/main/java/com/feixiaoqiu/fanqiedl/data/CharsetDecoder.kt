package com.feixiaoqiu.fanqiedl.data

import android.content.Context
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicReference

/**
 * Port of browser-client.js decodeBody / charset.json tables.
 */
class CharsetDecoder(context: Context) {
    private val appContext = context.applicationContext
    private val tablesRef = AtomicReference<List<List<String>>?>(null)

    private fun tables(): List<List<String>> {
        tablesRef.get()?.let { return it }
        synchronized(this) {
            tablesRef.get()?.let { return it }
            val text = appContext.assets.open("charset.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONArray(i)
                    add(buildList {
                        for (j in 0 until row.length()) add(row.optString(j, "?"))
                    })
                }
            }
            tablesRef.set(list)
            return list
        }
    }

    fun decodeBody(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val tables = tables()
        if (tables.isEmpty()) return htmlToText(raw)
        val mode = pickMode(raw, tables)
        val table = tables.getOrElse(mode) { tables[0] }
        return htmlToText(decodeContent(raw, mode, table))
    }

    private fun decodeContent(content: String, mode: Int, table: List<String>): String {
        val (lo, hi) = CODE_RANGES.getOrElse(mode) { CODE_RANGES[0] }
        val sb = StringBuilder(content.length)
        var i = 0
        while (i < content.length) {
            val cp = content.codePointAt(i)
            if (cp in lo..hi) {
                val bias = cp - lo
                if (bias in table.indices && table[bias] != "?") sb.append(table[bias])
                else sb.appendCodePoint(cp)
            } else {
                sb.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun pickMode(raw: String, tables: List<List<String>>): Int {
        fun score(s: String): Int {
            var cjk = 0
            var priv = 0
            var i = 0
            while (i < s.length) {
                val code = s.codePointAt(i)
                if (code in 0x4e00..0x9fff) cjk++
                if (code in 0xe000..0xf8ff || code in 58344..58716) priv++
                i += Character.charCount(code)
            }
            return cjk * 2 - priv * 5
        }
        val d0 = decodeContent(raw, 0, tables[0])
        val d1 = decodeContent(raw, 1, tables.getOrElse(1) { tables[0] })
        return if (score(d0) >= score(d1)) 0 else 1
    }

    private fun htmlToText(html: String): String {
        var s = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        s = s
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
            }
            .replace(Regex("&#x([0-9a-f]+);", RegexOption.IGNORE_CASE)) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
            }
        return s.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    companion object {
        private val CODE_RANGES = listOf(58344 to 58715, 58345 to 58716)
    }
}
