# MengYa Life 0.24.3 · UI 审计

基线：`9e2e4b9`（master）。审计方法：从 `MengyaLifeApp.kt` 的实际路由出发，
逐个检查页面引用、ViewModel 数据来源与界面文案；不以「文件存在」推断可达性。

## 0. 可达性结论

| 页面 | 入口 | 结论 |
|---|---|---|
| TodayScreen | `AppTab.TODAY` | 正式可达 |
| TrajectoryScreen | `AppTab.PATH` | 正式可达 |
| DiaryScreen | `AppTab.DIARY` | 正式可达 |
| AdvisorScreen | `AppTab.ADVISOR` / Care 通知 / 顾问测试总线（仅 QA） | 正式可达 |
| LifePanoramaScreen | 今天页「查看全景」、目标跳转 | 正式可达 |
| ExploreScreen / DecisionScreen | 今天页入口 | 正式可达 |
| SettingsScreen | 顶栏「设置」 | 正式可达 |
| WeeklyReviewScreen | 轨迹页 / 通知 | 正式可达 |
| OverviewScreen | 无路由引用（仅自身 `fun`） | **不可达** |
| TrendsScreen | 无路由引用 | **不可达** |
| GoalsScreen | 无路由引用 | **不可达** |

不可达的三个页面只被彼此或自身引用，正式版没有任何跳转能进入；
本轮删除，并同时删除只服务它们的 `PageHeader`、`DemoTag`、`MultiLineChart`、`Sparkline`。

## 1. 今天页：区块过多、没有唯一焦点

- 原问题：一屏内并列「大标题 / 洞察卡 / 主动关心 / 小计划列表 / 方向列表（take 3）/
  三个入口磁贴 / 日记卡」七类区块，三个入口磁贴与洞察卡、日记卡互相争夺注意力；
  「也值得记住的好事」与日记页信息重复。
- 影响：打开 App 后不知道「现在最值得做什么」；长文案会把主要按钮挤出首屏。
- 改动：改为三层结构——当前关注、下一步（唯一主行动，含主动关心）、成长概览；
  小计划并入「下一步」；日记卡删除；方向只保留 2 条摘要，其余进全景。
- 源码：`ui/screens/TodayScreen.kt`、`ui/screens/TodayNextStep.kt`。

## 2. 今天页的「下一步」没有真实推导

- 原问题：入口磁贴固定为「探索 / 写日记 / 讨论决定」，与用户当前状态无关。
- 影响：推荐与真实记录无关，用户会把它当成任务清单。
- 改动：新增纯函数 `decideTodayNextStep`，按真实状态排序：
  主动关心 > 本周未记录的小计划 > 无方向 > 超过 10 天未更新的方向 >
  今天没写日记 > 和顾问聊聊；有单测覆盖。
- 源码：`ui/screens/TodayNextStep.kt`、`app/src/test/.../TodayNextStepTest.kt`。

## 3. 顾问页：顶部过高、状态措辞不实

- 原问题：空对话时顶部有两行大标题（约 70dp），再叠加状态胶囊；状态文案写「已连接」，
  但只是本机存在 endpoint/model，并没有真实探测。
- 影响：对话可视面积被压缩；「已连接」会让用户误以为网络已连通。
- 改动：删除空态大标题；状态胶囊改为「已配置 · 模型 / 等待授权 / 未配置模型」；
  展开后的状态卡明确写「发送消息时才会发起真实请求，这里只表示本机配置齐全」。
- 源码：`ui/screens/AdvisorScreen.kt`。

## 4. 轨迹页：五个区块语义重叠

- 原问题：数值变化 / 一周回顾 / 我看见的变化 / 为什么会这样 / 说明并列，
  「变化」与「为什么」大量引用同一批事实；时间范围只作用于图表。
- 影响：用户需要读四段相似内容，仍回答不了「这段时间我有什么变化」。
- 改动：合并为三段：数值变化、这段时间的变化（含一周回顾入口）、线索与回溯；
  变化叙述跟随所选时间范围。
