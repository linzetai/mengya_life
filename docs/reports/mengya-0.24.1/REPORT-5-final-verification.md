# Gate 5 最终验证（0.24.1）

## 通过
- JVM：350 tests PASS / 0 FAIL。
- Debug/QA 构建成功。
- 真机 DB 21→22 迁移成功（user_version=22），Key 保留。
- 真机备份→恢复往返成功：`恢复完成，已保留本机 Key 与授权`；恢复后 record_proposals=12、care_preferences=4，记录候选与偏好均保留。
- 修复恢复候选表排序：record_proposals 按 created_at,id 校验，摘要一致。
- SENDING 收敛 UNKNOWN（0.24 真机 + 0.24.1 代码）。
- 偏好解析：否定/反问/恢复/局部忙碌 JVM 覆盖。

## 仍未执行/阻塞
- CareRunner+真实 DAO 集成测试、Worker 连续/并发/崩溃注入。
- 通知幂等与“稍后/不再提醒”0.24.1 真机回归。
- Room migration instrumented（仅真机启动验证）。
- lint。

## 结论
**CONDITIONAL**：代码与备份链路已补齐，关键集成/通知回归仍缺证据，不判 PASS。

## 追加真机证据（0.24.1）
- 周日 20:00 测试时钟：SEND 决策生成，时间偏好窗口内通过；22:42 系统通知出现一次。
- notified_at 在通知后写入；连续 3 次 qa_care_now Worker 后该字段未变化，未再产生新的通知事件（截图 notification-once.png）。
- 局部 BUSY 修复：BUSY(plan:5) 不再阻断无关目标候选。
- QA 测试时钟下 decision.created_at 为真实时间，sentSince(testDayStart) 会低估已发送数；仅影响 QA 时钟，生产同一时钟不受影响，记为已知测试环境风险。

## 追加：lint 检查
- `./gradlew :app:lintDebug` FAILED：`NonNullableMutableLiveDataDetector` 抛 `IncompatibleClassChangeError`，lint 工具链与当前 AGP/依赖不兼容，未产出可读报告。
- 结论：lint 为 BLOCKED（工具原因），不能计为通过；需单独修 lint 工具链后重跑。
