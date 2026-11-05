# 测试执行报告 · 20261105-091956

- 开始：2026-11-05T09:19:56+0800
- 结束：2026-11-05T09:20:06+0800
- 总耗时：**10.0s**
- 级别：**fast**（L0 Fast）
- 模式：module
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`69cecdecb386a4a2c27c9dd3f6a89614dc071022`
- branch：`master`
- 基线：`69cecdecb386a4a2c27c9dd3f6a89614dc071022`（上一次 verify 运行记录）
- 工作区 dirty：False
- 变更文件：0

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| backup-export（完整备份导出） | high | 3 | — | 显式指定 --module backup-export |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.0s | issues=0 |  |
| JVM 测试（3 类） | PASS | 9.0s | tests=10, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备
- **backup-export:verify_backup_v2.py**：该用例层级 feature 高于当前 fast 允许的 []

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091956`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091956/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091956/logs`

## 级别验收

- 满足当前级别：**是**
