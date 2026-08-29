package ink.yan.reader.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ink.yan.reader.data.Appearance
import ink.yan.reader.data.BackgroundKind
import ink.yan.reader.data.BackgroundPresets
import ink.yan.reader.data.BackgroundScale
import ink.yan.reader.data.BackgroundSource
import ink.yan.reader.data.CornerStyle
import ink.yan.reader.data.ExportFormat
import ink.yan.reader.data.GlassStrength
import ink.yan.reader.data.HitokotoPresets
import ink.yan.reader.data.HitokotoSource
import ink.yan.reader.data.StylePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance")

/** 背景相关偏好。 */
data class BackgroundPrefs(
    /** 当前选中的源 id；空串表示用本地图片 */
    val sourceId: String = BackgroundPresets.ALCY.id,
    val customSources: List<BackgroundSource> = emptyList(),
    /** 本地图片绝对路径，非空时优先于网络源 */
    val localPath: String = "",
    val scale: BackgroundScale = BackgroundScale.FIT,
    val blurDp: Float = 12f,
    /** 压暗遮罩浓度，保证玻璃上的文字可读 */
    val scrimAlpha: Float = 0.42f,
    val showMature: Boolean = false,
) {
    val sources: List<BackgroundSource>
        get() = BackgroundPresets.visible(showMature) + customSources
}

/** 一言相关偏好。 */
data class HitokotoPrefs(
    val sourceId: String = HitokotoPresets.MIXED.id,
    val customSources: List<HitokotoSource> = emptyList(),
    val enabled: Boolean = true,
) {
    val sources: List<HitokotoSource>
        get() = HitokotoPresets.all + customSources
}

/**
 * 外观 / 背景 / 一言的落盘。
 *
 * 这里用 org.json 序列化而不像 NodeStore 那样手写分隔符：
 * 自定义源是「列表套对象」的嵌套结构，手写编码的容错分支会成倍膨胀，
 * 而本类依赖 android.content.Context，本来就不在单元测试范围内。
 *
 * 所有解析都包了 try-catch 并返回默认值 —— 设置项损坏最多是回到默认外观，
 * 不该让整个应用打不开。
 */
class AppearanceStore(private val context: Context) {

    private object Keys {
        val LOOK = stringPreferencesKey("look")
        val BG = stringPreferencesKey("bg")
        val HITOKOTO = stringPreferencesKey("hitokoto")
        val FORMAT = stringPreferencesKey("format")
    }

    /** 导出格式。原本只存在内存里，重启就丢，这里一并收进设置。 */
    val format: Flow<ExportFormat> = context.appearanceDataStore.data
        .map { ExportFormat.fromStorageOrNull(it[Keys.FORMAT]) ?: ExportFormat.EPUB }

    suspend fun saveFormat(f: ExportFormat) {
        context.appearanceDataStore.edit { it[Keys.FORMAT] = f.name }
    }

    val appearance: Flow<Appearance> = context.appearanceDataStore.data
        .map { decodeLook(it[Keys.LOOK]) }

    val background: Flow<BackgroundPrefs> = context.appearanceDataStore.data
        .map { decodeBg(it[Keys.BG]) }

    val hitokoto: Flow<HitokotoPrefs> = context.appearanceDataStore.data
        .map { decodeHitokoto(it[Keys.HITOKOTO]) }

    suspend fun saveLook(a: Appearance) {
        context.appearanceDataStore.edit { it[Keys.LOOK] = encodeLook(a) }
    }

    suspend fun saveBackground(p: BackgroundPrefs) {
        context.appearanceDataStore.edit { it[Keys.BG] = encodeBg(p) }
    }

    suspend fun saveHitokoto(p: HitokotoPrefs) {
        context.appearanceDataStore.edit { it[Keys.HITOKOTO] = encodeHitokoto(p) }
    }

    // —— 编解码 ——

    private fun encodeLook(a: Appearance): String = org.json.JSONObject().apply {
        put("preset", a.preset.name)
        a.strength?.let { put("strength", it.name) }
        a.corner?.let { put("corner", it.name) }
        a.fillAlpha?.let { put("fill", it.toDouble()) }
        a.borderAlpha?.let { put("border", it.toDouble()) }
        a.highlightAlpha?.let { put("highlight", it.toDouble()) }
    }.toString()

