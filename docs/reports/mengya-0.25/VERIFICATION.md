# MengYa Life 0.25 · 验收

> 判定口径：PASS 只用于“本轮代码 + 可复现证据都成立”的项；设备未执行的一律 BLOCKED，不写成 PASS。

## 构建与测试

| 项 | 命令 | 结果 |
|---|---|---|
| 版本 | — | **0.25 / versionCode 36**（QA 为 `0.25-qa`） |
| JVM 测试 | `:app:testDebugUnitTest` | ✅ **385/385**（0 failures / 0 errors） |
| Debug 构建 | `:app:assembleDebug` | ✅ |
| QA 构建 | `:app:assembleQa` | ✅ |
| Lint | `:app:lintDebug` | 见下方统计（单独统计 error/warning） |
| Instrumented | `:app:connectedDebugAndroidTest` | ✅ **19/19**，BUILD SUCCESSFUL（MI 8 / Android 15） |

### Lint 统计

- error：**0**（`lint-results-debug.txt` 实测，2026-11-10 18:16）
- warning：**39**
- 修复前：5 error（CarePolicy 1、CarePreferenceParser 1、CareRunner 2、WeeklyReviewNotification 1）

### Instrumented

- 配置：`androidx.test:runner 1.6.2`、`androidx.test.ext:junit 1.2.1`、`room-testing 2.7.2`，`testInstrumentationRunner=AndroidJUnitRunner`；
- 测试数据：`CareDaoIntegrationTest` / `CareRunnerFenceTest` 使用 **in-memory Room**，不触碰正式数据库；
- 执行结果：**PASS，19/19**（`TEST-MI 8 - 15-_app-.xml`：tests=19, failures=0, errors=0）；
- 首次执行失败是环境问题（Gradle 调用 `/opt/android-sdk/platform-tools/adb` 与 build-tools 的 x86-64 `aapt` 解释器缺失），已用 arm64 adb/aapt 修正后重跑通过。

## Gate 0 · Git 同步

| 检查 | 结果 |
|---|---|
| 远端 master（`git ls-remote` HTTPS） | `ee20bf8…` |
| 本地 HEAD（改动前） | `c25f290…`，领先 2 个提交，无分叉 |
| 推送 | ✅ 已推送（fast-forward）：`ee20bf8..b1b38cd` |
| 推送后远端 SHA | `b1b38cd0b3cd8471a950b20ca7320d422115248b`（`ls-remote` 复核） |
| force push / reset | 未执行 |

## Gate 1 · 可靠性收口

| 项 | 状态 | 证据 |
|---|---|---|
| 1.1 lint 5 个 error | **PASS** | 修复见 IMPLEMENTATION；`CareTimeApi26Test` 4 例；lint 实测 **0 error / 39 warning** |
| 1.2 恢复崩溃重试（不依赖当前 endpoint） | **PASS（代码+单测）** | Gate 持久化 intent；fail-closed；`RestoreCredentialPolicyTest` 6 例、`RestoreGateJournalTest` 1 例 |
| 1.3 QA 故障注入（提交后崩溃） | **BLOCKED** | `qa_restore_halt_after_commit` 已实现；未在真机执行完整场景 |
| 1.4 真实备份预览/确认证据 | **BLOCKED** | SAF 文件选择器无法在本机自动化中稳定驱动 |
| 1.5 Instrumented 测试 | **PASS** | 19/19，失败原因为 aapt/adb 架构，修正后通过 |
| 1.6 lint error 目标 0 | **PASS** | `lintDebug` 完整执行：0 error |

## Gate 2 · 真实成长闭环

**FAIL（未实现）**。

本轮只做了可靠性收口，没有实现“建议 → 讨论 → 候选 → 编辑/确认/拒绝 → 保存 → 今天/轨迹反映”的新闭环改造；
也没有新增 factIds 追溯 UI 或 E2E 测试。既有 0.24.x 的洞察/提案能力没有被当作本轮完成证据。

## Gate 3 · UI 与真机验证

**PARTIAL / BLOCKED**。

已完成的真机证据（本轮 0.25-qa 安装包）：

| 截图 | 内容 |
|---|---|
| `screenshots/01-today-data-light.png` | 今天页（有真实种子数据） |
| `screenshots/02-settings-about-light.png` | 设置页关于，显示 `0.25-qa` |

未完成：顾问无配置/无授权/失败/成功矩阵、日记键盘、轨迹、五领域全景、恢复预览、Care 触发、深色矩阵。
0.24.3 的截图不作为 0.25 验收证据。

## 版本与产物

| 项 | 值 |
|---|---|
| QA APK | `app/build/outputs/apk/qa/app-qa.apk` |
| QA SHA256 | `425b9fdb04743432e3b8d94cd7f2c0596713722844244ecdf28c793d5832e248` |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Debug SHA256 | `7573f54426eea56d59f91a02bca71a184e33e769b2c9e741b3d7804cdfb5aa12` |

## 结论

- 可靠性代码修复与单测：PASS；
- 真机故障注入、真实恢复预览、instrumented、Gate 2、Gate 3 全矩阵：BLOCKED / FAIL，未以 PASS 记录。
