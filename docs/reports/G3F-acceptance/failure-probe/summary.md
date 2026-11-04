# 测试执行报告 · 20261105-041250

- 开始：2026-11-05T04:12:50+0800
- 结束：2026-11-05T04:13:11+0800
- 总耗时：**21.4s**
- 级别：**fast**（L0 Fast）
- 模式：all
- 结论：**FAIL**
- 退出码：1

## Git 与变更

- commit：`10c14d3b100b48a15fd7697ae36a4b3141020d56`
- branch：`master`
- 基线：`10c14d3b100b48a15fd7697ae36a4b3141020d56`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：37

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `.gitignore` | unstaged |
| M | `README.md` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/MainActivity.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/advisor/AdvisorEngine.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyAiService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/di/AppContainer.kt` | unstaged |
| A | `app/src/test/java/com/mengyalife/app/advisor/GateFailureProbeTest.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/advisor/WeeklyAiFakeEngineTest.kt` | untracked |
| A | `docs/TEST-ACCEPTANCE.md` | untracked |
| A | `docs/TEST-ARCHITECTURE-AUDIT.md` | untracked |
| A | `docs/TEST-MODULE-MAP.md` | untracked |
| A | `docs/TEST-PERFORMANCE-BEFORE-AFTER.md` | untracked |
| A | `docs/TEST-RUNNER-DESIGN.md` | untracked |
| A | `docs/reports/logs/verify-shots/probe-fake.png` | untracked |
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

- 设备：未使用（本级别不启动设备）

## 选择与原因

- 规则 `force-full`：--all 强制全量回归

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| advisor（AI 顾问） | high | 9 | — | --all 强制全量回归 |
| ai-consent（AI 授权与配置） | high | 3 | — | --all 强制全量回归 |
| app-smoke（应用启动与正式包冒烟） | high | 0 | — | --all 强制全量回归 |
| database-migration（Room Schema / DAO / Migration） | critical | 0 | — | --all 强制全量回归 |
| diary（日记） | medium | 4 | — | --all 强制全量回归 |
| export（数据导出） | high | 1 | — | --all 强制全量回归 |
| goals（目标） | high | 3 | — | --all 强制全量回归 |
| growth-memory（成长记忆与日记提取） | high | 4 | — | --all 强制全量回归 |
| insight（成长洞察） | medium | 3 | — | --all 强制全量回归 |
| navigation（导航与共享 UI） | medium | 0 | — | --all 强制全量回归 |
| progress（进展与打卡） | high | 3 | — | --all 强制全量回归 |
| settings（设置与主题） | medium | 0 | — | --all 强制全量回归 |
| test-infra（测试体系自身） | low | 0 | — | --all 强制全量回归 |
| trends（趋势） | medium | 3 | — | --all 强制全量回归 |
| weekly-ai（每周 AI 增强） | high | 3 | — | --all 强制全量回归 |
| weekly-review（每周成长回顾（本地）） | high | 3 | — | --all 强制全量回归 |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（schema-contract, script-syntax, validate-map） | PASS | 5.3s | issues=0 |  |
| JVM 测试（全量） | **FAIL** | 14.5s | tests=245, failures=1 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备
- **advisor:verify_advisor.py**：该用例层级 feature 高于当前 fast 允许的 []
- **advisor:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **advisor:verify_advisor_network.py**：该用例层级 integration 高于当前 fast 允许的 []
- **ai-consent:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **ai-consent:verify_settings.py**：该用例层级 feature 高于当前 fast 允许的 []
- **app-smoke:verify_prod_smoke.py**：该用例层级 gate 高于当前 fast 允许的 []
- **database-migration:verify_migration.py**：该用例层级 integration 高于当前 fast 允许的 []
- **diary:verify_diary.py**：该用例层级 feature 高于当前 fast 允许的 []
- **export:verify_export.py**：该用例层级 feature 高于当前 fast 允许的 []
- **goals:verify_goals.py**：该用例层级 feature 高于当前 fast 允许的 []
- **goals:verify_dates.py**：该用例层级 feature 高于当前 fast 允许的 []
- **navigation:verify_navigation.py**：该用例层级 feature 高于当前 fast 允许的 []
- **progress:verify_goals.py**：该用例层级 feature 高于当前 fast 允许的 []
- **settings:verify_settings.py**：该用例层级 feature 高于当前 fast 允许的 []
- **settings:verify_theme.py**：该用例层级 feature 高于当前 fast 允许的 []
- **trends:verify_trends.py**：该用例层级 feature 高于当前 fast 允许的 []
- **weekly-ai:verify_weekly_ai.py**：该用例层级 feature 高于当前 fast 允许的 []
- **weekly-review:verify_weekly_ai.py**：该用例层级 feature 高于当前 fast 允许的 []

## 失败与日志

- `jvm` / com.mengyalife.app.advisor.GateFailureProbeTest.probe：java.lang.AssertionError: failure probe expected:<1> but was:<2>

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041250`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041250/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-041250/logs`

## 级别验收

- 满足当前级别：**否**
- 1 个步骤 FAIL。
- fast 级别未覆盖以下模块（需 L1+）：app-smoke, database-migration, navigation, settings
