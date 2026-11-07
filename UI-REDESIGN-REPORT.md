# 萌芽人生 · Morrow 风格 UI/UX 重构报告

日期：2026-11-07  
分支：`master`（单工作树，未使用 worktree）  
本轮起始提交：`f14ff3e606f938da72db7221a1efd21eb5b94f5e`  
交付提交：本轮 `master` 最新提交（提交哈希见最终回复）  
工作树：本报告与源码、最终截图一并提交；APK 不进入 Git。

## 1. 结论

本轮把现有五 Tab 的开发者工作台，改造成四 Tab 的长期成长顾问产品：

- 视觉从「暖米色 + Material 卡片」切换为 Morrow 的「浅灰绿纸面 + 森林绿洞察卡 + 衬线大标题 + 细线克制层级」。
- 信息架构为 **今天 / 轨迹 / 日记 / 顾问**，目标树移入二级「人生全景」。
- 首页所有内容绑定真实状态：日期、目标、进展、日记数量、本地可核验洞察；空数据库不会出现假资产、假学习或假坚持天数。
- 五个核心页面（今天、人生全景、轨迹、日记、顾问）统一设计语言；探索方向、讨论决定、每周回顾保持可运行闭环。
- 业务边界未改：Room schema / 迁移、BYOK、密钥、授权、备份与恢复全部复用，只新增目标编辑器的预填字段映射。

## 2. 设计系统

### 2.1 颜色（`ui/theme/Color.kt`）

| 角色 | 浅色 | 深色 |
|---|---|---|
| Canvas | `#F6F7F1` | `#18221D` |
| Card | `#FFFEFC` | `#202C25` |
| Nested | `#EDF2EA` | `#28362D` |
| 主文字 | `#28372E` | `#EEF3E9` |
| 次文字 | `#7B857B` | `#B0BEB1` |
| 品牌绿 | `#345E4A` | `#8FB99C` |
| 洞察卡 | `#345E4A` | `#446E58` |
| 细描边 | `#E3E8DF` | `#3A4B3D` |

不使用蓝紫渐变、霓虹绿、厚阴影或玻璃拟态；危险色只出现在真实错误和不可逆操作。

### 2.2 字体与层级（`ui/theme/Type.kt`）

- 首页开场：衬线 30sp / 41sp，两行，常规字重。
- 页面标题：衬线 27sp / 37sp；探索/决定用 24sp / 34sp。
- 洞察句：衬线 23sp / 33sp。
- 区块标题：无衬线 16sp 半粗；正文 14sp / 22sp；说明 12–13sp。
- 中文衬线使用 `FontFamily.Serif`（设备 Noto Serif CJK 回退），正文和导航保持系统无衬线。

### 2.3 间距、圆角与组件

- 水平内容边距 22dp；区块间距 26dp；卡片内边距 20dp；点击区域 ≥48dp。
- 洞察卡 22dp 圆角、白卡 18dp、目标行 16dp。
- `ui/components/Common.kt` 统一了 `MorrowTopBar`、`SectionHeading`、`MorrowPrimaryButton`、`MorrowQuietButton`、`MorrowTextField`、`MorrowChoicePill`、`SettingsAvatar`、`MorrowGlyph` 和空态卡片。
- 底部导航 `MorrowBottomBar` 为自绘四 Tab：暖白表面、1dp 分割线、浅绿选中底，不使用 Material3 默认大药丸。

## 3. 信息架构

| 旧 | 新 |
|---|---|
| 总览 | **今天**（全新首页） |
| 目标 | **人生全景**（二级页，首页「查看全景」/方向行进入） |
| 趋势 | **轨迹**（真实数值趋势 + 每周回顾 + 证据） |
| 日记 | **日记** |
| 顾问 | **顾问** |

设置仍在每个一级页右上角圆形头像，二级页从顶栏保持同一入口；周回顾、成长记忆、探索、决定、设置都不占底部导航。首页与全景滚动位置、日记草稿、顾问输入和主题切换在返回/旋转时保持。

## 4. 页面与真实数据映射

### 今天

- 日期：设备本地日期与星期。
- 洞察卡：
  1. 优先展示当前数据版本下可用的洞察报告（含来源条数与区间）；
  2. 否则展示 14 天内最近一次手动进展，或最近一篇日记；
  3. 数据不足时使用「给自己一点时间，我们从今天开始记录。」+「开始探索」。
  首页渲染只读取本地状态，不触发 AI 生成，不伪造统计。
- 人生方向：`GoalSnapshot.roots` 的真实长期目标；显示领域、描述、阶段数与进展数，进度条是已完成阶段 / 全部阶段。
- 日常入口：探索人生方向、写一篇日记、讨论一个决定。
- 好事卡：真实日记篇数与最近日期；没有记录时只用温柔空态。

### 人生全景

- 复用 `GoalsViewModel` / `GoalRepository` / `GoalOverlays`。
- 按真实领域分组，首屏最多 4 个领域；目标随领域创建顺序展示，与首页顺序一致。
- 点父目标展开/收起；点叶子目标直接打开「记录进展」；三点菜单保留记录进展、进展历史、状态、编辑、新建子层级和删除确认。
- 探索入口生成草案，只有用户进入目标编辑器并再次保存才写入。

### 轨迹

