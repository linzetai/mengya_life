# G4A-0 恢复事务设计（RESTORE TRANSACTION）

> 目标：以“全量替换”恢复 v2 备份；不实现自动合并、不做部分恢复。
> 本文是设计，不是实现。所有恢复代码、数据库改动都属于 G4A-2，本批不落地。

## 1. 设计目标与非目标

### 目标

1. 恢复成功后，本机用户数据与备份**逐表逐字段一致**（除明确排除项）。
2. 恢复失败时，旧用户数据保持完整（数据库事务回滚）。
3. Room 多表 + DataStore 多文件 + WorkManager 的一致性可验证、可在启动时收敛。
4. 恢复期间禁止 UI 写入、禁止 Worker、禁止 AI 请求。
5. 恢复后强制重新同意授权、重新配置 API Key、关闭 AI 自动发送。
6. 防止半恢复、重复恢复、空间不足、进程被杀导致不可恢复状态。

### 非目标

- 不做逐条合并/去重/冲突解决（未来单独设计）。
- 不恢复 API Key、Keystore、授权同意、系统通知权限。
- 不恢复 WorkManager 内部数据库。
- 不把 v1 三表导出当作完整备份恢复。
- 不自动上传/下载备份；只经 SAF 用户选择。

## 2. 恢复状态机

```text
IDLE
  → FILE_SELECTED
  → VALIDATED_HEADER
  → DECRYPTED
  → STRUCTURE_VALIDATED
  → PREVIEW_READY
  → CONFIRMED
  → PREFLIGHT_OK
  → SNAPSHOT_TAKEN
  → SECURITY_RESET        (撤权 / AI 自动关 / Key 清除策略)
  → DB_RESTORE_STARTED    (journal + DB transaction)
  → DB_COMMITTED          (app_meta.restore_id = backupId)
  → SETTINGS_APPLIED
  → COMPLETE
  → RELAUNCH
```

失败分支：

- `VALIDATED_HEADER`..`PREFLIGHT_OK`：直接 ABORTED，数据库未动。
- `SNAPSHOT_TAKEN` / `SECURITY_RESET`：数据库未动；授权/Key 可能已按安全策略重置（fail-closed）。
- `DB_RESTORE_STARTED`：SQLite 事务回滚，数据库保持旧数据；journal 标记 FAILED/ABANDONED。
- `DB_COMMITTED` 之后：数据库已是新数据；启动期 reconciliation 继续完成设置，必要时提示用户。

## 3. 用户流程

### 3.1 文件选择

- 设置页“恢复备份”入口（当前 `settings_import_data` 是禁用占位，`SettingsScreen.kt:264-268`）。
- 使用 SAF `ACTION_OPEN_DOCUMENT`，`type="*/*"`；不申请存储权限。
- 只读取用户选择的 URI；不扫描 Download/相册。
- 选择后立即 `stat` 大小（>64 MiB 拒绝），不把整个文件复制到公共目录。

### 3.2 校验与解密

顺序：

1. 读前 4 KiB，解析 `format`/`backupFormatVersion`；
2. 文件大小/行数上限（见格式文档 §6）；
3. 加密头：KDF/cipher/iterations/salt/nonce 合法性；
4. 用户输入口令 → 派生密钥 → AES-GCM 解密；
5. `payloadSha256`、counts、per-table 摘要；
6. 每行字段/枚举/长度/有限数；
7. 引用关系（见 §5）；
8. 跨包/跨版本检查；
9. 生成预览。

任何失败：显示具体原因（文件损坏/口令错/版本不支持/超限/引用缺失），**不写数据库**。

### 3.3 恢复前预览

必须展示：

| 项 | 示例 |
|---|---|
| 备份时间 | 2026-11-05 12:00（本机时区显示，底层 UTC） |
| 备份来源 App | 版本/versionCode；QA/正式 |
| 数据库版本 | 11 → 当前 11（或旧版映射说明） |
| 备份格式 | v2 / 加密 / KDF 参数摘要 |
| 条目计数 | 日记 N、目标 N、进展 N、对话 N、草案 N、线索 N、记忆 N、洞察 N、周回顾 N |
| 当前本机计数 | 同上（用于对比“将被覆盖”） |
| 不会恢复 | API Key、授权同意、AI 自动开关、通知权限、weekly AI 缓存 |
| 风险提示 | “恢复会替换本机全部用户数据；失败时回滚，但请先导出当前备份” |
| 文件大小/预计耗时 | 由校验结果估算 |

