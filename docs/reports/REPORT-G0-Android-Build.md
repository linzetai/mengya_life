# Gate 0 报告 · ZeroTermux 本机构建验证

| 项 | 值 |
|---|---|
| 项目 | 萌芽人生 / Mengya Life |
| 工程名 | MengyaLife |
| applicationId | `com.mengyalife.app` |
| 轮次 | Gate 0（本机构建验证） |
| 执行时间 | 2026-11-02 00:38 – 01:16（Asia/Shanghai） |
| 报告路径 | `docs/reports/REPORT-G0-Android-Build.md` |
| 结论 | **PASS（A–E 全部通过）** |

---

## 1. 结论状态

**PASS**

在 Android 手机本机（ZeroTermux + proot Ubuntu，aarch64）完成：

1. 最小 Kotlin + Jetpack Compose + Material 3 单模块工程创建；
2. `./gradlew :app:assembleDebug --no-daemon` 构建成功，退出码 `0`，产出可校验的 debug APK；
3. 同一台手机通过 `adb` **安装成功、冷启动成功、按钮点击产生可见状态变化**，并以 UI dump + 截图取证；
4. 构建可从零复现：`clean` + `--offline` + `--no-build-cache` 全量重建，**APK SHA-256 与首次构建完全一致**；
5. 全程无云编译、无远程推送、无密钥、无越界修改。

### 验收项对照

| # | 验收标准 | 结果 | 证据 |
|---|---|---|---|
| A | 工程、README、版本矩阵及报告完整 | ✅ | `README.md` §3/§4、本报告 §2/§3 |
| B | ZeroTermux 本机完成 Kotlin/Compose 构建，产生可校验 APK | ✅ | 本报告 §4/§5，`logs/build-1..3*.log` |
| C | APK 同机安装、启动成功，按钮交互实际验证 | ✅ | 本报告 §6，`logs/install.log`、`logs/shots/*.png`、`logs/ui_*.xml` |
| D | 构建方法可复现 | ✅ | 本报告 §5.3（三次构建，两次 clean 重建，哈希一致） |
| E | 无越界修改、无密钥泄露、无无关业务实现 | ✅ | 本报告 §3、§9 |

---

## 2. 环境事实（实测）

> 采集方式均为只读命令；未打印任何密钥或全量环境变量。

### 2.1 设备与运行时

| 项 | 值 | 命令 |
|---|---|---|
| 机型 | `2509FPN0BC` | `getprop ro.product.model` |
| Android | 17（**API 37**），`OS4.0.5.0.XPBCNXM` | `getprop ro.build.version.*` |
| 系统 UI | HyperOS `V816` | `getprop ro.miui.ui.version.name` |
| CPU | `aarch64`，8 核 | `uname -m` / `nproc` |
| ABI | `arm64-v8a` | `getprop ro.product.cpu.abi` |
| 容器 | Termux + proot **Ubuntu 24.04.5 LTS** | `/etc/os-release` |
| `$PREFIX` | `/data/data/com.termux/files/usr` | 目录存在性检查 |
| 屏幕 | 内置屏 1200×2608，density 480；另有副屏 `mDisplayId=1`（976×596，state OFF） | `dumpsys display` |

### 2.2 工具链

| 项 | 值 |
|---|---|
| JDK | OpenJDK **21.0.13**（Termux aarch64），`javac 21.0.13` |
| 备用 JDK | OpenJDK 17（存在，未使用） |
| Gradle Wrapper | **8.9**（分发包 `gradle-8.9-bin` 已在本机 `~/.gradle/wrapper/dists`） |
| Gradle 全局 | 9.8.2（**未使用**，与 AGP 8.7 不兼容） |
| Android SDK | `$PREFIX/opt/android-sdk`；platforms 仅 `android-35` |
| build-tools | `35.0.1`（唯一） |
| aapt2 | SDK 内置：ELF64 `e_machine=0x3e`（**x86-64**）→ 不可执行<br>Termux：`$PREFIX/bin/aapt2`，ELF64 `e_machine=0xb7`（**AArch64**）→ 已覆盖 |
| adb | `1.0.41` / `37.0.0p1-android-tools`（Termux 版） |
| 无线调试 | `127.0.0.1:46771`（**动态端口**，会随重启变化） |

### 2.3 资源

