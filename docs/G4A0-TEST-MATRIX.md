# G4A-0 测试矩阵（TEST MATRIX）

> 适用于 G4A-1/2/3；本批只做规划，不新增测试代码。
> 必须沿用 Gate 3F 的统一入口与分层策略：`./scripts/verify.sh` + `tests/test-map.json`。
> 任何新模块都要先登记到 `tests/test-map.json`，不允许 Agent 临时决定“只跑冒烟”。

## 1. 分层策略（继承 G3F）

| 级别 | 何时 | G4A 内容 | 设备 |
|---|---|---|---|
| **L0 fast** | 每次小改动 | 受影响的 JVM：格式/校验/KDF/行映射/事务纯逻辑 | 不启动 |
| **L1 feature** | 功能完成 | 备份导出 UI + 文件回读；模块 JVM | QA 真机导出 |
| **L2 integration** | 跨存储/事务/高风险 | 恢复事务、Room+DataStore、失败注入、Worker/AI 并发、回滚 | QA 真机 |
| **L3 gate** | 阶段收口/发布 | 全量 JVM + 全部恢复 E2E + 进程死亡/空间/跨版本/跨包 + 安全用例 | QA + 正式只读冒烟 |
| **Release** | 发布前 | L3 + 严格不跳过 + APK 哈希/版本 | 真机 |

**自动升级规则（写入 `test-map.json`）：**

- `app/src/main/java/**/backup/**`、`restore/**`、`crypto/**`、`data/local/MengyaDatabase.kt`、`app/schemas/**`、`app/src/main/AndroidManifest.xml` 中的备份规则 → 至少 **L2**；
- 恢复总控、加密、schema/migration、正式包恢复入口 → **L3**；
- 仅改文档/格式说明 → L0（不启动设备）；
- 不允许为了省时间把安全关键用例降级或标 SKIPPED 当通过。

## 2. 新增测试模块（建议注册表条目）

| 模块 | 源码 | JVM | 真机 | 风险 | 默认级别 |
|---|---|---|---|---|---|
| `backup-format` | `data/backup/**`（格式/编码/校验） | BackupFormatTest、BackupValidatorTest | — | high | feature |
| `backup-crypto` | `data/backup/crypto/**` | BackupCryptoTest（KDF/AEAD/篡改） | `verify_backup_crypto.py`（可选） | critical | integration |
| `backup-export` | `data/backup/BackupExporter.kt`、UI | BackupExporterTest、ExportV2Test | `verify_backup_export.py` | high | feature |
| `restore-validate` | `data/restore/BackupReader.kt` | RestoreValidatorTest | — | critical | integration |
| `restore-transaction` | `data/restore/RestoreTransaction.kt` | RestoreTransactionTest（fake DB） | `verify_restore.py` | critical | integration |
| `restore-coordinator` | `data/restore/RestoreCoordinator.kt`、journal、app_meta | RestoreCoordinatorTest | `verify_restore_recovery.py` | critical | gate |
| `backup-migration` | 旧 schema → v11 映射 | BackupCompatibilityTest | `verify_restore_migration.py` | critical | gate |
| `backup-security` | 秘密排除/包名/权限 | SecretExclusionTest | `verify_backup_privacy.py` | critical | gate |

> 这些是**计划**；G4A-1 先在 `tests/test-map.json` 注册，再写测试。

## 3. 测试数据与夹具

### 3.1 固定夹具（仓库内，纯 QA 假数据）

| 夹具 | 内容 | 用途 |
|---|---|---|
| `fixtures/backup-v2-minimal.mengyalife` | 空核心表 + 设置 | 空备份恢复 |
| `fixtures/backup-v2-typical.mengyalife` | 2 日记、三层目标、2 进展、2 对话、1 草案、2 线索、1 记忆、1 洞察、1 周回顾 | 完整恢复 |
| `fixtures/backup-v2-full.mengyalife` | 多单位/软删除/纠正/UNDONE/STALE | 边界 |
| `fixtures/backup-v2-legacy-schema10.mengyalife` | 缺 v11 字段 | 旧版映射 |
| `fixtures/backup-v2-wrong-password.mengyalife` | 正确口令之外的密文 | 解密失败 |
| `fixtures/backup-v2-corrupt.mengyalife` | 截断/字节翻转 | 完整性失败 |
| `fixtures/backup-v2-huge.mengyalife` | 超 64 MiB/行数 | 限额 |
| `fixtures/backup-v1-legacy.json` | 现有 v1 三表 | 只识别不恢复 |
| `fixtures/backup-v2-dangling.mengyalife` | 悬挂 proposal/signal | 引用校验 |
| `fixtures/backup-v2-duplicate-ids.mengyalife` | 重复 id/period | 唯一键/校验 |

- 所有夹具用测试数据，不含真实用户信息/Key。
- 加密夹具的测试口令只用于 QA，不写入生产。
- 不把真实用户导出放进仓库/证据包。

