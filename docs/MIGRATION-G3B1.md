# Gate 3B-1 · 数据库迁移（v3 → v4）

数据库：`MengyaDatabase`  
版本：`DB_SCHEMA_VERSION = 4`  
迁移：`MIGRATION_3_4`（**非破坏性**）

## 1. 变更

### `advisor_messages` 新增列

| 列 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `context_epoch` | INTEGER NOT NULL | 0 | 产生消息时的授权代次 |
| `sources_json` | TEXT NULL | NULL | 实际引用来源（JSON，仅引用） |
| `proposal_error` | TEXT NULL | NULL | 草案解析失败信息 |

旧消息升级后 `context_epoch = 0`；首次授权后 epoch ≥1，因此 **Gate 3A 旧消息不会自动进入新请求**，但仍可在本机查看。

### 新增 `goal_proposals`

```sql
CREATE TABLE IF NOT EXISTS goal_proposals (
  id TEXT PRIMARY KEY NOT NULL,
  message_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  schema_version INTEGER NOT NULL,
  status TEXT NOT NULL,
  summary TEXT NOT NULL,
  assumptions_json TEXT NOT NULL,
  risks_json TEXT NOT NULL,
  nodes_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS index_goal_proposals_message_id
  ON goal_proposals(message_id);
```

## 2. SQL

```sql
ALTER TABLE advisor_messages ADD COLUMN context_epoch INTEGER NOT NULL DEFAULT 0;
ALTER TABLE advisor_messages ADD COLUMN sources_json TEXT;
ALTER TABLE advisor_messages ADD COLUMN proposal_error TEXT;
CREATE TABLE IF NOT EXISTS goal_proposals (...);
CREATE INDEX IF NOT EXISTS index_goal_proposals_message_id ON goal_proposals(message_id);
```

没有 `DROP TABLE`、没有 `fallbackToDestructiveMigration()`。

## 3. Schema 文件

保留并新增：

- `app/schemas/.../1.json`
- `app/schemas/.../2.json`
- `app/schemas/.../3.json`
- `app/schemas/.../4.json`

## 4. 验证

- 正式包从 v3 覆盖安装到 v4：`PRAGMA user_version = 4`；
- 旧日记 `MigrationA` / `MigrationB` 保留且内容一致；
- 旧顾问消息保留，`context_epoch = 0`；
- `goal_proposals` 表存在；
- 授权存储在独立 DataStore，不存在需要迁移的旧授权（G3A 用户默认未授权）。
