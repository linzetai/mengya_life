# Gate D
- appendAssistant expectedRestoreGeneration 从 null 改为 boundGeneration；写决策前再次 boundOk。
- 通知动作在恢复维护中直接拒绝。
- 未完成：claim/消息/决策/通知同 Room 事务；恢复挂起点 instrumented；旧结果跨代次真机。
- 风险：消息后崩溃仍可能孤立消息（机会 UNKNOWN，不重发）。

## Round 7
- SEND 提交改为：RestoreWriteBarrier.withWrite(boundGeneration) 包裹 + Room withTransaction(claimSending、appendAssistant、saveDecision、markDelivered、markStatus)。
- appendAssistant 使用 boundGeneration；boundOk 失败抛 RESTORE_STALE_RESULT，事务回滚。
- 仍未做：恢复挂起点逐点 instrumented、通知与数据库跨系统原子（仍 at-most-once）。
