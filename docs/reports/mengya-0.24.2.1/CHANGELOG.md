# 0.24.2.1 — 恢复边界最终收敛

- 恢复凭据策略：RestoreCredentialPolicy。endpoint 不变保留本机 Key/授权；endpoint 变化删除 Key 并 reset 授权；备份无 endpoint 保持现状。
- RestoreCoordinator 在设置收敛阶段读取 before/after endpoint 并应用该策略；删除 Key 未确认则保持维护状态。
- 设置页删除矛盾的“导入尚未实现”入口，恢复说明改为 endpoint/Key 策略；RestoreSection 为实际入口。
- CareRunner 所有决策/状态写（ensure/EXPIRED/DROP/DEFER/markStatus）统一走 fenced：boundOk + RestoreWriteBarrier.withWrite(boundGeneration)。
- SEND 仍为同一 Room 事务 + 写屏障；旧代次在 ensure 阶段即放弃，不再留下 PENDING 候选。
- 通知投递：Worker/ViewModel 在 runOnce、claim 前、post 前校验 barrier.isReady 与 generation；恢复维护/代次变化不投递。
- 测试：RestoreCredentialPolicyTest 4 项；CareRunnerFenceTest 更新旧代次断言（0 行）；instrumented OK (15 tests)。
