package com.mkonline.player.data

import org.json.JSONArray
import org.json.JSONObject

/** 与 Web 端 js/ajax.js 中的音乐对象保持一致。 */
data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val source: String,
    val urlId: String,
    val picId: String,
    val lyricId: String,
    val pic: String = "",
    val url: String = "",
) {
    /** 全局唯一键，用于历史/收藏去重。 */
    val key: String get() = "$source:$id"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("artist", artist); put("album", album)
        put("source", source); put("url_id", urlId); put("pic_id", picId)
        put("lyric_id", lyricId); put("pic", pic); put("url", url)
    }

    companion object {
        fun fromJson(o: JSONObject): Song = Song(
            id = o.jsonStr("id"),
            name = o.jsonStr("name"),
            artist = o.jsonStr("artist"),
            album = o.jsonStr("album"),
            source = o.jsonStr("source", "netease"),
            urlId = o.jsonStr("url_id"),
            picId = o.jsonStr("pic_id"),
            lyricId = o.jsonStr("lyric_id"),
            pic = o.jsonStr("pic"),
            url = o.jsonStr("url"),
        )

        /** 解析 search 接口（format=true）的单条结果，artist 为数组。 */
        fun fromSearchJson(o: JSONObject): Song = fromJson(o).let {
            it.copy(artist = o.optJSONArray("artist")?.let { arr ->
                buildString {
                    for (i in 0 until arr.length()) {
                        if (i > 0) append("/")
                        append(arr.optString(i))
                    }
                }
            } ?: it.artist)
        }
    }
}

/** 歌单元信息（详情加载前可能只知道 id）。 */
data class SheetMeta(
    val id: String,
    val name: String = "",
    val cover: String = "",
    val creatorName: String = "",
)

/** 歌单详情（types=playlist 返回 web 原生结构）。 */
data class Sheet(
    val id: String,
    val name: String,
    val cover: String,
    val creatorName: String,
    val creatorAvatar: String,
    val songs: List<Song>,
)

data class CommentItem(
    val id: String,
    val userName: String,
    val avatar: String,
    val time: String,
    val content: String,
)

data class LyricLine(val timeMs: Long, val text: String)

/** org.json 的 optString 会把 JSON null 变成字符串 "null"，这里统一处理。 */
fun JSONObject.jsonStr(key: String, def: String = ""): String =
    if (isNull(key)) def else optString(key, def)

fun JSONArray.songList(mapper: (JSONObject) -> Song): List<Song> {
    val out = ArrayList<Song>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { out.add(mapper(it)) }
    }
    return out
}
