# Room v10 → v11

- `weekly_ai_reviews` 新增 `config_hash TEXT NOT NULL DEFAULT ''`。
- `MIGRATION_10_11` 单条 ALTER TABLE；无 DROP。
- 设备升级 user_version=11；旧周回顾/AI 历史与业务数据保留。
