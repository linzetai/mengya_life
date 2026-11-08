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

## Round 6（endpoint 设备 e2e）
- 新增 QA 钩子 qa_endpoint_change_restore：A 备份→切换 endpoint B→恢复 A，期望删 Key/reset 授权。
- 设备冷启动执行时钩子未产生 CareRoundtrip 日志、Key 未删除；判定为 BLOCKED（钩子未触发，原因未定），不计 PASS。
- 恢复凭据策略仍由 4 项 JVM 单测覆盖；设备端到端未验证。

## Round 7（endpoint 诊断）
- `am start -S/-W` 会丢失 extras；改为先 `am force-stop` 再普通 `am start --ez`，extras 正常到达（endpointFlag=true）。
- 但钩子在 awaitReady/build/restore 阶段仍无结果日志、Key 未删除，判定 BLOCKED；尚未定位卡点。
- 已移除临时 QA 诊断日志。

## Round 8（endpoint 卡点）
- 阶段日志：`stage=awaitReady` → `stage=build` 后不再前进；卡在 `BackupExporter.build()`，与 extras/awaitReady 无关。
- 该 build 在 qa_care_roundtrip 路径曾成功，新增 endpoint 钩子场景下卡住原因未定位；判定 BLOCKED，不计 PASS。
- 已移除阶段日志。
