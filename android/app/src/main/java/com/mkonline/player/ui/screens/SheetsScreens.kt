package com.mkonline.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mkonline.player.AppContainer
import com.mkonline.player.data.Sheet
import com.mkonline.player.data.SheetIds
import com.mkonline.player.data.SheetMeta
import com.mkonline.player.data.Song
import com.mkonline.player.ui.components.CoverImage
import com.mkonline.player.ui.components.EmptyHint
import com.mkonline.player.ui.components.LoadingBox
import com.mkonline.player.ui.components.SongActionDialog
import com.mkonline.player.ui.components.SongRow
import com.mkonline.player.ui.components.rememberSongActions
import com.mkonline.player.util.Downloads
import kotlinx.coroutines.launch

/** 歌单详情完整缓存（含歌曲列表，进程级，不持久化）。 */
private object FullSheetCache {
    private val map = java.util.Collections.synchronizedMap(LinkedHashMap<String, Sheet>())
    fun put(sheet: Sheet) { map[sheet.id] = sheet }
    fun get(id: String): Sheet? = map[id]
    fun clear() = map.clear()
}

/** 登出/切换服务器时清理歌单缓存（元数据 + 详情）。 */
fun clearSheetCache() {
    com.mkonline.player.data.SheetCache.clear()
    FullSheetCache.clear()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetsScreen(
    container: AppContainer,
    onOpenSheet: (String) -> Unit,
    onOpenRecommend: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("歌单") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 智能推荐入口卡片
            item {
                RecommendCard(onClick = onOpenRecommend)
            }
            gridItems(SheetIds.all) { meta ->
                SheetCard(api = container.api, meta = meta, onClick = { onOpenSheet(meta.id) })
            }
        }
    }
}

@Composable
private fun RecommendCard(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    "智能推荐",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "根据收藏生成",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            "智能推荐",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SheetCard(
    api: com.mkonline.player.data.Api,
    meta: SheetMeta,
    onClick: () -> Unit,
) {
    // 懒加载：卡片进入可见区时才拉取歌单封面（完整 playlist API），加缓存避免滚动回来看重复请求
    val cached = com.mkonline.player.data.SheetCache.get(meta.id)
    var name by remember(meta.id) { mutableStateOf(cached?.name ?: meta.name) }
    var cover by remember(meta.id) { mutableStateOf(cached?.cover ?: "") }
    var loaded by remember(meta.id) { mutableStateOf(cached != null) }

    LaunchedEffect(meta.id) {
        if (loaded) return@LaunchedEffect
        try {
            val sheet = api.playlist(meta.id)
            com.mkonline.player.data.SheetCache.put(sheet.id, sheet.name, sheet.cover)
            name = sheet.name
            cover = sheet.cover
        } catch (_: Exception) {
            // 加载失败静默，保持占位图标
        } finally {
            loaded = true
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover.isNotEmpty()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (loaded) {
                // 加载过但没封面（或服务端没返回封面）
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // loaded == false 时 Box 保持空背景，避免闪烁
        }
        Text(
            name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetDetailScreen(
    container: AppContainer,
    id: String,
    onBack: () -> Unit,
) {
    val api = container.api
    val pm = container.playerManager
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember(id) { mutableStateOf(FullSheetCache.get(id)) }
    var loading by remember(id) { mutableStateOf(sheet == null) }
    var refreshing by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val (actionSong, setActionSong) = rememberSongActions()

    fun reload(force: Boolean) {
        if (force) refreshing = true else loading = true
        error = null
        scope.launch {
            runCatching { api.playlist(id) }
                .onSuccess {
                    FullSheetCache.put(it)
                    sheet = it
                }
                .onFailure { error = it.message ?: "无法获取歌单信息" }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(id) {
        if (sheet != null) return@LaunchedEffect
        reload(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sheet?.name ?: "歌单", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val s = sheet
                    if (s != null && s.songs.isNotEmpty()) {
                        IconButton(onClick = { pm.playAll(s.songs, 0) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "播放全部")
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { reload(true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                loading -> LoadingBox(true)
                error != null -> EmptyHint(error!!)
                sheet == null -> EmptyHint("歌单为空")
                else -> SheetDetailContent(
                    sheet = sheet!!,
                    pm = pm,
                    onLongClick = setActionSong,
                )
            }
        }
    }

    SongActionDialog(
        song = actionSong,
        onDismiss = { setActionSong(null) },
        onPlayNext = { pm.insertNext(it); pm.toast("已加入下一首播放") },
        onComments = { /* 歌单详情不直接进评论，留给播放页 */ },
        onDownload = { s ->
            scope.launch { pm.toast(Downloads.start(context, container.api, s)) }
        },
    )
}

@Composable
private fun SheetDetailContent(
    sheet: Sheet,
    pm: com.mkonline.player.playback.PlayerManager,
    onLongClick: (Song) -> Unit,
) {
    val current by pm.currentSong.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(sheet.cover, 96)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(sheet.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (sheet.creatorName.isNotEmpty()) {
                        Text(
                            "by ${sheet.creatorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { if (sheet.songs.isNotEmpty()) pm.playAll(sheet.songs, 0) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("播放全部") }
                }
            }
        }
        items(sheet.songs.size) { i ->
            val song = sheet.songs[i]
            SongRow(
                song = song,
                playing = current?.key == song.key,
                index = i,
                onClick = { pm.playAll(sheet.songs, i) },
                onLongClick = { onLongClick(song) },
            )
        }
    }
}
