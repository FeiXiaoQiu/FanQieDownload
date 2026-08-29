package ink.yan.reader.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ink.yan.reader.data.NodeCodec
import ink.yan.reader.data.NodeConfig
import ink.yan.reader.data.NodePresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nodeDataStore: DataStore<Preferences> by preferencesDataStore(name = "nodes")

/**
 * 节点落盘。
 *
 * 只用一行字符串存全部节点，不上 Room —— 节点数量是个位数到几十，
 * 为一个列表引一整套 ORM 不划算。写入走 DataStore，天然 main-safe 且原子。
 */
class NodeStore(private val context: Context) {

    private object Keys {
        val NODES = stringPreferencesKey("nodes")
    }

    /**
     * 空存储、删空、或内容损坏，一律回落到预置节点。
     *
     * 注意这个语义的副作用：用户手动删光所有节点后，下次进来又会看到默认的六条。
     * 这是有意的 —— 空列表没有任何用处，而「恢复默认」本来就是设置里最需要的操作。
     */
    val nodes: Flow<List<NodeConfig>> = context.nodeDataStore.data
        .map { prefs ->
            NodeCodec.decode(prefs[Keys.NODES].orEmpty())
                .takeIf { it.isNotEmpty() }
                ?: NodePresets.builtin()
        }

    suspend fun save(list: List<NodeConfig>) {
        context.nodeDataStore.edit { prefs ->
            prefs[Keys.NODES] = NodeCodec.encode(list)
        }
    }
}
