# 测试执行报告 · 20261106-164126

- 开始：2026-11-06T16:41:26+0800
- 结束：2026-11-06T16:42:37+0800
- 总耗时：**71.5s**
- 级别：**integration**（L2 Integration）
- 执行 profile：**standard**
- 模式：module
- 结论：**CASE PASS (suite deferred): DB_UNREADABLE**
- 退出码：0

## Git 与变更

- commit：`75c4d9550406929c24e111eb411da695b96fafa4`
- branch：`master`
- 基线：`46b6963246f8e8254c3168b35da2c374e7b8d971`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：9

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt` | committed |
| M | `docs/reports/G4A2B-acceptance/failures/failure-01/diagnostics.json` | unstaged |
| M | `docs/reports/G4A2B-acceptance/failures/failure-01/ui.xml` | unstaged |
| A | `docs/reports/G4A2B-acceptance/failures/failure-SETTINGS_FAIL-result.json` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/source/restore-write-barrier.patch` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/source/test-selection-and-scenario.patch` | untracked |
| M | `tests/test-map.json` | committed |
| M | `tests/verify/validate.py` | committed |
| M | `tools/ui-verify/verify_restore_db_unreadable.py` | committed |

## 设备与 APK

- 设备：2509FPN0BC / Android 17 / `127.0.0.1:34707`
- qa APK：`c2fcdfc0d50b251c286291f483d52eade2845491cee446b2c925660906feaab1` · 12666113 B · versionName=0.14.1-g3e2-1 · 复用安装

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| database-migration（Room Schema / DAO / Migration） | critical | 0 | verify_migration.py | integration 级别强制包含高风险模块 |
| restore-validate（恢复只读校验/预览） | high | 6 | verify_restore_e2e.py, verify_restore_failures.py, verify_restore_fresh_db.py, verify_restore_db_unreadable.py | 显式指定 --module restore-validate |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（schema-contract, validate-map） | PASS | 0.1s | issues=0 |  |
| 安装 qa APK | SKIPPED | 0.0s |  |  |
| QA 安全前置校验（包名 / QA_BUILD 标记） | PASS | 29.3s | assertions=11/11 | `qa-verify_qa_preflight.log` |
| JVM 测试（6 类） | PASS | 4.3s | tests=25, failures=0 | `jvm-testDebugUnitTest.log` |
| 真机 verify_restore_db_unreadable.py [case=DB_UNREADABLE] | PASS | 36.6s | assertions=12/12 | `qa-verify_restore_db_unreadable.log` |
| 清理 QA 数据（pm clear） | PASS | 0.0s |  |  |

## 真机用例明细

| 包 | 脚本 | 模式 | 状态 | 断言 | 耗时 |
|---|---|---|---|---|---|
| com.mengyalife.app.qa | verify_restore_db_unreadable.py | case:DB_UNREADABLE | PASS | 12/12 | 36.6s |

## 未运行与原因

- **qa:verify_migration.py**：--case DB_UNREADABLE：只重跑单个scenario；其他用例保留为NOT RUN
- **qa:verify_restore_e2e.py**：--case DB_UNREADABLE：只重跑单个scenario；其他用例保留为NOT RUN
- **qa:verify_restore_failures.py**：--case DB_UNREADABLE：只重跑单个scenario；其他用例保留为NOT RUN
- **qa:verify_restore_fresh_db.py**：--case DB_UNREADABLE：只重跑单个scenario；其他用例保留为NOT RUN

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-164126`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-164126/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-164126/logs`

## 级别验收

- 满足当前级别：**否**
- 本 profile 检查满足：**否**
- 完整级别验收满足：**否**
- 存在被选择但没有任何测试执行的模块（NOT RUN）。
- 仅运行case DB_UNREADABLE；其他L2用例延期，不视为完整级别PASS。
