# AI 预算与重试收尾

- 每周一次：period_key 唯一 + PENDING→SENT 原子领取。
- 未发送 PENDING 可删除；SENT/READY/FAILED 消耗预算，不自动重试。
- 输入 facts ≤20，单条 300 字；maxTokens=900。
- 失败/超时/取消保留本地回顾；AI 状态 FAILED；不无限重试。
- 缓存绑定过滤后 AI 输入、consent、endpoint/model、period。
