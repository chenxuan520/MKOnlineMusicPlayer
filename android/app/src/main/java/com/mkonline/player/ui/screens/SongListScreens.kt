package com.mkonline.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mkonline.player.AppContainer
import com.mkonline.player.data.Song
import com.mkonline.player.ui.components.EmptyHint
import com.mkonline.player.ui.components.LoadingBox
import com.mkonline.player.ui.components.SongActionDialog
import com.mkonline.player.ui.components.SongRow
import com.mkonline.player.ui.components.rememberSongActions
import com.mkonline.player.util.Downloads
import kotlinx.coroutines.launch

// ============================ 收藏 ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(container: AppContainer, onOpenComments: (Song) -> Unit) {
    val api = container.api
    val pm = container.playerManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = remember { mutableStateListOf<Song>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val current by pm.currentSong.collectAsState()
    val (actionSong, setActionSong) = rememberSongActions()

    // fzf 风格模糊匹配（子序列匹配，忽略大小写和空格）
    fun fuzzyMatch(q: String, target: String): Boolean {
        val needle = q.lowercase().replace(" ", "")
        if (needle.isEmpty()) return true
        val hay = target.lowercase()
        var i = 0
        var j = 0
        while (i < needle.length && j < hay.length) {
            if (needle[i] == hay[j]) i++
            j++
        }
        return i == needle.length
    }

    val shown = if (query.isBlank()) list else list.filter {
        fuzzyMatch(query, it.name) || fuzzyMatch(query, it.artist) || fuzzyMatch(query, it.album)
    }

    fun refresh() {
        if (loading) return
        refreshing = true
        loading = true
        error = null
        scope.launch {
            try {
                val data = api.collectionsList()
                list.clear()
                list.addAll(data)
            } catch (e: Exception) {
                error = e.message ?: "加载收藏失败"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // ---------- 检测收藏有效性（对齐 Web 端 checkMusicUrl） ----------

    var detecting by remember { mutableStateOf(false) }
    var detectText by remember { mutableStateOf("") }
    var deadKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    /** 逐首调 types=url 验证可播放性（含 3 次重试，对齐 Web 端）。 */
    fun detect() {
        if (detecting || list.isEmpty()) return
        detecting = true
        deadKeys = emptySet()
        scope.launch {
            val dead = mutableListOf<Song>()
            list.toList().forEachIndexed { idx, song ->
                detectText = "检测中 ${idx + 1}/${list.size}"
                var ok = false
                repeat(3) { attempt ->
                    if (!ok) {
                        val url = runCatching { api.songUrl(song) }.getOrDefault("")
                        if (url.isNotEmpty()) ok = true
                        else kotlinx.coroutines.delay(if (attempt == 0) 500L else 1000L)
                    }
                }
                if (!ok) {
                    dead.add(song)
                    deadKeys = deadKeys + song.key
                }
            }
            detecting = false
            detectText = ""
            pm.toast(if (dead.isEmpty()) "全部可播放" else "发现 ${dead.size} 首失效歌曲")
        }
    }

    /** 批量删除失效歌曲。 */
    fun removeDead() {
        scope.launch {
            val dead = list.filter { it.key in deadKeys }
            dead.forEach { runCatching { api.collectionRemove(it) } }
            list.removeAll { it.key in deadKeys }
            val n = deadKeys.size
            deadKeys = emptySet()
            pm.toast("已删除 $n 首失效歌曲")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("我的收藏 (${shown.size}/${list.size})")
                        if (detectText.isNotEmpty()) {
                            Text(detectText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (deadKeys.isNotEmpty()) {
                        TextButton(onClick = ::removeDead) {
                            Text("删失效 ${deadKeys.size}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = ::detect, enabled = !detecting) {
                        Icon(Icons.Default.Checklist, contentDescription = "检测收藏")
                    }
                    IconButton(onClick = ::refresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 搜索框：过滤收藏列表
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("搜索收藏（歌名/歌手/专辑，支持模糊匹配）") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = ::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    loading && list.isEmpty() -> LoadingBox(true)
                    error != null -> EmptyHint(error!!)
                    list.isEmpty() -> EmptyHint("还没有收藏任何歌曲")
                    shown.isEmpty() -> EmptyHint("没有匹配 \"$query\" 的收藏")
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(shown.size) { i ->
                            val song = shown[i]
                            SongRow(
                                song = song,
                                playing = current?.key == song.key,
                                index = i,
                                onClick = { pm.playAll(shown, i) },
                                onLongClick = { setActionSong(song) },
                                trailing = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            val (ok, msg) = api.collectionRemove(song)
                                            if (ok) list.removeAll { it.key == song.key }
                                            pm.toast(msg)
                                        }
                                    }) {
                                        // 检测出的失效歌曲删除键标红
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "取消收藏",
                                            tint = if (song.key in deadKeys) MaterialTheme.colorScheme.error
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    SongActionDialog(
        song = actionSong,
        onDismiss = { setActionSong(null) },
        onPlayNext = { pm.insertNext(it); pm.toast("已加入下一首播放") },
        onComments = onOpenComments,
        onDownload = { s ->
            scope.launch { pm.toast(Downloads.start(context, container.api, s)) }
        },
    )
}

// ============================ 历史 ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(container: AppContainer, onOpenComments: (Song) -> Unit) {
    val pm = container.playerManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = remember { mutableStateListOf<Song>() }
    var loaded by remember { mutableStateOf(false) }
    val current by pm.currentSong.collectAsState()
    val (actionSong, setActionSong) = rememberSongActions()

    LaunchedEffect(Unit) {
        list.clear()
        list.addAll(container.prefs.loadHistory())
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放历史 (${list.size})") },
                actions = {
                    if (list.isNotEmpty()) {
                        IconButton(onClick = {
                            container.prefs.clearHistory()
                            list.clear()
                            pm.toast("已清空历史")
                        }) { Icon(Icons.Default.DeleteSweep, contentDescription = "清空") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!loaded) {
                LoadingBox(true)
            } else if (list.isEmpty()) {
                EmptyHint("暂无播放历史")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list.size) { i ->
                        val song = list[i]
                        SongRow(
                            song = song,
                            playing = current?.key == song.key,
                            index = i,
                            onClick = { pm.playAll(list.toList(), i) },
                            onLongClick = { setActionSong(song) },
                        )
                    }
                }
            }
        }
    }

    SongActionDialog(
        song = actionSong,
        onDismiss = { setActionSong(null) },
        onPlayNext = { pm.insertNext(it); pm.toast("已加入下一首播放") },
        onComments = onOpenComments,
        onDownload = { s ->
            scope.launch { pm.toast(Downloads.start(context, container.api, s)) }
        },
    )
}
