# Gate D
- appendAssistant expectedRestoreGeneration 从 null 改为 boundGeneration；写决策前再次 boundOk。
- 通知动作在恢复维护中直接拒绝。
- 未完成：claim/消息/决策/通知同 Room 事务；恢复挂起点 instrumented；旧结果跨代次真机。
- 风险：消息后崩溃仍可能孤立消息（机会 UNKNOWN，不重发）。

## Round 7
- SEND 提交改为：RestoreWriteBarrier.withWrite(boundGeneration) 包裹 + Room withTransaction(claimSending、appendAssistant、saveDecision、markDelivered、markStatus)。
- appendAssistant 使用 boundGeneration；boundOk 失败抛 RESTORE_STALE_RESULT，事务回滚。
- 仍未做：恢复挂起点逐点 instrumented、通知与数据库跨系统原子（仍 at-most-once）。

## Round 8（设备）
- Instrumented `sendTransactionRollsBackMessageAndDecisionTogether`：事务内插消息+决策后抛错，消息与决策均回滚为 0。
- 设备 instrumented：`OK (8 tests)`。
- 仍未做恢复挂起点交错与通知跨系统原子。

## Round 11
- 复用/确认 `RestoreWriteBarrierTest.exclusive restore waits existing write and invalidates stale work`：
  - 旧 generation 写持有期间恢复等待；
  - 恢复完成后 `withWrite(oldGeneration)` 抛 RestoreException，旧写不落库。
- 这是 D1/D4 的屏障级证明；仍缺 CareRunner 事务内“claim 后恢复”的 Runner 级挂起点测试。

## Round 12/13
- CareRunner 增加默认关闭的 `beforeCommit` 事务挂起点（生产 null，无行为变化）；位于 claimSending 之后、写消息之前。
- 后续 instrumented 可用真实 Room + 该 hook 在事务内启动并完成恢复，验证旧结果不落库。
