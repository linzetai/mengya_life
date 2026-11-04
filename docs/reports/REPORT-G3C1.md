# Gate 3C-1 报告 · 遗留收口 + 日记 AI 提取与成长线索

| 项 | 值 |
|---|---|
| 版本 | **0.9.0-g3c1**（versionCode 9） |
| 数据库 | Room **v6**（v5→v6 非破坏迁移） |
| 真机 | Xiaomi 2509FPN0BC / Android 17 |
| 执行 | 2026-11-04（Asia/Shanghai） |
| 结论 | **PASS（核心）**；未验证项见 §5 |

---

## 1. 结论

Gate 3C-1 已完成并在真机验证：

- G3B-2 遗留的 JSON 映射、tempId、appliedGoalIds 问题修复；
- QA 故障注入：第 2/3 个目标创建失败整体回滚；撤销中途失败回滚；
- 真机创建目标并添加进展后，撤销安全阻止；
- v6 迁移旧数据完整保留；
- 日记 AI 提取、编辑重提取、同版本幂等、纠正/删除、授权关闭/重开、顾问线索、关闭隔离；
- 两篇真实 DeepSeek 日记（职业成长事件、普通生活记录）均成功；
- 五个一级页面与设置/导航/导出回归通过。

未验证项不冒充通过：处理中撤回真机竞态、API 超时真机注入、真实模型的推测样本。

---

## 2. 前置修复（P0）

| 项 | 标记 | 证据 |
|---|---|---|
| 统一 tempId→goalId JSON 编解码 | **PASS** | `GoalProposalIds`；JVM 映射测试；真机 apply/undo 使用 mapping |
| 禁止手工拼 JSON | **PASS** | apply/repo 全部走 codec；代码审计无业务手拼 |
| 严格 tempId | **PASS** | JVM：非法字符/数字开头/超长；parser + codec 双重 |
| appliedGoalIds 读空 | **PASS** | 对象映射读取；撤销真机成功 |
| QA 注入第 2/3 个创建失败 | **PASS** | 真机两次：goals=[]、草案 DRAFT、提示回滚 |
| 事务完整回滚 | **PASS** | 第 2/3 个失败后无任何目标残留 |
| 撤销中途失败回滚 | **PASS** | 撤销第 1 个删除前注入失败：goals 仍 3、草案 APPLIED |
| 真机创建目标+进展后撤销阻止 | **PASS** | 提示「已有 1 条进展记录」；目标/进展保留 |
| v5 设置/导航/五页回归 | **PASS（v6 上重跑）** | 日记 25、目标 64、趋势 73、导航 28、设置 39、导出 49、正式包 50 |

---

## 3. 成长线索数据模型

新增 `growth_signals`：

- id、sourceType/sourceId、sourceVersion（SHA-256）、category、content、evidence、isInference、confidence、extractedAt、status、origin、sourceTitle、contentHash、updatedAt；
- 分类：EVENT/PROGRESS/BLOCKER/HABIT/EMOTION/ACTION_CANDIDATE；
- 状态：ACTIVE/CORRECTED/DELETED/STALE；
- 唯一索引 `(source_type, source_id, source_version, content_hash)`；
- `journal_entries` 增加 analysis 状态/哈希/错误/尝试次数/时间。

**PASS**：v6 真机迁移，`user_version=6`，旧日记/目标/消息/草案保留；详见 `g3c1-migration.log`。

---

## 4. 日记 AI 提取

实现：

- 保存成功后前台触发；失败不影响保存；
- 仅 AI 配置有效、持续授权有效、JOURNAL 开启才发送；
- 状态：PENDING/ANALYZING/COMPLETED/FAILED；
- 内容哈希幂等；编辑旧线索 STALE；删除日记删除线索；
- 失败可重试；唯一 contentHash 防重复；
- Mutex、30 秒最小间隔、4000 字输入、最多 6 条/1200 tokens；
- 请求中授权/配置/日记变化：丢弃结果、不写线索；
- 冷启动把遗留 ANALYZING 置 FAILED；
- 日记详情「AI 发现」：状态、分类、事实/推测、可信度、原文依据、纠正、删除、重试。

验证：