| 项 | 值 |
|---|---|
| 内存 | 10 GiB 总；构建时 available ≈ 2.8 GiB；swap 已用 5.8 GiB |
| 磁盘 | 478 GB，已用 345 GB（73%），可用 133 GB |
| `~/.gradle` 缓存 | 构建前 500 MB → 构建后 926 MB（**+426 MB**，为 Kotlin 2.2.0 / Compose / AndroidX 依赖） |
| 工程 `app/build` | 62 MB |

### 2.4 既有工程（只读，未改动）

`/data/data/com.termux/files/home/android-projects/` 下已有 `hello-zero`、`counter-lab`、`pulseboard`（均为 **Java/XML，未使用 Compose**）。

- 本工程仅从中**复制** Gradle 8.9 Wrapper（`gradlew`、`gradle-wrapper.jar`、`gradle-wrapper.properties`）；
- 源工程未做任何写入/删除（已用 `ls -l` 比对确认）；
- 工作目录 `/root/mengya-life` 原为空目录，**无既有 Git 仓库**（`fatal: not a git repository`）。

---

## 3. 变更清单

### 3.1 新增文件（全部为本轮新建，无覆盖、无删除）

| 文件 | 说明 |
|---|---|
| `settings.gradle` | 模块声明 + 镜像仓库（aliyun 优先，google/mavenCentral 兜底） |
| `build.gradle` | 插件版本声明：AGP 8.7.3 / Kotlin 2.2.0 / Compose 插件 2.2.0 |
| `gradle.properties` | 内存与并发保守参数；**不含设备路径** |
| `gradlew`、`gradlew.bat`、`gradle/wrapper/*` | Gradle 8.9 Wrapper（从本机既有工程复制） |
| `env.sh` | 项目级环境脚本（JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME / aapt2 覆盖） |
| `build.sh` | 一键构建包装脚本 |
| `.gitignore` | 忽略 build/、.gradle/、*.apk 等；保留 `docs/reports/logs/*.log` 证据 |
| `app/build.gradle` | 模块配置：compileSdk 35 / targetSdk 35 / minSdk 26；Compose BOM 2025.06.01 |
| `app/src/main/AndroidManifest.xml` | 单 Activity；**零权限** |
| `app/src/main/java/com/mengyalife/app/MainActivity.kt` | 首页 + 按钮交互（唯一状态：点击次数） |
| `app/src/main/java/com/mengyalife/app/Theme.kt` | Material 3 固定浅色主题 |
| `app/src/main/res/values/{strings,themes,colors}.xml` | 应用名「萌芽人生」、窗口主题、配色 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 纯 vector 图标前景（无二进制资源） |
| `app/src/main/res/mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml` | 自适应图标（minSdk 26） |
| `README.md` | 工具版本、环境配置、构建/安装方法、常见问题 |
| `docs/reports/REPORT-G0-Android-Build.md` | 本报告 |
| `docs/reports/logs/*` | 构建/安装/启动日志、UI dump、3 张截图 |

### 3.2 未做的事（范围控制）

- ❌ 未实现任何业务功能：无 AI、无网络请求、无 Room、无设置页、无导航；
- ❌ 未申请任何权限（连 `INTERNET` 都没有）；
- ❌ 未修改全局 `~/.bashrc`、未执行 `pkg upgrade`、未改动手机系统设置；
- ❌ 未提交远程仓库、未发布应用、未获取/写入任何 API Key；
- ❌ 未删除或重置任何既有文件。

### 3.3 Git

工作目录原本不是 Git 仓库。为便于评审变更，本轮初始化了**本地**仓库（无远端），
仅做仓库级 `user.name/user.email` 配置（未改全局 git 配置）。
提交：`351e7e3 Gate 0: 最小 Kotlin/Compose 工程 + ZeroTermux 本机构建验证`（工作区干净）。

### 3.4 路径与敏感信息合规（已逐项校验）

| 检查 | 方法 | 结果 |
|---|---|---|
| 构建脚本/配置是否含设备绝对路径 | 对**所有被 git 跟踪**的非文档文件执行 `grep -E "/data/data\|/root/\|/sdcard"` | ✅ 无命中 |
| 文档中的 `PREFIX` 示例 | `README.md` / 本报告 | 出现的 `/data/data/com.termux/files/usr` 是 **Termux 通用 `$PREFIX` 取值**（所有 Termux 安装一致、与机型无关），仅用于说明如何设置环境变量；构建链路本身由 `$PREFIX` 推导 |
| 是否含密钥/令牌 | 正则扫描 `api_key=`、`sk-xxxx`、`token=` 等 | ✅ 无命中 |
| 是否误提交产物 | `git ls-files \| grep -E "\.apk$\|/build/"` | ✅ 无命中（`.gitignore` 生效） |
| 构建产物中是否含敏感串 | 仅含代码、资源与调试签名 | ✅ |

