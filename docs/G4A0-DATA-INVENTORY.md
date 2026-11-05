# G4A-0 全量数据盘点（DATA INVENTORY）

> 审计基线：`master` / `f2ba1b2`（Gate 3F 已提交，工作区干净）。
> 本轮只读审计；没有修改业务代码、数据库、迁移或恢复逻辑。
> 数据来源：仓库源码、Room schema `app/schemas/.../11.json`、导出实现、Manifest、G3F 验收报告。
> **未连接设备**：本轮设备已断开，因此没有读取真实行数/字节数；表结构与字段来自 schema v11，不猜测用户数据量。

## 1. 结论先行

1. 当前“导出我的数据”（v1）只覆盖 **10 张 Room 表中的 3 张**、**120 个列中的 23 个（19.2%）**，且**没有任何导入/恢复实现**。
2. v1 的定位是“日记/目标/进展三表导出”，不是完整备份；把它称作完整备份会误导用户。
3. 非 Room 状态（主题、AI 配置、AI 同意、周回顾开关）全部未覆盖；API Key/Keystore 必须永久排除。
4. 当前 Room 有 10 张表、11 个 schema 版本、2 个真实外键；其余引用（proposal→message/goal、signal→journal、memory→signal）是**逻辑引用，没有数据库外键**，恢复时必须由应用层校验。
5. 系统级备份配置为 `allowBackup="false"`，但**没有 `dataExtractionRules`，也没有 `fullBackupContent="false"`**；在部分 Android 12+ 设备上 D2D 迁移可能仍复制应用数据，存在隐私与“半恢复”风险（P0，见 §7）。

## 2. 持久化存储总览

| 存储 | 位置/名称 | 内容 | 是否用户数据 | 当前 v1 | v2 计划 |
|---|---|---|---|---|---|
| Room | `mengya-life.db` / QA `mengya-life-qa.db` | 10 张表 | 是 | 3 表 | 核心 + 派生缓存 |
| DataStore | `files/datastore/mengya_settings.preferences_pb` | `theme_mode` | 是（偏好） | 否 | 是（可选） |
| DataStore | `files/datastore/mengya_ai.preferences_pb` | endpoint/model/encrypted key/fake flag | 部分是 | 否 | 仅 endpoint/model（可选）；Key 永不 |
| DataStore | `files/datastore/mengya_consent.preferences_pb` | granted/textVersion/categoriesVersion/grantedAt/recipient/categories/epoch | 是（敏感） | 否 | **永不恢复**，强制重新同意 |
| DataStore | `files/datastore/mengya_settings_weekly.preferences_pb` | weekly enabled/notificationAsked/aiEnabled | 是（偏好） | 否 | enabled；AI 强制关；asked 重置 |
| Android Keystore | alias `mengya_ai_key` / QA `mengya_ai_key_qa` | API Key 加密密钥（不可导出） | 是（秘密） | 否 | **永久排除** |
| SAF/Downloads | 用户选择的 `mengya-life-export-*.json` | 用户导出的文件 | 是（文件） | 只写 | 用户文件，不是应用状态 |
| WorkManager | `androidx.work.workdb` | 周期任务/一次性任务 | 否（可重建） | 否 | 不备份；恢复后重建 |
| 系统通知 | NotificationChannel / POST_NOTIFICATIONS | 通知权限/渠道 | 否（设备状态） | 否 | 不备份；恢复后按需请求 |

依据：`app/build.gradle:22-27,61-65`（物理隔离）；`AndroidManifest.xml:15`；四个 DataStore 定义分别在 `ThemePreferences.kt:23`、`AiConfigStorage.kt:32`、`AiConsentRepository.kt:30`、`WeeklyReviewPreferences.kt:17`。

## 3. Room 表级矩阵

