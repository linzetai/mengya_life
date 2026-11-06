# G4A-2B P0 修复与验收收口报告

> Base HEAD：`3fb6781b955163994d891b98ff5f1d12c30692fc`；最终提交：`472524652aae1bf9e1d68f9a36bc45e9030f7ed3`（master，worktree clean）。
> 最终状态：P0代码修复与JVM通过；最终源码L1/L2设备验收因QA设备离线**NOT RUN**，最终Gate **NOT PASS**。

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

focused L2 预计约 15–16 分钟（基于迁移26s、E2E约342s、R2-R7约518s、fresh DB及前置/JVM），**本轮未实际完成focused L2，不把估算当实测**。

## 3. 最终可执行证据

| 项 | 结果 |
|---|---|
| 全量 JVM + 静态 | **299/299 PASS**，测试4.1s/总6.9s；报告 `G4A2B-closure/l0/final-report.json` |
| affected modules L0 | 35 类/246 tests PASS（此前运行；见历史日志） |
| settings独立脚本 | 38/38 PASS，112s |
| failure comparator self-test | PASS |
| QA/debug APK构建 | PASS |
| v10→v12/v11→v12迁移真机 | 历史运行26/26 PASS；本轮 barrier 最终代码后未重跑 |
| R2-R7 设备矩阵 | 历史代码 59/59 PASS；本轮 barrier 最终代码后未重跑 |
| A→B→A最终 APK E2E | 历史代码35/35 PASS；本轮 barrier 最终代码后未重跑 |
| focused final L2 | **NOT RUN/未完成**：设备 `127.0.0.1:46511` refused，`10.13.224.241:35193` offline |

## 4. 剩余P0与风险

**严格验收尚未关闭**：同一设备未对 barrier 最终源码运行 focused L2；因此不能证明 R2-R7、迁移、最终APK E2E在最终构建上一致通过。

其他待补：

- DB读取异常/设置写失败/Worker中Gate/UNKNOWN写阻断的专项设备注入；代码有 fail-closed，最终设备未测。
- 保护快照可重读的最终 build 设备复验。
- 大数据量性能与七组全量回归（本轮不跑）。

## 5. APK

| 包 | SHA256 |
|---|---|
| QA | `a0c0fba9246ed3008352bf33f7edfcfc1646de9a7249e298e57a01de7b041b0e` |
| Debug | `6522385171821d932a04f23818365657413e99c44af4f0e6d30473d80bf482b9` |

## 6. 后续最小验证命令

设备恢复后，仅运行 focused L2，不再跑 broad integration：

```bash
DEV=127.0.0.1:<port> ./scripts/verify.sh --module restore-validate --level integration
```

选择器dry-run已归档于 `G4A2B-closure/optimization/focused-plan.txt`。

## 7. 结论

P0代码修复已完成并通过静态/JVM；由于当前设备不可用，最终 L2 没有执行，**Gate 保持 NOT PASS**。本报告不以历史 build 的真机 PASS 替代最终源码验证。
