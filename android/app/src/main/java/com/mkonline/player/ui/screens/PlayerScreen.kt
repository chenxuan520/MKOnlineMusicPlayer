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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkonline.player.AppContainer
import com.mkonline.player.data.PlayMode
import com.mkonline.player.data.Song
import com.mkonline.player.playback.LyricState
import com.mkonline.player.ui.components.CoverImage
import com.mkonline.player.util.Downloads
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 全屏播放页：封面 / 歌词 / 进度 / 控制 / 收藏 / 评论 / 下载 / 队列。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenComments: (Song) -> Unit,
) {
    val pm = container.playerManager
    val api = container.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val song by pm.currentSong.collectAsState()
    val pic by pm.currentPic.collectAsState()
    val playing by pm.isPlaying.collectAsState()
    val mode by pm.playMode.collectAsState()
    val favorite by pm.currentFavorite.collectAsState()
    val lyricState by pm.lyricState.collectAsState()

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(song?.key, playing) {
        while (isActive) {
            if (!dragging) {
                position = pm.positionMs()
                duration = pm.durationMs()
            }
            delay(if (playing) 250L else 800L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在播放", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val s = song
                    if (s != null) {
                        IconButton(onClick = { onOpenComments(s) }) {
                            Icon(Icons.Default.Comment, contentDescription = "评论")
                        }
                        IconButton(onClick = {
                            scope.launch { pm.toast(Downloads.start(context, api, s)) }
                        }) { Icon(Icons.Default.Download, contentDescription = "下载") }
                    }
                },
            )
        },
    ) { padding ->
        val s = song
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("未在播放", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 头部：封面 + 歌名
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(pic.ifEmpty { s.pic }, 84)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        s.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${s.artist} · ${s.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 歌词区
            LyricArea(
                lyricState = lyricState,
                positionMs = position,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.height(8.dp))

            // 进度条
            val dur = duration.coerceAtLeast(0L)
            val pos = if (dragging) (dragValue * dur).toLong() else position
            Slider(
                value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { v ->
                    dragging = true
                    dragValue = v
                },
                onValueChangeFinished = {
                    if (dur > 0) pm.seekTo((dragValue * dur).toLong())
                    dragging = false
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(pos), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtTime(dur), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // 主控制行
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { pm.cycleMode() }) {
                    Icon(
                        when (mode) {
                            PlayMode.ORDER -> Icons.Default.Repeat
                            PlayMode.RANDOM -> Icons.Default.Shuffle
                            PlayMode.SINGLE -> Icons.Default.RepeatOne
                        },
                        contentDescription = "播放模式",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { pm.prev() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(40.dp))
                }
                FilledIconButton(
                    onClick = { pm.toggle() },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { pm.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { pm.toggleFavorite() }) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 次级入口
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButtonWithIcon(
                    icon = Icons.Default.QueueMusic,
                    label = "队列",
                    onClick = onOpenQueue,
                )
                TextButtonWithIcon(
                    icon = Icons.Default.Comment,
                    label = "评论",
                    onClick = { onOpenComments(s) },
                )
                TextButtonWithIcon(
                    icon = Icons.Default.Download,
                    label = "下载",
                    onClick = {
                        scope.launch { pm.toast(Downloads.start(context, api, s)) }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LyricArea(
    lyricState: LyricState,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    when (lyricState) {
        LyricState.Idle, LyricState.Loading -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        LyricState.Empty -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is LyricState.Ready -> {
            val lines = lyricState.lines
            val activeIdx = remember(positionMs, lines) {
                lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
            }
            LaunchedEffect(activeIdx) {
                listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0))
            }
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lines.size) { i ->
                    val line = lines[i]
                    Text(
                        line.text,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        color = if (i == activeIdx) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (i == activeIdx) MaterialTheme.typography.bodyLarge
                        else MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextButtonWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

private fun fmtTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
