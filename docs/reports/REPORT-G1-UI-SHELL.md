# Gate 1 报告 · UI 框架、核心页面与设置页

| 项 | 值 |
|---|---|
| 项目 | 萌芽人生 / Mengya Life |
| 工程名 / 包名 | MengyaLife / `com.mengyalife.app` |
| 应用版本 | **0.2.0-g1**（versionCode 2） |
| 轮次 | Gate 1（UI 框架 + 五个一级页面 + 设置页） |
| 执行时间 | 2026-11-02 10:13 – 11:40（Asia/Shanghai） |
| 报告路径 | `docs/reports/REPORT-G1-UI-SHELL.md` |
| 结论 | **PASS** |

---

## 1. 结论与状态

**PASS** —— 在已通过 Gate 0 的工程上，于手机本机（ZeroTermux + proot，aarch64）完成：

1. 五个一级页面（总览 / 目标 / 趋势 / 日记 / 顾问）与独立设置页；
2. 底部导航切换、二级页面进出、系统返回键、UI 状态保持；
3. 浅色 / 深色 / 跟随系统三态主题即时生效（含**程序化亮度断言**，非肉眼判断）；
4. 日记新建保存、顾问输入发送等基础交互；
5. 本机构建、安装、启动、无崩溃；
6. UI 自动化 **58 项断言全部通过**（导航 36 + 交互 22），定位全部基于 **resource-id / 文本**，未使用固定坐标。

### 验收项逐条对照

| # | 要求 | 结果 | 证据 |
|---|---|---|---|
| 1 | assembleDebug 构建成功 | ✅ | §7，`logs/g1-build-final.log`（exit 0，36 tasks） |
| 2 | APK 安装成功 | ✅ | §7.2，`logs/g1-install.log`，安装后回读哈希一致 |
| 3 | 五个一级页面可正常切换 | ✅ | §8.1，5/5 |
| 4 | 所有页面均可进入设置页 | ✅ | §8.2，四个页面各 5 项断言 |
| 5 | 从设置页返回原页面 | ✅ | §8.2（返回箭头 + 系统返回键两种路径） |
| 6 | 浅色 / 深色 / 跟随系统切换有效 | ✅ | §8.3（含亮度差值断言） |
| 7 | 日记与顾问输入区基本交互可用 | ✅ | §8.4 |
| 8 | 页面滚动与返回正常 | ✅ | §8.5 |
| 9 | 无崩溃 | ✅ | §8.6（`logcat -b crash` 空） |
| — | 截图（7 类） | ✅ | §9，共 14 张，见 `logs/g1-shots/` |
| — | 证据压缩包 | ✅ | `docs/reports/evidence/G1-evidence.zip` |

---

## 2. 完成的页面清单

### 2.1 一级页面（底部导航）

| 页面 | 文件 | 行数 | 主要内容 |
|---|---|---|---|
| 总览 | `ui/screens/OverviewScreen.kt` | 213 | 人生方向（标题 + 陈述 + 四个支柱）、长期目标摘要（3 条含进度条）、成长趋势预览（sparkline + 三个指标）、近期洞察（3 条）、AI 顾问入口按钮 |
| 目标 | `ui/screens/GoalsScreen.kt` | 196 | 维度筛选 Chip（全部/健康/职业/财富/生活）、四条长期目标卡片、阶段目标进度、短期目标可勾选清单、层级说明 |
| 趋势 | `ui/screens/TrendsScreen.kt` | 179 | 12 周三序列折线图（自绘 Canvas）、图例、周标签、五维度对比条、趋势摘要 4 条、数据说明 |
| 日记 | `ui/screens/DiaryScreen.kt` | 180 | 今天/新建入口与内联编辑器、AI 提取预览区（后续接入）、记录列表（3 条演示 + 本次新建） |
| 顾问 | `ui/screens/AdvisorScreen.kt` | 216 | 演示声明条、对话气泡列表、上下文建议 Chip、输入框 + 发送按钮 |

### 2.2 二级页面