### 3.4 高风险确认

- 必须二次确认（勾选“我理解会覆盖本机数据”+ 点击“恢复”），不使用默认选中。
- 可在确认前提供“先导出当前完整备份”按钮（推荐强制/强提示）。
- 不允许在恢复过程中通过返回键取消已进入 DB 写入的阶段；UI 明确“正在替换，请勿退出”。

### 3.5 恢复前安全保护

1. 自动生成本机**恢复前快照**（pre-restore snapshot）；
2. 检查剩余空间 ≥ 快照 + 临时文件 + 安全余量（建议 20%）；
3. 取消防周回顾 Worker（见 §6）；
4. 关闭 AI 自动发送、撤回授权（fail-closed）；
5. 阻断 UI 写入与后台 AI（RestoreGate）；
6. 记录 journal + 设备/版本/备份摘要（不含敏感正文）。

## 4. Room 全量替换事务

### 4.1 为什么不是“直接替换 .db 文件”

- WAL/SHM 可能含最新数据；单拷 `.db` 会漏数据或拿到半写状态（现有导出注释已说明，`DataExportRepository.kt`）。
- Room 单例持有连接；替换文件需要关闭所有连接、处理 `-wal/-shm`、跨进程，失败面大。
- 直接替换会绕过 Room schema 校验/migration，版本不一致时更危险。

**推荐：在同一个 Room 事务里 DELETE + INSERT + 校验。** 对当前数据量（个人成长数据）足够；未来数据量增大再评估 staging DB。

### 4.2 事务顺序

```sql
-- 1) 删除（子先父后；自引用先清空 parent_id 或按层级）
DELETE FROM weekly_ai_reviews;
DELETE FROM weekly_reviews;
DELETE FROM insight_reports;
DELETE FROM growth_memories;
DELETE FROM growth_signals;
DELETE FROM goal_proposals;
DELETE FROM advisor_messages;
DELETE FROM goal_checkins;
DELETE FROM goals;
DELETE FROM journal_entries;

-- 2) 插入（父先子后）
INSERT journal_entries...
INSERT goals (LONG_TERM -> STAGE -> ACTION, id 保持)
INSERT goal_checkins...
INSERT advisor_messages...
INSERT goal_proposals...
INSERT growth_signals...
INSERT growth_memories...
INSERT insight_reports...
INSERT weekly_reviews...
-- weekly_ai_reviews 不插入（只归档）

-- 3) 写恢复标记（G4A-2 新增 app_meta 表，schema v12）
INSERT OR REPLACE INTO app_meta(key,value) VALUES
  ('restore_id', :backupId),
  ('restore_completed_at', :now),
  ('data_generation', :newGeneration);

-- 4) 事务内校验
-- counts、FK、悬挂引用、枚举、唯一键；任一失败 throw -> ROLLBACK
```

注意：

- `goals` 的自引用 FK：按 `level` 排序插入；同一层内按 `id`。若备份有环/父缺失，校验阶段拒绝，不依赖 SQLite 报错。
- 删除 `goals` 前先删 `goal_checkins`；`ON DELETE CASCADE` 只是兜底。
- `weekly_ai_reviews` 删除是**有意**的：旧 AI 结果绑定旧授权/预算，恢复后不得占用本周额度或伪装成新结果。
- `app_meta` 是 G4A-2 的 schema 变更（v12），用于跨存储恢复标记；备份格式不包含它，恢复时重建。

### 4.3 恢复后校验（事务内，失败回滚）

1. 每表行数 == 备份 counts；
2. 每表摘要/规范化哈希 == 备份 per-table hash；
3. 所有 FK 有效；
4. 逻辑引用有效：
   - `advisor_messages.context_goal_id` 为空或存在于 goals；
   - `goal_proposals.message_id` 存在；
   - `goal_proposals.applied_goal_ids_json` 中每个 id 存在；
   - `growth_signals.source_id` 存在（source_type='DIARY'）；
   - `growth_memories.signal_ids_json`/`source_diary_ids_json` 引用存在；
   - `weekly_reviews.period_key` 唯一；
5. 枚举/状态在允许集合；
6. 日期/数值有限；
7. `journal.analysis_status='ANALYZING'` 已按规则处理（不留在 ANALYZING）。

