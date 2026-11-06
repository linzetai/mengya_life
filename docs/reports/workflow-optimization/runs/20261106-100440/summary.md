# 测试执行报告 · 20261106-100440

- 开始：2026-11-06T10:04:40+0800
- 结束：2026-11-06T10:04:42+0800
- 总耗时：**2.7s**
- 级别：**fast**（L0 Fast）
- 执行 profile：**dev**
- 模式：changed
- 结论：**LOCAL PASS (device deferred)**
- 退出码：0

## Git 与变更

- commit：`efa8607a59f38b90ac5ba615e7090fc90f7ca52a`
- branch：`master`
- 基线：`efa8607a59f38b90ac5ba615e7090fc90f7ca52a`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：27

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
| M | `tests/test-map.json` | unstaged |
| M | `tests/verify/cli.py` | unstaged |
| M | `tests/verify/gradle.py` | unstaged |
| M | `tests/verify/report.py` | unstaged |
| M | `tests/verify/selection.py` | unstaged |
| M | `tests/verify/validate.py` | unstaged |
| M | `tools/ui-verify/README.md` | unstaged |

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因

- 规则 `G-docs-only`：仅文档/证据变化
- 规则 `H-test-infra`：测试体系自身变化

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| test-infra（测试体系自身） | low | 0 | — | 源码路径命中（tests/test-map.json）；规则 H-test-infra：测试体系自身变化 |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（script-syntax, validate-map） | PASS | 2.1s | issues=0 |  |
| JVM 测试 | SKIPPED | 0.0s |  |  |

## 未运行与原因

- **真机测试**：fast 级别不启动设备

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-100440`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-100440/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-100440/logs`

## 级别验收

- 满足当前级别：**否**
- 本 profile 检查满足：**是**
- 完整级别验收满足：**是**
- dev profile 只验证本地静态/JVM/编译；所有选中真机用例延期，不能作为L1/L2/Gate PASS。
