# Gate 4A-1.1 报告：完整备份验证收口

> 本批只关闭“10 表非空 + 无损性 + 设备失败路径”验收缺口；**没有**恢复/导入，**没有** schema 变更。
> 产品决策仍为 A1/B1/C1/D1。
> 结果：**PASS**。

## 1. 外部缺口与修正

**缺口**：G4A-1 QA 样本只有 1 日记、1 目标、1 周回顾；其余 7 表为空，不能证明 10 表/120 列无损。

**修正**：

1. 新增 QA-only `QaBackupFixtureSeeder`，一次构造 10 表非空、23 行、120 列的假数据。
2. 新增 `verify_backup_v2_full.py`：真机 SAF 导出 → 独立 Node 解密 → Python 按 120 列逐字段比较。
3. 新增 `QaBackupFaultInjector` + 设备脚本：取消/写失败/截断/回读失败/临时清理失败/目标删除失败。
4. 新增 JVM 回归（临时清理 warning）与 L1/L2 真机证据。

## 2. 夹具覆盖

| 表 | 行数 | 关键覆盖 |
|---|---:|---|
| journal_entries | 2 | Unicode/换行/引号/反斜杠/emoji；COMPLETED/FAILED；null title/hash/error |
| goals | 3 | 三级目标；父子外键；null description/start/due |
| goal_checkins | 2 | 行动/阶段 FK；Double 3.5；null metric |
| advisor_messages | 3 | 用户/助手/失败；JSON sources；null context/error |
| goal_proposals | 2 | DRAFT（空 applied）+ APPLIED（JSON TEXT） |
| growth_signals | 3 | ACTIVE/CORRECTED/STALE；用户纠正；null confidence/title |
| growth_memories | 2 | ACTIVE + 用户纠正；DELETED 墓碑/deletedAt |
| insight_reports | 2 | READY/FAILED；JSON facts/insights；null error/model |
| weekly_reviews | 2 | notifiedAt 有/无；JSON sections |
| weekly_ai_reviews | 2 | 历史 U+001F + 新版 JSON factIds；READY/STALE |

`QaBackupFixtureSeeder` 只在 `BuildConfig.QA_BUILD` 的 `qaSeedBackupFixture()` 入口执行；正式包不会调用，也没有真实数据读取。

## 3. 独立无损验证

```text
QA Room DB (before) ── pull sqlite ──► db-before.json
        │
        └─ 设置页 → 口令 → SAF 导出 → v2 文件
                                      │
                       Node 独立解密 ──┤
                                       ├─ payloadSha256 / perTableSha256
                                       ├─ 敏感字段扫描
                                       └─ payload-full.json
                                                │
Python：10 表 × 120 列逐字段比较 ◄──────────────┘
        → field-diff.json（0 errors）
```

- 比较 ID、字段名、类型、值、null、Double、JSON TEXT、历史状态、逻辑引用。
- `weekly_ai_reviews.fact_ids_json` 按 v2 规则拆成 `factIds` + `legacyFactIdsRaw`：
  - `factIds` 解析旧 U+001F/新 JSON；
  - `legacyFactIdsRaw` 保持原始值；
  - 不要求转换前后字节相同。
- 结果：**10/10 表、120/120 列、0 差异**；DB before/after 全表 JSON 相等。
- 独立 `perTableSha256` 10/10 匹配；`payloadSha256` 匹配；解密 payload 敏感扫描 PASS。

真实文件：**15,373 字节 / 18.07s**（1 次假数据端到端）。

## 4. 设备失败路径

`QaBackupFaultInjector` 由 MainActivity QA intent 设置，`SafBackupDestination`/`AndroidBackupTempStore` 在 QA_BUILD 下注入：

| 场景 | 注入 | 断言 |
|---|---|---|
| 取消 SAF | BACK | 无成功、无文件、DB 不变 |
| 写失败 | 写 8 字节后抛错 | 无成功、清理、无明文 |
| 截断 | 写 bytes-1 | 回读不一致、拒绝 |
| 回读失败 | read 抛错 | 拒绝、清理 |
| 临时清理失败 | temp delete 抛错 | 成功结果附 warning；临时文件密文 |
| 目标删除失败 | delete 返回 false | “目标文件可能无效”；不报成功 |

真实设备 **6 场景 / 45 断言 PASS**，DB 不变，既有文件不覆盖。

## 5. 测试

| 命令 | 退出码 | 结果 |
|---|---|---|
| `verify_backup_v2_full.py` | 0 | 19/19 PASS；18.07s |
| `verify_backup_v2_failures.py` | 0 | 45/45 PASS；327.21s |
| `--module backup-export --level feature` | 0 | L1 PASS，647.5s |
| `--module backup-export --level integration` | 0 | L2 PASS，1248.4s |
| 全量 JVM | 0 | 274/274 PASS |

L1/L2 包含 G3F 入口按级别要求的相关脚本；没有执行七组完整回归。

## 6. 关键代码

- 夹具：`app/src/main/java/com/mengyalife/app/data/qa/QaBackupFixtureSeeder.kt`
- 注入器：`app/src/main/java/com/mengyalife/app/data/backup/QaBackupFaultInjector.kt`
- 写入/清理：`.../data/backup/BackupFileWriter.kt`
- SAF 注入：`.../data/backup/AndroidBackupStorage.kt`
- UI warning：`.../ui/settings/BackupViewModel.kt`
- 验证：`tools/ui-verify/verify_backup_v2_full.py`、`verify_backup_v2_failures.py`、`backup_v2_probe.js`
- 测试：`app/src/test/java/com/mengyalife/app/backup/**`

## 7. 证据包

`G4A1-1-evidence.zip` 包含：实现/测试源码、test-map/CLI 选择规则、QA 脚本、全部 L1/L2/JVM/模块报告、10 表夹具与 DB before/after、加密样本、独立探针、Manifest/XML、Git 清单/HEAD。

## 8. 未做

- 恢复/导入、RestoreGate、schema v12；
- 物理拔盘（用可控注入替代，已标注）；
- 大数据量性能；
- 七组完整真机回归。

## 9. 结论

G4A-1.1 验收缺口已关闭，Gate PASS；停止，不进入 G4A-2。
