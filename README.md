# 萌芽人生 · Mengya Life

> Android 应用。**工程名 MengyaLife**，包名 `com.mengyalife.app`。
> 当前版本 **0.2.0-g1**（Gate 1：UI 框架、五个一级页面与设置页）。
>
> 技术栈：Kotlin + Jetpack Compose + Material 3，本地优先。
> **本轮不接 Room / KSP / AI / 后端**，页面内容全部是明确标注的演示数据。

---

## 1. 当前状态

| 项 | 状态 |
|---|---|
| 五个一级页面（总览 / 目标 / 趋势 / 日记 / 顾问） | ✅ 已完成 |
| 底部 NavigationBar 切换、滚动、状态保持 | ✅ 已验证 |
| 独立设置页（二级页面，五个一级页面顶部齿轮进入） | ✅ 已完成 |
| 设置四项分组（AI 服务 / 数据与隐私 / 外观与体验 / 关于） | ✅ 已完成 |
| 浅色 / 深色 / 跟随系统 即时切换 | ✅ 已验证（含程序化亮度断言） |
| 日记新建、顾问发送等输入交互 | ✅ 已验证 |
| ZeroTermux 本机构建 | ✅ `BUILD SUCCESSFUL`，exit 0 |
| 同机安装 / 启动 / 无崩溃 | ✅ 已验证 |
| Gate 0 报告 | `docs/reports/REPORT-G0-Android-Build.md` |
| Gate 1 报告 | `docs/reports/REPORT-G1-UI-SHELL.md` |

**Gate 1 结论：PASS**，验收项逐条对照见 `REPORT-G1-UI-SHELL.md` §1。

---

## 2. 页面结构与导航

```text
                    ┌──────────────────────────────┐
                    │  顶栏：页面标题 + ⚙ 设置按钮   │  ← 五个一级页面共用
                    └──────────────────────────────┘
                                    │
   ┌──────────┬──────────┬──────────┼──────────┬──────────┐
   │  总览     │  目标     │  趋势     │  日记     │  顾问     │   ← 一级页面
   └──────────┴──────────┴──────────┴──────────┴──────────┘
                                    │
                              ⚙ → 设置页（二级）
                                    │
                         返回箭头 / 系统返回键 → 回到原一级页面
```

| 页面 | 内容 |
|---|---|
| **总览** | 人生方向、长期目标摘要、成长趋势预览、近期洞察、AI 顾问入口 |
| **目标** | 健康 / 职业 / 财富 / 生活 四个维度；长期 → 阶段 → 短期三层结构；维度筛选、短期目标勾选 |
| **趋势** | 自绘折线图（12 周、三条序列）、多维度比较、趋势摘要 |
| **日记** | 记录列表、新建入口（可保存到内存列表）、AI 提取区域（标注「后续接入」） |
| **顾问** | 对话列表、输入框、发送、上下文建议；**发送不会调用任何模型** |
| **设置** | 二级页面，四项分组，见 §3 |

### 导航设计要点

- **导航逻辑集中管理**：`MengyaLifeApp.kt` 用 `currentTab`（一级）+ `showSettings`（二级）两个状态描述全部导航，
  没有为设置页创建侧边栏或伪 Tab。
- **设置不占底部导航位**：进入设置时整棵主界面子树被替换（不是盖一层），
  因此底栏在设置页**根本不存在**（自动化断言：设置页无任何 `tab_*` 节点）。
- **返回行为**：`BackHandler(enabled = showSettings)` → 系统返回键先关闭设置；
  设置页另有左上角返回箭头，行为一致；在主界面按返回则正常退出应用。
- **状态不丢失**：五个页面的滚动位置、日记草稿、顾问输入、维度筛选、勾选状态
  全部**上提**到 `MengyaLifeApp`，进出设置不会销毁，返回后原样恢复。

---

## 3. 设置页

四个分组，全部为静态结构 + 说明；未实现项统一标注「后续接入」，点击只给说明性提示，**不产生虚假成功反馈**。

### A. AI 服务
模型配置、当前配置状态（未配置）、API 服务地址 / 模型 / 密钥入口。
**本轮不接收、不保存、不发送任何 API Key**；应用未申请 `INTERNET` 权限。

### B. 数据与隐私
本地数据管理、数据导入 / 导出、数据采集与授权、删除全部数据。
- 删除按钮**没有任何实际副作用**，点击只提示「功能尚未实现，未删除任何数据」。
- 当前版本不采集数据、无网络权限。

### C. 外观与体验
跟随系统 / 浅色模式 / 深色模式，**立即生效并作用于整个 App**；提醒与通知入口（后续接入）。

> ⚠️ **主题仅当前运行期间有效**：本轮不引入 DataStore，不写 SharedPreferences，
> 进程结束（或系统回收）后回到「跟随系统」。**没有做持久化**，界面与文档均如实标注。

### D. 关于
应用名称「萌芽人生」、英文标识「Mengya Life」、
**当前版本号读取自实际安装的包信息**（`PackageManager.getPackageInfo`，不是写死的假版本）、
产品定位与简介。

