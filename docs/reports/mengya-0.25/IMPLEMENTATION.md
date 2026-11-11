# MengYa Life 0.25 · 实现说明

> 状态口径：本文件只写“做了什么、代码在哪”；是否验收以 `VERIFICATION.md` 的 PASS/FAIL/BLOCKED 为准。

## Gate 0 · Git 同步

| 项 | 结果 |
|---|---|
| `git remote -v` | `origin git@github.com:linzetai/mengya_life.git`（fetch/push） |
| 本地 HEAD | `c25f290d4fbed6ed10810c9cf4f857a8ef5020b4` |
| 远端 `refs/heads/master`（HTTPS `ls-remote` 实测） | `ee20bf881a993f32443cd6ef47462b2ba828bef3` |
| 关系 | 本地领先 2 个提交（`9e2e4b9`、`c25f290`），**无分叉**，可 fast-forward |
| 推送 | ✅ 已推送：`ee20bf8..b1b38cd master -> master`（fast-forward，无 force） |
| 推送后远端 SHA | `b1b38cd0b3cd8471a950b20ca7320d422115248b`（`git ls-remote` 复核一致） |

未执行任何 force push / reset / 重写历史。

## Gate 1 · 可靠性收口

### 1.1 Lint 5 个 error

| 文件 | 原问题 | 修复 |
|---|---|---|
| `domain/care/CarePolicy.kt:22` | `LocalTime.ofInstant`（API 31+） | 改为 `Instant.atZone(zone).toLocalTime()` |
| `domain/care/CarePreferenceParser.kt:149` | `LocalDate.ofInstant`（API 34+） | 改为 `Instant.atZone(zone).toLocalDate()` |
| `data/care/CareRunner.kt:141,176` | 同上 | 同上 |
| `data/weekly/WeeklyReviewNotification.kt:55` | `notify()` 未处理权限被撤销 | 包 `try/catch (SecurityException)`，失败返回 false，不伪造成功 |

未启用 core library desugaring；改用 `java.time` 的 API 26 可用方法，避免引入 desugar 依赖。

新增测试 `app/src/test/java/com/mengyalife/app/care/CareTimeApi26Test.kt`：
免打扰起止边界、时区换算、周结束时间（上海周日 23:59）。

### 1.2 RestoreCoordinator 崩溃重试

原问题：`converge()` 在重试时重新读取“当前 endpoint”再比较。若进程在 DB 提交后、安全收敛前死亡，
重启时当前 endpoint 可能已是恢复后的值，会被误判为“地址未变”，从而保留旧 Key。

修复：

1. `RestoreGateState` 增加并持久化三项意图：
   - `beforeEndpoint`（恢复前本机地址）
   - `targetEndpoint`（备份将写入的地址，来自 `RestorePlan.settings.aiEndpoint`）
   - `hadKeyAtCommit`（恢复前是否有 Key）
2. 在写 `ARMED` Gate（DB 提交之前）时就固化意图；`RESET_PENDING` 继承同一份。
3. `converge()` 只使用 Gate 中的持久化意图（`PersistedCredentialPolicy`），不再读当前 endpoint 做判断。
4. 缺失/不完整意图（旧版 Gate、journal-only 恢复）→ **fail closed**：删除本机 Key 并撤销授权，
   不因“看起来地址没变”而保留凭据。
5. Key 删除失败仍返回 `COMMITTED_NEEDS_CONVERGENCE` 并保持维护态，下一次重试会用同一份意图重试删除。

新增测试：
- `RestoreCredentialPolicyTest`：崩溃重试（当前 endpoint 已变）、缺失意图 fail-closed、同地址保留、空 target 保持配置；
- `RestoreGateJournalTest`：Gate JSON 跨重启保留三项意图。

### 1.3 QA 故障注入

`QaRestoreFaultInjector` 新增 `haltAfterCommitBeforeConverge`，由 `MainActivity` 的
`qa_restore_halt_after_commit` extra 打开（仅 QA 构建）。
注入点在 DB 提交、Gate 写成 `RESET_PENDING`、`app_meta` 确认之后，`converge()` 之前，
用 `Runtime.getRuntime().halt(70)` 模拟进程死亡。

已有注入保留：`failTransaction`、`failKeyDelete`、`failSettings`、`pauseAfterCommitMs`、`pauseAfterConsentMs`。

真机执行情况见 `VERIFICATION.md`（本轮未完成完整设备场景，标记为 BLOCKED 项）。

### 1.4–1.6

- 真实 v2 备份预览/确认流程证据：**BLOCKED**（SAF 文件选择器无法在本机自动化中稳定驱动）；
- instrumented 测试：配置存在（`androidx.test:runner 1.6.2` 等），执行结果见 `VERIFICATION.md`；
- lint 独立统计 error/warning 数量，见 `VERIFICATION.md`。

## Gate 2 / Gate 3

本轮**未实现** Gate 2 的真实成长闭环改造，也未完成 Gate 3 要求的全量真机矩阵；
现状与原因见 `VERIFICATION.md` 与 `REMAINING-ISSUES.md`，不以既有 0.24.3 能力冒充本轮完成。
