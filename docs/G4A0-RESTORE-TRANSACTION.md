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

## 2. 恢复状态机（G4A-0.1 修订）

### 2.1 三个独立事实源

| 事实源 | 位置 | 作用 | 是否可单独决定“DB 已提交” |
|---|---|---|---|
| **维护门禁（Gate）** | `noBackupFilesDir/restore/gate.json` | 进程重启后仍然阻断 UI/Worker/AI | 否 |
| **DB 恢复标记（app_meta）** | Room `app_meta` 表（G4A-2 v12） | 与数据同一事务写入；`restore_attempt_id`/`restore_generation`/设置快照 | **是（唯一 truth）** |
| **恢复 journal** | `noBackupFilesDir/restore/journal.json` | 记录 attempt、备份摘要、阶段、设置快照 | **否（仅辅助/排错）** |

**核心规则：禁止仅根据 `journal.phase` 判断 DB 是否提交。**

启动时先读 Gate → 再读 `app_meta` → 最后参考 journal；DB 提交与否只看：

```text
app_meta.restore_attempt_id == journal.attemptId
```

若 `journal` 缺失/损坏，则以 `app_meta.restore_attempt_id` 与 Gate 中保存的 attemptId 判断；两者都没有时才视为“未提交”。

### 2.2 关键标识

- `attemptId`：每次用户确认恢复生成的新 UUID；不是 backupId，避免旧 journal 与旧恢复误匹配。
- `backupId`：备份文件自身的 UUID（来自备份）。
- `generation`：单调递增整数；恢复事务中 `generation = previous_generation + 1`；永不复用/回退。
- `restore_settings_json`：DB 事务内写入的非秘密设置快照（theme/weekly/AI endpoint/model），保证 journal 丢失时仍能收敛。
- Gate 状态：`ARMED` / `RESET_PENDING` / `SETTINGS_PENDING` / `READY_TO_RELEASE`；Gate 文件存在即禁止 UI/Worker/AI。

### 2.3 阶段序列

```text
IDLE
  → FILE_SELECTED → VALIDATED_HEADER → DECRYPTED
  → STRUCTURE_VALIDATED → PREVIEW_READY → CONFIRMED
  → PREFLIGHT_OK
  → SNAPSHOT_TAKEN
  → GATE_ARMED                 (持久化门禁；此前不碰 DB/DataStore/Key)
  → DB_TXN_START               (DELETE/INSERT + app_meta 同一事务)
  → DB_COMMITTED               (app_meta.attemptId=attemptId, generation++)
  → RESET_PENDING              (撤权/删除 Key/关闭 AI 自动；可失败重试)
  → SETTINGS_PENDING           (主题/周开关/可选 AI endpoint)
  → SETTINGS_APPLIED
  → READY_TO_RELEASE
  → GATE_RELEASED              (删除 gate 文件)
  → RELAUNCH
```

失败分支：

- `IDLE`..`PREFLIGHT_OK`：ABORTED，DB/Key/consent 均未动。
- `SNAPSHOT_TAKEN` / `GATE_ARMED`：只创建快照/门禁文件，用户数据/Key 未动；Gate 必须清理或继续收敛。
- `DB_TXN_START`：SQLite 事务回滚，旧数据/Key 保持；Gate 进入失败收敛。
- `DB_COMMITTED` 之后：DB 新数据；**Key/consent 尚未动**（符合“提交前不破坏原数据及 API Key”）；必须完成 reset/settings 才释放 Gate。

### 2.4 可执行状态转移表

列定义：

- `J`：journal.phase（`NONE`/`CORRUPT`/`PREPARED`/`DB_COMMITTED`/`SETTINGS_APPLIED`/`COMPLETE`）。
- `M`：`app_meta.restore_attempt_id`（`A` = 当前 journal/gate 的 attempt；`O` = 其他/空）。
- `DB`：实际数据状态（`OLD` = 本次 attempt 未提交；`NEW` = 本次 attempt 已提交）。
- `G`：Gate 是否存在且 `ARMED`。

