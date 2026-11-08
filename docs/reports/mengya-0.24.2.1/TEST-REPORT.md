# 0.24.2.1 测试报告

- JVM：`./gradlew :app:testDebugUnitTest` → 361 tests PASS / 0 FAIL（含 RestoreCredentialPolicyTest 4）。
- 构建：`:app:assembleDebug :app:assembleQa` → BUILD SUCCESSFUL。
- lint：`:app:lintDebug` → BUILD SUCCESSFUL。
- Android instrumented（设备 2509FPN0BC / Android 17 / 127.0.0.1:37523）：
  `adb shell am instrument -w com.mengyalife.app.test/androidx.test.runner.AndroidJUnitRunner`
  → OK (19 tests)，含迁移/事务回滚/Runner 恢复交错/旧代次 0 行断言。
- 设备最小回归：本轮未重新跑通知/备份 UI；沿用 0.24.2 设备证据。
- BLOCKED：无。

## 设备最小回归（Round 2）
- 设备 2509FPN0BC / Android 17 / port 37523，安装 0.24.2.1 QA 并启动成功。
- 广播通知动作：
  - LATER(id=185) → care_feedback(185, DEFERRED)；
  - SILENCE(id=187) → care_feedback(187, SILENCED)、机会 DISMISSED、care_preferences 写 SILENCED。
- 观察：185 机会状态在查询时为 SENT（LATER 本应置 PENDING）；存在前台 Care 重跑并发覆盖的可能，已列为待查项，不判 PASS。

## Round 3 复测
- 设备 LATER：应用进程启动后广播 id=185 → 机会 PENDING，新增 DEFERRED feedback(created_at=测试时钟)。此前 SENT 是安装后进程未启动、广播未写，非状态机 bug。
- 新增 instrumented：模型调用期间写入 DEFERRED，返回 SEND 时最终复核拒绝发送，机会回 PENDING、无消息/SEND 决策。
- 最终：JVM 362 tests PASS；instrumented OK (19 tests)；Debug/QA/lint PASS。

## Round 4
- 新增 instrumented：模型运行期间写入全局 BUSY，返回 SEND 时最终复核拒绝，机会 PENDING、唯一决策 DEFER、无消息。
- Instrumented 总计 OK (19 tests)。

## Round 5
- 新增 instrumented：模型返回 SEND 后日上限策略拒绝，写 DEFER、无消息、无新增 SEND。
- Instrumented：OK (19 tests)。
