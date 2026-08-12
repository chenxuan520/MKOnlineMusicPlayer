package com.mkonline.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mkonline.player.MainActivity
import com.mkonline.player.R
import com.mkonline.player.playback.PlaybackService
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * 4x1 桌面部件：封面 + 歌名/歌手 + 上一首/播放暂停/下一首。
 *
 * 控制链路：挂件点击 → BroadcastReceiver 收到自定义 action → 连到 PlaybackService
 * 的 MediaSession 执行对应命令（切歌/播放暂停）。
 *
 * 状态链路：PlaybackService 的 Player.Listener 在切歌/播放态变化时调
 * [updateFromService]，写入 SharedPreferences 并刷新全部挂件；
 * 系统侧周期性 onUpdate 只从缓存状态渲染，不依赖服务存活。
 */
class PlayerWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.mkonline.player.widget.PLAY_PAUSE"
        private const val ACTION_PREV = "com.mkonline.player.widget.PREV"
        private const val ACTION_NEXT = "com.mkonline.player.widget.NEXT"

        private const val PREFS = "widget_state"
        private const val K_TITLE = "title"
        private const val K_ARTIST = "artist"
        private const val K_ART = "art"
        private const val K_PLAYING = "playing"
        private const val K_POS = "pos"
        private const val K_DUR = "dur"

        /** 封面 bitmap 内存缓存：避免每次状态推送都重下。 */
        private val bmpCache = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>?) =
                    size > 16
            }
        )

        /** 播放服务主动推送当前状态，写盘 + 刷新所有挂件。 */
        fun updateFromService(context: Context, title: String, artist: String, art: String, playing: Boolean, posMs: Long = 0L, durMs: Long = 0L) {
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_TITLE, title)
                .putString(K_ARTIST, artist)
                .putString(K_ART, art)
                .putBoolean(K_PLAYING, playing)
                .putLong(K_POS, posMs)
                .putLong(K_DUR, durMs)
                .apply()
            renderAll(appContext)
        }

        /** 从缓存状态渲染所有挂件（封面 bitmap 按需后台下载）。 */
        fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = sp.getString(K_TITLE, "") ?: ""
            val artist = sp.getString(K_ARTIST, "") ?: ""
            val art = sp.getString(K_ART, "") ?: ""
            val playing = sp.getBoolean(K_PLAYING, false)
            val posMs = sp.getLong(K_POS, 0L)
            val durMs = sp.getLong(K_DUR, 0L)

            // 后台线程下载封面 bitmap；内存命中不重复下载，下载失败保留旧封面
            Thread {
                val bmp = if (art.isNotEmpty()) {
                    bmpCache[art] ?: runCatching { loadBitmap(art) }.getOrNull()?.also { bmpCache[art] = it }
                } else null
                // 下载失败时用上次缓存封面兜底，避免封面闪退为占位图标
                val finalBmp = bmp ?: bmpCache.values.lastOrNull()
                ids.forEach { id ->
                    mgr.updateAppWidget(id, buildView(context, id, title, artist, playing, posMs, durMs, finalBmp))
                }
            }.start()
        }

        private fun buildView(
            context: Context, widgetId: Int,
            title: String, artist: String, playing: Boolean,
            posMs: Long, durMs: Long,
            artBitmap: android.graphics.Bitmap?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.widget_title, title.ifEmpty { context.getString(R.string.widget_placeholder) })
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_play,
                if (playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
            )
            // 进度条：0~1000 映射当前位置
            val progress = if (durMs > 0) ((posMs * 1000) / durMs).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)
            if (artBitmap != null) {
                views.setImageViewBitmap(R.id.widget_cover, artBitmap)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.widget_ic_play)
            }

            fun pi(action: String): PendingIntent = PendingIntent.getBroadcast(
                context, action.hashCode(),
                Intent(context, PlayerWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            views.setOnClickPendingIntent(R.id.widget_play, pi(ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_prev, pi(ACTION_PREV))
            views.setOnClickPendingIntent(R.id.widget_next, pi(ACTION_NEXT))

            // 点击歌名区域打开 App
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_info, openApp)
            views.setOnClickPendingIntent(R.id.widget_cover, openApp)

            return views
        }

        private fun loadBitmap(url: String): android.graphics.Bitmap? {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            return try {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> commandPlayer(context, 0)
            ACTION_PREV -> commandPlayer(context, 1)
            ACTION_NEXT -> commandPlayer(context, 2)
            else -> super.onReceive(context, intent)
        }
    }

    /** 连 MediaSession 发送命令；goAsync 保活进程到命令发完。 */
    private fun commandPlayer(context: Context, cmd: Int) {
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val latch = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post {
                    val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
                    val future = MediaController.Builder(appContext, token)
                        .setApplicationLooper(Looper.getMainLooper())
                        .buildAsync()
                    future.addListener({
                        runCatching {
                            val c = future.get()
                            when (cmd) {
                                0 -> if (c.isPlaying) c.pause() else {
                                    // 冷启动/恢复队列后处于 IDLE，先 prepare 再播放
                                    if (c.playbackState == androidx.media3.common.Player.STATE_IDLE && c.mediaItemCount > 0) c.prepare()
                                    c.play()
                                }
                                1 -> c.seekToPreviousMediaItem()
                                2 -> c.seekToNextMediaItem()
                            }
                            c.release()
                        }
                        latch.countDown()
                    }, Executor { r -> r.run() })
                }
                latch.await(5, TimeUnit.SECONDS)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
