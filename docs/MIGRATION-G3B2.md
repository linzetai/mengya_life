# Gate 3B-2 · 数据库迁移（v4 → v5）

数据库：`MengyaDatabase`  
版本：`DB_SCHEMA_VERSION = 5`  
迁移：`MIGRATION_4_5`（非破坏性）

## 1. 变更

### `advisor_messages`

| 列 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `proposal_mode` | INTEGER NOT NULL | 0 | 该消息是否由草案请求产生，重试保持模式 |

### `goal_proposals`

| 列 | 类型 | 用途 |
|---|---|---|
| `selected_temp_ids_json` | TEXT | 用户最后确认的节点集合 |
| `applied_at` | INTEGER | 应用时间 |
| `applied_goal_ids_json` | TEXT | `{tempId: goalId}` 映射，用于撤销 |
| `undone_at` | INTEGER | 撤销时间 |

`status` 继续使用 TEXT：`DRAFT / APPLIED / UNDONE`。

## 2. SQL

```sql
ALTER TABLE advisor_messages ADD COLUMN proposal_mode INTEGER NOT NULL DEFAULT 0;
ALTER TABLE goal_proposals ADD COLUMN selected_temp_ids_json TEXT;
ALTER TABLE goal_proposals ADD COLUMN applied_at INTEGER;
ALTER TABLE goal_proposals ADD COLUMN applied_goal_ids_json TEXT;
ALTER TABLE goal_proposals ADD COLUMN undone_at INTEGER;
```

没有 DROP、没有 destructive migration。

## 3. 保留

- 日记、目标、打卡、趋势：不修改表结构；
- 历史顾问消息保留；旧消息 `proposal_mode=0`；
- 旧草案保留为 DRAFT；
- 授权 DataStore 不变。

## 4. 验证

正式包 v4 备份 → 安装 v5：

- `user_version=5`；
- 旧日记 `MigrationA/B` 逐字段一致；
- 旧消息保留；
- `goal_proposals` 新列存在；
- 真机真实流程创建 5 个目标后撤销，`goals=0`，旧日记仍在。

证据：`docs/reports/logs/g3b2-migration.log`。
