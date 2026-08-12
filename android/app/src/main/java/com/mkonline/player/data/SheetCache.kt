package com.mkonline.player.data

import android.content.Context
import org.json.JSONObject

/**
 * 歌单封面+名字缓存（含磁盘持久化）。
 * - 内存：进程级 LRU（上限 50 个歌单）
 * - 磁盘：SharedPreferences 持久化，进程重启后无需重新调 types=playlist 获取元数据
 * - 由 AppContainer 初始化（init），之后 SheetsScreen/SheetDetailScreen 使用
 */
object SheetCache {

    private const val MAX_SIZE = 50
    private const val PREFS_NAME = "sheet_cache"
    private const val KEY_MAP = "map"

    private var sp: android.content.SharedPreferences? = null

    /** 缓存的歌单封面元数据（歌曲列表太大，不持久化）。 */
    data class SheetMetaLite(val name: String, val cover: String)

    // LinkedHashMap 默认按插入序排列，容量超限时从头部开始删（即最早加入的）
    private val mem: MutableMap<String, SheetMetaLite> =
        java.util.Collections.synchronizedMap(LinkedHashMap())

    /** 由 AppContainer 创建时调用一次，加载磁盘缓存。 */
    fun init(context: Context) {
        if (sp != null) return
        sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp?.getString(KEY_MAP, null)?.let { raw ->
            runCatching {
                val obj = JSONObject(raw)
                for (key in obj.keys()) {
                    obj.optJSONObject(key)?.let { o ->
                        val name = o.optString("name")
                        val cover = o.optString("cover")
                        if (name.isNotEmpty()) mem[key] = SheetMetaLite(name, cover)
                    }
                }
            }
        }
    }

    fun get(id: String): SheetMetaLite? = mem[id]

    fun put(id: String, name: String, cover: String) {
        if (mem.size >= MAX_SIZE) {
            // LinkedHashMap 按插入序，删掉最老的一个
            mem.keys.firstOrNull()?.let { mem.remove(it) }
        }
        mem[id] = SheetMetaLite(name, cover)
        persist()
    }

    private fun persist() {
        val obj = JSONObject()
        // 加锁防多线程 put 时 ConcurrentModificationException
        synchronized(mem) {
            mem.forEach { (k, v) ->
                obj.put(k, JSONObject().put("name", v.name).put("cover", v.cover))
            }
        }
        sp?.edit()?.putString(KEY_MAP, obj.toString())?.apply()
    }

    fun clear() {
        mem.clear()
        sp?.edit()?.remove(KEY_MAP)?.apply()
    }
}
