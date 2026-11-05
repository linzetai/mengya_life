package com.mengyalife.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Gate 活跃时的 fail-closed 页面：不提供业务写入入口。 */
@Composable
fun RestoreMaintenanceScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("restore_maintenance"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("正在完成安全恢复", style = MaterialTheme.typography.headlineSmall)
        Text(
            "数据恢复或安全重置尚未收敛。为避免产生不一致，暂不能使用应用；请保持应用打开，重启后会继续完成。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
