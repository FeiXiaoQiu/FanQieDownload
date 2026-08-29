package ink.yan.reader.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ink.yan.reader.data.DownloadPresets
import ink.yan.reader.data.DownloadSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

/**
 * 应用更新设置：下载源列表。
 *
 * 预置源只能停用不能删除（删了没法恢复），自定义源可自由增删。
 * 序列化用 org.json，理由同 AppearanceStore —— 本类依赖 Context，不在单测范围。
 */
data class UpdatePrefs(
    val customSources: List<DownloadSource> = emptyList(),
    /** 被停用的预置源 id */
    val disabledIds: Set<String> = emptySet(),
) {
    /** 参与下载尝试的源：预置里没被停用的 + 全部自定义 */
    val activeSources: List<DownloadSource>
        get() = DownloadPresets.all.filter { it.id !in disabledIds } + customSources

    fun isEnabled(src: DownloadSource): Boolean = src.id !in disabledIds
}

class UpdateStore(private val context: Context) {

    private object Keys {
        val CUSTOMS = stringPreferencesKey("custom_sources")
        val DISABLED = stringPreferencesKey("disabled_ids")
    }

    val prefs: Flow<UpdatePrefs> = context.updateDataStore.data.map { decode(it) }

    suspend fun setBuiltinEnabled(id: String, enabled: Boolean) {
        context.updateDataStore.edit { p ->
            val set = decodeIds(p[Keys.DISABLED]).toMutableSet()
            if (enabled) set.remove(id) else set.add(id)
            p[Keys.DISABLED] = set.joinToString(",")
        }
    }

    suspend fun addCustom(name: String, template: String) {
        context.updateDataStore.edit { p ->
            val list = decodeCustoms(p[Keys.CUSTOMS]).toMutableList()
            list += DownloadSource(
                id = freshId(list),
                name = name.ifBlank { "自定义镜像" },
                urlTemplate = template,
            )
            p[Keys.CUSTOMS] = encodeCustoms(list)
        }
    }

    suspend fun removeCustom(id: String) {
        context.updateDataStore.edit { p ->
            val list = decodeCustoms(p[Keys.CUSTOMS]).filterNot { it.id == id }
            p[Keys.CUSTOMS] = encodeCustoms(list)
        }
    }

    suspend fun save(p: UpdatePrefs) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.CUSTOMS] = encodeCustoms(p.customSources)
            prefs[Keys.DISABLED] = p.disabledIds.joinToString(",")
        }
    }

    // —— 编解码 ——

    private fun decode(p: Preferences): UpdatePrefs = UpdatePrefs(
        customSources = decodeCustoms(p[Keys.CUSTOMS]),
        disabledIds = decodeIds(p[Keys.DISABLED]),
    )

    private fun decodeCustoms(raw: String?): List<DownloadSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val out = mutableListOf<DownloadSource>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val tpl = o.optString("template")
                // 没有占位符的模板解析不出地址，直接丢弃，不让脏数据混进列表
                if (!tpl.contains("{url}")) continue
                out += DownloadSource(
                    id = o.optString("id").ifBlank { "cds-$i" },
                    name = o.optString("name").ifBlank { "自定义镜像" },
                    urlTemplate = tpl,
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeCustoms(list: List<DownloadSource>): String =
        org.json.JSONArray().apply {
            list.forEach { src ->
                put(
                    org.json.JSONObject()
                        .put("id", src.id)
                        .put("name", src.name)
                        .put("template", src.urlTemplate)
                )
            }
        }.toString()

    private fun decodeIds(raw: String?): Set<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun freshId(existing: List<DownloadSource>): String {
        var id = "cds-${System.currentTimeMillis()}"
        while (existing.any { it.id == id }) id += "x"
        return id
    }
}