| # | 表 | 列数 | 主键 | 外键/逻辑引用 | 敏感 | 可重建 | 应纳入完整备份 | 恢复顺序 | v1 支持 | 迁移风险 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `journal_entries` | 11 | `id` 自增 | 无 | **高**（正文） | 否 | **是（核心）** | 1 | 部分（5/11 列） | v6 增加 analysis*；v1 文件无这些列 |
| 2 | `goals` | 11 | `id` 自增 | 自引用 `parent_id`，ON DELETE CASCADE | **高** | 否 | **是（核心）** | 2（先父后子） | 是（11/11） | v2 起稳定；枚举名存 TEXT |
| 3 | `goal_checkins` | 7 | `id` 自增 | `goal_id`→`goals.id`，CASCADE | **高**（健康/数值） | 否 | **是（核心）** | 3 | 是（7/7） | v2 起稳定 |
| 4 | `advisor_messages` | 12 | `id` UUID | 逻辑 `context_goal_id`→goals | **高**（对话） | 否 | **是（核心）** | 4 | 否 | v3/v4/v5 加列；旧消息缺 epoch/来源 |
| 5 | `goal_proposals` | 13 | `id` UUID | 逻辑 `message_id`→messages；`applied_goal_ids_json`→goals | **高**（AI 草案） | 否（AI 输出不确定） | **是（核心）** | 5 | 否 | v4/v5 加应用/撤销字段；JSON 可能损坏 |
| 6 | `growth_signals` | 15 | `id` UUID | 逻辑 `source_id`→journals；`source_version` 是日记哈希 | **高**（推断） | 部分（重跑 AI，成本/结果不确定） | **是（核心）** | 6 | 否 | v6 唯一索引；重复/哈希不匹配风险 |
| 7 | `growth_memories` | 16 | `id` UUID | JSON 引用 signal ids / source diary ids | **高**（长期画像） | 部分（可重算，但用户纠正/墓碑不可重建） | **是（核心）** | 7 | 否 | v7；软删除/纠正状态必须保留 |
| 8 | `insight_reports` | 11 | `id` UUID | 逻辑 `data_version` 绑定输入 | **高**（AI 解释） | 是（重跑 AI，不确定） | 可选派生 | 8 | 否 | v8；缓存按 dataVersion 失效 |
| 9 | `weekly_reviews` | 11 | `id` UUID，`period_key` 唯一 | 无 | **高**（周回顾） | 是（本地规则确定性） | 可选派生 | 9 | 否 | v9/v10 加 data/consent version |
| 10 | `weekly_ai_reviews` | 13 | `period_key` | 逻辑绑定 consent/config | **高**（AI 周报） | 否（AI 不确定） | 归档；**恢复必须排除**（预算/授权） | —（不恢复） | 否 | v10/v11；`fact_ids_json` 不是 JSON（P1 技术债） |

总计：**10 表 / 120 列**。v1 导出 3 表 / 23 列（19.2%）；详见 §6。

### 3.1 真实外键与逻辑引用

**数据库外键（2 个）：**

```text
goals.parent_id          -> goals.id        ON DELETE CASCADE
goal_checkins.goal_id    -> goals.id        ON DELETE CASCADE
```

**逻辑引用（无 FK，恢复必须应用层校验）：**

| 引用 | 目标 | 风险 |
|---|---|---|
| `advisor_messages.context_goal_id` | `goals.id`（可空） | 目标被删后可能悬挂；上下文回看可能失效 |
| `goal_proposals.message_id` | `advisor_messages.id` | 消息缺失会导致草案无法打开 |
| `goal_proposals.applied_goal_ids_json[]` | `goals.id` | 撤销/审计依赖；缺失会误判 |
| `growth_signals.source_id` | `journal_entries.id`（`source_type='DIARY'`） | 日记删除/回滚后线索悬挂 |
| `growth_signals.source_version` | 日记 title+body 的 SHA-256 | 内容变更后线索应 STALE |
| `growth_memories.signal_ids_json[]` | `growth_signals.id` | 记忆重建/来源展示 |
| `growth_memories.source_diary_ids_json[]` | `journal_entries.id` | 记忆来源展示 |
| `insight_reports.data_version` | 目标/进展/日记快照版本 | 恢复后数据版本变化应失效 |
| `weekly_reviews.data_version` / `ai_consent_version` | 输入/授权版本 | 恢复后可能 STALE |

依据：`app/schemas/.../11.json` 的 `createSql`；`MengyaDatabase.kt:102-370`；`GoalProposalApplyService.kt` 对 `applied_goal_ids_json` 的实际使用。

## 4. 字段级清单（备份/敏感/可重建）

说明：`敏感` 指“属于用户个人成长/健康/对话内容”；`备份` 指 v2 核心或派生；`v1` 指当前 `ExportJson` 是否写出了该字段。

