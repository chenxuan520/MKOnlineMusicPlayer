package com.mkonline.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mkonline.player.AppContainer
import com.mkonline.player.ui.components.EmptyHint
import com.mkonline.player.ui.components.SongRow

/** 正在播放队列：展示、切歌、移除、清空。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(container: AppContainer, onBack: () -> Unit) {
    val pm = container.playerManager
    val queue by pm.queueState.collectAsState()
    val current by pm.currentSong.collectAsState()
    val index by pm.currentIndex.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放队列 (${queue.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { pm.clearQueue() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (queue.isEmpty()) {
                EmptyHint("播放队列为空")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(queue.size) { i ->
                        val song = queue[i]
                        SongRow(
                            song = song,
                            playing = i == index,
                            index = i,
                            onClick = { pm.playAt(i) },
                            onLongClick = {},
                            trailing = {
                                IconButton(onClick = { pm.removeAt(i) }) {
                                    Icon(Icons.Default.Close, contentDescription = "移除")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