> 校验过程中曾在 `env.sh` 的错误提示与注释里发现 `PREFIX` 字面值，
> 已改写为指向 README 的说明；改写后重新执行构建，退出码仍为 `0`、APK 哈希不变。


---

## 4. 版本矩阵

### 4.1 选定组合

| 组件 | 版本 | 依据 |
|---|---|---|
| JDK | 21.0.13 | Termux 已装；AGP 8.7 要求 ≥17，Gradle 8.9 支持 21 |
| Gradle | **8.9** | AGP 8.7 的最低要求；发行包本机已缓存 |
| AGP | **8.7.3** | 本机既有工程已验证；支持 compileSdk 35 |
| Kotlin (KGP) | **2.2.0** | 官方兼容表：Gradle 7.6.3–8.14，AGP 7.3.1–8.10.0 |
| Compose Compiler 插件 | **2.2.0** | 与 Kotlin 同版本（Kotlin 2.0+ 起随 Kotlin 发布） |
| Compose BOM | **2025.06.01** | → compose-ui 1.8.3 / material3 1.3.2 |
| compileSdk / targetSdk | **35 / 35** | 本机仅装 platform android-35；设备 API 37 可正常安装 target 35 应用 |
| minSdk | **26** | 覆盖面足够，免 multidex/desugaring |
| build-tools | **35.0.1** | 本机唯一版本，与 compileSdk 35 匹配 |
| aapt2 | `$PREFIX/bin/aapt2`（AArch64） | SDK 自带为 x86-64，必须覆盖 |
| AndroidX | core-ktx 1.16.0、activity-compose 1.10.1、compose-ui 1.8.3、material3 1.3.2 | BOM + 显式版本，构建后实测解析结果 |

### 4.2 官方资料核对（含排除过程）

官方兼容表：<https://kotlinlang.org/docs/gradle-configure-project.html>

| KGP | Gradle | AGP | 对本项目（Gradle 8.9 + AGP 8.7.3）是否可接受 |
|---|---|---|---|
| 2.0.20–2.0.21 | 6.8.3–8.8* | 7.1.3–**8.5** | ❌ AGP 8.7.3 超出上限 |
| 2.1.0–2.1.10 | 7.6.3–8.10* | 7.3.1–**8.7.2** | ❌ AGP 8.7.3 超出上限 |
| 2.1.20–2.1.21 | 7.6.3–8.12.1 | 7.3.1–**8.7.2** | ❌ AGP 8.7.3 超出上限 |
| **2.2.0** | **7.6.3–8.14** | **7.3.1–8.10.0** | ✅ 两者均在官方区间内 |
| 2.2.10+ | 7.6.3–8.14 | 7.3.1–8.10.0 | ✅ 亦可行，此处取 2.2.0 与 Compose 插件严格同版 |

AGP 最低要求（Gradle 8.9 / JDK 17）：<https://developer.android.com/build/releases/past-releases/agp-8-7-0-release-notes>

### 4.3 实测校验（不止看文档）

1. 所有 artifact 在本机可达镜像上逐一验证 POM 返回 HTTP 200：
   `kotlin-gradle-plugin:2.2.0`、`compose-compiler-gradle-plugin:2.2.0`、`gradle:8.7.3`、
   `core-ktx:1.16.0`、`activity-compose:1.10.1`、`compose-bom:2025.06.01`。
2. 读取 BOM 的 POM 确认 material3 = 1.3.2；
   构建后从 Gradle 缓存确认实际解析为 compose-ui **1.8.3**。
3. 两个 aapt2 的 ELF `e_machine` 实测为 `0x3e` / `0xb7`，证明覆盖是必要的（而非猜测）。

> 环境偏差说明：本机直连 `developer.android.com` 超时（`web_fetch` 与 `curl` 均失败），
> 因此 AGP 侧结论以官方发布说明链接 + 本机镜像 POM 实证 + 实际构建结果三重印证，
> 而非仅依赖单页文档。

---

## 5. 构建日志与退出码

### 5.1 构建一：首次冷构建（联网）

