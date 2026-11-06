# 测试执行报告 · 20261106-174143

- 开始：2026-11-06T17:41:43+0800
- 结束：2026-11-06T17:41:51+0800
- 总耗时：**7.7s**
- 级别：**fast**（L0 Fast）
- 执行 profile：**standard**
- 模式：all
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`944cbe1b1f050cbf2fa8eb68ededd32fa79d19e8`
- branch：`master`
- 基线：`407ef8cabbed51fe00b58b8e6e9a4c65ca5a726c`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：17

| 状态 | 文件 | 来源 |
|---|---|---|
| A | `docs/reports/G4A2B-acceptance/failures/failure-R2-result.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-R3-result.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-R4-result.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-R5-result.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-R6-result.json` | untracked |
| M | `docs/reports/G4A2B-acceptance/failures/failure-R7-result.json` | unstaged |
| A | `docs/reports/G4A2B-acceptance/failures/failure-SETTINGS_FAIL-result.json` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/G4A2B-FINAL-ACCEPTANCE.md` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/SHA256SUMS` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/apk-history.md` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/logs/l0-jvm-303-summary.md` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/logs/settings-fail-result.json` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/source/restore-write-barrier.patch` | untracked |
| A | `docs/reports/G4A2B-final-acceptance/source/test-selection-and-scenario.patch` | untracked |
| M | `tests/test-map.json` | committed |
| M | `tests/verify/validate.py` | committed |
| M | `tools/ui-verify/verify_restore_db_unreadable.py` | committed |

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因

- 规则 `force-full`：--all 强制全量回归

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| advisor（AI 顾问） | high | 9 | — | --all 强制全量回归 |
| ai-consent（AI 授权与配置） | high | 3 | — | --all 强制全量回归 |
| app-smoke（应用启动与正式包冒烟） | high | 0 | — | --all 强制全量回归 |
| backup-crypto（备份加密） | critical | 1 | — | --all 强制全量回归 |
| backup-export（完整备份导出） | high | 4 | — | --all 强制全量回归 |
| backup-format（备份格式/校验） | high | 2 | — | --all 强制全量回归 |
| backup-security（备份安全） | critical | 1 | — | --all 强制全量回归 |
| database-migration（Room Schema / DAO / Migration） | critical | 0 | — | --all 强制全量回归 |
| diary（日记） | medium | 4 | — | --all 强制全量回归 |
| export（数据导出） | high | 1 | — | --all 强制全量回归 |
| goals（目标） | high | 3 | — | --all 强制全量回归 |
| growth-memory（成长记忆与日记提取） | high | 4 | — | --all 强制全量回归 |
| insight（成长洞察） | medium | 3 | — | --all 强制全量回归 |
| navigation（导航与共享 UI） | medium | 0 | — | --all 强制全量回归 |
| progress（进展与打卡） | high | 3 | — | --all 强制全量回归 |
| restore-validate（恢复只读校验/预览） | high | 6 | — | --all 强制全量回归 |
| settings（设置与主题） | medium | 0 | — | --all 强制全量回归 |
| test-infra（测试体系自身） | low | 0 | — | --all 强制全量回归 |
| trends（趋势） | medium | 3 | — | --all 强制全量回归 |
| weekly-ai（每周 AI 增强） | high | 4 | — | --all 强制全量回归 |
| weekly-review（每周成长回顾（本地）） | high | 3 | — | --all 强制全量回归 |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（schema-contract, script-syntax, validate-map） | PASS | 2.1s | issues=0 |  |
| JVM 测试（全量） | PASS | 4.9s | tests=303, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备
- **advisor:verify_advisor.py**：该用例层级 feature 高于当前 fast 允许的 []
- **advisor:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **advisor:verify_advisor_network.py**：该用例层级 gate 高于当前 fast 允许的 []
- **ai-consent:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **app-smoke:verify_prod_smoke.py**：该用例层级 gate 高于当前 fast 允许的 []
- **backup-export:verify_backup_v2_full.py**：该用例层级 feature 高于当前 fast 允许的 []
- **backup-export:verify_backup_v2_failures.py**：该用例层级 integration 高于当前 fast 允许的 []
- **database-migration:verify_migration.py**：该用例层级 integration 高于当前 fast 允许的 []
- **diary:verify_diary.py**：该用例层级 feature 高于当前 fast 允许的 []
- **export:verify_export.py**：该用例层级 feature 高于当前 fast 允许的 []
- **goals:verify_goals.py**：该用例层级 feature 高于当前 fast 允许的 []
- **goals:verify_dates.py**：该用例层级 feature 高于当前 fast 允许的 []
- **navigation:verify_navigation.py**：该用例层级 feature 高于当前 fast 允许的 []
- **progress:verify_goals.py**：该用例层级 feature 高于当前 fast 允许的 []
- **restore-validate:verify_restore_e2e.py**：该用例层级 feature 高于当前 fast 允许的 []
- **restore-validate:verify_restore_failures.py**：该用例层级 feature 高于当前 fast 允许的 []
- **restore-validate:verify_restore_fresh_db.py**：该用例层级 feature 高于当前 fast 允许的 []
- **restore-validate:verify_restore_db_unreadable.py**：该用例层级 integration 高于当前 fast 允许的 []
- **settings:verify_settings.py**：该用例层级 feature 高于当前 fast 允许的 []
- **settings:verify_theme.py**：该用例层级 feature 高于当前 fast 允许的 []
- **trends:verify_trends.py**：该用例层级 feature 高于当前 fast 允许的 []
- **weekly-ai:verify_weekly_ai.py**：该用例层级 feature 高于当前 fast 允许的 []
- **weekly-review:verify_weekly_ai.py**：该用例层级 feature 高于当前 fast 允许的 []

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-174143`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-174143/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-174143/logs`

## 级别验收

- 满足当前级别：**是**
- 本 profile 检查满足：**是**
- 完整级别验收满足：**是**
- fast 级别未覆盖以下模块（需 L1+）：app-smoke, database-migration, navigation, settings
