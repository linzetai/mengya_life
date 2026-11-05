# 备份 v2 实现说明（BACKUP-V2-IMPLEMENTATION）

> Gate 4A-1：只实现完整加密备份导出；**没有**导入/恢复、RestoreGate、恢复事务或 schema 变更。
> 产品决策已确认：A1 默认口令加密、B1 核心 7 表 + 洞察/本地周回顾（weekly AI 仅归档）、C1 v1 只识别、D1 endpoint/model 不含 Key。

## 1. 代码结构

| 层 | 文件 | 职责 |
|---|---|---|
| 模型 | `app/src/main/java/com/mengyalife/app/data/backup/BackupModels.kt` | 10 表快照、设置快照、artifact、CryptoParams |
| 收集 | `BackupSnapshotSource.kt` | `RoomBackupSnapshotSource` 在 `withTransaction` 内读 10 表；`AppBackupSettingsSource` 读非秘密设置 |
| 格式 | `BackupFormatV2.kt` | payload 编码、counts、perTableSha256、payloadSha256、AAD、envelope、v1/v2 识别 |
| 规范化 | `CanonicalJson.kt` | 键按 UTF-8 字节序、数字规范化、字符串转义、SHA-256 |
| 校验 | `BackupValidator.kt` | 结构/字段/计数/限额/主键/强引用/历史 warning |
| 加密 | `BackupCrypto.kt` | PBKDF2-HMAC-SHA256 + AES-256-GCM；参数边界；固定向量 |
| 编排 | `BackupExporter.kt` | 设置前后比对 → Room 快照 → 编码 → 校验 → 加密 → envelope |
| 落盘 | `BackupFileWriter.kt` + `AndroidBackupStorage.kt` | 私有临时文件 → SAF → 回读 → 解密校验 → 清理/删除新文件 |
| 投影 | `BackupPostRestoreProjection.kt` | 仅用于计算“预期恢复后哈希”的纯 JSON 变换；不写 DB |
| UI | `ui/settings/BackupViewModel.kt`、`BackupSection.kt` | 口令对话框、准备/写入状态、SAF CreateDocument、结果提示 |
| 依赖 | `di/AppContainer.kt` | `backupExporter`、`backupFileWriter` |
| 系统备份 | `AndroidManifest.xml`、`res/xml/data_extraction_rules.xml` | allowBackup=false + 云/D2D 全排除 |

数据流严格分层：

```text
Room 只读事务 + DataStore 设置快照
  → BackupFormatV2（Canonical JSON / counts / hashes）
  → BackupValidator（结构/引用/限额）
  → BackupCrypto（PBKDF2 + AES-GCM，AAD）
  → BackupFileWriter（私有临时文件 → SAF → 回读解密校验）
```

## 2. 10 张表字段覆盖矩阵

所有字段逐列导出，不依赖 UI 字段子集。字段名见 `BackupFormatV2.kt` 的 row builder；下表是覆盖清单。

| # | 表 | 导出数组 | 列数 | 字段 |
|---|---|---|---|---|
| 1 | `journal_entries` | `journalEntries` | 11 | id, title, body, createdAt, updatedAt, analysisStatus, analysisHash, analysisError, analysisAttempts, analyzedAt, analysisLastAttemptAt |
| 2 | `goals` | `goals` | 11 | id, title, description, level, parentId, domain, status, startDate, dueDate, createdAt, updatedAt |
| 3 | `goal_checkins` | `goalCheckins` | 7 | id, goalId, recordedAt, note, metricValue, metricUnit, source |
| 4 | `advisor_messages` | `advisorMessages` | 12 | id, role, content, createdAt, status, errorCode, errorMessage, contextGoalId, contextEpoch, sourcesJson, proposalError, proposalMode |
| 5 | `goal_proposals` | `goalProposals` | 13 | id, messageId, createdAt, schemaVersion, status, summary, assumptionsJson, risksJson, nodesJson, selectedTempIdsJson, appliedAt, appliedGoalIdsJson, undoneAt |
| 6 | `growth_signals` | `growthSignals` | 15 | id, sourceType, sourceId, sourceVersion, category, content, evidence, isInference, confidence, extractedAt, status, origin, sourceTitle, contentHash, updatedAt |
| 7 | `growth_memories` | `growthMemories` | 16 | id, theme, content, isInference, status, createdAt, updatedAt, signalIdsJson, sourceDiaryIdsJson, userCorrected, version, confidence, insufficient, recentChange, fingerprint, deletedAt |
| 8 | `insight_reports` | `insightReports` | 11 | id, dataVersion, status, summary, factsJson, insightsJson, periodStart, periodEnd, generatedAt, error, model |
| 9 | `weekly_reviews` | `weeklyReviews` | 11 | id, periodKey, periodStart, periodEnd, generatedAt, version, dataVersion, aiConsentVersion, factsJson, sectionsJson, notifiedAt |
| 10 | `weekly_ai_reviews` | `weeklyAiReviews` | 13 + 归档语义 | periodKey, localDataVersion, aiConsentVersion, status, generatedAt, summary, highlightsJson, suggestion, **factIds**（解析后数组）、**legacyFactIdsRaw**（原始列）、categories, error, model, configHash |

