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

## 追加（Round 6）
- Instrumented（真机）：`OK (4 tests)`：
  - CareDaoIntegrationTest：通知 claim 幂等、两个并发 claim 仅 1 成功、SENDING→UNKNOWN；
  - 并发机会认领仅 1 成功；
  - CareMigrationTest：v21→v22 迁移新增 notified_at，旧行保留。
- 命令：`adb shell am instrument -w com.mengyalife.app.test/androidx.test.runner.AndroidJUnitRunner`
- 仍未覆盖：claim→写消息→写决策→通知之间的逐点崩溃注入；消息写入本身无 opportunity 幂等键，D06 仅部分覆盖。

## Round 7 最终
- Instrumented：`OK (6 tests)`（3 DAO + 1 migration + 2 crash-point）。
- `:app:lintDebug` PASS；350 JVM tests PASS；Debug/QA PASS。
- 崩溃注入为状态级模拟（claim 后、消息后、通知 claim 后），未做真实 `kill -9`。
- 残留：崩溃在消息写入后可能留下一条孤立助手消息，但机会为 UNKNOWN，不会重复发送/通知；QA 测试时钟发送计数偏低仅测试环境。

## 验收结论
**PASS（有条件）**：满足“同一机会至多一次通知、用户静音优先、恢复不重复、备份完整、授权绑定”的工程底线；条件为上述两项已知非阻塞残留。