### 4.1 `journal_entries`（11 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | INTEGER PK | 是（关联） | 是 | 是 | 自增；恢复保留原 id |
| `title` | TEXT NULL | 是 | 是 | 是 | 空标题归一为 null |
| `body` | TEXT NOT NULL | **是** | 是 | 是 | 最大 20,000 字（`JournalRules.kt:11`） |
| `created_at` | INTEGER | 是 | 是 | 是 | Epoch ms UTC |
| `updated_at` | INTEGER | 是 | 是 | 是 | Epoch ms UTC |
| `analysis_status` | TEXT | 是（状态） | 否 | 是 | PENDING/ANALYZING/COMPLETED/FAILED |
| `analysis_hash` | TEXT NULL | 是 | 否 | 是 | 内容版本哈希 |
| `analysis_error` | TEXT NULL | 是 | 否 | 是 | 失败原因 |
| `analysis_attempts` | INTEGER | 是 | 否 | 是 | 重试次数 |
| `analyzed_at` | INTEGER NULL | 是 | 否 | 是 | 成功时间 |
| `analysis_last_attempt_at` | INTEGER NULL | 是 | 否 | 是 | 最近尝试 |

恢复规则：`ANALYZING` 不得原样恢复（进程已死），应转为 `FAILED` 或 `PENDING`，并保留错误；不能把半分析状态当成功。

### 4.2 `goals`（11 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | INTEGER PK | 是 | 是 | 是 | 保留 |
| `title` | TEXT NOT NULL | 是 | 是 | 是 | ≤60 字（`GoalRules.kt:28`） |
| `description` | TEXT NULL | 是 | 是 | 是 | ≤1000 字 |
| `level` | TEXT | 是 | 是 | 是 | LONG_TERM/STAGE/ACTION |
| `parent_id` | INTEGER NULL | 是 | 是 | 是 | 根为 null；自引用 |
| `domain` | TEXT | 是 | 是 | 是 | 枚举名 |
| `status` | TEXT | 是 | 是 | 是 | 枚举名 |
| `start_date` | INTEGER NULL | 是 | 是 | 是 | Epoch ms |
| `due_date` | INTEGER NULL | 是 | 是 | 是 | Epoch ms |
| `created_at` | INTEGER | 是 | 是 | 是 | Epoch ms |
| `updated_at` | INTEGER | 是 | 是 | 是 | Epoch ms |

恢复规则：按 `level` 拓扑插入（LONG_TERM→STAGE→ACTION），父必须存在；未知枚举值拒绝，不能用 `Converters` 的默认值静默替换。

### 4.3 `goal_checkins`（7 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | INTEGER PK | 是 | 是 | 是 | 保留 |
| `goal_id` | INTEGER FK | 是 | 是 | 是 | 必须存在 |
| `recorded_at` | INTEGER | 是 | 是 | 是 | Epoch ms |
| `note` | TEXT | 是 | 是 | 是 | ≤500 字 |
| `metric_value` | REAL NULL | 是 | 是 | 是 | 有限数；±1e9 内 |
| `metric_unit` | TEXT NULL | 是 | 是 | 是 | ≤16 字 |
| `source` | TEXT | 是 | 是 | 是 | 当前 `MANUAL` |

### 4.4 `advisor_messages`（12 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | TEXT PK | 是 | 否 | 是 | UUID |
| `role` | TEXT | 是 | 否 | 是 | user/assistant |
| `content` | TEXT | **是** | 否 | 是 | 对话正文；可能引用日记/目标 |
| `created_at` | INTEGER | 是 | 否 | 是 | 排序依据 |
| `status` | TEXT | 是 | 否 | 是 | SENT/FAILED/CANCELLED |
| `error_code` | TEXT NULL | 是 | 否 | 是 | 错误码 |
| `error_message` | TEXT NULL | 是 | 否 | 是 | 用户可见错误 |
| `context_goal_id` | INTEGER NULL | 是 | 否 | 是 | 逻辑引用；悬挂需校验 |
| `context_epoch` | INTEGER | 是 | 否 | 是 | 授权 epoch |
| `sources_json` | TEXT NULL | 是 | 否 | 是 | 来源数组 JSON；不是正文/密钥 |
| `proposal_error` | TEXT NULL | 是 | 否 | 是 | 草案失败原因 |
| `proposal_mode` | INTEGER | 是 | 否 | 是 | 0/1 |

