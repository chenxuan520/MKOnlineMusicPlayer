package com.mkonline.player

import android.app.Application
import com.mkonline.player.data.Api
import com.mkonline.player.data.CoverCache
import com.mkonline.player.data.Prefs
import com.mkonline.player.data.SheetCache
import com.mkonline.player.playback.PlayerManager

/** 手动依赖注入容器，避免引入 Hilt 等编译期依赖。 */
class AppContainer(val app: Application) {
    val prefs: Prefs by lazy { Prefs(app) }
    val api: Api by lazy { Api { prefs.settings.value } }
    val playerManager: PlayerManager by lazy { PlayerManager(app, prefs, api) }

    init {
        // 加载磁盘封面/歌单缓存，避免进程重启后重复调 API
        CoverCache.init(app)
        SheetCache.init(app)
    }
}

class MkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
