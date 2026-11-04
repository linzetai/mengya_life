# Gate 3D-2 · 数据库版本说明

**无数据库版本升级**：Room 仍为 **v8**。

原因：
- 洞察报告仍使用 `insight_reports`；新增 `factIds` 只存在于 `insights_json` 内部，不新增列；
- 旧报告 `factIds` 为空时，UI 回退展示该报告的全部规则事实；
- 目标字段（description、parentId、level、start/due）已存在于 `goals`，无需迁移；
- 不修改日记/目标/进展/记忆表。

因此没有 v8→v9 migration，不需要清除正式数据。
