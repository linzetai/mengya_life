# Gate 4A-2B 报告：完整备份恢复闭环

> 范围：真实恢复、Room v12、app_meta、Gate/journal、安全重置、快照、恢复 UI。
> 结果：**主链路 35/35 PASS；失败/崩溃路径 R2-R7 NOT RUN；最终 Gate NOT PASS（条件通过）。**

## 1. 实现概述

在 G4A-2A 只读预览基础上：

1. DB v11→v12，新增 `app_meta`；`MIGRATION_11_12`；无 destructive migration。
2. `RestoreTransaction`：单 Room 事务删除 10 表 → 插入 9 表 → 写 app_meta → 回读摘要校验；weeklyAI 清空。
3. `RestorePlan`：显式转换 ANALYZING→FAILED、contextGoalId→null、signal/memory→STALE；计算 expectedRestoredSha256。
4. `RestoreGate`/`RestoreJournal`/`RestoreCoordinator`/启动 reconciliation。
5. 安全重置：`resetAfterRestore` 幂等 + lastResetGeneration；`deleteKeyConfirmed` 确认 alias；AI 自动发送关闭；endpoint/model 恢复但 Key 不恢复。
6. 保护快照：v2 备份 + 独立 Keystore alias 保护随机口令；可重读。
7. UI：SAF→口令→预览→风险勾选→确认→执行→成功/维护；Gate 活跃时维护页。
8. Worker 入口、对话/洞察/日记/周 AI 写回入口检查 Gate。
9. 修复 TempStore 删除 false；G4A1 ZIP 移出仓库。

## 2. 恢复状态机

- Gate 文件 phases：ARMED → RESET_PENDING → SETTINGS_PENDING → READY_TO_RELEASE。
- app_meta attempt 是 DB 提交唯一依据；journal 只辅助。
- 执行顺序：快照 → Gate ARMED → journal PREPARED → DB 事务（app_meta）→ journal DB_COMMITTED → consent reset → Key delete confirmed → AI auto off → settings apply → markConverged → Gate release。
- 提交后失败：Gate 保持，UI 维护，重启 reconciliation 重试。
- DB 前失败：Gate/journal 清理，旧数据/Key/consent 保持。
- Gate/journal 同时丢失：app_meta attempt 存在但 converged_at 为空 → fail-closed；converged 后才允许正常。
- Gate 损坏/DB 不可用：维护态，不自动清库。
- UI 维护页阻断普通业务；Worker/AI 写回 gate 检查。

## 3. 真实 E2E（A→导出→B→恢复 A）

QA 包 `com.mengyalife.app.qa`：
- A：10 表 23 行 + QA Key/consent/自动发送；
- 导出真实 SAF v2 文件；
- B：同表数不同正文；
- 恢复 A（预览、勾选、确认、事务）；
- 结果：**35/35 PASS，346.84s**；
- 核心 8 表 A 逐字段一致；weekly_reviews 恢复；weeklyAI 0；Key/consent/AI auto 均 false；快照存在；Gate 释放；app_meta 保留；重启核心 8 表仍 A。

证据：
- `docs/reports/G4A2B-acceptance/qa/e2e-output.txt`
- `db-A.json` / `db-B.json` / `db-AFTER.json` / `db-AFTER2.json`
- `app_meta-AFTER2.json`
- A 备份样本/DB 来自 QA 假数据；不含真实数据。

## 4. 失败/崩溃验证（未完成）

`verify_restore_failures.py` 已实现 R2-R7：
- R2 事务失败；
- R3 Key 删除失败；
- R4 提交后 journal 前崩溃；
- R5 安全重置中途崩溃；
- R6 Journal 丢失；
- R7 Gate 丢失。
**本轮设备在运行前断开，未执行；结果 NOT RUN。** 因此不能把 G4A-2B 标为完整 PASS。

## 5. 测试

| 项 | 结果 |
|---|---|
| JVM | **291/291 PASS**（24.5s） |
| restore-validate L0 | 15 restore tests PASS（历史 7.8s） |
| E2E | 35/35 PASS（346.84s） |
| 失败/崩溃 | NOT RUN |
| L1/L2 verify.sh | NOT RUN（仅直接 E2E） |
| 迁移 | 手动 `PRAGMA user_version=12` + app_meta 列；G3F verify_migration NOT RUN |
| APK | 构建成功；QA `397637...`，debug `680813...` |

## 6. 数据库/app_meta

`app_meta` 9 列：id, restore_attempt_id, backup_id, generation, settings_json, completed_at, expected_sha256, payload_sha256, converged_at。
真机 E2E AFTER2 中行保留；快照/安全状态文件保留。

## 7. 安全

- DB 事务提交前不删除 Key/consent；
- `deleteKeyConfirmed` 确认 alias 不存在（Keystore alias 独立于快照 alias）；
- consent reset 幂等、lastResetGeneration，不依赖 epoch>=generation；
- AI 自动关闭；旧 recipient/categories 清空；
- 快照加密，不依赖 AI Key alias；
- 无广泛存储权限；SAF 只读；无明文日志。
- 旧 AI 请求写回在 conversation/insight/diary/weekly 入口 gate 阻断。

## 8. 已知问题/风险

1. R2-R7 未跑，是 Gate 阻断项。
2. L1/L2 `verify.sh` 未跑；最终 APK 在 E2E 后有小改动，未重跑主链路。
3. 迁移仅手动检查，未跑 G3F 迁移脚本。
4. 成功提示在设置页滚动位置可能不在首屏；已加 Toast/全局 host，但最终 APK 未复验。
5. 大数据量性能、七组全量回归未做。
6. 无真实用户数据测试；全部 QA 假数据。

## 9. 下一步（G4A-2C）

1. 恢复 QA 设备，执行 `verify_restore_failures.py`（R2-R7），逐项保留 Gate/journal/app_meta/Key 证据。
2. 跑 `verify_migration.py` 与 `restore-validate --level feature/integration`。
3. 用最终 APK 重跑 E2E 35/35。
4. 通过后再标记完整 G4A-2B PASS；本轮停止。
