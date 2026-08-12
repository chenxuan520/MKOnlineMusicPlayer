package com.mkonline.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mkonline.player.data.Api
import com.mkonline.player.data.CoverCache
import com.mkonline.player.data.LrcParser
import com.mkonline.player.data.LyricLine
import com.mkonline.player.data.PlayMode
import com.mkonline.player.data.Prefs
import com.mkonline.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface LyricState {
    data object Idle : LyricState
    data object Loading : LyricState
    data class Ready(val lines: List<LyricLine>) : LyricState
    data object Empty : LyricState
}

/**
 * 播放控制中枢：持有 MediaController（连到 PlaybackService）、
 * 维护播放队列/历史/当前歌词/收藏状态，向 UI 暴露 StateFlow。
 */
class PlayerManager(
    private val context: Context,
    private val prefs: Prefs,
    private val api: Api,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controller: MediaController? = null

    /** 与 player 内 mediaItems 严格同序。 */
    private val queue = ArrayList<Song>()

    // ---------- 暴露给 UI 的状态 ----------

    val ready = MutableStateFlow(false)
    val queueState = MutableStateFlow<List<Song>>(emptyList())
    val currentSong = MutableStateFlow<Song?>(null)
    val currentIndex = MutableStateFlow(-1)
    val isPlaying = MutableStateFlow(false)
    val playMode = MutableStateFlow(prefs.settings.value.playMode)
    val lyricState = MutableStateFlow<LyricState>(LyricState.Idle)
    val currentFavorite = MutableStateFlow(false)
    /** 当前封面（运行时补全，歌单/封面接口慢时先靠 Song.pic）。 */
    val currentPic = MutableStateFlow("")

    /** 一次性提示（错误、收藏结果等），由 UI 收集后 Toast。 */
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

    /** 连续播放错误自动跳过计数，避免整个队列全是死链时无限连跳。 */
    private var failStreak = 0

    private var lyricJob: kotlinx.coroutines.Job = kotlinx.coroutines.Job()

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                applyMode(playMode.value)
                restoreQueue(c)
                ready.value = true
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idx = controller?.currentMediaItemIndex ?: -1
            currentIndex.value = idx
            val song = queue.getOrNull(idx)
            currentSong.value = song
            if (song != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                onSongStart(song)
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
            if (playing) failStreak = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            failStreak++
            val c = controller ?: return
            if (failStreak <= 3 && c.mediaItemCount > 1 && c.hasNextMediaItem()) {
                _toasts.tryEmit("歌曲无法播放，自动跳过")
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            } else {
                _toasts.tryEmit("播放失败：${error.errorCodeName}")
            }
        }
    }

    // ---------- 队列恢复 ----------

    private fun restoreQueue(c: MediaController) {
        val (saved, index) = prefs.loadQueue()
        if (saved.isEmpty()) return
        queue.clear()
        queue.addAll(saved)
        queueState.value = queue.toList()
        c.setMediaItems(saved.map(::toMediaItem), index, 0L)
        currentIndex.value = index
        currentSong.value = saved.getOrNull(index)
        currentPic.value = saved.getOrNull(index)?.pic.orEmpty()
        // 只恢复界面，不 prepare，避免无谓抓直链
    }

    private fun persistQueue() {
        prefs.saveQueue(queue, (controller?.currentMediaItemIndex ?: 0).coerceAtLeast(0))
    }

    // ---------- 播放操作 ----------

    private fun toMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.key)
            .setUri("mk://${song.source}/${Uri.encode(song.id.ifEmpty { song.urlId })}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.pic.takeIf { it.isNotEmpty() }?.let(Uri::parse))
                    .build()
            )
            .build()

    /** 用整份列表替换队列并从 startIndex 播放。 */
    fun playAll(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        queue.clear()
        queue.addAll(songs)
        queueState.value = queue.toList()
        c.setMediaItems(songs.map(::toMediaItem), startIndex.coerceIn(0, songs.size - 1), 0L)
        c.prepare()
        c.play()
        persistQueue()
    }

    /** 播放单首：若已在队列则直接切过去，否则插到当前之后立即播放。 */
    fun playSong(song: Song) {
        val idx = queue.indexOfFirst { it.key == song.key }
        if (idx >= 0) playAt(idx) else {
            val wasEmpty = queue.isEmpty()
            insertNext(song)
            // insertNext 在队列从空变非空时已自动 playAt(0)，避免重复调用
            if (!wasEmpty) playAt(currentIndex.value + 1)
        }
    }

    fun insertNext(song: Song) {
        val c = controller ?: return
        val pos = if (queue.isEmpty()) 0 else (currentIndex.value + 1).coerceAtMost(queue.size)
        queue.add(pos, song)
        c.addMediaItem(pos, toMediaItem(song))
        queueState.value = queue.toList()
        persistQueue()
        if (queue.size == 1) playAt(0)
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        queue.removeAt(index)
        c.removeMediaItem(index)
        queueState.value = queue.toList()
        persistQueue()
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        queue.clear()
        queueState.value = emptyList()
        currentSong.value = null
        currentIndex.value = -1
        prefs.saveQueue(emptyList(), 0)
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        c.seekTo(index, 0L)
        c.prepare()
        c.play()
        persistQueue()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE && c.mediaItemCount > 0) c.prepare()
            c.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun prev() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun positionMs(): Long = controller?.currentPosition ?: 0L
    fun durationMs(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    fun cycleMode() {
        val m = playMode.value.next()
        playMode.value = m
        prefs.update { it.copy(playMode = m) }
        applyMode(m)
    }

    private fun applyMode(m: PlayMode) {
        val c = controller ?: return
        when (m) {
            PlayMode.ORDER -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ALL
            }
            PlayMode.RANDOM -> {
                c.shuffleModeEnabled = true
                c.repeatMode = Player.REPEAT_MODE_ALL
            }
            PlayMode.SINGLE -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    // ---------- 切歌后的联动 ----------

    private fun onSongStart(song: Song) {
        currentPic.value = song.pic
        prefs.addHistory(song)
        loadLyric(song)
        refreshFavorite(song)
        if (song.pic.isEmpty()) {
            scope.launch(Dispatchers.IO) {
                val pic = api.picUrl(song)
                if (pic.isNotEmpty() && currentSong.value?.key == song.key) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        currentPic.value = pic
                        // 写入进程级封面缓存，搜索/收藏/历史等列表渲染时复用
                        CoverCache.put(song.source, song.id, pic)
                        queue.indexOfFirst { it.key == song.key }.takeIf { it >= 0 }?.let { i ->
                            queue[i] = queue[i].copy(pic = pic)
                            queueState.value = queue.toList()
                            // 同步更新播放器内该 MediaItem 的封面（桌面部件/通知栏用）
                            val c = controller
                            if (c != null && i == c.currentMediaItemIndex) {
                                c.replaceMediaItem(i, toMediaItem(queue[i]))
                            }
                        }
                        // 持久化队列（含封面），下次启动恢复时直接可用
                        persistQueue()
                    }
                } else if (pic.isNotEmpty()) {
                    // 已切歌但封面仍有效，至少写入缓存供列表用
                    CoverCache.put(song.source, song.id, pic)
                }
            }
        }
    }

    private fun loadLyric(song: Song) {
        lyricJob.cancel()
        lyricState.value = LyricState.Loading
        lyricJob = scope.launch {
            val pair = api.lyric(song)
            if (currentSong.value?.key != song.key) return@launch
            val lines = LrcParser.parse(pair?.first, pair?.second)
            lyricState.value = if (lines.isEmpty()) LyricState.Empty else LyricState.Ready(lines)
        }
    }

    fun reloadLyric() = currentSong.value?.let(::loadLyric)

    // ---------- 收藏 ----------

    fun refreshFavorite(song: Song? = null) {
        val s = song ?: currentSong.value ?: return
        scope.launch {
            val fav = api.collectionCheck(s)
            if (currentSong.value?.key == s.key) currentFavorite.value = fav
        }
    }

    fun toggleFavorite() {
        val song = currentSong.value ?: return
        scope.launch {
            try {
                val (ok, msg) = if (currentFavorite.value) api.collectionRemove(song) else api.collectionAdd(song)
                if (ok || msg.contains("已收藏")) {
                    currentFavorite.value = !currentFavorite.value
                }
                _toasts.tryEmit(msg.ifEmpty { if (ok) "操作成功" else "操作失败" })
            } catch (e: Exception) {
                _toasts.tryEmit(e.message ?: "操作失败")
            }
        }
    }

    fun toast(message: String) = _toasts.tryEmit(message)

    /** UI 轮的进度Ticker。 */
    suspend fun tickPosition(intervalMs: Long = 500, onTick: (posMs: Long, durationMs: Long) -> Unit) {
        while (true) {
            onTick(positionMs(), durationMs())
            delay(intervalMs)
        }
    }
}
