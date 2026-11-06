# 测试执行报告 · 20261106-101716

- 开始：2026-11-06T10:17:16+0800
- 结束：2026-11-06T10:17:20+0800
- 总耗时：**4.5s**
- 级别：**integration**（L2 Integration）
- 执行 profile：**dev**
- 模式：module
- 结论：**LOCAL PASS (device deferred)**
- 退出码：0

## Git 与变更

- commit：`efa8607a59f38b90ac5ba615e7090fc90f7ca52a`
- branch：`master`
- 基线：`efa8607a59f38b90ac5ba615e7090fc90f7ca52a`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：40

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `docs/TEST-ARCHITECTURE-AUDIT.md` | unstaged |
| M | `docs/TEST-MODULE-MAP.md` | unstaged |
| M | `docs/TEST-RUNNER-DESIGN.md` | unstaged |
| A | `docs/reports/TEST-WORKFLOW-OPTIMIZATION.md` | untracked |
| A | `docs/reports/workflow-optimization/README.md` | untracked |
| A | `docs/reports/workflow-optimization/plans/advisor-l2-dedupe.txt` | untracked |
| A | `docs/reports/workflow-optimization/plans/backup-export-plan.txt` | untracked |
| A | `docs/reports/workflow-optimization/plans/backup-feature-dedupe.txt` | untracked |
| A | `docs/reports/workflow-optimization/plans/focused-restore-l2.txt` | untracked |
| A | `docs/reports/workflow-optimization/plans/standard-advisor-dedupe.txt` | untracked |
| A | `docs/reports/workflow-optimization/plans/weekly-gate-full.txt` | untracked |
| A | `docs/reports/workflow-optimization/plans/weekly-l2-smoke.txt` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-094849/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-094849/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-095225/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-095225/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-095457/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-095457/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-095600/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-095600/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-100313/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-100313/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-100440/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-100440/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-100538/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-100538/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-101208/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-101208/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-101358/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-101358/summary.md` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-101535/report.json` | untracked |
| A | `docs/reports/workflow-optimization/runs/20261106-101535/summary.md` | untracked |
| M | `tests/test-map.json` | unstaged |
| M | `tests/verify/cli.py` | unstaged |
| M | `tests/verify/gradle.py` | unstaged |
| M | `tests/verify/report.py` | unstaged |
| M | `tests/verify/selection.py` | unstaged |
| M | `tests/verify/validate.py` | unstaged |
| M | `tools/ui-verify/README.md` | unstaged |
| M | `tools/ui-verify/verify_weekly_ai.py` | unstaged |

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| database-migration（Room Schema / DAO / Migration） | critical | 0 | verify_migration.py | integration 级别强制包含高风险模块 |
| restore-validate（恢复只读校验/预览） | high | 6 | verify_restore_e2e.py, verify_restore_failures.py, verify_restore_fresh_db.py | 显式指定 --module restore-validate |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（schema-contract, validate-map） | PASS | 0.1s | issues=0 |  |
| JVM 测试（6 类） | PASS | 3.7s | tests=23, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **qa:verify_migration.py**：--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收
- **qa:verify_restore_e2e.py**：--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收
- **qa:verify_restore_failures.py**：--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收
- **qa:verify_restore_fresh_db.py**：--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-101716`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-101716/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-101716/logs`

## 级别验收

- 满足当前级别：**否**
- 本 profile 检查满足：**是**
- 完整级别验收满足：**否**
- 存在被选择但没有任何测试执行的模块（NOT RUN）。
- dev profile 只验证本地静态/JVM/编译；所有选中真机用例延期，不能作为L1/L2/Gate PASS。
