# 测试模块注册表（TEST-MODULE-MAP）

> 机器可读的权威定义：`tests/test-map.json`。本文件是它的可读摘要；两者不一致时以 JSON 为准，并由 `./scripts/verify.sh --dry-run` 的注册表校验兜底。

- Gate：3F
- 模块数：21；执行级别：fast, feature, integration, gate, release。
- 设计原则：**逻辑模块化**，不拆 Gradle 多模块；源码路径 → 模块 → JVM/真机入口 → 级别，全部可审计。
- 注册表20个唯一真机资产（19个QA + 1个prod；23处模块引用）；共享脚本按 `(script, package)` 合并，只跑一次。
- 耗时为历史真机数据/估算；仅在本轮实测后回填，`—` 表示无真机覆盖或未测。

## 模块总表

| 模块 | 业务 | 风险 | 默认级别 | 上游依赖 | JVM 类 | 真机入口 | QA 构建 | 重置 QA | 正式包只读 | 实测耗时 |
|---|---|---|---|---|---|---|---|---|---|---|
| `diary` | 日记 | medium | feature | database-migration | JournalRulesTest<br>JournalRepositoryTest<br>JournalViewModelTest<br>TimestampsTest | `verify_diary.py` (feature, qa) | 是 | 是 | — | 161s |
| `goals` | 目标 | high | feature | database-migration | GoalRulesTest<br>GoalRepositoryTest<br>TimestampsTest | `verify_goals.py` (feature, qa)<br>`verify_dates.py` (feature, qa) | 是 | 是 | — | 563s |
| `progress` | 进展与打卡 | high | feature | goals | GoalRepositoryTest<br>GoalTrendsTest<br>TimestampsTest | `verify_goals.py` (feature, qa) | 是 | 是 | — | 470s |
| `trends` | 趋势 | medium | feature | goals, progress, diary | GoalTrendsTest<br>MetricFormatTest<br>TimestampsTest | `verify_trends.py` (feature, qa) | 是 | 是 | — | 475s |
| `advisor` | AI 顾问 | high | feature | ai-consent | AdvisorContextAssemblerTest<br>AdvisorRulesTest<br>AdvisorViewModelTest<br>AdvisorRequestBudgetTest<br>NetworkAdvisorEngineTest<br>GoalProposalApplyRulesTest<br>GoalProposalIdsTest<br>GoalProposalParserTest<br>QaFaultInjectorTest | `verify_advisor.py` (feature, qa)<br>`verify_g3b1.py` (integration, qa)<br>`verify_advisor_network.py` (gate, qa) | 是 | 是 | — | 897s |
| `ai-consent` | AI 授权与配置 | high | integration | database-migration | AiConsentRulesTest<br>AdvisorConfigTest<br>InsightServiceTest | `verify_g3b1.py` (integration, qa)<br>`verify_settings.py` (feature, qa) | 是 | 是 | — | 539s；与 advisor/settings 同轮时共享脚本只跑一次 |
| `growth-memory` | 成长记忆与日记提取 | high | feature | diary, database-migration | GrowthMemoryTest<br>GrowthMemoryValidityTest<br>DiaryExtractionTest<br>DiaryExtractionProtocolTest | —（覆盖缺口） | 是 | 否 | — | — |
| `insight` | 成长洞察 | medium | feature | advisor, ai-consent, growth-memory, diary, goals | InsightParserTest<br>InsightRulesTest<br>InsightServiceTest | —（覆盖缺口） | 是 | 否 | — | — |
| `weekly-review` | 每周成长回顾（本地） | high | feature | goals, diary, growth-memory | WeeklyReviewRulesTest<br>WeeklyReviewServiceTest<br>TimestampsTest | `verify_weekly_ai.py` (feature, qa) | 是 | 是 | — | 308s |
| `weekly-ai` | 每周 AI 增强 | high | feature | weekly-review, ai-consent, advisor, goals, diary, trends | WeeklyAiTest<br>AdvisorRequestBudgetTest<br>WeeklyAiFakeEngineTest | `verify_weekly_ai.py` (feature, qa) | 是 | 是 | — | 308s |
| `settings` | 设置与主题 | medium | feature | — | — | `verify_settings.py` (feature, qa)<br>`verify_theme.py` (feature, qa) | 是 | 是 | — | 290s；`verify_settings` 与 ai-consent 共用一次 |
| `export` | 数据导出 | high | feature | diary, goals, database-migration | ExportTest | `verify_export.py` (feature, qa) | 是 | 是 | — | 195s |
| `backup-format` | 备份格式/校验 | high | fast | database-migration | CanonicalJsonTest<br>BackupFormatV2Test | 无真机 | 否 | 否 | — | JVM |
| `backup-crypto` | 备份加密 | critical | fast | — | BackupCryptoTest | 无真机 | 否 | 否 | — | JVM |
| `backup-export` | 完整备份导出 | high | feature | backup-format, backup-crypto | BackupExporterTest<br>BackupFileWriterTest<br>BackupArtifactMetricsTest<br>FileBackupTempStoreTest | `verify_backup_v2_full.py` + `verify_backup_v2_failures.py` | 是 | 是 | — | 384s历史估时 |
| `backup-security` | 系统备份排除 | critical | integration | — | BackupManifestSecurityTest | 无真机 | 否 | 否 | — | JVM |
| `restore-validate` | 完整备份恢复/预览 | high | fast | backup-format, backup-crypto | Reader/Validator/Planner/Gate/Barrier tests | `verify_restore_e2e.py`, `verify_restore_failures.py`, `verify_restore_fresh_db.py`, `verify_restore_db_unreadable.py` | 是 | 是 | — | L2估算包含R2-R7矩阵 |
| `database-migration` | Room Schema / DAO / Migration | critical | integration | — | — | `verify_migration.py` (integration, qa) | 是 | 是 | — | ~34s估算 |
| `navigation` | 导航与共享 UI | medium | feature | — | — | `verify_navigation.py` (feature, qa) | 是 | 否 | — | 124s |
| `app-smoke` | 应用启动与正式包冒烟 | high | integration | database-migration | — | `verify_prod_smoke.py` (gate, prod) | 是 | 否 | 是 | 181s |
| `test-infra` | 测试体系自身 | low | fast | — | — | —（覆盖缺口） | 否 | 否 | — | — |

