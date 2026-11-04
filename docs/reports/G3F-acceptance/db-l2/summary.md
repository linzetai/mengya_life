# 测试执行报告 · 20261105-044524

- 开始：2026-11-05T04:45:24+0800
- 结束：2026-11-05T04:58:34+0800
- 总耗时：**790.5s**
- 级别：**integration**（L2 Integration）
- 模式：module
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`10c14d3b100b48a15fd7697ae36a4b3141020d56`
- branch：`master`
- 基线：`10c14d3b100b48a15fd7697ae36a4b3141020d56`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：69

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `.gitignore` | unstaged |
| M | `README.md` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/MainActivity.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/advisor/AdvisorEngine.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyAiService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/di/AppContainer.kt` | unstaged |
| A | `app/src/test/java/com/mengyalife/app/advisor/WeeklyAiFakeEngineTest.kt` | untracked |
| A | `docs/TEST-ACCEPTANCE.md` | untracked |
| A | `docs/TEST-ARCHITECTURE-AUDIT.md` | untracked |
| A | `docs/TEST-MODULE-MAP.md` | untracked |
| A | `docs/TEST-PERFORMANCE-BEFORE-AFTER.md` | untracked |
| A | `docs/TEST-RUNNER-DESIGN.md` | untracked |
| A | `docs/reports/G3F-acceptance/advisor-feature.log` | untracked |
| A | `docs/reports/G3F-acceptance/diary-l1/logs/gradle-assemble.log` | untracked |
| A | `docs/reports/G3F-acceptance/diary-l1/logs/jvm-testDebugUnitTest.log` | untracked |
| A | `docs/reports/G3F-acceptance/diary-l1/logs/qa-verify_diary.log` | untracked |
| A | `docs/reports/G3F-acceptance/diary-l1/logs/qa-verify_navigation.log` | untracked |
| A | `docs/reports/G3F-acceptance/diary-l1/logs/qa-verify_qa_preflight.log` | untracked |
| A | `docs/reports/G3F-acceptance/diary-l1/report.json` | untracked |
| A | `docs/reports/G3F-acceptance/diary-l1/summary.md` | untracked |
| A | `docs/reports/G3F-acceptance/docs-only/report.json` | untracked |
| A | `docs/reports/G3F-acceptance/docs-only/summary.md` | untracked |
| A | `docs/reports/G3F-acceptance/failure-probe/logs/jvm-testDebugUnitTest.log` | untracked |
| A | `docs/reports/G3F-acceptance/failure-probe/report.json` | untracked |
| A | `docs/reports/G3F-acceptance/failure-probe/summary.md` | untracked |
| A | `docs/reports/G3F-acceptance/l0-fast/logs/jvm-testDebugUnitTest.log` | untracked |
| A | `docs/reports/G3F-acceptance/l0-fast/report.json` | untracked |
| A | `docs/reports/G3F-acceptance/l0-fast/summary.md` | untracked |
| A | `docs/reports/G3F-acceptance/weekly-l1/logs/jvm-testDebugUnitTest.log` | untracked |
| A | `docs/reports/G3F-acceptance/weekly-l1/logs/qa-verify_navigation.log` | untracked |
| A | `docs/reports/G3F-acceptance/weekly-l1/logs/qa-verify_qa_preflight.log` | untracked |
| A | `docs/reports/G3F-acceptance/weekly-l1/logs/qa-verify_settings.log` | untracked |
| A | `docs/reports/G3F-acceptance/weekly-l1/logs/qa-verify_weekly_ai.log` | untracked |
| A | `docs/reports/G3F-acceptance/weekly-l1/report.json` | untracked |
| A | `docs/reports/G3F-acceptance/weekly-l1/summary.md` | untracked |
| D | `docs/reports/logs/verify-shots/g3a-chat-401.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-chat-cancelled.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-chat-errors.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-chat-first.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-chat-retry.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-context-consent.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-context-sent.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-export-with-key.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-key-deleted.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-real-network-401.png` | unstaged |
| D | `docs/reports/logs/verify-shots/g3a-settings-saved.png` | unstaged |
| A | `scripts/verify.sh` | untracked |
| A | `tests/__init__.py` | untracked |
| A | `tests/test-map.json` | untracked |
| A | `tests/verify/__init__.py` | untracked |
| A | `tests/verify/changes.py` | untracked |
| A | `tests/verify/cli.py` | untracked |
| A | `tests/verify/device.py` | untracked |
| A | `tests/verify/gradle.py` | untracked |
| A | `tests/verify/report.py` | untracked |
| A | `tests/verify/selection.py` | untracked |
| A | `tests/verify/util.py` | untracked |
| A | `tests/verify/validate.py` | untracked |
| M | `tools/ui-verify/harness.py` | unstaged |
| M | `tools/ui-verify/run_all.sh` | unstaged |
| M | `tools/ui-verify/verify_advisor.py` | unstaged |
| M | `tools/ui-verify/verify_export.py` | unstaged |
| M | `tools/ui-verify/verify_g3b1.py` | unstaged |
| M | `tools/ui-verify/verify_goals.py` | unstaged |
| A | `tools/ui-verify/verify_migration.py` | untracked |
| M | `tools/ui-verify/verify_prod_smoke.py` | unstaged |
| A | `tools/ui-verify/verify_qa_preflight.py` | untracked |
| M | `tools/ui-verify/verify_settings.py` | unstaged |
| A | `tools/ui-verify/verify_weekly_ai.py` | untracked |

## 设备与 APK

- 设备：2509FPN0BC / Android 17 / `192.168.10.183:33537`
- qa APK：`055da4efd2f6042fd80400943f08924f2d8e09a1fe341aa6c3eba56bf39c2e70` · 12393063 B · versionName=0.14.1-g3e2-1 · 复用安装

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| ai-consent（AI 授权与配置） | high | 3 | verify_g3b1.py, verify_settings.py | integration 级别强制包含高风险模块 |
| database-migration（Room Schema / DAO / Migration） | critical | 0 | verify_migration.py | 显式指定 --module database-migration |
| navigation（导航与共享 UI） | medium | 0 | — | L1+ 必要导航冒烟（跨页面回归） |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（schema-contract, validate-map） | PASS | 0.1s | issues=0 |  |
| 安装 qa APK | SKIPPED | 0.0s |  |  |
| QA 安全前置校验（包名 / QA_BUILD 标记） | PASS | 30.1s | assertions=10/10 | `qa-verify_qa_preflight.log` |
| JVM 测试（3 类） | PASS | 6.9s | tests=20, failures=0 | `jvm-testDebugUnitTest.log` |
| 真机 verify_g3b1.py | PASS | 426.4s | assertions=47/47 | `qa-verify_g3b1.log` |
| 真机 verify_migration.py | PASS | 29.2s | assertions=17/17 | `qa-verify_migration.log` |
| 真机 verify_navigation.py | PASS | 124.0s | assertions=28/28 | `qa-verify_navigation.log` |
| 真机 verify_settings.py | PASS | 170.3s | assertions=38/38 | `qa-verify_settings.log` |
| 清理 QA 数据（pm clear） | PASS | 0.0s |  |  |

## 真机用例明细

| 包 | 脚本 | 状态 | 断言 | 耗时 |
|---|---|---|---|---|
| com.mengyalife.app.qa | verify_g3b1.py | PASS | 47/47 | 426.4s |
| com.mengyalife.app.qa | verify_migration.py | PASS | 17/17 | 29.2s |
| com.mengyalife.app.qa | verify_navigation.py | PASS | 28/28 | 124.0s |
| com.mengyalife.app.qa | verify_settings.py | PASS | 38/38 | 170.3s |

## 未运行与原因

- 无

## 失败与日志

- 无失败

- 报告目录：`docs/reports/G3F-acceptance/db-l2`
- JSON：`docs/reports/G3F-acceptance/db-l2/report.json`
- 日志目录：`docs/reports/G3F-acceptance/db-l2/logs`

## 级别验收

- 满足当前级别：**是**
