# 测试执行报告 · 20261106-111732

- 开始：2026-11-06T11:17:32+0800
- 结束：2026-11-06T11:17:52+0800
- 总耗时：**19.4s**
- 级别：**fast**（L0 Fast）
- 执行 profile：**standard**
- 模式：module
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`731a7f6cbc3719977dbeadbd960230b4f2c4a745`
- branch：`master`
- 基线：`731a7f6cbc3719977dbeadbd960230b4f2c4a745`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：24

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreCoordinator.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreSnapshotStore.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreWriteBarrier.kt` | unstaged |
| M | `app/src/test/java/com/mengyalife/app/restore/RestoreSnapshotSpaceTest.kt` | unstaged |
| M | `app/src/test/java/com/mengyalife/app/restore/RestoreWriteBarrierTest.kt` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-01/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-01/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-02/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-02/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-03/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-03/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-04/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-04/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-05/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-05/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-06/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-06/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-07/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-07/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-08/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-08/ui.xml` | unstaged |
| M | `docs/reports/G4A2B-acceptance/qa/e2e-result.json` | unstaged |
| M | `tests/verify/selection.py` | unstaged |
| M | `tests/verify/validate.py` | unstaged |

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| restore-validate（恢复只读校验/预览） | high | 6 | — | 显式指定 --module restore-validate |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.1s | issues=0 |  |
| JVM 测试（6 类） | PASS | 18.1s | tests=24, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备
- **restore-validate:verify_restore_e2e.py**：该用例层级 feature 高于当前 fast 允许的 []
- **restore-validate:verify_restore_failures.py**：该用例层级 feature 高于当前 fast 允许的 []
- **restore-validate:verify_restore_fresh_db.py**：该用例层级 feature 高于当前 fast 允许的 []

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-111732`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-111732/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-111732/logs`

## 级别验收

- 满足当前级别：**是**
- 本 profile 检查满足：**是**
- 完整级别验收满足：**是**