### 4.4 回滚

- DB 事务失败：SQLite `ROLLBACK`，旧数据保持；journal 记 `DB_RESTORE_FAILED`。
- 事务提交后设置失败：见 §7，启动期继续完成；不回滚已提交 DB（避免二次风险）。
- 预恢复快照用于**用户手动恢复**/灾难恢复，不自动覆盖新数据。

## 5. 引用关系校验（恢复前/事务内）

| 引用 | 规则 | 失败处理 |
|---|---|---|
| goals.parent_id | 父必须存在；LONG_TERM 无父；STAGE 父=LONG_TERM；ACTION 父=STAGE | 拒绝 |
| goal_checkins.goal_id | 必须存在 | 拒绝 |
| advisor_messages.context_goal_id | null 或存在 | 拒绝（或预览中明确“将清空悬挂引用”？不推荐静默） |
| goal_proposals.message_id | 必须存在 | 拒绝 |
| applied_goal_ids_json | 每个 id 存在且 level 合理 | 拒绝 |
| growth_signals.source_id | 对应 journal 存在 | 拒绝 |
| growth_signals.source_version | 等于 journal 当前 title+body 哈希；不等则标 STALE 并提示 | 允许但标记 |
| growth_memories.signal_ids_json | 引用存在；允许已删除/STALE？ | 拒绝未知 id；允许 DELETED 但保留 |
| weekly_reviews.period_key | 唯一 | 拒绝重复 |
| insight_reports.data_version | 不要求匹配当前；不匹配则不显示为当前 | 允许，标 stale |
| weekly_ai_reviews | 不恢复 | 忽略（预览说明） |

## 6. 并发与后台任务

### 6.1 恢复期间必须停的东西

| 来源 | 处理 |
|---|---|
| UI 写入（日记/目标/草案/设置） | 全屏维护遮罩 + 禁用导航；恢复期间 Activity 不接收业务操作 |
| `WeeklyReviewWorker` | `WorkManager.cancelUniqueWork("weekly_review_periodic")` + `cancelAllWorkByTag("weekly_review")`；Worker 启动时检查 RestoreGate，活动则直接 success 不写 |
| 一次性 QA Worker | 同一 tag 取消 |
| AI 顾问请求 | `RestoreGate` 检查；取消 ViewModel 协程/禁止发送；请求返回后校验 generation，过期丢弃 |
| 洞察/周 AI/日记提取 | 同上；服务入口检查 gate + generation |
| Room 写入 | 维护 gate + 事务串行；恢复事务本身阻塞其他写事务 |
| DataStore 写入 | 维护 gate；恢复器在 DB 提交后统一写 |

### 6.2 为什么不能只靠 Room 事务

Room 事务只保证 DB；DataStore 是 4 个独立文件，WorkManager 是另一个数据库。必须用：

- **维护 gate**（应用级状态，所有写入/网络入口检查）；
- **恢复 journal + app_meta**（跨存储收敛）；
- **generation/epoch**（旧请求结果丢弃）；
- **启动期 reconciliation**（见 §7）。

### 6.3 正在进行的 AI 请求

最危险的是：恢复前发出的请求在恢复后返回，把旧数据/旧授权结果写入新 DB。

处理：

1. 恢复前 `dataGeneration++`（内存 + app_meta）；
2. AI 服务在请求前捕获 generation/consent epoch；
3. 返回后比较 generation/epoch；不一致则丢弃，不写 assistant 消息/不写 AI 报告；
4. UI 在恢复期间不可发送；
5. 若进程在恢复中被杀，启动后 generation 来自 app_meta，旧协程已死，天然丢弃。

现有代码已有 consent epoch 检查（Advisor/Insight/Weekly AI），但**没有全局 dataGeneration**；G4A-2 增加。

## 7. 跨存储一致性与启动期 reconciliation

### 7.1 恢复 journal

位置：`noBackupFilesDir/restore/journal.json`（不参与系统备份）。内容：

```json
{
  "phase": "PREPARED|DB_COMMITTED|SETTINGS_APPLIED|COMPLETE|FAILED",
  "backupId": "uuid",
  "backupSha256": "hex",
  "startedAt": 0,
  "dbSchemaVersion": 11,
  "preSnapshotPath": "...",
  "settings": { "themeMode": "SYSTEM", "weeklyEnabled": true }
}
```

