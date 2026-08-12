package com.mkonline.player.playback

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mkonline.player.MkApp
import com.mkonline.player.widget.PlayerWidgetProvider
import java.io.IOException

/**
 * 后台播放服务：接管通知栏/锁屏媒体控制。
 *
 * 歌曲播放地址有时效性，MediaItem 使用 `mk://source/id` 占位 scheme，
 * 播放时由 [ResolvingDataSource] 同步调用 api.php（types=url）解析成真实地址，
 * 对齐 Web 端 ajaxUrl 的“播放时再临时抓取”策略。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val api = (application as MkApp).container.api

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("MKOnlinePlayer-Android/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val resolving = ResolvingDataSource.Factory(http) { dataSpec: DataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme != "mk") {
                dataSpec
            } else {
                val source = uri.host ?: "netease"
                val id = uri.path.orEmpty().removePrefix("/")
                val real = api.songUrlBlocking(source, id)
                if (real.isEmpty()) throw IOException("无法获取播放地址: $source/$id")
                dataSpec.withUri(Uri.parse(real))
            }
        }

        val player = ExoPlayer.Builder(
            this,
            DefaultMediaSourceFactory(this).setDataSourceFactory(resolving),
        ).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }

        // 切歌/播放态/元数据变化时推送状态到桌面部件
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = pushToWidget(player)
            override fun onIsPlayingChanged(isPlaying: Boolean) = pushToWidget(player)
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = pushToWidget(player)
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun pushToWidget(player: Player) {
        val meta = player.mediaMetadata
        PlayerWidgetProvider.updateFromService(
            this,
            title = meta.title?.toString() ?: "",
            artist = meta.artist?.toString() ?: "",
            art = meta.artworkUri?.toString() ?: "",
            playing = player.isPlaying,
            posMs = player.currentPosition.coerceAtLeast(0L),
            durMs = player.duration.coerceAtLeast(0L),
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务列表划掉 App 时，若未在播放则停止服务，避免后台空跑
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // 服务结束时把暂停态推给挂件，避免界面残留"播放中"
        PlayerWidgetProvider.updateFromService(
            this,
            title = "", artist = "", art = "", playing = false,
        )
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
