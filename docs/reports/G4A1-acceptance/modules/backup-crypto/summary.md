# 测试执行报告 · 20261105-091947

- 开始：2026-11-05T09:19:47+0800
- 结束：2026-11-05T09:19:55+0800
- 总耗时：**8.3s**
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
| backup-crypto（备份加密） | critical | 1 | — | 显式指定 --module backup-crypto |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.1s | issues=0 |  |
| JVM 测试（1 类） | PASS | 7.2s | tests=4, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091947`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091947/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091947/logs`

## 级别验收

- 满足当前级别：**是**
