package com.mkonline.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mkonline.player.AppContainer
import kotlinx.coroutines.launch

/**
 * 首次启动 / 登出后进入：配置服务器地址并验证入口密码。
 * 与 Web 端 `api.php?types=auth` 一致，密码写死在服务端 [api.php] 的 MKPLAYER_PASSWORD。
 */
@Composable
fun AuthScreen(container: AppContainer, onSuccess: () -> Unit) {
    val settings by container.prefs.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var server by remember { mutableStateOf(settings.serverUrl) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // 服务器地址后续在设置页修改时同步回这里
    LaunchedEffect(settings.serverUrl) {
        if (server.isEmpty()) server = settings.serverUrl
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "MKOnline 在线音乐",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = server,
            onValueChange = { server = it; error = null },
            label = { Text("服务器地址") },
            placeholder = { Text("http://192.168.1.2:4000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("访问密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (server.isBlank()) { error = "请填写服务器地址"; return@Button }
                if (password.isBlank()) { error = "请填写密码"; return@Button }
                loading = true
                error = null
                scope.launch {
                    // 先保存服务器地址，Api 才能正确拼出 endpoint
                    container.prefs.update { it.copy(serverUrl = server.trim(), authenticated = false) }
                    try {
                        val (ok, msg) = container.api.auth(password.trim())
                        loading = false
                        if (ok) {
                            container.prefs.update { it.copy(authenticated = true) }
                            onSuccess()
                        } else {
                            error = msg.ifEmpty { "密码错误" }
                        }
                    } catch (e: Exception) {
                        loading = false
                        error = e.message ?: "服务器不可达"
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("登录")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "服务器地址填部署 api.php 的站点（含端口/子目录均可）。\n" +
                "默认密码见服务端 api.php 中的 MKPLAYER_PASSWORD。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
