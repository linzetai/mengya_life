# Gate B
- 当前静音唯一来源 = care_preferences；care_feedback 仅审计；silencedTopics() 返回空集。
- 站内静音写 care_preferences（幂等检查），通知静音写同表；自然语言恢复 revokeByTopic。
- 最窄范围：优先 planId，其次 goalId，不因 goalId 连带静音目标下其他计划。
- DB v23 一次性迁移旧 SILENCED feedback → care_preferences，已有有效静音不重复写。
- 测试：JVM 解析；迁移 instrumented 待补。
