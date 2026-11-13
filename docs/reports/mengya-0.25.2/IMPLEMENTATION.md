# MengYa Life 0.25.2 — 实现范围

基线 `419fc111a0c2bb0db44791f5719049b6ba2b4811`，版本 `0.25.2` / versionCode 37。

## 复用路径

- **Insight**：`InsightService` + `GrowthInsight.factIds` + 规则 `InsightFact`；当前 source 继续由报告的 `dataVersion`、授权 epoch/recipient、目标/进展/日记/成长记忆计算。没有新建议表。
- **唯一顾问会话**：Insight 点击携带 `InsightReference(reportId,dataVersion,insightId)` 进入既有 `AdvisorViewModel`/`ConversationRepository`。引用写在现有 `advisor_messages.sources_json`（包含 report、建议、factIds 与原始 GOAL/CHECKIN/JOURNAL/MEMORY IDs 的 metadata），未另建聊天会话或引用数据库。
- **记录候选**：顾问后续讨论复用现有 `RecordProposal`；计划仍走 `PlanRecordApplyService` → `SmallPlanRepository`。确认前只有 DRAFT；编辑、取消、重复确认沿用现有 proposal 状态与事务。修复 plan 编辑时 `plan_change_json` 未被保存的问题及计划编辑器误叠加通用 Record 编辑器的问题。
- **页面闭环**：Today 优先展示可追溯的建议并提供生成/更新入口；确认后 SmallPlan 的现有观察流更新 Today。Trajectory 读取同一批 SmallPlan 与真实 plan_progress 计数；零条执行记录显示“不代表没有执行”。
- **QA**：FakeAdvisorEngine 和 `QaGrowthLoopFixtureSeeder` 仅用于 QA 包离线 walkthrough；夹具标题/正文明确标为 QA。正式运行时没有虚构进展，FakeAdvisorEngine 在正式包不可用。

## 关键防护

- Insight 解析只接受本轮 factIds/source 白名单，SUGGESTION 必须标记为 AI 推测；提示明确禁止诊断。
- dataVersion 包括目标、进展、日记原文版本、成长记忆、授权类别/epoch/接收方。缓存或 handoff 过期、来源删除、授权关闭/变化、数据不足时不把旧建议展示为当前依据。
- AdvisorViewModel 在 handoff、模型返回后、行动草案生成和最终确认前验证来源；失效时不创建/应用正式目标或计划。
- 正式数据只经现有 proposal/plan service 写入；AI 不直写业务表，无新数据库或迁移。
