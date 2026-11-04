# Gate 3C-1 · 数据库迁移（v5 → v6）

## 1. journal_entries 新增

| 列 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `analysis_status` | TEXT NOT NULL | PENDING | PENDING/ANALYZING/COMPLETED/FAILED |
| `analysis_hash` | TEXT | NULL | 最近成功分析的内容哈希 |
| `analysis_error` | TEXT | NULL | 最近失败原因 |
| `analysis_attempts` | INTEGER NOT NULL | 0 | 尝试次数 |
| `analyzed_at` | INTEGER | NULL | 成功时间 |
| `analysis_last_attempt_at` | INTEGER | NULL | 频率限制/重启恢复 |

## 2. 新表 growth_signals

字段见 `GROWTH-SIGNAL-SCHEMA.md`；索引：

- `(source_type, source_id)`
- `(source_id, status)`
- unique `(source_type, source_id, source_version, content_hash)`

## 3. SQL

```sql
ALTER TABLE journal_entries ADD COLUMN analysis_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE journal_entries ADD COLUMN analysis_hash TEXT;
ALTER TABLE journal_entries ADD COLUMN analysis_error TEXT;
ALTER TABLE journal_entries ADD COLUMN analysis_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN analyzed_at INTEGER;
ALTER TABLE journal_entries ADD COLUMN analysis_last_attempt_at INTEGER;

CREATE TABLE growth_signals (...);
CREATE INDEX ...;
CREATE UNIQUE INDEX ...;
```

没有 DROP、没有 destructive migration。

## 4. 保留

- 日记正文/标题/时间戳不变；
- 目标/进展/顾问消息/草案不变；
- 旧日记分析状态默认 PENDING，旧线索为空；
- schema 1–6 全部保留。

## 5. 验证状态

- 编译/单测通过；
- **真机迁移未验证**：按用户要求，本轮先不执行 adb，等待 Wi-Fi 后验证。