## 真机脚本资产

| 脚本 | 断言数 | 层级 | 实测 | 清库 | 改数据 | 说明 |
|---|---|---|---|---|---|---|
| `verify_advisor.py` | 59 | feature | 368s | 是 | 是 | QA 隔离 |
| `verify_advisor_network.py` | 10 | gate | 102s | 是 | 是 | 外网真实401探针；移到Gate/Release，L2用JVM网络模拟 |
| `verify_dates.py` | 17 | feature | 93s | 是 | 是 | QA 隔离 |
| `verify_diary.py` | 25 | feature | 161s | 是 | 是 | QA 隔离 |
| `verify_export.py` | 49 | feature | 195s | 是 | 是 | QA 隔离 |
| `verify_g3b1.py` | 47 | integration | 427s | 是 | 是 | QA 隔离 |
| `verify_goals.py` | 64 | feature | 470s | 是 | 是 | QA 隔离 |
| `verify_migration.py` | 26 | integration | 34s | 是 | 是 | v10/v11→v12迁移；最新schema演进时跑 |
| `verify_backup_v2_full.py` | 19 | feature | 55s | 是 | 是 | 默认全字段备份验收；已覆盖旧 `verify_backup_v2.py` 的基本SAF/解密/计数，不重复跑旧脚本 |
| `verify_backup_v2_failures.py` | 45 | feature | 329s | 是 | 是 | SAF写/读/清理故障路径，低频/合并运行 |
| `verify_restore_e2e.py` | 39 | feature | 当前198.30s | 是 | 是 | A→B→A全字段/安全状态及重启 |
| `verify_restore_failures.py` | 59 | feature | ~518s历史 | 是 | 是 | R2-R7/SETTINGS_FAIL支持case独跑，自建QA基线避免回放前序 |
| `verify_restore_fresh_db.py` | 6 | feature | 当前6/6 | 是 | 是 | fresh v12 app_meta初始化，可`--case FRESH_V12`单跑 |
| `verify_restore_db_unreadable.py` | 12 | integration | 当前12/12 | 是 | 是 | QA DB拒读维护页/无业务副作用，可`--case DB_UNREADABLE`单跑 |
| `verify_navigation.py` | 28 | feature | 124s | 否 | 否 | QA 隔离 |
| `verify_prod_smoke.py` | 50 | gate | 181s | 否 | 否 | 正式包只读 |
| `verify_settings.py` | 40 | feature | 112s | 是 | 是 | 文案/QA标识/备份与恢复入口；设置屏改动时检测核心入口可见 |
| `verify_theme.py` | 11 | feature | 178s | 否 | 否 | QA 隔离 |
| `verify_trends.py` | 73 | feature | 475s | 是 | 是 | QA 隔离 |
| `verify_weekly_ai.py` | 95 | feature | 308s | 是 | 是 | L1 冒烟 22 条；完整 95 条 |

