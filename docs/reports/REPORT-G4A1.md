# Gate 4A-1 报告：完整加密备份导出

> 批次：G4A-1（只导出，不恢复）。
> 产品决策：**A1** 默认口令加密；**B1** 核心 7 表 + 洞察/本地周回顾，weekly AI 仅归档；**C1** v1 只识别；**D1** 备份 endpoint/model、不备份 API Key。
> 时间：2026-11-05（Asia/Shanghai）。
> 状态：**PASS（JVM + QA 真机均通过；B-11 真实 SAF 故障注入为残余项）。**

## 1. 执行摘要

1. 新增独立 `data/backup` 模块，按“收集 → 格式 → 校验 → 加密 → 文件”分层；未写单 Repository/ViewModel。
2. 10 张表逐字段导出；核心 7 表完整；`insight_reports`/`weekly_reviews` 按 B1 恢复；`weekly_ai_reviews` 仅归档，恢复时排除。
3. 实现 v2 格式（`backupFormatVersion=2`、counts、perTableSha256、payloadSha256、Canonical JSON、requiredFeatures、generator/qaBuild）与口令加密（PBKDF2-HMAC-SHA256 310k、AES-256-GCM、独立 salt/nonce、AAD）。
4. 实现 SAF：设置页新入口 → 口令 → 准备 → 私有临时文件 → CreateDocument → 回读 → 解密/结构校验；失败不显示成功并按策略清理。
5. 安全：API Key/encrypted_api_key/Keystore/consent/旧接收方/fake engine/设备标识不进入备份；解密密文后的 payload 测试确认。
6. 系统备份：`allowBackup=false` 保持；新增 `dataExtractionRules` 与 `fullBackupContent=false`；QA/debug 合并 Manifest 核验通过。
7. v1 旧导出未修改；设置页同时显示“旧版数据导出（不完整）”与“完整加密备份 v2”。
8. `fact_ids_json` 技术债独立修正：新写入 JSON 数组，读取兼容旧 U+001F，历史行不改。
9. **JVM 273/273 PASS**；QA/debug APK 构建成功。
10. **QA 真机 28/28 PASS**：真实 SAF 导出/回读、独立 Node 解密、10 表计数、敏感字段扫描均通过；假数据文件 **5,445 字节**，端到端 **18.06s**。

## 2. 关键代码路径

| 主题 | 路径 |
|---|---|
| 格式/哈希/AAD | `app/src/main/java/com/mengyalife/app/data/backup/BackupFormatV2.kt` |
| Canonical JSON | `.../data/backup/CanonicalJson.kt` |
| 校验 | `.../data/backup/BackupValidator.kt` |
| 加密 | `.../data/backup/BackupCrypto.kt` |
| 快照/设置 | `.../data/backup/BackupSnapshotSource.kt` |
| 编排 | `.../data/backup/BackupExporter.kt` |
| SAF/临时文件 | `.../data/backup/BackupFileWriter.kt`、`AndroidBackupStorage.kt` |
| UI | `.../ui/settings/BackupViewModel.kt`、`BackupSection.kt` |
| 设置页接入 | `.../ui/screens/SettingsScreen.kt` |
| 依赖 | `.../di/AppContainer.kt` |
| 系统备份 | `app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/data_extraction_rules.xml` |
| 周 AI 技术债 | `.../domain/weekly/WeeklyAiParser.kt`、`.../data/weekly/WeeklyAiService.kt` |

## 3. 10 表覆盖（摘要）

完整字段矩阵见 `BACKUP-V2-IMPLEMENTATION.md` §2。

