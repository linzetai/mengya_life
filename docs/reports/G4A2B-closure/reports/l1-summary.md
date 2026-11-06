# 测试执行报告 · 20261106-002339

- 开始：2026-11-06T00:23:39+0800
- 结束：2026-11-06T00:43:11+0800
- 总耗时：**1172.1s**
- 级别：**feature**（L1 Feature）
- 模式：module
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`3fb6781b955163994d891b98ff5f1d12c30692fc`
- branch：`master`
- 基线：`3fb6781b955163994d891b98ff5f1d12c30692fc`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：18

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `app/src/main/java/com/mengyalife/app/data/advisor/GoalProposalApplyService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/growth/GrowthMemoryService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/qa/QaBackupFixtureSeeder.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/repository/GoalRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/repository/JournalRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreCoordinator.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreGate.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreJournal.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewWorker.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/di/AppContainer.kt` | unstaged |
| A | `app/src/test/java/com/mengyalife/app/restore/RestoreGateJournalTest.kt` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failures-result.json` | untracked |
| M | `docs/reports/G4A2B-acceptance/qa/e2e-result.json` | unstaged |
| M | `tests/test-map.json` | unstaged |
| A | `tools/ui-verify/test_restore_failure_assertions.py` | untracked |
| M | `tools/ui-verify/verify_restore_e2e.py` | unstaged |
| M | `tools/ui-verify/verify_restore_failures.py` | unstaged |

## 设备与 APK

- 设备：2509FPN0BC / Android 17 / `127.0.0.1:35193`
- qa APK：`aa8ade31872fb9fa06e7e582050f2b0df2dd366a12d9f528827254cb8ad9ed5e` · 12722570 B · versionName=0.14.1-g3e2-1 · 复用安装

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| navigation（导航与共享 UI） | medium | 0 | — | L1+ 必要导航冒烟（跨页面回归） |
| restore-validate（恢复只读校验/预览） | high | 4 | verify_restore_preview.py, verify_restore_e2e.py, verify_restore_failures.py | 显式指定 --module restore-validate |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.2s | issues=0 |  |
| 安装 qa APK | SKIPPED | 0.0s |  |  |
| QA 安全前置校验（包名 / QA_BUILD 标记） | PASS | 27.6s | assertions=10/10 | `qa-verify_qa_preflight.log` |
| JVM 测试（4 类） | PASS | 15.5s | tests=19, failures=0 | `jvm-testDebugUnitTest.log` |
| 真机 verify_navigation.py | PASS | 123.1s | assertions=28/28 | `qa-verify_navigation.log` |
| 真机 verify_restore_e2e.py | PASS | 342.4s | assertions=35/35 | `qa-verify_restore_e2e.log` |
| 真机 verify_restore_failures.py | PASS | 518.5s | assertions=59/59 | `qa-verify_restore_failures.log` |
| 真机 verify_restore_preview.py | PASS | 140.8s | assertions=21/21 | `qa-verify_restore_preview.log` |
| 清理 QA 数据（pm clear） | PASS | 0.0s |  |  |

## 真机用例明细

| 包 | 脚本 | 状态 | 断言 | 耗时 |
|---|---|---|---|---|
| com.mengyalife.app.qa | verify_navigation.py | PASS | 28/28 | 123.1s |
| com.mengyalife.app.qa | verify_restore_e2e.py | PASS | 35/35 | 342.4s |
| com.mengyalife.app.qa | verify_restore_failures.py | PASS | 59/59 | 518.5s |
| com.mengyalife.app.qa | verify_restore_preview.py | PASS | 21/21 | 140.8s |

## 未运行与原因

- 无

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-002339`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-002339/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-002339/logs`

## 级别验收

- 满足当前级别：**是**
