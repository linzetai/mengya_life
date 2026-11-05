# 测试执行报告 · 20261105-150527

- 开始：2026-11-05T15:05:27+0800
- 结束：2026-11-05T15:16:14+0800
- 总耗时：**647.5s**
- 级别：**feature**（L1 Feature）
- 模式：module
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`fb46f29cab86368e695d10544788cd382b40124a`
- branch：`master`
- 基线：`fb46f29cab86368e695d10544788cd382b40124a`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：22

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `app/src/main/java/com/mengyalife/app/MainActivity.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/backup/AndroidBackupStorage.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/backup/BackupFileWriter.kt` | unstaged |
| A | `app/src/main/java/com/mengyalife/app/data/backup/QaBackupFaultInjector.kt` | untracked |
| A | `app/src/main/java/com/mengyalife/app/data/qa/QaBackupFixtureSeeder.kt` | untracked |
| M | `app/src/main/java/com/mengyalife/app/di/AppContainer.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/ui/settings/BackupViewModel.kt` | unstaged |
| M | `app/src/test/java/com/mengyalife/app/backup/BackupFileWriterTest.kt` | unstaged |
| A | `docs/reports/G4A1-1-acceptance/failures/run-output.txt` | untracked |
| A | `docs/reports/G4A1-1-acceptance/failures/scenarios.json` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/db-after.json` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/db-before.json` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/field-diff.json` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/payload-full.json` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/probe-full.json` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/probe-recheck.json` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/run-output.txt` | untracked |
| A | `docs/reports/G4A1-1-acceptance/qa/sample-v2-full.mengyalife` | untracked |
| M | `tests/test-map.json` | unstaged |
| M | `tools/ui-verify/backup_v2_probe.js` | unstaged |
| A | `tools/ui-verify/verify_backup_v2_failures.py` | untracked |
| A | `tools/ui-verify/verify_backup_v2_full.py` | untracked |

## 设备与 APK

- 设备：2509FPN0BC / Android 17 / `10.13.224.241:46519`
- qa APK：`7968cc662a703558f9a955b2e79ae5ab0e054834a361be97223a8a0be44a2474` · 12517274 B · versionName=0.14.1-g3e2-1 · 复用安装

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| backup-export（完整备份导出） | high | 3 | verify_backup_v2.py, verify_backup_v2_full.py, verify_backup_v2_failures.py | 显式指定 --module backup-export |
| navigation（导航与共享 UI） | medium | 0 | — | L1+ 必要导航冒烟（跨页面回归） |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.1s | issues=0 |  |
| 安装 qa APK | SKIPPED | 0.0s |  |  |
| QA 安全前置校验（包名 / QA_BUILD 标记） | PASS | 25.4s | assertions=10/10 | `qa-verify_qa_preflight.log` |
| JVM 测试（3 类） | PASS | 3.6s | tests=11, failures=0 | `jvm-testDebugUnitTest.log` |
| 真机 verify_backup_v2.py | PASS | 111.6s | assertions=28/28 | `qa-verify_backup_v2.log` |
| 真机 verify_backup_v2_failures.py | PASS | 329.2s | assertions=45/45 | `qa-verify_backup_v2_failures.log` |
| 真机 verify_backup_v2_full.py | PASS | 55.1s | assertions=19/19 | `qa-verify_backup_v2_full.log` |
| 真机 verify_navigation.py | PASS | 120.9s | assertions=28/28 | `qa-verify_navigation.log` |
| 清理 QA 数据（pm clear） | PASS | 0.0s |  |  |

## 真机用例明细

| 包 | 脚本 | 状态 | 断言 | 耗时 |
|---|---|---|---|---|
| com.mengyalife.app.qa | verify_backup_v2.py | PASS | 28/28 | 111.6s |
| com.mengyalife.app.qa | verify_backup_v2_failures.py | PASS | 45/45 | 329.2s |
| com.mengyalife.app.qa | verify_backup_v2_full.py | PASS | 19/19 | 55.1s |
| com.mengyalife.app.qa | verify_navigation.py | PASS | 28/28 | 120.9s |

## 未运行与原因

- 无

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-150527`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-150527/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-150527/logs`

## 级别验收

- 满足当前级别：**是**