| 项 | 结果 |
|---|---|
| 职业成长事件真实提取 | PASS（3 条，DeepSeek） |
| 普通生活记录真实提取 | PASS（5 条，DeepSeek） |
| 编辑重提取 | PASS（旧 STALE，新 ACTIVE） |
| 用户纠正 | PASS（CORRECTED/USER/isInference=0） |
| 用户删除线索 | PASS（DELETED，UI 隐藏） |
| 删除日记 | PASS（signals=0） |
| JOURNAL 关闭 | PASS（PENDING、无请求、0 线索） |
| 重新开启后保存 | PASS（COMPLETED、1 条） |
| 顾问引用线索 | PASS（GROWTH 来源、日记#id） |
| 关闭 JOURNAL 后 | PASS（无 GROWTH、sources=null） |
| 重启保留 | PASS |
| 不修改目标/进展 | PASS |

---

## 5. 协议与顾问

协议：schemaVersion=1，严格校验 category/content/evidence/isInference/confidence；evidence 必须是日记原文子串；拒绝诊断；最多 6 条。详见 `GROWTH-SIGNAL-SCHEMA.md`。

顾问：

- 仅 JOURNAL 开启时使用 ACTIVE/CORRECTED 且当前版本哈希一致的线索；
- 原日记已选中时跳过其线索，避免重复；
- 用户纠正后只使用纠正结果；
- DELETED/STALE/日记删除后的线索不进入上下文。

---

## 6. 测试

| 项 | 结果 |
|---|---|
| JVM | **180/180** |
| 日记回归 | 25/25 |
| 目标回归 | 64/64 |
| 趋势回归 | 73/73 |
| 导航回归 | 28/28 |
| 设置回归 | 39/39 |
| 导出回归 | 49/49 |
| 正式包冒烟 | 50/50 |
| 真机功能 | PASS（见 `g3c1-device.log`） |
| 真实模型 | PASS（两篇日记） |

---

## 7. 未验证 / 已知风险

1. **处理中撤回授权的真机竞态**：JVM 单测覆盖；真机未精确卡在请求中。
2. **API 超时/失败的真机注入**：JVM 单测覆盖；未在真机网络层注入。
3. **真实模型的 isInference=true 样本**：本次 DeepSeek 输出全部标记为事实；QA fake/单测覆盖推测。
4. 同一内容版本重复保存幂等：JVM 单测通过；真机只验证 PENDING→COMPLETED 首次分析。
5. 设置/导航脚本在清空数据后的首启授权/配置状态上做过适配（功能项通过）。
6. 真实测试日记含 adb 输入残留片段（Career 尾部）；有效第一段与线索均正确；证据采集后测试日记已删除。

---

## 8. 交付

| 文件 | SHA-256 / 大小 |
|---|---|
| `artifacts/mengya-life-0.9.0-g3c1-debug.apk` | `e7150fae2e9f46fc2a40e2682db6df8f3ff13f75f29cb2563588a28e9080bd5b` · 11,809,206 B |
| `artifacts/mengya-life-0.9.0-g3c1-qa-test.apk` | `9a96e9283791534c0071e7f993080493ee766a08ea1843878115ca202a2507fe` · 11,809,993 B |
| 设备安装哈希 | 与上面一致 |
| 证据包 | `docs/reports/evidence/G3C1-evidence.zip` |

调试证书：`6fcae914998d767b41461241c69b4213b67143a01bbc385e81636a9b69a0efaf`。

---

## 9. 源码改动

新增：

- `domain/growth/GrowthSignal.kt`、`DiaryExtraction.kt`、`DiaryContentHash.kt`
- `data/local/growth/GrowthSignalEntity.kt`、`GrowthSignalDao.kt`
- `data/growth/GrowthSignalRepository.kt`、`DiaryExtractionService.kt`
- `domain/advisor/GoalProposalIds.kt`
- `data/advisor/QaFaultInjector.kt`
- 测试：`DiaryExtractionProtocolTest`、`DiaryExtractionTest`、`GoalProposalIdsTest`、`QaFaultInjectorTest`
- 文档：`GROWTH-SIGNAL-SCHEMA.md`、`DIARY-EXTRACTION-DESIGN.md`、`MIGRATION-G3C1.md`

修改：

- `JournalEntryEntity/Dao`、`JournalRepository`
- `JournalViewModel/Contract`、`DiaryScreen`
- `AdvisorContext`、`AdvisorDataRepository`、`AdvisorSourceType`
- `MengyaDatabase`（v6）
- `AppContainer`、`MainActivity`（QA 故障 extras）
- `GoalProposalRepository/ApplyService/Ids`
- `build.gradle` 0.9.0-g3c1

---

## 10. 后续建议

1. 真机注入网络超时与处理中撤回，补齐并发证据；
2. 真实模型补一次明确的 `isInference=true` 样本；
3. 线索批量操作/搜索（本轮保持单篇阅读为主）；
4. 将线索纳入导出/恢复（P1）前先定义隐私与体积策略。
