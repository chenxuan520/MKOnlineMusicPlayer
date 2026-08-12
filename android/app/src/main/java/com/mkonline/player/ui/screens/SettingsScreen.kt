package com.mkonline.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mkonline.player.AppContainer

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.prefs.settings.collectAsState()
    val pm = container.playerManager

    var recommendDomain by remember(settings.recommendDomain) {
        mutableStateOf(settings.recommendDomain)
    }
    var recommendToken by remember(settings.recommendToken) {
        mutableStateOf(settings.recommendToken)
    }
    var recommendFavCount by remember(settings.recommendFavCount) {
        mutableStateOf(settings.recommendFavCount.toString())
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 服务器
        SectionTitle("服务器")
        Text(
            "当前地址：${settings.serverUrl.ifEmpty { "未配置" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                // 置为未认证，AppNav 会自动回到登录页
                container.prefs.update { it.copy(authenticated = false) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("更换服务器 / 重新登录") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // 外观
        SectionTitle("外观")
        ThemeModeRow(settings = settings, onUpdate = { mode ->
            container.prefs.update { it.copy(themeMode = mode) }
        })

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // 播放
        SectionTitle("播放")
        SwitchRow(
            label = "VIP 过滤（搜索与推荐）",
            checked = settings.filterVip,
            onCheckedChange = { v -> container.prefs.update { it.copy(filterVip = v) } },
        )
        SwitchRow(
            label = "评论页只看热门",
            checked = settings.hotCommentsOnly,
            onCheckedChange = { v -> container.prefs.update { it.copy(hotCommentsOnly = v) } },
        )
        OutlinedButton(
            onClick = { pm.clearQueue(); pm.toast("已清空播放队列") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("清空播放队列") }
        OutlinedButton(
            onClick = {
                container.prefs.clearHistory()
                pm.toast("已清空播放历史")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("清空播放历史") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // 智能推荐
        SectionTitle("智能推荐")
        OutlinedTextField(
            value = recommendDomain,
            onValueChange = { recommendDomain = it },
            label = { Text("推荐服务域名（可选，留空用服务器地址）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = recommendToken,
            onValueChange = { recommendToken = it },
            label = { Text("推荐服务 Token（必填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = recommendFavCount,
            onValueChange = { recommendFavCount = it.filter { c -> c.isDigit() } },
            label = { Text("参考收藏数量") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val count = recommendFavCount.toIntOrNull()?.coerceIn(1, 100) ?: 20
                container.prefs.update {
                    it.copy(
                        recommendDomain = recommendDomain.trim(),
                        recommendToken = recommendToken.trim(),
                        recommendFavCount = count,
                    )
                }
                pm.toast("已保存")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存推荐设置") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // 关于
        SectionTitle("关于")
        Text("MKOnline 在线音乐 · Android v1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "后端接口：api.php（types=search/url/pic/lyric/playlist/comments/collections/download/auth）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeModeRow(
    settings: com.mkonline.player.data.Settings,
    onUpdate: (com.mkonline.player.data.ThemeMode) -> Unit,
) {
    val modes = listOf(
        com.mkonline.player.data.ThemeMode.SYSTEM to "跟随系统",
        com.mkonline.player.data.ThemeMode.LIGHT to "浅色",
        com.mkonline.player.data.ThemeMode.DARK to "深色",
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { (mode, label) ->
            androidx.compose.material3.FilterChip(
                selected = settings.themeMode == mode,
                onClick = { onUpdate(mode) },
                label = { Text(label) },
            )
        }
    }
}