| 表 | 列数 | 导出 | 恢复策略 |
|---|---|---|---|
| journal_entries | 11 | 全字段 | 核心，G4A-2 |
| goals | 11 | 全字段 | 核心 |
| goal_checkins | 7 | 全字段 | 核心 |
| advisor_messages | 12 | 全字段 | 核心 |
| goal_proposals | 13 | 全字段 | 核心 |
| growth_signals | 15 | 全字段（含 STALE/DELETED） | 核心 |
| growth_memories | 16 | 全字段（含墓碑/纠正） | 核心 |
| insight_reports | 11 | 全字段 | B1 派生，stale 规则 |
| weekly_reviews | 11 | 全字段 | B1 派生 |
| weekly_ai_reviews | 13 + 归档字段 | 全字段 + factIds/legacy raw | **仅归档，不恢复** |

设置（D1）：theme、weekly enabled/ai/notificationAsked、AI endpoint/model；不含 Key/consent/fake。
一致性：Room `withTransaction` 读 10 表；设置读三次比对，变化则失败重试，不静默产出不一致备份。

## 4. 测试结果（真实）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `--module backup-format --level fast` | 0 | PASS，2 类，10.0s |
| `--module backup-crypto --level fast` | 0 | PASS，1 类，8.3s |
| `--module backup-export --level fast` | 0 | PASS，3 类，10.0s |
| `--module backup-security --level fast` | 0 | PASS，1 类，9.3s |
| `--all --level fast` | 0 | PASS，**273/273 JVM**，38.1s |
| `assembleQa + assembleDebug` | 0 | BUILD SUCCESSFUL，25s |
| `verify_backup_v2.py`（QA 真机） | 0 | **PASS，28/28**；端到端 18.06s |

B-01..B-15 的 JVM/静态与 QA 真机覆盖逐项见 `G4A1-TEST-RESULTS.md`；Gate 结论见 `G4A1-ACCEPTANCE.md`。
固定 KDF/AES-GCM 向量、Canonical JSON 向量已实际通过（key `a69b...`、ciphertext `67c5...`、tag `73b3...`）。

## 5. 大小与耗时

- QA 真机真实文件：**5,445 字节**（假数据：1 日记、1 目标、1 周回顾），端到端 **18.06s**。
- JVM 合成样本（12 条假记录、310k 迭代）：**6,689 字节 / 397ms**；仅链路/量级。
- 模块耗时：见上表；全量 JVM 测试 35.2s（总 38.1s）。

## 6. APK

| 包 | 版本 | SHA256 |
|---|---|---|
| QA `app-qa.apk` | `0.14.1-g3e2-1-qa` | `baa7a8b15bda89bc21b86cac86ceecc64861b2099b3b3dd304d698e432a04fba` |
| Debug `app-debug.apk` | `0.14.1-g3e2-1` | `dc97eedc77930fa2b5943fef8245ac0a68399db21bdeb86a6f882f85ee532c76` |

## 7. Git

| 提交 | 内容 |
|---|---|
| `2ec84ab` | `4A-1a` fact_ids JSON/旧格式兼容（独立提交） |
| `2d3dcf1` | `4A-1b` v2 格式/加密/导出/快照核心 |
| `1232dbc` | `4A-1c` 设置页 UI + 系统备份排除 |
| `69cecde` | `4A-1d` 测试、注册表、QA 探针 |
| `780091b` | `4A-1e` QA 真机脚本选择器与 28/28 结果 |
| 本报告提交 | 见最终 `git log -1` |

工作区最终状态：见交付时 `git status --short`（目标 clean）。

## 8. 未完成 / NOT RUN

1. B-11 的**真实 SAF 写失败注入**未执行；JVM fake destination 覆盖失败逻辑（残余项）。
2. 七组完整真机回归（本批不做）。
3. 恢复/导入、RestoreGate、app_meta、schema v12（本批明确不做，未开始 G4A-2）。
4. 大数据量真机性能未测；当前仅 1/1/1 假数据样本 5,445B / 18.06s。

## 9. 结论

**Gate 4A-1 PASS。** 实现、JVM 273/273、QA 真机 28/28、系统备份规则与 APK 构建均已验收。B-11 真实 SAF 故障注入仍为残余项，但不阻塞导出正向链路；完成后停止，不进入 G4A-2。
