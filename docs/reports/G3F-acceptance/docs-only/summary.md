# 测试执行报告 · 20261105-032406

- 开始：2026-11-05T03:24:06+0800
- 结束：2026-11-05T03:24:06+0800
- 总耗时：**0.2s**
- 级别：**fast**（L0 Fast）
- 模式：changed
- 结论：**PASS (docs-only)**
- 退出码：0

## Git 与变更

- commit：`10c14d3b100b48a15fd7697ae36a4b3141020d56`
- branch：`master`
- 基线：`(simulated)`（--simulate-changed 测试样本）
- 工作区 dirty：True
- 变更文件：1

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `README.md` | simulated |

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因


| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.0s | issues=0 |  |
| JVM 测试 | SKIPPED | 0.0s |  |  |

## 未运行与原因

- **Android 构建/安装/真机**：仅文档变化；不改变 APK，按映射规则 G 不安装 APK。

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-032406`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-032406/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-032406/logs`

## 级别验收

- 满足当前级别：**是**
- 仅文档变化：没有需要执行的 Android 测试，符合规则 G。