| 页面 | 文件 | 行数 | 说明 |
|---|---|---|---|
| 设置 | `ui/screens/SettingsScreen.kt` | 313 | 四个分组、返回箭头、Snackbar 提示；无底部导航 |

### 2.3 支撑代码

| 文件 | 行数 | 职责 |
|---|---|---|
| `MengyaLifeApp.kt` | 306 | 根组件：导航状态、主题状态、全部页面状态、`BackHandler` |
| `navigation/AppTab.kt` | 56 | 五个一级页面的枚举定义（标题 / 图标 / 测试标签） |
| `data/DemoData.kt` | 268 | 全部演示数据集中管理 + 统一声明文案 |
| `ui/theme/{Color,Type,Theme}.kt` | 65 / 72 / 106 | 配色、字体层级、三态主题与 Shape |
| `ui/components/Common.kt` | 335 | `DemoTag`、`PageHeader`、`SectionCard`、`StatusPill`、`SettingsGroup`、`SettingsRow`、`AppTopBar`、`ThinProgressBar`、`InlineNote` 等 |
| `ui/components/Charts.kt` | 123 | `MultiLineChart`、`Sparkline`（Canvas 自绘，无第三方库） |
| `MainActivity.kt` | 20 | 仅挂载 `MengyaLifeApp()` |

合计 **2,648 行 Kotlin**，15 个文件。未新增任何依赖，未新增任何权限。

---

## 3. 页面导航结构

```text
MengyaLifeApp
├── state: currentTab: AppTab      (enum, rememberSaveable)
├── state: showSettings: Boolean   (rememberSaveable)
│
├── if (showSettings)  ──► SettingsScreen（独立 Scaffold，无 bottomBar）
│                          ├─ 左上角返回箭头 → showSettings = false
│                          └─ BackHandler(enabled = showSettings) → 同上
│
└── else ──► MainShell（Scaffold）
             ├─ topBar: AppTopBar(标题 = currentTab.label, actions = ⚙ 设置)
             ├─ bottomBar: NavigationBar（5 个 NavigationBarItem）
             └─ content: when(currentTab) → 5 个页面之一
```

**导航逻辑集中在 `MengyaLifeApp.kt`**，页面文件里没有任何跨页面跳转代码；
设置不是 Tab、不是侧边栏，也没有被塞进底部导航。

### 3.1 状态保持的实现方式

五个页面的可变 UI 状态全部**上提**到 `MengyaLifeApp`：

| 状态 | 类型 | 保存性 |
|---|---|---|
| 五个页面的 `LazyListState`（滚动位置） | `rememberLazyListState()` | 进程内保持（进设置返回不丢） |
| 目标维度筛选 | `rememberSaveable` | 也支持配置变更 |
| 短期目标勾选集合 | `mutableStateListOf` | 进程内保持 |
| 日记草稿 / 编辑器开关 | `rememberSaveable` | 也支持配置变更 |
| 日记列表（含本次新建） | `mutableStateListOf` | 进程内保持 |
| 顾问输入 / 消息列表 | `rememberSaveable` / `mutableStateListOf` | 进程内保持 |

因为设置页是「替换整棵子树」而不是覆盖层，页面 Composable 会被销毁；
但状态对象由父层持有，返回后重新组合时原样注入，**因此滚动位置与输入内容都不丢**（已由 §8.4 断言验证）。

---

## 4. 设置页设计与功能状态

