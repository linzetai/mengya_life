# MengYa Life 0.24.3 · 演示数据与真实性审计

## A. 正式版实际可达的页面

`MengyaLifeApp.kt` 的 `MainShell` 只挂四个 Tab：

- `AppTab.TODAY` → `TodayScreen`
- `AppTab.PATH` → `TrajectoryScreen`
- `AppTab.DIARY` → `DiaryScreen`
- `AppTab.ADVISOR` → `AdvisorScreen`

二级页面/浮层（`showSettings` / `showPanorama` / `showExplore` / `showDecision` /
`showMemories` / `showWeeklyReview`）都从上述四个页面进入，正式版没有隐藏路由。

## B. 旧版本遗留、已不可达的页面

| 页面 | 引用检查 | 处理 |
|---|---|---|
| `OverviewScreen.kt` | 仅自身定义，无任何调用点 | 删除 |
| `TrendsScreen.kt` | 仅自身定义 | 删除 |
| `GoalsScreen.kt` | 仅自身定义 | 删除 |

它们专用的 `PageHeader`、`DemoTag`、`MultiLineChart`、`Sparkline` 一并删除；
`Charts.kt` 只保留 `MetricTrendChart` / `TrendPointList` 这两个读取真实
`goal_checkins` 的组件。

## C. QA 专用数据与机制（保留并与正式包隔离）

- `BuildConfig.QA_BUILD`：`applicationId`、数据库名、DataStore、Keystore 别名隔离；
- `AdvisorTestBus`：仅 QA 构建接收测试提示词（`MengyaLifeApp.kt` 内 `if (!BuildConfig.QA_BUILD) return`）；
- `FakeAdvisorEngine` / 离线测试引擎开关：仅 QA 构建存在；
- `QaRestoreFaultInjector`、QA 恢复取证：仅 QA 构建；
- 设置页「QA 隔离构建」横幅与顶栏「QA」标记：仅 QA 构建渲染。

这些机制只影响 QA 包，正式包不创建假引擎、不读取测试总线。

## D. 正式版可能出现的虚构数据

审计结论：**无**。

- 今天页洞察卡：优先 `GrowthInsight`（带 factIds 与数据版本校验），
  否则退回本机可核验的最近进展/日记，否则显示「还在认识你」空态；
- 今天页数字：`GoalSnapshot.longTermCount / stageCount / lastCheckinAt` 与真实日记条数；
- 轨迹页：只画 `GoalTrend.series.points`（`goal_checkins`），没有演示曲线；
  单点只画一个点并明确说明无法判断趋势；
- 周回顾：`WeeklyReview` 由本机聚合生成，AI 增强失败时只展示本地事实；
- 顾问：未配置/未授权时不显示任何「来自模型」的回复。

本轮删除 `DemoData.kt` 后，代码中已没有演示目标、演示趋势、演示洞察或「成长分数」。
设置页「关于 → 产品定位」下的数据范围说明改为直接描述真实数据来源。

## E. 功能已实现但文案仍描述旧版本

| 位置 | 旧文案 | 现在 |
|---|---|---|
| 恢复分组 | 「恢复备份（只读预览）」「本阶段不会写入数据」 | 「恢复备份」+ 校验/预览/确认/执行流程说明 |
| 恢复弹窗 | 「恢复预览（只读）」「尚未执行恢复；没有写入任何数据」 | 「恢复预览」+ 仅未确认时说明「还没有改动数据」 |
| 恢复成功 | 「API Key 与 AI 授权不会恢复」（无条件） | 按 endpoint 策略生成：保留 / 删除 Key / 撤销授权 |
| 设置-数据采集 | 「INTERNET 仅用于你主动发起的 AI 请求」 | 列出对话、每周 AI 回顾、主动关心三种联网情形 |
| 设置-通知 | 「后续接入」「不申请通知权限」 | 真实说明 + 打开系统通知设置 |
| 顶栏（QA） | 「演示数据」 | 「QA」 |
| 版本 | 0.24-morrow / 34 | 0.24.3 / 35 |

## 说明

`README.md` 与 `docs/` 中历史版本报告仍保留当时的演示数据描述，属于历史记录，
本轮不改写；以本报告和当前代码为准。
