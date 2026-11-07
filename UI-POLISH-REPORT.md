# 萌芽人生 0.15.1 · UI Detail Polish 报告

日期：2026-11-07  
分支：`master`（单工作树）  
基线提交：`a3fe769`（0.15.0-morrow）  
版本：`0.15.1-morrow` / versionCode 17  
截图目录：`docs/reports/morrow-ui-polish-20261107/`

## 1. 这轮做了什么

不是重做产品，而是把 0.15.0 的“结构正确”提升到“细节完成”：

- 用一套自绘 24×24 线性图标替换汉字占位、Material 混合图标和不一致箭头。
- 建立 Primary / Secondary / Tertiary 三层按钮体系，修正主次不明的 CTA。
- 重做顾问页状态区、空态、输入区和消息层；重做周回顾的卡片结构。
- 修正次级文字对比度（`#7B857B` 3.56:1 → `#5B6C61` 5.18:1 / `#627368` 4.67:1）。
- 统一页面边距、卡片内距、方向行高度、快捷入口高度、图标与文字基线。
- 修正深色用户气泡、深色主按钮、系统栏图标和 1.3 倍字体行为。
- 不触碰 Room Schema、备份恢复、Keystore、BYOK 协议、目标规则、隐私授权。

## 2. 审计发现（实际截图）

对比 `morrow-ui-20261107` 的 11 张截图，确认这些问题：

1. 财富/职业/生活使用「财/职/生」汉字当图标，和底部导航的线条图标不是一套语言。
2. 底部导航和页面内箭头视觉重量不一致；部分箭头 13/14/15dp 混用。
3. 首页方向卡没有进度时高度仍被空灰进度条撑大；三张卡看起来“有的满、有的空”。
4. 人生全景同时有左侧展开箭头和右侧更多菜单，点卡片主体却没有明确结果。
5. 顾问页模型状态、授权状态、设置入口分成三行，空对话留下大片无意义空白。
6. 「整理为目标草案」在没有可整理内容时也出现。
7. 周回顾由多个 `SectionCard` 叠成，来源链接在每个事实下重复，页面像模板拼接。
8. 决定结果四张一模一样的白卡从上堆到下，没有阅读层级。
9. 次级文字对比度不足；深色用户气泡浅绿底白字不可读。
10. 探索结果“保存 / 讨论 / 重来”三个操作视觉权重接近；多个页面出现两个同级描边大按钮。

## 3. 统一规范

完整规范见 [UI-SPEC.md](UI-SPEC.md)。核心：

- 图标：24×24 逻辑画布、2.1 单位圆头描边、默认 20dp、箭头 16dp；默认/选中/弱化/禁用/深色底五种颜色明确。
- 按钮：Primary 52dp/14dp 填充；Secondary 50dp/14dp 描边；Tertiary 48dp 触控文字操作。
- 对比度：正文次级 `#5B6C61`（5.18:1），说明 `#627368`（4.67:1）；`#9BA59A` 只做禁用。
- 间距：页面 22dp、区块 26dp、卡片 20dp、卡间距 8–12dp。
- 系统栏：App 主题切换时同步设置状态栏/导航栏图标明暗，避免深色背景上出现深色图标。

## 4. 逐页精修

### 今天

- 领域图标改为 Wallet / Briefcase / Leaf / Heart。
- 三张方向卡统一 78dp 最小高度、14/11dp 内距、36dp 图标底、右侧 16dp 箭头。
- 有阶段时显示 3dp 进度；无阶段时保留等高空间但不画空灰线，避免“空卡片”观感。
- 三个 ActionTile 统一 110dp 高、32dp 图标圆、14dp 内距、底部标题基线；360dp 下仍三等分。
- 「查看全景」「更多趋势」「写下一篇」统一 12sp 文本 + 16dp 箭头。
- 洞察卡 CTA 使用同一行左右布局，箭头贴右且不随文案长度漂移。

### 人生全景

- 去掉左侧展开箭头与顶部更多菜单的并行导航。
- 卡片主体点击展开/收起；右上只留一个 Chevron 指示。
- 展开后才出现「记录进展 | 编辑 | 更多」操作条；更多内保留进展历史、状态、子层级、删除。
- 领域图标、标题、状态标签、进展数、进度条统一 16dp 左内距；卡片圆角 17dp。
- 底部改为 Primary「新增长期目标」+ Secondary「探索方向」，主次明确。

### 轨迹

- 筛选药丸改为 48dp 触控区 + 36dp 视觉高度。
- 图表纵轴改为自然刻度：12–14.5 万元显示 10 / 14 / 18，而不是 11.625 / 13.25 / 14.875。
- Y 轴宽度 54dp；图表上下留 7dp，右侧 10dp；X 轴首尾标签不贴边。
- 网格更淡，面积填充降到 0.07，强调细线本身。
- 「一周回顾」「我看见的变化」「为什么会这样」保持层级但不重复大段文本。

### 日记

- 写日记入口 80dp 高、19dp 铅笔、16dp 箭头；图标与两行文字垂直居中。
- 月份标题改为 16sp 半粗，篇数 11sp；日期列固定 44dp，日号与星期基线一致。
- 隐私说明从页面底部移到书写入口下方，变成自然提示而不是孤立尾注。
- 正文预览固定 2 行省略；编辑/取消改为 Primary + Tertiary。

