# Gate 3C-2 · 数据库迁移（v6 → v7）

新增 `growth_memories`：

| 列 | 说明 |
|---|---|
| id | UUID PK |
| theme | 成长领域/主题 |
| content | 记忆内容 |
| is_inference | 事实/推测 |
| status | ACTIVE/CORRECTED/STALE/DELETED |
| created_at/updated_at | 时间 |
| signal_ids_json | 线索 ID 数组（JSON） |
| source_diary_ids_json | 日记 ID 数组（JSON） |
| user_corrected | 用户是否纠正 |
| version | 版本 |
| confidence | 平均置信度，可选 |
| insufficient | 信息不足 |
| recent_change | 最近变化 |
| fingerprint | 去重/墓碑 |
| deleted_at | 删除时间 |

索引：theme、status、(theme,fingerprint)。

SQL 由 `MIGRATION_6_7` 执行；无 DROP、无 destructive migration；日记/目标/打卡/顾问消息/草案/线索不改变。

schema 1–7 全部保留。