---

## 4. 目录结构

```text
MengyaLife/
├── app/src/main/java/com/mengyalife/app/
│   ├── MainActivity.kt              # 唯一 Activity，只负责挂载
│   ├── MengyaLifeApp.kt             # 根组件：导航 + 主题 + 全部页面状态
│   ├── navigation/AppTab.kt         # 五个一级页面的定义（含测试标签）
│   ├── data/DemoData.kt             # 演示数据集中管理
│   ├── ui/theme/                    # Color / Type / Theme（三态主题）
│   ├── ui/components/               # 可复用组件 + 自绘图表
│   └── ui/screens/                  # 6 个页面各自独立 Composable
├── env.sh                           # 项目级环境脚本（source 使用）
├── build.sh                         # 一键构建
├── gradle.properties                # 通用构建参数（无设备路径）
├── docs/reports/
│   ├── REPORT-G0-Android-Build.md
│   ├── REPORT-G1-UI-SHELL.md
│   ├── logs/                        # 构建 / 安装 / 验证日志与截图
│   └── evidence/                    # 证据压缩包
└── README.md
```

---

## 5. 环境事实（本机实测）

| 项 | 实测值 | 取得方式 |
|---|---|---|
| 设备型号 | `2509FPN0BC` | `getprop ro.product.model` |
| Android 版本 / API | Android 17 / **API 37** | `getprop ro.build.version.*` |
| 系统 | HyperOS `V816`（`OS4.0.5.0.XPBCNXM`） | `getprop ro.miui.ui.version.name` |
| CPU / ABI | `aarch64` / `arm64-v8a`，8 核 | `uname -m`、`nproc` |
| 运行环境 | ZeroTermux + proot Ubuntu 24.04.5 LTS | `/etc/os-release` |
| JDK | **OpenJDK 21.0.13**（Termux aarch64） | `java -version` |
| Gradle（Wrapper） | **8.9** | `gradle/wrapper/gradle-wrapper.properties` |
| Android SDK | `$PREFIX/opt/android-sdk`，platform `android-35` | 目录实测 |
| build-tools | **35.0.1** | 目录实测 |
| aapt2（SDK 内置） | ELF64 **x86-64**（`e_machine=0x3e`）→ 无法执行 | 读 ELF 头 |
| aapt2（Termux） | ELF64 **AArch64**（`e_machine=0xb7`）→ 本项目使用 | 读 ELF 头 |
| 磁盘 | 478 GB，可用 ≈132 GB | `df -h /` |

---

## 6. 版本矩阵（Gate 0 冻结，Gate 1 未升级）

| 组件 | 版本 | 依据 |
|---|---|---|
| JDK | 21.0.13 | Termux 已装；AGP 8.7 要求 ≥17 |
| Gradle | **8.9** | AGP 8.7 的最低要求，发行包本机已缓存 |
| AGP | **8.7.3** | 本机既有工程已验证，支持 compileSdk 35 |
| Kotlin (KGP) | **2.2.0** | 官方表：Gradle 7.6.3–8.14、AGP 7.3.1–8.10.0 |
| Compose 编译器插件 | **2.2.0** | 与 Kotlin 同版本 |
| Compose BOM | **2025.06.01** | → compose-ui 1.8.3 / material3 1.3.2 |
| compileSdk / targetSdk | **35 / 35** | 本机仅 platform android-35 |
| minSdk | **26** | 免 multidex / desugaring |
| aapt2 | `$PREFIX/bin/aapt2`（AArch64） | SDK 自带为 x86-64，必须覆盖 |

依据来源：
- KGP 兼容表 <https://kotlinlang.org/docs/gradle-configure-project.html>
- AGP 8.7 发布说明 <https://developer.android.com/build/releases/past-releases/agp-8-7-0-release-notes>

> Gate 1 **没有升级任何构建工具版本**，只把应用版本从 `0.1.0-g0` 提到 `0.2.0-g1`（versionCode 1 → 2）。

---

## 7. 环境配置

### 7.1 `env.sh`（必需）

```sh
export PREFIX=/data/data/com.termux/files/usr   # proot 容器里通常为空，需手动设
. ./env.sh
```

脚本做四件事（全部由 `$PREFIX` 推导，**仓库中不写设备绝对路径**）：
`JAVA_HOME` → `$PREFIX/lib/jvm/java-21-openjdk`；
`ANDROID_HOME` → `$PREFIX/opt/android-sdk`（因此不需要 `local.properties`）；
`GRADLE_USER_HOME` → Termux 侧既有缓存；
`GRADLE_OPTS` 注入 `-Dorg.gradle.project.android.aapt2FromMavenOverride=$PREFIX/bin/aapt2`。

### 7.2 为什么必须覆盖 aapt2（ARM64）

SDK 的 `build-tools/35.0.1/aapt2` 是 x86-64 ELF，在 aarch64 上执行会得到
`Syntax error: "(" unexpected`。本机实测：SDK 版 `e_machine=0x3e`，Termux 版 `e_machine=0xb7`。
`env.sh` 已自动注入覆盖项；构建时出现
`The option setting 'android.aapt2FromMavenOverride=...' is experimental.` 是正常提示，不是错误。

