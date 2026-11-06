# G4A-2B 最终验收状态

## Gate

**NOT PASS（port 34225 的 L2 在 R7 setup 阶段中断）。**

最终QA APK已在设备上跑过preflight、迁移、g3b1、A→B→A E2E及R2-R6；E2E 39/39、R2-R6各断言PASS。R7的B场景启动断言失败后，用户要求暂停；R7剩余断言、fresh DB、weekly L2 smoke没有完成。进程被SIGTERM，runner总报告未生成，不能宣称L2 PASS。

## 状态矩阵

| 类别 | PASS | FAIL / 未完成 | NOT RUN |
|---|---|---|---|
| 全量JVM/静态 | 历史版本 **299/299**；最终改动后的 restore-validate L0 **6类PASS** | 最终改动后未重跑全量299 | — |
| QA前置 | **11/11** | — | — |
| v10/v11→v12迁移 | **26/26** | — | — |
| AI consent g3b1 | **47/47** | — | — |
| A→B→A E2E | **39/39，379.77s** | — | — |
| R2–R6故障/崩溃 | **逐项PASS** | — | — |
| R7 Gate缺失 | — | **B setup断言失败；R7主体未执行** | — |
| fresh v12、weekly L2 smoke | — | — | **用户暂停前未到达** |
| QA集成总结果 | — | **SIGTERM前未生成最终report.json，整体INCOMPLETE** | — |
| DB读取失败/Worker额外并发等注入 | — | — | 未专项设备验证 |

历史真机结果不等于最终 barrier 源码结果。

## 时间优化

旧 broad L2 **2043.5s**。restore-only focused plan为4个设备脚本，预计16–17分钟；本次实际组合 `restore-validate + weekly-ai` 共6个脚本，预计约28–29分钟。运行约23分钟后用户要求暂停，未完成。这个长组合不应作为每轮开发默认动作；日常应使用workflow `dev` profile，合并前再按目标风险单跑standard模块。

设置旧记录38/38、112s；当前脚本加了2个入口可见断言，尚未重新真机计时。

## 工作区/交付

基础P0实现提交 `472524652aae1bf9e1d68f9a36bc45e9030f7ed3`；snapshot空间/Barrier收敛修复提交 `63e49ad`；当前worktree clean。R7 setup修复并按用户同意恢复设备后，只复跑R7及fresh/weekly未完成项，再决定是否PASS。