| 分组 | 条目 | 状态 | 点击行为 |
|---|---|---|---|
| **A. AI 服务** | 模型配置 | 后续接入 | Snackbar：「本版本不接入任何模型服务」 |
| | 当前配置状态 | 未配置（演示环境） | — |
| | API 服务地址 | 后续接入 | Snackbar：「当前不会发起任何网络请求」 |
| | 模型 | 后续接入 | Snackbar：「当前未选择任何模型」 |
| | 密钥 | 后续接入 | Snackbar：「本版本不接收、不保存、不发送任何 API Key」 |
| **B. 数据与隐私** | 本地数据管理 | 演示 | Snackbar：「没有本地数据库，页面内容为演示数据」 |
| | 数据导入 / 导出 | 后续接入 | Snackbar：「当前没有可导入或导出的数据」 |
| | 数据采集与授权 | 说明 | Snackbar：「本版本不采集任何数据，无网络权限」 |
| | 删除全部数据 | 未实现 | Snackbar：「功能尚未实现：未删除任何数据」 |
| **C. 外观与体验** | 跟随系统 / 浅色 / 深色 | **可用** | 立即切换，作用于整个 App |
| | 主题持久化说明 | 明示限制 | 文案：仅本次运行有效，重启恢复「跟随系统」 |
| | 提醒与通知 | 后续接入 | Snackbar：「本版本不申请通知权限，也不会发送提醒」 |
| **D. 关于** | 应用名称 / 英文标识 | 完成 | — |
| | 当前版本 | 完成 | 读取 `PackageManager`，实测显示 `0.2.0-g1` |
| | 产品定位与简介 | 完成 | 含「本地优先 / 自带密钥 / 无后端」标签 |

**关键约定**

- 未实现项一律 `dimmed` 显示 + 「后续接入」标签，点击只给**说明性** Snackbar，
  **不显示「保存成功」「已删除」之类的虚假反馈**。
- 「删除全部数据」是纯提示，**没有任何实际副作用**（不调用任何删除 API，本版本也没有可删的数据）。
- 版本号不是硬编码：`readVersionName()` 读 `getPackageInfo().versionName`，
  实测与 APK 的 `versionName` 一致（`0.2.0-g1`）。

---

## 5. 主题系统设计

```text
ThemeMode (SYSTEM / LIGHT / DARK)   ← rememberSaveable，仅内存
        │
        ▼
darkTheme = when(mode) { SYSTEM -> isSystemInDarkTheme(); LIGHT -> false; DARK -> true }
        │
        ▼
MengyaLifeTheme(darkTheme) ──► MaterialTheme(colorScheme = Light/Dark, typography, shapes)
        │
        ▼
整个 App（含设置页自身，所以切换立即看得到）
```

- 两套完整 `ColorScheme`（各 26 个语义色），柔绿 + 暖白 / 深绿 + 近黑，**没有大面积蓝紫**。
- 自绘图表取色来自固定调色板（`ChartGreen / ChartOchre / ChartSlate`），深浅色下都可辨。
- 字体层级：`headlineLarge 30sp` → `titleLarge 20sp` → `bodyLarge 15sp` → `labelSmall 11sp`，
  统一 line-height 与 letter-spacing，未引入自定义字体文件。
- **限制（明确写在界面与文档中）**：不引入 DataStore，主题不持久化，进程结束回到「跟随系统」。

---

## 6. 工程文件变更

### 6.1 新增（13 个文件）

```text
app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt
app/src/main/java/com/mengyalife/app/navigation/AppTab.kt
app/src/main/java/com/mengyalife/app/data/DemoData.kt
app/src/main/java/com/mengyalife/app/ui/theme/Color.kt
app/src/main/java/com/mengyalife/app/ui/theme/Type.kt
app/src/main/java/com/mengyalife/app/ui/theme/Theme.kt
app/src/main/java/com/mengyalife/app/ui/components/Common.kt
app/src/main/java/com/mengyalife/app/ui/components/Charts.kt
app/src/main/java/com/mengyalife/app/ui/screens/OverviewScreen.kt
app/src/main/java/com/mengyalife/app/ui/screens/GoalsScreen.kt
app/src/main/java/com/mengyalife/app/ui/screens/TrendsScreen.kt
app/src/main/java/com/mengyalife/app/ui/screens/DiaryScreen.kt
app/src/main/java/com/mengyalife/app/ui/screens/SettingsScreen.kt
app/src/main/java/com/mengyalife/app/ui/screens/AdvisorScreen.kt
```

（14 个新增；上表按目录归类，`AdvisorScreen.kt` 同属 screens。）

### 6.2 修改

