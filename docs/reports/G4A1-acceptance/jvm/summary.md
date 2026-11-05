# 测试执行报告 · 20261105-091819

- 开始：2026-11-05T09:18:19+0800
- 结束：2026-11-05T09:18:57+0800
- 总耗时：**38.1s**
- 级别：**fast**（L0 Fast）
- 模式：all
- 结论：**PASS**
- 退出码：0

## Git 与变更

- commit：`69cecdecb386a4a2c27c9dd3f6a89614dc071022`
- branch：`master`
- 基线：`2ec84ab5a94ad8083be69309406c1dbef00f3d67`（上一次 verify 运行记录）
- 工作区 dirty：False
- 变更文件：36

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `app/src/main/AndroidManifest.xml` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/AndroidBackupStorage.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupCrypto.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupExporter.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupFileWriter.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupFormatV2.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupModels.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupPostRestoreProjection.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupSnapshotSource.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/BackupValidator.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/data/backup/CanonicalJson.kt` | committed |
| M | `app/src/main/java/com/mengyalife/app/data/local/growth/GrowthSignalDao.kt` | committed |
| M | `app/src/main/java/com/mengyalife/app/data/local/insight/InsightReportDao.kt` | committed |
| M | `app/src/main/java/com/mengyalife/app/data/local/proposal/GoalProposalDao.kt` | committed |
| M | `app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyAiDao.kt` | committed |
| M | `app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyReviewDao.kt` | committed |
| M | `app/src/main/java/com/mengyalife/app/di/AppContainer.kt` | committed |
| M | `app/src/main/java/com/mengyalife/app/ui/screens/SettingsScreen.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/ui/settings/BackupSection.kt` | committed |
| A | `app/src/main/java/com/mengyalife/app/ui/settings/BackupViewModel.kt` | committed |
| A | `app/src/main/res/xml/data_extraction_rules.xml` | committed |
| M | `app/src/test/java/com/mengyalife/app/advisor/InsightServiceTest.kt` | committed |
| M | `app/src/test/java/com/mengyalife/app/advisor/WeeklyAiTest.kt` | committed |
| M | `app/src/test/java/com/mengyalife/app/advisor/WeeklyReviewServiceTest.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/BackupArtifactMetricsTest.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/BackupCryptoTest.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/BackupExporterTest.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/BackupFileWriterTest.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/BackupFormatV2Test.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/BackupManifestSecurityTest.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/BackupTestFixtures.kt` | committed |
| A | `app/src/test/java/com/mengyalife/app/backup/CanonicalJsonTest.kt` | committed |
| M | `app/src/test/java/com/mengyalife/app/data/FakeGrowthSignalDao.kt` | committed |
| M | `tests/test-map.json` | committed |
| A | `tools/ui-verify/backup_v2_probe.js` | committed |
| A | `tools/ui-verify/verify_backup_v2.py` | committed |

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
| backup-export（完整备份导出） | high | 3 | — | --all 强制全量回归 |
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
| settings（设置与主题） | medium | 0 | — | --all 强制全量回归 |
| test-infra（测试体系自身） | low | 0 | — | --all 强制全量回归 |
| trends（趋势） | medium | 3 | — | --all 强制全量回归 |
| weekly-ai（每周 AI 增强） | high | 4 | — | --all 强制全量回归 |
| weekly-review（每周成长回顾（本地）） | high | 3 | — | --all 强制全量回归 |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（schema-contract, script-syntax, validate-map） | PASS | 2.2s | issues=0 |  |
| JVM 测试（全量） | PASS | 35.2s | tests=273, failures=0 | `jvm-testDebugUnitTest.log` |

## 未运行与原因

- **真机测试**：fast 级别不启动设备
- **advisor:verify_advisor.py**：该用例层级 feature 高于当前 fast 允许的 []
- **advisor:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **advisor:verify_advisor_network.py**：该用例层级 integration 高于当前 fast 允许的 []
- **ai-consent:verify_g3b1.py**：该用例层级 integration 高于当前 fast 允许的 []
- **ai-consent:verify_settings.py**：该用例层级 feature 高于当前 fast 允许的 []
- **app-smoke:verify_prod_smoke.py**：该用例层级 gate 高于当前 fast 允许的 []
- **backup-export:verify_backup_v2.py**：该用例层级 feature 高于当前 fast 允许的 []
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

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091819`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091819/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261105-091819/logs`

## 级别验收

- 满足当前级别：**是**
- fast 级别未覆盖以下模块（需 L1+）：app-smoke, database-migration, navigation, settings
