# 萌芽人生 0.15.2 · UI 精修与品牌统一

日期：2026-11-07  
版本：`0.15.2-morrow` / versionCode 18  
分支：`master`（单工作树）  
基线：0.15.1 `b3cb5a6`

## 1. 品牌与顶栏

- 首页品牌从「萌芽.」精确替换为 **MengYa**：26sp / 32sp、中文衬线 Medium、深绿主文字；无 Logo、无图标、无中文副标题、无句号。
- 品牌与正文共用 22dp 左边界；顶栏高度 56dp；日期与顶栏间距从 20dp 收到 16dp。
- 右侧改为「演示数据」（仅 QA）+ 纯文字「设置」：14sp / 20sp Medium、森林绿、无背景、无齿轮、无头像；48dp 触控区，约 12–16dp 视觉间距。
- **四个主页面顶栏改为 sticky header**：今天、轨迹、日记、顾问在滚动后仍能看到顶部品牌/页面名和设置入口；详情页不重复设置入口。
- 顶栏在 1.3 倍字体下自动放宽演示标签与设置的间距，不再重叠；品牌不额外缩进。
- 删除旧的 `AppTopBar` 齿轮按钮和 `SettingsAvatar` 头像组件，代码中不再保留头像/齿轮占位。

## 2. 设置入口恢复（功能回退修复）

### 追溯

- `ui/screens/SettingsScreen.kt` 仍存在，未删除；原有 AI、代理、隐私、每周回顾、备份、恢复、关于等分组都在。
- 导航仍由 `MengyaLifeApp` 的 `showSettings` 状态控制，没有改路由。
- 上一次 UI 改版把顶栏入口收敛成右上角圆形「芽」头像；头像本身是设置入口（`btn_settings`），但本轮要求移除头像，因此必须先恢复文字入口。

### 修复

- 在 `MorrowTopBar` 中新增纯文字「设置」，继续使用同一个 `onSettingsClick` 和 `btn_settings` testTag。
- 设置页自己的顶栏也换成统一 `MorrowTopBar`，移除默认 Material `TopAppBar`。
- 未新建任何设置页面，原有设置项、AI Key 字段、备份入口和返回逻辑保持不变。

### 验证

在真机上分别从今天、轨迹、日记、顾问四个主页面滚动后点击「设置」：

| 页面 | 打开设置 | 返回原页面 |
|---|---|---|
| 今天 | 通过 | 通过 |
| 轨迹 | 通过 | 通过 |
| 日记 | 通过 | 通过 |
| 顾问 | 通过 | 通过 |

- 深浅色切换正常，返回后页面状态保留；无死链/空白页；无崩溃日志。
- 设置页 AI 字段改为 14dp 圆角、统一标签色；保存为 Primary、测试连接为 Secondary、删除密钥为 Tertiary + 箭头。

## 3. 全局字体体系

全应用只维护三种角色（见 `UI-SPEC.md`）：

- **Brand**：中文衬线 26/32 Medium，仅用于 `MengYa`。
- **Display**：中文衬线 30/40 Regular（页面大标题）；发现卡 26/35；详情页 26/35。
- **UI**：中文无衬线；章节 18/26 SemiBold、卡片 16/24 Medium、正文 14/22、次级 13/20、按钮 15/22、标签/时间 12/18、日记日期数字 20/26。

页面不再手写 `FontFamily` / `fontSize` / `fontWeight` / `lineHeight`；旧页面的临时 `MorrowType.Brand.copy(...)` 已移除。Material Typography 也映射到同一套值。

### 本次字体修正

- 首页方向卡标题去掉重复的「财富 · / 职业 · / 生活 ·」前缀，领域由左侧图标表达；1.3 倍字体下「职业 · 找到下一阶段的发展方向」「生活 · 建立可持续的生活节奏」不再把最后一个字孤立到第二行。
- 发现卡文案改为「你又留下了一次 / 真实的进展。」，具体目标放到来源行，消除单独一行「上，」。
- 日期、状态标签、来源、时间戳统一 12/18；正文 14/22；不再出现同级标题宋黑混用。

## 4. 页面布局修正

