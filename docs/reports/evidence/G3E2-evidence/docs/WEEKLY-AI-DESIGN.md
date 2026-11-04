# 每周 AI 增强设计

- 本地回顾与 AI 增强分层：`weekly_reviews` + `weekly_ai_reviews`。
- AI 开关默认关闭；开启前必须已有 AI 授权 + 配置 + runtime endpoint 一致。
- 自动/手动均按 `period_key` 唯一预留，每自然周最多一次请求；maxTokens=900。
- AI 输入从当前快照按 GOALS/PROGRESS/JOURNAL 重新构建，不发送完整本地回顾缓存。
- 输出严格 JSON：summary、1–3 highlights、suggestion；每条 factIds 必须属于 facts，sources 必须关联事实来源；失败保留本地回顾。
- 请求后再次检查 consent/epoch/endpoint；过期结果写 FAILED，不作为有效 AI 内容。
- 失败不自动重试；用户手动按钮也受同周期唯一约束。
