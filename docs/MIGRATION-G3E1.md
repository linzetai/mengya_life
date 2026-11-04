# Gate 3E-1 · 数据库迁移（v8 → v9）

新增 `weekly_reviews`：

| 列 | 说明 |
|---|---|
| id | UUID |
| period_key | ISO 周 key，唯一索引 |
| period_start/end | 周期时间 |
| generated_at | 生成时间 |
| version | 规则版本 |
| facts_json | 全部事实及来源 |
| sections_json | 四段结构 |
| notified_at | 通知发送时间 |

迁移 SQL：`MIGRATION_8_9`，只 CREATE TABLE + UNIQUE INDEX；无 DROP、无 destructive migration。

旧表（日记/目标/进展/顾问/草案/线索/记忆/洞察）全部保留。设备升级验证：user_version 8→9，MigrationA/B 仍在，goals 等不受影响。schema 1–9 保留。

无需迁移用户数据；测试数据在 QA 包隔离。
