package com.feixiaoqiu.fanqiedl.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "fanqie_settings")

class AppSettings(private val context: Context) {
    private val keyNodes = stringPreferencesKey("nodes_json")
    private val keyHitokoto = stringPreferencesKey("hitokoto_url")
    private val keyLastGood = stringPreferencesKey("last_good_base")
    private val keyBgMode = stringPreferencesKey("bg_mode")
    private val keyBgApi = stringPreferencesKey("bg_api_url")
    private val keyBgImage = stringPreferencesKey("bg_image_url")

    val nodesFlow: Flow<List<NodeConfig>> = context.dataStore.data.map { prefs ->
        parseNodes(prefs[keyNodes])
    }

    val hitokotoUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyHitokoto] ?: DefaultNodes.DEFAULT_HITOKOTO
    }

    val lastGoodBaseFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyLastGood]
    }

    val backgroundModeFlow: Flow<BackgroundMode> = context.dataStore.data.map { prefs ->
        BackgroundMode.fromStorage(prefs[keyBgMode])
    }

    val backgroundApiUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyBgApi].orEmpty()
    }

    val backgroundImageUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyBgImage].orEmpty()
    }

    suspend fun setNodes(nodes: List<NodeConfig>) {
        context.dataStore.edit { prefs ->
            prefs[keyNodes] = serializeNodes(nodes)
        }
    }

    suspend fun setHitokotoUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[keyHitokoto] = url.trim().ifEmpty { DefaultNodes.DEFAULT_HITOKOTO }
        }
    }

    suspend fun setBackground(
        mode: BackgroundMode,
        apiUrl: String,
        imageUrl: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[keyBgMode] = mode.name
            prefs[keyBgApi] = apiUrl.trim()
            prefs[keyBgImage] = imageUrl.trim()
        }
    }

    suspend fun setLastGoodBase(base: String?) {
        context.dataStore.edit { prefs ->
            if (base.isNullOrBlank()) prefs.remove(keyLastGood)
            else prefs[keyLastGood] = DefaultNodes.normalizeBaseUrl(base)
        }
    }

    suspend fun restoreDefaultNodes() {
        setNodes(DefaultNodes.builtin())
    }

    suspend fun addNode(name: String, baseUrl: String): NodeConfig {
        val node = NodeConfig(
            id = "custom-" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "自定义节点" },
            baseUrl = DefaultNodes.normalizeBaseUrl(baseUrl),
            enabled = true,
            builtin = false,
        )
        setNodes(snapshotNodes() + node)
        return node
    }

    suspend fun updateNode(node: NodeConfig) {
        val cur = snapshotNodes().map {
            if (it.id == node.id) node.copy(baseUrl = DefaultNodes.normalizeBaseUrl(node.baseUrl))
            else it
        }
        setNodes(cur)
    }

    suspend fun removeNode(id: String) {
        setNodes(snapshotNodes().filterNot { it.id == id && !it.builtin })
    }

    private suspend fun snapshotNodes(): List<NodeConfig> = nodesFlow.first()

    companion object {
        fun parseNodes(raw: String?): List<NodeConfig> {
            if (raw.isNullOrBlank()) return DefaultNodes.builtin()
            return try {
                val arr = JSONArray(raw)
                if (arr.length() == 0) return DefaultNodes.builtin()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            NodeConfig(
                                id = o.optString("id", "n$i"),
                                name = o.optString("name", "节点"),
                                baseUrl = DefaultNodes.normalizeBaseUrl(o.optString("baseUrl", "")),
                                enabled = o.optBoolean("enabled", true),
                                builtin = o.optBoolean("builtin", false),
                            )
                        )
                    }
                }.filter { it.baseUrl.isNotBlank() }
            } catch (_: Exception) {
                DefaultNodes.builtin()
            }
        }

        fun serializeNodes(nodes: List<NodeConfig>): String {
            val arr = JSONArray()
            nodes.forEach { n ->
                arr.put(
                    JSONObject()
                        .put("id", n.id)
                        .put("name", n.name)
                        .put("baseUrl", n.baseUrl)
                        .put("enabled", n.enabled)
                        .put("builtin", n.builtin)
                )
            }
            return arr.toString()
        }
    }
}
