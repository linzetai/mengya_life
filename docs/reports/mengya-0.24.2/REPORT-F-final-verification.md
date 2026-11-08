# Gate F
- 352 JVM：1 FAIL（WeeklyAiTest，既有周报 factIds 校验；本轮未改周报代码），351 PASS。
- Debug/QA 构建待最终命令。
- 真机：本轮未跑 0.24.2 设备回归。
- 结论：CONDITIONAL/BLOCKED：A1 fixture、D 事务/栅栏挂起测试、B 迁移 instrumented、C 真机、E 新鲜 BUSY 未完成；且存在 1 个非本轮代码的 JVM 失败需先定位。

## Round 1 更新
- JVM：352 tests PASS / 0 FAIL。
- 修复 WeeklyAiTest 测试时钟误传（trailing lambda 给了 beforeComplete，导致按系统周计算）；改为显式 now={now}，非生产代码问题。
- 未变：A1 旧 fixture、D 事务/挂起测试、B 迁移 instrumented、C 真机、E 新鲜 BUSY 未完成。

## Round 2
- JVM 356 tests PASS / 0 FAIL（新增 RestoreCompatibilityTest 4 项）。
- A1/A5/A6 设备 fixture 未做；Gate D 事务/挂起测试未做。

## Round 3
- 新增迁移 instrumented 测试但未运行：设备掉线（port 44435 connection refused）。
- JVM 356 PASS（上轮）；本轮无 JVM 变更。

## Round 4
- 设备恢复（37523），instrumented `OK (7 tests)`：3 DAO/claim + 2 崩溃点 + 2 迁移（21→22、22→23）。
- 仍缺：A1 旧 fixture、Gate D 事务/挂起、Gate E BUSY/TIME。

## Round 6
- JVM：357 tests PASS / 0 FAIL（新增旧格式 fixture）。
- A1 以编码器版本开关生成 fixture（非真实 0.24 加密文件）；Gate D 事务仍未重构。

## Round 7
- Gate D 事务/屏障实现；357 JVM PASS；Debug/QA/lint PASS。
- 未跑：恢复挂起点设备测试、通知跨系统原子验证。

## Round 8
- 设备 instrumented `OK (8 tests)`（新增事务回滚）。
- 仍未：A5/A6 设备恢复、D 恢复挂起点、C 真机跨周、E TIME。

## Round 9
- A6 设备失败注入：原库计数不变、Key 保留。
- 仍缺 A5 旧格式设备预览、D 恢复挂起点、C 真机。

## Round 10
- A5 设备 legacy 恢复 PASS（record_proposals=0，偏好/Key 保留）。
- 修复可选表哈希校验过严。
- 仍缺：D 恢复挂起点交错、C 真机跨周、E TIME。

## Round 11（Gate D Runner 级恢复栅栏 + 最终验证）
- 新增 `app/src/androidTest/java/com/mengyalife/app/care/CareRunnerFenceTest.kt`：7 个真实 Room + 真实 Care 仓库的 instrumented 用例，覆盖恢复前、模型前、模型后、claim 后、恢复后旧 generation 提交（栅栏级 + Runner 级）。细节见 `REPORT-D-restore-fencing.md` Round 14。
- 生产代码未改：仅使用 d92241e 已有的 `beforeCommit`（默认 null）与 `restoreGeneration` 读口。
- JVM：`./gradlew :app:testDebugUnitTest` → 358 tests / 0 failures / 0 skipped（57 个测试类 XML）。
- 构建与 lint：`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` → BUILD SUCCESSFUL；lint 报告 `app/build/reports/lint-results-debug.html`。
- 设备（adb 127.0.0.1:37523，debug + androidTest APK 均 `Success` 安装）：
  - 全量 `am instrument -w -r com.mengyalife.app.test/androidx.test.runner.AndroidJUnitRunner` → `OK (15 tests)`；
  - 定向 `-e class com.mengyalife.app.care.CareRunnerFenceTest` → `OK (7 tests)`。
- Gate D 结论：恢复栅栏的 Runner 级证据已补齐；claim 后恢复无法插入事务内部（锁语义），以“恢复等待 in-flight 事务 + 恢复后旧代次提交被 RESTORE_STALE_RESULT 拒绝且 DB 零副作用”作为等价证明，已在报告中说明。
- 剩余风险（不阻塞本轮 D）：候选 `ensure()` 未加 generation 栅栏；`CareWorker` 的通知 claim/post 仍在栅栏之外（DB at-most-once 成立，但与恢复非跨系统原子）；b) 用 `clearAllTables` 近似备份替换。详见 Gate D 报告。

## Round 15
- C 真机：周日 20:00 SEND，reason 引用偏好时段。
- 仍缺 E TIME 运行中变化；其余 A/B/C/D 主用例已闭合。

## Round 15
- E4 修复：QA 时钟统一注入 CareRepository，预算/冷却口径一致。
- 最终：358 JVM、15 instrumented、Debug/QA/lint PASS。

## 审查修复（M1/M2）
- M1：MIGRATION_22_23 改为派生表 GROUP BY 主题键 + NOT EXISTS，避免 1 feedback + 2 decisions 写重复 SILENCED；迁移测试加入 SCHEDULE+SEND 两条决策并断言 1 条偏好；设置页恢复改为按 topicKey 撤销。
- M2：移除无候选的硬时段门与自证测试；TIME 明确为软偏好（提示词），不拦截无关关心。
- 设备 instrumented 复跑：OK (15 tests)。
- 其余 MINOR/NIT 作为残留风险记录在 REPORT-D/E（非阻塞）。
