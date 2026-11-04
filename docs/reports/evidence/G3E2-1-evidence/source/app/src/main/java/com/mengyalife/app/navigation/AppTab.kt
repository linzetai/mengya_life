package com.mengyalife.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 一级页面（底部导航的 5 个目的地）。
 *
 * 设置页刻意**不**出现在这里：它是二级页面，由 [com.mengyalife.app.MengyaLifeApp]
 * 用独立的布尔状态控制，因此不占用底部导航位置。
 *
 * [testTag] 会映射为 Android 的 resource-id（见 MengyaLifeApp 的
 * `testTagsAsResourceId`），便于 UI 自动化按标签定位，避免依赖固定坐标。
 */
enum class AppTab(
    val label: String,
    val testTag: String,
    val icon: ImageVector,
    val description: String,
) {
    OVERVIEW(
        label = "总览",
        testTag = "tab_overview",
        icon = Icons.Filled.Home,
        description = "人生方向与近期洞察",
    ),
    GOALS(
        label = "目标",
        testTag = "tab_goals",
        icon = Icons.Filled.Star,
        description = "长期 / 阶段 / 短期目标",
    ),
    TRENDS(
        label = "趋势",
        testTag = "tab_trends",
        icon = Icons.Filled.DateRange,
        description = "时间序列与多维比较",
    ),
    DIARY(
        label = "日记",
        testTag = "tab_diary",
        icon = Icons.Filled.Edit,
        description = "记录与回顾",
    ),
    ADVISOR(
        label = "顾问",
        testTag = "tab_advisor",
        icon = Icons.Filled.Person,
        description = "AI 顾问入口",
    ),
}
