package com.mkonline.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mkonline.player.AppContainer
import com.mkonline.player.data.Song
import com.mkonline.player.ui.components.EmptyHint
import com.mkonline.player.ui.components.LoadingBox
import com.mkonline.player.ui.components.SongRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 智能推荐：调用外部推荐服务（与 Web 端 loadRecommendList 对齐）。
 * 流程：收藏列表 -> 打乱取 N 首歌名 -> POST /api/v1/recommend/music -> 轮询结果 -> 逐首搜索匹配。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(container: AppContainer, onBack: () -> Unit) {
    val api = container.api
    val pm = container.playerManager
    val settings by container.prefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val current by pm.currentSong.collectAsState()

    val results = remember { mutableStateListOf<Song>() }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun generate() {
        if (running) return
        if (settings.recommendToken.isBlank()) {
            error = "请先在设置中配置推荐服务 Token"
            return
        }
        running = true
        error = null
        results.clear()
        scope.launch {
            try {
                status = "正在获取收藏列表…"
                val collections = runCatching { api.collectionsList() }.getOrDefault(emptyList())
                var names = collections.shuffled()
                    .take(settings.recommendFavCount.coerceAtLeast(1))
                    .map { it.name }
                    .filter { it.isNotBlank() }
                if (names.isEmpty()) {
                    // 回落到播放历史
                    names = container.prefs.loadHistory().take(10).map { it.name }.filter { it.isNotBlank() }
                }
                if (names.isEmpty()) {
                    names = listOf("流行音乐") // 兜底，与 Web 端一致
                }

                status = "正在提交推荐请求…"
                val taskId = api.recommendStart(names)

                status = "正在生成推荐…"
                var polls = 0
                var recommendNames: List<String> = emptyList()
                while (isActive && polls < 30) {
                    delay(10_000)
                    val (state, items, errMsg) = api.recommendPoll(taskId)
                    when (state) {
                        "completed" -> { recommendNames = items; break }
                        "failed" -> throw RuntimeException(errMsg.ifEmpty { "推荐任务失败" })
                        else -> status = "正在生成推荐…（${polls + 1}）"
                    }
                    polls++
                }
                if (recommendNames.isEmpty()) {
                    throw RuntimeException("推荐结果为空")
                }

                // 逐首搜索匹配歌曲，限制前 15 首
                val toSearch = recommendNames.take(15)
                val seen = HashSet<String>()
                for ((i, name) in toSearch.withIndex()) {
                    status = "匹配歌曲 ${i + 1}/${toSearch.size}"
                    val list = runCatching {
                        api.search(name, source = "netease", page = 1, count = 20, filterVip = settings.filterVip)
                    }.getOrDefault(emptyList())
                    val first = list.firstOrNull() ?: continue
                    val key = first.name.trim().lowercase()
                    if (key.isNotEmpty() && !seen.add(key)) continue
                    results.add(first)
                }

                status = ""
                if (results.isEmpty()) {
                    error = "没有匹配到可播放的歌曲"
                } else {
                    pm.toast("推荐已生成，共 ${results.size} 首")
                }
            } catch (e: Exception) {
                error = e.message ?: "推荐生成失败"
            } finally {
                running = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能推荐") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 顶部说明 + 生成按钮
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    "根据收藏/历史生成",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = ::generate, enabled = !running) {
                    Text(if (running) "生成中…" else "生成推荐")
                }
            }

            if (running) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text(status.ifEmpty { "处理中…" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            if (results.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { pm.playAll(results.toList(), 0) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "播放全部")
                    }
                }
            }

            if (results.isEmpty() && !running && error == null) {
                EmptyHint("点击右上角“生成推荐”开始")
            } else if (results.isEmpty() && running) {
                LoadingBox(false)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results.size) { i ->
                        val song = results[i]
                        SongRow(
                            song = song,
                            playing = current?.key == song.key,
                            index = i,
                            onClick = { pm.playAll(results.toList(), i) },
                            onLongClick = {},
                        )
                    }
                }
            }
        }
    }
}