## 源码路径映射

### `diary` · 日记

```
app/src/main/java/com/mengyalife/app/data/local/JournalEntryEntity.kt
app/src/main/java/com/mengyalife/app/data/local/JournalEntryDao.kt
app/src/main/java/com/mengyalife/app/data/repository/JournalRepository.kt
app/src/main/java/com/mengyalife/app/domain/JournalRules.kt
app/src/main/java/com/mengyalife/app/ui/journal/**
app/src/main/java/com/mengyalife/app/ui/screens/DiaryScreen.kt
```
QA 包清库后走新建/编辑/重启/删除全流程。

### `goals` · 目标

```
app/src/main/java/com/mengyalife/app/data/local/goal/**
app/src/main/java/com/mengyalife/app/data/repository/GoalRepository.kt
app/src/main/java/com/mengyalife/app/domain/goals/**
app/src/main/java/com/mengyalife/app/ui/goals/**
app/src/main/java/com/mengyalife/app/ui/screens/GoalsScreen.kt
app/src/main/java/com/mengyalife/app/ui/screens/GoalSheets.kt
app/src/main/java/com/mengyalife/app/data/DemoData.kt
```
verify_goals 含三层目标/禁止跳级/进展/状态/编辑/删除/统计；verify_dates 覆盖截止日期与 UTC 零点。

### `progress` · 进展与打卡

```
app/src/main/java/com/mengyalife/app/data/local/goal/GoalCheckinEntity.kt
app/src/main/java/com/mengyalife/app/domain/goals/GoalModels.kt
app/src/main/java/com/mengyalife/app/ui/screens/GoalSheets.kt
```
无独立脚本；进展面板与计数由 verify_goals 覆盖，图表由 verify_trends 覆盖。

### `trends` · 趋势

```
app/src/main/java/com/mengyalife/app/ui/screens/TrendsScreen.kt
app/src/main/java/com/mengyalife/app/ui/components/Charts.kt
app/src/main/java/com/mengyalife/app/domain/goals/GoalTrends.kt
app/src/main/java/com/mengyalife/app/util/MetricFormat.kt
```
依赖目标进展数据；空/单点/多点/多单位/非法数值。

### `advisor` · AI 顾问

```
app/src/main/java/com/mengyalife/app/data/advisor/**
app/src/main/java/com/mengyalife/app/domain/advisor/**
app/src/main/java/com/mengyalife/app/ui/advisor/**
app/src/main/java/com/mengyalife/app/ui/screens/AdvisorScreen.kt
```
verify_advisor 用 QA 假引擎（不联网）；verify_advisor_network 用无效测试 Key 验证真实 401，需外网。

### `ai-consent` · AI 授权与配置

```
app/src/main/java/com/mengyalife/app/data/consent/**
app/src/main/java/com/mengyalife/app/data/advisor/AdvisorConfigRepository.kt
app/src/main/java/com/mengyalife/app/data/advisor/AiConfigStorage.kt
app/src/main/java/com/mengyalife/app/data/advisor/SecretCipher.kt
app/src/main/java/com/mengyalife/app/domain/advisor/AiConsent.kt
app/src/main/java/com/mengyalife/app/ui/settings/AiPrivacySection.kt
app/src/main/java/com/mengyalife/app/ui/settings/AiSettingsSection.kt
app/src/main/java/com/mengyalife/app/ui/settings/AiSettingsViewModel.kt
```
授权、类别、撤回、密钥不回显/不入库/不进日志由 verify_g3b1 与 verify_settings 覆盖。

