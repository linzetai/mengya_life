# Gate 4A-0 报告：完整备份与恢复架构核查

> 批次性质：只读审计 + 方案设计 + 测试规划；**未实现恢复、未修改业务逻辑/数据库/迁移**。
> 审计时间：2026-11-05（Asia/Shanghai）。
> 审计基线：`master` / `f2ba1b2`（Gate 3F 已提交，工作区干净）。
> 本轮交付：`G4A0-DATA-INVENTORY.md`、`G4A0-BACKUP-FORMAT-V2.md`、`G4A0-RESTORE-TRANSACTION.md`、`G4A0-SECURITY-PRIVACY.md`、`G4A0-TEST-MATRIX.md`、本报告。

## 1. 执行摘要

1. **Gate 3F 已完成且证据充分**：统一入口、模块注册表、分级选择、报告与验收均存在；本批重新运行 L0 全量 JVM **244/244 PASS**（6.2s 测试执行，9.6s 总耗时；证据 `docs/reports/G3F-acceptance/g4a0-recheck/`）。详见 §6。
2. **当前没有“完整备份”**：v1 只导出 3/10 张表、23/120 个列（19.2%），且**没有恢复功能**；设置页明确“导入恢复尚未实现”。
3. **当前没有“恢复架构”**：没有文件选择/校验/预览/事务/回滚/并发控制；Room + 4 个 DataStore + WorkManager 的跨存储一致性未设计。
4. **系统备份配置不足**：`allowBackup=false` 但没有 `dataExtractionRules` / `fullBackupContent=false`；部分 Android 12+ OEM 的 D2D 可能复制 DB/Prefs，导致隐私与半恢复风险（P0）。
5. **秘密/授权策略必须 fail-closed**：API Key、Keystore、consent 永不进入备份；恢复后强制重新同意、重输 Key、关闭 AI 自动发送。
6. **推荐 v2**：逻辑行级、版本化、默认口令加密（PBKDF2 + AES-256-GCM）、核心 7 表 + 派生缓存、`weekly_ai_reviews` 只归档不恢复；全量替换 + 预快照 + app_meta/journal + 启动 reconciliation。
7. **结论：G3F 验收 PASS，具备进入 G4A-1 的技术前置；但需用户先确认 4 个 A/B 决策（加密默认、派生缓存、v1 策略、AI 配置范围）。**

## 2. 真实源码路径与关键证据

| 主题 | 路径 | 证据 |
|---|---|---|
| Room schema | `app/schemas/com.mengyalife.app.data.local.MengyaDatabase/11.json` | 10 表、120 列、version 11、identityHash `c70abb...` |
| 数据库/迁移 | `app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt` | `DB_SCHEMA_VERSION=11`（:55）；`MIGRATION_1_2`..`10_11`（:102-370）；无 destructive migration |
| 实体 | `data/local/**/*Entity.kt` | 10 个实体；字段见 DATA-INVENTORY §4 |
| DAO | `data/local/**/*Dao.kt` | 读写/事务/唯一键/软删除；无全表 `DELETE`（除 messages） |
| 导出 v1 | `data/export/DataExportRepository.kt`、`ExportJson.kt` | 三表事务（:47-49）；v1 `SCHEMA_VERSION=1`（:43）；只写 23 列（:152-184） |
| 导出 UI | `ui/settings/ExportViewModel.kt` | SAF 直接写 `openOutputStream`（:157）；回读校验（:111,166-168） |
| 导入占位 | `ui/screens/SettingsScreen.kt` | “导入恢复尚未实现”（:264-268,406） |
| 主题 DataStore | `data/preferences/ThemePreferences.kt` | `preferencesDataStore(name=PREFS_NAME)`（:23）；`theme_mode` |
| AI 配置 DataStore | `data/advisor/AiConfigStorage.kt` | 4 键（:43-46）；`encrypted_api_key` |
| AI 同意 DataStore | `data/consent/AiConsentRepository.kt` | 7 键（:46-52）；grant/withdraw/epoch |
| 周回顾 Prefs | `data/weekly/WeeklyReviewPreferences.kt` | 3 键（:22-24）；独立 DataStore |
| Keystore | `data/advisor/SecretCipher.kt` | AES/GCM，密钥不可导出；删除 alias 使旧密文不可解 |
| 周 AI 技术债 | `data/weekly/WeeklyAiService.kt` | `factIds.joinToString("\u001f")`（:184）；`toDomain` 返回空 factIds（:187-190） |
| 后台任务 | `data/weekly/WeeklyReviewWorker.kt`、`WeeklyReviewScheduler.kt` | 周期 Worker + 唯一任务；恢复时需取消/门禁 |
| 启动 | `MengyaApplication.kt` | 只做 scheduler sync（:24）；无恢复 reconciliation |
| Manifest | `app/src/main/AndroidManifest.xml` | `allowBackup=false`（:15）；无 dataExtractionRules/fullBackupContent |
| 合并 Manifest | `app/build/intermediates/merged_manifest/qa/.../AndroidManifest.xml` | 仍只有 allowBackup=false（:32） |
| G3F 入口 | `scripts/verify.sh`、`tests/test-map.json` | 16 模块；L0-L4；报告/退出码 |
| G3F 证据 | `docs/reports/G3F-acceptance/` | db-l2 PASS、diary-l1 PASS、weekly-l1 PASS、weekly-full 95/95、failure-probe FAIL(rc=1) |

