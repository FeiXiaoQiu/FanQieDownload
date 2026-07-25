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
    private val keyCustomBgs = stringPreferencesKey("custom_bgs_json")
    private val keyR18Accepted = stringPreferencesKey("r18_accepted")
    private val keyDownloadSrc = stringPreferencesKey("download_source_id")
    private val keyCustomDownloadSrcs = stringPreferencesKey("custom_download_sources_json")
    private val keyBgScale = stringPreferencesKey("bg_scale")
    private val keyBgBlur = stringPreferencesKey("bg_blur")

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

    val customBackgroundsFlow: Flow<List<CustomBackground>> = context.dataStore.data.map { prefs ->
        parseCustomBgs(prefs[keyCustomBgs])
    }

    val r18AcceptedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyR18Accepted] == "1"
    }

    val backgroundScaleFlow: Flow<BackgroundScale> = context.dataStore.data.map { prefs ->
        BackgroundScale.fromStorage(prefs[keyBgScale])
    }

    val backgroundBlurFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[keyBgBlur]?.toFloatOrNull() ?: 10f
    }

    suspend fun setR18Accepted() {
        context.dataStore.edit { prefs ->
            prefs[keyR18Accepted] = "1"
        }
    }

    suspend fun setBackgroundScale(scale: BackgroundScale) {
        context.dataStore.edit { prefs ->
            prefs[keyBgScale] = scale.name
        }
    }

    suspend fun setBackgroundBlur(blur: Float) {
        context.dataStore.edit { prefs ->
            prefs[keyBgBlur] = blur.toString()
        }
    }

    suspend fun addCustomBg(name: String, url: String): CustomBackground {
        val bg = CustomBackground(
            id = "cbg-" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "自定义接口" },
            url = url.trim(),
        )
        setCustomBgs(snapshotCustomBgs() + bg)
        return bg
    }

    suspend fun removeCustomBg(id: String) {
        setCustomBgs(snapshotCustomBgs().filter { it.id != id })
    }

    suspend fun updateCustomBg(id: String, name: String, url: String) {
        setCustomBgs(snapshotCustomBgs().map {
            if (it.id == id) it.copy(name = name, url = url.trim()) else it
        })
    }

    suspend fun snapshotCustomBgs(): List<CustomBackground> {
        return parseCustomBgs(context.dataStore.data.first()[keyCustomBgs])
    }

    private suspend fun setCustomBgs(list: List<CustomBackground>) {
        context.dataStore.edit { prefs ->
            prefs[keyCustomBgs] = serializeCustomBgs(list)
        }
    }

    private fun parseCustomBgs(raw: String?): List<CustomBackground> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomBackground(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun serializeCustomBgs(list: List<CustomBackground>): String {
        val arr = JSONArray()
        list.forEach { bg ->
            arr.put(JSONObject().put("id", bg.id).put("name", bg.name).put("url", bg.url))
        }
        return arr.toString()
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
        // 内置也可删；列表为空时业务层会提示恢复默认
        setNodes(snapshotNodes().filterNot { it.id == id })
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

    // ── 下载源 ──

    val downloadSourceIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyDownloadSrc] ?: "mirror"
    }

    val customDownloadSourcesFlow: Flow<List<DownloadSource>> = context.dataStore.data.map { prefs ->
        parseDownloadSources(prefs[keyCustomDownloadSrcs])
    }

    suspend fun selectDownloadSource(id: String) {
        context.dataStore.edit { prefs ->
            prefs[keyDownloadSrc] = id
        }
    }

    suspend fun addCustomDownloadSource(name: String, urlTemplate: String) {
        val newSrc = DownloadSource(
            id = "dl-" + java.util.UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "自定义源" },
            urlTemplate = urlTemplate.trim(),
        )
        setCustomDownloadSources(snapshotCustomDownloadSources() + newSrc)
    }

    suspend fun removeCustomDownloadSource(id: String) {
        setCustomDownloadSources(snapshotCustomDownloadSources().filter { it.id != id })
    }

    private suspend fun snapshotCustomDownloadSources(): List<DownloadSource> {
        return parseDownloadSources(context.dataStore.data.first()[keyCustomDownloadSrcs])
    }

    private suspend fun setCustomDownloadSources(list: List<DownloadSource>) {
        context.dataStore.edit { prefs ->
            prefs[keyCustomDownloadSrcs] = serializeDownloadSources(list)
        }
    }

    private fun parseDownloadSources(raw: String?): List<DownloadSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DownloadSource(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    urlTemplate = o.optString("urlTemplate"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeDownloadSources(list: List<DownloadSource>): String {
        val arr = org.json.JSONArray()
        list.forEach { src ->
            arr.put(
                org.json.JSONObject()
                    .put("id", src.id)
                    .put("name", src.name)
                    .put("urlTemplate", src.urlTemplate)
            )
        }
        return arr.toString()
    }
}