写入方式：临时文件 + fsync + 原子 rename（`File.renameTo` 同目录）；避免半写 journal。

### 7.2 启动期 reconciliation（必须）

`MengyaApplication.onCreate` 或 MainActivity 启动前（后台 IO）执行：

1. 读 journal；无 journal → 正常启动。
2. `PREPARED`：DB 事务未提交（SQLite 原子）；删除/忽略 journal，清理临时文件；旧数据可用。
3. `DB_COMMITTED`：读 `app_meta.restore_id`：
   - 与 journal.backupId 一致 → 提交成功；幂等应用设置；写 `SETTINGS_APPLIED`/`COMPLETE`；重建 WorkManager；清缓存。
   - 不一致/缺失 → 异常：保留 journal，提示“恢复未完成，请联系支持”，不自动写 DB。
4. `SETTINGS_APPLIED`：补写 `COMPLETE`，清理临时文件。
5. `COMPLETE`：正常启动；清理过期快照。
6. 任何 phase 发现 DB 打不开/校验失败：进入只读/维护模式，绝不自动 destructive reset。

恢复期间 UI 必须等待 reconciliation（启动遮罩），避免半个状态被操作。

### 7.3 设置应用顺序（幂等）

DB 提交后：

1. 撤权（若尚未）：`AiConsentRepository.withdraw()`；
2. 删除 API Key（按策略，见 §8）；
3. `weeklyAiEnabled=false`；
4. `notificationAsked=false`；
5. 主题（若备份包含）；
6. 周回顾 enabled（若备份包含；按此调度）；
7. AI endpoint/model（若采用 D1；不启用）；
8. `app_meta` generation 保持事务写入值；
9. 写 journal COMPLETE。

每一步幂等；重复执行不改变结果。

## 8. 恢复后的重置规则

| 项 | 恢复后 | 原因 |
|---|---|---|
| AI 授权 `granted` | **false** | 用户必须在当前设备重新同意；旧同意不能跨设备/跨数据版本 |
| 授权类别/接收方/epoch | 清空/新 epoch | 防止旧授权静默发送 |
| API Key | **删除（推荐）** | 备份不含 Key；避免旧 Key 与新数据/新 endpoint 混用；用户重输 |
| Keystore alias | 删除（推荐） | 与 Key 一致；旧密文不可用 |
| AI endpoint/model | 可选恢复（D1） | 仅展示；`configured=false` 直到重新输入 Key |
| AI 自动发送 | **false** | 防止恢复后后台自动发数据 |
| 周回顾 enabled | 可恢复 | 本地功能，不联网 |
| 通知已询问 | false | OS 权限不可恢复；按需重新请求 |
| WorkManager | 重建唯一周期任务 | 不复制内部 DB |
| `weekly_ai_reviews` | 删除/不恢复 | 旧预算/旧授权绑定；避免锁死本周 |
| `journal.analysis_status=ANALYZING` | 改为 FAILED/PENDING | 进程已死，不能假装分析中 |
| 洞察缓存 | 保留但按 dataVersion 失效 | 数据变化后不展示旧结论 |
| 周回顾 `notified_at` | 置 null | 恢复后允许重新通知；避免“已通知”假状态 |
| 内存缓存/ViewModel | 重建（清任务/重启 UI） | 避免旧快照 |

> API Key 删除 vs 保留是 A/B 决策：推荐删除（fail-closed）；保留可减少重输，但会让旧密钥在新数据上继续可用，隐私风险更高。

## 9. 失败场景矩阵

| 场景 | 发生点 | 处理 | 旧数据 |
|---|---|---|---|
| 文件损坏/截断 | 校验 | 拒绝，提示 | 不变 |
| 口令错误 | 解密 | 拒绝，不泄露“接近正确” | 不变 |
| 篡改 | AEAD/hash/counts | 拒绝 | 不变 |
| 超大/解压炸弹（无压缩） | stat/parse | 拒绝，不分配 | 不变 |
| 缺字段/未知枚举 | 结构校验 | 拒绝 | 不变 |
| 引用缺失 | 引用校验 | 拒绝 | 不变 |
| 版本过新 | 兼容检查 | 拒绝，提示升级 | 不变 |
| 版本过旧 | 兼容检查 | 字段映射/默认，预览警告 | 恢复后新数据 |
| 空间不足 | 预检 | 中止 | 不变 |
| 导出写入中断 | 导出 | 回读失败，提示；删除临时文件 | 原文件可能不完整，不覆盖旧 DB |
| 恢复进程被杀（事务中） | DB_RESTORE_STARTED | SQLite 回滚；启动 reconciliation 清理 | 不变 |
| 恢复进程被杀（提交后） | DB_COMMITTED | 启动补设置 | 新数据 |
| 设置写入失败 | SETTINGS | 启动重试；维护遮罩 | 新数据，设置可能未全应用 |
| 重复恢复同一备份 | 开始 | 检测 backupId/app_meta；提示“已恢复过”，要求显式再次确认 | 新数据（再次替换） |
| 恢复中 AI 返回 | 任意 | generation/epoch 不匹配，丢弃 | — |
| QA/正式互相导入 | 校验 | 拒绝 | 不变 |