## 3. 当前完整备份覆盖率

### 3.1 按实体/存储

| 维度 | 当前 v1 | 目标 v2 |
|---|---|---|
| Room 表 | 3 / 10 = **30.0%** | 10 / 10 = 100%（其中 1 表只归档） |
| Room 列 | 23 / 120 = **19.2%** | 120 / 120 = 100% |
| 核心用户表（journal/goals/checkins/messages/proposals/signals/memories） | 3 / 7 = **42.9%** | 7 / 7 = 100% |
| 派生表（insight/weekly review/weekly AI） | 0 / 3 = 0% | 3 / 3 归档；恢复 2 / 3 |
| DataStore 逻辑键 | 0 / 15 = 0% | 6 / 6 非秘密键（consent/Key 永不） |
| 恢复能力 | **0%** | G4A-2 全量替换 |
| 系统备份 | allowBackup=false，但无 rules | 显式排除全部域 |

### 3.2 逐实体

| 实体 | 当前 v1 | v2 备份 | v2 恢复 | 说明 |
|---|---|---|---|---|
| JournalEntryEntity | 5/11 字段 | 是（核心） | 是 | 缺 analysis* |
| GoalEntity | 11/11 | 是（核心） | 是 | 父子拓扑 |
| GoalCheckinEntity | 7/7 | 是（核心） | 是 | FK |
| AdvisorMessageEntity | 0 | 是（核心） | 是 | 对话历史 |
| GoalProposalEntity | 0 | 是（核心） | 是 | 草案/审计 |
| GrowthSignalEntity | 0 | 是（核心） | 是 | 用户纠正不可重建 |
| GrowthMemoryEntity | 0 | 是（核心） | 是 | 用户纠正/墓碑 |
| InsightReportEntity | 0 | 是（派生） | 是（stale 规则） | AI 缓存 |
| WeeklyReviewEntity | 0 | 是（派生） | 是 | 本地规则可重建 |
| WeeklyAiEntity | 0 | 是（归档） | **否** | 预算/授权绑定；fact_ids 非 JSON |

## 4. P0/P1 风险

### P0（阻断 G4A-1/2 验收，必须关闭）

| ID | 风险 | 证据 | 影响 | 处理/门禁 |
|---|---|---|---|---|
| P0-1 | v1 不是完整备份，且无恢复 | 3/10 表、19.2% 列；Settings 导入占位 | 换机/刷机丢 7 表 + 设置；用户误信 | 文档/UI 明确；v2 目标；G4A-1 不得宣称 v1 完整 |
| P0-2 | 系统 D2D/云备份配置不足 | Manifest 只有 allowBackup=false；无 rules | 部分 OEM 复制未加密 DB/Prefs；半恢复 | G4A-1 加 `dataExtractionRules` + `fullBackupContent=false` + lint |
| P0-3 | 无跨存储恢复事务 | Room + 4 DataStore + WorkManager | 半恢复/状态错乱 | G4A-2 app_meta + journal + reconciliation + gate |
| P0-4 | 秘密/授权恢复策略未定义 | Key/Keystore/consent | 旧授权自动发送/Key 不可解 | 永不备份；恢复 reset；测试断言 |
| P0-5 | 无导入文件校验/限额 | 仅 v1 结构校验；无大小上限 | OOM/崩溃/部分写入 | 格式 §6；先校验后写 |
| P0-6 | 后台/AI 与恢复并发 | Worker/Advisor/Insight/Weekly AI 无全局 gate | 旧结果写新库；数据竞争 | G4A-2 RestoreGate + generation/epoch |
| P0-7 | 无恢复 UI/预览/确认/快照 | 无导入代码 | 误操作不可恢复 | G4A-2 设计；预快照 |


### P1（G4A-2/3 关闭）

