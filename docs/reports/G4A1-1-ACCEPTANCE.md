# G4A-1.1 验收（完整备份验证收口）

## 1. Gate 状态

**G4A-1.1 Gate：PASS。**

外部指出的“只证明 1 日记 / 1 目标 / 1 周回顾”缺口已关闭：QA 真机夹具现在 **10/10 表非空、120/120 列**，
真实 SAF 导出后用独立 Node 探针解密、Python 逐表逐字段比较，**0 差异**。
B-11 的设备端失败路径也由 QA-only 可控注入补跑：**6 场景 / 45 断言 PASS**。

## 2. 10 表非空夹具

QA 假数据（`QaBackupFixtureSeeder`，仅 QA 构建可调用）：

| 表 | 行数 | 覆盖点 |
|---|---:|---|
| journal_entries | 2 | 标题/正文/分析状态；Unicode、换行、引号、反斜杠、emoji；可空 title/analysisHash/analysisError |
| goals | 3 | LONG_TERM → STAGE → ACTION 三级；父子外键；可空 description/startDate/dueDate |
| goal_checkins | 2 | 行动/阶段外键；3.5 Double；null metricValue/metricUnit |
| advisor_messages | 3 | 用户/助手/失败消息；JSON sources；可空 contextGoalId/error |
| goal_proposals | 2 | DRAFT（空 applied）与 APPLIED（selected/applied/JSON TEXT） |
| growth_signals | 3 | ACTIVE / CORRECTED / STALE；用户纠正；可空 confidence/sourceTitle |
| growth_memories | 2 | ACTIVE + 用户纠正；DELETED 墓碑/`deletedAt` |
| insight_reports | 2 | READY + FAILED；JSON facts/insights；可空 error/model |
| weekly_reviews | 2 | notifiedAt 非空/空；JSON facts/sections |
| weekly_ai_reviews | 2 | 历史 U+001F factIds + 新版 JSON factIds；READY/STALE；历史 AI 周报 |

**总计 23 行、120 原始列；所有表非空。**

## 3. 无损性结果

| 项 | 结果 |
|---|---|
| 10/10 表非空 | PASS |
| 120/120 原始列映射 | PASS |
| 逐表逐字段比较（ID/名称/类型/值/null/数值/JSON/状态/引用） | **0 differences** |
| IDs（Long/UUID） | PASS |
| 可空字段 null 保持 | PASS |
| Double 精度（3.5 等） | PASS |
| Unicode/换行/引号/反斜杠/emoji | PASS |
| JSON TEXT 语义（解析后） | PASS |
| 三级目标/打卡外键 | PASS |
| weekly_ai `factIds` + `legacyFactIdsRaw` 显式转换 | PASS（不要求字节相同） |
| `perTableSha256` | 10/10 独立重算匹配 |
| `payloadSha256` | 独立重算匹配 |
| 敏感字段扫描（解密 payload） | PASS |
| 导出后原 DB 完全不变 | PASS（before/after 全表 JSON 相等） |

真实文件：**15,373 字节**；QA 端到端 **18.07s**。
样本：`docs/reports/G4A1-1-acceptance/qa/sample-v2-full.mengyalife`。

`payloadSha256`：

```text
ba986e783260e8c75c9786ed0776c399cd8444f0206cdf150cd26d278f095396
```

10 表 perTableSha256 见 `docs/reports/G4A1-1-acceptance/qa/probe-full.json`（独立探针已逐表重算并匹配）。

## 4. B-11 设备失败路径

QA-only 可控故障注入（不物理拔盘），真实设备 45/45：

| 场景 | 结果 |
|---|---|
| 用户取消 SAF | 不显示成功；不生成文件；DB 不变 |
| 目标写入失败 | 不显示成功；尝试清理；DB 不变；无明文临时文件 |
| 目标文件被截断 | 回读不一致拒绝；不显示成功；清理 |
| 回读失败 | 拒绝；不显示成功；清理 |
| 临时文件清理失败 | 成功结果附明确 warning；临时文件为密文；无明文 |
| 目标无法删除 | 明确提示“目标文件可能无效”；不显示成功；DB 不变 |

证据：`docs/reports/G4A1-1-acceptance/failures/run-output.txt`、`scenarios.json`。

## 5. 测试

| 命令 | 退出码 | 结果 | 实测 |
|---|---|---|---|
| `verify_backup_v2_full.py`（真机） | 0 | **19/19 PASS** | 18.07s 备份；55.3s 脚本 |
| `verify_backup_v2_failures.py`（真机） | 0 | **45/45 PASS** | 327.21s |
| `--module backup-export --level feature` | 0 | **L1 PASS** | 647.5s |
| `--module backup-export --level integration` | 0 | **L2 PASS** | 1248.4s |
| 全量 JVM | 0 | **274/274 PASS** | 8.5s |
| QA/debug APK 构建 | 0 | BUILD SUCCESSFUL | 19s |

L1/L2 报告：`docs/reports/G4A1-1-acceptance/l1/report.json`、`l2/report.json`。

## 6. APK

| 包 | 版本 | SHA256 |
|---|---|---|
| QA | `0.14.1-g3e2-1-qa` | `7968cc662a703558f9a955b2e79ae5ab0e054834a361be97223a8a0be44a2474` |
| Debug | `0.14.1-g3e2-1` | `5bdae5b3efa690f89d200b136d2c175ac88eabdf897d3ad12552eabea551fa53` |

## 7. 残余/未执行

1. **物理拔盘/真实存储硬件故障**未执行；用 QA-only 可控注入替代（已标注）。
2. 大数据量性能未测；样本是 23 行假数据。
3. 未跑七组完整真机回归；L2 按 G3F 入口跑了必要相关脚本。
4. 未实现恢复/导入；未修改 schema/正式数据。

## 8. 结论

10/10 表、120/120 列、逐字段 0 差异、完整性哈希与设备失败路径均已验证；**G4A-1.1 PASS**。
