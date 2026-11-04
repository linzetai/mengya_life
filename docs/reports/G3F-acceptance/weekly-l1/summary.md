# 测试执行报告 · 20261105-041941

- 开始：2026-11-05T04:19:41+0800
- 结束：2026-11-05T04:30:25+0800
- 总耗时：**644.2s**
- 级别：**feature**（L1 Feature）
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
| M | `app/src/main/java/com/mengyalife/app/domain/weekly/WeeklyAiParser.kt` | simulated |

## 设备与 APK

- 设备：2509FPN0BC / Android 17 / `192.168.10.183:33537`
- qa APK：`055da4efd2f6042fd80400943f08924f2d8e09a1fe341aa6c3eba56bf39c2e70` · 12393063 B · versionName=0.14.1-g3e2-1 · 复用安装

## 选择与原因

- 规则 `A-weekly`：周回顾 / 周 AI 源码变化

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| advisor（AI 顾问） | high | 9 | — | 依赖传播：ai-consent 是 advisor 的上游 |
| ai-consent（AI 授权与配置） | high | 3 | verify_settings.py | 规则 A-weekly：周回顾 / 周 AI 源码变化 |
| insight（成长洞察） | medium | 3 | — | 依赖传播：ai-consent 是 insight 的上游 |
| navigation（导航与共享 UI） | medium | 0 | — | L1+ 必要导航冒烟（跨页面回归） |
| weekly-ai（每周 AI 增强） | high | 3 | verify_weekly_ai.py | 源码路径命中（app/src/main/java/com/mengyalife/app/domain/weekly/WeeklyAiParser.kt）；规则 A-weekly：周回顾 / 周 AI 源码变化 |
| weekly-review（每周成长回顾（本地）） | high | 3 | verify_weekly_ai.py | 规则 A-weekly：周回顾 / 周 AI 源码变化 |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.1s | issues=0 |  |
| 安装 qa APK | SKIPPED | 0.0s |  |  |
| QA 安全前置校验（包名 / QA_BUILD 标记） | PASS | 28.6s | assertions=10/10 | `qa-verify_qa_preflight.log` |
| JVM 测试（19 类） | PASS | 9.8s | tests=132, failures=0 | `jvm-testDebugUnitTest.log` |
| 真机 verify_navigation.py | PASS | 124.8s | assertions=28/28 | `qa-verify_navigation.log` |
| 真机 verify_settings.py | PASS | 170.0s | assertions=38/38 | `qa-verify_settings.log` |
| 真机 verify_weekly_ai.py | PASS | 308.3s | assertions=22/22 | `qa-verify_weekly_ai.log` |
| 清理 QA 数据（pm clear） | PASS | 0.0s |  |  |

## 真机用例明细

| 包 | 脚本 | 状态 | 断言 | 耗时 |
|---|---|---|---|---|
| com.mengyalife.app.qa | verify_navigation.py | PASS | 28/28 | 124.8s |
| com.mengyalife.app.qa | verify_settings.py | PASS | 38/38 | 170.0s |
| com.mengyalife.app.qa | verify_weekly_ai.py | PASS | 22/22 | 308.3s |

## 未运行与原因

- **advisor:verify_g3b1.py**：该用例层级 integration 高于当前 feature 允许的 ['feature']
- **advisor:verify_advisor_network.py**：该用例层级 integration 高于当前 feature 允许的 ['feature']
- **ai-consent:verify_g3b1.py**：该用例层级 integration 高于当前 feature 允许的 ['feature']

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041941`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041941/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041941/logs`

## 级别验收

- 满足当前级别：**是**