### 7.3 内存参数

```properties
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
org.gradle.parallel=false
org.gradle.workers.max=1
```

---

## 8. 构建

```sh
cd <项目根目录>
export PREFIX=/data/data/com.termux/files/usr
. ./env.sh

./gradlew :app:assembleDebug --no-daemon        # 标准命令
./build.sh                                       # 或一键脚本
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

本机实测：首次冷构建约 3 分钟（需联网下载依赖），之后离线增量约 35–45 秒，
`clean` + `--offline` + `--no-build-cache` 全量重建约 45–60 秒。

---

## 9. 安装与运行

```sh
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export HOME=/data/data/com.termux/files/home     # 关键：配对私钥在 Termux home
adb connect <手机IP>:<无线调试端口>              # 端口在「开发者选项 → 无线调试」查看
adb devices

adb shell input keyevent KEYCODE_WAKEUP          # 必须先唤醒，否则 HyperOS 拒绝安装
adb shell wm dismiss-keyguard
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.mengyalife.app/.MainActivity
```

### 常见问题（本机实际踩到）

| 现象 | 原因 | 处理 |
|---|---|---|
| `INSTALL_FAILED_USER_RESTRICTED` | HyperOS 锁屏/息屏时拒绝 adb 安装 | 先 `KEYCODE_WAKEUP` 唤醒再装 |
| `adb connect` 失败 / `device offline` | `HOME` 指向 `/root` 用了错误 adbkey；或端口已变 | `export HOME=<Termux home>`；重新读取端口 |
| 截图是坏 PNG | `screencap` 会向 stdout 打多屏警告 | 从 `\x89PNG` 偏移截断 |
| `uiautomator dump` 找不到屏外节点 | LazyColumn 只组合可见项 | 先滚动到目标再 dump |
| 输入后点不到底栏 | 软键盘遮挡（布局会 resize） | 先 `input keyevent KEYCODE_BACK` 关闭键盘 |

---

## 10. 已验证结果（Gate 1）

| 验证项 | 结果 |
|---|---|
| 构建 | `BUILD SUCCESSFUL`，exit 0，36 tasks（clean + `--no-build-cache`） |
| APK | 9,458,845 字节，SHA-256 `b3687416503a16dca060ae79d90c7ff0e2b695299c1bd142b1b7e74eed2f1c42` |
| 安装 | `Success`，`versionName=0.2.0-g1`；安装后回读的设备内 APK 与交付 APK **哈希一致** |
| 五个一级页面切换 | 5/5 通过 |
| 每页进入设置 → 返回原页面（含系统返回键） | 4/4 页面 × 5 项断言全部通过 |
| 设置页隐藏底部导航 | 通过（无任何 `tab_*` 节点） |
| 主题三态 + 亮度断言 | 浅色 L≈244、深色 L≈26（程序化断言 `dark < light - 60`） |
| 日记新建 / 保存 / 状态保持 | 通过 |
| 顾问输入 / 发送 / 演示提示 | 通过 |
| 滚动 | 通过 |
| 崩溃 | `logcat -b crash` 无记录，进程存活 |

自动化合计 **58 项断言全部通过**（导航 36 + 交互 22）。
截图见 `docs/reports/logs/g1-shots/`，证据包见 `docs/reports/evidence/`。

---

## 11. 安全与范围声明

- 不引入 Room / KSP / AI / 云服务；`AndroidManifest.xml` **不申请任何权限**（含 `INTERNET`）。
- 仓库**不含**任何 API Key / token；设置页**不接收、不保存**密钥。
- 所有演示数据在界面上均有「演示数据 · 非真实记录」标记；顾问的「回复」明确标注为演示。
- 删除类操作**无任何实际副作用**，也不显示虚假成功提示。
- 未提交任何远程仓库、未发布应用、未修改其它工程。
- 构建脚本与配置文件中**没有**任何设备绝对路径（SDK 由 `ANDROID_HOME` 解析，aapt2 由 `env.sh` 注入）。

---

## 12. 已知限制

1. **主题不持久化**（本轮明确不引 DataStore）：重启回到「跟随系统」。
2. **日记 / 顾问消息只存在内存**：进程结束即丢失，进设置返回不会丢失。
3. **无本地数据库**：所有内容为 `DemoData` 中的静态演示数据。
4. **顾问不是 AI**：发送后仅追加一条标注「演示」的说明，不调用任何模型。
5. **趋势图为自绘 Canvas**：无缩放、无交互提示、无数据 label。
6. 仅浅色 / 深色两套配色，未做动态取色（Dynamic Color）与字号缩放适配。

---

## 13. 下一步（Gate 2，未开始）

按评审结论再启动。候选方向：Room 本地数据层（需先单独验证 KSP 与 Kotlin 2.2.0 的版本匹配）、
设置页真正落地（DataStore 持久化主题与偏好）、以及「用户自带 API Key 的本地安全存储」设计。
