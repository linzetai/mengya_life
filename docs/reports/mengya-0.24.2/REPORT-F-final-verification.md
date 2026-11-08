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