### 4.5 `goal_proposals`（13 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | TEXT PK | 是 | 否 | 是 | UUID |
| `message_id` | TEXT | 是 | 否 | 是 | 逻辑引用消息 |
| `created_at` | INTEGER | 是 | 否 | 是 | |
| `schema_version` | INTEGER | 是 | 否 | 是 | 草案 JSON 版本（当前 1） |
| `status` | TEXT | 是 | 否 | 是 | DRAFT/APPLIED/UNDONE |
| `summary` | TEXT | 是 | 否 | 是 | |
| `assumptions_json` | TEXT | 是 | 否 | 是 | JSON 数组 |
| `risks_json` | TEXT | 是 | 否 | 是 | JSON 数组 |
| `nodes_json` | TEXT | **是** | 否 | 是 | 草案节点；恢复必须逐项校验 |
| `selected_temp_ids_json` | TEXT NULL | 是 | 否 | 是 | 选择集合 |
| `applied_at` | INTEGER NULL | 是 | 否 | 是 | |
| `applied_goal_ids_json` | TEXT NULL | 是 | 否 | 是 | tempId→goalId 映射；引用校验 |
| `undone_at` | INTEGER NULL | 是 | 否 | 是 | |

### 4.6 `growth_signals`（15 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | TEXT PK | 是 | 否 | 是 | UUID |
| `source_type` | TEXT | 是 | 否 | 是 | 当前 DIARY |
| `source_id` | INTEGER | 是 | 否 | 是 | 日记 id |
| `source_version` | TEXT | 是 | 否 | 是 | 日记内容哈希 |
| `category` | TEXT | 是 | 否 | 是 | 枚举/字符串 |
| `content` | TEXT | **是** | 否 | 是 | AI 提取内容 |
| `evidence` | TEXT | **是** | 否 | 是 | 原文证据 |
| `is_inference` | INTEGER | 是 | 否 | 是 | 0/1 |
| `confidence` | REAL NULL | 是 | 否 | 是 | |
| `extracted_at` | INTEGER | 是 | 否 | 是 | |
| `status` | TEXT | 是 | 否 | 是 | ACTIVE/CORRECTED/DELETED/STALE |
| `origin` | TEXT | 是 | 否 | 是 | AI/USER |
| `source_title` | TEXT NULL | 是 | 否 | 是 | 展示用 |
| `content_hash` | TEXT | 是 | 否 | 是 | 唯一索引的一部分 |
| `updated_at` | INTEGER | 是 | 否 | 是 | |

### 4.7 `growth_memories`（16 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | TEXT PK | 是 | 否 | 是 | UUID |
| `theme` | TEXT | 是 | 否 | 是 | 主题 |
| `content` | TEXT | **是** | 否 | 是 | 长期画像摘要 |
| `is_inference` | INTEGER | 是 | 否 | 是 | |
| `status` | TEXT | 是 | 否 | 是 | ACTIVE/CORRECTED/DELETED/STALE |
| `created_at` | INTEGER | 是 | 否 | 是 | |
| `updated_at` | INTEGER | 是 | 否 | 是 | |
| `signal_ids_json` | TEXT | 是 | 否 | 是 | 引用 signal |
| `source_diary_ids_json` | TEXT | 是 | 否 | 是 | 引用 journal |
| `user_corrected` | INTEGER | 是 | 否 | 是 | 用户纠正标记（不可重建） |
| `version` | INTEGER | 是 | 否 | 是 | |
| `confidence` | REAL NULL | 是 | 否 | 是 | |
| `insufficient` | INTEGER | 是 | 否 | 是 | |
| `recent_change` | TEXT NULL | 是 | 否 | 是 | |
| `fingerprint` | TEXT | 是 | 否 | 是 | 墓碑/去重 |
| `deleted_at` | INTEGER NULL | 是 | 否 | 是 | 软删除 |

### 4.8 `insight_reports`（11 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | TEXT PK | 是 | 否 | 可选 | UUID |
| `data_version` | TEXT | 是 | 否 | 可选 | 输入版本 |
| `status` | TEXT | 是 | 否 | 可选 | READY/FAILED |
| `summary` | TEXT | **是** | 否 | 可选 | AI 摘要 |
| `facts_json` | TEXT | 是 | 否 | 可选 | 规则事实 |
| `insights_json` | TEXT | **是** | 否 | 可选 | AI 解释 |
| `period_start` / `period_end` | INTEGER | 是 | 否 | 可选 | |
| `generated_at` | INTEGER | 是 | 否 | 可选 | |
| `error` | TEXT NULL | 是 | 否 | 可选 | |
| `model` | TEXT NULL | 是 | 否 | 可选 | |

