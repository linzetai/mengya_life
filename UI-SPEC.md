# 萌芽人生 · Morrow UI 规范（0.15.2）

本轮只收敛三套字体角色、顶栏、图标和按钮；不引入新的设计语言。

## 1. 字体体系

全应用只保留三种字体角色，页面不得自写 `FontFamily`、`fontSize` 或 `fontWeight`。

| 角色 | 字体 | 字号 / 行高 | 字重 | 用途 |
|---|---|---|---|---|
| Brand | 中文衬线（Serif） | 26sp / 32sp | Medium | 仅 `MengYa` |
| Display | 中文衬线（Serif） | 30sp / 40sp | Regular | 页面大标题 |
| Display Small | 中文衬线（Serif） | 26sp / 35sp | Regular | 发现卡叙述、详情页大标题 |
| Section | 中文无衬线（Sans） | 18sp / 26sp | SemiBold | 章节标题 |
| Card title | 中文无衬线 | 16sp / 24sp | Medium | 卡片标题 |
| Body | 中文无衬线 | 14sp / 22sp | Regular | 正文 |
| Secondary | 中文无衬线 | 13sp / 20sp | Regular | 次级说明 |
| Button | 中文无衬线 | 15sp / 22sp | Medium | 按钮 |
| Label / date | 中文无衬线 | 12sp / 18sp | Medium/Regular | 标签、时间、eyebrow |
| Date number | 中文无衬线 | 20sp / 26sp | Medium | 日记日期数字 |

实现位置：`ui/theme/Type.kt` 的 `MorrowType` 与 `MengyaTypography`。页面只通过 `MorrowType` 或 `MaterialTheme.typography` 取样式。

## 2. 顶栏

- 高度 56dp；左右内容边距 22dp；状态栏 Insets 由页面/Scaffold 处理，不用固定空白撑高。
- 今天、轨迹、日记、顾问四个主页面用 sticky header，滚动后品牌/页面名与设置仍固定可见。
- 左侧：首页为 `MengYa`（Brand 26/32 衬线 Medium）；其他主页面为页面名（Section 18/26）。
- 右侧：仅 QA 显示「演示数据」标签，随后是纯文字「设置」。
- 「设置」14sp/20sp Medium，森林绿，无背景、无图标、无头像；触控区 48×48dp。
- 演示标签与设置视觉间距约 12–16dp；字号放大时自动放宽，不能重叠。
- 不放头像、齿轮、更多菜单或任何装饰图标。详情页不重复设置入口。

## 3. 图标

- 逻辑画布 24×24；默认 20dp；箭头 16dp；描边 2.1 逻辑单位，圆头线帽/圆角拐角。
- 统一使用 `MorrowIcon` / `MorrowIconKind`，不混用填充图标、Emoji、汉字占位或 Material 占位图标。
- 语义：HOME / TREND / BOOK / SPARKLE；WALLET / BRIEFCASE / LEAF / HEART / COMPASS / PENCIL / BRANCH；ARROW_LEFT / ARROW_RIGHT / CHEVRON_DOWN / CHEVRON_UP / MORE / PLUS / SEND。
- 颜色：Default `textSecondary`，Active `forest`，Muted `textTertiary`，Disabled `textDisabled`，OnDark `insightText`。

## 4. 按钮

| 类型 | 高度 | 圆角 | 视觉 | 用途 |
|---|---:|---:|---|---|
| Primary | 52dp | 14dp | 森林绿填充、白字 | 页面唯一主行动 |
| Secondary | 50dp | 14dp | 纸色/描边 | 次要行动 |
| Tertiary | 48dp 触控 | 10dp | 无背景文字 + 可选 16dp 箭头 | 返回、取消、重来、更多 |

- 同一页面只保留一个 Primary；加载 / 禁用 / 按压状态完整。
- 结果页（探索 / 决定）使用固定底部操作区：Primary、Secondary、Tertiary 完整可见，正文可滚动，操作区不遮住最终内容。
- 所有触控区不小于 48dp。

## 5. 间距、颜色与对比度

- 页面水平边距 22dp；区块间距 26dp；卡片内边距 20dp；卡间距 8–12dp。
- 白卡圆角 18dp；洞察卡 22dp；按钮 14dp；输入框 14dp。
- 浅色：Canvas `#F6F7F1`，Card `#FFFEFC`，Nested `#EDF2EA`；主文字 `#28372E`；次级 `#5B6C61`（5.18:1）；说明 `#627368`（4.67:1）；品牌绿 `#345E4A`。
- 深色：Canvas `#18221D`，Card `#202C25`，Nested `#28362D`；主文字 `#EEF3E9`；次级 `#B8C6B9`；说明 `#9BA59A`；Primary/用户气泡 `#446E58`。
- `#9BA59A` 只用于禁用态，不再拿低对比灰当普通说明。

## 6. 状态

- 选中：森林绿填充/柔绿底；禁用：`nested` + `textDisabled`。
- 空态：居中标题、说明、最多三个可执行建议。
- 错误：真实错误文案 + 明确重试文字按钮。
- 表单键盘：操作栏固定在键盘上方，字段区独立滚动。