    private fun decodeLook(raw: String?): Appearance {
        if (raw.isNullOrBlank()) return Appearance()
        return try {
            val o = org.json.JSONObject(raw)
            Appearance(
                preset = StylePreset.fromStorage(o.optString("preset")),
                strength = o.optStringOrNull("strength")?.let { GlassStrength.fromStorage(it) },
                corner = o.optStringOrNull("corner")?.let { CornerStyle.fromStorage(it) },
                fillAlpha = o.optDoubleOrNull("fill")?.toFloat(),
                borderAlpha = o.optDoubleOrNull("border")?.toFloat(),
                highlightAlpha = o.optDoubleOrNull("highlight")?.toFloat(),
            )
        } catch (_: Exception) {
            Appearance()
        }
    }

    private fun encodeBg(p: BackgroundPrefs): String = org.json.JSONObject().apply {
        put("sourceId", p.sourceId)
        put("localPath", p.localPath)
        put("scale", p.scale.name)
        put("blur", p.blurDp.toDouble())
        put("scrim", p.scrimAlpha.toDouble())
        put("mature", p.showMature)
        put("customs", org.json.JSONArray().apply {
            p.customSources.forEach { src ->
                put(
                    org.json.JSONObject()
                        .put("id", src.id)
                        .put("name", src.name)
                        .put("url", src.url)
                        .put("kind", src.kind.name)
                        .put("path", src.jsonPath)
                )
            }
        })
    }.toString()

    private fun decodeBg(raw: String?): BackgroundPrefs {
        if (raw.isNullOrBlank()) return BackgroundPrefs()
        return try {
            val o = org.json.JSONObject(raw)
            val customs = mutableListOf<BackgroundSource>()
            o.optJSONArray("customs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    val url = it.optString("url")
                    if (url.isBlank()) continue
                    customs += BackgroundSource(
                        id = it.optString("id").ifBlank { "cbg-$i" },
                        name = it.optString("name").ifBlank { "自定义接口" },
                        url = url,
                        kind = BackgroundKind.fromStorage(it.optString("kind")),
                        jsonPath = it.optString("path"),
                    )
                }
            }
            BackgroundPrefs(
                sourceId = o.optString("sourceId", BackgroundPresets.ALCY.id),
                customSources = customs,
                localPath = o.optString("localPath"),
                scale = BackgroundScale.fromStorage(o.optString("scale")),
                blurDp = o.optDoubleOrNull("blur")?.toFloat() ?: 12f,
                scrimAlpha = o.optDoubleOrNull("scrim")?.toFloat() ?: 0.42f,
                showMature = o.optBoolean("mature", false),
            )
        } catch (_: Exception) {
            BackgroundPrefs()
        }
    }

    private fun encodeHitokoto(p: HitokotoPrefs): String = org.json.JSONObject().apply {
        put("sourceId", p.sourceId)
        put("enabled", p.enabled)
        put("customs", org.json.JSONArray().apply {
            p.customSources.forEach { src ->
                put(
                    org.json.JSONObject()
                        .put("id", src.id)
                        .put("name", src.name)
                        .put("url", src.url)
                )
            }
        })
    }.toString()

    private fun decodeHitokoto(raw: String?): HitokotoPrefs {
        if (raw.isNullOrBlank()) return HitokotoPrefs()
        return try {
            val o = org.json.JSONObject(raw)
            val customs = mutableListOf<HitokotoSource>()
            o.optJSONArray("customs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    val url = it.optString("url")
                    if (url.isBlank()) continue
                    customs += HitokotoSource(
                        id = it.optString("id").ifBlank { "chk-$i" },
                        name = it.optString("name").ifBlank { "自定义接口" },
                        url = url,
                    )
                }
            }
            HitokotoPrefs(
                sourceId = o.optString("sourceId", HitokotoPresets.MIXED.id),
                customSources = customs,
                enabled = o.optBoolean("enabled", true),
            )
        } catch (_: Exception) {
            HitokotoPrefs()
        }
    }

    /** JSONObject.optString 在字段缺失时返回 ""，需要区分「缺失」和「空串」。 */
    private fun org.json.JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun org.json.JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null
}
