# 测试执行报告 · 20261106-102012

- 开始：2026-11-06T10:20:12+0800
- 结束：2026-11-06T10:20:15+0800
- 总耗时：**3.1s**
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
- 变更文件：44

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `docs/TEST-ARCHITECTURE-AUDIT.md` | staged |
| M | `docs/TEST-MODULE-MAP.md` | staged |
| M | `docs/TEST-RUNNER-DESIGN.md` | staged |
| A | `docs/reports/TEST-WORKFLOW-OPTIMIZATION.md` | staged |
| A | `docs/reports/workflow-optimization/README.md` | staged |
| A | `docs/reports/workflow-optimization/plans/advisor-l2-dedupe.txt` | staged |
| A | `docs/reports/workflow-optimization/plans/backup-export-plan.txt` | staged |
| A | `docs/reports/workflow-optimization/plans/backup-feature-dedupe.txt` | staged |
| A | `docs/reports/workflow-optimization/plans/focused-restore-l2.txt` | staged |
| A | `docs/reports/workflow-optimization/plans/standard-advisor-dedupe.txt` | staged |
| A | `docs/reports/workflow-optimization/plans/weekly-gate-full.txt` | staged |
| A | `docs/reports/workflow-optimization/plans/weekly-l2-smoke.txt` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-094849/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-094849/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-095225/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-095225/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-095457/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-095457/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-095600/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-095600/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-100313/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-100313/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-100440/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-100440/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-100538/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-100538/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101208/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101208/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101358/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101358/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101535/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101535/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101701/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101701/summary.md` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101716/report.json` | staged |
| A | `docs/reports/workflow-optimization/runs/20261106-101716/summary.md` | staged |
| M | `tests/test-map.json` | staged |
| M | `tests/verify/cli.py` | staged |
| M | `tests/verify/gradle.py` | staged |
| M | `tests/verify/report.py` | staged |
| M | `tests/verify/selection.py` | staged |
| M | `tests/verify/validate.py` | staged |
| M | `tools/ui-verify/README.md` | staged |
| M | `tools/ui-verify/verify_weekly_ai.py` | staged |

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
| 静态检查（script-syntax, validate-map） | PASS | 2.3s | issues=0 |  |
| JVM 测试 | SKIPPED | 0.0s |  |  |

## 未运行与原因

- **真机测试**：fast 级别不启动设备

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-102012`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-102012/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-102012/logs`

## 级别验收

- 满足当前级别：**是**
- 本 profile 检查满足：**是**
- 完整级别验收满足：**是**
