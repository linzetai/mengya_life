# MengYa Life 0.24.3 · 真机与构建验证

## 设备与构建

| 项 | 值 |
|---|---|
| 设备 | Xiaomi MI 8（LineageOS），Android 15 / SDK 35，1080×2248 |
| 连接 | adb 无线调试（本机 proot → 127.0.0.1 网络） |
| 包 | `com.mengyalife.app.qa`（QA）· `com.mengyalife.app`（正式） |
| 版本 | **0.24.3-qa / 0.24.3**（versionCode 35） |
| Debug APK | `app-debug.apk` · 13,546,102 B · sha256 `0c1f3d06c32f8fe220c56feb56f0bbc446ba77843dc6ec5037dc753277131258` |
| QA APK | `app-qa.apk` · 13,060,839 B · sha256 `8c98db380d5268658472f25cc757d84bd740e38b643251e0bae3f1c09096f7c8` |

## 构建与测试

| 项 | 命令 | 结果 |
|---|---|---|
| Debug 构建 | `:app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| QA 构建 | `:app:assembleQa` | ✅ BUILD SUCCESSFUL |
| JVM 测试 | `:app:testDebugUnitTest` | ✅ **375/375**（新增 9 个「下一步」决策测试） |
| Lint | `:app:lintDebug` | ✅ 任务完成：5 error / 39 warning；5 个 error 均在未修改的 care/通知代码，本轮新增 2 条风格 warning（见 REMAINING-ISSUES §2） |
| Instrumented 测试 | `connectedDebugAndroidTest` | **BLOCKED**：本机环境未配置 instrumentation 运行器，本轮未执行 |

## 真机交互验证

| 验收项 | 结果 | 证据 |
|---|---|---|
| 四个底部 Tab 切换 | ✅ | 截图 01/06/07b/03 |
| 今天页空态（无数据不虚构） | ✅ | 截图 01 |
| 今天页有数据（当前关注 + 下一步 + 概览） | ✅ 浅色/深色 | 02 浅 / 02 深 |
| 下一步=写日记（真实推导） | ✅ | 02 |
| 人生全景：5 个领域全部可达、可折叠 | ✅ | 08 |
| 轨迹页空态 / 真实数值 | ✅ | 05 / 06（浅+深） |
| 日记列表 / 新增编辑 / 键盘 | ✅ | 07b / 07c / 07d |
| 顾问未配置 | ✅ | 03 |
| 顾问待授权（真实授权页） | ✅ | 04c |
| 顾问空对话 | ✅ | 03b |
| 顾问发送 + 键盘 + 离线回复 | ✅ | 04 / 04b |
| 顾问失败与重试（真实网络不可用） | ✅ | 04b 里保留了失败卡与重试按钮 |
| 设置页（AI/通知/关于） | ✅ 浅色/深色 | 09 浅 / 09 深 / 附加：关于、AI 隐私 |
| 恢复流程文案（校验→预览→确认） | ✅ | 10 |
| 恢复预览弹窗 | **BLOCKED** | 需要真实 v2 备份文件并走系统 SAF 选择器；本轮未完成 |
| 主动关心卡（Care） | **BLOCKED** | 需要满足真实触发条件（时间/记录规则），未在验收窗口内复现 |
| 无演示数据/虚构分数 | ✅ | 代码审计见 DEMO-DATA-AUDIT.md |

## 截图索引（`screenshots/`）

| 文件 | 内容 |
|---|---|
| `01-today-empty-light.png` | 今天页空态，浅色 |
| `02-today-data-light.png` | 今天页有数据，浅色 |
| `02-today-data-dark.png` | 今天页有数据，深色 |
| `03-advisor-not-configured-light.png` | 顾问未配置 |
| `03b-advisor-empty-conversation-light.png` | 顾问空对话（已配置、已授权） |
| `04-advisor-keyboard-light.png` | 对话 + 键盘 |
| `04b-advisor-reply-light.png` | 用户消息 + 离线回复 + 失败重试 |
| `04c-consent-panel-light.png` | 首次授权页 |
| `05-trajectory-empty-light.png` | 轨迹空态 |
| `06-trajectory-data-light.png` | 轨迹真实数据 |
| `06-trajectory-data-dark.png` | 轨迹真实数据，深色 |
| `07-diary-empty-light.png` | 日记空态 |
| `07b-diary-list-light.png` | 日记列表（3 篇） |
| `07c-diary-editor-light.png` | 日记编辑 |
| `07d-diary-editor-keyboard-light.png` | 日记编辑 + 键盘 |
| `08-panorama-light.png` | 人生全景，5 个领域 |
| `09-settings-light.png` | 设置页，浅色 |
| `09-settings-dark.png` | 设置页，深色 |
| `10-restore-section-light.png` | 恢复备份分组（新文案） |
| `10b-ai-privacy-light.png` | 数据授权与类别开关 |

截图均为本轮 **0.24.3-qa** 安装包在真机上的实际画面，非 Preview、非旧版本。

## 自动化方式说明

本轮截图和点击使用 `adb shell screencap` + `adb shell input tap`，**不使用 `uiautomator`**：
uiautomator 会占用无障碍通道，与 DSH Go 的手机操控桥接冲突，导致宿主运行被中断。
坐标按 1080×2248 实际像素计算；页面状态通过截图核对。

QA 种子数据（5 个领域、5 条进展、3 篇日记、1 次顾问对话）直接写入 QA 包数据库，
仅用于截图，不涉及正式包。
