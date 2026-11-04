# 数据库迁移 v9 → v10

- `weekly_reviews` 增加 `data_version`、`ai_consent_version`。
- 新增 `weekly_ai_reviews`（period_key 主键）：本地/AI 分层、调用状态、摘要、highlights、建议、类别、错误。
- `MIGRATION_9_10` 使用 ALTER TABLE + CREATE TABLE；无 DROP；旧数据保留。
- 设备升级 user_version 9→10，MigrationA/B 仍在。
