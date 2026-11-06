# APK Hash 来源说明

当前文件hash是在启动Gate修复提交后由实际构建产物重新计算；QA与Debug为不同variant，不能互相替代。

| 记录 | QA SHA256 | Debug SHA256 | 结论 |
|---|---|---|---|
| `apk/apk.sha256`（mtime 2026-11-06 09:30） | `a0c0fba9…` | `65223851…` | 早期构建记录，早于11:18 QA APK产物；非当前。 |
| 旧closure报告 | `2951bdf6…` | `439565fa…` | QA `2951`被R7旧case runner明确记录为已安装/reused；`4395`未在当前文件重现，Debug不是QA设备被测包。 |
| 当前构建（startup Gate修复后） | `c2fcdfc0…` | `a77858ad…` | 由本目录 `SHA256SUMS` 对应的本机APK重新hash；当前新版本设备测试以QA `c2fc…`为准。 |

因此差异不是SHA算法或路径问题，而是旧manifest、旧报告与当前重建产物混用了不同构建批次。旧版`2951`和当前`c2fc`不是同一个APK；仅旧R7和旧partial L2日志使用`2951`。`439565fa…`没有保留可复算的APK，无法再证明其确切来源，故标记为stale/intermediate记录，不用于最终验收。