| # | G | J | M | 实际 DB | 启动处理动作 | UI | Worker | AI |
|---|---|---|---|---|---|---|---|---|
| 1 | 无 | NONE/COMPLETE | 任意历史 | OLD 或历史 NEW | 正常启动；清理过期 journal/快照 | 允许 | 允许 | 允许（按 consent） |
| 2 | ARMED | PREPARED | O/空 | **OLD**（事务未提交） | 不应用设置；删除 journal/tmp；删除 Gate；旧数据/Key 保持 | 释放后允许 | 释放后允许 | 释放后允许 |
| 3 | ARMED | PREPARED | **A** | **NEW**（提交后 journal 未更新） | 从 `app_meta.restore_settings_json` 执行 reset+settings；完成并释放 Gate | 禁止 | 禁止 | 禁止 |
| 4 | ARMED | DB_COMMITTED | **A** | NEW | 执行 reset+settings（幂等）；完成并释放 | 禁止 | 禁止 | 禁止 |
| 5 | ARMED | SETTINGS_APPLIED / 部分完成 | **A** | NEW | 重跑幂等 reset+settings；校验后释放 | 禁止 | 禁止 | 禁止 |
| 6 | ARMED | **CORRUPT/NONE** | **A** | NEW | 用 `app_meta` 的设置快照完成收敛；重建 journal；释放 | 禁止 | 禁止 | 禁止 |
| 7 | ARMED | CORRUPT/NONE | O/空 | **OLD** | 无提交证据；删除 tmp/损坏 journal；删除 Gate；旧数据保持 | 释放后允许 | 释放后允许 | 释放后允许 |
| 8 | ARMED | PREPARED/DB_COMMITTED | **O**（旧 attempt） | OLD/历史 NEW | 当前 attempt 未提交；忽略旧 journal；清理并释放；不覆盖历史 NEW | 释放后允许 | 释放后允许 | 释放后允许 |
| 9 | **无** | PREPARED/DB_COMMITTED（active） | A/O | 不确定/NEW | **fail-closed**：重建 Gate，先按 `app_meta` 收敛；无法确认则维护模式 | 禁止 | 禁止 | 禁止 |
| 10 | ARMED | 任意 | A 但 generation 非递增/损坏 | 不可信 | **fail-closed 维护模式**；不自动覆盖；提示导出/支持 | 禁止 | 禁止 | 禁止 |
| 11 | ARMED | COMPLETE | A | NEW | 已收敛但 Gate 未删：校验后删除 Gate/清理 | 禁止至释放 | 禁止至释放 | 禁止至释放 |
| 12 | 任意 | 任意 | DB 无法打开 | 不可信 | 只读/维护；绝不 destructive reset | 禁止 | 禁止 | 禁止 |

补充：

- **场景 1（P0 要求）**：journal=PREPARED、DB 未提交 → 行 2，旧数据/Key 原样。
- **场景 2**：journal=PREPARED、DB 已提交 → 行 3，以 `app_meta` 为准，不因 journal 写“PREPARED”就回滚/放弃。
- **场景 3**：journal=DB_COMMITTED、设置未应用 → 行 4。
- **场景 4**：设置应用一半后死亡 → 行 5，重跑幂等 reset/settings。
- **场景 5**：journal 损坏/缺失但 DB 有 marker → 行 6；若 Gate 也缺失 → 行 9 fail-closed。
- **场景 6**：Worker/AI 早于 reconciliation → Gate 存在时 Worker 直接 `Result.retry()`/success 不写，AI 返回维护错误；UI 遮罩；见 §6.4。

### 2.5 app_meta 事务内容

```sql
UPDATE app_meta SET
  restore_attempt_id = :attemptId,
  restore_backup_id = :backupId,
  restore_generation = :newGeneration,
  restore_settings_json = :settingsJson,  -- 非秘密：theme/weekly/ai endpoint/model
  restore_completed_at = :now
WHERE id = 1;
```

- 与数据 DELETE/INSERT **同一事务**；任一步失败整体回滚，`app_meta` 仍指向旧 generation。
- `restore_settings_json` 不含 Key/consent/fake flag；只用于 journal 丢失时收敛。
- `app_meta` 不进入 v2 备份；恢复时重建/更新。

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

### 3.5 恢复前安全保护（顺序不可交换）

1. 自动生成本机**恢复前快照**（pre-restore snapshot）；
2. 检查剩余空间 ≥ 快照 + 临时文件 + 安全余量（建议 20%）；
3. **先持久化维护门禁**（`gate.json` = `ARMED`，fsync+rename），再取消防周 Worker；
4. 阻断 UI 写入与后台 AI（Gate 先于任何 DB/DataStore 修改）；
5. 记录 journal + 设备/版本/备份摘要（不含敏感正文）。