- 只画 `goal_checkins` 中用户手动填写的数值；按单位/目标分开，不做单位换算。
- 近一周 / 近一月 / 近三月按真实记录时间过滤；没有点时不补线。
- 「我看见的变化」来自数值趋势、最近进展和最近日记。
- 「为什么会这样」链接周回顾事实和日记原文；数据不足显示补录建议。
- 周回顾作为精选入口，不额外堆叠无解释图表。

### 日记 / 顾问

- 日记：真实 Room 写入、编辑、删除、AI 线索确认/纠正全部保留；只重做入口、时间线与编辑纸面。
- 顾问：未配置、未授权、已授权三种状态真实可理解；保留 BYOK 配置、测试连接、发送前授权、来源、失败重试、取消、目标草案确认。
- 探索：三步本地表单 → 待确认草案 → 预填目标编辑器；不联网、不自动创建目标。
- 决定：本地输入 → 四段思考框架（短期影响、长期关系、不确定因素、可验证小行动）；金额只引用用户输入，不做财务预测。
- 设置：模型、隐私、导出、v2 完整备份/恢复、主题、关于页能力保持不变；仅修正过时文案与统一标题/卡片。

## 5. 真机验证

设备：Xiaomi 2509FPN0BC，Android 17，1200×2608 @480dpi（约 400×869dp），QA 包 `com.mengyalife.app.qa`（与正式包、正式数据完全隔离）。

已验证：

- 新建长期目标、阶段目标，记录 3 次数值进展；首页、全景、轨迹数据一致。
- 新建 2 篇日记，时间线按月分组、编辑和删除确认逻辑未退化。
- 探索三步 → 草案 → 目标编辑器预填（实际保存前可取消）。
- 讨论决定 → 四段框架 → 「和顾问继续讨论」预填对话。
- 顾问离线 QA 引擎配置、授权、三个问题入口、输入区与草案入口；真实包不伪造回答。
- 洞察卡在无 AI 报告时用本地事实并跳转轨迹；不自动点击生成。
- 系统返回键：全景返回首页，不再退出应用；探索返回上一步；决定结果返回输入态。
- 浅色 / 深色主题切换并持久化；1.3 倍系统字体下首页不裁字，洞察卡 CTA 完整。
- JVM 单元测试 303 个，0 失败；`assembleDebug` 与 `assembleQa` 成功。

截图（均为 1200×2608 原尺寸、未裁剪）：

- `docs/reports/morrow-ui-20261107/01-today-light.png`
- `02-today-light-lower.png`
- `03-life-panorama.png`
- `04-trajectory.png`
- `05-diary.png`
- `06-advisor.png`
- `07-today-dark.png`
- `08-explore-draft.png`
- `09-decision-framework.png`
- `10-weekly-review.png`
- `11-large-font-check.png`

## 6. 变更文件

新增：

- `ui/screens/TodayScreen.kt`
- `ui/screens/LifePanoramaScreen.kt`
- `ui/screens/TrajectoryScreen.kt`
- `ui/screens/ExploreScreen.kt`
- `ui/screens/DecisionScreen.kt`
- `ui/components/MorrowBottomBar.kt`

主要修改：

- `ui/theme/Color.kt` / `Type.kt` / `Theme.kt`
- `ui/components/Common.kt` / `Charts.kt`
- `MengyaLifeApp.kt` / `navigation/AppTab.kt`
- `ui/screens/DiaryScreen.kt` / `AdvisorScreen.kt`
- `ui/weekly/WeeklyReviewScreen.kt`
- `ui/goals/GoalContract.kt` / `GoalsViewModel.kt`（只加目标编辑器预填字段）
- `ui/settings/AiSettingsSection.kt`、`data/DemoData.kt`（修正过时文案）
- `app/build.gradle`（版本 `0.15.0-morrow` / versionCode 16）

未改：Room schema 与迁移、备份 v2 / 恢复事务、Keystore、SAF、授权判定、导出格式。

## 7. 已知剩余问题与下一步

1. 数值趋势坐标轴已消除长小数，但刻度还不是严格的「整步长」（例如 14.9 / 13.3 / 11.6）；下一轮可做 nice-ticks。
2. 旧 `OverviewScreen.kt` / `TrendsScreen.kt` / `GoalsScreen.kt` 仍保留在源码与测试映射中，但已不被路由引用；后续可单独清理并同步测试映射。
3. 首页洞察卡的 AI 详情弹窗只有存在 AI 报告时可达；QA 演示数据展示本地事实并跳转轨迹，本轮没有为了截图消耗真实模型调用。
4. 真机截图覆盖当前 400×869dp 与 1.3 倍字体；未单独拍 360dp 设备，但布局全部使用 dp、单列滚动与三列等分入口。
5. 按任务要求没有重跑未触碰的 G4A 备份/恢复高成本验收；相关代码本轮未修改。

## 8. APK

目录：`/sdcard/Download/DSH-Go/morrow-ui/`

- `mengya-life-0.15.0-debug.apk`  
  SHA-256 `a2ad13485d1db9052e8beb07fd0c19ee10485212bfd48338beaa09b413de5f05`
- `mengya-life-0.15.0-qa.apk`  
  SHA-256 `23bd90853d085a1ad97246cfee6818a647eea8f5ed00b0e45f4023d3d87e6761`

QA 包与正式包独立 applicationId、数据库、偏好与密钥别名；截图中展示的是 QA 隔离数据，并带「演示数据」标识。
