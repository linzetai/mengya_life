package com.mengyalife.app.navigation

import com.mengyalife.app.ui.components.MorrowGlyphKind

/**
 * 一级页面：与原版一致的四 Tab。
 *
 * 「人生全景」和设置都是二级页面，不占底部导航位置：
 * - 目标树由首页「查看全景」进入；
 * - 设置由每个页面右上角的头像入口进入。
 */
enum class AppTab(
    val label: String,
    val testTag: String,
    val glyph: MorrowGlyphKind,
    val description: String,
) {
    TODAY(
        label = "今天",
        testTag = "tab_today",
        glyph = MorrowGlyphKind.HOME,
        description = "今天的方向、洞察与记录入口",
    ),
    PATH(
        label = "轨迹",
        testTag = "tab_path",
        glyph = MorrowGlyphKind.CHART,
        description = "真实记录里的长期变化",
    ),
    DIARY(
        label = "日记",
        testTag = "tab_diary",
        glyph = MorrowGlyphKind.BOOK,
        description = "写下今天，留给未来的自己",
    ),
    ADVISOR(
        label = "顾问",
        testTag = "tab_advisor",
        glyph = MorrowGlyphKind.SPARKLE,
        description = "带着你的方向，慢慢想清楚",
    ),
}