**此阶段不做**：不撤回授权、不删除 API Key、不清 DataStore、不修改用户数据。

原因：若 DB 事务失败/进程死亡，旧数据与 Key 必须原样可用；安全重置只在 DB 提交后执行（§8）。

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

-- 3) 同一个事务写恢复标记（G4A-2 新增 app_meta 表，schema v12）
--    只有这个标记能证明“DB 已提交”；journal 不作为证据。
UPDATE app_meta SET
  restore_attempt_id = :attemptId,
  restore_backup_id = :backupId,
  restore_generation = :newGeneration,      -- previous + 1，单调递增
  restore_settings_json = :settingsJson,    -- 非秘密：theme/weekly/ai endpoint/model
  restore_completed_at = :now
WHERE id = 1;

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
3. 所有 FK 有效（强约束）；
4. 逻辑引用按 §5 三层处理：
   - 强约束（goals 父子、checkins→goals）必须有效；
   - 历史悬挂（message context/proposal message/applied ids/signal/memory）允许，按 §5C 置空/STALE 并报告；
   - `weekly_reviews.period_key` 唯一；
5. 枚举/状态在允许集合；
6. 日期/数值有限；
7. `journal.analysis_status='ANALYZING'` 已按规则处理（不留在 ANALYZING）。

### 4.4 回滚

- DB 事务失败：SQLite `ROLLBACK`，旧数据/Key/consent 保持；journal 记 `DB_RESTORE_FAILED`；Gate 保留到确认旧数据可用后释放。
- 不要因为 journal 写 `DB_COMMITTED` 就假定提交；只认 `app_meta.restore_attempt_id`。
- 事务提交后设置失败：见 §7，启动期继续完成；不回滚已提交 DB（避免二次风险）。
- 预恢复快照用于**用户手动恢复**/灾难恢复，不自动覆盖新数据。

## 5. 引用关系校验（G4A-0.1 修订）

**不能假设所有历史逻辑引用都必须存在。** 分为三类：

### A. 强约束（必须拒绝）

| 引用 | 规则 | 理由 |
|---|---|---|
| `goals.parent_id` | 父必须存在；LONG_TERM 无父；STAGE 父=LONG_TERM；ACTION 父=STAGE；无环 | 数据库自引用 FK + 业务层级，破坏即不可用 |
| `goal_checkins.goal_id` | 必须存在 | 数据库 FK；否则孤儿进展 |
| 主键/唯一键 | 无重复；period_key 唯一 | 数据完整性 |
| 枚举/状态/时间/数值 | 合法值、有限数、必填 | 防止静默默认值 |

### B. 历史合法状态（允许，标记 warning/stale）

| 引用 | 合法场景 | 处理 |
|---|---|---|
| `advisor_messages.context_goal_id` | 目标后来被用户删除；旧消息保留 | 允许悬挂；字段可保留（UI 不解析即忽略）或按 §C 置 null；报告中列出 |
| `goal_proposals.message_id` | 对话消息后来被删除；草案保留 | 允许悬挂；不删草案；报告中列出 |
| `goal_proposals.applied_goal_ids_json` | 应用后用户删除了目标（APPLIED/UNDONE 历史） | 允许缺失；**不重新创建目标**；报告 warning |
| `growth_signals.source_id` | 旧版本遗留/日记已删除 | 允许；按 §C 标 `STALE`；不参与 AI 上下文 |
| `growth_memories.signal_ids_json` | 线索已软删除/旧版本 | 允许；ACTIVE/CORRECTED 若引用缺失则按 §C 转 `STALE`；DELETED 保持 |
| `growth_memories.source_diary_ids_json` | 日记已删除但记忆保留 | 允许；报告中列出；不据此拒绝 |
| `insight_reports.data_version` | 数据已变化 | 允许；不匹配则不显示为当前 |
| `weekly_reviews` 无外键 | — | 只校验 period_key 唯一 |

### C. 可确定修复（显式规则，恢复事务内执行）

