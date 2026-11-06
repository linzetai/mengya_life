# G4A-2B P0 修复与验收收口报告

> Base HEAD：`3fb6781b955163994d891b98ff5f1d12c30692fc`；P0实现提交：`472524652aae1bf9e1d68f9a36bc45e9030f7ed3`。
> 最终状态：两项P0已修复；34225真机L2部分通过后用户暂停，R7 setup失败且余下项NOT RUN；整体Gate **NOT PASS**。

## 1. P0 修复

### 恢复启动/普通写入竞态

- 新增共享 `RestoreWriteBarrier`：
  - startup 默认 UNKNOWN/blocked；协调器收敛后才 READY；
  - 普通写持有 barrier permit 完成全部 DB 写；恢复/启动 Reconciliation 获得 exclusive permit；
  - exclusive generation 递增；排队中的旧写/迟到 AI 结果以旧 generation 拒绝；
  - initial UNKNOWN/RECONCILING/MAINTENANCE 下新写立即拒绝，不排队到 Gate 释放后执行。
- 接入 Journal/Goal/Checkin/GrowthSignal/GrowthMemory/Proposal/Conversation/Insight/Diary/WeeklyReview/WeeklyAI，以及 consent、AI config、theme、weekly preferences。
- Worker 将整个 DB/通知工作包在 barrier 内；Gate 未 READY 时返回 retry，不误报成功。
- 恢复交易与 Reconciliation 使用同一 exclusive barrier；Gate 文件状态不是唯一内存依据。
- DB/app_meta读取异常与无恢复记录分开；DB不可读、meta缺行、generation/attempt不匹配均 fail-closed。
- 修复首次恢复 snapshot 预检目录不存在：StatFs改查最近存在的父目录，并记录空间不足为可诊断的pre-commit状态。
- 恢复exclusive permit允许授权的convergence内部DataStore写；普通协程在Gate/maintenance仍立即被阻断。
- Gate/Journal 写入执行文件 fsync、原子 rename、父目录 fsync；Gate 删除失败抛错；收敛标记回读验证后才释放 Gate。

### QA 夹具与失败脚本

- B 变体逐表制造 10/10 表差异；恢复后按核心数据/weekly派生结果分别比较。
- 失败脚本拆分 `beforeB` 与 `expectedA`；R2 比较完整 10 表，R3-R7 比较预期 A 的核心 8 表；weeklyAI明确要求不恢复。
- 增加 durable `DB_COMMITTED`/`CONSENT_DONE` 阶段标记，用确认阶段后 force-stop 替代固定 sleep。
- 失败时收集DB/app_meta/Gate/Journal/QA状态/UI树/脱敏 logcat；self-test 使用同一表比较器证明错误预期被捕获。
- fresh v12 Room `onCreate` 初始化 app_meta；QA seeder等待 READY，避免启动竞态。

## 2. 测试运行时间优化

旧 L2 broad 命令耗时 **2043.5s（34.1分钟）**，额外强制选择通用 `verify_g3b1.py`、迁移、导航、设置页；设置页当时失败。

已实现 focused selection：`restore-validate` 在 integration 层跳过泛用 `ai-consent` 与导航冒烟，保留 v12迁移及恢复专用脚本；E2E内包含预览校验，不重复跑单独 preview。dry-run 选中：migration + restore E2E + R2-R7 + fresh DB。

设置脚本从全页多轮dump改为 tag定位/单次滚动，单独实测 **38/38 PASS，112s**（旧失败运行452.1s）。

Port 34225 实际启动 combined L2（restore-validate + weekly-ai），估算约28–29分钟。约23分钟后用户要求暂停：已完成 QA 前置、迁移、g3b1、E2E和R2-R6；R7 setup断言失败，fresh DB/weekly smoke未执行。Runner被SIGTERM，**没有总报告；本轮L2标记INCOMPLETE**。

## 3. 最终可执行证据

| 项 | 结果 |
|---|---|
| 全量 JVM + 静态 | 历史 **299/299 PASS**（final两项修复前）；最终源码 restore-validate L0 **6类/24 tests PASS，19.4s** |
| SnapshotSpace/Barrier单测 | PASS（包含“目录尚未创建时查父目录”“exclusive内部写允许”） |
| QA/debug APK构建 | PASS；QA `2951bdf6...`，Debug `439565fa...` |
| Port 34225 QA前置 | **11/11 PASS** |
| v10/v11→v12迁移 | **26/26 PASS** |
| g3b1授权/隐私 | **47/47 PASS** |
| 最终代码 A→B→A E2E | **39/39 PASS，379.77s** |
| R2-R6故障/崩溃矩阵 | **逐项PASS**；包含R3收敛和R4-R6重启路径 |
| R7 Gate缺失 | **INCOMPLETE**：启动B场景断言失败（B夹具10表已存在）；R7恢复断言未跑完 |
| Fresh DB / weekly L2 smoke | **NOT RUN**：用户暂停在R7 setup之后 |
| Combined L2整体 | **INCOMPLETE / NOT PASS**：约23分钟后SIGTERM，无最终report.json |

## 4. 剩余P0与风险

**严格验收尚未关闭**：最终源码已在设备上通过E2E、迁移及R2-R6，但R7 setup断言失败、R7主体及fresh/weekly用例未执行。当前不应重跑整套长L2；应先分析R7 `wait_app_ui()` 假阴性/启动条件，再只复验R7和剩余用例。

其他待补：

- DB读取异常/设置写失败/Worker中Gate/UNKNOWN写阻断的专项设备注入；代码有 fail-closed，最终设备未测。
- 保护快照可重读的最终 build 设备复验。
- 大数据量性能与七组全量回归（本轮不跑）。

## 5. APK

| 包 | SHA256 |
|---|---|
| QA | `2951bdf6d48b101e372082464627b6425b3447a092dc156375d82d04a68e852b` |
| Debug | `439565fad056ae5f0fc6e8550fb3810ee75fc4ced0df212aea30fc502982ebfd` |

## 6. 后续最小验证命令

本轮按用户要求已暂停，不再执行设备操作。恢复验收时不要整套重跑：先分析/修复 R7 setup 等待条件，增加可单独运行的 R7 case；然后只跑 R7、fresh DB、weekly smoke。已通过的 E2E、迁移、R2-R6可引用本次部分日志，不要重复跑。

本次原始记录：`docs/reports/workflow-optimization/runs/20261106-111828-partial-l2/`。

## 7. 结论

已修复 snapshot空间预检路径与exclusive收敛写阻断，并由真实E2E及R2-R6验证通过。R7 setup失败、fresh DB/weekly未跑且用户暂停设备；**最终Gate仍 NOT PASS**。QA包可能保留部分假数据；生产包及生产数据未触碰。
