# G4A-2B 验收：完整备份恢复闭环

## 1. Gate 状态

**最终代码：G4A-2B Gate NOT PASS。** port 34225 上的final combined L2部分执行：E2E 39/39、迁移26/26、R2-R6通过；R7 setup断言失败后用户暂停，整体INCOMPLETE。以 `G4A2B-CLOSURE-ACCEPTANCE.md` 为最终状态。

旧版本曾有 E2E 35/35、R2-R7 59/59；这些不是当前源码证据。当前partial logs位于 `workflow-optimization/runs/20261106-111828-partial-l2/`；fresh DB/weekly smoke未跑。

## 2. 已完成的主链路证据

| 项 | 结果 |
|---|---|
| A 数据 | 10 表 23 行 QA 假数据 + API Key/consent/自动发送 |
| A 导出 | 真实 SAF v2 加密文件 |
| B 数据 | 同表数不同正文（B 变体） |
| 恢复 A | 真实 SAF 选择、口令、预览、风险勾选、确认、事务 |
| 核心 8 表 | A 逐字段恢复（journal/goals/checkins/messages/proposals/signals/memories/insights） |
| weekly_reviews | 恢复后存在；重启后按本地规则重算（派生数据） |
| weekly_ai_reviews | **不恢复**，恢复后 0 行 |
| 安全重置 | QA evidence `keyPresent=false`、`consentGranted=false`、`aiEnabled=false` |
| 保护快照 | 存在且 key/artifact 可重读（随机口令由独立 Keystore alias 保护） |
| app_meta | attempt/backup/generation/settings/expected/payload/converged 均写入；重启后仍在 |
| Gate | 成功后释放；重启无 Gate |
| 重启 | 核心 8 表 A 保持，应用可正常启动 |
| 结果 | **35/35 PASS，346.84s** |

证据：`G4A2B-acceptance/qa/e2e-output.txt`、`db-{A,B,AFTER,AFTER2}.json`、`app_meta-AFTER2.json`。

## 3. 事务与迁移

- DB v12，`MIGRATION_11_12` 新增 `app_meta`；真机启动后 `PRAGMA user_version=12`，app_meta 9 列存在。
- `RestoreTransaction` 在同一 Room 事务内：
  - 删除 10 表（weeklyAI 也清空）；
  - 按父子顺序插入 9 表，保留主键；
  - 写入 app_meta；
  - 重新读取规范化数据，逐表摘要 + 总摘要校验；
  - 失败抛异常回滚。
- 转换规则：ANALYZING→FAILED、contextGoalId 挂起→null、signal/memory 历史挂起→STALE；weeklyAI 不写回。
- 事务后摘要 != payloadSha256；payload 原始摘要、expectedRestoredSha256、实际摘要分开。

## 4. Gate/Journal/Reconciliation

已实现：
- `RestoreGate`（noBackupFilesDir，atomic write）；
- `RestoreJournal`（仅辅助，不作为提交依据）；
- app_meta attempt 为提交唯一依据；
- 启动 `reconcileOnStartup` 覆盖：Gate 存在未提交、已提交、Journal PREPARED、Journal 丢失、Gate 丢失、Generation 异常、DB 不可用；
- 提交后安全 reset 失败保留 Gate；
- 成功后释放 Gate；
- Gate 活跃时 UI 维护页、Worker 入口阻断、AI 写回阻断（conversation/insight/diary/weekly hooks）；
- AI 自动发送强制关闭。

## 5. 测试

| 命令 | 结果 | 耗时 |
|---|---|---|
| 全量 JVM（barrier前历史版本） | **299/299 PASS** | 历史记录 |
| 最终 restore-validate L0 | **6类/24 tests PASS** | 19.4s |
| `verify_restore_e2e.py`（最终修复后） | **39/39 PASS** | 379.77s |
| 迁移 v10/v11→v12 | **26/26 PASS** | 真机 |
| L2 g3b1 授权/隐私 | **47/47 PASS** | 真机 |
| R2–R6 故障/崩溃场景 | **逐项PASS** | 最终combined L2部分执行 |
| R7 / combined L2 | **INCOMPLETE**；用户暂停前R7 setup断言失败 | 约23分钟后SIGTERM |

## 6. 失败/崩溃矩阵

| ID | 场景 | 状态 |
|---|---|---|
| R2 | 事务中注入失败，回滚、旧 Key/consent 保留 | **PASS** |
| R3 | DB提交后Key删除失败，Gate保持、重启收敛 | **PASS** |
| R4 | DB提交后、journal前崩溃 | **PASS** |
| R5 | 安全重置中途崩溃 | **PASS** |
| R6 | Journal缺失/损坏 | **PASS** |
| R7 | Gate缺失/损坏 | **INCOMPLETE**：B setup断言失败后用户暂停，恢复断言未跑 |
| R8/R9 | 强引用拒绝/历史悬挂 | JVM 已覆盖；设备未跑 |
| R11-R13 | Key/consent/自动发送 | **PASS**（E2E qa-after） |
| R14/R15 | 旧 AI 请求/周 AI 预算 | 代码有 gate 阻断；设备未专项 |
| R16/R17 | 重复点击/重复恢复 | Gate+mutex 有代码；设备未专项 |
| R18/R19 | Worker/重启 | Worker gate 代码；重启 E2E PASS |
| R20 | SAF/口令/预览/二次确认/恢复 | **PASS**（E2E） |

## 7. 未覆盖/风险

1. R7尚未完成；这是完整Gate的阻断项。B fixture 10表已在库，但 `start()` 的UI readiness断言失败，需修稳后单独复测。
2. combined L2在用户要求暂停时SIGTERM，fresh DB与weekly AI smoke未执行，无runner总报告。
3. DB不可读、Worker并发/通知、设置收敛失败等专项目标未在本次设备矩阵覆盖。
4. QA数据清理步骤未运行，QA包可能残留测试假数据；正式包/生产数据未触碰。
5. 真实大数据量/七组全量回归未做。
6. 成功提示依赖设置页滚动/RestoreResultHost；Toast 已加，但设备最终版未复验。

## 8. 结论

**主链路E2E与R2-R6已在最终APK通过；R7 setup及额外Worker/DB故障注入仍未完成，因此Gate保持 NOT PASS。** 不进入G4A-2C，等待用户另行安排剩余R7验证。
