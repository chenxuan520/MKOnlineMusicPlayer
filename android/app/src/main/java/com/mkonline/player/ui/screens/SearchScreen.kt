package com.mkonline.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import com.mkonline.player.AppContainer
import com.mkonline.player.data.Song
import com.mkonline.player.ui.components.EmptyHint
import com.mkonline.player.ui.components.LoadingBox
import com.mkonline.player.ui.components.SongActionDialog
import com.mkonline.player.ui.components.SongRow
import com.mkonline.player.ui.components.rememberSongActions
import com.mkonline.player.util.Downloads
import kotlinx.coroutines.launch

/** 搜索状态进程级缓存：切换 Tab 时保留已搜到的结果。 */
private object SearchHolder {
    var keyword: String = ""
    var source: String = "netease"
    val results: MutableList<Song> = mutableListOf<Song>().toMutableStateList()
    var page: Int = 1
    var hasMore: Boolean = false
    var searched: Boolean = false
    var loading: Boolean = false

    fun reset() {
        results.clear()
        page = 1
        hasMore = false
        searched = false
        loading = false
    }
}

/** 登出/切换服务器时清理进程级搜索缓存，避免跨会话泄漏旧结果。 */
fun clearSearchCache() = SearchHolder.reset()

private val sources = listOf("netease" to "网易云", "kugou" to "酷狗", "tencent" to "QQ音乐")

@Composable
fun SearchScreen(
    container: AppContainer,
    onOpenComments: (Song) -> Unit,
) {
    val pm = container.playerManager
    val settings by container.prefs.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var keyword by remember { mutableStateOf(SearchHolder.keyword) }
    var source by remember { mutableStateOf(SearchHolder.source) }
    val results = SearchHolder.results
    val listState = rememberLazyListState()
    // 进入页面时强制清除可能残留的 loading 状态（上次切 Tab 时协程被取消可能没走到 finally）
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(SearchHolder.searched) }
    var hasMore by remember { mutableStateOf(SearchHolder.hasMore) }
    val (actionSong, setActionSong) = rememberSongActions()

    fun doSearch(reset: Boolean) {
        if (loading) return
        if (reset) {
            if (keyword.isBlank()) return
            SearchHolder.keyword = keyword
            SearchHolder.source = source
            results.clear()
            SearchHolder.page = 1
            SearchHolder.hasMore = false
            SearchHolder.searched = false
            searched = false
            hasMore = false
        }
        loading = true
        SearchHolder.loading = true
        scope.launch {
            if (reset) listState.scrollToItem(0)
            val page = SearchHolder.page
            try {
                val list = container.api.search(
                    keyword = SearchHolder.keyword,
                    source = SearchHolder.source,
                    page = page,
                    count = 20,
                    filterVip = settings.filterVip,
                )
                results.addAll(list)
                SearchHolder.hasMore = list.size >= 20
                SearchHolder.page = page + 1
                SearchHolder.searched = true
                searched = true
                hasMore = SearchHolder.hasMore
            } catch (e: Exception) {
                pm.toast(e.message ?: "搜索失败")
            } finally {
                loading = false
                SearchHolder.loading = false
            }
        }
    }

    // 滚到接近底部自动加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasMore && !loading && results.isNotEmpty() && last >= results.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) doSearch(false)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sources.forEach { (id, label) ->
                FilterChip(
                    selected = source == id,
                    onClick = {
                        source = id
                        SearchHolder.source = id
                        if (SearchHolder.searched) doSearch(true)
                    },
                    label = { Text(label) },
                )
            }
        }

        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("搜索歌曲、歌手") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { keyword = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空")
                        }
                    }
                    IconButton(onClick = {
                        keyboard?.hide()
                        doSearch(true)
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                doSearch(true)
            }),
        )

        LoadingBox(loading = loading && results.isEmpty())

        if (results.isEmpty() && !loading && searched) {
            EmptyHint("没有找到相关歌曲")
        } else if (results.isEmpty() && !searched && !loading) {
            EmptyHint("输入关键词开始搜索")
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results.size) { i ->
                    val song = results[i]
                    val currentSong by pm.currentSong.collectAsState()
                    SongRow(
                        song = song,
                        playing = currentSong?.key == song.key,
                        index = i,
                        onClick = { pm.playAll(results.toList(), i) },
                        onLongClick = { setActionSong(song) },
                    )
                }
                if (loading && results.isNotEmpty()) {
                    item { LoadingBox(true) }
                }
                if (!hasMore && results.isNotEmpty()) {
                    item { EmptyHint("没有更多了") }
                }
            }
        }
    }

    SongActionDialog(
        song = actionSong,
        onDismiss = { setActionSong(null) },
        onPlayNext = {
            pm.insertNext(it)
            pm.toast("已加入下一首播放")
        },
        onComments = onOpenComments,
        onDownload = { s ->
            scope.launch { pm.toast(Downloads.start(context, container.api, s)) }
        },
    )
}