### 3.2 测试缝（G4A-1/2 需要）

| 缝 | 用途 |
|---|---|
| `BackupClock`（可注入时间） | 稳定 createdAt/backupId 测试 |
| `FileStore`/`CapacityProvider`（可注入） | 模拟空间不足、写失败、进程中断 |
| `RestoreFaultInjector`（仅 QA_BUILD） | 在 DB 前/事务中/提交后/设置阶段注入异常 |
| `CryptoTestVectors` | KDF/AEAD 已知向量；不依赖设备 |
| `FakeBackupDao`/Room in-memory（若可用） | JVM 事务逻辑；真机用 QA 包 |
| `QaFaultInjector`（已有） | 复用 DB 失败注入风格 |

## 4. 测试用例矩阵

### 4.1 备份（G4A-1）

| ID | 用例 | 级别 | 类型 | 步骤/断言 | 预期 |
|---|---|---|---|---|---|
| B-01 | 完整 v2 导出 | L1 | 真机 QA | 造典型数据 → 导出 → 拉文件 → 解密/校验 | 10 表计数/摘要一致；无 Key |
| B-02 | 空数据导出 | L0 | JVM+真机 | 空 DB → 导出 | counts=0；可恢复空数据 |
| B-03 | 限额/超大 | L0/L2 | JVM | 构造 >64MiB/超行数 | 拒绝，不 OOM |
| B-04 | v1 识别 | L0 | JVM | 现有 v1 文件 | 识别为 v1；不提供恢复 |
| B-05 | 未知字段/未知表 | L0 | JVM | 注入未知键 | 未知元数据警告；未知表拒绝 |
| B-06 | 加密往返 | L0 | JVM | 口令→加密→解密 | 载荷逐字节一致；salt/nonce 不同 |
| B-07 | 错误口令 | L0 | JVM | 错口令 | 解密失败，无部分输出 |
| B-08 | 篡改/截断 | L0 | JVM | 翻位/截断 | AEAD/hash 失败 |
| B-09 | KDF 参数边界 | L0 | JVM | 低迭代/坏 salt | 拒绝或升级提示 |
| B-10 | 计数/摘要 | L0 | JVM | 改 counts/hash | 校验失败 |
| B-11 | 导出中断 | L2 | 真机 QA | SAF 写入中断/回读失败 | 不显示成功；临时文件清理；旧文件不覆盖 |
| B-12 | 无敏感字段 | L0/L3 | JVM+证据扫描 | 扫描备份文本/日志 | 无 Key/consent/Keystore |

### 4.2 恢复（G4A-2）

| ID | 用例 | 级别 | 类型 | 步骤/断言 | 预期 |
|---|---|---|---|---|---|
| R-01 | 完整恢复 | L2 | 真机 QA | 造 A 数据 → 备份 → 改成 B → 恢复 | A 逐表逐字段一致；B 被替换 |
| R-02 | v1 拒绝 | L0 | JVM+真机 | 选 v1 | 明确拒绝；DB 不变 |
| R-03 | 缺字段 | L0 | JVM | 删除必填字段 | 拒绝，列出字段 |
| R-04 | 悬挂引用 | L0/L2 | JVM+真机 | 删父/消息/日记 | 拒绝；DB 不变 |
| R-05 | 非法枚举/状态 | L0 | JVM | 注入未知 level/status | 拒绝；不用默认值静默替换 |
| R-06 | 重复 id/period | L0 | JVM | 重复行 | 拒绝 |
| R-07 | 旧 schema（<11） | L2 | JVM+真机 | v10 备份 | 字段映射+显式默认；预览说明 |
| R-08 | 未来 schema（>11） | L0 | JVM | v12 备份 | 拒绝，提示升级 |
| R-09 | 重复导入 | L2 | 真机 QA | 同 backupId 连续两次 | 第二次提示“已恢复过”；显式确认后全量替换 |
| R-10 | 事务失败回滚 | L2 | 真机 QA | FaultInjector 在插入中抛错 | DB 逐行不变；journal FAILED |
| R-11 | 进程死亡（事务中） | L3 | 真机 QA | force-stop | 启动 reconciliation；旧 DB 不变 |
| R-12 | 进程死亡（提交后） | L3 | 真机 QA | force-stop 在 SETTINGS 前 | 启动补设置；数据新、状态一致 |
| R-13 | 空间不足 | L2 | JVM/真机 | 注入容量 | 预检中止；DB 不变 |
| R-14 | Room/DataStore 一致 | L2/L3 | 真机 QA | 恢复后读 DB + Prefs | 主题/周开关按规则；AI 关；key 无 |
| R-15 | Worker 并发 | L2/L3 | 真机 QA | 恢复时触发 `qa_run_weekly_review` | Worker 不写；或 gate 后 success |
| R-16 | AI 并发 | L2/L3 | 真机 QA | 慢速 AI 请求中恢复 | 旧结果丢弃；不写新 DB |
| R-17 | 授权/Key 重置 | L2 | 真机 QA | 恢复后查看设置 | 未授权；AI 未配置；自动关 |
| R-18 | QA/正式隔离 | L2/L3 | 真机 | QA 备份导入正式/反之 | 拒绝；数据不变 |
| R-19 | 失败后原数据不变 | L2 | 真机 QA | 各类失败后比对 | 旧 DB 哈希/行不变（设置按 fail-closed 说明） |
| R-20 | 缓存失效 | L2/L3 | 真机 QA | 恢复旧数据后看洞察/周回顾 | dataVersion 不匹配不显示旧 AI；weekly AI 不锁预算 |
| R-21 | 重启/重建 | L2 | 真机 QA | 恢复后杀进程/重启 | 数据/设置保持；无半状态 |
| R-22 | 恢复前快照 | L3 | 真机 QA | 恢复后选择回滚 | 快照可解；回滚走确认 |

