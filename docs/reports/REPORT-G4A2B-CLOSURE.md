# G4A-2B P0 修复与验收收口报告

> Base HEAD：`3fb6781b955163994d891b98ff5f1d12c30692fc`；P0实现提交：`472524652aae1bf9e1d68f9a36bc45e9030f7ed3`；现场复测修复提交：`63e49ad`。
> 最终状态：旧34225 combined L2被用户暂停；经独立case补测，当前QA SHA `c2fcdfc0…`上的E2E、R2-R7、Fresh、Weekly、DB不可读、migration与g3b1均有PASS证据。由于Coordinator Gate.clear专门故障注入及WorkManager attempt状态未直接观测，最终仍为 **NOT PASS**，不宣布关闭。

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

Port 34225 combined L2（restore-validate + weekly-ai）运行约23分钟后用户要求暂停：preflight、迁移、g3b1、E2E及R2-R6通过，R7 bulk setup断言失败，且当时无combined report。用户随后给出port 34707；startup Gate修复构建后在同一QA SHA c2fc上独立完成E2E、R2-R7、fresh v12、weekly READY、DB不可读及g3b1。各用例可按SHA归并，不要求重跑迁移以生成combined report。

## 3. 最终可执行证据

| 项 | 结果 |
|---|---|
| 全量L0 | **303/303 PASS**；静态/映射校验0 issues，run 20261106-174143 |
| QA/Debug APK | 当前SHA：QA `c2fcdfc0…`；Debug `a77858ad…`；统一表 `docs/reports/G4A2B-final-acceptance/SHA256SUMS` |
| QA前置 | 当前QA SHA c2fc：**11/11 PASS**（run 20261106-164126）；另有前台切换导致的一次UI preflight超时，目标用例未开始 |
| v10/v11→v12迁移 | 当前QA SHA c2fc：**26/26 PASS** |
| g3b1授权/隐私 | 当前QA SHA c2fc：**47/47 PASS** |
| A→B→A E2E | 当前QA SHA c2fc：**39/39 PASS，198.30s** |
| R2–R7故障/崩溃 | 当前QA SHA c2fc：R2 12/12、R3 19/19、R4/R5/R6各15/15、R7 15/15 PASS |
| Fresh v12 / Weekly READY smoke | 当前QA SHA c2fc：Fresh 6/6、Weekly 22/22 PASS |
| DB不可读startup | 当前QA SHA c2fc：维护页/无业务与AI入口/业务行不变，12/12 PASS |
| 设置收敛失败 | 当前QA SHA c2fc：20/20 PASS；Gate保持，重启后收敛 |
| Combined L2整体 | 未生成combined report；按用户许可汇总独立case，但Gate.clear专用注入及WorkManager attempt记录仍不足，整体 **NOT PASS** |

## 4. 剩余P0与风险

**严格验收尚未关闭（NOT PASS）**：当前QA SHA c2fc的Fresh、Weekly READY、DB不可读、E2E、R2-R7、g3b1及迁移均有独立PASS；用户允许按同一版本聚合，不要求combined runner报告。但以下两项没有直接运行证据：Coordinator内部`gate.clear()`故障注入后仍维护态；WorkManager WorkSpec attempt/retry状态（业务DB无副作用及Worker Barrier单测通过）。此外，`MengyaApplication`在reconcile后仍无条件sync调度器，可能更新WorkManager内部状态；Worker外层Barrier能阻断业务写/AI/通知。

其他未覆盖风险：

- 最终build的保护快照可重读专项设备复验。
- 大数据量性能与七组全量回归（本轮明确不跑）。

## 5. APK

| 包 | SHA256 |
|---|---|
| QA | `c2fcdfc0d50b251c286291f483d52eade2845491cee446b2c925660906feaab1` |
| Debug | `a77858ad0a84133df9dc2ffeca7cbdc5fd9e015b1dd3fa7a3ca642dad2d829f3` |

## 6. 后续最小验证命令

34225 combined L2的原始记录仍保存在`docs/reports/workflow-optimization/runs/20261106-111828-partial-l2/`。随后按独立case补测Fresh/Weekly/DB不可读/E2E/R2-R7/g3b1/迁移；逐条日志见`docs/reports/G4A2B-final-acceptance/logs/`。未为生成combined report而重跑整套历史matrix。

## 7. 结论

P0恢复修复和当前APK核心矩阵均通过；当前不宣布关闭，因Gate-clear coordinator直接故障注入及WorkManager attempt状态尚未直接观测，且startup维护期调度器仍会更新WorkManager内部状态。最终判定 **NOT PASS**。最终 QA 包清理已通过，生产包与生产数据未触碰。