### 4.9 `weekly_reviews`（11 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `id` | TEXT PK | 是 | 否 | 可选 | UUID |
| `period_key` | TEXT UNIQUE | 是 | 否 | 可选 | 唯一 |
| `period_start` / `period_end` | INTEGER | 是 | 否 | 可选 | |
| `generated_at` | INTEGER | 是 | 否 | 可选 | |
| `version` | INTEGER | 是 | 否 | 可选 | |
| `data_version` | TEXT | 是 | 否 | 可选 | |
| `ai_consent_version` | TEXT | 是 | 否 | 可选 | |
| `facts_json` | TEXT | 是 | 否 | 可选 | |
| `sections_json` | TEXT | **是** | 否 | 可选 | |
| `notified_at` | INTEGER NULL | 是 | 否 | 可选 | 恢复后通知状态不应视为已发 |

### 4.10 `weekly_ai_reviews`（13 列）

| 字段 | 类型 | 敏感 | v1 | v2 | 说明 |
|---|---|---|---|---|---|
| `period_key` | TEXT PK | 是 | 否 | 归档 | 唯一 |
| `local_data_version` | TEXT | 是 | 否 | 归档 | |
| `ai_consent_version` | TEXT | 是 | 否 | 归档 | |
| `status` | TEXT | 是 | 否 | 归档 | PENDING/SENT/READY/FAILED |
| `generated_at` | INTEGER | 是 | 否 | 归档 | |
| `summary` | TEXT | **是** | 否 | 归档 | |
| `highlights_json` | TEXT | **是** | 否 | 归档 | |
| `suggestion` | TEXT | **是** | 否 | 归档 | |
| `fact_ids_json` | TEXT | 是 | 否 | 归档 | **实际是 U+001F 分隔串，不是 JSON** |
| `categories` | TEXT | 是 | 否 | 归档 | |
| `error` | TEXT NULL | 是 | 否 | 归档 | |
| `model` | TEXT NULL | 是 | 否 | 归档 | |
| `config_hash` | TEXT | 是 | 否 | 归档 | |

**恢复策略**：归档到 v2 以保留证据，但**不恢复到 live DB**；恢复后不得让旧 AI 结果占用本周预算或伪装成新授权下的结果。

## 5. 非 Room 持久化

| 数据 | 键/文件 | 敏感 | 可重建 | 建议 |
|---|---|---|---|---|
| 主题 | `mengya_settings` → `theme_mode` | 低 | 否（偏好） | 可备份；恢复可选 |
| AI endpoint | `mengya_ai` → `endpoint` | 中（可暴露服务商） | 否 | 可选备份；默认不自动应用 |
| AI model | `mengya_ai` → `model` | 中 | 否 | 可选备份 |
| 加密 Key | `mengya_ai` → `encrypted_api_key` | **高（秘密）** | 否 | **永不备份** |
| QA 假引擎 | `mengya_ai` → `use_fake_engine` | 低 | — | 永不从正式备份恢复；QA 也不应从正式备份获得 |
| 授权总开关 | `mengya_consent` → `granted` | **高** | 否 | **永不恢复** |
| 授权文本/类别版本 | `text_version` / `categories_version` | 高 | 否 | 永不恢复 |
| 授权接收方/类别 | `recipient` / `categories` | **高** | 否 | 永不恢复 |
| 授权时间/epoch | `granted_at` / `epoch` | 高 | 否 | 永不恢复；恢复后重新同意产生新 epoch |
| 周回顾开关 | `mengya_settings_weekly` → `weekly_review_enabled` | 低 | 否 | 可恢复 |
| 周 AI 自动开关 | `weekly_ai_enabled` | 中 | 否 | **恢复时强制 false** |
| 通知已询问 | `notification_permission_asked` | 低 | 否 | 恢复时 false（OS 权限不可恢复） |
| Keystore 密钥 | alias `mengya_ai_key` | **高** | 否 | **永不出设备、永不备份** |
| WorkManager DB | `androidx.work.workdb` | 低 | 是 | 不备份；恢复后重建唯一周期任务 |
| 通知渠道/权限 | OS | 低 | 否 | 不备份；按需重新请求 |
| 设备标识 | 未采集 | — | — | 不备份 |