## 10. 旧版本备份/数据库迁移边界

- v1（三表）**不恢复**；只识别提示。
- v2 + `dbSchemaVersion < 11`：读取器按当前字段映射，缺字段用**显式默认**（如 analysis_status=PENDING），预览列出“补默认值”；不允许静默。
- v2 + `dbSchemaVersion = 11`：直接恢复。
- v2 + `dbSchemaVersion > 11`：拒绝；提示升级 App。
- 未来 schema 12（含 app_meta）：v2 备份不含 app_meta；恢复时重建；reader 仍接受 11 的备份。
- 数据库迁移：恢复不运行旧 schema 的 Room migration；它是逻辑行导入。迁移测试仍由 G3F `verify_migration.py` 覆盖。
- 恢复后不做 destructive migration；缺表/列直接失败。

## 11. 恢复前快照与回滚

### 11.1 快照策略

- 位置：`noBackupFilesDir/restore/pre-restore-<timestamp>.mengyalife`（不进系统备份）。
- 内容：与 v2 相同逻辑载荷，但**设备本地加密**（Keystore 别名 `restore_snapshot_key`，AES-GCM，不出设备）；或首版使用 app 私有明文 + 文件权限 0600。
- 保留：最近 1 份 + 7 天；成功后不立即删除，给用户手动回滚窗口；超期清理。
- 失败回滚：DB 事务失败不需要快照；提交后设置失败不需要回滚 DB；快照用于“用户后悔/灾难恢复”。

### 11.2 自动回滚的边界

- **不自动**用快照覆盖当前数据；避免二次破坏。
- 只有用户明确选择“回滚到恢复前”才执行，并再次走完整预览/确认。

## 12. 恢复后应用状态重建

1. **进程/任务**：完成页 → `finishAffinity()` + 以 `CLEAR_TASK` 启动 MainActivity（或 equivalent），确保 ViewModel/内存缓存不残留。
2. **Room**：Flow 自然重新发射；`MengyaDatabase` 单例保持，但数据已替换。
3. **DataStore**：按 §7.3 应用；AI 状态查询返回未配置/未授权。
4. **WorkManager**：`WeeklyReviewScheduler.sync(prefs)`；enabled 则调度，AI 自动关。
5. **ANALYZING 修复**：启动/恢复后调用 `JournalRepository.resetInterruptedAnalyses()`（现有方法，`JournalRepository.kt:103`；目前只在 DiaryViewModel 调用）。
6. **缓存失效**：Insight 按 dataVersion 查询；weekly review 按 dataVersion/consent；weekly AI 已删；不手工清空用户数据。
7. **通知**：不恢复 OS 权限；`notified_at=null` 允许下一次通知；周回顾开关若恢复则调度。
8. **日志/证据**：记录 backupId/phase/耗时/错误码，不记录正文/Key/口令。

## 13. 验收标准（G4A-2 退出条件）

- 完整 v2 备份 → 恢复 → 所有核心表逐行/逐字段一致；派生表按规则失效。
- 恢复失败（注入异常/断电模拟）→ 旧 DB 逐行不变；journal 可收敛。
- 恢复后 API Key 缺失、consent 未授权、AI 自动关；重新输入 Key + 重新同意后 AI 才可用。
- 恢复期间 Worker/AI 不写 DB；旧请求结果被丢弃。
- 重复导入、超大、损坏、截断、篡改、旧版本、跨包全部拒绝且旧数据不变。
- 真机 E2E 覆盖 SAF、进程重启、空间不足、QA/正式隔离；见 `G4A0-TEST-MATRIX.md`。