| ID | 风险 | 处理 |
|---|---|---|
| P1-1 | 逻辑引用无 FK（proposal/signal/memory） | 恢复前/事务内引用校验；拒绝悬挂 |
| P1-2 | 派生缓存 stale/预算锁 | weekly AI 不恢复；insight 按 dataVersion；周回顾 notifiedAt 重置 |
| P1-3 | 旧 schema 备份字段缺失 | 有界映射 + 显式默认 + 预览说明 |
| P1-4 | 重复恢复/空间不足 | backupId/app_meta；预检容量 |
| P1-5 | QA/正式串数据 | 包名+qaBuild 严格匹配 |
| P1-6 | SAF 写入非原子 | 临时文件 + 回读 + 失败不报成功 |
| P1-7 | 测试/设备缺口 | G4A-0 设备断开；G4A-1/2 用 QA 真机补齐；沿用 G3F 分层 |
| P1-8 | 大文件性能/内存 | 上限 + 解析预算；不压缩；G4A-1 基准 |
| P1-9 | `fact_ids_json` 非 JSON | G4A-1 修 writer/reader（接受旧 U+001F/新 JSON）；历史不直接改 |

## 5. 推荐方案与备选

### 5.1 推荐

**v2 逻辑加密备份 + 全量替换恢复 + fail-closed 状态重置。**

- 格式：`backupFormatVersion=2`，独立于 `dbSchemaVersion=11`；核心 7 表 + 派生 2 表 + 设置；`weekly_ai_reviews` 只归档。
- 加密：口令 + PBKDF2-HMAC-SHA256（高迭代）+ AES-256-GCM；salt/nonce 随机；AAD 绑头；不压缩。
- v1：只识别、不恢复；绝不静默部分恢复。
- 恢复：校验→预览→确认→预快照→撤权/关 AI/清 Key→Room 事务全量替换→app_meta→DataStore→journal→重启；启动 reconciliation。
- 系统备份：显式排除全部域；不依赖 OS 备份。

### 5.2 备选与不推荐

| 备选 | 结论 |
|---|---|
| 直接复制 SQLite `.db` | 不推荐：WAL/连接/版本/跨设备；无法覆盖 DataStore |
| 逐条合并/upsert | 本轮不做；冲突语义复杂，先全量替换 |
| 依赖 Android Auto Backup/D2D | 不推荐：未加密、版本/Key 不一致、隐私风险 |
| 设备绑定 Keystore 加密备份 | 仅本地快照；不能换机 |
| 明文 v2 | 仅调试；不默认 |
| SQLCipher 整库 | 成本高，不解决 DataStore/跨设备；不选 |

## 6. Gate 3F 验收状态

**状态：PASS，无 PENDING。**

| 项 | 结果 |
|---|---|
| Git | `master` / `f2ba1b2`；G3F 提交完整 |
| 入口 | `./scripts/verify.sh`；`tests/test-map.json` 16 模块 |
| 模块化 | 源码→模块→JVM/真机→级别；`--changed/--module/--level/--all/--dry-run/--explain` |
| 本次复跑 | `--all --level fast`：静态 PASS + JVM **244/244 PASS**；总 9.6s（测试 6.2s）；证据 `g4a0-recheck/` |
| 证据 | `docs/reports/G3F-acceptance/`：db-l2 PASS（790.5s）、diary-l1 PASS（337.6s）、weekly-l1 PASS（644.2s）、weekly-full 95/95（1160s）、failure-probe FAIL rc=1 |
| 已知限制 | growth-memory/insight 无真机脚本；真实第三方模型仍为 Beta 前置；不影响本批备份审计 |

> G3F 真机证据来自提交时运行；本轮设备已断开，未重跑真机。没有把未执行的真机用例报成 PASS。

## 7. Git 状态

| 项 | 值 |
|---|---|
| 分支 | `master` |
| 审计起点 HEAD | `f2ba1b295f1ba7111c19dbcfbd3695088a7a220a` |
| 起点 status | clean |
| 交付时 HEAD | 本批文档提交（父提交 `f2ba1b2`；`git log -1`） |
| 交付时 status | clean（仅本批 6 个文档） |
| 本批变更 | **新增** 6 个 `docs/G4A0-*.md` + G3F 前置复跑证据（`docs/reports/G3F-acceptance/g4a0-recheck/`）；无业务/数据库/恢复代码改动 |
| 本批提交 | 见 `git log -1`（提交信息 `Gate 4A-0: backup/restore audit and design`） |
| 用户既有修改 | 未删除/覆盖；G3F 证据与历史截图保留 |

## 8. 是否满足进入 G4A-1

**技术前置：满足。** G3F 已验证，G4A-0 审计/格式/恢复/安全/测试规划已交付。