| 场景 | 转换 | 是否影响用户内容 |
|---|---|---|
| `advisor_messages.context_goal_id` 悬挂 | 置 `NULL`（保留消息与来源 JSON） | 不改正文 |
| `growth_signals.source_id` 悬挂 | `status='STALE'`；保留内容/来源标题 | 不改内容，只停止使用 |
| ACTIVE/CORRECTED `growth_memory` 引用缺失 | `status='STALE'`；保留内容 | 不改内容 |
| `goal_proposals` 的 applied ids 缺失 | 不修改；仅 warning（审计记录不能伪造） | 不改草案 |
| 旧 schema 缺字段 | 显式默认（如 analysis_status=PENDING） | 预览列出 |
| `journal.analysis_status=ANALYZING` | `FAILED`/`PENDING`（见 §8） | 不改正文 |

修复必须：

1. 在 DB 事务内完成；
2. 只做上表列出的确定性转换；
3. 预览/报告中列出每类数量和 id（不含正文）；
4. 不删除历史行、不重建目标、不把缺失引用伪装成有效。

### D. “应用自身合法生成的备份必须能通过”

- 当前 App 正常删除日记时会删除其 signals（`JournalRepository.deleteEntry`），正常删除目标会级联 checkins；因此正常备份不应有 FK 悬挂。
- 消息/草案/上下文/记忆的悬挂是业务允许的历史，不得拒绝。
- 如果备份中的 `goals.parent_id`/`goal_checkins.goal_id` 悬挂，那一定不是当前 App 合法生成的完整备份，应拒绝。
- 校验器必须用“强约束拒绝 + 历史允许 + 显式修复”三层，而不是一律拒绝。

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

1. DB 事务提交时 `generation++`（app_meta 内）；内存中的 `RestoreGate` 同步切到维护态；
2. AI 服务在请求前捕获 `generation + consent epoch`；
3. 返回后比较 generation/epoch；不一致则丢弃，不写 assistant 消息/不写 AI 报告；
4. UI 在 Gate 存在期间不可发送；
5. 若进程在恢复中被杀，启动后 generation 来自 app_meta，旧协程已死，天然丢弃。

现有代码已有 consent epoch 检查（Advisor/Insight/Weekly AI），但**没有全局 generation 门禁**；G4A-2 增加。

### 6.4 Worker/AI 早于 reconciliation

- `MengyaApplication.onCreate` 必须先读 Gate 文件（同步、小文件），再决定是否允许容器/调度初始化；Gate 存在时不调用 `weeklyReviewScheduler.sync()`。
- 已存在的 WorkManager 任务可能被系统拉起：`WeeklyReviewWorker.doWork()` 第一行检查 Gate 文件；存在则 `Result.retry()`（或 success 但不写任何表），不得生成周回顾/AI。
- AI 服务（AdvisorViewModel、InsightService、WeeklyAiService、DiaryExtractionService）入口检查 Gate；存在则返回“维护中”，不发起网络。
- UI：MainActivity 显示阻断式维护页；不渲染业务页面。
- Gate 文件损坏/不可读：按“存在”处理（fail-closed），不得放行。

## 7. 跨存储一致性与启动期 reconciliation

### 7.1 Gate 文件（必须先于 DB）

位置：`noBackupFilesDir/restore/gate.json`（不参与系统备份）。

```json
{
  "state": "ARMED|RESET_PENDING|SETTINGS_PENDING|READY_TO_RELEASE",
  "attemptId": "uuid",
  "backupId": "uuid",
  "targetGeneration": 3,
  "createdAt": 1793827200000,
  "preSnapshot": "pre-restore-....mengyalife",
  "lastError": null
}
```

- 写入：同目录临时文件 → flush/fsync → atomic rename。
- **Gate 写失败 → 中止恢复**，不得进入 DB 替换。
- Gate 只有全部收敛后才删除；删除前 UI/Worker/AI 一直禁止。
- Gate 损坏/不可读 → 视为 `ARMED`，进入维护模式。

### 7.2 Journal（辅助，不是 truth）

位置：`noBackupFilesDir/restore/journal.json`。

```json
{
  "attemptId": "uuid",
  "backupId": "uuid",
  "phase": "PREPARED|DB_COMMITTED|SETTINGS_APPLIED|COMPLETE|FAILED",
  "targetGeneration": 3,
  "backupSha256": "hex",
  "startedAt": 0,
  "settings": { "themeMode": "SYSTEM", "weeklyEnabled": true }
}
```