## 6. 当前完整备份覆盖率

### 6.1 按 Room 表/列

| 指标 | 当前 v1 | 说明 |
|---|---|---|
| 表覆盖 | **3 / 10 = 30.0%** | journal_entries、goals、goal_checkins |
| 列覆盖 | **23 / 120 = 19.2%** | journal 5/11、goals 11/11、checkins 7/7 |
| 核心用户数据表覆盖 | **3 / 7 = 42.9%** | 缺 messages、proposals、signals、memories |
| 派生数据表覆盖 | **0 / 3 = 0%** | insight、weekly review、weekly AI |
| 恢复能力 | **0%** | 无导入代码；设置页明确“导入恢复尚未实现” |

计算依据：`ExportJson.kt:152-184` 只写 journal 的 5 个字段、goal 的 11 个字段、checkin 的 7 个字段；schema v11 共 120 列。

### 6.2 按非 Room 存储

| 指标 | 当前 v1 |
|---|---|
| DataStore 逻辑键 | **0 / 15** |
| 其中非秘密且可恢复的键 | **0 / 6**（theme、AI endpoint/model、weekly enabled/ai/asked） |
| 同意授权 | **0 / 7 键，且按策略永不恢复** |
| Keystore/API Key | **0 / 1（正确排除）** |
| 系统备份 | **0 / 1（allowBackup=false；但缺 rules，见 P0）** |
| 恢复 UI | **0** |

### 6.3 v2 目标覆盖（设计值，不是已实现）

| 指标 | v2 目标 |
|---|---|
| Room 表 | 10/10 写入备份（100%） |
| Room 列 | 120/120（100%） |
| 恢复表 | 9/10（`weekly_ai_reviews` 只归档不恢复） |
| 非秘密设置键 | 6/6（theme、AI endpoint/model、weekly 三键） |
| 秘密 | 0（API Key/Keystore/consent 永不进入备份） |
| 恢复能力 | 全量替换（G4A-2） |

## 7. 关键风险（数据层）

| 级别 | 风险 | 证据 | 影响 |
|---|---|---|---|
| **P0** | v1 被误称为完整备份 | `ExportJson` 三表；设置页“导入未实现” | 用户换机/刷机会丢 7 张表与设置；隐私承诺失真 |
| **P0** | 无系统备份规则 | `AndroidManifest.xml:15` 只有 allowBackup=false；无 dataExtractionRules/fullBackupContent | 部分 Android 12+ OEM 的 D2D 可能复制 DB/Prefs；与“本机优先”承诺冲突 |
| **P0** | 无跨存储事务 | Room + 4 个 DataStore + WorkManager | 恢复中断会半恢复；需 journal/reconciliation |
| **P0** | 秘密/授权恢复策略未定义 | `AiConfigStorage.kt:45`；`SecretCipher` Keystore；consent keys | 可能把不可解密 Key 或旧授权带入新设备，导致静默发送或永久锁死 |
| **P0** | 无导入校验/限额 | 只有 `verify` 结构校验；无大小/行数上限 | 恶意/损坏文件可 OOM、崩溃、部分写入 |
| **P1** | `fact_ids_json` 非 JSON | `WeeklyAiService.kt:184`；G3F 日志 `'week-counts'` | 备份解析器若按 JSON 处理会失败；归档会保留脏格式 |
| **P1** | 逻辑引用无 FK | proposals/signals/memories/insights | 恢复可能留下悬挂引用，UI 打不开或展示错误 |
| **P1** | 迁移/缓存兼容 | v6-v11 字段；insight dataVersion；weekly AI 预算 | 旧备份在新版本可能缺字段/缓存错误命中 |
| **P1** | 设备断开，未采集真实行数 | 本轮 adb 无设备 | 无法给出实际字节；v2 限额需在 G4A-1 用 QA 数据校准 |

## 8. 结论与下一步

- 当前不具备“完整备份/完整恢复”能力，v1 只能作为三表导出/人工查看。
- 进入 G4A-1 前必须先确定：v2 是否加密、v1 是否只识别不恢复、派生缓存是否纳入、AI 配置是否只备份 endpoint/model。
- 具体格式见 `G4A0-BACKUP-FORMAT-V2.md`，恢复事务见 `G4A0-RESTORE-TRANSACTION.md`，安全见 `G4A0-SECURITY-PRIVACY.md`。
