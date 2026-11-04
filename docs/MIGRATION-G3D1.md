# Gate 3D-1 · 数据库迁移（v7 → v8）

新增 `insight_reports`：

| 列 | 说明 |
|---|---|
| id | UUID |
| data_version | 输入数据/权限版本哈希 |
| status | READY / FAILED |
| summary | AI 摘要或空 |
| facts_json | 规则事实数组 |
| insights_json | AI 洞察数组（失败时为空） |
| period_start/end | 数据区间 |
| generated_at | 生成时间 |
| error | 失败原因 |
| model | 预留 |

索引：data_version、generated_at。

SQL 由 `MIGRATION_7_8` 执行；没有 DROP、没有 destructive migration；日记/目标/打卡/顾问消息/草案/线索/记忆均不改变。

schema 1–8 保留。