说明：

- `weekly_ai_reviews` 是 **B1 归档**内容，写入备份但**恢复时排除**；不参与预算/授权判断。
- `fact_ids_json` 历史值可能是 U+001F 分隔串；导出时同时给出 `factIds` 数组和 `legacyFactIdsRaw`，不丢历史。
- 各表按主键稳定排序（goals 按 level/id，其余 id/periodKey），排序结果进入 perTableSha256。
- 设置只含 `themeMode`、`weeklyReview.enabled/aiEnabled/notificationAsked`、`ai.endpoint/model`；不含 Key/consent/fake engine/设备标识。

## 3. 一致快照

- `RoomBackupSnapshotSource.load()` 使用 `MengyaDatabase.withTransaction` 读取 DAO 全表；DAO 查询在同一事务快照内。
- 设置读取两次（DB 前后）+ 加密后再核对一次；任一前后不一致抛 `SETTINGS_CHANGED`，UI 提示重试。
- 设置读取不放在 Room 事务里（DataStore 独立），因此不能宣称“跨存储原子快照”；当前策略是**检测变化并失败**，不静默生成不一致备份。
- 新增 DAO 只读方法：`GoalProposalDao.loadAll`、`GrowthSignalDao.loadAll`（含 STALE/DELETED）、`InsightReportDao.loadAll`、`WeeklyReviewDao.loadAll`、`WeeklyAiDao.loadAll`。

## 4. v2 格式实现要点

- `backupFormatVersion=2`、`dbSchemaVersion=DB_SCHEMA_VERSION(11)`、`backupId`、`createdAt`/`createdAtIso`。
- `counts` 与 10 个数组长度一致；每表 `perTableSha256`；外层 `payloadSha256`。
- `CanonicalJson`：键 UTF-8 字节序、无多余空白、数字去尾零/无指数、字符串转义、`[]` 空表向量。
- AAD 对象：`format/backupFormatVersion/payloadSha256/encryption`；不包含 `aad`/`ciphertext`。
- envelope：`format/backupFormatVersion/payloadSha256/encryption/ciphertext`；payload 不含 `payloadSha256`，无自引用。
- 大小限制：64 MiB 文件 / 48 MiB payload / 200k 总行 / 100k 单表。
- 未知表/未知 requiredFeatures/未来版本拒绝；未知可选字段仅 warning。

## 5. v1 保留

- `ExportJson.kt` / `ExportViewModel` / 旧 SAF 流程未修改。
- 设置页同时保留旧“导出我的数据”（三表 JSON）和新的“完整加密备份 v2”，并在 v2 区域提示旧格式不完整。
- v1 文件识别为 `V1_LEGACY`（`BackupFormatV2.detect`）；不提供恢复。

## 6. 系统备份

- `allowBackup=false` 保持不变。
- 新增 `dataExtractionRules` 与 `fullBackupContent=false`；`cloud-backup`/`device-transfer` 排除 root/file/database/sharedpref/external。
- 最终合并 Manifest（QA 与 debug）均确认三个属性存在。

## 7. 技术债修正（独立提交 `2ec84ab`）

- 新写入 `fact_ids_json` 为真正 JSON 数组（`WeeklyAiParser.encodeFactIds`）。
- 读取兼容旧 U+001F（`decodeFactIds`）。
- `WeeklyAiService.toDomain` 使用兼容读取；历史行不批量改写。
- 单测 `WeeklyAiFactIdsTest`；备份格式仍同时保留 `legacyFactIdsRaw`。

## 8. 未实现/边界

- **没有恢复/导入**；`BackupPostRestoreProjection` 仅做哈希投影，不写 DB。
- 没有 RestoreGate、恢复状态机、app_meta 表或 schema 变更。
- QA 真机已执行导出验证（28/28 PASS）；真实 SAF 写失败注入仍为残余项。
- 没有云同步/上传。
- 未修改正式用户数据。
