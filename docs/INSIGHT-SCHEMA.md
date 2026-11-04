# Gate 3D-1 · 成长洞察 Schema

## 1. 领域模型

`InsightType`: POSITIVE（做得好）/ RISK（需关注）/ SUGGESTION（下一步建议）

`InsightSourceRef`: type（GOAL/CHECKIN/MEMORY）、recordId、label

`InsightFact`: 规则层生成的可核验事实；sourceRefs 必须指向真实输入；isInference=false

`GrowthInsight`: AI 解释/建议；type、title、body、isInference、confidence、sources

`InsightReport`: id、dataVersion、status（READY/FAILED）、summary、facts、insights、period、generatedAt、error

## 2. 规则层输入

- GOALS：长期/阶段/行动目标、状态、起止日期
- PROGRESS：目标进展、数值、单位
- JOURNAL：有效成长记忆（ACTIVE/CORRECTED，来源日记仍存在）

严格受三类授权控制；关闭类别时对应数据不进入 facts/来源白名单。

## 3. 规则事实

1. 目标数量/进行中/层级；
2. 最近 30 天进展数、覆盖目标；
3. 同目标 + 同单位数值序列（≥2 点）的 first/latest/delta/方向；
4. 不同单位单独列出，明确“不做跨单位相加/换算”；
5. 14 天内到期且未完成的目标；
6. 成长记忆数量/主题/用户纠正；
7. 空数据或记录过少 → fact `insufficient` 明确写“数据不足”。

## 4. 来源校验

AI JSON：

```json
{"schemaVersion":1,"summary":"...","insights":[{
 "type":"POSITIVE|RISK|SUGGESTION","title":"...","body":"...",
 "isInference":true,"confidence":0.0,
 "sources":[{"type":"GOAL|CHECKIN|MEMORY","id":"...","label":"..."}]
}]}
```

- 最多 3 条；字段长度/类型/confidence 0..1；
- 每个 source 必须 `TYPE:id` 在 allowedSources 白名单；
- 兼容模型 `"id":"CHECKIN:7"` 写法，归一化为 7 后校验；
- 不允许 sources 为空；
- 解析失败不写 AI insights，只保留 facts + FAILED 状态；
- 不把 AI 输出当目标/进展写库。

## 5. 数据版本与缓存

`dataVersion` = 授权类别 + goals（标题/层级/状态/日期）+ checkins（目标/时间/内容/数值/单位）+ memories（主题/内容/纠正/来源）的 SHA-256。

- 报告存 `insight_reports`，按 dataVersion 查找；
- 打开总览只 refresh 当前版本缓存，不调用模型；
- 用户点「生成/更新洞察」才调用；
- 数据变化、类别关闭、记忆纠正 → 版本变化，旧报告不展示；
- 新报告覆盖最新，旧版本保留审计。

## 6. 权限

- GOALS 关闭：不读取目标；
- PROGRESS 关闭：不读取进展/数值；
- JOURNAL 关闭：不读取成长记忆；
- 全部关闭/未授权：不调用 AI，显示数据不足/未授权；
- 请求后重新校验 dataVersion/consent，变化则丢弃 AI 结果。
