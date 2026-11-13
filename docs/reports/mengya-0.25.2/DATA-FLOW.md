# 0.25.2 Growth Loop — 数据流

```text
授权类别 + 当前 Room 目标/进展/日记/成长记忆
  → InsightRules 规则 facts / sourceRefs / hasEnoughData / dataVersion
  → InsightService（不足则仅保留本地事实，不调用 AI）
  → InsightParser 校验 factIds、source 白名单及建议推测标记
  → Today 展示“事实 / AI 推测 / 下一步建议”
  → 用户选中建议
  → 用 reportId + dataVersion + insightId 重新解析当前来源
  → 唯一 Advisor 会话：user message 持久化 Insight/fact/source 引用
  → Advisor 解释 / 用户异议 / 修正前提
  → 用户明确表达未来行动
  → RecordProposal(DRAFT, PLAN)，sourceMessageIds 回链原讨论
  → 编辑 / 确认 / 取消
  → PlanRecordApplyService Room 事务
  → small_plans + plan_events + proposal APPLIED
  → Today ACTIVE 小计划 / Trajectory 所有已确认计划与已记录执行数
```

| 状态 | 判断与写入边界 |
|---|---|
| 洞察当前 | 当前授权和数据版本与 InsightReference 一致，report READY，建议 ID 存在、factIds 全有效，sources 仍在允许列表，且有足够事实。 |
| 洞察过期 / 授权变化 / 来源删除 / 数据不足 | 隐藏旧洞察为当前建议；handoff 不调用顾问，行动草案生成/确认再复核；不写正式目标、计划或进展。 |
| 顾问讨论 | `advisor_messages` 唯一会话；user 和 assistant 消息都保留来源引用。Insight metadata 存在现有 `sources_json`，没有新表。 |
| 行动草案 | 提案按 assistant message 幂等保存为 DRAFT；编辑仍是 DRAFT；取消变 CANCELLED。 |
| 确认计划 | 一个 Room transaction 创建 SmallPlan 和创建事件，并把 proposal 从 DRAFT 标成 APPLIED；重复确认回到相同 planId。 |
| 后续进展 | 只有用户明确报告执行并确认，才通过现有 PlanProgress proposal 写入 plan_progress。无记录只显示“尚无执行记录”，不推断未执行。 |

QA FakeAdvisorEngine 只从本次 InsightInput 的既有 facts/sourceRefs 选择引用；数据不足时拒绝输出建议。QA fixture 是单独标记的测试记录，不是用户真实进展。