- `phase` 仅用于排错/日志；**启动判断不依赖它**。
- Journal 缺失/损坏时，以 `app_meta.restore_settings_json` 继续收敛。
- Journal 不包含 Key/consent。

### 7.3 启动期 reconciliation（必须）

启动顺序（任何一步失败 → 保持 Gate/维护态，不释放）：

1. **同步读 Gate**（在 Application/首个 Activity 前）。无 Gate 且无 active journal → 正常启动。
2. Gate 存在 → 阻断 UI/Worker/AI，打开 DB 读取 `app_meta`。
3. 判定提交（唯一依据）：

   ```text
   committed = (app_meta.restore_attempt_id == gate.attemptId)
   ```

   - journal.attemptId 只用于辅助核对；若与 gate 不一致，以 gate/app_meta 为准并记录异常。
4. 若 committed：
   - 用 `app_meta.restore_settings_json`（journal 缺失也可）执行 §8 的 reset + settings；
   - 校验 DB 行数/哈希/引用（§4.3/§5）；
   - 写 journal `SETTINGS_APPLIED` → Gate `READY_TO_RELEASE`；
   - 删除 Gate；重建 WorkManager；清内存缓存；重启 UI。
5. 若未 committed：
   - 说明本次 DB 事务未提交（SQLite 原子）；旧用户数据/Key/consent 保持；
   - 清理临时文件/损坏 journal；删除 Gate；
   - 正常启动（Worker/AI 按旧 consent 状态）。
6. Journal 缺失/损坏但 committed → 走第 4 步（settings 来自 app_meta）。
7. Gate 缺失但 journal 是 active（PREPARED/DB_COMMITTED）→ **fail-closed**：重建 Gate，按 app_meta 收敛；无法确认则维护模式。
8. 任何 DB 无法打开/校验失败 → 维护模式；绝不 destructive reset。

### 7.4 设置应用顺序（幂等，失败保持 Gate）

DB 提交后（只在 committed 后）：

1. `AiConsentRepository.resetAfterRestore(generation)`（§8.1）；
2. `AdvisorConfigRepository.deleteKeyConfirmed()`（§8.2）；失败 → Gate `RESET_PENDING`，停止；
3. `weeklyAiEnabled=false`；
4. `notificationAsked=false`；
5. 主题（若设置快照包含）；
6. 周回顾 enabled（若包含；据此调度）；
7. AI endpoint/model（D1；不启用）；
8. 回读校验：consent 未授权、Key 不存在、AI 自动关；
9. Journal `SETTINGS_APPLIED` → Gate `READY_TO_RELEASE` → 删除 Gate → 重启。

每一步可重入；重复执行结果相同。任何一步失败保留 Gate，下次启动重试，不释放 UI/AI。

## 8. 恢复后的重置规则（G4A-0.1 修订）

### 8.1 `resetAfterRestore(generation)` 语义

现有 `AiConsentRepository.withdraw()` 的真实行为（`AiConsentRepository.kt:96-106`）：

- 每次调用 `epoch++`（即使已未授权）；
- **保留 recipient**（用于展示“上次授权给谁”）；
- 清 granted/textVersion/categoriesVersion/grantedAt/categories。

它**不能**直接用于恢复：旧接收方会残留，重复调用会让 epoch 无意义增长。新增独立方法：

```kotlin
suspend fun resetAfterRestore(generation: Long): ResetResult
```

语义：

1. **清空**：`granted=0`、`textVersion=0`、`categoriesVersion=0`、`grantedAt=0`、`recipient=""`、`categories=""`。
2. **失效旧请求**：`epoch = max(currentEpoch + 1, generation)`；`generation` 来自 app_meta，单调递增，永不回退/复用。
3. **幂等**：若当前已满足“未授权 + recipient/categories 空 + epoch >= generation”，返回 `ALREADY_RESET`，不再改 epoch。
4. **不重复授权**：该方法只撤销，不 grant；恢复后 UI 必须重新走首次授权面板。
5. **原子性**：单个 `DataStore.edit` 完成所有字段；崩溃不会留下半清空。
6. **并发前置**：只在 Gate/维护态调用；此时 UI 不可用，不会有人同时重新授权。
7. **重复执行**：同一 generation 的重复调用返回 `ALREADY_RESET`；不同 generation 的新恢复会再次撤销并把 epoch 抬高。

> 不新增 DataStore key；用现有 `epoch` 与清空状态判定幂等，避免改变 Data Inventory 的键数量。

