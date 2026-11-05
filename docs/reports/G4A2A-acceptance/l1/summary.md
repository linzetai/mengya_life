# 测试执行报告 · 20261105-164200

- 开始：2026-11-05T16:42:00+0800
- 结束：2026-11-05T16:46:55+0800
- 总耗时：**295.1s**
- 级别：**feature**（L1 Feature）
- 模式：module
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`72e2a551512cfed1080122a455ad175819d6c43c`
- branch：`master`
- 基线：`72e2a551512cfed1080122a455ad175819d6c43c`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：23

| 状态 | 文件 | 来源 |
|---|---|---|
| D | `G4A1-1-evidence.zip` | staged |
| M | `app/src/main/java/com/mengyalife/app/data/backup/AndroidBackupStorage.kt` | unstaged |
| A | `app/src/main/java/com/mengyalife/app/data/backup/FileBackupTempStore.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/data/restore/BackupRestoreReader.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/data/restore/RestoreCurrentStateSource.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/data/restore/RestoreModels.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/data/restore/RestorePlanner.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/data/restore/RestoreValidator.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/data/restore/SafRestoreFileSource.kt` | untracked |
| M | `app/src/main/java/com/mengyalife/app/di/AppContainer.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/ui/screens/SettingsScreen.kt` | unstaged |
| A | `app/src/main/java/com/mengyalife/app/ui/settings/RestoreSection.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/ui/settings/RestoreViewModel.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/backup/FileBackupTempStoreTest.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/restore/BackupRestoreReaderTest.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/restore/RestorePlannerTest.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/restore/RestoreTestSupport.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/restore/RestoreValidatorTest.kt` | untracked |
| A | `docs/reports/G4A2A-acceptance/qa/preview-result.json` | untracked |
| A | `docs/reports/G4A2A-acceptance/qa/run-output.txt` | untracked |
| M | `docs/reports/REPORT-G4A1-1.md` | unstaged |
| M | `tests/test-map.json` | unstaged |
| A | `tools/ui-verify/verify_restore_preview.py` | untracked |

## 设备与 APK

- 设备：2509FPN0BC / Android 17 / `10.13.224.241:46519`
- qa APK：`584e312badb63ec901e91ff8b515129ebe50dd00dcf6552ee5d91672343db404` · 12653952 B · versionName=0.14.1-g3e2-1 · 复用安装

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| navigation（导航与共享 UI） | medium | 0 | — | L1+ 必要导航冒烟（跨页面回归） |
| restore-validate（恢复只读校验/预览） | high | 3 | verify_restore_preview.py | 显式指定 --module restore-validate |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.1s | issues=0 |  |
| 安装 qa APK | SKIPPED | 0.0s |  |  |
| QA 安全前置校验（包名 / QA_BUILD 标记） | PASS | 25.3s | assertions=10/10 | `qa-verify_qa_preflight.log` |
| JVM 测试（3 类） | PASS | 4.4s | tests=15, failures=0 | `jvm-testDebugUnitTest.log` |
| 真机 verify_navigation.py | PASS | 123.2s | assertions=28/28 | `qa-verify_navigation.log` |
| 真机 verify_restore_preview.py | PASS | 140.1s | assertions=21/21 | `qa-verify_restore_preview.log` |
| 清理 QA 数据（pm clear） | PASS | 0.0s |  |  |

## 真机用例明细

| 包 | 脚本 | 状态 | 断言 | 耗时 |
|---|---|---|---|---|
| com.mengyalife.app.qa | verify_navigation.py | PASS | 28/28 | 123.2s |
| com.mengyalife.app.qa | verify_restore_preview.py | PASS | 21/21 | 140.1s |

## 未运行与原因

- 无

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-164200`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-164200/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-164200/logs`

## 级别验收

- 满足当前级别：**是**