**流程前置：需用户确认 4 个 A/B 决策。** 未确认前不开始 G4A-1 编码。

### 需要用户确认

| 决策 | 选项 | 推荐 |
|---|---|---|
| A. v2 加密默认 | A1 默认口令加密；A2 默认明文可选加密 | **A1** |
| B. 派生缓存 | B1 核心 7 + 派生 2（weekly AI 只归档）；B2 仅核心 7 | **B1** |
| C. v1 处理 | C1 只识别不恢复；C2 有界兼容三表 | **C1** |
| D. AI endpoint/model | D1 备份但不含 Key、不自动启用；D2 完全不备份 | **D1** |

## 9. G4A-1 / G4A-2 / G4A-3 实施边界

### G4A-1：v2 备份导出 + 安全基线

**范围（In）：**

- `data/backup/BackupFormatV2.kt`：模型/编码/解析/规范化/限额。
- `data/backup/BackupValidator.kt`：结构/计数/引用/枚举/版本。
- `data/backup/crypto/BackupCrypto.kt`：PBKDF2/AES-GCM；测试向量。
- `data/backup/BackupExporter.kt`：10 表 + 设置一致快照；v1 识别。
- `data/backup/BackupFileWriter.kt`：临时文件 + SAF + 回读。
- UI：设置页“导出完整备份（v2）”；v1 说明。
- Manifest：`dataExtractionRules` + `fullBackupContent=false`。
- `WeeklyAiService` fact_ids writer/reader 修正（标准化 JSON；历史不改）。
- `tests/test-map.json` 注册新模块 + L0/L1 测试 + QA 导出脚本。

**范围外（Out）：** 恢复/导入、数据库 schema 变更、自动合并、云同步。

**退出：** B-01..B-12 通过；v1 不被伪装；无 Key/consent；manifest 规则有测试；G3F 回归不退化。

### G4A-2：全量替换恢复核心

**范围（In）：**

- DB schema v12：新增 `app_meta`（restore_id/generation）迁移 + G3F 迁移测试更新。
- `data/restore/BackupReader/Decryptor/RestoreValidator/RestorePlanner`。
- `data/restore/RestoreTransaction`：DELETE/INSERT/校验/回滚。
- `data/restore/RestoreJournal/Coordinator`：phase/app_meta；启动 reconciliation。
- `data/restore/RestoreGate`：UI + Worker + AI/服务门禁；cancel WorkManager。
- `data/restore/SettingsRestorePolicy`：consent/Key/AI auto/weekly/theme。
- 预恢复快照（noBackupFilesDir，设备本地加密/沙箱）。
- UI：文件选择、预览、确认、进度、完成页/重启。
- QA 真机脚本：`verify_restore.py`、`verify_restore_recovery.py`。

**范围外（Out）：** 合并/去重、跨设备云同步、自动回滚覆盖当前数据、正式包破坏性恢复。

**退出：** R-01..R-10、R-13、R-14、R-17、R-19 通过；失败旧数据不变；恢复后 AI/consent 重置；L2 真机 PASS。

### G4A-3：硬化与 Gate 收口

**范围（In）：**

- 进程死亡（事务中/提交后）、空间不足、重复导入、跨版本/跨包、AI/Worker 并发。
- 缓存 stale/预算、通知状态、启动 reconciliation 的边角。
- 性能基线（备份/恢复典型/大文件）与报告；不虚构节省。
- L3 全量 JVM + 七组真机 + 正式只读 + 新恢复 E2E。
- 运行手册/故障恢复文档；P0/P1 关闭审计。

**范围外：** 新功能、自动合并、云同步。

**退出：** 全部 R/S/M/G 用例；P0 清零；L3 PASS；发布说明。

## 10. 交付清单与变更

新增文档：

1. `docs/G4A0-DATA-INVENTORY.md`
2. `docs/G4A0-BACKUP-FORMAT-V2.md`
3. `docs/G4A0-RESTORE-TRANSACTION.md`
4. `docs/G4A0-SECURITY-PRIVACY.md`
5. `docs/G4A0-TEST-MATRIX.md`
6. `docs/G4A0-REPORT.md`

附加证据：`docs/reports/G3F-acceptance/g4a0-recheck/`（前置复跑 244/244 PASS），G3F 证据索引新增 1 行。

未修改：任何 `app/src/**`、`app/schemas/**`、`tools/ui-verify/**`、`tests/**`、`scripts/**`、`AndroidManifest.xml`、Gradle 文件。

> 本报告不声称任何恢复功能已实现；G4A-1/2/3 均需用户批准后另行实施。