### 8.2 API Key / Keystore 删除的“确认”语义

现状：`SecretCipher.deleteKey()` 是 `Unit`，内部 `runCatching` 吞掉所有异常（`SecretCipher.kt:79-84`）。调用返回不代表 alias 已删除。

新增**仅恢复使用**的确认 API（G4A-2）：

```kotlin
sealed interface KeyDeleteResult {
  data object DELETED : KeyDeleteResult
  data object ALREADY_ABSENT : KeyDeleteResult
  data class FAILED(val reason: String) : KeyDeleteResult
}
fun deleteKeyConfirmed(): KeyDeleteResult
```

实现要求：

1. 加载 `AndroidKeyStore`；若 alias 不存在 → `ALREADY_ABSENT`；
2. `deleteEntry(alias)`；
3. 重新加载 KeyStore 并确认 `containsAlias(alias) == false`；只有确认不存在才返回 `DELETED`；
4. 任何异常/仍存在 → `FAILED`，**不得**返回成功；
5. `AdvisorConfigRepository.deleteKeyConfirmed()`：
   - 先删 Keystore 并确认；
   - 成功/已不存在后，清 DataStore `encrypted_api_key`；
   - 回读确认 DataStore 无密文；
   - 任一失败 → Gate `RESET_PENDING`，不释放；
6. 恢复器只有在 `KeyDeleteResult.DELETED/ALREADY_ABSENT` 且 DataStore 回读无 `encrypted_api_key` 时，才记录 `keyDeleteConfirmed=true`；
7. 旧 `deleteKey()` 可保留给 UI，但**不得**用于恢复成功判定。

### 8.3 重置/应用表

| 项 | 恢复后 | 失败处理 |
|---|---|---|
| AI 授权 | **false**；recipient/categories 清空；epoch≥generation | Gate 保持，重试 |
| API Key | Keystore 确认删除 + DataStore 密文清空 | Gate `RESET_PENDING` |
| AI 自动发送 | false | Gate 保持 |
| AI endpoint/model | 可选恢复（D1）；不启用 | 可重试；不阻塞安全 |
| 主题 | 若备份包含；否则保持 | 可重试 |
| 周回顾 enabled | 若包含；据此调度 | 可重试 |
| 通知已询问 | false | 可重试 |
| WorkManager | 不复制；重建唯一任务 | Gate 释放后 |
| `weekly_ai_reviews` | 不恢复/删除 | DB 事务内 |
| `journal.analysis_status=ANALYZING` | FAILED/PENDING | DB 事务内 |
| 洞察缓存 | 保留，按 dataVersion 失效 | DB 事务内 |
| 周回顾 `notified_at` | null | DB 事务内 |
| 内存/ViewModel | 释放 Gate 后重启 UI | — |

> API Key 删除是 fail-closed 推荐；若产品选择保留本机 Key，则必须仍保持未授权/自动关，且不能从备份恢复 Key。该决策不改变“Key 永不进入备份”。

## 9. 失败场景矩阵

