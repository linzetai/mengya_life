# AI 预算与重试

- period_key 唯一：每个自然周最多一次请求；PENDING→SENT 原子。
- 未发送的 PENDING 可删除恢复；SENT/READY/FAILED 都消耗本周预算。
- 失败/超时：FAILED，不自动重试；UI 不再提供有效重试按钮（本周额度用尽）。
- 输入：facts ≤20、单条 ≤300 字；输出 maxTokens=900。
- 结果缓存：localDataVersion + aiConsentVersion + configHash；不匹配标 STALE/不展示。
