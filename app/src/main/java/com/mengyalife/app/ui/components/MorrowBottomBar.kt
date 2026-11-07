package com.mengyalife.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mengyalife.app.navigation.AppTab
import com.mengyalife.app.ui.theme.MorrowTheme

/**
 * 固定底部导航：今天 / 轨迹 / 日记 / 顾问。
 *
 * 视觉是暖白表面 + 1dp 极淡分割线；当前项使用浅绿柔和小底和深绿字，
 * 不使用 Material3 默认的大药丸指示器。
 */
@Composable
fun MorrowBottomBar(
    currentTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.card,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.hairline),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTab.entries.forEach { tab ->
                    BottomBarItem(
                        tab = tab,
                        selected = currentTab == tab,
                        onClick = { onTabChange(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val tint by animateColorAsState(
        targetValue = if (selected) colors.forest else colors.textTertiary,
        animationSpec = tween(durationMillis = 200),
        label = "navTint",
    )
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(tab.testTag)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (selected) colors.selectedNav else colors.card),
            contentAlignment = Alignment.Center,
        ) {
            MorrowGlyph(
                kind = tab.glyph,
                tint = tint,
                modifier = Modifier.size(19.dp),
                strokeWidth = if (selected) 1.7.dp else 1.4.dp,
            )
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