### 4.3 跨版本/迁移（G4A-2/3）

| ID | 用例 | 级别 | 断言 |
|---|---|---|---|
| M-01 | v10 备份 → v11 App | L2 | 缺字段显式默认；`config_hash` 默认；预览说明 |
| M-02 | v11 备份 → v12 App（未来） | L3 | reader 兼容；新表 app_meta 重建 |
| M-03 | v12 备份 → v11 App | L0 | 拒绝，不尝试 |
| M-04 | v1 → v2 | L0 | 只识别；不静默三表恢复 |
| M-05 | 迁移后数据保留 | L3 | 复用 G3F `verify_migration`；恢复不破坏 |
| M-06 | `fact_ids_json` 旧格式 | L0/L2 | 接受 U+001F/JSON 两种；写出标准 JSON；不丢 id |

### 4.4 安全/隐私（G4A-3）

| ID | 用例 | 级别 | 断言 |
|---|---|---|---|
| S-01 | 备份无 API Key/Keystore/consent | L0/L3 | 文本/二进制扫描无关键字段；无 alias |
| S-02 | 系统备份规则 | L0 | Manifest 有 dataExtractionRules/fullBackupContent=false；lint |
| S-03 | 日志脱敏 | L0/L2 | 日志不含正文/Key/口令/路径 |
| S-04 | 恶意文件 | L0/L2 | 超大/嵌套/路径/注入字符串全部拒绝 |
| S-05 | QA/正式 | L2 | 跨包拒绝 |
| S-06 | 恢复后自动发送 | L2/L3 | consent 未授权 + AI 关；Worker 不发 |

### 4.5 回归（G4A-3）

| ID | 用例 | 级别 |
|---|---|---|
| G-01 | G3F 全量 JVM | L3 |
| G-02 | G3F 七组真机回归 | L3 |
| G-03 | 正式包只读冒烟 | L3 |
| G-04 | 备份/恢复新模块回归 | L3 |
| G-05 | 性能记录（各级别） | L3 |

## 5. 执行顺序与门禁

```text
L0: backup-format/crypto/validator + 受影响 JVM
L1: QA 导出 v2 + 回读；不恢复
L2: QA 恢复典型/失败/回滚/并发/DataStore
L3: 全量 JVM + 全部 R/S/M + 七组真机 + 正式只读
```

- **不允许**在 L2 用 L1 的“导出成功”替代恢复事务测试。
- **不允许**把“未执行/环境跳过”报告为 PASS；沿用 G3F 的 `SKIPPED`/`NOT RUN` 语义。
- 高风险（schema/migration/crypto/恢复总控）自动 L2/L3；如果用户指定低级别，调度器应提示并升级，不可静默降级。

## 6. 性能记录

每个级别记录实际耗时：

| 项 | 记录 |
|---|---|
| 备份导出（典型/大） | 构建、快照、加密、写 SAF、回读 |
| 恢复（典型/大） | 校验、解密、DB 事务、设置、重启 |
| 失败注入 | 各阶段耗时 |
| 总耗时 | 端到端 |

禁止虚构节省比例；对比旧流程时使用 G3F 的 2373s/3620s 基线时，必须注明是 9 脚本还是 12 脚本。

## 7. 真机前置与安全

- 只用 QA 包做破坏性恢复；`require_qa` + `QA_BUILD` 前置校验。
- 正式包只读冒烟，不执行恢复。
- 不把真实用户导出/Key 放到设备或仓库。
- 设备断开时，真机用例必须 `NOT RUN`，不能以 JVM 代替。
- 每个用例独立初始化/清理 QA 数据，不依赖顺序。

## 8. G4A-1/2/3 的测试退出条件

| 阶段 | 必须通过 |
|---|---|
| G4A-1 | B-01..B-12（JVM + QA 导出）；v1 不恢复；manifest 安全规则测试 |
| G4A-2 | R-01..R-10、R-13、R-14、R-17、R-19；L2 真机；回滚 |
| G4A-3 | 全部 R/S/M/G；L3 全量；进程死亡/并发/跨版本；正式只读 |
