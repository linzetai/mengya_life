# Gate 5 最终验证（0.24.1）

## PASS
- JVM：350 tests PASS / 0 FAIL。
- Instrumented：CareDaoIntegrationTest 2 tests PASS（`am instrument`：OK (2 tests)）：
  - claimNotification 幂等；
  - 两个并发 claim 只有 1 个成功；
  - SENDING→UNKNOWN 收敛。
- `:app:lintDebug` PASS（lint.xml 跳过不兼容的 NullSafeMutableLiveData Detector）。
- DB 21→22 真机迁移；备份→恢复成功，record_proposals=12、偏好=4、Key 保留。
- 通知：周日 20:00 SEND，22:42 通知一次；连续 3 次 Worker 后 notified_at 未变。
- 通知动作自动化（QA 包 receiver 导出，正式包 exported=false）：
  - LATER → feedback DEFERRED、机会 PENDING；
  - SILENCE → feedback SILENCED、机会 DISMISSED、care_preferences 写入 goal:6 SILENCED。

## 部分/BLOCKED
- Worker 多次连跑已做；并发 Runner 与崩溃注入未做（DAO claim 并发已覆盖）。
- Room migration 仅真机启动验证，无 MigrationTestHelper。
- QA 测试时钟下 decision.created_at 用真实时间，sentSince(testDayStart) 会低估；仅测试环境。

## 结论
CONDITIONAL：关键代码、备份、通知幂等、动作与基础集成测试已 PASS；崩溃注入与完整 Worker 并发仍缺。
