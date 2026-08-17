package com.mkonline.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * api.php 客户端。所有 suspend 方法切到 IO 线程；Web 端一律 POST 表单（mkPlayer.method = POST），
 * 这里保持一致（服务端 getParam 同时兼容 GET/POST）。
 *
 * 返回结构对齐 Web 端：
 *  - search     -> [{id,name,artist:[],album,source,url_id,pic_id,lyric_id,...}]
 *  - url        -> {url, br?, size?}
 *  - pic        -> {url}
 *  - lyric      -> {lyric, tlyric}
 *  - playlist   -> {playlist:{name,coverImgUrl,creator:{...},tracks:[...]}}（网易云原生格式）
 *  - comments   -> {hot_comment:[{user:{name,avatar},time,content}], comment:[...]}
 *  - collections-> {success,message?,collections?,collected?}
 *  - download   -> {code:1|0,url,msg}
 */
class Api(private val settings: () -> Settings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 探测 CDN 可达性用：短超时，避免检测流程被单首慢响应拖住。 */
    private val probeClient = client.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // ---------- 地址规范化 ----------

    /** serverUrl 兼容填 "http://host"、“host”、“http://host/子目录” 或完整 api.php 地址。 */
    private fun endpoint(): String {
        var base = settings().serverUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw ApiException("请先在设置中填写服务器地址")
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://$base"
        return if (base.endsWith(".php")) base else "$base/api.php"
    }

    /** 推荐服务域名：优先独立配置，否则回落为 API 服务器 origin（对齐 Web 端 location.origin 逻辑）。 */
    private fun recommendOrigin(): String {
        val custom = settings().recommendDomain.trim().trimEnd('/')
        if (custom.isNotEmpty()) return custom
        val ep = endpoint()
        val idx = ep.indexOf("/", ep.indexOf("://") + 3)
        return if (idx > 0) ep.substring(0, idx) else ep
    }

    // ---------- 基础请求 ----------

    /** 同步 POST 表单，返回 body 字符串，供协程与 ExoPlayer 加载线程（ResolvingDataSource）共用。 */
    private fun postBlocking(params: Map<String, String>): String {
        val b = FormBody.Builder()
        params.forEach { (k, v) -> b.add(k, v) }
        val req = Request.Builder().url(endpoint()).post(b.build()).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}")
            return body
        }
    }

    private suspend fun post(params: Map<String, String>): String =
        withContext(Dispatchers.IO) { postBlocking(params) }

    private fun jsonObject(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrElse { throw ApiException("服务器返回格式错误") }

    // ---------- 认证 ----------

    suspend fun auth(password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val o = jsonObject(
            postBlocking(mapOf("types" to "auth", "password" to password))
        )
        o.optBoolean("success") to o.jsonStr("message")
    }

    // ---------- 搜索 ----------

    suspend fun search(
        keyword: String, source: String, page: Int = 1,
        count: Int = 20, filterVip: Boolean = true,
    ): List<Song> = withContext(Dispatchers.IO) {
        val body = postBlocking(
            mapOf(
                "types" to "search", "name" to keyword, "source" to source,
                "pages" to page.toString(), "count" to count.toString(),
                "filter_vip" to filterVip.toString(),
            )
        )
        runCatching { org.json.JSONArray(body) }
            .getOrElse { throw ApiException("搜索结果格式错误") }
            .songList(Song::fromSearchJson)
    }

    // ---------- 播放地址 ----------

    /** 原始 url 结果；空串表示取不到。 */
    private fun rawUrlBlocking(source: String, id: String): String {
        val o = jsonObject(postBlocking(mapOf("types" to "url", "id" to id, "source" to source)))
        return o.jsonStr("url")
    }

    /**
     * 对齐 Web 端 ajaxUrl 的处理：
     * 网易云空链接回退 outer url；m7c/m8c 域名替换为 m7/m8。
     * 返回 "" 表示该歌曲当前无可用链接。
     */
    fun songUrlBlocking(source: String, id: String): String {
        if (id.isEmpty()) return ""
        var url = runCatching { rawUrlBlocking(source, id) }.getOrDefault("")
        if (source == "netease") {
            if (url.isEmpty()) {
                url = "https://music.163.com/song/media/outer/url?id=$id.mp3"
            } else {
                url = url.replace("m7c.music.", "m7.music.").replace("m8c.music.", "m8.music.")
            }
        }
        return url
    }

    suspend fun songUrl(song: Song): String {
        if (song.url.isNotEmpty()) return song.url
        return withContext(Dispatchers.IO) { songUrlBlocking(song.source, song.id.ifEmpty { song.urlId }) }
    }

    /**
     * 收藏有效性探测：新鲜解析播放地址（不走 song.url 短路）后，对 CDN 真实发
     * Range GET 验证可达（跟随重定向），避免“API 返回了链接但实际 403/404”的误报
     * —— 比 Web 端 checkMusicUrl 更严格，因为后者只看 API 层非空。
     */
    suspend fun probePlayable(song: Song): Boolean = withContext(Dispatchers.IO) {
        val url = songUrlBlocking(song.source, song.id.ifEmpty { song.urlId })
        if (url.isEmpty()) return@withContext false
        runCatching {
            probeClient.newCall(
                Request.Builder().url(url)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .build()
            ).execute().use { it.code in 200..399 }
        }.getOrDefault(false)
    }

    // ---------- 封面 ----------

    suspend fun picUrl(song: Song): String {
        if (song.pic.isNotEmpty()) return song.pic
        val picId = song.picId
        if (picId.isEmpty()) return ""
        return withContext(Dispatchers.IO) {
            runCatching {
                jsonObject(
                    postBlocking(mapOf("types" to "pic", "id" to picId, "source" to song.source))
                ).jsonStr("url")
            }.getOrDefault("")
        }
    }

    // ---------- 歌词 ----------

    /** @return lyric 原文与翻译；两者皆空返回 null。 */
    suspend fun lyric(song: Song): Pair<String, String>? {
        val id = song.lyricId.ifEmpty { song.id }
        if (id.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val o = jsonObject(
                    postBlocking(mapOf("types" to "lyric", "id" to id, "source" to song.source))
                )
                val lrc = o.jsonStr("lyric")
                val tlyric = o.jsonStr("tlyric")
                if (lrc.isBlank() && tlyric.isBlank()) null else lrc to tlyric
            }.getOrNull()
        }
    }

    // ---------- 歌单 ----------

    /** types=playlist 返回网易云原生结构。 */
    suspend fun playlist(id: String): Sheet = withContext(Dispatchers.IO) {
        val root = jsonObject(postBlocking(mapOf("types" to "playlist", "id" to id)))
        val p = root.optJSONObject("playlist") ?: throw ApiException("无法获取歌单信息")
        val creator = p.optJSONObject("creator")
        val tracks = p.optJSONArray("tracks")
        val songs = ArrayList<Song>(tracks?.length() ?: 0)
        if (tracks != null) {
            for (i in 0 until tracks.length()) {
                val t = tracks.optJSONObject(i) ?: continue
                val sid = t.jsonStr("id")
                val al = t.optJSONObject("al")
                val artist = buildString {
                    val ar = t.optJSONArray("ar")
                    if (ar != null) for (j in 0 until ar.length()) {
                        if (j > 0) append("/")
                        append(ar.optJSONObject(j)?.jsonStr("name").orEmpty())
                    }
                }
                songs.add(
                    Song(
                        id = sid,
                        name = t.jsonStr("name"),
                        artist = artist,
                        album = al?.jsonStr("name").orEmpty(),
                        source = "netease",
                        urlId = sid,
                        picId = "",
                        lyricId = sid,
                        pic = al?.jsonStr("picUrl")?.let { if (it.isEmpty()) "" else "$it?param=300y300" }.orEmpty(),
                    )
                )
            }
        }
        Sheet(
            id = id,
            name = p.jsonStr("name"),
            cover = p.jsonStr("coverImgUrl").let { if (it.isEmpty()) "" else "$it?param=200y200" },
            creatorName = creator?.jsonStr("nickname").orEmpty(),
            creatorAvatar = creator?.jsonStr("avatarUrl").orEmpty(),
            songs = songs,
        )
    }

    // ---------- 评论 ----------

    /** @return hot to normal，对齐 Web 端 hot_comment / comment 两段。 */
    suspend fun comments(
        id: String, source: String, page: Int = 1, count: Int = 50,
    ): Pair<List<CommentItem>, List<CommentItem>> = withContext(Dispatchers.IO) {
        val root = jsonObject(
            postBlocking(
                mapOf(
                    "types" to "comments", "id" to id, "source" to source,
                    "pages" to page.toString(), "count" to count.toString(),
                )
            )
        )
        fun parse(key: String): List<CommentItem> {
            val arr = root.optJSONArray(key) ?: return emptyList()
            val out = ArrayList<CommentItem>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val u = c.optJSONObject("user")
                out.add(
                    CommentItem(
                        id = c.jsonStr("id"),
                        userName = u?.jsonStr("name").orEmpty(),
                        avatar = u?.jsonStr("avatar").orEmpty(),
                        time = c.jsonStr("time"),
                        content = c.jsonStr("content"),
                    )
                )
            }
            return out
        }
        parse("hot_comment") to parse("comment")
    }

    // ---------- 收藏 ----------

    suspend fun collectionsList(): List<Song> = withContext(Dispatchers.IO) {
        val o = jsonObject(postBlocking(mapOf("types" to "collections", "action" to "list")))
        val arr = o.optJSONArray("collections")
        arr?.songList(Song::fromJson) ?: emptyList()
    }

    suspend fun collectionAdd(song: Song): Pair<Boolean, String> =
        collectionAction("add", song)

    suspend fun collectionRemove(song: Song): Pair<Boolean, String> =
        collectionAction("remove", song)

    private suspend fun collectionAction(action: String, song: Song): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val o = jsonObject(
                postBlocking(
                    mapOf(
                        "types" to "collections", "action" to action,
                        "id" to song.id, "source" to song.source, "name" to song.name,
                        "artist" to song.artist, "album" to song.album,
                        "pic" to song.pic, "url_id" to song.urlId,
                        "pic_id" to song.picId, "lyric_id" to song.lyricId,
                    )
                )
            )
            o.optBoolean("success") to o.jsonStr("message")
        }

    suspend fun collectionCheck(song: Song): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            jsonObject(
                postBlocking(
                    mapOf(
                        "types" to "collections", "action" to "check",
                        "id" to song.id, "source" to song.source,
                    )
                )
            ).optBoolean("collected")
        }.getOrDefault(false)
    }

    // ---------- 下载 ----------

    /** 服务端缓存下载，返回可直接下载的 URL；失败抛 ApiException。 */
    suspend fun download(song: Song, playUrl: String): String = withContext(Dispatchers.IO) {
        val o = jsonObject(
            postBlocking(
                mapOf(
                    "types" to "download", "url" to playUrl, "name" to song.name,
                    "artist" to song.artist, "source" to song.source,
                )
            )
        )
        if (o.optInt("code") == 1 && o.jsonStr("url").isNotEmpty()) o.jsonStr("url")
        else throw ApiException(o.jsonStr("msg").ifEmpty { "下载失败" })
    }

    // ---------- 智能推荐 ----------

    private fun authedJson(): Pair<String, String> {
        val token = settings().recommendToken.trim()
        if (token.isEmpty()) throw ApiException("请先在设置中配置推荐服务 Token")
        return recommendOrigin() to token
    }

    /** 提交推荐任务，返回 task_id。 */
    suspend fun recommendStart(favorites: List<String>): String = withContext(Dispatchers.IO) {
        val (origin, token) = authedJson()
        val body = JSONObject().put("favorites", org.json.JSONArray(favorites)).toString()
        val req = Request.Builder()
            .url("$origin/api/v1/recommend/music?async=true")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 401) throw ApiException("鉴权失败(401)：请检查 Token")
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("推荐请求失败: HTTP ${resp.code}")
            val o = runCatching { JSONObject(text) }.getOrElse { throw ApiException("推荐返回格式错误") }
            o.jsonStr("task_id").ifEmpty { throw ApiException("推荐服务未返回任务ID") }
        }
    }

    /** @return Triple(status, 推荐歌名列表, 错误信息) */
    suspend fun recommendPoll(taskId: String): Triple<String, List<String>, String> =
        withContext(Dispatchers.IO) {
            val (origin, token) = authedJson()
            val req = Request.Builder()
                .url("$origin/api/v1/recommend/result/$taskId")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException("查询任务状态失败: HTTP ${resp.code}")
                val o = runCatching { JSONObject(text) }.getOrElse { throw ApiException("轮询返回格式错误") }
                val status = o.jsonStr("status")
                val names = ArrayList<String>()
                o.optJSONObject("data")?.optJSONArray("items")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.jsonStr("name")
                            ?.takeIf { it.isNotBlank() }?.let(names::add)
                    }
                }
                Triple(status, names, o.jsonStr("error"))
            }
        }
}