- **今天**：顶栏固定；方向卡等高、无空进度线；三个快捷入口固定 110dp 高、图标/标题基线一致；1.3 倍字体验证无裁切。
- **人生全景**：展开操作从底栏式白卡改为内联文字「记录进展 / 编辑 / ...」，不再像底部导航；子目标缩进和展开箭头保持。
- **顾问**：空态居中、三个建议入口；消息态保留引用、时间和草案入口；输入区与底部安全区贴合。
- **探索 / 决定结果**：统一为固定底部操作区，正文独立滚动；Primary / Secondary / Tertiary 三层完整可见，不再把按钮堆在中部或让底部空白。
- **GoalSheets**：标题 26/35 衬线、字段 14dp 圆角、领域/状态药丸、底部 Primary 保存 + Tertiary 取消；保存/取消固定在字段滚动区外，键盘弹出时不遮挡。
- **周回顾**：保持单主卡结构；标题 30、章节 18、正文 14/13，长内容可滚动，底部操作完整。
- **设置页**：去除默认 Material AppBar 和默认描边输入框；AI 服务区域改为统一卡片/字段/按钮；QA 提示收缩为「QA 隔离构建」一行，不再把包名/数据库名堆在主界面。

## 5. 验证

- JVM：**303 个测试，0 失败**。
- 构建：`assembleDebug` / `assembleQa` 成功。
- 真机：Xiaomi 2509FPN0BC，Android 17，1200×2608 @480dpi。
- 设置入口：四页面滚动后 4/4 打开并返回。
- GoalSheets：编辑表单、领域/状态选择、键盘弹出、保存/取消可见。
- 浅色 / 深色 / 1.3 倍字体 / 360dp 均已实机截图检查。
- 截图前临时把 DSH 宠物设为「不显示」，关闭 MIUI 侧边栏浮层；截图完成后会恢复宠物与系统通知设置。
- 未修改 Room Schema、备份恢复、Keystore、BYOK 协议、目标规则、隐私授权。

## 6. 截图

目录：`docs/reports/morrow-ui-0.15.2/`

1. `01-today-light-top.png` 浅色首页
2. `02-today-light-lower.png` 首页下半页（固定顶栏）
3. `03-life-panorama-collapsed.png` 人生全景收起
4. `04-life-panorama-expanded.png` 人生全景展开（内联操作）
5. `05-trajectory.png` 轨迹
6. `06-diary.png` 日记
7. `07-advisor-messages.png` 顾问有消息
8. `07b-advisor-empty.png` 顾问空态
9. `08-explore-result.png` 探索结果（固定底部操作）
10. `09-decision-result.png` 决定结果（固定底部操作）
11. `10-weekly-review.png` 一周回顾
12. `11-today-dark.png` 深色首页
13. `12-advisor-dark-empty.png` 深色顾问空态
14. `13-large-font.png` 1.3 倍字体
15. `14-360dp-top.png` / `14b-360dp-lower.png` 约 360dp
16. `15-settings.png` 设置页
17. `16-goal-editor.png` 目标编辑器
18. `17-goal-editor-keyboard.png` 目标编辑器 + 键盘

品牌/顶栏前后对照：`compare/01-today-light-top.jpg`、`compare/11-today-dark.jpg`。

## 7. 已知取舍

1. 探索结果正文较短，固定底部操作区上方会留出呼吸空间；这是为了不让按钮堆在中部、下方留大片空白。
2. 目标编辑器在键盘收起时占 90% 高度，字段与底部操作之间会有留白；键盘弹出时操作栏固定可见，优先保证可操作。
3. 系统 `Switch`、`RadioButton`、`DatePickerDialog` 保留平台控件外观；本轮统一的是页面、输入框、按钮和弹层框架。
4. QA 顾问回复来自离线测试引擎，明确标注「QA 离线测试引擎」，不代表真实模型输出。

## 8. APK

- debug：`/sdcard/Download/DSH-Go/morrow-ui-0.15.2/mengya-life-0.15.2-debug.apk`
  - SHA-256 `64a05e5ce670c1ffd27d3d18102fa79bfc221b3bcca888665958a6849471ad2b`
- QA：`/sdcard/Download/DSH-Go/morrow-ui-0.15.2/mengya-life-0.15.2-qa.apk`
  - SHA-256 `174c35d5f0f2b8f622055f72b37e56ed580597b5d5f191f4df40ce7246aa59c8`
- 交付 zip：`/sdcard/Download/DSH-Go/MengyaLife-UI-Polish-0.15.2-20261107.zip`
