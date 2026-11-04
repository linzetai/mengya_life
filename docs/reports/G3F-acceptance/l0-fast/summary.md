# 测试执行报告 · 20261105-041341

- 开始：2026-11-05T04:13:41+0800
- 结束：2026-11-05T04:13:56+0800
- 总耗时：**14.8s**
- 级别：**fast**（L0 Fast）
- 模式：changed
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`10c14d3b100b48a15fd7697ae36a4b3141020d56`
- branch：`master`
- 基线：`(simulated)`（--simulate-changed 测试样本）
- 工作区 dirty：True
- 变更文件：1

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `app/src/main/java/com/mengyalife/app/util/MetricFormat.kt` | simulated |

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因


| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| trends（趋势） | medium | 3 | — | 源码路径命中（app/src/main/java/com/mengyalife/app/util/MetricFormat.kt） |
| weekly-ai（每周 AI 增强） | high | 3 | — | 依赖传播：trends 是 weekly-ai 的上游 |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.1s | issues=0 |  |
| JVM 测试（6 类） | PASS | 13.9s | tests=31, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备
- **trends:verify_trends.py**：该用例层级 feature 高于当前 fast 允许的 []
- **weekly-ai:verify_weekly_ai.py**：该用例层级 feature 高于当前 fast 允许的 []

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041341`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041341/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041341/logs`

## 级别验收

- 满足当前级别：**是**