| 场景 | 发生点 | 处理 | 旧用户数据/Key |
|---|---|---|---|
| 文件损坏/截断 | 校验 | 拒绝，提示 | 不变 |
| 口令错误 | 解密 | 拒绝，不泄露“接近正确” | 不变 |
| 篡改 | AEAD/hash/counts | 拒绝 | 不变 |
| 超大/压缩炸弹（无压缩） | stat/parse | 拒绝，不分配 | 不变 |
| 缺字段/未知枚举 | 结构校验 | 拒绝 | 不变 |
| **强引用缺失**（goals 父子/checkins→goals） | 引用校验 | 拒绝 | 不变 |
| **历史悬挂**（message context/proposal message/applied ids/signal/memory） | 引用校验 | 允许 + warning/stale（§5） | 不变 |
| 历史可确定修复 | 事务内 | 置 null/STALE，预览列出（§5C） | 恢复后新数据（按规则） |
| 版本过新 | 兼容检查 | 拒绝，提示升级 | 不变 |
| 版本过旧 | 兼容检查 | 字段映射/默认，预览警告 | 恢复后新数据 |
| 空间不足 | 预检 | 中止 | 不变 |
| 导出写入中断 | 导出 | 回读失败；临时文件清理；不报成功 | 目标文件可能不完整 |
| **Gate 写入失败** | 预检 | 中止；不进入 DB | 不变 |
| 恢复进程被杀（DB 事务中） | DB_TXN_START | SQLite 回滚；Gate 仍在；启动按 M 判断未提交→清理释放 | 不变 |
| 恢复进程被杀（提交后、journal 未更新） | DB_COMMITTED | Gate+app_meta 判定已提交；补 reset/settings | Key/consent 尚未动（新数据） |
| 恢复进程被杀（设置半应用） | RESET/SETTINGS | Gate 保持；启动重跑幂等 reset/settings | 新数据；设置按规则补齐 |
| **journal 损坏/缺失，app_meta 有 marker** | 启动 | 用 app_meta.settings 收敛；不依赖 journal | 新数据 |
| **journal 损坏/缺失，app_meta 无 marker** | 启动 | 清理/释放；旧数据可用 | 不变 |
| **Gate 缺失但 journal active** | 启动 | fail-closed 重建 Gate；无法确认则维护 | 不变/新数据待确认 |
| Gate 损坏/不可读 | 启动 | 按 ARMED 处理；维护 | 不变 |
| 撤权失败 | RESET_PENDING | Gate 保持；重试；AI/UI 禁止 | 新数据；旧 consent 可能仍在但被 Gate 阻断 |
| Key 删除失败/未确认 | RESET_PENDING | 不改成功状态；重试；AI/UI 禁止 | 新数据；Key 可能残留但被 Gate 阻断 |
| 设置写入失败 | SETTINGS_PENDING | Gate 保持；重试 | 新数据；设置可能未全应用 |
| 重复恢复同一备份 | 开始 | 检测 app_meta.attempt/backupId；提示“已恢复过”，显式确认后才再次替换 | 新数据 |
| 恢复中 AI 返回 | 任意 | generation/epoch 不匹配，丢弃 | — |
| Worker/AI 早于 reconciliation | 启动 | Gate 存在 → 不写/不联网；Worker retry | 不变 |
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
3. **DataStore**：按 §7.4 应用；AI 状态查询返回未配置/未授权。
4. **WorkManager**：`WeeklyReviewScheduler.sync(prefs)`；enabled 则调度，AI 自动关。
5. **ANALYZING 修复**：启动/恢复后调用 `JournalRepository.resetInterruptedAnalyses()`（现有方法，`JournalRepository.kt:103`；目前只在 DiaryViewModel 调用）。
6. **缓存失效**：Insight 按 dataVersion 查询；weekly review 按 dataVersion/consent；weekly AI 已删；不手工清空用户数据。
7. **通知**：不恢复 OS 权限；`notified_at=null` 允许下一次通知；周回顾开关若恢复则调度。
8. **日志/证据**：记录 backupId/phase/耗时/错误码，不记录正文/Key/口令。

## 13. 验收标准（G4A-2 退出条件）

- 完整 v2 备份 → 恢复 → 所有核心表逐行/逐字段一致；派生表按规则失效。
- **状态机六窗口**：PREPARED+未提交、PREPARED+已提交、DB_COMMITTED、设置半应用、journal 损坏但 marker 存在、Worker/AI 早于 reconciliation；全部按 §2.4 收敛。
- 恢复失败（注入异常/断电模拟）→ 旧 DB/Key **逐行/逐字节不变**；Gate 可收敛。
- 恢复后 API Key 缺失、consent 未授权（recipient/categories 清空）、AI 自动关；重新输入 Key + 重新同意后 AI 才可用。
- `resetAfterRestore` 重复执行幂等；epoch 不回退；不产生重复授权。
- `deleteKeyConfirmed` 只在 alias 确实不存在时返回成功；失败保持维护。
- 恢复期间 Worker/AI 不写 DB；旧请求结果被丢弃。
- 重复导入、超大、损坏、截断、篡改、旧版本、跨包全部拒绝且旧数据不变。
- **合法历史悬挂引用**（删目标/删日记/软删/纠正）不被拒绝；强引用缺失被拒绝。
- 恢复后 `restoredSha256` == 由备份按规则计算的 `expectedRestoredSha256`，且不等于 `payloadSha256`（若发生转换）。
- 真机 E2E 覆盖 SAF、进程重启、空间不足、QA/正式隔离；见 `G4A0-TEST-MATRIX.md`。
