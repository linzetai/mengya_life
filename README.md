# 萌芽人生 · Mengya Life

> Android 应用。**工程名 MengyaLife**，包名 `com.mengyalife.app`。
> 本轮为 **Gate 0：ZeroTermux 本机构建验证**，只验证工具链与最小 Compose 界面，
> 不含任何业务功能（无 AI、无网络、无数据库）。

---

## 1. 本仓库当前状态（Gate 0）

| 项 | 状态 |
|---|---|
| Kotlin + Jetpack Compose + Material 3 单模块工程 | ✅ 已完成 |
| ZeroTermux（本机 aarch64）`./gradlew :app:assembleDebug` 构建 | ✅ BUILD SUCCESSFUL |
| debug APK 产出 | ✅ `app/build/outputs/apk/debug/app-debug.apk`（9,294,831 字节） |
| 同机安装 / 启动 / 按钮交互 | ✅ 已用 adb 验证（见 §8） |
| 离线 clean 重建可复现 | ✅ 字节级一致（见 §8.4） |
| README / 版本矩阵 / 报告 | ✅ `docs/reports/REPORT-G0-Android-Build.md` |

**结论：PASS（A–E 全部满足）**，详见 Gate 0 报告。

---

## 2. 目录结构

```text
MengyaLife/
├── app/
│   ├── build.gradle                     # 模块构建脚本（AGP + Kotlin + Compose）
│   └── src/main/
│       ├── AndroidManifest.xml          # 无任何权限（含 INTERNET）
│       ├── java/com/mengyalife/app/
│       │   ├── MainActivity.kt          # 首页：萌芽人生 / Mengya Life / 按钮交互
│       │   └── Theme.kt                 # Material 3 主题（固定浅色）
│       └── res/                         # 字符串、主题、自适应图标（纯 vector）
├── build.gradle                         # 根脚本：插件版本声明
├── settings.gradle                      # 仓库镜像 + 模块声明
├── gradle.properties                    # 通用构建参数（无设备路径）
├── gradle/wrapper/                      # Gradle 8.9 Wrapper
├── gradlew / gradlew.bat
├── env.sh                               # 项目级环境脚本（source 使用）
├── build.sh                             # 一键构建包装脚本
├── docs/reports/
│   ├── REPORT-G0-Android-Build.md       # Gate 0 报告
│   └── logs/                            # 构建日志、安装日志、UI dump、截图
└── README.md
```

---

## 3. 环境事实（本机实测，非推测）

| 项 | 实测值 | 取得方式 |
|---|---|---|
| 设备型号 | `2509FPN0BC` | `getprop ro.product.model` |
| Android 版本 / API | Android 17 / **API 37** | `getprop ro.build.version.release` / `.sdk` |
| 系统 | HyperOS `V816`（`OS4.0.5.0.XPBCNXM`） | `getprop ro.miui.ui.version.name` |
| CPU / ABI | `aarch64` / `arm64-v8a`，8 核 | `uname -m`、`nproc` |
| 运行环境 | ZeroTermux + proot Ubuntu 24.04.5 LTS | `/etc/os-release` |
| Termux PREFIX | `/data/data/com.termux/files/usr` | 实测存在 |
| JDK | **OpenJDK 21.0.13**（Termux aarch64） | `java -version` |
| 备用 JDK | OpenJDK 17（未使用） | `$PREFIX/lib/jvm/` |
| Gradle（Wrapper） | **8.9**（发行包已在本机缓存） | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle（系统全局） | 9.8.2，**本项目不使用** | `gradle --version` |
| Android SDK | `$PREFIX/opt/android-sdk`，platform **android-35** | 目录实测 |
| build-tools | **35.0.1** | 目录实测 |
| aapt2（SDK 内置） | ELF64 **x86-64**（`e_machine=0x3e`）→ aarch64 无法执行 | 读 ELF 头 |
| aapt2（Termux） | ELF64 **AArch64**（`e_machine=0xb7`）→ 本项目使用 | 读 ELF 头 |
| adb | 1.0.41（`37.0.0p1-android-tools`），Termux 版 | `adb version` |
| 无线调试 | `127.0.0.1:46771`（端口动态，重启后会变） | `adb devices` |
| 内存 | 10 GiB 总 / 约 2.8 GiB available，swap 已用 5.8 GiB | `free -h` |
| 磁盘 | 478 GB，已用 345 GB（73%），可用 133 GB | `df -h /` |

---

## 4. 版本矩阵与选择依据

### 4.1 选定组合（本机已验证）