- 源码：`ui/screens/TrajectoryScreen.kt`。

## 5. 人生全景：领域被 `take(4)` 截断且不可达

- 原问题：`roots.groupBy { domain }.take(4)`，第 5 个及以后的领域只显示
  「还有 N 个方向没有展示」，没有任何入口。
- 影响：用户创建的领域可能永远看不到，违反「所有目标可达」。
- 改动：移除 `take(4)`；按领域折叠/展开，默认展开前两个；提供「全部展开 / 全部收起」；
  从今天页点目标时自动展开所在领域与父级路径并滚动到该领域。
- 源码：`ui/screens/LifePanoramaScreen.kt`。

## 6. 日记页日期会跨夜过期

- 原问题：`val today = remember { LocalDate.now() }`，页面常驻时过了 0 点仍显示昨天。
- 影响：夜间记录时日期错误。
- 改动：新增 `rememberToday()`，在 `ON_RESUME` 与跨午夜时刷新；今天页同样使用。
- 源码：`ui/components/Common.kt`、`ui/screens/DiaryScreen.kt`、`ui/screens/TodayScreen.kt`。

## 7. 恢复页文案与真实行为矛盾

- 原问题：分组标题仍写「恢复备份（只读预览）」，说明写「本阶段不会写入数据 /
  最终执行按钮在 G4A-2B 开放」，而预览弹窗里已经有「确认恢复（替换本机数据）」按钮；
  成功横幅固定写「API Key 与 AI 授权不会恢复」，与 endpoint 相同时保留凭据的策略不符。
- 影响：用户无法判断恢复到底会不会写数据，也无法判断凭据会发生什么。
- 改动：流程改为「选择文件 → 口令校验 → 预览覆盖范围 → 确认执行 → 结果」；
  说明按 `RestoreCredentialPolicy` 描述（同地址保留、不同地址删 Key 并撤销授权）；
  成功消息按实际策略生成，不再无条件承诺。
- 源码：`ui/settings/RestoreSection.kt`、`data/restore/RestoreCoordinator.kt`（仅成功文案）。

## 8. 设置页联网与通知说明不准确

- 原问题：写「联网只发生在你主动发送 AI 请求时」，遗漏每周 AI 回顾与主动关心；
  通知行写「本版本不申请通知权限」，但清单已声明 `POST_NOTIFICATIONS`，
  主动关心开关还会真实申请权限。
- 影响：隐私说明与真实行为不一致。
- 改动：逐项列出会联网的三种情形；通知行改为真实说明并跳转系统通知设置；
  导出/删除等既有说明保持诚实（未实现的删除功能继续标注未实现）。
- 源码：`ui/screens/SettingsScreen.kt`。

## 9. 底部导航在窄屏/大字体下会溢出

- 原问题：每个导航项固定 `width(76.dp)`，4 项 + 20dp 内边距 = 324dp，
  320dp 屏幕或字体放大时文字被裁剪。
- 改动：改为 `weight(1f)` 均分宽度 + 48dp 最小触控高度 + 单行省略；
  图标底与文字层级不变。
- 源码：`ui/components/MorrowBottomBar.kt`。

## 10. QA 标记与版本号

- QA 构建顶栏误用「演示数据」标签，容易与真实演示数据混淆 → 改为「QA」。
- `versionName=0.24-morrow / versionCode 34` → `0.24.3 / 35`。
- 源码：`ui/components/Common.kt`、`app/build.gradle`。

## 11. 已删除的内容（无引用）

- `ui/screens/OverviewScreen.kt`、`ui/screens/TrendsScreen.kt`、`ui/screens/GoalsScreen.kt`
- `data/DemoData.kt`（演示文案、演示趋势、演示洞察）
- `ui/components/Charts.kt` 中的 `MultiLineChart` / `Sparkline`（只画演示分数）
- `ui/components/Common.kt` 中的 `DemoTag` / `PageHeader`（只被已删页面使用）
