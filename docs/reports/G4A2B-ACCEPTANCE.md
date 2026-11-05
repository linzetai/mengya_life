# G4A-2B 验收：完整备份恢复闭环

## 1. Gate 状态

**功能主链路 PASS；G4A-2B 最终 Gate：CONDITIONAL / NOT PASS。**

已实际完成 A→导出→B→恢复→A 的 QA 真机恢复（35/35），数据库事务、app_meta、Key/consent 重置、快照、重启可用均通过。
但 **R2/R3/R4/R5/R6/R7 失败与崩溃路径未执行**，L1/L2 的 `verify.sh` 集成也未跑；按“未执行不得报 PASS”，不能把 G4A-2B 标为完整 PASS。

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
| 全量 JVM | **291/291 PASS** | 24.5s |
| `--module restore-validate --level feature`（G4A-2A preview） | PASS（历史） | 292.3s |
| `verify_restore_e2e.py` | **35/35 PASS** | 346.84s |
| `verify_restore_failures.py` | **NOT RUN**（设备断开） | — |
| 迁移 G3F 真机脚本 | **NOT RUN**（仅手动 schema 检查） | — |
| L1/L2 `verify.sh` 完整入口 | **NOT RUN** | — |

## 6. 失败/崩溃矩阵

| ID | 场景 | 状态 |
|---|---|---|
| R2 | 事务中注入失败，回滚、旧 Key/consent 保留 | **NOT RUN**（脚本已写，未执行） |
| R3 | DB 提交后 Key 删除失败，Gate 保持、重启收敛 | **NOT RUN**（脚本已写） |
| R4 | DB 提交后、journal 前崩溃 | **NOT RUN**（脚本已写） |
| R5 | 安全重置中途崩溃 | **NOT RUN**（脚本已写） |
| R6 | Journal 缺失/损坏 | **NOT RUN**（脚本已写） |
| R7 | Gate 缺失/损坏 | **NOT RUN**（脚本已写） |
| R8/R9 | 强引用拒绝/历史悬挂 | JVM 已覆盖；设备未跑 |
| R11-R13 | Key/consent/自动发送 | **PASS**（E2E qa-after） |
| R14/R15 | 旧 AI 请求/周 AI 预算 | 代码有 gate 阻断；设备未专项 |
| R16/R17 | 重复点击/重复恢复 | Gate+mutex 有代码；设备未专项 |
| R18/R19 | Worker/重启 | Worker gate 代码；重启 E2E PASS |
| R20 | SAF/口令/预览/二次确认/恢复 | **PASS**（E2E） |

## 7. 未覆盖/风险

1. **未执行** R2-R7；这是完整 Gate 的阻断项。脚本已就绪，设备恢复后需跑。
2. 未跑 L1/L2 `verify.sh` 集成；仅直接 E2E 与 JVM。
3. 迁移仅手动验证 `user_version=12`/app_meta 列，未跑 G3F `verify_migration`。
4. 最终 APK 在 E2E 之后又做了成功提示/QA 状态小改动；主链路源码未变，但最终 APK 未重新跑 E2E（设备 21:33 后离线）。
5. 真实大数据量/七组全量回归未做。
6. 成功提示依赖设置页滚动/RestoreResultHost；Toast 已加，但设备最终版未复验。

## 8. 结论

**功能主链路已实际恢复成功并可重启使用；失败/崩溃收敛未验证，不能标记完整 PASS。** 停止在 G4A-2B，不进入 G4A-2C。