| 组件 | 版本 | 选择理由 |
|---|---|---|
| JDK | **21.0.13** | Termux 已装；AGP 8.x 要求 JDK ≥17，Gradle 8.9 支持 JDK 21 |
| Gradle | **8.9** | AGP 8.7 的**最低要求**即 8.9；且 8.9 发行包已在本机缓存，无需下载 |
| AGP | **8.7.3** | 本机既有工程已在用、依赖已缓存；支持 compileSdk 35 |
| Kotlin (KGP) | **2.2.0** | 官方表：2.2.0 支持 Gradle 7.6.3–8.14、AGP 7.3.1–8.10.0 → 与 Gradle 8.9 + AGP 8.7.3 **同时落在官方区间内** |
| Compose 编译器插件 | **2.2.0** | 与 Kotlin 同版本（Kotlin 2.0+ 起独立发布） |
| Compose BOM | **2025.06.01** | 该 BOM 解析为 compose-ui **1.8.3**、material3 **1.3.2** |
| compileSdk | **35** | 本机只安装了 platforms/android-35；AGP 8.7 支持 35 |
| targetSdk | **35** | 与 compileSdk 一致 |
| minSdk | **26** | 覆盖面足够，且免去 multidex/desugaring 复杂度 |
| build-tools | **35.0.1** | 本机唯一已装版本，与 compileSdk 35 匹配 |
| aapt2 | `$PREFIX/bin/aapt2`（**AArch64**） | SDK 自带为 x86-64，必须覆盖 |
| AndroidX | core-ktx 1.16.0 / activity-compose 1.10.1 / compose-ui 1.8.3 / material3 1.3.2 | 由 BOM + 显式版本解析 |

### 4.2 依据来源

- Kotlin Gradle Plugin ↔ Gradle ↔ AGP 兼容表（官方）：
  <https://kotlinlang.org/docs/gradle-configure-project.html>
  > 2.2.0：Gradle `7.6.3–8.14`，AGP `7.3.1–8.10.0`
  > 2.0.20–2.0.21：AGP 只到 `8.5`（故**未选** Kotlin 2.0.x）
  > 2.1.x：AGP 只到 `8.7.2`（故**未选** 2.1.x，8.7.3 超出官方区间）
- AGP 8.7 发布说明（最低 Gradle 8.9 / JDK 17）：
  <https://developer.android.com/build/releases/past-releases/agp-8-7-0-release-notes>
- Compose BOM → 库版本映射：直接读取 BOM 的 POM（`compose-bom:2025.06.01`），
  确认 material3 = 1.3.2；构建后实测 compose-ui = 1.8.3。
- 所有版本均在本机 Maven 镜像上校验过 POM 可取（HTTP 200），并对最终解析结果做了实测。

### 4.3 为什么不用手机上的全局 Gradle 9.8.2

AGP 8.7.3 与 Gradle 9.x 不兼容（AGP 8.x 官方上限远低于 9.0）。
本项目一律使用 **Wrapper 8.9**，保证版本可复现，且不受手机全局 Gradle 升级影响。

---

## 5. 环境配置

### 5.1 `env.sh`（必需，`source` 使用）

```sh
export PREFIX=/data/data/com.termux/files/usr   # 在 proot 容器里通常为空，需手动设
. ./env.sh
```

脚本做四件事（全部由 `$PREFIX` 推导，**仓库中不出现设备绝对路径**）：

1. `JAVA_HOME=$PREFIX/lib/jvm/java-21-openjdk`
2. `ANDROID_HOME=$PREFIX/opt/android-sdk`（因此**不需要** `local.properties`）
3. `GRADLE_USER_HOME` 指向 Termux 侧既有缓存，复用 gradle-8.9 发行包与已下载依赖
4. 通过 `GRADLE_OPTS` 注入 `-Dorg.gradle.project.android.aapt2FromMavenOverride=$PREFIX/bin/aapt2`

### 5.2 为什么必须覆盖 aapt2（ARM64 适配）

Android SDK 的 `build-tools/35.0.1/aapt2` 是 **x86-64** ELF；在 aarch64 手机上执行会得到：

```
Syntax error: "(" unexpected
```

（x86-64 机器码被当作 shell 脚本解析）

本机实测两个二进制：

| 路径 | ELF class | `e_machine` |
|---|---|---|
| `$PREFIX/opt/android-sdk/build-tools/35.0.1/aapt2` | ELF64 | `0x3e` = **x86-64** |
| `$PREFIX/bin/aapt2`（Termux 提供） | ELF64 | `0xb7` = **AArch64** |

覆盖方式：`env.sh` 里用系统属性注入，脚本本身不写死绝对路径；也可手工显式传入：

```sh
./gradlew :app:assembleDebug --no-daemon \
  -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2"
```

