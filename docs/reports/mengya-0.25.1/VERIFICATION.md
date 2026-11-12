# MengYa Life 0.25.1 — 可靠性收口验收

> 基线：`master` / `9b581b1`。本轮没有新增业务功能或 UI 重构。状态只按本轮可复现证据判定。

## 代码变更

- CareRunner 的完整 `engine.chat` 调用现在持有共享 `RestoreWriteBarrier`，并绑定请求开始时的 restore generation。UNKNOWN / RECONCILING / MAINTENANCE 下不启动请求；恢复请求与在途模型请求通过同一互斥栅栏串行化。
- Key 删除失败分支立即调用 `finishMaintenance()` 并写 QA 状态，startupState、writeBarrier、Gate/UI 维护态保持 fail-closed 一致。已审查其他 `COMMITTED_NEEDS_CONVERGENCE` / `MAINTENANCE` 返回分支，未发现同类遗漏。
- `CareDeliveryCoordinator.deliver()` 在一个栅栏临界区中执行通知认领和实际 post；Worker 与 ViewModel 已改用该入口。
- QA 构建的 Key 删除故障注入已接入 `deleteKeyConfirmed()`，可用于后续真机验证。
- Instrumented CareRunner 测试补充了 barrier 未 ready 时不发起模型调用，以及模型调用期间恢复等待、再拒绝旧代结果的 Room 交错用例；JVM 测试覆盖通知 claim/post 与恢复的互斥。
- 修正 `0.25/VERIFICATION.md` 结论处将 instrumented 同时记作 PASS 与 BLOCKED 的矛盾。

## 验收结果

| 项 | 状态 | 证据 / 限制 |
|---|---|---|
| `git diff --check` | **PASS** | 本轮源码补丁无空白错误 |
| JVM `:app:testDebugUnitTest` | **BLOCKED** | 未进入测试编译；AAPT2 默认 x86_64 二进制无法在本机 aarch64 环境启动。尝试本机 `/usr/bin/aapt2` 后，AAPT2 2.19 无法解析 SDK `platforms/android-35/android.jar` 的资源表（`RES_TABLE_TYPE_TYPE entry offsets overlap`）。 |
| Instrumented `:app:connectedDebugAndroidTest` | **BLOCKED** | `adb devices -l` 无设备；`localhost:46067` 连接被拒绝，无法安装或运行 QA/测试包。 |
| `:app:assembleDebug` | **BLOCKED** | 同一 Android 35 / AAPT2 环境问题，停在 `processDebugResources`。 |
| `:app:assembleQa` | **BLOCKED** | 同一 Android 35 / AAPT2 环境问题，停在 `processQaResources`。 |
| `:app:lintDebug` | **BLOCKED** | 同一 Android 35 / AAPT2 环境问题，停在 `processDebugResources`，未生成本轮 lint 报告。 |
| QA 提交后 halt → 重启收敛（同 endpoint） | **BLOCKED** | QA 设备不可达，未执行；未操作正式包数据。 |
| QA 提交后 halt → 重启收敛（跨 endpoint） | **BLOCKED** | QA 设备不可达，未执行；未操作正式包数据。 |
| Key 删除失败 → 维护态 → 故障解除重启收敛 | **BLOCKED** | QA 故障注入已接线，但设备不可达，未实际运行或核对 Key / consent / Gate / app_meta。 |
| SAF 真实备份预览/确认流程 | **BLOCKED** | SAF 选择器无法通过现有自动化驱动；底层 QA 链路亦因设备不可达未执行。 |
| APK SHA256 | **BLOCKED** | 本轮 Debug/QA APK 未能构建，无本轮产物可校验；不沿用旧 APK 的 SHA。 |

## 并发保证与剩余边界

- AI 出站：CareRunner 在 `withWrite(boundGeneration)` 内调用 `engine.chat`；restore 的 `withExclusive` 必须等待已开始的请求完成。请求释放后，恢复可先取得栅栏并推进 generation，旧模型结果再经 generation 检查被丢弃。生产容器向 CareRunner 注入共享 barrier；未注入 barrier 的独立测试构造不具备跨恢复互斥保证。
- 通知：`claimNotification` 是数据库层最多认领一次；`deliver` 将认领和调用通知 post 放进恢复栅栏，保证两者执行期间恢复不能进入维护态。Android 系统通知与 Room 不存在跨系统事务，进程崩溃时仍可能已认领但未成功显示；这不是 exactly-once 或用户必达保证。
- 同 endpoint / 跨 endpoint / 删除失败后的启动恢复状态与凭据最终值，本轮没有真机证据，不作 PASS 声明。

## 提交与产物

- 最终 commit SHA：见本轮验收回复（本报告与实现一并提交）。
- Debug / QA APK SHA256：未生成（见上表）。
