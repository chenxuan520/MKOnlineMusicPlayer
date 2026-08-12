package com.mkonline.player.data

import android.content.Context
import java.util.Collections
import java.util.LinkedHashMap
import org.json.JSONObject

/**
 * 封面 URL 缓存：source:id -> picUrl。
 * - 内存：LRU 缓存（上限 200 条），避免对 types=pic 的重复请求
 * - 磁盘：SharedPreferences 持久化，进程重启后可读，无需重新调 API
 * - 由 AppContainer 初始化（init），之后各列表直接引用
 */
object CoverCache {

    private const val MAX_SIZE = 200
    private const val PREFS_NAME = "cover_cache"
    private const val KEY_MAP = "map"

    private var sp: android.content.SharedPreferences? = null

    private val mem: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > MAX_SIZE
        }
    )

    /** 由 AppContainer 在启动时调用一次，加载磁盘缓存。 */
    fun init(context: Context) {
        if (sp != null) return
        sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 磁盘 → 内存
        sp?.getString(KEY_MAP, null)?.let { raw ->
            runCatching {
                val obj = JSONObject(raw)
                for (key in obj.keys()) {
                    val url = obj.optString(key)
                    if (url.isNotEmpty()) mem[key] = url
                }
            }
        }
    }

    fun get(source: String, id: String): String = mem["$source:$id"] ?: ""

    fun get(song: Song): String =
        if (song.pic.isNotEmpty()) song.pic else get(song.source, song.id)

    fun put(source: String, id: String, url: String) {
        if (url.isEmpty()) return
        val key = "$source:$id"
        mem[key] = url
        // 序列化时加锁，避免跨线程 put/get 时 ConcurrentModificationException
        val obj = JSONObject()
        synchronized(mem) {
            mem.forEach { (k, v) -> obj.put(k, v) }
        }
        sp?.edit()?.putString(KEY_MAP, obj.toString())?.apply()
    }
}