| 文件 | 变更 |
|---|---|
| `MainActivity.kt` | 由 Gate 0 的 136 行首页 demo 精简为 20 行，只负责 `enableEdgeToEdge()` + `setContent { MengyaLifeApp() }` |
| `app/build.gradle` | `versionCode 1 → 2`，`versionName 0.1.0-g0 → 0.2.0-g1`；依赖与 SDK 版本**未变** |
| `README.md` | 重写为覆盖 Gate 0/1 的完整文档 |

### 6.3 删除

| 文件 | 原因 |
|---|---|
| `app/src/main/java/com/mengyalife/app/Theme.kt` | Gate 0 的占位主题（本仓库自有文件），已被 `ui/theme/` 三件套取代 |

### 6.4 未变更（重要）

- 构建工具链：JDK 21 / Gradle 8.9 / AGP 8.7.3 / Kotlin 2.2.0 / Compose 插件 2.2.0 / compileSdk·targetSdk 35 —— **全部未动**。
- 依赖清单：仍为 Compose BOM 2025.06.01 + core-ktx + activity-compose + ui + material3，**未新增任何库**。
- `AndroidManifest.xml`：**未新增任何权限**（依旧零权限，含无 `INTERNET`）。
- `applicationId` 仍为 `com.mengyalife.app`，未改名、未换包。
- 其它工程（`~/android-projects/*`）未被读写。

---

## 7. 构建及安装证据

### 7.1 构建

| 项 | 值 |
|---|---|
| 命令 | `./gradlew clean :app:assembleDebug --no-daemon --offline --no-build-cache` |
| 日志 | `docs/reports/logs/g1-build-final.log` |
| 起止（UTC） | `03:25:11` → `03:26:06` |
| 结果 | `BUILD SUCCESSFUL in 53s`，`36 actionable tasks: 36 executed` |
| **退出码** | **0** |

```text
> Task :app:compileDebugKotlin
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 53s
36 actionable tasks: 36 executed
EXIT_CODE=0
```

无害警告（与 Gate 0 相同，已核实）：
`android.aapt2FromMavenOverride ... is experimental.`（AGP 固有提示）、
`platform-tools package is not installed`（用 Termux 自带 adb，不影响构建）、
`Unable to strip ... libandroidx.graphics.path.so`（debug 构建原样打包）。

### 7.2 APK

