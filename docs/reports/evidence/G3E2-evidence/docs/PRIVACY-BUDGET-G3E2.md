# 隐私与预算

- 本地回顾：始终读取本地数据，不要求 AI 授权，不联网。
- AI 输入：按当前 AI 类别重新构建；关闭类别不进入 prompt；不直接发送完整本地缓存。
- 请求前：consent epoch/categories、endpoint/recipient、config、dataVersion、预算。
- 请求后：再次校验；变化则丢弃响应，标 FAILED。
- 预算：每周一次（period_key 唯一），maxTokens 900；并发由唯一插入原子保证。
- 通知：本地通知；权限与 AI 数据授权独立；同周原子 claim 一次。