### `growth-memory` · 成长记忆与日记提取

```
app/src/main/java/com/mengyalife/app/data/growth/**
app/src/main/java/com/mengyalife/app/domain/growth/**
app/src/main/java/com/mengyalife/app/ui/growth/**
```
覆盖缺口：目前没有成长记忆/日记提取的真机脚本，只有 JVM；本轮不新增，记录为后续项。

### `insight` · 成长洞察

```
app/src/main/java/com/mengyalife/app/data/insight/**
app/src/main/java/com/mengyalife/app/domain/insight/**
app/src/main/java/com/mengyalife/app/ui/insight/**
app/src/main/java/com/mengyalife/app/ui/screens/OverviewScreen.kt
```
覆盖缺口：没有洞察真机脚本；仅 JVM。

### `weekly-review` · 每周成长回顾（本地）

```
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewService.kt
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewDataRepository.kt
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewNotification.kt
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewPreferences.kt
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewScheduler.kt
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewWorker.kt
app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyReviewEntity.kt
app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyReviewDao.kt
app/src/main/java/com/mengyalife/app/ui/weekly/WeeklyReviewScreen.kt
app/src/main/java/com/mengyalife/app/ui/weekly/WeeklyReviewViewModel.kt
```
本轮新增 verify_weekly_ai.py，覆盖本地回顾 + AI 状态机。

### `weekly-ai` · 每周 AI 增强

```
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyAiService.kt
app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyAiEntity.kt
app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyAiDao.kt
app/src/main/java/com/mengyalife/app/domain/weekly/**
```
本轮新增真机用例：成功/失败/超时/中断/STALE/撤权/自动开关；仅 QA 假引擎。

### `settings` · 设置与主题

```
app/src/main/java/com/mengyalife/app/ui/screens/SettingsScreen.kt
app/src/main/java/com/mengyalife/app/ui/settings/ThemeViewModel.kt
app/src/main/java/com/mengyalife/app/data/preferences/ThemePreferences.kt
app/src/main/java/com/mengyalife/app/ui/theme/**
```
文案漂移与主题持久化；不涉及数据库内容。

### `export` · 数据导出

```
app/src/main/java/com/mengyalife/app/data/export/**
app/src/main/java/com/mengyalife/app/ui/settings/ExportViewModel.kt
```
导出 JSON 与库逐项一致；写设备 Downloads（测试文件名带时间戳）。

### `database-migration` · Room Schema / DAO / Migration

```
app/src/main/java/com/mengyalife/app/data/local/**
app/schemas/**
```
JVM 无 Room 仪器测试；verify_migration.py 分别用v10/v11夹具在QA包上迁移到v12并校验保留。

### `navigation` · 导航与共享 UI

```
app/src/main/java/com/mengyalife/app/navigation/**
app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt
app/src/main/java/com/mengyalife/app/ui/components/Common.kt
```
五页切换/设置进出/返回；只读。

### `app-smoke` · 应用启动与正式包冒烟

```
app/src/main/java/com/mengyalife/app/MengyaApplication.kt
app/src/main/java/com/mengyalife/app/MainActivity.kt
app/src/main/java/com/mengyalife/app/di/AppContainer.kt
app/src/main/AndroidManifest.xml
app/build.gradle
build.gradle
settings.gradle
gradle.properties
```
正式包只读冒烟；要求正式库保留 2 条迁移日记且目标/进展为空。

### `test-infra` · 测试体系自身

```
tests/**
scripts/**
tools/ui-verify/**
```
改动测试脚本/注册表只做注册表校验与脚本静态检查，不跑无关 Android 真机。

## 影响规则

### `A-weekly` · 周回顾 / 周 AI 源码变化

- 最低级别：`feature`
- 选择模块：`weekly-review`, `weekly-ai`, `ai-consent`
- 规则真机模块：`weekly-review`, `weekly-ai`, `ai-consent`
- 理由：周回顾/AI 变化必须同时验证本地回顾、AI 状态机与授权边界，并跑周回顾 QA UI 冒烟。
- 命中路径：
```
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyAiService.kt
app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyAi*.kt
app/src/main/java/com/mengyalife/app/domain/weekly/WeeklyAi*.kt
app/src/main/java/com/mengyalife/app/ui/weekly/**
app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReview*.kt
app/src/main/java/com/mengyalife/app/data/local/weekly/WeeklyReview*.kt
```