- 命令：`./gradlew :app:assembleDebug --no-daemon`（先 `source env.sh`）
- 日志：`docs/reports/logs/build-1.log`
- 时间：`2026-11-01T16:55:37Z` → `16:58:21Z`
- **退出码：0**，`BUILD SUCCESSFUL in 2m 42s`，35 tasks executed（全部真正执行，无缓存）

关键行：

```text
WARNING: The option setting 'android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2' is experimental.
> Task :app:processDebugResources
> Task :app:compileDebugKotlin
...
BUILD SUCCESSFUL in 2m 42s
35 actionable tasks: 35 executed
```

### 5.2 构建二：`clean` + 离线

- 命令：`./gradlew clean :app:assembleDebug --no-daemon --offline`
- 日志：`docs/reports/logs/build-2-clean-offline.log`
- **退出码：0**，`BUILD SUCCESSFUL in 30s`，17 executed / 19 from cache
- SHA-256 与构建一**一致**

### 5.3 构建三：`clean` + 离线 + **禁用构建缓存**（真·全量重编译）

- 命令：`./gradlew clean :app:assembleDebug --no-daemon --offline --no-build-cache`
- 日志：`docs/reports/logs/build-3-clean-nocache-offline.log`
- **退出码：0**，`BUILD SUCCESSFUL in 46s`，**36 actionable tasks: 36 executed**
- SHA-256 与构建一、二**完全一致** → 构建可复现且确定

### 5.4 构建中的警告（均为无害，已核实）

| 警告 | 判定 |
|---|---|
| `The option setting 'android.aapt2FromMavenOverride=...' is experimental.` | AGP 对该开关的固有提示，功能正常（资源编译任务成功） |
| `Warning: License for package Android SDK Platform-Tools not accepted.` / `platform-tools package is not installed` | AGP 想顺带安装 SDK 版 platform-tools；本项目用 Termux 自带 adb，**不影响构建**，未去接受许可或改 SDK |
| `Unable to strip the following libraries ... libandroidx.graphics.path.so` | 该 native 库无对应 strip 工具链产物，原样打包；debug 构建无害 |

**没有出现** aapt2 的 `Syntax error: "(" unexpected`——说明 ARM64 适配生效。

---

## 6. APK 证据

