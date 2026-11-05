# 测试执行报告 · 20261105-165632

- 开始：2026-11-05T16:56:32+0800
- 结束：2026-11-05T17:01:24+0800
- 总耗时：**292.3s**
- 级别：**feature**（L1 Feature）
- 模式：module
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`9df2b22c3aaa831913a22f4b65fd8071f5e4ec64`
- branch：`master`
- 基线：`9df2b22c3aaa831913a22f4b65fd8071f5e4ec64`（上一次 verify 运行记录）
- 工作区 dirty：False
- 变更文件：0

## 设备与 APK

- 设备：2509FPN0BC / Android 17 / `10.13.224.241:46519`
- qa APK：`584e312badb63ec901e91ff8b515129ebe50dd00dcf6552ee5d91672343db404` · 12653952 B · versionName=0.14.1-g3e2-1 · 复用安装

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| navigation（导航与共享 UI） | medium | 0 | — | L1+ 必要导航冒烟（跨页面回归） |
| restore-validate（恢复只读校验/预览） | high | 3 | verify_restore_preview.py | 显式指定 --module restore-validate |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.1s | issues=0 |  |
| 安装 qa APK | SKIPPED | 0.0s |  |  |
| QA 安全前置校验（包名 / QA_BUILD 标记） | PASS | 26.0s | assertions=10/10 | `qa-verify_qa_preflight.log` |
| JVM 测试（3 类） | PASS | 3.4s | tests=15, failures=0 | `jvm-testDebugUnitTest.log` |
| 真机 verify_navigation.py | PASS | 121.2s | assertions=28/28 | `qa-verify_navigation.log` |
| 真机 verify_restore_preview.py | PASS | 140.3s | assertions=21/21 | `qa-verify_restore_preview.log` |
| 清理 QA 数据（pm clear） | PASS | 0.0s |  |  |

## 真机用例明细

| 包 | 脚本 | 状态 | 断言 | 耗时 |
|---|---|---|---|---|
| com.mengyalife.app.qa | verify_navigation.py | PASS | 28/28 | 121.2s |
| com.mengyalife.app.qa | verify_restore_preview.py | PASS | 21/21 | 140.3s |

## 未运行与原因

- 无

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-165632`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-165632/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-165632/logs`

## 级别验收

- 满足当前级别：**是**