### `B-goals-progress` · 目标 / 进展 / 打卡变化

- 最低级别：`feature`
- 选择模块：`goals`, `progress`, `trends`, `weekly-review`, `weekly-ai`
- 规则真机模块：`goals`, `progress`, `trends`
- 理由：目标/进展是趋势与周回顾的上游数据源，需连带验证依赖进展数据的趋势和持久化。
- 命中路径：
```
app/src/main/java/com/mengyalife/app/data/local/goal/**
app/src/main/java/com/mengyalife/app/data/repository/GoalRepository.kt
app/src/main/java/com/mengyalife/app/domain/goals/**
app/src/main/java/com/mengyalife/app/ui/goals/**
app/src/main/java/com/mengyalife/app/ui/screens/GoalsScreen.kt
app/src/main/java/com/mengyalife/app/ui/screens/GoalSheets.kt
```

### `C-ai-consent` · AI 授权 / 配置 / 密钥变化

- 最低级别：`feature`
- 选择模块：`advisor`, `ai-consent`, `weekly-ai`, `insight`, `settings`
- 规则真机模块：`advisor`, `ai-consent`, `settings`
- 理由：授权/配置影响所有 AI 出口（顾问、周 AI、洞察），必须验证授权、撤回、类别过滤与设置页冒烟。
- 命中路径：
```
app/src/main/java/com/mengyalife/app/data/consent/**
app/src/main/java/com/mengyalife/app/data/advisor/AdvisorConfigRepository.kt
app/src/main/java/com/mengyalife/app/data/advisor/AiConfigStorage.kt
app/src/main/java/com/mengyalife/app/data/advisor/SecretCipher.kt
app/src/main/java/com/mengyalife/app/domain/advisor/AiConsent.kt
app/src/main/java/com/mengyalife/app/ui/settings/AiPrivacySection.kt
app/src/main/java/com/mengyalife/app/ui/settings/AiSettingsSection.kt
app/src/main/java/com/mengyalife/app/ui/settings/AiSettingsViewModel.kt
```

### `D-room-migration` · Room Schema / DAO / Migration 变化

- 最低级别：`integration`
- 选择模块：`database-migration`, `diary`, `goals`, `progress`, `trends`, `advisor`, `growth-memory`, `insight`, `weekly-review`, `weekly-ai`, `export`
- 规则真机模块：`database-migration`, `export`
- 理由：数据库结构/迁移影响全部持久化数据；必须跑迁移、数据保留、导出与跨模块读取，禁止只跑单模块。
- 命中路径：
```
app/src/main/java/com/mengyalife/app/data/local/**
app/schemas/**
```

### `I-ui-screen` · 页面 UI 源码变化

- 最低级别：`feature`
- 选择模块：—
- 理由：页面 UI 变化需要对应模块的 QA UI 冒烟；具体模块由命中的源码路径决定，不靠 Agent 猜测。
- 命中路径：
```
app/src/main/java/com/mengyalife/app/ui/**
```

### `E-navigation-shared-ui` · 导航 / 共享 UI 组件变化

- 最低级别：`feature`
- 选择模块：`navigation`, `diary`, `goals`, `trends`, `advisor`, `settings`
- 规则真机模块：`navigation`, `diary`, `goals`, `trends`, `advisor`, `settings`
- 理由：导航/共享组件被五个一级页面复用，按实际依赖选择页面冒烟，避免只跑一个页面。
- 命中路径：
```
app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt
app/src/main/java/com/mengyalife/app/navigation/**
app/src/main/java/com/mengyalife/app/ui/components/**
app/src/main/java/com/mengyalife/app/ui/theme/**
```

### `F-build-manifest-di` · Gradle / Manifest / Application / DI 变化

