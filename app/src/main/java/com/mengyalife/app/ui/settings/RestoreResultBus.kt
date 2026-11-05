package com.mengyalife.app.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.platform.LocalContext
import com.mengyalife.app.MengyaApplication

/** 跨设置页/维护页的恢复成功提示，避免页面重建丢失。 */
object RestoreResultBus {
    val message = MutableStateFlow<String?>(null)

    fun success(text: String) { message.value = text }
    fun consume() { message.value = null }
}

@Composable
fun RestoreResultHost() {
    val context = LocalContext.current
    val app = context.applicationContext as MengyaApplication
    val coordinatorMessage by app.container.restoreCoordinator.completionMessage.collectAsStateWithLifecycle()
    val text by RestoreResultBus.message.collectAsStateWithLifecycle()
    val current = coordinatorMessage ?: text ?: return
    AlertDialog(
        modifier = Modifier.testTag("restore_success_global"),
        onDismissRequest = {
            RestoreResultBus.consume()
            app.container.restoreCoordinator.clearCompletionMessage()
        },
        title = { Text("恢复完成") },
        text = {
            Text(
                "$current\n\nAPI Key 与 AI 授权不会恢复，请重新配置并重新授权。历史 weekly AI 未写回，旧自动发送已关闭。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                RestoreResultBus.consume()
                app.container.restoreCoordinator.clearCompletionMessage()
            }) { Text("知道了") }
        },
    )
}
