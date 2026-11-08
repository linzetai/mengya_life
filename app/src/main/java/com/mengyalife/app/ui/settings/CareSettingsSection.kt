package com.mengyalife.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.care.CareNotification
import com.mengyalife.app.data.care.CareSettings
import com.mengyalife.app.ui.components.MorrowChoicePill
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.theme.MorrowTheme
import kotlinx.coroutines.launch

/** 设置页：主动关心总开关、App 外通知、主动程度、免打扰说明。 */
@Composable
fun CareSettingsSection() {
    val context = LocalContext.current
    val settings = remember { CareSettings(context.applicationContext) }
    val colors = MorrowTheme.colors
    var enabled by remember { mutableStateOf(settings.enabled) }
    var notifications by remember { mutableStateOf(settings.notificationsEnabled) }
    var level by remember { mutableStateOf(settings.level) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifications = granted
        settings.notificationsEnabled = granted
        if (!granted) {
            settings.notificationsEnabled = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SectionHeading(title = "主动关心")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "MengYa 会在有真实记录依据时，偶尔轻轻问一句；不会每天催。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("允许主动关心", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it; settings.enabled = it },
                modifier = Modifier.testTag("care_master_switch"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("App 外通知", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                Text(
                    text = "锁屏默认只显示“有件之前聊过的事”，不暴露计划或日记细节。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Switch(
                checked = notifications,
                onCheckedChange = { want ->
                    if (!want) {
                        notifications = false
                        settings.notificationsEnabled = false
                    } else if (Build.VERSION.SDK_INT < 33 || CareNotification.canPost(context)) {
                        notifications = true
                        settings.notificationsEnabled = true
                    } else {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.testTag("care_notification_switch"),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("主动程度", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MorrowChoicePill(
                text = "保守", selected = level == "CONSERVATIVE",
                onClick = { level = "CONSERVATIVE"; settings.level = level },
            )
            MorrowChoicePill(
                text = "正常", selected = level == "NORMAL",
                onClick = { level = "NORMAL"; settings.level = level },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "免打扰 22:00–08:00；同话题至少间隔 72 小时。可在关心卡上选择“不再提醒这个话题”。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(16.dp))
        CarePreferenceList()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CarePreferenceList() {
    val context = LocalContext.current
    val app = context.applicationContext as com.mengyalife.app.MengyaApplication
    val repository = remember { app.container.carePreferenceRepository }
    val preferences by repository.observeActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val colors = MorrowTheme.colors
    Text("已记住的关心偏好", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
    if (preferences.isEmpty()) {
        Text(
            "还没有。你可以在顾问对话里说“这周先别催我读书”或“更喜欢周日晚上回顾”。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    } else {
        preferences.forEach { pref ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${pref.topicLabel ?: "整体"} · ${
                            when (pref.value) {
                                "PAUSED" -> "本阶段暂停"
                                "SILENCED" -> "不再提醒"
                                "BUSY" -> "暂时少打扰"
                                "WELCOME" -> "欢迎这个关心"
                                "PREFER_WINDOW" -> pref.windowNote ?: "偏好时段"
                                else -> pref.value
                            }
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "来自" + if (pref.source == "USER_EXPLICIT") "你在聊天中的明确表达" else "你的反馈" +
                            (pref.sourceExcerpt?.take(24)?.let { "：$it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
                Text(
                    "恢复",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.forest,
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                if (pref.topicKey != null) repository.revokeByTopic(pref.topicKey)
                                else repository.revoke(pref.id)
                            }
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}