- 最低级别：`integration`
- 选择模块：`app-smoke`, `navigation`
- 规则真机模块：`app-smoke`, `navigation`
- 额外要求：全量 JVM
- 理由：构建/入口/依赖注入变化可能影响所有页面启动；全量 JVM + 启动冒烟，Gate 时强制完整回归。
- 命中路径：
```
app/build.gradle
app/build.gradle.kts
build.gradle
build.gradle.kts
settings.gradle
settings.gradle.kts
gradle.properties
gradle/**
app/src/main/AndroidManifest.xml
app/src/main/java/com/mengyalife/app/MengyaApplication.kt
app/src/main/java/com/mengyalife/app/MainActivity.kt
app/src/main/java/com/mengyalife/app/di/AppContainer.kt
```

### `G-docs-only` · 仅文档/证据变化

- 最低级别：`fast`
- 选择模块：—
- 文档变化：不构建、不安装、不跑真机
- 理由：文档不改变 APK；不安装 APK、不跑 Android 真机。
- 命中路径：
```
docs/**
**/*.md
artifacts/**
.gitignore
```

### `H-test-infra` · 测试体系自身变化

- 最低级别：`fast`
- 选择模块：`test-infra`
- 理由：只做注册表/脚本静态校验；除非同时改了 app 源码。
- 命中路径：
```
tests/**
scripts/**
tools/ui-verify/**
```

### `J-jvm-test-source` · JVM 测试源码变化

- 最低级别：`fast`
- 选择模块：—
- 额外要求：全量 JVM
- 理由：测试源码变化无法从路径推断对应模块；保守跑全量 JVM（不启动设备），避免漏跑或把测试改动误判为未知源码。
- 命中路径：
```
app/src/test/**
```

## 级别与真机范围

- **L0 fast**：编译 + 受影响 JVM；不启动设备、不安装 APK。
  - 真机层级：不启动设备
- **L1 feature**：受影响 JVM + 对应模块 QA 真机功能测试；只有通用UI覆盖不足时附加导航冒烟，模块可声明已有UI脚本覆盖。
  - 真机层级：feature
- **L2 integration**：跨模块依赖 + 数据库/迁移 + AI 隐私/授权 + 后台 Worker 高风险路径。
  - 真机层级：feature, integration
  - 强制模块：database-migration, ai-consent
- **L3 gate**：全量 JVM + 全量 QA 真机 + 正式包只读冒烟 + 迁移/持久化 + 隐私用例。
  - 真机层级：feature, integration, gate
- **L4 release**：Gate 全部内容 + 严格要求设备/APK 哈希校验，不允许环境性跳过。
  - 真机层级：feature, integration, gate

开发迭代请用 `./scripts/verify.sh --profile dev --changed`：只跑变更影响到的静态/JVM或必要编译，真机脚本逐条报告为延期，结果显示 `LOCAL PASS (device deferred)`，不能当作L1/L2/Gate PASS。合并前仍需standard L1/L2；最终发版跑L4。
同一命令可传多个 `--module`，调度器只构建/安装一次，并按 `(script, package)` 折叠跨模块的重复脚本。

## 恢复写入安全影响规则

- `RestoreWriteBarrier.kt` / `RestoreCoordinator.kt` 以及 `AppContainer.kt` / `WeeklyReviewWorker.kt` 走 `L-restore-write-barrier`：L2覆盖屏障与业务写入口JVM、restore E2E/故障/fresh/DB不可读及AI consent安全脚本。
- `MengyaLifeApp.kt` / `MengyaApplication.kt` 走 `L-restore-startup-gate`：Room ViewModel需等READY再创建，覆盖fresh、DB不可读维护态、Weekly READY及restore安全路径。
- Gate/Release 不接受 `--case` 降级；未知源码路径仍按保守规则扩大到全模块。

## 说明

- `verify_weekly_ai.py` 在 L1/L2 跑22条READY成功冒烟（约308s）；Gate/Release跑95条完整状态机（历史约1160s）。JVM状态机用例在所有级别照常执行。
- `verify_g3b1.py` 为 integration 层：授权/撤回/类别属于 AI 隐私高风险路径，L1 不强制。
- `verify_migration.py` 用仓库schema生成v10与v11夹具，分别验证QA包升级到v12及用户数据保留。
- 正式包冒烟依赖正式库的 2 条 v1 迁移日记；调度器只校验、不写入。
