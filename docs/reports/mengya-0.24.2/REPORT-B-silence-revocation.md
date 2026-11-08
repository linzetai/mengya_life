# Gate B
- 当前静音唯一来源 = care_preferences；care_feedback 仅审计；silencedTopics() 返回空集。
- 站内静音写 care_preferences（幂等检查），通知静音写同表；自然语言恢复 revokeByTopic。
- 最窄范围：优先 planId，其次 goalId，不因 goalId 连带静音目标下其他计划。
- DB v23 一次性迁移旧 SILENCED feedback → care_preferences，已有有效静音不重复写。
- 测试：JVM 解析；迁移 instrumented 待补。

## Round 3
- 新增 instrumented `migrate22To23MigratesSilencedFeedbackToPreference`：v22 中 SILENCED feedback + planId=5 → v23 care_preferences(plan:5, SILENCED, USER_EXPLICIT)。
- 运行状态：BLOCKED（设备 127.0.0.1:44435 连接被拒，adb devices 为空）。
