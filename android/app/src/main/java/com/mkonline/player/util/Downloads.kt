package com.mkonline.player.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.mkonline.player.data.Api
import com.mkonline.player.data.Song

/** 歌曲下载：先经 api.php(types=download) 由服务端取回并缓存，再用系统下载器落到公共音乐目录。 */
object Downloads {

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "song" }

    /** @return 用于 Toast 的提示文案。 */
    suspend fun start(context: Context, api: Api, song: Song): String {
        val playUrl = api.songUrl(song)
        if (playUrl.isEmpty()) return "无法获取播放地址，下载失败"
        val remote = try {
            api.download(song, playUrl)
        } catch (e: Exception) {
            return e.message ?: "下载失败"
        }
        val dm = context.getSystemService(DownloadManager::class.java)
            ?: return "系统下载服务不可用"
        val fileName = "${sanitize(song.name)} - ${sanitize(song.artist)}.mp3"
        val req = DownloadManager.Request(Uri.parse(remote))
            .setTitle("${song.name} - ${song.artist}")
            .setDescription(remote)
            .setMimeType("audio/mpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
        dm.enqueue(req)
        return "已开始下载到 Music/$fileName"
    }
}
