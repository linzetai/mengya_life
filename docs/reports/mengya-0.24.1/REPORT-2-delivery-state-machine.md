# Gate 2 投递幂等

## 已实现
- CareCard 增加 isNew/decisionId；旧 SEND 返回 isNew=false。
- care_decisions 新增 notified_at（DB v22）；claimNotification() 条件 UPDATE 原子占用。
- Worker 只在 isNew 且 claim 成功时发通知；ViewModel 退后台同样 claim，旧卡片不再通知。
- SENDING→UNKNOWN 在 Worker/启动收敛。

## 状态
机会：PENDING/SENDING/SENT/DISMISSED/DROPPED/EXPIRED/UNKNOWN；决策另有 notified_at/message_id/delivered_at。
at-most-once：通知与 DB 无法跨系统原子，通知后崩溃只能标记已 claim，接受至多一次。

## 验收
- D02/D03：PASS（代码路径）。
- D01/D05/D06/D07：BLOCKED（未做 Worker 连跑、并发、崩溃注入集成测试）。
- D04（未反馈不阻塞）：FAIL，仍以 latestSentWithoutFeedback 短路，旧卡会阻塞新决策。
- D08：PASS（SENDING→UNKNOWN 真机已验证，0.24）。
