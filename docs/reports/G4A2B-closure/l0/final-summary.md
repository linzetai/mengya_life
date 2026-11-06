# 测试执行报告 · 20261106-093126

- 开始：2026-11-06T09:31:26+0800
- 结束：2026-11-06T09:31:33+0800
- 总耗时：**6.9s**
- 级别：**fast**（L0 Fast）
- 模式：all
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`3fb6781b955163994d891b98ff5f1d12c30692fc`
- branch：`master`
- 基线：`3fb6781b955163994d891b98ff5f1d12c30692fc`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：117

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `app/src/main/java/com/mengyalife/app/MainActivity.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/advisor/AdvisorConfigRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/advisor/ConversationRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/advisor/GoalProposalApplyService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/advisor/GoalProposalRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/consent/AiConsentRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/growth/DiaryExtractionService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/growth/GrowthMemoryRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/growth/GrowthMemoryService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/growth/GrowthSignalRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/insight/InsightService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/preferences/ThemePreferences.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/qa/QaBackupFixtureSeeder.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/repository/GoalRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/repository/JournalRepository.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreCoordinator.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreGate.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreJournal.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/restore/RestoreSnapshotStore.kt` | unstaged |
| A | `app/src/main/java/com/mengyalife/app/data/restore/RestoreWriteBarrier.kt` | untracked |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyAiService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewPreferences.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewService.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewWorker.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/di/AppContainer.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/ui/advisor/AdvisorViewModel.kt` | unstaged |
| M | `app/src/main/java/com/mengyalife/app/ui/screens/SettingsScreen.kt` | unstaged |
| M | `app/src/test/java/com/mengyalife/app/advisor/AdvisorFakes.kt` | unstaged |
| A | `app/src/test/java/com/mengyalife/app/restore/RestoreGateJournalTest.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/restore/RestoreSnapshotSpaceTest.kt` | untracked |
| A | `app/src/test/java/com/mengyalife/app/restore/RestoreWriteBarrierTest.kt` | untracked |
| M | `docs/reports/G4A2A-acceptance/qa/preview-result.json` | unstaged |
| M | `docs/reports/G4A2A-acceptance/qa/preview-ui.xml` | unstaged |
| A | `docs/reports/G4A2B-CLOSURE-ACCEPTANCE.md` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-01/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-01/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-02/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-02/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-03/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-03/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-04/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-04/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-05/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-05/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-06/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-06/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-07/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-07/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-08/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-08/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-09/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-09/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-10/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-10/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-11/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-11/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-12/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-12/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-13/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-13/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-14/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-14/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-15/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-15/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-16/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-16/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-17/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-17/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-18/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-18/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-19/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-19/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-20/diagnostics.json` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failure-20/ui.xml` | untracked |
| A | `docs/reports/G4A2B-acceptance/failures/failures-result.json` | untracked |
| M | `docs/reports/G4A2B-acceptance/qa/e2e-result.json` | unstaged |
| A | `docs/reports/G4A2B-closure/apk/apk.sha256` | untracked |
| A | `docs/reports/G4A2B-closure/db/app_meta-A.json` | untracked |
| … | 其余 37 个 | |

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
| JVM 测试（全量） | PASS | 4.1s | tests=299, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备
- **advisor:verify_advisor.py**：该用例层级 feature 高于当前 fast 允许的 []
- **advisor:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **advisor:verify_advisor_network.py**：该用例层级 integration 高于当前 fast 允许的 []
- **ai-consent:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **ai-consent:verify_settings.py**：该用例层级 feature 高于当前 fast 允许的 []
- **app-smoke:verify_prod_smoke.py**：该用例层级 gate 高于当前 fast 允许的 []
- **backup-export:verify_backup_v2.py**：该用例层级 feature 高于当前 fast 允许的 []
- **backup-export:verify_backup_v2_full.py**：该用例层级 feature 高于当前 fast 允许的 []
- **backup-export:verify_backup_v2_failures.py**：该用例层级 feature 高于当前 fast 允许的 []
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
- **settings:verify_settings.py**：该用例层级 feature 高于当前 fast 允许的 []
- **settings:verify_theme.py**：该用例层级 feature 高于当前 fast 允许的 []
- **trends:verify_trends.py**：该用例层级 feature 高于当前 fast 允许的 []
- **weekly-ai:verify_weekly_ai.py**：该用例层级 feature 高于当前 fast 允许的 []
- **weekly-review:verify_weekly_ai.py**：该用例层级 feature 高于当前 fast 允许的 []

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-093126`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-093126/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-093126/logs`

## 级别验收

- 满足当前级别：**是**
- fast 级别未覆盖以下模块（需 L1+）：app-smoke, database-migration, navigation, settings
