# MengYa Life 0.25.1 — 可靠性收口验收

> 基线：`master` / `9b581b1`。本轮没有新增业务功能或 UI 重构。状态只按本轮可复现证据判定。

## 代码变更

- CareRunner 的完整 `engine.chat` 调用现在持有共享 `RestoreWriteBarrier`，并绑定请求开始时的 restore generation。UNKNOWN / RECONCILING / MAINTENANCE 下不启动请求；恢复请求与在途模型请求通过同一互斥栅栏串行化。
- Key 删除失败分支立即调用 `finishMaintenance()` 并写 QA 状态，startupState、writeBarrier、Gate/UI 维护态保持 fail-closed 一致。已审查其他 `COMMITTED_NEEDS_CONVERGENCE` / `MAINTENANCE` 返回分支，未发现同类遗漏。
- `CareDeliveryCoordinator.deliver()` 在一个栅栏临界区中执行通知认领和实际 post；Worker 与 ViewModel 已改用该入口。
- QA 构建的 Key 删除故障注入已接入 `deleteKeyConfirmed()`，可用于后续真机验证。
- Instrumented CareRunner 测试补充了 barrier 未 ready 时不发起模型调用，以及模型调用期间恢复等待、再拒绝旧代结果的 Room 交错用例；恢复失败用例改为独立协程模拟并发恢复；QA test clock 每例归零，避免设备共享偏好污染。
- `testBuildType 'qa'` 将 instrumented target 固定到隔离 QA 包，避免触达正式包数据。
- 修正 `0.25/VERIFICATION.md` 结论处将 instrumented 同时记作 PASS 与 BLOCKED 的矛盾。

## 验收结果

| 项 | 状态 | 证据 / 限制 |
|---|---|---|
| `git diff --check` | **PASS** | 本轮源码补丁无空白错误 |
| JVM `:app:testDebugUnitTest` | **PASS** | 使用项目 SDK 环境脚本后，**385/385**（0 failures / 0 errors / 0 skipped）。 |
| Instrumented `:app:connectedQaAndroidTest` | **PASS** | 小米真机 Android 17，**20/20**（0 failures / 0 errors / 0 skipped）；XML：`app/build/outputs/androidTest-results/connected/qa/TEST-2509FPN0BC - 17-_app-.xml`。只运行 QA target，未安装/操作正式包。 |
| `:app:assembleDebug` | **PASS** | 使用 `/opt/android-sdk/env.sh` 配置的 `/opt/android-aapt2/aapt2` 构建成功。 |
| `:app:assembleQa` | **PASS** | 使用项目 SDK 环境脚本构建成功。 |
| `:app:lintDebug` | **PASS** | lintDebug BUILD SUCCESSFUL：0 errors / 39 warnings。 |
| QA 提交后 halt → 重启收敛（同 endpoint） | **PASS** | halt 标记与 Gate/journal 在重启前存在；重启后 attempt `5bef2b5e…` generation 8 收敛：Key=true、consent=true、AI=false、gateActive=false，Gate/journal 已清除，snapshot 存在。 |
| QA 提交后 halt → 重启收敛（跨 endpoint） | **PASS** | 重启后 attempt `85761830…` generation 9：Key=false、consent=false、AI=false、gateActive=false，Gate/journal 已清除，snapshot 存在。 |
| Key 删除失败 → 维护态 → 故障解除重启收敛 | **PASS** | 故障阶段 `COMMITTED_NEEDS_CONVERGENCE`：Key=true、consent=true、gateActive=true；Gate=`SETTINGS_PENDING` 且 `hadKeyAtCommit=true`。移除注入并重启后 attempt `8ae8a441…` generation 10：Key=false、consent=false、AI=false、gateActive=false，Gate/journal 清除；只读查询 `app_meta.restore_converged_at=1794497066788`。`finishMaintenance()` 同步置 startupState=MAINTENANCE、writeBarrier not-ready；UI 订阅同一 `gateActive` 显示维护页；未单独保存 UI 截图。 |
| SAF 真实备份预览/确认流程 | **BLOCKED** | 系统 SAF 选择器未通过用户界面确认；本轮用已有 QA 入口验证底层链路，不把真实 SAF 流程记为 PASS。 |
| APK SHA256 | **PASS** | Debug `106ed569bc1d18dc8910c48c2dcdd401d588ac604caeb1bd2bb78cf7fa7a0f88`；QA `cb87cf00b79d2671ebc3293ac6effd63af26b7596065b6f32c21b6f3771e351f`。 |

## 构建与 ADB 环境复核

- 正确的构建/测试入口是从仓库根目录执行 `. ./env.sh`；当前环境在 `PREFIX` 未设置时转用 `/opt/android-sdk/env.sh`，由它指定兼容 API 35 的 `/opt/android-aapt2/aapt2`。此前第一次验收误用系统 `/usr/bin/aapt2`（2.19），导致错误地把资源链接失败判成工具链阻塞；本报告已按正确脚本重跑并更正状态。
- 仓库 [README 第 8 节](../../../README.md#L207-L218) 规定 `DEV=<手机IP>:<无线调试端口>` 后执行 `adb connect "$DEV"`；历史 [Gate 0 构建报告](../REPORT-G0-Android-Build.md#L253-L262) 记录曾连接 `127.0.0.1`。本轮最终设备地址为 `127.0.0.1:44211`。同一设备也曾同时通过 `198.19.0.1:44211` 暴露为第二个 adb serial；仪器测试前断开重复 alias，避免同一手机被并行测试两次。
- UTP 的测试后清理要运行 build-tools 内 x86 `aapt dump badging`，本机无法执行；最终 run 临时将该路径指向 ARM64 `/usr/bin/aapt`，测试通过后恢复原 SDK 文件。instrumented XML 记录 20/20 PASS。

## 并发保证与剩余边界

- AI 出站：CareRunner 在 `withWrite(boundGeneration)` 内调用 `engine.chat`；restore 的 `withExclusive` 必须等待已开始的请求完成。请求释放后，恢复可先取得栅栏并推进 generation，旧模型结果再经 generation 检查被丢弃。生产容器向 CareRunner 注入共享 barrier；未注入 barrier 的独立测试构造不具备跨恢复互斥保证。
- 通知：`claimNotification` 是数据库层最多认领一次；`deliver` 将认领和调用通知 post 放进恢复栅栏，保证两者执行期间恢复不能进入维护态。Android 系统通知与 Room 不存在跨系统事务，进程崩溃时仍可能已认领但未成功显示；这不是 exactly-once 或用户必达保证。
- 同 endpoint / 跨 endpoint / 删除失败后的启动恢复状态与凭据最终值，本轮没有真机证据，不作 PASS 声明。

## 提交与产物

- 最终 commit SHA：见本轮验收回复（本报告与实现一并提交）。
- Debug / QA APK SHA256：见验收结果表；均为本轮代码构建产物。
