package ink.yan.reader.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ink.yan.reader.data.NodeCodec
import ink.yan.reader.data.NodeConfig
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

    val nodes: Flow<List<NodeConfig>> = context.nodeDataStore.data
        .map { prefs -> NodeCodec.decode(prefs[Keys.NODES].orEmpty()) }

    suspend fun save(list: List<NodeConfig>) {
        context.nodeDataStore.edit { prefs ->
            prefs[Keys.NODES] = NodeCodec.encode(list)
        }
    }
}