| 项 | 值 |
|---|---|
| 绝对路径 | `/root/mengya-life/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | **9,294,831 字节**（8.9 MB） |
| SHA-256 | `1649e09cd257a4d0b173933df678539aa1ac2caaf5850ff09652d2cb58ba1e8f` |
| MD5 | `9bc3303e22098091011c6c928c29029f` |
| 包名 / 版本 | `com.mengyalife.app`，versionCode `1`，versionName `0.1.0-g0` |
| SDK | minSdk 26 / targetSdk 35 / compileSdk 35 |
| 应用名 | `萌芽人生`（`aapt2 dump badging` → `application-label:'萌芽人生'`） |
| 内容 | 111 个条目，含 dex、resources.arsc、`lib/arm64-v8a/libandroidx.graphics.path.so` |

三次独立构建（含一次完全禁用缓存的全量重编译）产出的 SHA-256 完全相同。

---

## 7. 安装 / 交互证据

### 7.1 连接

```text
$ adb connect 127.0.0.1:46771
connected to 127.0.0.1:46771
$ adb devices -l
127.0.0.1:46771  device product:popsicle model:2509FPN0BC device:popsicle transport_id:1
```

### 7.2 安装

首次三次安装**全部失败**，原始记录保留在 `docs/reports/logs/install-attempts.log`：

```text
adb install          -> Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]
adb push + pm install -> Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]
adb install --user 0  -> Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]
```

**根因**：HyperOS 在屏幕锁定状态下拒绝 adb 安装（不是权限、不是签名问题）。
**修复**：唤醒屏幕后重试：

```text
$ adb shell input keyevent KEYCODE_WAKEUP
$ adb shell wm dismiss-keyguard
$ adb install -r -t app-debug.apk
Performing Streamed Install
Success          # EXIT=0, 2026-11-02 01:13:14
```

安装位置：`package:/data/app/~~6hbOrsQHcE2riWDJDCaC2w==/com.mengyalife.app-pOreEnx_CLorhq72CyuW4w==/base.apk`

### 7.3 启动

```text
$ adb shell am start -W -n com.mengyalife.app/.MainActivity
Status: ok
LaunchState: COLD
Activity: com.mengyalife.app/.MainActivity
TotalTime: 351
Complete          # EXIT=0
```

`topResumedActivity=ActivityRecord{... com.mengyalife.app/.MainActivity}`

### 7.4 按钮交互（UI dump 前后对比）

点击坐标 (600, 1648) —— 取自 UI dump 中按钮节点的 bounds 中心。

| 时刻 | UI 文本（`uiautomator dump`） |
|---|---|
| 点击前 | `萌芽人生` \| `Mengya Life` \| **`状态：等待交互`** \| **`点击次数：0`** \| `点我一下` \| `Gate 0 · 本机构建验证` |
| 点击 1 次后 | `萌芽人生` \| `Mengya Life` \| **`状态：交互已验证（1 次）`** \| **`点击次数：1`** \| `点我一下` \| `Gate 0 · 本机构建验证` |
| 再点 2 次后 | `萌芽人生` \| `Mengya Life` \| **`状态：交互已验证（3 次）`** \| **`点击次数：3`** \| `点我一下` \| `Gate 0 · 本机构建验证` |

状态文本与计数器**同步变化**，即点击产生了可见状态变化（验收 C 的核心要求）。

### 7.5 截图

| 文件 | 内容 |
|---|---|
| `docs/reports/logs/shots/01-launch.png` | 启动后：状态「等待交互」，计数 0 |
| `docs/reports/logs/shots/02-after-tap1.png` | 点击 1 次：状态「交互已验证（1 次）」，计数 1 |
| `docs/reports/logs/shots/03-after-tap3.png` | 点击 3 次：状态「交互已验证（3 次）」，计数 3 |

（截图为 1200×2608 物理分辨率；按 skill 要求从 `\x89PNG` 偏移截断，避免 screencap 多屏警告污染。）

### 7.6 稳定性

| 检查 | 结果 |
|---|---|
| 进程 | `u0_a439 15550 ... com.mengyalife.app`（存活） |
| 崩溃缓冲 | `adb shell logcat -b crash -d` 无 `com.mengyalife.app` 记录 |
| force-stop 后冷启动 | 回到 `状态：等待交互 / 点击次数：0`（符合 `rememberSaveable` 语义：进程内保持、进程结束复位） |
| 已安装版本 | `versionName=0.1.0-g0`，`minSdk=26`，`targetSdk=35`，`firstInstallTime=2026-11-02 01:13:14` |

---

## 8. 复现步骤（从零）

前置：手机已开启开发者选项与「无线调试」，Termux 已装 JDK 21 / android-sdk(35) / build-tools 35.0.1 / aapt2 / adb。

```sh
# 0) 进入工程
cd /root/mengya-life
export PREFIX=/data/data/com.termux/files/usr
. ./env.sh

# 1) 构建（首次需联网下载依赖；之后可加 --offline）
./gradlew :app:assembleDebug --no-daemon

# 2) 校验产物
sha256sum app/build/outputs/apk/debug/app-debug.apk
# 期望：1649e09cd257a4d0b173933df678539aa1ac2caaf5850ff09652d2cb58ba1e8f
du -b app/build/outputs/apk/debug/app-debug.apk
# 期望：9294831

# 3) 连接本机（端口以「无线调试」界面显示为准）
export PATH=$PREFIX/bin:$PATH
export HOME=/data/data/com.termux/files/home
adb connect 127.0.0.1:<端口>
adb devices

