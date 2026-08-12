package com.mkonline.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** 播放模式，顺序与 Web 端的 rem.playmode 语义保持一致。 */
enum class PlayMode(val next: () -> PlayMode) {
    ORDER({ RANDOM }), RANDOM({ SINGLE }), SINGLE({ ORDER });

    companion object {
        fun fromInt(v: Int): PlayMode = entries.getOrElse(v) { ORDER }
    }
}

/** 主题模式：跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK;
    companion object {
        fun fromInt(v: Int): ThemeMode = entries.getOrElse(v) { SYSTEM }
    }
}

data class Settings(
    val serverUrl: String = "",
    val authenticated: Boolean = false,
    val filterVip: Boolean = true,
    val hotCommentsOnly: Boolean = false,
    val recommendDomain: String = "",
    val recommendToken: String = "",
    val recommendFavCount: Int = 20,
    val playMode: PlayMode = PlayMode.ORDER,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/**
 * 本地持久化，对齐 Web 端 playerSavedata/playerReaddata，
 * 后台地址/认证状态/设置/队列/历史都放这里。
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("mkplayer", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings

    private fun load(): Settings = Settings(
        serverUrl = sp.getString("serverUrl", "") ?: "",
        authenticated = sp.getBoolean("authenticated", false),
        filterVip = sp.getBoolean("filterVip", true),
        hotCommentsOnly = sp.getBoolean("hotCommentsOnly", false),
        recommendDomain = sp.getString("recommendDomain", "") ?: "",
        recommendToken = sp.getString("recommendToken", "") ?: "",
        recommendFavCount = sp.getInt("recommendFavCount", 20).coerceAtLeast(1),
        playMode = PlayMode.fromInt(sp.getInt("playMode", 0)),
        themeMode = ThemeMode.fromInt(sp.getInt("themeMode", 0)),
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit()
            .putString("serverUrl", s.serverUrl)
            .putBoolean("authenticated", s.authenticated)
            .putBoolean("filterVip", s.filterVip)
            .putBoolean("hotCommentsOnly", s.hotCommentsOnly)
            .putString("recommendDomain", s.recommendDomain)
            .putString("recommendToken", s.recommendToken)
            .putInt("recommendFavCount", s.recommendFavCount)
            .putInt("playMode", s.playMode.ordinal)
            .putInt("themeMode", s.themeMode.ordinal)
            .apply()
        _settings.value = s
    }

    // ---------- 播放队列持久化 ----------

    fun saveQueue(queue: List<Song>, index: Int) {
        val arr = JSONArray()
        queue.forEach { arr.put(it.toJson()) }
        sp.edit()
            .putString("queue", arr.toString())
            .putInt("queueIndex", index)
            .apply()
    }

    fun loadQueue(): Pair<List<Song>, Int> {
        val raw = sp.getString("queue", null) ?: return emptyList<Song>() to 0
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<Song>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(Song.fromJson(it)) }
            }
            val idx = sp.getInt("queueIndex", 0).coerceIn(0, (list.size - 1).coerceAtLeast(0))
            list to idx
        }.getOrElse { emptyList<Song>() to 0 }
    }

    // ---------- 播放历史（去重、最新在前、上限 100） ----------

    fun loadHistory(): MutableList<Song> {
        val raw = sp.getString("history", null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<Song>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(Song.fromJson(it)) }
            }
            list
        }.getOrElse { mutableListOf() }
    }

    fun saveHistory(list: List<Song>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("history", arr.toString()).apply()
    }

    fun addHistory(song: Song) {
        val list = loadHistory()
        list.removeAll { it.key == song.key }
        list.add(0, song)
        while (list.size > 100) list.removeAt(list.size - 1)
        saveHistory(list)
    }

    fun clearHistory() = sp.edit().remove("history").apply()
}