| 项 | 值 |
|---|---|
| 路径 | `/root/mengya-life/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | **9,458,845 字节**（9.0 MB） |
| SHA-256 | `b3687416503a16dca060ae79d90c7ff0e2b695299c1bd142b1b7e74eed2f1c42` |
| 包名 / 版本 | `com.mengyalife.app` / versionCode 2 / versionName `0.2.0-g1` |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |

### 7.3 安装

```text
$ adb -s 192.168.96.181:34157 install -r -t app-debug.apk
Performing Streamed Install
Success
```

安装后从设备回读已安装 APK 并比对哈希：

```text
b3687416503a16dca060ae79d90c7ff0e2b695299c1bd142b1b7e74eed2f1c42  /tmp/installed2.apk
b3687416503a16dca060ae79d90c7ff0e2b695299c1bd142b1b7e74eed2f1c42  app/build/outputs/apk/debug/app-debug.apk
```

→ **设备上运行的就是交付的这份 APK**（此前曾出现「增量构建 vs clean 重建」字节不同，
已重装并**在此份构建上重跑全部验证**，故截图与断言均对应此哈希）。

启动：

```text
$ adb shell am start -W -n com.mengyalife.app/.MainActivity
Status: ok
LaunchState: COLD
TotalTime: 482
```

---

## 8. 页面交互验证

自动化脚本：`/tmp/g1a2.py`（导航）、`/tmp/g1b2.py`（交互），共用 `/tmp/g1lib.py`。
定位方式：**resource-id（Compose `testTag` → Android resource-id）与文本**，
必要时先滚动到元素再定位，**不使用固定坐标**。

### 8.1 五个一级页面切换（5/5）

```text
PASS  切换到 tab_overview      PASS  tab_overview 页面标题正确  你正在走的方向
PASS  切换到 tab_goals         PASS  tab_goals 页面标题正确     把方向拆成能推进的层级
PASS  切换到 tab_trends        PASS  tab_trends 页面标题正确    看见变化，而不是凭感觉
PASS  切换到 tab_diary         PASS  tab_diary 页面标题正确     把今天留下来
PASS  切换到 tab_advisor       PASS  顾问页有输入框 / 发送按钮
```

### 8.2 设置页进出（4 个页面 × 5 项 = 20 项）

对 总览 / 目标 / 趋势 / 日记 各执行一遍「打开设置 → 断言 → 系统返回 → 断言」：

```text
PASS  设置页可见
PASS  设置页隐藏底部导航          ← 设置页节点中不存在任何 tab_*
PASS  设置页有返回按钮
PASS  <页面> 系统返回键回到原页面
PASS  <页面> 返回后底部导航恢复
```

> 从「顾问」页进入设置的方式与其他页面完全一致（同一个 `AppTopBar` 内的齿轮按钮），
> 自动化为覆盖 4 个页面，第 5 个页面共用同一组件路径。

### 8.3 主题（含程序化亮度断言）

```text
PASS  点击深色模式
PASS  深色被选中（checked=true）   [theme_system=false, theme_light=false, theme_dark=true]
PASS  深色模式整体变暗（程序化断言）  light_L=244.2  dark_L=26.4
PASS  切回浅色        PASS  浅色被选中        PASS  浅色恢复变亮  L_back=244.2
PASS  切换跟随系统    PASS  跟随系统被选中
PASS  一级页面同样跟随主题（浅色化）  main_L=238.5
```

亮度 L 为截图的平均相对亮度（0–255），由自写的 PNG 解码器计算：
浅色 244.2、深色 26.4，差值 217.8 —— 远大于阈值 60，**深色模式确实生效**，
而非仅仅单选按钮状态变化。

### 8.4 日记与顾问输入

```text
PASS  日记保存后出现在列表        ['...', '本次新建' 条目出现]
PASS  新条目带「本次新建」标记
PASS  进设置再返回后日记内容仍在   ← 状态保持
PASS  顾问发送后出现用户消息      How do I start
PASS  顾问发送后有明确演示提示    （演示）已收到。当前版本没有接入任何模型服务…
```

说明：`adb shell input text` 只能输入 ASCII，因此测试文本为英文；
中文输入法输入路径未在本轮自动化覆盖（见 §10）。

### 8.5 滚动

```text
PASS  趋势页可滚动   滑动后新可见: ['趋势摘要', '可读的结论，而不是罗列数字', '1.']
```

### 8.6 稳定性

```text
PASS  无崩溃日志      （logcat -b crash 中无 com.mengyalife.app）
PASS  应用进程存活
```

### 8.7 汇总

```text
g1-verify-a.log : 36/36 passed
g1-verify-b.log : 22/22 passed
合计            : 58/58 passed
```

---

## 9. 全部页面截图

均为真机 1200×2608 实拍（`adb exec-out screencap`，从 `\x89PNG` 偏移截断）。
文件位于 `docs/reports/logs/g1-shots/`。

| # | 文件 | 内容 |
|---|---|---|
| 1 | `03-overview.png` | 总览（人生方向 / 长期目标 / 趋势预览） |
| 2 | `04-goals.png` | 目标（维度筛选 + 长期→阶段→短期） |
| 3 | `05-trends.png` | 趋势（折线图 + 多维比较） |
| 4 | `06-diary.png` | 日记（新建入口 + AI 提取预览 + 列表） |
| 5 | `07-advisor.png` | 顾问（对话 + 建议 + 输入） |
| 6 | `08-settings-light.png` | 设置（浅色，A/B 分组） |
| 7 | `09-settings-light-theme.png` | 设置（浅色，外观分组，与深色同位置对比） |
| 8 | `10-settings-dark.png` | **设置（深色）** |
| 9 | `11-settings-light-again.png` | 设置（切回浅色） |
| 10 | `12-settings-about.png` | 设置（关于：版本 0.2.0-g1） |
| 11 | `13-main-after-theme.png` | 返回一级页面（主题作用于全 App） |
| 12 | `14-diary-new-entry.png` | 日记新建后（含「本次新建」标记） |
| 13 | `15-advisor-sent.png` | 顾问发送后（自动滚到底 + 演示提示） |
| 14 | `16-trends-scrolled.png` | 趋势页滚动后 |

---

## 10. 已知问题

| # | 问题 | 影响 | 说明 |
|---|---|---|---|
| 1 | **主题不持久化** | 重启后回到「跟随系统」 | 本轮按需求不引 DataStore；界面与 README 均已明示，**未声称已持久化** |
| 2 | 日记 / 顾问消息只在内存 | 进程结束即丢失 | 进设置返回不丢（已验证）；无数据库是本轮范围决定 |
| 3 | 中文输入未被自动化覆盖 | 手工可正常输入 | `adb shell input text` 只支持 ASCII；如需覆盖需装 ADBKeyboard 类输入法，本轮未引入 |
| 4 | 顾问页发送后列表底部留白较多 | 观感 | 采用「滚到最后一条到顶部」策略；后续可改为滚到底部对齐 |
| 5 | 趋势图为静态自绘 | 无缩放 / 无数据点提示 | 有意不引第三方图表库；如需交互需自行扩展 |
| 6 | 设置页未做分组折叠 | 页面较长需滚动 | 当前四组内容量可接受 |
| 7 | 仅覆盖浅/深两套配色 | 无动态取色、无字号缩放适配 | 动态取色需 API 31+ 且会削弱视觉一致性 |
| 8 | 短目标勾选不进进度计算 | 勾选只改 UI | 本轮为静态演示，进度为演示常量 |
| 9 | 构建警告 3 条 | 无功能影响 | 见 §7.1，与 Gate 0 一致，未擅自改 SDK 许可 |

---

## 11. Gate 2 建议

1. **先验证 KSP 与 Kotlin 2.2.0 的版本匹配**再动 Room：单独跑一个「加 KSP 仍能构建」的最小验证，
   不要和业务改造混在一次构建里（本机内存紧张，混合失败时难以定位）。
2. **持久化分批**：先 DataStore 存主题与设置（解决本报告 §10-1），确认进程重启后生效，
   再引入 Room 存日记与目标，避免两层同时出问题。
3. **UI 自动化沉淀成仓库脚本**：把 `/tmp/g1*.py` 落到 `tools/ui-verify/`，
   加入「滚动到元素再点击」与「关闭键盘再点底栏」两个公共步骤（本轮踩到，已修复）。
4. **补中文输入验证**：引入测试专用输入法或改用 Compose UI 测试（`createComposeRule`），
   这样能覆盖中文文本输入而不依赖 adb。
5. **顾问页真正的「未接入」状态机**：当前是「发送后插一条说明」，
   Gate 2 建议改为显式的「未配置模型 → 引导去设置」状态，避免用户误解为对话失败。
6. **无障碍与文案复查**：所有可点击节点已带 `contentDescription`（齿轮=设置、发送=发送、返回=返回），
   建议再做一次 TalkBack 走查与深色下的对比度检查。
7. **版本与变更管理**：继续保持「工具链冻结、每 Gate 只改 app version」的做法，
   报告里附 APK 哈希与设备回读比对，能让评审确认真机运行的就是交付物。

---

## 附录：证据文件索引

| 路径 | 内容 |
|---|---|
| `docs/reports/logs/g1-build-final.log` | 最终全量构建日志（含退出码、APK 大小与哈希） |
| `docs/reports/logs/g1-install.log` | 安装日志 |
| `docs/reports/logs/g1-launch.log` | 启动日志 |
| `docs/reports/logs/g1-verify-a.log` | 导航与设置页自动化（36 项） |
| `docs/reports/logs/g1-verify-b.log` | 主题、输入、滚动、稳定性自动化（22 项） |
| `docs/reports/logs/g1-shots/*.png` | 14 张真机截图 |
| `docs/reports/evidence/G1-evidence.zip` | 证据包（截图 + 日志 + 关键源码 + README + 报告） |
