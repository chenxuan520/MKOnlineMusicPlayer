package com.mkonline.player.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mkonline.player.AppContainer
import com.mkonline.player.data.Song
import com.mkonline.player.ui.components.MiniPlayer
import com.mkonline.player.ui.screens.AuthScreen
import com.mkonline.player.ui.screens.CollectionsScreen
import com.mkonline.player.ui.screens.CommentsScreen
import com.mkonline.player.ui.screens.HistoryScreen
import com.mkonline.player.ui.screens.PlayerScreen
import com.mkonline.player.ui.screens.QueueScreen
import com.mkonline.player.ui.screens.RecommendScreen
import com.mkonline.player.ui.screens.SearchScreen
import com.mkonline.player.ui.screens.SettingsScreen
import com.mkonline.player.ui.screens.SheetDetailScreen
import com.mkonline.player.ui.screens.SheetsScreen
import com.mkonline.player.ui.screens.clearSearchCache
import com.mkonline.player.ui.screens.clearSheetCache

private data class TabItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    TabItem("搜索", Icons.Default.Search),
    TabItem("歌单", Icons.Default.LibraryMusic),
    TabItem("收藏", Icons.Default.Favorite),
    TabItem("历史", Icons.Default.History),
    TabItem("设置", Icons.Default.Settings),
)

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val settings by container.prefs.settings.collectAsState()
    val pm = container.playerManager

    // 全局轻提示：播放错误、收藏结果、下载进度等
    LaunchedEffect(Unit) {
        pm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 认证状态失效（登出 / 改服务器）时回到登录页，并清理进程级缓存避免跨会话泄漏
    LaunchedEffect(settings.authenticated, settings.serverUrl) {
        val authed = settings.authenticated && settings.serverUrl.isNotBlank()
        if (!authed && nav.currentDestination?.route != "auth") {
            clearSearchCache()
            clearSheetCache()
            container.playerManager.clearQueue()
            nav.navigate("auth") { popUpTo(0) { inclusive = true } }
        }
    }

    val start = if (settings.authenticated && settings.serverUrl.isNotBlank()) "home" else "auth"

    NavHost(navController = nav, startDestination = start) {
        composable("auth") {
            AuthScreen(container = container) {
                nav.navigate("home") { popUpTo("auth") { inclusive = true } }
            }
        }
        composable("home") {
            HomeScreen(
                container = container,
                onOpenPlayer = { nav.navigate("player") },
                onOpenSheet = { id -> nav.navigate("sheet/$id") },
                onOpenRecommend = { nav.navigate("recommend") },
                onOpenComments = { song ->
                    val title = "${song.name} - ${song.artist}"
                    nav.navigate("comments/${song.source}/${song.id}?name=${Uri.encode(title)}")
                },
            )
        }
        composable("player") {
            PlayerScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenQueue = { nav.navigate("queue") },
                onOpenComments = { song ->
                    val title = "${song.name} - ${song.artist}"
                    nav.navigate("comments/${song.source}/${song.id}?name=${Uri.encode(title)}")
                },
            )
        }
        composable("queue") {
            QueueScreen(container = container, onBack = { nav.popBackStack() })
        }
        composable("sheet/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SheetDetailScreen(container = container, id = id, onBack = { nav.popBackStack() })
        }
        composable(
            route = "comments/{source}/{id}?name={name}",
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val source = entry.arguments?.getString("source").orEmpty()
            val id = entry.arguments?.getString("id").orEmpty()
            val name = entry.arguments?.getString("name").orEmpty()
            CommentsScreen(
                container = container,
                source = source,
                id = id,
                title = name,
                onBack = { nav.popBackStack() },
            )
        }
        composable("recommend") {
            RecommendScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun HomeScreen(
    container: AppContainer,
    onOpenPlayer: () -> Unit,
    onOpenSheet: (String) -> Unit,
    onOpenRecommend: () -> Unit,
    onOpenComments: (Song) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pm = container.playerManager

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(pm = pm, onOpenPlayer = onOpenPlayer)
                NavigationBar {
                    tabs.forEachIndexed { i, item ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> SearchScreen(container = container, onOpenComments = onOpenComments)
                1 -> SheetsScreen(
                    container = container,
                    onOpenSheet = onOpenSheet,
                    onOpenRecommend = onOpenRecommend,
                )
                2 -> CollectionsScreen(container = container, onOpenComments = onOpenComments)
                3 -> HistoryScreen(container = container, onOpenComments = onOpenComments)
                4 -> SettingsScreen(container = container)
            }
        }
    }
}
