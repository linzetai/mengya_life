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

## Round 14（0.24.2 Gate D：Runner 级恢复栅栏交错，真机）
### 改动文件
- 新增 `app/src/androidTest/java/com/mengyalife/app/care/CareRunnerFenceTest.kt`（7 个 instrumented 用例）。
- 生产代码零改动；只用 `CareRunner` 已有的测试缝：`beforeCommit`（默认 null）、`restoreGeneration`、`database`、`writeBarrier`。`beforeCommit` 缝来自 d92241e。

### 夹具
- 真实：Room in-memory `MengyaDatabase`（`allowMainThreadQueries`）、`CareRepository`、`CarePreferenceRepository`、`SmallPlanRepository`、`RoomConversationRepository`、`CareSettings`（真实 SharedPreferences，`notificationsEnabled=false`）。
- fake（只替换外部依赖）：
  - `AiConsentRepository`：全类别 granted，recipient 与 endpoint 都经 `AiConsentRules.normalizeRecipient` 匹配；`resetAfterRestore` 模拟生产重置。
  - `AdvisorConfigRepository`：`runtimeConfig()` 固定 `https://care-fence.example.test/v1` / `care-fence-model`。
  - `AdvisorDataRepository`：返回一个 30 天无进展的 `IN_PROGRESS` 目标，稳定产生 `GOAL_STALL`。
  - `AdvisorEngine`：不联网；从 prompt 的 `机会#N` 解析真实候选 id，返回合法 SEND JSON；可用 `CompletableDeferred` 卡住 snapshot / chat。
- 固定时钟 2026-03-10 10:00 Asia/Shanghai（避开 22:00–08:00 免打扰）；DB 断言全部读真实行。

### 用例与结论
1. `runnerCommitsSendAtomicallyAndNotifiesAtMostOnce`（正向基线）：1 条 `advisor_messages`、1 条 `SEND` 决策、机会 `SENT`、`message_id/delivered_at` 回填；`claimNotification` 第一次 true、第二次 false，`notified_at` 只写一次。证明夹具确实走通真实 SEND 事务。
2. `restoreBeforeRunOnceProducesNoMessageDecisionOrNotification`（模型前）：恢复（generation+1，并按生产语义 reset 授权）完成后调用 `runOnce()`，返回 null；模型 0 次调用，消息/决策/机会均 0。
3. `restoreBeforeModelCallDiscardsRunWithoutMessageOrDecision`：runOnce 卡在 snapshot 时恢复完成；释放后 `boundOk` 失败，模型 0 次调用，消息/决策 0。
4. `restoreDuringModelCallDiscardsReturnedSendWithoutRows`（模型后）：runOnce 卡在 `engine.chat` 时恢复完成；模型返回的 SEND 被 `boundOk` 丢弃，消息/决策 0，`latestSentDecision` 为空。
5. `restoreWaitsForClaimedSendThenReplacementLeavesNoResidue`（claim 后）：
   - `beforeCommit` 发“已 claim”信号并挂起（此时 `withWrite` 持 mutex、Room 事务未提交）；
   - 另一协程 `barrier.withExclusive` 只能等待：200ms 后维护体未进入、`barrier.isReady()` 仍为 true；
   - 释放后旧事务原子提交（消息 + SEND 决策 + 机会 `SENT` 同时可见），恢复随后才进入维护体；
   - 通知 claim 第一次 true、第二次 false；恢复按生产语义替换数据库（测试用 `clearAllTables` 近似）后，消息/决策/机会均为 0，旧 decision 不能再 claim。
6. `staleGenerationWriteAfterRestoreIsRejectedAndRowsUnchanged`（c，栅栏级）：先 `withExclusive` 完成恢复（gen+1，`setReady(true)` 模拟 Gate 放行），再用旧 generation `withWrite` 提交 → `RestoreException(RESTORE_STALE_RESULT)`，block 未进入；预置的 1 消息/1 决策/1 机会行数与状态完全不变，`notified_at` 仍为 null。
7. `runnerThatCapturedOldGenerationCannotClaimOrCommitAfterRestore`（c，Runner 级）：用 `restoreGeneration = { oldGeneration }` 模拟“恢复前捕获旧代次、恢复后才走到提交点”的 Runner；真实 `runOnce` 完成了 ensure 与模型调用，在 `writeBarrier.withWrite(oldGeneration)` 处抛 `RESTORE_STALE_RESULT`；机会仍是 `PENDING`（claim 未执行），消息/决策 0，无 `notified_at`。

### 关于 b) 的锁语义
`RestoreWriteBarrier.withWrite` 在整个事务期间持有 mutex；`withExclusive` 只有在写锁释放后才能进入并 generation+1。因此“恢复在 claim 与 append 之间完成并使旧事务回滚”在现有锁语义下**不可构造**（会等待自身）。Round 12/13 里“在 hook 内启动并完成恢复”的计划据此修正为：
- b) 证明恢复无法插入事务内部、旧事务要么完整提交、要么被拒绝；
- c-1/c-2 证明恢复完成后的旧 generation 提交被 `RESTORE_STALE_RESULT` 拒绝且零 DB 副作用。
这是同一 Runner 路径的等价证明（`withWrite` 是唯一写入口，拒绝发生在进入事务/claim 之前）。

### 命令与实跑结果
```
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# BUILD SUCCESSFUL

adb -s 127.0.0.1:37523 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:37523 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s 127.0.0.1:37523 shell am instrument -w -r \
  com.mengyalife.app.test/androidx.test.runner.AndroidJUnitRunner
# OK (15 tests)

adb ... am instrument -w -r -e class com.mengyalife.app.care.CareRunnerFenceTest ...
# OK (7 tests)
```
- JVM：358 tests / 0 failures / 0 skipped。
- 设备：15 tests（原有 8 + 新增 7），0 failure。
- lint：`:app:lintDebug` BUILD SUCCESSFUL（报告 `app/build/reports/lint-results-debug.html`）。

### 残留风险
- `careRepository.ensure(drafts)` 的候选发现不是 generation 栅栏写：若恢复在 snapshot 期间完成，本地可能留下一条 `PENDING` 机会（用例 3 明确断言了该行为）。它不产生消息/决策/通知，且 dedupeKey 防重复；但严格说它是恢复后旧上下文的本地残留，后续可考虑加 generation 守卫。
- 通知 claim/post 在 `CareWorker` 中仍位于 `runOnce()` 返回之后、栅栏之外：用例 5 在恢复维护体进行期间 claim 成功（并只成功一次），说明“恢复开始得晚于事务提交”时系统通知仍可能在维护期间被投递。DB 侧 at-most-once 成立，但通知投递与恢复仍非跨系统原子。
- b) 的“替换数据库”用 `clearAllTables` 近似生产恢复（导入备份表）；不是对真实备份恢复链路的端到端覆盖（A 组已单独覆盖）。
- 崩溃窗口（消息已写、决策未写）仍是 UNKNOWN/不重发，属既有设计，不在本轮交错范围。
