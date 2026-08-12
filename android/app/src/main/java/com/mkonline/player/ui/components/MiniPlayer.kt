package com.mkonline.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkonline.player.playback.PlayerManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/** 底部迷你播放条，点击进入全屏播放页。 */
@Composable
fun MiniPlayer(pm: PlayerManager, onOpenPlayer: () -> Unit) {
    val song by pm.currentSong.collectAsState()
    val playing by pm.isPlaying.collectAsState()
    val pic by pm.currentPic.collectAsState()
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(song?.key, playing) {
        while (isActive) {
            val dur = pm.durationMs()
            progress = if (dur > 0) (pm.positionMs().toFloat() / dur).coerceIn(0f, 1f) else 0f
            delay(if (playing) 500L else 1500L)
        }
    }

    if (song == null) return

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPlayer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(pic.ifEmpty { song!!.pic }, 42)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song!!.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        song!!.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { pm.toggle() }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放",
                    )
                }
                IconButton(onClick = { pm.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首")
                }
            }
        }
    }
}
