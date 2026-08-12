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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mkonline.player.AppContainer
import com.mkonline.player.data.CommentItem
import com.mkonline.player.ui.components.EmptyHint
import com.mkonline.player.ui.components.LoadingBox
import kotlinx.coroutines.launch

/** 歌曲评论页：对齐 Web 端 hot_comment / comment 两段。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    container: AppContainer,
    source: String,
    id: String,
    title: String,
    onBack: () -> Unit,
) {
    val api = container.api
    val settings by container.prefs.settings.collectAsState()
    val scope = rememberCoroutineScope()

    val hot = remember { mutableStateListOf<CommentItem>() }
    val normal = remember { mutableStateListOf<CommentItem>() }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var hotOnly by remember { mutableStateOf(settings.hotCommentsOnly) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        if (reset) refreshing = true
        error = null
        if (reset) {
            page = 1
            hasMore = true
            hot.clear()
            normal.clear()
        }
        scope.launch {
            try {
                val (h, n) = api.comments(id = id, source = source, page = page, count = 50)
                if (page == 1) hot.addAll(h)
                normal.addAll(n)
                hasMore = n.size >= 50
                page += 1
            } catch (e: Exception) {
                error = e.message ?: "评论加载失败"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { load(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifEmpty { "评论" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    FilterChip(
                        selected = hotOnly,
                        onClick = { hotOnly = !hotOnly },
                        label = { Text("只看热门") },
                    )
                },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()

        // 滚到底部自动加载下一页
        val shouldLoadMore by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                hasMore && !loading && normal.isNotEmpty() &&
                    last >= (hot.size + normal.size + 1) - 2
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) load(false)
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading && hot.isEmpty() && normal.isEmpty()) {
                LoadingBox(true)
            } else if (error != null && hot.isEmpty() && normal.isEmpty()) {
                EmptyHint(error!!)
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { load(true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    if (hot.isNotEmpty()) {
                        item { SectionHeader("热门评论") }
                        items(hot) { CommentRow(it) }
                        item { HorizontalDivider() }
                    }
                    if (!hotOnly) {
                        item { SectionHeader("全部评论") }
                        items(normal) { CommentRow(it) }
                        if (loading) {
                            item { LoadingBox(true) }
                        }
                        if (!hasMore && normal.isNotEmpty()) {
                            item { EmptyHint("没有更多了") }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun CommentRow(item: CommentItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (item.avatar.isNotEmpty()) {
            AsyncImage(
                model = item.avatar,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Box(
                Modifier.size(36.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.userName.firstOrNull()?.toString() ?: "·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                item.userName.ifEmpty { "匿名用户" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(item.content, style = MaterialTheme.typography.bodyMedium)
            if (item.time.isNotEmpty()) {
                Text(item.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
