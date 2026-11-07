# 萌芽人生 UI 规范（0.15.1）

本轮所有可复用 UI 尺寸、颜色和组件行为收敛到 `ui/theme` 与 `ui/components`。页面不再各自定义相近的一组数字。

## 1. 图标

- 逻辑画布：24 × 24；默认视觉尺寸 20dp（导航、按钮、卡片图标统一），次级箭头 16dp。
- 描边：2.1 逻辑单位，圆头线帽、圆角拐角；不混用填充图标、Emoji 或汉字占位。
- 实现：`ui/components/Common.kt` 的 `MorrowIcon` / `MorrowIconKind`，统一 Canvas 绘制；不引入 `material-icons-extended`。
- 语义：
  - 今天 `HOME`、轨迹 `TREND`、日记 `BOOK`、顾问 `SPARKLE`
  - 财富 `WALLET`、职业 `BRIEFCASE`、生活 `LEAF`、健康 `HEART`、学习 `BOOK`
  - 探索 `COMPASS`、写日记 `PENCIL`、讨论决定 `BRANCH`
  - 返回 `ARROW_LEFT`、详情/进入 `ARROW_RIGHT`、展开 `CHEVRON_DOWN/UP`、更多 `MORE`、发送 `SEND`
- 颜色（`MorrowIconColors`）：
  - Default：`textSecondary`（浅色 `#5B6C61`，对比度 5.18:1）
  - Active：`forest`（`#345E4A`）
  - Muted：`textTertiary`（`#627368`，对比度 4.67:1）
  - Disabled：`textDisabled`（`#9BA59A`，仅禁用态）
  - OnDark：`insightText`（`#F9FBF7`）

## 2. 按钮

| 类型 | 高度 | 圆角 | 视觉 | 用途 |
|---|---:|---:|---|---|
| Primary | 52dp | 14dp | 森林绿填充、白字 | 页面唯一主行动 |
| Secondary | 50dp | 14dp | 纸色/描边、主文字色 | 重要但非首要行动 |
| Tertiary | ≥48dp 触控 | 10dp | 无卡片底、森林绿字，可带 16dp 箭头 | 详情、取消、修改、重来 |

- 同一页面只有一个 Primary；主次并列时 Primary 必须为填充，Secondary 为描边。
- Primary 支持 loading（16dp 进度环 + 文案）、disabled、pressed（`primaryPressed`）。
- Secondary/ Tertiary 都有 disabled 和按压反馈；所有触摸区不小于 48dp。
- 保险：日记编辑器、目标底栏、探索结果、决定结果都不再出现两个同级描边大按钮。

## 3. 间距与圆角

| Token | 值 |
|---|---:|
| 页面水平边距 | 22dp |
| 区块间距 | 26dp |
| 卡片内边距 | 20dp |
| 卡间距 | 8–12dp |
| 白卡圆角 | 18dp |
| 洞察卡圆角 | 22dp |
| 目标行圆角 | 16–17dp |
| 按钮间距 | 8–10dp |
| 最小触控 | 48dp |

## 4. 配色与对比度

浅色：

- Canvas `#F6F7F1`，Card `#FFFEFC`，Nested `#EDF2EA`
- 主文字 `#28372E`；正文次级 `#5B6C61`（5.18:1）；说明 `#627368`（4.67:1）
- 品牌绿 `#345E4A`；按压 `#274A3A`；洞察卡底 `#345E4A`
- 说明文字不再使用 `#7B857B`（3.56:1 不达标）
- 真正禁用态 `#9BA59A` 仅用于 disabled，不用于正常说明

深色：

- Canvas `#18221D`，Card `#202C25`，Nested `#28362D`
- 主文字 `#EEF3E9`，次级 `#B8C6B9`，说明 `#9BA59A`
- 深绿主行动 `#446E58`，用户气泡和 Primary 统一使用它保证白字可读
- 系统栏图标由 App 当前主题决定（`MengyaLifeTheme` 中显式设置 light/dark appearance）

## 5. 状态

- 选中：底部导航浅绿软底 + 深绿图标；筛选药丸森林绿填充白字。
- 禁用：容器 `nested`，文字/图标 `textDisabled`；不通过降低正常说明文字对比度来“看起来禁用”。
- 空态：居中、衬线标题 + 一行说明 + 最多三个可执行建议；不留下无意义的大空白。
- 错误：真实错误文案 + 明确重试入口；不用假成功 Toast。
- 分隔线：1dp `hairline`；区块优先用间距，不靠大量分割线。