> 构建时 Gradle 会打印 `The option setting 'android.aapt2FromMavenOverride=...' is experimental.`
> 这是 AGP 对该开关的固有提示，**不是错误**。

### 5.3 内存参数

`gradle.properties` 保守配置，适配手机紧张的内存：

```properties
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
org.gradle.parallel=false
org.gradle.workers.max=1
```

---

## 6. 构建

```sh
cd <项目根目录>
export PREFIX=/data/data/com.termux/files/usr
. ./env.sh

# 方式一：标准命令
./gradlew :app:assembleDebug --no-daemon

# 方式二：一键脚本（内部完成 source env.sh）
./build.sh
```

首次构建需联网下载 Kotlin/Compose/AndroidX 依赖（本机实测约 3 分钟）；
之后可离线构建（`--offline`），全量重建约 30 s 以内。

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 7. 安装与运行

### 7.1 连接本机（无线调试回环）

```sh
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export HOME=/data/data/com.termux/files/home     # 关键：配对私钥在 Termux home
adb connect 127.0.0.1:<无线调试端口>            # 端口在「开发者选项 → 无线调试」查看
adb devices                                     # 需为 device
```

### 7.2 安装 / 启动

```sh
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.mengyalife.app
adb shell am start -W -n com.mengyalife.app/.MainActivity
```

### 7.3 常见问题（本机实际踩到）

| 现象 | 原因 | 处理 |
|---|---|---|
| `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` | HyperOS 在**锁屏 / 屏幕熄灭**时会拒绝 adb 安装 | 先 `adb shell input keyevent KEYCODE_WAKEUP` 唤醒并解锁，再重试；本机实测唤醒后立即安装成功 |
| `adb connect` 报 `failed to connect`，但 `adb devices` 显示 `offline` | `HOME` 指向 `/root`，用的是新生成的 adbkey，与手机已配对的不一致 | 统一 `export HOME=/data/data/com.termux/files/home` |
| 无线调试端口连不上 | 端口是**动态的**，重启/开关调试后会变 | 到「无线调试」界面看当前端口后重新 `adb connect` |
| 截图得到损坏 PNG | `screencap` 会先向 stdout 打多屏警告 | 从 `\x89PNG` 偏移处截断后再保存 |
| `uiautomator dump` 报 `could not get idle state` | 页面有持续动画 | 本项目页面为静态，无此问题 |

---

## 8. 已验证结果

| 验证项 | 结果 |
|---|---|
| 构建退出码 | `0`（BUILD SUCCESSFUL，2m42s 冷构建） |
| APK 大小 | 9,294,831 字节（8.9 MB） |
| APK SHA-256 | `1649e09cd257a4d0b173933df678539aa1ac2caaf5850ff09652d2cb58ba1e8f` |
| 安装 | `Success`（2026-11-02 01:13:14） |
| 冷启动 | `LaunchState: COLD`，`TotalTime: 351 ms` |
| 按钮交互 | 点击前 `状态：等待交互 / 点击次数：0` → 点击 1 次 `状态：交互已验证（1 次）/ 点击次数：1` → 3 次后 `（3 次）/ 3` |
| 进程 / 崩溃 | 进程存活；`logcat -b crash` 无 `com.mengyalife.app` 记录 |
| 冷启动状态复位 | force-stop 后重启回到 `点击次数：0`（符合预期） |
| 离线 clean 重建 | BUILD SUCCESSFUL，产出 APK **SHA-256 与首次构建完全一致** |

证据文件：`docs/reports/logs/`（构建日志、安装日志、UI dump、截图 `shots/01..03`）。

---

## 9. 安全与范围声明

- 本轮**无** AI、无后端、无网络能力；`AndroidManifest.xml` **不申请任何权限**（连 `INTERNET` 都没有）。
- 仓库**不含**任何 API Key / token / 密钥；debug 签名使用 AGP 自动生成的调试密钥。
- 仓库**不含**设备专属绝对路径：构建脚本与配置文件里没有任何绝对路径
  （SDK 路径由 `ANDROID_HOME` 解析，aapt2 由 `env.sh` 运行时注入）。
  文档中出现的 `/data/data/com.termux/files/usr` 是 **Termux 通用 `$PREFIX` 取值示例**
  （所有 Termux 安装都相同、与具体机型无关），仅用于说明如何设置 `PREFIX`。
- 未提交任何远程仓库、未发布应用。
- 未改动任何既有工程（`~/android-projects/{hello-zero,counter-lab,pulseboard}` 只读复用 Wrapper）。

---

## 10. 下一轮（Gate 1，未开始）

按评审结论再启动。方向：Room 本地数据层、页面骨架、以及「用户自带 API Key」的本地安全存储设计（仍不引入后端口）。