### 顾问

- 顶部保留衬线情绪标题。
- 模型、授权合并为一个紧凑、可展开状态区：
  - 折叠：连接状态、可读取类别、模型/授权两个文字入口。
  - 展开：服务地址、模型名、授权类别；技术字段不占据主界面。
- 空态改为垂直居中的设计化空态：一句引导 + 三个图标明确的建议问题 + 授权检索说明。
- 输入框与 48dp 发送按钮垂直居中；发送禁用到可用态颜色明确；发送图标改为统一线性纸飞机。
- 「整理为目标草案」只在存在助手回复时出现。
- 失败消息保留证据 + 明确“重试”文字按钮；重试成功清除“正在重试”过时提示。
- 深色用户气泡改为 `insightCard` 深森林绿，白字可读。

### 探索 / 决定 / 周回顾

- 探索结果：Primary 保存、Secondary 继续讨论、Tertiary 重来；上一步/跳过降为文字操作。
- 决定结果：四段分析合并进一张带 01–04 编号和分隔线的卡片；底部 Primary 讨论、Secondary 修改、Tertiary 回首页。
- 周回顾：主卡内部用分隔线组织「本周变化 / 值得肯定 / 值得关注 / 下一步」，不再四张卡片叠加；每个区块最多一个相关目标入口；重新整理与 AI 增强降到次级文字操作。

## 5. 修改前后对照

同尺寸（1200×2608）真机截图，左侧 0.15.0，右侧 0.15.1：

- [今天顶部](docs/reports/morrow-ui-polish-20261107/compare/01-today-light-top.jpg)
- [今天下半部](docs/reports/morrow-ui-polish-20261107/compare/02-today-light-lower.jpg)
- [人生全景折叠](docs/reports/morrow-ui-polish-20261107/compare/03-life-panorama-collapsed.jpg)
- [轨迹](docs/reports/morrow-ui-polish-20261107/compare/05-trajectory.jpg)
- [日记](docs/reports/morrow-ui-polish-20261107/compare/06-diary.jpg)
- [顾问空态](docs/reports/morrow-ui-polish-20261107/compare/07-advisor-empty.jpg)
- [探索结果](docs/reports/morrow-ui-polish-20261107/compare/09-explore-result.jpg)
- [决定结果](docs/reports/morrow-ui-polish-20261107/compare/10-decision-result.jpg)
- [深色今天](docs/reports/morrow-ui-polish-20261107/compare/11-today-dark.jpg)
- [1.3 倍字体](docs/reports/morrow-ui-polish-20261107/compare/13-large-font.jpg)
- [周回顾](docs/reports/morrow-ui-polish-20261107/compare/15-weekly-review.jpg)

0.15.1 新增状态：

- [人生全景展开](docs/reports/morrow-ui-polish-20261107/after/04-life-panorama-expanded.png)
- [顾问有消息](docs/reports/morrow-ui-polish-20261107/after/08-advisor-messages.png)
- [顾问深色](docs/reports/morrow-ui-polish-20261107/after/12-advisor-dark.png)
- [360dp 首页](docs/reports/morrow-ui-polish-20261107/after/14-wide-360dp.png)
- [360dp 下半部](docs/reports/morrow-ui-polish-20261107/after/14b-360dp-lower.png)

全部原尺寸 after 截图在 `after/`，before 截图在 `before/`。

## 6. 验证

- JVM：303 个测试，0 失败。
- 构建：`assembleDebug` / `assembleQa` 成功。
- 真机：Xiaomi 2509FPN0BC，Android 17，1200×2608 @480dpi。
- 最终真机冒烟 10/10：四个 Tab、今天洞察卡、人生全景展开、返回、无崩溃日志。
- 状态覆盖：浅色/深色、1.3 倍字体、约 360dp 宽度（1080×2400）、空态、有消息、错误重试、展开/折叠、禁用/可用发送。
- 截图前临时关闭 MIUI `DockAssistantView` 浮层（系统侧边栏，不是应用滚动条），截图后已恢复系统设置；最终截图无系统浮层。
- QA 数据与正式包隔离；目标、进展、日记均为 QA 演示数据，首页有「演示数据」标识。

## 7. 已知剩余问题

1. 目标编辑 / 状态选择 / 进展历史等 `GoalSheets` 弹层仍保留部分 Material 控件外观，下一轮可继续统一到 Secondary/Tertiary 和 `MorrowTextField`。
2. 设置页只统一了设计令牌与部分文案，尚未逐项重排 AI、隐私、备份分组。
3. 图表固定 3 条自然刻度；更多数据点时可考虑 4–5 条刻度，但不新增图表功能。
4. QA 离线引擎回复明确写着“QA 离线测试引擎”，截图中的对话是测试内容，不是真实模型输出。
5. 未重跑未触碰的 G4A 备份/恢复全量验收；本轮未修改相关代码。

## 8. APK

- debug：`/sdcard/Download/DSH-Go/morrow-ui-polish/mengya-life-0.15.1-debug.apk`
- QA：`/sdcard/Download/DSH-Go/morrow-ui-polish/mengya-life-0.15.1-qa.apk`
- 交付 zip：`/sdcard/Download/DSH-Go/MengyaLife-UI-Polish-0.15.1-20261107.zip`

SHA-256 见交付目录的 `SHA256SUMS.txt`，最终值以构建产物为准。