# 4) 安装（务必先唤醒屏幕，否则 HyperOS 会拒绝）
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# 5) 启动并验证
adb shell am force-stop com.mengyalife.app
adb shell am start -W -n com.mengyalife.app/.MainActivity
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml .
adb shell input tap 600 1648        # 按钮中心；如分辨率不同请从 UI dump 取 bounds
adb shell uiautomator dump /sdcard/ui2.xml && adb pull /sdcard/ui2.xml .
# 期望 ui2.xml 中出现「状态：交互已验证（1 次）」与「点击次数：1」
```

---

## 9. 风险与限制

| 风险 | 影响 | 缓解 |
|---|---|---|
| **构建确定性依赖缓存**：三次构建哈希一致，但这是 AGP 规范化 ZIP 时间戳 + 固定调试签名密钥的结果；换机器可能不同 | 不能把 SHA-256 当作跨环境校验值 | README 中只把哈希作为**本机**证据；复现以「构建成功 + 同机安装」为准 |
| **依赖依赖国内镜像**（aliyun） | 镜像缺某个版本时会构建失败 | `settings.gradle` 保留 `google()` / `mavenCentral()` 兜底 |
| **`~~/.gradle` 缓存增长 +426 MB**（现 926 MB），磁盘已用 73% | 后续 Gate 会继续增长 | 定期 `./gradlew clean`；必要时删旧 artifacts；可用空间仍有 133 GB |
| **内存紧张**（available ≈ 2.8 GiB） | 加大堆/并行易被 LMK 杀；daemon 构建后消失属正常 | 已固定 `-Xmx1536m`、`parallel=false`、`workers.max=1`；构建统一 `--no-daemon` |
| **无线调试端口动态** | 端口变化后 `adb connect` 失败 | 每次从「无线调试」界面取端口；`HOME` 必须指向 Termux home 以复用配对私钥 |
| **HyperOS 锁屏期拒绝 adb 安装** | 安装静默失败，`INSTALL_FAILED_USER_RESTRICTED` | 安装前唤醒屏幕；已在 README/报告中固化为步骤 |
| **aapt2 覆盖属 AGP「experimental」开关** | 未来 AGP 版本可能改名 | 升级 AGP 时需同步复核 `android.aapt2FromMavenOverride` |
| **仅 compileSdk 35** | AGP 8.7 上限即 35；上到 36 需同时升 AGP/Gradle/build-tools | 下一轮若需 36，走整体版本矩阵升级并重新验证 |
| **零权限** | 尚无网络能力（符合本轮范围） | Gate 1 引入 AI 时才加 `INTERNET`，并单独做权限评审 |
| **无图标本地化 / 无深色主题** | 仅 Gate 0 占位 | 后续 UI 轮次补齐 |

---

## 10. 下一轮建议（Gate 1，尚未开始）

1. **先做「版本矩阵冻结」**：把本报告 §4 固化为 `docs/VERSIONS.md`，后续升级必须附构建证据。
2. **Room 引入有前置条件**：Room 需 KSP（`com.google.devtools.ksp`），KSP 版本必须与 Kotlin 2.2.0 严格对应（`2.2.0-2.0.x` 系列），需先单独验证一次「加 KSP 后仍能构建」再写业务。
3. **建立 CI-free 的自检脚本**：`tools/verify.sh`，一条命令跑「构建 → 哈希 → 安装 → 冷启动 → 按钮/页面断言」，避免每轮手工取证。
4. **UI 自动化替代坐标点击**：本轮用固定坐标 (600,1648) 点击，分辨率一变换就失效；建议 Gate 1 起在 Compose 中开 `testTagsAsResourceId` 或用 `uiautomator` 按文本定位。
5. **API Key 存储方案提前设计但先不实现**：Android Keystore + 加密偏好；本轮<u>未</u>索取也未写入任何真实 Key。
6. **待人工确认项（可选）**：`platform-tools` 的 SDK 许可未接受导致构建出现一条 WARNING；若希望消除，可自行决定是否写入 SDK licenses，本轮**未改动**该状态。

---

## 附录：证据文件索引

| 文件 | 内容 |
|---|---|
| `docs/reports/logs/build-1.log` | 首次冷构建完整日志（含退出码 0） |
| `docs/reports/logs/build-2-clean-offline.log` | clean + offline 构建 |
| `docs/reports/logs/build-3-clean-nocache-offline.log` | clean + offline + no-build-cache 全量重建 |
| `docs/reports/logs/install.log` | 首次安装失败记录 |
| `docs/reports/logs/install-attempts.log` | 三次失败尝试 + 唤醒后成功记录 |
| `docs/reports/logs/launch.log` | 启动日志（COLD / 351 ms） |
| `docs/reports/logs/ui_1.xml` | 点击前 UI 层级 |
| `docs/reports/logs/ui_2.xml` | 点击 1 次后 UI 层级 |
| `docs/reports/logs/ui_3.xml` | 点击 3 次后 UI 层级 |
| `docs/reports/logs/ui_4-relaunch.xml` | force-stop 后冷启动 UI 层级（状态复位） |
| `docs/reports/logs/shots/01-launch.png` | 启动截图 |
| `docs/reports/logs/shots/02-after-tap1.png` | 点击 1 次截图 |
| `docs/reports/logs/shots/03-after-tap3.png` | 点击 3 次截图 |
